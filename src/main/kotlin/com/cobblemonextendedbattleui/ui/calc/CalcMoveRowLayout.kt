package com.cobblemonextendedbattleui.ui.calc

import com.cobblemonextendedbattleui.calc.CalcMoveRow

/**
 * Responsive layout tiers for the calculator move rows, determined purely by available
 * panel content width (independent of whole-screen viewport size).
 */
enum class CalcMoveRowTier {
    TINY,
    COMPACT,
    WIDE;

    companion object {
        const val TINY_MAX_WIDTH = 149
        const val COMPACT_MAX_WIDTH = 209

        fun fromWidth(contentWidth: Int): CalcMoveRowTier = when {
            contentWidth < 150 -> TINY
            contentWidth < 210 -> COMPACT
            else -> WIDE
        }
    }
}

/**
 * Lightweight text measurer interface for decoupling layout computation from
 * Minecraft's [net.minecraft.client.font.TextRenderer].
 */
fun interface CalcTextMeasurer {
    fun measureWidth(text: String): Int
}

/**
 * Immutable result of a measured move-row layout computation.
 *
 * All coordinates are relative to the content origin (0):
 * - [moveNameWidth], [damageWidth], and [koWidth] are all non-negative (>= 0).
 * - Regions never overlap or order-invert:
 *   `0 <= moveNameX <= moveNameRight <= damageX <= damageRight <= koX <= koRight <= availableWidth`.
 * - For widths of 0 or negative inputs, all widths and offsets are 0 and no exceptions are thrown.
 */
data class CalcMoveRowLayoutResult(
    val tier: CalcMoveRowTier,
    val availableWidth: Int,
    val moveNameX: Int,
    val moveNameWidth: Int,
    val damageX: Int,
    val damageWidth: Int,
    val damageVisible: Boolean,
    val koX: Int,
    val koWidth: Int,
    val koVisible: Boolean
) {
    val moveNameRight: Int get() = moveNameX + moveNameWidth
    val damageRight: Int get() = damageX + damageWidth
    val koRight: Int get() = koX + koWidth
}

/**
 * Pure, Minecraft-free measured move-row layout utility.
 *
 * Allocates column offsets and widths within an available content width following
 * the strict priority:
 * 1. Reserve the KO label first at the right edge.
 * 2. Reserve damage text second (left of the KO label) when it fits alongside a minimum
 *    move-name allocation.
 * 3. Give all remaining width to a non-negative move-name region on the left.
 *
 * Hardened against negative widths, tiny widths, large font measurements, and extreme Int inputs.
 */
object CalcMoveRowLayout {
    const val DEFAULT_GAP = 4
    const val DEFAULT_MIN_NAME_WIDTH_WIDE = 40
    const val DEFAULT_MIN_NAME_WIDTH_COMPACT = 28
    const val DEFAULT_MIN_NAME_WIDTH_TINY = 20

    /**
     * Computes the move row layout from pre-measured pixel widths of right-side fields.
     */
    fun calculate(
        availableWidth: Int,
        measuredDamageWidth: Int,
        measuredKoWidth: Int,
        gap: Int = DEFAULT_GAP,
        minMoveNameWidth: Int? = null
    ): CalcMoveRowLayoutResult {
        val safeAvailableWidth = if (availableWidth <= 0) 0 else availableWidth
        val tier = CalcMoveRowTier.fromWidth(availableWidth)
        val safeGap = if (gap < 0) 0 else gap

        if (safeAvailableWidth == 0) {
            return CalcMoveRowLayoutResult(
                tier = tier,
                availableWidth = 0,
                moveNameX = 0,
                moveNameWidth = 0,
                damageX = 0,
                damageWidth = 0,
                damageVisible = false,
                koX = 0,
                koWidth = 0,
                koVisible = false
            )
        }

        val safeMeasuredKo = if (measuredKoWidth <= 0) 0 else measuredKoWidth
        val safeMeasuredDamage = if (measuredDamageWidth <= 0) 0 else measuredDamageWidth

        val effectiveMinNameWidth = minMoveNameWidth?.let { if (it < 0) 0 else it } ?: when (tier) {
            CalcMoveRowTier.WIDE -> DEFAULT_MIN_NAME_WIDTH_WIDE
            CalcMoveRowTier.COMPACT -> DEFAULT_MIN_NAME_WIDTH_COMPACT
            CalcMoveRowTier.TINY -> DEFAULT_MIN_NAME_WIDTH_TINY
        }

        // 1. Reserve KO label first (at right edge)
        val koWidth = minOf(safeAvailableWidth, safeMeasuredKo)
        val koVisible = koWidth > 0
        val koX = safeAvailableWidth - koWidth
        val spaceLeftOfKo = koX

        val gapBeforeKo = if (koVisible && spaceLeftOfKo > 0) minOf(safeGap, spaceLeftOfKo) else 0
        val spaceForNameAndDamage = spaceLeftOfKo - gapBeforeKo

        // 2. Reserve damage text second when it fits
        val neededForDamageAndName = safeMeasuredDamage.toLong() +
            effectiveMinNameWidth.toLong() +
            (if (effectiveMinNameWidth > 0 && safeMeasuredDamage > 0) safeGap.toLong() else 0L)
        val damageFits = safeMeasuredDamage > 0 && spaceForNameAndDamage.toLong() >= neededForDamageAndName

        val damageVisible: Boolean
        val damageWidth: Int
        val damageX: Int
        val moveNameWidth: Int
        val moveNameX = 0

        if (damageFits) {
            damageVisible = true
            damageWidth = minOf(spaceForNameAndDamage, safeMeasuredDamage)
            damageX = koX - gapBeforeKo - damageWidth
            val spaceLeftOfDamage = damageX
            val gapBeforeDamage = if (spaceLeftOfDamage > 0) minOf(safeGap, spaceLeftOfDamage) else 0
            moveNameWidth = maxOf(0, spaceLeftOfDamage - gapBeforeDamage)
        } else {
            damageVisible = false
            damageWidth = 0
            damageX = koX - gapBeforeKo
            moveNameWidth = maxOf(0, spaceForNameAndDamage)
        }

        return CalcMoveRowLayoutResult(
            tier = tier,
            availableWidth = safeAvailableWidth,
            moveNameX = moveNameX,
            moveNameWidth = moveNameWidth,
            damageX = damageX,
            damageWidth = damageWidth,
            damageVisible = damageVisible,
            koX = koX,
            koWidth = koWidth,
            koVisible = koVisible
        )
    }

    /**
     * Computes the move row layout using a text measurer for right-side texts.
     */
    fun calculate(
        availableWidth: Int,
        damageText: String,
        koText: String,
        measurer: CalcTextMeasurer,
        gap: Int = DEFAULT_GAP,
        minMoveNameWidth: Int? = null
    ): CalcMoveRowLayoutResult = calculate(
        availableWidth = availableWidth,
        measuredDamageWidth = measurer.measureWidth(damageText),
        measuredKoWidth = measurer.measureWidth(koText),
        gap = gap,
        minMoveNameWidth = minMoveNameWidth
    )

    /**
     * Computes the move row layout for a [CalcMoveRow] using a text measurer.
     */
    fun calculate(
        availableWidth: Int,
        row: CalcMoveRow,
        measurer: CalcTextMeasurer,
        gap: Int = DEFAULT_GAP,
        minMoveNameWidth: Int? = null
    ): CalcMoveRowLayoutResult = calculate(
        availableWidth = availableWidth,
        damageText = row.damageText,
        koText = row.koText,
        measurer = measurer,
        gap = gap,
        minMoveNameWidth = minMoveNameWidth
    )
}
