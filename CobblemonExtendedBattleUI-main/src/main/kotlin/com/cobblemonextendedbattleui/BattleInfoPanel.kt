package com.cobblemonextendedbattleui

import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.battle.ClientBattleSide
import com.cobblemonextendedbattleui.ui.panel.PanelContentRenderer
import com.cobblemonextendedbattleui.ui.panel.PanelLayoutCalculator
import com.cobblemonextendedbattleui.ui.shared.ScrollbarRenderer
import com.cobblemonextendedbattleui.ui.shared.WidgetInteractionHandler
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.util.InputUtil
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import org.lwjgl.glfw.GLFW
import java.util.UUID

/**
 * Battle information panel with 9-slice textured frame and single-fill-cell layout.
 * Header cell (fixed) + one content cell that fills all remaining space.
 * Content groups separated by dividers within the cell.
 * Features draggable positioning, edge/corner resizing, and scrollable content.
 */
object BattleInfoPanel {

    // ═══════════════════════════════════════════════════════════════════════════
    // UI Translation Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private object UI {
        fun translate(key: String): String = Text.translatable("cobblemonextendedbattleui.ui.$key").string
        val ally: String get() = translate("ally")
        val enemy: String get() = translate("enemy")
    }

    private var isMinimised: Boolean = false

    /**
     * Applies the current opacity (minimized state) to a color's alpha channel.
     */
    private fun applyOpacity(color: Int): Int = UIUtils.applyMinimisedOpacity(color, isMinimised)

    /**
     * Returns a color transform lambda for drawPopupCell/drawPopupRowDivider.
     */
    private fun colorTransform(): (Int) -> Int = ::applyOpacity

    // Panel state
    var isExpanded: Boolean = false
        private set

    // Input tracking
    private var wasToggleKeyPressed: Boolean = false
    private var wasIncreaseFontKeyPressed: Boolean = false
    private var wasDecreaseFontKeyPressed: Boolean = false
    private var wasMousePressed: Boolean = false

    // Shared interaction handler for drag/resize/scrollbar
    private val interaction = WidgetInteractionHandler(UIUtils.ActivePanel.INFO_PANEL).apply {
        dragThreshold = 5  // Click-to-toggle support
    }

    private const val RESIZE_HANDLE_SIZE = 6

    // Frame tint (cool blue-gray)
    private const val FRAME_TINT_R = 0.6f
    private const val FRAME_TINT_G = 0.7f
    private const val FRAME_TINT_B = 0.85f

    // Colors
    private val RESIZE_HANDLE_COLOR = UIUtils.color(100, 120, 140, 200)
    private val RESIZE_HANDLE_HOVER = UIUtils.color(130, 160, 190, 255)
    private val SCROLLBAR_BG = UIUtils.color(40, 48, 58, 200)
    private val SCROLLBAR_THUMB = UIUtils.color(80, 95, 115, 255)
    private val SCROLLBAR_THUMB_HOVER = UIUtils.color(100, 120, 145, 255)
    private val TEXT_WHITE = UIUtils.color(255, 255, 255, 255)
    private val TEXT_LIGHT = UIUtils.color(220, 225, 230, 255)
    private val TEXT_DIM = UIUtils.color(140, 150, 165, 255)
    private val TEXT_GOLD = UIUtils.color(255, 210, 80, 255)
    private val STAT_BOOST = UIUtils.color(255, 100, 100, 255)
    private val STAT_DROP = UIUtils.color(100, 160, 255, 255)
    private val ACCENT_PLAYER = UIUtils.color(100, 200, 255, 255)
    private val ACCENT_OPPONENT = UIUtils.color(255, 130, 110, 255)
    private val ACCENT_FIELD = UIUtils.color(255, 200, 100, 255)
    private val HEADER_ACCENT = UIUtils.color(200, 165, 60, 180)

    // Layout constants
    private const val BASE_PANEL_MARGIN = 10
    private const val BASE_LINE_HEIGHT = 12
    private const val SCROLLBAR_WIDTH = 3
    private const val SECTION_LABEL_SCALE = 0.7f
    private const val GROUP_DIVIDER_EXTRA = 4  // Extra spacing for group separators

    // Base font multiplier (makes default text ~20% larger)
    private const val BASE_FONT_MULTIPLIER = 1.2f

    // Text scale (base * user font preference only, no auto-scaling)
    private var textScale: Float = BASE_FONT_MULTIPLIER

    // Line height scales with font
    private var lineHeight: Int = BASE_LINE_HEIGHT

    // Header padding (more generous than content cells)
    private const val HEADER_VPAD_TOP = 6
    private const val HEADER_VPAD_BOTTOM = 5
    private const val HEADER_ACCENT_H = 1  // Gold accent line at bottom

    // Header cell height (computed from text scale)
    private val headerCellH: Int get() = HEADER_VPAD_TOP + (8 * 0.85f * textScale).toInt() + HEADER_VPAD_BOTTOM + HEADER_ACCENT_H

    // Sub-label height (for side names inside content cell)
    private fun subLabelHeight(): Int = (8 * SECTION_LABEL_SCALE * textScale).toInt() + 2

    // Group divider height (row divider + extra spacing between major groups)
    private fun groupDividerHeight(): Int = UIUtils.DIVIDER_H + GROUP_DIVIDER_EXTRA

    // Cached panel bounds for input detection
    private var panelBoundsX = 0
    private var panelBoundsY = 0
    private var panelBoundsW = 0
    private var panelBoundsH = 0
    private var headerEndY = 0

    // Content dimensions (actual content height vs visible height)
    private var contentHeight = 0
    private var visibleContentHeight = 0

    // Scissor bounds for manual clipping (drawScaledText doesn't respect GL scissor)
    private var scissorBounds = UIUtils.ScissorBounds()

    // Shared scrollbar renderer
    private val scrollbarRenderer = ScrollbarRenderer(
        trackWidth = SCROLLBAR_WIDTH,
        bgColor = SCROLLBAR_BG,
        thumbColor = SCROLLBAR_THUMB,
        thumbHoverColor = SCROLLBAR_THUMB_HOVER,
        minThumbHeight = 6,
        opacityProvider = { { c: Int -> applyOpacity(c) } }
    )

    // Hover state for visual feedback
    private var isOverScrollbar = false

    // Track previously active Pokemon to detect switches and clear their stats
    private var previouslyActiveUUIDs: Set<UUID> = emptySet()

    // Track if we were in a battle last frame (for detecting spectate exit)
    private var wasInBattle: Boolean = false

    data class PokemonBattleData(
        val uuid: UUID,
        val name: String,
        val statChanges: Map<BattleStateTracker.BattleStat, Int>,
        val volatiles: Set<BattleStateTracker.VolatileStatusState>,
        val isAlly: Boolean
    ) {
        fun hasAnyEffects(): Boolean = statChanges.isNotEmpty() || volatiles.isNotEmpty()
    }

    fun clearBattleState() {
        previouslyActiveUUIDs = emptySet()
    }

    private fun updateScaledValues() {
        // Text scale: base multiplier * user font preference (no auto-scaling)
        textScale = BASE_FONT_MULTIPLIER * PanelConfig.fontScale
        lineHeight = (BASE_LINE_HEIGHT * textScale).toInt()
    }

    fun toggle() {
        isExpanded = !isExpanded
        PanelConfig.setStartExpanded(isExpanded)
        PanelConfig.scrollOffset = 0
        PanelConfig.save()
    }

    fun initialize() {
        PanelConfig.load()
        isExpanded = PanelConfig.startExpanded
    }

    private fun getResizeZone(mouseX: Int, mouseY: Int): UIUtils.ResizeZone {
        val x = panelBoundsX
        val y = panelBoundsY
        val w = panelBoundsW
        val h = panelBoundsH

        val onLeft = mouseX >= x - RESIZE_HANDLE_SIZE && mouseX <= x + RESIZE_HANDLE_SIZE
        val onRight = mouseX >= x + w - RESIZE_HANDLE_SIZE && mouseX <= x + w + RESIZE_HANDLE_SIZE
        val onTop = mouseY >= y - RESIZE_HANDLE_SIZE && mouseY <= y + 2
        val onBottom = mouseY >= y + h - RESIZE_HANDLE_SIZE && mouseY <= y + h + RESIZE_HANDLE_SIZE
        val withinX = mouseX >= x - RESIZE_HANDLE_SIZE && mouseX <= x + w + RESIZE_HANDLE_SIZE
        val withinY = mouseY >= y - RESIZE_HANDLE_SIZE && mouseY <= y + h + RESIZE_HANDLE_SIZE

        return when {
            onTop && onLeft && withinX && withinY -> UIUtils.ResizeZone.TOP_LEFT
            onTop && onRight && withinX && withinY -> UIUtils.ResizeZone.TOP_RIGHT
            onBottom && onLeft && withinX && withinY -> UIUtils.ResizeZone.BOTTOM_LEFT
            onBottom && onRight && withinX && withinY -> UIUtils.ResizeZone.BOTTOM_RIGHT
            onLeft && withinY -> UIUtils.ResizeZone.LEFT
            onRight && withinY -> UIUtils.ResizeZone.RIGHT
            onTop && withinX -> UIUtils.ResizeZone.TOP
            onBottom && withinX -> UIUtils.ResizeZone.BOTTOM
            else -> UIUtils.ResizeZone.NONE
        }
    }

    private fun calculateThumbHeight(trackHeight: Int): Int {
        val minThumb = (trackHeight / 4).coerceIn(6, 20)
        return ((visibleContentHeight.toFloat() / contentHeight) * trackHeight).toInt()
            .coerceIn(minThumb, trackHeight)
    }

    private fun isOverScrollbarThumb(mouseX: Int, mouseY: Int): Boolean {
        if (contentHeight <= visibleContentHeight) return false

        val scrollbarX = panelBoundsX + panelBoundsW - UIUtils.FRAME_INSET - SCROLLBAR_WIDTH
        val scrollbarY = headerEndY + UIUtils.CELL_GAP
        val scrollbarHeight = panelBoundsY + panelBoundsH - UIUtils.FRAME_INSET - scrollbarY

        if (mouseX < scrollbarX || mouseX > scrollbarX + SCROLLBAR_WIDTH) return false
        if (mouseY < scrollbarY || mouseY > scrollbarY + scrollbarHeight) return false

        val thumbHeight = calculateThumbHeight(scrollbarHeight)
        val maxScroll = contentHeight - visibleContentHeight
        val scrollRatio = if (maxScroll > 0) PanelConfig.scrollOffset.toFloat() / maxScroll else 0f
        val thumbY = scrollbarY + ((scrollbarHeight - thumbHeight) * scrollRatio).toInt()

        return mouseY >= thumbY && mouseY <= thumbY + thumbHeight
    }

    private fun handleInput(mc: MinecraftClient) {
        val handle = mc.window.handle

        // Poll keybinds directly with GLFW (Minecraft's keybinding system doesn't work during Cobblemon battle overlay)
        val toggleKey = InputUtil.fromTranslationKey(CobblemonExtendedBattleUIClient.togglePanelKey.boundKeyTranslationKey)
        val isToggleDown = UIUtils.isKeyOrButtonPressed(handle, toggleKey)
        if (isToggleDown && !wasToggleKeyPressed) toggle()
        wasToggleKeyPressed = isToggleDown

        // Font keybinds - only handle if no other panel has priority
        val otherPanelHasPriority = TeamIndicatorUI.shouldHandleFontInput() || BattleLogWidget.isMouseOverWidget() || MoveTooltipRenderer.shouldHandleFontInput()

        val increaseKey = InputUtil.fromTranslationKey(CobblemonExtendedBattleUIClient.increaseFontKey.boundKeyTranslationKey)
        val isIncreaseDown = UIUtils.isKeyOrButtonPressed(handle, increaseKey)
        if (isIncreaseDown && !wasIncreaseFontKeyPressed && !otherPanelHasPriority) {
            PanelConfig.adjustFontScale(PanelConfig.FONT_SCALE_STEP)
            PanelConfig.save()
        }
        wasIncreaseFontKeyPressed = isIncreaseDown

        val decreaseKey = InputUtil.fromTranslationKey(CobblemonExtendedBattleUIClient.decreaseFontKey.boundKeyTranslationKey)
        val isDecreaseDown = UIUtils.isKeyOrButtonPressed(handle, decreaseKey)
        if (isDecreaseDown && !wasDecreaseFontKeyPressed && !otherPanelHasPriority) {
            PanelConfig.adjustFontScale(-PanelConfig.FONT_SCALE_STEP)
            PanelConfig.save()
        }
        wasDecreaseFontKeyPressed = isDecreaseDown

        val mouseX = (mc.mouse.x * mc.window.scaledWidth / mc.window.width).toInt()
        val mouseY = (mc.mouse.y * mc.window.scaledHeight / mc.window.height).toInt()
        val isMouseDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS

        val screenWidth = mc.window.scaledWidth
        val screenHeight = mc.window.scaledHeight

        val canInteract = UIUtils.canInteract(UIUtils.ActivePanel.INFO_PANEL)
        interaction.hoveredZone = if (!interaction.isInteracting && canInteract) getResizeZone(mouseX, mouseY) else if (!canInteract) UIUtils.ResizeZone.NONE else interaction.hoveredZone
        isOverScrollbar = if (!interaction.isInteracting && canInteract) isOverScrollbarThumb(mouseX, mouseY) else if (!canInteract) false else isOverScrollbar

        val isOverPanel = mouseX >= panelBoundsX && mouseX <= panelBoundsX + panelBoundsW &&
                          mouseY >= panelBoundsY && mouseY <= panelBoundsY + panelBoundsH
        val isOverHeader = isOverPanel && mouseY <= headerEndY

        if (isMouseDown) {
            when {
                !wasMousePressed && canInteract && isOverScrollbarThumb(mouseX, mouseY) -> {
                    interaction.startScrollbarDrag(mouseY, PanelConfig.scrollOffset)
                }
                interaction.isDraggingScrollbar -> {
                    val scrollbarHeight = panelBoundsY + panelBoundsH - UIUtils.FRAME_INSET - headerEndY - UIUtils.CELL_GAP
                    val thumbHeight = calculateThumbHeight(scrollbarHeight)
                    val trackHeight = scrollbarHeight - thumbHeight

                    if (trackHeight > 0) {
                        val deltaY = mouseY - interaction.scrollbarDragStartY
                        val maxScroll = contentHeight - visibleContentHeight
                        val scrollDelta = (deltaY.toFloat() / trackHeight * maxScroll).toInt()
                        PanelConfig.scrollOffset = (interaction.scrollbarDragStartOffset + scrollDelta).coerceIn(0, maxScroll)
                    }
                }
                !wasMousePressed && canInteract && interaction.hoveredZone != UIUtils.ResizeZone.NONE -> {
                    interaction.startResize(mouseX, mouseY, interaction.hoveredZone, panelBoundsX, panelBoundsY, panelBoundsW, panelBoundsH)
                }
                interaction.isResizing -> {
                    val minW = PanelConfig.getMinWidth()
                    val minH = if (isExpanded) PanelConfig.getMinHeight() else PanelConfig.getMinCollapsedHeight()
                    val maxW = PanelConfig.getMaxWidth(screenWidth)
                    val maxH = PanelConfig.getMaxHeight(screenHeight)

                    val result = interaction.calculateResize(
                        mouseX, mouseY,
                        minWidth = minW, maxWidth = maxW,
                        minHeight = minH, maxHeight = maxH,
                        screenWidth = screenWidth, screenHeight = screenHeight
                    )

                    if (isExpanded) {
                        PanelConfig.setDimensions(result.newWidth, result.newHeight)
                    } else {
                        PanelConfig.setCollapsedDimensions(result.newWidth, result.newHeight)
                    }
                    PanelConfig.setPosition(result.newX, result.newY)
                }
                !wasMousePressed && canInteract && isOverHeader -> {
                    interaction.startDrag(mouseX, mouseY, panelBoundsX, panelBoundsY)
                }
                interaction.isDragging -> {
                    val pos = interaction.updateDrag(mouseX, mouseY, screenWidth, screenHeight, panelBoundsW, panelBoundsH)
                    if (pos != null) {
                        PanelConfig.setPosition(pos.first, pos.second)
                    }
                }
            }
        } else {
            if (interaction.isDragging) {
                val didDrag = interaction.endDrag()
                if (didDrag) {
                    PanelConfig.save()
                } else {
                    toggle()
                }
            } else if (interaction.isResizing) {
                interaction.endResize()
                PanelConfig.save()
            } else if (interaction.isDraggingScrollbar) {
                interaction.endScrollbarDrag()
            }
        }
        wasMousePressed = isMouseDown
    }

    fun onScroll(mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        val mc = MinecraftClient.getInstance()
        val scaledX = (mouseX * mc.window.scaledWidth / mc.window.width).toInt()
        val scaledY = (mouseY * mc.window.scaledHeight / mc.window.height).toInt()

        val isOverPanel = scaledX >= panelBoundsX && scaledX <= panelBoundsX + panelBoundsW &&
                          scaledY >= panelBoundsY && scaledY <= panelBoundsY + panelBoundsH

        if (isOverPanel && deltaY != 0.0) {
            val isCtrlHeld = GLFW.glfwGetKey(mc.window.handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                             GLFW.glfwGetKey(mc.window.handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS

            if (isCtrlHeld) {
                val delta = if (deltaY > 0) PanelConfig.FONT_SCALE_STEP else -PanelConfig.FONT_SCALE_STEP
                PanelConfig.adjustFontScale(delta)
                PanelConfig.save()
                return true
            } else {
                val scrollAmount = (lineHeight * 2 * if (deltaY > 0) -1 else 1)
                if (contentHeight > visibleContentHeight) {
                    val maxScroll = (contentHeight - visibleContentHeight).coerceAtLeast(0)
                    PanelConfig.scrollOffset = (PanelConfig.scrollOffset + scrollAmount).coerceIn(0, maxScroll)
                    return true
                }
            }
        }
        return false
    }

    fun render(context: DrawContext) {
        val battle = CobblemonClient.battle

        // Handle battle exit (including spectator exit)
        if (battle == null) {
            if (wasInBattle) {
                BattleStateTracker.clear()
                TeamIndicatorUI.clear()
                clearBattleState()
                BattleLog.clear()
                BattleLogWidget.clear()
                wasInBattle = false
                CobblemonExtendedBattleUI.LOGGER.debug("BattleInfoPanel: Cleared state on battle exit")
            }
            return
        }

        isMinimised = battle.minimised
        wasInBattle = true
        BattleStateTracker.checkBattleChanged(battle.battleId)

        val mc = MinecraftClient.getInstance()

        if (!isMinimised) {
            handleInput(mc)
        }

        val screenWidth = mc.window.scaledWidth
        val screenHeight = mc.window.scaledHeight
        val playerUUID = mc.player?.uuid ?: return

        val playerInSide1 = battle.side1.actors.any { it.uuid == playerUUID }
        val playerInSide2 = battle.side2.actors.any { it.uuid == playerUUID }
        val isSpectating = !playerInSide1 && !playerInSide2

        val playerSide = when {
            playerInSide1 -> battle.side1
            playerInSide2 -> battle.side2
            else -> battle.side2
        }
        val opponentSide = if (playerSide == battle.side1) battle.side2 else battle.side1

        val playerSideName = getPlayerSideName(playerSide, isSpectating, isPlayerSide = true)
        val opponentSideName = getPlayerSideName(opponentSide, isSpectating, isPlayerSide = false)

        val allyActorName = playerSide.actors.firstOrNull()?.displayName?.string ?: ""
        val opponentActorName = opponentSide.actors.firstOrNull()?.displayName?.string ?: ""
        BattleStateTracker.setPlayerNames(allyActorName, opponentActorName)

        val allyPokemon = playerSide.activeClientBattlePokemon.mapNotNull { it.battlePokemon }
        val opponentPokemon = opponentSide.activeClientBattlePokemon.mapNotNull { it.battlePokemon }
        val currentActiveUUIDs = (allyPokemon + opponentPokemon).map { it.uuid }.toSet()

        for (uuid in previouslyActiveUUIDs) {
            if (uuid !in currentActiveUUIDs) {
                val usedBatonPass = BattleStateTracker.prepareBatonPassIfUsed(uuid)
                if (!usedBatonPass) {
                    BattleStateTracker.clearPokemonStats(uuid)
                    BattleStateTracker.clearPokemonVolatiles(uuid)
                }
                BattleStateTracker.restoreOriginalTypes(uuid)
            }
        }
        previouslyActiveUUIDs = currentActiveUUIDs

        for (pokemon in allyPokemon) {
            BattleStateTracker.registerPokemon(pokemon.uuid, pokemon.displayName.string, isAlly = true)
            pokemon.properties.species?.let { speciesName ->
                BattleStateTracker.registerPokemon(pokemon.uuid, speciesName, isAlly = true)
                BattleStateTracker.registerSpeciesId(pokemon.uuid, Identifier.of("cobblemon", speciesName))
            }
        }
        for (pokemon in opponentPokemon) {
            BattleStateTracker.registerPokemon(pokemon.uuid, pokemon.displayName.string, isAlly = false)
            pokemon.properties.species?.let { speciesName ->
                BattleStateTracker.registerPokemon(pokemon.uuid, speciesName, isAlly = false)
                BattleStateTracker.registerSpeciesId(pokemon.uuid, Identifier.of("cobblemon", speciesName))
            }
        }

        for (pokemon in allyPokemon) {
            BattleStateTracker.applyBatonPassIfPending(pokemon.uuid)
        }
        for (pokemon in opponentPokemon) {
            BattleStateTracker.applyBatonPassIfPending(pokemon.uuid)
        }

        val allyPokemonData = allyPokemon.map { pokemon ->
            val statChanges = BattleStateTracker.getStatChanges(pokemon.uuid)
            val volatiles = BattleStateTracker.getVolatileStatuses(pokemon.uuid)
            PokemonBattleData(pokemon.uuid, pokemon.displayName.string, statChanges, volatiles, isAlly = true)
        }
        val opponentPokemonData = opponentPokemon.map { pokemon ->
            val statChanges = BattleStateTracker.getStatChanges(pokemon.uuid)
            val volatiles = BattleStateTracker.getVolatileStatuses(pokemon.uuid)
            PokemonBattleData(pokemon.uuid, pokemon.displayName.string, statChanges, volatiles, isAlly = false)
        }

        updateScaledValues()

        val panelWidth: Int
        val panelHeight: Int

        if (isExpanded) {
            panelWidth = PanelConfig.panelWidth ?: PanelConfig.DEFAULT_WIDTH
            contentHeight = calculateExpandedContentHeight(allyPokemonData, opponentPokemonData, panelWidth)
            val autoHeight = UIUtils.FRAME_INSET * 2 + headerCellH + UIUtils.CELL_GAP + contentHeight
            panelHeight = PanelConfig.panelHeight ?: autoHeight
        } else {
            panelWidth = PanelConfig.collapsedWidth ?: PanelConfig.DEFAULT_WIDTH
            contentHeight = calculateCollapsedContentHeight(allyPokemonData, opponentPokemonData, panelWidth)
            val autoHeight = UIUtils.FRAME_INSET * 2 + headerCellH + UIUtils.CELL_GAP + contentHeight
            panelHeight = PanelConfig.collapsedHeight ?: autoHeight
        }

        val panelX = PanelConfig.panelX ?: (screenWidth - panelWidth - BASE_PANEL_MARGIN)
        val panelY = PanelConfig.panelY ?: ((screenHeight - panelHeight) / 2)

        val clampedX = panelX.coerceIn(0, (screenWidth - panelWidth).coerceAtLeast(0))
        val clampedY = panelY.coerceIn(0, (screenHeight - panelHeight).coerceAtLeast(0))

        panelBoundsX = clampedX
        panelBoundsY = clampedY
        panelBoundsW = panelWidth
        panelBoundsH = panelHeight

        // Visible content area: panel height minus frame insets, header cell, and gap
        visibleContentHeight = panelHeight - UIUtils.FRAME_INSET * 2 - headerCellH - UIUtils.CELL_GAP

        val maxScroll = (contentHeight - visibleContentHeight).coerceAtLeast(0)
        PanelConfig.scrollOffset = PanelConfig.scrollOffset.coerceIn(0, maxScroll)

        if (isExpanded) {
            renderExpanded(context, clampedX, clampedY, panelWidth, panelHeight, allyPokemonData, opponentPokemonData, playerSideName, opponentSideName)
        } else {
            renderCollapsed(context, clampedX, clampedY, panelWidth, panelHeight, allyPokemonData, opponentPokemonData, playerSideName, opponentSideName)
        }

        if (!isMinimised) {
            drawResizeHandles(context, clampedX, clampedY, panelWidth, panelHeight)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Frame and header rendering
    // ═══════════════════════════════════════════════════════════════════════════

    private fun renderFrame(context: DrawContext, x: Int, y: Int, width: Int, height: Int) {
        val opacity = if (isMinimised) UIUtils.MINIMISED_OPACITY else 1f
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(FRAME_TINT_R, FRAME_TINT_G, FRAME_TINT_B, opacity)
        UIUtils.renderPopupFrame(context, x, y, width, height)
        context.draw()
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
    }

    private fun renderHeader(context: DrawContext, x: Int, y: Int, width: Int) {
        val cellX = x + UIUtils.FRAME_INSET
        val cellW = width - UIUtils.FRAME_INSET * 2
        val cellY = y + UIUtils.FRAME_INSET

        UIUtils.drawPopupCell(context, cellX, cellY, cellW, headerCellH, colorTransform())
        headerEndY = cellY + headerCellH

        // Gold accent line at the bottom of the header cell
        val accentY = cellY + headerCellH - HEADER_ACCENT_H - 1  // -1 to sit inside the cell border
        context.fill(cellX + 1, accentY, cellX + cellW - 1, accentY + HEADER_ACCENT_H, applyOpacity(HEADER_ACCENT))

        // Text positioned with top padding (visual centering between top pad and accent line)
        val textH = (8 * 0.85f * textScale).toInt()
        val availableH = headerCellH - HEADER_ACCENT_H - 1  // 1px cell border
        val textY = cellY + (availableH - textH) / 2
        val textX = cellX + UIUtils.CELL_PAD

        val arrow = if (isExpanded) "▼" else "▶"
        drawText(context, arrow, textX.toFloat(), textY.toFloat(), TEXT_GOLD, 0.85f * textScale)
        drawText(context, "BATTLE INFO", (textX + (12 * textScale).toInt()).toFloat(), textY.toFloat(), TEXT_WHITE, 0.85f * textScale)

        val turnText = "T${BattleStateTracker.currentTurn}"
        val charWidth = (5 * textScale).toInt()
        drawText(context, turnText, (cellX + cellW - UIUtils.CELL_PAD - turnText.length * charWidth).toFloat(),
            textY.toFloat(), TEXT_GOLD, 0.85f * textScale)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Expanded mode
    // ═══════════════════════════════════════════════════════════════════════════

    private fun renderExpanded(
        context: DrawContext,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        allyPokemonData: List<PokemonBattleData>,
        opponentPokemonData: List<PokemonBattleData>,
        playerSideName: SideName,
        opponentSideName: SideName
    ) {
        renderFrame(context, x, y, width, height)
        renderHeader(context, x, y, width)

        val cellX = x + UIUtils.FRAME_INSET
        val cellW = width - UIUtils.FRAME_INSET * 2
        val contentCellY = headerEndY + UIUtils.CELL_GAP
        val contentCellH = height - UIUtils.FRAME_INSET * 2 - headerCellH - UIUtils.CELL_GAP

        // Reserve scrollbar space
        val needsScrollbar = contentHeight > visibleContentHeight
        val scrollbarSpace = if (needsScrollbar) SCROLLBAR_WIDTH + 2 else 0
        val effectiveW = cellW - scrollbarSpace

        // Single cell fills ALL remaining space
        UIUtils.drawPopupCell(context, cellX, contentCellY, effectiveW, contentCellH, colorTransform())

        val contentX = cellX + UIUtils.CELL_PAD
        val contentW = effectiveW - UIUtils.CELL_PAD * 2

        enableScissor(cellX, contentCellY, effectiveW, contentCellH)

        val startY = contentCellY + UIUtils.CELL_VPAD_TOP - PanelConfig.scrollOffset

        renderExpandedContent(context, contentX, startY, contentW, allyPokemonData, opponentPokemonData, playerSideName, opponentSideName)

        disableScissor()

        if (needsScrollbar) {
            val scrollbarX = x + width - UIUtils.FRAME_INSET - SCROLLBAR_WIDTH
            renderScrollbar(context, scrollbarX, contentCellY, contentCellH)
        }
    }

    private fun renderExpandedContent(
        context: DrawContext,
        x: Int,
        startY: Int,
        width: Int,
        allyPokemonData: List<PokemonBattleData>,
        opponentPokemonData: List<PokemonBattleData>,
        playerSideName: SideName,
        opponentSideName: SideName
    ) = PanelContentRenderer.renderExpandedContent(
        context, x, startY, width,
        allyPokemonData, opponentPokemonData, playerSideName, opponentSideName,
        textScale, lineHeight, GROUP_DIVIDER_EXTRA, SECTION_LABEL_SCALE, subLabelHeight(),
        TEXT_DIM, TEXT_LIGHT, ACCENT_PLAYER, ACCENT_OPPONENT, ACCENT_FIELD, STAT_BOOST, STAT_DROP,
        colorTransform(), scissorBounds,
        ::drawTextClipped, ::drawGroupDivider
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // Collapsed mode
    // ═══════════════════════════════════════════════════════════════════════════

    private fun renderCollapsed(
        context: DrawContext,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        allyPokemonData: List<PokemonBattleData>,
        opponentPokemonData: List<PokemonBattleData>,
        playerSideName: SideName,
        opponentSideName: SideName
    ) {
        renderFrame(context, x, y, width, height)
        renderHeader(context, x, y, width)

        val cellX = x + UIUtils.FRAME_INSET
        val cellW = width - UIUtils.FRAME_INSET * 2
        val contentCellY = headerEndY + UIUtils.CELL_GAP
        val contentCellH = height - UIUtils.FRAME_INSET * 2 - headerCellH - UIUtils.CELL_GAP

        val needsScrollbar = contentHeight > visibleContentHeight
        val scrollbarSpace = if (needsScrollbar) SCROLLBAR_WIDTH + 2 else 0
        val effectiveW = cellW - scrollbarSpace

        // Draw cell at FIXED position filling remaining space
        UIUtils.drawPopupCell(context, cellX, contentCellY, effectiveW, contentCellH, colorTransform())

        enableScissor(cellX, contentCellY, effectiveW, contentCellH)

        // Content scrolls WITHIN the cell
        val startY = contentCellY + UIUtils.CELL_VPAD_TOP - PanelConfig.scrollOffset
        val textX = cellX + UIUtils.CELL_PAD
        val textW = effectiveW - UIUtils.CELL_PAD * 2
        renderCollapsedContent(context, textX, startY, textW, allyPokemonData, opponentPokemonData, playerSideName, opponentSideName)

        disableScissor()

        if (needsScrollbar) {
            val scrollbarX = x + width - UIUtils.FRAME_INSET - SCROLLBAR_WIDTH
            renderScrollbar(context, scrollbarX, contentCellY, contentCellH)
        }
    }

    private fun renderCollapsedContent(
        context: DrawContext,
        x: Int,
        startY: Int,
        width: Int,
        allyPokemonData: List<PokemonBattleData>,
        opponentPokemonData: List<PokemonBattleData>,
        playerSideName: SideName,
        opponentSideName: SideName
    ) = PanelContentRenderer.renderCollapsedContent(
        context, x, startY, width,
        allyPokemonData, opponentPokemonData, playerSideName, opponentSideName,
        textScale, lineHeight, TEXT_DIM, TEXT_LIGHT, ACCENT_PLAYER, ACCENT_OPPONENT, ACCENT_FIELD,
        colorTransform(), ::drawTextClipped, ::drawConditionLine
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // Content detection (delegated to PanelLayoutCalculator)
    // ═══════════════════════════════════════════════════════════════════════════

    // Content detection delegated to PanelLayoutCalculator
    private fun hasFieldEffects(): Boolean = PanelLayoutCalculator.hasFieldEffects()
    private fun hasAnySideConditions(isPlayer: Boolean): Boolean = PanelLayoutCalculator.hasAnySideConditions(isPlayer)

    private fun calculateExpandedContentHeight(
        allyPokemonData: List<PokemonBattleData>,
        opponentPokemonData: List<PokemonBattleData>,
        panelWidth: Int
    ): Int = PanelLayoutCalculator.calculateExpandedContentHeight(
        allyPokemonData, opponentPokemonData, panelWidth,
        textScale, lineHeight, SCROLLBAR_WIDTH,
        groupDividerHeight(), subLabelHeight()
    )

    private fun calculateCollapsedContentHeight(
        allyPokemonData: List<PokemonBattleData>,
        opponentPokemonData: List<PokemonBattleData>,
        panelWidth: Int
    ): Int = PanelLayoutCalculator.calculateCollapsedContentHeight(
        allyPokemonData, opponentPokemonData, panelWidth, lineHeight
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // Common rendering helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private fun drawGroupDivider(context: DrawContext, x: Int, y: Int, width: Int): Int {
        val divY = y + GROUP_DIVIDER_EXTRA / 2
        UIUtils.drawPopupRowDivider(context, x, width, divY, colorTransform())
        return divY + UIUtils.DIVIDER_H + GROUP_DIVIDER_EXTRA / 2
    }

    private fun drawResizeHandles(context: DrawContext, x: Int, y: Int, w: Int, h: Int) {
        val handleColor = applyOpacity(if (interaction.hoveredZone != UIUtils.ResizeZone.NONE || interaction.isResizing) RESIZE_HANDLE_HOVER else RESIZE_HANDLE_COLOR)
        val cornerLength = 12
        val thickness = 2

        UIUtils.drawCornerHandle(context, x + w, y + h, cornerLength, thickness, handleColor, bottomRight = true)

        if (interaction.hoveredZone != UIUtils.ResizeZone.NONE || interaction.isResizing) {
            UIUtils.drawCornerHandle(context, x, y, cornerLength, thickness, handleColor, topLeft = true)
            UIUtils.drawCornerHandle(context, x + w, y, cornerLength, thickness, handleColor, topRight = true)
            UIUtils.drawCornerHandle(context, x, y + h, cornerLength, thickness, handleColor, bottomLeft = true)

            val edgeLength = 16
            val midX = x + w / 2
            val midY = y + h / 2

            context.fill(midX - edgeLength / 2, y, midX + edgeLength / 2, y + thickness, handleColor)
            context.fill(midX - edgeLength / 2, y + h - thickness, midX + edgeLength / 2, y + h, handleColor)
            context.fill(x, midY - edgeLength / 2, x + thickness, midY + edgeLength / 2, handleColor)
            context.fill(x + w - thickness, midY - edgeLength / 2, x + w, midY + edgeLength / 2, handleColor)
        }
    }

    private fun renderScrollbar(context: DrawContext, x: Int, y: Int, height: Int) {
        scrollbarRenderer.render(
            context, x, y, height,
            contentHeight, visibleContentHeight,
            PanelConfig.scrollOffset,
            isHovered = isOverScrollbar || interaction.isDraggingScrollbar
        )
    }

    private fun drawConditionLine(
        context: DrawContext,
        x: Int,
        y: Int,
        width: Int,
        icon: String,
        name: String,
        info: String,
        accentColor: Int
    ) {
        val charWidth = (5 * textScale).toInt()
        drawTextClipped(context, icon, x.toFloat(), y.toFloat(), accentColor, 0.8f * textScale)
        drawTextClipped(context, name, (x + (14 * textScale).toInt()).toFloat(), y.toFloat(), TEXT_LIGHT, 0.8f * textScale)
        if (info.isNotEmpty()) {
            val infoWidth = info.length * charWidth
            drawTextClipped(context, info, (x + width - infoWidth).toFloat(), y.toFloat(), TEXT_DIM, 0.75f * textScale)
        }
    }

    private fun enableScissor(x: Int, y: Int, width: Int, height: Int) {
        scissorBounds = UIUtils.enableScissor(x, y, width, height)
    }

    private fun disableScissor() {
        UIUtils.disableScissor()
    }

    private fun drawText(context: DrawContext, text: String, x: Float, y: Float, color: Int, scale: Float) {
        UIUtils.drawText(context, text, x, y, applyOpacity(color), scale)
    }

    private fun drawTextClipped(context: DrawContext, text: String, x: Float, y: Float, color: Int, scale: Float) {
        UIUtils.drawTextClipped(context, text, x, y, applyOpacity(color), scale, scissorBounds)
    }

    @JvmInline
    value class SideName(val name: String)

    private fun getPlayerSideName(side: ClientBattleSide, isSpectating: Boolean, isPlayerSide: Boolean): SideName {
        val actors = side.actors
        if (actors.isEmpty()) {
            return if (isPlayerSide) SideName(UI.ally) else SideName(UI.enemy)
        }

        val firstActorName = actors.firstOrNull()?.displayName?.string
            ?: return if (isPlayerSide) SideName(UI.ally) else SideName(UI.enemy)

        val truncatedName = if (firstActorName.length > 12) {
            firstActorName.take(11) + "…"
        } else {
            firstActorName
        }

        return SideName(truncatedName.uppercase())
    }
}
