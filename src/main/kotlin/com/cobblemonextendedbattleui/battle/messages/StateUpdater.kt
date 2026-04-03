package com.cobblemonextendedbattleui.battle.messages

import com.cobblemonextendedbattleui.BattleStateTracker
import com.cobblemonextendedbattleui.BattleStateTracker.BattleStat
import com.cobblemonextendedbattleui.BattleStateTracker.ItemStatus
import com.cobblemonextendedbattleui.BattleStateTracker.VolatileStatus
import com.cobblemonextendedbattleui.CobblemonExtendedBattleUI
import com.cobblemonextendedbattleui.TeamIndicatorUI
import net.minecraft.text.Text

/**
 * Takes parsed battle message arguments and calls the appropriate
 * BattleStateTracker methods to update state.
 */
object StateUpdater {

    fun extractTurn(args: Array<out Any>) {
        if (args.isEmpty()) return

        val turnStr = when (val arg0 = args[0]) {
            is Text -> arg0.string
            is String -> arg0
            is Number -> arg0.toString()
            else -> arg0.toString()
        }

        val turn = turnStr.toIntOrNull()
        if (turn != null) {
            BattleStateTracker.setTurn(turn)
        }
    }

    // Args: [pokemonName, statName]. stages > 0 for boost, < 0 for drop.
    fun extractBoost(args: Array<out Any>, stages: Int) {
        if (args.size < 2) {
            CobblemonExtendedBattleUI.LOGGER.debug("StateUpdater: Boost args too short: ${args.size}")
            return
        }

        val pokemonName = MessageParser.extractPokemonName(args[0])
        val stat = MessageParser.resolveStat(args[1]) ?: return

        CobblemonExtendedBattleUI.LOGGER.debug("StateUpdater: $pokemonName ${stat.abbr} ${if (stages > 0) "+" else ""}$stages")
        BattleStateTracker.applyStatChange(pokemonName, stat, stages)
    }

    // Belly Drum / Anger Point: sets Attack to +6
    fun extractSetBoost(args: Array<out Any>) {
        if (args.isEmpty()) return

        val pokemonName = MessageParser.argToString(args[0])
        CobblemonExtendedBattleUI.LOGGER.debug("StateUpdater: $pokemonName Attack set to +6 (Belly Drum/Anger Point)")
        BattleStateTracker.setStatStage(pokemonName, BattleStat.ATTACK, 6)
    }

    fun extractClearBoost(args: Array<out Any>) {
        if (args.isEmpty()) return

        val pokemonName = MessageParser.argToString(args[0])
        CobblemonExtendedBattleUI.LOGGER.debug("StateUpdater: Clearing all stats for $pokemonName")
        BattleStateTracker.clearPokemonStatsByName(pokemonName)
    }

    // Topsy-Turvy: invert all stat changes
    fun extractInvertBoost(args: Array<out Any>) {
        if (args.isEmpty()) return

        val pokemonName = MessageParser.argToString(args[0])
        CobblemonExtendedBattleUI.LOGGER.debug("StateUpdater: Inverting stats for $pokemonName")
        BattleStateTracker.invertStats(pokemonName)
    }

    // Heart Swap: swap all stats. Single-arg variant uses lastMoveTarget.
    fun extractSwapBoostAllStats(args: Array<out Any>) {
        if (args.isEmpty()) {
            CobblemonExtendedBattleUI.LOGGER.debug("StateUpdater: SwapBoostAllStats no args")
            return
        }

        val pokemon1 = MessageParser.argToString(args[0])
        val pokemon2: String

        if (args.size >= 2) {
            pokemon2 = MessageParser.argToString(args[1])
        } else {
            pokemon2 = MessageParser.lastMoveTarget ?: run {
                CobblemonExtendedBattleUI.LOGGER.debug("StateUpdater: SwapBoostAllStats no target tracked for $pokemon1")
                return
            }
        }

        CobblemonExtendedBattleUI.LOGGER.debug("StateUpdater: Swapping ALL stats between $pokemon1 and $pokemon2")
        BattleStateTracker.swapStats(pokemon1, pokemon2)
    }

