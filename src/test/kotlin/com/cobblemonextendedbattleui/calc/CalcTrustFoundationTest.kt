package com.cobblemonextendedbattleui.calc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CalcTrustFoundationTest {

    @Test
    fun testGuaranteedOhkoAndLikelyOhkoAreDistinctStructuredOutcomes() {
        assertNotEquals(CalcMoveOutcome.GUARANTEED_OHKO, CalcMoveOutcome.LIKELY_OHKO)
        assertTrue(CalcMoveOutcome.GUARANTEED_OHKO.isOhko)
        assertTrue(CalcMoveOutcome.LIKELY_OHKO.isOhko)
        assertTrue(CalcMoveOutcome.GUARANTEED_OHKO.isGuaranteedOhko)
        assertFalse(CalcMoveOutcome.GUARANTEED_OHKO.isLikelyOhko)
        assertTrue(CalcMoveOutcome.LIKELY_OHKO.isLikelyOhko)
        assertFalse(CalcMoveOutcome.LIKELY_OHKO.isGuaranteedOhko)

        // Threshold checks via outcomeFor
        val guaranteed = BestEffortDamageEngine.outcomeFor(currentHp = 100, minDamage = 105, maxDamage = 125)
        assertEquals(CalcMoveOutcome.GUARANTEED_OHKO, guaranteed)
        assertEquals("OHKO", BestEffortDamageEngine.koLabel(currentHp = 100, minDamage = 105, maxDamage = 125))

        val likely = BestEffortDamageEngine.outcomeFor(currentHp = 100, minDamage = 92, maxDamage = 108)
        assertEquals(CalcMoveOutcome.LIKELY_OHKO, likely)
        assertEquals("Likely OHKO", BestEffortDamageEngine.koLabel(currentHp = 100, minDamage = 92, maxDamage = 108))

        val twoHko = BestEffortDamageEngine.outcomeFor(currentHp = 100, minDamage = 45, maxDamage = 54)
        assertEquals(CalcMoveOutcome.TWO_HKO, twoHko)
        assertEquals("2HKO", BestEffortDamageEngine.koLabel(currentHp = 100, minDamage = 45, maxDamage = 54))

        val threeHko = BestEffortDamageEngine.outcomeFor(currentHp = 100, minDamage = 28, maxDamage = 35)
        assertEquals(CalcMoveOutcome.THREE_HKO, threeHko)
        assertEquals("3HKO", BestEffortDamageEngine.koLabel(currentHp = 100, minDamage = 28, maxDamage = 35))

        val fourHko = BestEffortDamageEngine.outcomeFor(currentHp = 100, minDamage = 10, maxDamage = 22)
        assertEquals(CalcMoveOutcome.FOUR_HKO_PLUS, fourHko)
        assertEquals("4HKO+", BestEffortDamageEngine.koLabel(currentHp = 100, minDamage = 10, maxDamage = 22))

        val ko = BestEffortDamageEngine.outcomeFor(currentHp = 0, minDamage = 0, maxDamage = 0)
        assertEquals(CalcMoveOutcome.KO, ko)
        assertEquals("KO", BestEffortDamageEngine.koLabel(currentHp = 0, minDamage = 0, maxDamage = 0))
    }

    @Test
    fun testOutcomeForHandlesExtremeIntValuesWithoutOverflow() {
        // Test near-overflow where maxDamage * 2 would overflow 32-bit signed Int
        val maxDmgHalfMax = Int.MAX_VALUE / 2 + 100
        val twoHkoResult = BestEffortDamageEngine.outcomeFor(
            currentHp = Int.MAX_VALUE,
            minDamage = 10,
            maxDamage = maxDmgHalfMax
        )
        assertEquals(CalcMoveOutcome.TWO_HKO, twoHkoResult)

        // Test where maxDamage * 3 would overflow 32-bit signed Int
        val maxDmgThirdMax = Int.MAX_VALUE / 3 + 100
        val threeHkoResult = BestEffortDamageEngine.outcomeFor(
            currentHp = Int.MAX_VALUE,
            minDamage = 10,
            maxDamage = maxDmgThirdMax
        )
        assertEquals(CalcMoveOutcome.THREE_HKO, threeHkoResult)

        // Int.MAX_VALUE HP with small damage is 4HKO+
        val chipResult = BestEffortDamageEngine.outcomeFor(
            currentHp = Int.MAX_VALUE,
            minDamage = 10,
            maxDamage = 100
        )
        assertEquals(CalcMoveOutcome.FOUR_HKO_PLUS, chipResult)

        // Int.MAX_VALUE damage is guaranteed OHKO
        val ohkoResult = BestEffortDamageEngine.outcomeFor(
            currentHp = Int.MAX_VALUE,
            minDamage = Int.MAX_VALUE,
            maxDamage = Int.MAX_VALUE
        )
        assertEquals(CalcMoveOutcome.GUARANTEED_OHKO, ohkoResult)

        // Negative/zero HP values produce KO without crashing
        assertEquals(CalcMoveOutcome.KO, BestEffortDamageEngine.outcomeFor(currentHp = Int.MIN_VALUE, minDamage = 0, maxDamage = 0))
        assertEquals(CalcMoveOutcome.KO, BestEffortDamageEngine.outcomeFor(currentHp = -1, minDamage = 0, maxDamage = 0))
    }

    @Test
    fun testStatusProducesStatusOutcomeAndIsStatusTrueWithoutUnsupportedWarning() {
        val estimate = BestEffortDamageEngine.statusEstimate("Thunder Wave")

        assertEquals(CalcMoveOutcome.STATUS, estimate.outcome)
        assertTrue(estimate.supported)
        assertEquals("Status", estimate.koLabel)
        assertFalse(estimate.warnings.any { it.contains("unsupported", ignoreCase = true) })
        assertFalse(estimate.warnings.any { it.contains("status move", ignoreCase = true) })

        val row = CalcComputationService.toRow(estimate)

        assertEquals(CalcMoveOutcome.STATUS, row.outcome)
        assertTrue(row.isStatus)
        assertTrue(row.supported)
        assertEquals("--", row.damageText)
        assertEquals("Status", row.koText)
        assertFalse(row.warnings.any { it.contains("unsupported", ignoreCase = true) })
    }

    @Test
    fun testUnsupportedRemainsDistinct() {
        val estimate = BestEffortDamageEngine.unsupportedEstimate("Metronome", "Unsupported move data")

        assertEquals(CalcMoveOutcome.UNSUPPORTED, estimate.outcome)
        assertFalse(estimate.supported)
        assertEquals("--", estimate.koLabel)
        assertTrue(estimate.warnings.contains("Unsupported move data"))

        val row = CalcComputationService.toRow(estimate)

        assertEquals(CalcMoveOutcome.UNSUPPORTED, row.outcome)
        assertFalse(row.isStatus)
        assertFalse(row.supported)
        assertEquals("unsupported move data", row.damageText)
        assertEquals("--", row.koText)
    }

    @Test
    fun testConfidenceWarningsAndSupportedSurviveToCalcMoveRow() {
        val estimate = DamageEstimate(
            moveId = "surf",
            moveName = "Surf",
            minDamage = 80,
            maxDamage = 95,
            minPercent = 48.5,
            maxPercent = 57.6,
            koLabel = "2HKO",
            confidence = DamageConfidence.MEDIUM,
            warnings = listOf("Inferred spread", "Inferred item"),
            supported = true,
            outcome = CalcMoveOutcome.TWO_HKO
        )

        val row = CalcComputationService.toRow(estimate)

        assertEquals(DamageConfidence.MEDIUM, row.confidence)
        assertEquals(listOf("Inferred spread", "Inferred item"), row.warnings)
        assertTrue(row.supported)
        assertEquals(CalcMoveOutcome.TWO_HKO, row.outcome)
        assertFalse(row.isStatus)
        assertEquals("48.5 - 57.6%", row.damageText)
        assertEquals("2HKO", row.koText)
    }

    @Test
    fun testWarningTextsIsStableDeduplicatedAndExcludesRecomputeReasonProse() {
        val damageResultWarnings = listOf(
            "Inferred spread",
            "Inferred item",
            "Snapshot stable",
            "Recomputed: turn, weather",
            "Inferred spread" // Duplicate in general warnings
        )
        val yourMoves = listOf(
            CalcMoveRow(
                moveName = "Bullet Seed",
                damageText = "40 - 60%",
                koText = "2HKO",
                outcome = CalcMoveOutcome.TWO_HKO,
                warnings = listOf("Multi-hit (2-5 hits)", "Inferred item")
            )
        )
        val opponentMoves = listOf(
            CalcMoveRow(
                moveName = "Shadow Ball",
                damageText = "50 - 60%",
                koText = "2HKO",
                outcome = CalcMoveOutcome.TWO_HKO,
                warnings = listOf("Guessed opponent move", "Multi-hit (2-5 hits)")
            )
        )

        val result = CalcComputationService.buildWarningTexts(damageResultWarnings, yourMoves, opponentMoves)

        // Must preserve first-seen order without duplicates
        assertEquals(
            listOf("Inferred spread", "Inferred item", "Multi-hit (2-5 hits)", "Guessed opponent move"),
            result
        )

        // Must exclude recompute reasons
        assertFalse(result.any { it.startsWith("Snapshot stable", ignoreCase = true) })
        assertFalse(result.any { it.startsWith("Recomputed", ignoreCase = true) })
    }
}
