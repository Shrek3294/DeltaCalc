package com.cobblemonextendedbattleui.ui.calc

import com.cobblemonextendedbattleui.UIUtils
import com.cobblemonextendedbattleui.calc.CalcMoveOutcome
import com.cobblemonextendedbattleui.calc.DamageConfidence
import kotlin.math.roundToInt

/**
 * Risk fill style for move outcome damage bars.
 */
enum class CalcRiskFillStyle {
    NONE,
    SOLID,
    HATCHED
}

/**
 * Immutable presentation style for a move outcome.
 */
data class CalcOutcomePresentation(
    val outcome: CalcMoveOutcome,
    val labelColor: Int,
    val barColor: Int,
    val fillStyle: CalcRiskFillStyle,
    val showDamageBar: Boolean
)

/**
 * Presentation style for confidence rails and error states.
 */
data class CalcConfidenceStyle(
    val showRail: Boolean,
    val railColor: Int,
    val isErrorLike: Boolean
)

/**
 * Immutable half-open 2D hit bounds [x, x + width) and [y, y + height).
 * Uses 64-bit Long arithmetic for right/bottom properties to prevent integer overflow.
 */
data class CalcHitBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
) {
    val rightLong: Long get() = x.toLong() + width.toLong()
    val bottomLong: Long get() = y.toLong() + height.toLong()

    val right: Int get() = rightLong.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    val bottom: Int get() = bottomLong.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()

    fun contains(px: Int, py: Int): Boolean = CalcPresentation.containsHalfOpen(px, py, x, y, width, height)
    val isEmpty: Boolean get() = width <= 0 || height <= 0

    companion object {
        val EMPTY = CalcHitBounds(0, 0, 0, 0)
    }
}

/**
 * Layout calculation for an override row (Item, Ability, Spread).
 *
 * Guarantees half-open non-overlap across all regions:
 * `bodyX <= bodyRight <= prevX <= prevRight <= nextX <= nextRight <= pillX <= pillRight <= resetX <= resetRight`
 */
data class CalcOverrideRowLayoutResult(
    val prevBounds: CalcHitBounds,
    val nextBounds: CalcHitBounds,
    val bodyBounds: CalcHitBounds,
    val resetBounds: CalcHitBounds,
    val pillBounds: CalcHitBounds,
    val prevVisible: Boolean,
    val nextVisible: Boolean,
    val resetVisible: Boolean
)

/**
 * Measured header layout allocating title, warning chip, turn text, and chevron.
 */
data class CalcHeaderLayoutResult(
    val title: String,
    val titleX: Int,
    val titleWidth: Int,
    val turnVisible: Boolean,
    val turnX: Int,
    val turnWidth: Int,
    val warningChipVisible: Boolean,
    val warningChipX: Int,
    val warningChipWidth: Int,
    val warningChipIsRed: Boolean,
    val chevronX: Int,
    val chevronWidth: Int
)

/**
 * Measured layout result for compact matchup row (You %, arrow, Opp %).
 */
data class CalcCompactMatchupResult(
    val youText: String,
    val youX: Int,
    val youWidth: Int,
    val arrowText: String,
    val arrowX: Int,
    val arrowWidth: Int,
    val oppText: String,
    val oppX: Int,
    val oppWidth: Int,
    val totalWidth: Int
)

/**
 * Layout helper for compact matchup row.
 */
object CalcCompactMatchupLayout {
    fun calculate(
        availableWidth: Int,
        youPct: Int,
        oppPct: Int,
        arrow: String,
        measurer: (String) -> Int,
        youWord: String = "You",
        oppWord: String = "Opp",
        gap: Int = 4
    ): CalcCompactMatchupResult = CalcPresentation.calculateCompactMatchup(
        availableWidth, youPct, oppPct, arrow, measurer, youWord, oppWord, gap
    )
}

/**
 * Decision helper for panel and content viability.
 */
