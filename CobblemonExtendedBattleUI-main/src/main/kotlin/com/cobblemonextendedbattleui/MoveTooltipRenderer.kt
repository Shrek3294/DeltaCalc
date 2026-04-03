package com.cobblemonextendedbattleui

import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.categories.DamageCategories
import com.cobblemon.mod.common.api.types.ElementalType
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.battles.ai.strongBattleAI.AIUtility
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.pokemon.Pokemon
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.util.InputUtil
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.text.Text

import org.lwjgl.glfw.GLFW
import java.util.UUID

/**
 * Renders tooltips for moves in the Fight selection menu.
 * Uses cell-based layout with 9-slice texture frame tinted by move type color.
 */
object MoveTooltipRenderer {

    // ═══════════════════════════════════════════════════════════════
    // Layout constants (shared constants imported from UIUtils)
    // ═══════════════════════════════════════════════════════════════

    private const val TOOLTIP_BASE_WIDTH = 210
    private const val LEFT_COL_RATIO = 0.50f

    private const val TOOLTIP_BASE_LINE_HEIGHT = 10
    private const val TOOLTIP_FONT_SCALE = 0.85f

    // ═══════════════════════════════════════════════════════════════
    // Text colors
    // ═══════════════════════════════════════════════════════════════

    private val TOOLTIP_LABEL = UIUtils.color(140, 150, 165)
    private val TOOLTIP_DIM = UIUtils.color(100, 110, 120)

    private val COLOR_POWER = UIUtils.color(255, 140, 90)
    private val COLOR_ACCURACY = UIUtils.color(100, 180, 255)
    private val COLOR_PP = UIUtils.color(255, 210, 80)
    private val COLOR_PP_LOW = UIUtils.color(255, 100, 80)

    private val COLOR_PHYSICAL = UIUtils.color(255, 150, 100)
    private val COLOR_SPECIAL = UIUtils.color(160, 140, 255)
    private val COLOR_STATUS = UIUtils.color(170, 170, 180)

    private val COLOR_PRIORITY_POSITIVE = UIUtils.color(100, 220, 200)
    private val COLOR_PRIORITY_NEGATIVE = UIUtils.color(220, 100, 120)

    private val COLOR_CRIT = UIUtils.color(255, 200, 50)
    private val COLOR_EFFECT = UIUtils.color(200, 160, 255)

    private val SUPER_EFFECTIVE_4X = UIUtils.color(50, 220, 50)
    private val SUPER_EFFECTIVE_2X = UIUtils.color(80, 200, 80)
    private val NEUTRAL = UIUtils.color(180, 185, 190)
    private val NOT_EFFECTIVE = UIUtils.color(220, 100, 80)
    private val IMMUNE = UIUtils.color(120, 120, 120)

    // ═══════════════════════════════════════════════════════════════
    // State
    // ═══════════════════════════════════════════════════════════════

    data class MoveTileBounds(
        val x: Float, val y: Float, val width: Int, val height: Int,
        val moveTemplate: MoveTemplate, val currentPp: Int, val maxPp: Int
    )

    private val moveTileBounds = mutableListOf<MoveTileBounds>()
    private var hoveredMove: MoveTileBounds? = null
    private var activePokemonUuid: UUID? = null
    private var wasIncreaseFontKeyPressed = false
    private var wasDecreaseFontKeyPressed = false
    private var mixinActiveLastFrame = false

    fun setActivePokemonUuid(uuid: UUID?) {
        activePokemonUuid = uuid
    }

    /**
     * Called each HUD render frame (before Screen render) to detect when the
     * BattleMoveSelection mixin has stopped calling [clear]. Since HUD renders
     * before Screen, this uses a 2-frame detection window: if the mixin didn't
     * call [clear] during the previous frame's Screen render, hoveredMove is stale.
     */
    fun resetIfStale() {
        if (!mixinActiveLastFrame) {
            hoveredMove = null
        }
        mixinActiveLastFrame = false
    }

    fun clear() {
        moveTileBounds.clear()
        hoveredMove = null
        mixinActiveLastFrame = true
    }

    fun registerMoveTile(
        x: Float, y: Float, width: Int, height: Int,
        moveTemplate: MoveTemplate, currentPp: Int, maxPp: Int
    ) {
        moveTileBounds.add(MoveTileBounds(x, y, width, height, moveTemplate, currentPp, maxPp))
    }

