# Building and installing

## Setup

Use JDK 21 and Android SDK platform 36. Install Android SDK Build Tools 36.0.0 and platform-tools through Android Studio's SDK Manager. Gradle uses Java 17 bytecode; the build itself runs on JDK 21.

Clone with submodules:

```sh
git clone --recurse-submodules https://github.com/Rahyan/SimpMusic-WearOS.git
cd SimpMusic-WearOS
```

For an existing checkout, run `git submodule update --init --recursive`. `MediaServiceCore` and its nested shared modules are needed. The checked-out modules take precedence over similarly named folders beside the project.

Set `JAVA_HOME` to your JDK and `ANDROID_HOME` to your SDK. Android Studio can create `local.properties` with `sdk.dir` instead. Keep that file local. Debug builds do not require Sentry credentials or a release keystore.

The commands below use the Unix wrapper. In PowerShell use `./gradlew.bat`. For a machine with less free RAM, add `--max-workers=1 -Dorg.gradle.jvmargs=-Xmx2048m`.

## Debug APKs

```sh
./gradlew --no-daemon :wearApp:assembleDebug :companionApp:assembleDebug
```

| App | Output |
| --- | --- |
| Watch | `wearApp/build/outputs/apk/debug/wearApp-debug.apk` |
| Companion | `companionApp/build/outputs/apk/debug/companionApp-debug.apk` |

Build both on the same machine so they share the debug signing key. Install with the device IDs shown by `adb devices -l`:

```sh
adb -s <watch-id> install -r wearApp/build/outputs/apk/debug/wearApp-debug.apk
adb -s <phone-id> install -r companionApp/build/outputs/apk/debug/companionApp-debug.apk
```

`-r` preserves app data when the existing package has the same signing key. A signature mismatch requires the original key to update without losing data. Uninstalling removes the app's local data.

The optional full phone app is built with:

```sh
./gradlew --no-daemon :app:assembleFossDebug
```

Its split and universal APKs appear under `app/build/outputs/apk/foss/debug/`. It replaces the companion on the same package channel.

## Separate test installs

Release packages use `io.github.rahyan.simpmusicwear`; debug adds `.dev`. This fork can be installed alongside upstream SimpMusic. The watch and phone must share both the final package name and signing certificate for Wear Data Layer messages.

To keep a debug test install separate from an existing `.dev` app:

```sh
./gradlew --no-daemon \
  -Psimpmusic.applicationId=io.github.rahyan.simpmusicwear.test \
  :wearApp:assembleDebug :companionApp:assembleDebug
```

Both outputs then install as `io.github.rahyan.simpmusicwear.test.dev`. The shared `simpmusic.applicationId` property also applies to the optional full phone app. A release build has no `.dev` suffix; use the complete package name you want. The older module-specific `simpmusic.wear.applicationId` and `simpmusic.companion.applicationId` overrides remain supported and take precedence for their respective modules.

Changing the package name creates a separate install. It does not carry over an older package's sign-in, library or downloads. Future updates must retain the public package name and signing key. Internal Kotlin namespaces remain separate from this install identity.

For a smaller debug APK, select one ABI with `-Psimpmusic.wear.abis=armeabi-v7a` or `-Psimpmusic.wear.abis=arm64-v8a`. Check the device with `adb -s <watch-id> shell getprop ro.product.cpu.abilist` rather than guessing from its CPU. Emulator ABIs can also be selected.

## Tests and lint

```sh
./gradlew --no-daemon --no-configuration-cache \
  :domain:testDebugUnitTest :common:testDebugUnitTest \
  :kotlinYtmusicScraper:testDebugUnitTest :wearApp:testDebugUnitTest \
  :companionApp:testDebugUnitTest :app:testFossDebugUnitTest \
  :app:compileFossDebugKotlin \
  :wearApp:lintDebug :wearApp:lintRelease \
  :companionApp:lintDebug :companionApp:lintRelease
```

Unit results are under each module's `build/test-results/`; lint reports are under `build/reports/`. A green build does not cover the hardware checks in [the release checklist](release-checklist.md).

Instrumentation includes opt-in tests that change playback, queue or volume. Their source documents the required arguments and isolated package checks. Do not run these against a daily-use install. `PhysicalInteractionTest` also supports `interactionPreview=true` for drawer and rotary input checks using the debug preview activity, without music or an account. Real bezel behavior still needs a physical watch.

## Minified releases

```sh
./gradlew --no-daemon --no-configuration-cache \
  :wearApp:clean :companionApp:clean \
  :wearApp:assembleRelease :companionApp:assembleRelease
```

These APKs are unsigned. Watch release builds include ARMv7 and ARM64 by default. Python/FFmpeg phone export libraries are kept in `:nativeMedia`; they must not appear in the watch APK. V8 remains for the watch's extractor fallback.

