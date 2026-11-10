package com.editorshub

import org.junit.Assert.assertEquals
import org.junit.Test

class AppVersionTest {
    @Test fun appReportsV452() {
        assertEquals("4.5.2", BuildConfig.APP_VERSION_NAME)
        assertEquals(21, BuildConfig.APP_VERSION_CODE)
    }
}
