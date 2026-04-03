package com.cobblemonextendedbattleui.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.client.gui.battle.widgets.BattleMessagePane;
import com.cobblemonextendedbattleui.PanelConfig;
import net.minecraft.client.gui.DrawContext;

/**
 * Mixin to hide Cobblemon's BattleMessagePane when our custom battle log is enabled.
 * This prevents Cobblemon's log from rendering while we display our own enhanced log.
 *
 * Note: BattleMessagePane is a Cobblemon class so its name won't be remapped.
 * The method renderWidget and DrawContext WILL be remapped to intermediary names in production.
 */
@Mixin(BattleMessagePane.class)
public class BattleMessagePaneMixin {

    /**
     * Intercept the renderWidget method and skip it entirely when our battle log is enabled.
     * When enableBattleLog is false, Cobblemon's native log will be shown instead.
     */
    @Inject(
        method = "renderWidget(Lnet/minecraft/client/gui/DrawContext;IIF)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onRenderWidget(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (PanelConfig.INSTANCE.getEnableBattleLog()) {
            ci.cancel();
        }
    }
}
