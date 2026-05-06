package com.cobblemonextendedbattleui.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.client.net.battle.BattleHealthChangeHandler;
import com.cobblemon.mod.common.net.messages.client.battle.BattleHealthChangePacket;
import com.cobblemonextendedbattleui.DamageTracker;
import com.cobblemonextendedbattleui.TeamIndicatorUI;
import com.cobblemonextendedbattleui.BattleStateTracker;
import com.cobblemonextendedbattleui.PanelConfig;
import com.cobblemonextendedbattleui.battle.messages.MessageParser;
import com.cobblemonextendedbattleui.battle.state.PokemonRegistry;
import com.cobblemonextendedbattleui.calc.DebugDumper;
import net.minecraft.client.MinecraftClient;

import java.util.UUID;

/**
 * Intercepts HP changes to track damage/healing percentages for the battle log.
 *
 * Uses DamageTracker to maintain HP tracking per Pokemon by UUID. HP baselines are
 * pre-populated when Pokemon switch in (via BattleSwitchHandlerMixin), ensuring we
 * always have an accurate baseline for damage calculations.
 *
 * Conditionally processes tracking based on enabled features:
 * - DamageTracker: Only when battle log is enabled (for damage percentages)
 * - TeamIndicatorUI KO tracking: Only when team indicators are enabled
 * - BattleStateTracker KO tracking: Only when panel or team indicators are enabled
 */
@Mixin(value = BattleHealthChangeHandler.class, remap = false)
public class BattleHealthChangeHandlerMixin {

    @Inject(method = "handle", at = @At("HEAD"))
    private void onHandlePre(BattleHealthChangePacket packet, MinecraftClient client, CallbackInfo ci) {
        // Skip entirely if no features need HP tracking
        boolean needsDamage = PanelConfig.INSTANCE.needsDamageTracking();
        boolean needsKOTracking = PanelConfig.INSTANCE.needsBattleStateTracking() ||
                                   PanelConfig.INSTANCE.getEnableTeamIndicators();
        if (!needsDamage && !needsKOTracking) return;

        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        if (battle == null) return;

        try {
            String pnx = packet.getPnx();
            var result = battle.getPokemonFromPNX(pnx);
            var activePokemon = result.getSecond();
            ClientBattlePokemon pokemon = activePokemon.getBattlePokemon();

            if (pokemon != null) {
                UUID uuid = pokemon.getUuid();
                float maxHp = pokemon.getMaxHp();
                float newHpValue = packet.getNewHealth();
                boolean isFlat = pokemon.isHpFlat();

                // Convert new HP value to percentage (0-100)
                float newPercent;
                if (isFlat && maxHp > 0) {
                    newPercent = (newHpValue / maxHp) * 100f;
                } else {
                    // Non-flat values are already 0.0-1.0 percentages
                    newPercent = newHpValue * 100f;
                }

                // Only track HP changes if battle log needs damage percentages
                if (needsDamage) {
                    Float trackedOldPercent = DamageTracker.INSTANCE.getLastKnownHpPercent(uuid);

                    if (trackedOldPercent == null) {
                        DamageTracker.INSTANCE.updateHpPercent(uuid, newPercent);
                    } else {
                        float oldPercent = trackedOldPercent;
                        DamageTracker.INSTANCE.updateHpPercent(uuid, newPercent);

                        float hpChange = oldPercent - newPercent;
                        String pokemonName = pokemon.getDisplayName().getString();

                        if (hpChange > 0.5f) {
                            DamageTracker.INSTANCE.recordDamage(pokemonName, hpChange);
                            DebugDumper.INSTANCE.recordActualDamage(
                                    uuid, pokemonName, oldPercent, newPercent, maxHp, isFlat);
                            inferLifeOrbFromSelfDamage(uuid, pokemonName, hpChange);
                        } else if (hpChange < -0.5f) {
                            DamageTracker.INSTANCE.recordHealing(pokemonName, -hpChange);
                        }
                    }
                }

                // Track KO at the moment HP reaches 0 (only if needed by enabled features)
                if (newPercent <= 0 && needsKOTracking) {
                    if (PanelConfig.INSTANCE.getEnableTeamIndicators()) {
                        TeamIndicatorUI.INSTANCE.markPokemonAsKO(uuid);
                    }
                    if (PanelConfig.INSTANCE.needsBattleStateTracking()) {
                        BattleStateTracker.INSTANCE.markAsKO(uuid);
                    }
                }
            }
        } catch (Exception e) {
            // Silent fail to avoid breaking gameplay
        }
    }

    /**
     * Infer Life Orb when an attacker takes ~10% self-damage immediately after using a move.
     * Cobblemon's `cobblemon.battle.damage.lifeorb` translation key handles the explicit case,
     * but Delta builds may not always emit it. This is a fallback so the calc's item-aware
     * damage modifiers (Life Orb's +30% multiplier) actually fire.
     *
     * Filters to avoid false positives:
     *  - Target must be the most recent move's user (i.e. self-damage on the attacker).
     *  - The move must still be resolving (lastMoveResolved=false) so this isn't a hazard tick.
     *  - Damage delta in [9.0, 11.0]% — tight enough to exclude:
     *      * Iron Barbs / Rough Skin (12.5%)
     *      * Rocky Helmet (16.67%)
     *      * Brave Bird / Flare Blitz / Wood Hammer recoil (33%)
     *      * Take Down / Submission recoil (25%)
     *  - setItem with HELD is no-op when an item is already revealed, so confirmed items win.
     */
    private void inferLifeOrbFromSelfDamage(UUID targetUuid, String targetName, float damagePct) {
        if (damagePct < 9.0f || damagePct > 11.0f) return;
        if (MessageParser.INSTANCE.getLastMoveResolved()) return;
        String attacker = MessageParser.INSTANCE.getLastMoveUser();
        String moveName = MessageParser.INSTANCE.getLastMoveName();
        if (attacker == null || moveName == null) return;

        UUID attackerUuid = PokemonRegistry.INSTANCE.resolvePokemonUuid(attacker, null);
        if (attackerUuid == null || !attackerUuid.equals(targetUuid)) return;

        // Don't overwrite a confirmed item — setItem(HELD) is no-op when already set.
        BattleStateTracker.INSTANCE.setItem(
                attacker, "Life Orb", BattleStateTracker.ItemStatus.HELD, null);
    }
}
