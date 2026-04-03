package com.cobblemonextendedbattleui

import com.cobblemon.mod.common.api.types.ElementalType
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemonextendedbattleui.ui.shared.NineSliceRenderer
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.util.InputUtil
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import org.lwjgl.glfw.GLFW

/**
 * Shared UI utilities to avoid code duplication between BattleInfoPanel and BattleLogWidget.
 */
object UIUtils {

    // ═══════════════════════════════════════════════════════════════════════════
    // Panel interaction tracking
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Identifies which panel is being interacted with.
     */
    enum class ActivePanel {
        NONE, INFO_PANEL, BATTLE_LOG
    }

    /**
     * Currently active panel for drag/resize operations.
     * Only one panel can be interacted with at a time.
     */
    var activePanel: ActivePanel = ActivePanel.NONE
        private set

    /**
     * Attempts to claim interaction for a panel.
     * Returns true if successful (no other panel is active).
     */
    fun claimInteraction(panel: ActivePanel): Boolean {
        if (activePanel == ActivePanel.NONE || activePanel == panel) {
            activePanel = panel
            return true
        }
        return false
    }

    /**
     * Releases interaction claim for a panel.
     */
    fun releaseInteraction(panel: ActivePanel) {
        if (activePanel == panel) {
            activePanel = ActivePanel.NONE
        }
    }

