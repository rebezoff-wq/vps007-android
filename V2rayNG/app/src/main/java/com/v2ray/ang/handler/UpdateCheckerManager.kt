package com.v2ray.ang.handler

import android.os.Build
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.dto.CheckUpdateResult
import com.v2ray.ang.dto.GitHubRelease
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.extension.concatUrl
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UpdateCheckerManager {
    private const val PREF_LAST_AUTO_CHECK = "pref_vps007_last_update_check"

    /** Startup update check runs at most once per [AppConfig.APP_UPDATE_CHECK_INTERVAL_MS]. */
    fun shouldAutoCheck(now: Long = System.currentTimeMillis()): Boolean =
        now - MmkvManager.decodeSettingsLong(PREF_LAST_AUTO_CHECK, 0L) >= AppConfig.APP_UPDATE_CHECK_INTERVAL_MS

    fun markAutoChecked(now: Long = System.currentTimeMillis()) {
        MmkvManager.encodeSettings(PREF_LAST_AUTO_CHECK, now)
    }

    suspend fun checkForUpdate(includePreRelease: Boolean = false): CheckUpdateResult = withContext(Dispatchers.IO) {
        // GitHub (API and asset hosts) is often slow or blocked in Russia; the own mirror is tried
        // first. Pre-releases and F-Droid builds are not mirrored and keep using GitHub.
        if (!includePreRelease && !BuildConfig.APPLICATION_ID.contains("fdroid", ignoreCase = true)) {
            checkMirror()?.let { return@withContext it }
        }

        val url = if (includePreRelease) {
            AppConfig.APP_API_URL
        } else {
            AppConfig.APP_API_URL.concatUrl("latest")
        }

        val proxyUsername = SettingsManager.getSocksUsername()
        val proxyPassword = SettingsManager.getSocksPassword()

        var response = HttpUtil.getUrlContent(
            UrlContentRequest(
                url = url,
                timeout = 5000
            )
        )
        if (response.isNullOrEmpty()) {
            val httpPort = SettingsManager.getHttpPort()
            response = HttpUtil.getUrlContent(
                UrlContentRequest(
                    url = url,
                    timeout = 5000,
                    httpPort = httpPort,
                    proxyUsername = proxyUsername,
                    proxyPassword = proxyPassword
                )
            )
                ?: throw IllegalStateException("Failed to get response")
        }

        val latestRelease = if (includePreRelease) {
            JsonUtil.fromJsonSafe(response, Array<GitHubRelease>::class.java)
                ?.firstOrNull()
                ?: throw IllegalStateException("No pre-release found")
        } else {
            JsonUtil.fromJsonSafe(response, GitHubRelease::class.java)
        }
        if (latestRelease == null) {
            return@withContext CheckUpdateResult(hasUpdate = false)
        }

        val latestVersion = latestRelease.tagName.removePrefix("v")
        LogUtil.i(
            AppConfig.TAG,
            "Found new version: $latestVersion (current: ${BuildConfig.VERSION_NAME})"
        )

        return@withContext if (compareVersions(latestVersion, BuildConfig.VERSION_NAME) > 0) {
            val downloadUrl = getDownloadUrl(latestRelease, Build.SUPPORTED_ABIS[0])
            CheckUpdateResult(
                hasUpdate = true,
                latestVersion = latestVersion,
                releaseNotes = latestRelease.body,
                downloadUrl = downloadUrl,
                isPreRelease = latestRelease.prerelease
            )
        } else {
            CheckUpdateResult(hasUpdate = false)
        }
    }

    /** Null when the mirror is unreachable or answers garbage: the caller falls back to GitHub. */
    private fun checkMirror(): CheckUpdateResult? {
        val text = try {
            HttpUtil.getUrlContent(UrlContentRequest(url = AppConfig.APP_MIRROR_VERSION_URL, timeout = 5000))
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "Update check: mirror version request failed", e)
            null
        }
        val latestVersion = parseMirrorVersion(text)
        if (latestVersion == null) {
            LogUtil.w(AppConfig.TAG, "Update check: mirror version unavailable, falling back to GitHub")
            return null
        }
        LogUtil.i(AppConfig.TAG, "Mirror version: $latestVersion (current: ${BuildConfig.VERSION_NAME})")
        return if (compareVersions(latestVersion, BuildConfig.VERSION_NAME) > 0) {
            CheckUpdateResult(
                hasUpdate = true,
                latestVersion = latestVersion,
                downloadUrl = AppConfig.APP_MIRROR_DOWNLOAD_URL,
            )
        } else {
            CheckUpdateResult(hasUpdate = false)
        }
    }

    private fun compareVersions(version1: String, version2: String): Int {
        val v1 = version1.split(".")
        val v2 = version2.split(".")

        for (i in 0 until maxOf(v1.size, v2.size)) {
            val num1 = if (i < v1.size) v1[i].toInt() else 0
            val num2 = if (i < v2.size) v2[i].toInt() else 0
            if (num1 != num2) return num1 - num2
        }
        return 0
    }

    private fun getDownloadUrl(release: GitHubRelease, abi: String): String {
        val fDroid = "fdroid"

        val assetsByAbi = release.assets.filter {
            (it.name.contains(abi, true))
        }

        val asset = if (BuildConfig.APPLICATION_ID.contains(fDroid, ignoreCase = true)) {
            assetsByAbi.firstOrNull { it.name.contains(fDroid) }
        } else {
            assetsByAbi.firstOrNull { !it.name.contains(fDroid) }
        }

        return asset?.browserDownloadUrl
            ?: throw IllegalStateException("No compatible APK found")
    }
}

/**
 * Mirror `version` body («v1.3.3\n») -> «1.3.3». Null for anything that is not a plain dotted
 * version (HTML error page, empty body, «1.0.1-test»), so a bad answer never offers an update.
 */
internal fun parseMirrorVersion(text: String?): String? =
    text?.trim()?.removePrefix("v")?.takeIf { Regex("""\d+(\.\d+){0,3}""").matches(it) }
