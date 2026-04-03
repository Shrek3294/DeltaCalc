package com.cobblemonextendedbattleui.ui.panel

import com.cobblemonextendedbattleui.BattleInfoPanel.PokemonBattleData
import com.cobblemonextendedbattleui.BattleStateTracker
import com.cobblemonextendedbattleui.UIUtils

/**
 * Calculates content heights for BattleInfoPanel in both expanded and collapsed modes.
 * Pure calculation logic — no rendering or state mutation.
 */
object PanelLayoutCalculator {

    /**
     * Calculate total content height for expanded mode.
     */
    fun calculateExpandedContentHeight(
        allyPokemonData: List<PokemonBattleData>,
        opponentPokemonData: List<PokemonBattleData>,
        panelWidth: Int,
        textScale: Float,
        lineHeight: Int,
        scrollbarWidth: Int,
        groupDividerHeight: Int,
        subLabelHeight: Int
    ): Int {
        // Always assume scrollbar for worst-case width
        val effectiveW = panelWidth - UIUtils.FRAME_INSET * 2 - scrollbarWidth - 2
        val contentWidth = effectiveW - UIUtils.CELL_PAD * 2

        val hasGlobal = hasFieldEffects()
        val hasAlly = hasAnySideConditions(true)
        val hasEnemy = hasAnySideConditions(false)
        val allPokemon = allyPokemonData + opponentPokemonData
        val hasPokemonEffects = allPokemon.any { it.hasAnyEffects() }
        val hasConditions = hasGlobal || hasAlly || hasEnemy

        if (!hasConditions && !hasPokemonEffects) {
            return UIUtils.CELL_VPAD_TOP + lineHeight + UIUtils.CELL_VPAD_BOTTOM
        }

        var height = UIUtils.CELL_VPAD_TOP

        if (hasGlobal) height += calculateFieldContentHeight(lineHeight)
        if (hasGlobal && (hasAlly || hasEnemy)) height += groupDividerHeight

        if (hasAlly) {
            height += subLabelHeight
            height += calculateSideContentHeight(true, lineHeight)
        }
        if (hasAlly && hasEnemy) height += groupDividerHeight

        if (hasEnemy) {
            height += subLabelHeight
            height += calculateSideContentHeight(false, lineHeight)
        }

        if (hasConditions && hasPokemonEffects) height += groupDividerHeight

        if (hasPokemonEffects) height += calculatePokemonContentHeight(allPokemon, contentWidth, textScale, lineHeight)

        height += UIUtils.CELL_VPAD_BOTTOM
        return height
    }

    /**
     * Calculate total content height for collapsed mode.
     */
    fun calculateCollapsedContentHeight(
        allyPokemonData: List<PokemonBattleData>,
        opponentPokemonData: List<PokemonBattleData>,
        panelWidth: Int,
        lineHeight: Int
    ): Int {
        var rowCount = 0

        val hasWeather = BattleStateTracker.weather != null
        val hasTerrain = BattleStateTracker.terrain != null
        val fieldCount = BattleStateTracker.getFieldConditions().size
        val playerConds = BattleStateTracker.getPlayerSideConditions()
        val oppConds = BattleStateTracker.getOpponentSideConditions()
        val allyEffectCount = allyPokemonData.count { it.hasAnyEffects() }
        val enemyEffectCount = opponentPokemonData.count { it.hasAnyEffects() }

        val hasAnyEffects = hasWeather || hasTerrain || fieldCount > 0 ||
                           playerConds.isNotEmpty() || oppConds.isNotEmpty() ||
                           allyEffectCount > 0 || enemyEffectCount > 0

        if (!hasAnyEffects) {
            return UIUtils.CELL_VPAD_TOP + lineHeight + UIUtils.CELL_VPAD_BOTTOM
        }

        var contentH = 0
        if (hasWeather) { contentH += lineHeight; rowCount++ }
        if (hasTerrain) { contentH += lineHeight; rowCount++ }
        contentH += fieldCount * lineHeight; rowCount += fieldCount
        if (playerConds.isNotEmpty()) { contentH += (lineHeight * 0.9).toInt(); rowCount++ }
        if (oppConds.isNotEmpty()) { contentH += (lineHeight * 0.9).toInt(); rowCount++ }
        if (allyEffectCount > 0) { contentH += (lineHeight * 0.9).toInt(); rowCount++ }
        if (enemyEffectCount > 0) { contentH += (lineHeight * 0.9).toInt(); rowCount++ }

        // Add dividers between rows
        val dividers = if (rowCount > 1) (rowCount - 1) * UIUtils.DIVIDER_H else 0

        return UIUtils.CELL_VPAD_TOP + contentH + dividers + UIUtils.CELL_VPAD_BOTTOM
    }

