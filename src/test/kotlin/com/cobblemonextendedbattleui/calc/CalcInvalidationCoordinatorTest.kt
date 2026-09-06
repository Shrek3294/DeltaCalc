package com.cobblemonextendedbattleui.calc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class CalcInvalidationCoordinatorTest {

    private fun createPokemon(
        uuid: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        displayName: String = "Pikachu",
        speciesId: String? = "cobblemon:pikachu",
        teraType: String? = null,
        baseStats: CalcStats? = CalcStats(35, 55, 40, 50, 50, 90),
        actualStats: CalcStats? = CalcStats(110, 75, 60, 70, 70, 110),
        canEvolve: Boolean = true,
        weightKg: Double? = 6.0
    ): CalcPokemonSnapshot = CalcPokemonSnapshot(
        uuid = uuid,
        displayName = displayName,
        speciesId = speciesId,
        speciesKey = "pikachu",
        speciesLabel = "Pikachu",
        formName = null,
        level = 50,
        currentHp = 100,
        maxHp = 100,
        exactHpValues = true,
        status = null,
        typeNames = listOf("electric"),
        teraType = teraType,
        itemName = "Light Ball",
        abilityName = "Static",
        revealedMoves = listOf("Thunderbolt"),
        statStages = emptyMap(),
        moveList = listOf(CalcMoveRef("thunderbolt", "Thunderbolt")),
        baseStats = baseStats,
        actualStats = actualStats,
        canEvolve = canEvolve,
        weightKg = weightKg
    )

    private fun createSnapshot(
        weather: String? = null,
        playerActive: CalcPokemonSnapshot = createPokemon(),
        compatNotes: List<String> = emptyList()
    ): CalcBattleSnapshot = CalcBattleSnapshot(
        battleId = UUID.fromString("00000000-0000-0000-0000-000000000099"),
        turn = 1,
        weather = weather,
        terrain = null,
        playerSide = CalcSideState(emptyMap()),
        opponentSide = CalcSideState(emptyMap()),
        playerTeam = listOf(playerActive),
        opponentTeam = emptyList(),
        playerActiveUuid = playerActive.uuid,
        opponentActiveUuid = null,
        playerActive = playerActive,
        opponentActive = null,
        selectedMoveName = "Thunderbolt",
        compatNotes = compatNotes
    )

    @Test
    fun testFirstUpdateReturnsTrueWithBattleStarted() {
        val coordinator = CalcInvalidationCoordinator()
        val snapshot = createSnapshot()

        val changed = coordinator.update(snapshot)
        assertTrue(changed)

        val reasons = coordinator.consumeReasons()
        assertEquals(setOf(CalcInvalidationReason.BATTLE_STARTED), reasons)

        // consumeReasons cleared the pending reasons
        assertTrue(coordinator.consumeReasons().isEmpty())
    }

    @Test
    fun testUnchangedSnapshotReturnsFalseWithEmptyReasons() {
        val coordinator = CalcInvalidationCoordinator()
        val snapshot = createSnapshot()
        coordinator.update(snapshot)
        coordinator.consumeReasons()

        val changedAgain = coordinator.update(snapshot.copy())
        assertFalse(changedAgain)
        assertTrue(coordinator.consumeReasons().isEmpty())
    }

    @Test
    fun testTeraTypeChangeEmitsCalcInputChange() {
        val coordinator = CalcInvalidationCoordinator()
        val base = createSnapshot()
        coordinator.update(base)
        coordinator.consumeReasons()

        val mutatedMon = base.playerActive!!.copy(teraType = "Water")
        val mutatedSnapshot = base.copy(
            playerActive = mutatedMon,
            playerTeam = listOf(mutatedMon)
        )

        val changed = coordinator.update(mutatedSnapshot)
        assertTrue(changed)

        val reasons = coordinator.consumeReasons()
        assertTrue(reasons.isNotEmpty())
        assertTrue(CalcInvalidationReason.CALC_INPUT_CHANGE in reasons)
    }

    @Test
    fun testBaseStatsChangeEmitsCalcInputChange() {
        val coordinator = CalcInvalidationCoordinator()
        val base = createSnapshot()
        coordinator.update(base)
        coordinator.consumeReasons()

        val mutatedMon = base.playerActive!!.copy(baseStats = CalcStats(35, 60, 40, 50, 50, 90))
        val mutatedSnapshot = base.copy(
            playerActive = mutatedMon,
            playerTeam = listOf(mutatedMon)
        )

        val changed = coordinator.update(mutatedSnapshot)
        assertTrue(changed)

        val reasons = coordinator.consumeReasons()
        assertTrue(reasons.isNotEmpty())
        assertTrue(CalcInvalidationReason.CALC_INPUT_CHANGE in reasons)
    }

    @Test
    fun testActualStatsChangeEmitsCalcInputChange() {
        val coordinator = CalcInvalidationCoordinator()
        val base = createSnapshot()
        coordinator.update(base)
        coordinator.consumeReasons()

        val mutatedMon = base.playerActive!!.copy(actualStats = CalcStats(110, 80, 60, 70, 70, 110))
        val mutatedSnapshot = base.copy(
            playerActive = mutatedMon,
            playerTeam = listOf(mutatedMon)
        )

        val changed = coordinator.update(mutatedSnapshot)
        assertTrue(changed)

        val reasons = coordinator.consumeReasons()
        assertTrue(reasons.isNotEmpty())
        assertTrue(CalcInvalidationReason.CALC_INPUT_CHANGE in reasons)
    }

    @Test
    fun testCanEvolveChangeEmitsCalcInputChange() {
        val coordinator = CalcInvalidationCoordinator()
        val base = createSnapshot()
        coordinator.update(base)
        coordinator.consumeReasons()

        val mutatedMon = base.playerActive!!.copy(canEvolve = false)
        val mutatedSnapshot = base.copy(
            playerActive = mutatedMon,
            playerTeam = listOf(mutatedMon)
        )

        val changed = coordinator.update(mutatedSnapshot)
        assertTrue(changed)

        val reasons = coordinator.consumeReasons()
        assertTrue(reasons.isNotEmpty())
        assertTrue(CalcInvalidationReason.CALC_INPUT_CHANGE in reasons)
    }

    @Test
    fun testWeightKgChangeEmitsCalcInputChange() {
        val coordinator = CalcInvalidationCoordinator()
        val base = createSnapshot()
        coordinator.update(base)
        coordinator.consumeReasons()

        val mutatedMon = base.playerActive!!.copy(weightKg = 30.0)
        val mutatedSnapshot = base.copy(
            playerActive = mutatedMon,
            playerTeam = listOf(mutatedMon)
        )

        val changed = coordinator.update(mutatedSnapshot)
        assertTrue(changed)

        val reasons = coordinator.consumeReasons()
        assertTrue(reasons.isNotEmpty())
        assertTrue(CalcInvalidationReason.CALC_INPUT_CHANGE in reasons)
    }

    @Test
    fun testCompatNotesChangeEmitsCalcInputChange() {
        val coordinator = CalcInvalidationCoordinator()
        val base = createSnapshot()
        coordinator.update(base)
        coordinator.consumeReasons()

        val mutatedSnapshot = base.copy(compatNotes = listOf("Delta platform rule update"))

        val changed = coordinator.update(mutatedSnapshot)
        assertTrue(changed)

        val reasons = coordinator.consumeReasons()
        assertTrue(reasons.isNotEmpty())
        assertTrue(CalcInvalidationReason.CALC_INPUT_CHANGE in reasons)
    }

    @Test
    fun testSpecificReasonWeatherChangeDoesNotEmitCalcInputChange() {
        val coordinator = CalcInvalidationCoordinator()
        val base = createSnapshot()
        coordinator.update(base)
        coordinator.consumeReasons()

        val mutatedSnapshot = base.copy(weather = "Rain")

        val changed = coordinator.update(mutatedSnapshot)
        assertTrue(changed)

        val reasons = coordinator.consumeReasons()
        assertEquals(setOf(CalcInvalidationReason.WEATHER_CHANGE), reasons)
        assertFalse(CalcInvalidationReason.CALC_INPUT_CHANGE in reasons)
    }

    @Test
    fun testUnconsumedDuplicateReasonDoesNotSpuriouslyEmitCalcInputChange() {
        val coordinator = CalcInvalidationCoordinator()
        val base = createSnapshot(weather = null)
        coordinator.update(base)
        coordinator.consumeReasons()

        // First transition: weather changes from null to "Rain"
        val rainSnapshot = base.copy(weather = "Rain")
        assertTrue(coordinator.update(rainSnapshot))
        // Do NOT consume reasons yet; pendingReasons now contains [WEATHER_CHANGE]

        // Second transition: weather changes from "Rain" to "Sun"
        val sunSnapshot = base.copy(weather = "Sun")
        assertTrue(coordinator.update(sunSnapshot))

        val reasons = coordinator.consumeReasons()
        assertEquals(setOf(CalcInvalidationReason.WEATHER_CHANGE), reasons)
        assertFalse(CalcInvalidationReason.CALC_INPUT_CHANGE in reasons)
    }
}
