package com.maxrave.simpmusic.wearbridge

import android.content.Intent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.maxrave.common.Config
import com.maxrave.common.WearCompanionBridge
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.searchResult.songs.Album
import com.maxrave.domain.data.model.searchResult.songs.Artist
import com.maxrave.domain.data.model.searchResult.songs.Thumbnail
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.mediaservice.handler.PlayerEvent
import com.maxrave.domain.mediaservice.handler.PlaylistType
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.common.R as CommonR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.koin.core.context.GlobalContext
import java.time.LocalTime
import java.time.temporal.ChronoUnit

private const val PATH_OPEN_LOGIN_ON_PHONE = "/simpmusic/login/open"
private const val PATH_SYNC_LOGIN_FROM_PHONE = "/simpmusic/login/sync"
private const val PATH_LOGIN_COOKIE = "/simpmusic/login/cookie"
private const val PATH_LOGIN_STATUS = "/simpmusic/login/status"
private const val CHANNEL_ID = "wear_login"
private const val NOTIFICATION_ID = 0x534D4C // "SML"

class WearDataLayerListenerService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            PATH_OPEN_LOGIN_ON_PHONE -> postLoginNotification(sourceNodeId = messageEvent.sourceNodeId)
            PATH_SYNC_LOGIN_FROM_PHONE -> syncExistingLoginToWatch(sourceNodeId = messageEvent.sourceNodeId)
            WearCompanionBridge.PATH_REQUEST -> onCompanionRequestFromWatch(messageEvent)
            WearCompanionBridge.PATH_RESPONSE -> onCompanionResponse(messageEvent)
            else -> return
        }
    }

    private fun postLoginNotification(sourceNodeId: String) {
        runCatching {
            val i =
                Intent(this, WearLoginRelayActivity::class.java).apply {
                    putExtra(WearLoginRelayActivity.EXTRA_SOURCE_NODE_ID, sourceNodeId)
                }
            createNotificationChannel()

            val pending =
                PendingIntent.getActivity(
                    this,
                    0,
                    i,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            val notification =
                NotificationCompat
                    .Builder(this, CHANNEL_ID)
                    .setSmallIcon(CommonR.drawable.mono)
                    .setContentTitle("SimpMusic: Sign in for watch")
                    .setContentText("Tap to open sign-in and sync to your watch.")
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build()

            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        }.onSuccess {
            sendStatus(
                sourceNodeId = sourceNodeId,
                status = "requested",
                message = "Phone received request. Check phone notification.",
            )
        }.onFailure {
            sendStatus(
                sourceNodeId = sourceNodeId,
                status = "failed",
                message = "Phone could not show login notification. Open SimpMusic on phone.",
            )
        }
    }

    private fun syncExistingLoginToWatch(sourceNodeId: String) {
        scope.launch {
            val messageClient = Wearable.getMessageClient(applicationContext)

            val dataStoreManager =
                runCatching { GlobalContext.get().get<DataStoreManager>() }.getOrNull()
                    ?: run {
                        sendStatus(
                            sourceNodeId = sourceNodeId,
                            status = "failed",
                            message = "Open SimpMusic on your phone first, then try again.",
                        )
                        return@launch
                    }

            val loggedIn = dataStoreManager.loggedIn.first() == DataStoreManager.TRUE
            val cookie = dataStoreManager.cookie.first()

            if (!loggedIn || cookie.isBlank()) {
                // Update watch UI with a clearer message than "check your phone".
                sendStatus(
                    sourceNodeId = sourceNodeId,
                    status = "failed",
                    message = "No active phone session found. Open SimpMusic on your phone and sign in first.",
                )
                return@launch
            }

            // Send cookie to watch; watch will validate and persist by fetching account info.
            messageClient.sendMessage(sourceNodeId, PATH_LOGIN_COOKIE, cookie.toByteArray())
            sendStatus(
                sourceNodeId = sourceNodeId,
                status = "processing",
                message = "Syncing signed-in session from phone...",
            )
        }
    }

    private fun sendStatus(
        sourceNodeId: String,
        status: String,
        message: String,
    ) {
        Wearable
            .getMessageClient(applicationContext)
            .sendMessage(
                sourceNodeId,
                PATH_LOGIN_STATUS,
                "$status|$message".toByteArray(),
            )
    }

    private fun onCompanionRequestFromWatch(messageEvent: MessageEvent) {
        val payload = runCatching { messageEvent.data.decodeToString() }.getOrNull().orEmpty()
        if (payload.isBlank()) return
        val envelope = runCatching { JSONObject(payload) }.getOrNull() ?: return
        val action = envelope.optString("action")
        val requestPayload = envelope.optJSONObject("payload") ?: JSONObject()
        if (action != WearCompanionBridge.ACTION_REMOTE) return

        scope.launch {
            val response =
                runCatching { handleRemoteFromWatch(requestPayload) }.getOrElse { error ->
                    RemoteResult(
                        ok = false,
                        message = "Watch remote request failed: ${error.message ?: "unknown error"}",
                    )
                }
            appendCompanionLog(response.message)
            sendCompanionResponseToWatch(
                sourceNodeId = messageEvent.sourceNodeId,
                requestId = envelope.optString("requestId"),
                action = WearCompanionBridge.ACTION_REMOTE,
                ok = response.ok,
                message = response.message,
            )
        }
    }

    private suspend fun handleRemoteFromWatch(payload: JSONObject): RemoteResult {
        val mediaPlayerHandler = runCatching { GlobalContext.get().get<MediaPlayerHandler>() }.getOrNull()
            ?: return RemoteResult(ok = false, message = "Phone playback service unavailable.")
        return when (payload.optString("command")) {
            WearCompanionBridge.REMOTE_PLAY_PAUSE -> {
                mediaPlayerHandler.onPlayerEvent(PlayerEvent.PlayPause)
                RemoteResult(ok = true, message = "Phone play/pause toggled from watch.")
            }

            WearCompanionBridge.REMOTE_NEXT -> {
                mediaPlayerHandler.onPlayerEvent(PlayerEvent.Next)
                RemoteResult(ok = true, message = "Phone skipped to next from watch.")
            }

            WearCompanionBridge.REMOTE_PREVIOUS -> {
                mediaPlayerHandler.onPlayerEvent(PlayerEvent.Previous)
                RemoteResult(ok = true, message = "Phone went to previous from watch.")
            }

            WearCompanionBridge.REMOTE_HANDOFF_QUEUE_TO_PHONE -> {
                val tracks = parseTracks(payload.optString("tracks"))
                if (tracks.isEmpty()) {
                    RemoteResult(ok = false, message = "Watch handoff ignored: queue is empty.")
                } else {
                    val index = payload.optInt("index", 0).coerceIn(0, tracks.lastIndex)
                    val playlistType =
                        payload.optString("playlistType").takeIf { it.isNotBlank() }?.let {
                            runCatching { PlaylistType.valueOf(it) }.getOrNull()
                        }
                    mediaPlayerHandler.setQueueData(
                        QueueData.Data(
                            listTracks = tracks,
                            firstPlayedTrack = tracks[index],
                            playlistId = payload.optStringOrNull("playlistId"),
                            playlistName = payload.optStringOrNull("playlistName"),
                            playlistType = playlistType,
                        ),
                    )
                    mediaPlayerHandler.loadMediaItem<Track>(
                        anyTrack = tracks[index],
                        type = Config.SONG_CLICK,
                        index = index,
                    )
                    RemoteResult(ok = true, message = "Handoff applied: continuing on phone (${tracks.size} tracks).")
                }
            }

            else -> RemoteResult(ok = false, message = "Unsupported watch remote command.")
        }
    }

    private fun sendCompanionResponseToWatch(
        sourceNodeId: String,
        requestId: String,
        action: String,
        ok: Boolean,
        message: String,
    ) {
        val payload =
            JSONObject()
                .put("requestId", requestId)
                .put("action", action)
                .put("ok", ok)
                .put("message", message)
                .put("timestamp", System.currentTimeMillis())
                .toString()
                .toByteArray(Charsets.UTF_8)
        Wearable
            .getMessageClient(applicationContext)
            .sendMessage(sourceNodeId, WearCompanionBridge.PATH_RESPONSE, payload)
    }

    private fun onCompanionResponse(messageEvent: MessageEvent) {
        val payload = runCatching { messageEvent.data.decodeToString() }.getOrNull().orEmpty()
        if (payload.isBlank()) return
        scope.launch {
            val dataStoreManager = runCatching { GlobalContext.get().get<DataStoreManager>() }.getOrNull() ?: return@launch
            dataStoreManager.putString(WearCompanionBridge.KEY_LAST_RESPONSE, payload)

            val response = runCatching { JSONObject(payload) }.getOrNull()
            if (response?.optString("action") == WearCompanionBridge.ACTION_STATUS) {
                dataStoreManager.putString(WearCompanionBridge.KEY_LAST_DIAGNOSTICS, payload)
            }
            appendCompanionLog(response?.optString("message").orEmpty().ifBlank { payload })
        }
    }

    private suspend fun appendCompanionLog(message: String) {
        val dataStoreManager = runCatching { GlobalContext.get().get<DataStoreManager>() }.getOrNull() ?: return
        val currentLog = dataStoreManager.getString(WearCompanionBridge.KEY_LOG).first().orEmpty()
        val timestamp = LocalTime.now().truncatedTo(ChronoUnit.SECONDS)
        val updatedLog =
            if (currentLog.isBlank()) {
                "[$timestamp] $message"
            } else {
                "$currentLog\n[$timestamp] $message"
            }
        dataStoreManager.putString(WearCompanionBridge.KEY_LOG, updatedLog.takeLast(16000))
    }

    private fun parseTracks(json: String): List<Track> {
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val title = item.optString("title")
                val videoId = item.optString("videoId")
                if (title.isBlank() || videoId.isBlank()) continue

                val album =
                    item.optJSONObject("album")?.let { albumObj ->
                        val id = albumObj.optString("id")
                        val name = albumObj.optString("name")
                        if (id.isBlank() || name.isBlank()) null else Album(id, name)
                    }
                val artists =
                    item.optJSONArray("artists")
                        ?.let { artistsArray ->
                            buildList {
                                for (artistIndex in 0 until artistsArray.length()) {
                                    val artistObj = artistsArray.optJSONObject(artistIndex) ?: continue
                                    val artistName = artistObj.optString("name")
                                    if (artistName.isBlank()) continue
                                    add(Artist(id = artistObj.optStringOrNull("id"), name = artistName))
                                }
                            }
                        }?.ifEmpty { null }
                val thumbnails =
                    item.optJSONArray("thumbnails")
                        ?.let { thumbnailArray ->
                            buildList {
                                for (thumbnailIndex in 0 until thumbnailArray.length()) {
                                    val thumbnailObj = thumbnailArray.optJSONObject(thumbnailIndex) ?: continue
                                    val url = thumbnailObj.optString("url")
                                    if (url.isBlank()) continue
                                    add(
                                        Thumbnail(
                                            width = thumbnailObj.optInt("width", 0),
                                            url = url,
                                            height = thumbnailObj.optInt("height", 0),
                                        ),
                                    )
                                }
                            }
                        }?.ifEmpty { null }

                add(
                    Track(
                        album = album,
                        artists = artists,
                        duration = item.optStringOrNull("duration"),
                        durationSeconds = item.optIntOrNull("durationSeconds"),
                        isAvailable = item.optBoolean("isAvailable", true),
                        isExplicit = item.optBoolean("isExplicit", false),
                        likeStatus = item.optStringOrNull("likeStatus"),
                        thumbnails = thumbnails,
                        title = title,
                        videoId = videoId,
                        videoType = item.optStringOrNull("videoType"),
                        category = item.optStringOrNull("category"),
                        feedbackTokens = null,
                        resultType = item.optStringOrNull("resultType"),
                        year = item.optStringOrNull("year"),
                    ),
                )
            }
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) {
            null
        } else {
            optString(key).ifBlank { null }
        }

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (!has(key) || isNull(key)) {
            null
        } else {
            optInt(key)
        }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Watch login",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Prompts to sign in on phone and sync session to WearOS watch."
            }
        nm.createNotificationChannel(channel)
    }
}

private data class RemoteResult(
    val ok: Boolean,
    val message: String,
)
