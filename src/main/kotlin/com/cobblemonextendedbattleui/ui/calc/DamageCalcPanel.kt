package com.cobblemonextendedbattleui.ui.calc

import com.cobblemonextendedbattleui.UIUtils
import com.cobblemonextendedbattleui.calc.CalcComputationService
import com.cobblemonextendedbattleui.calc.CalcMoveRow
import com.cobblemonextendedbattleui.calc.CalcPokemonSnapshot
import com.cobblemonextendedbattleui.calc.CalcPreviewTab
import com.cobblemonextendedbattleui.calc.CalcRenderModel
import com.cobblemonextendedbattleui.calc.InferenceValueState
import com.cobblemonextendedbattleui.calc.OverrideRow
import com.cobblemonextendedbattleui.compat.delta.DeltaBattlePlatformAdapter
import com.cobblemonextendedbattleui.ui.shared.WidgetInteractionHandler
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import org.lwjgl.glfw.GLFW
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.roundToInt

object DamageCalcPanel {
    private const val DEFAULT_WIDTH = 228
    private const val DEFAULT_HEIGHT = 310
    private const val COLLAPSED_HEIGHT = 28
    private const val RESIZE_HANDLE = 6
    private const val HEADER_HEIGHT = 18
    private const val BASE_FONT_SCALE = 0.7f
    private const val TAB_HEIGHT = 13
    private const val TAB_GAP = 3
    private const val TAB_TEXT_SCALE_OFFSET = 0.18f
    private const val TAB_TEXT_SCALE_MIN = 0.42f
    private const val TAB_TEXT_MAX_LENGTH = 5
    private const val TAB_MIN_WIDTH = 20

    // V2 palette (muted navy calc look).
    private val V2_TEXT = UIUtils.color(226, 232, 240)
    private val V2_TEXT_DIM = UIUtils.color(160, 174, 192)
    private val V2_TEXT_LABEL = UIUtils.color(113, 128, 150)
    private val V2_ACCENT_GREEN = UIUtils.color(104, 211, 145)
    private val V2_ACCENT_YELLOW = UIUtils.color(246, 224, 94)
    private val V2_ACCENT_BLUE = UIUtils.color(147, 183, 220)

    // Severity (drives KO label color + left accent bar + forecast fill).
    private val SEV_OHKO_FG = UIUtils.color(255, 107, 107)
    private val SEV_OHKO_BAR = UIUtils.color(229, 62, 62)
    private val SEV_2HKO_FG = UIUtils.color(255, 174, 76)
    private val SEV_2HKO_BAR = UIUtils.color(237, 137, 54)
    private val SEV_3HKO_FG = UIUtils.color(255, 215, 76)
    private val SEV_3HKO_BAR = UIUtils.color(214, 158, 46)
    private val SEV_WEAK_FG = UIUtils.color(144, 163, 181)
    private val SEV_WEAK_BAR = UIUtils.color(74, 85, 104)
    private val SEV_STATUS_FG = UIUtils.color(147, 183, 220)
    private val SEV_STATUS_BAR = UIUtils.color(90, 122, 154)

    // HP bar palette.
    private val HP_GOOD = UIUtils.color(72, 187, 120)
    private val HP_MID = UIUtils.color(236, 201, 75)
    private val HP_LOW = UIUtils.color(245, 101, 101)
    private val HP_TRACK = UIUtils.color(10, 15, 22)

    // State pill palette.
    private val PILL_SEEN_BG = UIUtils.color(56, 161, 105, 60)
    private val PILL_LIKELY_BG = UIUtils.color(214, 158, 46, 60)
    private val PILL_UNK_BG = UIUtils.color(113, 128, 150, 60)
    private val PILL_MANUAL_BG = UIUtils.color(56, 109, 161, 60)

    private val interaction = WidgetInteractionHandler(UIUtils.ActivePanel.DAMAGE_CALC).apply {
        dragThreshold = 4
    }

    private var wasMouseDown = false
    private var wasRightMouseDown = false
    private var lastBounds = intArrayOf(0, 0, 0, 0)
    private var lastTeamHeaderBounds = intArrayOf(0, 0, 0, 0)
    private var lastSummaryBounds = intArrayOf(0, 0, 0, 0)
    private var lastMovesHeaderBounds = intArrayOf(0, 0, 0, 0)
    private var lastItemRowBounds = intArrayOf(0, 0, 0, 0)
    private var lastAbilityRowBounds = intArrayOf(0, 0, 0, 0)
    private var lastSpreadRowBounds = intArrayOf(0, 0, 0, 0)
    private var lastItemBackBounds = intArrayOf(0, 0, 0, 0)
    private var lastAbilityBackBounds = intArrayOf(0, 0, 0, 0)
    private var lastSpreadBackBounds = intArrayOf(0, 0, 0, 0)
    private var lastPlayerTabBounds = emptyList<TabBounds>()
    private var lastOpponentTabBounds = emptyList<TabBounds>()
    private var summaryClickArmed = false
    private var pendingTabSelection: PendingTabSelection? = null
    private var pendingSectionToggle: SectionToggle? = null
    private var pendingOverrideClick: PendingOverrideClick? = null

    private data class PendingOverrideClick(val row: OverrideRow, val direction: Int)

    fun initialize() {
        CalcPanelState.load()
    }

