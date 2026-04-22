package com.cobblemonextendedbattleui.calc

import com.cobblemon.mod.common.api.pokemon.status.Status
import com.cobblemon.mod.common.api.pokemon.status.Statuses
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.battles.ai.strongBattleAI.AIUtility
import com.cobblemonextendedbattleui.BattleStateTracker
import com.cobblemonextendedbattleui.TeamIndicatorUI
import com.cobblemonextendedbattleui.pokemon.stats.StatCalculator
import net.minecraft.util.Identifier
import kotlin.math.floor
import kotlin.math.roundToInt

object CalcBattleAnalysisSupport {
    data class SwitchPreviewInfo(
        val effectiveCurrentHp: Int,
        val summaryText: String?,
        val hazardNoteText: String?
    )

    fun buildMatchupLabel(player: CalcPokemonSnapshot?, opponent: CalcPokemonSnapshot?): String {
        val playerName = player?.speciesLabel ?: player?.displayName ?: "Your Active"
        val opponentName = opponent?.speciesLabel ?: opponent?.displayName ?: "Opponent Active"
        return "$playerName -> $opponentName"
    }

    fun buildSpeedText(player: CalcPokemonSnapshot?, opponent: CalcPokemonSnapshot?, inferredSet: EffectiveBattleSet): String? {
        player ?: return null
        opponent ?: return null
        val playerBaseSpeed = player.actualStats?.spe ?: return null
        val playerEffective = TeamIndicatorUI.calculateEffectiveSpeed(
            playerBaseSpeed,
            player.stageFor("spe"),
            player.abilityName,
            resolveStatus(player.status),
            player.itemName,
            false
        )

        val opponentRange = resolveOpponentSpeedRange(opponent)
        val guessedOpponentSpeed = resolveGuessedOpponentSpeed(opponent, inferredSet)

        return when {
            opponentRange != null && playerEffective > opponentRange.maxSpeed -> "Speed: You move first"
            opponentRange != null && playerEffective < opponentRange.minSpeed -> "Speed: Opponent moves first"
            guessedOpponentSpeed != null && playerEffective > guessedOpponentSpeed -> "Speed: You likely move first"
            guessedOpponentSpeed != null && playerEffective < guessedOpponentSpeed -> "Speed: Opponent likely moves first"
            guessedOpponentSpeed != null -> "Speed: Likely speed tie"
            opponentRange != null -> "Speed: Speed tie window"
            else -> null
        }?.let { "$it ($playerEffective vs ${formatOpponentSpeed(opponentRange, guessedOpponentSpeed)})" }
    }

    fun buildSwitchPreview(player: CalcPokemonSnapshot?, snapshot: CalcBattleSnapshot): SwitchPreviewInfo {
        player ?: return SwitchPreviewInfo(0, null, null)
        if (player.uuid == snapshot.playerActiveUuid) {
            return SwitchPreviewInfo(
                effectiveCurrentHp = player.currentHp,
                summaryText = "On field HP: ${player.currentHp}/${player.maxHp} (${formatPercent(player.currentHp * 100.0 / player.maxHp.coerceAtLeast(1))}%)",
                hazardNoteText = null
            )
        }

        val notes = mutableListOf<String>()
        var currentHp = player.currentHp
        val maxHp = player.maxHp.coerceAtLeast(1)

        if (snapshot.opponentSide.hasCondition("Stealth Rock")) {
            val rockDamage = floor(maxHp * stealthRockFraction(player)).toInt().coerceAtLeast(1)
            currentHp = (currentHp - rockDamage).coerceAtLeast(0)
            notes += "Stealth Rock -${formatPercent(rockDamage * 100.0 / maxHp)}%"
        }

        val spikesLayers = snapshot.opponentSide.layersFor("Spikes")
        if (spikesLayers > 0 && isGrounded(player)) {
            val spikesFraction = when (spikesLayers.coerceAtMost(3)) {
                1 -> 1.0 / 8.0
                2 -> 1.0 / 6.0
                else -> 1.0 / 4.0
            }
            val spikesDamage = floor(maxHp * spikesFraction).toInt().coerceAtLeast(1)
            currentHp = (currentHp - spikesDamage).coerceAtLeast(0)
            notes += "Spikes x$spikesLayers -${formatPercent(spikesDamage * 100.0 / maxHp)}%"
        }

        if (snapshot.opponentSide.layersFor("Toxic Spikes") > 0 && isGrounded(player) && !isToxicSpikesImmune(player)) {
            notes += "Toxic Spikes on entry"
        }
        if (snapshot.opponentSide.hasCondition("Sticky Web") && isGrounded(player)) {
            notes += "Sticky Web speed drop"
        }

        val noteText = notes.takeIf { it.isNotEmpty() }?.joinToString(" | ")
        return SwitchPreviewInfo(
            effectiveCurrentHp = currentHp,
            summaryText = "Switch-in HP: $currentHp/$maxHp (${formatPercent(currentHp * 100.0 / maxHp)}%)",
            hazardNoteText = noteText
        )
    }

