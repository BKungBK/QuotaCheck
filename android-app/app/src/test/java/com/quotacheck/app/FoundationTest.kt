package com.quotacheck.app

import org.junit.Assert.assertEquals
import org.junit.Test

class FoundationTest {
    @Test
    fun packageNameIsStable() {
        assertEquals("com.quotacheck.app", BuildConfig.APPLICATION_ID)
    }
}
