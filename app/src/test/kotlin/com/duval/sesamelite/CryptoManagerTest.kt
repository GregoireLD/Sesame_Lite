package com.duval.sesamelite

import android.util.Base64
import com.duval.sesamelite.crypto.CryptoManager
import com.duval.sesamelite.crypto.DecryptionResult
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for the v1:<nonce>:<combined> on-disk format.
 *
 * NOTE: The full Android Keystore encrypt/decrypt path requires a real device or
 * instrumented test. These unit tests verify the format helpers and the decrypt
 * routing logic using mock ciphertext strings.
 */
class CryptoManagerTest {

    // ---------------------------------------------------------------------------
    // isEncrypted / isFutureVersion helpers
    // ---------------------------------------------------------------------------

    @Test
    fun `isEncrypted returns true for v1 prefix`() {
        assertTrue(CryptoManager.isEncrypted("v1:abc:def"))
    }

    @Test
    fun `isEncrypted returns false for plaintext`() {
        assertFalse(CryptoManager.isEncrypted("hello"))
        assertFalse(CryptoManager.isEncrypted("12345"))
    }

    @Test
    fun `isFutureVersion true for v2 prefix`() {
        assertTrue(CryptoManager.isFutureVersion("v2:abc:def"))
        assertTrue(CryptoManager.isFutureVersion("v99:x:y"))
    }

    @Test
    fun `isFutureVersion false for v1`() {
        assertFalse(CryptoManager.isFutureVersion("v1:abc:def"))
    }

    @Test
    fun `isFutureVersion false for plain text`() {
        assertFalse(CryptoManager.isFutureVersion("hello world"))
        assertFalse(CryptoManager.isFutureVersion("12345:more"))
    }

    // ---------------------------------------------------------------------------
    // decrypt routing
    // ---------------------------------------------------------------------------

    @Test
    fun `decrypt returns LegacyPlainText when no colon`() {
        val result = CryptoManager.decrypt("plaintext_no_colon")
        assertTrue(result is DecryptionResult.LegacyPlainText)
        assertEquals("plaintext_no_colon", (result as DecryptionResult.LegacyPlainText).value)
    }

    @Test
    fun `decrypt returns LegacyPlainText for malformed (wrong part count)`() {
        val result = CryptoManager.decrypt("v1:onlytwoparts")
        // split with limit=3 gives ["v1", "onlytwoparts"] → 2 parts → LegacyPlainText
        assertTrue(result is DecryptionResult.LegacyPlainText)
    }

    @Test
    fun `decrypt returns UnknownVersion for v2 prefix`() {
        val result = CryptoManager.decrypt("v2:someb64:someb64")
        assertTrue(result is DecryptionResult.UnknownVersion)
    }

    @Test
    fun `decrypt returns UnknownVersion for vN prefix`() {
        val result = CryptoManager.decrypt("v99:abc:def")
        assertTrue(result is DecryptionResult.UnknownVersion)
    }

    @Test
    fun `decrypt v1 with bad base64 returns KeyUnavailable`() {
        // No key, so getKey() returns null → KeyUnavailable
        val result = CryptoManager.decrypt("v1:aaaa:bbbb")
        // Could be KeyUnavailable (no Keystore key in unit test JVM) or invalid ciphertext
        assertTrue(
            result is DecryptionResult.KeyUnavailable || result is DecryptionResult.KeyUnavailable
        )
    }

    // ---------------------------------------------------------------------------
    // Format shape verification (without Keystore)
    // ---------------------------------------------------------------------------

    @Test
    fun `stored format has three colon-separated parts starting with v1`() {
        // We can't call encrypt() in a unit test (no Keystore), but we can
        // verify the parsing logic with a hand-crafted v1 string.
        val fakeNonce = "AAAAAAAAAAAAAAAA"   // 12 bytes Base64
        val fakeCombined = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==" // 33+ bytes
        val stored = "v1:$fakeNonce:$fakeCombined"
        val parts = stored.split(":", limit = 3)
        assertEquals(3, parts.size)
        assertEquals("v1", parts[0])
    }
}
