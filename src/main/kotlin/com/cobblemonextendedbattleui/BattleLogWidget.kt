package com.cobblemonextendedbattleui

import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemonextendedbattleui.ui.shared.NineSliceRenderer
import com.cobblemonextendedbattleui.ui.shared.ScrollbarRenderer
import com.cobblemonextendedbattleui.ui.shared.WidgetInteractionHandler
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.util.InputUtil
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import org.lwjgl.glfw.GLFW

/**
 * Battle log widget styled to match Cobblemon's visual aesthetic.
 * Uses Cobblemon's actual textures with 9-slice rendering for resize support.
 *
 * Features:
 * - Moveable (drag from header)
 * - Resizable (drag from edges/corners) with 9-slice texture rendering
 * - Font size adjustable ([ and ] keys, or Ctrl+Scroll when hovering)
 * - Expandable/collapsible
 * - Turn separators
 * - Scrollable content
 */
object BattleLogWidget {

    // ═══════════════════════════════════════════════════════════════════════════
    // Cobblemon textures & 9-slice config
    // ═══════════════════════════════════════════════════════════════════════════

    private val FRAME_TEXTURE: Identifier = cobblemonResource("textures/gui/battle/battle_log.png")
    private val FRAME_EXPANDED_TEXTURE: Identifier = cobblemonResource("textures/gui/battle/battle_log_expanded.png")

    private const val TEXTURE_WIDTH = 169
    private const val TEXTURE_HEIGHT_COLLAPSED = 55
    private const val TEXTURE_HEIGHT_EXPANDED = 101

    private val SLICE_INSETS = NineSliceRenderer.SliceInsets(6)

    // ═══════════════════════════════════════════════════════════════════════════
    // Layout constants
    // ═══════════════════════════════════════════════════════════════════════════

    private const val HEADER_HEIGHT = 6  // Just enough for texture border, no actual header
    private const val COLLAPSED_HEIGHT = 70  // Small preset size
    private const val PADDING = 6
    private const val LOG_TEXT_INDENT = 4    // Extra indent for log entries
    private const val BASE_LINE_HEIGHT = 11
    private const val RESIZE_HANDLE_SIZE = 5
    private const val SCROLLBAR_WIDTH = 3

    // ═══════════════════════════════════════════════════════════════════════════
    // Colors - matching Cobblemon's battle log style
    // ═══════════════════════════════════════════════════════════════════════════

    private fun color(r: Int, g: Int, b: Int, a: Int = 255): Int = UIUtils.color(r, g, b, a)

    private fun applyOpacity(color: Int): Int = UIUtils.applyMinimisedOpacity(color, isMinimised)

    private fun getCurrentOpacity(): Float = if (isMinimised) UIUtils.MINIMISED_OPACITY else 1f

    // Text colors
    private val TEXT_DIM = color(150, 160, 175)

    // Entry type colors
    private val COLOR_MOVE = color(255, 255, 255)
    private val COLOR_HP = color(120, 195, 255)
    private val COLOR_HEALING = color(100, 220, 100)  // Green for healing
    private val COLOR_EFFECT = color(255, 215, 90)
    private val COLOR_FIELD = color(160, 255, 160)
    private val COLOR_OTHER = color(175, 175, 185)

    // Turn separator - subtle styling that blends with Cobblemon texture
    private val TURN_LINE_COLOR = color(90, 100, 120, 100)  // Very subtle, semi-transparent
    private val TURN_TEXT_COLOR = color(160, 150, 130)      // Muted gold/tan to match texture

    // Scrollbar
    private val SCROLLBAR_BG = color(30, 38, 50, 180)
    private val SCROLLBAR_THUMB = color(80, 100, 130, 220)

    // Resize handles
    private val RESIZE_HANDLE_COLOR = color(80, 100, 130, 180)
    private val RESIZE_HANDLE_HOVER = color(120, 150, 190, 255)

    // ═══════════════════════════════════════════════════════════════════════════
    // State
    // ═══════════════════════════════════════════════════════════════════════════

