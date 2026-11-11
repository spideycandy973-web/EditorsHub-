package com.editorshub

import org.junit.Assert.assertEquals
import org.junit.Test

class AppVersionTest {
    @Test fun appReportsV451() {
        assertEquals("4.5.1", BuildConfig.APP_VERSION_NAME)
        assertEquals(21, BuildConfig.APP_VERSION_CODE)
    }
}
