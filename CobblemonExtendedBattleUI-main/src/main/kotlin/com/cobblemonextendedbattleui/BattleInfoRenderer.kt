package com.cobblemonextendedbattleui

import com.cobblemon.mod.common.client.CobblemonClient
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext

/**
 * Renders battle information overlays.
 */
object BattleInfoRenderer {

    fun render(context: DrawContext) {
        // Clear stale move tooltip state when BattleMoveSelection stops rendering.
        // Must run before panels check shouldHandleFontInput() and before the
        // F1 early return (otherwise staleness is never detected while HUD is hidden).
        MoveTooltipRenderer.resetIfStale()

        // Respect F1 to hide HUD
        if (MinecraftClient.getInstance().options.hudHidden) return

        // Critical: Check for battle changes FIRST when any feature needs state tracking
        // This ensures state is cleared when a new battle starts, regardless of which features are enabled
        if (PanelConfig.needsBattleStateTracking()) {
            CobblemonClient.battle?.let { battle ->
                BattleStateTracker.checkBattleChanged(battle.battleId)
            }
        }

        if (PanelConfig.enableTeamIndicators) {
            TeamIndicatorUI.render(context)
        }
        if (PanelConfig.enableBattleInfoPanel) {
            BattleInfoPanel.render(context)
        }
        if (PanelConfig.enableBattleLog) {
            BattleLogWidget.render(context)
        }

        // Tooltip renders LAST to appear on top of everything
        if (PanelConfig.enableTeamIndicators) {
            TeamIndicatorUI.renderHoverTooltip(context)
        }
    }
}
