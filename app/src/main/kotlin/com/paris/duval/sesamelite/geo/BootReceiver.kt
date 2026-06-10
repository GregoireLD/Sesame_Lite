package com.paris.duval.sesamelite.geo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-registers all geofences after device reboot or app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED
            || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val pending = goAsync()
            GeofenceManager.reRegisterAll(context) { pending.finish() }
        }
    }
}
