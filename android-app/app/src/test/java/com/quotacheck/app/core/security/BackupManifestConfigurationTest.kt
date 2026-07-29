package com.quotacheck.app.core.security

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManifestConfigurationTest {
    @Test
    fun manifestLinksLegacyFullBackupRules() {
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must be available to the JVM test", manifest.isFile)
        val source = manifest.readText()

        assertTrue(source.contains("android:fullBackupContent=\"@xml/backup_rules\""))
        assertTrue(source.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
    }
}
