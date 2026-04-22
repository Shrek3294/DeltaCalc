package com.cobblemonextendedbattleui.calc

import java.util.UUID

data class CalcMoveRef(
    val id: String,
    val displayName: String
)

data class CalcStats(
    val hp: Int,
    val atk: Int,
    val def: Int,
    val spa: Int,
    val spd: Int,
    val spe: Int
)

data class CalcStatAssumptions(
    val baseStats: CalcStats?,
    val ivs: CalcStats,
    val evs: CalcStats,
    val nature: String,
    val derivedStats: CalcStats
)

data class CalcSideState(
    val sideConditions: Map<String, Int>
) {
    fun hasCondition(condition: String): Boolean {
        val needle = normalizeToken(condition)
        return sideConditions.any { normalizeToken(it.key) == needle }
    }

    fun layersFor(condition: String): Int {
        val needle = normalizeToken(condition)
        return sideConditions.entries.firstOrNull { normalizeToken(it.key) == needle }?.value ?: 0
    }
}

data class CalcPokemonSnapshot(
    val uuid: UUID,
    val displayName: String,
    val speciesId: String?,
    val speciesKey: String?,
    val speciesLabel: String,
    val formName: String?,
    val level: Int,
    val currentHp: Int,
    val maxHp: Int,
    val exactHpValues: Boolean,
    val status: String?,
    val typeNames: List<String>,
    val teraType: String?,
    val itemName: String?,
    val abilityName: String?,
    val revealedMoves: List<String>,
    val statStages: Map<String, Int>,
    val moveList: List<CalcMoveRef>,
    val baseStats: CalcStats?,
    val actualStats: CalcStats?,
    val canEvolve: Boolean,
    val weightKg: Double? = null
) {
    fun stageFor(statName: String): Int {
        val needles = statLookupCandidates(statName)
        return statStages.entries.firstOrNull { statLookupCandidates(it.key).any(needles::contains) }?.value ?: 0
    }

    fun hasType(typeName: String): Boolean {
        val needle = normalizeToken(typeName)
        return typeNames.any { normalizeToken(it) == needle }
    }
}

data class CalcBattleSnapshot(
    val battleId: UUID,
    val turn: Int,
    val weather: String?,
    val terrain: String?,
    val playerSide: CalcSideState,
    val opponentSide: CalcSideState,
    val playerTeam: List<CalcPokemonSnapshot>,
    val opponentTeam: List<CalcPokemonSnapshot>,
    val playerActiveUuid: UUID?,
    val opponentActiveUuid: UUID?,
    val playerActive: CalcPokemonSnapshot?,
    val opponentActive: CalcPokemonSnapshot?,
    val selectedMoveName: String?,
    val compatNotes: List<String>
) {
    fun fingerprint(): String = listOf(
        battleId.toString(),
        turn.toString(),
        weather.orEmpty(),
        terrain.orEmpty(),
        playerSide.sideConditions.entries.sortedBy { it.key }.joinToString("|") { "${it.key}:${it.value}" },
        opponentSide.sideConditions.entries.sortedBy { it.key }.joinToString("|") { "${it.key}:${it.value}" },
        playerActiveUuid?.toString().orEmpty(),
        opponentActiveUuid?.toString().orEmpty(),
        playerTeam.joinToString("|") { it.fingerprintPart() },
        opponentTeam.joinToString("|") { it.fingerprintPart() },
        playerActive.fingerprintPart(),
        opponentActive.fingerprintPart(),
        selectedMoveName.orEmpty()
    ).joinToString("::")

    fun findPlayer(uuid: UUID?): CalcPokemonSnapshot? = playerTeam.firstOrNull { it.uuid == uuid }

    fun findOpponent(uuid: UUID?): CalcPokemonSnapshot? = opponentTeam.firstOrNull { it.uuid == uuid }
}

enum class InferenceValueState {
    GUESSED,
    REVEALED,
    UNKNOWN
}

data class InferenceMoveSlot(
    val moveName: String,
    val state: InferenceValueState,
    val confidenceLabel: String
)

data class InferenceSpread(
    val nature: String,
    val evs: CalcStats,
    val state: InferenceValueState,
    val confidenceLabel: String
)

