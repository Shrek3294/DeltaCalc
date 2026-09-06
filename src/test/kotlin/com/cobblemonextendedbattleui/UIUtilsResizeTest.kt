package com.cobblemonextendedbattleui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UIUtilsResizeTest {

    private fun resize(
        zone: UIUtils.ResizeZone,
        deltaX: Int,
        deltaY: Int,
        startX: Int = 100,
        startY: Int = 100,
        startW: Int = 200,
        startH: Int = 150,
        minW: Int = 180,
        maxW: Int = 400,
        minH: Int = 28,
        maxH: Int = 300,
        screenW: Int = 800,
        screenH: Int = 600
    ): UIUtils.ResizeResult = UIUtils.calculateResize(
        zone = zone,
        deltaX = deltaX,
        deltaY = deltaY,
        startX = startX,
        startY = startY,
        startWidth = startW,
        startHeight = startH,
        minWidth = minW,
        maxWidth = maxW,
        minHeight = minH,
        maxHeight = maxH,
        screenWidth = screenW,
        screenHeight = screenH
    )

    @Test
    fun testAllResizeZones() {
        val testCases = listOf(
            TestCase(UIUtils.ResizeZone.RIGHT, 50, 0, 100, 100, 250, 150),
            TestCase(UIUtils.ResizeZone.BOTTOM, 0, 50, 100, 100, 200, 200),
            TestCase(UIUtils.ResizeZone.BOTTOM_RIGHT, 50, 40, 100, 100, 250, 190),
            TestCase(UIUtils.ResizeZone.LEFT, -40, 0, 60, 100, 240, 150),
            TestCase(UIUtils.ResizeZone.TOP, 0, -30, 100, 70, 200, 180),
            TestCase(UIUtils.ResizeZone.TOP_LEFT, -40, -30, 60, 70, 240, 180),
            TestCase(UIUtils.ResizeZone.TOP_RIGHT, 50, -30, 100, 70, 250, 180),
            TestCase(UIUtils.ResizeZone.BOTTOM_LEFT, -40, 50, 60, 100, 240, 200),
            TestCase(UIUtils.ResizeZone.NONE, 50, 50, 100, 100, 200, 150)
        )

        for (case in testCases) {
            val result = resize(case.zone, case.deltaX, case.deltaY)
            assertEquals(
                UIUtils.ResizeResult(case.expectedX, case.expectedY, case.expectedW, case.expectedH),
                result,
                "Failed for zone ${case.zone}"
            )
        }
    }

    @Test
    fun testEdgeClampingAndInvertedBounds() {
        // Left expansion past origin stops at x=0
        val clampLeft = resize(UIUtils.ResizeZone.LEFT, deltaX = -300, deltaY = 0, startX = 100, startW = 150)
        assertEquals(0, clampLeft.newX)
        assertEquals(250, clampLeft.newWidth)

        // Right expansion past screen stops at screen edge
        val clampRight = resize(UIUtils.ResizeZone.RIGHT, deltaX = 400, deltaY = 0, startX = 600, startW = 100, screenW = 800)
        assertEquals(600, clampRight.newX)
        assertEquals(200, clampRight.newWidth)

        // Inverted min/max (maxWidth < minWidth) handles safely
        val inverted = resize(UIUtils.ResizeZone.RIGHT, deltaX = 50, deltaY = 0, minW = 300, maxW = 150)
        assertEquals(150, inverted.newWidth)
    }

    private data class TestCase(
        val zone: UIUtils.ResizeZone,
        val deltaX: Int,
        val deltaY: Int,
        val expectedX: Int,
        val expectedY: Int,
        val expectedW: Int,
        val expectedH: Int
    )
}
