package com.cobblemonextendedbattleui.ui.calc

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.io.File

object CalcPanelState {
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
            fontScale = data.fontScale.coerceIn(0.7f, 1.6f)
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

    fun adjustFontScale(delta: Float) {
        fontScale = (fontScale + delta).coerceIn(0.7f, 1.6f)
    }
}
