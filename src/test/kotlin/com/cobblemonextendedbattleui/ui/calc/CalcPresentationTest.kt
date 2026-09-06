package com.cobblemonextendedbattleui.ui.calc

import com.cobblemonextendedbattleui.calc.CalcMoveOutcome
import com.cobblemonextendedbattleui.calc.DamageConfidence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.math.roundToInt

class CalcPresentationTest {

    @ParameterizedTest
    @EnumSource(CalcMoveOutcome::class)
    fun testStructuredOutcomeToStyleMapping(outcome: CalcMoveOutcome) {
        val style = CalcPresentation.styleForOutcome(outcome, supported = true)
        assertEquals(outcome, style.outcome)

        when (outcome) {
            CalcMoveOutcome.GUARANTEED_OHKO -> {
                assertEquals(CalcPresentation.SEV_OHKO_FG, style.labelColor)
                assertEquals(CalcPresentation.SEV_OHKO_BAR, style.barColor)
                assertEquals(CalcRiskFillStyle.SOLID, style.fillStyle)
                assertTrue(style.showDamageBar)
            }
            CalcMoveOutcome.LIKELY_OHKO -> {
                assertEquals(CalcPresentation.SEV_LIKELY_OHKO_FG, style.labelColor)
                assertEquals(CalcPresentation.SEV_LIKELY_OHKO_BAR, style.barColor)
                assertEquals(CalcRiskFillStyle.HATCHED, style.fillStyle)
                assertTrue(style.showDamageBar)
                val guaranteed = CalcPresentation.styleForOutcome(CalcMoveOutcome.GUARANTEED_OHKO)
                assertNotEquals(guaranteed.labelColor, style.labelColor)
                assertNotEquals(guaranteed.fillStyle, style.fillStyle)
            }
            CalcMoveOutcome.TWO_HKO -> {
                assertEquals(CalcPresentation.SEV_2HKO_FG, style.labelColor)
                assertEquals(CalcPresentation.SEV_2HKO_BAR, style.barColor)
                assertEquals(CalcRiskFillStyle.SOLID, style.fillStyle)
                assertTrue(style.showDamageBar)
            }
            CalcMoveOutcome.THREE_HKO -> {
                assertEquals(CalcPresentation.SEV_3HKO_FG, style.labelColor)
                assertEquals(CalcPresentation.SEV_3HKO_BAR, style.barColor)
                assertEquals(CalcRiskFillStyle.SOLID, style.fillStyle)
                assertTrue(style.showDamageBar)
            }
            CalcMoveOutcome.FOUR_HKO_PLUS -> {
                assertEquals(CalcPresentation.SEV_WEAK_FG, style.labelColor)
                assertEquals(CalcPresentation.SEV_WEAK_BAR, style.barColor)
                assertEquals(CalcRiskFillStyle.SOLID, style.fillStyle)
                assertTrue(style.showDamageBar)
            }
            CalcMoveOutcome.KO -> {
                assertEquals(CalcPresentation.SEV_OHKO_FG, style.labelColor)
                assertEquals(CalcPresentation.SEV_OHKO_BAR, style.barColor)
                assertEquals(CalcRiskFillStyle.SOLID, style.fillStyle)
                assertTrue(style.showDamageBar)
            }
            CalcMoveOutcome.IMMUNE -> {
                assertEquals(CalcPresentation.SEV_IMMUNE_FG, style.labelColor)
                assertEquals(CalcPresentation.SEV_IMMUNE_BAR, style.barColor)
                assertEquals(CalcRiskFillStyle.NONE, style.fillStyle)
                assertTrue(style.showDamageBar)
            }
            CalcMoveOutcome.STATUS -> {
                assertEquals(CalcPresentation.SEV_STATUS_FG, style.labelColor)
                assertEquals(CalcPresentation.SEV_STATUS_BAR, style.barColor)
                assertEquals(CalcRiskFillStyle.NONE, style.fillStyle)
                assertFalse(style.showDamageBar, "Never draw damage bar for STATUS")
            }
            CalcMoveOutcome.UNSUPPORTED -> {
                assertEquals(CalcPresentation.SEV_UNSUPPORTED_FG, style.labelColor)
                assertEquals(CalcPresentation.SEV_UNSUPPORTED_BAR, style.barColor)
                assertEquals(CalcRiskFillStyle.NONE, style.fillStyle)
                assertFalse(style.showDamageBar, "Never draw damage bar for UNSUPPORTED")
            }
        }
    }