    private var isMinimised: Boolean = false
    private var scrollOffset: Int = 0
    private var contentHeight: Int = 0
    private var visibleHeight: Int = 0

    // Auto-scroll tracking: if user is at/near bottom, auto-follow new content
    private var isFollowingBottom: Boolean = true
    private const val BOTTOM_THRESHOLD = 5  // Pixels from bottom to consider "at bottom"

    // Input tracking
    private var wasMousePressed: Boolean = false
    private var wasIncreaseFontKeyPressed: Boolean = false
    private var wasDecreaseFontKeyPressed: Boolean = false

    // Shared interaction handler for drag/resize/scrollbar
    private val interaction = WidgetInteractionHandler(UIUtils.ActivePanel.BATTLE_LOG)

    // Shared scrollbar renderer
    private val scrollbar = ScrollbarRenderer(
        trackWidth = SCROLLBAR_WIDTH,
        bgColor = SCROLLBAR_BG,
        thumbColor = SCROLLBAR_THUMB,
        opacityProvider = { { c: Int -> applyOpacity(c) } }
    )

    // Bounds for click detection
    private var widgetX: Int = 0
    private var widgetY: Int = 0
    private var widgetW: Int = 0
    private var widgetH: Int = 0
    private var headerEndY: Int = 0

    // Scissor bounds
    private var scissorMinY: Int = 0
    private var scissorMaxY: Int = 0

    // Computed line height based on font scale
    private var lineHeight: Int = BASE_LINE_HEIGHT

    // ═══════════════════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if mouse is currently over the battle log widget.
     */
    fun isMouseOverWidget(): Boolean {
        if (!PanelConfig.enableBattleLog) return false
        val mc = MinecraftClient.getInstance()
        val mouseX = (mc.mouse.x * mc.window.scaledWidth / mc.window.width).toInt()
        val mouseY = (mc.mouse.y * mc.window.scaledHeight / mc.window.height).toInt()
        return mouseX >= widgetX && mouseX <= widgetX + widgetW &&
               mouseY >= widgetY && mouseY <= widgetY + widgetH
    }

    fun render(context: DrawContext) {
        if (!PanelConfig.enableBattleLog) return
        val battle = CobblemonClient.battle ?: return

        // Track minimized state - render greyed out instead of hiding
        isMinimised = battle.minimised

        val mc = MinecraftClient.getInstance()

        // Update line height based on font scale
        lineHeight = (BASE_LINE_HEIGHT * PanelConfig.logFontScale).toInt().coerceAtLeast(8)

        // Skip input handling when minimized (read-only)
        if (!isMinimised) {
            handleInput(mc)
        }

        val screenWidth = mc.window.scaledWidth
        val screenHeight = mc.window.scaledHeight

        val isExpanded = PanelConfig.logExpanded

        // Get dimensions from config or use defaults
        val width = PanelConfig.logWidth ?: PanelConfig.DEFAULT_LOG_WIDTH
        val expandedHeight = PanelConfig.logHeight ?: PanelConfig.DEFAULT_LOG_HEIGHT
        val height = if (isExpanded) expandedHeight else COLLAPSED_HEIGHT

        // Default position: bottom right, above battle controls
        // We track position by BOTTOM edge so collapse/expand keeps bottom fixed
        val defaultX = screenWidth - width - 10  // 10px from right edge
        val defaultBottomY = screenHeight - 55  // Bottom edge position

        // Calculate Y from bottom edge (collapse downwards behavior)
        val bottomY = (PanelConfig.logY ?: (defaultBottomY - expandedHeight)) + expandedHeight
        val y = (bottomY - height).coerceIn(0, screenHeight - height)
        val x = (PanelConfig.logX ?: defaultX).coerceIn(0, screenWidth - width)

        widgetX = x
        widgetY = y
        widgetW = width
        widgetH = height

        // Apply opacity for minimized state (greys out like Cobblemon's BattleOverlay)
        // Must enable blending for texture alpha to work correctly
        val opacity = getCurrentOpacity()
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(1f, 1f, 1f, opacity)

        // Render frame using 9-slice with Cobblemon textures
        renderFrame9Slice(context, x, y, width, height, isExpanded)
        // Flush texture batch before any fill operations
        context.draw()
        headerEndY = y + HEADER_HEIGHT

        // Always render the same content - "collapsed" is just a smaller preset size
        renderContent(context, x, y, width, height)

        // Resize handles only when expanded and not minimized (minimized = read-only)
        if (!isMinimised && isExpanded && (interaction.hoveredZone != UIUtils.ResizeZone.NONE || interaction.isResizing)) {
            renderResizeHandles(context, x, y, width, height)
        }

        // Reset shader color to default
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
    }

