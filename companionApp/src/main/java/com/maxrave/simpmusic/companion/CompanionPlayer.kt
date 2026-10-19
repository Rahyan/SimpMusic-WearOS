package com.maxrave.simpmusic.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.maxrave.common.WatchPlaybackSnapshot
import com.maxrave.common.WearCompanionBridge as Bridge
import org.json.JSONObject
import kotlinx.coroutines.delay
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun CompanionPlayer(state: WatchPlaybackSnapshot?, connected: Boolean, busy: Boolean, onAction: (JSONObject) -> Unit) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    // A stalled poll may return the same state; freshness must still expire without another response.
    LaunchedEffect(connected, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (connected) { now = SystemClock.elapsedRealtime(); delay(1_000) }
        }
    }
    val receipt = remember(state?.generatedAt) { PlaybackReceipt(System.currentTimeMillis(), SystemClock.elapsedRealtime()) }
    val fresh = connected && playerSnapshotIsFresh(state, now, receipt)
    val active = fresh && !busy && state?.videoId?.isNotBlank() == true
    var showQueue by remember { mutableStateOf(false) }
    fun command(value: String) = onAction(JSONObject().put("command", value))
    if (state?.videoId.isNullOrBlank() && (state?.queueSize ?: 0) == 0) {
        Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Headphones, null, tint = Blue, modifier = Modifier.size(32.dp))
                Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Listen from your wrist.", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(if (connected) "Choose a track on your watch. Your player and queue will appear here."
                        else "Sign in here, then bring your watch online to sync your music.", color = Muted, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        OfflineQueuePanel(state, fresh, busy, onAction)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("ON YOUR WATCH", color = Electric, fontSize = 10.sp, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
            Text(when { !connected -> "OFFLINE"; !fresh -> "CONNECTING"; state?.isBuffering == true -> "BUFFERING"; state?.isPlaying == true -> "PLAYING"; else -> "PAUSED" },
                color = Muted, fontSize = 10.sp)
        }
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)).background(Panel), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.GraphicEq, null, tint = Blue.copy(alpha = .35f), modifier = Modifier.size(96.dp))
            if (!state?.artwork.isNullOrBlank()) AsyncImage(state?.artwork, "Current album artwork",
                Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (state?.isBuffering == true && fresh) CircularProgressIndicator(Modifier.size(36.dp), color = Electric)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(state?.title?.ifBlank { "Choose your next track" } ?: "Your music lives here.", fontSize = 25.sp,
                lineHeight = 29.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(state?.artist?.ifBlank { "Start playback on your watch." } ?: "Connect your watch to see what’s playing.",
                color = Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        CompanionProgress(state, fresh, active, onAction)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Control(Icons.Rounded.SkipPrevious, "Previous track", { command(Bridge.REMOTE_PREVIOUS) }, Modifier.weight(1f), active && state?.canPrevious == true, height = 62)
            Control(if (state?.isPlaying == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (state?.isPlaying == true) "Pause" else "Play", { command(Bridge.REMOTE_PLAY_PAUSE) }, Modifier.weight(1.3f), active, true, 76)
            Control(Icons.Rounded.SkipNext, "Next track", { command(Bridge.REMOTE_NEXT) }, Modifier.weight(1f), active && state?.canNext == true, height = 62)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Control(Icons.AutoMirrored.Rounded.VolumeDown, "Lower watch volume", { command(Bridge.REMOTE_VOLUME_DOWN) }, enabled = fresh && !busy)
            Action("Queue · ${state?.queueSize ?: 0}", Modifier.weight(1f), enabled = fresh, onClick = { showQueue = !showQueue })
            Control(Icons.AutoMirrored.Rounded.VolumeUp, "Raise watch volume", { command(Bridge.REMOTE_VOLUME_UP) }, enabled = fresh && !busy)
        }
        if (connected && !fresh) Text("Waiting for a fresh player update. Controls will return when your watch responds.", color = Muted, fontSize = 12.sp)
        OfflineQueuePanel(state, fresh, busy, onAction)
        if (showQueue && state != null) {
            if (state.queue.isEmpty()) Text("Your queue is empty. Pick a track on the watch.", color = Muted, fontSize = 13.sp)
            state.queue.forEach { track ->
                val current = track.index == state.currentIndex
                fun queueAction(command: String, to: Int? = null) {
                    val payload = JSONObject().put("command", command).put("index", track.index)
                        .put("videoId", track.videoId).put("queueRevision", state.queueRevision)
                    if (to != null) payload.put("to", to)
                    onAction(payload)
                }
                Surface(color = if (current) Blue.copy(alpha = .12f) else Panel, shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (current) "NOW PLAYING" else "${track.index + 1}", color = if (current) Electric else Muted, fontSize = 9.sp)
                        Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(track.artist, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Control(Icons.Rounded.PlayArrow, "Play ${track.title}", { queueAction(Bridge.REMOTE_QUEUE_PLAY) }, enabled = fresh && !busy)
                            Control(Icons.Rounded.ArrowUpward, "Move ${track.title} earlier", { queueAction(Bridge.REMOTE_QUEUE_MOVE, track.index - 1) }, enabled = fresh && !busy && track.index > 0)
                            Control(Icons.Rounded.ArrowDownward, "Move ${track.title} later", { queueAction(Bridge.REMOTE_QUEUE_MOVE, track.index + 1) }, enabled = fresh && !busy && track.index < state.queueSize - 1)
                            Control(Icons.Rounded.Close, "Remove ${track.title}", { queueAction(Bridge.REMOTE_QUEUE_REMOVE) }, enabled = fresh && !busy)
                        }
                    }
                }
            }
            if (state.queue.size < state.queueSize) Text("Showing ${state.queue.size} tracks around the current song. Full queue is on your watch.", color = Muted, fontSize = 11.sp)
        }
    }
}

