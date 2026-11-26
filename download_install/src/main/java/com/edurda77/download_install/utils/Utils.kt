package com.edurda77.download_install.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

fun Context.getApkFile(fileName: String): File {
    val dir = externalCacheDir ?: cacheDir
    return File(dir, fileName)
}

fun Context.pathToDownloadFile(fileName: String): String =
    getApkFile(fileName).absolutePath

fun Context.getCurrentAppVersionName(): String? {
    return try {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        packageInfo.versionName
    } catch (e: Exception) {
        null
    }
}

fun isNewVersionAvailable(currentVersion: String, latestVersion: String): Boolean {
    val currentNormalized = currentVersion.trim().trimStart('v', 'V')
    val latestNormalized = latestVersion.trim().trimStart('v', 'V')
    if (currentNormalized.isBlank() || latestNormalized.isBlank()) return false
    if (currentNormalized == latestNormalized) return false

    return try {
        val currentParts = currentNormalized.split(".").map { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull() ?: return false
        }
        val latestParts = latestNormalized.split(".").map { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull() ?: return false
        }
        if (currentParts.isEmpty() || latestParts.isEmpty()) return false

        val maxLength = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until maxLength) {
            val currentPart = currentParts.getOrElse(i) { 0 }
            val latestPart = latestParts.getOrElse(i) { 0 }
            if (latestPart > currentPart) return true
            if (latestPart < currentPart) return false
        }
        false
    } catch (e: Exception) {
        false
    }
}