    fun onScroll(mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        if (!PanelConfig.enableBattleLog) return false
        if (isMinimised) return false  // Read-only when minimized

        val mc = MinecraftClient.getInstance()
        val scaledX = (mouseX * mc.window.scaledWidth / mc.window.width).toInt()
        val scaledY = (mouseY * mc.window.scaledHeight / mc.window.height).toInt()

        val isOver = scaledX >= widgetX && scaledX <= widgetX + widgetW &&
                     scaledY >= widgetY && scaledY <= widgetY + widgetH

        if (!isOver) return false

        // Ctrl+Scroll for font size
        val handle = mc.window.handle
        val ctrlDown = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                       GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS

        if (ctrlDown) {
            val delta = if (deltaY > 0) PanelConfig.FONT_SCALE_STEP else -PanelConfig.FONT_SCALE_STEP
            PanelConfig.adjustLogFontScale(delta)
            BattleLog.invalidateWrappedTextCache()  // Font scale changed, invalidate cache
            PanelConfig.save()
            return true
        }

        // Regular scroll
        if (contentHeight > visibleHeight) {
            val scrollAmount = (lineHeight * 2 * if (deltaY > 0) -1 else 1)
            val maxScroll = (contentHeight - visibleHeight).coerceAtLeast(0)
            scrollOffset = (scrollOffset + scrollAmount).coerceIn(0, maxScroll)

            // Update follow state: if user scrolled to bottom, start following again
            // If user scrolled up away from bottom, stop following
            isFollowingBottom = scrollOffset >= maxScroll - BOTTOM_THRESHOLD
            return true
        }
        return false
    }

