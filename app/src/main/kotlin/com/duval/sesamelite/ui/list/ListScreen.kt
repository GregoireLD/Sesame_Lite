package com.duval.sesamelite.ui.list

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.duval.sesamelite.R
import com.duval.sesamelite.crypto.CryptoManager
import com.duval.sesamelite.data.model.AccessCode
import com.duval.sesamelite.location.LocationHelper
import com.duval.sesamelite.location.LocationPermissionState
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    vm: ListViewModel,
    onAddEntry: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onAbout: () -> Unit
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showSearch by remember { mutableStateOf(false) }

    // Re-check all runtime permissions on every resume — covers grant/revoke in Settings.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.setLocationPermissionState(LocationHelper.getLocationPermissionState(context))
                vm.setLocationServicesEnabled(LocationHelper.isLocationServicesEnabled(context))
                vm.setHasNotificationPermission(LocationHelper.hasNotificationPermission(context))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Seed with last-known fix immediately, then keep updating with live location.
    // Restarted whenever permission changes (e.g. newly granted).
    LaunchedEffect(state.hasLocationPermission) {
        if (state.hasLocationPermission) {
            LocationHelper.getLastKnownLocation(context)?.let { vm.setLocation(it) }
            LocationHelper.locationFlow(context).collect { location ->
                vm.setLocation(location)
            }
        } else {
            vm.setLocation(null)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                navigationIcon = {
                    IconButton(onClick = onAbout) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.about_title))
                    }
                },
                actions = {
                    // Sort toggle
                    val sortIcon = when {
                        state.sortOrder == SortOrder.ByDistance && !state.hasLocationPermission ->
                            Icons.Default.LocationOff
                        state.sortOrder == SortOrder.ByDistance -> Icons.Default.NearMe
                        else -> Icons.Default.SortByAlpha
                    }
                    IconButton(onClick = {
                        vm.setSortOrder(
                            if (state.sortOrder == SortOrder.ByDistance) SortOrder.Alphabetical
                            else SortOrder.ByDistance
                        )
                    }) {
                        Icon(sortIcon, contentDescription = stringResource(R.string.sort_toggle))
                    }
                    // Search
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_prompt))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddEntry) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_title))
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showSearch) {
                SearchBar(
                    query = state.searchQuery,
                    onQueryChange = vm::setSearchQuery,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            when {
                state.entries.isEmpty() && state.searchQuery.isEmpty() -> EmptyState(onAddEntry)
                state.entries.isEmpty() -> SearchEmptyState(state.searchQuery)
                else -> EntryList(
                    entries = state.entries,
                    vm = vm,
                    state = state,
                    onOpenEntry = onOpenEntry
                )
            }
        }

        // Permission banners — pinned to the bottom, matching iOS behaviour
        PermissionBanners(
            state = state,
            onOpenAppSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                )
            },
            onOpenLocationSettings = {
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            },
            onDismissLocation = vm::dismissLocationBanner,
            onDismissNotification = vm::dismissNotificationBanner,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        } // end Box
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.search_prompt)) },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, null)
                }
            }
        },
        singleLine = true,
        modifier = modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryList(
    entries: List<AccessCode>,
    vm: ListViewModel,
    state: ListUiState,
    onOpenEntry: (String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(entries, key = { it.id }) { entry ->
            EntryRow(
                entry = entry,
                vm = vm,
                state = state,
                onTap = { onOpenEntry(entry.id) }
            )
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryRow(
    entry: AccessCode,
    vm: ListViewModel,
    state: ListUiState,
    onTap: () -> Unit
) {
    val isFuture = entry.code?.let(CryptoManager::isFutureVersion) == true
    val hasCoords = entry.encryptedLatitude != null && entry.encryptedLongitude != null
    val context = LocalContext.current

    val dismissState = rememberSwipeToDismissBoxState()

    // Fire actions when swipe settles, then snap back to Default
    LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                if (!isFuture) vm.toggleSilence(entry)
                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
            }
            SwipeToDismissBoxValue.EndToStart -> {
                if (hasCoords) {
                    val lat = vm.repo.decryptedLatitude(entry)
                    val lon = vm.repo.decryptedLongitude(entry)
                    if (lat != null && lon != null) openInMaps(context, lat, lon, entry.label)
                }
                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
            }
            else -> {}
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            if (direction == SwipeToDismissBoxValue.StartToEnd) {
                // Leading swipe: silence toggle
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            if (entry.isSilenced) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Icon(
                        if (entry.isSilenced) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                        contentDescription = null,
                        tint = if (entry.isSilenced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (direction == SwipeToDismissBoxValue.EndToStart) {
                // Trailing swipe: open maps
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            if (hasCoords) Color(0xFF1565C0) else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        if (hasCoords) Icons.Default.Map else Icons.Default.LocationOff,
                        contentDescription = stringResource(R.string.action_maps),
                        tint = if (hasCoords) Color.White else MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        enableDismissFromStartToEnd = !isFuture,
        enableDismissFromEndToStart = true
    ) {
        ListItem(
            headlineContent = {
                Text(
                    entry.label,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isFuture) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.primary
                )
            },
            supportingContent = {
                Column {
                    val address = vm.repo.decryptedAddress(entry)
                    if (!address.isNullOrEmpty()) {
                        Text(address, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isFuture) {
                            Icon(Icons.Default.Lock, null, tint = Color(0xFFFF9500), modifier = Modifier.size(14.dp))
                            Text(
                                stringResource(R.string.entry_requires_newer_version),
                                fontSize = 11.sp, color = Color(0xFFFF9500)
                            )
                        } else {
                            if (entry.code != null) Icon(Icons.Default.Key, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.tertiary)
                            if (entry.locationDetails != null) Icon(Icons.Default.Info, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.tertiary)
                            if (entry.comment != null) Icon(Icons.Default.Comment, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.tertiary)
                            val hasCoords = entry.encryptedLatitude != null && entry.encryptedLongitude != null
                            if (entry.encryptedAddress != null) {
                                Icon(
                                    if (hasCoords) Icons.Default.LocationOn else Icons.Default.LocationOff,
                                    null, modifier = Modifier.size(12.dp),
                                    tint = if (hasCoords) MaterialTheme.colorScheme.tertiary else Color(0xFFFF9500)
                                )
                            }
                            if (entry.isSilenced) Icon(Icons.Default.NotificationsOff, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.tertiary)
                        }
                        // Distance label
                        if (state.sortOrder == SortOrder.ByDistance && state.hasLocationPermission) {
                            val dist = vm.distanceTo(entry)
                            if (dist != null) {
                                Spacer(Modifier.weight(1f))
                                Text(formatDistance(dist), fontSize = 11.sp, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                }
            },
            modifier = Modifier.clickable(
                enabled = !isFuture,
                onClick = onTap
            )
        )
    }
}

@Composable
private fun PermissionBanners(
    state: ListUiState,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onDismissLocation: () -> Unit,
    onDismissNotification: () -> Unit,
    modifier: Modifier = Modifier
) {
    val locationMessage = when {
        !state.locationServicesEnabled ->
            stringResource(R.string.permission_location_services_off)
        state.locationPermissionState == LocationPermissionState.NONE ->
            stringResource(R.string.permission_location_none)
        state.locationPermissionState == LocationPermissionState.APPROXIMATE ->
            stringResource(R.string.permission_location_approximate)
        state.locationPermissionState == LocationPermissionState.FOREGROUND_ONLY ->
            stringResource(R.string.permission_location_foreground)
        else -> null
    }
    val showNotifBanner = !state.hasNotificationPermission && !state.notificationBannerDismissed
    val showLocationBanner = locationMessage != null && !state.locationBannerDismissed

    if (!showLocationBanner && !showNotifBanner) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showNotifBanner) {
            PermissionBannerCard(
                icon = Icons.Default.NotificationsOff,
                message = stringResource(R.string.permission_notification_banner),
                onSettings = onOpenAppSettings,
                onDismiss = onDismissNotification
            )
        }
        if (locationMessage != null && !state.locationBannerDismissed) {
            PermissionBannerCard(
                icon = Icons.Default.LocationOff,
                message = locationMessage,
                onSettings = if (!state.locationServicesEnabled) onOpenLocationSettings else onOpenAppSettings,
                onDismiss = onDismissLocation
            )
        }
    }
}

@Composable
private fun PermissionBannerCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    onSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp).padding(top = 1.dp))
                Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSettings, modifier = Modifier.height(32.dp)) {
                    Text(stringResource(R.string.permission_open_settings), style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.height(32.dp)) {
                    Text(stringResource(R.string.action_dismiss), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun openInMaps(context: Context, lat: Double, lon: Double, label: String) {
    val encoded = Uri.encode(label)
    val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon($encoded)")
    val intent = Intent(Intent.ACTION_VIEW, uri)
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    }
}

private fun formatDistance(meters: Float): String {
    val locale = Locale.getDefault()
    return if (locale.country in setOf("US", "LR", "MM")) {
        val feet = meters * 3.28084f
        if (feet < 1000f) "${feet.roundToInt()} ft"
        else String.format(locale, "%.1f mi", feet / 5280f)
    } else {
        if (meters < 1000f) "${meters.roundToInt()} m"
        else String.format(locale, "%.1f km", meters / 1000f)
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Key, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAdd) {
            Text(stringResource(R.string.empty_add_button))
        }
    }
}

@Composable
private fun SearchEmptyState(query: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.search_empty_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.search_empty_subtitle, query),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
