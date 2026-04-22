package com.cobblemonextendedbattleui.compat.delta

import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemonextendedbattleui.BattleStateTracker
import com.cobblemonextendedbattleui.MoveTooltipRenderer
import com.cobblemonextendedbattleui.PanelConfig
import com.cobblemonextendedbattleui.compat.core.BattlePlatformAdapter
import net.minecraft.client.MinecraftClient
import java.util.UUID

object DeltaBattlePlatformAdapter : BattlePlatformAdapter {
    override fun currentBattleId(): UUID? = CobblemonClient.battle?.battleId

    override fun currentTurn(): Int = BattleStateTracker.currentTurn

    override fun selectedMoveName(): String? = MoveTooltipRenderer.currentHoveredMoveName()

    override fun currentScreenClassName(): String? = MinecraftClient.getInstance().currentScreen?.javaClass?.name

    override fun compatNotes(): List<String> {
        val notes = mutableListOf<String>()
        // DeltaClient UI detection: no official API exists, so class-name sniff is unavoidable here.
        val screenClass = currentScreenClassName().orEmpty()
        if (screenClass.contains("deltaclient", ignoreCase = true)) {
            notes += "DeltaClient battle UI detected"
        }
        if (PanelConfig.enableBattleLog && CobblemonClient.battle != null) {
            notes += "Custom log suppression active"
        }
        return notes
    }
}
