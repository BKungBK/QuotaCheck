package com.quotacheck.app.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Stores the refresh token only as Android Keystore-encrypted app-private data. */
class AndroidCredentialVault(context: Context) : CredentialVault {
    private val applicationContext = context.applicationContext
    private val mutex = Mutex()
    private val atomicFile = AtomicFile(File(applicationContext.filesDir, CIPHERTEXT_FILE_NAME))

    override suspend fun saveRefreshToken(token: CharArray) = mutex.withLock {
        val plainText = encode(token)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                // Android Keystore creates a new random IV for every encryption.
                init(Cipher.ENCRYPT_MODE, key())
            }
            val iv = cipher.iv
            check(iv.size == IV_SIZE_BYTES) { "Unexpected credential IV length" }
            val encrypted = cipher.doFinal(plainText)
            try {
                val payload = ByteArray(1 + iv.size + encrypted.size)
                try {
                    payload[0] = FORMAT_VERSION
                    iv.copyInto(payload, destinationOffset = 1)
                    encrypted.copyInto(payload, destinationOffset = 1 + iv.size)
                    writeAtomically(payload)
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

    override suspend fun readRefreshToken(): CharArray? = mutex.withLock {
        val payload = atomicFile.baseFile.takeIf(File::exists)?.readBytes() ?: return@withLock null
        try {
            if (!isValidPayload(payload)) return@withLock discardCorruptPayload()

            val iv = payload.copyOfRange(1, 1 + IV_SIZE_BYTES)
            val encrypted = payload.copyOfRange(1 + IV_SIZE_BYTES, payload.size)
            val plainText = try {
                Cipher.getInstance(TRANSFORMATION).run {
                    init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_SIZE_BITS, iv))
                    doFinal(encrypted)
                }
            } catch (_: GeneralSecurityException) {
                return@withLock discardCorruptPayload()
            } finally {
                iv.fill(0)
                encrypted.fill(0)
            }
            try {
                decode(plainText)
            } catch (_: CharacterCodingException) {
                discardCorruptPayload()
            } finally {
                plainText.fill(0)
            }
        } finally {
            payload.fill(0)
        }
    }

    override suspend fun clear() = mutex.withLock { deleteCiphertextOrThrow() }

    private fun writeAtomically(payload: ByteArray) {
        var output: FileOutputStream? = null
        try {
            output = atomicFile.startWrite()
            output.write(payload)
            output.fd.sync()
            atomicFile.finishWrite(output)
            output = null
        } catch (exception: Exception) {
            output?.let(atomicFile::failWrite)
            throw exception
        }
    }

    private fun isValidPayload(payload: ByteArray): Boolean =
        payload.size >= 1 + IV_SIZE_BYTES + GCM_TAG_SIZE_BYTES && payload[0] == FORMAT_VERSION

    private fun discardCorruptPayload(): CharArray? {
        deleteCiphertextOrThrow()
        return null
    }

    private fun deleteCiphertextOrThrow() {
        atomicFile.delete()
        check(!atomicFile.baseFile.exists() && !backupFile().exists()) {
            "Unable to remove encrypted credential"
        }
    }

    private fun backupFile(): File = File(atomicFile.baseFile.parentFile, "${atomicFile.baseFile.name}.bak")

    private fun encode(token: CharArray): ByteArray {
        val encoded = Charsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(token))
        return try {
            ByteArray(encoded.remaining()).also(encoded::get)
        } finally {
            zero(encoded)
        }
    }

    private fun decode(bytes: ByteArray): CharArray {
        val decoded = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
        return try {
            CharArray(decoded.remaining()).also(decoded::get)
        } finally {
            zero(decoded)
        }
    }

    private fun zero(buffer: ByteBuffer) {
        buffer.clear()
        while (buffer.hasRemaining()) buffer.put(0)
    }

    private fun zero(buffer: CharBuffer) {
        buffer.clear()
        while (buffer.hasRemaining()) buffer.put('\u0000')
    }

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
        const val GCM_TAG_SIZE_BYTES = GCM_TAG_SIZE_BITS / 8
        const val KEY_SIZE_BITS = 256
    }
}
