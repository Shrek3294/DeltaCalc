package com.cobblemonextendedbattleui

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BattleLogWidgetLayoutTest {

    @BeforeEach
    @AfterEach
    fun cleanUp() {
        BattleLogWidget.clear()
        PanelConfig.resetHudLayout()
    }

    @Test
    fun testReconcileBoundsWithDefaults() {
        val bounds = BattleLogWidget.reconcileBounds(
            savedX = null,
            savedY = null,
            savedWidth = null,
            savedHeight = null,
            isExpanded = true,
            screenWidth = 800,
            screenHeight = 600
        )

        assertEquals(200, bounds.width)
        assertEquals(120, bounds.height)
        assertEquals(590, bounds.x)
        assertEquals(425, bounds.y)
        assertEquals(545, bounds.y + bounds.height, "Bottom edge should be at screenHeight - 55")
    }

    @Test
    fun testReconcileBoundsPreservesBottomEdgeAnchorOnCollapse() {
        val expandedBounds = BattleLogWidget.reconcileBounds(
            savedX = null,
            savedY = null,
            savedWidth = null,
            savedHeight = null,
            isExpanded = true,
            screenWidth = 800,
            screenHeight = 600
        )

        val collapsedBounds = BattleLogWidget.reconcileBounds(
            savedX = null,
            savedY = null,
            savedWidth = null,
            savedHeight = null,
            isExpanded = false,
            screenWidth = 800,
            screenHeight = 600
        )

        val expectedBottom = 600 - 55
        assertEquals(expectedBottom, expandedBounds.y + expandedBounds.height)
        assertEquals(expectedBottom, collapsedBounds.y + collapsedBounds.height)
        assertEquals(70, collapsedBounds.height)
        assertEquals(475, collapsedBounds.y)
        assertEquals(expandedBounds.x, collapsedBounds.x)
    }

    @Test
    fun testReconcileBoundsPreservesBottomEdgeAnchorWithCustomPosition() {
        // User positioned the log widget at (100, 200) with height 150 (bottom is 350)
        val expandedBounds = BattleLogWidget.reconcileBounds(
            savedX = 100,
            savedY = 200,
            savedWidth = 220,
            savedHeight = 150,
            isExpanded = true,
            screenWidth = 800,
            screenHeight = 600
        )

        val collapsedBounds = BattleLogWidget.reconcileBounds(
            savedX = 100,
            savedY = 200,
            savedWidth = 220,
            savedHeight = 150,
            isExpanded = false,
            screenWidth = 800,
            screenHeight = 600
        )

        assertEquals(350, expandedBounds.y + expandedBounds.height)
        assertEquals(350, collapsedBounds.y + collapsedBounds.height)
        assertEquals(70, collapsedBounds.height)
        assertEquals(280, collapsedBounds.y)
    }

    @Test
    fun testReconcileBoundsTinyViewportNeverInvertsOrCrashes() {
        val bounds = BattleLogWidget.reconcileBounds(
            savedX = 500,
            savedY = 400,
            savedWidth = 300,
            savedHeight = 200,
            isExpanded = true,
            screenWidth = 50,
            screenHeight = 40
        )

        assertEquals(50, bounds.width)
        assertEquals(40, bounds.height)
        assertEquals(0, bounds.x)
        assertEquals(0, bounds.y)
        assertTrue(bounds.x >= 0)
        assertTrue(bounds.y >= 0)
        assertTrue(bounds.x + bounds.width <= 50)
        assertTrue(bounds.y + bounds.height <= 40)
    }

    @Test
    fun testReconcileBoundsOneByOneViewport() {
        val bounds = BattleLogWidget.reconcileBounds(
            savedX = 10,
            savedY = 10,
            savedWidth = 200,
            savedHeight = 120,
            isExpanded = true,
            screenWidth = 1,
            screenHeight = 1
        )

        assertEquals(1, bounds.width)
        assertEquals(1, bounds.height)
        assertEquals(0, bounds.x)
        assertEquals(0, bounds.y)
        assertTrue(bounds.width < 30, "1x1 width is below minimal safe widget width (30)")
    }

    @Test
    fun testReconcileBoundsZeroOrNegativeViewportReturnsZeros() {
        val zeroBounds = BattleLogWidget.reconcileBounds(
            savedX = 10,
            savedY = 10,
            savedWidth = 100,
            savedHeight = 100,
            isExpanded = true,
            screenWidth = 0,
            screenHeight = 0
        )
        assertEquals(0, zeroBounds.width)
        assertEquals(0, zeroBounds.height)
        assertEquals(0, zeroBounds.x)
        assertEquals(0, zeroBounds.y)

        val negativeBounds = BattleLogWidget.reconcileBounds(
            savedX = 10,
            savedY = 10,
            savedWidth = 100,
            savedHeight = 100,
            isExpanded = true,
            screenWidth = -100,
            screenHeight = -50
        )
        assertEquals(0, negativeBounds.width)
        assertEquals(0, negativeBounds.height)
        assertEquals(0, negativeBounds.x)
        assertEquals(0, negativeBounds.y)
    }

    @Test
    fun testReconcileBoundsOverflowResistant() {
        val bounds = BattleLogWidget.reconcileBounds(
            savedX = Int.MAX_VALUE,
            savedY = Int.MAX_VALUE,
            savedWidth = Int.MAX_VALUE,
            savedHeight = Int.MAX_VALUE,
            isExpanded = true,
            screenWidth = 800,
            screenHeight = 600
        )

        assertEquals(400, bounds.width)
        assertEquals(300, bounds.height)
        assertEquals(400, bounds.x) // 800 - 400
        assertEquals(300, bounds.y) // 600 - 300
    }
}
