package com.cobblemonextendedbattleui.pokemon.stats

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import net.minecraft.util.Identifier

/**
 * Pure stat calculation utilities shared by speed calculator, tooltip builder,
 * and stat display. No mutable state.
 */
object StatCalculator {

    /**
     * Get the stat stage multiplier for a given stage (-6 to +6).
     * Uses the standard Pokemon formula: (2 + stage) / 2 for positive, 2 / (2 - stage) for negative.
     */
    fun getStageMultiplier(stage: Int): Double {
        return if (stage >= 0) {
            (2.0 + stage) / 2.0
        } else {
            2.0 / (2.0 - stage)
        }
    }

    /**
     * Calculate a stat value using the standard Pokemon formula.
     * stat = floor((floor((2 * base + iv + floor(ev/4)) * level / 100) + 5) * nature)
     */
    fun calculateStat(base: Int, level: Int, iv: Int, ev: Int, natureMod: Double): Int {
        val inner = ((2 * base + iv + ev / 4) * level / 100) + 5
        return (inner * natureMod).toInt()
    }

    /**
     * Apply a stat stage multiplier to a base value.
     */
    fun applyStageMultiplier(baseValue: Int, stage: Int): Int {
        return (baseValue * getStageMultiplier(stage)).toInt()
    }

    /**
     * Normalize ability name for comparison (lowercase, no spaces/underscores).
     */
    fun normalizeAbilityName(name: String?): String? =
        name?.lowercase()?.replace(" ", "")?.replace("_", "")

    /**
     * Check if a Pokemon can still evolve (for Eviolite eligibility).
     */
    fun canPokemonEvolve(pokemonId: Identifier?): Boolean {
        if (pokemonId == null) return false
        val species = PokemonSpecies.getByIdentifier(pokemonId) ?: return false
        return species.evolutions.isNotEmpty()
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Item-based Stat Multipliers
    // ═════════════════════════════════════════════════════════════════════════

    fun getItemSpeedMultiplier(itemName: String?): Double {
        if (itemName == null) return 1.0
        val normalizedItem = itemName.lowercase().replace(" ", "").replace("_", "")
        return when (normalizedItem) {
            "choicescarf" -> 1.5
            "ironball" -> 0.5
            else -> 1.0
        }
    }

    fun getItemAttackMultiplier(itemName: String?, speciesName: String?): Double {
        if (itemName == null) return 1.0
        val normalizedItem = itemName.lowercase().replace(" ", "").replace("_", "")
        val species = speciesName?.lowercase()?.replace(" ", "")?.replace("-", "") ?: ""
        return when {
            normalizedItem == "choiceband" -> 1.5
            normalizedItem == "thickclub" && species in listOf("cubone", "marowak", "marowakalola") -> 2.0
            normalizedItem == "lightball" && species.startsWith("pikachu") -> 2.0
            else -> 1.0
        }
    }

    fun getItemSpecialAttackMultiplier(itemName: String?, speciesName: String?): Double {
        if (itemName == null) return 1.0
        val normalizedItem = itemName.lowercase().replace(" ", "").replace("_", "")
        val species = speciesName?.lowercase()?.replace(" ", "")?.replace("-", "") ?: ""
        return when {
            normalizedItem == "choicespecs" -> 1.5
            normalizedItem == "lightball" && species.startsWith("pikachu") -> 2.0
            normalizedItem == "deepseatooth" && species == "clamperl" -> 2.0
            else -> 1.0
        }
    }

    fun getItemDefenseMultiplier(itemName: String?, canEvolve: Boolean): Double {
        if (itemName == null) return 1.0
        val normalizedItem = itemName.lowercase().replace(" ", "").replace("_", "")
        return when {
            normalizedItem == "eviolite" && canEvolve -> 1.5
            else -> 1.0
        }
    }

    fun getItemSpecialDefenseMultiplier(itemName: String?, speciesName: String?, canEvolve: Boolean): Double {
        if (itemName == null) return 1.0
        val normalizedItem = itemName.lowercase().replace(" ", "").replace("_", "")
        val species = speciesName?.lowercase()?.replace(" ", "")?.replace("-", "") ?: ""
        return when {
            normalizedItem == "assaultvest" -> 1.5
            normalizedItem == "eviolite" && canEvolve -> 1.5
            normalizedItem == "deepseascale" && species == "clamperl" -> 2.0
            else -> 1.0
        }
    }
}
