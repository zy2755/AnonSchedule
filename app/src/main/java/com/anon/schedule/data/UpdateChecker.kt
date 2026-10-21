package com.anon.schedule.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub Release 检查。
 *
 * 先用 REST API 取最新正式版（能拿到更新说明）；
 * API 被限流 / 不可用时，退回到 releases/latest 的重定向地址取 tag——这条路径不消耗 API 额度。
 * GitHub 要求请求必须带 User-Agent。
 */
object UpdateChecker {

    private const val API_URL = "https://api.github.com/repos/zy2755/AnonSchedule/releases/latest"
    private const val LATEST_URL = "https://github.com/zy2755/AnonSchedule/releases/latest"
    private const val USER_AGENT = "AnonSchedule-Android"

    data class ReleaseInfo(
        val version: String,
        val name: String,
        val notes: String,
        val pageUrl: String
    )

    sealed interface Result {
        data class Newer(val release: ReleaseInfo) : Result
        data class Latest(val version: String) : Result
        data class Failed(val message: String) : Result
    }

    suspend fun check(currentVersion: String): Result = withContext(Dispatchers.IO) {
        val apiResult = checkViaApi(currentVersion)
        if (apiResult !is Result.Failed) {
            return@withContext apiResult
        }
        val tag = latestTagViaRedirect() ?: return@withContext apiResult
        val version = tag.removePrefix("v").removePrefix("V")
        if (isNewer(version, currentVersion)) {
            Result.Newer(ReleaseInfo(version, tag, "", LATEST_URL))
        } else {
            Result.Latest(currentVersion)
        }
    }

    private fun checkViaApi(currentVersion: String): Result {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", USER_AGENT)
            }
            val code = conn.responseCode
            if (code != 200) {
                val detail = runCatching {
                    conn.errorStream?.bufferedReader()?.use { it.readText() }
                }.getOrNull().orEmpty()
                val apiMessage = runCatching {
                    JSONObject(detail).optString("message")
                }.getOrNull().orEmpty()
                return Result.Failed(
                    when {
                        apiMessage.isNotEmpty() -> apiMessage
                        else -> "GitHub 返回 HTTP $code"
                    }
                )
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name").trim()
            if (tag.isEmpty()) {
                return Result.Failed("未获取到版本号")
            }
            val version = tag.removePrefix("v").removePrefix("V")
            return if (isNewer(version, currentVersion)) {
                Result.Newer(
                    ReleaseInfo(
                        version = version,
                        name = json.optString("name").ifBlank { tag },
                        notes = json.optString("body"),
                        pageUrl = json.optString("html_url").ifBlank { LATEST_URL }
                    )
                )
            } else {
                Result.Latest(currentVersion)
            }
        } catch (e: Exception) {
            return Result.Failed(e.message ?: "网络连接失败")
        } finally {
            conn?.disconnect()
        }
    }

    /** 关掉自动跳转，从 Location 头里取 tag：github.com/.../releases/tag/v1.0.1 */
    private fun latestTagViaRedirect(): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", USER_AGENT)
            }
            val code = conn.responseCode
            val location = conn.getHeaderField("Location")
            if (code in 300..399 && !location.isNullOrBlank() && location.contains("/tag/")) {
                location.substringAfterLast("/tag/").ifBlank { null }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** 逐段比较数字版本号：1.10 > 1.9；解析不出数字的段按 0 处理 */
    private fun isNewer(remote: String, current: String): Boolean {
        val remoteParts = versionParts(remote)
        if (remoteParts.isEmpty()) return false
        val currentParts = versionParts(current)
        for (i in 0 until maxOf(remoteParts.size, currentParts.size)) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return false
    }

    private fun versionParts(version: String): List<Int> =
        version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .split('.', '-', '_', '+')
            .mapNotNull { it.toIntOrNull() }
}
