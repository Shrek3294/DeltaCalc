package com.cobblemonextendedbattleui

import com.cobblemonextendedbattleui.pokemon.render.TeamPanelRenderer
import com.cobblemonextendedbattleui.pokemon.tooltip.PokeballBounds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class TooltipPopupLayoutTest {

    // ─── PokemonInfoPopup tests ─────────────────────────────────────────────

    @Test
    fun testResolvePopupWidth() {
        // Normal viewport: requested width returned
        assertEquals(240, PokemonInfoPopup.resolvePopupWidth(requestedWidth = 240, screenWidth = 800))
        assertEquals(300, PokemonInfoPopup.resolvePopupWidth(requestedWidth = 300, screenWidth = 800))

        // Narrow screen: clamps to available width (screenWidth - 2 * margin)
        assertEquals(192, PokemonInfoPopup.resolvePopupWidth(requestedWidth = 240, screenWidth = 200))

        // Tiny screen: cannot hold minimally useful frame -> returns null
        assertNull(PokemonInfoPopup.resolvePopupWidth(requestedWidth = 240, screenWidth = 40))
        assertNull(PokemonInfoPopup.resolvePopupWidth(requestedWidth = 240, screenWidth = 20))
        assertNull(PokemonInfoPopup.resolvePopupWidth(requestedWidth = 240, screenWidth = 0))
        assertNull(PokemonInfoPopup.resolvePopupWidth(requestedWidth = 240, screenWidth = -100))
    }

    @Test
    fun testPokemonInfoPopupVerticalPlacementIntentAndFallback() {
        val dummyUuid = UUID.randomUUID()

        // Left side team: preferred right fits
        val leftBall = PokeballBounds(x = 10, y = 100, width = 24, height = 24, uuid = dummyUuid, isLeftSide = true, isPlayerPokemon = true)
        val posRight = PokemonInfoPopup.resolvePopupPosition(
            bounds = leftBall, popupWidth = 240, totalHeight = 150, screenWidth = 800, screenHeight = 600, isVertical = true
        )
        // Expected right of ball: 10 + 24 + 4 = 38
        assertEquals(38, posRight.x)
        // Centered vertically: 100 + 12 - 75 = 37
        assertEquals(37, posRight.y)

        // Left side team near right edge: right overflows, fallback to left
        val leftBallNearRight = PokeballBounds(x = 700, y = 100, width = 24, height = 24, uuid = dummyUuid, isLeftSide = true, isPlayerPokemon = true)
        val posFlippedLeft = PokemonInfoPopup.resolvePopupPosition(
            bounds = leftBallNearRight, popupWidth = 240, totalHeight = 150, screenWidth = 800, screenHeight = 600, isVertical = true
        )
        // Right placement: 700 + 24 + 4 = 728; 728 + 240 = 968 > 800 - 4 (overflows)
        // Fallback left: 700 - 240 - 4 = 456 >= 4 -> chosen
        assertEquals(456, posFlippedLeft.x)

        // Right side team: preferred left fits
        val rightBall = PokeballBounds(x = 760, y = 200, width = 24, height = 24, uuid = dummyUuid, isLeftSide = false, isPlayerPokemon = false)
        val posLeft = PokemonInfoPopup.resolvePopupPosition(
            bounds = rightBall, popupWidth = 240, totalHeight = 150, screenWidth = 800, screenHeight = 600, isVertical = true
        )
        // Expected left of ball: 760 - 240 - 4 = 516
        assertEquals(516, posLeft.x)

        // Right side team near left edge: left underflows, fallback to right
        val rightBallNearLeft = PokeballBounds(x = 50, y = 200, width = 24, height = 24, uuid = dummyUuid, isLeftSide = false, isPlayerPokemon = false)
        val posFlippedRight = PokemonInfoPopup.resolvePopupPosition(
            bounds = rightBallNearLeft, popupWidth = 240, totalHeight = 150, screenWidth = 800, screenHeight = 600, isVertical = true
        )
        // Preferred left: 50 - 240 - 4 = -194 < 4 (underflows)
        // Fallback right: 50 + 24 + 4 = 78 -> fits
        assertEquals(78, posFlippedRight.x)
    }

    @Test
    fun testPokemonInfoPopupHorizontalPlacementIntentAndFallback() {
        val dummyUuid = UUID.randomUUID()

        // Ball with room below: preferred below
        val ballWithRoom = PokeballBounds(x = 100, y = 20, width = 24, height = 24, uuid = dummyUuid, isLeftSide = true, isPlayerPokemon = true)
        val posBelow = PokemonInfoPopup.resolvePopupPosition(
            bounds = ballWithRoom, popupWidth = 240, totalHeight = 150, screenWidth = 800, screenHeight = 600, isVertical = false
        )
        // Expected below: 20 + 24 + 4 = 48
        assertEquals(48, posBelow.y)

        // Ball near bottom: below overflows (500 + 24 + 4 + 150 = 678 > 600 - 4), fallback above
        val ballNearBottom = PokeballBounds(x = 100, y = 500, width = 24, height = 24, uuid = dummyUuid, isLeftSide = true, isPlayerPokemon = true)
        val posAbove = PokemonInfoPopup.resolvePopupPosition(
            bounds = ballNearBottom, popupWidth = 240, totalHeight = 150, screenWidth = 800, screenHeight = 600, isVertical = false
        )
        // Expected above: 500 - 150 - 4 = 346
        assertEquals(346, posAbove.y)

        // Height overflow: totalHeight > screenHeight, clamps without throwing
        val ballInSmallScreen = PokeballBounds(x = 100, y = 50, width = 24, height = 24, uuid = dummyUuid, isLeftSide = true, isPlayerPokemon = true)
        val posOversized = PokemonInfoPopup.resolvePopupPosition(
            bounds = ballInSmallScreen, popupWidth = 200, totalHeight = 400, screenWidth = 500, screenHeight = 300, isVertical = false
        )
        assertEquals(0, posOversized.y)
        assertTrue(posOversized.x in 0..300)
    }

    @Test
    fun testPokemonInfoPopupExtremeCoordinatesHardened() {
        val dummyUuid = UUID.randomUUID()
        val extremeBall = PokeballBounds(x = Int.MIN_VALUE, y = Int.MAX_VALUE, width = 24, height = 24, uuid = dummyUuid, isLeftSide = true, isPlayerPokemon = true)
        val pos = PokemonInfoPopup.resolvePopupPosition(
            bounds = extremeBall, popupWidth = 240, totalHeight = 150, screenWidth = 800, screenHeight = 600, isVertical = true
        )
        assertTrue(pos.x in 4..556)
        assertTrue(pos.y in 4..446)
    }

    // ─── MoveTooltipRenderer tests ──────────────────────────────────────────

    @Test
    fun testResolveMoveTooltipWidth() {
        assertEquals(210, MoveTooltipRenderer.resolveTooltipWidth(requestedWidth = 210, screenWidth = 800))
        assertEquals(172, MoveTooltipRenderer.resolveTooltipWidth(requestedWidth = 210, screenWidth = 180))
        assertNull(MoveTooltipRenderer.resolveTooltipWidth(requestedWidth = 210, screenWidth = 40))
        assertNull(MoveTooltipRenderer.resolveTooltipWidth(requestedWidth = 210, screenWidth = 0))
    }

    @Test
    fun testMoveTooltipPositionIntentAndFallback() {
        // Preferred above fits
        val posAbove = MoveTooltipRenderer.resolveTooltipPosition(
            tileX = 200, tileY = 400, tileW = 100, tileH = 30,
            tooltipWidth = 210, totalHeight = 120, screenWidth = 800, screenHeight = 600
        )
        // Expected above: 400 - 120 - 4 = 276
        assertEquals(276, posAbove.y)
        // Centered horizontally: 200 + 50 - 105 = 145
        assertEquals(145, posAbove.x)

        // Above underflows (tileY=50, 50 - 120 - 4 = -74 < 4), fallback below
        val posBelow = MoveTooltipRenderer.resolveTooltipPosition(
            tileX = 200, tileY = 50, tileW = 100, tileH = 30,
            tooltipWidth = 210, totalHeight = 120, screenWidth = 800, screenHeight = 600
        )
        // Expected below: 50 + 30 + 4 = 84
        assertEquals(84, posBelow.y)

        // Oversized height: clamped to 0 without throwing
        val posOversized = MoveTooltipRenderer.resolveTooltipPosition(
            tileX = 200, tileY = 200, tileW = 100, tileH = 30,
            tooltipWidth = 200, totalHeight = 700, screenWidth = 800, screenHeight = 500
        )
        assertEquals(0, posOversized.y)
    }

    @Test
    fun testMoveTooltipExtremeCoordinatesHardened() {
        val pos = MoveTooltipRenderer.resolveTooltipPosition(
            tileX = Int.MAX_VALUE, tileY = Int.MIN_VALUE, tileW = 100, tileH = 30,
            tooltipWidth = 210, totalHeight = 120, screenWidth = 800, screenHeight = 600
        )
        assertTrue(pos.x in 4..586)
        assertTrue(pos.y in 4..476)
    }

    // ─── TeamPanelRenderer control hints tests ──────────────────────────────

    @Test
    fun testResolveControlHintsPosition() {
        // Normal room below: preferred below
        val posBelow = TeamPanelRenderer.resolveControlHintsPosition(
            panelX = 10, panelY = 20, panelWidth = 160, panelHeight = 30,
            hintWidth = 120, hintHeight = 16, screenWidth = 800, screenHeight = 600
        )
        assertNotNull(posBelow)
        // Expected below: 20 + 30 + 2 = 52
        assertEquals(52, posBelow!!.y)
        // Centered: 10 + 80 - 60 = 30
        assertEquals(30, posBelow.x)

        // Room below exceeds screen: fallback above
        val posAbove = TeamPanelRenderer.resolveControlHintsPosition(
            panelX = 10, panelY = 570, panelWidth = 160, panelHeight = 30,
            hintWidth = 120, hintHeight = 16, screenWidth = 800, screenHeight = 600
        )
        assertNotNull(posAbove)
        // Expected above: 570 - 16 - 2 = 552
        assertEquals(552, posAbove!!.y)

        // Tiny screen cannot hold hints: returns null
        val posTiny = TeamPanelRenderer.resolveControlHintsPosition(
            panelX = 10, panelY = 20, panelWidth = 160, panelHeight = 30,
            hintWidth = 120, hintHeight = 16, screenWidth = 80, screenHeight = 50
        )
        assertNull(posTiny)
    }
}
