package com.cobblemonextendedbattleui.ui.calc

import com.cobblemonextendedbattleui.calc.CalcMoveOutcome
import com.cobblemonextendedbattleui.calc.CalcMoveRow
import com.cobblemonextendedbattleui.calc.DamageConfidence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CalcMoveRowLayoutTest {

    @ParameterizedTest
    @ValueSource(ints = [0, 132, 150, 180, 228, 320])
    fun testRepresentativeWidthsNeverOverlapOrInvert(availableWidth: Int) {
        val testMeasurements = listOf(
            Pair(40, 30),
            Pair(80, 45),
            Pair(180, 120), // Large measurements
            Pair(0, 0)
        )

        for ((dmgMeasured, koMeasured) in testMeasurements) {
            val layout = CalcMoveRowLayout.calculate(
                availableWidth = availableWidth,
                measuredDamageWidth = dmgMeasured,
                measuredKoWidth = koMeasured
            )

            // Widths must be strictly non-negative
            assertTrue(layout.moveNameWidth >= 0, "moveNameWidth >= 0 for width=$availableWidth")
            assertTrue(layout.damageWidth >= 0, "damageWidth >= 0 for width=$availableWidth")
            assertTrue(layout.koWidth >= 0, "koWidth >= 0 for width=$availableWidth")

            // Region boundaries must be relative to origin 0 and never order-invert:
            // 0 <= moveNameX <= moveNameRight <= damageX <= damageRight <= koX <= koRight <= normalizedAvailableWidth
            val maxRight = if (availableWidth > 0) availableWidth else 0

            assertTrue(layout.moveNameX >= 0, "moveNameX >= 0")
            assertTrue(layout.moveNameRight <= layout.damageX, "moveNameRight <= damageX")
            assertTrue(layout.damageRight <= layout.koX, "damageRight <= koX")
            assertTrue(layout.koRight <= maxRight, "koRight <= maxRight: koRight=${layout.koRight}, maxRight=$maxRight")
        }
    }

    @Test
    fun testTierResolutionFromWidth() {
        assertEquals(CalcMoveRowTier.TINY, CalcMoveRowTier.fromWidth(Int.MIN_VALUE))
        assertEquals(CalcMoveRowTier.TINY, CalcMoveRowTier.fromWidth(-10))
        assertEquals(CalcMoveRowTier.TINY, CalcMoveRowTier.fromWidth(0))
        assertEquals(CalcMoveRowTier.TINY, CalcMoveRowTier.fromWidth(132))
        assertEquals(CalcMoveRowTier.TINY, CalcMoveRowTier.fromWidth(149))

        assertEquals(CalcMoveRowTier.COMPACT, CalcMoveRowTier.fromWidth(150))
        assertEquals(CalcMoveRowTier.COMPACT, CalcMoveRowTier.fromWidth(180))
        assertEquals(CalcMoveRowTier.COMPACT, CalcMoveRowTier.fromWidth(209))

        assertEquals(CalcMoveRowTier.WIDE, CalcMoveRowTier.fromWidth(210))
        assertEquals(CalcMoveRowTier.WIDE, CalcMoveRowTier.fromWidth(228))
        assertEquals(CalcMoveRowTier.WIDE, CalcMoveRowTier.fromWidth(320))
        assertEquals(CalcMoveRowTier.WIDE, CalcMoveRowTier.fromWidth(Int.MAX_VALUE))
    }

    @Test
    fun testKoReservedFirstAndDamageSecondWhenItFits() {
        // Available width 228 (WIDE tier, default minNameWidth = 40)
        // KO takes 35px. Space left of KO = 228 - 35 = 193px.
        // Space before KO gap (4) = 189px.
        // Damage takes 60px. Needed = 60 + 40 (min name) + 4 (gap) = 104px <= 189px.
        // Damage fits! Damage gets 60px. Move name gets remainder = 189 - 60 - 4 = 125px.
        val layout = CalcMoveRowLayout.calculate(
            availableWidth = 228,
            measuredDamageWidth = 60,
            measuredKoWidth = 35,
            gap = 4
        )

        assertEquals(CalcMoveRowTier.WIDE, layout.tier)
        assertTrue(layout.koVisible)
        assertEquals(35, layout.koWidth)
        assertEquals(228 - 35, layout.koX)

        assertTrue(layout.damageVisible)
        assertEquals(60, layout.damageWidth)
        assertEquals(layout.koX - 4 - 60, layout.damageX)

        assertEquals(0, layout.moveNameX)
        assertEquals(layout.damageX - 4, layout.moveNameWidth)
        assertTrue(layout.moveNameWidth >= 40)
    }

    @Test
    fun testDamageOmittedWhenItDoesNotFitLeavingRemainderToMoveName() {
        // At width 132, KO takes 35px. Space left = 132 - 35 = 97px.
        // Space before KO gap (4) = 93px.
        // If damage takes 85px: needed = 85 + 20 (min name) + 4 = 109px > 93px.
        // Damage does NOT fit! Damage should be omitted, giving full 93px to move name.
        val layout = CalcMoveRowLayout.calculate(
            availableWidth = 132,
            measuredDamageWidth = 85,
            measuredKoWidth = 35,
            gap = 4
        )

        assertEquals(CalcMoveRowTier.TINY, layout.tier)
        assertTrue(layout.koVisible)
        assertEquals(35, layout.koWidth)
        assertEquals(132 - 35, layout.koX)

        assertFalse(layout.damageVisible)
        assertEquals(0, layout.damageWidth)

        assertEquals(0, layout.moveNameX)
        assertEquals(layout.koX - 4, layout.moveNameWidth)
        assertEquals(93, layout.moveNameWidth)
    }

    @Test
    fun testExtremeInputsDoNotThrowOrReturnNegativeWidths() {
        // Width 0
        val zeroLayout = CalcMoveRowLayout.calculate(
            availableWidth = 0,
            measuredDamageWidth = 50,
            measuredKoWidth = 50
        )
        assertEquals(0, zeroLayout.moveNameWidth)
        assertEquals(0, zeroLayout.damageWidth)
        assertEquals(0, zeroLayout.koWidth)
        assertEquals(0, zeroLayout.moveNameX)

        // Negative widths (including Int.MIN_VALUE)
        for (w in listOf(-100, -1, Int.MIN_VALUE)) {
            val negLayout = CalcMoveRowLayout.calculate(
                availableWidth = w,
                measuredDamageWidth = 50,
                measuredKoWidth = 50
            )
            assertEquals(0, negLayout.availableWidth)
            assertEquals(0, negLayout.moveNameWidth)
            assertEquals(0, negLayout.damageWidth)
            assertEquals(0, negLayout.koWidth)
            assertEquals(0, negLayout.moveNameX)
            assertEquals(0, negLayout.koX)
        }

        // Massive measurements exceeding Int bounds safely
        val hugeLayout = CalcMoveRowLayout.calculate(
            availableWidth = 200,
            measuredDamageWidth = Int.MAX_VALUE,
            measuredKoWidth = Int.MAX_VALUE
        )
        assertTrue(hugeLayout.koWidth in 0..200)
        assertTrue(hugeLayout.damageWidth >= 0)
        assertTrue(hugeLayout.moveNameWidth >= 0)
        assertTrue(hugeLayout.koRight <= 200)

        // Negative measured widths safely handled as 0
        val negMeasured = CalcMoveRowLayout.calculate(
            availableWidth = 200,
            measuredDamageWidth = Int.MIN_VALUE,
            measuredKoWidth = -50
        )
        assertEquals(0, negMeasured.koWidth)
        assertEquals(0, negMeasured.damageWidth)
        assertTrue(negMeasured.moveNameWidth in 0..200)

        // Int.MAX_VALUE available width
        val maxAvailable = CalcMoveRowLayout.calculate(
            availableWidth = Int.MAX_VALUE,
            measuredDamageWidth = 100,
            measuredKoWidth = 50
        )
        assertTrue(maxAvailable.moveNameX >= 0)
        assertTrue(maxAvailable.moveNameRight <= maxAvailable.damageX)
        assertTrue(maxAvailable.damageRight <= maxAvailable.koX)
        assertTrue(maxAvailable.koRight <= Int.MAX_VALUE)
    }

    @Test
    fun testTextMeasurerAndRowOverloads() {
        val measurer = CalcTextMeasurer { text -> text.length * 6 }
        val row = CalcMoveRow(
            moveName = "Close Combat",
            damageText = "75.4 - 88.9%",
            koText = "Likely OHKO",
            outcome = CalcMoveOutcome.LIKELY_OHKO,
            confidence = DamageConfidence.HIGH
        )

        val layout = CalcMoveRowLayout.calculate(
            availableWidth = 228,
            row = row,
            measurer = measurer
        )

        assertEquals(CalcMoveRowTier.WIDE, layout.tier)
        assertTrue(layout.koWidth > 0)
        assertTrue(layout.damageWidth > 0)
        assertTrue(layout.moveNameWidth > 0)
        assertTrue(layout.moveNameRight <= layout.damageX)
        assertTrue(layout.damageRight <= layout.koX)
        assertTrue(layout.koRight <= 228)
    }

    @ParameterizedTest
    @ValueSource(ints = [132, 150, 180, 228, 320])
    fun testLayoutAcrossRequiredWidthAndFontScaleMatrix(availableWidth: Int) {
        val fontScales = listOf(0.35f, 1.0f, 1.6f)
        val baseFontScale = 0.7f

        val testCases = listOf(
            // Short/standard move outcome
            "52.1 - 61.4%" to "2HKO",
            // Longer multi-hit / crit damage outcome
            "75.4 - 88.9% ×2 / crit 112.5-133.4%" to "Likely OHKO",
            // Guaranteed OHKO
            "102.4 - 120.8%" to "Guaranteed OHKO",
            // Status move
            "--" to "Status",
            // Extreme long string
            "100.0 - 100.0% ×5 / crit 150.0-150.0%" to "Likely 4HKO+"
        )

        for (fontScale in fontScales) {
            val effectiveScale = baseFontScale * fontScale
            for ((dmgText, koText) in testCases) {
                // Character width model: ~6px per char at scale 1.0
                val measuredDmg = kotlin.math.ceil(dmgText.length * 6 * effectiveScale).toInt()
                val measuredKo = kotlin.math.ceil(koText.length * 6 * effectiveScale).toInt()

                val layout = CalcMoveRowLayout.calculate(
                    availableWidth = availableWidth,
                    measuredDamageWidth = measuredDmg,
                    measuredKoWidth = measuredKo
                )

                // Every width must be strictly non-negative
                assertTrue(layout.moveNameWidth >= 0, "moveNameWidth >= 0 at w=$availableWidth, s=$fontScale")
                assertTrue(layout.damageWidth >= 0, "damageWidth >= 0 at w=$availableWidth, s=$fontScale")
                assertTrue(layout.koWidth >= 0, "koWidth >= 0 at w=$availableWidth, s=$fontScale")

                // Relative ordering and containment within availableWidth
                assertTrue(layout.moveNameX >= 0)
                assertTrue(layout.moveNameRight <= layout.damageX, "moveNameRight <= damageX at w=$availableWidth, s=$fontScale")
                assertTrue(layout.damageRight <= layout.koX, "damageRight <= koX at w=$availableWidth, s=$fontScale")
                assertTrue(layout.koRight <= availableWidth, "koRight (${layout.koRight}) <= availableWidth ($availableWidth) at s=$fontScale")
                assertTrue(layout.moveNameRight <= availableWidth, "moveNameRight <= availableWidth")
                assertTrue(layout.damageRight <= availableWidth, "damageRight <= availableWidth")
            }
        }
    }
}
