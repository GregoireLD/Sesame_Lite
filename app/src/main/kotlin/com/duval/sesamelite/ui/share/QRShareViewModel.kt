package com.duval.sesamelite.ui.share

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.duval.sesamelite.data.db.AppDatabase
import com.duval.sesamelite.data.repository.AccessCodeRepository
import com.duval.sesamelite.share.ImportExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class QRShareUiState(
    val label: String = "",
    val qrBitmap: Bitmap? = null,
    val shareUrl: String? = null,
    val generationFailed: Boolean = false,
    val includeRadius: Boolean = false,
    val includeLocationDetails: Boolean = false,
    val includeComment: Boolean = false,
    val includeCoordinates: Boolean = true,
    val useLegacyScheme: Boolean = false
)

class QRShareViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).accessCodeDao()
    private val repo = AccessCodeRepository(dao)

    private val _state = MutableStateFlow(QRShareUiState())
    val state: StateFlow<QRShareUiState> = _state.asStateFlow()

    private var entryId: String? = null

    fun load(id: String) {
        entryId = id
        viewModelScope.launch {
            val entry = dao.getById(id) ?: return@launch
            _state.value = _state.value.copy(label = entry.label)
            generate()
        }
    }

    fun setIncludeRadius(v: Boolean) { _state.value = _state.value.copy(includeRadius = v); generate() }
    fun setIncludeLocationDetails(v: Boolean) { _state.value = _state.value.copy(includeLocationDetails = v); generate() }
    fun setIncludeComment(v: Boolean) { _state.value = _state.value.copy(includeComment = v); generate() }
    fun setIncludeCoordinates(v: Boolean) { _state.value = _state.value.copy(includeCoordinates = v); generate() }
    fun setUseLegacyScheme(v: Boolean) { _state.value = _state.value.copy(useLegacyScheme = v); generate() }

    private fun generate() {
        val id = entryId ?: return
        val s = _state.value
        viewModelScope.launch {
            val entry = dao.getById(id) ?: return@launch
            withContext(Dispatchers.Default) {
                val url = ImportExport.buildUrl(
                    entry, repo,
                    includeRadius = s.includeRadius,
                    includeLocationDetails = s.includeLocationDetails,
                    includeComment = s.includeComment,
                    includeCoordinates = s.includeCoordinates,
                    useLegacyScheme = s.useLegacyScheme
                )
                if (url == null) {
                    _state.value = _state.value.copy(generationFailed = true)
                    return@withContext
                }
                val bmp = ImportExport.generateQrBitmap(url)
                _state.value = _state.value.copy(
                    qrBitmap = bmp,
                    shareUrl = url,
                    generationFailed = bmp == null
                )
            }
        }
    }
}
