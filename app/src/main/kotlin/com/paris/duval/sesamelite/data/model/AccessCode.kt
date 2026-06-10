package com.paris.duval.sesamelite.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "access_codes")
data class AccessCode(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val label: String,
    val code: String? = null,              // ENCRYPTED at rest (v1:… string)
    val encryptedAddress: String? = null,  // ENCRYPTED (human-readable address)
    val encryptedLatitude: String? = null, // ENCRYPTED (stringified Double)
    val encryptedLongitude: String? = null,// ENCRYPTED (stringified Double)
    val radiusMeters: Double = 100.0,      // plaintext, geofence radius
    val isSilenced: Boolean = false,       // plaintext, excludes from monitoring
    val locationDetails: String? = null,   // ENCRYPTED ("3rd floor, blue door")
    val comment: String? = null,           // ENCRYPTED (free notes)
    val schemaVersion: Int = 3
)