data class EffectiveBattleSet(
    val speciesId: String?,
    val item: Pair<String?, InferenceValueState>,
    val ability: Pair<String?, InferenceValueState>,
    val spread: InferenceSpread?,
    val spreadLabel: String?,
    val sourceLabel: String,
    val moves: List<InferenceMoveSlot>
)

enum class DamageConfidence {
    HIGH,
    MEDIUM,
    LOW
}

data class DamageEstimate(
    val moveId: String,
    val moveName: String,
    val minDamage: Int?,
    val maxDamage: Int?,
    val minPercent: Double?,
    val maxPercent: Double?,
    val koLabel: String,
    val confidence: DamageConfidence,
    val warnings: List<String> = emptyList(),
    val supported: Boolean = true,
    val emphasized: Boolean = false,
    val critMinDamage: Int? = null,
    val critMaxDamage: Int? = null,
    val critMinPercent: Double? = null,
    val critMaxPercent: Double? = null,
    val damageRolls: List<Int> = emptyList(),
    val critDamageRolls: List<Int> = emptyList(),
    val minHits: Int = 1,
    val maxHits: Int = 1
)

data class DamageComputationResult(
    val yourMoves: List<DamageEstimate>,
    val opponentMoves: List<DamageEstimate>,
    val warnings: List<String> = emptyList()
)

data class CalcMoveRow(
    val moveName: String,
    val damageText: String,
    val koText: String,
    val emphasized: Boolean = false
)

data class CalcPreviewTab(
    val uuid: UUID,
    val label: String,
    val isSelected: Boolean,
    val isActive: Boolean,
    val isDisabled: Boolean
)

data class CalcRenderModel(
    val snapshot: CalcBattleSnapshot,
    val selectedPlayerUuid: UUID?,
    val selectedOpponentUuid: UUID?,
    val selectedPlayer: CalcPokemonSnapshot?,
    val selectedOpponent: CalcPokemonSnapshot?,
    val playerTabs: List<CalcPreviewTab>,
    val opponentTabs: List<CalcPreviewTab>,
    val matchupLabel: String,
    val switchSummaryText: String?,
    val hazardNoteText: String?,
    val isPreview: Boolean,
    val opponentSet: EffectiveBattleSet,
    val yourMoves: List<CalcMoveRow>,
    val opponentMoves: List<CalcMoveRow>,
    val statusText: String,
    val speedText: String? = null,
    val debugText: String = ""
)

private fun CalcPokemonSnapshot?.fingerprintPart(): String {
    if (this == null) return ""
    return listOf(
        uuid.toString(),
        displayName,
        speciesId.orEmpty(),
        speciesKey.orEmpty(),
        speciesLabel,
        formName.orEmpty(),
        level.toString(),
        currentHp.toString(),
        maxHp.toString(),
        exactHpValues.toString(),
        status.orEmpty(),
        itemName.orEmpty(),
        abilityName.orEmpty(),
        typeNames.joinToString("|"),
        revealedMoves.joinToString("|"),
        moveList.joinToString("|") { "${it.id}:${it.displayName}" },
        statStages.entries.sortedBy { it.key }.joinToString("|") { "${it.key}:${it.value}" }
    ).joinToString("::")
}

internal fun normalizeToken(value: String?): String {
    return value.orEmpty().lowercase()
        .replace(" ", "")
        .replace("-", "")
        .replace("_", "")
        .replace("'", "")
        .replace(".", "")
}

private fun statLookupCandidates(value: String?): Set<String> {
    val normalized = normalizeToken(value)
    if (normalized.isBlank()) return emptySet()

    return when (normalized) {
        "atk", "attack" -> setOf("atk", "attack")
        "def", "defense", "defence" -> setOf("def", "defense", "defence")
        "spa", "spatk", "specialattack" -> setOf("spa", "spatk", "specialattack")
        "spd", "spdef", "specialdefense", "specialdefence" -> setOf("spd", "spdef", "specialdefense", "specialdefence")
        "spe", "speed" -> setOf("spe", "speed")
        "acc", "accuracy" -> setOf("acc", "accuracy")
        "eva", "evasion", "evasiveness" -> setOf("eva", "evasion", "evasiveness")
        else -> setOf(normalized)
    }
}
