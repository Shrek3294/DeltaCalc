package com.cobblemonextendedbattleui.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.net.battle.BattleSwitchPokemonHandler;
import com.cobblemon.mod.common.net.messages.client.battle.BattleSwitchPokemonPacket;
import com.cobblemon.mod.common.net.messages.client.battle.BattleInitializePacket;
import com.cobblemonextendedbattleui.BattleStateTracker;
import com.cobblemonextendedbattleui.DamageTracker;
import com.cobblemonextendedbattleui.PanelConfig;
import net.minecraft.client.MinecraftClient;

import java.util.UUID;

/**
 * Intercepts Pokemon switch-in events to:
 * - Pre-populate HP baselines in DamageTracker (when battle log enabled)
 * - Register Pokemon with BattleStateTracker (when panel/tooltips enabled)
 */
@Mixin(value = BattleSwitchPokemonHandler.class, remap = false)
public class BattleSwitchHandlerMixin {

    @Inject(method = "handle", at = @At("HEAD"))
    private void onHandlePre(BattleSwitchPokemonPacket packet, MinecraftClient client, CallbackInfo ci) {
        boolean needsStateTracking = PanelConfig.INSTANCE.needsBattleStateTracking();
        boolean needsDamageTracking = PanelConfig.INSTANCE.needsDamageTracking();

        // Skip if no tracking is needed
        if (!needsStateTracking && !needsDamageTracking) return;

        try {
            BattleInitializePacket.ActiveBattlePokemonDTO newPokemon = packet.getNewPokemon();
            if (newPokemon == null) return;

            UUID uuid = newPokemon.getUuid();

            // Pre-populate the HP tracker with the switch-in HP value (only if battle log needs it)
            if (needsDamageTracking) {
                float hpValue = newPokemon.getHpValue();
                float maxHp = newPokemon.getMaxHp();
                boolean isFlat = newPokemon.isFlatHp();
                DamageTracker.INSTANCE.initializeHpFromSwitch(uuid, hpValue, maxHp, isFlat);
            }

            // Register Pokemon with BattleStateTracker for name->UUID lookup (for stat tracking)
            if (needsStateTracking) {
                String name = newPokemon.getDisplayName() != null ? newPokemon.getDisplayName().getString() : "Unknown";

                // Determine if this Pokemon is ally or opponent by checking the pnx actor
                boolean isAlly = determineIfAlly(packet, client);
                BattleStateTracker.INSTANCE.registerPokemon(uuid, name, isAlly);
            }
        } catch (Exception e) {
            // Silent fail to avoid breaking gameplay
        }
    }

    /**
     * Determine if the switching Pokemon is on the ally or opponent side.
     * Uses battle.getPokemonFromPNX() to find the side, then compares with player's side.
     */
    private boolean determineIfAlly(BattleSwitchPokemonPacket packet, MinecraftClient client) {
        try {
            ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
            if (battle == null) return true;

            UUID playerUUID = client.getSession().getUuidOrNull();
            if (playerUUID == null) return true;

            // Check if player is in side1 or side2
            boolean playerInSide1 = battle.getSide1().getActors().stream()
                    .anyMatch(actor -> playerUUID.equals(actor.getUuid()));
            boolean playerInSide2 = battle.getSide2().getActors().stream()
                    .anyMatch(actor -> playerUUID.equals(actor.getUuid()));

            // Determine which side the switch is on by checking if the actor is in side1
            String pnx = packet.getPnx();
            var result = battle.getPokemonFromPNX(pnx);
            if (result == null) return true;

            var actor = result.getFirst();
            if (actor == null) return true;

            UUID actorUuid = actor.getUuid();
            boolean switchInSide1 = battle.getSide1().getActors().stream()
                    .anyMatch(a -> actorUuid.equals(a.getUuid()));

            // Spectators: treat side1 as ally
            if (!playerInSide1 && !playerInSide2) {
                return switchInSide1; // side1 is "ally" for spectators
            }

            // If player is in side1, side1 switches are ally
            // If player is in side2, side2 switches are ally
            if (playerInSide1) {
                return switchInSide1;
            } else {
                return !switchInSide1;
            }
        } catch (Exception e) {
            return true; // Default to ally on error
        }
    }
}
