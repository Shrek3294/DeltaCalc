package com.cobblemonextendedbattleui.battle.state

import com.cobblemonextendedbattleui.BattleStateTracker.BattleStat
import com.cobblemonextendedbattleui.CobblemonExtendedBattleUI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks stat stage changes (-6 to +6) for each Pokemon in battle.
 * Handles Haze, Topsy-Turvy, stat swaps, Psych Up, and Spectral Thief.
 */
object StatTracker {

    private val statChanges = ConcurrentHashMap<UUID, ConcurrentHashMap<BattleStat, Int>>()

    fun clear() {
        statChanges.clear()
    }

    fun applyStatChange(pokemonName: String, stat: BattleStat, stages: Int, preferAlly: Boolean? = null) {
        val uuid = PokemonRegistry.resolvePokemonUuid(pokemonName, preferAlly) ?: run {
            CobblemonExtendedBattleUI.LOGGER.debug("StatTracker: Unknown Pokemon '$pokemonName' for stat change")
            return
        }

        val pokemonStats = statChanges.computeIfAbsent(uuid) { ConcurrentHashMap() }
        val currentStage = pokemonStats[stat] ?: 0
        val newStage = (currentStage + stages).coerceIn(-6, 6)
        pokemonStats[stat] = newStage

        CobblemonExtendedBattleUI.LOGGER.debug("StatTracker: $pokemonName ${stat.abbr} $currentStage -> $newStage")
    }

    fun setStatStage(pokemonName: String, stat: BattleStat, stage: Int, preferAlly: Boolean? = null) {
        val uuid = PokemonRegistry.resolvePokemonUuid(pokemonName, preferAlly) ?: return
        val pokemonStats = statChanges.computeIfAbsent(uuid) { ConcurrentHashMap() }
        pokemonStats[stat] = stage.coerceIn(-6, 6)
        CobblemonExtendedBattleUI.LOGGER.debug("StatTracker: $pokemonName ${stat.abbr} set to $stage")
    }

    fun clearPokemonStats(uuid: UUID) {
        statChanges[uuid]?.clear()
        CobblemonExtendedBattleUI.LOGGER.debug("StatTracker: Cleared stats for UUID $uuid")
    }

    fun clearPokemonStatsByName(pokemonName: String, preferAlly: Boolean? = null) {
        val uuid = PokemonRegistry.resolvePokemonUuid(pokemonName, preferAlly) ?: return
        clearPokemonStats(uuid)
    }

    fun clearAllStatsForAll() {
        statChanges.values.forEach { it.clear() }
        CobblemonExtendedBattleUI.LOGGER.debug("StatTracker: Cleared all stats for all Pokemon (Haze)")
    }

    fun invertStats(pokemonName: String, preferAlly: Boolean? = null) {
        val uuid = PokemonRegistry.resolvePokemonUuid(pokemonName, preferAlly) ?: return
        statChanges[uuid]?.let { stats ->
            stats.entries.forEach { (stat, value) ->
                stats[stat] = -value
            }
        }
        CobblemonExtendedBattleUI.LOGGER.debug("StatTracker: Inverted stats for $pokemonName")
    }

    fun copyStats(sourceName: String, targetName: String, sourceIsAlly: Boolean? = null, targetIsAlly: Boolean? = null) {
        val sourceUuid = PokemonRegistry.resolvePokemonUuid(sourceName, sourceIsAlly) ?: return
        val targetUuid = PokemonRegistry.resolvePokemonUuid(targetName, targetIsAlly) ?: return
        val sourceStats = statChanges[sourceUuid] ?: return

        val targetStats = statChanges.computeIfAbsent(targetUuid) { ConcurrentHashMap() }
        targetStats.clear()
        targetStats.putAll(sourceStats)
        CobblemonExtendedBattleUI.LOGGER.debug("StatTracker: $targetName copied stats from $sourceName")
    }

    fun swapStats(pokemon1Name: String, pokemon2Name: String, pokemon1IsAlly: Boolean? = null, pokemon2IsAlly: Boolean? = null) {
        val uuid1 = PokemonRegistry.resolvePokemonUuid(pokemon1Name, pokemon1IsAlly) ?: return
        val uuid2 = PokemonRegistry.resolvePokemonUuid(pokemon2Name, pokemon2IsAlly) ?: return

        val stats1 = statChanges[uuid1]?.toMap() ?: emptyMap()
        val stats2 = statChanges[uuid2]?.toMap() ?: emptyMap()

        val pokemonStats1 = statChanges.computeIfAbsent(uuid1) { ConcurrentHashMap() }
        val pokemonStats2 = statChanges.computeIfAbsent(uuid2) { ConcurrentHashMap() }

        pokemonStats1.clear()
        pokemonStats1.putAll(stats2)
        pokemonStats2.clear()
        pokemonStats2.putAll(stats1)

        CobblemonExtendedBattleUI.LOGGER.debug("StatTracker: Swapped stats between $pokemon1Name and $pokemon2Name")
    }

