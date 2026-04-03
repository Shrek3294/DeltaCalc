package com.cobblemonextendedbattleui.ui.calc

import com.cobblemonextendedbattleui.UIUtils
import com.cobblemonextendedbattleui.calc.CalcComputationService
import com.cobblemonextendedbattleui.calc.CalcMoveRow
import com.cobblemonextendedbattleui.calc.InferenceValueState
import com.cobblemonextendedbattleui.compat.delta.DeltaBattlePlatformAdapter
import com.cobblemonextendedbattleui.ui.shared.WidgetInteractionHandler
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import org.lwjgl.glfw.GLFW

object DamageCalcPanel {
    private const val DEFAULT_WIDTH = 228
    private const val DEFAULT_HEIGHT = 176
    private const val COLLAPSED_HEIGHT = 28
    private const val RESIZE_HANDLE = 6
    private const val HEADER_HEIGHT = 18
    private const val BASE_FONT_SCALE = 0.8f

    private val interaction = WidgetInteractionHandler(UIUtils.ActivePanel.DAMAGE_CALC).apply {
        dragThreshold = 4
    }

    private var wasMouseDown = false
    private var lastBounds = intArrayOf(0, 0, 0, 0)
    private var lastSummaryBounds = intArrayOf(0, 0, 0, 0)
    private var summaryClickArmed = false

    fun initialize() {
        CalcPanelState.load()
    }

    fun render(context: DrawContext) {
        if (!CalcPanelState.enabled || !DeltaBattlePlatformAdapter.isBattleActive()) {
            interaction.releaseAll()
            wasMouseDown = false
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
            drawResizeHandle(context, x, y, width, height)
            return
        }

        val contentY = cellY + HEADER_HEIGHT + UIUtils.CELL_GAP
        val contentH = height - UIUtils.FRAME_INSET * 2 - HEADER_HEIGHT - UIUtils.CELL_GAP
        UIUtils.drawPopupCell(context, cellX, contentY, cellW, contentH)

        var textY = contentY + 6f
        val textScale = BASE_FONT_SCALE * CalcPanelState.fontScale
        val playerName = model.snapshot.playerActive?.speciesLabel ?: model.snapshot.playerActive?.displayName ?: "Your Active"
        val opponentName = model.snapshot.opponentActive?.speciesLabel ?: model.snapshot.opponentActive?.displayName ?: "Opponent Active"
        val summaryToggle = if (CalcPanelState.summaryExpanded) "[-]" else "[+]"
        val summaryStartY = textY.toInt()

        UIUtils.drawText(context, "$summaryToggle $playerName -> $opponentName", (cellX + 6).toFloat(), textY, UIUtils.color(160, 230, 190), textScale)
        textY += 11f
        if (CalcPanelState.summaryExpanded) {
            UIUtils.drawText(context, "Item: ${formatInference(model.opponentSet.item.first, model.opponentSet.item.second)}", (cellX + 6).toFloat(), textY, UIUtils.color(220, 225, 230), textScale)
            textY += 10f
            UIUtils.drawText(context, "Ability: ${formatInference(model.opponentSet.ability.first, model.opponentSet.ability.second)}", (cellX + 6).toFloat(), textY, UIUtils.color(220, 225, 230), textScale)
            textY += 10f
            UIUtils.drawText(context, "Spread: ${model.opponentSet.spreadLabel ?: "Unknown"}", (cellX + 6).toFloat(), textY, UIUtils.color(180, 190, 205), textScale)
            textY += 10f
            UIUtils.drawText(context, model.opponentSet.sourceLabel, (cellX + 6).toFloat(), textY, UIUtils.color(140, 150, 165), textScale)
            textY += 12f
        } else {
            UIUtils.drawText(context, model.opponentSet.sourceLabel, (cellX + 20).toFloat(), textY - 1f, UIUtils.color(140, 150, 165), textScale)
            textY += 8f
        }
        lastSummaryBounds = intArrayOf(cellX, summaryStartY - 2, cellW, (textY.toInt() - summaryStartY + 2).coerceAtLeast(12))
        UIUtils.drawPopupRowDivider(context, cellX, cellW, textY.toInt())
        textY += 6f
        UIUtils.drawText(context, "YOUR MOVES", (cellX + 6).toFloat(), textY, UIUtils.color(100, 200, 255), textScale)
        textY += 10f
        model.yourMoves.take(4).forEach { row ->
            drawMoveRow(context, cellX + 6, textY, row, if (row.emphasized) UIUtils.color(255, 240, 150) else UIUtils.color(230, 230, 230), textScale)
            textY += 10f
        }

        textY += 3f
        UIUtils.drawText(context, "LIKELY OPPONENT MOVES", (cellX + 6).toFloat(), textY, UIUtils.color(255, 150, 130), textScale)
        textY += 10f
        model.opponentMoves.take(4).forEach { row ->
            drawMoveRow(context, cellX + 6, textY, row, UIUtils.color(230, 230, 230), textScale)
            textY += 10f
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
        CalcPanelState.adjustFontScale(if (deltaY > 0) 0.05f else -0.05f)
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
                    interaction.updateDrag(mouseX, mouseY, mc.window.scaledWidth, mc.window.scaledHeight, width, height)?.let { (nextX, nextY) ->
                        CalcPanelState.setPosition(nextX, nextY)
                    }
                }
                interaction.isResizing -> {
                    summaryClickArmed = false
                    val result = interaction.calculateResize(
                        mouseX = mouseX,
                        mouseY = mouseY,
                        minWidth = 180,
                        maxWidth = (mc.window.scaledWidth * 0.6f).toInt(),
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
                    if (resizeZone != UIUtils.ResizeZone.NONE) {
                        summaryClickArmed = false
                        interaction.startResize(mouseX, mouseY, resizeZone, x, y, width, height)
                    } else if (contains(mouseX, mouseY, x + UIUtils.FRAME_INSET, y + UIUtils.FRAME_INSET, width - UIUtils.FRAME_INSET * 2, HEADER_HEIGHT)) {
                        summaryClickArmed = false
                        interaction.startDrag(mouseX, mouseY, x, y)
                    } else if (CalcPanelState.expanded && contains(mouseX, mouseY, summaryBounds[0], summaryBounds[1], summaryBounds[2], summaryBounds[3])) {
                        summaryClickArmed = true
                    }
                }
            }
        } else {
            if (interaction.isDragging) {
                summaryClickArmed = false
                val didDrag = interaction.endDrag()
                if (!didDrag && contains(mouseX, mouseY, x + UIUtils.FRAME_INSET, y + UIUtils.FRAME_INSET, width - UIUtils.FRAME_INSET * 2, HEADER_HEIGHT)) {
                    CalcPanelState.toggleExpanded()
                }
                CalcPanelState.save()
            } else if (interaction.isResizing) {
                summaryClickArmed = false
                interaction.endResize()
                CalcPanelState.save()
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

    private fun drawMoveRow(context: DrawContext, x: Int, y: Float, row: CalcMoveRow, color: Int, scale: Float) {
        UIUtils.drawText(context, row.moveName, x.toFloat(), y, color, scale)
        UIUtils.drawText(context, row.damageText, (x + 92).toFloat(), y, UIUtils.color(220, 225, 230), scale)
        UIUtils.drawText(context, row.koText, (x + 156).toFloat(), y, UIUtils.color(255, 180, 80), scale)
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
}
