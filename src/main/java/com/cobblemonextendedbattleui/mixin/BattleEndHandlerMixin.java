package com.cobblemonextendedbattleui.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.client.net.battle.BattleEndHandler;
import com.cobblemon.mod.common.net.messages.client.battle.BattleEndPacket;
import com.cobblemonextendedbattleui.BattleInfoPanel;
import com.cobblemonextendedbattleui.BattleLog;
import com.cobblemonextendedbattleui.BattleLogWidget;
import com.cobblemonextendedbattleui.BattleStateTracker;
import com.cobblemonextendedbattleui.DamageTracker;
import com.cobblemonextendedbattleui.MoveTooltipRenderer;
import com.cobblemonextendedbattleui.TeamIndicatorUI;
import net.minecraft.client.MinecraftClient;

/**
 * Mixin to clear tracking when battle ends.
 */
@Mixin(value = BattleEndHandler.class, remap = false)
public class BattleEndHandlerMixin {

    /**
     * Inject at the start of handle() to clear our trackers.
     */
    @Inject(method = "handle", at = @At("HEAD"))
    private void onHandle(BattleEndPacket packet, MinecraftClient client, CallbackInfo ci) {
        BattleStateTracker.INSTANCE.clear();
        TeamIndicatorUI.INSTANCE.clear();
        BattleInfoPanel.INSTANCE.clearBattleState();
        BattleLog.INSTANCE.clear();
        BattleLogWidget.INSTANCE.clear();
        DamageTracker.INSTANCE.clear();
        MoveTooltipRenderer.INSTANCE.clear();
    }
}
