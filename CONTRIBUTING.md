# Contributing

For a bug report, include the watch model, Wear OS version, app version and steps to reproduce it. Say whether you use the lightweight companion or full phone app. For playback problems, a track link and whether you were streaming or playing a download are useful.

Do not post session cookies, tokens or account databases. Check logs and screenshots before attaching them.

## Working on the app

Start with [building and installing](docs/building.md). Use a separate package channel for device tests so you don't overwrite a daily-use install.

Keep watch, companion and full phone behavior in mind when changing shared interfaces. Compile the Foss phone variant as well as both watch/companion apps. Run the unit tests and relevant lint checks from the build guide; changes to playback, downloads, signing or the bridge also need the matching runtime checks in [the release checklist](docs/release-checklist.md).

For a UI change, include screenshots from a round display and check larger text. Label preview fixtures as previews. For a playback fix, describe the failure you reproduced and the conditions you tested.

## Upstream changes

Record the upstream commit and the behavior being adapted in [upstream-compatibility.md](docs/upstream-compatibility.md). Upstream's multiplatform modules do not directly replace this fork's Android modules. Keep database changes explicit, preserve existing data, and avoid bringing phone-only native libraries into the watch APK.

PR descriptions should explain what changed and how it was checked. Call out anything that still needs a device test.
