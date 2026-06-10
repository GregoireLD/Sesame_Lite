package com.duval.sesamelite.share

import android.net.Uri
import android.util.Base64
import com.duval.sesamelite.crypto.CryptoManager
import com.duval.sesamelite.crypto.DecryptionResult
import com.duval.sesamelite.data.model.AccessCode
import com.duval.sesamelite.data.repository.AccessCodeRepository
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.Locale
import java.util.zip.CRC32

/**
 * Cross-platform share format compatible with the iOS Sesame app.
 *
 * URL fragment format (payload goes in # so browsers/servers never see it):
 *   https://sesame-app.com/share#<base64url(queryString&crc32=<8hex>)>
 *   sesame://import#<base64url(queryString&crc32=<8hex>)>
 *
 * Spaces encoded as %20 (NOT +); a literal '+' on the wire is a plus sign,
 * never a space (iOS URLComponents leaves '+' unescaped). CRC32 is
 * java.util.zip.CRC32.
 * Base64URL: standard Base64 with +→-, /→_, = stripped.
 */
object ImportExport {

    private const val BASE_URL = "https://sesame-app.com/share"

    // ---------------------------------------------------------------------------
    // Export
    // ---------------------------------------------------------------------------

    fun buildUrl(
        entry: AccessCode,
        repo: AccessCodeRepository,
        includeRadius: Boolean = false,
        includeLocationDetails: Boolean = false,
        includeComment: Boolean = false,
        includeCoordinates: Boolean = true,
        useLegacyScheme: Boolean = false
    ): String? {
        val items = mutableListOf<Pair<String, String>>()

        items += "v" to "1"
        items += "label" to entry.label

        val address = repo.decryptedAddress(entry)
        if (address != null) items += "address" to address

        val code = repo.decryptedCode(entry)
        if (code != null) items += "code" to code

        if (includeCoordinates) {
            val lat = repo.decryptedLatitude(entry)
            val lon = repo.decryptedLongitude(entry)
            if (lat != null && lon != null) {
                items += "lat" to formatCoordinate(lat)
                items += "lon" to formatCoordinate(lon)
            }
        }

        if (includeRadius) {
            items += "radius" to entry.radiusMeters.toString()
        }

        if (includeLocationDetails) {
            val details = repo.decryptedLocationDetails(entry)
            if (!details.isNullOrEmpty()) items += "details" to details
        }

        if (includeComment) {
            val comment = repo.decryptedComment(entry)
            if (!comment.isNullOrEmpty()) items += "comment" to comment
        }

        // Build query string with %20 for spaces (not +)
        val queryString = items.joinToString("&") { (k, v) ->
            "${percentEncode20(k)}=${percentEncode20(v)}"
        }

        val crc = crc32Hex(queryString)
        val payload = "$queryString&crc32=$crc"
        val fragment = base64UrlEncode(payload)

        val base = if (useLegacyScheme) "sesame://import" else BASE_URL
        return "$base#$fragment"
    }

