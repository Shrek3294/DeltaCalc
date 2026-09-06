package com.cobblemonextendedbattleui

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TeamIndicatorLayoutTest {

    @BeforeEach
    @AfterEach
    fun resetPositions() {
        PanelConfig.resetAllTeamIndicatorSettings()
    }

    // ─── calculateIndicatorY tests ──────────────────────────────────────────

    @Test
    fun testCalculateIndicatorYSinglesDoublesTriples() {
        // Zero / negative: base offset
        val y0 = TeamIndicatorUI.calculateIndicatorY(0)
        assertEquals(TeamIndicatorUI.VERTICAL_INSET + TeamIndicatorUI.TILE_HEIGHT + TeamIndicatorUI.MODEL_OFFSET_Y, y0)

        val yNeg = TeamIndicatorUI.calculateIndicatorY(-5)
        assertEquals(y0, yNeg)

        // Singles (count = 1): base offset (1-1)*15 = 0
        val y1 = TeamIndicatorUI.calculateIndicatorY(1)
        assertEquals(TeamIndicatorUI.VERTICAL_INSET + TeamIndicatorUI.TILE_HEIGHT + TeamIndicatorUI.MODEL_OFFSET_Y, y1)

        // Doubles (count = 2): +15px spacing
        val y2 = TeamIndicatorUI.calculateIndicatorY(2)
        assertEquals(y1 + 15, y2)

        // Triples (count = 3): uses compact tile height and 30px effective spacing
        val y3 = TeamIndicatorUI.calculateIndicatorY(3)
        val expectedY3 = TeamIndicatorUI.VERTICAL_INSET + 2 * 30 + TeamIndicatorUI.COMPACT_TILE_HEIGHT + TeamIndicatorUI.MODEL_OFFSET_Y
        assertEquals(expectedY3, y3)

        // Huge activeCount (e.g. 1000): clamped to 100, no integer overflow
        val yHuge = TeamIndicatorUI.calculateIndicatorY(1000)
        assertTrue(yHuge > 0)
        val expectedHuge = TeamIndicatorUI.VERTICAL_INSET + (100 - 1) * 30 + TeamIndicatorUI.COMPACT_TILE_HEIGHT + TeamIndicatorUI.MODEL_OFFSET_Y
        assertEquals(expectedHuge, yHuge)
    }

    // ─── calculateModelPositionFromDrag tests ───────────────────────────────

    @Test
    fun testCalculateModelPositionFromDrag() {
        val padding = TeamIndicatorUI.PANEL_PADDING_H
        val panelWidth = 160
        val screenWidth = 800

        // Normal drag in middle of screen
        val midPos = TeamIndicatorUI.calculateModelPositionFromDrag(
            dragStartModelCoord = 100, delta = 50, padding = padding,
            panelSpan = panelWidth, viewportSpan = screenWidth
        )
        // startPanel = 100 - 5 = 95; delta = 50 -> candidatePanel = 145 -> clamped = 145 -> model = 150
        assertEquals(150, midPos)

        // Drag past right edge: panel clamped to screenWidth - panelWidth = 640 -> model = 645
        val rightPos = TeamIndicatorUI.calculateModelPositionFromDrag(
            dragStartModelCoord = 700, delta = 200, padding = padding,
            panelSpan = panelWidth, viewportSpan = screenWidth
        )
        assertEquals(640 + padding, rightPos)

        // Drag past left edge: panel clamped to 0 -> model = 0 + padding
        val leftPos = TeamIndicatorUI.calculateModelPositionFromDrag(
            dragStartModelCoord = 50, delta = -100, padding = padding,
            panelSpan = panelWidth, viewportSpan = screenWidth
        )
        assertEquals(padding, leftPos)

        // Oversized panel (panelSpan > viewportSpan): pins panel to 0 -> model = padding
        val oversizedPos = TeamIndicatorUI.calculateModelPositionFromDrag(
            dragStartModelCoord = 100, delta = 50, padding = padding,
            panelSpan = 900, viewportSpan = screenWidth
        )
        assertEquals(padding, oversizedPos)

        // Zero / negative viewport: returns padding safely
        assertEquals(padding, TeamIndicatorUI.calculateModelPositionFromDrag(100, 50, padding, panelWidth, 0))
        assertEquals(padding, TeamIndicatorUI.calculateModelPositionFromDrag(100, 50, padding, panelWidth, -50))

        // Extreme inputs never throw or overflow
        val extremePos = TeamIndicatorUI.calculateModelPositionFromDrag(
            dragStartModelCoord = Int.MIN_VALUE, delta = Int.MIN_VALUE, padding = padding,
            panelSpan = panelWidth, viewportSpan = screenWidth
        )
        assertEquals(padding, extremePos)
    }

    // ─── getTeamPosition tests ──────────────────────────────────────────────

    @Test
    fun testGetTeamPositionDefaultPlacement() {
        val screenWidth = 800
        val screenHeight = 600
        val defaultY = 60
        val teamSize = 6

        // Left side default
        val (leftX, leftY) = TeamIndicatorUI.getTeamPosition(
            isLeftSide = true, teamSize = teamSize, defaultY = defaultY,
            screenWidth = screenWidth, screenHeight = screenHeight
        )
        assertEquals(TeamIndicatorUI.HORIZONTAL_INSET, leftX)
        assertEquals(defaultY, leftY)

        // Right side default in horizontal mode
        val (rightX, rightY) = TeamIndicatorUI.getTeamPosition(
            isLeftSide = false, teamSize = teamSize, defaultY = defaultY,
            screenWidth = screenWidth, screenHeight = screenHeight
        )
        val teamWidth = teamSize * TeamIndicatorUI.modelSize + (teamSize - 1) * TeamIndicatorUI.modelSpacing
        assertEquals(screenWidth - TeamIndicatorUI.HORIZONTAL_INSET - teamWidth, rightX)
        assertEquals(defaultY, rightY)

        // Right side default in vertical mode
        PanelConfig.toggleTeamIndicatorOrientation()
        val (vertRightX, vertRightY) = TeamIndicatorUI.getTeamPosition(
            isLeftSide = false, teamSize = teamSize, defaultY = defaultY,
            screenWidth = screenWidth, screenHeight = screenHeight
        )
        assertEquals(screenWidth - TeamIndicatorUI.HORIZONTAL_INSET - TeamIndicatorUI.modelSize, vertRightX)
        assertEquals(defaultY, vertRightY)
    }

    @Test
    fun testGetTeamPositionSavedPositionReconciliation() {
        val screenWidth = 800
        val screenHeight = 600

        // Custom position within screen is preserved exactly
        PanelConfig.setTeamIndicatorLeftPosition(150, 200)
        val (clampedX, clampedY) = TeamIndicatorUI.getTeamPosition(
            isLeftSide = true, teamSize = 6, defaultY = 60,
            screenWidth = screenWidth, screenHeight = screenHeight
        )
        assertEquals(150, clampedX)
        assertEquals(200, clampedY)

        // Extreme off-screen positive coordinates: clamped so panel right/bottom edge stays on screen
        PanelConfig.setTeamIndicatorLeftPosition(5000, 5000)
        val (extremePosX, extremePosY) = TeamIndicatorUI.getTeamPosition(
            isLeftSide = true, teamSize = 6, defaultY = 60,
            screenWidth = screenWidth, screenHeight = screenHeight
        )
        val (panelWidth, panelHeight) = TeamIndicatorUI.calculatePanelDimensions(6)
        // Panel right edge at screenWidth: panelX = 800 - panelWidth -> modelX = 800 - panelWidth + PANEL_PADDING_H
        assertEquals(screenWidth - panelWidth + TeamIndicatorUI.PANEL_PADDING_H, extremePosX)
        assertEquals(screenHeight - panelHeight + TeamIndicatorUI.PANEL_PADDING_V, extremePosY)
        // Config itself is not modified during reconciliation
        assertEquals(5000, PanelConfig.teamIndicatorLeftX)
        assertEquals(5000, PanelConfig.teamIndicatorLeftY)

        // Extreme off-screen negative coordinates: clamped so panel left/top edge is at 0
        PanelConfig.setTeamIndicatorLeftPosition(-5000, -5000)
        val (extremeNegX, extremeNegY) = TeamIndicatorUI.getTeamPosition(
            isLeftSide = true, teamSize = 6, defaultY = 60,
            screenWidth = screenWidth, screenHeight = screenHeight
        )
        assertEquals(TeamIndicatorUI.PANEL_PADDING_H, extremeNegX)
        assertEquals(TeamIndicatorUI.PANEL_PADDING_V, extremeNegY)
        assertEquals(-5000, PanelConfig.teamIndicatorLeftX)
    }

    @Test
    fun testGetTeamPositionTinyViewportSafeClamping() {
        val (panelWidth, panelHeight) = TeamIndicatorUI.calculatePanelDimensions(6)
        assertEquals(169, panelWidth)
        assertEquals(28, panelHeight)

        // Viewport width smaller than panel, height slightly larger than panel (30 > 28)
        val (partialX, partialY) = TeamIndicatorUI.getTeamPosition(
            isLeftSide = true, teamSize = 6, defaultY = 60,
            screenWidth = 50, screenHeight = 30
        )
        // Width: 169 > 50 -> pinned to 0 -> modelX = 0 + padding = 5
        assertEquals(TeamIndicatorUI.PANEL_PADDING_H, partialX)
        // Height: 28 <= 30 -> clamped to 30 - 28 = 2 -> modelY = 2 + padding = 4
        assertEquals(2 + TeamIndicatorUI.PANEL_PADDING_V, partialY)

        // Viewport both dimensions smaller than panel (50 < 169, 20 < 28)
        val (tinyX, tinyY) = TeamIndicatorUI.getTeamPosition(
            isLeftSide = true, teamSize = 6, defaultY = 60,
            screenWidth = 50, screenHeight = 20
        )
        // Both spans > viewport -> pinned to 0 -> model = 0 + padding
        assertEquals(TeamIndicatorUI.PANEL_PADDING_H, tinyX)
        assertEquals(TeamIndicatorUI.PANEL_PADDING_V, tinyY)
    }
}
