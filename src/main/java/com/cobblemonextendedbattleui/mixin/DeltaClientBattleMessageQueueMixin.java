package com.cobblemonextendedbattleui.mixin;

import com.cobblemonextendedbattleui.BattleLog;
import com.cobblemonextendedbattleui.BattleMessageInterceptor;
import com.cobblemonextendedbattleui.BoostTraceLog;
import com.cobblemonextendedbattleui.PanelConfig;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.List;

/**
 * DeltaClient short-circuits Cobblemon's native BattleMessageHandler and appends
 * incoming battle messages directly into ClientBattleMessageQueue via a synthetic
 * deltaclient$add(Collection) method. Hook that path so battle state tracking and
 * the custom log still see the same raw messages.
 */
@Pseudo
@Mixin(targets = "com.cobblemon.mod.common.client.battle.ClientBattleMessageQueue", remap = false)
public class DeltaClientBattleMessageQueueMixin {

    @Inject(
        method = "deltaclient$add(Ljava/util/Collection;)V",
        at = @At("HEAD"),
        require = 0
    )
    private void cobblemonextendedbattleui$processDeltaQueuedMessages(Collection<?> messages, CallbackInfo ci) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        List<Text> textMessages = messages.stream()
            .filter(Text.class::isInstance)
            .map(Text.class::cast)
            .toList();

        if (textMessages.isEmpty()) {
            return;
        }

        boolean containsBoostMessage = textMessages.stream().anyMatch(text ->
            text != null && (
                text.getString().contains(" rose") ||
                text.getString().contains(" fell") ||
                text.getString().contains("stat changes")
            )
        );
        if (containsBoostMessage) {
            BoostTraceLog.INSTANCE.append("delta queue received " + textMessages.size() + " messages: " + textMessages);
        }

        if (PanelConfig.INSTANCE.needsBattleStateTracking()) {
            BattleMessageInterceptor.INSTANCE.processMessages(textMessages);
        }

        if (PanelConfig.INSTANCE.getEnableBattleLog()) {
            BattleLog.INSTANCE.processMessages(textMessages);
        }
    }
}
