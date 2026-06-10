package com.paris.duval.sesamelite.share

data class ParsedImport(
    val label: String,
    val address: String?,
    val code: String?,
    val radiusMeters: Double?,
    val locationDetails: String?,
    val comment: String?,
    val isSilenced: Boolean,
    val latitude: Double?,
    val longitude: Double?
)
