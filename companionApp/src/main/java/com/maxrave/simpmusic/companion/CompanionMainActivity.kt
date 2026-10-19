package com.maxrave.simpmusic.companion

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.maxrave.common.WearCompanionBridge
import com.maxrave.common.WatchPlaybackSnapshot
import com.maxrave.simpmusic.companion.bridge.ConnectedNode
import com.maxrave.simpmusic.companion.bridge.requestWatch
import androidx.compose.runtime.saveable.rememberSaveable
import com.maxrave.simpmusic.companion.bridge.CompanionStore
import com.maxrave.simpmusic.companion.bridge.buildCompanionEnvelope
import com.maxrave.simpmusic.companion.bridge.getConnectedNodes
import com.maxrave.simpmusic.companion.bridge.sendMessageToNode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val PATH_LOGIN_COOKIE = "/simpmusic/login/cookie"
private const val PATH_LOGIN_STATUS = "/simpmusic/login/status"

class CompanionMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            CompanionTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CompanionHomeScreen()
                }
            }
        }
    }
}

private data class CompanionUiState(
    val connectedNodes: List<ConnectedNode> = emptyList(),
    val playback: WatchPlaybackSnapshot? = null,
    val hasCookie: Boolean = false,
    val isSessionExpired: Boolean = false,
    val requiresRelogin: Boolean = false,
    val reloginReason: String = "",
    val diagnostics: String = "",
    val lastResponse: String = "",
    val logs: String = "",
)

@Composable
private fun CompanionHomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var uiState by remember { mutableStateOf(CompanionUiState()) }
    var selectedNodeId by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }

    suspend fun refreshUiState() {
        val nodes = getConnectedNodes(context)
        if (nodes.none { it.id == selectedNodeId }) selectedNodeId = nodes.firstOrNull()?.id.orEmpty()
        val sessionState = CompanionStore.getSessionState(context)
        uiState =
            uiState.copy(
                connectedNodes = nodes,
                playback = if (selectedNodeId.isBlank()) null else CompanionStore.getPlayback(context, selectedNodeId),
                hasCookie = sessionState.hasCookie,
                isSessionExpired = sessionState.isExpired,
                requiresRelogin = sessionState.requiresRelogin,
                reloginReason = sessionState.reloginReason,
                diagnostics = CompanionStore.getLastDiagnostics(context),
                lastResponse = CompanionStore.getLastResponse(context),
                logs = CompanionStore.getLog(context),
            )
    }

    suspend fun sendWatchAction(action: String, payload: JSONObject): Boolean {
        if (busy || selectedNodeId.isBlank()) return false
        val target = selectedNodeId
        busy = true
        feedback = "Waiting for watch…"
        return try {
            val reply = requestWatch(context, target, action, payload,
                if (action == WearCompanionBridge.ACTION_SYNC_SESSION) 45_000 else 15_000)
            feedback = reply?.message?.ifBlank { if (reply.ok) "Watch updated." else "Watch could not apply this command." }
                ?: "No confirmation from watch. Reconnect and try again."
            reply?.data?.let { runCatching { JSONObject(it) }.getOrNull() }
                ?.let { CompanionStore.savePlayback(context, target, it) }
            refreshUiState()
            reply?.ok == true
        } finally { busy = false }
    }

    suspend fun sendSessionToWatch() {
        val session = CompanionStore.getSessionState(context)
        if (!session.hasCookie || session.isExpired || session.requiresRelogin) {
            feedback = "Sign in on your phone before syncing."; return
        }
        sendWatchAction(WearCompanionBridge.ACTION_SYNC_SESSION, JSONObject().put("cookie", CompanionStore.getCookie(context)))
    }

    suspend fun revokeSession() {
        if (selectedNodeId.isNotBlank() && !sendWatchAction(WearCompanionBridge.ACTION_LOGOUT, JSONObject())) return
        CompanionStore.clearCookie(context)
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        feedback = if (selectedNodeId.isBlank()) "Phone session cleared. Connect your watch to sign out there." else "Signed out on this phone and selected watch."
        refreshUiState()
    }

    fun copyText(label: String, text: String) {
        if (text.isBlank()) {
            Toast.makeText(context, "Nothing to copy", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }

    fun shareText(label: String, text: String) {
        if (text.isBlank()) {
            Toast.makeText(context, "Nothing to share", Toast.LENGTH_SHORT).show()
            return
        }
        val shareIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, label)
                putExtra(Intent.EXTRA_TEXT, text)
            }
        val chooser = Intent.createChooser(shareIntent, "Share $label")
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                refreshUiState()
                val target = selectedNodeId
                if (target.isNotBlank() && !busy) {
                    val reply = requestWatch(context, target, WearCompanionBridge.ACTION_PLAYBACK_STATE, timeoutMs = 4_000)
                    reply?.data?.let { runCatching { JSONObject(it) }.getOrNull() }
                        ?.let { CompanionStore.savePlayback(context, target, it) }
                    refreshUiState()
                }
                delay(3_000L)
            }
        }
    }

    CompanionDashboard(
        watches = uiState.connectedNodes.map { it.name },
        selectedWatch = uiState.connectedNodes.indexOfFirst { it.id == selectedNodeId },
        onSelectWatch = { index -> selectedNodeId = uiState.connectedNodes[index].id; scope.launch { refreshUiState() } },
        playback = uiState.playback, busy = busy, feedback = feedback,
        onPlaybackAction = { payload -> scope.launch { sendWatchAction(WearCompanionBridge.ACTION_REMOTE, payload) } },
        hasSession = uiState.hasCookie,
        needsLogin = uiState.isSessionExpired || uiState.requiresRelogin,
        loginReason = uiState.reloginReason,
        diagnostics = uiState.diagnostics, response = uiState.lastResponse, logs = uiState.logs,
        onRefresh = { scope.launch { refreshUiState() } },
        onLogin = { context.startActivity(Intent(context, CompanionLoginRelayActivity::class.java)) },
        onSync = { scope.launch { sendSessionToWatch() } },
        onClearSession = { scope.launch { revokeSession(); refreshUiState() } },
        onRemote = { command -> scope.launch {
            sendWatchAction(WearCompanionBridge.ACTION_REMOTE, JSONObject().put("command", command))
        } },
        onDiagnostics = { scope.launch { sendWatchAction(WearCompanionBridge.ACTION_STATUS, JSONObject()) } },
        onCopy = ::copyText, onShare = ::shareText,
        onClearLogs = { CompanionStore.clearLog(context); scope.launch { refreshUiState() } },
    )
}
