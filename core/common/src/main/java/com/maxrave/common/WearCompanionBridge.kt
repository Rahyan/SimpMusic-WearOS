package com.maxrave.common

object WearCompanionBridge {
    const val PATH_REQUEST = "/simpmusic/watch/companion/request"
    const val PATH_RESPONSE = "/simpmusic/watch/companion/response"

    const val ACTION_STATUS = "status"
    const val ACTION_SYNC_SESSION = "sync_session"
    const val ACTION_SYNC_SONG = "sync_song"
    const val ACTION_SYNC_PLAYLIST = "sync_playlist"
    const val ACTION_SYNC_ALBUM = "sync_album"
    const val ACTION_SYNC_ARTIST = "sync_artist"
    const val ACTION_SYNC_PODCAST = "sync_podcast"
    const val ACTION_SYNC_DOWNLOAD = "sync_download"
    const val ACTION_REMOTE = "remote"

    const val REMOTE_PLAY_PAUSE = "play_pause"
    const val REMOTE_NEXT = "next"
    const val REMOTE_PREVIOUS = "previous"
    const val REMOTE_VOLUME_UP = "volume_up"
    const val REMOTE_VOLUME_DOWN = "volume_down"
    const val REMOTE_HANDOFF_QUEUE = "handoff_queue"
    const val REMOTE_HANDOFF_QUEUE_TO_PHONE = "handoff_queue_to_phone"

    const val KEY_LAST_RESPONSE = "wear_companion_last_response"
    const val KEY_LAST_DIAGNOSTICS = "wear_companion_last_diagnostics"
    const val KEY_LOG = "wear_companion_log"
    const val KEY_AUTO_SYNC_BATTERY_SAVER = "wear_companion_auto_sync_battery_saver"
    const val KEY_AUTO_SYNC_UNMETERED_ONLY = "wear_companion_auto_sync_unmetered_only"
    const val KEY_AUTO_SYNC_LAST_STATS = "wear_companion_auto_sync_last_stats"

    const val KEY_AUTO_SYNC_SIG_SESSION = "watch_companion_sig_session"
    const val KEY_AUTO_SYNC_SIG_SONGS = "watch_companion_sig_songs"
    const val KEY_AUTO_SYNC_SIG_PLAYLISTS = "watch_companion_sig_playlists"
    const val KEY_AUTO_SYNC_SIG_ALBUMS = "watch_companion_sig_albums"
    const val KEY_AUTO_SYNC_SIG_ARTISTS = "watch_companion_sig_artists"
    const val KEY_AUTO_SYNC_SIG_PODCASTS = "watch_companion_sig_podcasts"
    const val KEY_AUTO_SYNC_SIG_DOWNLOADS = "watch_companion_sig_downloads"

    const val KEY_WEAR_BATTERY_SAVER_MODE = "wear_battery_saver_mode"
    const val KEY_WEAR_PHONE_OFFLOAD_CONTROLS = "wear_phone_offload_controls"
}
