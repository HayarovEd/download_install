package com.edurda77.download_install.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.edurda77.download_install.utils.ACTION_START_UPDATE_DOWNLOAD
import com.edurda77.download_install.utils.FILE_NAME
import com.edurda77.download_install.utils.FILE_URL
import com.edurda77.download_install.utils.UNIQUE_UPDATE_DOWNLOAD_WORK
import com.edurda77.download_install.utils.UPDATE_AVAILABLE_NOTIFICATION_ID
import com.edurda77.download_install.worker.DownloadUpdateWorker

class UpdateActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_START_UPDATE_DOWNLOAD) return

        val downloadUrl = intent.getStringExtra(FILE_URL)
        if (downloadUrl.isNullOrBlank()) return

        val fileName = intent.getStringExtra(FILE_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: "update.apk"

        try {
            NotificationManagerCompat.from(context).cancel(UPDATE_AVAILABLE_NOTIFICATION_ID)
        } catch (_: Exception) {
        }

        val inputData = workDataOf(
            FILE_URL to downloadUrl,
            FILE_NAME to fileName
        )

        val workRequest = OneTimeWorkRequestBuilder<DownloadUpdateWorker>()
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_UPDATE_DOWNLOAD_WORK,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }
}
