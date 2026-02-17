package com.maxrave.simpmusic.wear.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.google.android.gms.wearable.Wearable
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.repository.AccountRepository
import com.maxrave.domain.repository.CommonRepository
import com.maxrave.simpmusic.wear.auth.WearAccountManager
import com.maxrave.simpmusic.wear.ui.components.WearList
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

private const val PATH_SYNC_LOGIN_FROM_PHONE = "/simpmusic/login/sync"

@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    openLogin: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val dataStoreManager: DataStoreManager = remember { GlobalContext.get().get() }
    val accountRepository: AccountRepository = remember { GlobalContext.get().get() }
    val commonRepository: CommonRepository = remember { GlobalContext.get().get() }
    val wearAccountManager =
        remember {
            WearAccountManager(
                context = context.applicationContext,
                dataStoreManager = dataStoreManager,
                accountRepository = accountRepository,
                commonRepository = commonRepository,
            )
        }

    val accountsFlow =
        remember(accountRepository) {
            accountRepository.getGoogleAccounts().map { it.orEmpty() }
        }
    val accounts by accountsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val loggedIn =
        dataStoreManager.loggedIn.collectAsStateWithLifecycle(initialValue = DataStoreManager.FALSE).value ==
            DataStoreManager.TRUE
    val proxyFallbackEnabled =
        dataStoreManager.streamProxyFallback.collectAsStateWithLifecycle(initialValue = DataStoreManager.TRUE).value ==
            DataStoreManager.TRUE
    val explicitContentEnabled =
        dataStoreManager.explicitContentEnabled.collectAsStateWithLifecycle(initialValue = DataStoreManager.TRUE).value ==
            DataStoreManager.TRUE
    val normalizeVolumeEnabled =
        dataStoreManager.normalizeVolume.collectAsStateWithLifecycle(initialValue = DataStoreManager.FALSE).value ==
            DataStoreManager.TRUE
    val skipSilentEnabled =
        dataStoreManager.skipSilent.collectAsStateWithLifecycle(initialValue = DataStoreManager.FALSE).value ==
            DataStoreManager.TRUE
    val saveRecentQueueEnabled =
        dataStoreManager.saveRecentSongAndQueue.collectAsStateWithLifecycle(initialValue = DataStoreManager.FALSE).value ==
            DataStoreManager.TRUE
    val endlessQueueEnabled =
        dataStoreManager.endlessQueue.collectAsStateWithLifecycle(initialValue = DataStoreManager.FALSE).value ==
            DataStoreManager.TRUE
    val restorePlaybackStateEnabled =
        dataStoreManager.saveStateOfPlayback.collectAsStateWithLifecycle(initialValue = DataStoreManager.FALSE).value ==
            DataStoreManager.TRUE
    val sendBackToGoogleEnabled =
        dataStoreManager.sendBackToGoogle.collectAsStateWithLifecycle(initialValue = DataStoreManager.FALSE).value ==
            DataStoreManager.TRUE
    val sponsorBlockEnabled =
        dataStoreManager.sponsorBlockEnabled.collectAsStateWithLifecycle(initialValue = DataStoreManager.FALSE).value ==
            DataStoreManager.TRUE
    val preferVideoEnabled =
        dataStoreManager.watchVideoInsteadOfPlayingAudio.collectAsStateWithLifecycle(initialValue = DataStoreManager.FALSE).value ==
            DataStoreManager.TRUE
    val killServiceOnExitEnabled =
        dataStoreManager.killServiceOnExit.collectAsStateWithLifecycle(initialValue = DataStoreManager.TRUE).value ==
            DataStoreManager.TRUE
    val backupDownloadedEnabled =
        dataStoreManager.backupDownloaded.collectAsStateWithLifecycle(initialValue = DataStoreManager.FALSE).value ==
            DataStoreManager.TRUE
    val autoCheckForUpdatesEnabled =
        dataStoreManager.autoCheckForUpdates.collectAsStateWithLifecycle(initialValue = DataStoreManager.TRUE).value ==
            DataStoreManager.TRUE
    val spotifyLyricsEnabled =
        dataStoreManager.spotifyLyrics.collectAsStateWithLifecycle(initialValue = DataStoreManager.FALSE).value ==
            DataStoreManager.TRUE
    val spotifyCanvasEnabled =
        dataStoreManager.spotifyCanvas.collectAsStateWithLifecycle(initialValue = DataStoreManager.FALSE).value ==
            DataStoreManager.TRUE
    val spotifySpdc =
        dataStoreManager.spdc.collectAsStateWithLifecycle(initialValue = "").value
    val spotifyLinked = spotifySpdc.isNotBlank()

    fun requestPhoneSync() {
        val appCtx = context.applicationContext
        val nodeClient = Wearable.getNodeClient(appCtx)
        val messageClient = Wearable.getMessageClient(appCtx)
        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    Toast.makeText(context, "No connected phone", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, PATH_SYNC_LOGIN_FROM_PHONE, ByteArray(0))
                }
                Toast.makeText(context, "Requested phone session sync", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener {
                Toast.makeText(context, "Failed to contact phone", Toast.LENGTH_SHORT).show()
            }
    }

    WearList {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        scope.launch {
                            wearAccountManager.logOutAll()
                            Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = loggedIn || accounts.isNotEmpty(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Log out all")
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (loggedIn) "Signed in" else "Guest",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { requestPhoneSync() })
                        .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Sync, contentDescription = "Sync phone session")
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sync session from phone",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Request current phone login cookie",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Account",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (accounts.isEmpty()) {
            item {
                Text(
                    text = "No accounts yet.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Tap Add account to sign in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(accounts.size) { index ->
                val acc = accounts[index]
                val title = buildString {
                    if (acc.isUsed) append("In use: ")
                    append(acc.name)
                }
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    wearAccountManager.setUsedAccount(acc)
                                    Toast.makeText(context, "Switched account", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(vertical = 8.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = acc.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = openLogin)
                        .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add account")
                Text(
                    text = "Add account",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Playback & stream",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        item {
            SettingToggleRow(
                title = "Proxy stream fallback",
                subtitle = "Use proxy instances when direct clients fail.",
                enabled = proxyFallbackEnabled,
                onToggle = {
                    scope.launch {
                        dataStoreManager.setStreamProxyFallback(!proxyFallbackEnabled)
                    }
                },
            )
        }

        item {
            SettingToggleRow(
                title = "Explicit content",
                subtitle = "Allow explicit tracks in results.",
                enabled = explicitContentEnabled,
                onToggle = {
                    scope.launch {
                        dataStoreManager.setExplicitContentEnabled(!explicitContentEnabled)
                    }
                },
            )
        }

        item {
            SettingToggleRow(
                title = "Normalize volume",
                subtitle = "Keep loudness more consistent.",
                enabled = normalizeVolumeEnabled,
                onToggle = {
                    scope.launch {
                        dataStoreManager.setNormalizeVolume(!normalizeVolumeEnabled)
                    }
                },
            )
        }

        item {
            SettingToggleRow(
                title = "Skip silence",
                subtitle = "Skip detected silent segments.",
                enabled = skipSilentEnabled,
                onToggle = {
                    scope.launch {
                        dataStoreManager.setSkipSilent(!skipSilentEnabled)
                    }
                },
            )
        }

        item {
            SettingToggleRow(
                title = "Restore playback state",
                subtitle = "Resume playback state after app restart.",
                enabled = restorePlaybackStateEnabled,
                onToggle = {
                    scope.launch {
                        dataStoreManager.setSaveStateOfPlayback(!restorePlaybackStateEnabled)
                    }
                },
            )
        }

        item {
            SettingToggleRow(
                title = "Save recent queue",
                subtitle = "Remember recent track + queue on watch.",
                enabled = saveRecentQueueEnabled,
                onToggle = {
                    scope.launch {
                        dataStoreManager.setSaveRecentSongAndQueue(!saveRecentQueueEnabled)
                    }
                },
            )
        }

        item {
            SettingToggleRow(
                title = "Endless queue",
                subtitle = "Auto-extend queue with related tracks.",
                enabled = endlessQueueEnabled,
                onToggle = {
                    scope.launch {
                        dataStoreManager.setEndlessQueue(!endlessQueueEnabled)
                    }
                },
            )
        }

        item {
            SettingToggleRow(
                title = "Send watch-time to Google",
                subtitle = "Send playback watch-time updates back to YouTube.",
                enabled = sendBackToGoogleEnabled,
                onToggle = {
                    scope.launch {
                        dataStoreManager.setSendBackToGoogle(!sendBackToGoogleEnabled)
                    }
                },
            )
        }

        item {
            SettingToggleRow(
                title = "SponsorBlock",
                subtitle = "Skip community-marked sponsored segments when available.",
                enabled = sponsorBlockEnabled,
                onToggle = {
                    scope.launch {
                        dataStoreManager.setSponsorBlockEnabled(!sponsorBlockEnabled)
                    }
                },
            )
        }

        item {
            SettingToggleRow(
                title = "Prefer video over audio",
                subtitle = "Use video stream path instead of pure audio when possible.",
                enabled = preferVideoEnabled,
                onToggle = {
                    scope.launch {
                        dataStoreManager.setWatchVideoInsteadOfPlayingAudio(!preferVideoEnabled)
                    }
                },
            )
        }

        item {
            SettingToggleRow(
                title = "Kill service on exit",
                subtitle = "Stop background player service when app exits.",
                enabled = killServiceOnExitEnabled,
                onToggle = {
                    scope.launch {
                        dataStoreManager.setKillServiceOnExit(!killServiceOnExitEnabled)
                    }
                },
            )
        }

        item {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Ecosystem",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        item {
            Text(
                text = if (spotifyLinked) "Spotify account linked" else "Spotify not linked (use phone app)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            SettingToggleRow(
                title = "Spotify lyrics",
                subtitle =
                    if (spotifyLinked) {
                        "Enable Spotify lyrics provider fallback."
                    } else {
                        "Requires Spotify login in phone app first."
                    },
                enabled = spotifyLyricsEnabled,
                isInteractive = spotifyLinked,
                onDisabledClick = {
                    Toast.makeText(context, "Link Spotify from phone app first", Toast.LENGTH_SHORT).show()
                },
                onToggle = {
                    scope.launch {
                        dataStoreManager.setSpotifyLyrics(!spotifyLyricsEnabled)
                    }
                },
            )
        }

        item {
            SettingToggleRow(
                title = "Spotify canvas",
                subtitle =
                    if (spotifyLinked) {
                        "Enable Spotify canvas artwork support."
                    } else {
                        "Requires Spotify login in phone app first."
                    },
                enabled = spotifyCanvasEnabled,
                isInteractive = spotifyLinked,
                onDisabledClick = {
                    Toast.makeText(context, "Link Spotify from phone app first", Toast.LENGTH_SHORT).show()
                },
                onToggle = {
                    scope.launch {
                        dataStoreManager.setSpotifyCanvas(!spotifyCanvasEnabled)
                    }
                },
            )
        }

        item {
            SettingToggleRow(
                title = "Backup downloaded media",
                subtitle = "Include downloaded files in backup/restore flows.",
                enabled = backupDownloadedEnabled,
                onToggle = {
                    scope.launch {
                        dataStoreManager.setBackupDownloaded(!backupDownloadedEnabled)
                    }
                },
            )
        }

        item {
            SettingToggleRow(
                title = "Auto-check updates",
                subtitle = "Automatically check app updates in supported channels.",
                enabled = autoCheckForUpdatesEnabled,
                onToggle = {
                    scope.launch {
                        dataStoreManager.setAutoCheckForUpdates(!autoCheckForUpdatesEnabled)
                    }
                },
            )
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    isInteractive: Boolean = true,
    onDisabledClick: (() -> Unit)? = null,
    onToggle: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = {
                        if (isInteractive) {
                            onToggle()
                        } else {
                            onDisabledClick?.invoke()
                        }
                    },
                )
                .padding(vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isInteractive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text =
                    if (isInteractive) {
                        if (enabled) "On" else "Off"
                    } else {
                        "Unavailable"
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
