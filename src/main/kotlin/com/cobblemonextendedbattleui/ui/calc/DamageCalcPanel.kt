package com.cobblemonextendedbattleui.ui.calc

import com.cobblemonextendedbattleui.UIUtils
import com.cobblemonextendedbattleui.calc.CalcComputationService
import com.cobblemonextendedbattleui.calc.CalcMoveRow
import com.cobblemonextendedbattleui.calc.CalcPreviewTab
import com.cobblemonextendedbattleui.calc.InferenceValueState
import com.cobblemonextendedbattleui.compat.delta.DeltaBattlePlatformAdapter
import com.cobblemonextendedbattleui.ui.shared.WidgetInteractionHandler
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import org.lwjgl.glfw.GLFW
import java.util.UUID
import kotlin.math.ceil

object DamageCalcPanel {
    private const val DEFAULT_WIDTH = 228
    private const val DEFAULT_HEIGHT = 214
    private const val COLLAPSED_HEIGHT = 28
    private const val RESIZE_HANDLE = 6
    private const val HEADER_HEIGHT = 18
    private const val BASE_FONT_SCALE = 0.7f
    private const val TAB_HEIGHT = 11
    private const val TAB_GAP = 3
    private const val TAB_TEXT_SCALE_OFFSET = 0.18f
    private const val TAB_TEXT_SCALE_MIN = 0.42f
    private const val TAB_TEXT_MAX_LENGTH = 5
    private const val TAB_MIN_WIDTH = 20

    private val interaction = WidgetInteractionHandler(UIUtils.ActivePanel.DAMAGE_CALC).apply {
        dragThreshold = 4
    }

    private var wasMouseDown = false
    private var lastBounds = intArrayOf(0, 0, 0, 0)
    private var lastTeamHeaderBounds = intArrayOf(0, 0, 0, 0)
    private var lastSummaryBounds = intArrayOf(0, 0, 0, 0)
    private var lastMovesHeaderBounds = intArrayOf(0, 0, 0, 0)
    private var lastPlayerTabBounds = emptyList<TabBounds>()
    private var lastOpponentTabBounds = emptyList<TabBounds>()
    private var summaryClickArmed = false
    private var pendingTabSelection: PendingTabSelection? = null
    private var pendingSectionToggle: SectionToggle? = null

    fun initialize() {
        CalcPanelState.load()
    }

