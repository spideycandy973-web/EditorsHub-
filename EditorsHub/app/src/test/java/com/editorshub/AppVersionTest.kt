package com.editorshub

import org.junit.Assert.assertEquals
import org.junit.Test

class AppVersionTest {
    @Test fun appReportsV440() {
        assertEquals("4.4.0", BuildConfig.APP_VERSION_NAME)
        assertEquals(20, BuildConfig.APP_VERSION_CODE)
    }
}
