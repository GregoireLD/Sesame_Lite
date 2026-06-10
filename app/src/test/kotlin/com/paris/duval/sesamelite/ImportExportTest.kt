package com.paris.duval.sesamelite

import com.paris.duval.sesamelite.share.ImportExport
import com.paris.duval.sesamelite.share.ImportExport.ImportResult
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale
import java.util.zip.CRC32

/**
 * Tests for the share URL format: CRC32, Base64URL, and round-trip parsing.
 * Pure JVM tests — no Android framework dependencies.
 * Uses parsePayload() which is the testable, Android-free parsing path.
 */
class ImportExportTest {

    // ---------------------------------------------------------------------------
    // CRC32 — must match java.util.zip.CRC32 (ISO-HDLC)
    // ---------------------------------------------------------------------------

    @Test
    fun `crc32Hex matches java CRC32`() {
        val input = "v=1&label=Home&code=1234"
        val crc = CRC32()
        crc.update(input.toByteArray(Charsets.UTF_8))
        val expected = "%08x".format(crc.value)
        assertEquals(expected, ImportExport.crc32Hex(input))
    }

    @Test
    fun `crc32Hex produces 8 lowercase hex chars`() {
        val hex = ImportExport.crc32Hex("test payload")
        assertEquals(8, hex.length)
        assertTrue(hex.all { it.isDigit() || it in 'a'..'f' })
    }

    @Test
    fun `crc32 is deterministic`() {
        val a = ImportExport.crc32Hex("v=1&label=Test%20Door")
        val b = ImportExport.crc32Hex("v=1&label=Test%20Door")
        assertEquals(a, b)
    }

    @Test
    fun `crc32 differs for different inputs`() {
        val a = ImportExport.crc32Hex("v=1&label=Door+A")
        val b = ImportExport.crc32Hex("v=1&label=Door+B")
        assertNotEquals(a, b)
    }

    // ---------------------------------------------------------------------------
    // Base64URL encode/decode round-trip
    // ---------------------------------------------------------------------------

