package com.quotacheck.app.core.security

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AndroidCredentialVaultTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val vault = AndroidCredentialVault(context)
    private val ciphertextFile = File(context.filesDir, "credential_vault.bin")
    private val backupFile = File(context.filesDir, "credential_vault.bin.bak")

    @After
    fun tearDown() = runBlocking { vault.clear() }

    @Test
    fun savesReadsReplacesAndClearsRefreshToken() = runBlocking {
        val first = "first-token-for-test".toCharArray()
        val replacement = "replacement-token-for-test".toCharArray()

        vault.saveRefreshToken(first)
        assertArrayEquals(first, vault.readRefreshToken())

        vault.saveRefreshToken(replacement)
        assertArrayEquals(replacement, vault.readRefreshToken())

        backupFile.writeBytes(byteArrayOf(1))
        vault.clear()
        assertNull(vault.readRefreshToken())
        assertFalse(ciphertextFile.exists())
        assertFalse(backupFile.exists())
    }

    @Test
    fun replaceUsesDifferentIvAndCiphertextDoesNotContainPlaintext() = runBlocking {
        val token = "plaintext-must-not-be-persisted".toCharArray()
        vault.saveRefreshToken(token)
        val firstPayload = ciphertextFile.readBytes()
        vault.saveRefreshToken(token)
        val replacementPayload = ciphertextFile.readBytes()
        try {
            assertFalse(firstPayload.copyOfRange(1, 13).contentEquals(replacementPayload.copyOfRange(1, 13)))
            assertFalse(containsSequence(replacementPayload, token.map { it.code.toByte() }.toByteArray()))
        } finally {
            firstPayload.fill(0)
            replacementPayload.fill(0)
            token.fill('\u0000')
        }
    }

    @Test
    fun tamperedAndTruncatedPayloadsAreRejectedAndRemoved() = runBlocking {
        vault.saveRefreshToken("tamper-test-token".toCharArray())
        val tampered = ciphertextFile.readBytes()
        tampered[tampered.lastIndex] = (tampered.last().toInt() xor 1).toByte()
        ciphertextFile.writeBytes(tampered)
        tampered.fill(0)

        assertNull(vault.readRefreshToken())
        assertFalse(ciphertextFile.exists())

        ciphertextFile.writeBytes(byteArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
        assertNull(vault.readRefreshToken())
        assertFalse(ciphertextFile.exists())
    }

    @Test
    fun packagedBackupExclusionsCoverCiphertextAndAtomicBackup() {
        listOf("backup_rules", "data_extraction_rules").forEach { resourceName ->
            assertTrue(resourceExcludes(resourceName, "credential_vault.bin"))
            assertTrue(resourceExcludes(resourceName, "credential_vault.bin.bak"))
        }
    }

    private fun xmlResourceId(resourceName: String): Int =
        context.resources.getIdentifier(resourceName, "xml", context.packageName)

    private fun resourceExcludes(resourceName: String, path: String): Boolean {
        val parser = context.resources.getXml(xmlResourceId(resourceName))
        while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (
                parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG &&
                parser.name == "exclude" &&
                parser.getAttributeValue(null, "domain") == "file" &&
                parser.getAttributeValue(null, "path") == path
            ) return true
            parser.next()
        }
        return false
    }

    private fun containsSequence(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.size > haystack.size) return false
        return (0..haystack.size - needle.size).any { offset ->
            needle.indices.all { index -> haystack[offset + index] == needle[index] }
        }
    }
}
