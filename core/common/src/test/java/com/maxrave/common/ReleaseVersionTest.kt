package com.maxrave.common

import org.junit.Assert.*
import org.junit.Test

class ReleaseVersionTest {
    private fun version(value: String) = checkNotNull(ReleaseVersion.parse(value))

    @Test fun numericAndPrereleaseVersionsSortCorrectly() {
        val versions = listOf("0.3.0-beta.2", "0.3.0-beta.10", "0.3.0-rc.1", "0.3.0", "0.3.1", "0.10.0", "1.0.0")
        versions.zipWithNext().forEach { (left, right) -> assertTrue("$left < $right", version(left) < version(right)) }
        assertTrue(version("0.3.0-1") < version("0.3.0-alpha"))
    }

    @Test fun prefixesAndBuildMetadataDoNotCreateFalseUpdates() {
        assertEquals(0, version("v0.3.0-beta.1+build.7").compareTo(version("0.3.0-beta.1")))
        assertFalse(version("0.3.0").isPrerelease)
        assertTrue(version("0.3.0-beta.1").isPrerelease)
    }

    @Test fun malformedVersionsFailClosed() {
        listOf("", "latest", "0.3", "01.3.0", "0.3.0-01", "0.3.0/../other", "0.3.0-", "0.3.0+", "999999999999999999999999.0.0")
            .forEach { assertNull(it, ReleaseVersion.parse(it)) }
    }
}
