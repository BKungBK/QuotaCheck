package com.quotacheck.app.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores the refresh token only as Android Keystore-encrypted app-private data. */
class AndroidCredentialVault(context: Context) : CredentialVault {
    private val applicationContext = context.applicationContext

    override suspend fun saveRefreshToken(token: CharArray) {
        val plainText = token.concatToString().toByteArray(Charsets.UTF_8)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                // Android Keystore creates the required random IV for each encryption.
                init(Cipher.ENCRYPT_MODE, key())
            }
            val iv = cipher.iv
            check(iv.size == IV_SIZE_BYTES) { "Unexpected credential IV length" }
            val encrypted = cipher.doFinal(plainText)
            try {
                val payload = ByteArray(1 + iv.size + encrypted.size)
                payload[0] = FORMAT_VERSION
                iv.copyInto(payload, destinationOffset = 1)
                encrypted.copyInto(payload, destinationOffset = 1 + iv.size)
                try {
                    ciphertextFile().outputStream().use { output -> output.write(payload) }
                } finally {
                    payload.fill(0)
                }
            } finally {
                iv.fill(0)
                encrypted.fill(0)
            }
        } finally {
            plainText.fill(0)
        }
    }

    override suspend fun readRefreshToken(): CharArray? {
        val payload = ciphertextFile().takeIf(File::exists)?.readBytes() ?: return null
        try {
            require(payload.size > 1 + IV_SIZE_BYTES) { "Invalid credential payload" }
            require(payload[0] == FORMAT_VERSION) { "Unsupported credential payload" }

            val iv = payload.copyOfRange(1, 1 + IV_SIZE_BYTES)
            val encrypted = payload.copyOfRange(1 + IV_SIZE_BYTES, payload.size)
            val plainText = try {
                Cipher.getInstance(TRANSFORMATION).run {
                    init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_SIZE_BITS, iv))
                    doFinal(encrypted)
                }
            } finally {
                iv.fill(0)
                encrypted.fill(0)
            }
            return try {
                plainText.toString(Charsets.UTF_8).toCharArray()
            } finally {
                plainText.fill(0)
            }
        } finally {
            payload.fill(0)
        }
    }

    override suspend fun clear() {
        ciphertextFile().delete()
    }

    private fun ciphertextFile(): File = File(applicationContext.filesDir, CIPHERTEXT_FILE_NAME)

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        return (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setKeySize(KEY_SIZE_BITS)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build(),
                )
                generateKey()
            }
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "quota_check_refresh_token"
        const val CIPHERTEXT_FILE_NAME = "credential_vault.bin"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION: Byte = 1
        const val IV_SIZE_BYTES = 12
        const val GCM_TAG_SIZE_BITS = 128
        const val KEY_SIZE_BITS = 256
    }
}
