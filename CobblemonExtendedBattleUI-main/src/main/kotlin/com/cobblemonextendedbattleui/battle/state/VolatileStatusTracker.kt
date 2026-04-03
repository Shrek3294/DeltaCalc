package com.cobblemonextendedbattleui.battle.state

import com.cobblemonextendedbattleui.BattleStateTracker.VolatileStatus
import com.cobblemonextendedbattleui.BattleStateTracker.VolatileStatusState
import com.cobblemonextendedbattleui.CobblemonExtendedBattleUI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks per-Pokemon volatile statuses that clear on switch.
 * Handles Perish Song (applied to all), countdown/duration tracking.
 */
object VolatileStatusTracker {

    private val volatileStatuses = ConcurrentHashMap<UUID, MutableSet<VolatileStatusState>>()

    fun clear() {
        volatileStatuses.clear()
    }

    /** Ensure a Pokemon has a volatile set (called from registerPokemon). */
    fun ensureInitialized(uuid: UUID) {
        volatileStatuses.computeIfAbsent(uuid) { ConcurrentHashMap.newKeySet() }
    }

    fun setVolatileStatus(pokemonName: String, type: VolatileStatus, currentTurn: Int, preferAlly: Boolean? = null) {
        val uuid = PokemonRegistry.resolvePokemonUuid(pokemonName, preferAlly) ?: run {
            CobblemonExtendedBattleUI.LOGGER.debug("VolatileStatusTracker: Unknown Pokemon '$pokemonName' for volatile status")
            return
        }

        val effectiveStartTurn = maxOf(1, currentTurn)
        val statuses = volatileStatuses.computeIfAbsent(uuid) { ConcurrentHashMap.newKeySet() }

        // Perish Song has an absolute countdown - don't reset if already applied
        if (type == VolatileStatus.PERISH_SONG && statuses.any { it.type == VolatileStatus.PERISH_SONG }) {
            CobblemonExtendedBattleUI.LOGGER.debug("VolatileStatusTracker: $pokemonName already has Perish Song, keeping original startTurn")
            return
        }

        statuses.removeIf { it.type == type }
        statuses.add(VolatileStatusState(type, effectiveStartTurn))

        CobblemonExtendedBattleUI.LOGGER.debug("VolatileStatusTracker: $pokemonName gained volatile ${type.displayName}")
    }

    fun clearVolatileStatus(pokemonName: String, type: VolatileStatus, preferAlly: Boolean? = null) {
        val uuid = PokemonRegistry.resolvePokemonUuid(pokemonName, preferAlly) ?: return
        volatileStatuses[uuid]?.removeIf { it.type == type }
        CobblemonExtendedBattleUI.LOGGER.debug("VolatileStatusTracker: $pokemonName lost volatile ${type.displayName}")
    }

    fun clearPokemonVolatiles(uuid: UUID) {
        volatileStatuses[uuid]?.clear()
        CobblemonExtendedBattleUI.LOGGER.debug("VolatileStatusTracker: Cleared volatiles for UUID $uuid")
    }

    fun clearPokemonVolatilesByName(pokemonName: String, preferAlly: Boolean? = null) {
        val uuid = PokemonRegistry.resolvePokemonUuid(pokemonName, preferAlly) ?: return
        clearPokemonVolatiles(uuid)
    }

    fun getVolatileStatuses(uuid: UUID): Set<VolatileStatusState> =
        volatileStatuses[uuid]?.toSet() ?: emptySet()

    fun getVolatileTurnsRemaining(state: VolatileStatusState, currentTurn: Int): String? {
        val duration = state.type.baseDuration ?: return null
        val turnsElapsed = currentTurn - state.startTurn

        return if (state.type.countsDown) {
            val countdown = (duration - turnsElapsed).coerceIn(0, duration - 1)
            countdown.toString()
        } else {
            val remaining = duration - turnsElapsed
            remaining.coerceAtLeast(1).toString()
        }
    }

    fun applyPerishSongToAll(currentTurn: Int) {
        val effectiveStartTurn = maxOf(1, currentTurn)
        var count = 0

        PokemonRegistry.forEachRegistered { uuid ->
            val statuses = volatileStatuses.computeIfAbsent(uuid) { ConcurrentHashMap.newKeySet() }
            if (statuses.none { it.type == VolatileStatus.PERISH_SONG }) {
                statuses.add(VolatileStatusState(VolatileStatus.PERISH_SONG, effectiveStartTurn))
                count++
            }
        }

        CobblemonExtendedBattleUI.LOGGER.debug("VolatileStatusTracker: Perish Song applied to $count Pokemon")
    }

    /** Get raw volatile set for Baton Pass filtering. */
    fun getRawVolatiles(uuid: UUID): Set<VolatileStatusState>? =
        volatileStatuses[uuid]?.toSet()

    /** Apply volatiles from Baton Pass. */
    fun applyBatonPassVolatiles(uuid: UUID, volatiles: Set<VolatileStatusState>, currentTurn: Int) {
        val statuses = volatileStatuses.computeIfAbsent(uuid) { ConcurrentHashMap.newKeySet() }
        val effectiveStartTurn = maxOf(1, currentTurn)
        for (volatileState in volatiles) {
            statuses.add(VolatileStatusState(volatileState.type, effectiveStartTurn))
        }
    }
}
