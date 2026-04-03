package com.cobblemonextendedbattleui.battle.state

import com.cobblemonextendedbattleui.CobblemonExtendedBattleUI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks UUID-to-name mappings, ally/opponent designation, KO status,
 * and transform status for all Pokemon in battle.
 *
 * Provides [resolvePokemonUuid] — the core name-to-UUID resolution used
 * by every other sub-tracker.
 */
object PokemonRegistry {

    // Maps lowercase name -> list of (UUID, isAlly) pairs to handle mirror matches
    private val nameToUuids = ConcurrentHashMap<String, MutableList<Pair<UUID, Boolean>>>()
    private val uuidIsAlly = ConcurrentHashMap<UUID, Boolean>()

    // Player names for each side to disambiguate owner prefixes
    private var allyPlayerName: String? = null
    private var opponentPlayerName: String? = null

    // KO tracking — persists after faint, only cleared on battle end
    private val knockedOutPokemon = ConcurrentHashMap.newKeySet<UUID>()

    // Transform tracking (Ditto via Transform/Impostor)
    private val transformedPokemon = ConcurrentHashMap.newKeySet<UUID>()

    fun clear() {
        nameToUuids.clear()
        uuidIsAlly.clear()
        allyPlayerName = null
        opponentPlayerName = null
        knockedOutPokemon.clear()
        transformedPokemon.clear()
    }

    fun setPlayerNames(allyName: String, opponentName: String) {
        allyPlayerName = allyName.lowercase()
        opponentPlayerName = opponentName.lowercase()
        CobblemonExtendedBattleUI.LOGGER.debug("PokemonRegistry: Player names set - Ally: $allyName, Opponent: $opponentName")
    }

    fun registerPokemon(uuid: UUID, name: String, isAlly: Boolean) {
        val lowerName = name.lowercase()
        val uuidList = nameToUuids.computeIfAbsent(lowerName) { mutableListOf() }

        val existingEntry = uuidList.find { it.first == uuid }
        if (existingEntry == null) {
            uuidList.add(Pair(uuid, isAlly))
            CobblemonExtendedBattleUI.LOGGER.debug("PokemonRegistry: Registered '$name' (${if (isAlly) "ally" else "opponent"}) with UUID $uuid")
        } else if (existingEntry.second != isAlly) {
            uuidList.remove(existingEntry)
            uuidList.add(Pair(uuid, isAlly))
        }

        uuidIsAlly[uuid] = isAlly
    }

    fun isPokemonAlly(uuid: UUID): Boolean = uuidIsAlly[uuid] ?: false

    fun getPokemonUuid(pokemonName: String, preferAlly: Boolean? = null): UUID? =
        resolvePokemonUuid(pokemonName, preferAlly)

    // ── KO Tracking ──────────────────────────────────────────────────────────

    fun markAsKO(pokemonName: String, preferAlly: Boolean? = null) {
        val uuid = resolvePokemonUuid(pokemonName, preferAlly) ?: run {
            CobblemonExtendedBattleUI.LOGGER.debug("PokemonRegistry: Unknown Pokemon '$pokemonName' for KO tracking")
            return
        }
        markAsKO(uuid)
    }

    fun markAsKO(uuid: UUID) {
        knockedOutPokemon.add(uuid)
        CobblemonExtendedBattleUI.LOGGER.debug("PokemonRegistry: Marked UUID $uuid as KO'd")
    }

    fun isKO(uuid: UUID): Boolean = knockedOutPokemon.contains(uuid)

    // ── Transform Tracking ───────────────────────────────────────────────────

    fun markAsTransformed(pokemonName: String, preferAlly: Boolean? = null) {
        val uuid = resolvePokemonUuid(pokemonName, preferAlly) ?: run {
            CobblemonExtendedBattleUI.LOGGER.debug("PokemonRegistry: Unknown Pokemon '$pokemonName' for transform tracking")
            return
        }
        markAsTransformed(uuid)
    }

    fun markAsTransformed(uuid: UUID) {
        transformedPokemon.add(uuid)
        CobblemonExtendedBattleUI.LOGGER.debug("PokemonRegistry: Marked UUID $uuid as transformed")
    }

