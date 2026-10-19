package com.maxrave.simpmusic.wear.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.Text
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.mediaservice.handler.DownloadHandler
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.simpmusic.wear.offline.OfflinePlaylistState
import com.maxrave.simpmusic.wear.offline.OfflinePlaylists
import com.maxrave.simpmusic.wear.ui.components.WearEmptyState
import com.maxrave.simpmusic.wear.ui.components.WearList
import com.maxrave.simpmusic.wear.ui.components.WearScreenHeader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext

@Composable
fun KeepPlaylistOffline(id: String, title: String, tracks: List<Track>) {
    val context = LocalContext.current
    val store = remember { OfflinePlaylists(context) }
    val scope = rememberCoroutineScope()
    val state by remember(store) { store.observe() }.collectAsStateWithLifecycle(OfflinePlaylistState())
    val charging by remember(store) { store.observePolicy() }.collectAsStateWithLifecycle(true)
    val saved = state.playlists.any { it.id == id }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = !busy && (saved || tracks.size in 1..100), onClick = {
            busy = true
            scope.launch {
                try { if (saved) store.unpin(id) else store.pin(id, title, tracks) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (invalid: IllegalArgumentException) { Toast.makeText(context, invalid.message, Toast.LENGTH_SHORT).show() }
                catch (_: Exception) { Toast.makeText(context, "Couldn’t update offline playlists.", Toast.LENGTH_SHORT).show() }
                finally { busy = false }
            }
        }) { Text(if (saved) "Stop keeping offline" else "Keep offline", maxLines = 2) }
        Text(when {
            saved -> "Stopping sync keeps your downloaded files."
            tracks.size > 100 -> "Offline playlists support up to 100 tracks."
            charging -> "Updates on Wi-Fi while charging"
            else -> "Updates when connected"
        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun OfflinePlaylistsScreen(handler: MediaPlayerHandler, onBack: () -> Unit, openNowPlaying: () -> Unit) {
    val context = LocalContext.current
    val store = remember { OfflinePlaylists(context) }
    val scope = rememberCoroutineScope()
    val state by remember(store) { store.observe() }.collectAsStateWithLifecycle(OfflinePlaylistState())
    val charging by remember(store) { store.observePolicy() }.collectAsStateWithLifecycle(true)
    val downloads: DownloadHandler = remember { GlobalContext.get().get() }
    val owner = LocalLifecycleOwner.current
    val ready by produceState(emptySet<String>(), downloads, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { value = withContext(Dispatchers.IO) { downloads.completedAudioIds() }; delay(5_000) }
        }
    }
    fun act(block: suspend () -> Unit) {
        scope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { Toast.makeText(context, "Couldn’t update offline playlists. Try again.", Toast.LENGTH_SHORT).show() }
        }
    }

    WearList {
        item { WearScreenHeader("Offline playlists", onBack = onBack) }
        item { Text(if (charging) "Updates on Wi-Fi while charging" else "Updates when connected",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (state.message.isNotBlank()) item { Text(state.message, style = MaterialTheme.typography.bodySmall) }
        if (state.playlists.isEmpty()) item { WearEmptyState("No saved playlists", "Open a playlist and choose Keep offline.") }
        items(state.playlists.size, key = { state.playlists[it].id }) { index ->
            val playlist = state.playlists[index]
            val count = playlist.tracks.count { it.videoId in ready }
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer,
                RoundedCornerShape(16.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(playlist.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text("$count / ${playlist.tracks.size} tracks ready", style = MaterialTheme.typography.bodySmall)
                Text(playlist.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = count > 0, onClick = {
                    act { store.play(playlist.id, handler); openNowPlaying() }
                }) { Text("Play offline") }
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { act { store.unpin(playlist.id) } }) {
                    Text("Stop syncing", maxLines = 2)
                }
            }
        }
        item { OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = state.playlists.isNotEmpty(),
            onClick = { act { store.requestRefresh() } }) { Text("Sync playlists") } }
        item { Text("Download settings", style = MaterialTheme.typography.titleSmall) }
        item { OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
            act { downloads.setWatchDownloadPolicy(!store.chargingOnly()) }
        }) { Text(if (charging) "Wi-Fi + charging" else "Any connection", maxLines = 2) } }
        item { Text("Applies to all watch downloads.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
            val next = when (state.budgetMb) { 256 -> 512; 512 -> 1024; else -> 256 }
            act { store.settings(next, state.removeStale) }
        }) { Text("Est. budget: ${state.budgetMb} MB", maxLines = 2) } }
        item { Text("For new requests; existing downloads stay. Manual downloads are excluded.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
            act { store.settings(state.budgetMb, !state.removeStale) }
        }) { Text("Auto cleanup: ${if (state.removeStale) "On" else "Off"}", maxLines = 2) } }
        item { Text("Removes sync-owned tracks no longer in a saved playlist. Manual downloads and queued music stay.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