    // Power/Guard/Speed Swap: swap specific stats. Single-arg variant uses lastMoveTarget.
    fun extractSwapBoostSpecific(args: Array<out Any>, statsToSwap: List<BattleStat>) {
        if (args.isEmpty()) {
            CobblemonExtendedBattleUI.LOGGER.debug("StateUpdater: SwapBoostSpecific no args")
            return
        }

        val pokemon1 = MessageParser.argToString(args[0])
        val pokemon2: String

        if (args.size >= 2) {
            pokemon2 = MessageParser.argToString(args[1])
        } else {
            pokemon2 = MessageParser.lastMoveTarget ?: run {
                CobblemonExtendedBattleUI.LOGGER.debug("StateUpdater: SwapBoostSpecific no target tracked for $pokemon1")
                return
            }
        }

        CobblemonExtendedBattleUI.LOGGER.debug("StateUpdater: Swapping ${statsToSwap.map { it.abbr }} between $pokemon1 and $pokemon2")
        BattleStateTracker.swapSpecificStats(pokemon1, pokemon2, statsToSwap)
    }

    // Psych Up: copy all stat changes from source to copier
    fun extractCopyBoost(args: Array<out Any>) {
        if (args.size < 2) {
            CobblemonExtendedBattleUI.LOGGER.debug("StateUpdater: CopyBoost needs 2 args, got ${args.size}")
            return
        }

        val copier = MessageParser.argToString(args[0])
        val source = MessageParser.argToString(args[1])

        CobblemonExtendedBattleUI.LOGGER.debug("StateUpdater: $copier copies stats from $source")
        BattleStateTracker.copyStats(source, copier)
    }

    fun extractVolatileStatusStart(args: Array<out Any>, volatileStatus: VolatileStatus) {
        if (args.isEmpty()) {
            CobblemonExtendedBattleUI.LOGGER.debug("StateUpdater: No args for volatile status start: ${volatileStatus.displayName}")
            return
        }

        val pokemonName = MessageParser.argToString(args[0])
        CobblemonExtendedBattleUI.LOGGER.debug("StateUpdater: Volatile start - $pokemonName gained ${volatileStatus.displayName}")
        BattleStateTracker.setVolatileStatus(pokemonName, volatileStatus)
    }

