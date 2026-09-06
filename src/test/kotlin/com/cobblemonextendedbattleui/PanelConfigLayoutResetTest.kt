package com.cobblemonextendedbattleui

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PanelConfigLayoutResetTest {

    @BeforeEach
    @AfterEach
    fun restoreDefaults() {
        PanelConfig.resetHudLayout()
        PanelConfig.setEnableTeamIndicators(true)
        PanelConfig.setEnableBattleInfoPanel(true)
        PanelConfig.setEnableBattleLog(false)
        PanelConfig.setEnableMoveTooltips(true)
        PanelConfig.setFontScale(1.0f)
        PanelConfig.setStartExpanded(false)
        PanelConfig.adjustLogFontScale(1.0f - PanelConfig.logFontScale)
        PanelConfig.setLogExpanded(true)
        PanelConfig.setTeamIndicatorOrientation(PanelConfig.TeamIndicatorOrientation.HORIZONTAL)
        PanelConfig.adjustTeamIndicatorScale(1.0f - PanelConfig.teamIndicatorScale)
        PanelConfig.setTeamIndicatorRepositioningEnabled(true)
        PanelConfig.adjustTooltipFontScale(1.0f - PanelConfig.tooltipFontScale)
        PanelConfig.adjustMoveTooltipFontScale(1.0f - PanelConfig.moveTooltipFontScale)
        PanelConfig.setShowTeraType(false)
        PanelConfig.setShowStatRanges(true)
        PanelConfig.setShowBaseCritRate(false)
        PanelConfig.setShowCritDamage(false)
        PanelConfig.setShowMultiHitCount(true)
        PanelConfig.setDebugDumpEnabled(false)
    }

    @Test
    fun testResetHudLayoutClearsOnlyLayoutFields() {
        // Set layout coordinates and dimensions
        PanelConfig.setPosition(120, 240)
        PanelConfig.clampAndSetDimensions(320, 480, screenWidth = 800, screenHeight = 600)
        PanelConfig.clampAndSetCollapsedDimensions(220, 160, screenWidth = 800, screenHeight = 600)
        PanelConfig.scrollOffset = 75
        PanelConfig.setLogPosition(50, 60)
        PanelConfig.clampAndSetLogDimensions(260, 180, screenWidth = 800, screenHeight = 600)
        PanelConfig.setTeamIndicatorLeftPosition(15, 25)
        PanelConfig.setTeamIndicatorRightPosition(35, 45)

        // Set preserved preferences to non-default values
        PanelConfig.setEnableTeamIndicators(false)
        PanelConfig.setEnableBattleInfoPanel(false)
        PanelConfig.setEnableBattleLog(true)
        PanelConfig.setEnableMoveTooltips(false)
        PanelConfig.setFontScale(1.4f)
        PanelConfig.setStartExpanded(true)
        PanelConfig.adjustLogFontScale(0.3f)
        PanelConfig.setLogExpanded(false)
        PanelConfig.setTeamIndicatorOrientation(PanelConfig.TeamIndicatorOrientation.VERTICAL)
        PanelConfig.adjustTeamIndicatorScale(0.4f)
        PanelConfig.setTeamIndicatorRepositioningEnabled(false)
        PanelConfig.adjustTooltipFontScale(0.2f)
        PanelConfig.adjustMoveTooltipFontScale(0.25f)
        PanelConfig.setShowTeraType(true)
        PanelConfig.setShowStatRanges(false)
        PanelConfig.setShowBaseCritRate(true)
        PanelConfig.setShowCritDamage(true)
        PanelConfig.setShowMultiHitCount(false)
        PanelConfig.setDebugDumpEnabled(true)

        // Execute reset
        PanelConfig.resetHudLayout()

        // Verify layout fields are reset
        assertNull(PanelConfig.panelX)
        assertNull(PanelConfig.panelY)
        assertNull(PanelConfig.panelWidth)
        assertNull(PanelConfig.panelHeight)
        assertNull(PanelConfig.collapsedWidth)
        assertNull(PanelConfig.collapsedHeight)
        assertEquals(0, PanelConfig.scrollOffset)

        assertNull(PanelConfig.logX)
        assertNull(PanelConfig.logY)
        assertNull(PanelConfig.logWidth)
        assertNull(PanelConfig.logHeight)

        assertNull(PanelConfig.teamIndicatorLeftX)
        assertNull(PanelConfig.teamIndicatorLeftY)
        assertNull(PanelConfig.teamIndicatorRightX)
        assertNull(PanelConfig.teamIndicatorRightY)

        // Verify non-layout settings are strictly preserved
        assertFalse(PanelConfig.enableTeamIndicators)
        assertFalse(PanelConfig.enableBattleInfoPanel)
        assertTrue(PanelConfig.enableBattleLog)
        assertFalse(PanelConfig.enableMoveTooltips)
        assertEquals(1.4f, PanelConfig.fontScale, 0.001f)
        assertTrue(PanelConfig.startExpanded)
        assertEquals(1.3f, PanelConfig.logFontScale, 0.001f)
        assertFalse(PanelConfig.logExpanded)
        assertEquals(PanelConfig.TeamIndicatorOrientation.VERTICAL, PanelConfig.teamIndicatorOrientation)
        assertEquals(1.4f, PanelConfig.teamIndicatorScale, 0.001f)
        assertFalse(PanelConfig.teamIndicatorRepositioningEnabled)
        assertEquals(1.2f, PanelConfig.tooltipFontScale, 0.001f)
        assertEquals(1.25f, PanelConfig.moveTooltipFontScale, 0.001f)
        assertTrue(PanelConfig.showTeraType)
        assertFalse(PanelConfig.showStatRanges)
        assertTrue(PanelConfig.showBaseCritRate)
        assertTrue(PanelConfig.showCritDamage)
        assertFalse(PanelConfig.showMultiHitCount)
        assertTrue(PanelConfig.debugDumpEnabled)
    }

    @Test
    fun testClampAndSetDimensionsSafeClampOnTinyViewport() {
        // screenWidth=80 -> maxAllowedW = 68 (less than preferredMin 100)
        // screenHeight=50 -> maxAllowedH = 42 (less than preferredMin 60)
        PanelConfig.clampAndSetDimensions(250, 200, screenWidth = 80, screenHeight = 50)
        assertEquals(68, PanelConfig.panelWidth)
        assertEquals(42, PanelConfig.panelHeight)

        // Null dimensions remain null
        PanelConfig.clampAndSetDimensions(null, null, screenWidth = 80, screenHeight = 50)
        assertNull(PanelConfig.panelWidth)
        assertNull(PanelConfig.panelHeight)
    }

    @Test
    fun testClampAndSetCollapsedDimensionsSafeClampOnTinyViewport() {
        // screenHeight=40 -> maxAllowedH = 34 (less than preferredMin 40)
        PanelConfig.clampAndSetCollapsedDimensions(250, 100, screenWidth = 100, screenHeight = 40)
        assertEquals(85, PanelConfig.collapsedWidth)
        assertEquals(34, PanelConfig.collapsedHeight)
    }

    @Test
    fun testClampAndSetLogDimensionsSafeClampOnTinyViewport() {
        // screenWidth=90 (less than MIN_LOG_WIDTH 120), screenHeight=45 (less than MIN_LOG_HEIGHT 60)
        PanelConfig.clampAndSetLogDimensions(200, 150, screenWidth = 90, screenHeight = 45)
        assertEquals(90, PanelConfig.logWidth)
        assertEquals(45, PanelConfig.logHeight)
    }
}
