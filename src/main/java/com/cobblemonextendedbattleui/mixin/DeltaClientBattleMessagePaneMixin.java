package com.cobblemonextendedbattleui.mixin;

import com.cobblemonextendedbattleui.PanelConfig;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DeltaClient swaps Cobblemon's vanilla battle widgets for its own battle UI classes.
 * When EBU owns the battle log, its replacement message pane needs the same suppression
 * as Cobblemon's native BattleMessagePane.
 */
@Pseudo
@Mixin(targets = "com.symstudios.deltaclient.gui.battle.update.battle.widgets.BattleMessagePane", remap = false)
public class DeltaClientBattleMessagePaneMixin {

    @Inject(
        method = "method_48579(Lnet/minecraft/class_332;IIF)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void cobblemonextendedbattleui$cancelDeltaBattleLogRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (PanelConfig.INSTANCE.getEnableBattleLog()) {
            ci.cancel();
        }
    }
}
