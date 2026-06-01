package com.duval.sesamelite.crypto

sealed class DecryptionResult {
    data class Success(val value: String) : DecryptionResult()
    data object KeyUnavailable : DecryptionResult()
    data object UnknownVersion : DecryptionResult()
    data class LegacyPlainText(val value: String) : DecryptionResult()
}

sealed class EncryptionResult {
    data class Success(val value: String) : EncryptionResult()
    data object KeyUnavailable : EncryptionResult()
}