object CalcContentViability {
    const val MIN_HEADER_CONTENT_WIDTH = 32
    const val MIN_HEADER_HEIGHT = 18
    const val MIN_USABLE_CONTENT_WIDTH = 120
    const val MIN_USABLE_CONTENT_HEIGHT = 60

    fun canRenderHeader(panelWidth: Int, panelHeight: Int, frameInset: Int = 3): Boolean {
        val cellW = panelWidth - frameInset * 2
        val cellH = panelHeight - frameInset * 2
        return cellW >= MIN_HEADER_CONTENT_WIDTH && cellH >= MIN_HEADER_HEIGHT
    }

    fun canRenderContent(
        panelWidth: Int,
        panelHeight: Int,
        isExpanded: Boolean,
        frameInset: Int = 3,
        headerHeight: Int = 18,
        cellGap: Int = 2
    ): Boolean {
        if (!isExpanded) return false
        val cellW = panelWidth - frameInset * 2
        val contentH = panelHeight - frameInset * 2 - headerHeight - cellGap
        return cellW >= MIN_USABLE_CONTENT_WIDTH && contentH >= MIN_USABLE_CONTENT_HEIGHT
    }
}

/**
 * Pure presentation helpers and math for the damage calculator panel.
 */
object CalcPresentation {

    // Palette references matching DamageCalcPanel
    val SEV_OHKO_FG: Int = UIUtils.color(255, 107, 107)
    val SEV_OHKO_BAR: Int = UIUtils.color(229, 62, 62)
    val SEV_LIKELY_OHKO_FG: Int = UIUtils.color(246, 173, 85)
    val SEV_LIKELY_OHKO_BAR: Int = UIUtils.color(221, 107, 32)
    val SEV_2HKO_FG: Int = UIUtils.color(255, 174, 76)
    val SEV_2HKO_BAR: Int = UIUtils.color(237, 137, 54)
    val SEV_3HKO_FG: Int = UIUtils.color(255, 215, 76)
    val SEV_3HKO_BAR: Int = UIUtils.color(214, 158, 46)
    val SEV_WEAK_FG: Int = UIUtils.color(144, 163, 181)
    val SEV_WEAK_BAR: Int = UIUtils.color(74, 85, 104)
    val SEV_STATUS_FG: Int = UIUtils.color(147, 183, 220)
    val SEV_STATUS_BAR: Int = UIUtils.color(90, 122, 154)
    val SEV_IMMUNE_FG: Int = UIUtils.color(113, 128, 150)
    val SEV_IMMUNE_BAR: Int = UIUtils.color(60, 70, 85)
    val SEV_UNSUPPORTED_FG: Int = UIUtils.color(160, 112, 112)
    val SEV_UNSUPPORTED_BAR: Int = UIUtils.color(90, 64, 64)

    val CONF_MED_RAIL: Int = UIUtils.color(246, 224, 94)
    val CONF_LOW_RAIL: Int = UIUtils.color(237, 137, 54)
    val CONF_ERROR_RAIL: Int = UIUtils.color(197, 48, 48)

    /**
     * Exact state pill horizontal padding and total width helpers, ensuring identical
     * rounding between layout allocation and renderer drawing.
     */
    fun pillPaddingX(uiScale: Float): Int = (3 * uiScale).roundToInt().coerceAtLeast(1)
    fun pillTotalWidth(textWidth: Int, uiScale: Float): Int = textWidth + pillPaddingX(uiScale) * 2

