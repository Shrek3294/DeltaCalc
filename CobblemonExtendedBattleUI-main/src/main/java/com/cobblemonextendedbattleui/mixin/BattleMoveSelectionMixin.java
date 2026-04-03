package com.cobblemonextendedbattleui.mixin;

import com.cobblemon.mod.common.client.battle.SingleActionRequest;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleActionSelection;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection.MoveTile;
import com.cobblemonextendedbattleui.MoveTooltipRenderer;
import com.cobblemonextendedbattleui.PanelConfig;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.UUID;

/**
 * Mixin to add move tooltips to Cobblemon's move selection screen.
 * Hooks into renderWidget to track move tile bounds and render tooltips on hover.
 */
@Mixin(value = BattleMoveSelection.class, remap = false)
public class BattleMoveSelectionMixin {

    @Shadow
    public List<MoveTile> moveTiles;

    /**
     * Clear move tile tracking at the start of each render frame.
     * Note: remap = true overrides class-level remap = false for this Minecraft method.
     */
    @Inject(method = "renderWidget", at = @At("HEAD"), remap = true)
    private void onRenderWidgetHead(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (PanelConfig.INSTANCE.getEnableMoveTooltips()) {
            MoveTooltipRenderer.INSTANCE.clear();
        }
    }

    /**
     * After move tiles are rendered, register their bounds and render tooltip.
     * Note: remap = true overrides class-level remap = false for this Minecraft method.
     */
    @Inject(method = "renderWidget", at = @At("RETURN"), remap = true)
    private void onRenderWidgetReturn(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!PanelConfig.INSTANCE.getEnableMoveTooltips()) {
            return;
        }

        // Identify which Pokemon is selecting a move (critical for doubles)
        SingleActionRequest request = ((BattleActionSelection) (Object) this).getRequest();
        UUID pokemonUuid = null;
        if (request != null && request.getActivePokemon() != null
                && request.getActivePokemon().getBattlePokemon() != null) {
            pokemonUuid = request.getActivePokemon().getBattlePokemon().getUuid();
        }
        MoveTooltipRenderer.INSTANCE.setActivePokemonUuid(pokemonUuid);

        // Register each move tile's bounds for hover detection
        for (MoveTile tile : moveTiles) {
            MoveTooltipRenderer.INSTANCE.registerMoveTile(
                tile.getX(),
                tile.getY(),
                BattleMoveSelection.MOVE_WIDTH,
                BattleMoveSelection.MOVE_HEIGHT,
                tile.getMoveTemplate(),
                tile.getMove().getPp(),
                tile.getMove().getMaxpp()
            );
        }

        // Update hover state and render tooltip if hovering a move
        MoveTooltipRenderer.INSTANCE.updateHoverState(mouseX, mouseY);
        MoveTooltipRenderer.INSTANCE.renderTooltip(context);

        // Handle font scaling input ([ ] keys)
        MoveTooltipRenderer.INSTANCE.handleInput();
    }
}
