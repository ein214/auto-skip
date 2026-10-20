package com.personal.autoskip

import com.personal.autoskip.accessibility.AutoSkipAccessibilityService.Companion.isServiceListed
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityStatusTest {
    private val pkg = "com.personal.autoskip"
    private val cls = "com.personal.autoskip.accessibility.AutoSkipAccessibilityService"

    @Test fun nullOrEmpty() {
        assertFalse(isServiceListed(null, pkg, cls))
        assertFalse(isServiceListed("", pkg, cls))
    }

    @Test fun fullClassName() {
        assertTrue(isServiceListed("other/x.Y:$pkg/$cls", pkg, cls))
    }

    @Test fun shortClassName() {
        assertTrue(isServiceListed("$pkg/.accessibility.AutoSkipAccessibilityService", pkg, cls))
    }

    @Test fun otherServiceOnly() {
        assertFalse(isServiceListed("com.other/com.other.Service", pkg, cls))
        assertFalse(isServiceListed("garbage", pkg, cls))
    }
}
