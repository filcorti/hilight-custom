package com.hilight.studio

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal data class GitHubRelease(
    val tagName: String,
    val versionName: String,
    val pageUrl: String,
)

internal sealed interface UpdateCheckResult {
    data class Available(val release: GitHubRelease) : UpdateCheckResult
    data class Current(val latestVersionName: String) : UpdateCheckResult
    data object NoPublishedRelease : UpdateCheckResult
    data object Failed : UpdateCheckResult
}

/**
 * Manual update lookup for the project's public GitHub releases.
 *
 * GitHub's `releases/latest` endpoint excludes prereleases, and HiLight builds are
 * published as experimental prereleases. The list endpoint includes them, so we resolve
 * the greatest semantic version ourselves and ignore drafts and non-version tags.
 */
internal object GitHubUpdateChecker {
    private const val RELEASES_API =
        "https://api.github.com/repos/filcorti/hilight-custom/releases"
    private const val TAGS_API =
        "https://api.github.com/repos/filcorti/hilight-custom/tags"
    internal const val RELEASES_PAGE =
        "https://github.com/filcorti/hilight-custom/releases/tag/"

    private fun logD(tag: String, msg: String) {
        runCatching { Log.d(tag, msg) }.onFailure { println("[$tag] $msg") }
    }

    private fun logE(tag: String, msg: String, tr: Throwable? = null) {
        runCatching {
            if (tr != null) Log.e(tag, msg, tr) else Log.e(tag, msg)
        }.onFailure {
            println("[$tag] ERROR: $msg")
            tr?.printStackTrace()
        }
    }

    private fun logW(tag: String, msg: String) {
        runCatching { Log.w(tag, msg) }.onFailure { println("[$tag] WARNING: $msg") }
    }

    private fun fetchJson(apiUrl: String, currentVersionName: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(apiUrl)
            logD("UpdateChecker", "Querying URL: $url")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
            connection.setRequestProperty("User-Agent", "HiLight-Studio/$currentVersionName")

            val responseCode = connection.responseCode
            logD("UpdateChecker", "HTTP Response Code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else null
        } catch (e: Exception) {
            logE("GitHubUpdateChecker", "Network error fetching $apiUrl", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    fun check(currentVersionName: String): UpdateCheckResult {
        val releasesResponse = fetchJson(RELEASES_API, currentVersionName)
        if (releasesResponse != null) {
            val res = resolve(currentVersionName, releasesResponse)
            if (res !is UpdateCheckResult.NoPublishedRelease) return res
        }
        val tagsResponse = fetchJson(TAGS_API, currentVersionName) ?: return UpdateCheckResult.Failed
        return resolve(currentVersionName, tagsResponse)
    }

    internal fun resolve(
        currentVersionName: String,
        response: String,
    ): UpdateCheckResult = try {
        logD("UpdateChecker", "Parsing local version: $currentVersionName")
        val current = ReleaseVersion.parse(currentVersionName)
        if (current == null) {
            logW("UpdateChecker", "Failed to parse local version: $currentVersionName")
            return UpdateCheckResult.Failed
        }
        logD("UpdateChecker", "Parsed local version: $current")

        val releasesArray = when {
            response.trimStart().startsWith("[") -> JSONArray(response)
            response.trimStart().startsWith("{") -> JSONArray().put(JSONObject(response))
            else -> {
                logW("UpdateChecker", "Response is not valid JSON array or object")
                return UpdateCheckResult.Failed
            }
        }

        if (releasesArray.length() == 0) {
            logD("UpdateChecker", "Releases array is empty")
            return UpdateCheckResult.NoPublishedRelease
        }

        val candidates = mutableListOf<ResolvedRelease>()

        for (i in 0 until releasesArray.length()) {
            val entry = releasesArray.optJSONObject(i) ?: continue
            if (entry.optBoolean("draft", false)) continue

            val tag = entry.optString("tag_name", entry.optString("name", ""))
            val version = ReleaseVersion.parse(tag)
            if (version == null) {
                logD("UpdateChecker", "Skipping release with unparsable tag: $tag")
                continue
            }
            val htmlUrl = entry.optString("html_url", RELEASES_PAGE + tag)

            logD("UpdateChecker", "Found candidate release: tag=$tag, parsedVersion=$version")
            candidates.add(ResolvedRelease(tag = tag, version = version, pageUrl = htmlUrl))
        }

        val latest = candidates.maxByOrNull { it.version }
        if (latest == null) {
            logD("UpdateChecker", "No valid release candidates found")
            return UpdateCheckResult.NoPublishedRelease
        }

        logD("UpdateChecker", "Latest remote version: ${latest.version} (tag: ${latest.tag}) vs Local: $current")

        if (latest.version > current) {
            logD("UpdateChecker", "New update available: ${latest.version} > $current")
            UpdateCheckResult.Available(
                GitHubRelease(
                    tagName = latest.tag,
                    versionName = latest.version.displayName,
                    pageUrl = latest.pageUrl,
                )
            )
        } else {
            logD("UpdateChecker", "App is up to date or local is newer: ${latest.version} <= $current")
            UpdateCheckResult.Current(latest.version.displayName)
        }
    } catch (e: Exception) {
        logE("GitHubUpdateChecker", "Error parsing response", e)
        UpdateCheckResult.Failed
    }

    private data class ResolvedRelease(
        val tag: String,
        val version: ReleaseVersion,
        val pageUrl: String,
    )

    private data class ReleaseVersion(
        val major: Int,
        val minor: Int,
        val patch: Int,
    ) : Comparable<ReleaseVersion> {
        val displayName: String = "$major.$minor.$patch"

        override fun compareTo(other: ReleaseVersion): Int =
            compareValuesBy(this, other, ReleaseVersion::major, ReleaseVersion::minor, ReleaseVersion::patch)

        companion object {
            private val VERSION_TAG =
                Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+][0-9A-Za-z.-]+)?$")

            fun parse(value: String): ReleaseVersion? {
                val match = VERSION_TAG.matchEntire(value.trim()) ?: return null
                return ReleaseVersion(
                    major = match.groupValues[1].toIntOrNull() ?: return null,
                    minor = match.groupValues[2].toIntOrNull() ?: return null,
                    patch = match.groupValues[3].toIntOrNull() ?: return null,
                )
            }
        }
    }
}