    /**
     * Maps a move outcome and supported flag to an explicit visual style.
     * Never shows a damage bar for STATUS or UNSUPPORTED.
     */
    fun styleForOutcome(outcome: CalcMoveOutcome, supported: Boolean = true): CalcOutcomePresentation {
        if (!supported || outcome == CalcMoveOutcome.UNSUPPORTED) {
            return CalcOutcomePresentation(
                outcome = outcome,
                labelColor = SEV_UNSUPPORTED_FG,
                barColor = SEV_UNSUPPORTED_BAR,
                fillStyle = CalcRiskFillStyle.NONE,
                showDamageBar = false
            )
        }
        return when (outcome) {
            CalcMoveOutcome.GUARANTEED_OHKO -> CalcOutcomePresentation(
                outcome = outcome,
                labelColor = SEV_OHKO_FG,
                barColor = SEV_OHKO_BAR,
                fillStyle = CalcRiskFillStyle.SOLID,
                showDamageBar = true
            )
            CalcMoveOutcome.LIKELY_OHKO -> CalcOutcomePresentation(
                outcome = outcome,
                labelColor = SEV_LIKELY_OHKO_FG,
                barColor = SEV_LIKELY_OHKO_BAR,
                fillStyle = CalcRiskFillStyle.HATCHED,
                showDamageBar = true
            )
            CalcMoveOutcome.TWO_HKO -> CalcOutcomePresentation(
                outcome = outcome,
                labelColor = SEV_2HKO_FG,
                barColor = SEV_2HKO_BAR,
                fillStyle = CalcRiskFillStyle.SOLID,
                showDamageBar = true
            )
            CalcMoveOutcome.THREE_HKO -> CalcOutcomePresentation(
                outcome = outcome,
                labelColor = SEV_3HKO_FG,
                barColor = SEV_3HKO_BAR,
                fillStyle = CalcRiskFillStyle.SOLID,
                showDamageBar = true
            )
            CalcMoveOutcome.FOUR_HKO_PLUS -> CalcOutcomePresentation(
                outcome = outcome,
                labelColor = SEV_WEAK_FG,
                barColor = SEV_WEAK_BAR,
                fillStyle = CalcRiskFillStyle.SOLID,
                showDamageBar = true
            )
            CalcMoveOutcome.KO -> CalcOutcomePresentation(
                outcome = outcome,
                labelColor = SEV_OHKO_FG,
                barColor = SEV_OHKO_BAR,
                fillStyle = CalcRiskFillStyle.SOLID,
                showDamageBar = true
            )
            CalcMoveOutcome.IMMUNE -> CalcOutcomePresentation(
                outcome = outcome,
                labelColor = SEV_IMMUNE_FG,
                barColor = SEV_IMMUNE_BAR,
                fillStyle = CalcRiskFillStyle.NONE,
                showDamageBar = true
            )
            CalcMoveOutcome.STATUS -> CalcOutcomePresentation(
                outcome = outcome,
                labelColor = SEV_STATUS_FG,
                barColor = SEV_STATUS_BAR,
                fillStyle = CalcRiskFillStyle.NONE,
                showDamageBar = false
            )
            CalcMoveOutcome.UNSUPPORTED -> CalcOutcomePresentation(
                outcome = outcome,
                labelColor = SEV_UNSUPPORTED_FG,
                barColor = SEV_UNSUPPORTED_BAR,
                fillStyle = CalcRiskFillStyle.NONE,
                showDamageBar = false
            )
        }
    }

    /**
     * Maps confidence level and support status to a 1px rail style.
     */
    fun styleForConfidence(confidence: DamageConfidence, supported: Boolean = true): CalcConfidenceStyle {
        if (!supported) {
            return CalcConfidenceStyle(showRail = true, railColor = CONF_ERROR_RAIL, isErrorLike = true)
        }
        return when (confidence) {
            DamageConfidence.HIGH -> CalcConfidenceStyle(showRail = false, railColor = 0, isErrorLike = false)
            DamageConfidence.MEDIUM -> CalcConfidenceStyle(showRail = true, railColor = CONF_MED_RAIL, isErrorLike = false)
            DamageConfidence.LOW -> CalcConfidenceStyle(showRail = true, railColor = CONF_LOW_RAIL, isErrorLike = false)
        }
    }

    /**
     * Half-open rectangle containment check: [x, x + width) and [y, y + height).
     * Uses 64-bit Long comparisons to prevent integer overflow on extreme bounds.
     * Strictly guards non-positive dimensions (returns false if width <= 0 or height <= 0).
     */
    fun containsHalfOpen(px: Int, py: Int, x: Int, y: Int, width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return false
        val pxL = px.toLong()
        val pyL = py.toLong()
        val xL = x.toLong()
        val yL = y.toLong()
        return pxL >= xL && pxL < xL + width.toLong() && pyL >= yL && pyL < yL + height.toLong()
    }

