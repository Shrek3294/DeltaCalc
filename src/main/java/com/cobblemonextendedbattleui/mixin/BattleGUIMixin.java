package com.cobblemonextendedbattleui.mixin;

import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import com.cobblemon.mod.common.client.gui.battle.widgets.BattleMessagePane;
import com.cobblemonextendedbattleui.PanelConfig;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BattleGUI keeps its own message pane child in addition to the minimized overlay instance.
 * Hide that child while EBU's custom battle log is enabled.
 */
@Mixin(value = BattleGUI.class, remap = false)
public abstract class BattleGUIMixin {

    @Shadow(remap = false)
    private BattleMessagePane messagePane;

    @Inject(method = "method_25394", at = @At("HEAD"))
    private void updateBattleMessagePaneVisibility(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (messagePane != null) {
            messagePane.visible = !PanelConfig.INSTANCE.getEnableBattleLog();
        }
    }
}
