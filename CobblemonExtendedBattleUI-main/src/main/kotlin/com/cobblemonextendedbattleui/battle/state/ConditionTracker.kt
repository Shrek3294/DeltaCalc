package com.cobblemonextendedbattleui.battle.state

import com.cobblemonextendedbattleui.BattleStateTracker.FieldCondition
import com.cobblemonextendedbattleui.BattleStateTracker.FieldConditionState
import com.cobblemonextendedbattleui.BattleStateTracker.SideCondition
import com.cobblemonextendedbattleui.BattleStateTracker.SideConditionState
import com.cobblemonextendedbattleui.BattleStateTracker.Terrain
import com.cobblemonextendedbattleui.BattleStateTracker.TerrainState
import com.cobblemonextendedbattleui.BattleStateTracker.Weather
import com.cobblemonextendedbattleui.BattleStateTracker.WeatherState
import com.cobblemonextendedbattleui.CobblemonExtendedBattleUI
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks weather, terrain, field conditions, and side conditions.
 * Handles turn duration, Court Change swaps, and extension detection.
 */
object ConditionTracker {

    var weather: WeatherState? = null
        private set

    var terrain: TerrainState? = null
        private set

    private val fieldConditions = ConcurrentHashMap<FieldCondition, FieldConditionState>()
    private val playerSideConditions = ConcurrentHashMap<SideCondition, SideConditionState>()
    private val opponentSideConditions = ConcurrentHashMap<SideCondition, SideConditionState>()

    // Terastallization tracking
    private val terastallizedPokemon = ConcurrentHashMap<java.util.UUID, String>()

    fun clear() {
        weather = null
        terrain = null
        fieldConditions.clear()
        playerSideConditions.clear()
        opponentSideConditions.clear()
        terastallizedPokemon.clear()
    }

    // ── Weather ──────────────────────────────────────────────────────────────

    fun setWeather(type: Weather, currentTurn: Int) {
        val effectiveStartTurn = maxOf(1, currentTurn)
        weather = WeatherState(type, effectiveStartTurn)
        CobblemonExtendedBattleUI.LOGGER.debug("ConditionTracker: Weather set to ${type.displayName} on turn $effectiveStartTurn")
    }

    fun clearWeather(currentTurn: Int) {
        val prev = weather
        weather = null
        if (prev != null) {
            CobblemonExtendedBattleUI.LOGGER.debug("ConditionTracker: Weather ${prev.type.displayName} ended on turn $currentTurn")
        }
    }

    fun getWeatherTurnsRemaining(currentTurn: Int): String? {
        val w = weather ?: return null
        val turnsElapsed = currentTurn - w.startTurn
        val minRemaining = 5 - turnsElapsed
        val maxRemaining = 8 - turnsElapsed

        return when {
            w.confirmedExtended -> maxRemaining.coerceAtLeast(1).toString()
            minRemaining <= 0 -> {
                w.confirmedExtended = true
                maxRemaining.coerceAtLeast(1).toString()
            }
            minRemaining == maxRemaining -> minRemaining.toString()
            else -> "$minRemaining-$maxRemaining"
        }
    }

    // ── Terrain ──────────────────────────────────────────────────────────────

    fun setTerrain(type: Terrain, currentTurn: Int) {
        val effectiveStartTurn = maxOf(1, currentTurn)
        terrain = TerrainState(type, effectiveStartTurn)
        CobblemonExtendedBattleUI.LOGGER.debug("ConditionTracker: Terrain set to ${type.displayName} on turn $effectiveStartTurn")
    }

    fun clearTerrain(currentTurn: Int) {
        val prev = terrain
        terrain = null
        if (prev != null) {
            CobblemonExtendedBattleUI.LOGGER.debug("ConditionTracker: Terrain ${prev.type.displayName} ended on turn $currentTurn")
        }
    }

    fun getTerrainTurnsRemaining(currentTurn: Int): String? {
        val t = terrain ?: return null
        val turnsElapsed = currentTurn - t.startTurn
        val minRemaining = 5 - turnsElapsed
        val maxRemaining = 8 - turnsElapsed

        return when {
            t.confirmedExtended -> maxRemaining.coerceAtLeast(1).toString()
            minRemaining <= 0 -> {
                t.confirmedExtended = true
                maxRemaining.coerceAtLeast(1).toString()
            }
            minRemaining == maxRemaining -> minRemaining.toString()
            else -> "$minRemaining-$maxRemaining"
        }
    }

    // ── Field Conditions ─────────────────────────────────────────────────────

    fun setFieldCondition(type: FieldCondition, currentTurn: Int) {
        val effectiveStartTurn = maxOf(1, currentTurn)
        fieldConditions[type] = FieldConditionState(type, effectiveStartTurn)
        CobblemonExtendedBattleUI.LOGGER.debug("ConditionTracker: Field condition ${type.displayName} started on turn $effectiveStartTurn")
    }

    fun clearFieldCondition(type: FieldCondition) {
        fieldConditions.remove(type)
        CobblemonExtendedBattleUI.LOGGER.debug("ConditionTracker: Field condition ${type.displayName} ended")
    }

    fun getFieldConditions(): Map<FieldCondition, FieldConditionState> = fieldConditions.toMap()

    fun getFieldConditionTurnsRemaining(type: FieldCondition, currentTurn: Int): String? {
        val fc = fieldConditions[type] ?: return null
        val turnsElapsed = currentTurn - fc.startTurn
        val remaining = fc.type.baseDuration - turnsElapsed
        return remaining.coerceAtLeast(1).toString()
    }

