package com.cobblemonextendedbattleui.calc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class CalcBattleSnapshotFingerprintTest {

    private fun createPokemon(
        uuid: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        displayName: String = "Pikachu",
        speciesId: String? = "cobblemon:pikachu",
        speciesKey: String? = "pikachu",
        speciesLabel: String = "Pikachu",
        formName: String? = null,
        level: Int = 50,
        currentHp: Int = 100,
        maxHp: Int = 100,
        exactHpValues: Boolean = true,
        status: String? = null,
        typeNames: List<String> = listOf("electric"),
        teraType: String? = null,
        itemName: String? = "Light Ball",
        abilityName: String? = "Static",
        revealedMoves: List<String> = listOf("Thunderbolt"),
        statStages: Map<String, Int> = emptyMap(),
        moveList: List<CalcMoveRef> = listOf(CalcMoveRef("thunderbolt", "Thunderbolt")),
        baseStats: CalcStats? = CalcStats(35, 55, 40, 50, 50, 90),
        actualStats: CalcStats? = CalcStats(110, 75, 60, 70, 70, 110),
        canEvolve: Boolean = true,
        weightKg: Double? = 6.0
    ): CalcPokemonSnapshot = CalcPokemonSnapshot(
        uuid = uuid,
        displayName = displayName,
        speciesId = speciesId,
        speciesKey = speciesKey,
        speciesLabel = speciesLabel,
        formName = formName,
        level = level,
        currentHp = currentHp,
        maxHp = maxHp,
        exactHpValues = exactHpValues,
        status = status,
        typeNames = typeNames,
        teraType = teraType,
        itemName = itemName,
        abilityName = abilityName,
        revealedMoves = revealedMoves,
        statStages = statStages,
        moveList = moveList,
        baseStats = baseStats,
        actualStats = actualStats,
        canEvolve = canEvolve,
        weightKg = weightKg
    )

    private fun createSnapshot(
        battleId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000099"),
        turn: Int = 1,
        weather: String? = null,
        terrain: String? = null,
        playerSide: CalcSideState = CalcSideState(emptyMap()),
        opponentSide: CalcSideState = CalcSideState(emptyMap()),
        playerActive: CalcPokemonSnapshot = createPokemon(),
        opponentActive: CalcPokemonSnapshot = createPokemon(
            uuid = UUID.fromString("00000000-0000-0000-0000-000000000002"),
            displayName = "Eevee",
            speciesId = "cobblemon:eevee",
            speciesKey = "eevee",
            speciesLabel = "Eevee",
            typeNames = listOf("normal"),
            baseStats = CalcStats(55, 55, 50, 45, 65, 55),
            actualStats = CalcStats(130, 75, 70, 65, 85, 75),
            canEvolve = true,
            weightKg = 6.5
        ),
        selectedMoveName: String? = "Thunderbolt",
        compatNotes: List<String> = emptyList()
    ): CalcBattleSnapshot = CalcBattleSnapshot(
        battleId = battleId,
        turn = turn,
        weather = weather,
        terrain = terrain,
        playerSide = playerSide,
        opponentSide = opponentSide,
        playerTeam = listOf(playerActive),
        opponentTeam = listOf(opponentActive),
        playerActiveUuid = playerActive.uuid,
        opponentActiveUuid = opponentActive.uuid,
        playerActive = playerActive,
        opponentActive = opponentActive,
        selectedMoveName = selectedMoveName,
        compatNotes = compatNotes
    )

    @Test
    fun testUnchangedSnapshotsHaveIdenticalFingerprints() {
        val base = createSnapshot()
        assertEquals(base.fingerprint(), base.fingerprint())
        assertEquals(base.fingerprint(), base.copy().fingerprint())
        assertEquals(base.playerActive.fingerprintPart(), base.playerActive.fingerprintPart())
    }

    @Test
    fun testTeraTypeMutationChangesFingerprint() {
        val base = createSnapshot()
        val nullTera = base.playerActive!!.fingerprintPart()

        val withFire = base.playerActive!!.copy(teraType = "Fire")
        val fireTera = withFire.fingerprintPart()
        assertNotEquals(nullTera, fireTera)

        val withWater = base.playerActive!!.copy(teraType = "Water")
        val waterTera = withWater.fingerprintPart()
        assertNotEquals(fireTera, waterTera)

        val snapshotWithFire = base.copy(
            playerActive = withFire,
            playerTeam = listOf(withFire)
        )
        assertNotEquals(base.fingerprint(), snapshotWithFire.fingerprint())
    }

    @Test
    fun testBaseStatsMutationChangesFingerprint() {
        val base = createSnapshot()
        val originalPart = base.playerActive.fingerprintPart()

        // null vs value
        val withNullBaseStats = base.playerActive!!.copy(baseStats = null)
        assertNotEquals(originalPart, withNullBaseStats.fingerprintPart())

        // change each individual stat field
        val stats = CalcStats(100, 100, 100, 100, 100, 100)
        val mon = base.playerActive!!.copy(baseStats = stats)
        val monPart = mon.fingerprintPart()

        assertNotEquals(monPart, mon.copy(baseStats = stats.copy(hp = 101)).fingerprintPart())
        assertNotEquals(monPart, mon.copy(baseStats = stats.copy(atk = 101)).fingerprintPart())
        assertNotEquals(monPart, mon.copy(baseStats = stats.copy(def = 101)).fingerprintPart())
        assertNotEquals(monPart, mon.copy(baseStats = stats.copy(spa = 101)).fingerprintPart())
        assertNotEquals(monPart, mon.copy(baseStats = stats.copy(spd = 101)).fingerprintPart())
        assertNotEquals(monPart, mon.copy(baseStats = stats.copy(spe = 101)).fingerprintPart())

        val snapshotMutated = base.copy(
            playerActive = mon.copy(baseStats = stats.copy(atk = 101)),
            playerTeam = listOf(mon.copy(baseStats = stats.copy(atk = 101)))
        )
        assertNotEquals(base.fingerprint(), snapshotMutated.fingerprint())
    }

    @Test
    fun testActualStatsMutationChangesFingerprint() {
        val base = createSnapshot()
        val originalPart = base.playerActive.fingerprintPart()

        // null vs value
        val withNullActualStats = base.playerActive!!.copy(actualStats = null)
        assertNotEquals(originalPart, withNullActualStats.fingerprintPart())

        // change stat field
        val stats = CalcStats(200, 150, 120, 90, 110, 130)
        val mon = base.playerActive!!.copy(actualStats = stats)
        val monPart = mon.fingerprintPart()

        assertNotEquals(monPart, mon.copy(actualStats = stats.copy(hp = 201)).fingerprintPart())
        assertNotEquals(monPart, mon.copy(actualStats = stats.copy(atk = 151)).fingerprintPart())
        assertNotEquals(monPart, mon.copy(actualStats = stats.copy(def = 125)).fingerprintPart())
        assertNotEquals(monPart, mon.copy(actualStats = stats.copy(spa = 95)).fingerprintPart())
        assertNotEquals(monPart, mon.copy(actualStats = stats.copy(spd = 115)).fingerprintPart())
        assertNotEquals(monPart, mon.copy(actualStats = stats.copy(spe = 135)).fingerprintPart())

        val snapshotMutated = base.copy(
            playerActive = mon.copy(actualStats = stats.copy(def = 125)),
            playerTeam = listOf(mon.copy(actualStats = stats.copy(def = 125)))
        )
        assertNotEquals(base.fingerprint(), snapshotMutated.fingerprint())
    }

    @Test
    fun testCanEvolveMutationChangesFingerprint() {
        val base = createSnapshot()
        val originalPart = base.playerActive.fingerprintPart()

        val toggled = base.playerActive!!.copy(canEvolve = !base.playerActive!!.canEvolve)
        assertNotEquals(originalPart, toggled.fingerprintPart())

        val snapshotMutated = base.copy(
            playerActive = toggled,
            playerTeam = listOf(toggled)
        )
        assertNotEquals(base.fingerprint(), snapshotMutated.fingerprint())
    }

    @Test
    fun testWeightKgMutationChangesFingerprint() {
        val base = createSnapshot()
        val originalPart = base.playerActive.fingerprintPart()

        // null vs value
        val withNullWeight = base.playerActive!!.copy(weightKg = null)
        assertNotEquals(originalPart, withNullWeight.fingerprintPart())

        // value vs different value
        val withDifferentWeight = base.playerActive!!.copy(weightKg = 59.9)
        assertNotEquals(originalPart, withDifferentWeight.fingerprintPart())

        // small floating point difference (e.g. 6.000000000000001)
        val withTinyDiff = base.playerActive!!.copy(weightKg = 6.000000000000001)
        assertNotEquals(originalPart, withTinyDiff.fingerprintPart())

        val snapshotMutated = base.copy(
            playerActive = withDifferentWeight,
            playerTeam = listOf(withDifferentWeight)
        )
        assertNotEquals(base.fingerprint(), snapshotMutated.fingerprint())
    }

    @Test
    fun testCompatNotesMutationChangesFingerprint() {
        val base = createSnapshot()
        val originalFp = base.fingerprint()

        val withNote = base.copy(compatNotes = listOf("Damage formula override active"))
        assertNotEquals(originalFp, withNote.fingerprint())

        val withDifferentNote = base.copy(compatNotes = listOf("Inference fallback engaged"))
        assertNotEquals(withNote.fingerprint(), withDifferentNote.fingerprint())

        val order1 = base.copy(compatNotes = listOf("Note A", "Note B"))
        val order2 = base.copy(compatNotes = listOf("Note B", "Note A"))
        assertNotEquals(order1.fingerprint(), order2.fingerprint())
    }

    @Test
    fun testNullPokemonFingerprintPartIsNegativeOnePrefixed() {
        val nullMon: CalcPokemonSnapshot? = null
        assertEquals("-1:", nullMon.fingerprintPart())
    }

    @Test
    fun testCompatNotesDelimiterCollisionAvoidance() {
        val base = createSnapshot()

        // Classic pipe separator ambiguity
        val mergedNote = base.copy(compatNotes = listOf("a|b"))
        val splitNotes = base.copy(compatNotes = listOf("a", "b"))
        assertNotEquals(mergedNote.fingerprint(), splitNotes.fingerprint())

        // Colon / length prefix injection ambiguity
        val injectedNote = base.copy(compatNotes = listOf("1:[3:foo]"))
        val normalNotes = base.copy(compatNotes = listOf("1", "[3:foo]"))
        assertNotEquals(injectedNote.fingerprint(), normalNotes.fingerprint())
    }

    @Test
    fun testNestedPokemonListAndStringDelimiterCollisionAvoidance() {
        val base = createSnapshot()
        val mon = base.playerActive!!

        // revealedMoves: single element containing pipe vs two elements
        val monMergedRevealed = mon.copy(revealedMoves = listOf("Thunderbolt|Thunder"))
        val monSplitRevealed = mon.copy(revealedMoves = listOf("Thunderbolt", "Thunder"))
        assertNotEquals(monMergedRevealed.fingerprintPart(), monSplitRevealed.fingerprintPart())

        // typeNames: single element containing pipe vs two elements
        val monMergedTypes = mon.copy(typeNames = listOf("electric|flying"))
        val monSplitTypes = mon.copy(typeNames = listOf("electric", "flying"))
        assertNotEquals(monMergedTypes.fingerprintPart(), monSplitTypes.fingerprintPart())

        // moveList: id/displayName separator shifting
        val monMoveA = mon.copy(moveList = listOf(CalcMoveRef("thunder:wave", "Thunder Wave")))
        val monMoveB = mon.copy(moveList = listOf(CalcMoveRef("thunder", "wave:Thunder Wave")))
        assertNotEquals(monMoveA.fingerprintPart(), monMoveB.fingerprintPart())

        // statStages: key/value separator shifting
        val monStageA = mon.copy(statStages = mapOf("atk:1" to 2))
        val monStageB = mon.copy(statStages = mapOf("atk" to 12))
        assertNotEquals(monStageA.fingerprintPart(), monStageB.fingerprintPart())

        // Delimiter inside user display string cannot bleed across field boundaries
        val monColonName = mon.copy(displayName = "Pikachu:Extra", formName = null)
        val monShiftedForm = mon.copy(displayName = "Pikachu", formName = "Extra")
        assertNotEquals(monColonName.fingerprintPart(), monShiftedForm.fingerprintPart())
    }
}