    fun formatPercent(value: Double): String {
        val roundedTenth = (value * 10.0).roundToInt() / 10.0
        return if (roundedTenth == roundedTenth.toInt().toDouble()) {
            roundedTenth.toInt().toString()
        } else {
            roundedTenth.toString()
        }
    }

    private fun stealthRockFraction(player: CalcPokemonSnapshot): Double {
        val rockType = ElementalTypes.get("rock") ?: return 0.125
        val effectiveness = player.typeNames
            .mapNotNull { ElementalTypes.get(it.lowercase()) }
            .fold(1.0) { acc, defendingType -> acc * AIUtility.getDamageMultiplier(rockType, defendingType) }
        return 0.125 * effectiveness
    }

    private fun isGrounded(player: CalcPokemonSnapshot): Boolean {
        return !player.hasType("flying") && normalizeToken(player.abilityName) != "levitate"
    }

    private fun isToxicSpikesImmune(player: CalcPokemonSnapshot): Boolean {
        return player.hasType("poison") || player.hasType("steel") || !isGrounded(player)
    }

    private fun resolveOpponentSpeedRange(opponent: CalcPokemonSnapshot): TeamIndicatorUI.SpeedRangeResult? {
        val speciesId = resolveSpeciesIdentifier(opponent.speciesId ?: opponent.speciesKey) ?: return null
        val trackedItem = opponent.itemName?.let {
            BattleStateTracker.TrackedItem(it, BattleStateTracker.ItemStatus.HELD, 0)
        }
        return TeamIndicatorUI.calculateOpponentSpeedRange(
            opponent.uuid,
            speciesId,
            opponent.level,
            opponent.stageFor("spe"),
            resolveStatus(opponent.status),
            trackedItem,
            null
        )
    }

    private fun resolveGuessedOpponentSpeed(opponent: CalcPokemonSnapshot, inferredSet: EffectiveBattleSet): Int? {
        val spread = inferredSet.spread ?: return null
        val baseSpeed = opponent.baseStats?.spe ?: return null
        val derivedSpeed = StatCalculator.calculateStat(
            baseSpeed,
            opponent.level,
            31,
            spread.evs.spe,
            natureSpeedModifier(spread.nature)
        )
        val effectiveAbility = opponent.abilityName ?: inferredSet.ability.first
        val effectiveItem = opponent.itemName ?: inferredSet.item.first
        return TeamIndicatorUI.calculateEffectiveSpeed(
            derivedSpeed,
            opponent.stageFor("spe"),
            effectiveAbility,
            resolveStatus(opponent.status),
            effectiveItem,
            false
        )
    }

    private fun formatOpponentSpeed(
        range: TeamIndicatorUI.SpeedRangeResult?,
        guessedSpeed: Int?
    ): String {
        return when {
            range != null -> "${range.minSpeed}-${range.maxSpeed}"
            guessedSpeed != null -> guessedSpeed.toString()
            else -> "?"
        }
    }

    private fun resolveSpeciesIdentifier(speciesId: String?): Identifier? {
        if (speciesId.isNullOrBlank()) return null
        return Identifier.tryParse(speciesId) ?: Identifier.of("cobblemon", speciesId)
    }

    private fun resolveStatus(statusId: String?): Status? {
        return when (statusId?.lowercase()) {
            "poison" -> Statuses.POISON
            "poisonbadly", "badpoison", "toxic" -> Statuses.POISON_BADLY
            "burn" -> Statuses.BURN
            "paralysis", "paralyzed", "par" -> Statuses.PARALYSIS
            "frozen", "freeze" -> Statuses.FROZEN
            "sleep", "asleep" -> Statuses.SLEEP
            else -> null
        }
    }

    private fun natureSpeedModifier(nature: String): Double {
        return when (nature.lowercase()) {
            "timid", "hasty", "jolly", "naive" -> 1.1
            "brave", "relaxed", "quiet", "sassy" -> 0.9
            else -> 1.0
        }
    }
}