    @Test
    fun testUnsupportedFlagSuppressesDamageBarRegardlessOfOutcome() {
        for (outcome in CalcMoveOutcome.entries) {
            val style = CalcPresentation.styleForOutcome(outcome, supported = false)
            assertFalse(style.showDamageBar, "Damage bar must be suppressed when supported=false for $outcome")
            assertEquals(CalcPresentation.SEV_UNSUPPORTED_FG, style.labelColor)
            assertEquals(CalcRiskFillStyle.NONE, style.fillStyle)
        }
    }

    @Test
    fun testConfidenceRailStyles() {
        val high = CalcPresentation.styleForConfidence(DamageConfidence.HIGH, supported = true)
        assertFalse(high.showRail, "HIGH confidence should be visually neutral")
        assertFalse(high.isErrorLike)

        val med = CalcPresentation.styleForConfidence(DamageConfidence.MEDIUM, supported = true)
        assertTrue(med.showRail, "MEDIUM confidence should surface a 1px rail")
        assertEquals(CalcPresentation.CONF_MED_RAIL, med.railColor)
        assertFalse(med.isErrorLike)

        val low = CalcPresentation.styleForConfidence(DamageConfidence.LOW, supported = true)
        assertTrue(low.showRail, "LOW confidence should surface a 1px rail")
        assertEquals(CalcPresentation.CONF_LOW_RAIL, low.railColor)
        assertFalse(low.isErrorLike)

        val unsupp = CalcPresentation.styleForConfidence(DamageConfidence.HIGH, supported = false)
        assertTrue(unsupp.showRail, "Unsupported should surface a rail")
        assertEquals(CalcPresentation.CONF_ERROR_RAIL, unsupp.railColor)
        assertTrue(unsupp.isErrorLike, "Unsupported must be clearly muted/error-like")
    }

    @ParameterizedTest
    @ValueSource(ints = [16, 24, 32, 40, 65, 80, 100, 120, 132, 150, 180, 228, 320])
    fun testHeaderAllocationNoOverlapAcrossWidthsAndLargeWarningCount(availableWidth: Int) {
        val charW = 5
        val measurer = { text: String -> text.length * charW }
        val tier = CalcMoveRowTier.fromWidth(availableWidth)

        for (warningCount in listOf(0, 1, 5, 99)) {
            for (hasLow in listOf(false, true)) {
                val layout = CalcPresentation.calculateHeaderLayout(
                    availableWidth = availableWidth,
                    tier = tier,
                    warningCount = warningCount,
                    hasLowOrUnsupported = hasLow,
                    turn = 12,
                    isExpanded = true,
                    measurer = measurer,
                    fullTitle = "DAMAGE CALC",
                    shortTitle = "CALC",
                    turnText = "T12",
                    warningChipText = "!$warningCount"
                )

                // Sub-minimal width check: widths 16 and 24 are explicitly non-viable
                if (availableWidth < CalcContentViability.MIN_HEADER_CONTENT_WIDTH) {
                    assertEquals(0, layout.titleWidth)
                    assertFalse(layout.turnVisible)
                    assertFalse(layout.warningChipVisible, "Warning chip must not be rendered at sub-minimal width $availableWidth")
                    assertFalse(CalcContentViability.canRenderHeader(panelWidth = availableWidth + 6, panelHeight = 24, frameInset = 3))
                    continue
                }

                // Invariant: At every viable width (including 32 and 40), warning chip is always visible when warnings exist
                if (warningCount > 0) {
                    assertTrue(layout.warningChipVisible, "Warning chip must be visible at viable width $availableWidth with warningCount=$warningCount")
                }

                // Invariant 1: All widths and positions must be non-negative
                assertTrue(layout.titleX >= 0)
                assertTrue(layout.titleWidth >= 0)
                assertTrue(layout.turnX >= 0)
                assertTrue(layout.turnWidth >= 0)
                assertTrue(layout.warningChipX >= 0)
                assertTrue(layout.warningChipWidth >= 0)
                assertTrue(layout.chevronX >= 0)
                assertTrue(layout.chevronWidth >= 0)

                // Invariant 2: Every single right edge must be <= availableWidth
                assertTrue(layout.titleX + layout.titleWidth <= availableWidth, "title right <= availableWidth ($availableWidth)")
                if (layout.turnVisible) {
                    assertTrue(layout.turnX + layout.turnWidth <= availableWidth, "turn right <= availableWidth ($availableWidth)")
                }
                if (layout.warningChipVisible) {
                    assertTrue(layout.warningChipX + layout.warningChipWidth <= availableWidth, "chip right <= availableWidth ($availableWidth)")
                }
                assertTrue(layout.chevronX + layout.chevronWidth <= availableWidth, "chevron right <= availableWidth ($availableWidth)")

                // Invariant 3: Strict non-overlapping ordering from left to right
                val rightOfTitle = layout.titleX + layout.titleWidth
                if (layout.turnVisible) {
                    assertTrue(rightOfTitle <= layout.turnX, "title must not overlap turn")
                    val rightOfTurn = layout.turnX + layout.turnWidth
                    if (layout.warningChipVisible) {
                        assertTrue(rightOfTurn <= layout.warningChipX, "turn must not overlap chip")
                        assertTrue(layout.warningChipX + layout.warningChipWidth <= layout.chevronX, "chip must not overlap chevron")
                    } else {
                        assertTrue(rightOfTurn <= layout.chevronX, "turn must not overlap chevron")
                    }
                } else if (layout.warningChipVisible) {
                    assertTrue(rightOfTitle <= layout.warningChipX, "title must not overlap chip")
                    assertTrue(layout.warningChipX + layout.warningChipWidth <= layout.chevronX, "chip must not overlap chevron")
                } else {
                    assertTrue(rightOfTitle <= layout.chevronX, "title must not overlap chevron")
                }
            }
        }
    }

