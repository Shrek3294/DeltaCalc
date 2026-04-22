package com.cobblemonextendedbattleui.calc

import com.cobblemonextendedbattleui.BattleMoveSupport

class OpponentInferenceService {
    fun infer(snapshot: CalcBattleSnapshot, opponent: CalcPokemonSnapshot?, battleDatabase: BattleDatabase): EffectiveBattleSet {
        val species = battleDatabase.findSpecies(opponent?.speciesKey, opponent?.speciesId, opponent?.speciesLabel, opponent?.displayName)
        val usage = battleDatabase.findDefaultSet(species, opponent?.speciesKey, opponent?.speciesId, opponent?.speciesLabel, opponent?.displayName)
        if (opponent == null || usage == null) {
            return EffectiveBattleSet(
                speciesId = species?.speciesKey ?: opponent?.speciesKey ?: opponent?.speciesId,
                item = null to InferenceValueState.UNKNOWN,
                ability = null to InferenceValueState.UNKNOWN,
                spread = null,
                spreadLabel = null,
                sourceLabel = "No Delta ranked or Smogon fallback",
                moves = opponent?.revealedMoves
                    ?.distinctBy(::normalizeToken)
                    ?.map { InferenceMoveSlot(it, InferenceValueState.REVEALED, "High") }
                    ?: emptyList()
            )
        }

        val topSpread = usage.spreads.firstOrNull()
        val spread = topSpread?.let {
            InferenceSpread(
                nature = it.nature,
                evs = CalcStats(
                    hp = it.evs["hp"] ?: 0,
                    atk = it.evs["atk"] ?: 0,
                    def = it.evs["def"] ?: 0,
                    spa = it.evs["spa"] ?: 0,
                    spd = it.evs["spd"] ?: 0,
                    spe = it.evs["spe"] ?: 0
                ),
                state = InferenceValueState.GUESSED,
                confidenceLabel = confidenceLabel(it.usagePercent)
            )
        }

        val moves = linkedMapOf<String, RankedMoveSlot>()
        usage.moves.sortedByDescending { it.usagePercent }
            .filter { !it.displayName.equals("Other", ignoreCase = true) }
            .forEach { option ->
                val displayName = BattleMoveSupport.resolveDisplayName(option.displayName)
                val key = normalizeToken(BattleMoveSupport.resolveMoveId(option.id.ifBlank { option.displayName }))
                if (moves.size < 4 && key !in moves) {
                    moves[key] = RankedMoveSlot(
                        slot = InferenceMoveSlot(
                            moveName = displayName,
                            state = InferenceValueState.GUESSED,
                            confidenceLabel = confidenceLabel(option.usagePercent)
                        ),
                        usagePercent = option.usagePercent
                    )
                }
            }

        opponent.revealedMoves.forEach { revealed ->
            val displayName = BattleMoveSupport.resolveDisplayName(revealed)
            val normalized = normalizeToken(BattleMoveSupport.resolveMoveId(revealed))
            val existing = moves[normalized]
            if (existing != null) {
                moves[normalized] = existing.copy(
                    slot = existing.slot.copy(
                        moveName = displayName,
                        state = InferenceValueState.REVEALED,
                        confidenceLabel = "High"
                    )
                )
            } else {
                val replacement = moves.entries
                    .filter { it.value.slot.state != InferenceValueState.REVEALED }
                    .minByOrNull { it.value.usagePercent }
                    ?.key
                if (replacement != null) {
                    moves.remove(replacement)
                }
                moves[normalized] = RankedMoveSlot(
                    slot = InferenceMoveSlot(displayName, InferenceValueState.REVEALED, "High"),
                    usagePercent = -1.0
                )
            }
        }

        val orderedMoves = moves.values
            .sortedWith(
                compareByDescending<RankedMoveSlot> { it.slot.state == InferenceValueState.REVEALED }
                    .thenBy { if (it.usagePercent < 0.0) Double.MAX_VALUE else -it.usagePercent }
                    .thenBy { it.slot.moveName }
            )
            .map { it.slot }

        return EffectiveBattleSet(
            speciesId = species?.speciesKey ?: usage.speciesKey,
            item = (opponent.itemName ?: usage.items.firstOrNull()?.displayName) to if (opponent.itemName != null) InferenceValueState.REVEALED else InferenceValueState.GUESSED,
            ability = (opponent.abilityName ?: usage.abilities.firstOrNull()?.displayName) to if (opponent.abilityName != null) InferenceValueState.REVEALED else InferenceValueState.GUESSED,
            spread = spread,
            spreadLabel = spread?.let { "${it.nature} ${formatSpread(it.evs)}" },
            sourceLabel = sourceLabel(usage.source),
            moves = orderedMoves
        )
    }

    private data class RankedMoveSlot(
        val slot: InferenceMoveSlot,
        val usagePercent: Double
    )

    private fun formatSpread(evs: CalcStats): String {
        return listOf(evs.hp, evs.atk, evs.def, evs.spa, evs.spd, evs.spe).joinToString("/") { it.toString() }
    }

    private fun confidenceLabel(usagePercent: Double): String {
        return when {
            usagePercent >= 50.0 -> "High"
            usagePercent >= 15.0 -> "Medium"
            else -> "Low"
        }
    }

    private fun sourceLabel(source: String): String {
        val normalized = normalizeToken(source)
        return when {
            normalized == "deltaranked" -> "Delta ranked"
            normalized.startsWith("deltaranked1300") -> "Delta ranked (1300)"
            normalized.startsWith("deltaranked1000") -> "Delta ranked (1000)"
            normalized.startsWith("deltaranked1500") -> "Delta ranked (1500)"
            normalized == "smogonfallback" -> "Smogon fallback"
            else -> "Default set data"
        }
    }
}
