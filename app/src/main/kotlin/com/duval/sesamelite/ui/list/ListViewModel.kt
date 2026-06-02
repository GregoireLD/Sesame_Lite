package com.duval.sesamelite.ui.list

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.duval.sesamelite.data.db.AppDatabase
import com.duval.sesamelite.data.model.AccessCode
import com.duval.sesamelite.data.repository.AccessCodeRepository
import com.duval.sesamelite.geo.GeofenceManager
import com.duval.sesamelite.location.LocationHelper
import com.duval.sesamelite.location.LocationPermissionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortOrder { ByDistance, Alphabetical }

data class ListUiState(
    val entries: List<AccessCode> = emptyList(),
    val searchQuery: String = "",
    val sortOrder: SortOrder = SortOrder.ByDistance,
    val currentLocation: Location? = null,
    val locationPermissionState: LocationPermissionState = LocationPermissionState.NONE,
    val locationServicesEnabled: Boolean = true,
    val hasNotificationPermission: Boolean = true,
    val locationBannerDismissed: Boolean = false,
    val notificationBannerDismissed: Boolean = false
) {
    val hasLocationPermission: Boolean
        get() = locationPermissionState == LocationPermissionState.FOREGROUND_ONLY ||
                locationPermissionState == LocationPermissionState.BACKGROUND
}

class ListViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).accessCodeDao()
    val repo = AccessCodeRepository(dao)

    private val _sortOrder = MutableStateFlow(SortOrder.ByDistance)
    private val _searchQuery = MutableStateFlow("")
    private val _location = MutableStateFlow<Location?>(null)
    private val _locationPermState = MutableStateFlow(LocationPermissionState.NONE)
    private val _locationServicesEnabled = MutableStateFlow(true)
    private val _hasNotificationPerm = MutableStateFlow(true)
    private val _locationBannerDismissed = MutableStateFlow(false)
    private val _notificationBannerDismissed = MutableStateFlow(false)

    val uiState: StateFlow<ListUiState> = combine(
        dao.getAllFlow(),
        _searchQuery,
        _sortOrder,
        _location,
        combine(_locationPermState, _locationServicesEnabled, _hasNotificationPerm,
                _locationBannerDismissed, _notificationBannerDismissed) {
            permState, locEnabled, hasNotif, locDismissed, notifDismissed ->
            listOf(permState, locEnabled, hasNotif, locDismissed, notifDismissed)
        }
    ) { entries, query, sort, loc, perms ->
        val permState = perms[0] as LocationPermissionState
        val locEnabled = perms[1] as Boolean
        val hasNotif = perms[2] as Boolean
        val locDismissed = perms[3] as Boolean
        val notifDismissed = perms[4] as Boolean
        val hasFine = permState == LocationPermissionState.FOREGROUND_ONLY ||
                      permState == LocationPermissionState.BACKGROUND

        val filtered = if (query.isEmpty()) entries else entries.filter { entry ->
            entry.label.contains(query, ignoreCase = true) ||
            (repo.decryptedAddress(entry) ?: "").contains(query, ignoreCase = true)
        }
        val sorted = when {
            sort == SortOrder.ByDistance && hasFine && loc != null ->
                filtered.sortedBy { entry ->
                    val lat = repo.decryptedLatitude(entry)
                    val lon = repo.decryptedLongitude(entry)
                    if (lat != null && lon != null) {
                        LocationHelper.distanceMeters(loc.latitude, loc.longitude, lat, lon).toDouble()
                    } else Double.MAX_VALUE
                }
            else -> filtered.sortedBy { it.label }
        }
        ListUiState(
            entries = sorted,
            searchQuery = query,
            sortOrder = sort,
            currentLocation = loc,
            locationPermissionState = permState,
            locationServicesEnabled = locEnabled,
            hasNotificationPermission = hasNotif,
            locationBannerDismissed = locDismissed,
            notificationBannerDismissed = notifDismissed
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ListUiState())

    fun setSearchQuery(q: String) { _searchQuery.value = q }
    fun setSortOrder(order: SortOrder) { _sortOrder.value = order }
    fun setLocation(loc: Location?) { _location.value = loc }
    fun setLocationPermissionState(state: LocationPermissionState) {
        if (state != _locationPermState.value) _locationBannerDismissed.value = false
        _locationPermState.value = state
    }
    fun setLocationServicesEnabled(enabled: Boolean) {
        if (enabled != _locationServicesEnabled.value) _locationBannerDismissed.value = false
        _locationServicesEnabled.value = enabled
    }
    fun setHasNotificationPermission(has: Boolean) { _hasNotificationPerm.value = has }
    fun dismissLocationBanner() { _locationBannerDismissed.value = true }
    fun dismissNotificationBanner() { _notificationBannerDismissed.value = true }
    fun resetPermissionBanners() {
        _locationBannerDismissed.value = false
        _notificationBannerDismissed.value = false
    }

    fun toggleSilence(entry: AccessCode) {
        viewModelScope.launch {
            repo.save(entry.copy(isSilenced = !entry.isSilenced))
            GeofenceManager.registerAll(getApplication(), dao.getAll(), _location.value)
        }
    }

    fun delete(entry: AccessCode) {
        viewModelScope.launch {
            repo.delete(entry)
            GeofenceManager.registerAll(getApplication(), dao.getAll(), _location.value)
        }
    }

    fun distanceTo(entry: AccessCode): Float? {
        val loc = _location.value ?: return null
        val lat = repo.decryptedLatitude(entry) ?: return null
        val lon = repo.decryptedLongitude(entry) ?: return null
        return LocationHelper.distanceMeters(loc.latitude, loc.longitude, lat, lon)
    }
}
