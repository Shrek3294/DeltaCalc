package com.cobblemonextendedbattleui.mixin;

import com.cobblemon.mod.common.client.gui.battle.BattleOverlay;
import com.cobblemon.mod.common.client.gui.battle.widgets.BattleMessagePane;
import com.cobblemonextendedbattleui.PanelConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cobblemon's minimized overlay owns a separate BattleMessagePane instance and renders it
 * directly. Toggle that widget's visibility instead of redirecting a fragile render callsite.
 */
@Mixin(value = BattleOverlay.class, remap = false)
public abstract class BattleOverlayMixin {

    @Shadow(remap = false)
    private BattleMessagePane messagePane;

    @Inject(method = "method_1753", at = @At("HEAD"))
    private void updateBattleMessagePaneVisibility(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (messagePane != null) {
            messagePane.visible = !PanelConfig.INSTANCE.getEnableBattleLog();
        }
    }
}
