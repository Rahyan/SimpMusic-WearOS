# Watch companion protocol

Protocol v2 uses `/simpmusic/watch/companion/request` and `/simpmusic/watch/companion/response`. The application ID and signing certificate must match between phone and watch. Transport success means delivery only; a positive response means the watch applied the operation.

## Requests and responses

Each request carries `protocolVersion`, a UUID `requestId`, `action` and an object `payload`. Responses echo request/action and include `ok`, a user-readable message, timestamp and optional data. Pending requests match the **source node, request ID and action**. Registration occurs before sending; cancellation, delivery failure and timeout release the waiter. Late/unmatched responses cannot replace the lightweight companion's current player state.

Requests are limited to 100,000 bytes. The watch serializes mutations and keeps a bounded, two-minute replay cache for command responses. A new playback-state poll is read-only and bypasses that mutation queue. A process restart clears the replay cache; clients should not blindly retry a transport toggle after an unknown outcome.

## Player state and remote control

`playback_state` returns metadata, artwork, playing/buffering state, position/duration, availability of next/previous, volume and a queue window of at most 40 entries. Queue indices refer to the full queue. `queueRevision` hashes the ordered track IDs. A snapshot older than 15 seconds disables lightweight companion playback controls. Polling occurs every three seconds only while the companion is visible; a disconnected/stale watch is shown explicitly. Device clocks should be synchronized.

`remote` supports play/pause, next, previous, bounded seek, volume, queue play/remove/move and existing handoff commands. Seek carries the displayed `videoId`; the watch rejects it if playback has advanced to another track. Queue mutations carry `queueRevision`, `index` and `videoId`. The watch rejects a changed queue before acting and returns a fresh snapshot. This also disambiguates repeated appearances of the same track by position.

The lightweight companion explicitly selects one watch when several are connected; controls and logout are never broadcast. It displays applied, rejected or unconfirmed outcomes, and locks repeat control taps while a command is pending.

## Session lifecycle

`sync_session` validates/imports the phone session before acknowledging it. `logout` removes watch accounts and Spotify tokens without deleting local music. A successful companion logout then clears its phone session/WebView storage. An unsuccessful connected-watch logout preserves the phone session for retry. Without a connected watch, clearing the phone session explicitly says the watch still needs signing out.

An explicit logout marker prevents watch startup from immediately requesting automatic relogin. Background library synchronization never sends session cookies; session refresh remains explicit. A valid subsequent account import clears the marker.

## Library synchronization

The full phone app's worker visits every connected watch. `status` returns a persistent watch `libraryGeneration`; per-node fingerprints include that generation so a reinstall/data reset resends the library. Fingerprints contain all serialized metadata, independent of collection ordering; ordered track arrays retain their order.

Changed collections are sent in resumable batches of 60 items. Each item must receive an applied acknowledgement before the batch manifest is sent. A `reconcile` manifest includes category, full-collection SHA-256 revision, offset, IDs and a `complete` flag. Watch manifest progress persists across process restart, rejects skipped/changed pages, and accepts a repeated last page after a lost acknowledgement. The final acknowledged manifest alone permits membership removal; incomplete pages never do. The watch caps an accumulated manifest at 10,000 distinct entries.

The worker stores a completed fingerprint only after final reconciliation is acknowledged. Failed/unconfirmed operations request a WorkManager retry. Successful intermediate pages store a cursor and also request continuation. Retries use linear one-minute backoff; the periodic schedule remains six hours. Existing charging/unmetered policies still apply.

Ownership is scoped to the importing phone. Pre-existing watch-local favorites are not claimed by a phone. Reconciliation only removes imported likes/follows; another phone's owned membership protects an entry. Rows, listening history and downloaded files are retained. Partial Room metadata updates exclude local download state, history and membership timestamps. Phone download metadata never asserts that a file exists on the watch.

Local watch favorite/follow edits now record a separate explicit intent. A watch-local unlike remains unliked when an importing phone still likes the item, and a watch-local like survives phone reconciliation. Imported mutations carry coroutine origin context so they do not create local overrides. This resolves that membership conflict locally; it does not upload the watch's changes to YouTube or the phone library.

## Offline queue

The optional `offlinePack` in a playback-state response reports a persistent pack revision, total/verified-ready track counts, ready duration, download policy and message. Older responses without this field remain compatible. The lightweight companion keeps the offline panel visible when the live player queue is empty.

`prepare_offline_queue` carries the displayed `queueRevision` and `chargingOnly`. The watch rejects a changed queue and accepts 1–100 complete track records with a conservative estimated audio budget of 512 MB. Duplicate queue entries download once. Preparation replaces the pinned manifest, while existing downloads remain available. The actual Media3 download policy persists across restart: unmetered network plus charging, or connected network when disabled; this policy affects all watch downloads.

`retry_offline_queue` and `play_offline_queue` carry `packRevision`, rejecting a replaced pack. Retry submits durable WorkManager requests. Completion requires a completed audio download record and the full corresponding cache range; database download flags alone never establish pack readiness. Play rebuilds the queue from verified-ready audio and keeps it finite even with Endless queue enabled. The watch queue screen can prepare a pack independently; Downloads and the tile provide quick start. Scheduling/background service resumption and sustained offline playback remain physical runtime release checks.
