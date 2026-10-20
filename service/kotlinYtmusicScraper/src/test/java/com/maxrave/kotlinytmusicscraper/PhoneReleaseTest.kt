package com.maxrave.kotlinytmusicscraper

import com.maxrave.common.ForkReleases
import com.maxrave.kotlinytmusicscraper.models.simpmusic.GithubResponse
import com.maxrave.kotlinytmusicscraper.models.simpmusic.newerPhoneRelease
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PhoneReleaseTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private fun release(tag: String, name: String = "SimpMusic-foss-arm64-v8a-release.apk", draft: Boolean = false,
                        prerelease: Boolean = tag.contains('-'), base: String = ForkReleases.PAGE_URL, state: String = "uploaded"): GithubResponse =
        json.decodeFromString("""{"tag_name":"$tag","draft":$draft,"prerelease":$prerelease,"published_at":"2026-10-19T00:00:00Z",
            "assets":[{"name":"$name","state":"$state","browser_download_url":"$base/download/$tag/$name"}]}""")
    private fun List<GithubResponse>.choose(current: String = "0.3.0-beta.1", flavor: String = "foss", abis: List<String> = listOf("arm64-v8a")) =
        newerPhoneRelease(current, flavor, abis)

    @Test fun watchAndCompanionOnlyReleasesDoNotOfferPhoneUpdates() {
        assertNull(listOf(release("v0.4.0", "SimpMusic-WearOS-0.4.0-arm64-v8a.apk"),
            release("v0.4.0", "SimpMusic-Companion-0.4.0.apk")).choose())
    }

    @Test fun newestCompatibleReleaseWinsRatherThanApiOrder() {
        val releases = listOf(release("v0.3.0-beta.2"), release("v0.3.0-beta.10"), release("v0.3.0-beta.3"))
        assertEquals("v0.3.0-beta.10", releases.choose()?.tagName)
    }

    @Test fun stableInstallsStayOnStableAndBetaInstallsCanReachStable() {
        val releases = listOf(release("v0.4.0-beta.1"), release("v0.3.1"))
        assertEquals("v0.3.1", releases.choose(current = "0.3.0")?.tagName)
        assertEquals("v0.3.0", listOf(release("v0.3.0")).choose()?.tagName)
    }

    @Test fun equalOlderMalformedAndDraftVersionsAreIgnored() {
        assertNull(listOf(release("v0.3.0-beta.1"), release("v0.2.0"), release("latest"), release("v0.5.0", draft = true)).choose())
        assertNull(listOf(release("v0.5.0")).choose(current = "invalid"))
    }

    @Test fun installedFlavorAndAbiMustMatch() {
        val full = release("v0.4.0", "SimpMusic-full-arm64-v8a-release.apk")
        assertNull(listOf(full).choose())
        assertNotNull(listOf(full).choose(flavor = "full"))
        assertNull(listOf(full).choose(flavor = "full", abis = listOf("armeabi-v7a")))
        assertNotNull(listOf(release("v0.4.0", "SimpMusic-foss-universal-release.apk")).choose(abis = listOf("armeabi-v7a")))
        assertNull(listOf(full).choose(flavor = "unknown"))
    }

    @Test fun foreignDownloadLocationsAndIncompleteUploadsAreRejected() {
        assertNull(listOf(release("v0.4.0", base = "https://github.com/maxrave-dev/SimpMusic/releases"),
            release("v0.5.0", state = "starter")).choose())
    }
}
