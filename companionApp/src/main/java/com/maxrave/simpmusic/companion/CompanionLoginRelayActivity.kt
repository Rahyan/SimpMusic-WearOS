package com.maxrave.simpmusic.companion

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.wearable.Wearable
import com.maxrave.common.Config
import com.maxrave.simpmusic.companion.bridge.hasMusicLoginCookie
import com.maxrave.simpmusic.companion.bridge.CompanionStore

private const val PATH_LOGIN_COOKIE = "/simpmusic/login/cookie"
private const val PATH_LOGIN_STATUS = "/simpmusic/login/status"

class CompanionLoginRelayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            CompanionTheme {
                Surface(Modifier.fillMaxSize()) {
                    CompanionLoginRelayScreen(onClose = { finish() })
                }
            }
        }
    }

    companion object {
        const val EXTRA_SOURCE_NODE_ID = "source_node_id"
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CompanionLoginRelayScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val sourceNodeId =
        (context as? CompanionLoginRelayActivity)
            ?.intent
            ?.getStringExtra(CompanionLoginRelayActivity.EXTRA_SOURCE_NODE_ID)

    val relaying = remember { mutableStateOf(false) }
    val status = remember { mutableStateOf("Open Google sign-in and complete login.") }
    val appCtx = context.applicationContext
    val messageClient = Wearable.getMessageClient(appCtx)
    val nodeClient = Wearable.getNodeClient(appCtx)

    fun sendStatusToWatch(
        state: String,
        message: String,
    ) {
        val payload = "$state|$message".toByteArray(Charsets.UTF_8)
        if (!sourceNodeId.isNullOrBlank()) {
            messageClient.sendMessage(sourceNodeId, PATH_LOGIN_STATUS, payload)
            return
        }
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { node -> messageClient.sendMessage(node.id, PATH_LOGIN_STATUS, payload) }
        }
    }

    LaunchedEffect(Unit) {
        CookieManager.getInstance().removeAllCookies(null)
        sendStatusToWatch("processing", "Phone login page opened.")
    }

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
            }
            Text(
                text = "Companion Login",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            text = status.value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp),
        )

        Spacer(Modifier.height(6.dp))

        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            onRelease = { view ->
                view.stopLoading()
                view.destroy()
            },
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams =
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient =
                        object : WebViewClient() {
                            override fun onPageFinished(
                                view: WebView?,
                                url: String?,
                            ) {
                                val resolvedUrl = url ?: return
                                if (relaying.value) return

                                val cookie = CookieManager.getInstance().getCookie(resolvedUrl)
                                if (!hasMusicLoginCookie(resolvedUrl, cookie)) return
                                val sessionCookie = cookie ?: return
                                relaying.value = true

                                CompanionStore.saveCookie(ctx, sessionCookie)
                                CompanionStore.appendLog(ctx, "Saved login cookie from phone relay.")
                                status.value = "Login complete. Sending to watch..."
                                sendStatusToWatch("processing", "Phone login complete. Sending session to watch...")

                                WebStorage.getInstance().deleteAllData()
                                CookieManager.getInstance().removeAllCookies(null)
                                CookieManager.getInstance().flush()
                                clearCache(true)
                                clearFormData()
                                clearHistory()
                                clearSslPreferences()

                                val payload = sessionCookie.toByteArray(Charsets.UTF_8)
                                if (!sourceNodeId.isNullOrBlank()) {
                                    messageClient
                                        .sendMessage(sourceNodeId, PATH_LOGIN_COOKIE, payload)
                                        .addOnSuccessListener {
                                            messageClient.sendMessage(
                                                sourceNodeId,
                                                PATH_LOGIN_STATUS,
                                                "processing|Session sent to watch. Applying sign-in...".toByteArray(),
                                            )
                                            Toast.makeText(ctx, "Sent to watch", Toast.LENGTH_SHORT).show()
                                            onClose()
                                        }.addOnFailureListener { error ->
                                            messageClient.sendMessage(
                                                sourceNodeId,
                                                PATH_LOGIN_STATUS,
                                                "failed|Failed to send session to watch. Retry with both devices connected.".toByteArray(),
                                            )
                                            relaying.value = false
                                            status.value = "Session saved. Reconnect and use Sync to watch on the dashboard."
                                        }
                                    return
                                }

                                nodeClient.connectedNodes
                                    .addOnSuccessListener { nodes ->
                                        if (nodes.isEmpty()) {
                                            relaying.value = false
                                            status.value = "Session saved. Connect your watch and use Sync to watch on the dashboard."
                                            sendStatusToWatch("failed", "No watch connected.")
                                            return@addOnSuccessListener
                                        }
                                        nodes.forEach { node ->
                                            messageClient.sendMessage(node.id, PATH_LOGIN_COOKIE, payload)
                                            messageClient.sendMessage(
                                                node.id,
                                                PATH_LOGIN_STATUS,
                                                "processing|Session sent to watch. Applying sign-in...".toByteArray(),
                                            )
                                        }
                                        Toast.makeText(ctx, "Sent to watch", Toast.LENGTH_SHORT).show()
                                        onClose()
                                    }.addOnFailureListener { error ->
                                        relaying.value = false
                                        status.value = "Session saved. Check the watch connection on the dashboard."
                                        sendStatusToWatch("failed", "Failed to query watch connection.")
                                    }
                            }
                        }
                    loadUrl(Config.LOG_IN_URL)
                }
            },
        )
    }
}
