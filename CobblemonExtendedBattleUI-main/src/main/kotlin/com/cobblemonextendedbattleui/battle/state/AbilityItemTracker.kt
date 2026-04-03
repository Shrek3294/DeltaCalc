package com.cobblemonextendedbattleui.battle.state

import com.cobblemonextendedbattleui.BattleStateTracker.ItemStatus
import com.cobblemonextendedbattleui.BattleStateTracker.TrackedItem
import com.cobblemonextendedbattleui.CobblemonExtendedBattleUI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks revealed abilities and held items for all Pokemon in battle.
 * Items persist after faint/switch for competitive information.
 */
object AbilityItemTracker {

    private val revealedAbilities = ConcurrentHashMap<UUID, String>()
    private val pokemonItems = ConcurrentHashMap<UUID, TrackedItem>()

    fun clear() {
        revealedAbilities.clear()
        pokemonItems.clear()
    }

    // ── Ability Tracking ─────────────────────────────────────────────────────

    fun setRevealedAbility(pokemonName: String, abilityName: String, preferAlly: Boolean? = null) {
        val uuid = PokemonRegistry.resolvePokemonUuid(pokemonName, preferAlly) ?: run {
            CobblemonExtendedBattleUI.LOGGER.debug("AbilityItemTracker: Unknown Pokemon '$pokemonName' for ability tracking")
            return
        }
        setRevealedAbility(uuid, abilityName)
        CobblemonExtendedBattleUI.LOGGER.debug("AbilityItemTracker: $pokemonName ability revealed: $abilityName")
    }

    fun setRevealedAbility(uuid: UUID, abilityName: String) {
        val normalizedName = abilityName.split(" ", "_", "-")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        revealedAbilities[uuid] = normalizedName
    }

    fun getRevealedAbility(uuid: UUID): String? = revealedAbilities[uuid]

    fun getRevealedAbilityByName(pokemonName: String, preferAlly: Boolean? = null): String? {
        val uuid = PokemonRegistry.resolvePokemonUuid(pokemonName, preferAlly) ?: return null
        return revealedAbilities[uuid]
    }

    fun clearRevealedAbility(uuid: UUID) {
        if (revealedAbilities.remove(uuid) != null) {
            CobblemonExtendedBattleUI.LOGGER.debug("AbilityItemTracker: Cleared revealed ability for UUID $uuid")
        }
    }

    // ── Item Tracking ────────────────────────────────────────────────────────

    fun setItem(pokemonName: String, itemName: String, status: ItemStatus, currentTurn: Int, preferAlly: Boolean? = null) {
        val uuid = PokemonRegistry.resolvePokemonUuid(pokemonName, preferAlly) ?: run {
            CobblemonExtendedBattleUI.LOGGER.debug("AbilityItemTracker: Unknown Pokemon '$pokemonName' for item tracking")
            return
        }

        val effectiveTurn = maxOf(1, currentTurn)
        val existingItem = pokemonItems[uuid]

        when (status) {
            ItemStatus.HELD -> {
                if (existingItem == null) {
                    pokemonItems[uuid] = TrackedItem(itemName, status, effectiveTurn)
                    CobblemonExtendedBattleUI.LOGGER.debug("AbilityItemTracker: $pokemonName revealed item $itemName")
                }
            }
            ItemStatus.KNOCKED_OFF, ItemStatus.STOLEN, ItemStatus.SWAPPED, ItemStatus.CONSUMED -> {
                pokemonItems[uuid] = TrackedItem(
                    itemName,
                    status,
                    existingItem?.revealTurn ?: effectiveTurn,
                    removalTurn = effectiveTurn
                )
                CobblemonExtendedBattleUI.LOGGER.debug("AbilityItemTracker: $pokemonName item $itemName ${status.displaySuffix}")
            }
        }
    }

    fun getItem(uuid: UUID): TrackedItem? = pokemonItems[uuid]

    fun getItemByName(pokemonName: String, preferAlly: Boolean? = null): TrackedItem? {
        val uuid = PokemonRegistry.resolvePokemonUuid(pokemonName, preferAlly) ?: return null
        return pokemonItems[uuid]
    }

    fun transferItem(fromPokemon: String, toPokemon: String, itemName: String, currentTurn: Int, fromIsAlly: Boolean? = null, toIsAlly: Boolean? = null) {
        val fromUuid = PokemonRegistry.resolvePokemonUuid(fromPokemon, fromIsAlly)
        val toUuid = PokemonRegistry.resolvePokemonUuid(toPokemon, toIsAlly)
        val effectiveTurn = maxOf(1, currentTurn)

        if (fromUuid != null) {
            val existingItem = pokemonItems[fromUuid]
            pokemonItems[fromUuid] = TrackedItem(
                itemName,
                ItemStatus.STOLEN,
                existingItem?.revealTurn ?: effectiveTurn,
                removalTurn = effectiveTurn
            )
        }

        if (toUuid != null) {
            pokemonItems[toUuid] = TrackedItem(itemName, ItemStatus.HELD, effectiveTurn)
        }

        CobblemonExtendedBattleUI.LOGGER.debug("AbilityItemTracker: $toPokemon stole $itemName from $fromPokemon")
    }

    fun receiveItemViaTrick(pokemonName: String, newItemName: String, currentTurn: Int, preferAlly: Boolean? = null) {
        val uuid = PokemonRegistry.resolvePokemonUuid(pokemonName, preferAlly) ?: run {
            CobblemonExtendedBattleUI.LOGGER.debug("AbilityItemTracker: Unknown Pokemon '$pokemonName' for Trick item swap")
            return
        }

        val effectiveTurn = maxOf(1, currentTurn)
        val existingItem = pokemonItems[uuid]

        if (existingItem != null && existingItem.status == ItemStatus.HELD) {
            CobblemonExtendedBattleUI.LOGGER.debug(
                "AbilityItemTracker: $pokemonName's ${existingItem.name} swapped for $newItemName via Trick"
            )
        } else {
            CobblemonExtendedBattleUI.LOGGER.debug(
                "AbilityItemTracker: $pokemonName obtained $newItemName via Trick"
            )
        }

        pokemonItems[uuid] = TrackedItem(newItemName, ItemStatus.HELD, effectiveTurn)
    }

    fun markItemSwapped(pokemonName: String, currentTurn: Int, preferAlly: Boolean? = null) {
        val uuid = PokemonRegistry.resolvePokemonUuid(pokemonName, preferAlly) ?: return

        val effectiveTurn = maxOf(1, currentTurn)
        val existingItem = pokemonItems[uuid] ?: return

        if (existingItem.status == ItemStatus.HELD) {
            pokemonItems[uuid] = TrackedItem(
                existingItem.name,
                ItemStatus.SWAPPED,
                existingItem.revealTurn,
                removalTurn = effectiveTurn
            )
            CobblemonExtendedBattleUI.LOGGER.debug(
                "AbilityItemTracker: $pokemonName's ${existingItem.name} marked as swapped away"
            )
        }
    }
}