    fun generateQrBitmap(url: String, sizePx: Int = 512): android.graphics.Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1
            )
            val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val bmp = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.RGB_565)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bmp
        } catch (_: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------------------
    // Import — Android entry point
    // ---------------------------------------------------------------------------

    /** Parses any Sesame share URL string. Works for both https:// and sesame:// forms. */
    fun parse(uriString: String): ImportResult {
        return try {
            val uri = Uri.parse(uriString)
            // Fragment first, then query string (legacy)
            val fragment = uri.fragment
            val query = uri.query
            parsePayload(fragment, query)
        } catch (_: Exception) {
            ImportResult.Malformed
        }
    }

    fun parseUri(uri: Uri): ImportResult {
        return parsePayload(uri.fragment, uri.query)
    }

    // ---------------------------------------------------------------------------
    // parsePayload — pure Kotlin, testable without Android
    // ---------------------------------------------------------------------------

    /**
     * Parses a Sesame payload given the raw fragment and fallback query string.
     * This function is separate so it can be unit-tested without Android.
     */
    fun parsePayload(rawFragment: String?, rawQuery: String?): ImportResult {
        val paramString: String = when {
            !rawFragment.isNullOrEmpty() -> {
                val decoded = base64UrlDecode(rawFragment!!)
                if (decoded != null && decoded.contains("=")) decoded else rawFragment
            }
            !rawQuery.isNullOrEmpty() -> rawQuery!!
            else -> return ImportResult.Malformed
        }

        // Verify and strip CRC32
        val payloadString: String
        val crcIdx = paramString.lastIndexOf("&crc32=")
        if (crcIdx >= 0) {
            val candidate = paramString.substring(crcIdx + 7)
            val payload = paramString.substring(0, crcIdx)
            if (candidate.length != 8 || !candidate.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
                return ImportResult.Malformed
            }
            if (crc32Hex(payload) != candidate.lowercase()) return ImportResult.Malformed
            payloadString = payload
        } else {
            payloadString = paramString
        }

        // Parse key=value pairs — percent-decode values
        val params = mutableMapOf<String, String>()
        payloadString.split("&").forEach { pair ->
            val eqIdx = pair.indexOf('=')
            if (eqIdx > 0) {
                val key = percentDecode(pair.substring(0, eqIdx))
                val value = percentDecode(pair.substring(eqIdx + 1))
                params[key] = value
            }
        }

        val version = params["v"] ?: "1"
        if (version != "1") {
            return if (version.toIntOrNull()?.let { it > 1 } == true) {
                ImportResult.FutureVersion
            } else {
                ImportResult.Malformed
            }
        }

        val label = params["label"]?.takeIf { it.isNotEmpty() } ?: return ImportResult.Malformed

        return ImportResult.Success(
            ParsedImport(
                label = label,
                address = params["address"],
                code = params["code"],
                radiusMeters = params["radius"]?.toDoubleOrNull(),
                locationDetails = params["details"],
                comment = params["comment"],
                isSilenced = params["silenced"] == "1",
                latitude = params["lat"]?.toDoubleOrNull(),
                longitude = params["lon"]?.toDoubleOrNull()
            )
        )
    }

    sealed class ImportResult {
        data class Success(val import: ParsedImport) : ImportResult()
        data object Malformed : ImportResult()
        data object FutureVersion : ImportResult()
    }

    // ---------------------------------------------------------------------------
    // Coordinate formatting — locale-independent (wire format requires '.')
    // ---------------------------------------------------------------------------

    /**
     * Formats a coordinate for the share payload. Must be locale-independent:
     * the default locale produces a decimal comma on fr/de/… devices
     * ("48,85837"), which the Double parsers on both platforms reject — the
     * coordinates would be silently dropped on import (the CRC still
     * validates, so no error ever surfaces).
     */
    fun formatCoordinate(value: Double): String = "%.5f".format(Locale.US, value)

    // ---------------------------------------------------------------------------
    // CRC32 (ISO-HDLC — exactly java.util.zip.CRC32)
    // ---------------------------------------------------------------------------

    fun crc32Hex(input: String): String {
        val crc = CRC32()
        crc.update(input.toByteArray(Charsets.UTF_8))
        return "%08x".format(crc.value)
    }

    // ---------------------------------------------------------------------------
    // Base64URL (no padding, +→-, /→_)
    // ---------------------------------------------------------------------------

    fun base64UrlEncode(input: String): String =
        java.util.Base64.getEncoder().encodeToString(input.toByteArray(Charsets.UTF_8))
            .replace('+', '-')
            .replace('/', '_')
            .trimEnd('=')

    fun base64UrlDecode(input: String): String? {
        var b64 = input.replace('-', '+').replace('_', '/')
        val rem = b64.length % 4
        if (rem > 0) b64 += "=".repeat(4 - rem)
        return try {
            java.util.Base64.getDecoder().decode(b64).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------------------
    // Percent encode/decode: space → %20 (NOT +)
    // ---------------------------------------------------------------------------

    fun percentEncode20(value: String): String {
        // java.net.URLEncoder uses + for spaces; we need %20 for iOS compatibility
        return java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }

    fun percentDecode(value: String): String {
        // The wire format is RFC 3986 percent-encoding: '+' is a literal plus.
        // iOS URLComponents leaves '+' unescaped on export, while URLDecoder
        // follows HTML-form rules and would turn it into a space — protect it
        // first. No compliant producer ever encodes a space as '+' (both
        // platforms emit %20), so nothing is lost by being strict here.
        return try {
            java.net.URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
        } catch (_: Exception) {
            value
        }
    }
}