    @Test
    fun testTurnTextDroppedBeforeWarningChipWhenTight() {
        val charW = 6
        val measurer = { text: String -> text.length * charW }
        val layout = CalcPresentation.calculateHeaderLayout(
            availableWidth = 65,
            tier = CalcMoveRowTier.TINY,
            warningCount = 2,
            hasLowOrUnsupported = false,
            turn = 5,
            isExpanded = true,
            measurer = measurer,
            fullTitle = "DAMAGE CALC",
            shortTitle = "CALC",
            turnText = "T5",
            warningChipText = "!2"
        )

        assertTrue(layout.warningChipVisible, "Warning chip must be preserved even when space is tight")
        assertFalse(layout.turnVisible, "Turn text must be dropped before warning chip")
        assertTrue(layout.titleWidth > 0, "Title should receive remaining width")
        assertTrue(layout.titleX + layout.titleWidth <= layout.warningChipX, "Title must not overlap warning chip")
    }

    @Test
    fun testCalcContentViability() {
        // Header viability (min content width 32, min content height 18)
        assertFalse(CalcContentViability.canRenderHeader(panelWidth = 22, panelHeight = 24, frameInset = 3), "cellW = 16 < 32 (non-viable)")
        assertFalse(CalcContentViability.canRenderHeader(panelWidth = 30, panelHeight = 24, frameInset = 3), "cellW = 24 < 32 (non-viable)")
        assertTrue(CalcContentViability.canRenderHeader(panelWidth = 38, panelHeight = 24, frameInset = 3), "cellW = 32 >= 32, cellH = 18 >= 18")
        assertFalse(CalcContentViability.canRenderHeader(panelWidth = 200, panelHeight = 20, frameInset = 3), "cellH = 14 < 18")

        // Content viability (min content width 120, min content height 60)
        assertFalse(CalcContentViability.canRenderContent(panelWidth = 200, panelHeight = 50, isExpanded = true), "contentH = 24 < 60")
        assertFalse(CalcContentViability.canRenderContent(panelWidth = 100, panelHeight = 120, isExpanded = true), "cellW = 94 < 120")
        assertFalse(CalcContentViability.canRenderContent(panelWidth = 200, panelHeight = 120, isExpanded = false), "not expanded")
        assertTrue(CalcContentViability.canRenderContent(panelWidth = 180, panelHeight = 90, isExpanded = true), "cellW = 174, contentH = 64")
    }

    @ParameterizedTest
    @ValueSource(ints = [132, 150, 180, 228, 320])
    fun testCompactMatchupLayoutAcrossWidthsAndScales(availableWidth: Int) {
        val fontScales = listOf(0.35f, 1.0f, 1.6f)
        val arrow = ">"

        for (scale in fontScales) {
            val charW = (6 * scale).roundToInt().coerceAtLeast(1)
            val measurer = { text: String -> text.length * charW }

            val result = CalcCompactMatchupLayout.calculate(
                availableWidth = availableWidth,
                youPct = 85,
                oppPct = 40,
                arrow = arrow,
                measurer = measurer,
                youWord = "You",
                oppWord = "Opp"
            )

            assertTrue(result.youWidth >= 0)
            assertTrue(result.arrowWidth >= 0)
            assertTrue(result.oppWidth >= 0)
            assertTrue(result.totalWidth <= availableWidth, "totalWidth (${result.totalWidth}) <= availableWidth ($availableWidth)")

            // Ordering
            if (result.youWidth > 0 && result.arrowWidth > 0) {
                assertTrue(result.youX + result.youWidth <= result.arrowX)
            }
            if (result.arrowWidth > 0 && result.oppWidth > 0) {
                assertTrue(result.arrowX + result.arrowWidth <= result.oppX)
            }
            if (result.oppWidth > 0) {
                assertTrue(result.oppX + result.oppWidth <= availableWidth)
            }
        }
    }

