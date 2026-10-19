package com.maxrave.simpmusic.companion.wearbridge

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.maxrave.common.BridgeRequests
import com.maxrave.common.BridgeAcknowledgement
import com.maxrave.common.WearCompanionBridge
import kotlinx.coroutines.cancel
import com.maxrave.simpmusic.companion.BuildConfig
import com.maxrave.simpmusic.companion.CompanionLoginRelayActivity
import com.maxrave.simpmusic.companion.bridge.CompanionStore
import com.maxrave.simpmusic.companion.bridge.decodeCompanionRequest
import com.maxrave.simpmusic.companion.bridge.decodeCompanionResponse
import com.maxrave.simpmusic.companion.bridge.responseIndicatesAuthExpiry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val CHANNEL_ID = "companion_login"
private const val NOTIFICATION_ID = 0x434F4D // "COM"
private const val PATH_OPEN_LOGIN_ON_PHONE = "/simpmusic/login/open"
private const val PATH_SYNC_LOGIN_FROM_PHONE = "/simpmusic/login/sync"
private const val PATH_LOGIN_COOKIE = "/simpmusic/login/cookie"
private const val PATH_LOGIN_STATUS = "/simpmusic/login/status"

class CompanionWearListenerService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            PATH_OPEN_LOGIN_ON_PHONE -> postLoginNotification(messageEvent.sourceNodeId)
            PATH_SYNC_LOGIN_FROM_PHONE -> syncStoredSessionToWatch(messageEvent.sourceNodeId)
            WearCompanionBridge.PATH_REQUEST -> onCompanionRequestFromWatch(messageEvent)
            WearCompanionBridge.PATH_RESPONSE -> onCompanionResponse(messageEvent)
            else -> return
        }
    }

    private fun postLoginNotification(sourceNodeId: String) {
        runCatching {
            createNotificationChannel()
            val intent =
                Intent(this, CompanionLoginRelayActivity::class.java).apply {
                    putExtra(CompanionLoginRelayActivity.EXTRA_SOURCE_NODE_ID, sourceNodeId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            val pendingIntent =
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val notification =
                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("SimpMusic Companion")
                    .setContentText("Tap to sign in and sync to your watch.")
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build()
            if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
                sendStatus(sourceNodeId, "requested", "Open the phone companion to complete sign-in.")
            } else {
                sendStatus(sourceNodeId, "requested", "Open the companion on your phone to sign in. Login notifications are disabled.")
            }
        }.onFailure {
            sendStatus(sourceNodeId, "failed", "Unable to open phone login flow.")
        }
    }

    private fun syncStoredSessionToWatch(sourceNodeId: String) {
        scope.launch {
            val sessionState = CompanionStore.getSessionState(applicationContext)
            if (!sessionState.hasCookie) {
                sendStatus(sourceNodeId, "failed", "No saved phone session. Open companion login first.")
                return@launch
            }
            if (sessionState.isExpired || sessionState.requiresRelogin) {
                val reason =
                    sessionState.reloginReason.ifBlank {
                        "Saved session expired. Open companion login to refresh your session."
                    }
                sendStatus(sourceNodeId, "failed", reason)
                CompanionStore.appendLog(applicationContext, "Blocked session sync: $reason")
                return@launch
            }
            val cookie = CompanionStore.getCookie(applicationContext)
            Wearable.getMessageClient(applicationContext)
                .sendMessage(sourceNodeId, PATH_LOGIN_COOKIE, cookie.toByteArray(Charsets.UTF_8))
            sendStatus(sourceNodeId, "processing", "Syncing saved phone session to watch...")
            CompanionStore.appendLog(applicationContext, "Sent saved session cookie to watch.")
        }
    }

    private fun onCompanionRequestFromWatch(messageEvent: MessageEvent) {
        val raw = runCatching { messageEvent.data.decodeToString() }.getOrNull().orEmpty()
        if (raw.isBlank()) return
        val envelope = decodeCompanionRequest(raw) ?: return
        val action = envelope.action
        val requestId = envelope.requestId
        val payload = envelope.payload
        scope.launch {
            when (action) {
                WearCompanionBridge.ACTION_STATUS -> {
                    val data =
                        JSONObject()
                            .put("companionOnly", true)
                            .put("appVersion", BuildConfig.VERSION_NAME)
                            .put("hasCookie", CompanionStore.getCookie(applicationContext).isNotBlank())
                            .put("note", "Lightweight phone companion (no embedded playback engine).")
                    sendCompanionResponse(
                        sourceNodeId = messageEvent.sourceNodeId,
                        requestId = requestId,
                        action = action,
                        ok = true,
                        message = "Companion diagnostics ready.",
                        data = data,
                    )
                }

                WearCompanionBridge.ACTION_REMOTE -> {
                    val result = handleRemoteCommand(payload.optString("command"))
                    sendCompanionResponse(
                        sourceNodeId = messageEvent.sourceNodeId,
                        requestId = requestId,
                        action = action,
                        ok = result.ok,
                        message = result.message,
                    )
                }

                else -> {
                    sendCompanionResponse(
                        sourceNodeId = messageEvent.sourceNodeId,
                        requestId = requestId,
                        action = action,
                        ok = false,
                        message = "Unsupported action in companion app: $action",
                    )
                }
            }
        }
    }

    private fun onCompanionResponse(messageEvent: MessageEvent) {
        val payload = runCatching { messageEvent.data.decodeToString() }.getOrNull().orEmpty()
        if (payload.isBlank()) return
        val response = decodeCompanionResponse(payload) ?: return
        if (!BridgeRequests.complete(messageEvent.sourceNodeId, response.requestId, response.action,
                BridgeAcknowledgement(response.ok, response.message, response.data?.toString()))) return
        response.data?.let { CompanionStore.savePlayback(applicationContext, messageEvent.sourceNodeId, it) }
        if (response.action == WearCompanionBridge.ACTION_PLAYBACK_STATE) return
        CompanionStore.saveLastResponse(applicationContext, payload)
        if (response.action == WearCompanionBridge.ACTION_STATUS) CompanionStore.saveLastDiagnostics(applicationContext, payload)
        if (response.action == WearCompanionBridge.ACTION_SYNC_SESSION && responseIndicatesAuthExpiry(response)) {
            CompanionStore.markReloginRequired(applicationContext, response.message.ifBlank { "Please sign in again." })
        }
        if (response.message.isNotBlank()) CompanionStore.appendLog(applicationContext, response.message)
    }

    private fun handleRemoteCommand(command: String): RemoteResult {
        val audioManager = getSystemService(AudioManager::class.java)
        return when (command) {
            WearCompanionBridge.REMOTE_PLAY_PAUSE ->
                dispatchMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "Phone media play/pause triggered.")

            WearCompanionBridge.REMOTE_NEXT ->
                dispatchMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_NEXT, "Phone media next triggered.")

            WearCompanionBridge.REMOTE_PREVIOUS ->
                dispatchMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Phone media previous triggered.")

            WearCompanionBridge.REMOTE_VOLUME_UP -> {
                audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                RemoteResult(ok = audioManager != null, message = "Phone volume up triggered.")
            }

            WearCompanionBridge.REMOTE_VOLUME_DOWN -> {
                audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                RemoteResult(ok = audioManager != null, message = "Phone volume down triggered.")
            }

            WearCompanionBridge.REMOTE_HANDOFF_QUEUE_TO_PHONE ->
                RemoteResult(
                    ok = false,
                    message = "Queue handoff needs the full SimpMusic phone app. Install/switch to full app, then retry.",
                )

            else -> RemoteResult(ok = false, message = "Unsupported remote command: $command")
        }
    }

    private fun dispatchMediaKey(
        audioManager: AudioManager?,
        keyCode: Int,
        message: String,
    ): RemoteResult {
        if (audioManager == null) return RemoteResult(ok = false, message = "AudioManager unavailable.")
        val eventTime = System.currentTimeMillis()
        audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0))
        audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0))
        return RemoteResult(ok = true, message = message)
    }

    private fun sendStatus(
        sourceNodeId: String,
        status: String,
        message: String,
    ) {
        Wearable.getMessageClient(applicationContext)
            .sendMessage(sourceNodeId, PATH_LOGIN_STATUS, "$status|$message".toByteArray(Charsets.UTF_8))
    }

    private fun sendCompanionResponse(
        sourceNodeId: String,
        requestId: String,
        action: String,
        ok: Boolean,
        message: String,
        data: JSONObject? = null,
    ) {
        val payload =
            JSONObject()
                .put("requestId", requestId)
                .put("action", action)
                .put("ok", ok)
                .put("message", message)
                .put("timestamp", System.currentTimeMillis())
        if (data != null) payload.put("data", data)
        Wearable.getMessageClient(applicationContext)
            .sendMessage(sourceNodeId, WearCompanionBridge.PATH_RESPONSE, payload.toString().toByteArray(Charsets.UTF_8))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Watch companion login",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Prompts login flow for SimpMusic watch companion."
            }
        manager.createNotificationChannel(channel)
    }
}

private data class RemoteResult(
    val ok: Boolean,
    val message: String,
)
