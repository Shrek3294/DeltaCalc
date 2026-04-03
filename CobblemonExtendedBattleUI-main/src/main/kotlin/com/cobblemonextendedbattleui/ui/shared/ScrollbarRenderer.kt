package com.cobblemonextendedbattleui.ui.shared

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.gui.DrawContext

/**
 * Reusable scrollbar renderer with thumb tracking and drag support.
 *
 * Manages its own scrollbar geometry (track position, thumb position/size)
 * and provides hit-testing for mouse interaction.
 */
class ScrollbarRenderer(
    private val trackWidth: Int = 3,
    private val bgColor: Int,
    private val thumbColor: Int,
    private val thumbHoverColor: Int? = null,
    private val minThumbHeight: Int = 10,
    private val opacityProvider: () -> (Int) -> Int = { { it } }
) {

    // Computed bounds (updated each render call)
    var trackX: Int = 0; private set
    var trackY: Int = 0; private set
    var trackHeight: Int = 0; private set
    var thumbY: Int = 0; private set
    var thumbHeight: Int = 0; private set

    /**
     * Renders the scrollbar track and thumb.
     *
     * @param context        The draw context
     * @param x              X position of the scrollbar track
     * @param y              Y position of the scrollbar track
     * @param height         Height of the scrollbar track
     * @param contentHeight  Total content height
     * @param visibleHeight  Visible viewport height
     * @param scrollOffset   Current scroll offset
     * @param isHovered      Whether the thumb is currently hovered/dragged
     */
    fun render(
        context: DrawContext,
        x: Int,
        y: Int,
        height: Int,
        contentHeight: Int,
        visibleHeight: Int,
        scrollOffset: Int,
        isHovered: Boolean = false
    ) {
        if (contentHeight <= visibleHeight) return

        val applyOpacity = opacityProvider()

        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()

        trackX = x
        trackY = y
        trackHeight = height

        // Background track
        context.fill(x, y, x + trackWidth, y + height, applyOpacity(bgColor))

        // Thumb
        thumbHeight = ((visibleHeight.toFloat() / contentHeight) * height).toInt()
            .coerceAtLeast(minThumbHeight)
        val maxScroll = contentHeight - visibleHeight
        val ratio = if (maxScroll > 0) scrollOffset.toFloat() / maxScroll else 0f
        thumbY = y + ((height - thumbHeight) * ratio).toInt()

        val color = if (isHovered && thumbHoverColor != null) thumbHoverColor else thumbColor
        context.fill(x, thumbY, x + trackWidth, thumbY + thumbHeight, applyOpacity(color))
    }

    /**
     * Calculates the thumb height for a given track height (used for layout calculations).
     */
    fun calculateThumbHeight(trackHeight: Int, contentHeight: Int, visibleHeight: Int): Int {
        if (contentHeight <= visibleHeight) return 0
        return ((visibleHeight.toFloat() / contentHeight) * trackHeight).toInt()
            .coerceAtLeast(minThumbHeight)
    }

    /**
     * Tests whether the mouse is over the scrollbar track area (wider hit area for usability).
     */
    fun isOverTrack(mouseX: Int, mouseY: Int, hitPadding: Int = 3): Boolean {
        return mouseX >= trackX - hitPadding && mouseX <= trackX + trackWidth + hitPadding &&
               mouseY >= trackY && mouseY <= trackY + trackHeight
    }

    /**
     * Tests whether the mouse is over the thumb specifically.
     */
    fun isOverThumb(mouseX: Int, mouseY: Int, hitPadding: Int = 3): Boolean {
        return mouseX >= trackX - hitPadding && mouseX <= trackX + trackWidth + hitPadding &&
               mouseY >= thumbY && mouseY <= thumbY + thumbHeight
    }

    /**
     * Converts a mouse Y position on the track to a scroll offset.
     * Used for "click on track to jump" behavior.
     */
    fun trackClickToScrollOffset(mouseY: Int, contentHeight: Int, visibleHeight: Int): Int {
        if (trackHeight <= 0 || contentHeight <= visibleHeight) return 0
        val trackClickRatio = (mouseY - trackY).toFloat() / trackHeight
        val maxScroll = (contentHeight - visibleHeight).coerceAtLeast(0)
        return (trackClickRatio * maxScroll).toInt().coerceIn(0, maxScroll)
    }

    /**
     * Calculates a new scroll offset from a scrollbar drag delta.
     *
     * @param dragDeltaY    Pixel delta from drag start
     * @param dragStartOffset The scroll offset when drag started
     * @param contentHeight Total content height
     * @param visibleHeight Visible viewport height
     */
    fun dragToScrollOffset(
        dragDeltaY: Int,
        dragStartOffset: Int,
        contentHeight: Int,
        visibleHeight: Int
    ): Int {
        val maxScroll = (contentHeight - visibleHeight).coerceAtLeast(0)
        if (maxScroll <= 0 || trackHeight <= 0) return 0

        val scrollableTrackHeight = trackHeight - thumbHeight
        if (scrollableTrackHeight <= 0) return 0

        val scrollDelta = (dragDeltaY.toFloat() / scrollableTrackHeight * maxScroll).toInt()
        return (dragStartOffset + scrollDelta).coerceIn(0, maxScroll)
    }
}
