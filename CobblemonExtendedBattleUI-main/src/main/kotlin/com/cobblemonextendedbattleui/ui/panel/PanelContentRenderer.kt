package com.cobblemonextendedbattleui.ui.panel

import com.cobblemonextendedbattleui.BattleInfoPanel.PokemonBattleData
import com.cobblemonextendedbattleui.BattleInfoPanel.SideName
import com.cobblemonextendedbattleui.BattleStateTracker
import com.cobblemonextendedbattleui.UIUtils
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text

/**
 * Renders BattleInfoPanel content (expanded and collapsed modes).
 * Handles field effects, side conditions, and per-Pokemon stat/volatile display.
 */
object PanelContentRenderer {

    // ═══════════════════════════════════════════════════════════════════════════
    // UI Translation Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private object UI {
        fun translate(key: String): String = Text.translatable("cobblemonextendedbattleui.ui.$key").string

        val noEffects: String get() = translate("no_effects")
        val affected: String get() = translate("affected")
        val effectSingular: String get() = translate("effect")
        val effectPlural: String get() = translate("effects")
        val pokemonSingular: String get() = translate("pokemon_count.singular")
        val pokemonPlural: String get() = translate("pokemon_count.plural")

        fun effectCount(count: Int): String = if (count == 1) effectSingular else effectPlural
        fun pokemonCount(count: Int): String = if (count == 1) "$count $pokemonSingular" else "$count $pokemonPlural"
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Expanded mode content
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Renders all expanded content groups sequentially within the single fill cell.
     * Groups are separated by group dividers (row divider + extra spacing).
     */
    fun renderExpandedContent(
        context: DrawContext,
        x: Int,
        startY: Int,
        width: Int,
        allyPokemonData: List<PokemonBattleData>,
        opponentPokemonData: List<PokemonBattleData>,
        playerSideName: SideName,
        opponentSideName: SideName,
        textScale: Float,
        lineHeight: Int,
        groupDividerExtra: Int,
        sectionLabelScale: Float,
        subLabelHeight: Int,
        textDim: Int,
        textLight: Int,
        accentPlayer: Int,
        accentOpponent: Int,
        accentField: Int,
        statBoost: Int,
        statDrop: Int,
        colorTransform: (Int) -> Int,
        scissorBounds: UIUtils.ScissorBounds,
        drawTextClipped: (DrawContext, String, Float, Float, Int, Float) -> Unit,
        drawGroupDivider: (DrawContext, Int, Int, Int) -> Int
    ) {
        val hasGlobal = PanelLayoutCalculator.hasFieldEffects()
        val hasAlly = PanelLayoutCalculator.hasAnySideConditions(true)
        val hasEnemy = PanelLayoutCalculator.hasAnySideConditions(false)
        val allPokemon = allyPokemonData + opponentPokemonData
        val hasPokemonEffects = allPokemon.any { it.hasAnyEffects() }
        val hasConditions = hasGlobal || hasAlly || hasEnemy

        if (!hasConditions && !hasPokemonEffects) {
            drawTextClipped(context, UI.noEffects, x.toFloat(), startY.toFloat(), textDim, 0.8f * textScale)
            return
        }

        val dividerX = x - UIUtils.CELL_PAD
        val dividerW = width + UIUtils.CELL_PAD * 2
        var curY = startY

        if (hasGlobal) {
            curY = renderFieldContent(context, x, curY, width, textScale, lineHeight, textLight, textDim, accentField, colorTransform, drawTextClipped)
        }

        if (hasGlobal && (hasAlly || hasEnemy)) {
            curY = drawGroupDivider(context, dividerX, curY, dividerW)
        }

        if (hasAlly) {
            drawTextClipped(context, playerSideName.name, x.toFloat(), curY.toFloat(), accentPlayer, sectionLabelScale * textScale)
            curY += subLabelHeight
            curY = renderSideContent(context, x, curY, width, isPlayer = true, textScale, lineHeight, textLight, textDim, accentPlayer, colorTransform, drawTextClipped)
        }

        if (hasAlly && hasEnemy) {
            curY = drawGroupDivider(context, dividerX, curY, dividerW)
        }

        if (hasEnemy) {
            drawTextClipped(context, opponentSideName.name, x.toFloat(), curY.toFloat(), accentOpponent, sectionLabelScale * textScale)
            curY += subLabelHeight
            curY = renderSideContent(context, x, curY, width, isPlayer = false, textScale, lineHeight, textLight, textDim, accentOpponent, colorTransform, drawTextClipped)
        }

        if (hasConditions && hasPokemonEffects) {
            curY = drawGroupDivider(context, dividerX, curY, dividerW)
        }

        if (hasPokemonEffects) {
            renderPokemonContent(context, x, curY, width, allPokemon, textScale, lineHeight, textLight, accentPlayer, accentOpponent, statBoost, statDrop, colorTransform, drawTextClipped)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Collapsed mode content
    // ═══════════════════════════════════════════════════════════════════════════

    fun renderCollapsedContent(
        context: DrawContext,
        x: Int,
        startY: Int,
        width: Int,
        allyPokemonData: List<PokemonBattleData>,
        opponentPokemonData: List<PokemonBattleData>,
        playerSideName: SideName,
        opponentSideName: SideName,
        textScale: Float,
        lineHeight: Int,
        textDim: Int,
        textLight: Int,
        accentPlayer: Int,
        accentOpponent: Int,
        accentField: Int,
        colorTransform: (Int) -> Int,
        drawTextClipped: (DrawContext, String, Float, Float, Int, Float) -> Unit,
        drawConditionLine: (DrawContext, Int, Int, Int, String, String, String, Int) -> Unit
    ) {
        var currentY = startY

        val hasWeather = BattleStateTracker.weather != null
        val hasTerrain = BattleStateTracker.terrain != null
        val fieldConditions = BattleStateTracker.getFieldConditions()
        val playerConds = BattleStateTracker.getPlayerSideConditions()
        val oppConds = BattleStateTracker.getOpponentSideConditions()
        val allyEffectCount = allyPokemonData.count { it.hasAnyEffects() }
        val enemyEffectCount = opponentPokemonData.count { it.hasAnyEffects() }

        val hasAnyEffects = hasWeather || hasTerrain || fieldConditions.isNotEmpty() ||
                           playerConds.isNotEmpty() || oppConds.isNotEmpty() ||
                           allyEffectCount > 0 || enemyEffectCount > 0

        if (!hasAnyEffects) {
            drawTextClipped(context, UI.noEffects, x.toFloat(), currentY.toFloat(), textDim, 0.8f * textScale)
            return
        }

        val dividerX = x - UIUtils.CELL_PAD
        val dividerW = width + UIUtils.CELL_PAD * 2
        var rowIndex = 0

        BattleStateTracker.weather?.let { w ->
            if (rowIndex > 0) {
                UIUtils.drawPopupRowDivider(context, dividerX, dividerW, currentY, colorTransform)
                currentY += UIUtils.DIVIDER_H
            }
            val turns = BattleStateTracker.getWeatherTurnsRemaining() ?: "?"
            drawConditionLine(context, x, currentY, width, w.type.icon, w.type.displayName, turns, accentField)
            currentY += lineHeight
            rowIndex++
        }

        BattleStateTracker.terrain?.let { t ->
            if (rowIndex > 0) {
                UIUtils.drawPopupRowDivider(context, dividerX, dividerW, currentY, colorTransform)
                currentY += UIUtils.DIVIDER_H
            }
            val turns = BattleStateTracker.getTerrainTurnsRemaining() ?: "?"
            drawConditionLine(context, x, currentY, width, t.type.icon, t.type.displayName, turns, accentField)
            currentY += lineHeight
            rowIndex++
        }

        fieldConditions.forEach { (type, _) ->
            if (rowIndex > 0) {
                UIUtils.drawPopupRowDivider(context, dividerX, dividerW, currentY, colorTransform)
                currentY += UIUtils.DIVIDER_H
            }
            val turns = BattleStateTracker.getFieldConditionTurnsRemaining(type) ?: "?"
            drawConditionLine(context, x, currentY, width, type.icon, type.displayName, turns, accentField)
            currentY += lineHeight
            rowIndex++
        }

        if (playerConds.isNotEmpty()) {
            if (rowIndex > 0) {
                UIUtils.drawPopupRowDivider(context, dividerX, dividerW, currentY, colorTransform)
                currentY += UIUtils.DIVIDER_H
            }
            drawTextClipped(context, "${playerSideName.name}: ${playerConds.size} ${UI.effectCount(playerConds.size)}",
                x.toFloat(), currentY.toFloat(), accentPlayer, 0.8f * textScale)
            currentY += (lineHeight * 0.9).toInt()
            rowIndex++
        }

        if (oppConds.isNotEmpty()) {
            if (rowIndex > 0) {
                UIUtils.drawPopupRowDivider(context, dividerX, dividerW, currentY, colorTransform)
                currentY += UIUtils.DIVIDER_H
            }
            drawTextClipped(context, "${opponentSideName.name}: ${oppConds.size} ${UI.effectCount(oppConds.size)}",
                x.toFloat(), currentY.toFloat(), accentOpponent, 0.8f * textScale)
            currentY += (lineHeight * 0.9).toInt()
            rowIndex++
        }

        if (allyEffectCount > 0) {
            if (rowIndex > 0) {
                UIUtils.drawPopupRowDivider(context, dividerX, dividerW, currentY, colorTransform)
                currentY += UIUtils.DIVIDER_H
            }
            drawTextClipped(context, "${playerSideName.name}: ${UI.pokemonCount(allyEffectCount)} ${UI.affected}",
                x.toFloat(), currentY.toFloat(), accentPlayer, 0.8f * textScale)
            currentY += (lineHeight * 0.9).toInt()
            rowIndex++
        }

        if (enemyEffectCount > 0) {
            if (rowIndex > 0) {
                UIUtils.drawPopupRowDivider(context, dividerX, dividerW, currentY, colorTransform)
                currentY += UIUtils.DIVIDER_H
            }
            drawTextClipped(context, "${opponentSideName.name}: ${UI.pokemonCount(enemyEffectCount)} ${UI.affected}",
                x.toFloat(), currentY.toFloat(), accentOpponent, 0.8f * textScale)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Content renderers (expanded mode sub-sections)
    // ═══════════════════════════════════════════════════════════════════════════

    private fun renderFieldContent(
        context: DrawContext,
        x: Int,
        startY: Int,
        width: Int,
        textScale: Float,
        lineHeight: Int,
        textLight: Int,
        textDim: Int,
        accentField: Int,
        colorTransform: (Int) -> Int,
        drawTextClipped: (DrawContext, String, Float, Float, Int, Float) -> Unit
    ): Int {
        var sy = startY
        var rowIndex = 0

        BattleStateTracker.weather?.let { w ->
            val turns = BattleStateTracker.getWeatherTurnsRemaining() ?: "?"
            drawConditionLineExpanded(context, x, sy, width, w.type.icon, w.type.displayName, turns, accentField, textLight, textDim, textScale, drawTextClipped)
            sy += lineHeight
            rowIndex++
        }

        BattleStateTracker.terrain?.let { t ->
            if (rowIndex > 0) {
                UIUtils.drawPopupRowDivider(context, x - UIUtils.CELL_PAD, width + UIUtils.CELL_PAD * 2, sy, colorTransform)
                sy += UIUtils.DIVIDER_H
            }
            val turns = BattleStateTracker.getTerrainTurnsRemaining() ?: "?"
            drawConditionLineExpanded(context, x, sy, width, t.type.icon, t.type.displayName, turns, accentField, textLight, textDim, textScale, drawTextClipped)
            sy += lineHeight
            rowIndex++
        }

        BattleStateTracker.getFieldConditions().forEach { (type, _) ->
            if (rowIndex > 0) {
                UIUtils.drawPopupRowDivider(context, x - UIUtils.CELL_PAD, width + UIUtils.CELL_PAD * 2, sy, colorTransform)
                sy += UIUtils.DIVIDER_H
            }
            val turns = BattleStateTracker.getFieldConditionTurnsRemaining(type) ?: "?"
            drawConditionLineExpanded(context, x, sy, width, type.icon, type.displayName, turns, accentField, textLight, textDim, textScale, drawTextClipped)
            sy += lineHeight
            rowIndex++
        }

        return sy
    }

    private fun renderSideContent(
        context: DrawContext,
        x: Int,
        startY: Int,
        width: Int,
        isPlayer: Boolean,
        textScale: Float,
        lineHeight: Int,
        textLight: Int,
        textDim: Int,
        accentColor: Int,
        colorTransform: (Int) -> Int,
        drawTextClipped: (DrawContext, String, Float, Float, Int, Float) -> Unit
    ): Int {
        var sy = startY
        val conditions = if (isPlayer) BattleStateTracker.getPlayerSideConditions() else BattleStateTracker.getOpponentSideConditions()

        var rowIndex = 0
        conditions.forEach { (type, state) ->
            if (rowIndex > 0) {
                UIUtils.drawPopupRowDivider(context, x - UIUtils.CELL_PAD, width + UIUtils.CELL_PAD * 2, sy, colorTransform)
                sy += UIUtils.DIVIDER_H
            }
            val turnsRemaining = BattleStateTracker.getSideConditionTurnsRemaining(isPlayer, type)
            val info = when {
                turnsRemaining != null -> turnsRemaining
                state.stacks > 1 -> "x${state.stacks}"
                else -> ""
            }
            drawConditionLineExpanded(context, x, sy, width, type.icon, type.displayName, info, accentColor, textLight, textDim, textScale, drawTextClipped)
            sy += lineHeight
            rowIndex++
        }

        return sy
    }

    private fun renderPokemonContent(
        context: DrawContext,
        x: Int,
        startY: Int,
        width: Int,
        pokemonData: List<PokemonBattleData>,
        textScale: Float,
        lineHeight: Int,
        textLight: Int,
        accentPlayer: Int,
        accentOpponent: Int,
        statBoost: Int,
        statDrop: Int,
        colorTransform: (Int) -> Int,
        drawTextClipped: (DrawContext, String, Float, Float, Int, Float) -> Unit
    ): Int {
        var sy = startY

        var pokemonIndex = 0
        for (pokemon in pokemonData) {
            if (!pokemon.hasAnyEffects()) continue

            if (pokemonIndex > 0) {
                UIUtils.drawPopupRowDivider(context, x - UIUtils.CELL_PAD, width + UIUtils.CELL_PAD * 2, sy, colorTransform)
                sy += UIUtils.DIVIDER_H
            }

            // Pokemon name (colored by side)
            val nameColor = if (pokemon.isAlly) accentPlayer else accentOpponent
            drawTextClipped(context, pokemon.name, x.toFloat(), sy.toFloat(), nameColor, 0.85f * textScale)
            sy += (lineHeight * 0.95).toInt()

            // Stat changes
            if (pokemon.statChanges.isNotEmpty()) {
                val sortedStats = pokemon.statChanges.entries.sortedBy { PanelLayoutCalculator.getStatSortOrder(it.key) }
                val charWidth = (5 * textScale).toInt()
                val indentX = x + (8 * textScale).toInt()

                var statX = indentX
                for ((stat, value) in sortedStats) {
                    val abbr = stat.abbr
                    val arrows = if (value > 0) "\u2191".repeat(value) else "\u2193".repeat(-value)
                    val color = if (value > 0) statBoost else statDrop
                    val entryWidth = ((abbr.length * charWidth) + 2 + (arrows.length * charWidth) + (8 * textScale)).toInt()

                    if (statX + entryWidth > x + width && statX != indentX) {
                        sy += (lineHeight * 0.9).toInt()
                        statX = indentX
                    }

                    drawTextClipped(context, abbr, statX.toFloat(), sy.toFloat(), textLight, 0.75f * textScale)
                    statX += (abbr.length * charWidth) + 2
                    drawTextClipped(context, arrows, statX.toFloat(), sy.toFloat(), color, 0.75f * textScale)
                    statX += (arrows.length * charWidth) + (8 * textScale).toInt()
                }
                sy += (lineHeight * 0.9).toInt()
            }

            // Volatile effects
            if (pokemon.volatiles.isNotEmpty()) {
                val charWidth = (5 * textScale).toInt()
                val indentX = x + (8 * textScale).toInt()
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

                    if (effectX + effectWidth > x + width && effectX != indentX) {
                        sy += (lineHeight * 0.85).toInt()
                        effectX = indentX
                    }

                    val effectColor = if (volatile.isNegative) statDrop else statBoost
                    drawTextClipped(context, display, effectX.toFloat(), sy.toFloat(), effectColor, 0.75f * textScale)
                    effectX += effectWidth
                }
                sy += (lineHeight * 0.9).toInt()
            }

            pokemonIndex++
        }

        return sy
    }

    // ─── Internal Helpers ───────────────────────────────────────────────────

    private fun drawConditionLineExpanded(
        context: DrawContext,
        x: Int,
        y: Int,
        width: Int,
        icon: String,
        name: String,
        info: String,
        accentColor: Int,
        textLight: Int,
        textDim: Int,
        textScale: Float,
        drawTextClipped: (DrawContext, String, Float, Float, Int, Float) -> Unit
    ) {
        val charWidth = (5 * textScale).toInt()
        drawTextClipped(context, icon, x.toFloat(), y.toFloat(), accentColor, 0.8f * textScale)
        drawTextClipped(context, name, (x + (14 * textScale).toInt()).toFloat(), y.toFloat(), textLight, 0.8f * textScale)
        if (info.isNotEmpty()) {
            val infoWidth = info.length * charWidth
            drawTextClipped(context, info, (x + width - infoWidth).toFloat(), y.toFloat(), textDim, 0.75f * textScale)
        }
    }
}
