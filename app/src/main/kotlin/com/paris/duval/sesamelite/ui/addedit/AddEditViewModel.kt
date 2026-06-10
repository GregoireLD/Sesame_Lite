package com.paris.duval.sesamelite.ui.addedit

import android.app.Application
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paris.duval.sesamelite.crypto.CryptoManager
import com.paris.duval.sesamelite.crypto.EncryptionResult
import com.paris.duval.sesamelite.data.db.AppDatabase
import com.paris.duval.sesamelite.data.model.AccessCode
import com.paris.duval.sesamelite.data.repository.AccessCodeRepository
import com.paris.duval.sesamelite.geo.GeofenceManager
import com.paris.duval.sesamelite.share.ImportExport
import com.paris.duval.sesamelite.share.ParsedImport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume

enum class GeoState { NotResolved, Geocoding, Resolved, Error }

data class AddEditUiState(
    val label: String = "",
    val code: String = "",
    val showCode: Boolean = false,
    val address: String = "",
    val geoState: GeoState = GeoState.NotResolved,
    val geoError: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMeters: Double = 100.0,
    val isSilenced: Boolean = false,
    val locationDetails: String = "",
    val comment: String = "",
    val isEditing: Boolean = false,
    val isImporting: Boolean = false,
    val keyUnavailable: Boolean = false,
    val showUnresolvedWarning: Boolean = false,
    val showClipboardError: Boolean = false,
    val showClipboardOverwrite: Boolean = false,
    val pendingClipboardImport: ParsedImport? = null,
    val showDeleteConfirm: Boolean = false,
    val showDuplicateWarning: Boolean = false,
    val duplicateMatchId: String? = null,
    val saved: Boolean = false,
    val deleted: Boolean = false
) {
    val canSave get() = label.isNotEmpty()
    val isUnresolved get() = geoState != GeoState.Resolved && address.isNotEmpty()
}

class AddEditViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).accessCodeDao()
    private val repo = AccessCodeRepository(dao)

    private val _state = MutableStateFlow(AddEditUiState())
    val state: StateFlow<AddEditUiState> = _state.asStateFlow()

    private var editingId: String? = null
    private var pendingEntry: AccessCode? = null

    // ---------------------------------------------------------------------------
    // Initialise for add, edit, or import
    // ---------------------------------------------------------------------------

    fun initForAdd() {
        _state.value = AddEditUiState()
    }

    fun initForEdit(entryId: String) {
        viewModelScope.launch {
            val entry = dao.getById(entryId) ?: return@launch
            editingId = entryId
            val addressPlain = repo.decryptedAddress(entry) ?: ""
            val lat = repo.decryptedLatitude(entry)
            val lon = repo.decryptedLongitude(entry)
            val hasValidCoords = lat != null && lon != null && (lat != 0.0 || lon != 0.0)
            val codePlain = repo.decryptedCode(entry) ?: ""
            val detailsPlain = repo.decryptedLocationDetails(entry) ?: ""
            val commentPlain = repo.decryptedComment(entry) ?: ""

            _state.value = AddEditUiState(
                label = entry.label,
                code = codePlain,
                address = addressPlain,
                geoState = if (hasValidCoords) GeoState.Resolved else GeoState.NotResolved,
                latitude = if (hasValidCoords) lat else null,
                longitude = if (hasValidCoords) lon else null,
                radiusMeters = entry.radiusMeters,
                isSilenced = entry.isSilenced,
                locationDetails = detailsPlain,
                comment = commentPlain,
                isEditing = true
            )
        }
    }

    fun initForImport(parsed: ParsedImport) {
        val hasCoords = parsed.latitude != null && parsed.longitude != null
        _state.value = AddEditUiState(
            label = parsed.label,
            code = parsed.code ?: "",
            address = parsed.address ?: "",
            geoState = if (hasCoords) GeoState.Resolved else GeoState.NotResolved,
            latitude = parsed.latitude,
            longitude = parsed.longitude,
            radiusMeters = parsed.radiusMeters ?: 100.0,
            isSilenced = parsed.isSilenced,
            locationDetails = parsed.locationDetails ?: "",
            comment = parsed.comment ?: "",
            isImporting = true
        )
        // If no coords came with the import, geocode the address
        if (!hasCoords && !parsed.address.isNullOrEmpty()) {
            geocodeAddress(parsed.address)
        }
    }

    // ---------------------------------------------------------------------------
    // Field updates — user vs programmatic
    // ---------------------------------------------------------------------------

    fun onLabelChange(v: String) { _state.value = _state.value.copy(label = v) }
    fun onCodeChange(v: String) { _state.value = _state.value.copy(code = v) }
    fun onShowCodeToggle() { _state.value = _state.value.copy(showCode = !_state.value.showCode) }
    fun onRadiusChange(v: Double) { _state.value = _state.value.copy(radiusMeters = v) }
    fun onSilencedToggle() { _state.value = _state.value.copy(isSilenced = !_state.value.isSilenced) }
    fun onLocationDetailsChange(v: String) { _state.value = _state.value.copy(locationDetails = v) }
    fun onCommentChange(v: String) { _state.value = _state.value.copy(comment = v) }

    /** Called from TextField.onValueChange. Invalidates geocode if text actually changed. */
    fun onAddressEditedByUser(newText: String) {
        val old = _state.value.address
        if (newText == old) return
        _state.value = _state.value.copy(
            address = newText,
            geoState = GeoState.NotResolved,
            geoError = null,
            latitude = null,
            longitude = null
        )
    }

    /** Called for import pre-fill, edit pre-fill, and writing back resolved address. */
    private fun setAddressProgrammatically(text: String) {
        _state.value = _state.value.copy(address = text)
    }

    // ---------------------------------------------------------------------------
    // Geocoding
    // ---------------------------------------------------------------------------

    /** Called from the UI "Look up" button — fires and forgets. */
    fun geocodeAddress(overrideAddress: String? = null) {
        viewModelScope.launch { geocodeAndAwait(overrideAddress ?: _state.value.address) }
    }

    /**
     * Suspending geocode: sets Geocoding state, awaits the result, then updates state.
     * Returns true if the address resolved successfully.
     */
    private suspend fun geocodeAndAwait(addr: String): Boolean {
        if (addr.isEmpty()) return false
        _state.value = _state.value.copy(geoState = GeoState.Geocoding, geoError = null)
        val result = geocodeAsync(addr)
        return if (result != null) {
            setAddressProgrammatically(result.first)
            _state.value = _state.value.copy(
                geoState = GeoState.Resolved,
                latitude = result.second,
                longitude = result.third,
                geoError = null
            )
            true
        } else {
            _state.value = _state.value.copy(
                geoState = GeoState.Error,
                geoError = getApplication<Application>().getString(
                    com.paris.duval.sesamelite.R.string.address_error_not_found
                )
            )
            false
        }
    }

    private suspend fun geocodeAsync(addr: String): Triple<String, Double, Double>? {
        val ctx = getApplication<Application>()
        if (!Geocoder.isPresent()) return null

        return suspendCancellableCoroutine { cont ->
            try {
                val geocoder = Geocoder(ctx)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocationName(addr, 1) { addresses ->
                        val a = addresses.firstOrNull()
                        cont.resume(
                            if (a != null) Triple(a.getAddressLine(0) ?: addr, a.latitude, a.longitude)
                            else null
                        )
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocationName(addr, 1)
                    val a = addresses?.firstOrNull()
                    cont.resume(
                        if (a != null) Triple(a.getAddressLine(0) ?: addr, a.latitude, a.longitude)
                        else null
                    )
                }
            } catch (_: Exception) {
                cont.resume(null)
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Clipboard import
    // ---------------------------------------------------------------------------

    fun importFromClipboard(rawClipText: String?) {
        val clipText = rawClipText
            ?.replace(Regex("^(?:%20|\\s|\\.)+", RegexOption.IGNORE_CASE), "")
            ?.replace(Regex("(?:%20|\\s|\\.)+$", RegexOption.IGNORE_CASE), "")
        if (clipText.isNullOrEmpty()) {
            _state.value = _state.value.copy(showClipboardError = true)
            return
        }
        val result = ImportExport.parse(clipText)
        if (result !is ImportExport.ImportResult.Success) {
            _state.value = _state.value.copy(showClipboardError = true)
            return
        }
        val parsed = result.import
        val s = _state.value
        val hasContent = s.label.isNotEmpty() || s.code.isNotEmpty() ||
                         s.address.isNotEmpty() || s.locationDetails.isNotEmpty() || s.comment.isNotEmpty()
        if (hasContent) {
            _state.value = _state.value.copy(pendingClipboardImport = parsed, showClipboardOverwrite = true)
        } else {
            applyImport(parsed)
        }
    }

    fun confirmClipboardOverwrite() {
        val pending = _state.value.pendingClipboardImport ?: return
        applyImport(pending)
        _state.value = _state.value.copy(pendingClipboardImport = null, showClipboardOverwrite = false)
    }

    fun dismissClipboardOverwrite() {
        _state.value = _state.value.copy(pendingClipboardImport = null, showClipboardOverwrite = false)
    }

    private fun applyImport(parsed: ParsedImport) {
        val hasCoords = parsed.latitude != null && parsed.longitude != null
        _state.value = _state.value.copy(
            label = parsed.label,
            code = parsed.code ?: "",
            address = parsed.address ?: "",
            geoState = if (hasCoords) GeoState.Resolved else GeoState.NotResolved,
            latitude = parsed.latitude,
            longitude = parsed.longitude,
            radiusMeters = parsed.radiusMeters ?: _state.value.radiusMeters,
            isSilenced = parsed.isSilenced,
            locationDetails = parsed.locationDetails ?: "",
            comment = parsed.comment ?: "",
            geoError = null
        )
        if (!hasCoords && !parsed.address.isNullOrEmpty()) {
            geocodeAddress(parsed.address)
        }
    }

    // ---------------------------------------------------------------------------
    // Save
    // ---------------------------------------------------------------------------

    fun attemptSave() {
        val s = _state.value
        if (s.geoState == GeoState.Geocoding) return  // already in-flight, let it finish

        if (s.address.isNotEmpty() && s.geoState != GeoState.Resolved) {
            // Address entered but not yet geocoded — await the result before deciding
            viewModelScope.launch {
                val resolved = geocodeAndAwait(s.address)
                if (resolved) {
                    performSave()
                } else {
                    _state.value = _state.value.copy(showUnresolvedWarning = true)
                }
            }
            return
        }
        performSave()
    }

    fun forceSave() {
        _state.value = _state.value.copy(showUnresolvedWarning = false)
        performSave()
    }

    private fun performSave() {
        val s = _state.value

        fun encrypt(plain: String): String? {
            if (plain.isEmpty()) return null
            return when (val r = CryptoManager.encrypt(plain)) {
                is EncryptionResult.Success -> r.value
                else -> { _state.value = _state.value.copy(keyUnavailable = true); null }
            }
        }

        val encCode = if (s.code.isEmpty()) null else encrypt(s.code) ?: return
        val encAddress = if (s.address.isEmpty()) null else encrypt(s.address) ?: return
        val encDetails = if (s.locationDetails.isEmpty()) null else encrypt(s.locationDetails) ?: return
        val encComment = if (s.comment.isEmpty()) null else encrypt(s.comment) ?: return
        val encLat = if (s.latitude != null) encrypt(s.latitude.toString()) ?: return else null
        val encLon = if (s.longitude != null) encrypt(s.longitude.toString()) ?: return else null

        val entry = AccessCode(
            id = editingId ?: UUID.randomUUID().toString(),
            label = s.label,
            code = encCode,
            encryptedAddress = encAddress,
            encryptedLatitude = encLat,
            encryptedLongitude = encLon,
            radiusMeters = s.radiusMeters,
            isSilenced = s.isSilenced,
            locationDetails = encDetails,
            comment = encComment,
            schemaVersion = 3
        )

        viewModelScope.launch {
            val duplicate = repo.findDuplicateByLabelAndAddress(s.label, s.address, editingId)
            if (duplicate != null) {
                pendingEntry = entry
                _state.value = _state.value.copy(
                    showDuplicateWarning = true,
                    duplicateMatchId = duplicate.id
                )
                return@launch
            }
            repo.save(entry)
            GeofenceManager.registerAll(getApplication(), dao.getAll(), null)
            _state.value = _state.value.copy(saved = true)
        }
    }

    // ---------------------------------------------------------------------------
    // Delete
    // ---------------------------------------------------------------------------

    fun requestDelete() { _state.value = _state.value.copy(showDeleteConfirm = true) }
    fun dismissDelete() { _state.value = _state.value.copy(showDeleteConfirm = false) }

    fun confirmDelete() {
        val id = editingId ?: return
        viewModelScope.launch {
            val entry = dao.getById(id) ?: return@launch
            repo.delete(entry)
            GeofenceManager.removeEntry(getApplication(), id)
            _state.value = _state.value.copy(deleted = true, showDeleteConfirm = false)
        }
    }

    // ---------------------------------------------------------------------------
    // Duplicate warning
    // ---------------------------------------------------------------------------

    fun dismissDuplicateWarning() {
        pendingEntry = null
        _state.value = _state.value.copy(showDuplicateWarning = false, duplicateMatchId = null)
    }

    /** Replace the existing matching entry with the new data (keeps the duplicate's id). */
    fun confirmReplaceMatch() {
        val entry = pendingEntry ?: return
        val matchId = _state.value.duplicateMatchId ?: return
        val replacement = entry.copy(id = matchId)
        viewModelScope.launch {
            repo.save(replacement)
            val wasEditing = editingId
            if (wasEditing != null && wasEditing != matchId) {
                dao.getById(wasEditing)?.let { repo.delete(it) }
                GeofenceManager.removeEntry(getApplication(), wasEditing)
            }
            GeofenceManager.registerAll(getApplication(), dao.getAll(), null)
            pendingEntry = null
            _state.value = _state.value.copy(saved = true, showDuplicateWarning = false, duplicateMatchId = null)
        }
    }

    /** Ignore the duplicate and save as a new/separate entry. */
    fun confirmSaveAnyway() {
        val entry = pendingEntry ?: return
        viewModelScope.launch {
            repo.save(entry)
            GeofenceManager.registerAll(getApplication(), dao.getAll(), null)
            pendingEntry = null
            _state.value = _state.value.copy(saved = true, showDuplicateWarning = false, duplicateMatchId = null)
        }
    }

    fun dismissKeyUnavailable() { _state.value = _state.value.copy(keyUnavailable = false) }
    fun dismissUnresolvedWarning() { _state.value = _state.value.copy(showUnresolvedWarning = false) }
    fun dismissClipboardError() { _state.value = _state.value.copy(showClipboardError = false) }
}
