package com.cobblemonextendedbattleui

import com.cobblemonextendedbattleui.mixin.BattleOverlayMixin
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.spongepowered.asm.mixin.injection.Inject

class BattleOverlayMixinTargetTest {

    @Test
    fun testBattleOverlayMixinInjectionTargetsBothNamedAndIntermediarySelectors() {
        val method = BattleOverlayMixin::class.java.declaredMethods.firstOrNull {
            it.name == "updateBattleMessagePaneVisibility"
        }
        assertNotNull(method, "updateBattleMessagePaneVisibility handler method must exist")

        val inject = method!!.getAnnotation(Inject::class.java)
        assertNotNull(inject, "@Inject annotation must be present on updateBattleMessagePaneVisibility")

        // Must target 'render' for named dev runtime and 'method_1753' for intermediary production runtime
        assertArrayEquals(
            arrayOf("render", "method_1753"),
            inject.method,
            "Inject method selectors must include both named 'render' and intermediary 'method_1753'"
        )

        // Must be remap = false so that Mixin does not attempt to resolve selectors against the refmap
        assertFalse(
            inject.remap,
            "@Inject remap must be explicitly false for cross-namespace selectors without refmap lookups"
        )

        // Method signature must take DrawContext, RenderTickCounter, CallbackInfo
        assertEquals(3, method.parameterCount, "Handler must accept 3 parameters (DrawContext, RenderTickCounter, CallbackInfo)")
    }
}