    fun render(context: DrawContext) {
        if (!CalcPanelState.enabled || !DeltaBattlePlatformAdapter.isBattleActive()) {
            interaction.releaseAll()
            wasMouseDown = false
            wasRightMouseDown = false
            pendingTabSelection = null
            pendingSectionToggle = null
            pendingOverrideClick = null
            return
        }

        val mc = MinecraftClient.getInstance()
        val model = CalcComputationService.currentModel() ?: return
        val screenWidth = mc.window.scaledWidth
        val width = CalcPanelState.width ?: DEFAULT_WIDTH
        val fullHeight = CalcPanelState.height ?: DEFAULT_HEIGHT
        val height = if (CalcPanelState.expanded) fullHeight else COLLAPSED_HEIGHT
        val x = CalcPanelState.x ?: (screenWidth - width - 14)
        val y = CalcPanelState.y ?: 100

        lastBounds = intArrayOf(x, y, width, height)
        handleInput(mc, x, y, width, height, model)

        UIUtils.renderPopupFrame(context, x, y, width, height)
        val cellX = x + UIUtils.FRAME_INSET
        val cellY = y + UIUtils.FRAME_INSET
        val cellW = width - UIUtils.FRAME_INSET * 2
        val compact = mc.window.scaledWidth < 640 || mc.window.scaledHeight < 400 || cellW < 200
        UIUtils.drawPopupCell(context, cellX, cellY, cellW, HEADER_HEIGHT)
        // V2 live-status dot + neutral title.
        context.fill(cellX + 6, cellY + 8, cellX + 9, cellY + 11, V2_ACCENT_GREEN)
        UIUtils.drawText(context, "DAMAGE CALC", (cellX + 13).toFloat(), (cellY + 5).toFloat(), V2_TEXT, BASE_FONT_SCALE)
        UIUtils.drawText(context, "T${model.snapshot.turn}", (cellX + cellW - 28).toFloat(), (cellY + 5).toFloat(), V2_TEXT_DIM, BASE_FONT_SCALE)

        if (!CalcPanelState.expanded) {
            lastPlayerTabBounds = emptyList()
            lastOpponentTabBounds = emptyList()
            drawResizeHandle(context, x, y, width, height)
            return
        }

        val contentY = cellY + HEADER_HEIGHT + UIUtils.CELL_GAP
        val contentH = height - UIUtils.FRAME_INSET * 2 - HEADER_HEIGHT - UIUtils.CELL_GAP
        UIUtils.drawPopupCell(context, cellX, contentY, cellW, contentH)

        var textY = contentY + 6
        val textScale = BASE_FONT_SCALE * CalcPanelState.fontScale
        val uiScale = CalcPanelState.fontScale
        fun s(n: Int): Int = (n * uiScale).roundToInt().coerceAtLeast(1)

        // V2 matchup row — YOU / OPP with name + HP bar + HP% + speed indicator.
        textY = drawMatchupSection(context, cellX, textY, cellW, model.selectedPlayer, model.selectedOpponent, model.speedText, textScale, uiScale, compact)

        val tabAreaWidth = cellW - 12
        val summaryToggle = if (CalcPanelState.summaryExpanded) "[-]" else "[+]"
        val summaryStartY = textY
        UIUtils.drawText(context, "$summaryToggle ${model.matchupLabel}", (cellX + 6).toFloat(), textY.toFloat(), V2_TEXT, textScale)
        textY += s(11)
        if (CalcPanelState.summaryExpanded) {
            if (!compact) {
                UIUtils.drawText(
                    context,
                    if (model.isPreview) "Previewing switch matchup" else "Current active matchup",
                    (cellX + 6).toFloat(),
                    textY.toFloat(),
                    if (model.isPreview) V2_ACCENT_YELLOW else V2_TEXT_LABEL,
                    textScale
                )
                textY += s(10)
                model.switchSummaryText?.let { switchSummary ->
                    UIUtils.drawText(context, switchSummary, (cellX + 6).toFloat(), textY.toFloat(), V2_TEXT, textScale)
                    textY += s(10)
                }
            }
            model.hazardNoteText?.let { note ->
                UIUtils.drawText(context, note, (cellX + 6).toFloat(), textY.toFloat(), UIUtils.color(245, 180, 120), textScale)
                textY += s(10)
            }
            // Speed line shows in both compact and full modes so users can tell
            // at a glance whether overrides moved the speed comparison. The
            // speedText already starts with "Speed: ..." so no extra label needed.
            model.speedText?.let { speedText ->
                UIUtils.drawText(context, speedText, (cellX + 6).toFloat(), textY.toFloat(), speedColor(speedText), textScale)
                textY += s(10)
            }
            val opponentUuid = model.selectedOpponentUuid
            val itemOverridden = opponentUuid?.let { CalcComputationService.hasOverride(it, OverrideRow.ITEM) } ?: false
            val abilityOverridden = opponentUuid?.let { CalcComputationService.hasOverride(it, OverrideRow.ABILITY) } ?: false
            val spreadOverridden = opponentUuid?.let { CalcComputationService.hasOverride(it, OverrideRow.SPREAD) } ?: false
            val itemHasAlts = model.opponentSet.itemAlternatives.size > 1
            val abilityHasAlts = model.opponentSet.abilityAlternatives.size > 1
            val spreadHasAlts = model.opponentSet.spreadAlternatives.size > 1
            val rowHeight = s(11).coerceAtLeast(8)
            val pillX = cellX + cellW - s(34).coerceAtLeast(18)
            val forwardX = pillX - s(8)
            val backX = pillX - s(18)
            val backZoneW = s(8).coerceAtLeast(6)

            val itemValue = formatInference(model.opponentSet.item.first)
            val itemUsage = model.opponentSet.itemUsagePercent[normalizeForUsageLookup(model.opponentSet.item.first)]
            val itemDisplay = buildOverrideRowText(itemValue, itemUsage, itemOverridden)
            UIUtils.drawText(context, "Item: $itemDisplay", (cellX + 6).toFloat(), textY.toFloat(), V2_TEXT, textScale)
            drawStatePill(context, pillX, textY - 1, model.opponentSet.item.second, textScale, uiScale, itemOverridden)
            if (itemHasAlts) {
                UIUtils.drawText(context, "<", backX.toFloat(), textY.toFloat(), V2_TEXT_DIM, textScale)
                UIUtils.drawText(context, ">", forwardX.toFloat(), textY.toFloat(), V2_TEXT_DIM, textScale)
                lastItemBackBounds = intArrayOf(backX - 1, textY - 2, backZoneW, rowHeight)
            } else {
                lastItemBackBounds = intArrayOf(0, 0, 0, 0)
            }
            lastItemRowBounds = intArrayOf(cellX + 4, textY - 2, cellW - 8, rowHeight)
            textY += s(10)

            val abilityValue = formatInference(model.opponentSet.ability.first)
            val abilityUsage = model.opponentSet.abilityUsagePercent[normalizeForUsageLookup(model.opponentSet.ability.first)]
            val abilityDisplay = buildOverrideRowText(abilityValue, abilityUsage, abilityOverridden)
            UIUtils.drawText(context, "Ability: $abilityDisplay", (cellX + 6).toFloat(), textY.toFloat(), V2_TEXT, textScale)
            drawStatePill(context, pillX, textY - 1, model.opponentSet.ability.second, textScale, uiScale, abilityOverridden)
            if (abilityHasAlts) {
                UIUtils.drawText(context, "<", backX.toFloat(), textY.toFloat(), V2_TEXT_DIM, textScale)
                UIUtils.drawText(context, ">", forwardX.toFloat(), textY.toFloat(), V2_TEXT_DIM, textScale)
                lastAbilityBackBounds = intArrayOf(backX - 1, textY - 2, backZoneW, rowHeight)
            } else {
                lastAbilityBackBounds = intArrayOf(0, 0, 0, 0)
            }
            lastAbilityRowBounds = intArrayOf(cellX + 4, textY - 2, cellW - 8, rowHeight)
            textY += s(10)

            // Spread row stays visible even in compact mode so users can still
            // override it; only the source label below is dropped when compact.
            val spreadValue = model.opponentSet.spreadLabel ?: "Unknown"
            val spreadUsage = model.opponentSet.spread?.usagePercent
            val spreadDisplay = buildOverrideRowText(spreadValue, spreadUsage, spreadOverridden)
            UIUtils.drawText(context, "Spread: $spreadDisplay", (cellX + 6).toFloat(), textY.toFloat(), V2_TEXT_DIM, textScale)
            // Spread gets a MANUAL pill when overridden; otherwise the inference
            // state pill (LIKELY for usage-derived, UNKNOWN for missing data).
            val spreadState = model.opponentSet.spread?.state ?: InferenceValueState.UNKNOWN
            drawStatePill(context, pillX, textY - 1, spreadState, textScale, uiScale, spreadOverridden)
            if (spreadHasAlts) {
                UIUtils.drawText(context, "<", backX.toFloat(), textY.toFloat(), V2_TEXT_DIM, textScale)
                UIUtils.drawText(context, ">", forwardX.toFloat(), textY.toFloat(), V2_TEXT_DIM, textScale)
                lastSpreadBackBounds = intArrayOf(backX - 1, textY - 2, backZoneW, rowHeight)
            } else {
                lastSpreadBackBounds = intArrayOf(0, 0, 0, 0)
            }
            lastSpreadRowBounds = intArrayOf(cellX + 4, textY - 2, cellW - 8, rowHeight)
            textY += s(9)
            if (!compact) {
                UIUtils.drawText(context, model.opponentSet.sourceLabel, (cellX + 6).toFloat(), textY.toFloat(), V2_TEXT_LABEL, textScale)
                textY += s(10)
            } else {
                textY += s(2)
            }
        } else {
            UIUtils.drawText(context, model.opponentSet.sourceLabel, (cellX + 20).toFloat(), (textY - 1).toFloat(), V2_TEXT_LABEL, textScale)
            textY += s(8)
            // Summary collapsed: rows aren't drawn so disable click-fire on stale bounds.
            lastItemRowBounds = intArrayOf(0, 0, 0, 0)
            lastAbilityRowBounds = intArrayOf(0, 0, 0, 0)
            lastSpreadRowBounds = intArrayOf(0, 0, 0, 0)
            lastItemBackBounds = intArrayOf(0, 0, 0, 0)
            lastAbilityBackBounds = intArrayOf(0, 0, 0, 0)
            lastSpreadBackBounds = intArrayOf(0, 0, 0, 0)
        }

        lastSummaryBounds = intArrayOf(cellX, summaryStartY - 2, cellW, (textY - summaryStartY + 2).coerceAtLeast(12))
        // Subtle 1px border around the predicted-set block (mockup style).
        val boxColor = UIUtils.color(255, 255, 255, 28)
        val boxTop = summaryStartY - 3
        val boxBot = textY - 2
        val boxLeft = cellX + 3
        val boxRight = cellX + cellW - 3
        context.fill(boxLeft, boxTop, boxRight, boxTop + 1, boxColor)
        context.fill(boxLeft, boxBot - 1, boxRight, boxBot, boxColor)
        context.fill(boxLeft, boxTop, boxLeft + 1, boxBot, boxColor)
        context.fill(boxRight - 1, boxTop, boxRight, boxBot, boxColor)
        textY += s(6)

        val movesHeader = if (CalcPanelState.movesSectionCollapsed) "[+] MOVE PREDICTIONS" else "[-] MOVE PREDICTIONS"
        UIUtils.drawText(context, movesHeader, (cellX + 6).toFloat(), textY.toFloat(), V2_TEXT_LABEL, textScale)
        lastMovesHeaderBounds = intArrayOf(cellX, textY - 2, cellW, s(11).coerceAtLeast(8))
        textY += s(10)

        if (!CalcPanelState.movesSectionCollapsed) {
            val rowH = s(14).coerceAtLeast(7)
            val opponentHpPct = hpPercent(model.selectedOpponent)
            val playerHpPct = hpPercent(model.selectedPlayer)

            UIUtils.drawText(context, "YOU \u2192 OPPONENT", (cellX + 6).toFloat(), textY.toFloat(), V2_ACCENT_GREEN, textScale)
            textY += s(9)
            // Bar under YOUR moves shows OPPONENT's HP (the side taking the hit).
            model.yourMoves.take(4).forEach { row ->
                drawMoveRow(context, cellX + 6, textY.toFloat(), row, if (row.emphasized) V2_ACCENT_YELLOW else V2_TEXT, textScale, uiScale, compact, opponentHpPct)
                textY += rowH
            }

            textY += s(2)
            UIUtils.drawText(context, "OPPONENT \u2192 YOU", (cellX + 6).toFloat(), textY.toFloat(), UIUtils.color(252, 129, 129), textScale)
            textY += s(9)
            // Bar under OPP's moves shows YOUR HP (the side taking the hit).
            model.opponentMoves.take(4).forEach { row ->
                drawMoveRow(context, cellX + 6, textY.toFloat(), row, V2_TEXT, textScale, uiScale, compact, playerHpPct)
                textY += rowH
            }
        } else {
            UIUtils.drawText(context, "Tap to expand", (cellX + 6).toFloat(), textY.toFloat() + s(8), UIUtils.color(140, 150, 165), textScale)
            textY += s(16)
        }

        // ─── Team roster at the bottom ─────────────────────────────────────
        textY += s(2)
        UIUtils.drawPopupRowDivider(context, cellX, cellW, textY)
        textY += s(3)
        val teamHeader = if (CalcPanelState.teamSectionCollapsed) "[+] TEAM" else "[-] TEAM"
        UIUtils.drawText(context, teamHeader, (cellX + 6).toFloat(), textY.toFloat(), V2_TEXT_LABEL, textScale)
        lastTeamHeaderBounds = intArrayOf(cellX, textY - 2, cellW, s(11).coerceAtLeast(8))
        textY += s(10)

        if (!CalcPanelState.teamSectionCollapsed) {
            lastPlayerTabBounds = drawTabs(context, cellX + 6, textY, tabAreaWidth, model.playerTabs, model.snapshot.playerTeam, textScale, uiScale)
            textY += tabBlockHeight(model.playerTabs, tabAreaWidth, uiScale)

            if (model.opponentTabs.size > 1) {
                lastOpponentTabBounds = drawTabs(context, cellX + 6, textY, tabAreaWidth, model.opponentTabs, model.snapshot.opponentTeam, textScale, uiScale)
                textY += tabBlockHeight(model.opponentTabs, tabAreaWidth, uiScale)
            } else {
                lastOpponentTabBounds = emptyList()
            }
        } else {
            lastPlayerTabBounds = emptyList()
            lastOpponentTabBounds = emptyList()
        }

        drawResizeHandle(context, x, y, width, height)
    }

