# Offline playlists

Open a YouTube Music playlist on the watch and choose **Keep offline**. Manage your selections under **Downloads → Offline playlists**.

You can save up to eight playlists, with up to 100 tracks each. The watch periodically refreshes their track lists and queues missing downloads. Refreshes follow the watch's download policy: Wi-Fi and charging by default. Android controls when background work runs, so changes may take time to appear. Use **Sync playlists** to request a refresh under the same conditions.

See [Android's background data transfer guidance](https://developer.android.com/develop/background-work/background-tasks/data-transfer-options) for the platform's scheduling limits.

Change the connection policy in the offline playlist screen's download settings. It applies to all watch downloads, including the queue prepared from the companion.

If a playlist cannot be fetched completely, its previously saved track list stays available. **Play offline** uses only tracks verified to be fully downloaded; the ready count shows how much of the playlist is available.

The watch only offers **Keep offline** when the playlist's details are complete. If some details are unavailable, you can still download individual tracks.

## Download budget

Choose an estimated auto-download budget of 256 MB, 512 MB or 1 GB. It applies to files downloaded by playlist sync, excluding existing manual downloads. Requests reserve space based on track duration; actual file sizes depend on the audio format.

The budget limits new download requests. Lowering it keeps existing files and does not cancel requests already queued. Low device storage also stops new requests. Increase the budget, stop syncing a playlist or free space to continue.

## Cleanup

**Auto cleanup** is off by default. When enabled, it removes files downloaded by playlist sync that are no longer needed by any saved playlist. It preserves manual downloads, the pinned offline queue and music still in the player's queue.

**Stop syncing** keeps the playlist's downloaded files. To permanently remove a track, stop keeping its playlist offline before deleting the download; otherwise a later refresh can download it again.
