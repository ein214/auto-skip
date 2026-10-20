package com.personal.autoskip

import com.personal.autoskip.settings.AppAllowlistRepository.Companion.isExcluded
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowlistTest {
    @Test fun storeSettingsAndSelfAreExcluded() {
        assertTrue(isExcluded("com.android.vending"))
        assertTrue(isExcluded("com.android.settings"))
        assertTrue(isExcluded("com.personal.autoskip"))
    }

    @Test fun normalAppsAreNotExcluded() {
        assertFalse(isExcluded("com.personal.autoskip.mockhost"))
    }
}