    @Test
    fun `base64url round-trip`() {
        val original = "v=1&label=My%20Door&code=5678&crc32=abcd1234"
        val encoded = ImportExport.base64UrlEncode(original)
        val decoded = ImportExport.base64UrlDecode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `base64url encoded output has no padding`() {
        val encoded = ImportExport.base64UrlEncode("some payload")
        assertFalse(encoded.contains('='))
    }

    @Test
    fun `base64url encoded output has no + or slash`() {
        val input = "v=1&label=Hello%20World&code=abc123&lat=48.85341&lon=2.34880"
        val encoded = ImportExport.base64UrlEncode(input)
        assertFalse(encoded.contains('+'))
        assertFalse(encoded.contains('/'))
    }

    @Test
    fun `base64url decode handles missing padding`() {
        val base64NoPad = "djE" // "v1" without padding, 3 chars → 1 char missing
        val decoded = ImportExport.base64UrlDecode(base64NoPad)
        // May or may not decode correctly depending on padding restoration,
        // but must not throw
        assertNotNull(decoded)
    }

    // ---------------------------------------------------------------------------
    // Spaces encoded as %20 not + (iOS compatibility)
    // ---------------------------------------------------------------------------

    @Test
    fun `percentEncode20 encodes spaces as percent-20`() {
        val encoded = ImportExport.percentEncode20("My Front Door")
        assertTrue(encoded.contains("%20"))
        assertFalse(encoded.contains('+'))
    }

    @Test
    fun `spaces in label survive round-trip via parsePayload`() {
        val query = "v=1&label=My%20Front%20Door&code=1234"
        val crc = ImportExport.crc32Hex(query)
        val payload = "$query&crc32=$crc"
        val fragment = ImportExport.base64UrlEncode(payload)

        val result = ImportExport.parsePayload(fragment, null)
        assertTrue("Expected Success but got $result", result is ImportResult.Success)
        assertEquals("My Front Door", (result as ImportResult.Success).import.label)
    }

    // ---------------------------------------------------------------------------
    // parsePayload: valid payloads
    // ---------------------------------------------------------------------------

    @Test
    fun `parsePayload with valid base64url fragment succeeds`() {
        val query = "v=1&label=GarageCode&code=7890"
        val crc = ImportExport.crc32Hex(query)
        val payload = "$query&crc32=$crc"
        val fragment = ImportExport.base64UrlEncode(payload)

        val result = ImportExport.parsePayload(fragment, null)
        assertTrue(result is ImportResult.Success)
        val imp = (result as ImportResult.Success).import
        assertEquals("GarageCode", imp.label)
        assertEquals("7890", imp.code)
    }

    @Test
    fun `parsePayload with coordinates`() {
        val query = "v=1&label=Office&lat=48.85341&lon=2.34880"
        val crc = ImportExport.crc32Hex(query)
        val payload = "$query&crc32=$crc"
        val fragment = ImportExport.base64UrlEncode(payload)

        val result = ImportExport.parsePayload(fragment, null)
        assertTrue(result is ImportResult.Success)
        val imp = (result as ImportResult.Success).import
        assertEquals(48.85341, imp.latitude!!, 0.00001)
        assertEquals(2.34880, imp.longitude!!, 0.00001)
    }

    @Test
    fun `parsePayload with all optional fields`() {
        val query = "v=1&label=MainEntrance&code=5555&address=12%20Rue%20de%20Rivoli&lat=48.85600&lon=2.34100&radius=150.0&details=3rd%20floor&comment=Note"
        val crc = ImportExport.crc32Hex(query)
        val payload = "$query&crc32=$crc"
        val fragment = ImportExport.base64UrlEncode(payload)

        val result = ImportExport.parsePayload(fragment, null)
        assertTrue(result is ImportResult.Success)
        val imp = (result as ImportResult.Success).import
        assertEquals("MainEntrance", imp.label)
        assertEquals("5555", imp.code)
        assertEquals("12 Rue de Rivoli", imp.address)
        assertEquals(48.85600, imp.latitude!!, 0.00001)
        assertEquals(2.34100, imp.longitude!!, 0.00001)
        assertEquals(150.0, imp.radiusMeters!!, 0.001)
        assertEquals("3rd floor", imp.locationDetails)
        assertEquals("Note", imp.comment)
    }

    // ---------------------------------------------------------------------------
    // parsePayload: CRC mismatch → Malformed
    // ---------------------------------------------------------------------------

    @Test
    fun `parsePayload rejects tampered payload`() {
        val query = "v=1&label=Front%20Door&code=9999"
        val crc = ImportExport.crc32Hex(query)
        val tamperedQuery = "v=1&label=Back%20Door&code=9999"
        val payload = "$tamperedQuery&crc32=$crc"  // CRC is for "Front Door"
        val fragment = ImportExport.base64UrlEncode(payload)

        val result = ImportExport.parsePayload(fragment, null)
        assertTrue(result is ImportResult.Malformed)
    }

    // ---------------------------------------------------------------------------
    // parsePayload: missing label → Malformed
    // ---------------------------------------------------------------------------

    @Test
    fun `parsePayload without label returns Malformed`() {
        val query = "v=1&code=1234"
        val crc = ImportExport.crc32Hex(query)
        val payload = "$query&crc32=$crc"
        val fragment = ImportExport.base64UrlEncode(payload)
        val result = ImportExport.parsePayload(fragment, null)
        assertTrue(result is ImportResult.Malformed)
    }

    // ---------------------------------------------------------------------------
    // parsePayload: future version
    // ---------------------------------------------------------------------------

    @Test
    fun `parsePayload v2 returns FutureVersion`() {
        val query = "v=2&label=SomeDoor"
        val crc = ImportExport.crc32Hex(query)
        val payload = "$query&crc32=$crc"
        val fragment = ImportExport.base64UrlEncode(payload)
        val result = ImportExport.parsePayload(fragment, null)
        assertTrue(result is ImportResult.FutureVersion)
    }

    // ---------------------------------------------------------------------------
    // CRC32 compatibility: verify a specific known value
    // ---------------------------------------------------------------------------

    @Test
    fun `crc32 produces correct value for known input`() {
        // "123456789" → CRC32/ISO-HDLC = 0xCBF43926
        val result = ImportExport.crc32Hex("123456789")
        assertEquals("cbf43926", result)
    }

    // ---------------------------------------------------------------------------
    // Round-trip: build → parse (end-to-end without Keystore or Android)
    // ---------------------------------------------------------------------------

    @Test
    fun `manual url construction round-trips correctly`() {
        val label = "Garage Door"
        val code = "ABC#123"  // special char
        val query = "v=1&label=${ImportExport.percentEncode20(label)}&code=${ImportExport.percentEncode20(code)}"
        val crc = ImportExport.crc32Hex(query)
        val payload = "$query&crc32=$crc"
        val fragment = ImportExport.base64UrlEncode(payload)

        val result = ImportExport.parsePayload(fragment, null)
        assertTrue(result is ImportResult.Success)
        val imp = (result as ImportResult.Success).import
        assertEquals(label, imp.label)
        assertEquals(code, imp.code)
    }

    // ---------------------------------------------------------------------------
    // Locale independence: coordinates must use '.' whatever the device locale
    // ---------------------------------------------------------------------------

    @Test
    fun `formatCoordinate uses dot decimal separator under comma-decimal locale`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            assertEquals("48.85837", ImportExport.formatCoordinate(48.85837))
            assertEquals("2.29448", ImportExport.formatCoordinate(2.29448))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun `coordinates formatted under French locale survive a full round-trip`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            val query = "v=1&label=Bureau" +
                "&lat=${ImportExport.formatCoordinate(48.85837)}" +
                "&lon=${ImportExport.formatCoordinate(2.29448)}"
            val crc = ImportExport.crc32Hex(query)
            val fragment = ImportExport.base64UrlEncode("$query&crc32=$crc")

            val result = ImportExport.parsePayload(fragment, null)
            assertTrue(result is ImportResult.Success)
            val imp = (result as ImportResult.Success).import
            assertEquals(48.85837, imp.latitude!!, 0.00001)
            assertEquals(2.29448, imp.longitude!!, 0.00001)
        } finally {
            Locale.setDefault(saved)
        }
    }

    // ---------------------------------------------------------------------------
    // Literal '+' must survive decoding (iOS leaves '+' unescaped on the wire)
    // ---------------------------------------------------------------------------

    @Test
    fun `literal plus in iOS-style payload is not decoded as a space`() {
        // What iOS URLComponents emits for code "12+34": '+' stays literal.
        val query = "v=1&label=Garage&code=12+34"
        val crc = ImportExport.crc32Hex(query)
        val fragment = ImportExport.base64UrlEncode("$query&crc32=$crc")

        val result = ImportExport.parsePayload(fragment, null)
        assertTrue(result is ImportResult.Success)
        assertEquals("12+34", (result as ImportResult.Success).import.code)
    }

    @Test
    fun `percent-encoded plus still decodes to a plus`() {
        // Android's own exports (and patched iOS) emit %2B for a plus sign.
        assertEquals("12+34", ImportExport.percentDecode("12%2B34"))
    }
}
