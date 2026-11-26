package com.edurda77.download_install.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.edurda77.download_install.R
import com.edurda77.download_install.utils.DOWNLOAD_UPDATE_CHANNEL
import com.edurda77.download_install.utils.DownloadDataError
import com.edurda77.download_install.utils.FILE_NAME
import com.edurda77.download_install.utils.FILE_URL
import com.edurda77.download_install.utils.KEY_ERROR
import com.edurda77.download_install.utils.KEY_PROGRESS
import com.edurda77.download_install.utils.UPDATE_AVAILABLE_NOTIFICATION_ID
import com.edurda77.download_install.utils.getApkFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class DownloadUpdateWorker(
    context: Context,
    private val workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            val context = applicationContext
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationId = 1001

            val fileUrl = workerParameters.inputData.getString(FILE_URL)
            val fileName = workerParameters.inputData.getString(FILE_NAME) ?: "update.apk"
            val targetFile = context.getApkFile(fileName)

            try {
                if (fileUrl.isNullOrEmpty()) {
                    return@withContext Result.failure(workDataOf(KEY_ERROR to DownloadDataError.Network.BAD_REQUEST.name))
                }

                try {
                    NotificationManagerCompat.from(context).cancel(UPDATE_AVAILABLE_NOTIFICATION_ID)
                } catch (_: Exception) {
                }

                val url = URL(fileUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.connect()

                if (connection.responseCode !in 200..299) {
                    return@withContext Result.failure(workDataOf(KEY_ERROR to DownloadDataError.Network.BAD_REQUEST.name))
                }

                val contentLength = connection.contentLength.toLong()
                if (contentLength > 0 && !checkAvailableSpace(targetFile.parentFile, contentLength)) {
                    return@withContext Result.failure(workDataOf(KEY_ERROR to DownloadDataError.Network.SERVER_ERROR.name))
                }

                cleanupOldApks(context, fileName)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channelName = context.getString(R.string.download_update)
                    val channel = NotificationChannel(DOWNLOAD_UPDATE_CHANNEL, channelName, NotificationManager.IMPORTANCE_LOW)
                    notificationManager.createNotificationChannel(channel)
                }

                val channelName = context.getString(R.string.download_update)
                val builder = NotificationCompat.Builder(context, DOWNLOAD_UPDATE_CHANNEL)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle(channelName)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)

                connection.inputStream.use { inputStream ->
                    FileOutputStream(targetFile).use { outputStream ->
                        val buffer = ByteArray(32768)
                        var totalDownloaded = 0L
                        var bytesRead: Int
                        var lastProgress = -1
                        var lastNotifyTime = 0L

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            totalDownloaded += bytesRead
                            outputStream.write(buffer, 0, bytesRead)

                            if (contentLength > 0) {
                                val progress = (totalDownloaded * 100 / contentLength).toInt()
                                val currentTime = System.currentTimeMillis()

                                if (progress > lastProgress && (currentTime - lastNotifyTime > 300 || progress == 100)) {
                                    lastProgress = progress
                                    lastNotifyTime = currentTime

                                    builder.setContentText("$fileName: $progress%")
                                        .setProgress(100, progress, false)
                                    notificationManager.notify(notificationId, builder.build())
                                    setProgress(workDataOf(KEY_PROGRESS to progress))
                                }
                            }
                        }
                    }
                }

                val installPendingIntent = createInstallPendingIntent(context, targetFile)
                val doneBuilder = NotificationCompat.Builder(context, DOWNLOAD_UPDATE_CHANNEL)
                    .setContentTitle(context.getString(R.string.download_finished))
                    .setContentText(context.getString(R.string.update_downloaded_tap_to_install))
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setProgress(0, 0, false)

                installPendingIntent?.let { doneBuilder.setContentIntent(it) }
                notificationManager.notify(notificationId, doneBuilder.build())

                Result.success()
            } catch (e: Exception) {
                e.printStackTrace()
                if (targetFile.exists()) {
                    targetFile.delete()
                }

                val errorBuilder = NotificationCompat.Builder(context, DOWNLOAD_UPDATE_CHANNEL)
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setContentTitle(context.getString(R.string.download_error))
                    .setContentText(e.message ?: context.getString(R.string.unknown))
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                notificationManager.notify(notificationId, errorBuilder.build())

                Result.failure(workDataOf(KEY_ERROR to DownloadDataError.Network.SERVER_ERROR.name))
            }
        }
    }

    private fun createInstallPendingIntent(context: Context, apkFile: File): PendingIntent? {
        if (!apkFile.exists()) return null
        return try {
            val authority = "${context.packageName}.provider"
            val apkUri = FileProvider.getUriForFile(context, authority, apkFile)
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            PendingIntent.getActivity(
                context,
                INSTALL_REQUEST_CODE,
                installIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun checkAvailableSpace(dir: File?, requiredBytes: Long): Boolean {
        val path = dir?.absolutePath ?: return true
        return try {
            val stat = android.os.StatFs(path)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            availableBytes > requiredBytes + (20 * 1024 * 1024)
        } catch (e: Exception) {
            true
        }
    }

    private fun cleanupOldApks(context: Context, currentFileName: String) {
        try {
            val dir = context.externalCacheDir ?: context.cacheDir
            dir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".apk") && file.name != currentFileName) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    companion object {
        private const val INSTALL_REQUEST_CODE = 1003
    }
}
