package com.paris.duval.sesamelite.migration

import com.paris.duval.sesamelite.crypto.CryptoManager
import com.paris.duval.sesamelite.crypto.EncryptionResult
import com.paris.duval.sesamelite.data.db.AccessCodeDao
import com.paris.duval.sesamelite.data.model.AccessCode

/**
 * Encrypts any plaintext-looking sensitive fields on launch.
 * A fresh install has no plaintext data, but imported entries arrive
 * as plaintext and must be encrypted before they're persisted.
 * Values already encrypted (isEncrypted) or future-version (isFutureVersion)
 * are left untouched.
 */
object MigrationManager {

    suspend fun migrateIfNeeded(dao: AccessCodeDao) {
        val entries = dao.getAll()
        val updated = mutableListOf<AccessCode>()

        for (entry in entries) {
            var changed = false
            var code = entry.code
            var locationDetails = entry.locationDetails
            var comment = entry.comment

            if (shouldEncrypt(code)) {
                code = encrypt(code!!) ?: code
                if (code != entry.code) changed = true
            }
            if (shouldEncrypt(locationDetails)) {
                locationDetails = encrypt(locationDetails!!) ?: locationDetails
                if (locationDetails != entry.locationDetails) changed = true
            }
            if (shouldEncrypt(comment)) {
                comment = encrypt(comment!!) ?: comment
                if (comment != entry.comment) changed = true
            }

            if (changed) {
                updated.add(entry.copy(code = code, locationDetails = locationDetails, comment = comment))
            }
        }

        updated.forEach { dao.update(it) }
    }

    private fun shouldEncrypt(value: String?): Boolean {
        if (value.isNullOrEmpty()) return false
        if (CryptoManager.isEncrypted(value)) return false
        if (CryptoManager.isFutureVersion(value)) return false
        return true
    }

    private fun encrypt(plain: String): String? {
        return when (val r = CryptoManager.encrypt(plain)) {
            is EncryptionResult.Success -> r.value
            else -> null
        }
    }
}
