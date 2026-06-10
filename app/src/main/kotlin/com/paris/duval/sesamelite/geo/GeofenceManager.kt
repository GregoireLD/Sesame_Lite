package com.paris.duval.sesamelite.geo

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.paris.duval.sesamelite.crypto.CryptoManager
import com.paris.duval.sesamelite.crypto.DecryptionResult
import com.paris.duval.sesamelite.data.db.AppDatabase
import com.paris.duval.sesamelite.data.model.AccessCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Android geofencing (up to 100 registered geofences via Google Play Services).
 *
 * Simple mode (≤ 90 non-silenced entries with valid coords): register them all.
 * Dynamic mode (> 90): register the nearest 90 by near-edge distance
 *   max(0, distanceToCentre − radius), plus one safety geofence that triggers
 *   a re-evaluation when the user travels beyond the covered area — 91
 *   total, below the platform limit.
 *
 * Geofences are wiped on reboot — BootReceiver calls reRegisterAll().
 * Silenced entries are excluded entirely (no geofence, no muted notification).
 */
object GeofenceManager {

    const val SAFETY_GEOFENCE_ID = "com.paris.duval.sesamelite.safety"
    /** Android's hard limit: 100 geofences per app. */
    private const val MAX_GEOFENCES = 100

    /** Safety margin below the platform cap (room for the safety fence + slack). */
    private const val GEOFENCE_MAX_BUDGET = MAX_GEOFENCES - 9 // 100 - 9 = 91

    /**
    * Dynamic mode adds one safety geofence on top of the active set, so the set
    * must leave a slot free. Budgeting 91 (well under the 100 hard cap) avoids
    * GEOFENCE_TOO_MANY_GEOFENCES — and since removeAll() has already run before
    * the re-add, an overflow would otherwise leave the user with zero geofences.
    * (The iOS version leaves the same headroom: 15 + 1 ≤ 20.)
    */
    private const val ACTIVE_SET_SIZE = GEOFENCE_MAX_BUDGET - 1   // 90 + 1 safety = 91
    private const val DYNAMIC_THRESHOLD = ACTIVE_SET_SIZE

