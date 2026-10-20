package com.maxrave.kotlinytmusicscraper.models.simpmusic

import com.maxrave.common.ForkReleases
import com.maxrave.common.ReleaseVersion

fun List<GithubResponse>.newerPhoneRelease(currentVersion: String, flavor: String, supportedAbis: List<String>): GithubResponse? {
    val current = ReleaseVersion.parse(currentVersion) ?: return null
    if (flavor !in setOf("full", "foss") || supportedAbis.isEmpty()) return null
    return mapNotNull { release ->
        val version = ReleaseVersion.parse(release.tagName ?: return@mapNotNull null) ?: return@mapNotNull null
        if (release.draft != false || release.prerelease == null || version <= current) return@mapNotNull null
        if (!current.isPrerelease && (release.prerelease || version.isPrerelease)) return@mapNotNull null
        val compatible = release.assets.orEmpty().filterNotNull().any { asset ->
            val name = asset.name ?: return@any false
            name.startsWith("SimpMusic-$flavor-") && name.endsWith(".apk") && asset.state == "uploaded" &&
                (name.contains("-universal-") || supportedAbis.any { name.contains("-$it-") }) &&
                asset.browserDownloadUrl?.startsWith("${ForkReleases.PAGE_URL}/download/${release.tagName}/") == true
        }
        if (compatible) version to release else null
    }.maxByOrNull { it.first }?.second
}
