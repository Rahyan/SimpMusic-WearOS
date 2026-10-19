package com.maxrave.simpmusic.companion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxrave.common.WearCompanionBridge
import org.json.JSONObject

@Composable
internal fun CompanionDashboard(
    watches: List<String>, selectedWatch: Int, onSelectWatch: (Int) -> Unit,
    playback: com.maxrave.common.WatchPlaybackSnapshot?, busy: Boolean, feedback: String,
    onPlaybackAction: (JSONObject) -> Unit, hasSession: Boolean, needsLogin: Boolean, loginReason: String,
    diagnostics: String, response: String, logs: String,
    onRefresh: () -> Unit, onLogin: () -> Unit, onSync: () -> Unit, onClearSession: () -> Unit,
    onRemote: (String) -> Unit, onDiagnostics: () -> Unit,
    onCopy: (String, String) -> Unit, onShare: (String, String) -> Unit, onClearLogs: () -> Unit,
) {
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    val horizontalGutter = ((androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp - 600) / 2).coerceAtLeast(24).dp
    if (confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false },
        title = { Text("Sign out?") }, text = { Text("Sign out on this phone and the selected connected watch. Downloaded music stays on the watch.") },
        confirmButton = { TextButton(onClick = { confirmLogout = false; onClearSession() }) { Text("Sign out") } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } })
    val connected = watches.isNotEmpty()
    val lastMessage = remember(response) { runCatching { JSONObject(response).optString("message") }.getOrDefault("") }
    Column(
        Modifier.fillMaxSize().background(Ink).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = horizontalGutter, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SimpMusic", fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                Text("WATCH COMPANION", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
            }
            Control(Icons.Rounded.Refresh, "Refresh connection", onRefresh)
        }
        if (connected) Surface(color = Panel, shape = RoundedCornerShape(14.dp)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(Electric, RoundedCornerShape(2.dp)))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("CONNECTED", color = Electric, fontSize = 9.sp, letterSpacing = 1.sp)
                    Text(watches.getOrNull(selectedWatch) ?: watches.first(), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                WatchArtwork(Modifier.size(width = 32.dp, height = 50.dp), true)
            }
        } else Surface(color = Panel, shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.fillMaxWidth().padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(6.dp).background(if (connected) Electric else Muted, RoundedCornerShape(2.dp)))
                        Text(if (connected) "CONNECTED" else "WAITING FOR WATCH", fontSize = 10.sp,
                            letterSpacing = 1.sp, color = if (connected) Electric else Muted)
                    }
                    Text(if (connected) "Music.\nWithin reach." else "Bring your\nwatch closer.",
                        fontSize = 27.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.7).sp)
                    Text(watches.joinToString().ifBlank { "Turn on Bluetooth on both devices." },
                        color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                }
                WatchArtwork(Modifier.padding(start = 12.dp).size(width = 76.dp, height = 120.dp), connected)
            }
        }
        if (watches.size > 1) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("CONTROL A WATCH", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
            watches.forEachIndexed { index, name -> Action(name, Modifier.fillMaxWidth(),
                primary = index == selectedWatch, enabled = !busy, onClick = { onSelectWatch(index) }) }
        }
        CompanionPlayer(playback, connected, busy, onPlaybackAction)
        if (feedback.isNotBlank()) Text(feedback, color = Blue, fontSize = 12.sp)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("02", "Your account")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PersonOutline, null, tint = Blue, modifier = Modifier.size(25.dp))
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(when { needsLogin -> "Sign in again"; hasSession -> "Session saved"; else -> "Connect your music" },
                        fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(when { needsLogin -> loginReason.ifBlank { "Your session needs refreshing." }
                        hasSession -> "Ready to send to your watch"
                        else -> "Sign in on your phone to sync your account." },
                        color = if (needsLogin) MaterialTheme.colorScheme.error else Muted, fontSize = 12.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Action(if (hasSession && !needsLogin) "Sync to watch" else "Sign in", Modifier.weight(1f),
                    primary = true, enabled = !busy && (!hasSession || needsLogin || connected),
                    onClick = if (hasSession && !needsLogin) onSync else onLogin)
                if (hasSession) Action("Sign in again", Modifier.weight(1f), onClick = onLogin)
            }
            if (hasSession) TextButton(enabled = !busy, onClick = { confirmLogout = true }, contentPadding = PaddingValues(0.dp)) {
                Text(if (connected) "Sign out on phone & watch" else "Clear saved phone session", color = Muted, fontSize = 12.sp)
            }
        }
        Surface(onClick = { showDiagnostics = !showDiagnostics }, color = Panel, shape = RoundedCornerShape(12.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Tune, null, tint = Muted, modifier = Modifier.size(18.dp))
                Text("Connection & diagnostics", Modifier.padding(start = 10.dp).weight(1f), fontSize = 13.sp)
                Icon(if (showDiagnostics) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (showDiagnostics) "Collapse diagnostics" else "Expand diagnostics", tint = Muted)
            }
        }
        AnimatedVisibility(showDiagnostics) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Check whether the watch received your session and commands.", color = Muted, fontSize = 12.sp)
                Action("Check watch", Modifier.fillMaxWidth(), enabled = connected, onClick = onDiagnostics)
                DiagnosticText("WATCH STATUS", diagnostics)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Action("Copy status", Modifier.weight(1f), onClick = { onCopy("Diagnostics", diagnostics) })
                    Action("Share status", Modifier.weight(1f), onClick = { onShare("Diagnostics", diagnostics) })
                }
                DiagnosticText("LAST RESPONSE", response)
                DiagnosticText("BRIDGE LOG", logs)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Action("Copy log", Modifier.weight(1f), onClick = { onCopy("Logs", logs) })
                    Action("Share log", Modifier.weight(1f), onClick = { onShare("Logs", logs) })
                }
                TextButton(onClick = onClearLogs) { Text("Clear log", color = Muted) }
                Text("App channel: ${BuildConfig.APPLICATION_ID}\nPhone and watch must use the same channel.", color = Muted, fontSize = 11.sp)
            }
        }
        Text("SIMP MUSIC  /  ${BuildConfig.VERSION_NAME}", color = Muted.copy(alpha = 0.6f), fontSize = 9.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun SectionLabel(number: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(number, color = Electric, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
internal fun Action(text: String, modifier: Modifier = Modifier, primary: Boolean = false,
    enabled: Boolean = true, containerColor: Color? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(10.dp), modifier = modifier,
        color = containerColor ?: if (primary) Electric else Panel,
        contentColor = (if (primary) Ink else MaterialTheme.colorScheme.onSurface).copy(alpha = if (enabled) 1f else 0.4f)) {
        Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun Control(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, highlighted: Boolean = false, height: Int = 48) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(14.dp),
        color = if (highlighted) Electric else Panel, modifier = modifier.height(height.dp).widthIn(min = 48.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = (if (highlighted) Ink else Color.White).copy(alpha = if (enabled) 1f else 0.35f),
                modifier = Modifier.size(if (highlighted) 36.dp else 24.dp))
        }
    }
}

@Composable
private fun DiagnosticText(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = Blue, fontSize = 10.sp, letterSpacing = 1.sp)
        Text(text.ifBlank { "No data yet." }, color = Muted, fontSize = 11.sp,
            fontFamily = FontFamily.Monospace, lineHeight = 17.sp)
    }
}

@Composable
private fun WatchArtwork(modifier: Modifier, connected: Boolean) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(Color(0xFF283747), Offset(w * .27f, 0f), Size(w * .46f, h), CornerRadius(w * .16f))
        drawCircle(Ink, w * .48f, Offset(w * .5f, h * .5f))
        drawCircle(Blue.copy(alpha = .4f), w * .48f, Offset(w * .5f, h * .5f), style = Stroke(2.dp.toPx()))
        drawCircle(Blue.copy(alpha = .35f), w * .37f, Offset(w * .5f, h * .5f),
            style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx()))))
        val heights = listOf(.12f, .24f, .36f, .2f, .28f)
        heights.forEachIndexed { index, height ->
            val x = w * (.28f + index * .11f)
            drawLine(if (connected) Electric else Muted, Offset(x, h * .5f - w * height / 2),
                Offset(x, h * .5f + w * height / 2), strokeWidth = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}