    /**
     * Computes hit and render regions for an override row (Item, Ability, Spread).
     *
     * Invariants:
     * - Pill is inert and outside next/prev/body/reset bounds.
     * - If !hasAlts, prevBounds and nextBounds are empty; bodyBounds is empty (not cyclable).
     * - If !isOverridden, resetBounds is empty.
     * - All regions are strictly ordered with half-open non-overlapping boundaries.
     */
    fun calculateOverrideRowLayout(
        rowX: Int,
        rowY: Int,
        rowW: Int,
        rowH: Int,
        hasAlts: Boolean,
        isOverridden: Boolean,
        pillWidth: Int,
        uiScale: Float = 1.0f
    ): CalcOverrideRowLayoutResult {
        val safeRowW = if (rowW <= 0) 0 else rowW
        val safeRowH = if (rowH <= 0) 0 else rowH
        if (safeRowW == 0 || safeRowH == 0) {
            return CalcOverrideRowLayoutResult(
                prevBounds = CalcHitBounds.EMPTY,
                nextBounds = CalcHitBounds.EMPTY,
                bodyBounds = CalcHitBounds.EMPTY,
                resetBounds = CalcHitBounds.EMPTY,
                pillBounds = CalcHitBounds.EMPTY,
                prevVisible = false,
                nextVisible = false,
                resetVisible = false
            )
        }

        fun s(n: Int): Int = (n * uiScale).roundToInt().coerceAtLeast(1)
        val rightEdge = rowX + safeRowW

        // 1. Reset control: far right if overridden
        val resetW = if (isOverridden) s(9).coerceAtLeast(8) else 0
        val resetBounds = if (isOverridden && resetW > 0 && rightEdge - resetW >= rowX) {
            CalcHitBounds(rightEdge - resetW, rowY, resetW, safeRowH)
        } else {
            CalcHitBounds.EMPTY
        }
        val spaceLeftOfReset = if (resetBounds.isEmpty) rightEdge else resetBounds.x - 2

        // 2. Pill: placed to the left of reset (inert)
        val safePillW = minOf(pillWidth.coerceAtLeast(0), maxOf(0, spaceLeftOfReset - rowX))
        val pillBounds = if (safePillW > 0) {
            CalcHitBounds(spaceLeftOfReset - safePillW, rowY, safePillW, safeRowH)
        } else {
            CalcHitBounds.EMPTY
        }
        val spaceLeftOfPill = if (pillBounds.isEmpty) spaceLeftOfReset else pillBounds.x - 2

        // 3. Controls (< and >): placed to the left of pill if hasAlts
        val btnW = s(8).coerceAtLeast(7)
        val nextBounds: CalcHitBounds
        val prevBounds: CalcHitBounds
        val spaceLeftOfControls: Int

        if (hasAlts) {
            val nextX = spaceLeftOfPill - btnW
            nextBounds = if (nextX >= rowX) CalcHitBounds(nextX, rowY, btnW, safeRowH) else CalcHitBounds.EMPTY
            val prevX = (nextBounds.x.takeIf { !nextBounds.isEmpty } ?: spaceLeftOfPill) - 2 - btnW
            prevBounds = if (prevX >= rowX) CalcHitBounds(prevX, rowY, btnW, safeRowH) else CalcHitBounds.EMPTY
            spaceLeftOfControls = (prevBounds.x.takeIf { !prevBounds.isEmpty }
                ?: (nextBounds.x.takeIf { !nextBounds.isEmpty } ?: spaceLeftOfPill)) - 2
        } else {
            nextBounds = CalcHitBounds.EMPTY
            prevBounds = CalcHitBounds.EMPTY
            spaceLeftOfControls = spaceLeftOfPill
        }

        // 4. Body: covers from rowX up to spaceLeftOfControls.
        // Actionable for cycling only when hasAlts is true.
        val bodyW = maxOf(0, spaceLeftOfControls - rowX)
        val bodyBounds = if (hasAlts && bodyW > 0) {
            CalcHitBounds(rowX, rowY, bodyW, safeRowH)
        } else {
            CalcHitBounds.EMPTY
        }

        return CalcOverrideRowLayoutResult(
            prevBounds = prevBounds,
            nextBounds = nextBounds,
            bodyBounds = bodyBounds,
            resetBounds = resetBounds,
            pillBounds = pillBounds,
            prevVisible = !prevBounds.isEmpty,
            nextVisible = !nextBounds.isEmpty,
            resetVisible = !resetBounds.isEmpty
        )
    }