    fun clear() {
        scrollOffset = 0
        isFollowingBottom = true
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 9-Slice Texture Rendering
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Renders the frame using 9-slice technique with Cobblemon's textures.
     * Delegates to the shared NineSliceRenderer.
     */
    private fun renderFrame9Slice(context: DrawContext, x: Int, y: Int, width: Int, height: Int, isExpanded: Boolean) {
        val texture = if (isExpanded) FRAME_EXPANDED_TEXTURE else FRAME_TEXTURE
        val texH = if (isExpanded) TEXTURE_HEIGHT_EXPANDED else TEXTURE_HEIGHT_COLLAPSED

        NineSliceRenderer.render(
            context, texture,
            x, y, width, height,
            TEXTURE_WIDTH, texH,
            SLICE_INSETS
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Input handling
    // ═══════════════════════════════════════════════════════════════════════════

    private fun handleInput(mc: MinecraftClient) {
        val handle = mc.window.handle
        val mouseX = (mc.mouse.x * mc.window.scaledWidth / mc.window.width).toInt()
        val mouseY = (mc.mouse.y * mc.window.scaledHeight / mc.window.height).toInt()
        val isMouseDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS

        val screenWidth = mc.window.scaledWidth
        val screenHeight = mc.window.scaledHeight

        // Check if mouse is over this widget for keybind handling
        val isOverWidget = mouseX >= widgetX && mouseX <= widgetX + widgetW &&
                           mouseY >= widgetY && mouseY <= widgetY + widgetH

        // Font size keybinds (only when hovering over widget)
        if (isOverWidget) {
            handleFontKeybinds(handle)
        }

        // Only update hover state when this panel can interact
        val canInteract = UIUtils.canInteract(UIUtils.ActivePanel.BATTLE_LOG)
        if (!interaction.isInteracting && canInteract) {
            interaction.hoveredZone = getResizeZone(mouseX, mouseY)
        } else if (!canInteract) {
            interaction.hoveredZone = UIUtils.ResizeZone.NONE
        }

        val isOverHeader = isOverWidget && mouseY <= headerEndY

        // Expand/collapse toggle zone (bottom-right corner, matching Cobblemon's arrow location)
        val toggleZoneX = widgetX + widgetW - 16
        val toggleZoneY = widgetY + widgetH - 14
        val isOverToggle = mouseX >= toggleZoneX && mouseX <= toggleZoneX + 14 &&
                           mouseY >= toggleZoneY && mouseY <= toggleZoneY + 12

        // Check if mouse is over scrollbar (use wider hit area for easier clicking)
        val isOverScrollbar = contentHeight > visibleHeight && scrollbar.isOverTrack(mouseX, mouseY)

        if (isMouseDown) {
            when {
                // Continue scrollbar drag (check first to maintain drag even if mouse moves off)
                interaction.isDraggingScrollbar -> {
                    val deltaY = mouseY - interaction.scrollbarDragStartY
                    scrollOffset = scrollbar.dragToScrollOffset(deltaY, interaction.scrollbarDragStartOffset, contentHeight, visibleHeight)
                    val maxScroll = (contentHeight - visibleHeight).coerceAtLeast(0)
                    isFollowingBottom = scrollOffset >= maxScroll - BOTTOM_THRESHOLD
                }
                // Toggle expand/collapse
                !wasMousePressed && isOverToggle -> {
                    PanelConfig.setLogExpanded(!PanelConfig.logExpanded)
                    scrollOffset = 0
                    isFollowingBottom = true
                    PanelConfig.save()
                }
                // Start scrollbar drag
                !wasMousePressed && canInteract && isOverScrollbar -> {
                    interaction.startScrollbarDrag(mouseY, scrollOffset)
                    // If clicked on track (not thumb), jump to that position
                    if (!scrollbar.isOverThumb(mouseX, mouseY)) {
                        scrollOffset = scrollbar.trackClickToScrollOffset(mouseY, contentHeight, visibleHeight)
                        interaction.startScrollbarDrag(mouseY, scrollOffset)
                    }
                    isFollowingBottom = false
                }
                // Start resize
                !wasMousePressed && canInteract && interaction.hoveredZone != UIUtils.ResizeZone.NONE -> {
                    interaction.startResize(mouseX, mouseY, interaction.hoveredZone, widgetX, widgetY, widgetW, widgetH)
                }
                // Continue resizing
                interaction.isResizing -> {
                    handleResize(mouseX, mouseY, screenWidth, screenHeight)
                }
                // Start dragging (from header, excluding toggle)
                !wasMousePressed && canInteract && isOverHeader && !isOverToggle -> {
                    interaction.startDrag(mouseX, mouseY, widgetX, widgetY)
                }
                // Continue dragging
                interaction.isDragging -> {
                    val pos = interaction.updateDrag(mouseX, mouseY, screenWidth, screenHeight, widgetW, widgetH)
                    if (pos != null) {
                        PanelConfig.setLogPosition(pos.first, pos.second)
                    }
                }
            }
        } else {
            // Release interaction when mouse is released
            if (interaction.isDragging || interaction.isResizing) {
                PanelConfig.save()
            }
            interaction.releaseAll()
        }

        wasMousePressed = isMouseDown
    }

    private fun handleFontKeybinds(handle: Long) {
        val increaseKey = InputUtil.fromTranslationKey(CobblemonExtendedBattleUIClient.increaseFontKey.boundKeyTranslationKey)
        val isIncreaseDown = UIUtils.isKeyOrButtonPressed(handle, increaseKey)
        if (isIncreaseDown && !wasIncreaseFontKeyPressed) {
            PanelConfig.adjustLogFontScale(PanelConfig.FONT_SCALE_STEP)
            BattleLog.invalidateWrappedTextCache()  // Font scale changed, invalidate cache
            PanelConfig.save()
        }
        wasIncreaseFontKeyPressed = isIncreaseDown

        val decreaseKey = InputUtil.fromTranslationKey(CobblemonExtendedBattleUIClient.decreaseFontKey.boundKeyTranslationKey)
        val isDecreaseDown = UIUtils.isKeyOrButtonPressed(handle, decreaseKey)
        if (isDecreaseDown && !wasDecreaseFontKeyPressed) {
            PanelConfig.adjustLogFontScale(-PanelConfig.FONT_SCALE_STEP)
            BattleLog.invalidateWrappedTextCache()  // Font scale changed, invalidate cache
            PanelConfig.save()
        }
        wasDecreaseFontKeyPressed = isDecreaseDown
    }

    private fun getResizeZone(mouseX: Int, mouseY: Int): UIUtils.ResizeZone {
        // Only allow resize in expanded mode
        if (!PanelConfig.logExpanded) return UIUtils.ResizeZone.NONE

        val x = widgetX
        val y = widgetY
        val w = widgetW
        val h = widgetH

        // Exclude scrollbar area from resize detection (right side, inside content area)
        // This prevents the scrollbar from being impossible to click
        val scrollbarX = x + w - SCROLLBAR_WIDTH - 6
        val contentTop = y + HEADER_HEIGHT
        val contentBottom = y + h - 14  // Bottom arrow area
        val isOverScrollbarArea = mouseX >= scrollbarX && mouseX <= x + w - 3 &&
                                   mouseY >= contentTop && mouseY <= contentBottom
        if (isOverScrollbarArea) return UIUtils.ResizeZone.NONE

        // Corner detection zones (L-shaped areas around corners)
        val cornerSize = 12  // Size of the corner detection zone

        val nearLeft = mouseX >= x - RESIZE_HANDLE_SIZE && mouseX <= x + cornerSize
        val nearRight = mouseX >= x + w - cornerSize && mouseX <= x + w + RESIZE_HANDLE_SIZE
        val nearTop = mouseY >= y - RESIZE_HANDLE_SIZE && mouseY <= y + 4  // Reduced top zone - only outside/just inside
        val nearBottom = mouseY >= y + h - cornerSize && mouseY <= y + h + RESIZE_HANDLE_SIZE

        // Edge detection (middle portions of edges, outside the widget)
        val edgeZone = 5  // How close to edge to trigger - reduced for less sensitivity
        val onLeftEdge = mouseX >= x - edgeZone && mouseX <= x + 2
        val onRightEdge = mouseX >= x + w - 2 && mouseX <= x + w + edgeZone
        val onTopEdge = mouseY >= y - edgeZone && mouseY <= y + 2  // Reduced - mostly outside
        val onBottomEdge = mouseY >= y + h - 2 && mouseY <= y + h + edgeZone

        val withinX = mouseX >= x - RESIZE_HANDLE_SIZE && mouseX <= x + w + RESIZE_HANDLE_SIZE
        val withinY = mouseY >= y - RESIZE_HANDLE_SIZE && mouseY <= y + h + RESIZE_HANDLE_SIZE

        return when {
            // Corners (prioritize these - L-shaped detection)
            nearTop && nearLeft -> UIUtils.ResizeZone.TOP_LEFT
            nearTop && nearRight -> UIUtils.ResizeZone.TOP_RIGHT
            nearBottom && nearLeft -> UIUtils.ResizeZone.BOTTOM_LEFT
            nearBottom && nearRight -> UIUtils.ResizeZone.BOTTOM_RIGHT

            // Edges (only the outer edge portions)
            onLeftEdge && withinY && !nearTop && !nearBottom -> UIUtils.ResizeZone.LEFT
            onRightEdge && withinY && !nearTop && !nearBottom -> UIUtils.ResizeZone.RIGHT
            onTopEdge && withinX && !nearLeft && !nearRight -> UIUtils.ResizeZone.TOP
            onBottomEdge && withinX && !nearLeft && !nearRight -> UIUtils.ResizeZone.BOTTOM

            else -> UIUtils.ResizeZone.NONE
        }
    }

    private fun handleResize(mouseX: Int, mouseY: Int, screenWidth: Int, screenHeight: Int) {
        val result = interaction.calculateResize(
            mouseX, mouseY,
            minWidth = PanelConfig.MIN_LOG_WIDTH,
            maxWidth = PanelConfig.MAX_LOG_WIDTH,
            minHeight = PanelConfig.MIN_LOG_HEIGHT,
            maxHeight = PanelConfig.MAX_LOG_HEIGHT,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )

        PanelConfig.setLogDimensions(result.newWidth, result.newHeight)
        PanelConfig.setLogPosition(result.newX, result.newY)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rendering
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Begins a batch of fill operations.
     * Must call endFillBatch() after all fills are done.
     * This flushes DrawContext's pending operations to avoid Tessellator conflicts.
     */
    private fun beginFillBatch(context: DrawContext) {
        // Flush any pending texture draws before we start fills
        // This is critical because DrawContext batches operations and shares the Tessellator
        context.draw()
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
    }

    /**
     * Ends a batch of fill operations and restores state for texture rendering.
     */
    private fun endFillBatch(context: DrawContext) {
        // Flush the fills we just drew
        context.draw()
        // Blend stays enabled (needed for textures with alpha)
    }

    private fun renderResizeHandles(context: DrawContext, x: Int, y: Int, width: Int, height: Int) {
        val handleColor = if (interaction.hoveredZone != UIUtils.ResizeZone.NONE || interaction.isResizing) RESIZE_HANDLE_HOVER else RESIZE_HANDLE_COLOR
        val cornerLength = 12
        val thickness = 2

        beginFillBatch(context)

        // Bottom-right corner (always visible when hovering resize zone)
        UIUtils.drawCornerHandle(context, x + width, y + height, cornerLength, thickness, handleColor, bottomRight = true)

        if (interaction.hoveredZone != UIUtils.ResizeZone.NONE || interaction.isResizing) {
            // Other corners
            UIUtils.drawCornerHandle(context, x, y, cornerLength, thickness, handleColor, topLeft = true)
            UIUtils.drawCornerHandle(context, x + width, y, cornerLength, thickness, handleColor, topRight = true)
            UIUtils.drawCornerHandle(context, x, y + height, cornerLength, thickness, handleColor, bottomLeft = true)

            // Edge handles
            val edgeLength = 16
            val midX = x + width / 2
            val midY = y + height / 2

            // Top edge
            context.fill(midX - edgeLength / 2, y, midX + edgeLength / 2, y + thickness, handleColor)
            // Bottom edge
            context.fill(midX - edgeLength / 2, y + height - thickness, midX + edgeLength / 2, y + height, handleColor)
            // Left edge
            context.fill(x, midY - edgeLength / 2, x + thickness, midY + edgeLength / 2, handleColor)
            // Right edge
            context.fill(x + width - thickness, midY - edgeLength / 2, x + width, midY + edgeLength / 2, handleColor)
        }

        endFillBatch(context)
    }

    private fun renderContent(context: DrawContext, x: Int, y: Int, width: Int, height: Int) {
        // Content area (leave space at bottom for texture's built-in arrow area)
        val contentY = y + HEADER_HEIGHT
        val contentAreaHeight = height - HEADER_HEIGHT - 14  // Space at bottom for arrow region

        // Calculate text width (assume scrollbar present for height calc to avoid circular dependency)
        val fullContentX = x + PADDING  // Full width start (for separators)
        val textStartX = x + PADDING + LOG_TEXT_INDENT  // Indented start (for text)
        val maxFullContentWidth = width - PADDING * 2 - SCROLLBAR_WIDTH - 4  // Assume scrollbar for height calc
        val maxTextContentWidth = maxFullContentWidth - LOG_TEXT_INDENT

        // Calculate content height with text wrapping
        val mc = MinecraftClient.getInstance()
        val previousContentHeight = contentHeight
        contentHeight = calculateContentHeight(mc, maxTextContentWidth)
        visibleHeight = contentAreaHeight

        val maxScroll = (contentHeight - visibleHeight).coerceAtLeast(0)

        // Auto-scroll to bottom if following and content grew (new messages arrived)
        if (isFollowingBottom && contentHeight > previousContentHeight) {
            scrollOffset = maxScroll
        } else {
            scrollOffset = scrollOffset.coerceIn(0, maxScroll)
        }

        // Also update following state if we're now at/near bottom
        if (maxScroll <= 0 || scrollOffset >= maxScroll - BOTTOM_THRESHOLD) {
            isFollowingBottom = true
        }

        // Now calculate actual widths based on whether scrollbar is needed
        val scrollbarSpace = if (contentHeight > visibleHeight) SCROLLBAR_WIDTH + 4 else 0
        val fullContentWidth = width - PADDING * 2 - scrollbarSpace
        val textContentWidth = fullContentWidth - LOG_TEXT_INDENT

        // Render log entries with scissor (use full width for scissor to allow separators)
        enableScissor(context, fullContentX, contentY, fullContentWidth, contentAreaHeight)
        renderLogEntries(context, mc, fullContentX, textStartX, contentY, fullContentWidth, textContentWidth)
        disableScissor()

        // Scrollbar if needed
        if (contentHeight > visibleHeight) {
            renderScrollbar(context, x + width - SCROLLBAR_WIDTH - 3, contentY, contentAreaHeight)
        }
    }

    private fun calculateContentHeight(mc: MinecraftClient, textWidth: Int): Int {
        val entries = BattleLog.getEntries()
        if (entries.isEmpty()) return lineHeight

        val fontScale = 0.7f * PanelConfig.logFontScale
        var height = 0
        var lastTurn = -1

        for (entry in entries) {
            if (entry.turn != lastTurn) {
                if (lastTurn != -1) {
                    height += 3  // Gap before separator
                }
                height += lineHeight  // Turn header
                height += 2  // Gap after separator
                lastTurn = entry.turn
            }

            if (entry.type == BattleLog.EntryType.TURN) continue

            // Use cached wrapped lines for performance
            val lines = entry.getWrappedLines(textWidth, fontScale) { text, width, scale ->
                wrapText(mc, text, width, scale)
            }
            height += lineHeight * lines.size
        }

        return height.coerceAtLeast(lineHeight)
    }

    /**
     * Wraps text into multiple lines that fit within the given width.
     */
    private fun wrapText(mc: MinecraftClient, text: String, maxWidth: Int, scale: Float): List<String> {
        if (text.isEmpty()) return listOf("")

        val textRenderer = mc.textRenderer
        val scaledMaxWidth = (maxWidth / scale).toInt()

        val lines = mutableListOf<String>()
        val words = text.split(" ")
        var currentLine = StringBuilder()

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            val testWidth = textRenderer.getWidth(testLine)

            if (testWidth <= scaledMaxWidth) {
                currentLine = StringBuilder(testLine)
            } else {
                // Current line is full, start a new one
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                }
                // Check if single word is too long
                if (textRenderer.getWidth(word) > scaledMaxWidth) {
                    // Word itself is too long, need to break it
                    var remaining = word
                    while (remaining.isNotEmpty()) {
                        var fit = remaining
                        while (textRenderer.getWidth(fit) > scaledMaxWidth && fit.length > 1) {
                            fit = fit.dropLast(1)
                        }
                        lines.add(fit)
                        remaining = remaining.drop(fit.length)
                    }
                    currentLine = StringBuilder()
                } else {
                    currentLine = StringBuilder(word)
                }
            }
        }

        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }

        return if (lines.isEmpty()) listOf("") else lines
    }

    private fun renderLogEntries(context: DrawContext, mc: MinecraftClient, separatorX: Int, textX: Int, startY: Int, separatorWidth: Int, textWidth: Int) {
        val entries = BattleLog.getEntries()

        if (entries.isEmpty()) {
            val emptyY = startY + 4
            if (emptyY >= scissorMinY && emptyY <= scissorMaxY) {
                drawText(context, "No battle messages", textX.toFloat(), emptyY.toFloat(), TEXT_DIM, 0.7f * PanelConfig.logFontScale)
            }
            return
        }

        var currentY = startY - scrollOffset
        var lastTurn = -1
        val fontScale = 0.7f * PanelConfig.logFontScale

        for (entry in entries) {
            // Turn separator (uses full width, no indent)
            if (entry.turn != lastTurn) {
                if (lastTurn != -1) {
                    currentY += 3
                }

                if (currentY >= scissorMinY - lineHeight && currentY <= scissorMaxY + lineHeight) {
                    renderTurnSeparator(context, separatorX, currentY, separatorWidth, entry.turn)
                }
                currentY += lineHeight + 2
                lastTurn = entry.turn
            }

            if (entry.type == BattleLog.EntryType.TURN) continue

            // Render entry with cached text wrapping (uses indented position)
            val color = getEntryColor(entry.type)
            val lines = entry.getWrappedLines(textWidth, fontScale) { text, width, scale ->
                wrapText(mc, text, width, scale)
            }

            for (line in lines) {
                if (currentY >= scissorMinY - lineHeight && currentY <= scissorMaxY) {
                    drawText(context, line, textX.toFloat(), currentY.toFloat(), color, fontScale)
                }
                currentY += lineHeight
            }
        }
    }

    private fun renderTurnSeparator(context: DrawContext, x: Int, y: Int, width: Int, turn: Int) {
        val centerY = y + lineHeight / 2
        val fontScale = 0.6f * PanelConfig.logFontScale

        // No background - just subtle lines and text that blend with the texture
        val turnText = "Turn $turn"
        val mc = MinecraftClient.getInstance()
        val actualTextWidth = (mc.textRenderer.getWidth(turnText) * fontScale).toInt()
        val gap = 6  // Gap between line and text

        // Calculate centered position for text
        val textX = x + (width - actualTextWidth) / 2
        val lineEndLeft = textX - gap
        val lineStartRight = textX + actualTextWidth + gap

        // Subtle horizontal lines
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        val lineColor = applyOpacity(TURN_LINE_COLOR)
        context.fill(x, centerY, lineEndLeft.coerceAtLeast(x), centerY + 1, lineColor)
        context.fill(lineStartRight.coerceAtMost(x + width), centerY, x + width, centerY + 1, lineColor)

        // Centered turn text
        drawText(context, turnText, textX.toFloat(), (y + 1).toFloat(), TURN_TEXT_COLOR, fontScale)
    }

    private fun renderScrollbar(context: DrawContext, x: Int, y: Int, height: Int) {
        scrollbar.render(context, x, y, height, contentHeight, visibleHeight, scrollOffset)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private fun getEntryColor(type: BattleLog.EntryType): Int {
        return when (type) {
            BattleLog.EntryType.MOVE -> COLOR_MOVE
            BattleLog.EntryType.HP -> COLOR_HP
            BattleLog.EntryType.HEALING -> COLOR_HEALING
            BattleLog.EntryType.EFFECT -> COLOR_EFFECT
            BattleLog.EntryType.FIELD -> COLOR_FIELD
            BattleLog.EntryType.TURN -> TURN_TEXT_COLOR
            else -> COLOR_OTHER
        }
    }

    private fun drawText(context: DrawContext, text: String, x: Float, y: Float, color: Int, scale: Float) {
        drawScaledText(
            context = context,
            text = Text.literal(text),
            x = x,
            y = y,
            scale = scale,
            colour = applyOpacity(color),
            shadow = true
        )
    }

    private fun enableScissor(context: DrawContext, x: Int, y: Int, width: Int, height: Int) {
        scissorMinY = y
        scissorMaxY = y + height

        val mc = MinecraftClient.getInstance()
        val scale = mc.window.scaleFactor

        val scaledX = (x * scale).toInt()
        val scaledY = ((mc.window.scaledHeight - y - height) * scale).toInt()
        val scaledWidth = (width * scale).toInt()
        val scaledHeight = (height * scale).toInt()

        RenderSystem.enableScissor(scaledX, scaledY, scaledWidth, scaledHeight)
    }

    private fun disableScissor() {
        RenderSystem.disableScissor()
    }
}
