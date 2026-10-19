# Maintainer release checklist

Run these checks against the source revision and APKs being released. Record the device model, Android version, ABI and artifact hash with device results.

Status for `0.3.0-beta.1` (version code 41), updated October 19, 2026.

## Build and tests

- [x] Run the domain, common, scraper, watch, companion and full-phone Foss unit suites: 88 tests pass.
- [x] Run the release-tool suite: 13 tests pass, including artifact integrity and draft-only creation.
- [x] Compile both Foss and Full phone Kotlin variants.
- [x] Build watch and companion debug APKs, instrumentation APKs and minified release APKs.
- [x] Run watch and companion debug/release lint; review remaining warnings.
- [x] Exercise the drawer, scrolling, rotary input and volume feedback in the emulator interaction fixture.
- [x] Run the isolated watch emulator suite: eight tests pass, including idle service reconnect, offline playlist persistence/safety, drawer input and progress updates.
- [ ] Run GitHub-hosted CI on the exact source revision being released.

| App | Debug lint warnings | Release lint warnings |
| --- | ---: | ---: |
| Watch | 24 | 23 |
| Companion | 14 | 11 |

There are no lint errors or fatal issues. Emulator input checks supplement the physical-device checks below.

## Packaging and signing

- [x] Verify ARMv7, ARM64 and universal watch packaging, plus the matching companion.
- [x] Check watch native payloads: retain V8; exclude phone-only Python and FFmpeg libraries.
- [x] Verify 64-bit ELF and final APK ZIP alignment for 16 KB pages.
- [x] Confirm release APKs are non-debuggable and exclude preview activities.
- [x] Verify session-containing app data is excluded from Android backup and device transfer.
- [x] Exercise package/version/certificate checks, checksums and rejection of test packages, debug keys, mismatched pairs and stale output directories.
- [ ] Sign final public APKs with the retained production key and confirm watch/phone package names and certificates match.
- [ ] Back up the signing key and credentials securely; configure repository signing secrets.
- [ ] Verify hashes and the release manifest against the exact APKs to be uploaded.

## Physical-device validation

Use minified APKs signed with the same key on the watch and phone.

- [ ] Stream as a guest and with a signed-in account. Check seeking, buffering, retries and the transition at track completion.
- [ ] Check drawer dragging in both directions, settling, Back, and scrolling lyrics/queue without accidental dismissal.
- [ ] Check real bezel volume steps, temporary volume feedback and rotary list scrolling.
- [ ] Check the player and drawer on a smaller round display and with larger text.
- [ ] Play, seek and change tracks in a fully downloaded queue with Wi-Fi and Bluetooth disabled.
- [ ] Refresh a saved offline playlist while the app is closed. Check charging/network constraints and that newly downloaded tracks play with radios disabled.
- [ ] Check optional playlist cleanup after successful and failed refreshes. Manual downloads, the pinned queue and currently queued music must remain available.
- [ ] Change the global download policy and retry an older queue request; verify the newer policy is retained, including after relaunch.
- [ ] Reconnect Bluetooth headphones, change audio output and recover from Wi-Fi/Bluetooth proxy transitions.
- [ ] Start watch playback from the phone with the watch app cold or in the background. Confirm playback and its media session remain active.
- [ ] Sign out, relaunch, sign back in and refresh the session on both devices.
- [ ] Check stale remote commands, queue ordering and library synchronization with local watch changes.
- [ ] Check restricted/unavailable media and useful error/retry behavior.
- [ ] Measure cold start, player frame behavior and memory use.
- [ ] For updates, install over the previous supported version with the same signing key and verify settings, library and downloads are preserved.

## Publication

- [x] Review installation instructions, APK selection, screenshots and local documentation links.
- [x] Review release notes for user-facing features, fixes and known limitations.
- [ ] Match the source tag to the version in `gradle/libs.versions.toml`.
- [ ] Attach matching APKs, `SHA256SUMS` and `release-manifest.json` to the release.
- [ ] Confirm uploaded assets contain no test-only builds, keys, session exports or account data.
- [ ] Publish beta versions as GitHub prereleases.

Build and signing commands are in [building.md](building.md). Release notes describe the app for users; test evidence and outstanding checks belong in maintainer records.
