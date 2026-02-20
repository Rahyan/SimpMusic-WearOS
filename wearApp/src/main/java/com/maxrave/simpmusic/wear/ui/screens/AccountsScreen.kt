package com.maxrave.simpmusic.wear.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.composed
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
import com.maxrave.simpmusic.wear.ui.theme.ACCENT_PRESET_DEFAULT
import com.maxrave.simpmusic.wear.ui.theme.KEY_WEAR_ACCENT_PRESET
import com.maxrave.simpmusic.wear.ui.theme.KEY_WEAR_BATTERY_SAVER_MODE
import com.maxrave.simpmusic.wear.ui.theme.KEY_WEAR_PHONE_OFFLOAD_CONTROLS
import com.maxrave.simpmusic.wear.ui.theme.KEY_WEAR_PLAYER_STYLE
import com.maxrave.simpmusic.wear.ui.theme.WEAR_PLAYER_STYLE_DEFAULT
import com.maxrave.simpmusic.wear.ui.theme.accentPresetLabel
import com.maxrave.simpmusic.wear.ui.theme.nextAccentPreset
import com.maxrave.simpmusic.wear.ui.theme.nextWearPlayerStyle
import com.maxrave.simpmusic.wear.ui.theme.wearPlayerStyleLabel
import com.maxrave.simpmusic.wear.ui.components.WearEmptyState
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
    val accentPreset =
        dataStoreManager.getString(KEY_WEAR_ACCENT_PRESET)
            .collectAsStateWithLifecycle(initialValue = ACCENT_PRESET_DEFAULT)
            .value ?: ACCENT_PRESET_DEFAULT
    val playerStyle =
        dataStoreManager.getString(KEY_WEAR_PLAYER_STYLE)
            .collectAsStateWithLifecycle(initialValue = WEAR_PLAYER_STYLE_DEFAULT)
            .value ?: WEAR_PLAYER_STYLE_DEFAULT
    val wearBatterySaverEnabled =
        dataStoreManager.getString(KEY_WEAR_BATTERY_SAVER_MODE)
            .collectAsStateWithLifecycle(initialValue = DataStoreManager.FALSE)
            .value == DataStoreManager.TRUE
    val phoneOffloadEnabled =
        dataStoreManager.getString(KEY_WEAR_PHONE_OFFLOAD_CONTROLS)
            .collectAsStateWithLifecycle(initialValue = DataStoreManager.FALSE)
            .value == DataStoreManager.TRUE

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
                        .settingCardModifier(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Sync,
                    contentDescription = "Sync phone session",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sync session from phone",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "Request current phone login cookie",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        item {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            val next = nextAccentPreset(accentPreset)
                            scope.launch {
                                dataStoreManager.putString(KEY_WEAR_ACCENT_PRESET, next)
                                Toast
                                    .makeText(
                                        context,
                                        "Accent: ${accentPresetLabel(next)}",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            }
                        }.settingCardModifier(),
            ) {
                Text(
                    text = "Material You accent",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "${accentPresetLabel(accentPreset)} • tap to cycle",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            val next = nextWearPlayerStyle(playerStyle)
                            scope.launch {
                                dataStoreManager.putString(KEY_WEAR_PLAYER_STYLE, next)
                                Toast
                                    .makeText(
                                        context,
                                        "Player: ${wearPlayerStyleLabel(next)}",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            }
                        }.settingCardModifier(),
            ) {
                Text(
                    text = "Now playing layout",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "${wearPlayerStyleLabel(playerStyle)} • tap to switch",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
                WearEmptyState(
                    title = "No accounts yet.",
                    hint = "Tap Add account to sign in.",
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
                            .settingCardModifier(),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = acc.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
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
                        .settingCardModifier(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Add account",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "Add account",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
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
                title = "Wear battery saver",
                subtitle = "Reduce visuals and lyric update work to save battery.",
                enabled = wearBatterySaverEnabled,
                onToggle = {
                    scope.launch {
                        dataStoreManager.putString(
                            KEY_WEAR_BATTERY_SAVER_MODE,
                            if (wearBatterySaverEnabled) DataStoreManager.FALSE else DataStoreManager.TRUE,
                        )
                    }
                },
            )
        }

        item {
            SettingToggleRow(
                title = "Auto offload to phone",
                subtitle = "Route play/next/previous to phone when connected (override in player).",
                enabled = phoneOffloadEnabled,
                onToggle = {
                    scope.launch {
                        dataStoreManager.putString(
                            KEY_WEAR_PHONE_OFFLOAD_CONTROLS,
                            if (phoneOffloadEnabled) DataStoreManager.FALSE else DataStoreManager.TRUE,
                        )
                    }
                },
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
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                    shape = RoundedCornerShape(16.dp),
                ).border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primaryDim.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(16.dp),
                )
                .clickable(
                    onClick = {
                        if (isInteractive) {
                            onToggle()
                        } else {
                            onDisabledClick?.invoke()
                        }
                    },
                )
                .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (isInteractive) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    },
            )
            Text(
                text =
                    if (isInteractive) {
                        if (enabled) "On" else "Off"
                    } else {
                        "Unavailable"
                    },
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (isInteractive && enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                    },
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun Modifier.settingCardModifier(): Modifier =
    composed {
        this
            .background(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                shape = RoundedCornerShape(16.dp),
            ).border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primaryDim.copy(alpha = 0.75f),
                shape = RoundedCornerShape(16.dp),
            ).padding(horizontal = 10.dp, vertical = 10.dp)
    }
