package com.paris.duval.sesamelite.crypto

import com.paris.duval.sesamelite.data.db.AccessCodeDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class KeyAvailabilityMonitor {

    enum class State { Available, UnavailableNoData, UnavailableWithData }

    private val _state = MutableStateFlow(State.Available)
    val state: StateFlow<State> = _state.asStateFlow()

    suspend fun check(dao: AccessCodeDao) {
        if (CryptoManager.getKey() != null) {
            _state.value = State.Available
            return
        }
        val entries = dao.getAll()
        val hasEncrypted = entries.any { entry ->
            (entry.code?.let(CryptoManager::isEncrypted) ?: false) ||
            (entry.locationDetails?.let(CryptoManager::isEncrypted) ?: false) ||
            (entry.comment?.let(CryptoManager::isEncrypted) ?: false) ||
            (entry.encryptedAddress?.let(CryptoManager::isEncrypted) ?: false) ||
            (entry.encryptedLatitude?.let(CryptoManager::isEncrypted) ?: false)
        }
        _state.value = if (hasEncrypted) State.UnavailableWithData else State.UnavailableNoData
    }

    /** Forces the unavailable-with-data state for developer testing. */
    fun simulateBrokenKey() {
        _state.value = State.UnavailableWithData
    }

    suspend fun resetAllData(dao: AccessCodeDao) {
        dao.deleteAll()
        CryptoManager.deleteKey()
        _state.value = State.UnavailableNoData
    }
}