    @ParameterizedTest
    @ValueSource(floats = [0.35f, 0.85f, 1.0f, 1.6f])
    fun testPillRoundingAlignmentAndNonOverlapAcrossScales(uiScale: Float) {
        val pillLabels = listOf("MANUAL", "SEEN", "LIKELY", "?")
        for (label in pillLabels) {
            val tw = (label.length * 6 * uiScale).roundToInt()
            val drawnPillWidth = CalcPresentation.pillTotalWidth(tw, uiScale)

            val layout = CalcPresentation.calculateOverrideRowLayout(
                rowX = 10,
                rowY = 50,
                rowW = 180,
                rowH = 14,
                hasAlts = true,
                isOverridden = true,
                pillWidth = drawnPillWidth,
                uiScale = uiScale
            )

            // Assert exact alignment: drawn pill width must equal allocated pill bounds width
            assertEquals(drawnPillWidth, layout.pillBounds.width, "Drawn pill width must equal allocated pill bounds width at scale $uiScale")
            assertTrue(layout.pillBounds.right <= layout.resetBounds.x, "Pill must not overlap reset button")
            assertTrue(layout.nextBounds.right <= layout.pillBounds.x, "Next button must not overlap pill")
            assertTrue(layout.prevBounds.right <= layout.nextBounds.x, "Prev button must not overlap next button")
            assertTrue(layout.bodyBounds.right <= layout.prevBounds.x, "Body must not overlap prev button")
            assertTrue(layout.resetBounds.right <= 10 + 180, "Reset must not exceed row boundary")
        }
    }

    @Test
    fun testOverrideControlRegionNonOverlapAndPresence() {
        val rowX = 10
        val rowY = 50
        val rowW = 200
        val rowH = 14
        val pillW = 28

        // Case 1: hasAlts=true, isOverridden=true
        val full = CalcPresentation.calculateOverrideRowLayout(
            rowX = rowX,
            rowY = rowY,
            rowW = rowW,
            rowH = rowH,
            hasAlts = true,
            isOverridden = true,
            pillWidth = pillW
        )

        assertTrue(full.prevVisible)
        assertTrue(full.nextVisible)
        assertTrue(full.resetVisible)
        assertFalse(full.bodyBounds.isEmpty)
        assertFalse(full.pillBounds.isEmpty)

        // Strict non-overlapping ordering: body <= prev <= next <= pill <= reset <= rightEdge
        assertTrue(full.bodyBounds.right <= full.prevBounds.x)
        assertTrue(full.prevBounds.right <= full.nextBounds.x)
        assertTrue(full.nextBounds.right <= full.pillBounds.x)
        assertTrue(full.pillBounds.right <= full.resetBounds.x)
        assertTrue(full.resetBounds.right <= rowX + rowW)

        // Case 2: hasAlts=false, isOverridden=false (Spread row with no alternatives)
        val noAltsNoOverride = CalcPresentation.calculateOverrideRowLayout(
            rowX = rowX,
            rowY = rowY,
            rowW = rowW,
            rowH = rowH,
            hasAlts = false,
            isOverridden = false,
            pillWidth = pillW
        )

        assertFalse(noAltsNoOverride.prevVisible)
        assertFalse(noAltsNoOverride.nextVisible)
        assertFalse(noAltsNoOverride.resetVisible)
        assertTrue(noAltsNoOverride.prevBounds.isEmpty, "Prev bounds must be empty when hasAlts=false")
        assertTrue(noAltsNoOverride.nextBounds.isEmpty, "Next bounds must be empty when hasAlts=false")
        assertTrue(noAltsNoOverride.bodyBounds.isEmpty, "Body bounds must be empty when hasAlts=false (no phantom target)")
        assertTrue(noAltsNoOverride.resetBounds.isEmpty, "Reset bounds must be empty when isOverridden=false")
        assertFalse(noAltsNoOverride.pillBounds.isEmpty, "Pill bounds must remain present")

        // Case 3: hasAlts=true, isOverridden=false
        val altsNoOverride = CalcPresentation.calculateOverrideRowLayout(
            rowX = rowX,
            rowY = rowY,
            rowW = rowW,
            rowH = rowH,
            hasAlts = true,
            isOverridden = false,
            pillWidth = pillW
        )

        assertTrue(altsNoOverride.prevVisible)
        assertTrue(altsNoOverride.nextVisible)
        assertFalse(altsNoOverride.resetVisible)
        assertTrue(altsNoOverride.resetBounds.isEmpty)
        assertTrue(altsNoOverride.bodyBounds.right <= altsNoOverride.prevBounds.x)
        assertTrue(altsNoOverride.prevBounds.right <= altsNoOverride.nextBounds.x)
        assertTrue(altsNoOverride.nextBounds.right <= altsNoOverride.pillBounds.x)
        assertTrue(altsNoOverride.pillBounds.right <= rowX + rowW)
    }

