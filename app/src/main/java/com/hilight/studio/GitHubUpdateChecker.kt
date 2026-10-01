package com.hilight.studio

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
 * GitHub's `releases/latest` endpoint excludes prereleases, and every HiLight build is intentionally
 * published as an experimental prerelease. The list endpoint includes them, so we resolve the
 * greatest semantic version ourselves and ignore drafts and non-version tags.
 */
internal object GitHubUpdateChecker {
    private const val RELEASES_API =
        "https://api.github.com/repos/filcorti/hilight-custom/releases/latest"
    private const val RELEASES_PAGE =
        "https://github.com/filcorti/hilight-custom/releases/tag/"

    fun check(currentVersionName: String): UpdateCheckResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(RELEASES_API).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
            connection.setRequestProperty("User-Agent", "HiLight-Studio/$currentVersionName")

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                connection.errorStream?.close()
                UpdateCheckResult.Failed
            } else {
                val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                    it.readText()
                }
                resolve(currentVersionName, response)
            }
        } catch (_: Exception) {
            UpdateCheckResult.Failed
        } finally {
            connection?.disconnect()
        }
    }

    internal fun resolve(
        currentVersionName: String,
        response: String,
    ): UpdateCheckResult = try {
        val current = ReleaseVersion.parse(currentVersionName)
            ?: return UpdateCheckResult.Failed
        val entry = org.json.JSONObject(response)
        
        if (entry.optBoolean("draft", false)) return UpdateCheckResult.NoPublishedRelease
        val tag = entry.optString("tag_name")
        val version = ReleaseVersion.parse(tag) ?: return UpdateCheckResult.NoPublishedRelease
        
        val latest = ResolvedRelease(tag, version)

        if (latest.version > current) {
            UpdateCheckResult.Available(
                GitHubRelease(
                    tagName = latest.tag,
                    versionName = latest.version.displayName,
                    pageUrl = entry.optString("html_url", RELEASES_PAGE + latest.tag),
                )
            )
        } else {
            UpdateCheckResult.Current(latest.version.displayName)
        }
    } catch (_: Exception) {
        UpdateCheckResult.Failed
    }

    private data class ResolvedRelease(
        val tag: String,
        val version: ReleaseVersion,
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
