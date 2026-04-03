package com.cobblemonextendedbattleui.pokemon.tooltip

import com.cobblemonextendedbattleui.UIUtils

/**
 * Shared color constants and settings for tooltip rendering.
 * Used by both TeamIndicatorUI and PokemonInfoPopup.
 */
object TooltipConstants {
    val TOOLTIP_BG = color(22, 27, 34, 245)
    val TOOLTIP_BORDER = color(55, 65, 80, 255)
    val TOOLTIP_TEXT = color(220, 225, 230, 255)
    val TOOLTIP_HEADER = color(255, 255, 255, 255)
    val TOOLTIP_LABEL = color(140, 150, 165, 255)
    val TOOLTIP_DIM = color(100, 110, 120, 255)
    val TOOLTIP_HP_HIGH = color(100, 220, 100, 255)
    val TOOLTIP_HP_MED = color(220, 180, 50, 255)
    val TOOLTIP_HP_LOW = color(220, 80, 80, 255)
    val TOOLTIP_STAT_BOOST = color(100, 200, 100, 255)
    val TOOLTIP_STAT_DROP = color(200, 100, 100, 255)
    const val TOOLTIP_PADDING = 6
    const val TOOLTIP_CORNER = 3
    const val TOOLTIP_BASE_LINE_HEIGHT = 10
    const val TOOLTIP_FONT_SCALE = 0.85f
    val TOOLTIP_SPEED = color(150, 180, 220, 255)
    val TOOLTIP_DEFENSE = color(150, 220, 180, 255)
    val TOOLTIP_SPECIAL_DEFENSE = color(180, 150, 220, 255)
    val TOOLTIP_ATTACK = color(220, 150, 150, 255)
    val TOOLTIP_SPECIAL_ATTACK = color(220, 180, 150, 255)
    val TOOLTIP_PP = color(255, 210, 80, 255)
    val TOOLTIP_PP_LOW = color(255, 100, 80, 255)
    val TOOLTIP_ABILITY = color(200, 180, 255, 255)
    val TOOLTIP_ABILITY_POSSIBLE = color(160, 150, 180, 255)

    private fun color(r: Int, g: Int, b: Int, a: Int = 255): Int = UIUtils.color(r, g, b, a)
}
