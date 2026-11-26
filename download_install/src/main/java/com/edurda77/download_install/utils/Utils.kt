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
            part.takeWhile { it.isDigit() }.also { if (it.isEmpty()) return false }
        }
        val latestParts = latestNormalized.split(".").map { part ->
            part.takeWhile { it.isDigit() }.also { if (it.isEmpty()) return false }
        }
        if (currentParts.isEmpty() || latestParts.isEmpty()) return false

        val maxLength = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until maxLength) {
            val currentPart = currentParts.getOrElse(i) { "0" }
            val latestPart = latestParts.getOrElse(i) { "0" }
            val cmp = compareVersionSegment(
                left = latestPart,
                right = currentPart,
                allowFractionalLeadingZeros = i > 0
            )
            if (cmp > 0) return true
            if (cmp < 0) return false
        }
        false
    } catch (e: Exception) {
        false
    }
}

private fun compareVersionSegment(
    left: String,
    right: String,
    allowFractionalLeadingZeros: Boolean
): Int {
    val leftTrimmed = left.trimStart('0')
    val rightTrimmed = right.trimStart('0')

    if (leftTrimmed.isEmpty() && rightTrimmed.isEmpty()) return 0
    if (leftTrimmed.isEmpty()) return -1
    if (rightTrimmed.isEmpty()) return 1

    if (allowFractionalLeadingZeros) {
        val leftHasLeadingZero = left.length > 1 && left.startsWith('0')
        val rightHasLeadingZero = right.length > 1 && right.startsWith('0')

        if (leftHasLeadingZero || rightHasLeadingZero) {
            if (leftHasLeadingZero != rightHasLeadingZero) {
                // Any positive 0.xxx (< 0.1) is strictly smaller than any positive integer segment (>= 0.1)
                return if (leftHasLeadingZero) -1 else 1
            }
            // Both have leading zeros (e.g. "001" vs "01" -> "001" vs "010")
            val maxLen = maxOf(left.length, right.length)
            return left.padEnd(maxLen, '0').compareTo(right.padEnd(maxLen, '0'))
        }
    }

    if (leftTrimmed.length != rightTrimmed.length) {
        return leftTrimmed.length.compareTo(rightTrimmed.length)
    }
    return leftTrimmed.compareTo(rightTrimmed)
}

fun shouldNotifyUpdate(
    currentVersion: String,
    latestVersion: String,
    lastNotifiedVersion: String?,
    forceNotify: Boolean = false
): Boolean {
    if (!isNewVersionAvailable(currentVersion, latestVersion)) return false
    if (forceNotify) return true
    if (lastNotifiedVersion.isNullOrBlank()) return true
    return isNewVersionAvailable(lastNotifiedVersion, latestVersion)
}
