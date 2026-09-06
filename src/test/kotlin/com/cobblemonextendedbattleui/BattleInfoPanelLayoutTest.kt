package com.cobblemonextendedbattleui

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BattleInfoPanelLayoutTest {

    @BeforeEach
    @AfterEach
    fun cleanUp() {
        PanelConfig.resetHudLayout()
    }

    @Test
    fun testReconcileBoundsWithDefaults() {
        val bounds = BattleInfoPanel.reconcileBounds(
            savedX = null,
            savedY = null,
            savedWidth = null,
            savedHeight = null,
            autoHeight = 160,
            isExpanded = true,
            screenWidth = 800,
            screenHeight = 600
        )

        assertEquals(200, bounds.width)
        assertEquals(160, bounds.height)
        assertEquals(10, bounds.x) // Left margin (10) to avoid fresh-install overlap with DamageCalc
        assertEquals(220, bounds.y) // (600 - 160) / 2
    }

    @Test
    fun testReconcileBoundsTinyViewportSafeClamping() {
        // screenWidth=80 -> maxAllowedW = 68
        // screenHeight=50 -> maxAllowedH = 42
        val bounds = BattleInfoPanel.reconcileBounds(
            savedX = 500,
            savedY = 400,
            savedWidth = 300,
            savedHeight = 250,
            autoHeight = 120,
            isExpanded = true,
            screenWidth = 80,
            screenHeight = 50
        )

        assertEquals(68, bounds.width)
        assertEquals(42, bounds.height)
        assertTrue(bounds.x >= 0)
        assertTrue(bounds.y >= 0)
        assertTrue(bounds.x + bounds.width <= 80)
        assertTrue(bounds.y + bounds.height <= 50)
    }

    @Test
    fun testReconcileBoundsOneByOneViewport() {
        val bounds = BattleInfoPanel.reconcileBounds(
            savedX = 10,
            savedY = 10,
            savedWidth = 200,
            savedHeight = 150,
            autoHeight = 100,
            isExpanded = true,
            screenWidth = 1,
            screenHeight = 1
        )

        // 1x1 screen: maxAllowedW = (1 * 0.85).toInt() = 0, so clamped dimension is 0
        assertEquals(0, bounds.width)
        assertEquals(0, bounds.height)
        assertTrue(bounds.x in 0..1)
        assertTrue(bounds.y in 0..1)
        assertTrue(bounds.width < BattleInfoPanel.MIN_SAFE_WIDTH)
    }

    @Test
    fun testReconcileBoundsBelowMinSafeHeaderWidth() {
        // screenWidth=20 -> maxAllowedW = (20 * 0.85).toInt() = 17
        val bounds = BattleInfoPanel.reconcileBounds(
            savedX = null,
            savedY = null,
            savedWidth = null,
            savedHeight = null,
            autoHeight = 50,
            isExpanded = true,
            screenWidth = 20,
            screenHeight = 50
        )

        assertEquals(17, bounds.width)
        assertTrue(bounds.width < BattleInfoPanel.MIN_SAFE_WIDTH, "Resolved width 17 must be detected below MIN_SAFE_WIDTH (24)")
        assertTrue(bounds.x >= 0)
        assertTrue(bounds.x + bounds.width <= 20)
    }

    @Test
    fun testReconcileBoundsZeroOrNegativeViewportReturnsZeros() {
        val zeroBounds = BattleInfoPanel.reconcileBounds(
            savedX = 10,
            savedY = 10,
            savedWidth = 100,
            savedHeight = 100,
            autoHeight = 100,
            isExpanded = true,
            screenWidth = 0,
            screenHeight = 0
        )
        assertEquals(0, zeroBounds.width)
        assertEquals(0, zeroBounds.height)
        assertEquals(0, zeroBounds.x)
        assertEquals(0, zeroBounds.y)

        val negativeBounds = BattleInfoPanel.reconcileBounds(
            savedX = 10,
            savedY = 10,
            savedWidth = 100,
            savedHeight = 100,
            autoHeight = 100,
            isExpanded = true,
            screenWidth = -800,
            screenHeight = -600
        )
        assertEquals(0, negativeBounds.width)
        assertEquals(0, negativeBounds.height)
        assertEquals(0, negativeBounds.x)
        assertEquals(0, negativeBounds.y)
    }

    @Test
    fun testReconcileBoundsOverflowResistant() {
        val bounds = BattleInfoPanel.reconcileBounds(
            savedX = Int.MAX_VALUE,
            savedY = Int.MAX_VALUE,
            savedWidth = Int.MAX_VALUE,
            savedHeight = Int.MAX_VALUE,
            autoHeight = 150,
            isExpanded = true,
            screenWidth = 800,
            screenHeight = 600
        )

        val maxAllowedW = (800 * 0.85f).toInt() // 680
        val maxAllowedH = (600 * 0.85f).toInt() // 510
        assertEquals(maxAllowedW, bounds.width)
        assertEquals(maxAllowedH, bounds.height)
        assertEquals(800 - maxAllowedW, bounds.x)
        assertEquals(600 - maxAllowedH, bounds.y)
    }
}
