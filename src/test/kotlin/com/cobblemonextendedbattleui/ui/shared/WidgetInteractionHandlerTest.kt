package com.cobblemonextendedbattleui.ui.shared

import com.cobblemonextendedbattleui.UIUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WidgetInteractionHandlerTest {

    @Test
    fun testDragMovementAndThreshold() {
        val handler = WidgetInteractionHandler(UIUtils.ActivePanel.DAMAGE_CALC)
        handler.dragThreshold = 3

        // Below threshold (within 3px of start)
        handler.startDrag(mouseX = 100, mouseY = 100, widgetX = 50, widgetY = 50)
        assertNull(handler.updateDrag(101, 101, screenWidth = 800, screenHeight = 600, widgetW = 200, widgetH = 150))

        // Exceeds threshold: moves to (mouseX - offsetX, mouseY - offsetY)
        val dragged = handler.updateDrag(120, 130, screenWidth = 800, screenHeight = 600, widgetW = 200, widgetH = 150)
        assertEquals(Pair(70, 80), dragged)
        assertTrue(handler.endDrag())

        // Click without dragging returns false from endDrag
        handler.startDrag(mouseX = 100, mouseY = 100, widgetX = 50, widgetY = 50)
        assertFalse(handler.endDrag())
    }

    @Test
    fun testUpdateDragBoundsSafety() {
        val handler = WidgetInteractionHandler(UIUtils.ActivePanel.DAMAGE_CALC)
        handler.startDrag(mouseX = 100, mouseY = 100, widgetX = 50, widgetY = 50)

        // Oversized widget in small viewport pins to 0 without throwing IllegalArgumentException
        assertEquals(
            Pair(0, 0),
            handler.updateDrag(500, 500, screenWidth = 200, screenHeight = 150, widgetW = 400, widgetH = 300)
        )

        // Non-positive viewport dimensions pin to 0 without throwing
        assertEquals(
            Pair(0, 0),
            handler.updateDrag(100, 100, screenWidth = 0, screenHeight = -10, widgetW = 200, widgetH = 150)
        )

        // Clamping to screen boundaries
        assertEquals(
            Pair(600, 450),
            handler.updateDrag(1000, 1000, screenWidth = 800, screenHeight = 600, widgetW = 200, widgetH = 150)
        )
        assertEquals(
            Pair(0, 0),
            handler.updateDrag(-100, -100, screenWidth = 800, screenHeight = 600, widgetW = 200, widgetH = 150)
        )

        handler.endDrag()
    }
}