    fun swapSpecificStats(
        pokemon1Name: String, pokemon2Name: String, statsToSwap: List<BattleStat>,
        pokemon1IsAlly: Boolean? = null, pokemon2IsAlly: Boolean? = null
    ) {
        val uuid1 = PokemonRegistry.resolvePokemonUuid(pokemon1Name, pokemon1IsAlly) ?: return
        val uuid2 = PokemonRegistry.resolvePokemonUuid(pokemon2Name, pokemon2IsAlly) ?: return

        val pokemonStats1 = statChanges.computeIfAbsent(uuid1) { ConcurrentHashMap() }
        val pokemonStats2 = statChanges.computeIfAbsent(uuid2) { ConcurrentHashMap() }

        for (stat in statsToSwap) {
            val val1 = pokemonStats1[stat] ?: 0
            val val2 = pokemonStats2[stat] ?: 0
            pokemonStats1[stat] = val2
            pokemonStats2[stat] = val1
        }

        CobblemonExtendedBattleUI.LOGGER.debug("StatTracker: Swapped ${statsToSwap.map { it.abbr }} between $pokemon1Name and $pokemon2Name")
    }

    fun stealPositiveStats(
        userPokemonName: String, targetPokemonName: String,
        userIsAlly: Boolean? = null, targetIsAlly: Boolean? = null
    ) {
        val userUuid = PokemonRegistry.resolvePokemonUuid(userPokemonName, userIsAlly) ?: run {
            CobblemonExtendedBattleUI.LOGGER.debug("StatTracker: Unknown user '$userPokemonName' for Spectral Thief")
            return
        }
        val targetUuid = PokemonRegistry.resolvePokemonUuid(targetPokemonName, targetIsAlly) ?: run {
            CobblemonExtendedBattleUI.LOGGER.debug("StatTracker: Unknown target '$targetPokemonName' for Spectral Thief")
            return
        }

        val targetStats = statChanges[targetUuid] ?: return
        val userStats = statChanges.computeIfAbsent(userUuid) { ConcurrentHashMap() }

        var stolenCount = 0
        for (stat in BattleStat.entries) {
            val targetValue = targetStats[stat] ?: 0
            if (targetValue > 0) {
                val userValue = userStats[stat] ?: 0
                userStats[stat] = (userValue + targetValue).coerceIn(-6, 6)
                targetStats[stat] = 0
                stolenCount++
            }
        }

        if (stolenCount > 0) {
            CobblemonExtendedBattleUI.LOGGER.debug(
                "StatTracker: $userPokemonName stole $stolenCount stat boosts from $targetPokemonName via Spectral Thief"
            )
        }
    }

    fun getStatChanges(uuid: UUID): Map<BattleStat, Int> {
        return statChanges[uuid]?.filter { it.value != 0 } ?: emptyMap()
    }

    fun getStatFromName(name: String): BattleStat? {
        return when (name.lowercase()) {
            "atk", "attack" -> BattleStat.ATTACK
            "def", "defense" -> BattleStat.DEFENSE
            "spa", "specialattack", "spattack", "spatk" -> BattleStat.SPECIAL_ATTACK
            "spd", "specialdefense", "spdefense", "spdef" -> BattleStat.SPECIAL_DEFENSE
            "spe", "speed" -> BattleStat.SPEED
            "accuracy", "acc" -> BattleStat.ACCURACY
            "evasion", "evasiveness", "eva" -> BattleStat.EVASION
            else -> null
        }
    }

    /** Get raw stat map for Baton Pass transfer. */
    fun getRawStats(uuid: UUID): Map<BattleStat, Int>? =
        statChanges[uuid]?.filter { it.value != 0 }?.toMap()

    /** Apply stats from Baton Pass. */
    fun applyBatonPassStats(uuid: UUID, stats: Map<BattleStat, Int>) {
        val pokemonStats = statChanges.computeIfAbsent(uuid) { ConcurrentHashMap() }
        pokemonStats.putAll(stats)
    }
}
