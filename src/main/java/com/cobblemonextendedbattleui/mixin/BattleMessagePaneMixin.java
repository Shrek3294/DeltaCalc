package com.cobblemonextendedbattleui.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.client.gui.battle.widgets.BattleMessagePane;
import com.cobblemonextendedbattleui.PanelConfig;
import net.minecraft.client.gui.DrawContext;

/**
 * Hide Cobblemon's native BattleMessagePane when EBU owns the battle log.
 * Vanilla Cobblemon reaches this through renderWidget, and UI Tweaks is handled separately
 * through its own BattleLogRenderer compatibility mixin.
 */
@Mixin(value = BattleMessagePane.class)
public class BattleMessagePaneMixin {

    /**
     * Suppress the native pane draw path whenever EBU's custom log is active.
     */
    @Inject(
        method = "renderWidget(Lnet/minecraft/client/gui/DrawContext;IIF)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onRenderWidget(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        cancelIfCustomLogEnabled(ci);
    }

    private void cancelIfCustomLogEnabled(CallbackInfo ci) {
        if (PanelConfig.INSTANCE.getEnableBattleLog()) {
            ci.cancel();
        }
    }
}
