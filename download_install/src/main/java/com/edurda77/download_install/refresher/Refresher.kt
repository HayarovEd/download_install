package com.edurda77.download_install.refresher

import com.edurda77.download_install.model.LastVersionApp
import com.edurda77.download_install.model.UpdateConfig
import com.edurda77.download_install.utils.DownloadDataError
import com.edurda77.download_install.utils.DownloadStatus
import com.edurda77.download_install.utils.ResultDownloadWork
import kotlinx.coroutines.flow.Flow

interface Refresher {
    suspend fun getLastVersion(versionUrl: String): ResultDownloadWork<LastVersionApp, DownloadDataError>

    suspend fun updateInApp(downloadUrl: String, downloadedFileName: String): Flow<DownloadStatus>

    suspend fun updateInBackground(downloadUrl: String, downloadedFileName: String): Flow<DownloadStatus>

    fun schedulePeriodicUpdateCheck(
        versionUrl: String,
        downloadUrl: String? = null,
        downloadedFileName: String = "update.apk",
        repeatIntervalHours: Long = 1L,
    )

    fun checkUpdatesInBackground(
        versionUrl: String,
        downloadUrl: String? = null,
        downloadedFileName: String = "update.apk",
        forceNotify: Boolean = true,
    )

    fun cancelPeriodicUpdateCheck()

    suspend fun getLastVersion(config: UpdateConfig): ResultDownloadWork<LastVersionApp, DownloadDataError> =
        getLastVersion(config.versionUrl)

    suspend fun updateInApp(config: UpdateConfig): Flow<DownloadStatus> =
        updateInApp(config.downloadUrl, config.downloadedFileName)

    suspend fun updateInBackground(config: UpdateConfig): Flow<DownloadStatus> =
        updateInBackground(config.downloadUrl, config.downloadedFileName)

    fun schedulePeriodicUpdateCheck(config: UpdateConfig) {
        schedulePeriodicUpdateCheck(
            versionUrl = config.versionUrl,
            downloadUrl = config.downloadUrl,
            downloadedFileName = config.downloadedFileName,
            repeatIntervalHours = config.checkIntervalHours,
        )
    }

    fun checkUpdatesInBackground(config: UpdateConfig, forceNotify: Boolean = true) {
        checkUpdatesInBackground(
            versionUrl = config.versionUrl,
            downloadUrl = config.downloadUrl,
            downloadedFileName = config.downloadedFileName,
            forceNotify = forceNotify,
        )
    }
}