    /**
     * Allocates header right cluster (warning chip, turn text, chevron) and assigns
     * remaining width to the title. Guarantees every visible region is clamped in [0, availableWidth]
     * with zero overlapping fields across all widths >= 0.
     */
    fun calculateHeaderLayout(
        availableWidth: Int,
        tier: CalcMoveRowTier,
        warningCount: Int,
        hasLowOrUnsupported: Boolean,
        turn: Int,
        isExpanded: Boolean,
        measurer: (String) -> Int,
        fullTitle: String = "DAMAGE CALC",
        shortTitle: String = "CALC",
        turnText: String = "T$turn",
        warningChipText: String = "!$warningCount",
        chevronExpandedText: String = "\u25BE",
        chevronCollapsedText: String = "\u25B8"
    ): CalcHeaderLayoutResult {
        val safeW = if (availableWidth <= 0) 0 else availableWidth
        if (safeW < CalcContentViability.MIN_HEADER_CONTENT_WIDTH) {
            return CalcHeaderLayoutResult(
                title = "",
                titleX = 0,
                titleWidth = 0,
                turnVisible = false,
                turnX = 0,
                turnWidth = 0,
                warningChipVisible = false,
                warningChipX = 0,
                warningChipWidth = 0,
                warningChipIsRed = false,
                chevronX = 0,
                chevronWidth = 0
            )
        }

        val chevronText = if (isExpanded) chevronExpandedText else chevronCollapsedText
        val chevronMargin = if (safeW >= 32) 4 else 2
        val chevronW = minOf(safeW - chevronMargin, maxOf(4, measurer(chevronText)))
        val chevronX = maxOf(0, safeW - chevronMargin - chevronW)
        val chevronGap = if (safeW >= 32) 4 else 2
        val spaceLeftOfChevron = maxOf(0, chevronX - chevronGap)

        // Warning chip: prioritized over turn text and title
        val warningChipVisible: Boolean
        val chipW: Int
        val chipX: Int
        val spaceLeftOfChip: Int

        if (warningCount > 0) {
            val minChipW = 8
            if (spaceLeftOfChevron >= minChipW) {
                warningChipVisible = true
                val chipPad = if (safeW >= 40) 4 else 2
                val desiredChipW = measurer(warningChipText) + chipPad * 2
                chipW = minOf(spaceLeftOfChevron, desiredChipW)
                chipX = spaceLeftOfChevron - chipW
                val chipGap = if (chipX >= 2) 2 else 0
                spaceLeftOfChip = maxOf(0, chipX - chipGap)
            } else {
                warningChipVisible = false
                chipW = 0
                chipX = 0
                spaceLeftOfChip = spaceLeftOfChevron
            }
        } else {
            warningChipVisible = false
            chipW = 0
            chipX = 0
            spaceLeftOfChip = spaceLeftOfChevron
        }

        val titleX = if (safeW >= 40) 13 else 6
        val effectiveTitle = if (tier == CalcMoveRowTier.TINY) shortTitle else fullTitle
        val turnW = measurer(turnText)
        val minTitleW = if (safeW >= 40) 16 else 8

        // Drop turn text before warning chip if space is too tight
        val spaceAvailableForTurnAndTitle = spaceLeftOfChip - titleX
        val canFitTurn = spaceAvailableForTurnAndTitle >= (turnW + 4 + minTitleW)

        val turnVisible: Boolean
        val turnX: Int
        val turnAllocatedW: Int
        val spaceForTitle: Int

        if (canFitTurn) {
            turnVisible = true
            turnAllocatedW = turnW
            turnX = spaceLeftOfChip - turnAllocatedW
            spaceForTitle = maxOf(0, turnX - 4 - titleX)
        } else {
            turnVisible = false
            turnAllocatedW = 0
            turnX = 0
            spaceForTitle = maxOf(0, spaceLeftOfChip - titleX)
        }

        val titleW = minOf(spaceForTitle, measurer(effectiveTitle))
        val safeTitleX = if (titleW > 0) titleX else 0

        return CalcHeaderLayoutResult(
            title = effectiveTitle,
            titleX = safeTitleX,
            titleWidth = titleW,
            turnVisible = turnVisible,
            turnX = turnX,
            turnWidth = turnAllocatedW,
            warningChipVisible = warningChipVisible,
            warningChipX = chipX,
            warningChipWidth = chipW,
            warningChipIsRed = hasLowOrUnsupported,
            chevronX = chevronX,
            chevronWidth = chevronW
        )
    }

