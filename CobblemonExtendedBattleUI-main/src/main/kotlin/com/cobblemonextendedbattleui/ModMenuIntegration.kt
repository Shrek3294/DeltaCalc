package com.cobblemonextendedbattleui

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import me.shedaniel.clothconfig2.api.ConfigBuilder
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text

/**
 * Mod Menu integration for config screen.
 * This class is only loaded when Mod Menu is present (registered as modmenu entrypoint).
 */
class ModMenuIntegration : ModMenuApi {

    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> {
        return ConfigScreenFactory { parent -> createConfigScreen(parent) }
    }

    private fun createConfigScreen(parent: Screen): Screen {
        val builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Text.translatable("cobblemonextendedbattleui.config.title"))
            .setSavingRunnable { PanelConfig.save() }

        val general = builder.getOrCreateCategory(Text.translatable("cobblemonextendedbattleui.config.category.features"))
        val entryBuilder = builder.entryBuilder()

        // Team Indicators toggle
        general.addEntry(
            entryBuilder.startBooleanToggle(
                Text.translatable("cobblemonextendedbattleui.config.enableTeamIndicators"),
                PanelConfig.enableTeamIndicators
            )
                .setDefaultValue(true)
                .setTooltip(Text.translatable("cobblemonextendedbattleui.config.enableTeamIndicators.tooltip"))
                .setSaveConsumer { value -> PanelConfig.setEnableTeamIndicators(value) }
                .build()
        )

        // Team Indicator Repositioning toggle
        general.addEntry(
            entryBuilder.startBooleanToggle(
                Text.translatable("cobblemonextendedbattleui.config.teamIndicatorRepositioning"),
                PanelConfig.teamIndicatorRepositioningEnabled
            )
                .setDefaultValue(true)
                .setTooltip(Text.translatable("cobblemonextendedbattleui.config.teamIndicatorRepositioning.tooltip"))
                .setSaveConsumer { value -> PanelConfig.setTeamIndicatorRepositioningEnabled(value) }
                .build()
        )

        // Battle Info Panel toggle
        general.addEntry(
            entryBuilder.startBooleanToggle(
                Text.translatable("cobblemonextendedbattleui.config.enableBattleInfoPanel"),
                PanelConfig.enableBattleInfoPanel
            )
                .setDefaultValue(true)
                .setTooltip(Text.translatable("cobblemonextendedbattleui.config.enableBattleInfoPanel.tooltip"))
                .setSaveConsumer { value -> PanelConfig.setEnableBattleInfoPanel(value) }
                .build()
        )

        // Battle Log toggle
        general.addEntry(
            entryBuilder.startBooleanToggle(
                Text.translatable("cobblemonextendedbattleui.config.enableBattleLog"),
                PanelConfig.enableBattleLog
            )
                .setDefaultValue(true)
                .setTooltip(Text.translatable("cobblemonextendedbattleui.config.enableBattleLog.tooltip"))
                .setSaveConsumer { value -> PanelConfig.setEnableBattleLog(value) }
                .build()
        )

        // Move Tooltips toggle
        general.addEntry(
            entryBuilder.startBooleanToggle(
                Text.translatable("cobblemonextendedbattleui.config.enableMoveTooltips"),
                PanelConfig.enableMoveTooltips
            )
                .setDefaultValue(true)
                .setTooltip(Text.translatable("cobblemonextendedbattleui.config.enableMoveTooltips.tooltip"))
                .setSaveConsumer { value -> PanelConfig.setEnableMoveTooltips(value) }
                .build()
        )

        // Tooltip Display Options category
        val tooltipOptions = builder.getOrCreateCategory(Text.translatable("cobblemonextendedbattleui.config.category.tooltipOptions"))

        // Show Tera Type toggle
        tooltipOptions.addEntry(
            entryBuilder.startBooleanToggle(
                Text.translatable("cobblemonextendedbattleui.config.showTeraType"),
                PanelConfig.showTeraType
            )
                // Disabled by default due to noisiness
                .setDefaultValue(false)
                .setTooltip(Text.translatable("cobblemonextendedbattleui.config.showTeraType.tooltip"))
                .setSaveConsumer { value -> PanelConfig.setShowTeraType(value) }
                .build()
        )

        // Show Stat Ranges toggle
        tooltipOptions.addEntry(
            entryBuilder.startBooleanToggle(
                Text.translatable("cobblemonextendedbattleui.config.showStatRanges"),
                PanelConfig.showStatRanges
            )
                .setDefaultValue(true)
                .setTooltip(Text.translatable("cobblemonextendedbattleui.config.showStatRanges.tooltip"))
                .setSaveConsumer { value -> PanelConfig.setShowStatRanges(value) }
                .build()
        )

        // Show Base Crit Rate toggle
        tooltipOptions.addEntry(
            entryBuilder.startBooleanToggle(
                Text.translatable("cobblemonextendedbattleui.config.showBaseCritRate"),
                PanelConfig.showBaseCritRate
            )
                // Disabled by default due to noisiness
                .setDefaultValue(false)
                .setTooltip(Text.translatable("cobblemonextendedbattleui.config.showBaseCritRate.tooltip"))
                .setSaveConsumer { value -> PanelConfig.setShowBaseCritRate(value) }
                .build()
        )

        return builder.build()
    }
}
