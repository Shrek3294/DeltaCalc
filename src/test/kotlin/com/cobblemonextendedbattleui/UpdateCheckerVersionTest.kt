package com.cobblemonextendedbattleui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpdateCheckerVersionTest {

    @Test
    fun testNewerPatchVersion() {
        assertTrue(UpdateChecker.isNewerVersion("3.2.1", "3.2.0"))
        assertTrue(UpdateChecker.isNewerVersion("3.3.0", "3.2.0"))
        assertTrue(UpdateChecker.isNewerVersion("4.0.0", "3.2.0"))
    }

    @Test
    fun testIdenticalVersionNotNewer() {
        assertFalse(UpdateChecker.isNewerVersion("3.2.0", "3.2.0"))
    }

    @Test
    fun testOlderVersionNotNewer() {
        assertFalse(UpdateChecker.isNewerVersion("3.1.1", "3.2.0"))
        assertFalse(UpdateChecker.isNewerVersion("2.9.9", "3.2.0"))
    }

    @Test
    fun testVersionPrefixes() {
        assertTrue(UpdateChecker.isNewerVersion("v3.2.1", "3.2.0"))
        assertTrue(UpdateChecker.isNewerVersion("V3.2.1", "3.2.0"))
        assertTrue(UpdateChecker.isNewerVersion("v3.2.1", "v3.2.0"))
        assertTrue(UpdateChecker.isNewerVersion("V3.2.1", "v3.2.0"))
        assertFalse(UpdateChecker.isNewerVersion("v3.2.0", "V3.2.0"))
        assertFalse(UpdateChecker.isNewerVersion("V3.2.0", "v3.2.0"))
        assertFalse(UpdateChecker.isNewerVersion("3.2.0", "v3.2.0"))
        assertFalse(UpdateChecker.isNewerVersion("v3.2.0", "3.2.0"))
    }

    @Test
    fun testSuffixEquivalence() {
        // Equal numeric cores with different suffixes are equal
        assertFalse(UpdateChecker.isNewerVersion("3.2.0-deltacalc", "3.2.0"))
        assertFalse(UpdateChecker.isNewerVersion("3.2.0", "3.2.0-deltacalc"))
        assertFalse(UpdateChecker.isNewerVersion("3.2.0-alpha", "3.2.0-beta"))
        assertFalse(UpdateChecker.isNewerVersion("3.2.0+build.1", "3.2.0+build.2"))
        // Different numeric cores with suffixes compare cores
        assertTrue(UpdateChecker.isNewerVersion("3.2.1-deltacalc", "3.2.0-deltacalc"))
        assertFalse(UpdateChecker.isNewerVersion("3.2.0-deltacalc", "3.2.1-deltacalc"))
    }

    @Test
    fun testComponentPadding() {
        // Missing components compare as zero
        assertFalse(UpdateChecker.isNewerVersion("3.2", "3.2.0"))
        assertFalse(UpdateChecker.isNewerVersion("3.2.0", "3.2"))
        assertFalse(UpdateChecker.isNewerVersion("3.2", "3.2.0.0"))
        assertTrue(UpdateChecker.isNewerVersion("3.2.1", "3.2"))
        assertFalse(UpdateChecker.isNewerVersion("3.2", "3.2.1"))
        assertTrue(UpdateChecker.isNewerVersion("3.2.0.1", "3.2.0"))
        assertFalse(UpdateChecker.isNewerVersion("3.2.0", "3.2.0.1"))
    }

    @Test
    fun testInvalidLatestFailsClosed() {
        assertFalse(UpdateChecker.isNewerVersion("", "3.2.0"))
        assertFalse(UpdateChecker.isNewerVersion("   ", "3.2.0"))
        assertFalse(UpdateChecker.isNewerVersion("invalid", "3.2.0"))
        assertFalse(UpdateChecker.isNewerVersion("v", "3.2.0"))
        assertFalse(UpdateChecker.isNewerVersion("V", "3.2.0"))
        assertNull(UpdateChecker.parseVersionCore(""))
        assertNull(UpdateChecker.parseVersionCore("invalid"))
    }

    @Test
    fun testInvalidCurrentFailsClosed() {
        assertFalse(UpdateChecker.isNewerVersion("3.2.1", ""))
        assertFalse(UpdateChecker.isNewerVersion("3.2.1", "   "))
        assertFalse(UpdateChecker.isNewerVersion("3.2.1", "invalid"))
        assertFalse(UpdateChecker.isNewerVersion("3.2.1", "v"))
        assertFalse(UpdateChecker.isNewerVersion("3.2.1", "V"))
        assertNull(UpdateChecker.parseVersionCore("v"))
    }

    @Test
    fun testNonnumericMiddleComponentFailsClosed() {
        // Must fail closed and never silently drop nonnumeric middle components
        assertFalse(UpdateChecker.isNewerVersion("3.foo.1", "3.2.0"))
        assertFalse(UpdateChecker.isNewerVersion("3.2.1", "3.foo.1"))
        assertFalse(UpdateChecker.isNewerVersion("3.foo.1", "3.0.0"))
        assertFalse(UpdateChecker.isNewerVersion("1.x.3", "1.0.0"))
        assertNull(UpdateChecker.parseVersionCore("3.foo.1"))
        assertNull(UpdateChecker.parseVersionCore("3.foo"))
        assertNull(UpdateChecker.parseVersionCore("foo.bar"))
    }
}
