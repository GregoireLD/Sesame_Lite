package com.duval.sesamelite.data.repository

import com.duval.sesamelite.crypto.CryptoManager
import com.duval.sesamelite.crypto.DecryptionResult
import com.duval.sesamelite.data.db.AccessCodeDao
import com.duval.sesamelite.data.model.AccessCode
import kotlinx.coroutines.flow.Flow

class AccessCodeRepository(private val dao: AccessCodeDao) {

    fun getAllFlow(): Flow<List<AccessCode>> = dao.getAllFlow()

    suspend fun getAll(): List<AccessCode> = dao.getAll()

    suspend fun getById(id: String): AccessCode? = dao.getById(id)

    suspend fun save(code: AccessCode) {
        if (dao.getById(code.id) != null) dao.update(code) else dao.insert(code)
    }

    suspend fun delete(code: AccessCode) = dao.delete(code)

    /** Returns the first existing entry whose label and decrypted address both match, excluding [excludeId]. */
    suspend fun findDuplicateByLabelAndAddress(label: String, address: String, excludeId: String?): AccessCode? {
        if (address.isEmpty()) return null
        return dao.getAll().firstOrNull { entry ->
            entry.id != excludeId &&
            entry.label.equals(label, ignoreCase = true) &&
            decryptedAddress(entry)?.equals(address, ignoreCase = true) == true
        }
    }

    suspend fun deleteAll() = dao.deleteAll()

    // Decrypted accessors — never in the entity, always via the repository
    fun decryptedAddress(code: AccessCode): String? {
        val enc = code.encryptedAddress ?: return null
        return when (val r = CryptoManager.decrypt(enc)) {
            is DecryptionResult.Success -> r.value.ifEmpty { null }
            is DecryptionResult.LegacyPlainText -> r.value.ifEmpty { null }
            else -> null
        }
    }

    fun decryptedLatitude(code: AccessCode): Double? {
        val enc = code.encryptedLatitude ?: return null
        return when (val r = CryptoManager.decrypt(enc)) {
            is DecryptionResult.Success -> r.value.toDoubleOrNull()
            is DecryptionResult.LegacyPlainText -> r.value.toDoubleOrNull()
            else -> null
        }
    }

    fun decryptedLongitude(code: AccessCode): Double? {
        val enc = code.encryptedLongitude ?: return null
        return when (val r = CryptoManager.decrypt(enc)) {
            is DecryptionResult.Success -> r.value.toDoubleOrNull()
            is DecryptionResult.LegacyPlainText -> r.value.toDoubleOrNull()
            else -> null
        }
    }

    fun decryptedCode(code: AccessCode): String? {
        val enc = code.code ?: return null
        return when (val r = CryptoManager.decrypt(enc)) {
            is DecryptionResult.Success -> r.value
            is DecryptionResult.LegacyPlainText -> r.value
            else -> null
        }
    }

    fun decryptedLocationDetails(code: AccessCode): String? {
        val enc = code.locationDetails ?: return null
        return when (val r = CryptoManager.decrypt(enc)) {
            is DecryptionResult.Success -> r.value.ifEmpty { null }
            is DecryptionResult.LegacyPlainText -> r.value.ifEmpty { null }
            else -> null
        }
    }

    fun decryptedComment(code: AccessCode): String? {
        val enc = code.comment ?: return null
        return when (val r = CryptoManager.decrypt(enc)) {
            is DecryptionResult.Success -> r.value.ifEmpty { null }
            is DecryptionResult.LegacyPlainText -> r.value.ifEmpty { null }
            else -> null
        }
    }
}
