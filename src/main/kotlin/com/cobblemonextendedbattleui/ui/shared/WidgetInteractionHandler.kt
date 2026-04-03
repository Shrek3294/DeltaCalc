package com.cobblemonextendedbattleui.ui.shared

import com.cobblemonextendedbattleui.UIUtils

/**
 * Manages drag and resize interaction state for a UI widget.
 *
 * Encapsulates the common drag/resize state machine used by both
 * BattleInfoPanel and BattleLogWidget. Each widget creates its own
 * instance and delegates mouse interaction to it.
 */
class WidgetInteractionHandler(
    private val panel: UIUtils.ActivePanel
) {
    // Drag state
    var isDragging: Boolean = false; private set
    var dragOffsetX: Int = 0; private set
    var dragOffsetY: Int = 0; private set

    // Optional drag threshold support (BattleInfoPanel uses this for click-to-toggle)
    var dragStartX: Int = 0; private set
    var dragStartY: Int = 0; private set
    var hasDragged: Boolean = false; private set
    var dragThreshold: Int = 0

    // Resize state
    var isResizing: Boolean = false; private set
    var resizeZone: UIUtils.ResizeZone = UIUtils.ResizeZone.NONE; private set
    var resizeStartX: Int = 0; private set
    var resizeStartY: Int = 0; private set
    var resizeStartWidth: Int = 0; private set
    var resizeStartHeight: Int = 0; private set
    var resizeStartPanelX: Int = 0; private set
    var resizeStartPanelY: Int = 0; private set

    // Scrollbar drag state
    var isDraggingScrollbar: Boolean = false; private set
    var scrollbarDragStartY: Int = 0; private set
    var scrollbarDragStartOffset: Int = 0; private set

    // Hover state
    var hoveredZone: UIUtils.ResizeZone = UIUtils.ResizeZone.NONE

    /** Whether any interaction is currently active. */
    val isInteracting: Boolean
        get() = isDragging || isResizing || isDraggingScrollbar

    /**
     * Begins a drag operation from a header/title bar region.
     */
    fun startDrag(mouseX: Int, mouseY: Int, widgetX: Int, widgetY: Int) {
        UIUtils.claimInteraction(panel)
        isDragging = true
        hasDragged = false
        dragOffsetX = mouseX - widgetX
        dragOffsetY = mouseY - widgetY
        dragStartX = mouseX
        dragStartY = mouseY
    }

    /**
     * Updates the drag position. Returns the new widget (x, y) position.
     * If a drag threshold is set, returns null until the threshold is exceeded.
     */
    fun updateDrag(mouseX: Int, mouseY: Int, screenWidth: Int, screenHeight: Int, widgetW: Int, widgetH: Int): Pair<Int, Int>? {
        if (!isDragging) return null

        if (dragThreshold > 0 && !hasDragged) {
            val deltaX = kotlin.math.abs(mouseX - dragStartX)
            val deltaY = kotlin.math.abs(mouseY - dragStartY)
            if (deltaX <= dragThreshold && deltaY <= dragThreshold) return null
            hasDragged = true
        } else {
            hasDragged = true
        }

        val newX = (mouseX - dragOffsetX).coerceIn(0, screenWidth - widgetW)
        val newY = (mouseY - dragOffsetY).coerceIn(0, screenHeight - widgetH)
        return Pair(newX, newY)
    }

    /**
     * Ends a drag operation. Returns true if the widget was actually dragged
     * (moved beyond threshold), false if it was a click.
     */
    fun endDrag(): Boolean {
        val didDrag = hasDragged
        isDragging = false
        hasDragged = false
        releaseIfIdle()
        return didDrag
    }

    /**
     * Begins a resize operation.
     */
    fun startResize(mouseX: Int, mouseY: Int, zone: UIUtils.ResizeZone, widgetX: Int, widgetY: Int, widgetW: Int, widgetH: Int) {
        UIUtils.claimInteraction(panel)
        isResizing = true
        resizeZone = zone
        resizeStartX = mouseX
        resizeStartY = mouseY
        resizeStartWidth = widgetW
        resizeStartHeight = widgetH
        resizeStartPanelX = widgetX
        resizeStartPanelY = widgetY
    }

    /**
     * Calculates the resize result from current mouse position.
     */
    fun calculateResize(
        mouseX: Int,
        mouseY: Int,
        minWidth: Int,
        maxWidth: Int,
        minHeight: Int,
        maxHeight: Int,
        screenWidth: Int,
        screenHeight: Int
    ): UIUtils.ResizeResult {
        return UIUtils.calculateResize(
            zone = resizeZone,
            deltaX = mouseX - resizeStartX,
            deltaY = mouseY - resizeStartY,
            startX = resizeStartPanelX,
            startY = resizeStartPanelY,
            startWidth = resizeStartWidth,
            startHeight = resizeStartHeight,
            minWidth = minWidth,
            maxWidth = maxWidth,
            minHeight = minHeight,
            maxHeight = maxHeight,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )
    }

    /**
     * Ends a resize operation.
     */
    fun endResize() {
        isResizing = false
        resizeZone = UIUtils.ResizeZone.NONE
        releaseIfIdle()
    }

    /**
     * Begins a scrollbar drag operation.
     */
    fun startScrollbarDrag(mouseY: Int, currentScrollOffset: Int) {
        UIUtils.claimInteraction(panel)
        isDraggingScrollbar = true
        scrollbarDragStartY = mouseY
        scrollbarDragStartOffset = currentScrollOffset
    }

    /**
     * Ends a scrollbar drag operation.
     */
    fun endScrollbarDrag() {
        isDraggingScrollbar = false
        releaseIfIdle()
    }

    /**
     * Releases all interaction state (called on mouse release).
     * Returns true if any interaction was active.
     */
    fun releaseAll(): Boolean {
        val wasInteracting = isInteracting
        isDragging = false
        hasDragged = false
        isResizing = false
        resizeZone = UIUtils.ResizeZone.NONE
        isDraggingScrollbar = false
        if (wasInteracting) {
            UIUtils.releaseInteraction(panel)
        }
        return wasInteracting
    }

    private fun releaseIfIdle() {
        if (!isInteracting) {
            UIUtils.releaseInteraction(panel)
        }
    }
}
