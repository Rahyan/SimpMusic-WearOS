# Maintainer release checklist

Run these checks against the source revision and APKs being released. Record the device model, Android version, ABI and artifact hash with device results.

Status for `0.3.0-beta.1` (version code 41), updated October 20, 2026.

[Published beta](https://github.com/Rahyan/SimpMusic-WearOS/releases/tag/v0.3.0-beta.1). The tag and signed APKs use source commit `87f75f1a7af5fba502c97035e2e50f0aba1020b3`, which passed [GitHub CI](https://github.com/Rahyan/SimpMusic-WearOS/actions/runs/39433896440). Remaining hardware checks below are deferred coverage for this beta.

## Build and tests

- [x] Validate the domain, common, scraper, watch, companion and full-phone Foss unit suites: all 103 regression cases pass in hosted CI, with no failures, errors or skips.
- [x] Run the release-tool suite: 15 tests pass, including artifact integrity, public package identity, retained signing certificate and draft-only creation.
- [x] Compile both Foss and Full phone Kotlin variants.
- [x] Build watch and companion debug APKs, instrumentation APKs and minified release APKs.
- [x] Run watch and companion debug/release lint; review remaining warnings.
- [x] Exercise the drawer, scrolling, rotary input and volume feedback in the emulator interaction fixture.
- [x] Run the isolated watch emulator suite: eight tests pass, including idle service reconnect, offline playlist persistence/safety, drawer input and progress updates.
- [x] Run GitHub-hosted CI on the exact source revision being released.

| App | Debug lint warnings | Release lint warnings |
| --- | ---: | ---: |
| Watch | 24 | 23 |
| Companion | 14 | 11 |

There are no lint errors or fatal issues. Emulator input checks supplement the physical-device checks below.

## Packaging and signing

- [x] Set the watch, companion and optional full phone app to `io.github.rahyan.simpmusicwear`, with `.dev` for debug builds.
- [x] Verify ARMv7, ARM64 and universal watch packaging, plus the matching companion.
- [x] Check watch native payloads: retain V8; exclude phone-only Python and FFmpeg libraries.
- [x] Verify 64-bit ELF and final APK ZIP alignment for 16 KB pages.
- [x] Confirm release APKs are non-debuggable and exclude preview activities.
- [x] Verify session-containing app data is excluded from Android backup and device transfer.
- [x] Exercise package/version/certificate checks, checksums and rejection of test packages, debug keys, mismatched pairs and stale output directories.
- [x] Sign the public APKs from the successful exact-source CI artifacts with the retained production key and confirm watch/phone package names and certificates match.
- [x] Verify an independent encrypted signing-key backup and save its recovery password separately.
- Hosted signing is optional. Configure repository signing secrets and `ENABLE_HOSTED_RELEASE_SIGNING` only when enabling that workflow; local signing keeps the key on the maintainer's machine.
- [x] Verify hashes and the release manifest against the four locally packaged APKs, then compare all six uploaded asset digests with the local files.

## Physical-device validation

Use minified APKs signed with the same key on the watch and phone.

Completed checks used minified builds on a Galaxy Watch4 Classic (API 34, ARMv7) paired with a Galaxy S23 (API 34). Earlier checks used an isolated package. The signed public pair has also passed sign-in, synchronization, a three-minute streaming run through track completion, consecutive one-second player updates and companion controls. A ready offline queue and its files survived an in-place signed candidate reinstall. Fresh multi-track playback with both radios disabled remains unverified: the latest attempt stopped before playback because Wi-Fi stayed enabled.

- [x] Search and stream as a guest, seek forward and continue to the next queued track without a playback error.
- [x] Sustain signed-in streaming for three minutes and continue into the next track without a playback error.
- [ ] Exercise buffering and failed-stream retry with a signed-in account.
- [x] Restart a finished finite queue with the normal watch and companion Play buttons.
- [x] Check consecutive one-second updates on the watch and companion, including the companion slider.
- [x] Check held drawer drags in both directions, settling, Back and tab changes on the watch.
- [ ] Scroll long synced lyrics and long queues without accidental drawer dismissal.
- [x] Scroll the public build's 51-track queue without dismissing the drawer or changing its order; captured excerpts cover the first entries, not the whole list.
- [x] Confirm real bezel volume changes by one hardware step per click.
- [x] Confirm the volume overlay appears when turning the physical bezel.
- [ ] Check temporary volume feedback restoration and rotary list scrolling on hardware.
- [x] Check the player and drawer with larger system text, including lyric display controls.
- [x] Open another song from a retained search screen after scrolling song details; each new selection starts at the top.
- [ ] Check a smaller physical round display; emulator coverage supplements this check.
- [x] Reopen a stopped downloaded queue and play after a late seek with Wi-Fi and Bluetooth disabled.
- [ ] Select a fresh offline queue and change between multiple downloaded tracks with both radios disabled. Deferred: the automated precondition failed because Wi-Fi stayed enabled.
- [x] Complete a real pinned-queue download requested from the phone while the watch UI is closed.
- [ ] Refresh a saved offline playlist while the app is closed. Check charging/network constraints and that newly downloaded tracks play with radios disabled.
- [ ] Check optional playlist cleanup after successful and failed refreshes. Manual downloads, the pinned queue and currently queued music must remain available.
- [ ] Change the global download policy and retry an older queue request; verify the newer policy is retained, including after relaunch.
- [ ] Deferred optional coverage: reconnect Bluetooth headphones and change audio output. Check recovery from Wi-Fi/Bluetooth proxy transitions.
- [x] Start watch playback from the phone with the watch UI closed and confirm its media session remains active.
- [x] Stop the watch process and all its services without force-stopping the package, then start its saved offline queue from the companion with the watch UI closed. A new process starts and playback advances.
- [x] Sign out on the watch, confirm Guest mode after relaunch, then restore the session from the companion with an applied acknowledgement.
- [x] Complete fresh Google sign-in and acknowledge session synchronization on the public watch/companion package pair.
- [x] Clear the saved sessions on the phone and selected watch through combined sign-out, confirm Guest mode and persistence after phone restart, and retain downloaded music. This clears the apps' sessions; it does not revoke the Google account's sessions elsewhere.
- [ ] Check stale remote commands, queue ordering and library synchronization with local watch changes.
- [ ] Check restricted/unavailable media and useful error/retry behavior.
- [ ] Measure watch cold start, player frame behavior and memory use. The companion's three measured cold activity launches were 154–158 ms; that does not measure watch performance.
- [x] Update the isolated test apps in place and retain their session and verified offline download.
- [x] Update the signed public candidates in place, retain session/settings, and verify the ready offline queue and completed download files survive reinstall.
- [ ] Future releases: install over the last publicly supported version with its retained signing key and verify settings, library and downloads. There is no published predecessor for the new package yet.

The new fork package is a separate install from older packages. Their data is not transferred automatically. Candidate-to-candidate update checks used the retained signing key and version code 41; they do not replace testing upgrades from a published predecessor when one exists.

## Publication

- [x] Review installation instructions, APK selection, screenshots and local documentation links.
- [x] Review release notes for user-facing features, fixes and known limitations.
- [x] Match the source tag to the version in `gradle/libs.versions.toml`.
- [x] Attach matching APKs, `SHA256SUMS` and `release-manifest.json` to the release.
- [x] Confirm uploaded assets contain no test-only builds, keys, session exports or account data.
- [x] Publish beta versions as GitHub prereleases.

Build and signing commands are in [building.md](building.md). Release notes describe the app for users; test evidence and outstanding checks belong in maintainer records.