    /**
     * Check if any field effects (weather, terrain, field conditions) are active.
     */
    fun hasFieldEffects(): Boolean {
        return BattleStateTracker.weather != null ||
               BattleStateTracker.terrain != null ||
               BattleStateTracker.getFieldConditions().isNotEmpty()
    }

    /**
     * Check if any side conditions are active for the given side.
     */
    fun hasAnySideConditions(isPlayer: Boolean): Boolean {
        return if (isPlayer) BattleStateTracker.getPlayerSideConditions().isNotEmpty()
               else BattleStateTracker.getOpponentSideConditions().isNotEmpty()
    }

    // ─── Internal Helpers ───────────────────────────────────────────────────

    private fun calculateFieldContentHeight(lineHeight: Int): Int {
        val fieldCount = listOfNotNull(BattleStateTracker.weather, BattleStateTracker.terrain).size +
                         BattleStateTracker.getFieldConditions().size
        if (fieldCount == 0) return 0
        val dividers = if (fieldCount > 1) (fieldCount - 1) * UIUtils.DIVIDER_H else 0
        return fieldCount * lineHeight + dividers
    }

    private fun calculateSideContentHeight(isPlayer: Boolean, lineHeight: Int): Int {
        val count = if (isPlayer) BattleStateTracker.getPlayerSideConditions().size
                    else BattleStateTracker.getOpponentSideConditions().size
        if (count == 0) return 0
        val dividers = if (count > 1) (count - 1) * UIUtils.DIVIDER_H else 0
        return count * lineHeight + dividers
    }

    private fun calculatePokemonContentHeight(
        pokemonData: List<PokemonBattleData>,
        contentWidth: Int,
        textScale: Float,
        lineHeight: Int
    ): Int {
        val pokemonWithEffects = pokemonData.filter { it.hasAnyEffects() }
        if (pokemonWithEffects.isEmpty()) return 0

        var height = 0
        val charWidth = (5 * textScale).toInt()
        val indentX = (8 * textScale).toInt()
        val maxX = contentWidth

        for ((i, pokemon) in pokemonWithEffects.withIndex()) {
            if (i > 0) height += UIUtils.DIVIDER_H  // divider between pokemon

            height += (lineHeight * 0.95).toInt()  // Pokemon name

            if (pokemon.statChanges.isNotEmpty()) {
                var statX = indentX
                val sortedStats = pokemon.statChanges.entries.sortedBy { getStatSortOrder(it.key) }

                for ((stat, value) in sortedStats) {
                    val abbr = stat.abbr
                    val arrowCount = kotlin.math.abs(value)
                    val entryWidth = ((abbr.length * charWidth) + 2 + (arrowCount * charWidth) + (8 * textScale)).toInt()

                    if (statX + entryWidth > maxX && statX != indentX) {
                        height += (lineHeight * 0.9).toInt()
                        statX = indentX
                    }
                    statX += entryWidth
                }
                height += (lineHeight * 0.9).toInt()
            }

            if (pokemon.volatiles.isNotEmpty()) {
                var effectX = indentX

                for (volatileState in pokemon.volatiles) {
                    val volatile = volatileState.type
                    val turnsRemaining = BattleStateTracker.getVolatileTurnsRemaining(volatileState)
                    val display = if (turnsRemaining != null) {
                        "${volatile.icon} ${volatile.displayName} ($turnsRemaining)"
                    } else {
                        "${volatile.icon} ${volatile.displayName}"
                    }
                    val effectWidth = (display.length * charWidth * 0.8).toInt() + (10 * textScale).toInt()

                    if (effectX + effectWidth > maxX && effectX != indentX) {
                        height += (lineHeight * 0.85).toInt()
                        effectX = indentX
                    }
                    effectX += effectWidth
                }
                height += (lineHeight * 0.9).toInt()
            }
        }

        return height
    }

    internal fun getStatSortOrder(stat: BattleStateTracker.BattleStat): Int {
        return when (stat) {
            BattleStateTracker.BattleStat.ATTACK -> 0
            BattleStateTracker.BattleStat.DEFENSE -> 1
            BattleStateTracker.BattleStat.SPECIAL_ATTACK -> 2
            BattleStateTracker.BattleStat.SPECIAL_DEFENSE -> 3
            BattleStateTracker.BattleStat.SPEED -> 4
            BattleStateTracker.BattleStat.ACCURACY -> 5
            BattleStateTracker.BattleStat.EVASION -> 6
        }
    }
}
