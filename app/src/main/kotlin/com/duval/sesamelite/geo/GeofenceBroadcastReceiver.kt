package com.duval.sesamelite.geo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.duval.sesamelite.notification.NotificationHelper

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return

        if (event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            event.triggeringGeofences?.forEach { geofence ->
                val id = geofence.requestId
                if (id != GeofenceManager.SAFETY_GEOFENCE_ID) {
                    NotificationHelper.sendArrivalNotification(context, id)
                }
            }
        } else if (event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            event.triggeringGeofences?.forEach { geofence ->
                if (geofence.requestId == GeofenceManager.SAFETY_GEOFENCE_ID) {
                    // Safety fence exit: re-register geofences with updated active set
                    GeofenceManager.reRegisterAll(context)
                }
            }
        }
    }
}