    /**
     * Checks if interaction is available for a panel.
     */
    fun canInteract(panel: ActivePanel): Boolean {
        return activePanel == ActivePanel.NONE || activePanel == panel
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Constants
    // ═══════════════════════════════════════════════════════════════════════════

    /** Opacity multiplier when the battle overlay is minimised. */
    const val MINIMISED_OPACITY = 0.5f

    /** Z-offset for popup rendering (above battle overlay). */
    const val POPUP_Z_OFFSET = 400.0

    // ═══════════════════════════════════════════════════════════════════════════
    // Color utilities
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates an ARGB color from component values.
     */
    fun color(r: Int, g: Int, b: Int, a: Int = 255): Int = (a shl 24) or (r shl 16) or (g shl 8) or b

    /**
     * Applies minimised-state opacity to a color's alpha channel.
     * When minimised, reduces alpha by [MINIMISED_OPACITY]. Otherwise returns the color unchanged.
     */
    fun applyMinimisedOpacity(color: Int, isMinimised: Boolean): Int {
        if (!isMinimised) return color
        val a = ((color shr 24) and 0xFF)
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val newA = (a * MINIMISED_OPACITY).toInt()
        return (newA shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Gets a display color for an ElementalType, brightened for visibility on dark backgrounds.
     */
    fun getTypeColor(type: ElementalType): Int {
        val hue = type.hue
        var r = (hue shr 16) and 0xFF
        var g = (hue shr 8) and 0xFF
        var b = hue and 0xFF
        // Brighten for visibility on dark background
        r = (r * 1.15f).toInt().coerceAtMost(255)
        g = (g * 1.15f).toInt().coerceAtMost(255)
        b = (b * 1.15f).toInt().coerceAtMost(255)
        return color(r, g, b, 255)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Resize zones (shared enum)
    // ═══════════════════════════════════════════════════════════════════════════

    enum class ResizeZone {
        NONE, LEFT, RIGHT, TOP, BOTTOM,
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Scissor management
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Tracks current scissor bounds for manual clipping checks.
     */
    data class ScissorBounds(val minY: Int = 0, val maxY: Int = 0)

    /**
     * Enables scissor test with the given bounds.
     * Returns ScissorBounds for use with drawTextClipped.
     */
    fun enableScissor(x: Int, y: Int, width: Int, height: Int): ScissorBounds {
        val mc = MinecraftClient.getInstance()
        val scale = mc.window.scaleFactor

        val scaledX = (x * scale).toInt()
        val scaledY = ((mc.window.scaledHeight - y - height) * scale).toInt()
        val scaledWidth = (width * scale).toInt()
        val scaledHeight = (height * scale).toInt()

        RenderSystem.enableScissor(scaledX, scaledY, scaledWidth, scaledHeight)
        return ScissorBounds(y, y + height)
    }

    /**
     * Disables scissor test.
     */
    fun disableScissor() {
        RenderSystem.disableScissor()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Text rendering
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Draws scaled text with shadow using Cobblemon's renderer.
     */
    fun drawText(context: DrawContext, text: String, x: Float, y: Float, color: Int, scale: Float) {
        drawScaledText(
            context = context,
            text = Text.literal(text),
            x = x,
            y = y,
            scale = scale,
            colour = color,
            shadow = true
        )
    }

    /**
     * Draws scaled text only if within scissor bounds (Y axis).
     */
    fun drawTextClipped(
        context: DrawContext,
        text: String,
        x: Float,
        y: Float,
        color: Int,
        scale: Float,
        bounds: ScissorBounds
    ) {
        val textHeight = (8 * scale).toInt()
        if (y + textHeight < bounds.minY || y > bounds.maxY) return
        drawText(context, text, x, y, color, scale)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Input utilities
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Checks if a key or mouse button is currently pressed.
     * Works with both keyboard keys and mouse buttons.
     */
    fun isKeyOrButtonPressed(handle: Long, key: InputUtil.Key): Boolean {
        return when (key.category) {
            InputUtil.Type.MOUSE -> GLFW.glfwGetMouseButton(handle, key.code) == GLFW.GLFW_PRESS
            else -> GLFW.glfwGetKey(handle, key.code) == GLFW.GLFW_PRESS
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Resize zone detection
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Determines which resize zone the mouse is hovering over.
     * @param mouseX Current mouse X position
     * @param mouseY Current mouse Y position
     * @param x Widget X position
     * @param y Widget Y position
     * @param w Widget width
     * @param h Widget height
     * @param handleSize Size of the resize handle detection area
     * @param enabled Whether resizing is currently allowed
     */
    fun getResizeZone(
        mouseX: Int,
        mouseY: Int,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        handleSize: Int,
        enabled: Boolean = true
    ): ResizeZone {
        if (!enabled) return ResizeZone.NONE

        val onLeft = mouseX >= x - handleSize && mouseX <= x + handleSize
        val onRight = mouseX >= x + w - handleSize && mouseX <= x + w + handleSize
        val onTop = mouseY >= y - handleSize && mouseY <= y + handleSize
        val onBottom = mouseY >= y + h - handleSize && mouseY <= y + h + handleSize
        val withinX = mouseX >= x - handleSize && mouseX <= x + w + handleSize
        val withinY = mouseY >= y - handleSize && mouseY <= y + h + handleSize

        return when {
            onTop && onLeft && withinX && withinY -> ResizeZone.TOP_LEFT
            onTop && onRight && withinX && withinY -> ResizeZone.TOP_RIGHT
            onBottom && onLeft && withinX && withinY -> ResizeZone.BOTTOM_LEFT
            onBottom && onRight && withinX && withinY -> ResizeZone.BOTTOM_RIGHT
            onLeft && withinY -> ResizeZone.LEFT
            onRight && withinY -> ResizeZone.RIGHT
            onTop && withinX -> ResizeZone.TOP
            onBottom && withinX -> ResizeZone.BOTTOM
            else -> ResizeZone.NONE
        }
    }

    /**
     * Calculates new dimensions and position based on resize zone and mouse delta.
     */
    data class ResizeResult(
        val newX: Int,
        val newY: Int,
        val newWidth: Int,
        val newHeight: Int
    )

    fun calculateResize(
        zone: ResizeZone,
        deltaX: Int,
        deltaY: Int,
        startX: Int,
        startY: Int,
        startWidth: Int,
        startHeight: Int,
        minWidth: Int,
        maxWidth: Int,
        minHeight: Int,
        maxHeight: Int,
        screenWidth: Int,
        screenHeight: Int
    ): ResizeResult {
        var newWidth = startWidth
        var newHeight = startHeight
        var newX = startX
        var newY = startY

        // Calculate the fixed edges (the edges that should NOT move during resize)
        val fixedRight = startX + startWidth  // For left-side resizing
        val fixedBottom = startY + startHeight  // For top-side resizing
        val fixedLeft = startX  // For right-side resizing
        val fixedTop = startY  // For bottom-side resizing

        when (zone) {
            ResizeZone.RIGHT -> {
                // Right edge moves, left edge stays fixed
                newWidth = (startWidth + deltaX).coerceIn(minWidth, maxWidth)
                // Don't let it go past screen edge
                val maxAllowedWidth = screenWidth - fixedLeft
                newWidth = newWidth.coerceAtMost(maxAllowedWidth)
            }
            ResizeZone.BOTTOM -> {
                // Bottom edge moves, top edge stays fixed
                newHeight = (startHeight + deltaY).coerceIn(minHeight, maxHeight)
                val maxAllowedHeight = screenHeight - fixedTop
                newHeight = newHeight.coerceAtMost(maxAllowedHeight)
            }
            ResizeZone.BOTTOM_RIGHT -> {
                newWidth = (startWidth + deltaX).coerceIn(minWidth, maxWidth)
                newHeight = (startHeight + deltaY).coerceIn(minHeight, maxHeight)
                val maxAllowedWidth = screenWidth - fixedLeft
                val maxAllowedHeight = screenHeight - fixedTop
                newWidth = newWidth.coerceAtMost(maxAllowedWidth)
                newHeight = newHeight.coerceAtMost(maxAllowedHeight)
            }
            ResizeZone.LEFT -> {
                // Left edge moves, right edge stays fixed
                newWidth = (startWidth - deltaX).coerceIn(minWidth, maxWidth)
                newX = fixedRight - newWidth
                // Don't let left edge go past screen left
                if (newX < 0) {
                    newX = 0
                    newWidth = fixedRight  // Can only be as wide as distance to right edge
                    newWidth = newWidth.coerceIn(minWidth, maxWidth)
                }
            }
            ResizeZone.TOP -> {
                // Top edge moves, bottom edge stays fixed
                newHeight = (startHeight - deltaY).coerceIn(minHeight, maxHeight)
                newY = fixedBottom - newHeight
                if (newY < 0) {
                    newY = 0
                    newHeight = fixedBottom
                    newHeight = newHeight.coerceIn(minHeight, maxHeight)
                }
            }
            ResizeZone.TOP_LEFT -> {
                newWidth = (startWidth - deltaX).coerceIn(minWidth, maxWidth)
                newHeight = (startHeight - deltaY).coerceIn(minHeight, maxHeight)
                newX = fixedRight - newWidth
                newY = fixedBottom - newHeight
                if (newX < 0) {
                    newX = 0
                    newWidth = fixedRight.coerceIn(minWidth, maxWidth)
                }
                if (newY < 0) {
                    newY = 0
                    newHeight = fixedBottom.coerceIn(minHeight, maxHeight)
                }
            }
            ResizeZone.TOP_RIGHT -> {
                newWidth = (startWidth + deltaX).coerceIn(minWidth, maxWidth)
                newHeight = (startHeight - deltaY).coerceIn(minHeight, maxHeight)
                newY = fixedBottom - newHeight
                val maxAllowedWidth = screenWidth - fixedLeft
                newWidth = newWidth.coerceAtMost(maxAllowedWidth)
                if (newY < 0) {
                    newY = 0
                    newHeight = fixedBottom.coerceIn(minHeight, maxHeight)
                }
            }
            ResizeZone.BOTTOM_LEFT -> {
                newWidth = (startWidth - deltaX).coerceIn(minWidth, maxWidth)
                newHeight = (startHeight + deltaY).coerceIn(minHeight, maxHeight)
                newX = fixedRight - newWidth
                val maxAllowedHeight = screenHeight - fixedTop
                newHeight = newHeight.coerceAtMost(maxAllowedHeight)
                if (newX < 0) {
                    newX = 0
                    newWidth = fixedRight.coerceIn(minWidth, maxWidth)
                }
            }
            ResizeZone.NONE -> {}
        }

        return ResizeResult(newX, newY, newWidth, newHeight)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Popup frame and cell rendering
    // ═══════════════════════════════════════════════════════════════════════════

    val POPUP_FRAME_TEXTURE: Identifier = Identifier.of(
        "cobblemonextendedbattleui", "textures/gui/popup_frame.png"
    )
    const val POPUP_TEX_W = 24
    const val POPUP_TEX_H = 24
    const val POPUP_SLICE = 6

    // Layout constants shared across popup-style UIs
    const val FRAME_INSET = 4
    const val CELL_GAP = 3
    const val CELL_PAD = 3
    const val CELL_VPAD_TOP = 5
    const val CELL_VPAD_BOTTOM = 2
    const val COL_GAP = 3
    const val DIVIDER_H = 5

    // Cell colors (Cobblemon dark blue-tinted palette)
    val CELL_BG = color(22, 27, 34)
    val CELL_BORDER = color(50, 58, 72)
    val ROW_DIVIDER_COLOR = color(38, 45, 58)

    /** Shared 9-slice insets for the popup frame texture. */
    val POPUP_FRAME_INSETS = NineSliceRenderer.SliceInsets(POPUP_SLICE)

    /**
     * Renders a 9-slice frame using the shared popup_frame.png texture.
     */
    fun renderPopupFrame(context: DrawContext, x: Int, y: Int, width: Int, height: Int) {
        NineSliceRenderer.render(
            context, POPUP_FRAME_TEXTURE,
            x, y, width, height,
            POPUP_TEX_W, POPUP_TEX_H,
            POPUP_FRAME_INSETS
        )
    }

    /**
     * Draws a cell background with 1px border and filled interior.
     * @param colorTransform Optional transform applied to colors (e.g., for opacity)
     */
    fun drawPopupCell(
        context: DrawContext, x: Int, y: Int, w: Int, h: Int,
        colorTransform: (Int) -> Int = { it }
    ) {
        context.fill(x, y, x + w, y + h, colorTransform(CELL_BORDER))
        context.fill(x + 1, y + 1, x + w - 1, y + h - 1, colorTransform(CELL_BG))
    }

    /**
     * Draws a thin horizontal divider within a cell.
     * @param colorTransform Optional transform applied to color (e.g., for opacity)
     */
    fun drawPopupRowDivider(
        context: DrawContext, cellX: Int, cellW: Int, y: Int,
        colorTransform: (Int) -> Int = { it }
    ) {
        context.fill(cellX + 1, y, cellX + cellW - 1, y + 1, colorTransform(ROW_DIVIDER_COLOR))
    }


    // ═══════════════════════════════════════════════════════════════════════════
    // Corner handle rendering
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Draws an L-shaped corner handle for resize indicators.
     */
    fun drawCornerHandle(
        context: DrawContext,
        cornerX: Int,
        cornerY: Int,
        length: Int,
        thickness: Int,
        color: Int,
        topLeft: Boolean = false,
        topRight: Boolean = false,
        bottomLeft: Boolean = false,
        bottomRight: Boolean = false
    ) {
        when {
            topLeft -> {
                context.fill(cornerX, cornerY, cornerX + length, cornerY + thickness, color)
                context.fill(cornerX, cornerY, cornerX + thickness, cornerY + length, color)
            }
            topRight -> {
                context.fill(cornerX - length, cornerY, cornerX, cornerY + thickness, color)
                context.fill(cornerX - thickness, cornerY, cornerX, cornerY + length, color)
            }
            bottomLeft -> {
                context.fill(cornerX, cornerY - thickness, cornerX + length, cornerY, color)
                context.fill(cornerX, cornerY - length, cornerX + thickness, cornerY, color)
            }
            bottomRight -> {
                context.fill(cornerX - length, cornerY - thickness, cornerX, cornerY, color)
                context.fill(cornerX - thickness, cornerY - length, cornerX, cornerY, color)
            }
        }
    }
}
