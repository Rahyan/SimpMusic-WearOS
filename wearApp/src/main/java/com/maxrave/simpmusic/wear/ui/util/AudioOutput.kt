package com.maxrave.simpmusic.wear.ui.util

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.provider.Settings
import android.widget.Toast

/** System output selection, with Bluetooth settings on watches without the switcher. */
fun Context.openWatchAudioOutput() {
    val chooser = Intent("com.android.settings.panel.action.MEDIA_OUTPUT")
        .putExtra("com.android.settings.panel.extra.PACKAGE_NAME", packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val systemActivity = packageManager.queryIntentActivities(chooser, 0).firstOrNull {
        it.activityInfo.applicationInfo.flags and
            (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }?.activityInfo
    if (systemActivity != null) {
        chooser.component = ComponentName(systemActivity.packageName, systemActivity.name)
        try {
            startActivity(chooser)
            return
        } catch (_: ActivityNotFoundException) {
            // An OS update can remove the resolved activity before launch.
        } catch (_: SecurityException) {
            // Some vendors expose the activity but restrict access.
        }
    }
    try {
        startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("EXTRA_CONNECTION_ONLY", true).putExtra("EXTRA_CLOSE_ON_CONNECT", true)
            .putExtra("android.bluetooth.devicepicker.extra.FILTER_TYPE", 1))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, "Open Bluetooth in watch settings to choose headphones", Toast.LENGTH_LONG).show()
    } catch (_: SecurityException) {
        Toast.makeText(this, "Open Bluetooth in watch settings to choose headphones", Toast.LENGTH_LONG).show()
    }
}