    fun isTransformed(uuid: UUID): Boolean = transformedPokemon.contains(uuid)

    fun clearTransformStatus(uuid: UUID) {
        if (transformedPokemon.remove(uuid)) {
            CobblemonExtendedBattleUI.LOGGER.debug("PokemonRegistry: Cleared transform status for UUID $uuid")
        }
    }

    fun clearTransformStatusByName(pokemonName: String, preferAlly: Boolean? = null) {
        val uuid = resolvePokemonUuid(pokemonName, preferAlly) ?: return
        clearTransformStatus(uuid)
    }

    // ── Name → UUID Resolution ───────────────────────────────────────────────

    /**
     * Resolve Pokemon name to UUID. For mirror matches, disambiguates via:
     * 1. Owner prefix (e.g., "Player123's Togekiss")
     * 2. "opposing" or "the opposing" prefix (indicates opponent's Pokemon)
     * 3. preferAlly hint
     * 4. First registered (fallback)
     */
    fun resolvePokemonUuid(pokemonName: String, preferAlly: Boolean? = null): UUID? {
        var lookupName = pokemonName.lowercase()
        var ownerDeterminedSide: Boolean? = null

        val opposingPrefixes = listOf("the opposing ", "opposing ")
        for (prefix in opposingPrefixes) {
            if (lookupName.startsWith(prefix)) {
                lookupName = lookupName.removePrefix(prefix)
                ownerDeterminedSide = false
                CobblemonExtendedBattleUI.LOGGER.debug(
                    "PokemonRegistry: Detected opponent prefix in '$pokemonName', using name '$lookupName'"
                )
                break
            }
        }

        if (pokemonName.contains("'s ")) {
            val ownerName = pokemonName.substringBefore("'s ").lowercase()
            val strippedName = pokemonName.substringAfter("'s ").lowercase()

            val allyName = allyPlayerName
            val oppName = opponentPlayerName

            if (allyName != null && (ownerName == allyName || ownerName.contains(allyName) || allyName.contains(ownerName))) {
                ownerDeterminedSide = true
                CobblemonExtendedBattleUI.LOGGER.debug(
                    "PokemonRegistry: Owner '$ownerName' matched ally player '$allyName'"
                )
            } else if (oppName != null && (ownerName == oppName || ownerName.contains(oppName) || oppName.contains(ownerName))) {
                ownerDeterminedSide = false
                CobblemonExtendedBattleUI.LOGGER.debug(
                    "PokemonRegistry: Owner '$ownerName' matched opponent player '$oppName'"
                )
            }

            if (nameToUuids[lookupName] == null) {
                lookupName = strippedName
            }
        }

        val uuidList = nameToUuids[lookupName] ?: run {
            CobblemonExtendedBattleUI.LOGGER.debug(
                "PokemonRegistry: Could not find Pokemon '$lookupName' (original: '$pokemonName') in registry"
            )
            return null
        }

        if (uuidList.size == 1) {
            return uuidList[0].first
        }

        if (uuidList.size > 1) {
            val targetIsAlly = ownerDeterminedSide ?: preferAlly

            if (targetIsAlly != null) {
                val match = uuidList.find { it.second == targetIsAlly }
                if (match != null) {
                    val method = if (ownerDeterminedSide != null) "owner name" else "preferAlly hint"
                    CobblemonExtendedBattleUI.LOGGER.debug(
                        "PokemonRegistry: Resolved '$pokemonName' to ${if (targetIsAlly) "ally" else "opponent"} via $method"
                    )
                    return match.first
                }
            }

            CobblemonExtendedBattleUI.LOGGER.debug(
                "PokemonRegistry: Ambiguous Pokemon '$pokemonName' in mirror match, using first registered"
            )
            return uuidList[0].first
        }

        return null
    }

    /**
     * Iterate over all registered UUIDs. Used by Perish Song (applies to all active Pokemon).
     */
    fun forEachRegistered(action: (uuid: UUID) -> Unit) {
        for ((_, uuidList) in nameToUuids) {
            for ((uuid, _) in uuidList) {
                action(uuid)
            }
        }
    }
}
