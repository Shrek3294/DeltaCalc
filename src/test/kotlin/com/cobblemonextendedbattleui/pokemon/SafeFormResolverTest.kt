package com.cobblemonextendedbattleui.pokemon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SafeFormResolverTest {

    @Test
    fun testDuplicateKeyLogsOnce() {
        val gate = BoundedDiagnosticGate(maxUniqueKeys = 32)

        val first = gate.recordFailure("species_charizard_mega")
        assertEquals(DiagnosticDecision.LOG_WARNING, first)
        assertEquals(1, gate.seenKeyCount)

        val second = gate.recordFailure("species_charizard_mega")
        assertEquals(DiagnosticDecision.SILENT, second)
        assertEquals(1, gate.seenKeyCount)
    }

    @Test
    fun testUniqueKeysLogThroughCapAndEmitSingleSuppressionNotice() {
        val cap = 32
        val gate = BoundedDiagnosticGate(maxUniqueKeys = cap)

        // Unique keys up to cap must all emit LOG_WARNING
        for (i in 1..cap) {
            val decision = gate.recordFailure("context_species_$i")
            assertEquals(DiagnosticDecision.LOG_WARNING, decision, "Key $i should log warning")
        }

        assertEquals(cap, gate.seenKeyCount)
        assertFalse(gate.isSuppressed)

        // The very first unique key past the cap must emit LOG_SUPPRESSION_NOTICE
        val suppressionDecision = gate.recordFailure("context_species_${cap + 1}")
        assertEquals(DiagnosticDecision.LOG_SUPPRESSION_NOTICE, suppressionDecision)
        assertTrue(gate.isSuppressed)
        assertEquals(cap, gate.seenKeyCount, "Key count must not grow beyond cap")

        // Any subsequent unique keys must be SILENT
        for (i in (cap + 2)..(cap + 10)) {
            val decision = gate.recordFailure("context_species_$i")
            assertEquals(DiagnosticDecision.SILENT, decision, "Key $i past cap should be silent")
        }
        assertEquals(cap, gate.seenKeyCount, "Key count must remain bounded at cap")

        // Duplicate keys after suppression must also be SILENT
        assertEquals(DiagnosticDecision.SILENT, gate.recordFailure("context_species_1"))
        assertEquals(DiagnosticDecision.SILENT, gate.recordFailure("context_species_${cap + 1}"))
    }

    @Test
    fun testGateResetHookClearsSeenKeysAndSuppression() {
        val gate = BoundedDiagnosticGate(maxUniqueKeys = 2)
        gate.recordFailure("key_1")
        gate.recordFailure("key_2")
        gate.recordFailure("key_3") // triggers suppression
        assertTrue(gate.isSuppressed)
        assertEquals(2, gate.seenKeyCount)

        gate.resetForTests()
        assertFalse(gate.isSuppressed)
        assertEquals(0, gate.seenKeyCount)

        val afterReset = gate.recordFailure("key_1")
        assertEquals(DiagnosticDecision.LOG_WARNING, afterReset)
        assertEquals(1, gate.seenKeyCount)
    }

    @Test
    fun testSafeResolveReturnsValueOnSuccess() {
        SafeFormResolver.resetForTests()

        val value = SafeFormResolver.safeResolve(
            context = "test.success",
            speciesName = "cobblemon:pikachu",
            aspects = listOf("alola")
        ) {
            "resolved_form_data"
        }

        assertEquals("resolved_form_data", value)
        assertEquals(0, SafeFormResolver.gate.seenKeyCount)
    }

    @Test
    fun testSafeResolveCatchesExceptionAndReturnsNull() {
        SafeFormResolver.resetForTests()

        val value = SafeFormResolver.safeResolve(
            context = "test.exception",
            speciesName = "cobblemon:unknown_species",
            aspects = listOf("custom_aspect")
        ) {
            throw IllegalStateException("Cobblemon species aspect index out of bounds")
        }

        assertNull(value)
        assertEquals(1, SafeFormResolver.gate.seenKeyCount)
    }

    @Test
    fun testSafeResolveDoesNotCatchThrowableErrors() {
        SafeFormResolver.resetForTests()

        assertThrows(AssertionError::class.java) {
            SafeFormResolver.safeResolve(
                context = "test.error",
                speciesName = "cobblemon:test",
                aspects = listOf("error")
            ) {
                throw AssertionError("Unrecoverable test assertion failure")
            }
        }
    }
}
