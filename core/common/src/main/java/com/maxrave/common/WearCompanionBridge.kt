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

    const val KEY_LAST_RESPONSE = "wear_companion_last_response"
    const val KEY_LAST_DIAGNOSTICS = "wear_companion_last_diagnostics"
    const val KEY_LOG = "wear_companion_log"
}