Use Python 3 and the SDK's `aapt2` to check the watch artifact:

```sh
python3 verify_wear_apk.py wearApp/build/outputs/apk/release/wearApp-release-unsigned.apk \
  --release --aapt2 "$ANDROID_HOME/build-tools/36.0.0/aapt2"
```

The check rejects phone native payloads, missing V8, incompatible 64-bit ELF alignment, excessive APK padding and a release manifest containing the debug preview activity. Emulator builds using the old x86_64 V8 library are not 16 KB-compatible release candidates.

On Linux, `bash ./build_wear_release.sh` builds, aligns, signs and verifies matching watch/companion APKs. It requires Python 3.11+, `ANDROID_HOME`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and a keystore at `./simpmusic.jks` or `KEYSTORE_PATH`. `KEY_PASSWORD` defaults to the store password. `APPLICATION_ID` overrides both release package names; `WEAR_ABIS` overrides the watch architectures. Signing tools default to Build Tools 36.0.0; `BUILD_TOOLS_VERSION` can select another installed version. Use environment variables for passwords, not command-line arguments.

Signed outputs go to `build/release/`, or `OUTPUT_DIR`. Choose an empty directory for each build so older APKs cannot be mixed into the result. The helper produces a complete watch APK for each included ARM ABI, an ARM universal watch APK when both ABIs are present, the companion, `SHA256SUMS` and `release-manifest.json`. Each APK is independently installable. Smaller watch files come from the same minified build with the other ABI's native libraries removed, then are aligned, signed and checked again.

Packaging checks matching package/version/certificate, non-debuggable manifests, native payloads and final 16 KB alignment. Its manifest also records the source commit and whether the checkout has uncommitted changes. It rejects Android debug keys and test package names for public packaging. Keep the production signing key and its password backed up securely between releases; a newly generated key cannot update an existing install with its data intact. Do not publish outputs produced with the local-only `--allow-test-key` option.

The public package and signing certificate fingerprint are recorded in [release-signing.json](../release-signing.json). Packaging and independent release verification reject a different signer. That file contains public verification data only; keep the private keystore and its password outside Git.

After an unsigned Gradle build, the same packaging step works on Windows using `python package_wear_release.py`:

```sh
python3 package_wear_release.py \
  --wear wearApp/build/outputs/apk/release/wearApp-release-unsigned.apk \
  --companion companionApp/build/outputs/apk/release/companionApp-release-unsigned.apk \
  --build-tools "$ANDROID_HOME/build-tools/36.0.0" \
  --keystore "$KEYSTORE_PATH" --alias "$KEY_ALIAS" --output build/release
```

Set `KEYSTORE_PASSWORD` and `KEY_PASSWORD` in the process environment first. In PowerShell, replace the Unix continuations and environment-variable syntax with their PowerShell equivalents, or place the command on one line. Validate downloads with `sha256sum -c SHA256SUMS` on Linux or `Get-FileHash <apk> -Algorithm SHA256` on Windows. Compare the APK's hash to its entry in `SHA256SUMS`.

The unsigned GitHub workflow builds and checks the apps. The release workflow signs watch/companion artifacts on version tags or manual dispatch; it needs the signing secrets configured by the maintainer. Optional full phone APKs can be enabled with the `full_phone` dispatch input.

## Preparing a GitHub release

App versions are set in `gradle/libs.versions.toml`. Release notes belong under `docs/releases/`; see [0.3.0-beta.1](releases/0.3.0-beta.1.md) for the install pair and upgrade guidance.

1. Complete the [maintainer release checklist](release-checklist.md) against the final minified build, and run the GitHub build workflow on the source being released.
2. Configure `BASE_64_SIGNING_KEY`, `KEY_STORE_PASSWORD`, `ALIAS` and optional `KEY_PASSWORD` repository secrets. Use a retained production key; do not substitute a local debug key. Keep an independent, secure backup.
3. Build signed artifacts with the workflow or helper. Confirm the tag/version/code, package and certificate in `release-manifest.json`. Keep all APKs from one build together.
4. A version-tag run creates an unpublished GitHub prerelease draft with the four APKs, `SHA256SUMS`, manifest and release notes. On manual dispatch, enable `create_draft` and run against the commit referenced by the existing version tag. The workflow independently checks all assets, signatures, source identity and native payloads before creating the draft. It refuses test-only or dirty-source builds and never overwrites an existing release or draft.
5. Review the draft and publish it when the release checklist is complete. The workflow never publishes automatically.

Run the network-free release-tool checks with `python3 -m unittest discover -s tests`. To verify a signed set independently, use `python3 verify_release.py build/release --version <version> --source-revision <commit> --build-tools <sdk-build-tools-directory>`.