    fun reRegisterAll(context: Context, onComplete: (() -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entries = AppDatabase.get(context).accessCodeDao().getAll()
                val location =
                    if (eligibleEntries(entries).size > DYNAMIC_THRESHOLD)
                        getCurrentLocationOrNull(context)
                    else null
                registerAll(context, entries, currentLocation = location)
            } finally {
                onComplete?.invoke()
            }
        }
    }

    suspend fun registerAll(
        context: Context,
        entries: List<AccessCode>,
        currentLocation: Location?
    ) {
        if (!hasBackgroundLocationPermission(context)) return

        val client = LocationServices.getGeofencingClient(context)
        val pi = buildPendingIntent(context)

        // Collect only non-silenced entries with valid (non-zero) coordinates
        val eligible = eligibleEntries(entries)

        val toMonitor: List<AccessCode>
        val addSafety: Boolean

        if (eligible.size <= DYNAMIC_THRESHOLD) {
            toMonitor = eligible
            addSafety = false
        } else {
            // Sort by near-edge distance to current location
            toMonitor = sortByNearEdge(eligible, currentLocation).take(ACTIVE_SET_SIZE)
            addSafety = currentLocation != null
        }

        val geofences = mutableListOf<Geofence>()
        for (entry in toMonitor) {
            val lat = decryptDouble(entry.encryptedLatitude) ?: continue
            val lon = decryptDouble(entry.encryptedLongitude) ?: continue
            geofences.add(buildGeofence(entry.id, lat, lon, entry.radiusMeters.toFloat()))
        }

        // Add safety geofence for dynamic mode
        if (addSafety && currentLocation != null && toMonitor.isNotEmpty()) {
            val safetyGeofence = buildSafetyGeofence(currentLocation, toMonitor)
            if (safetyGeofence != null) geofences.add(safetyGeofence)
        }

        removeAll(client, context)
        if (geofences.isEmpty()) return

        try {
            val request = GeofencingRequest.Builder()
                .setInitialTrigger(0)
                .addGeofences(geofences)
                .build()
            client.addGeofences(request, pi).await()
        } catch (e: Exception) {
            // A failure here means NO geofences are registered (removeAll has
            // already run) — arrival alerts are dead. Never swallow it silently.
            android.util.Log.w(
                "GeofenceManager",
                "addGeofences failed for ${geofences.size} fence(s)", e
            )
        }
    }

    suspend fun removeEntry(context: Context, entryId: String) {
        if (!hasBackgroundLocationPermission(context)) return
        try {
            LocationServices.getGeofencingClient(context)
                .removeGeofences(listOf(entryId)).await()
        } catch (_: Exception) {
        }
    }

    private suspend fun removeAll(client: GeofencingClient, context: Context) {
        try { client.removeGeofences(buildPendingIntent(context)).await() } catch (_: Exception) {}
    }

    private fun buildGeofence(
        id: String, lat: Double, lon: Double, radius: Float
    ) = Geofence.Builder()
        .setRequestId(id)
        .setCircularRegion(lat, lon, radius.coerceAtLeast(1f))
        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
        .setExpirationDuration(Geofence.NEVER_EXPIRE)
        .setNotificationResponsiveness(0)
        .build()

    private fun buildSafetyGeofence(
        currentLocation: Location,
        activeSet: List<AccessCode>
    ): Geofence? {
        val farthestNearEdge = activeSet.mapNotNull { entry ->
            val lat = decryptDouble(entry.encryptedLatitude) ?: return@mapNotNull null
            val lon = decryptDouble(entry.encryptedLongitude) ?: return@mapNotNull null
            val results = FloatArray(1)
            Location.distanceBetween(
                currentLocation.latitude, currentLocation.longitude,
                lat, lon, results
            )
            maxOf(0f, results[0] - entry.radiusMeters.toFloat())
        }.maxOrNull() ?: return null

        val safetyRadius = (farthestNearEdge * 0.95f).coerceAtLeast(100f)
        return Geofence.Builder()
            .setRequestId(SAFETY_GEOFENCE_ID)
            .setCircularRegion(currentLocation.latitude, currentLocation.longitude, safetyRadius)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setNotificationResponsiveness(0)
            .build()
    }

    private fun eligibleEntries(entries: List<AccessCode>): List<AccessCode> =
        entries.filter { entry ->
            if (entry.isSilenced) return@filter false
            val lat = decryptDouble(entry.encryptedLatitude) ?: return@filter false
            val lon = decryptDouble(entry.encryptedLongitude) ?: return@filter false
            lat != 0.0 || lon != 0.0
        }

    private suspend fun getCurrentLocationOrNull(context: Context): Location? {
        if (!hasBackgroundLocationPermission(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val cts = CancellationTokenSource()
        return try {
            withTimeoutOrNull(8_000L) {
                client.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cts.token
                ).await()
            }
        } catch (e: Exception) {
            android.util.Log.w("GeofenceManager", "getCurrentLocation failed", e)
            null
        } finally {
            cts.cancel()
        }
    }

    private fun sortByNearEdge(
        entries: List<AccessCode>, location: Location?
    ): List<AccessCode> {
        if (location == null) return entries
        return entries.sortedBy { entry ->
            val lat = decryptDouble(entry.encryptedLatitude) ?: return@sortedBy Float.MAX_VALUE.toDouble()
            val lon = decryptDouble(entry.encryptedLongitude) ?: return@sortedBy Float.MAX_VALUE.toDouble()
            val results = FloatArray(1)
            Location.distanceBetween(
                location.latitude, location.longitude,
                lat, lon, results
            )
            maxOf(0.0, (results[0] - entry.radiusMeters).toDouble())
        }
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    private fun hasBackgroundLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val bg = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            fine == PackageManager.PERMISSION_GRANTED && bg == PackageManager.PERMISSION_GRANTED
        } else {
            fine == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun decryptDouble(encrypted: String?): Double? {
        encrypted ?: return null
        return when (val r = CryptoManager.decrypt(encrypted)) {
            is DecryptionResult.Success -> r.value.toDoubleOrNull()
            is DecryptionResult.LegacyPlainText -> r.value.toDoubleOrNull()
            else -> null
        }
    }
}
