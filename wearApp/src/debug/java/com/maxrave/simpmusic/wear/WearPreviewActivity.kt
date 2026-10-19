package com.maxrave.simpmusic.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.compose.ui.Alignment
import com.maxrave.domain.mediaservice.handler.RepeatState
import kotlinx.coroutines.delay
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeText
import com.maxrave.simpmusic.wear.ui.components.*
import com.maxrave.simpmusic.wear.ui.theme.WearTheme
import com.maxrave.simpmusic.wear.ui.util.openWatchAudioOutput

/** Debug-only round-screen fixture using production components. No media commands are sent. */
class WearPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra("mode") ?: "queue"
        setContent { WearTheme { AppScaffold(timeText = { TimeText() }) { Preview(mode) } } }
    }
    @Composable private fun Preview(mode: String) {
        var screen by remember { mutableStateOf(mode) }
        var playing by remember { mutableStateOf(true) }
        var liked by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val drawer = remember { PlayerDrawerState() }
        val drawerClosed by remember { derivedStateOf { drawer.fraction == 0f && !drawer.dragging } }
        val drawerList = rememberLazyListState()
        var shuffled by remember { mutableStateOf(false) }
        var repeated by remember { mutableStateOf(false) }
        var volumeFeedback by remember { mutableStateOf<Float?>(null) }
        var volumeChangedAt by remember { mutableLongStateOf(0L) }
        LaunchedEffect(volumeChangedAt) {
            if (volumeChangedAt > 0) { delay(1_500); volumeFeedback = null }
        }
        if (screen.startsWith("player")) {
            Box(Modifier.fillMaxSize()) {
                Image(painterResource(R.drawable.preview_album), null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .58f)))
                PlayerDrawer(drawer, drawerList, MaterialTheme.colorScheme.surfaceContainerHigh, player = { drag ->
                WatchPlayerFace(if (mode == "player-long") "A moment between everything" else "Over the Horizon", "Samsung", null, true,
                    playing, mode == "player-loading", if (mode == "player-retry") "Taking longer" else "Downloaded",
                    .43f, "1:32 / 3:34", true, true, true, true,
                    {}, { playing = !playing }, {}, { screen = "seek" }, { screen = "queue" }, { drawer.moveTo(scope, true) },
                    isLiked = liked, onLike = { liked = !liked }, onVolume = { screen = "volume" },
                    onOutput = { this@WearPreviewActivity.openWatchAudioOutput() }, drawerModifier = drag,
                    volumeFraction = volumeFeedback,
                    modifier = Modifier.rotaryVolume(drawerClosed) { current, maximum ->
                        volumeFeedback = current.toFloat() / maximum
                        volumeChangedAt = android.os.SystemClock.elapsedRealtime()
                    })
                }) {
                    item { Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                        androidx.wear.compose.material3.Icon(Icons.Filled.Tune, "Controls", Modifier.size(24.dp))
                    } }
                    item { PlayerControls(shuffled, if (repeated) RepeatState.All else RepeatState.None,
                        false, true, "Download", true, true,
                        { shuffled = !shuffled }, { repeated = !repeated }, { screen = "queue" }, {}, { screen = "volume" }, {}) }
                    items(6) { Text("Preview queue item ${it + 1}", Modifier.padding(vertical = 8.dp)) }
                }
            }
            return
        }
        if (screen == "volume") {
            com.maxrave.simpmusic.wear.ui.screens.VolumeScreen { screen = "player" }
            return
        }
        if (screen == "seek") {
            com.maxrave.simpmusic.wear.ui.screens.SeekScreen(92_000, 214_000, { screen = "player" }, { screen = "player" })
            return
        }
        WearList {
            if (mode == "offline") {
                item { WearScreenHeader("Downloads", onBack = { finish() }) }
                item { OfflineQueueCard(com.maxrave.common.WatchOfflinePack("preview", 12, 8, 1820, true,
                    "Downloads wait for Wi-Fi and charging"), {}, {}) }
                return@WearList
            }
            item { WearScreenHeader(if (mode == "states") "Your library" else "Up next", "Design preview", onBack = { finish() }) }
            if (mode == "states") {
                item { WearEmptyState("A little quiet here", "Find a song in Discover, then add it to your library.") }
                item { WearLoadingState("Finding your music…") }
                item { WearErrorState("Couldn’t connect. Your downloaded music is still available.", "Try again", {}) }
            } else {
                items(4) { i -> WearTrackRow(listOf("A moment between everything", "Night drive", "Somewhere, slowly", "The other side of morning")[i],
                    "SimpMusic · Design preview", null, current = i == 0, onClick = {}) }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickActionChip("Library", {}, Modifier.weight(1f))
                    QuickActionChip("Downloads", {}, Modifier.weight(1f))
                } }
            }
        }
    }
}
