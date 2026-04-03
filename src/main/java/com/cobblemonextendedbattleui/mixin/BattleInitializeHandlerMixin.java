package com.cobblemonextendedbattleui.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.client.net.battle.BattleInitializeHandler;
import com.cobblemon.mod.common.net.messages.client.battle.BattleInitializePacket;
import com.cobblemonextendedbattleui.BattleStateTracker;
import com.cobblemonextendedbattleui.DamageTracker;
import com.cobblemonextendedbattleui.PanelConfig;
import net.minecraft.client.MinecraftClient;

import java.util.UUID;

/**
 * Intercepts battle initialization to pre-populate HP baselines and register Pokemon.
 *
 * Conditionally initializes tracking based on enabled features:
 * - DamageTracker HP baselines: Only when battle log is enabled
 * - BattleStateTracker Pokemon registration: Only when panel or team indicators are enabled
 */
@Mixin(value = BattleInitializeHandler.class, remap = false)
public class BattleInitializeHandlerMixin {

    @Inject(method = "handle", at = @At("HEAD"))
    private void onHandlePre(BattleInitializePacket packet, MinecraftClient client, CallbackInfo ci) {
        boolean needsStateTracking = PanelConfig.INSTANCE.needsBattleStateTracking();
        boolean needsDamageTracking = PanelConfig.INSTANCE.needsDamageTracking();

        // Skip entirely if no features need initialization
        if (!needsStateTracking && !needsDamageTracking) return;

        try {
            // Determine if player is spectating
            UUID playerUUID = client.getSession().getUuidOrNull();
            boolean isPlayerInSide1 = isPlayerInSide(packet.getSide1(), playerUUID);
            boolean isPlayerInSide2 = isPlayerInSide(packet.getSide2(), playerUUID);
            boolean isSpectating = !isPlayerInSide1 && !isPlayerInSide2;

            // Cobblemon's BattleOverlay swaps sides based on player presence:
            // - If player is in side1: side1 is LEFT (ally), side2 is RIGHT
            // - If player is in side2: side2 is LEFT (ally), side1 is RIGHT
            // - If spectating: side2 is LEFT (ally), side1 is RIGHT
            // So side1 is "ally" only when the player is actually in side1
            boolean side1IsAlly = isPlayerInSide1;

            // Only set up BattleStateTracker if needed
            if (needsStateTracking) {
                BattleStateTracker.INSTANCE.setSpectating(isSpectating);

                String side1PlayerName = getFirstActorName(packet.getSide1());
                String side2PlayerName = getFirstActorName(packet.getSide2());

                if (side1PlayerName != null && side2PlayerName != null) {
                    if (side1IsAlly) {
                        BattleStateTracker.INSTANCE.setPlayerNames(side1PlayerName, side2PlayerName);
                    } else {
                        BattleStateTracker.INSTANCE.setPlayerNames(side2PlayerName, side1PlayerName);
                    }
                }
            }

            // Initialize Pokemon from both sides (conditionally based on needed tracking)
            initializePokemonFromSide(packet.getSide1(), side1IsAlly, needsStateTracking, needsDamageTracking);
            initializePokemonFromSide(packet.getSide2(), !side1IsAlly, needsStateTracking, needsDamageTracking);
        } catch (Exception e) {
            // Silent fail to avoid breaking gameplay
        }
    }

    private boolean isPlayerInSide(BattleInitializePacket.BattleSideDTO side, UUID playerUUID) {
        if (side == null || playerUUID == null) return false;
        for (BattleInitializePacket.BattleActorDTO actor : side.getActors()) {
            if (actor != null && playerUUID.equals(actor.getUuid())) {
                return true;
            }
        }
        return false;
    }

    private String getFirstActorName(BattleInitializePacket.BattleSideDTO side) {
        if (side == null) return null;
        for (BattleInitializePacket.BattleActorDTO actor : side.getActors()) {
            if (actor != null && actor.getDisplayName() != null) {
                return actor.getDisplayName().getString();
            }
        }
        return null;
    }

    private void initializePokemonFromSide(BattleInitializePacket.BattleSideDTO side, boolean isAlly,
                                           boolean needsStateTracking, boolean needsDamageTracking) {
        if (side == null) return;

        for (BattleInitializePacket.BattleActorDTO actor : side.getActors()) {
            if (actor == null) continue;

            for (BattleInitializePacket.ActiveBattlePokemonDTO pokemon : actor.getActivePokemon()) {
                if (pokemon == null) continue;

                UUID uuid = pokemon.getUuid();

                // Pre-populate the HP tracker only if battle log needs damage percentages
                if (needsDamageTracking) {
                    float hpValue = pokemon.getHpValue();
                    float maxHp = pokemon.getMaxHp();
                    boolean isFlat = pokemon.isFlatHp();
                    DamageTracker.INSTANCE.initializeHpFromSwitch(uuid, hpValue, maxHp, isFlat);
                }

                // Register Pokemon with BattleStateTracker only if panel/tooltips need it
                if (needsStateTracking) {
                    String name = pokemon.getDisplayName() != null ? pokemon.getDisplayName().getString() : "Unknown";
                    BattleStateTracker.INSTANCE.registerPokemon(uuid, name, isAlly);
                }
            }
        }
    }
}
