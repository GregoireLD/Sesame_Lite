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
    val hasLocationPermission: Boolean = false,
    val hasBackgroundLocationPermission: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val locationBannerDismissed: Boolean = false,
    val notificationBannerDismissed: Boolean = false
)

class ListViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).accessCodeDao()
    val repo = AccessCodeRepository(dao)

    private val _sortOrder = MutableStateFlow(SortOrder.ByDistance)
    private val _searchQuery = MutableStateFlow("")
    private val _location = MutableStateFlow<Location?>(null)
    private val _hasLocationPerm = MutableStateFlow(false)
    private val _hasBackgroundLocationPerm = MutableStateFlow(false)
    private val _hasNotificationPerm = MutableStateFlow(true)
    private val _locationBannerDismissed = MutableStateFlow(false)
    private val _notificationBannerDismissed = MutableStateFlow(false)

    val uiState: StateFlow<ListUiState> = combine(
        dao.getAllFlow(),
        _searchQuery,
        _sortOrder,
        _location,
        combine(_hasLocationPerm, _hasBackgroundLocationPerm, _hasNotificationPerm,
                _locationBannerDismissed, _notificationBannerDismissed) {
            hasFine, hasBg, hasNotif, locDismissed, notifDismissed ->
            listOf(hasFine, hasBg, hasNotif, locDismissed, notifDismissed)
        }
    ) { entries, query, sort, loc, perms ->
        val hasFine = perms[0] as Boolean
        val hasBg = perms[1] as Boolean
        val hasNotif = perms[2] as Boolean
        val locDismissed = perms[3] as Boolean
        val notifDismissed = perms[4] as Boolean

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
            hasLocationPermission = hasFine,
            hasBackgroundLocationPermission = hasBg,
            hasNotificationPermission = hasNotif,
            locationBannerDismissed = locDismissed,
            notificationBannerDismissed = notifDismissed
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ListUiState())

    fun setSearchQuery(q: String) { _searchQuery.value = q }
    fun setSortOrder(order: SortOrder) { _sortOrder.value = order }
    fun setLocation(loc: Location?) { _location.value = loc }
    fun setHasLocationPermission(has: Boolean) { _hasLocationPerm.value = has }
    fun setHasBackgroundLocationPermission(has: Boolean) { _hasBackgroundLocationPerm.value = has }
    fun setHasNotificationPermission(has: Boolean) { _hasNotificationPerm.value = has }
    fun dismissLocationBanner() { _locationBannerDismissed.value = true }
    fun dismissNotificationBanner() { _notificationBannerDismissed.value = true }

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
