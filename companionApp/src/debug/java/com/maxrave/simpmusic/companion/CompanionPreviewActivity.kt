package com.maxrave.simpmusic.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.File
import com.maxrave.common.WatchPlaybackSnapshot
import com.maxrave.common.WatchQueueTrack

/** Local design fixture. Excluded from release APKs; never connects to a real watch. */
class CompanionPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        val art = File(cacheDir, "design-preview.png")
        val bitmap = Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888)
        resources.getDrawable(R.drawable.preview_album, theme).apply {
            setBounds(0, 0, 720, 720); draw(Canvas(bitmap))
        }
        art.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val sample = WatchPlaybackSnapshot(
            generatedAt = System.currentTimeMillis(), title = "A moment between everything", artist = "SimpMusic · Design preview",
            artwork = android.net.Uri.fromFile(art).toString(), videoId = "preview",
            isPlaying = true, isBuffering = false, positionMs = 91_000, durationMs = 245_000,
            canNext = true, canPrevious = true, queueRevision = "preview", queueSize = 4, currentIndex = 0,
            queue = listOf("A moment between everything", "Night drive", "Somewhere, slowly", "The other side of morning")
                .mapIndexed { i, title -> WatchQueueTrack(i, "preview-$i", title, "Design preview") }, volume = 5, maxVolume = 10,
            offlinePack = com.maxrave.common.WatchOfflinePack("preview", 12, 8, 1820, true, "Downloads wait for Wi-Fi and charging"),
        )
        setContent { CompanionTheme { Surface {
            CompanionDashboard(watches = listOf("Galaxy Watch4 Classic"), selectedWatch = 0, onSelectWatch = {},
                playback = if (intent.getBooleanExtra("empty", false)) sample.copy(videoId = "", queue = emptyList(), queueSize = 0,
                    isPlaying = false, positionMs = 0, durationMs = 0, canNext = false, canPrevious = false, currentIndex = -1) else sample,
                busy = false, feedback = "Local design preview · controls do not affect devices",
                onPlaybackAction = {}, hasSession = true, needsLogin = false, loginReason = "", diagnostics = "", response = "", logs = "",
                onRefresh = {}, onLogin = {}, onSync = {}, onClearSession = {}, onRemote = {}, onDiagnostics = {}, onCopy = { _, _ -> },
                onShare = { _, _ -> }, onClearLogs = {})
        } } }
    }
}
