# Upstream compatibility

Reviewed October 18, 2026.

This fork keeps SimpMusic's Android codebase for the Wear app, the small companion and the optional full phone app. Upstream now uses a multiplatform app and a separate core repository. Keeping up means reviewing both repositories and adapting changes to the Android interfaces used here.

The fork is not fully merged with upstream. The table below records what was compared, and the next section records what was actually brought over.

## Compared revisions

| Repository/ref | Revision |
| --- | --- |
| [SimpMusic main](https://github.com/maxrave-dev/SimpMusic) | `f037043d714450bac5aaac2f9dbf77e17b019303` |
| SimpMusic dev | `4430fd14` |
| [Core pinned by main](https://github.com/maxrave-dev/core) | `b9f4ceac52e7c25cb059ea78141784dc164e0161` |
| Core pinned by dev | `2b57079891309fc023a62dbcbb430dae3b7096a2` |

Both application refs and their core pins were fetched directly from GitHub during this review. The current core branch is `multiplatform`.

## Adapted fixes

| Upstream change | What changed here |
| --- | --- |
| Core `5d3fb4c`: publish paused seeks | ExoPlayer position discontinuities reach the domain listener, and paused seeks publish their position immediately. Both playback apps use this handler. |
| Core `d519c3b`: audio-only downloads | Video thumbnail work and downloads run only when video is enabled. Fully downloaded audio can play without a downloaded video. |
| Core `1e1cacd`: session activity at creation | The media service creates its launch pending intent before an activity binds. |
| Core `25bed8b`: playlist entry identity and continuation | Playlist-scoped setVideoIds, bare/VL identity compatibility, modern and legacy continuation parsing, and complete page walks. Failed or repeated continuations cannot report a partial sync as complete. |
| Core `c53896f` / `8f53f61`: shuffle insertion and movement | Preserve the shuffled sequence and played prefix, protect explicit Play next entries, and remap moved or removed indices in the ExoPlayer adapter. |
| Core `8c849cd`: rotating diagnostics | Private rotating files, bounded background writes, release debug suppression and redaction of credentials, structured payloads and signed URLs. Vendor logging still needs its own review. |
| Core `b9f4cea`: radio queue policy and history trimming | Explain why Add to queue is unavailable for radio/endless queues. Keep Play next available. Trim played history in batches while preserving duplicate track IDs, the current index and the queue mirror. |
| Core `0a84b19`: failed downloads must not report completion | Reject bad HTTP status, incomplete byte ranges, empty or truncated data, and mismatches with any known HTTP/format length. Preserve cancellation, deliver the final result even to a busy consumer, and replace a transfer target only after the new file is complete. Export stops on failed transfer or conversion and reports completion after tagging. |

The last row concerns phone file exports. Watch downloads use Media3's cache and separate complete-file verification. The temporary-file guarantee applies to each transfer target, not the whole export/conversion pipeline.

Shared metadata imports also preserve watch-owned downloads, play history and local membership changes during phone synchronization. These adaptations do not change the Room schema or introduce a destructive migration.

## What remains separate

- **Multiplatform storage and UI:** moving them across requires mapping the account, queue, database and platform adapters first. Wear Compose and the existing Android Room schema remain in use.
- **Newer core features:** live-broadcast metadata/download refusal, complete-cache browsing and smart downloads need parser, model or retention-policy work. Comments, replies and dislike status have not been imported.
- **Video and crossfade:** upstream's first-frame callbacks and crossfade are outside this watch player. Its player uses artwork and audio controls.
- **Build upgrades:** JDK 21, Java target 17 and SDK 36 are retained with AGP 8.11.1 and Kotlin 2.2.20. Update Kotlin, AGP, Compose and KSP together, with fresh debug and minified checks.
- **Extractor updates:** NewPipe 0.26.5 and independent lazy fallbacks are integrated. A different client profile is not a permanent fix for YouTube stream restrictions. Sustained playback, retries and restricted media still require runtime checks.
- **Native dependencies:** Python and FFmpeg are isolated in the full phone app's `:nativeMedia` module. Wear retains V8 for its extractor fallback. Release packaging checks ARM payloads and 16 KB support for 64-bit native libraries.

## Validation and future updates

For a shared-code change, run the common, scraper, Wear and companion unit tests and compile the full phone app. Build and lint debug and minified watch/companion variants. Check release manifests, native payloads, matching package names and signing certificates. Debug preview activities must stay out of release APKs. Commands are in [building.md](building.md).

The [maintainer release checklist](release-checklist.md) covers build, signing, device and data-preservation checks. Record validation against the exact source revision and APKs being released.

For the next upstream update, record the application revision and its core pin, identify the behavior being fixed, adapt it to this fork, and add a regression where it protects meaningful behavior. Keep unrelated storage or UI migrations in a separate change so their data and device checks can be reviewed properly.