    fun updateHoverState(mouseX: Int, mouseY: Int) {
        hoveredMove = moveTileBounds.find { b ->
            mouseX >= b.x && mouseX <= b.x + b.width &&
                mouseY >= b.y && mouseY <= b.y + b.height
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Main render
    // ═══════════════════════════════════════════════════════════════

    fun renderTooltip(context: DrawContext) {
        val move = hoveredMove ?: return
        if (!PanelConfig.enableMoveTooltips) return

        val mc = MinecraftClient.getInstance()
        val screenWidth = mc.window.scaledWidth
        val screenHeight = mc.window.scaledHeight
        val tr = mc.textRenderer
        val fontScale = TOOLTIP_FONT_SCALE * PanelConfig.moveTooltipFontScale
        val lineH = (TOOLTIP_BASE_LINE_HEIGHT * fontScale).toInt().coerceAtLeast(7)

        val tooltipWidth = (TOOLTIP_BASE_WIDTH * PanelConfig.moveTooltipFontScale).toInt()
        val contentWidth = tooltipWidth - UIUtils.FRAME_INSET * 2

        // ── Compute data ──
        val data = computeMoveData(move)

        // ── Calculate layout ──
        val vPad = UIUtils.CELL_VPAD_TOP + UIUtils.CELL_VPAD_BOTTOM

        // Header: 1 line (name + type · category)
        val headerCellH = vPad + lineH

        // Power row: full-width, with modifier text inline
        val powerCellH = vPad + lineH

        // Detail grid: remaining stats in 2-column layout, filled left-to-right
        val detailItems = mutableListOf(
            StatEntry("PP", data.ppText, data.ppColor),
            StatEntry("Accuracy", data.accuracyText, COLOR_ACCURACY)
        )
        if (data.critText != null) detailItems.add(StatEntry("Crit", data.critText, COLOR_CRIT))
        if (data.effectText != null) detailItems.add(StatEntry("Effect", data.effectText, COLOR_EFFECT))
        if (data.priorityText != null) detailItems.add(StatEntry("Priority", data.priorityText, data.priorityColor))
        // Odd count: first item full-width, rest paired. Even: all paired.
        val hasHead = detailItems.size % 2 == 1
        val headCellH = if (hasHead) vPad + lineH else 0
        val headTotalH = if (hasHead) headCellH + UIUtils.CELL_GAP else 0
        val pairedStart = if (hasHead) 1 else 0
        val pairedCount = detailItems.size - pairedStart
        val pairedRows = pairedCount / 2
        val pairedCellH = if (pairedRows > 0) vPad + pairedRows * lineH + (pairedRows - 1) * UIUtils.DIVIDER_H else 0

        // Description cell (conditional)
        val descLines = if (move.moveTemplate.description.string.isNotEmpty())
            wrapText(move.moveTemplate.description.string, tooltipWidth, fontScale, tr)
        else emptyList()
        val hasDesc = descLines.isNotEmpty()
        val descCellH = if (hasDesc) vPad + descLines.size * lineH else 0
        val descTotalH = if (hasDesc) UIUtils.CELL_GAP + descCellH else 0

        // Effectiveness cell (conditional)
        val effectEntries = computeEffectiveness(move)
        val hasEffect = effectEntries.isNotEmpty()
        val effectCellH = if (hasEffect) vPad + effectEntries.size * lineH else 0
        val effectTotalH = if (hasEffect) UIUtils.CELL_GAP + effectCellH else 0

        val totalHeight = UIUtils.FRAME_INSET + headerCellH + UIUtils.CELL_GAP + powerCellH +
            UIUtils.CELL_GAP + headTotalH + pairedCellH + descTotalH + effectTotalH + UIUtils.FRAME_INSET

        // ── Position above the move tile ──
        var px = move.x.toInt() + (move.width / 2) - (tooltipWidth / 2)
        var py = move.y.toInt() - totalHeight - 4
        if (py < 4) py = move.y.toInt() + move.height + 4
        px = px.coerceIn(4, screenWidth - tooltipWidth - 4)
        py = py.coerceIn(4, screenHeight - totalHeight - 4)

        // ── Render ──
        context.matrices.push()
        context.matrices.translate(0.0, 0.0, UIUtils.POPUP_Z_OFFSET)

        // 9-slice frame tinted with move type color
        val r = ((data.typeColor shr 16) and 0xFF) / 255f
        val g = ((data.typeColor shr 8) and 0xFF) / 255f
        val b = (data.typeColor and 0xFF) / 255f
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(r, g, b, 1f)
        UIUtils.renderPopupFrame(context, px, py, tooltipWidth, totalHeight)
        context.draw()
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

        val cellX = px + UIUtils.FRAME_INSET
        var curY = py + UIUtils.FRAME_INSET

        // Header cell
        drawCell(context, cellX, curY, contentWidth, headerCellH)
        renderHeaderText(context, data, cellX + UIUtils.CELL_PAD, curY + UIUtils.CELL_VPAD_TOP,
            contentWidth - UIUtils.CELL_PAD * 2, fontScale, tr)
        curY += headerCellH + UIUtils.CELL_GAP

        // Power row (full-width)
        drawCell(context, cellX, curY, contentWidth, powerCellH)
        val powerY = (curY + UIUtils.CELL_VPAD_TOP).toFloat()
        draw(context, "Power", (cellX + UIUtils.CELL_PAD).toFloat(), powerY, TOOLTIP_LABEL, fontScale)
        val powerLabelW = tr.getWidth("Power") * fontScale
        draw(context, "  ${data.powerText}", (cellX + UIUtils.CELL_PAD) + powerLabelW, powerY, COLOR_POWER, fontScale)
        if (data.modifierText != null) {
            drawRightAligned(context, data.modifierText, cellX + contentWidth - UIUtils.CELL_PAD,
                powerY, SUPER_EFFECTIVE_2X, fontScale, tr)
        }
        curY += powerCellH + UIUtils.CELL_GAP

        // Detail grid: odd → head full-width + paired rows; even → all paired
        val leftColW = ((contentWidth - UIUtils.COL_GAP) * LEFT_COL_RATIO).toInt()
        val geo = GridGeometry(
            leftCellX = cellX, leftCellW = leftColW,
            rightCellX = cellX + leftColW + UIUtils.COL_GAP,
            rightCellW = contentWidth - UIUtils.COL_GAP - leftColW
        )

        // Head item (first item, full-width when odd count)
        if (hasHead) {
            val head = detailItems[0]
            drawCell(context, cellX, curY, contentWidth, headCellH)
            val headY = (curY + UIUtils.CELL_VPAD_TOP).toFloat()
            draw(context, head.label, (cellX + UIUtils.CELL_PAD).toFloat(), headY, TOOLTIP_LABEL, fontScale)
            drawRightAligned(context, head.value, cellX + contentWidth - UIUtils.CELL_PAD,
                headY, head.color, fontScale, tr)
            curY += headCellH + UIUtils.CELL_GAP
        }

        // Paired items in 2-column grid
        if (pairedRows > 0) {
            val pairedItems = detailItems.subList(pairedStart, detailItems.size)
            drawCell(context, geo.leftCellX, curY, geo.leftCellW, pairedCellH)
            drawCell(context, geo.rightCellX, curY, geo.rightCellW, pairedCellH)
            renderDetailGrid(context, pairedItems, geo,
                curY + UIUtils.CELL_VPAD_TOP, pairedRows, lineH, fontScale, tr)
            curY += pairedCellH
        }

        // Description cell (conditional)
        if (hasDesc) {
            curY += UIUtils.CELL_GAP
            drawCell(context, cellX, curY, contentWidth, descCellH)
            var descY = (curY + UIUtils.CELL_VPAD_TOP).toFloat()
            for (line in descLines) {
                draw(context, line, (cellX + UIUtils.CELL_PAD).toFloat(), descY, TOOLTIP_DIM, fontScale)
                descY += lineH
            }
            curY += descCellH
        }

        // Effectiveness cell (conditional)
        if (hasEffect) {
            curY += UIUtils.CELL_GAP
            drawCell(context, cellX, curY, contentWidth, effectCellH)
            var effY = (curY + UIUtils.CELL_VPAD_TOP).toFloat()
            for (entry in effectEntries) {
                draw(context, entry.text, (cellX + UIUtils.CELL_PAD).toFloat(), effY, entry.color, fontScale)
                effY += lineH
            }
        }

        context.matrices.pop()
    }

    // ═══════════════════════════════════════════════════════════════
    // Computed move data
    // ═══════════════════════════════════════════════════════════════

    private data class MoveData(
        val moveName: String,
        val typeColor: Int,
        val typeName: String,
        val categoryName: String,
        val categoryColor: Int,
        val powerText: String,
        val accuracyText: String,
        val ppText: String,
        val ppColor: Int,
        val critText: String?,
        val effectText: String?,
        val priorityText: String?,
        val priorityColor: Int,
        val modifierText: String?
    )

    private fun computeMoveData(move: MoveTileBounds): MoveData {
        val template = move.moveTemplate

        val effectiveType = getMoveEffectiveType(move)
        val weatherBallType = getWeatherBallEffectiveType(template)
        val playerAbility = getPlayerPokemonAbility()
        val displayType = weatherBallType ?: effectiveType
        val typeColor = UIUtils.getTypeColor(displayType)

        val categoryColor = when (template.damageCategory) {
            DamageCategories.PHYSICAL -> COLOR_PHYSICAL
            DamageCategories.SPECIAL -> COLOR_SPECIAL
            else -> COLOR_STATUS
        }

        // ── Power + modifiers ──
        var powerText = "--"
        var modifierText: String? = null

        if (template.power > 0) {
            var basePower = template.power.toInt()
            val playerTypes = getPlayerPokemonTypes()
            val dynamicPowerInfo = getDynamicPowerInfo(template)
            if (dynamicPowerInfo != null) basePower = dynamicPowerInfo.power

            val stabCheckType = weatherBallType ?: effectiveType
            val hasStab = playerTypes.any { it.name == stabCheckType.name }
            val hasSheerForce = playerAbility == "sheerforce" &&
                template.effectChances.isNotEmpty() && template.effectChances[0] > 0
            val itemBoost = getHeldItemPowerBoost(stabCheckType.name)

            var effectivePower = basePower.toDouble()
            val modifiers = mutableListOf<String>()
            if (dynamicPowerInfo?.reason != null) modifiers.add(dynamicPowerInfo.reason)
            if (hasStab) { effectivePower *= 1.5; modifiers.add("STAB") }
            if (hasSheerForce) { effectivePower *= 1.3; modifiers.add("Sheer Force") }
            if (itemBoost != null) { effectivePower *= itemBoost.multiplier; modifiers.add(itemBoost.displayName) }

            val displayBasePower = template.power.toInt()
            powerText = if (dynamicPowerInfo != null && basePower != displayBasePower) {
                "$displayBasePower \u2192 $basePower"
            } else {
                "$basePower"
            }

            if (modifiers.isNotEmpty()) {
                modifierText = "${modifiers.joinToString(" + ")} \u2192 ${effectivePower.toInt()}"
            }
        }

        // ── Accuracy ──
        val accuracyText = if (template.accuracy > 0) "${template.accuracy.toInt()}%" else "--"

        // ── PP ──
        val ppRatio = move.currentPp.toFloat() / move.maxPp.coerceAtLeast(1)
        val ppColor = if (ppRatio <= 0.25f) COLOR_PP_LOW else COLOR_PP

        // ── Crit ──
        var critText: String? = null
        if (template.power > 0) {
            val baseCritRatio = template.critRatio
            val hasSuperLuck = playerAbility == "superluck"
            val heldItemId = getPlayerPokemonHeldItemId()
            val hasCritItem = heldItemId == "cobblemon:scope_lens" || heldItemId == "cobblemon:razor_claw"
            var critBonus = 0
            if (hasSuperLuck) critBonus++
            if (hasCritItem) critBonus++
            val shouldShowCrit = PanelConfig.showBaseCritRate || baseCritRatio > 1.0 || critBonus > 0
            if (shouldShowCrit) {
                val basePct = critRatioToPercent(baseCritRatio)
                if (critBonus > 0) {
                    val effectiveCritRatio = (baseCritRatio + critBonus).coerceAtMost(4.0)
                    critText = "$basePct \u2192 ${critRatioToPercent(effectiveCritRatio)}"
                } else {
                    critText = basePct
                }
            }
        }

        // ── Effect ──
        var effectText: String? = null
        if (template.effectChances.isNotEmpty() && template.effectChances[0] > 0) {
            val baseEffect = template.effectChances[0]
            effectText = when (playerAbility) {
                "sheerforce" -> "N/A (Sheer Force)"
                "serenegrace" -> {
                    val doubled = (baseEffect * 2).coerceAtMost(100.0).toInt()
                    "${baseEffect.toInt()}% \u2192 $doubled%"
                }
                else -> "${baseEffect.toInt()}%"
            }
        }

        // ── Priority ──
        var priorityText: String? = null
        var priorityColor = COLOR_PRIORITY_POSITIVE
        if (template.priority != 0) {
            val sign = if (template.priority > 0) "+" else ""
            val label = if (template.priority > 0) "Fast" else "Slow"
            priorityText = "$sign${template.priority} ($label)"
            priorityColor = if (template.priority > 0) COLOR_PRIORITY_POSITIVE else COLOR_PRIORITY_NEGATIVE
        }

        return MoveData(
            moveName = template.displayName.string,
            typeColor = typeColor,
            typeName = displayType.displayName.string,
            categoryName = template.damageCategory.displayName.string,
            categoryColor = categoryColor,
            powerText = powerText,
            accuracyText = accuracyText,
            ppText = "${move.currentPp}/${move.maxPp}",
            ppColor = ppColor,
            critText = critText,
            effectText = effectText,
            priorityText = priorityText,
            priorityColor = priorityColor,
            modifierText = modifierText
        )
    }

    // ═══════════════════════════════════════════════════════════════
    // Effectiveness computation
    // ═══════════════════════════════════════════════════════════════

    private data class EffectivenessEntry(val text: String, val color: Int)

    private fun computeEffectiveness(move: MoveTileBounds): List<EffectivenessEntry> {
        val entries = mutableListOf<EffectivenessEntry>()
        val battle = CobblemonClient.battle ?: return entries
        val playerUUID = MinecraftClient.getInstance().player?.uuid ?: return entries
        val playerSide = battle.side1.actors.any { it.uuid == playerUUID }
        val opponentSide = if (playerSide) battle.side2 else battle.side1
        val opponents = opponentSide.activeClientBattlePokemon.mapNotNull { it.battlePokemon }
        if (opponents.isEmpty()) return entries

        val baseType = getMoveEffectiveType(move)
        val weatherBallType = getWeatherBallEffectiveType(move.moveTemplate)
        val moveType = weatherBallType ?: baseType
        val isStatusMove = move.moveTemplate.damageCategory == DamageCategories.STATUS

        for (opponent in opponents) {
            val types = getOpponentEffectiveTypes(opponent)
            if (types.isEmpty()) continue
            var multiplier = 1.0
            for (defType in types) multiplier *= AIUtility.getDamageMultiplier(moveType, defType)
            if (isStatusMove && multiplier != 0.0) continue

            val opponentName = opponent.displayName.string
            val (effectText, effectColor) = getEffectivenessText(multiplier)
            entries.add(EffectivenessEntry(Text.translatable("cobblemonextendedbattleui.effectiveness.vs", opponentName, effectText).string, effectColor))
        }
        return entries
    }

    private fun getEffectivenessText(multiplier: Double): Pair<String, Int> {
        val fmt = formatMultiplier(multiplier)
        return when {
            multiplier == 0.0 -> Text.translatable("cobblemonextendedbattleui.effectiveness.immune").string to IMMUNE
            multiplier <= 0.25 -> Text.translatable("cobblemonextendedbattleui.effectiveness.extremely_ineffective", fmt).string to NOT_EFFECTIVE
            multiplier < 0.5 -> Text.translatable("cobblemonextendedbattleui.effectiveness.not_effective", fmt).string to NOT_EFFECTIVE
            multiplier < 1.0 -> Text.translatable("cobblemonextendedbattleui.effectiveness.not_very_effective", fmt).string to NOT_EFFECTIVE
            multiplier >= 4.0 -> Text.translatable("cobblemonextendedbattleui.effectiveness.extremely_effective", fmt).string to SUPER_EFFECTIVE_4X
            multiplier > 2.0 -> Text.translatable("cobblemonextendedbattleui.effectiveness.super_effective", fmt).string to SUPER_EFFECTIVE_4X
            multiplier > 1.0 -> Text.translatable("cobblemonextendedbattleui.effectiveness.super_effective", fmt).string to SUPER_EFFECTIVE_2X
            else -> Text.translatable("cobblemonextendedbattleui.effectiveness.neutral").string to NEUTRAL
        }
    }

    private fun formatMultiplier(multiplier: Double): String =
        if (multiplier == multiplier.toInt().toDouble()) multiplier.toInt().toString()
        else String.format("%.2g", multiplier)

    // ═══════════════════════════════════════════════════════════════
    // Section rendering
    // ═══════════════════════════════════════════════════════════════

    private fun renderHeaderText(
        context: DrawContext, data: MoveData,
        x: Int, y: Int, w: Int,
        fontScale: Float, tr: TextRenderer
    ) {
        val xf = x.toFloat()
        val yf = y.toFloat()

        // Left: Move name (type-colored)
        draw(context, data.moveName, xf, yf, data.typeColor, fontScale)

        // Right: Type · Category
        val catW = tr.getWidth(data.categoryName) * fontScale
        val dotText = " \u00b7 "
        val dotW = tr.getWidth(dotText) * fontScale
        val typeW = tr.getWidth(data.typeName) * fontScale
        val rightEdge = x + w
        draw(context, data.categoryName, rightEdge - catW, yf, data.categoryColor, fontScale)
        draw(context, dotText, rightEdge - catW - dotW, yf, TOOLTIP_DIM, fontScale)
        draw(context, data.typeName, rightEdge - catW - dotW - typeW, yf, data.typeColor, fontScale)
    }

    private data class StatEntry(val label: String, val value: String, val color: Int)

    private data class GridGeometry(
        val leftCellX: Int, val leftCellW: Int,
        val rightCellX: Int, val rightCellW: Int
    ) {
        val leftTextX get() = leftCellX + UIUtils.CELL_PAD
        val rightTextX get() = rightCellX + UIUtils.CELL_PAD
        val leftTextW get() = leftCellW - UIUtils.CELL_PAD * 2
        val rightTextW get() = rightCellW - UIUtils.CELL_PAD * 2
    }

    private fun renderDetailGrid(
        context: DrawContext, items: List<StatEntry>, geo: GridGeometry,
        startY: Int, rows: Int, lineH: Int, fontScale: Float, tr: TextRenderer
    ) {
        val lx = geo.leftTextX.toFloat()
        val rx = geo.rightTextX.toFloat()
        val leftValEdge = geo.leftTextX + geo.leftTextW
        val rightValEdge = geo.rightTextX + geo.rightTextW

        // Items fill left-to-right, top-to-bottom (2 columns)
        var curY = startY.toFloat()
        for (row in 0 until rows) {
            if (row > 0) {
                curY += 2f
                drawRowDivider(context, geo.leftCellX, geo.leftCellW, curY.toInt())
                drawRowDivider(context, geo.rightCellX, geo.rightCellW, curY.toInt())
                curY += (UIUtils.DIVIDER_H - 2).toFloat()
            }
            val leftIdx = row * 2
            val rightIdx = row * 2 + 1
            if (leftIdx < items.size) {
                draw(context, items[leftIdx].label, lx, curY, TOOLTIP_LABEL, fontScale)
                drawRightAligned(context, items[leftIdx].value, leftValEdge, curY, items[leftIdx].color, fontScale, tr)
            }
            if (rightIdx < items.size) {
                draw(context, items[rightIdx].label, rx, curY, TOOLTIP_LABEL, fontScale)
                drawRightAligned(context, items[rightIdx].value, rightValEdge, curY, items[rightIdx].color, fontScale, tr)
            }
            curY += lineH
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Cell + frame rendering (delegates to UIUtils)
    // ═══════════════════════════════════════════════════════════════

    private fun drawCell(context: DrawContext, x: Int, y: Int, w: Int, h: Int) =
        UIUtils.drawPopupCell(context, x, y, w, h)

    private fun drawRowDivider(context: DrawContext, cellX: Int, cellW: Int, y: Int) =
        UIUtils.drawPopupRowDivider(context, cellX, cellW, y)

    // ═══════════════════════════════════════════════════════════════
    // Text drawing
    // ═══════════════════════════════════════════════════════════════

    private fun draw(
        context: DrawContext, text: String,
        x: Float, y: Float, color: Int, fontScale: Float
    ) {
        drawScaledText(
            context = context, text = Text.literal(text),
            x = x, y = y, scale = fontScale, colour = color, shadow = true
        )
    }

    private fun drawRightAligned(
        context: DrawContext, text: String, rightEdge: Int, y: Float,
        color: Int, fontScale: Float, tr: TextRenderer
    ) {
        val w = tr.getWidth(text) * fontScale
        draw(context, text, rightEdge - w, y, color, fontScale)
    }

    // ═══════════════════════════════════════════════════════════════
    // Text wrapping (pixel-based for fixed-width layout)
    // ═══════════════════════════════════════════════════════════════

    private fun wrapText(
        text: String, tooltipWidth: Int, fontScale: Float,
        tr: TextRenderer
    ): List<String> {
        val maxWidth = tooltipWidth - UIUtils.FRAME_INSET * 2 - UIUtils.CELL_PAD * 2 - 2
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (tr.getWidth(testLine) * fontScale > maxWidth && currentLine.isNotEmpty()) {
                lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            } else {
                currentLine = StringBuilder(testLine)
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
        return lines
    }

    // ═══════════════════════════════════════════════════════════════
    // Move data helpers (types, abilities, items, dynamic power)
    // ═══════════════════════════════════════════════════════════════

    private fun critRatioToPercent(critRatio: Double): String = when {
        critRatio >= 4.0 -> "100%"
        critRatio >= 3.0 -> "50%"
        critRatio >= 2.0 -> "12.5%"
        else -> "4.17%"
    }

    private fun getPlayerPokemonTypes(): List<ElementalType> {
        val pokemonUuid = activePokemonUuid ?: return emptyList()
        val battle = CobblemonClient.battle ?: return emptyList()
        val playerUUID = MinecraftClient.getInstance().player?.uuid ?: return emptyList()
        val playerSide = if (battle.side1.actors.any { it.uuid == playerUUID }) battle.side1 else battle.side2
        val clientBattlePokemon = playerSide.activeClientBattlePokemon
            .firstOrNull { it.battlePokemon?.uuid == pokemonUuid }?.battlePokemon

        val types = mutableListOf<ElementalType>()

        val dynamicTypes = BattleStateTracker.getDynamicTypes(pokemonUuid)
        if (dynamicTypes != null) {
            dynamicTypes.primaryType?.let { typeName ->
                ElementalTypes.get(typeName.lowercase())?.let { types.add(it) }
            }
            dynamicTypes.secondaryType?.let { typeName ->
                ElementalTypes.get(typeName.lowercase())?.let { types.add(it) }
            }
            for (addedType in dynamicTypes.addedTypes) {
                ElementalTypes.get(addedType.lowercase())?.let { types.add(it) }
            }
        } else if (clientBattlePokemon != null) {
            val species = clientBattlePokemon.species
            val aspects = clientBattlePokemon.state.currentAspects
            val form = species.getForm(aspects)
            form.primaryType?.let { types.add(it) }
            form.secondaryType?.let { types.add(it) }
        }

        val teraType = BattleStateTracker.getTeraType(pokemonUuid)
        if (teraType != null) {
            val teraElementalType = ElementalTypes.get(teraType.lowercase())
            if (teraElementalType != null && !types.contains(teraElementalType)) {
                types.add(teraElementalType)
            }
        }

        return types
    }

    private fun getMoveEffectiveType(move: MoveTileBounds): ElementalType {
        val playerPokemon = getPlayerPartyPokemon()
        return if (playerPokemon != null) {
            move.moveTemplate.getEffectiveElementalType(playerPokemon)
        } else {
            move.moveTemplate.elementalType
        }
    }

    private fun getOpponentEffectiveTypes(
        opponent: ClientBattlePokemon
    ): List<ElementalType> {
        val pokemonUuid = opponent.uuid

        val teraType = BattleStateTracker.getTeraType(pokemonUuid)
        if (teraType != null) {
            val elementalType = ElementalTypes.get(teraType.lowercase())
            if (elementalType != null) return listOf(elementalType)
        }

        val dynamicTypes = BattleStateTracker.getDynamicTypes(pokemonUuid)
        if (dynamicTypes != null) {
            val types = mutableListOf<ElementalType>()
            dynamicTypes.primaryType?.let { typeName ->
                ElementalTypes.get(typeName.lowercase())?.let { types.add(it) }
            }
            dynamicTypes.secondaryType?.let { typeName ->
                ElementalTypes.get(typeName.lowercase())?.let { types.add(it) }
            }
            for (addedType in dynamicTypes.addedTypes) {
                ElementalTypes.get(addedType.lowercase())?.let { types.add(it) }
            }
            return types
        }

        val species = opponent.species
        val aspects = opponent.state.currentAspects
        val form = species.getForm(aspects)
        return listOfNotNull(form.primaryType, form.secondaryType)
    }

    private fun getPlayerPokemonAbility(): String? {
        val partyPokemon = getPlayerPartyPokemon() ?: return null
        return partyPokemon.ability.name
    }

    private fun getPlayerPokemonHeldItemId(): String? {
        val heldItem = getPlayerPokemonHeldItemStack() ?: return null
        return heldItem.item.toString()
    }

    private fun getPlayerPokemonHeldItemShowdownName(): String? {
        val heldItem = getPlayerPokemonHeldItemStack() ?: return null
        val heldItemEffect = heldItem.get(CobblemonItemComponents.HELD_ITEM_EFFECT)
        if (heldItemEffect != null) {
            if (heldItemEffect.consumed) return null
            return heldItemEffect.showdownId
        }
        val registryPath = Registries.ITEM.getId(heldItem.item).path
        return registryPath.replace("_", "")
    }

    private fun getPlayerPokemonHeldItemStack(): ItemStack? {
        val uuid = activePokemonUuid ?: return null

        val trackedItem = BattleStateTracker.getItem(uuid)
        if (trackedItem != null && trackedItem.status != BattleStateTracker.ItemStatus.HELD) {
            return null
        }

        val partyPokemon = CobblemonClient.storage.party.findByUUID(uuid) ?: return null
        val heldItem = partyPokemon.heldItem()
        if (heldItem.isEmpty) return null
        return heldItem
    }

    private fun getHeldItemPowerBoost(moveType: String): ItemPowerBoostParser.ItemPowerBoost? {
        val heldItemName = getPlayerPokemonHeldItemShowdownName() ?: return null
        val boost = ItemPowerBoostParser.getBoostForItem(heldItemName) ?: return null
        return when {
            boost.boostedType == null -> boost
            boost.boostedType.equals(moveType, ignoreCase = true) -> boost
            else -> null
        }
    }

    private fun getPlayerPartyPokemon(): Pokemon? {
        val uuid = activePokemonUuid ?: return getPlayerPartyPokemonFallback()
        return CobblemonClient.storage.party.findByUUID(uuid)
    }

    private fun getPlayerPartyPokemonFallback(): Pokemon? {
        val battle = CobblemonClient.battle ?: return null
        val playerUUID = MinecraftClient.getInstance().player?.uuid ?: return null
        val playerSide = if (battle.side1.actors.any { it.uuid == playerUUID }) battle.side1 else battle.side2
        val battlePokemon = playerSide.activeClientBattlePokemon.firstOrNull()?.battlePokemon ?: return null
        return CobblemonClient.storage.party.findByUUID(battlePokemon.uuid)
    }

    // ═══════════════════════════════════════════════════════════════
    // Dynamic move calculations (Hex, Weather Ball)
    // ═══════════════════════════════════════════════════════════════

    private data class DynamicPowerInfo(val power: Int, val reason: String?)

    private fun getWeatherBallEffectiveType(template: MoveTemplate): ElementalType? {
        if (template.name.lowercase() != "weatherball") return null
        val weather = BattleStateTracker.weather ?: return null
        return when (weather.type) {
            BattleStateTracker.Weather.RAIN -> ElementalTypes.get("water")
            BattleStateTracker.Weather.SUN -> ElementalTypes.get("fire")
            BattleStateTracker.Weather.SANDSTORM -> ElementalTypes.get("rock")
            BattleStateTracker.Weather.HAIL, BattleStateTracker.Weather.SNOW -> ElementalTypes.get("ice")
        }
    }

    private fun getDynamicPowerInfo(template: MoveTemplate): DynamicPowerInfo? = when (template.name.lowercase()) {
        "hex" -> getHexDynamicPower(template)
        "weatherball" -> getWeatherBallDynamicPower(template)
        else -> null
    }

    private fun getHexDynamicPower(template: MoveTemplate): DynamicPowerInfo? {
        val basePower = template.power.toInt()
        val battle = CobblemonClient.battle ?: return null
        val playerUUID = MinecraftClient.getInstance().player?.uuid ?: return null
        val playerSide = battle.side1.actors.any { it.uuid == playerUUID }
        val opponentSide = if (playerSide) battle.side2 else battle.side1
        val opponentHasStatus = opponentSide.activeClientBattlePokemon
            .mapNotNull { it.battlePokemon }
            .any { it.status != null }
        return if (opponentHasStatus) DynamicPowerInfo(basePower * 2, "Status") else null
    }

    private fun getWeatherBallDynamicPower(template: MoveTemplate): DynamicPowerInfo? {
        val basePower = template.power.toInt()
        val weather = BattleStateTracker.weather ?: return null
        val weatherName = when (weather.type) {
            BattleStateTracker.Weather.RAIN -> "Rain"
            BattleStateTracker.Weather.SUN -> "Sun"
            BattleStateTracker.Weather.SANDSTORM -> "Sandstorm"
            BattleStateTracker.Weather.HAIL -> "Hail"
            BattleStateTracker.Weather.SNOW -> "Snow"
        }
        return DynamicPowerInfo(basePower * 2, weatherName)
    }

    // ═══════════════════════════════════════════════════════════════
    // Input handling
    // ═══════════════════════════════════════════════════════════════

    fun shouldHandleFontInput(): Boolean = hoveredMove != null

    fun handleInput() {
        if (!shouldHandleFontInput()) return
        val mc = MinecraftClient.getInstance()
        handleFontKeybinds(mc.window.handle)
    }

    private fun handleFontKeybinds(handle: Long) {
        val increaseKey = InputUtil.fromTranslationKey(
            CobblemonExtendedBattleUIClient.increaseFontKey.boundKeyTranslationKey)
        val isIncreaseDown = UIUtils.isKeyOrButtonPressed(handle, increaseKey)
        if (isIncreaseDown && !wasIncreaseFontKeyPressed) {
            PanelConfig.adjustMoveTooltipFontScale(PanelConfig.FONT_SCALE_STEP)
            PanelConfig.save()
        }
        wasIncreaseFontKeyPressed = isIncreaseDown

        val decreaseKey = InputUtil.fromTranslationKey(
            CobblemonExtendedBattleUIClient.decreaseFontKey.boundKeyTranslationKey)
        val isDecreaseDown = UIUtils.isKeyOrButtonPressed(handle, decreaseKey)
        if (isDecreaseDown && !wasDecreaseFontKeyPressed) {
            PanelConfig.adjustMoveTooltipFontScale(-PanelConfig.FONT_SCALE_STEP)
            PanelConfig.save()
        }
        wasDecreaseFontKeyPressed = isDecreaseDown
    }

    fun handleScroll(deltaY: Double): Boolean {
        if (hoveredMove == null) return false
        val mc = MinecraftClient.getInstance()
        val handle = mc.window.handle
        val isCtrlDown = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS
        if (isCtrlDown) {
            val delta = if (deltaY > 0) PanelConfig.FONT_SCALE_STEP else -PanelConfig.FONT_SCALE_STEP
            PanelConfig.adjustMoveTooltipFontScale(delta)
            PanelConfig.save()
            return true
        }
        return false
    }
}
