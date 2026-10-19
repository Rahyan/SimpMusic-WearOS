# Keep rules minimal for now; tighten once Wear UI stabilizes.

# WorkManager persists class names across app updates.
-keep class com.maxrave.simpmusic.wear.offline.**Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