    fun onScroll(mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        val (x, y, width, height) = lastBounds
        if (!contains(mouseX.toInt(), mouseY.toInt(), x, y, width, height)) {
            return false
        }
        val mc = MinecraftClient.getInstance()
        val handle = mc.window.handle
        val isCtrlDown = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS
        if (!isCtrlDown) {
            return false
        }
        CalcPanelState.adjustFontScale(if (deltaY > 0) CalcPanelState.FONT_SCALE_STEP else -CalcPanelState.FONT_SCALE_STEP)
        CalcPanelState.save()
        return true
    }

    private fun handleInput(mc: MinecraftClient, x: Int, y: Int, width: Int, height: Int, model: CalcRenderModel) {
        val mouseX = (mc.mouse.x * mc.window.scaledWidth / mc.window.width).toInt()
        val mouseY = (mc.mouse.y * mc.window.scaledHeight / mc.window.height).toInt()
        val mouseDown = GLFW.glfwGetMouseButton(mc.window.handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
        val rightDown = GLFW.glfwGetMouseButton(mc.window.handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS
        val canInteract = UIUtils.canInteract(UIUtils.ActivePanel.DAMAGE_CALC)
        val summaryBounds = lastSummaryBounds

        // Right-click on a row resets that row's override. Rising-edge only.
        if (rightDown && !wasRightMouseDown && canInteract) {
            val opponentUuid = model.selectedOpponentUuid
            if (opponentUuid != null && CalcPanelState.expanded && CalcPanelState.summaryExpanded) {
                val resetRow = when {
                    contains(mouseX, mouseY, lastItemRowBounds[0], lastItemRowBounds[1], lastItemRowBounds[2], lastItemRowBounds[3]) -> OverrideRow.ITEM
                    contains(mouseX, mouseY, lastAbilityRowBounds[0], lastAbilityRowBounds[1], lastAbilityRowBounds[2], lastAbilityRowBounds[3]) -> OverrideRow.ABILITY
                    contains(mouseX, mouseY, lastSpreadRowBounds[0], lastSpreadRowBounds[1], lastSpreadRowBounds[2], lastSpreadRowBounds[3]) -> OverrideRow.SPREAD
                    else -> null
                }
                resetRow?.let { CalcComputationService.resetOverride(opponentUuid, it) }
            }
        }
        wasRightMouseDown = rightDown

        if (mouseDown) {
            when {
                interaction.isDragging -> {
                    summaryClickArmed = false
                    pendingTabSelection = null
                    pendingSectionToggle = null
                    pendingOverrideClick = null
                    interaction.updateDrag(mouseX, mouseY, mc.window.scaledWidth, mc.window.scaledHeight, width, height)?.let { (nextX, nextY) ->
                        CalcPanelState.setPosition(nextX, nextY)
                    }
                }
                interaction.isResizing -> {
                    summaryClickArmed = false
                    pendingTabSelection = null
                    pendingSectionToggle = null
                    pendingOverrideClick = null
                    val result = interaction.calculateResize(
                        mouseX = mouseX,
                        mouseY = mouseY,
                        minWidth = 180,
                        maxWidth = (mc.window.scaledWidth * 0.65f).toInt(),
                        minHeight = COLLAPSED_HEIGHT,
                        maxHeight = (mc.window.scaledHeight * 0.8f).toInt(),
                        screenWidth = mc.window.scaledWidth,
                        screenHeight = mc.window.scaledHeight
                    )
                    CalcPanelState.setPosition(result.newX, result.newY)
                    CalcPanelState.setDimensions(result.newWidth, result.newHeight)
                }
                !wasMouseDown && canInteract -> {
                    val resizeZone = UIUtils.getResizeZone(mouseX, mouseY, x, y, width, height, RESIZE_HANDLE)
                    val clickedPlayerTab = findTabAt(mouseX, mouseY, lastPlayerTabBounds)
                    val clickedOpponentTab = findTabAt(mouseX, mouseY, lastOpponentTabBounds)
                    val rowsClickable = CalcPanelState.expanded && CalcPanelState.summaryExpanded
                    if (resizeZone != UIUtils.ResizeZone.NONE) {
                        summaryClickArmed = false
                        pendingTabSelection = null
                        pendingSectionToggle = null
                        pendingOverrideClick = null
                        interaction.startResize(mouseX, mouseY, resizeZone, x, y, width, height)
                    } else if (contains(mouseX, mouseY, lastTeamHeaderBounds[0], lastTeamHeaderBounds[1], lastTeamHeaderBounds[2], lastTeamHeaderBounds[3])) {
                        summaryClickArmed = false
                        pendingTabSelection = null
                        pendingOverrideClick = null
                        pendingSectionToggle = SectionToggle.TEAM
                    } else if (contains(mouseX, mouseY, lastMovesHeaderBounds[0], lastMovesHeaderBounds[1], lastMovesHeaderBounds[2], lastMovesHeaderBounds[3])) {
                        summaryClickArmed = false
                        pendingTabSelection = null
                        pendingOverrideClick = null
                        pendingSectionToggle = SectionToggle.MOVES
                    } else if (clickedPlayerTab != null && !clickedPlayerTab.disabled) {
                        summaryClickArmed = false
                        pendingSectionToggle = null
                        pendingOverrideClick = null
                        pendingTabSelection = PendingTabSelection(clickedPlayerTab.uuid, isPlayerSide = true)
                    } else if (clickedOpponentTab != null && !clickedOpponentTab.disabled) {
                        summaryClickArmed = false
                        pendingSectionToggle = null
                        pendingOverrideClick = null
                        pendingTabSelection = PendingTabSelection(clickedOpponentTab.uuid, isPlayerSide = false)
                    } else if (contains(mouseX, mouseY, x + UIUtils.FRAME_INSET, y + UIUtils.FRAME_INSET, width - UIUtils.FRAME_INSET * 2, HEADER_HEIGHT)) {
                        summaryClickArmed = false
                        pendingSectionToggle = null
                        pendingTabSelection = null
                        pendingOverrideClick = null
                        interaction.startDrag(mouseX, mouseY, x, y)
                    } else if (rowsClickable && contains(mouseX, mouseY, lastItemBackBounds[0], lastItemBackBounds[1], lastItemBackBounds[2], lastItemBackBounds[3])) {
                        summaryClickArmed = false
                        pendingTabSelection = null
                        pendingSectionToggle = null
                        pendingOverrideClick = PendingOverrideClick(OverrideRow.ITEM, -1)
                    } else if (rowsClickable && contains(mouseX, mouseY, lastAbilityBackBounds[0], lastAbilityBackBounds[1], lastAbilityBackBounds[2], lastAbilityBackBounds[3])) {
                        summaryClickArmed = false
                        pendingTabSelection = null
                        pendingSectionToggle = null
                        pendingOverrideClick = PendingOverrideClick(OverrideRow.ABILITY, -1)
                    } else if (rowsClickable && contains(mouseX, mouseY, lastSpreadBackBounds[0], lastSpreadBackBounds[1], lastSpreadBackBounds[2], lastSpreadBackBounds[3])) {
                        summaryClickArmed = false
                        pendingTabSelection = null
                        pendingSectionToggle = null
                        pendingOverrideClick = PendingOverrideClick(OverrideRow.SPREAD, -1)
                    } else if (rowsClickable && contains(mouseX, mouseY, lastItemRowBounds[0], lastItemRowBounds[1], lastItemRowBounds[2], lastItemRowBounds[3])) {
                        summaryClickArmed = false
                        pendingTabSelection = null
                        pendingSectionToggle = null
                        pendingOverrideClick = PendingOverrideClick(OverrideRow.ITEM, 1)
                    } else if (rowsClickable && contains(mouseX, mouseY, lastAbilityRowBounds[0], lastAbilityRowBounds[1], lastAbilityRowBounds[2], lastAbilityRowBounds[3])) {
                        summaryClickArmed = false
                        pendingTabSelection = null
                        pendingSectionToggle = null
                        pendingOverrideClick = PendingOverrideClick(OverrideRow.ABILITY, 1)
                    } else if (rowsClickable && contains(mouseX, mouseY, lastSpreadRowBounds[0], lastSpreadRowBounds[1], lastSpreadRowBounds[2], lastSpreadRowBounds[3])) {
                        summaryClickArmed = false
                        pendingTabSelection = null
                        pendingSectionToggle = null
                        pendingOverrideClick = PendingOverrideClick(OverrideRow.SPREAD, 1)
                    } else if (CalcPanelState.expanded && contains(mouseX, mouseY, summaryBounds[0], summaryBounds[1], summaryBounds[2], summaryBounds[3])) {
                        pendingTabSelection = null
                        pendingSectionToggle = null
                        pendingOverrideClick = null
                        summaryClickArmed = true
                    }
                }
            }
        } else {
            if (interaction.isDragging) {
                summaryClickArmed = false
                pendingTabSelection = null
                pendingSectionToggle = null
                pendingOverrideClick = null
                val didDrag = interaction.endDrag()
                if (!didDrag && contains(mouseX, mouseY, x + UIUtils.FRAME_INSET, y + UIUtils.FRAME_INSET, width - UIUtils.FRAME_INSET * 2, HEADER_HEIGHT)) {
                    CalcPanelState.toggleExpanded()
                }
                CalcPanelState.save()
            } else if (interaction.isResizing) {
                summaryClickArmed = false
                pendingTabSelection = null
                pendingSectionToggle = null
                pendingOverrideClick = null
                interaction.endResize()
                CalcPanelState.save()
            } else if (pendingOverrideClick != null) {
                val pending = pendingOverrideClick!!
                val (rowBounds, backBounds) = when (pending.row) {
                    OverrideRow.ITEM -> lastItemRowBounds to lastItemBackBounds
                    OverrideRow.ABILITY -> lastAbilityRowBounds to lastAbilityBackBounds
                    OverrideRow.SPREAD -> lastSpreadRowBounds to lastSpreadBackBounds
                }
                val targetBounds = if (pending.direction < 0) backBounds else rowBounds
                val opponentUuid = model.selectedOpponentUuid
                if (opponentUuid != null && contains(mouseX, mouseY, targetBounds[0], targetBounds[1], targetBounds[2], targetBounds[3])) {
                    when (pending.row) {
                        OverrideRow.ITEM -> CalcComputationService.cycleItem(opponentUuid, model.opponentSet.itemAlternatives.size, pending.direction)
                        OverrideRow.ABILITY -> CalcComputationService.cycleAbility(opponentUuid, model.opponentSet.abilityAlternatives.size, pending.direction)
                        OverrideRow.SPREAD -> CalcComputationService.cycleSpread(opponentUuid, model.opponentSet.spreadAlternatives.size, pending.direction)
                    }
                }
                pendingOverrideClick = null
            } else if (pendingSectionToggle != null) {
                val stillInside = when (pendingSectionToggle) {
                    SectionToggle.TEAM -> contains(mouseX, mouseY, lastTeamHeaderBounds[0], lastTeamHeaderBounds[1], lastTeamHeaderBounds[2], lastTeamHeaderBounds[3])
                    SectionToggle.MOVES -> contains(mouseX, mouseY, lastMovesHeaderBounds[0], lastMovesHeaderBounds[1], lastMovesHeaderBounds[2], lastMovesHeaderBounds[3])
                    null -> false
                }
                if (stillInside) {
                    when (pendingSectionToggle) {
                        SectionToggle.TEAM -> CalcPanelState.toggleTeamSectionCollapsed()
                        SectionToggle.MOVES -> CalcPanelState.toggleMovesSectionCollapsed()
                        null -> Unit
                    }
                    CalcPanelState.save()
                }
                pendingSectionToggle = null
            } else if (pendingTabSelection != null) {
                val pending = pendingTabSelection
                val tab = if (pending?.isPlayerSide == true) findTabAt(mouseX, mouseY, lastPlayerTabBounds) else findTabAt(mouseX, mouseY, lastOpponentTabBounds)
                if (pending != null && tab?.uuid == pending.uuid && !tab.disabled) {
                    if (pending.isPlayerSide) {
                        CalcComputationService.selectPlayerPreview(pending.uuid)
                    } else {
                        CalcComputationService.selectOpponentPreview(pending.uuid)
                    }
                }
                pendingTabSelection = null
            } else if (summaryClickArmed) {
                if (CalcPanelState.expanded && contains(mouseX, mouseY, summaryBounds[0], summaryBounds[1], summaryBounds[2], summaryBounds[3])) {
                    CalcPanelState.toggleSummaryExpanded()
                    CalcPanelState.save()
                }
                summaryClickArmed = false
            }
        }

        wasMouseDown = mouseDown
    }

    private fun drawTabs(
        context: DrawContext,
        startX: Int,
        startY: Int,
        availableWidth: Int,
        tabs: List<CalcPreviewTab>,
        team: List<CalcPokemonSnapshot>,
        textScale: Float,
        uiScale: Float
    ): List<TabBounds> {
        if (tabs.isEmpty()) return emptyList()

        val tabH = (TAB_HEIGHT * uiScale).roundToInt().coerceAtLeast(8)
        val tabGap = (TAB_GAP * uiScale).roundToInt().coerceAtLeast(2)
        val hpByUuid: Map<UUID, Pair<Int, Int>> = team.associate { it.uuid to (it.currentHp to it.maxHp) }
        val columns = tabColumns(tabs.size, availableWidth)
        val tabWidth = ((availableWidth - (columns - 1) * tabGap) / columns).coerceAtLeast(TAB_MIN_WIDTH)
        val tabTextScale = (textScale - TAB_TEXT_SCALE_OFFSET).coerceAtLeast(TAB_TEXT_SCALE_MIN)
        val bounds = mutableListOf<TabBounds>()
        val labelInsetX = (3 * uiScale).roundToInt().coerceAtLeast(1)
        val labelInsetY = (3 * uiScale).roundToInt().coerceAtLeast(1)
        val hpBarH = (2 * uiScale).roundToInt().coerceAtLeast(1)

        tabs.forEachIndexed { index, tab ->
            val row = index / columns
            val column = index % columns
            val x = startX + column * (tabWidth + tabGap)
            val y = startY + row * (tabH + tabGap)
            val background = when {
                tab.isDisabled -> UIUtils.color(45, 28, 32, 235)
                tab.isSelected -> UIUtils.color(34, 68, 56, 235)
                tab.isActive -> UIUtils.color(30, 44, 62, 235)
                else -> UIUtils.color(23, 33, 49, 235)
            }
            val accent = when {
                tab.isDisabled -> UIUtils.color(150, 90, 95, 255)
                tab.isSelected -> V2_ACCENT_GREEN
                tab.isActive -> V2_ACCENT_BLUE
                else -> V2_TEXT_LABEL
            }
            context.fill(x, y, x + tabWidth, y + tabH, background)
            context.fill(x, y, x + tabWidth, y + 1, accent)

            // HP fill bar at bottom.
            val hp = hpByUuid[tab.uuid]
            if (hp != null) {
                val max = hp.second.coerceAtLeast(1)
                val pct = ((hp.first * 100.0) / max).coerceIn(0.0, 100.0)
                val innerW = (tabWidth - 2).coerceAtLeast(1)
                val barTop = y + tabH - hpBarH - 1
                val barBot = y + tabH - 1
                context.fill(x + 1, barTop, x + 1 + innerW, barBot, HP_TRACK)
                if (hp.first > 0) {
                    val fillW = ((innerW * pct) / 100.0).roundToInt().coerceAtLeast(1)
                    val col = when {
                        pct > 50 -> HP_GOOD
                        pct > 20 -> HP_MID
                        else -> HP_LOW
                    }
                    context.fill(x + 1, barTop, x + 1 + fillW, barBot, col)
                }
            }

            val rawLabel = buildString {
                if (tab.isActive) append("*")
                append(tab.label)
            }
            val fitted = truncate(rawLabel, (tabWidth - 6).coerceAtLeast(10), tabTextScale)
            UIUtils.drawText(
                context,
                fitted,
                (x + labelInsetX).toFloat(),
                (y + labelInsetY).toFloat(),
                if (tab.isDisabled) V2_TEXT_LABEL else V2_TEXT,
                tabTextScale
            )
            // Strikethrough fainted.
            if (tab.isDisabled) {
                val strikeY = y + (tabH / 2)
                context.fill(x + 2, strikeY, x + tabWidth - 2, strikeY + 1, UIUtils.color(200, 120, 120, 200))
            }
            bounds += TabBounds(tab.uuid, x, y, tabWidth, tabH, tab.isDisabled)
        }

        return bounds
    }

    private fun tabBlockHeight(tabs: List<CalcPreviewTab>, availableWidth: Int, uiScale: Float): Int {
        if (tabs.isEmpty()) return 0
        val tabH = (TAB_HEIGHT * uiScale).roundToInt().coerceAtLeast(8)
        val tabGap = (TAB_GAP * uiScale).roundToInt().coerceAtLeast(2)
        val pad = (6 * uiScale).roundToInt().coerceAtLeast(2)
        val columns = tabColumns(tabs.size, availableWidth)
        val rows = ceil(tabs.size / columns.toDouble()).toInt()
        return rows * tabH + (rows - 1) * tabGap + pad
    }

    private fun tabColumns(tabCount: Int, availableWidth: Int): Int {
        // Prefer to fit all tabs on one row. Only wrap if individual tabs would
        // be narrower than minTabWidth (unreadable).
        val minTabWidth = 22
        val maxByWidth = ((availableWidth + TAB_GAP) / (minTabWidth + TAB_GAP)).coerceAtLeast(1)
        return tabCount.coerceAtMost(maxByWidth).coerceAtLeast(1)
    }

    private fun drawMoveRow(context: DrawContext, x: Int, y: Float, row: CalcMoveRow, color: Int, scale: Float, uiScale: Float, compact: Boolean, defenderHpPct: Int) {
        val sevBar = severityBar(row)
        val sevFg = severityFg(row)
        val yInt = y.toInt()
        val barYOffset = (7 * uiScale).roundToInt().coerceAtLeast(3)
        val barWidth = (78 * uiScale).roundToInt().coerceAtLeast(20)

        UIUtils.drawText(context, row.moveName, x.toFloat(), y, color, scale)
        UIUtils.drawText(context, row.damageText, x + 84f * uiScale, y, V2_TEXT_DIM, scale)
        UIUtils.drawText(context, row.koText, x + 144f * uiScale, y, sevFg, scale)

        // Damage-vs-HP bar under the name (skip status / missing data). Always shown so
        // it works regardless of compact mode; compact just affects surrounding sections.
        if (!row.isStatus && row.minPercent != null && row.maxPercent != null) {
            drawDamageBar(context, x, yInt + barYOffset, barWidth, row.minPercent, row.maxPercent, defenderHpPct, sevBar, uiScale)
        }
    }

    private fun severityFg(row: CalcMoveRow): Int {
        if (row.isStatus) return SEV_STATUS_FG
        val ko = row.koText.uppercase()
        return when {
            "OHKO" in ko -> SEV_OHKO_FG
            "2HKO" in ko -> SEV_2HKO_FG
            "3HKO" in ko -> SEV_3HKO_FG
            else -> SEV_WEAK_FG
        }
    }

    private fun severityBar(row: CalcMoveRow): Int {
        if (row.isStatus) return SEV_STATUS_BAR
        val ko = row.koText.uppercase()
        return when {
            "OHKO" in ko -> SEV_OHKO_BAR
            "2HKO" in ko -> SEV_2HKO_BAR
            "3HKO" in ko -> SEV_3HKO_BAR
            else -> SEV_WEAK_BAR
        }
    }

    /**
     * Damage-vs-HP bar: shows how much of the defender's HP the move will take.
     *   [ green: guaranteed remaining ][ severity: HP at risk ][ empty: pre-existing damage ]
     * Full width represents the defender's max HP.
     */
    private fun drawDamageBar(
        context: DrawContext, x: Int, y: Int, w: Int,
        minDmgPct: Double, maxDmgPct: Double,
        defenderHpPct: Int, sevColor: Int,
        uiScale: Float = 1.0f
    ) {
        val width = w.coerceAtLeast(4)
        val h = (4 * uiScale).roundToInt().coerceAtLeast(2)
        context.fill(x, y, x + width, y + h, HP_TRACK)

        val currentHp = defenderHpPct.coerceIn(0, 100).toDouble()
        val hpAfterMax = (currentHp - maxDmgPct).coerceAtLeast(0.0)
        val currentEnd = ((width * currentHp) / 100.0).roundToInt().coerceIn(0, width)
        val greenEnd = ((width * hpAfterMax) / 100.0).roundToInt().coerceIn(0, currentEnd)

        // Guaranteed remaining HP (solid, HP-colored by post-hit HP%).
        if (greenEnd > 0) {
            val hpCol = when {
                hpAfterMax > 50 -> HP_GOOD
                hpAfterMax > 20 -> HP_MID
                else -> HP_LOW
            }
            context.fill(x, y, x + greenEnd, y + h, hpCol)
        }
        // HP at risk (severity color, slightly dimmed so it reads as "damage zone" not solid).
        if (currentEnd > greenEnd) {
            val dimmed = (sevColor and 0x00FFFFFF) or (0xC0 shl 24)
            context.fill(x + greenEnd, y, x + currentEnd, y + h, dimmed)
        }
        // 100% tick at right edge (shows full-HP benchmark when defender is at full).
        if (defenderHpPct >= 99) {
            context.fill(x + width - 1, y - 1, x + width, y + h, UIUtils.color(255, 255, 255, 64))
        }
    }

    private fun drawHpBar(context: DrawContext, x: Int, y: Int, w: Int, pct: Double, uiScale: Float = 1.0f) {
        val width = w.coerceAtLeast(4)
        val h = (4 * uiScale).roundToInt().coerceAtLeast(2)
        context.fill(x, y, x + width, y + h, HP_TRACK)
        val clamped = pct.coerceIn(0.0, 100.0)
        val fill = ((width * clamped) / 100.0).roundToInt().coerceAtLeast(if (pct > 0) 1 else 0)
        val col = when {
            clamped > 50 -> HP_GOOD
            clamped > 20 -> HP_MID
            else -> HP_LOW
        }
        context.fill(x, y, x + fill, y + h, col)
    }

    private fun drawMatchupSection(
        context: DrawContext,
        cellX: Int,
        y: Int,
        cellW: Int,
        player: CalcPokemonSnapshot?,
        opponent: CalcPokemonSnapshot?,
        speedText: String?,
        textScale: Float,
        uiScale: Float,
        compact: Boolean
    ): Int {
        fun s(n: Int): Int = (n * uiScale).roundToInt().coerceAtLeast(1)
        val youPct = hpPercent(player)
        val oppPct = hpPercent(opponent)
        val (arrow, arrowColor) = speedArrow(speedText)
        if (compact) {
            val youLabel = "You ${youPct}%"
            val oppLabel = "Opp ${oppPct}%"
            val youW = (MinecraftClient.getInstance().textRenderer.getWidth(youLabel) * textScale).toInt()
            UIUtils.drawText(context, youLabel, (cellX + 6).toFloat(), (y + 1).toFloat(), V2_TEXT, textScale)
            UIUtils.drawText(context, arrow, (cellX + 6 + youW + s(4)).toFloat(), (y + 1).toFloat(), arrowColor, textScale)
            val arrowW = (MinecraftClient.getInstance().textRenderer.getWidth(arrow) * textScale).toInt()
            UIUtils.drawText(context, oppLabel, (cellX + 6 + youW + s(4) + arrowW + s(4)).toFloat(), (y + 1).toFloat(), V2_TEXT, textScale)
            return y + s(12).coerceAtLeast(7)
        }
        val colW = ((cellW - 12 - 3) / 2).coerceAtLeast(40)
        val youX = cellX + 6
        val oppX = cellX + 6 + colW + 3
        UIUtils.drawText(context, "YOU", youX.toFloat(), y.toFloat(), V2_TEXT_LABEL, textScale)
        UIUtils.drawText(context, "OPP", oppX.toFloat(), y.toFloat(), V2_TEXT_LABEL, textScale)
        // Speed arrow centered between the column labels.
        val arrowW = (MinecraftClient.getInstance().textRenderer.getWidth(arrow) * textScale).toInt()
        UIUtils.drawText(context, arrow, (cellX + 6 + colW + 3 - (arrowW / 2) - s(2)).toFloat(), y.toFloat(), arrowColor, textScale)
        val nameY = y + s(9)
        val nameW = (colW - s(4)).coerceAtLeast(s(20).coerceAtLeast(12))
        player?.let {
            UIUtils.drawText(context, truncate(it.displayName, nameW, textScale), youX.toFloat(), nameY.toFloat(), V2_TEXT, textScale)
        }
        opponent?.let {
            UIUtils.drawText(context, truncate(it.displayName, nameW, textScale), oppX.toFloat(), nameY.toFloat(), V2_TEXT, textScale)
        }
        val hpY = y + s(19)
        val barW = (colW - s(24)).coerceAtLeast(s(20).coerceAtLeast(12))
        drawHpBar(context, youX, hpY, barW, youPct.toDouble(), uiScale)
        UIUtils.drawText(context, "${youPct}%", (youX + barW + s(2)).toFloat(), (hpY - 1).toFloat(), V2_TEXT_DIM, textScale)
        drawHpBar(context, oppX, hpY, barW, oppPct.toDouble(), uiScale)
        UIUtils.drawText(context, "${oppPct}%", (oppX + barW + s(2)).toFloat(), (hpY - 1).toFloat(), V2_TEXT_DIM, textScale)
        UIUtils.drawPopupRowDivider(context, cellX, cellW, y + s(26))
        return y + s(30)
    }

    // Small speed indicator: ">" = you faster, "<" = opp faster, "=" = tie/unknown.
    // Color: green = confirmed, yellow = likely (inferred), dim gray = tie/unknown.
    private fun speedArrow(speedText: String?): Pair<String, Int> {
        if (speedText == null) return ">" to V2_TEXT_LABEL  // inconclusive but don't eat space
        val lc = speedText.lowercase()
        return when {
            "you likely move first" in lc -> ">" to V2_ACCENT_YELLOW
            "you move first" in lc -> ">" to HP_GOOD
            "opponent likely moves first" in lc -> "<" to V2_ACCENT_YELLOW
            "opponent moves first" in lc -> "<" to HP_LOW
            else -> "=" to V2_TEXT_LABEL
        }
    }

    private fun hpPercent(p: CalcPokemonSnapshot?): Int {
        if (p == null) return 0
        val max = p.maxHp.coerceAtLeast(1)
        return ((p.currentHp * 100.0) / max).roundToInt().coerceIn(0, 100)
    }

    private fun truncate(s: String, maxW: Int, scale: Float): String {
        val tr = MinecraftClient.getInstance().textRenderer
        if ((tr.getWidth(s) * scale) <= maxW) return s
        var out = s
        while (out.length > 1 && (tr.getWidth("$out…") * scale) > maxW) {
            out = out.substring(0, out.length - 1)
        }
        return "$out…"
    }

    private fun drawStatePill(context: DrawContext, x: Int, y: Int, state: InferenceValueState, scale: Float, uiScale: Float = 1.0f, isOverride: Boolean = false) {
        val (bg, fg, label) = when {
            isOverride -> Triple(PILL_MANUAL_BG, V2_ACCENT_BLUE, "MANUAL")
            state == InferenceValueState.REVEALED -> Triple(PILL_SEEN_BG, V2_ACCENT_GREEN, "SEEN")
            state == InferenceValueState.GUESSED -> Triple(PILL_LIKELY_BG, V2_ACCENT_YELLOW, "LIKELY")
            else -> Triple(PILL_UNK_BG, V2_TEXT_DIM, "?")
        }
        val pillScale = (scale - 0.15f).coerceAtLeast(0.42f)
        val tw = (MinecraftClient.getInstance().textRenderer.getWidth(label) * pillScale).toInt()
        val padX = (3 * uiScale).roundToInt().coerceAtLeast(1)
        val padY = (1 * uiScale).roundToInt().coerceAtLeast(1)
        val pillW = tw + padX * 2
        val pillH = (8 * uiScale).roundToInt().coerceAtLeast(5)
        context.fill(x, y, x + pillW, y + pillH, bg)
        UIUtils.drawText(context, label, (x + padX).toFloat(), (y + padY).toFloat(), fg, pillScale)
    }

    private fun drawResizeHandle(context: DrawContext, x: Int, y: Int, width: Int, height: Int) {
        UIUtils.drawCornerHandle(
            context = context,
            cornerX = x + width - 5,
            cornerY = y + height - 5,
            length = 6,
            thickness = 2,
            color = UIUtils.color(120, 140, 160, 255),
            bottomRight = true
        )
    }

    private fun findTabAt(mouseX: Int, mouseY: Int, tabs: List<TabBounds>): TabBounds? {
        return tabs.firstOrNull { contains(mouseX, mouseY, it.x, it.y, it.width, it.height) }
    }

    private fun contains(mouseX: Int, mouseY: Int, x: Int, y: Int, width: Int, height: Int): Boolean {
        return mouseX in x..(x + width) && mouseY in y..(y + height)
    }

    private fun formatInference(value: String?): String {
        // State is shown via pill chip in the render path; label stays plain.
        return value ?: "Unknown"
    }

    private fun normalizeForUsageLookup(value: String?): String {
        // Mirrors normalizeToken in CalcBattleSnapshot.kt so the renderer can
        // hit the EffectiveBattleSet's usage % maps without leaking the
        // internal helper here.
        return value.orEmpty().lowercase()
            .replace(" ", "")
            .replace("-", "")
            .replace("_", "")
            .replace("'", "")
            .replace(".", "")
    }

    private fun buildOverrideRowText(value: String, usagePercent: Double?, overridden: Boolean): String {
        val pct = usagePercent?.takeIf { it > 0.0 }?.let { " (${it.roundToInt()}%)" } ?: ""
        val star = if (overridden) "*" else ""
        return "$value$pct$star"
    }

    private fun speedColor(speedText: String): Int {
        return when {
            "you move first" in speedText.lowercase() -> UIUtils.color(120, 235, 170)
            "you likely move first" in speedText.lowercase() -> UIUtils.color(150, 220, 175)
            "opponent moves first" in speedText.lowercase() -> UIUtils.color(255, 145, 125)
            "opponent likely moves first" in speedText.lowercase() -> UIUtils.color(245, 175, 120)
            else -> UIUtils.color(220, 205, 120)
        }
    }

    private data class TabBounds(
        val uuid: UUID,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val disabled: Boolean
    )

    private data class PendingTabSelection(
        val uuid: UUID,
        val isPlayerSide: Boolean
    )

    private enum class SectionToggle {
        TEAM,
        MOVES
    }
}
