package com.maxrave.simpmusic.wear

// Destructive instrumentation is limited to disposable channels, including the legacy test install.
internal fun isIsolatedTestPackage(packageName: String): Boolean =
    packageName == "io.github.rahyan.simpmusicwear.test.dev" ||
        packageName == "com.maxrave.simpmusic.test.dev"