    fun extractVolatileStatusEnd(args: Array<out Any>, volatileStatus: VolatileStatus) {
        if (args.isEmpty()) {
            CobblemonExtendedBattleUI.LOGGER.debug("StateUpdater: No args for volatile status end: ${volatileStatus.displayName}")
            return
        }

        val pokemonName = MessageParser.argToString(args[0])
        CobblemonExtendedBattleUI.LOGGER.debug("StateUpdater: Volatile end - $pokemonName lost ${volatileStatus.displayName}")
        BattleStateTracker.clearVolatileStatus(pokemonName, volatileStatus)
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Item Extraction Functions
    // ═════════════════════════════════════════════════════════════════════════

    fun extractItemReveal(args: Array<out Any>) {
        if (args.size < 2) return
        val pokemonName = MessageParser.argToString(args[0])
        val itemName = MessageParser.argToString(args[1])
        BattleStateTracker.setItem(pokemonName, itemName, ItemStatus.HELD)
    }

    fun extractTrickItem(args: Array<out Any>) {
        if (args.size < 2) return
        val pokemonName = MessageParser.argToString(args[0])
        val itemName = MessageParser.argToString(args[1])
        BattleStateTracker.receiveItemViaTrick(pokemonName, itemName)
    }

    fun extractTrickActivation(args: Array<out Any>) {
        if (args.isEmpty()) return
        val userName = MessageParser.argToString(args[0])
        CobblemonExtendedBattleUI.LOGGER.debug("StateUpdater: $userName used Trick/Switcheroo")
        BattleStateTracker.markItemSwapped(userName)
    }

    fun extractLifeOrbReveal(args: Array<out Any>) {
        if (args.isEmpty()) return
        val pokemonName = MessageParser.argToString(args[0])
        CobblemonExtendedBattleUI.LOGGER.debug("StateUpdater: Life Orb revealed for $pokemonName")
        BattleStateTracker.setItem(pokemonName, "Life Orb", ItemStatus.HELD)
    }

    fun extractFriskItem(args: Array<out Any>) {
        if (args.size < 2) return
        val targetPokemon = MessageParser.argToString(args[0])
        val itemName = MessageParser.argToString(args[1])
        BattleStateTracker.setItem(targetPokemon, itemName, ItemStatus.HELD)
    }

    fun extractThiefItem(args: Array<out Any>) {
        if (args.size < 3) return
        val thiefPokemon = MessageParser.argToString(args[0])
        val itemName = MessageParser.argToString(args[1])
        val victimPokemon = MessageParser.argToString(args[2])
        BattleStateTracker.transferItem(victimPokemon, thiefPokemon, itemName)
    }

    fun extractBestowItem(args: Array<out Any>) {
        if (args.size < 3) return
        val receiverPokemon = MessageParser.argToString(args[0])
        val itemName = MessageParser.argToString(args[1])
        val giverPokemon = MessageParser.argToString(args[2])
        BattleStateTracker.transferItem(giverPokemon, receiverPokemon, itemName)
    }

    fun extractItemConsumed(args: Array<out Any>) {
        if (args.size < 2) return
        val pokemonName = MessageParser.argToString(args[0])
        val itemName = MessageParser.argToString(args[1])
        BattleStateTracker.setItem(pokemonName, itemName, ItemStatus.CONSUMED)
    }

    fun extractItemConsumedSingleArg(args: Array<out Any>, itemName: String) {
        if (args.isEmpty()) return
        val pokemonName = MessageParser.argToString(args[0])
        BattleStateTracker.setItem(pokemonName, itemName, ItemStatus.CONSUMED)
    }

    fun extractBerryFromKey(key: String, args: Array<out Any>) {
        if (args.isEmpty()) return
        val pokemonName = MessageParser.argToString(args[0])
        val berryId = key.substringAfterLast(".")
        val berryName = berryId
            .replace("berry", " Berry")
            .replaceFirstChar { it.uppercase() }
        BattleStateTracker.setItem(pokemonName, berryName, ItemStatus.CONSUMED)
    }

    fun extractKnockOff(args: Array<out Any>) {
        if (args.size < 2) return
        val targetPokemon = MessageParser.argToString(args[0])
        val itemName = MessageParser.argToString(args[1])
        BattleStateTracker.setItem(targetPokemon, itemName, ItemStatus.KNOCKED_OFF)
    }

    fun extractStealEat(args: Array<out Any>) {
        if (args.size < 3) return
        val itemName = MessageParser.argToString(args[1])
        val targetPokemon = MessageParser.argToString(args[2])
        BattleStateTracker.setItem(targetPokemon, itemName, ItemStatus.STOLEN)
    }

    fun extractCorrosiveGas(args: Array<out Any>) {
        if (args.size < 2) return
        val targetPokemon = MessageParser.argToString(args[0])
        val itemName = MessageParser.argToString(args[1])
        BattleStateTracker.setItem(targetPokemon, itemName, ItemStatus.CONSUMED)
    }

    fun extractHealingItem(args: Array<out Any>) {
        if (args.size < 2) return
        val pokemonName = MessageParser.argToString(args[0])
        val itemName = MessageParser.argToString(args[1])
        val status = if (itemName.lowercase().contains("berry")) {
            ItemStatus.CONSUMED
        } else {
            ItemStatus.HELD
        }
        BattleStateTracker.setItem(pokemonName, itemName, status)
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Faint, Switch, Transform Handling
    // ═════════════════════════════════════════════════════════════════════════

    fun markPokemonFainted(args: Array<out Any>) {
        if (args.isEmpty()) return

        val pokemonName = MessageParser.argToString(args[0])
        CobblemonExtendedBattleUI.LOGGER.debug("StateUpdater: Pokemon fainted - $pokemonName")

        BattleStateTracker.markAsKO(pokemonName)
        TeamIndicatorUI.markPokemonAsKO(pokemonName)
        BattleStateTracker.clearPokemonStatsByName(pokemonName)
        BattleStateTracker.clearPokemonVolatilesByName(pokemonName)
    }

    fun clearPokemonState(args: Array<out Any>) {
        if (args.isEmpty()) return

        val pokemonName = MessageParser.argToString(args[0])
        CobblemonExtendedBattleUI.LOGGER.debug("StateUpdater: Switch/drag detected for '$pokemonName' - clearing transform and form state")

        BattleStateTracker.clearPokemonStatsByName(pokemonName)
        BattleStateTracker.clearPokemonVolatilesByName(pokemonName)
        BattleStateTracker.clearTransformStatusByName(pokemonName)
        TeamIndicatorUI.clearTransformStatus(pokemonName)
        BattleStateTracker.restoreOriginalTypes(pokemonName)
        BattleStateTracker.clearCurrentForm(pokemonName)
    }

    fun markPokemonTransformed(args: Array<out Any>) {
        if (args.size < 2) {
            CobblemonExtendedBattleUI.LOGGER.debug("StateUpdater: Transform message needs 2 args, got ${args.size}")
            return
        }

        val transformerName = MessageParser.argToString(args[0])
        val targetName = MessageParser.argToString(args[1])

        CobblemonExtendedBattleUI.LOGGER.debug("StateUpdater: Transform detected - '$transformerName' transformed into '$targetName'")

        BattleStateTracker.markAsTransformed(transformerName)
        TeamIndicatorUI.markPokemonAsTransformed(transformerName, targetName)
    }
}
