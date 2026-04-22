package com.cobblemonextendedbattleui.ui.calc

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.io.File

object CalcPanelState {
    const val MIN_FONT_SCALE = 0.35f
    const val MAX_FONT_SCALE = 1.6f
    const val FONT_SCALE_STEP = 0.05f

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configFile: File by lazy {
        FabricLoader.getInstance().configDir.resolve("deltacalc-calc-panel.json").toFile()
    }

    var enabled: Boolean = true
        private set
    var x: Int? = null
        private set
    var y: Int? = null
        private set
    var width: Int? = null
        private set
    var height: Int? = null
        private set
    var expanded: Boolean = true
        private set
    var summaryExpanded: Boolean = true
        private set
    var teamSectionCollapsed: Boolean = false
        private set
    var movesSectionCollapsed: Boolean = false
        private set
    var fontScale: Float = 1.0f
        private set

    data class ConfigData(
        val enabled: Boolean = true,
        val x: Int? = null,
        val y: Int? = null,
        val width: Int? = null,
        val height: Int? = null,
        val expanded: Boolean = true,
        val summaryExpanded: Boolean = true,
        val teamSectionCollapsed: Boolean = false,
        val movesSectionCollapsed: Boolean = false,
        val fontScale: Float = 1.0f
    )

    fun load() {
        if (!configFile.exists()) return
        runCatching {
            val data = gson.fromJson(configFile.readText(), ConfigData::class.java)
            enabled = data.enabled
            x = data.x
            y = data.y
            width = data.width
            height = data.height
            expanded = data.expanded
            summaryExpanded = data.summaryExpanded
            teamSectionCollapsed = data.teamSectionCollapsed
            movesSectionCollapsed = data.movesSectionCollapsed
            fontScale = data.fontScale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
        }
    }

    fun save() {
        runCatching {
            configFile.parentFile?.mkdirs()
            configFile.writeText(
                gson.toJson(
                    ConfigData(
                        enabled = enabled,
                        x = x,
                        y = y,
                        width = width,
                        height = height,
                        expanded = expanded,
                        summaryExpanded = summaryExpanded,
                        teamSectionCollapsed = teamSectionCollapsed,
                        movesSectionCollapsed = movesSectionCollapsed,
                        fontScale = fontScale
                    )
                )
            )
        }
    }

    fun setPosition(nextX: Int, nextY: Int) {
        x = nextX
        y = nextY
    }

    fun setDimensions(nextWidth: Int, nextHeight: Int) {
        width = nextWidth
        height = nextHeight
    }

    fun toggleExpanded() {
        expanded = !expanded
    }

    fun toggleSummaryExpanded() {
        summaryExpanded = !summaryExpanded
    }

    fun toggleTeamSectionCollapsed() {
        teamSectionCollapsed = !teamSectionCollapsed
    }

    fun toggleMovesSectionCollapsed() {
        movesSectionCollapsed = !movesSectionCollapsed
    }

    fun adjustFontScale(delta: Float) {
        fontScale = (fontScale + delta).coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
    }

    fun setFontScale(value: Float) {
        fontScale = value.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
    }
}
