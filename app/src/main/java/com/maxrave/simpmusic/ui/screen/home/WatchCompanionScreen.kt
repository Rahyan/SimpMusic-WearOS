package com.maxrave.simpmusic.ui.screen.home

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.maxrave.common.R
import com.maxrave.simpmusic.ui.component.RippleIconButton
import com.maxrave.simpmusic.ui.component.SettingItem
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.WatchCompanionViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchCompanionScreen(
    navController: NavController,
    viewModel: WatchCompanionViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val connection by viewModel.connectionState.collectAsStateWithLifecycle()
    val syncSelection by viewModel.syncSelection.collectAsStateWithLifecycle()
    val autoSyncPolicy by viewModel.autoSyncPolicy.collectAsStateWithLifecycle()
    val autoSyncStats by viewModel.autoSyncStatsSummary.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val lastResponse by viewModel.lastResponse.collectAsStateWithLifecycle()
    val logs by viewModel.logText.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    val controlsLocked = actionState.inProgress || syncing

    Column {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.watch_companion),
                    style = typo.titleMedium,
                )
            },
            navigationIcon = {
                RippleIconButton(resId = R.drawable.baseline_arrow_back_ios_new_24) {
                    navController.navigateUp()
                }
            },
        )

        LazyColumn(modifier = Modifier.padding(horizontal = 12.dp)) {
            item("status_header") {
                Text("Connection", style = typo.labelMedium, modifier = Modifier.padding(vertical = 8.dp))
            }
            item("status_refresh") {
                SettingItem(
                    title = "Refresh watch status",
                    subtitle =
                        if (connection.connected) {
                            "Connected to: ${connection.nodeNames.joinToString()}"
                        } else {
                            "No connected watch found"
                        },
                    onClick = { viewModel.refreshConnection() },
                )
            }
            item("status_diag") {
                SettingItem(
                    title = "Request diagnostics",
                    subtitle = "Fetch watch health, app version, and playback state",
                    isEnable = !controlsLocked,
                    onClick = { viewModel.requestDiagnostics() },
                )
            }
            item("session_sync") {
                SettingItem(
                    title = "Sync phone session + Spotify",
                    subtitle = "Send phone account cookie + Spotify tokens to watch",
                    isEnable = !controlsLocked,
                    onClick = { viewModel.syncSessionAndSpotify() },
                )
            }
            item("action_status") {
                SettingItem(
                    title = if (actionState.inProgress) "Working: ${actionState.activeAction}" else "Bridge action status",
                    subtitle =
                        when {
                            actionState.lastFailure.isNotBlank() -> "Last failure: ${actionState.lastFailure}"
                            actionState.lastSuccess.isNotBlank() -> "Last success: ${actionState.lastSuccess}"
                            else -> "No companion action executed yet."
                        },
                    isEnable = false,
                )
            }
            item("action_retry") {
                SettingItem(
                    title = "Retry last failed action",
                    subtitle = "Repeat the latest failed bridge command",
                    isEnable = actionState.retryAvailable && !actionState.inProgress,
                    onClick = { viewModel.retryLastFailedAction() },
                )
            }

            item("sync_header") {
                Text("Selective sync", style = typo.labelMedium, modifier = Modifier.padding(vertical = 8.dp))
            }
            item("sync_song") {
                SettingItem(
                    title = "Liked songs",
                    subtitle = "Sync liked songs library snapshot",
                    isEnable = !controlsLocked,
                    switch = (syncSelection.songs to { viewModel.setSongsSync(it) }),
                )
            }
            item("sync_playlist") {
                SettingItem(
                    title = "Liked playlists",
                    subtitle = "Sync liked playlists library snapshot",
                    isEnable = !controlsLocked,
                    switch = (syncSelection.playlists to { viewModel.setPlaylistsSync(it) }),
                )
            }
            item("sync_album") {
                SettingItem(
                    title = "Liked albums",
                    subtitle = "Sync liked albums library snapshot",
                    isEnable = !controlsLocked,
                    switch = (syncSelection.albums to { viewModel.setAlbumsSync(it) }),
                )
            }
            item("sync_artist") {
                SettingItem(
                    title = "Followed artists",
                    subtitle = "Sync followed artists library snapshot",
                    isEnable = !controlsLocked,
                    switch = (syncSelection.artists to { viewModel.setArtistsSync(it) }),
                )
            }
            item("sync_podcast") {
                SettingItem(
                    title = "Favorite podcasts",
                    subtitle = "Sync favorite podcasts library snapshot",
                    isEnable = !controlsLocked,
                    switch = (syncSelection.podcasts to { viewModel.setPodcastsSync(it) }),
                )
            }
            item("sync_download") {
                SettingItem(
                    title = "Download metadata",
                    subtitle = "Sync downloaded/downloading state for songs and collections",
                    isEnable = !controlsLocked,
                    switch = (syncSelection.downloads to { viewModel.setDownloadsSync(it) }),
                )
            }
            item("sync_now") {
                SettingItem(
                    title = if (controlsLocked) "Syncing..." else "Sync now",
                    subtitle = "Push selected snapshots to watch",
                    isEnable = !controlsLocked,
                    onClick = { viewModel.syncSelectedData() },
                )
            }
            item("auto_sync_header") {
                Text("Auto-sync policy", style = typo.labelMedium, modifier = Modifier.padding(vertical = 8.dp))
            }
            item("auto_sync_battery_saver") {
                SettingItem(
                    title = "Battery saver mode",
                    subtitle = "Only auto-sync while charging to cut idle drain",
                    isEnable = !controlsLocked,
                    switch = (autoSyncPolicy.batterySaver to { viewModel.setAutoSyncBatterySaver(it) }),
                )
            }
            item("auto_sync_unmetered") {
                SettingItem(
                    title = "Wi-Fi only auto-sync",
                    subtitle = "Skip periodic auto-sync on metered/mobile data",
                    isEnable = !controlsLocked,
                    switch = (autoSyncPolicy.unmeteredOnly to { viewModel.setAutoSyncUnmeteredOnly(it) }),
                )
            }
            item("auto_sync_stats") {
                SettingItem(
                    title = "Auto-sync delta stats",
                    subtitle = autoSyncStats,
                    isEnable = false,
                )
            }
            item("auto_sync_reset") {
                SettingItem(
                    title = "Reset delta sync cache",
                    subtitle = "Force next auto-sync to resend all categories",
                    isEnable = !controlsLocked,
                    onClick = { viewModel.resetAutoSyncDeltaCache() },
                )
            }

            item("remote_header") {
                Text("Remote tools", style = typo.labelMedium, modifier = Modifier.padding(vertical = 8.dp))
            }
            items(
                listOf(
                    "Play/Pause" to { viewModel.remotePlayPause() },
                    "Next track" to { viewModel.remoteNext() },
                    "Previous track" to { viewModel.remotePrevious() },
                    "Volume +" to { viewModel.remoteVolumeUp() },
                    "Volume -" to { viewModel.remoteVolumeDown() },
                    "Queue handoff to watch" to { viewModel.handoffQueueToWatch() },
                ),
            ) { (title, action) ->
                SettingItem(
                    title = title,
                    subtitle = "Send command to watch player",
                    isEnable = !controlsLocked,
                    onClick = action,
                )
            }

            item("logs_header") {
                Text("Diagnostics + logs", style = typo.labelMedium, modifier = Modifier.padding(vertical = 8.dp))
            }
            item("export_logs") {
                SettingItem(
                    title = "Export logs",
                    subtitle = "Share watch diagnostics and bridge logs",
                    onClick = {
                        val body =
                            buildString {
                                appendLine("Diagnostics:")
                                appendLine(if (diagnostics.isBlank()) "No diagnostics yet." else diagnostics)
                                appendLine()
                                appendLine("Last response:")
                                appendLine(if (lastResponse.isBlank()) "No response yet." else lastResponse)
                                appendLine()
                                appendLine("Log:")
                                appendLine(if (logs.isBlank()) "No log yet." else logs)
                            }
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "SimpMusic Watch Companion logs")
                                    putExtra(Intent.EXTRA_TEXT, body)
                                },
                                "Export watch logs",
                            ),
                        )
                    },
                )
            }
            item("clear_logs") {
                SettingItem(
                    title = "Clear logs",
                    subtitle = "Remove local companion history",
                    onClick = { viewModel.clearLogs() },
                )
            }
            item("diag_preview") {
                Text(
                    text = if (diagnostics.isBlank()) "No diagnostics yet." else diagnostics,
                    style = typo.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            item("log_preview") {
                Text(
                    text = if (logs.isBlank()) "No logs yet." else logs,
                    style = typo.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
    }
}
