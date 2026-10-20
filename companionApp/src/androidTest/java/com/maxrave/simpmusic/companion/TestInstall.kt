package com.maxrave.simpmusic.companion

// Playback instrumentation must never target a normal public or development install.
internal fun isIsolatedTestPackage(packageName: String): Boolean =
    packageName == "io.github.rahyan.simpmusicwear.test.dev" ||
        packageName == "com.maxrave.simpmusic.test.dev"