    /**
     * Allocates compact matchup items (You %, arrow, Opp %) within available content width.
     * Drops words before percentages/arrow when space is tight, and provides bounded widths.
     */
    fun calculateCompactMatchup(
        availableWidth: Int,
        youPct: Int,
        oppPct: Int,
        arrow: String,
        measurer: (String) -> Int,
        youWord: String = "You",
        oppWord: String = "Opp",
        gap: Int = 4
    ): CalcCompactMatchupResult {
        val safeW = if (availableWidth <= 0) 0 else availableWidth
        if (safeW == 0) {
            return CalcCompactMatchupResult("", 0, 0, "", 0, 0, "", 0, 0, 0)
        }

        val arrowW = measurer(arrow)
        val fullYou = "$youWord $youPct%"
        val fullOpp = "$oppWord $oppPct%"
        val fullYouW = measurer(fullYou)
        val fullOppW = measurer(fullOpp)
        val neededFull = fullYouW + gap + arrowW + gap + fullOppW

        val useFullWords = safeW >= neededFull
        val youLabel = if (useFullWords) fullYou else "$youPct%"
        val oppLabel = if (useFullWords) fullOpp else "$oppPct%"
        val youW = measurer(youLabel)
        val oppW = measurer(oppLabel)
        val neededShort = youW + gap + arrowW + gap + oppW

        if (safeW >= neededShort) {
            val youX = 0
            val arrowX = youW + gap
            val oppX = arrowX + arrowW + gap
            return CalcCompactMatchupResult(
                youText = youLabel,
                youX = youX,
                youWidth = youW,
                arrowText = arrow,
                arrowX = arrowX,
                arrowWidth = arrowW,
                oppText = oppLabel,
                oppX = oppX,
                oppWidth = oppW,
                totalWidth = oppX + oppW
            )
        }

        // Extremely cramped: allocate arrow first, distribute remaining width
        val safeArrowW = minOf(safeW, arrowW)
        val remaining = maxOf(0, safeW - safeArrowW - gap * 2)
        val halfW = remaining / 2
        val youAllocatedW = minOf(youW, halfW)
        val oppAllocatedW = minOf(oppW, remaining - youAllocatedW)

        val youX = 0
        val arrowX = youAllocatedW + if (youAllocatedW > 0) gap else 0
        val oppX = arrowX + safeArrowW + if (safeArrowW > 0) gap else 0

        return CalcCompactMatchupResult(
            youText = youLabel,
            youX = youX,
            youWidth = youAllocatedW,
            arrowText = arrow,
            arrowX = arrowX,
            arrowWidth = safeArrowW,
            oppText = oppLabel,
            oppX = oppX,
            oppWidth = oppAllocatedW,
            totalWidth = minOf(safeW, oppX + oppAllocatedW)
        )
    }
}
