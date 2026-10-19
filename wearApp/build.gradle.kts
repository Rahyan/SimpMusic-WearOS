plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

val wearChannelApplicationId =
    providers.gradleProperty("simpmusic.wear.applicationId").orElse("com.maxrave.simpmusic").get()

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xwhen-guards")
        freeCompilerArgs.add("-Xcontext-parameters")
        freeCompilerArgs.add("-Xmulti-dollar-interpolation")
    }
}

android {
    namespace = "com.maxrave.simpmusic.wear"
    compileSdk = 36

    defaultConfig {
        // Use the same package name as the phone app so Wear Data Layer messages can be delivered.
        // (Data Layer comms are scoped per-app package.)
        applicationId = wearChannelApplicationId
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = libs.versions.version.code.get().toInt()
        versionName = libs.versions.version.name.get()

        // Keep it simple for the first buildable Wear target.
        vectorDrawables.useSupportLibrary = true
        // Explicit ABI overrides also allow 4 KB emulator builds during development.
        providers.gradleProperty("simpmusic.wear.abis").orNull?.let { abis ->
            val requested = abis.split(',').filter { it.isNotBlank() }
            require(requested.isNotEmpty() && requested.all { it in setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64") })
            ndk.abiFilters.addAll(requested)
        }
    }

    buildTypes {
        release {
            // Physical watches use ARM. The retained x86_64 V8 binary requires 4 KB pages;
            // exclude emulator ABIs from releases unless the caller explicitly requests them.
            if (!providers.gradleProperty("simpmusic.wear.abis").isPresent) {
                ndk.abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes +=
            listOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/*.kotlin_module",
            )
    }
}

dependencies {
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    coreLibraryDesugaring(libs.desugaring)

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)

    implementation(libs.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.ui.tooling.preview)
    implementation(libs.compose.material3.lib)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.navigation)
    implementation(libs.wear.input)
    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout)
    implementation(libs.wear.watchface.complications.data.source.ktx)
    implementation(libs.guava)
    // core/common has XML resources referencing Material theme attrs (e.g. ?attr/colorOnPrimary).
    implementation(libs.material)
    // Wear Data Layer (phone-assisted login, etc.)
    implementation(libs.play.services.wearable)
    implementation(libs.media3.common)
    implementation(libs.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)

    // DI (data layer is wired via Koin; dependency is not exposed transitively).
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)

    // Shared app logic. We’ll reuse these and build a Wear-specific UI on top.
    implementation(project(":common"))
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":media3"))

    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.wear.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
