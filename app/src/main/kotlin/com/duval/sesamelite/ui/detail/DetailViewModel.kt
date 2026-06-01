package com.duval.sesamelite.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.duval.sesamelite.data.db.AppDatabase
import com.duval.sesamelite.data.model.AccessCode
import com.duval.sesamelite.data.repository.AccessCodeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DetailUiState(
    val entry: AccessCode? = null,
    val plainCode: String? = null,
    val plainAddress: String? = null,
    val plainLocationDetails: String? = null,
    val plainComment: String? = null,
    val showCode: Boolean = false
)

class DetailViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).accessCodeDao()
    val repo = AccessCodeRepository(dao)

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    fun load(entryId: String) {
        viewModelScope.launch {
            val entry = dao.getById(entryId) ?: return@launch
            _state.value = DetailUiState(
                entry = entry,
                plainCode = repo.decryptedCode(entry),
                plainAddress = repo.decryptedAddress(entry),
                plainLocationDetails = repo.decryptedLocationDetails(entry),
                plainComment = repo.decryptedComment(entry)
            )
        }
    }

    fun toggleShowCode() {
        _state.value = _state.value.copy(showCode = !_state.value.showCode)
    }
}
