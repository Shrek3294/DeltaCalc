package com.cobblemonextendedbattleui.battle.state

import com.cobblemonextendedbattleui.BattleStateTracker.TrackedMove
import com.cobblemonextendedbattleui.CobblemonExtendedBattleUI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks revealed moves, move usage counts, PP tracking, and Pressure ability.
 */
object MoveTracker {

    private val revealedMoves = ConcurrentHashMap<UUID, MutableSet<String>>()
    private val moveUsageCounts = ConcurrentHashMap<UUID, ConcurrentHashMap<String, Int>>()
    private val trackedMoves = ConcurrentHashMap<UUID, List<TrackedMove>>()
    private val pressurePokemon = ConcurrentHashMap.newKeySet<UUID>()

    fun clear() {
        revealedMoves.clear()
        moveUsageCounts.clear()
        trackedMoves.clear()
        pressurePokemon.clear()
    }

    fun initializeMoves(uuid: UUID, moves: List<TrackedMove>) {
        if (moves.isNotEmpty() && trackedMoves.putIfAbsent(uuid, moves) == null) {
            CobblemonExtendedBattleUI.LOGGER.debug("MoveTracker: Initialized PP tracking for $uuid with ${moves.size} moves")
        }
    }

    fun decrementPP(uuid: UUID, moveName: String, targetName: String? = null) {
        val moves = trackedMoves[uuid] ?: return
        val move = moves.find { it.name.equals(moveName, ignoreCase = true) } ?: return

        val ppCost = if (targetName != null) {
            val targetUuid = PokemonRegistry.resolvePokemonUuid(targetName, preferAlly = false)
            if (targetUuid != null && pressurePokemon.contains(targetUuid)) 2 else 1
        } else {
            1
        }

        val newPp = move.decrementPp(ppCost)
        if (ppCost > 1) {
            CobblemonExtendedBattleUI.LOGGER.debug("MoveTracker: $moveName PP: $newPp/${move.maxPp} (Pressure: -$ppCost)")
        } else {
            CobblemonExtendedBattleUI.LOGGER.debug("MoveTracker: $moveName PP: $newPp/${move.maxPp}")
        }
    }

    fun registerPressure(pokemonName: String) {
        val uuid = PokemonRegistry.resolvePokemonUuid(pokemonName, preferAlly = null) ?: run {
            CobblemonExtendedBattleUI.LOGGER.debug("MoveTracker: Could not resolve UUID for Pressure Pokemon '$pokemonName'")
            return
        }
        if (pressurePokemon.add(uuid)) {
            CobblemonExtendedBattleUI.LOGGER.debug("MoveTracker: Registered Pressure for $pokemonName ($uuid)")
        }
    }

    fun hasPressure(uuid: UUID): Boolean = pressurePokemon.contains(uuid)

    fun getTrackedMoves(uuid: UUID): List<TrackedMove>? = trackedMoves[uuid]

    fun addRevealedMove(pokemonName: String, moveName: String, targetName: String? = null, preferAlly: Boolean? = null) {
        val uuid = PokemonRegistry.resolvePokemonUuid(pokemonName, preferAlly) ?: run {
            CobblemonExtendedBattleUI.LOGGER.debug("MoveTracker: Unknown Pokemon '$pokemonName' for move tracking")
            return
        }
        val moves = revealedMoves.computeIfAbsent(uuid) { ConcurrentHashMap.newKeySet() }
        if (moves.add(moveName)) {
            CobblemonExtendedBattleUI.LOGGER.debug("MoveTracker: $pokemonName revealed move '$moveName'")
        }

        val usageCounts = moveUsageCounts.computeIfAbsent(uuid) { ConcurrentHashMap() }
        usageCounts.compute(moveName) { _, count -> (count ?: 0) + 1 }

        decrementPP(uuid, moveName, targetName)
    }

    fun getRevealedMoves(uuid: UUID): Set<String> = revealedMoves[uuid]?.toSet() ?: emptySet()

    fun getMoveUsageCount(uuid: UUID, moveName: String): Int {
        return moveUsageCounts[uuid]?.get(moveName) ?: 0
    }
}
