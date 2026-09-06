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
    fun fingerprint(): String {
        val sb = StringBuilder()
        sb.append("14:[")
        sb.append(CalcSnapshotEncoder.encodeToken(battleId.toString()))
        sb.append(CalcSnapshotEncoder.encodeToken(turn.toString()))
        sb.append(CalcSnapshotEncoder.encodeToken(weather))
        sb.append(CalcSnapshotEncoder.encodeToken(terrain))
        sb.append(CalcSnapshotEncoder.encodeMap(playerSide.sideConditions))
        sb.append(CalcSnapshotEncoder.encodeMap(opponentSide.sideConditions))
        sb.append(CalcSnapshotEncoder.encodeToken(playerActiveUuid?.toString()))
        sb.append(CalcSnapshotEncoder.encodeToken(opponentActiveUuid?.toString()))
        sb.append(CalcSnapshotEncoder.encodePokemonList(playerTeam))
        sb.append(CalcSnapshotEncoder.encodePokemonList(opponentTeam))
        sb.append(CalcSnapshotEncoder.encodeToken(playerActive.fingerprintPart()))
        sb.append(CalcSnapshotEncoder.encodeToken(opponentActive.fingerprintPart()))
        sb.append(CalcSnapshotEncoder.encodeToken(selectedMoveName))
        sb.append(CalcSnapshotEncoder.encodeList(compatNotes))
        sb.append("]")
        return sb.toString()
    }

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
    val confidenceLabel: String,
    val usagePercent: Double? = null
)

data class EffectiveBattleSet(
    val speciesId: String?,
    val item: Pair<String?, InferenceValueState>,
    val ability: Pair<String?, InferenceValueState>,
    val spread: InferenceSpread?,
    val spreadLabel: String?,
    val sourceLabel: String,
    val moves: List<InferenceMoveSlot>,
    // Cyclable alternatives drawn from usage stats, top-N deduped.
    // Index 0 is the inferred default; cycling wraps back to no-override.
    val itemAlternatives: List<String> = emptyList(),
    val abilityAlternatives: List<String> = emptyList(),
    val spreadAlternatives: List<InferenceSpread> = emptyList(),
    // Usage % keyed by normalized name. Empty/missing for entries that come
    // from curated fallbacks rather than usage data.
    val itemUsagePercent: Map<String, Double> = emptyMap(),
    val abilityUsagePercent: Map<String, Double> = emptyMap()
)

enum class CalcMoveOutcome {
    GUARANTEED_OHKO,
    LIKELY_OHKO,
    TWO_HKO,
    THREE_HKO,
    FOUR_HKO_PLUS,
    KO,
    IMMUNE,
    STATUS,
    UNSUPPORTED;

    val isOhko: Boolean get() = this == GUARANTEED_OHKO || this == LIKELY_OHKO
    val isGuaranteedOhko: Boolean get() = this == GUARANTEED_OHKO
    val isLikelyOhko: Boolean get() = this == LIKELY_OHKO
}

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
    val maxHits: Int = 1,
    val outcome: CalcMoveOutcome
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
    val outcome: CalcMoveOutcome,
    val emphasized: Boolean = false,
    val minPercent: Double? = null,
    val maxPercent: Double? = null,
    val confidence: DamageConfidence = DamageConfidence.HIGH,
    val warnings: List<String> = emptyList(),
    val supported: Boolean = true
) {
    val isStatus: Boolean get() = outcome == CalcMoveOutcome.STATUS
}

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
    val debugText: String = "",
    val warningTexts: List<String> = emptyList()
)

internal fun CalcPokemonSnapshot?.fingerprintPart(): String {
    if (this == null) return "-1:"
    val sb = StringBuilder()
    sb.append("22:[")
    sb.append(CalcSnapshotEncoder.encodeToken(uuid.toString()))
    sb.append(CalcSnapshotEncoder.encodeToken(displayName))
    sb.append(CalcSnapshotEncoder.encodeToken(speciesId))
    sb.append(CalcSnapshotEncoder.encodeToken(speciesKey))
    sb.append(CalcSnapshotEncoder.encodeToken(speciesLabel))
    sb.append(CalcSnapshotEncoder.encodeToken(formName))
    sb.append(CalcSnapshotEncoder.encodeToken(level.toString()))
    sb.append(CalcSnapshotEncoder.encodeToken(currentHp.toString()))
    sb.append(CalcSnapshotEncoder.encodeToken(maxHp.toString()))
    sb.append(CalcSnapshotEncoder.encodeToken(exactHpValues.toString()))
    sb.append(CalcSnapshotEncoder.encodeToken(status))
    sb.append(CalcSnapshotEncoder.encodeToken(itemName))
    sb.append(CalcSnapshotEncoder.encodeToken(abilityName))
    sb.append(CalcSnapshotEncoder.encodeList(typeNames))
    sb.append(CalcSnapshotEncoder.encodeList(revealedMoves))
    sb.append(CalcSnapshotEncoder.encodeMoveRefs(moveList))
    sb.append(CalcSnapshotEncoder.encodeMap(statStages))
    sb.append(CalcSnapshotEncoder.encodeToken(teraType))
    sb.append(CalcSnapshotEncoder.encodeStats(baseStats))
    sb.append(CalcSnapshotEncoder.encodeStats(actualStats))
    sb.append(CalcSnapshotEncoder.encodeToken(canEvolve.toString()))
    sb.append(CalcSnapshotEncoder.encodeDouble(weightKg))
    sb.append("]")
    return sb.toString()
}

internal object CalcSnapshotEncoder {
    fun encodeToken(token: String?): String {
        return if (token == null) "-1:" else "${token.length}:$token"
    }

    fun encodeList(items: List<String>): String {
        val sb = StringBuilder()
        sb.append(items.size).append(":[")
        for (item in items) {
            sb.append(encodeToken(item))
        }
        sb.append("]")
        return sb.toString()
    }

    fun encodeMap(map: Map<String, Int>): String {
        val sorted = map.entries.sortedBy { it.key }
        val sb = StringBuilder()
        sb.append(sorted.size).append(":[")
        for (entry in sorted) {
            sb.append(encodeToken(entry.key))
            sb.append(encodeToken(entry.value.toString()))
        }
        sb.append("]")
        return sb.toString()
    }

    fun encodeMoveRefs(moves: List<CalcMoveRef>): String {
        val sb = StringBuilder()
        sb.append(moves.size).append(":[")
        for (m in moves) {
            sb.append(encodeToken(m.id))
            sb.append(encodeToken(m.displayName))
        }
        sb.append("]")
        return sb.toString()
    }

    fun encodeStats(stats: CalcStats?): String {
        if (stats == null) return encodeToken(null)
        val sb = StringBuilder()
        sb.append("6:[")
        sb.append(encodeToken(stats.hp.toString()))
        sb.append(encodeToken(stats.atk.toString()))
        sb.append(encodeToken(stats.def.toString()))
        sb.append(encodeToken(stats.spa.toString()))
        sb.append(encodeToken(stats.spd.toString()))
        sb.append(encodeToken(stats.spe.toString()))
        sb.append("]")
        return sb.toString()
    }

    fun encodeDouble(value: Double?): String {
        return if (value == null) encodeToken(null) else encodeToken(value.toBits().toString())
    }

    fun encodePokemonList(pokemon: List<CalcPokemonSnapshot>): String {
        val sb = StringBuilder()
        sb.append(pokemon.size).append(":[")
        for (p in pokemon) {
            sb.append(encodeToken(p.fingerprintPart()))
        }
        sb.append("]")
        return sb.toString()
    }
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
