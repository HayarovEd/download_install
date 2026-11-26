package com.edurda77.download_install.refresher

import android.app.Application
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.edurda77.download_install.downloader.DownloadRepository
import com.edurda77.download_install.installer.Installer
import com.edurda77.download_install.model.LastVersionApp
import com.edurda77.download_install.utils.DownloadDataError
import com.edurda77.download_install.utils.DownloadStatus
import com.edurda77.download_install.utils.FILE_NAME
import com.edurda77.download_install.utils.FILE_URL
import com.edurda77.download_install.utils.KEY_DOWNLOAD_URL
import com.edurda77.download_install.utils.KEY_FORCE_NOTIFY
import com.edurda77.download_install.utils.KEY_PROGRESS
import com.edurda77.download_install.utils.KEY_VERSION_URL
import com.edurda77.download_install.utils.ResultDownloadWork
import com.edurda77.download_install.utils.UNIQUE_IMMEDIATE_UPDATE_CHECK_WORK
import com.edurda77.download_install.utils.UNIQUE_PERIODIC_UPDATE_CHECK_WORK
import com.edurda77.download_install.utils.UNIQUE_UPDATE_DOWNLOAD_WORK
import com.edurda77.download_install.utils.UPDATE_AVAILABLE_NOTIFICATION_ID
import com.edurda77.download_install.worker.CheckUpdateWorker
import com.edurda77.download_install.worker.DownloadUpdateWorker
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

internal class RefresherImpl(
    private val application: Application,
    private val downloadRepository: DownloadRepository,
    private val installer: Installer,
) : Refresher {

    override suspend fun getLastVersion(versionUrl: String): ResultDownloadWork<LastVersionApp, DownloadDataError> =
        downloadRepository.getLastUpdateVersion(versionUrl)

    override suspend fun updateInApp(
        downloadUrl: String,
        downloadedFileName: String,
    ): Flow<DownloadStatus> {
        cancelUpdateAvailableNotification()
        return flow {
            downloadRepository.downloadFile(
                url = downloadUrl,
                downloadedFileName = downloadedFileName
            ).collect { collector ->
                when (collector) {
                    is DownloadStatus.Error -> {
                        emit(DownloadStatus.Error(collector.error))
                    }

                    is DownloadStatus.InProgress -> {
                        emit(DownloadStatus.InProgress(collector.percentage))
                    }

                    DownloadStatus.Started -> {
                        emit(DownloadStatus.Started)
                    }

                    DownloadStatus.Success -> {
                        installer.installAPK(downloadedFileName)
                        emit(DownloadStatus.Success)
                    }
                }
            }
        }
    }

    override suspend fun updateInBackground(
        downloadUrl: String,
        downloadedFileName: String,
    ): Flow<DownloadStatus> {
        cancelUpdateAvailableNotification()
        return callbackFlow {
            val workerData = Data.Builder().apply {
                putString(FILE_URL, downloadUrl)
                putString(FILE_NAME, downloadedFileName)
            }
            send(DownloadStatus.Started)
            val workerRequest = OneTimeWorkRequestBuilder<DownloadUpdateWorker>()
                .setInputData(workerData.build())
                .build()
            val workManager = WorkManager.getInstance(application)
            workManager.enqueueUniqueWork(
                UNIQUE_UPDATE_DOWNLOAD_WORK,
                ExistingWorkPolicy.REPLACE,
                workerRequest
            )

            launch {
                workManager.getWorkInfoByIdFlow(workerRequest.id).collect { workInfo ->
                    when (workInfo?.state) {
                        WorkInfo.State.RUNNING -> {
                            val progress = workInfo.progress.getInt(KEY_PROGRESS, 0)
                            trySend(DownloadStatus.InProgress(progress))
                        }

                        WorkInfo.State.SUCCEEDED -> {
                            installer.installAPK(downloadedFileName)
                            trySend(DownloadStatus.Success)
                            close()
                        }

                        WorkInfo.State.FAILED -> {
                            trySend(DownloadStatus.Error(DownloadDataError.Network.SERVER_ERROR))
                            close()
                        }

                        WorkInfo.State.CANCELLED -> {
                            trySend(DownloadStatus.Error(DownloadDataError.Network.UNKNOWN))
                            close()
                        }

                        else -> Unit
                    }
                }
            }
            awaitClose {
                workManager.cancelWorkById(workerRequest.id)
            }
        }
    }

    override fun schedulePeriodicUpdateCheck(
        versionUrl: String,
        downloadUrl: String?,
        downloadedFileName: String,
        repeatIntervalHours: Long,
    ) {
        val intervalHours = repeatIntervalHours.coerceAtLeast(1L)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = workDataOf(
            KEY_VERSION_URL to versionUrl,
            KEY_DOWNLOAD_URL to downloadUrl,
            FILE_NAME to downloadedFileName,
            KEY_FORCE_NOTIFY to false
        )

        val periodicRequest = PeriodicWorkRequestBuilder<CheckUpdateWorker>(
            intervalHours,
            TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(application).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_UPDATE_CHECK_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )
    }

    override fun checkUpdatesInBackground(
        versionUrl: String,
        downloadUrl: String?,
        downloadedFileName: String,
        forceNotify: Boolean,
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = workDataOf(
            KEY_VERSION_URL to versionUrl,
            KEY_DOWNLOAD_URL to downloadUrl,
            FILE_NAME to downloadedFileName,
            KEY_FORCE_NOTIFY to forceNotify
        )

        val request = OneTimeWorkRequestBuilder<CheckUpdateWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(application).enqueueUniqueWork(
            UNIQUE_IMMEDIATE_UPDATE_CHECK_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    override fun cancelPeriodicUpdateCheck() {
        WorkManager.getInstance(application)
            .cancelUniqueWork(UNIQUE_PERIODIC_UPDATE_CHECK_WORK)
    }

    private fun cancelUpdateAvailableNotification() {
        try {
            NotificationManagerCompat.from(application).cancel(UPDATE_AVAILABLE_NOTIFICATION_ID)
        } catch (_: Exception) {
        }
    }
}
