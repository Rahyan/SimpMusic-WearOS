package com.maxrave.simpmusic.wear.ui.screens

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.layout.*
import com.maxrave.simpmusic.wear.ui.components.rotaryVolume
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.maxrave.simpmusic.wear.ui.components.ThinProgressBar
import com.maxrave.simpmusic.wear.ui.components.WearScreenHeader
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun VolumeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val audioManager =
        remember {
            (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
        }
    var maxVolume by remember { mutableIntStateOf(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)) }
    var currentVolume by remember {
        mutableIntStateOf(
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, maxVolume),
        )
    }

    fun syncVolume() {
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, maxVolume)
    }

    fun setVolume(target: Int) {
        val clamped = target.coerceIn(0, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, 0)
        syncVolume()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                syncVolume()
                delay(800L)
            }
        }
    }

    val fraction = currentVolume.toFloat() / maxVolume.toFloat()
    val percent = (fraction * 100f).roundToInt().coerceIn(0, 100)

    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 28.dp).rotaryVolume(onVolumeChanged = { current, maximum ->
        currentVolume = current; maxVolume = maximum
    }),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
        WearScreenHeader("Volume", onBack = onBack)
        Text("$percent%", style = MaterialTheme.typography.titleLarge)
        ThinProgressBar(fraction)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton({ setVolume(currentVolume - 1) }, Modifier.size(48.dp), enabled = currentVolume > 0) {
                Icon(Icons.Filled.Remove, "Volume down")
            }
            IconButton({ setVolume(currentVolume + 1) }, Modifier.size(48.dp), enabled = currentVolume < maxVolume) {
                Icon(Icons.Filled.Add, "Volume up")
            }
        }
        Text("Turn the bezel to adjust", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
