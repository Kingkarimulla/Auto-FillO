package com.example.security

import android.util.Base64
import java.security.Key
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * AES Encryption / Decryption Manager for local secure data storage.
 */
object EncryptionManager {
    private const val ALGORITHM = "AES"
    // Local AES 256-bit key representation
    private val FIXED_KEY = byteArrayOf(
        0x46, 0x6f, 0x72, 0x6d, 0x46, 0x69, 0x6c, 0x6c,
        0x50, 0x72, 0x6f, 0x53, 0x65, 0x63, 0x75, 0x72,
        0x65, 0x4b, 0x65, 0x79, 0x32, 0x30, 0x32, 0x36,
        0x41, 0x45, 0x53, 0x32, 0x35, 0x36, 0x21, 0x21
    )

    private fun getKey(): Key {
        return SecretKeySpec(FIXED_KEY, ALGORITHM)
    }

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, getKey())
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            plainText
        }
    }

    fun decrypt(cipherText: String): String {
        if (cipherText.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, getKey())
            val decodedBytes = Base64.decode(cipherText, Base64.NO_WRAP)
            String(cipher.doFinal(decodedBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            cipherText
        }
    }

    fun maskValue(value: String): String {
        if (value.length <= 4) return "****"
        return value.take(2) + "*".repeat(value.length - 4) + value.takeLast(2)
    }
}