    // ── Side Conditions ──────────────────────────────────────────────────────

    fun setSideCondition(isPlayerSide: Boolean, type: SideCondition, currentTurn: Int) {
        val conditions = if (isPlayerSide) playerSideConditions else opponentSideConditions
        val existing = conditions[type]

        val effectiveStartTurn = maxOf(1, currentTurn)
        if (existing != null && type.maxStacks > 1) {
            existing.stacks = (existing.stacks + 1).coerceAtMost(type.maxStacks)
            CobblemonExtendedBattleUI.LOGGER.debug("ConditionTracker: ${if (isPlayerSide) "Player" else "Opponent"} ${type.displayName} stacked to ${existing.stacks}")
        } else {
            conditions[type] = SideConditionState(type, effectiveStartTurn)
            CobblemonExtendedBattleUI.LOGGER.debug("ConditionTracker: ${if (isPlayerSide) "Player" else "Opponent"} ${type.displayName} started on turn $effectiveStartTurn")
        }
    }

    fun clearSideCondition(isPlayerSide: Boolean, type: SideCondition) {
        val conditions = if (isPlayerSide) playerSideConditions else opponentSideConditions
        conditions.remove(type)
        CobblemonExtendedBattleUI.LOGGER.debug("ConditionTracker: ${if (isPlayerSide) "Player" else "Opponent"} ${type.displayName} ended")
    }

    fun getPlayerSideConditions(): Map<SideCondition, SideConditionState> = playerSideConditions.toMap()
    fun getOpponentSideConditions(): Map<SideCondition, SideConditionState> = opponentSideConditions.toMap()

    fun swapSideConditions() {
        val playerCopy = playerSideConditions.toMap()
        val opponentCopy = opponentSideConditions.toMap()

        playerSideConditions.clear()
        opponentSideConditions.clear()

        playerSideConditions.putAll(opponentCopy)
        opponentSideConditions.putAll(playerCopy)

        CobblemonExtendedBattleUI.LOGGER.debug(
            "ConditionTracker: Court Change - swapped side conditions. " +
            "Player now has: ${playerSideConditions.keys.map { it.displayName }}, " +
            "Opponent now has: ${opponentSideConditions.keys.map { it.displayName }}"
        )
    }

    fun getSideConditionTurnsRemaining(isPlayerSide: Boolean, type: SideCondition, currentTurn: Int): String? {
        val conditions = if (isPlayerSide) playerSideConditions else opponentSideConditions
        val sc = conditions[type] ?: return null

        if (sc.type.baseDuration == null) return null

        val turnsElapsed = currentTurn - sc.startTurn

        if (type in listOf(SideCondition.REFLECT, SideCondition.LIGHT_SCREEN, SideCondition.AURORA_VEIL)) {
            val minRemaining = 5 - turnsElapsed
            val maxRemaining = 8 - turnsElapsed

            return when {
                sc.confirmedExtended -> maxRemaining.coerceAtLeast(1).toString()
                minRemaining <= 0 -> {
                    sc.confirmedExtended = true
                    maxRemaining.coerceAtLeast(1).toString()
                }
                minRemaining == maxRemaining -> minRemaining.toString()
                else -> "$minRemaining-$maxRemaining"
            }
        }

        val remaining = sc.type.baseDuration - turnsElapsed
        return remaining.coerceAtLeast(1).toString()
    }

    // ── Terastallization ─────────────────────────────────────────────────────

    fun setTerastallized(uuid: java.util.UUID, teraTypeName: String) {
        terastallizedPokemon[uuid] = teraTypeName
        CobblemonExtendedBattleUI.LOGGER.debug("ConditionTracker: Marked UUID $uuid as Terastallized (type: $teraTypeName)")
    }

    fun isTerastallized(uuid: java.util.UUID): Boolean = terastallizedPokemon.containsKey(uuid)

    fun getTeraType(uuid: java.util.UUID): String? = terastallizedPokemon[uuid]

    // ── Expiration Check ─────────────────────────────────────────────────────

    fun checkForExpiredConditions(currentTurn: Int) {
        weather?.let { w ->
            val turnsElapsed = currentTurn - w.startTurn
            if (turnsElapsed >= 5 && !w.confirmedExtended) {
                w.confirmedExtended = true
                CobblemonExtendedBattleUI.LOGGER.debug("ConditionTracker: Weather confirmed extended (still active after 5 turns)")
            }
        }

        terrain?.let { t ->
            val turnsElapsed = currentTurn - t.startTurn
            if (turnsElapsed >= 5 && !t.confirmedExtended) {
                t.confirmedExtended = true
                CobblemonExtendedBattleUI.LOGGER.debug("ConditionTracker: Terrain confirmed extended (still active after 5 turns)")
            }
        }

        listOf(playerSideConditions, opponentSideConditions).forEach { conditions ->
            conditions.values.forEach { sc ->
                if (sc.type in listOf(SideCondition.REFLECT, SideCondition.LIGHT_SCREEN, SideCondition.AURORA_VEIL)) {
                    val turnsElapsed = currentTurn - sc.startTurn
                    if (turnsElapsed >= 5 && !sc.confirmedExtended) {
                        sc.confirmedExtended = true
                    }
                }
            }
        }
    }
}
