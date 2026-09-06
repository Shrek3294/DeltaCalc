package com.cobblemonextendedbattleui.ui.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResponsiveGeometryTest {

    private val defaultViewport = Viewport(800, 600)

    @Test
    fun testReconcileBoundaryClamping() {
        val cases = listOf(
            LayoutRect(2500, 1800, 200, 150) to LayoutRect(600, 450, 200, 150),
            LayoutRect(-400, -250, 200, 150) to LayoutRect(0, 0, 200, 150),
            LayoutRect(700, 100, 200, 150) to LayoutRect(600, 100, 200, 150),
            LayoutRect(100, 520, 200, 150) to LayoutRect(100, 450, 200, 150),
            LayoutRect(50, 50, 1000, 800) to LayoutRect(0, 0, 800, 600),
            LayoutRect(120, 80, 228, 310) to LayoutRect(120, 80, 228, 310)
        )

        for ((input, expected) in cases) {
            val actual = ResponsiveGeometry.reconcile(input, defaultViewport)
            assertEquals(expected, actual, "Failed for input: $input")
            assertTrue(ResponsiveGeometry.isReachable(actual, defaultViewport))
        }
    }

    @Test
    fun testDegenerateViewports() {
        val rect = LayoutRect(50, 50, 200, 150)
        val degenerateViewports = listOf(
            Viewport(0, 0),
            Viewport(-100, -50),
            Viewport(0, 400),
            Viewport(400, -20)
        )

        for (vp in degenerateViewports) {
            assertEquals(LayoutRect(0, 0, 0, 0), ResponsiveGeometry.reconcile(rect, vp))
        }

        val tinyVp = Viewport(100, 80)
        val tinyResult = ResponsiveGeometry.reconcile(rect, tinyVp, minWidth = 180, minHeight = 120)
        assertEquals(LayoutRect(0, 0, 100, 80), tinyResult)
        assertTrue(ResponsiveGeometry.isReachable(tinyResult, tinyVp))
    }

    @Test
    fun testLayoutRectContainsHalfOpenSemantics() {
        val rect = LayoutRect(10, 20, 30, 40)

        // Interior points
        assertTrue(rect.contains(10, 20))
        assertTrue(rect.contains(15, 25))
        assertTrue(rect.contains(39, 59))

        // Half-open outer boundaries are exclusive
        assertFalse(rect.contains(40, 20))
        assertFalse(rect.contains(10, 60))
        assertFalse(rect.contains(40, 60))

        // Exterior points
        assertFalse(rect.contains(9, 20))
        assertFalse(rect.contains(10, 19))

        // Non-positive dimensions never contain points
        assertFalse(LayoutRect(10, 20, 0, 40).contains(10, 20))
        assertFalse(LayoutRect(10, 20, 30, 0).contains(10, 20))
        assertFalse(LayoutRect(10, 20, -10, -10).contains(10, 20))
    }

    @Test
    fun testHardenedAgainstExtremeIntInputs() {
        val extremeCases = listOf(
            LayoutRect(Int.MIN_VALUE, Int.MAX_VALUE, 200, 150) to LayoutRect(0, 450, 200, 150),
            LayoutRect(50, 50, Int.MAX_VALUE, Int.MAX_VALUE) to LayoutRect(0, 0, 800, 600)
        )

        for ((input, expected) in extremeCases) {
            val result = ResponsiveGeometry.reconcile(input, defaultViewport)
            assertEquals(expected, result)
            assertTrue(ResponsiveGeometry.isReachable(result, defaultViewport))
        }

        val negativeDims = LayoutRect(50, 50, Int.MIN_VALUE, -100)
        val resultNeg = ResponsiveGeometry.reconcile(negativeDims, defaultViewport, minWidth = 100, minHeight = 80)
        assertEquals(LayoutRect(50, 50, 100, 80), resultNeg)

        val maxConstraints = ResponsiveGeometry.reconcile(
            LayoutRect(10, 10, 200, 150), defaultViewport,
            minWidth = Int.MAX_VALUE, minHeight = Int.MAX_VALUE, maxWidth = Int.MAX_VALUE, maxHeight = Int.MAX_VALUE
        )
        assertEquals(LayoutRect(0, 0, 800, 600), maxConstraints)

        val minConstraints = ResponsiveGeometry.reconcile(
            LayoutRect(10, 10, 200, 150), defaultViewport,
            minWidth = Int.MIN_VALUE, minHeight = Int.MIN_VALUE, maxWidth = Int.MIN_VALUE, maxHeight = Int.MIN_VALUE
        )
        assertEquals(LayoutRect(10, 10, 0, 0), minConstraints)
    }

    @Test
    fun testIdempotence() {
        val testCases = listOf(
            LayoutRect(100, 100, 200, 200) to Viewport(800, 600),
            LayoutRect(1200, 900, 200, 200) to Viewport(800, 600),
            LayoutRect(-50, -50, 200, 200) to Viewport(800, 600),
            LayoutRect(10, 10, 1000, 800) to Viewport(500, 400),
            LayoutRect(20, 20, 250, 200) to Viewport(100, 80),
            LayoutRect(50, 50, 200, 200) to Viewport(0, 0),
            LayoutRect(Int.MIN_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE) to Viewport(800, 600)
        )

        for ((rect, viewport) in testCases) {
            val once = ResponsiveGeometry.reconcile(rect, viewport, minWidth = 150, minHeight = 100)
            val twice = ResponsiveGeometry.reconcile(once, viewport, minWidth = 150, minHeight = 100)
            assertEquals(once, twice, "Idempotence failed for $rect in $viewport")
            assertTrue(ResponsiveGeometry.isReachable(once, viewport))
        }
    }

    @Test
    fun testSafeClampDimension() {
        assertEquals(228, ResponsiveGeometry.safeClampDimension(228, preferredMin = 180, maxAllowed = 400))
        assertEquals(180, ResponsiveGeometry.safeClampDimension(100, preferredMin = 180, maxAllowed = 400))
        assertEquals(400, ResponsiveGeometry.safeClampDimension(500, preferredMin = 180, maxAllowed = 400))
        assertEquals(120, ResponsiveGeometry.safeClampDimension(200, preferredMin = 180, maxAllowed = 120))
        assertEquals(0, ResponsiveGeometry.safeClampDimension(100, preferredMin = 180, maxAllowed = 0))
        assertEquals(0, ResponsiveGeometry.safeClampDimension(100, preferredMin = 180, maxAllowed = -50))
        assertEquals(0, ResponsiveGeometry.safeClampDimension(100, preferredMin = 180, maxAllowed = Int.MIN_VALUE))
        assertEquals(100, ResponsiveGeometry.safeClampDimension(50, preferredMin = Int.MAX_VALUE, maxAllowed = 100))
    }

    @Test
    fun testClampPosition() {
        val cases = listOf(
            PositionCase(100, 150, 200, 150, 800, 600, expectedX = 100, expectedY = 150),
            PositionCase(900, 700, 200, 150, 800, 600, expectedX = 600, expectedY = 450),
            PositionCase(-50, -50, 200, 150, 800, 600, expectedX = 0, expectedY = 0),
            PositionCase(50, 50, 400, 300, 200, 150, expectedX = 0, expectedY = 0),
            PositionCase(50, 50, 100, 100, 0, 0, expectedX = 0, expectedY = 0),
            PositionCase(Int.MIN_VALUE, Int.MAX_VALUE, 200, 150, 800, 600, expectedX = 0, expectedY = 450)
        )

        for (c in cases) {
            val point = ResponsiveGeometry.clampPosition(c.x, c.y, c.w, c.h, c.vw, c.vh)
            assertEquals(LayoutPoint(c.expectedX, c.expectedY), point, "Failed for $c")
        }
    }

    @Test
    fun testNullableReconcileAndDerivedAlignment() {
        val result = ResponsiveGeometry.reconcile(
            x = null, y = null, width = null, height = null,
            viewportWidth = 800, viewportHeight = 600,
            defaultWidth = 228, defaultHeight = 310,
            defaultX = { w, vw -> vw - w - 14 },
            defaultY = { _, _ -> 100 }
        )
        assertEquals(LayoutRect(x = 558, y = 100, width = 228, height = 310), result)
        assertEquals(786L, result.right)
        assertTrue(ResponsiveGeometry.isReachable(result, defaultViewport))
    }

    @Test
    fun testNullXWithOversizedOrCorruptWidthPreservesRightAlignment() {
        val screenW = 800
        val screenH = 600
        val maxW = (screenW * 0.65f).toInt() // 520
        val maxH = (screenH * 0.8f).toInt() // 480

        // Oversized width 2000 clamps to 520; defaultX derives 800 - 520 - 14 = 266
        val oversized = ResponsiveGeometry.reconcile(
            x = null, y = 100, width = 2000, height = 350,
            viewportWidth = screenW, viewportHeight = screenH,
            defaultWidth = 228, defaultHeight = 310,
            minWidth = 180, minHeight = 28, maxWidth = maxW, maxHeight = maxH,
            defaultX = { w, vw -> vw - w - 14 }, defaultY = { _, _ -> 100 }
        )
        assertEquals(520, oversized.width)
        assertEquals(266, oversized.x)
        assertEquals(786L, oversized.right)

        // Int.MIN_VALUE width clamps to 180 without arithmetic overflow; defaultX derives 800 - 180 - 14 = 606
        val minCorrupt = ResponsiveGeometry.reconcile(
            x = null, y = null, width = Int.MIN_VALUE, height = Int.MIN_VALUE,
            viewportWidth = screenW, viewportHeight = screenH,
            defaultWidth = 228, defaultHeight = 310,
            minWidth = 180, minHeight = 28, maxWidth = maxW, maxHeight = maxH,
            defaultX = { w, vw -> vw - w - 14 }, defaultY = { _, _ -> 100 }
        )
        assertEquals(180, minCorrupt.width)
        assertEquals(606, minCorrupt.x)
        assertEquals(786L, minCorrupt.right)
        assertEquals(28, minCorrupt.height)

        // Int.MAX_VALUE width clamps to 520
        val maxCorrupt = ResponsiveGeometry.reconcile(
            x = null, y = null, width = Int.MAX_VALUE, height = Int.MAX_VALUE,
            viewportWidth = screenW, viewportHeight = screenH,
            defaultWidth = 228, defaultHeight = 310,
            minWidth = 180, minHeight = 28, maxWidth = maxW, maxHeight = maxH,
            defaultX = { w, vw -> vw - w - 14 }, defaultY = { _, _ -> 100 }
        )
        assertEquals(520, maxCorrupt.width)
        assertEquals(266, maxCorrupt.x)
        assertEquals(786L, maxCorrupt.right)
    }

    @Test
    fun testCalculatorMaxPolicyMatrix() {
        val minW = 180
        val minH = 28

        // Large screen (1920x1080): 65% = 1248, 80% = 864
        val maxWLarge = minOf(1920, maxOf(minW, (1920 * 0.65f).toInt()))
        val maxHLarge = minOf(1080, maxOf(minH, (1080 * 0.8f).toInt()))
        val large = ResponsiveGeometry.reconcile(
            LayoutRect(0, 0, 2000, 1500), Viewport(1920, 1080),
            minWidth = minW, minHeight = minH, maxWidth = maxWLarge, maxHeight = maxHLarge
        )
        assertEquals(LayoutRect(0, 0, 1248, 864), large)

        // Constrained screen (240x200): 65% = 156 (< 180), viewport supports 180
        val maxWMed = minOf(240, maxOf(minW, (240 * 0.65f).toInt()))
        val maxHMed = minOf(200, maxOf(minH, (200 * 0.8f).toInt()))
        val med = ResponsiveGeometry.reconcile(
            LayoutRect(50, 50, 228, 310), Viewport(240, 200),
            minWidth = minW, minHeight = minH, maxWidth = maxWMed, maxHeight = maxHMed
        )
        assertEquals(LayoutRect(50, 40, 180, 160), med)

        // Tiny screen (140x100): viewport smaller than minW 180 -> viewport safety wins
        val maxWTiny = minOf(140, maxOf(minW, (140 * 0.65f).toInt()))
        val maxHTiny = minOf(100, maxOf(minH, (100 * 0.8f).toInt()))
        val tiny = ResponsiveGeometry.reconcile(
            LayoutRect(10, 10, 228, 310), Viewport(140, 100),
            minWidth = minW, minHeight = minH, maxWidth = maxWTiny, maxHeight = maxHTiny
        )
        assertEquals(LayoutRect(0, 10, 140, 80), tiny)
    }

    @Test
    fun testCollapsedModeHeightResolution() {
        val collapsed = ResponsiveGeometry.reconcile(
            x = null, y = null, width = 228, height = 28,
            viewportWidth = 800, viewportHeight = 600,
            defaultWidth = 228, defaultHeight = 28,
            minWidth = 180, minHeight = 28,
            defaultX = { w, vw -> vw - w - 14 }, defaultY = { _, _ -> 100 }
        )
        assertEquals(28, collapsed.height)
        assertEquals(558, collapsed.x)
    }

    @Test
    fun testCalculateResizeEdgeCases() {
        // Right resize hitting screen boundary
        val resizedRight = ResponsiveGeometry.calculateResize(
            startX = 600, startY = 100, startWidth = 150, startHeight = 100,
            deltaX = 200, deltaY = 0,
            modifiesLeft = false, modifiesRight = true, modifiesTop = false, modifiesBottom = false,
            minWidth = 100, maxWidth = 500, minHeight = 80, maxHeight = 400,
            viewportWidth = 800, viewportHeight = 600
        )
        assertEquals(LayoutRect(600, 100, 200, 100), resizedRight)

        // Left resize expanding past screen origin stops at 0
        val resizedLeft = ResponsiveGeometry.calculateResize(
            startX = 100, startY = 100, startWidth = 150, startHeight = 100,
            deltaX = -300, deltaY = 0,
            modifiesLeft = true, modifiesRight = false, modifiesTop = false, modifiesBottom = false,
            minWidth = 50, maxWidth = 500, minHeight = 80, maxHeight = 400,
            viewportWidth = 800, viewportHeight = 600
        )
        assertEquals(LayoutRect(0, 100, 250, 100), resizedLeft)

        // Resize on tiny screen with minWidth > viewport
        val resizedTiny = ResponsiveGeometry.calculateResize(
            startX = 0, startY = 0, startWidth = 80, startHeight = 60,
            deltaX = 50, deltaY = 50,
            modifiesLeft = false, modifiesRight = true, modifiesTop = false, modifiesBottom = true,
            minWidth = 150, maxWidth = 300, minHeight = 120, maxHeight = 300,
            viewportWidth = 100, viewportHeight = 80
        )
        assertEquals(LayoutRect(0, 0, 100, 80), resizedTiny)

        // Inactive edges on out-of-bounds start normalizes to valid bounds
        val noEdge = ResponsiveGeometry.calculateResize(
            startX = 2000, startY = 1500, startWidth = 300, startHeight = 200,
            deltaX = 50, deltaY = 50,
            modifiesLeft = false, modifiesRight = false, modifiesTop = false, modifiesBottom = false,
            minWidth = 100, maxWidth = 500, minHeight = 80, maxHeight = 400,
            viewportWidth = 800, viewportHeight = 600
        )
        assertEquals(LayoutRect(500, 400, 300, 200), noEdge)

        // Inverted min/max constraints
        val inverted = ResponsiveGeometry.calculateResize(
            startX = 100, startY = 100, startWidth = 200, startHeight = 200,
            deltaX = 50, deltaY = 50,
            modifiesLeft = false, modifiesRight = true, modifiesTop = false, modifiesBottom = true,
            minWidth = 300, maxWidth = 150, minHeight = 250, maxHeight = 120,
            viewportWidth = 800, viewportHeight = 600
        )
        assertEquals(LayoutRect(100, 100, 150, 120), inverted)

        // Degenerate viewport
        val zeroVp = ResponsiveGeometry.calculateResize(
            startX = 10, startY = 10, startWidth = 100, startHeight = 100,
            deltaX = 10, deltaY = 10,
            modifiesLeft = false, modifiesRight = true, modifiesTop = false, modifiesBottom = true,
            minWidth = 50, maxWidth = 200, minHeight = 50, maxHeight = 200,
            viewportWidth = 0, viewportHeight = -10
        )
        assertEquals(LayoutRect(0, 0, 0, 0), zeroVp)

        // Extreme Int inputs
        val extreme = ResponsiveGeometry.calculateResize(
            startX = Int.MIN_VALUE, startY = Int.MAX_VALUE, startWidth = Int.MAX_VALUE, startHeight = Int.MAX_VALUE,
            deltaX = Int.MAX_VALUE, deltaY = Int.MIN_VALUE,
            modifiesLeft = true, modifiesRight = false, modifiesTop = true, modifiesBottom = false,
            minWidth = 100, maxWidth = 500, minHeight = 100, maxHeight = 500,
            viewportWidth = 800, viewportHeight = 600
        )
        assertTrue(ResponsiveGeometry.isReachable(extreme, defaultViewport))
    }

    @Test
    fun testClampCoordWithMargin() {
        // Normal case with sufficient slack
        assertEquals(4, ResponsiveGeometry.clampCoordWithMargin(candidate = 0, widgetSpan = 200, viewportSpan = 800, margin = 4))
        assertEquals(100, ResponsiveGeometry.clampCoordWithMargin(candidate = 100, widgetSpan = 200, viewportSpan = 800, margin = 4))
        assertEquals(596, ResponsiveGeometry.clampCoordWithMargin(candidate = 700, widgetSpan = 200, viewportSpan = 800, margin = 4))

        // Exact fit with viewport: pins to 0
        assertEquals(0, ResponsiveGeometry.clampCoordWithMargin(candidate = 50, widgetSpan = 800, viewportSpan = 800, margin = 4))

        // Oversized widget: pins to 0
        assertEquals(0, ResponsiveGeometry.clampCoordWithMargin(candidate = 50, widgetSpan = 900, viewportSpan = 800, margin = 4))

        // Tight slack (< 2 * margin): clamps within [0, totalSlack]
        // viewport = 206, widget = 200, totalSlack = 6, margin = 4 -> [0, 6]
        assertEquals(6, ResponsiveGeometry.clampCoordWithMargin(candidate = 50, widgetSpan = 200, viewportSpan = 206, margin = 4))
        assertEquals(0, ResponsiveGeometry.clampCoordWithMargin(candidate = -10, widgetSpan = 200, viewportSpan = 206, margin = 4))
        assertEquals(3, ResponsiveGeometry.clampCoordWithMargin(candidate = 3, widgetSpan = 200, viewportSpan = 206, margin = 4))

        // Zero / negative viewport returns 0
        assertEquals(0, ResponsiveGeometry.clampCoordWithMargin(candidate = 10, widgetSpan = 50, viewportSpan = 0, margin = 4))
        assertEquals(0, ResponsiveGeometry.clampCoordWithMargin(candidate = 10, widgetSpan = 50, viewportSpan = -100, margin = 4))

        // Zero / negative margin behaves like standard clampCoord
        assertEquals(0, ResponsiveGeometry.clampCoordWithMargin(candidate = -10, widgetSpan = 100, viewportSpan = 500, margin = 0))
        assertEquals(400, ResponsiveGeometry.clampCoordWithMargin(candidate = 600, widgetSpan = 100, viewportSpan = 500, margin = -5))

        // Extreme inputs never throw or overflow
        assertEquals(4, ResponsiveGeometry.clampCoordWithMargin(candidate = Int.MIN_VALUE, widgetSpan = 200, viewportSpan = 800, margin = 4))
        assertEquals(596, ResponsiveGeometry.clampCoordWithMargin(candidate = Int.MAX_VALUE, widgetSpan = 200, viewportSpan = 800, margin = 4))
        assertEquals(0, ResponsiveGeometry.clampCoordWithMargin(candidate = Int.MAX_VALUE, widgetSpan = Int.MAX_VALUE, viewportSpan = 800, margin = 4))
    }

    @Test
    fun testResolvePlacementWithFallback() {
        // Preferred fits: returns preferred
        val preferredFits = ResponsiveGeometry.resolvePlacementWithFallback(
            preferredCoord = 50, widgetSpan = 100, viewportSpan = 800, margin = 4, fallbackCoord = 200
        )
        assertEquals(50, preferredFits)

        // Preferred does not fit on right/below, fallback fits: returns fallback
        val fallbackFits = ResponsiveGeometry.resolvePlacementWithFallback(
            preferredCoord = 750, widgetSpan = 100, viewportSpan = 800, margin = 4, fallbackCoord = 50
        )
        assertEquals(50, fallbackFits)

        // Preferred does not fit on left/above, fallback fits: returns fallback
        val fallbackLeftFits = ResponsiveGeometry.resolvePlacementWithFallback(
            preferredCoord = -20, widgetSpan = 100, viewportSpan = 800, margin = 4, fallbackCoord = 200
        )
        assertEquals(200, fallbackLeftFits)

        // Neither fits: clamps preferred to nearest reachable position
        val neitherFits = ResponsiveGeometry.resolvePlacementWithFallback(
            preferredCoord = 850, widgetSpan = 200, viewportSpan = 500, margin = 4, fallbackCoord = -100
        )
        assertEquals(296, neitherFits) // 500 - 200 - 4 = 296

        // Oversized widget: clamped to 0 without throwing
        val oversized = ResponsiveGeometry.resolvePlacementWithFallback(
            preferredCoord = 100, widgetSpan = 600, viewportSpan = 400, margin = 4, fallbackCoord = -50
        )
        assertEquals(0, oversized)

        // Degenerate viewport returns 0
        assertEquals(0, ResponsiveGeometry.resolvePlacementWithFallback(
            preferredCoord = 100, widgetSpan = 50, viewportSpan = 0, margin = 4, fallbackCoord = 20
        ))
    }

    private data class PositionCase(
        val x: Int, val y: Int, val w: Int, val h: Int, val vw: Int, val vh: Int,
        val expectedX: Int, val expectedY: Int
    )
}