private fun clock(ms: Long): String = "${ms.coerceAtLeast(0) / 60_000}:${"%02d".format(ms.coerceAtLeast(0) / 1_000 % 60)}"

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CompanionProgress(state: WatchPlaybackSnapshot?, fresh: Boolean, active: Boolean,
    onAction: (JSONObject) -> Unit) {
    val owner = LocalLifecycleOwner.current
    val anchor = remember(state?.videoId, state?.generatedAt) { SystemClock.elapsedRealtime() }
    // Account for transport delay once, then use a monotonic clock even if wall time changes.
    val ageAtReceipt = remember(anchor) { (System.currentTimeMillis() - (state?.generatedAt ?: System.currentTimeMillis())).coerceIn(0, 15_000) }
    var elapsed by remember(anchor) { mutableLongStateOf(ageAtReceipt) }
    var drag by remember(state?.videoId) { mutableStateOf<Float?>(null) }
    LaunchedEffect(anchor, fresh, state?.isPlaying, state?.isBuffering, owner) {
        if (!fresh || state?.isPlaying != true || state.isBuffering) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { elapsed = ageAtReceipt + SystemClock.elapsedRealtime() - anchor; delay(250) }
        }
    }
    val duration = state?.durationMs?.coerceAtLeast(1) ?: 1
    val position = projectedPlaybackPosition(state?.positionMs ?: 0, state?.durationMs ?: 0,
        state?.isPlaying == true, state?.isBuffering == true, elapsed)
    val fraction = (position.toFloat() / duration).coerceIn(0f, 1f)
    val smooth by animateFloatAsState(fraction,
        animationSpec = tween(if (fresh && state?.isPlaying == true && !state.isBuffering) 250 else 0, easing = LinearEasing),
        label = "Watch playhead")
    val colors = SliderDefaults.colors(thumbColor = Electric, activeTrackColor = Electric, inactiveTrackColor = Panel)
    Slider(value = drag ?: smooth, onValueChange = { drag = it }, enabled = active && duration > 1,
        onValueChangeFinished = { drag?.let { onAction(JSONObject().put("command", Bridge.REMOTE_SEEK)
            .put("videoId", state?.videoId).put("positionMs", (it * duration).toLong())) }; drag = null },
        colors = colors,
        thumb = { Box(Modifier.size(14.dp).background(if (active) Electric else Muted, androidx.compose.foundation.shape.CircleShape)) },
        track = { slider -> SliderDefaults.Track(sliderState = slider, enabled = active && duration > 1,
            modifier = Modifier.height(4.dp), colors = colors, thumbTrackGapSize = 0.dp, drawStopIndicator = null) })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(clock(drag?.let { (it * duration).toLong() } ?: position), color = Muted, fontSize = 11.sp)
        Text(clock(state?.durationMs ?: 0), color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun OfflineQueuePanel(state: WatchPlaybackSnapshot?, fresh: Boolean, busy: Boolean, onAction: (JSONObject) -> Unit) {
        state?.offlinePack?.let { pack ->
            Surface(color = Panel, shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("READY TO GO", color = Electric, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(if (pack.total > 0) "${pack.ready} / ${pack.total} tracks offline · ${pack.readySeconds / 60} min"
                        else "Take this queue with you", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(pack.message, color = Muted, fontSize = 12.sp)
                    var chargingOnly by remember(pack.revision, pack.chargingOnly) { mutableStateOf(pack.chargingOnly) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(chargingOnly, { chargingOnly = it }, enabled = fresh && !busy)
                        Text("Watch downloads: Wi-Fi + charging", fontSize = 12.sp)
                    }
                    Action("Keep current queue offline", Modifier.fillMaxWidth(), enabled = fresh && !busy && state.queueSize in 1..100,
                        containerColor = Color(0xFF26333E)) {
                        onAction(JSONObject().put("command", Bridge.REMOTE_PREPARE_OFFLINE).put("queueRevision", state.queueRevision).put("chargingOnly", chargingOnly))
                    }
                    if (pack.total > 0) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Action("Play offline", Modifier.weight(1f), primary = true, enabled = fresh && !busy && pack.ready > 0) {
                            onAction(JSONObject().put("command", Bridge.REMOTE_PLAY_OFFLINE).put("packRevision", pack.revision))
                        }
                        if (pack.ready < pack.total) Action("Retry", Modifier.weight(1f), enabled = fresh && !busy,
                            containerColor = Color(0xFF26333E)) {
                            onAction(JSONObject().put("command", Bridge.REMOTE_RETRY_OFFLINE).put("packRevision", pack.revision).put("chargingOnly", chargingOnly))
                        }
                    }
                    if (state.queueSize > 100) Text("Choose a queue of up to 100 tracks to prepare offline.", color = Muted, fontSize = 11.sp)
                }
            }
        }
}
