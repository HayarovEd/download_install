package com.edurda77.download_install.worker

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.edurda77.download_install.DownloadProvider
import com.edurda77.download_install.R
import com.edurda77.download_install.model.LastVersionApp
import com.edurda77.download_install.receiver.UpdateActionReceiver
import com.edurda77.download_install.utils.ACTION_START_UPDATE_DOWNLOAD
import com.edurda77.download_install.utils.DOWNLOAD_INSTALL_PREFS
import com.edurda77.download_install.utils.DownloadDataError
import com.edurda77.download_install.utils.EXTRA_LATEST_VERSION
import com.edurda77.download_install.utils.EXTRA_OPEN_UPDATE
import com.edurda77.download_install.utils.FILE_NAME
import com.edurda77.download_install.utils.FILE_URL
import com.edurda77.download_install.utils.KEY_CURRENT_VERSION
import com.edurda77.download_install.utils.KEY_DOWNLOAD_URL
import com.edurda77.download_install.utils.KEY_FORCE_NOTIFY
import com.edurda77.download_install.utils.KEY_LAST_NOTIFIED_VERSION
import com.edurda77.download_install.utils.KEY_VERSION_URL
import com.edurda77.download_install.utils.ResultDownloadWork
import com.edurda77.download_install.utils.UPDATE_AVAILABLE_CHANNEL
import com.edurda77.download_install.utils.UPDATE_AVAILABLE_NOTIFICATION_ID
import com.edurda77.download_install.utils.getCurrentAppVersionName
import com.edurda77.download_install.utils.isNewVersionAvailable
import com.edurda77.download_install.utils.shouldNotifyUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CheckUpdateWorker(
    context: Context,
    private val workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        val versionUrl = workerParameters.inputData.getString(KEY_VERSION_URL)
        if (versionUrl.isNullOrBlank()) {
            Log.w(TAG, "Version URL is missing, skipping background update check")
            return@withContext Result.failure()
        }

        val currentVersion = workerParameters.inputData.getString(KEY_CURRENT_VERSION)
            ?.takeIf { it.isNotBlank() }
            ?: context.getCurrentAppVersionName()
        if (currentVersion.isNullOrBlank()) {
            Log.w(TAG, "Current app version could not be determined")
            return@withContext Result.failure()
        }

        val downloadUrl = workerParameters.inputData.getString(KEY_DOWNLOAD_URL)
        val fileName = workerParameters.inputData.getString(FILE_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: "update.apk"
        val forceNotify = workerParameters.inputData.getBoolean(KEY_FORCE_NOTIFY, false)

        val httpClient = DownloadProvider.provideHttpClient()
        try {
            val repository = DownloadProvider.provideDownloader(
                httpClient = httpClient,
                context = context
            )
            when (val result = repository.getLastUpdateVersion(versionUrl)) {
                is ResultDownloadWork.Success -> {
                    handleSuccessResult(
                        context = context,
                        currentVersion = currentVersion,
                        lastVersionApp = result.data,
                        downloadUrl = downloadUrl,
                        fileName = fileName,
                        forceNotify = forceNotify
                    )
                    Result.success()
                }

                is ResultDownloadWork.Error -> {
                    Log.w(TAG, "Background update check failed with error: ${result.error}")
                    val isTransient = (result.error == DownloadDataError.Network.REQUEST_TIMEOUT) ||
                        (result.error == DownloadDataError.Network.SERVER_ERROR) ||
                        (result.error == DownloadDataError.Network.UNKNOWN)
                    if (isTransient && runAttemptCount < MAX_RETRY_ATTEMPTS) {
                        Result.retry()
                    } else {
                        Result.failure()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during background update check", e)
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure()
        } finally {
            httpClient.close()
        }
    }

    private fun handleSuccessResult(
        context: Context,
        currentVersion: String,
        lastVersionApp: LastVersionApp,
        downloadUrl: String?,
        fileName: String,
        forceNotify: Boolean
    ) {
        val prefs = context.getSharedPreferences(DOWNLOAD_INSTALL_PREFS, Context.MODE_PRIVATE)
        val lastNotifiedVersion = prefs.getString(KEY_LAST_NOTIFIED_VERSION, null)

        if (isNewVersionAvailable(currentVersion, lastVersionApp.lastVersion)) {
            val shouldNotify = shouldNotifyUpdate(
                currentVersion = currentVersion,
                latestVersion = lastVersionApp.lastVersion,
                lastNotifiedVersion = lastNotifiedVersion,
                forceNotify = forceNotify
            )
            if (shouldNotify && canPostNotifications(context)) {
                showUpdateAvailableNotification(
                    context = context,
                    lastVersionApp = lastVersionApp,
                    downloadUrl = downloadUrl,
                    fileName = fileName
                )
                prefs.edit {
                    putString(KEY_LAST_NOTIFIED_VERSION, lastVersionApp.lastVersion)
                }
            }
        } else {
            cancelUpdateAvailableNotification(context)
            if (!lastNotifiedVersion.isNullOrBlank() &&
                !isNewVersionAvailable(currentVersion, lastNotifiedVersion)
            ) {
                prefs.edit { remove(KEY_LAST_NOTIFIED_VERSION) }
            }
        }
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission")
    private fun showUpdateAvailableNotification(
        context: Context,
        lastVersionApp: LastVersionApp,
        downloadUrl: String?,
        fileName: String
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                UPDATE_AVAILABLE_CHANNEL,
                context.getString(R.string.update_available_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.update_available_channel_description)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val contentIntent = createOpenUpdatePendingIntent(context, lastVersionApp.lastVersion)
        val downloadPendingIntent = if (!downloadUrl.isNullOrBlank()) {
            createDownloadPendingIntent(context, downloadUrl, fileName)
        } else {
            null
        }

        val appTitle = lastVersionApp.name.ifBlank {
            context.applicationInfo.loadLabel(context.packageManager).toString()
        }
        val contentText = context.getString(
            R.string.update_available_text,
            appTitle,
            lastVersionApp.lastVersion
        )

        val builder = NotificationCompat.Builder(context, UPDATE_AVAILABLE_CHANNEL)
            .setSmallIcon(R.drawable.outline_update_24)
            .setContentTitle(context.getString(R.string.update_available_title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)

        contentIntent?.let { builder.setContentIntent(it) }
        downloadPendingIntent?.let {
            builder.addAction(
                R.drawable.outline_update_24,
                context.getString(R.string.update_available_action),
                it
            )
        }

        notificationManager.notify(UPDATE_AVAILABLE_NOTIFICATION_ID, builder.build())
    }

    private fun cancelUpdateAvailableNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(UPDATE_AVAILABLE_NOTIFICATION_ID)
        } catch (_: Exception) {
        }
    }

    private fun createOpenUpdatePendingIntent(
        context: Context,
        latestVersion: String
    ): PendingIntent? {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return null
        val intent = Intent(launchIntent).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_UPDATE, true)
            putExtra(EXTRA_LATEST_VERSION, latestVersion)
        }
        return PendingIntent.getActivity(
            context,
            UPDATE_AVAILABLE_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createDownloadPendingIntent(
        context: Context,
        downloadUrl: String,
        fileName: String
    ): PendingIntent {
        val intent = Intent(context, UpdateActionReceiver::class.java).apply {
            action = ACTION_START_UPDATE_DOWNLOAD
            putExtra(FILE_URL, downloadUrl)
            putExtra(FILE_NAME, fileName)
        }
        return PendingIntent.getBroadcast(
            context,
            DOWNLOAD_ACTION_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val TAG = "CheckUpdateWorker"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val DOWNLOAD_ACTION_REQUEST_CODE = UPDATE_AVAILABLE_NOTIFICATION_ID + 1
    }
}