    @Test
    fun testHalfOpenContainmentProperties() {
        val x = 10
        val y = 20
        val w = 30
        val h = 40

        // Inside
        assertTrue(CalcPresentation.containsHalfOpen(10, 20, x, y, w, h), "Top-left corner is inside")
        assertTrue(CalcPresentation.containsHalfOpen(25, 35, x, y, w, h), "Interior point is inside")
        assertTrue(CalcPresentation.containsHalfOpen(39, 59, x, y, w, h), "Bottom-right pixel (x+w-1, y+h-1) is inside")

        // Boundary exclusions (half-open [x, x+w) and [y, y+h))
        assertFalse(CalcPresentation.containsHalfOpen(40, 30, x, y, w, h), "Right edge x+w is excluded")
        assertFalse(CalcPresentation.containsHalfOpen(20, 60, x, y, w, h), "Bottom edge y+h is excluded")
        assertFalse(CalcPresentation.containsHalfOpen(40, 60, x, y, w, h), "Bottom-right corner (x+w, y+h) is excluded")

        // Outside
        assertFalse(CalcPresentation.containsHalfOpen(9, 20, x, y, w, h), "Left of box is outside")
        assertFalse(CalcPresentation.containsHalfOpen(10, 19, x, y, w, h), "Above box is outside")
        assertFalse(CalcPresentation.containsHalfOpen(50, 80, x, y, w, h), "Far outside is outside")

        // Guard non-positive dimensions
        assertFalse(CalcPresentation.containsHalfOpen(10, 20, 10, 20, 0, 40), "Width 0 returns false")
        assertFalse(CalcPresentation.containsHalfOpen(10, 20, 10, 20, 30, 0), "Height 0 returns false")
        assertFalse(CalcPresentation.containsHalfOpen(10, 20, 10, 20, -10, 40), "Negative width returns false")
        assertFalse(CalcPresentation.containsHalfOpen(10, 20, 10, 20, 30, -10), "Negative height returns false")

        // Adjacent regions test: adjacent [10, 20) and [20, 30) have zero overlap
        val r1 = CalcHitBounds(10, 20, 10, 10)
        val r2 = CalcHitBounds(20, 20, 10, 10)
        assertTrue(r1.contains(19, 25))
        assertFalse(r2.contains(19, 25))
        assertFalse(r1.contains(20, 25))
        assertTrue(r2.contains(20, 25))
    }

    @Test
    fun testCalcHitBoundsOverflowSafety() {
        // Extreme values near Int.MAX_VALUE
        val x = Int.MAX_VALUE - 5
        val w = 10
        val bounds = CalcHitBounds(x, 100, w, 20)

        assertEquals(Int.MAX_VALUE.toLong() + 5L, bounds.rightLong, "rightLong must not overflow Int")
        assertTrue(bounds.contains(Int.MAX_VALUE - 5, 105), "x is inside")
        assertTrue(bounds.contains(Int.MAX_VALUE - 1, 105), "Interior point is inside")
        assertFalse(bounds.contains(Int.MAX_VALUE + 5, 105), "Right edge (overflowed Int) is not inside")

        // Extreme bounds at Int boundaries
        val hugeBounds = CalcHitBounds(Int.MAX_VALUE, 0, Int.MAX_VALUE, 1)
        assertTrue(hugeBounds.contains(Int.MAX_VALUE, 0))
        assertFalse(hugeBounds.contains(Int.MAX_VALUE - 1, 0))

        // Negative coordinate bounds
        val minBounds = CalcHitBounds(Int.MIN_VALUE, Int.MIN_VALUE, 100, 100)
        assertTrue(minBounds.contains(Int.MIN_VALUE + 50, Int.MIN_VALUE + 50))
        assertFalse(minBounds.contains(Int.MIN_VALUE - 1, Int.MIN_VALUE))
    }
}
