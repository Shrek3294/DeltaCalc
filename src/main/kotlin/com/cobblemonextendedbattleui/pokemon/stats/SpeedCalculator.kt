package com.cobblemonextendedbattleui.pokemon.stats

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.status.Status
import com.cobblemon.mod.common.api.pokemon.status.Statuses
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemonextendedbattleui.BattleStateTracker
import net.minecraft.util.Identifier
import java.util.UUID

/**
 * All speed calculation logic: weather/terrain/ability/item speed modifiers,
 * effective speed for player Pokemon, and opponent speed range estimation.
 */
object SpeedCalculator {

    /**
     * Result of opponent speed range calculation.
     */
    data class SpeedRangeResult(
        val minSpeed: Int,
        val maxSpeed: Int,
        val abilityNote: String? = null,
        val itemNote: String? = null
    )

    // ═════════════════════════════════════════════════════════════════════════
    // Speed Modifier Constants
    // ═════════════════════════════════════════════════════════════════════════

    val WEATHER_SPEED_ABILITIES = mapOf(
        "chlorophyll" to BattleStateTracker.Weather.SUN,
        "swiftswim" to BattleStateTracker.Weather.RAIN,
        "sandrush" to BattleStateTracker.Weather.SANDSTORM,
        "slushrush" to BattleStateTracker.Weather.SNOW
    )

    val TERRAIN_SPEED_ABILITIES = mapOf(
        "surgesurfer" to BattleStateTracker.Terrain.ELECTRIC
    )

    // ═════════════════════════════════════════════════════════════════════════
    // Speed Modifier Functions
    // ═════════════════════════════════════════════════════════════════════════

    fun getAbilitySpeedMultiplier(
        abilityName: String?,
        weather: BattleStateTracker.Weather?,
        terrain: BattleStateTracker.Terrain?,
        hasStatus: Boolean,
        itemConsumed: Boolean
    ): Double {
        val normalizedAbility = StatCalculator.normalizeAbilityName(abilityName) ?: return 1.0

        WEATHER_SPEED_ABILITIES[normalizedAbility]?.let { requiredWeather ->
            if (normalizedAbility == "slushrush") {
                if (weather == BattleStateTracker.Weather.SNOW || weather == BattleStateTracker.Weather.HAIL) {
                    return 2.0
                }
            } else if (weather == requiredWeather) {
                return 2.0
            }
        }

        TERRAIN_SPEED_ABILITIES[normalizedAbility]?.let { requiredTerrain ->
            if (terrain == requiredTerrain) return 2.0
        }

        if (normalizedAbility == "quickfeet" && hasStatus) return 1.5
        if (normalizedAbility == "unburden" && itemConsumed) return 2.0

        return 1.0
    }

    fun getStatusSpeedMultiplier(status: Status?, abilityName: String?): Double {
        if (status == Statuses.PARALYSIS) {
            if (StatCalculator.normalizeAbilityName(abilityName) == "quickfeet") return 1.0
            return 0.5
        }
        return 1.0
    }

