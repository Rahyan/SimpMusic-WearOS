package com.maxrave.simpmusic.wear.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.wear.compose.material3.*
import coil3.compose.AsyncImage

/** The round Wear player: artwork behind metadata, circular transport and three quick actions. */
@Composable
fun WatchPlayerFace(title: String, artist: String, artwork: String?, immersive: Boolean,
    isPlaying: Boolean, loading: Boolean, status: String, progress: Float, time: String,
    canPrevious: Boolean, canNext: Boolean, canToggle: Boolean, canSeek: Boolean,
    onPrevious: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit,
    onSeek: () -> Unit, onQueue: () -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier,
    isLiked: Boolean = false, onLike: () -> Unit = {}, onOutput: () -> Unit = {},
    onVolume: () -> Unit = {}, canAdjustOutput: Boolean = true, drawerModifier: Modifier = Modifier,
    volumeFraction: Float? = null) {
    val height = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    val compact = height < 210
    val scroll = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.15f || height < 190
    val foreground = Color.White
    val secondary = foreground.copy(alpha = .72f)
    val disc = Color.Black.copy(alpha = if (immersive) .28f else .6f)

    @Composable fun metadata() {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).combinedClickable(onClick = onQueue,
            onClickLabel = "Open queue", onLongClick = onMore, onLongClickLabel = "More controls"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!immersive && !artwork.isNullOrBlank()) AsyncImage(artwork, null,
                contentScale = ContentScale.Crop, modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = foreground, maxLines = 1,
                modifier = Modifier.fillMaxWidth().basicMarquee(), textAlign = TextAlign.Center)
            Text(when { loading -> status; status.startsWith("Taking longer") -> "Tap play to retry"; else -> artist },
                style = MaterialTheme.typography.bodySmall, color = secondary, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
    }
    @Composable fun transport() {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape)
                .clickable(enabled = canPrevious, role = Role.Button, onClick = onPrevious), contentAlignment = Alignment.Center) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(disc), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.SkipPrevious, "Previous track", Modifier.size(24.dp),
                        tint = foreground.copy(alpha = if (canPrevious) 1f else .35f))
                }
            }
            Box(Modifier.size(if (compact) 64.dp else 68.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(2.dp)) {
                    val stroke = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
                    drawCircle(disc, style = stroke)
                    val level = volumeFraction ?: progress
                    if (level > 0f) drawArc(Color(0xFF8BB5FF), -90f, level.coerceIn(0f, 1f) * 360f,
                        false, style = stroke)
                }
                Box(Modifier.size(if (compact) 54.dp else 56.dp).clip(CircleShape).background(disc)
                    .combinedClickable(enabled = canToggle || canSeek, role = Role.Button,
                        onClickLabel = if (isPlaying) "Pause" else "Play or retry",
                        onClick = { if (canToggle) onToggle() },
                        onLongClickLabel = if (canSeek) "Seek to position" else null,
                        onLongClick = if (canSeek) onSeek else null)
                    .semantics { stateDescription = volumeFraction?.let { "Volume ${(it * 100).roundToInt()} percent" } ?: "$time. $status" }, contentAlignment = Alignment.Center) {
                    if (volumeFraction != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.VolumeUp, "Volume", Modifier.size(16.dp), tint = foreground)
                        Text("${(volumeFraction * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge, color = foreground)
                    }
                    else if (loading) CircularProgressIndicator(Modifier.size(28.dp))
                    else Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (isPlaying) "Pause" else "Play or retry", Modifier.size(34.dp),
                        tint = foreground.copy(alpha = if (canToggle) 1f else .35f))
                }
            }
            Box(Modifier.size(48.dp).clip(CircleShape)
                .clickable(enabled = canNext, role = Role.Button, onClick = onNext), contentAlignment = Alignment.Center) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(disc), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.SkipNext, "Next track", Modifier.size(24.dp),
                        tint = foreground.copy(alpha = if (canNext) 1f else .35f))
                }
            }
        }
    }
    @Composable fun actions() {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onOutput, enabled = canAdjustOutput, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Headphones, "Choose audio output", Modifier.size(24.dp),
                    tint = foreground.copy(alpha = if (canAdjustOutput) 1f else .35f))
            }
            IconButton(onLike, modifier = Modifier.size(48.dp)) {
                Icon(if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    if (isLiked) "Remove from liked songs" else "Like song", Modifier.size(26.dp),
                    tint = foreground)
            }
            IconButton(onVolume, enabled = canAdjustOutput, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.VolumeUp, "Volume", Modifier.size(24.dp),
                    tint = foreground.copy(alpha = if (canAdjustOutput) 1f else .35f))
            }
        }
    }
    Box(modifier.fillMaxSize()) {
        if (scroll) {
            WearList(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 36.dp, bottom = 36.dp)) {
                item { metadata() }; item { transport() }; item { actions() }
            }
        } else Column(Modifier.fillMaxSize().padding(start = if (compact) 18.dp else 24.dp,
            end = if (compact) 18.dp else 24.dp, top = if (compact) 30.dp else 36.dp,
            bottom = if (compact) 18.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            metadata()
            transport()
            actions()
        }
        Box(Modifier.align(Alignment.BottomCenter).width(64.dp).height(if (compact) 18.dp else 24.dp).then(drawerModifier)
            .clickable(role = Role.Button, onClickLabel = "More player controls", onClick = onMore)
            .semantics { contentDescription = "More player controls" },
            contentAlignment = Alignment.Center) {
            Box(Modifier.width(28.dp).height(4.dp).clip(CircleShape).background(secondary))
        }
    }
}
