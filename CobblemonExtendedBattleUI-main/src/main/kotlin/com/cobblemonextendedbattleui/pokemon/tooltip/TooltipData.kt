package com.cobblemonextendedbattleui.pokemon.tooltip

import com.cobblemon.mod.common.api.pokemon.status.Status
import com.cobblemon.mod.common.api.types.ElementalType
import com.cobblemon.mod.common.api.types.tera.TeraType
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemonextendedbattleui.BattleStateTracker
import net.minecraft.util.Identifier
import java.util.UUID

/**
 * Bounds for a rendered pokeball, used for hover detection.
 */
data class PokeballBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val uuid: UUID,
    val isLeftSide: Boolean,
    val isPlayerPokemon: Boolean
)

/**
 * Move information including PP (for player's Pokemon) or estimated (for opponent).
 */
data class MoveInfo(
    val name: String,
    val currentPp: Int? = null,
    val maxPp: Int? = null,
    val estimatedRemaining: Int? = null,
    val estimatedMax: Int? = null,
    val usageCount: Int? = null
)

/**
 * Aggregated data for tooltip display.
 */
data class TooltipData(
    val uuid: UUID,
    val pokemonName: String,
    val pokemonId: Identifier?,
    val hpPercent: Float,
    val statusCondition: Status?,
    val isKO: Boolean,
    val moves: List<MoveInfo>,
    val item: BattleStateTracker.TrackedItem?,
    val statChanges: Map<BattleStateTracker.BattleStat, Int>,
    val volatileStatuses: Set<BattleStateTracker.VolatileStatusState>,
    val level: Int?,
    val speciesName: String?,
    val isPlayerPokemon: Boolean,
    val actualSpeed: Int? = null,
    val actualAttack: Int? = null,
    val actualSpecialAttack: Int? = null,
    val actualDefence: Int? = null,
    val actualSpecialDefence: Int? = null,
    val abilityName: String? = null,
    val possibleAbilities: List<String>? = null,
    val primaryType: ElementalType? = null,
    val secondaryType: ElementalType? = null,
    val teraType: TeraType? = null,
    val form: FormData? = null,
    val lostPrimaryType: Boolean = false,
    val addedTypes: List<String> = emptyList(),
    val isTerastallized: Boolean = false,
    val activeTeraTypeName: String? = null
)

/**
 * Bounds data for tooltip and panel hit detection.
 */
data class TooltipBoundsData(val x: Int, val y: Int, val width: Int, val height: Int)