    fun render(context: DrawContext) {
        if (!CalcPanelState.enabled || !DeltaBattlePlatformAdapter.isBattleActive()) {
            interaction.releaseAll()
            wasMouseDown = false
            pendingTabSelection = null
            pendingSectionToggle = null
            return
        }

        val mc = MinecraftClient.getInstance()
        val model = CalcComputationService.currentModel() ?: return
        val screenWidth = mc.window.scaledWidth
        val width = CalcPanelState.width ?: DEFAULT_WIDTH
        val fullHeight = CalcPanelState.height ?: DEFAULT_HEIGHT
        val height = if (CalcPanelState.expanded) fullHeight else COLLAPSED_HEIGHT
        val x = CalcPanelState.x ?: (screenWidth - width - 14)
        val y = CalcPanelState.y ?: 18

        lastBounds = intArrayOf(x, y, width, height)
        handleInput(mc, x, y, width, height)

        UIUtils.renderPopupFrame(context, x, y, width, height)
        val cellX = x + UIUtils.FRAME_INSET
        val cellY = y + UIUtils.FRAME_INSET
        val cellW = width - UIUtils.FRAME_INSET * 2
        UIUtils.drawPopupCell(context, cellX, cellY, cellW, HEADER_HEIGHT)
        UIUtils.drawText(context, "DAMAGE CALC", (cellX + 6).toFloat(), (cellY + 5).toFloat(), UIUtils.color(255, 210, 80), BASE_FONT_SCALE)
        UIUtils.drawText(context, "T${model.snapshot.turn}", (cellX + cellW - 28).toFloat(), (cellY + 5).toFloat(), UIUtils.color(255, 210, 80), BASE_FONT_SCALE)

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

        val tabAreaWidth = cellW - 12
        val teamHeader = if (CalcPanelState.teamSectionCollapsed) "[+] TEAM PREVIEW" else "[-] TEAM PREVIEW"
        UIUtils.drawText(context, teamHeader, (cellX + 6).toFloat(), textY.toFloat(), UIUtils.color(120, 205, 255), textScale)
        lastTeamHeaderBounds = intArrayOf(cellX, textY - 2, cellW, 11)
        textY += 11

        if (!CalcPanelState.teamSectionCollapsed) {
            UIUtils.drawText(context, "YOUR TEAM", (cellX + 6).toFloat(), textY.toFloat(), UIUtils.color(120, 205, 255), textScale)
            textY += 9
            lastPlayerTabBounds = drawTabs(context, cellX + 6, textY, tabAreaWidth, model.playerTabs, textScale)
            textY += tabBlockHeight(model.playerTabs, tabAreaWidth)

            if (model.opponentTabs.size > 1) {
                UIUtils.drawText(context, "OPPONENT", (cellX + 6).toFloat(), textY.toFloat(), UIUtils.color(255, 165, 135), textScale)
                textY += 9
                lastOpponentTabBounds = drawTabs(context, cellX + 6, textY, tabAreaWidth, model.opponentTabs, textScale)
                textY += tabBlockHeight(model.opponentTabs, tabAreaWidth)
            } else {
                lastOpponentTabBounds = emptyList()
            }
        } else {
            lastPlayerTabBounds = emptyList()
            lastOpponentTabBounds = emptyList()
            UIUtils.drawText(context, "Tap to expand", (cellX + 6).toFloat(), textY.toFloat() + 8, UIUtils.color(140, 150, 165), textScale)
            textY += 16
        }

        val summaryToggle = if (CalcPanelState.summaryExpanded) "[-]" else "[+]"
        val summaryStartY = textY
        UIUtils.drawText(context, "$summaryToggle ${model.matchupLabel}", (cellX + 6).toFloat(), textY.toFloat(), UIUtils.color(160, 230, 190), textScale)
        textY += 11
        if (CalcPanelState.summaryExpanded) {
            UIUtils.drawText(
                context,
                if (model.isPreview) "Previewing switch matchup" else "Current active matchup",
                (cellX + 6).toFloat(),
                textY.toFloat(),
                if (model.isPreview) UIUtils.color(255, 220, 140) else UIUtils.color(140, 205, 235),
                textScale
            )
            textY += 10
            model.switchSummaryText?.let { switchSummary ->
                UIUtils.drawText(context, switchSummary, (cellX + 6).toFloat(), textY.toFloat(), UIUtils.color(220, 225, 230), textScale)
                textY += 10
            }
            model.hazardNoteText?.let { note ->
                UIUtils.drawText(context, note, (cellX + 6).toFloat(), textY.toFloat(), UIUtils.color(245, 180, 120), textScale)
                textY += 10
            }
            model.speedText?.let { speedText ->
                UIUtils.drawText(context, speedText, (cellX + 6).toFloat(), textY.toFloat(), speedColor(speedText), textScale)
                textY += 10
            }
            UIUtils.drawText(context, "Item: ${formatInference(model.opponentSet.item.first, model.opponentSet.item.second)}", (cellX + 6).toFloat(), textY.toFloat(), UIUtils.color(220, 225, 230), textScale)
            textY += 10
            UIUtils.drawText(context, "Ability: ${formatInference(model.opponentSet.ability.first, model.opponentSet.ability.second)}", (cellX + 6).toFloat(), textY.toFloat(), UIUtils.color(220, 225, 230), textScale)
            textY += 10
            UIUtils.drawText(context, "Spread: ${model.opponentSet.spreadLabel ?: "Unknown"}", (cellX + 6).toFloat(), textY.toFloat(), UIUtils.color(180, 190, 205), textScale)
            textY += 10
            UIUtils.drawText(context, model.opponentSet.sourceLabel, (cellX + 6).toFloat(), textY.toFloat(), UIUtils.color(140, 150, 165), textScale)
            textY += 12
        } else {
            UIUtils.drawText(context, model.opponentSet.sourceLabel, (cellX + 20).toFloat(), (textY - 1).toFloat(), UIUtils.color(140, 150, 165), textScale)
            textY += 8
        }

        lastSummaryBounds = intArrayOf(cellX, summaryStartY - 2, cellW, (textY - summaryStartY + 2).coerceAtLeast(12))
        UIUtils.drawPopupRowDivider(context, cellX, cellW, textY)
        textY += 6

        val movesHeader = if (CalcPanelState.movesSectionCollapsed) "[+] MOVE PREDICTIONS" else "[-] MOVE PREDICTIONS"
        UIUtils.drawText(context, movesHeader, (cellX + 6).toFloat(), textY.toFloat(), UIUtils.color(255, 150, 130), textScale)
        lastMovesHeaderBounds = intArrayOf(cellX, textY - 2, cellW, 11)
        textY += 11

        if (!CalcPanelState.movesSectionCollapsed) {
            UIUtils.drawText(context, "YOUR MOVES", (cellX + 6).toFloat(), textY.toFloat(), UIUtils.color(100, 200, 255), textScale)
            textY += 10
            model.yourMoves.take(4).forEach { row ->
                drawMoveRow(context, cellX + 6, textY.toFloat(), row, if (row.emphasized) UIUtils.color(255, 240, 150) else UIUtils.color(230, 230, 230), textScale)
                textY += 10
            }

            textY += 3
            UIUtils.drawText(context, "LIKELY OPPONENT MOVES", (cellX + 6).toFloat(), textY.toFloat(), UIUtils.color(255, 150, 130), textScale)
            textY += 10
            model.opponentMoves.take(4).forEach { row ->
                drawMoveRow(context, cellX + 6, textY.toFloat(), row, UIUtils.color(230, 230, 230), textScale)
                textY += 10
            }
        } else {
            UIUtils.drawText(context, "Tap to expand", (cellX + 6).toFloat(), textY.toFloat() + 8, UIUtils.color(140, 150, 165), textScale)
            textY += 16
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

    private fun handleInput(mc: MinecraftClient, x: Int, y: Int, width: Int, height: Int) {
        val mouseX = (mc.mouse.x * mc.window.scaledWidth / mc.window.width).toInt()
        val mouseY = (mc.mouse.y * mc.window.scaledHeight / mc.window.height).toInt()
        val mouseDown = GLFW.glfwGetMouseButton(mc.window.handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
        val canInteract = UIUtils.canInteract(UIUtils.ActivePanel.DAMAGE_CALC)
        val summaryBounds = lastSummaryBounds

        if (mouseDown) {
            when {
                interaction.isDragging -> {
                    summaryClickArmed = false
                    pendingTabSelection = null
                    pendingSectionToggle = null
                    interaction.updateDrag(mouseX, mouseY, mc.window.scaledWidth, mc.window.scaledHeight, width, height)?.let { (nextX, nextY) ->
                        CalcPanelState.setPosition(nextX, nextY)
                    }
                }
                interaction.isResizing -> {
                    summaryClickArmed = false
                    pendingTabSelection = null
                    pendingSectionToggle = null
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
                    if (resizeZone != UIUtils.ResizeZone.NONE) {
                        summaryClickArmed = false
                        pendingTabSelection = null
                        pendingSectionToggle = null
                        interaction.startResize(mouseX, mouseY, resizeZone, x, y, width, height)
                    } else if (contains(mouseX, mouseY, lastTeamHeaderBounds[0], lastTeamHeaderBounds[1], lastTeamHeaderBounds[2], lastTeamHeaderBounds[3])) {
                        summaryClickArmed = false
                        pendingTabSelection = null
                        pendingSectionToggle = SectionToggle.TEAM
                    } else if (contains(mouseX, mouseY, lastMovesHeaderBounds[0], lastMovesHeaderBounds[1], lastMovesHeaderBounds[2], lastMovesHeaderBounds[3])) {
                        summaryClickArmed = false
                        pendingTabSelection = null
                        pendingSectionToggle = SectionToggle.MOVES
                    } else if (clickedPlayerTab != null && !clickedPlayerTab.disabled) {
                        summaryClickArmed = false
                        pendingSectionToggle = null
                        pendingTabSelection = PendingTabSelection(clickedPlayerTab.uuid, isPlayerSide = true)
                    } else if (clickedOpponentTab != null && !clickedOpponentTab.disabled) {
                        summaryClickArmed = false
                        pendingSectionToggle = null
                        pendingTabSelection = PendingTabSelection(clickedOpponentTab.uuid, isPlayerSide = false)
                    } else if (contains(mouseX, mouseY, x + UIUtils.FRAME_INSET, y + UIUtils.FRAME_INSET, width - UIUtils.FRAME_INSET * 2, HEADER_HEIGHT)) {
                        summaryClickArmed = false
                        pendingSectionToggle = null
                        pendingTabSelection = null
                        interaction.startDrag(mouseX, mouseY, x, y)
                    } else if (CalcPanelState.expanded && contains(mouseX, mouseY, summaryBounds[0], summaryBounds[1], summaryBounds[2], summaryBounds[3])) {
                        pendingTabSelection = null
                        pendingSectionToggle = null
                        summaryClickArmed = true
                    }
                }
            }
        } else {
            if (interaction.isDragging) {
                summaryClickArmed = false
                pendingTabSelection = null
                pendingSectionToggle = null
                val didDrag = interaction.endDrag()
                if (!didDrag && contains(mouseX, mouseY, x + UIUtils.FRAME_INSET, y + UIUtils.FRAME_INSET, width - UIUtils.FRAME_INSET * 2, HEADER_HEIGHT)) {
                    CalcPanelState.toggleExpanded()
                }
                CalcPanelState.save()
            } else if (interaction.isResizing) {
                summaryClickArmed = false
                pendingTabSelection = null
                pendingSectionToggle = null
                interaction.endResize()
                CalcPanelState.save()
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
        textScale: Float
    ): List<TabBounds> {
        if (tabs.isEmpty()) return emptyList()

        val columns = tabColumns(tabs.size, availableWidth)
        val tabWidth = ((availableWidth - (columns - 1) * TAB_GAP) / columns).coerceAtLeast(TAB_MIN_WIDTH)
        val tabTextScale = (textScale - TAB_TEXT_SCALE_OFFSET).coerceAtLeast(TAB_TEXT_SCALE_MIN)
        val bounds = mutableListOf<TabBounds>()

        tabs.forEachIndexed { index, tab ->
            val row = index / columns
            val column = index % columns
            val x = startX + column * (tabWidth + TAB_GAP)
            val y = startY + row * (TAB_HEIGHT + TAB_GAP)
            val background = when {
                tab.isDisabled -> UIUtils.color(55, 60, 70, 220)
                tab.isSelected -> UIUtils.color(60, 120, 95, 235)
                tab.isActive -> UIUtils.color(85, 100, 135, 235)
                else -> UIUtils.color(45, 52, 60, 235)
            }
            val accent = when {
                tab.isDisabled -> UIUtils.color(95, 105, 115, 255)
                tab.isSelected -> UIUtils.color(180, 245, 185, 255)
                tab.isActive -> UIUtils.color(160, 215, 255, 255)
                else -> UIUtils.color(150, 160, 170, 255)
            }
            context.fill(x, y, x + tabWidth, y + TAB_HEIGHT, background)
            context.fill(x, y, x + tabWidth, y + 1, accent)
            val label = buildString {
                if (tab.isActive) append("*")
                append(tab.label.take(TAB_TEXT_MAX_LENGTH))
            }
            UIUtils.drawText(
                context,
                label,
                (x + 3).toFloat(),
                (y + 3).toFloat(),
                if (tab.isDisabled) UIUtils.color(135, 140, 150) else UIUtils.color(230, 235, 238),
                tabTextScale
            )
            bounds += TabBounds(tab.uuid, x, y, tabWidth, TAB_HEIGHT, tab.isDisabled)
        }

        return bounds
    }

    private fun tabBlockHeight(tabs: List<CalcPreviewTab>, availableWidth: Int): Int {
        if (tabs.isEmpty()) return 0
        val columns = tabColumns(tabs.size, availableWidth)
        val rows = ceil(tabs.size / columns.toDouble()).toInt()
        return rows * TAB_HEIGHT + (rows - 1) * TAB_GAP + 6
    }

    private fun tabColumns(tabCount: Int, availableWidth: Int): Int {
        val widthBasedColumns = when {
            availableWidth >= 260 -> tabCount.coerceAtMost(6)
            availableWidth >= 200 -> tabCount.coerceAtMost(4)
            availableWidth >= 140 -> tabCount.coerceAtMost(3)
            availableWidth >= 100 -> tabCount.coerceAtMost(2)
            else -> 1
        }
        return tabCount.coerceAtMost(widthBasedColumns).coerceAtLeast(1)
    }

    private fun drawMoveRow(context: DrawContext, x: Int, y: Float, row: CalcMoveRow, color: Int, scale: Float) {
        UIUtils.drawText(context, row.moveName, x.toFloat(), y, color, scale)
        UIUtils.drawText(context, row.damageText, (x + 84).toFloat(), y, UIUtils.color(220, 225, 230), scale)
        UIUtils.drawText(context, row.koText, (x + 144).toFloat(), y, UIUtils.color(255, 180, 80), scale)
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

    private fun formatInference(value: String?, state: InferenceValueState): String {
        val label = value ?: "Unknown"
        return when (state) {
            InferenceValueState.REVEALED -> "$label [R]"
            InferenceValueState.GUESSED -> "$label [G]"
            InferenceValueState.UNKNOWN -> label
        }
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
