package com.paris.duval.sesamelite.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.tasks.await

enum class LocationPermissionState {
    BACKGROUND,      // fine + background — geofencing fully works
    FOREGROUND_ONLY, // fine but no background — geofencing only while app is open
    APPROXIMATE,     // coarse only — geofencing won't work reliably
    NONE             // no location permission at all
}

object LocationHelper {

    fun getLocationPermissionState(context: Context): LocationPermissionState = when {
        hasBackgroundLocationPermission(context) && hasFineLocationPermission(context) ->
            LocationPermissionState.BACKGROUND
        hasFineLocationPermission(context)       -> LocationPermissionState.FOREGROUND_ONLY
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED    -> LocationPermissionState.APPROXIMATE
        else                                     -> LocationPermissionState.NONE
    }

    fun isLocationServicesEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    fun hasFineLocationPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    fun hasBackgroundLocationPermission(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            hasFineLocationPermission(context)
        }
    }

    suspend fun getLastKnownLocation(context: Context): Location? {
        if (!hasFineLocationPermission(context)) return null
        return try {
            LocationServices.getFusedLocationProviderClient(context).lastLocation.await()
        } catch (_: Exception) {
            null
        }
    }

    fun locationFlow(context: Context): Flow<Location> = callbackFlow {
        if (!hasFineLocationPermission(context)) {
            close()
            return@callbackFlow
        }
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10_000L)
            .setMinUpdateIntervalMillis(5_000L)
            .build()

        val callback = object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }

        try {
            client.requestLocationUpdates(request, callback, context.mainLooper)
        } catch (_: SecurityException) {
            close()
            return@callbackFlow
        }

        awaitClose { client.removeLocationUpdates(callback) }
    }.conflate()

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true // pre-API 33 notifications don't need runtime permission
        }
    }

    fun distanceMeters(
        fromLat: Double, fromLon: Double,
        toLat: Double, toLon: Double
    ): Float {
        val results = FloatArray(1)
        Location.distanceBetween(fromLat, fromLon, toLat, toLon, results)
        return results[0]
    }
}
