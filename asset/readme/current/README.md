# Current UI previews

Captured October 18, 2026 from the debug-only preview activities in this checkout:

- `watch-player.png` and `watch-queue.png`: Wear OS API 33 emulator, 454 × 454 pixels, normal text size. These use the production player and queue-row components.
- `companion-player.png` and `companion-offline.png`: Android API 34 emulator, 1080 × 2400 pixels. These use the production dashboard with sample playback, connection and offline-queue state.

The geometric album artwork is the project's preview asset. Music, watch connection and download state are fixtures, not an active device session. No account data is used. Preview activities are excluded from release APKs.

To refresh these, install matching debug builds on emulators, launch `WearPreviewActivity` with `--es mode player` or `--es mode queue`, and launch `CompanionPreviewActivity` for the dashboard. Disable Android's **Show taps** and **Pointer location** options before capturing with `adb exec-out screencap -p`. Capture the companion player promptly: its sample expires like a real stale playback snapshot. Scroll down for the offline panel.

Replace these files when the UI changes, and update this note with the capture date and whether real devices or previews were used.
