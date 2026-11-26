package com.edurda77.download_install.model

/**
 * Конфигурация адресов обновления для конкретного приложения.
 *
 * @param versionUrl URL эндпоинта проверки последней версии (JSON формата [VersionResponse]).
 * @param downloadUrl URL эндпоинта скачивания APK-файла.
 * @param downloadedFileName Имя локального APK-файла в кеше.
 * @param checkIntervalHours Интервал периодической проверки в часах (минимум 1 час).
 */
data class UpdateConfig(
    val versionUrl: String,
    val downloadUrl: String,
    val downloadedFileName: String = "update.apk",
    val checkIntervalHours: Long = 1L,
)
