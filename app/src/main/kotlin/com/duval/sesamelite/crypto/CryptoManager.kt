package com.duval.sesamelite.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM encryption using the Android Keystore.
 *
 * On-disk format (identical to iOS CryptoKit):
 *   v1:<base64(nonce)>:<base64(combined)>
 * where:
 *   nonce    = 12 bytes (96-bit), random per encryption
 *   combined = nonce ‖ ciphertext ‖ tag (16-byte tag)
 *
 * The standalone nonce in parts[1] is redundant (nonce is embedded in combined
 * as the first 12 bytes) — it is written for iOS compatibility but ignored on
 * decrypt. Only parts[2] (combined) is parsed.
 *
 * Standard Base64 (+, /, = padding) — not URL-safe. The share fragment uses
 * URL-safe Base64; don't mix them up.
 */
object CryptoManager {

    private const val KEY_ALIAS = "sesame_lite_aes_key"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BITS = 128
    private const val NONCE_LENGTH_BYTES = 12
    private const val VERSION = "v1"
    private const val SEP = ":"

    // ---------------------------------------------------------------------------
    // Key management
    // ---------------------------------------------------------------------------

    /** Returns the key if it exists; never creates one. Safe for decrypt path. */
    fun getKey(): SecretKey? {
        return try {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        } catch (_: Exception) {
            null
        }
    }

    /** Returns existing key or generates a new one. Only call on encrypt path. */
    fun getOrCreateKey(): SecretKey? {
        getKey()?.let { return it }
        return try {
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()

            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
                .apply { init(spec) }
                .generateKey()
        } catch (_: Exception) {
            null
        }
    }

    /** Deletes the Keystore key. Used during reset-all-data. */
    fun deleteKey() {
        try {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS)
        } catch (_: Exception) {
        }
    }

    // ---------------------------------------------------------------------------
    // Encrypt
    // ---------------------------------------------------------------------------

    fun encrypt(plainText: String): EncryptionResult {
        val key = getOrCreateKey() ?: return EncryptionResult.KeyUnavailable
        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv                                // 12 bytes
            val ctAndTag = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = iv + ctAndTag                      // nonce ‖ ciphertext ‖ tag
            val stored = "$VERSION$SEP${base64(iv)}$SEP${base64(combined)}"
            EncryptionResult.Success(stored)
        } catch (_: Exception) {
            EncryptionResult.KeyUnavailable
        }
    }

    // ---------------------------------------------------------------------------
    // Decrypt
    // ---------------------------------------------------------------------------

    fun decrypt(stored: String): DecryptionResult {
        if (!stored.contains(SEP)) return DecryptionResult.LegacyPlainText(stored)

        val parts = stored.split(SEP, limit = 3)
        if (parts.size != 3) return DecryptionResult.LegacyPlainText(stored)

        val version = parts[0]
        return when {
            version == VERSION -> decryptV1(parts[2])
            version.startsWith("v") -> DecryptionResult.UnknownVersion
            else -> DecryptionResult.LegacyPlainText(stored)
        }
    }

    private fun decryptV1(combinedBase64: String): DecryptionResult {
        val key = getKey() ?: return DecryptionResult.KeyUnavailable
        return try {
            val combined = Base64.decode(combinedBase64, Base64.DEFAULT)
            val iv = combined.copyOfRange(0, NONCE_LENGTH_BYTES)
            val body = combined.copyOfRange(NONCE_LENGTH_BYTES, combined.size)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            val plain = cipher.doFinal(body).toString(Charsets.UTF_8)
            DecryptionResult.Success(plain)
        } catch (_: Exception) {
            DecryptionResult.KeyUnavailable
        }
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    fun isEncrypted(value: String): Boolean = value.startsWith("$VERSION$SEP")

    fun isFutureVersion(value: String): Boolean {
        if (!value.contains(SEP)) return false
        val ver = value.substringBefore(SEP)
        return ver != VERSION && ver.startsWith("v")
    }

    private fun base64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)
}
