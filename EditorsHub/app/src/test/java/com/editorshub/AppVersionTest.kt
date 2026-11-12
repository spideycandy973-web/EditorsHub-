package com.editorshub

import org.junit.Assert.assertEquals
import org.junit.Test

class AppVersionTest {
    @Test fun appReportsV451() {
        assertEquals("4.5.4", BuildConfig.APP_VERSION_NAME)
        assertEquals(22, BuildConfig.APP_VERSION_CODE)
    }
}