    fun getMaxPossibleAbilitySpeedMultiplier(
        pokemonId: Identifier,
        weather: BattleStateTracker.Weather?,
        terrain: BattleStateTracker.Terrain?,
        hasStatus: Boolean,
        itemConsumed: Boolean
    ): Double {
        val species = PokemonSpecies.getByIdentifier(pokemonId) ?: return 1.0
        var maxMultiplier = 1.0

        val possibleAbilities = species.abilities.mapNotNull { StatCalculator.normalizeAbilityName(it.template.name) }

        for (ability in possibleAbilities) {
            val mult = getAbilitySpeedMultiplier(ability, weather, terrain, hasStatus, itemConsumed)
            if (mult > maxMultiplier) maxMultiplier = mult
        }

        return maxMultiplier
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Effective Speed Calculations
    // ═════════════════════════════════════════════════════════════════════════

    fun calculateEffectiveSpeed(
        baseSpeed: Int,
        speedStage: Int,
        abilityName: String?,
        status: Status?,
        itemName: String?,
        itemConsumed: Boolean
    ): Int {
        val weather = BattleStateTracker.weather?.type
        val terrain = BattleStateTracker.terrain?.type
        val hasStatus = status != null

        val stageMultiplier = StatCalculator.getStageMultiplier(speedStage)
        val abilityMultiplier = getAbilitySpeedMultiplier(abilityName, weather, terrain, hasStatus, itemConsumed)
        val statusMultiplier = getStatusSpeedMultiplier(status, abilityName)
        val itemMultiplier = if (!itemConsumed) StatCalculator.getItemSpeedMultiplier(itemName) else 1.0

        return (baseSpeed * stageMultiplier * abilityMultiplier * statusMultiplier * itemMultiplier).toInt()
    }

    fun calculateOpponentSpeedRange(
        uuid: UUID,
        pokemonId: Identifier,
        level: Int,
        speedStage: Int,
        status: Status?,
        knownItem: BattleStateTracker.TrackedItem?,
        form: FormData?,
        formatAbilityName: (String) -> String
    ): SpeedRangeResult? {
        val species = PokemonSpecies.getByIdentifier(pokemonId) ?: return null
        val baseSpeed = if (form != null) form.baseStats[Stats.SPEED] ?: return null
        else species.baseStats[Stats.SPEED] ?: return null

        val weather = BattleStateTracker.weather?.type
        val terrain = BattleStateTracker.terrain?.type
        val hasStatus = status != null
        val itemConsumed = knownItem?.status != BattleStateTracker.ItemStatus.HELD

        val minBaseStat = StatCalculator.calculateStat(baseSpeed, level, 0, 0, 0.9)
        val maxBaseStat = StatCalculator.calculateStat(baseSpeed, level, 31, 252, 1.1)

        val stageMultiplier = StatCalculator.getStageMultiplier(speedStage)
        val statusMultiplier = getStatusSpeedMultiplier(status, null)

        val itemName = if (!itemConsumed) knownItem?.name else null
        val itemMultiplier = if (itemName != null) StatCalculator.getItemSpeedMultiplier(itemName) else 1.0

        val minSpeed = (minBaseStat * stageMultiplier * statusMultiplier * itemMultiplier).toInt()

        val revealedAbility = BattleStateTracker.getRevealedAbility(uuid)

        val maxAbilityMultiplier = if (revealedAbility != null) {
            getAbilitySpeedMultiplier(StatCalculator.normalizeAbilityName(revealedAbility), weather, terrain, hasStatus, itemConsumed)
        } else {
            getMaxPossibleAbilitySpeedMultiplier(pokemonId, weather, terrain, hasStatus, itemConsumed)
        }

        val maxStatusMultiplier = if (status == Statuses.PARALYSIS) {
            if (revealedAbility != null) {
                if (StatCalculator.normalizeAbilityName(revealedAbility) == "quickfeet") 1.0 else statusMultiplier
            } else {
                val possibleAbilities = species.abilities.mapNotNull { StatCalculator.normalizeAbilityName(it.template.name) }
                if ("quickfeet" in possibleAbilities) 1.0 else statusMultiplier
            }
        } else {
            1.0
        }

        val maxSpeed =
            (maxBaseStat * stageMultiplier * maxAbilityMultiplier.coerceAtLeast(1.0) * maxStatusMultiplier * itemMultiplier).toInt()

        val abilityNote = if (revealedAbility != null) {
            if (maxAbilityMultiplier > 1.0) {
                formatAbilityName(revealedAbility)
            } else {
                null
            }
        } else {
            when {
                maxAbilityMultiplier > 1.0 -> {
                    val activeConditions = mutableListOf<String>()
                    val normalizedAbilities = species.abilities.mapNotNull { StatCalculator.normalizeAbilityName(it.template.name) }
                    if (weather == BattleStateTracker.Weather.SUN && "chlorophyll" in normalizedAbilities) {
                        activeConditions.add("Chlorophyll")
                    }
                    if (weather == BattleStateTracker.Weather.RAIN && "swiftswim" in normalizedAbilities) {
                        activeConditions.add("Swift Swim")
                    }
                    if (weather == BattleStateTracker.Weather.SANDSTORM && "sandrush" in normalizedAbilities) {
                        activeConditions.add("Sand Rush")
                    }
                    if ((weather == BattleStateTracker.Weather.SNOW || weather == BattleStateTracker.Weather.HAIL) && "slushrush" in normalizedAbilities) {
                        activeConditions.add("Slush Rush")
                    }
                    if (terrain == BattleStateTracker.Terrain.ELECTRIC && "surgesurfer" in normalizedAbilities) {
                        activeConditions.add("Surge Surfer")
                    }
                    if (activeConditions.isNotEmpty()) activeConditions.joinToString("/") + "?" else null
                }
                else -> null
            }
        }

        val itemNote = if (itemMultiplier != 1.0 && itemName != null) itemName else null

        return SpeedRangeResult(minSpeed, maxSpeed, abilityNote, itemNote)
    }
}
