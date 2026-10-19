plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}
kotlin { jvmToolchain(21) }
android {
    namespace = "com.maxrave.nativemedia"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
}
dependencies {
    coreLibraryDesugaring(libs.desugaring)
    implementation(project(":kotlinYtmusicScraper"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ffmpeg.kit.audio)
    implementation(libs.ytdlp.android)
}
