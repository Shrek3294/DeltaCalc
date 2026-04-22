package com.cobblemonextendedbattleui.mixin;

import com.cobblemon.mod.common.client.net.battle.BattleMessageHandler;
import com.cobblemon.mod.common.net.messages.client.battle.BattleMessagePacket;
import com.cobblemonextendedbattleui.BattleLog;
import com.cobblemonextendedbattleui.BattleMessageInterceptor;
import com.cobblemonextendedbattleui.PanelConfig;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercept raw battle messages for EBU's state tracking and custom log, then prevent
 * Cobblemon's default handler from populating its own message queue when the custom log is active.
 */
@Mixin(value = BattleMessageHandler.class, priority = 2000, remap = false)
public class BattleMessageHandlerMixin {

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void onHandle(BattleMessagePacket packet, MinecraftClient client, CallbackInfo ci) {
        var messages = packet.getMessages();
        if (messages.isEmpty()) {
            return;
        }

        if (PanelConfig.INSTANCE.needsBattleStateTracking()) {
            BattleMessageInterceptor.INSTANCE.processMessages(messages);
        }

        if (PanelConfig.INSTANCE.getEnableBattleLog()) {
            BattleLog.INSTANCE.processMessages(messages);
            // Keep Cobblemon's queue empty while EBU owns the battle log.
            ci.cancel();
        }
    }
}
