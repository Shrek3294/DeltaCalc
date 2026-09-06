package com.cobblemonextendedbattleui.ui.calc

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CalcPanelStateTest {

    @BeforeEach
    @AfterEach
    fun cleanUp() {
        CalcPanelState.resetLayout()
    }

    @Test
    fun testResetLayoutClearsCoordinatesAndDimensionsOnly() {
        try {
            CalcPanelState.setPosition(150, 250)
            CalcPanelState.setDimensions(350, 450)
            assertEquals(150, CalcPanelState.x)
            assertEquals(250, CalcPanelState.y)
            assertEquals(350, CalcPanelState.width)
            assertEquals(450, CalcPanelState.height)

            CalcPanelState.resetLayout()

            assertNull(CalcPanelState.x)
            assertNull(CalcPanelState.y)
            assertNull(CalcPanelState.width)
            assertNull(CalcPanelState.height)
            assertTrue(CalcPanelState.enabled)
            assertEquals(1.0f, CalcPanelState.fontScale)
        } finally {
            CalcPanelState.resetLayout()
        }
    }
}
