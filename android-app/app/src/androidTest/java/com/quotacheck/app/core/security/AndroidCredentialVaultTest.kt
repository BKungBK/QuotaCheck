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

        vault.clear()
        assertNull(vault.readRefreshToken())
        assertFalse(ciphertextFile.exists())
    }

    @Test
    fun ciphertextFileDoesNotContainPlaintext() = runBlocking {
        val token = "plaintext-must-not-be-persisted".toCharArray()
        vault.saveRefreshToken(token)

        val stored = ciphertextFile.readBytes()
        try {
            assertFalse(stored.decodeToString().contains(token.concatToString()))
        } finally {
            stored.fill(0)
            token.fill('\u0000')
        }
    }

    @Test
    fun backupRulesExcludeCredentialCiphertext() {
        assertTrue(resourceExcludesCiphertext("backup_rules"))
        assertTrue(resourceExcludesCiphertext("data_extraction_rules"))
    }

    private fun resourceExcludesCiphertext(resourceName: String): Boolean {
        val resourceId = context.resources.getIdentifier(resourceName, "xml", context.packageName)
        val parser = context.resources.getXml(resourceId)
        while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (
                parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG &&
                parser.name == "exclude" &&
                parser.getAttributeValue(null, "domain") == "file" &&
                parser.getAttributeValue(null, "path") == "credential_vault.bin"
            ) {
                return true
            }
            parser.next()
        }
        return false
    }
}
