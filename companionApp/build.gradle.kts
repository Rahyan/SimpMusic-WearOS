plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

val companionChannelApplicationId =
    providers
        .gradleProperty("simpmusic.companion.applicationId")
        .orElse(providers.gradleProperty("simpmusic.applicationId"))
        .get()

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "com.maxrave.simpmusic.companion"
    compileSdk = 36

    defaultConfig {
        applicationId = companionChannelApplicationId
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = libs.versions.version.code.get().toInt()
        versionName = libs.versions.version.name.get()
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "COMPANION_CHANNEL_APPLICATION_ID", "\"$companionChannelApplicationId\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-companion-dev"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(project(":common"))
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)
    implementation(libs.compose.material3.lib)
    implementation(libs.compose.ui)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.ui.tooling.preview)
    implementation(libs.coroutines.android)
    implementation(libs.play.services.wearable)
    implementation(libs.androidx.webkit)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    debugImplementation(libs.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
