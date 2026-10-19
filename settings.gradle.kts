pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { setUrl("https://jitpack.io") }
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") }
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
        }
    }
}

// Prefer this checkout. A neighboring workspace must never silently replace checked-in code.
fun moduleRoot(name: String): File {
    val local = File(rootDir, name)
    if (local.isDirectory) return local
    val sibling = File(rootDir, "../$name")
    require(sibling.isDirectory) { "Missing $name. Clone with --recurse-submodules or run git submodule update --init --recursive." }
    return sibling
}

val mediaServiceCore = moduleRoot("MediaServiceCore")
val sharedDir = File(mediaServiceCore, "SharedModules")
val coreDir = moduleRoot("core")
val serviceDir = moduleRoot("service")
val mediaDir = moduleRoot("media")

rootProject.name = "SimpMusic"
include(
    "app",
    "companionApp",
    "wearApp",
    ":common",
    ":data",
    ":domain",
    ":kotlinYtmusicScraper",
    ":nativeMedia",
    ":spotify",
    ":aiService",
    ":lyricsService",
    ":mediaserviceinterfaces",
    ":youtubeapi",
    ":googleapi",
    ":sharedtests",
    ":sharedutils",
    ":j2v8",
    ":media3",
    ":media3-ui",
)
project(":mediaserviceinterfaces").projectDir = File(mediaServiceCore, "mediaserviceinterfaces")
project(":youtubeapi").projectDir = File(mediaServiceCore, "youtubeapi")
project(":googleapi").projectDir = File(mediaServiceCore, "googleapi")
project(":sharedtests").projectDir = File(sharedDir, "sharedtests")
project(":sharedutils").projectDir = File(sharedDir, "sharedutils")
project(":j2v8").projectDir = File(sharedDir, "j2v8")

// core modules
project(":common").projectDir = File(coreDir, "common")
project(":data").projectDir = File(coreDir, "data")
project(":domain").projectDir = File(coreDir, "domain")

// service modules
project(":aiService").projectDir = File(serviceDir, "aiService")
project(":lyricsService").projectDir = File(serviceDir, "lyricsService")
project(":kotlinYtmusicScraper").projectDir = File(serviceDir, "kotlinYtmusicScraper")
project(":nativeMedia").projectDir = File(serviceDir, "nativeMedia")
project(":spotify").projectDir = File(serviceDir, "spotify")

// media modules
project(":media3").projectDir = File(mediaDir, "media3")
project(":media3-ui").projectDir = File(mediaDir, "media3-ui")
