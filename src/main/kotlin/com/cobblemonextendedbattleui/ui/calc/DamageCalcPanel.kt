package com.cobblemonextendedbattleui.ui.calc

import com.cobblemonextendedbattleui.UIUtils
import com.cobblemonextendedbattleui.calc.CalcComputationService
import com.cobblemonextendedbattleui.calc.CalcMoveOutcome
import com.cobblemonextendedbattleui.calc.CalcMoveRow
import com.cobblemonextendedbattleui.calc.CalcPokemonSnapshot
import com.cobblemonextendedbattleui.calc.CalcPreviewTab
import com.cobblemonextendedbattleui.calc.CalcRenderModel
import com.cobblemonextendedbattleui.calc.DamageConfidence
import com.cobblemonextendedbattleui.calc.InferenceValueState
import com.cobblemonextendedbattleui.calc.OverrideRow
import com.cobblemonextendedbattleui.compat.delta.DeltaBattlePlatformAdapter
import com.cobblemonextendedbattleui.ui.shared.ResponsiveGeometry
import com.cobblemonextendedbattleui.ui.shared.ScrollbarRenderer
import com.cobblemonextendedbattleui.ui.shared.WidgetInteractionHandler
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.roundToInt

object DamageCalcPanel {
    private const val DEFAULT_WIDTH = 228
    private const val DEFAULT_HEIGHT = 310
    private const val COLLAPSED_HEIGHT = 28
    private const val RESIZE_HANDLE = 6
    private const val HEADER_HEIGHT = 18
    private const val BASE_FONT_SCALE = 0.7f
    private const val TAB_HEIGHT = 13
    private const val TAB_GAP = 3
    private const val TAB_TEXT_SCALE_OFFSET = 0.18f
    private const val TAB_TEXT_SCALE_MIN = 0.42f
    private const val TAB_MIN_WIDTH = 20

    // V2 palette (muted navy calc look).
    private val V2_TEXT = UIUtils.color(226, 232, 240)
    private val V2_TEXT_DIM = UIUtils.color(160, 174, 192)
    private val V2_TEXT_LABEL = UIUtils.color(113, 128, 150)
    private val V2_ACCENT_GREEN = UIUtils.color(104, 211, 145)
    private val V2_ACCENT_YELLOW = UIUtils.color(246, 224, 94)
    private val V2_ACCENT_BLUE = UIUtils.color(147, 183, 220)

    // HP bar palette.
    private val HP_GOOD = UIUtils.color(72, 187, 120)
    private val HP_MID = UIUtils.color(236, 201, 75)
    private val HP_LOW = UIUtils.color(245, 101, 101)
    private val HP_TRACK = UIUtils.color(10, 15, 22)

    // State pill palette.
    private val PILL_SEEN_BG = UIUtils.color(56, 161, 105, 60)
    private val PILL_LIKELY_BG = UIUtils.color(214, 158, 46, 60)
    private val PILL_UNK_BG = UIUtils.color(113, 128, 150, 60)
    private val PILL_MANUAL_BG = UIUtils.color(56, 109, 161, 60)

    private val interaction = WidgetInteractionHandler(UIUtils.ActivePanel.DAMAGE_CALC).apply {
        dragThreshold = 4
    }

    private val contentScrollbar = ScrollbarRenderer(
        trackWidth = 3,
        bgColor = UIUtils.color(40, 50, 65, 120),
        thumbColor = UIUtils.color(140, 160, 180, 200),
        thumbHoverColor = UIUtils.color(180, 200, 220, 220)
    )

    // Scroll state for the moves + team viewport. Cached content height /
    // viewport bounds let the wheel handler clamp without re-rendering.
    private var contentScrollOffset: Int = 0
    private var lastContentHeight: Int = 0
    private var lastContentViewportTop: Int = 0
    private var lastContentViewportBottom: Int = 0
    // Scrollbar thumb drag state.
    private var isScrollbarDragging = false
    private var scrollDragStartMouseY = 0
    private var scrollDragStartOffset = 0

    private var wasMouseDown = false
    private var wasRightMouseDown = false
    private var lastBounds = intArrayOf(0, 0, 0, 0)
    private var lastTeamHeaderBounds = CalcHitBounds.EMPTY
    private var lastSummaryBounds = CalcHitBounds.EMPTY
    private var lastMovesHeaderBounds = CalcHitBounds.EMPTY
    private var lastItemPrevBounds = CalcHitBounds.EMPTY
    private var lastItemNextBounds = CalcHitBounds.EMPTY
    private var lastItemBodyBounds = CalcHitBounds.EMPTY
    private var lastItemResetBounds = CalcHitBounds.EMPTY
    private var lastAbilityPrevBounds = CalcHitBounds.EMPTY
    private var lastAbilityNextBounds = CalcHitBounds.EMPTY
    private var lastAbilityBodyBounds = CalcHitBounds.EMPTY
    private var lastAbilityResetBounds = CalcHitBounds.EMPTY
    private var lastSpreadPrevBounds = CalcHitBounds.EMPTY
    private var lastSpreadNextBounds = CalcHitBounds.EMPTY
    private var lastSpreadBodyBounds = CalcHitBounds.EMPTY
    private var lastSpreadResetBounds = CalcHitBounds.EMPTY
    private var lastPlayerTabBounds = emptyList<TabBounds>()
    private var lastOpponentTabBounds = emptyList<TabBounds>()
    private var summaryClickArmed = false
    private var pendingTabSelection: PendingTabSelection? = null
    private var pendingSectionToggle: SectionToggle? = null
    private var armedOverride: ArmedOverride? = null

    private enum class OverrideAction {
        PREV,
        NEXT,
        RESET
    }

    private data class ArmedOverride(
        val row: OverrideRow,
        val action: OverrideAction
    )

    private fun tr(key: String, vararg args: Any): String {
        return if (args.isEmpty()) Text.translatable(key).string
        else Text.translatable(key, *args).string
    }

    fun initialize() {
        CalcPanelState.load()
    }

    fun render(context: DrawContext) {
        if (!CalcPanelState.enabled || !DeltaBattlePlatformAdapter.isBattleActive()) {
            interaction.releaseAll()
            wasMouseDown = false
            wasRightMouseDown = false
            pendingTabSelection = null
            pendingSectionToggle = null
            armedOverride = null
            isScrollbarDragging = false
            lastContentViewportTop = 0
            lastContentViewportBottom = 0
            lastContentHeight = 0
            contentScrollOffset = 0
            return
        }

        val mc = MinecraftClient.getInstance()
        val model = CalcComputationService.currentModel() ?: return
        val screenWidth = mc.window.scaledWidth
        val screenHeight = mc.window.scaledHeight

        val minWidth = 180
        val minHeight = COLLAPSED_HEIGHT
        val maxWidth = minOf(screenWidth, maxOf(minWidth, (screenWidth * 0.65f).toInt()))
        val maxHeight = minOf(screenHeight, maxOf(minHeight, (screenHeight * 0.8f).toInt()))

        val requestedHeight = if (CalcPanelState.expanded) CalcPanelState.height else COLLAPSED_HEIGHT
        val defaultHeight = if (CalcPanelState.expanded) DEFAULT_HEIGHT else COLLAPSED_HEIGHT

        val bounds = ResponsiveGeometry.reconcile(
            x = CalcPanelState.x,
            y = CalcPanelState.y,
            width = CalcPanelState.width,
            height = requestedHeight,
            viewportWidth = screenWidth,
            viewportHeight = screenHeight,
            defaultWidth = DEFAULT_WIDTH,
            defaultHeight = defaultHeight,
            minWidth = minWidth,
            minHeight = minHeight,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            defaultX = { resolvedWidth, vw -> vw - resolvedWidth - 14 },
            defaultY = { _, _ -> 100 }
        )

        // Strict viability checks using CalcContentViability
        if (!CalcContentViability.canRenderHeader(bounds.width, bounds.height, UIUtils.FRAME_INSET)) {
            interaction.releaseAll()
            wasMouseDown = false
            wasRightMouseDown = false
            pendingTabSelection = null
            pendingSectionToggle = null
            armedOverride = null
            isScrollbarDragging = false
            lastContentViewportTop = 0
            lastContentViewportBottom = 0
            lastContentHeight = 0
            contentScrollOffset = 0
            return
        }

        val canRenderContent = CalcContentViability.canRenderContent(
            panelWidth = bounds.width,
            panelHeight = bounds.height,
            isExpanded = CalcPanelState.expanded,
            frameInset = UIUtils.FRAME_INSET,
            headerHeight = HEADER_HEIGHT,
            cellGap = UIUtils.CELL_GAP
        )

        val x = bounds.x
        val y = bounds.y
        val width = bounds.width
        val height = bounds.height

        lastBounds = intArrayOf(x, y, width, height)

        val mouseX = (mc.mouse.x * mc.window.scaledWidth / mc.window.width).toInt()
        val mouseY = (mc.mouse.y * mc.window.scaledHeight / mc.window.height).toInt()
        handleInput(mc, x, y, width, height, model)

        UIUtils.renderPopupFrame(context, x, y, width, height)
        val cellX = x + UIUtils.FRAME_INSET
        val cellY = y + UIUtils.FRAME_INSET
        val cellW = width - UIUtils.FRAME_INSET * 2

        // Determine content tier purely from actual content width after insets and scrollbar
        val moveRowAvailableWidth = (cellW - 12).coerceAtLeast(0)
        val panelTier = CalcMoveRowTier.fromWidth(moveRowAvailableWidth)
        val compact = panelTier != CalcMoveRowTier.WIDE

        // Header layout computation
        val trFont = MinecraftClient.getInstance().textRenderer
        val measurer = { text: String -> (trFont.getWidth(text) * BASE_FONT_SCALE).roundToInt() }
        val hasLowOrUnsupported = model.yourMoves.any { it.confidence == DamageConfidence.LOW || !it.supported || it.outcome == CalcMoveOutcome.UNSUPPORTED } ||
            model.opponentMoves.any { it.confidence == DamageConfidence.LOW || !it.supported || it.outcome == CalcMoveOutcome.UNSUPPORTED }

        val headerLayout = CalcPresentation.calculateHeaderLayout(
            availableWidth = cellW,
            tier = panelTier,
            warningCount = model.warningTexts.size,
            hasLowOrUnsupported = hasLowOrUnsupported,
            turn = model.snapshot.turn,
            isExpanded = canRenderContent,
            measurer = measurer,
            fullTitle = tr("deltacalc.calc.title"),
            shortTitle = tr("deltacalc.calc.title.short"),
            turnText = tr("deltacalc.calc.turn", model.snapshot.turn),
            warningChipText = tr("deltacalc.calc.warning.chip", model.warningTexts.size),
            chevronExpandedText = "\u25BE",
            chevronCollapsedText = "\u25B8"
        )

        UIUtils.drawPopupCell(context, cellX, cellY, cellW, HEADER_HEIGHT)

        // Live-status dot
        if (cellW >= 16) {
            context.fill(cellX + 6, cellY + 8, cellX + 9, cellY + 11, V2_ACCENT_GREEN)
        }

        // Title
        if (headerLayout.titleWidth > 0) {
            val safeTitle = truncateToWidth(headerLayout.title, headerLayout.titleWidth, BASE_FONT_SCALE)
            if (safeTitle.isNotEmpty()) {
                UIUtils.drawText(context, safeTitle, (cellX + headerLayout.titleX).toFloat(), (cellY + 5).toFloat(), V2_TEXT, BASE_FONT_SCALE)
            }
        }

        // Turn text (dropped before warning chip when space is tight)
        if (headerLayout.turnVisible && headerLayout.turnWidth > 0) {
            UIUtils.drawText(context, tr("deltacalc.calc.turn", model.snapshot.turn), (cellX + headerLayout.turnX).toFloat(), (cellY + 5).toFloat(), V2_TEXT_DIM, BASE_FONT_SCALE)
        }

        // Warning chip !N
        if (headerLayout.warningChipVisible && headerLayout.warningChipWidth > 0) {
            val chipBg = if (headerLayout.warningChipIsRed) UIUtils.color(180, 40, 40, 220) else UIUtils.color(180, 130, 30, 220)
            val chipFg = UIUtils.color(255, 255, 255)
            val chipText = tr("deltacalc.calc.warning.chip", model.warningTexts.size)
            val chipY = cellY + 4
            val chipH = 10
            context.fill(cellX + headerLayout.warningChipX, chipY, cellX + headerLayout.warningChipX + headerLayout.warningChipWidth, chipY + chipH, chipBg)
            val chipScale = BASE_FONT_SCALE * 0.85f
            val chipPad = if (cellW >= 40) 4 else 2
            val maxChipTextW = maxOf(0, headerLayout.warningChipWidth - chipPad * 2)
            val safeChipText = truncateToWidth(chipText, maxChipTextW, chipScale)
            if (safeChipText.isNotEmpty()) {
                val chipTextW = (trFont.getWidth(safeChipText) * chipScale).roundToInt()
                val textOffsetX = maxOf(0, (headerLayout.warningChipWidth - chipTextW) / 2)
                UIUtils.drawText(context, safeChipText, (cellX + headerLayout.warningChipX + textOffsetX).toFloat(), (chipY + 1).toFloat(), chipFg, chipScale)
            }
        }

        // Header chevron
        if (headerLayout.chevronWidth > 0) {
            val chevronText = if (canRenderContent) "\u25BE" else "\u25B8"
            UIUtils.drawText(context, chevronText, (cellX + headerLayout.chevronX).toFloat(), (cellY + 5).toFloat(), V2_TEXT_DIM, BASE_FONT_SCALE)
        }

        if (!canRenderContent) {
            lastPlayerTabBounds = emptyList()
            lastOpponentTabBounds = emptyList()
            lastItemPrevBounds = CalcHitBounds.EMPTY
            lastItemNextBounds = CalcHitBounds.EMPTY
            lastItemBodyBounds = CalcHitBounds.EMPTY
            lastItemResetBounds = CalcHitBounds.EMPTY
            lastAbilityPrevBounds = CalcHitBounds.EMPTY
            lastAbilityNextBounds = CalcHitBounds.EMPTY
            lastAbilityBodyBounds = CalcHitBounds.EMPTY
            lastAbilityResetBounds = CalcHitBounds.EMPTY
            lastSpreadPrevBounds = CalcHitBounds.EMPTY
            lastSpreadNextBounds = CalcHitBounds.EMPTY
            lastSpreadBodyBounds = CalcHitBounds.EMPTY
            lastSpreadResetBounds = CalcHitBounds.EMPTY
            lastSummaryBounds = CalcHitBounds.EMPTY
            lastMovesHeaderBounds = CalcHitBounds.EMPTY
            lastTeamHeaderBounds = CalcHitBounds.EMPTY
            lastContentViewportTop = 0
            lastContentViewportBottom = 0
            lastContentHeight = 0
            contentScrollOffset = 0
            drawResizeHandle(context, x, y, width, height)
            return
        }

        val contentY = cellY + HEADER_HEIGHT + UIUtils.CELL_GAP
        val contentH = height - UIUtils.FRAME_INSET * 2 - HEADER_HEIGHT - UIUtils.CELL_GAP
        UIUtils.drawPopupCell(context, cellX, contentY, cellW, contentH)

        var textY = contentY + 6
        val textScale = BASE_FONT_SCALE * CalcPanelState.fontScale
        val uiScale = CalcPanelState.fontScale
        fun s(n: Int): Int = (n * uiScale).roundToInt().coerceAtLeast(1)
        val safeLineW = maxOf(0, cellW - 12)

        // Compact warning strip below header when height permits
        val stripH = s(10)
        val canFitWarningStrip = model.warningTexts.isNotEmpty() && contentH >= stripH + s(60)
        if (canFitWarningStrip) {
            val firstWarning = model.warningTexts.first()
            val warningSummary = if (model.warningTexts.size > 1) {
                "$firstWarning (${tr("deltacalc.calc.warning.more", model.warningTexts.size - 1)})"
            } else {
                firstWarning
            }
            val stripBg = if (hasLowOrUnsupported) UIUtils.color(180, 40, 40, 50) else UIUtils.color(214, 158, 46, 50)
            val stripBorder = if (hasLowOrUnsupported) UIUtils.color(229, 62, 62, 120) else UIUtils.color(214, 158, 46, 120)
            val stripFg = if (hasLowOrUnsupported) UIUtils.color(255, 120, 120) else UIUtils.color(246, 224, 94)

            val stripLeft = cellX + 4
            val stripRight = cellX + cellW - 4
            context.fill(stripLeft, textY, stripRight, textY + stripH, stripBg)
            context.fill(stripLeft, textY, stripRight, textY + 1, stripBorder)
            context.fill(stripLeft, textY + stripH - 1, stripRight, textY + stripH, stripBorder)
            context.fill(stripLeft, textY, stripLeft + 1, textY + stripH, stripBorder)
            context.fill(stripRight - 1, textY, stripRight, textY + stripH, stripBorder)

            val safeWarningText = truncateToWidth("! $warningSummary", cellW - 14, textScale)
            UIUtils.drawText(context, safeWarningText, (stripLeft + 3).toFloat(), (textY + 1).toFloat(), stripFg, textScale)
            textY += stripH + s(4)
        }

        // V2 matchup row — measured compact combined form or full two-column layout
        textY = drawMatchupSection(context, cellX, textY, cellW, model.selectedPlayer, model.selectedOpponent, model.speedText, textScale, uiScale, compact)

        val tabAreaWidth = cellW - 12
        val summaryChevron = if (CalcPanelState.summaryExpanded) "\u25BE" else "\u25B8"
        val summaryStartY = textY
        val safeSummaryLabel = truncateToWidth("$summaryChevron ${model.matchupLabel}", safeLineW, textScale)
        UIUtils.drawText(context, safeSummaryLabel, (cellX + 6).toFloat(), textY.toFloat(), V2_TEXT, textScale)
        textY += s(11)

        if (CalcPanelState.summaryExpanded) {
            if (!compact) {
                val previewText = if (model.isPreview) tr("deltacalc.calc.preview.switch") else tr("deltacalc.calc.preview.active")
                val safePreviewText = truncateToWidth(previewText, safeLineW, textScale)
                UIUtils.drawText(
                    context,
                    safePreviewText,
                    (cellX + 6).toFloat(),
                    textY.toFloat(),
                    if (model.isPreview) V2_ACCENT_YELLOW else V2_TEXT_LABEL,
                    textScale
                )
                textY += s(10)
                model.switchSummaryText?.let { switchSummary ->
                    val safeSwitch = truncateToWidth(switchSummary, safeLineW, textScale)
                    UIUtils.drawText(context, safeSwitch, (cellX + 6).toFloat(), textY.toFloat(), V2_TEXT, textScale)
                    textY += s(10)
                }
            }
            model.hazardNoteText?.let { note ->
                val safeNote = truncateToWidth(note, safeLineW, textScale)
                UIUtils.drawText(context, safeNote, (cellX + 6).toFloat(), textY.toFloat(), UIUtils.color(245, 180, 120), textScale)
                textY += s(10)
            }
            model.speedText?.let { speedText ->
                val safeSpeed = truncateToWidth(speedText, safeLineW, textScale)
                UIUtils.drawText(context, safeSpeed, (cellX + 6).toFloat(), textY.toFloat(), speedColor(speedText), textScale)
                textY += s(10)
            }

            val opponentUuid = model.selectedOpponentUuid
            val itemOverridden = opponentUuid?.let { CalcComputationService.hasOverride(it, OverrideRow.ITEM) } ?: false
            val abilityOverridden = opponentUuid?.let { CalcComputationService.hasOverride(it, OverrideRow.ABILITY) } ?: false
            val spreadOverridden = opponentUuid?.let { CalcComputationService.hasOverride(it, OverrideRow.SPREAD) } ?: false

            val itemRealRevealed = !itemOverridden && model.opponentSet.item.second == InferenceValueState.REVEALED
            val abilityRealRevealed = !abilityOverridden && model.opponentSet.ability.second == InferenceValueState.REVEALED
            val itemHasAlts = !itemRealRevealed && model.opponentSet.itemAlternatives.size > 1
            val abilityHasAlts = !abilityRealRevealed && model.opponentSet.abilityAlternatives.size > 1
            val spreadHasAlts = model.opponentSet.spreadAlternatives.size > 1

            val rowHeight = s(11).coerceAtLeast(8)
            val pillScale = (textScale - 0.15f).coerceAtLeast(0.42f)

            // 1. Item row
            val itemPillLabel = pillLabel(model.opponentSet.item.second, itemOverridden)
            val itemTextW = (trFont.getWidth(itemPillLabel) * pillScale).roundToInt()
            val itemPillW = CalcPresentation.pillTotalWidth(itemTextW, uiScale)
            val itemLayout = CalcPresentation.calculateOverrideRowLayout(
                rowX = cellX + 4,
                rowY = textY - 2,
                rowW = cellW - 8,
                rowH = rowHeight,
                hasAlts = itemHasAlts,
                isOverridden = itemOverridden,
                pillWidth = itemPillW,
                uiScale = uiScale
            )
            lastItemPrevBounds = itemLayout.prevBounds
            lastItemNextBounds = itemLayout.nextBounds
            lastItemBodyBounds = itemLayout.bodyBounds
            lastItemResetBounds = itemLayout.resetBounds

            val itemValue = formatInference(model.opponentSet.item.first)
            val itemUsage = model.opponentSet.itemUsagePercent[normalizeForUsageLookup(model.opponentSet.item.first)]
            val itemDisplay = buildOverrideRowText(itemValue, itemUsage)

            // Body hover/pressed feedback
            if (!itemLayout.bodyBounds.isEmpty && itemLayout.bodyBounds.contains(mouseX, mouseY)) {
                val isBodyPressed = armedOverride == ArmedOverride(OverrideRow.ITEM, OverrideAction.NEXT)
                val bodyCol = if (isBodyPressed) UIUtils.color(56, 109, 161, 60) else UIUtils.color(255, 255, 255, 12)
                context.fill(itemLayout.bodyBounds.x, itemLayout.bodyBounds.y, itemLayout.bodyBounds.right, itemLayout.bodyBounds.bottom, bodyCol)
            }
            val maxItemTextW = if (!itemLayout.bodyBounds.isEmpty) itemLayout.bodyBounds.width - 2 else maxOf(0, itemLayout.pillBounds.x - cellX - 8)
            val safeItemDisplay = truncateToWidth("${tr("deltacalc.calc.item")}: $itemDisplay", maxItemTextW, textScale)
            UIUtils.drawText(context, safeItemDisplay, (cellX + 6).toFloat(), textY.toFloat(), V2_TEXT, textScale)

            if (!itemLayout.pillBounds.isEmpty) {
                drawStatePill(context, itemLayout.pillBounds.x, textY - 1, model.opponentSet.item.second, textScale, uiScale, itemOverridden)
            }
            if (itemLayout.prevVisible) {
                val isHovered = itemLayout.prevBounds.contains(mouseX, mouseY)
                val isArmed = armedOverride == ArmedOverride(OverrideRow.ITEM, OverrideAction.PREV)
                val col = if (isArmed) V2_ACCENT_BLUE else if (isHovered) V2_TEXT else V2_TEXT_DIM
                if (isHovered) context.fill(itemLayout.prevBounds.x, itemLayout.prevBounds.y, itemLayout.prevBounds.right, itemLayout.prevBounds.bottom, UIUtils.color(255, 255, 255, 15))
                UIUtils.drawText(context, tr("deltacalc.calc.control.prev"), (itemLayout.prevBounds.x + 1).toFloat(), textY.toFloat(), col, textScale)
            }
            if (itemLayout.nextVisible) {
                val isHovered = itemLayout.nextBounds.contains(mouseX, mouseY)
                val isArmed = armedOverride == ArmedOverride(OverrideRow.ITEM, OverrideAction.NEXT)
                val col = if (isArmed) V2_ACCENT_BLUE else if (isHovered) V2_TEXT else V2_TEXT_DIM
                if (isHovered) context.fill(itemLayout.nextBounds.x, itemLayout.nextBounds.y, itemLayout.nextBounds.right, itemLayout.nextBounds.bottom, UIUtils.color(255, 255, 255, 15))
                UIUtils.drawText(context, tr("deltacalc.calc.control.next"), (itemLayout.nextBounds.x + 1).toFloat(), textY.toFloat(), col, textScale)
            }
            if (itemLayout.resetVisible) {
                val isHovered = itemLayout.resetBounds.contains(mouseX, mouseY)
                val isArmed = armedOverride == ArmedOverride(OverrideRow.ITEM, OverrideAction.RESET)
                val col = if (isArmed) UIUtils.color(255, 255, 255) else if (isHovered) UIUtils.color(255, 107, 107) else UIUtils.color(200, 100, 100, 180)
                if (isHovered) context.fill(itemLayout.resetBounds.x, itemLayout.resetBounds.y, itemLayout.resetBounds.right, itemLayout.resetBounds.bottom, UIUtils.color(255, 107, 107, 25))
                UIUtils.drawText(context, tr("deltacalc.calc.control.reset"), (itemLayout.resetBounds.x + 1).toFloat(), textY.toFloat(), col, textScale)
            }
            textY += s(10)

            // 2. Ability row
            val abilityPillLabel = pillLabel(model.opponentSet.ability.second, abilityOverridden)
            val abilityTextW = (trFont.getWidth(abilityPillLabel) * pillScale).roundToInt()
            val abilityPillW = CalcPresentation.pillTotalWidth(abilityTextW, uiScale)
            val abilityLayout = CalcPresentation.calculateOverrideRowLayout(
                rowX = cellX + 4,
                rowY = textY - 2,
                rowW = cellW - 8,
                rowH = rowHeight,
                hasAlts = abilityHasAlts,
                isOverridden = abilityOverridden,
                pillWidth = abilityPillW,
                uiScale = uiScale
            )
            lastAbilityPrevBounds = abilityLayout.prevBounds
            lastAbilityNextBounds = abilityLayout.nextBounds
            lastAbilityBodyBounds = abilityLayout.bodyBounds
            lastAbilityResetBounds = abilityLayout.resetBounds

            val abilityValue = formatInference(model.opponentSet.ability.first)
            val abilityUsage = model.opponentSet.abilityUsagePercent[normalizeForUsageLookup(model.opponentSet.ability.first)]
            val abilityDisplay = buildOverrideRowText(abilityValue, abilityUsage)

            if (!abilityLayout.bodyBounds.isEmpty && abilityLayout.bodyBounds.contains(mouseX, mouseY)) {
                val isBodyPressed = armedOverride == ArmedOverride(OverrideRow.ABILITY, OverrideAction.NEXT)
                val bodyCol = if (isBodyPressed) UIUtils.color(56, 109, 161, 60) else UIUtils.color(255, 255, 255, 12)
                context.fill(abilityLayout.bodyBounds.x, abilityLayout.bodyBounds.y, abilityLayout.bodyBounds.right, abilityLayout.bodyBounds.bottom, bodyCol)
            }
            val maxAbilityTextW = if (!abilityLayout.bodyBounds.isEmpty) abilityLayout.bodyBounds.width - 2 else maxOf(0, abilityLayout.pillBounds.x - cellX - 8)
            val safeAbilityDisplay = truncateToWidth("${tr("deltacalc.calc.ability")}: $abilityDisplay", maxAbilityTextW, textScale)
            UIUtils.drawText(context, safeAbilityDisplay, (cellX + 6).toFloat(), textY.toFloat(), V2_TEXT, textScale)

            if (!abilityLayout.pillBounds.isEmpty) {
                drawStatePill(context, abilityLayout.pillBounds.x, textY - 1, model.opponentSet.ability.second, textScale, uiScale, abilityOverridden)
            }
            if (abilityLayout.prevVisible) {
                val isHovered = abilityLayout.prevBounds.contains(mouseX, mouseY)
                val isArmed = armedOverride == ArmedOverride(OverrideRow.ABILITY, OverrideAction.PREV)
                val col = if (isArmed) V2_ACCENT_BLUE else if (isHovered) V2_TEXT else V2_TEXT_DIM
                if (isHovered) context.fill(abilityLayout.prevBounds.x, abilityLayout.prevBounds.y, abilityLayout.prevBounds.right, abilityLayout.prevBounds.bottom, UIUtils.color(255, 255, 255, 15))
                UIUtils.drawText(context, tr("deltacalc.calc.control.prev"), (abilityLayout.prevBounds.x + 1).toFloat(), textY.toFloat(), col, textScale)
            }
            if (abilityLayout.nextVisible) {
                val isHovered = abilityLayout.nextBounds.contains(mouseX, mouseY)
                val isArmed = armedOverride == ArmedOverride(OverrideRow.ABILITY, OverrideAction.NEXT)
                val col = if (isArmed) V2_ACCENT_BLUE else if (isHovered) V2_TEXT else V2_TEXT_DIM
                if (isHovered) context.fill(abilityLayout.nextBounds.x, abilityLayout.nextBounds.y, abilityLayout.nextBounds.right, abilityLayout.nextBounds.bottom, UIUtils.color(255, 255, 255, 15))
                UIUtils.drawText(context, tr("deltacalc.calc.control.next"), (abilityLayout.nextBounds.x + 1).toFloat(), textY.toFloat(), col, textScale)
            }
            if (abilityLayout.resetVisible) {
                val isHovered = abilityLayout.resetBounds.contains(mouseX, mouseY)
                val isArmed = armedOverride == ArmedOverride(OverrideRow.ABILITY, OverrideAction.RESET)
                val col = if (isArmed) UIUtils.color(255, 255, 255) else if (isHovered) UIUtils.color(255, 107, 107) else UIUtils.color(200, 100, 100, 180)
                if (isHovered) context.fill(abilityLayout.resetBounds.x, abilityLayout.resetBounds.y, abilityLayout.resetBounds.right, abilityLayout.resetBounds.bottom, UIUtils.color(255, 107, 107, 25))
                UIUtils.drawText(context, tr("deltacalc.calc.control.reset"), (abilityLayout.resetBounds.x + 1).toFloat(), textY.toFloat(), col, textScale)
            }
            textY += s(10)

            // 3. Spread row
            val spreadState = model.opponentSet.spread?.state ?: InferenceValueState.UNKNOWN
            val spreadPillLabel = pillLabel(spreadState, spreadOverridden)
            val spreadTextW = (trFont.getWidth(spreadPillLabel) * pillScale).roundToInt()
            val spreadPillW = CalcPresentation.pillTotalWidth(spreadTextW, uiScale)
            val spreadLayout = CalcPresentation.calculateOverrideRowLayout(
                rowX = cellX + 4,
                rowY = textY - 2,
                rowW = cellW - 8,
                rowH = rowHeight,
                hasAlts = spreadHasAlts,
                isOverridden = spreadOverridden,
                pillWidth = spreadPillW,
                uiScale = uiScale
            )
            lastSpreadPrevBounds = spreadLayout.prevBounds
            lastSpreadNextBounds = spreadLayout.nextBounds
            lastSpreadBodyBounds = spreadLayout.bodyBounds
            lastSpreadResetBounds = spreadLayout.resetBounds

            val spreadValue = model.opponentSet.spreadLabel ?: tr("deltacalc.calc.unknown")
            val spreadUsage = model.opponentSet.spread?.usagePercent
            val spreadDisplay = buildOverrideRowText(spreadValue, spreadUsage)

            if (!spreadLayout.bodyBounds.isEmpty && spreadLayout.bodyBounds.contains(mouseX, mouseY)) {
                val isBodyPressed = armedOverride == ArmedOverride(OverrideRow.SPREAD, OverrideAction.NEXT)
                val bodyCol = if (isBodyPressed) UIUtils.color(56, 109, 161, 60) else UIUtils.color(255, 255, 255, 12)
                context.fill(spreadLayout.bodyBounds.x, spreadLayout.bodyBounds.y, spreadLayout.bodyBounds.right, spreadLayout.bodyBounds.bottom, bodyCol)
            }
            val maxSpreadTextW = if (!spreadLayout.bodyBounds.isEmpty) spreadLayout.bodyBounds.width - 2 else maxOf(0, spreadLayout.pillBounds.x - cellX - 8)
            val safeSpreadDisplay = truncateToWidth("${tr("deltacalc.calc.spread")}: $spreadDisplay", maxSpreadTextW, textScale)
            UIUtils.drawText(context, safeSpreadDisplay, (cellX + 6).toFloat(), textY.toFloat(), V2_TEXT_DIM, textScale)

            if (!spreadLayout.pillBounds.isEmpty) {
                drawStatePill(context, spreadLayout.pillBounds.x, textY - 1, spreadState, textScale, uiScale, spreadOverridden)
            }
            if (spreadLayout.prevVisible) {
                val isHovered = spreadLayout.prevBounds.contains(mouseX, mouseY)
                val isArmed = armedOverride == ArmedOverride(OverrideRow.SPREAD, OverrideAction.PREV)
                val col = if (isArmed) V2_ACCENT_BLUE else if (isHovered) V2_TEXT else V2_TEXT_DIM
                if (isHovered) context.fill(spreadLayout.prevBounds.x, spreadLayout.prevBounds.y, spreadLayout.prevBounds.right, spreadLayout.prevBounds.bottom, UIUtils.color(255, 255, 255, 15))
                UIUtils.drawText(context, tr("deltacalc.calc.control.prev"), (spreadLayout.prevBounds.x + 1).toFloat(), textY.toFloat(), col, textScale)
            }
            if (spreadLayout.nextVisible) {
                val isHovered = spreadLayout.nextBounds.contains(mouseX, mouseY)
                val isArmed = armedOverride == ArmedOverride(OverrideRow.SPREAD, OverrideAction.NEXT)
                val col = if (isArmed) V2_ACCENT_BLUE else if (isHovered) V2_TEXT else V2_TEXT_DIM
                if (isHovered) context.fill(spreadLayout.nextBounds.x, spreadLayout.nextBounds.y, spreadLayout.nextBounds.right, spreadLayout.nextBounds.bottom, UIUtils.color(255, 255, 255, 15))
                UIUtils.drawText(context, tr("deltacalc.calc.control.next"), (spreadLayout.nextBounds.x + 1).toFloat(), textY.toFloat(), col, textScale)
            }
            if (spreadLayout.resetVisible) {
                val isHovered = spreadLayout.resetBounds.contains(mouseX, mouseY)
                val isArmed = armedOverride == ArmedOverride(OverrideRow.SPREAD, OverrideAction.RESET)
                val col = if (isArmed) UIUtils.color(255, 255, 255) else if (isHovered) UIUtils.color(255, 107, 107) else UIUtils.color(200, 100, 100, 180)
                if (isHovered) context.fill(spreadLayout.resetBounds.x, spreadLayout.resetBounds.y, spreadLayout.resetBounds.right, spreadLayout.resetBounds.bottom, UIUtils.color(255, 107, 107, 25))
                UIUtils.drawText(context, tr("deltacalc.calc.control.reset"), (spreadLayout.resetBounds.x + 1).toFloat(), textY.toFloat(), col, textScale)
            }
            textY += s(9)

            if (!compact) {
                val safeSource = truncateToWidth(model.opponentSet.sourceLabel, safeLineW, textScale)
                UIUtils.drawText(context, safeSource, (cellX + 6).toFloat(), textY.toFloat(), V2_TEXT_LABEL, textScale)
                textY += s(10)
            } else {
                textY += s(2)
            }
        } else {
            val safeSource = truncateToWidth(model.opponentSet.sourceLabel, maxOf(0, cellW - 26), textScale)
            UIUtils.drawText(context, safeSource, (cellX + 20).toFloat(), (textY - 1).toFloat(), V2_TEXT_LABEL, textScale)
            textY += s(8)
            lastItemPrevBounds = CalcHitBounds.EMPTY
            lastItemNextBounds = CalcHitBounds.EMPTY
            lastItemBodyBounds = CalcHitBounds.EMPTY
            lastItemResetBounds = CalcHitBounds.EMPTY
            lastAbilityPrevBounds = CalcHitBounds.EMPTY
            lastAbilityNextBounds = CalcHitBounds.EMPTY
            lastAbilityBodyBounds = CalcHitBounds.EMPTY
            lastAbilityResetBounds = CalcHitBounds.EMPTY
            lastSpreadPrevBounds = CalcHitBounds.EMPTY
            lastSpreadNextBounds = CalcHitBounds.EMPTY
            lastSpreadBodyBounds = CalcHitBounds.EMPTY
            lastSpreadResetBounds = CalcHitBounds.EMPTY
        }

        lastSummaryBounds = CalcHitBounds(cellX, summaryStartY - 2, cellW, (textY - summaryStartY + 2).coerceAtLeast(12))

        val boxColor = UIUtils.color(255, 255, 255, 28)
        val boxTop = summaryStartY - 3
        val boxBot = textY - 2
        val boxLeft = cellX + 3
        val boxRight = cellX + cellW - 3
        context.fill(boxLeft, boxTop, boxRight, boxTop + 1, boxColor)
        context.fill(boxLeft, boxBot - 1, boxRight, boxBot, boxColor)
        context.fill(boxLeft, boxTop, boxLeft + 1, boxBot, boxColor)
        context.fill(boxRight - 1, boxTop, boxRight, boxBot, boxColor)
        textY += s(6)

        // ─── Scrollable content viewport (move predictions + team) ─────────
        val panelBottomLimit = y + height - UIUtils.FRAME_INSET
        val viewportTop = textY
        val viewportBottom = panelBottomLimit
        val viewportHeight = viewportBottom - viewportTop

        if (viewportHeight <= 0) {
            lastMovesHeaderBounds = CalcHitBounds.EMPTY
            lastTeamHeaderBounds = CalcHitBounds.EMPTY
            lastPlayerTabBounds = emptyList()
            lastOpponentTabBounds = emptyList()
            lastContentViewportTop = 0
            lastContentViewportBottom = 0
            lastContentHeight = 0
            contentScrollOffset = 0
            drawResizeHandle(context, x, y, width, height)
            return
        }

        val maxScroll = (lastContentHeight - viewportHeight).coerceAtLeast(0)
        contentScrollOffset = contentScrollOffset.coerceIn(0, maxScroll)
        val scrollOffset = contentScrollOffset
        lastContentViewportTop = viewportTop
        lastContentViewportBottom = viewportBottom

        context.matrices.push()
        context.matrices.translate(0f, -scrollOffset.toFloat(), 0f)
        context.enableScissor(cellX, viewportTop, cellX + cellW, viewportBottom)
        val contentStartY = textY

        val movesChevron = if (CalcPanelState.movesSectionCollapsed) "\u25B8" else "\u25BE"
        val movesHeader = "$movesChevron " + tr("deltacalc.calc.move_predictions")
        val safeMovesHeader = truncateToWidth(movesHeader, safeLineW, textScale)
        UIUtils.drawText(context, safeMovesHeader, (cellX + 6).toFloat(), textY.toFloat(), V2_TEXT_LABEL, textScale)
        lastMovesHeaderBounds = CalcHitBounds(cellX, (textY - 2) - scrollOffset, cellW, s(11).coerceAtLeast(8))
        textY += s(10)

        if (!CalcPanelState.movesSectionCollapsed) {
            val rowH = s(14).coerceAtLeast(7)
            val opponentHpPct = hpPercent(model.selectedOpponent)
            val playerHpPct = hpPercent(model.selectedPlayer)

            val safeYouToOpp = truncateToWidth(tr("deltacalc.calc.you_to_opponent"), safeLineW, textScale)
            UIUtils.drawText(context, safeYouToOpp, (cellX + 6).toFloat(), textY.toFloat(), V2_ACCENT_GREEN, textScale)
            textY += s(9)
            model.yourMoves.take(4).forEach { row ->
                drawMoveRow(context, cellX + 6, textY.toFloat(), row, if (row.emphasized) V2_ACCENT_YELLOW else V2_TEXT, textScale, uiScale, moveRowAvailableWidth, opponentHpPct)
                textY += rowH
            }

            textY += s(2)
            val safeOppToYou = truncateToWidth(tr("deltacalc.calc.opponent_to_you"), safeLineW, textScale)
            UIUtils.drawText(context, safeOppToYou, (cellX + 6).toFloat(), textY.toFloat(), UIUtils.color(252, 129, 129), textScale)
            textY += s(9)
            model.opponentMoves.take(4).forEach { row ->
                drawMoveRow(context, cellX + 6, textY.toFloat(), row, V2_TEXT, textScale, uiScale, moveRowAvailableWidth, playerHpPct)
                textY += rowH
            }
        } else {
            val safeTap = truncateToWidth(tr("deltacalc.calc.tap_to_expand"), safeLineW, textScale)
            UIUtils.drawText(context, safeTap, (cellX + 6).toFloat(), textY.toFloat() + s(8), UIUtils.color(140, 150, 165), textScale)
            textY += s(16)
        }

        textY += s(2)
        UIUtils.drawPopupRowDivider(context, cellX, cellW, textY)
        textY += s(3)

        val teamChevron = if (CalcPanelState.teamSectionCollapsed) "\u25B8" else "\u25BE"
        val teamHeader = "$teamChevron " + tr("deltacalc.calc.team")
        val safeTeamHeader = truncateToWidth(teamHeader, safeLineW, textScale)
        UIUtils.drawText(context, safeTeamHeader, (cellX + 6).toFloat(), textY.toFloat(), V2_TEXT_LABEL, textScale)
        lastTeamHeaderBounds = CalcHitBounds(cellX, (textY - 2) - scrollOffset, cellW, s(11).coerceAtLeast(8))
        textY += s(10)

        if (!CalcPanelState.teamSectionCollapsed) {
            val playerTabsRaw = drawTabs(context, cellX + 6, textY, tabAreaWidth, model.playerTabs, model.snapshot.playerTeam, textScale, uiScale)
            lastPlayerTabBounds = playerTabsRaw.map { tab -> tab.copy(y = tab.y - scrollOffset) }
            textY += tabBlockHeight(model.playerTabs, tabAreaWidth, uiScale)

            if (model.opponentTabs.size > 1) {
                val opponentTabsRaw = drawTabs(context, cellX + 6, textY, tabAreaWidth, model.opponentTabs, model.snapshot.opponentTeam, textScale, uiScale)
                lastOpponentTabBounds = opponentTabsRaw.map { tab -> tab.copy(y = tab.y - scrollOffset) }
                textY += tabBlockHeight(model.opponentTabs, tabAreaWidth, uiScale)
            } else {
                lastOpponentTabBounds = emptyList()
            }
        } else {
            lastPlayerTabBounds = emptyList()
            lastOpponentTabBounds = emptyList()
        }

        context.disableScissor()
        context.matrices.pop()
        lastContentHeight = textY - contentStartY

        // Right-edge scrollbar with active hover feedback
        val scrollableContent = lastContentHeight > viewportHeight
        val isThumbHovered = isScrollbarDragging || (scrollableContent && contentScrollbar.isOverThumb(mouseX, mouseY))
        contentScrollbar.render(
            context = context,
            x = cellX + cellW - 4,
            y = viewportTop,
            height = viewportHeight,
            contentHeight = lastContentHeight,
            visibleHeight = viewportHeight,
            scrollOffset = scrollOffset,
            isHovered = isThumbHovered
        )

        drawResizeHandle(context, x, y, width, height)
    }

    fun onScroll(mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        val mc = MinecraftClient.getInstance()
        val scaledX = (mouseX * mc.window.scaledWidth / mc.window.width).toInt()
        val scaledY = (mouseY * mc.window.scaledHeight / mc.window.height).toInt()
        val (x, y, width, height) = lastBounds
        if (!contains(scaledX, scaledY, x, y, width, height)) {
            return false
        }
        val handle = mc.window.handle
        val isCtrlDown = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS
        if (isCtrlDown) {
            CalcPanelState.adjustFontScale(if (deltaY > 0) CalcPanelState.FONT_SCALE_STEP else -CalcPanelState.FONT_SCALE_STEP)
            CalcPanelState.save()
            return true
        }

        val viewportHeight = (lastContentViewportBottom - lastContentViewportTop).coerceAtLeast(0)
        val maxScroll = (lastContentHeight - viewportHeight).coerceAtLeast(0)
        if (maxScroll <= 0) return false
        val step = (12 * deltaY).toInt()
        contentScrollOffset = (contentScrollOffset - step).coerceIn(0, maxScroll)
        return true
    }

    private fun handleInput(mc: MinecraftClient, x: Int, y: Int, width: Int, height: Int, model: CalcRenderModel) {
        val mouseX = (mc.mouse.x * mc.window.scaledWidth / mc.window.width).toInt()
        val mouseY = (mc.mouse.y * mc.window.scaledHeight / mc.window.height).toInt()
        val mouseDown = GLFW.glfwGetMouseButton(mc.window.handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
        val rightDown = GLFW.glfwGetMouseButton(mc.window.handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS
        val canInteract = UIUtils.canInteract(UIUtils.ActivePanel.DAMAGE_CALC)

        // Live hover update for resize handle
        val resizeZone = UIUtils.getResizeZone(mouseX, mouseY, x, y, width, height, RESIZE_HANDLE)
        interaction.hoveredZone = resizeZone

        // Right-click legacy alias for reset
        if (rightDown && !wasRightMouseDown && canInteract) {
            val opponentUuid = model.selectedOpponentUuid
            if (opponentUuid != null && CalcPanelState.expanded && CalcPanelState.summaryExpanded) {
                val resetRow = when {
                    lastItemBodyBounds.contains(mouseX, mouseY) || lastItemPrevBounds.contains(mouseX, mouseY) || lastItemNextBounds.contains(mouseX, mouseY) || lastItemResetBounds.contains(mouseX, mouseY) -> OverrideRow.ITEM
                    lastAbilityBodyBounds.contains(mouseX, mouseY) || lastAbilityPrevBounds.contains(mouseX, mouseY) || lastAbilityNextBounds.contains(mouseX, mouseY) || lastAbilityResetBounds.contains(mouseX, mouseY) -> OverrideRow.ABILITY
                    lastSpreadBodyBounds.contains(mouseX, mouseY) || lastSpreadPrevBounds.contains(mouseX, mouseY) || lastSpreadNextBounds.contains(mouseX, mouseY) || lastSpreadResetBounds.contains(mouseX, mouseY) -> OverrideRow.SPREAD
                    else -> null
                }
                resetRow?.let { CalcComputationService.resetOverride(opponentUuid, it) }
            }
        }
        wasRightMouseDown = rightDown

        if (mouseDown) {
            when {
                isScrollbarDragging -> {
                    summaryClickArmed = false
                    pendingTabSelection = null
                    pendingSectionToggle = null
                    armedOverride = null
                    val viewportHeight = (lastContentViewportBottom - lastContentViewportTop).coerceAtLeast(0)
                    contentScrollOffset = contentScrollbar.dragToScrollOffset(
                        dragDeltaY = mouseY - scrollDragStartMouseY,
                        dragStartOffset = scrollDragStartOffset,
                        contentHeight = lastContentHeight,
                        visibleHeight = viewportHeight
                    )
                }
                interaction.isDragging -> {
                    summaryClickArmed = false
                    pendingTabSelection = null
                    pendingSectionToggle = null
                    armedOverride = null
                    interaction.updateDrag(mouseX, mouseY, mc.window.scaledWidth, mc.window.scaledHeight, width, height)?.let { (nextX, nextY) ->
                        CalcPanelState.setPosition(nextX, nextY)
                    }
                }
                interaction.isResizing -> {
                    summaryClickArmed = false
                    pendingTabSelection = null
                    pendingSectionToggle = null
                    armedOverride = null
                    val result = interaction.calculateResize(
                        mouseX = mouseX,
                        mouseY = mouseY,
                        minWidth = 180,
                        maxWidth = minOf(mc.window.scaledWidth, maxOf(180, (mc.window.scaledWidth * 0.65f).toInt())),
                        minHeight = COLLAPSED_HEIGHT,
                        maxHeight = minOf(mc.window.scaledHeight, maxOf(COLLAPSED_HEIGHT, (mc.window.scaledHeight * 0.8f).toInt())),
                        screenWidth = mc.window.scaledWidth,
                        screenHeight = mc.window.scaledHeight
                    )
                    CalcPanelState.setPosition(result.newX, result.newY)
                    CalcPanelState.setDimensions(result.newWidth, result.newHeight)
                }
                !wasMouseDown && canInteract -> {
                    val clickedPlayerTab = findTabAt(mouseX, mouseY, lastPlayerTabBounds)
                    val clickedOpponentTab = findTabAt(mouseX, mouseY, lastOpponentTabBounds)
                    val rowsClickable = CalcPanelState.expanded && CalcPanelState.summaryExpanded
                    val viewportHeight = (lastContentViewportBottom - lastContentViewportTop).coerceAtLeast(0)
                    val scrollableContent = lastContentHeight > viewportHeight
                    val armed = if (rowsClickable) checkOverrideHit(mouseX, mouseY) else null

                    if (scrollableContent && contentScrollbar.isOverThumb(mouseX, mouseY)) {
                        summaryClickArmed = false
                        pendingTabSelection = null
                        pendingSectionToggle = null
                        armedOverride = null
                        isScrollbarDragging = true
                        scrollDragStartMouseY = mouseY
                        scrollDragStartOffset = contentScrollOffset
                    } else if (scrollableContent && contentScrollbar.isOverTrack(mouseX, mouseY)) {
                        summaryClickArmed = false
                        pendingTabSelection = null
                        pendingSectionToggle = null
                        armedOverride = null
                        contentScrollOffset = contentScrollbar.trackClickToScrollOffset(
                            mouseY = mouseY,
                            contentHeight = lastContentHeight,
                            visibleHeight = viewportHeight
                        )
                        isScrollbarDragging = true
                        scrollDragStartMouseY = mouseY
                        scrollDragStartOffset = contentScrollOffset
                    } else if (resizeZone != UIUtils.ResizeZone.NONE) {
                        summaryClickArmed = false
                        pendingTabSelection = null
                        pendingSectionToggle = null
                        armedOverride = null
                        interaction.startResize(mouseX, mouseY, resizeZone, x, y, width, height)
                    } else if (lastTeamHeaderBounds.contains(mouseX, mouseY)) {
                        summaryClickArmed = false
                        pendingTabSelection = null
                        armedOverride = null
                        pendingSectionToggle = SectionToggle.TEAM
                    } else if (lastMovesHeaderBounds.contains(mouseX, mouseY)) {
                        summaryClickArmed = false
                        pendingTabSelection = null
                        armedOverride = null
                        pendingSectionToggle = SectionToggle.MOVES
                    } else if (clickedPlayerTab != null && !clickedPlayerTab.disabled) {
                        summaryClickArmed = false
                        pendingSectionToggle = null
                        armedOverride = null
                        pendingTabSelection = PendingTabSelection(clickedPlayerTab.uuid, isPlayerSide = true)
                    } else if (clickedOpponentTab != null && !clickedOpponentTab.disabled) {
                        summaryClickArmed = false
                        pendingSectionToggle = null
                        armedOverride = null
                        pendingTabSelection = PendingTabSelection(clickedOpponentTab.uuid, isPlayerSide = false)
                    } else if (contains(mouseX, mouseY, x + UIUtils.FRAME_INSET, y + UIUtils.FRAME_INSET, width - UIUtils.FRAME_INSET * 2, HEADER_HEIGHT)) {
                        summaryClickArmed = false
                        pendingSectionToggle = null
                        pendingTabSelection = null
                        armedOverride = null
                        interaction.startDrag(mouseX, mouseY, x, y)
                    } else if (armed != null) {
                        summaryClickArmed = false
                        pendingTabSelection = null
                        pendingSectionToggle = null
                        armedOverride = armed
                    } else if (CalcPanelState.expanded && lastSummaryBounds.contains(mouseX, mouseY)) {
                        pendingTabSelection = null
                        pendingSectionToggle = null
                        armedOverride = null
                        summaryClickArmed = true
                    }
                }
            }
        } else {
            if (isScrollbarDragging) {
                isScrollbarDragging = false
            }
            if (interaction.isDragging) {
                summaryClickArmed = false
                pendingTabSelection = null
                pendingSectionToggle = null
                armedOverride = null
                val didDrag = interaction.endDrag()
                if (!didDrag && contains(mouseX, mouseY, x + UIUtils.FRAME_INSET, y + UIUtils.FRAME_INSET, width - UIUtils.FRAME_INSET * 2, HEADER_HEIGHT)) {
                    CalcPanelState.toggleExpanded()
                }
                CalcPanelState.save()
            } else if (interaction.isResizing) {
                summaryClickArmed = false
                pendingTabSelection = null
                pendingSectionToggle = null
                armedOverride = null
                interaction.endResize()
                CalcPanelState.save()
            } else if (armedOverride != null) {
                val armed = armedOverride!!
                val opponentUuid = model.selectedOpponentUuid
                if (opponentUuid != null && isArmedOverrideStillInside(armed, mouseX, mouseY)) {
                    when (armed.action) {
                        OverrideAction.RESET -> CalcComputationService.resetOverride(opponentUuid, armed.row)
                        OverrideAction.PREV -> cycleRow(opponentUuid, armed.row, -1, model)
                        OverrideAction.NEXT -> cycleRow(opponentUuid, armed.row, 1, model)
                    }
                }
                armedOverride = null
            } else if (pendingSectionToggle != null) {
                val toggle = pendingSectionToggle!!
                val stillInside = when (toggle) {
                    SectionToggle.TEAM -> lastTeamHeaderBounds.contains(mouseX, mouseY)
                    SectionToggle.MOVES -> lastMovesHeaderBounds.contains(mouseX, mouseY)
                }
                if (stillInside) {
                    when (toggle) {
                        SectionToggle.TEAM -> CalcPanelState.toggleTeamSectionCollapsed()
                        SectionToggle.MOVES -> CalcPanelState.toggleMovesSectionCollapsed()
                    }
                    CalcPanelState.save()
                }
                pendingSectionToggle = null
            } else if (pendingTabSelection != null) {
                val pending = pendingTabSelection
                val tab = if (pending?.isPlayerSide == true) findTabAt(mouseX, mouseY, lastPlayerTabBounds) else findTabAt(mouseX, mouseY, lastOpponentTabBounds)
                if (pending != null && tab?.uuid == pending.uuid && !tab.disabled) {
                    if (pending.isPlayerSide) {
                        CalcComputationService.selectPlayerPreview(pending.uuid)
                    } else {
                        CalcComputationService.selectOpponentPreview(pending.uuid)
                    }
                }
                pendingTabSelection = null
            } else if (summaryClickArmed) {
                if (CalcPanelState.expanded && lastSummaryBounds.contains(mouseX, mouseY)) {
                    CalcPanelState.toggleSummaryExpanded()
                    CalcPanelState.save()
                }
                summaryClickArmed = false
            }
        }

        wasMouseDown = mouseDown
    }

    private fun checkOverrideHit(mouseX: Int, mouseY: Int): ArmedOverride? {
        // ITEM
        if (lastItemResetBounds.contains(mouseX, mouseY)) return ArmedOverride(OverrideRow.ITEM, OverrideAction.RESET)
        if (lastItemPrevBounds.contains(mouseX, mouseY)) return ArmedOverride(OverrideRow.ITEM, OverrideAction.PREV)
        if (lastItemNextBounds.contains(mouseX, mouseY)) return ArmedOverride(OverrideRow.ITEM, OverrideAction.NEXT)
        if (lastItemBodyBounds.contains(mouseX, mouseY)) return ArmedOverride(OverrideRow.ITEM, OverrideAction.NEXT)

        // ABILITY
        if (lastAbilityResetBounds.contains(mouseX, mouseY)) return ArmedOverride(OverrideRow.ABILITY, OverrideAction.RESET)
        if (lastAbilityPrevBounds.contains(mouseX, mouseY)) return ArmedOverride(OverrideRow.ABILITY, OverrideAction.PREV)
        if (lastAbilityNextBounds.contains(mouseX, mouseY)) return ArmedOverride(OverrideRow.ABILITY, OverrideAction.NEXT)
        if (lastAbilityBodyBounds.contains(mouseX, mouseY)) return ArmedOverride(OverrideRow.ABILITY, OverrideAction.NEXT)

        // SPREAD
        if (lastSpreadResetBounds.contains(mouseX, mouseY)) return ArmedOverride(OverrideRow.SPREAD, OverrideAction.RESET)
        if (lastSpreadPrevBounds.contains(mouseX, mouseY)) return ArmedOverride(OverrideRow.SPREAD, OverrideAction.PREV)
        if (lastSpreadNextBounds.contains(mouseX, mouseY)) return ArmedOverride(OverrideRow.SPREAD, OverrideAction.NEXT)
        if (lastSpreadBodyBounds.contains(mouseX, mouseY)) return ArmedOverride(OverrideRow.SPREAD, OverrideAction.NEXT)

        return null
    }

    private fun isArmedOverrideStillInside(armed: ArmedOverride, mouseX: Int, mouseY: Int): Boolean {
        return when (armed.row) {
            OverrideRow.ITEM -> when (armed.action) {
                OverrideAction.RESET -> lastItemResetBounds.contains(mouseX, mouseY)
                OverrideAction.PREV -> lastItemPrevBounds.contains(mouseX, mouseY)
                OverrideAction.NEXT -> lastItemNextBounds.contains(mouseX, mouseY) || lastItemBodyBounds.contains(mouseX, mouseY)
            }
            OverrideRow.ABILITY -> when (armed.action) {
                OverrideAction.RESET -> lastAbilityResetBounds.contains(mouseX, mouseY)
                OverrideAction.PREV -> lastAbilityPrevBounds.contains(mouseX, mouseY)
                OverrideAction.NEXT -> lastAbilityNextBounds.contains(mouseX, mouseY) || lastAbilityBodyBounds.contains(mouseX, mouseY)
            }
            OverrideRow.SPREAD -> when (armed.action) {
                OverrideAction.RESET -> lastSpreadResetBounds.contains(mouseX, mouseY)
                OverrideAction.PREV -> lastSpreadPrevBounds.contains(mouseX, mouseY)
                OverrideAction.NEXT -> lastSpreadNextBounds.contains(mouseX, mouseY) || lastSpreadBodyBounds.contains(mouseX, mouseY)
            }
        }
    }

    private fun cycleRow(uuid: UUID, row: OverrideRow, direction: Int, model: CalcRenderModel) {
        when (row) {
            OverrideRow.ITEM -> CalcComputationService.cycleItem(uuid, model.opponentSet.itemAlternatives.size, direction)
            OverrideRow.ABILITY -> CalcComputationService.cycleAbility(uuid, model.opponentSet.abilityAlternatives.size, direction)
            OverrideRow.SPREAD -> CalcComputationService.cycleSpread(uuid, model.opponentSet.spreadAlternatives.size, direction)
        }
    }

    private fun drawTabs(
        context: DrawContext,
        startX: Int,
        startY: Int,
        availableWidth: Int,
        tabs: List<CalcPreviewTab>,
        team: List<CalcPokemonSnapshot>,
        textScale: Float,
        uiScale: Float
    ): List<TabBounds> {
        if (tabs.isEmpty()) return emptyList()

        val tabH = (TAB_HEIGHT * uiScale).roundToInt().coerceAtLeast(8)
        val tabGap = (TAB_GAP * uiScale).roundToInt().coerceAtLeast(2)
        val hpByUuid: Map<UUID, Pair<Int, Int>> = team.associate { it.uuid to (it.currentHp to it.maxHp) }
        val columns = tabColumns(tabs.size, availableWidth)
        val tabWidth = ((availableWidth - (columns - 1) * tabGap) / columns).coerceAtLeast(TAB_MIN_WIDTH)
        val tabTextScale = (textScale - TAB_TEXT_SCALE_OFFSET).coerceAtLeast(TAB_TEXT_SCALE_MIN)
        val bounds = mutableListOf<TabBounds>()
        val labelInsetX = (3 * uiScale).roundToInt().coerceAtLeast(1)
        val labelInsetY = (3 * uiScale).roundToInt().coerceAtLeast(1)
        val hpBarH = (2 * uiScale).roundToInt().coerceAtLeast(1)

        tabs.forEachIndexed { index, tab ->
            val row = index / columns
            val column = index % columns
            val x = startX + column * (tabWidth + tabGap)
            val y = startY + row * (tabH + tabGap)
            val background = when {
                tab.isDisabled -> UIUtils.color(45, 28, 32, 235)
                tab.isSelected -> UIUtils.color(34, 68, 56, 235)
                tab.isActive -> UIUtils.color(30, 44, 62, 235)
                else -> UIUtils.color(23, 33, 49, 235)
            }
            val accent = when {
                tab.isDisabled -> UIUtils.color(150, 90, 95, 255)
                tab.isSelected -> V2_ACCENT_GREEN
                tab.isActive -> V2_ACCENT_BLUE
                else -> V2_TEXT_LABEL
            }
            context.fill(x, y, x + tabWidth, y + tabH, background)
            context.fill(x, y, x + tabWidth, y + 1, accent)

            val hp = hpByUuid[tab.uuid]
            if (hp != null) {
                val max = hp.second.coerceAtLeast(1)
                val pct = ((hp.first * 100.0) / max).coerceIn(0.0, 100.0)
                val innerW = (tabWidth - 2).coerceAtLeast(1)
                val barTop = y + tabH - hpBarH - 1
                val barBot = y + tabH - 1
                context.fill(x + 1, barTop, x + 1 + innerW, barBot, HP_TRACK)
                if (hp.first > 0) {
                    val fillW = ((innerW * pct) / 100.0).roundToInt().coerceAtLeast(1)
                    val col = when {
                        pct > 50 -> HP_GOOD
                        pct > 20 -> HP_MID
                        else -> HP_LOW
                    }
                    context.fill(x + 1, barTop, x + 1 + fillW, barBot, col)
                }
            }

            val rawLabel = buildString {
                if (tab.isActive) append("*")
                append(tab.label)
            }
            val fitted = truncateToWidth(rawLabel, (tabWidth - 6).coerceAtLeast(6), tabTextScale)
            UIUtils.drawText(
                context,
                fitted,
                (x + labelInsetX).toFloat(),
                (y + labelInsetY).toFloat(),
                if (tab.isDisabled) V2_TEXT_LABEL else V2_TEXT,
                tabTextScale
            )
            if (tab.isDisabled) {
                val strikeY = y + (tabH / 2)
                context.fill(x + 2, strikeY, x + tabWidth - 2, strikeY + 1, UIUtils.color(200, 120, 120, 200))
            }
            bounds += TabBounds(tab.uuid, x, y, tabWidth, tabH, tab.isDisabled)
        }

        return bounds
    }

    private fun tabBlockHeight(tabs: List<CalcPreviewTab>, availableWidth: Int, uiScale: Float): Int {
        if (tabs.isEmpty()) return 0
        val tabH = (TAB_HEIGHT * uiScale).roundToInt().coerceAtLeast(8)
        val tabGap = (TAB_GAP * uiScale).roundToInt().coerceAtLeast(2)
        val pad = (6 * uiScale).roundToInt().coerceAtLeast(2)
        val columns = tabColumns(tabs.size, availableWidth)
        val rows = ceil(tabs.size / columns.toDouble()).toInt()
        return rows * tabH + (rows - 1) * tabGap + pad
    }

    private fun tabColumns(tabCount: Int, availableWidth: Int): Int {
        val minTabWidth = 22
        val maxByWidth = ((availableWidth + TAB_GAP) / (minTabWidth + TAB_GAP)).coerceAtLeast(1)
        return tabCount.coerceAtMost(maxByWidth).coerceAtLeast(1)
    }

    private fun drawMoveRow(
        context: DrawContext,
        startX: Int,
        y: Float,
        row: CalcMoveRow,
        color: Int,
        scale: Float,
        uiScale: Float,
        availableWidth: Int,
        defenderHpPct: Int
    ) {
        val presentation = CalcPresentation.styleForOutcome(row.outcome, row.supported)
        val confStyle = CalcPresentation.styleForConfidence(row.confidence, row.supported && row.outcome != CalcMoveOutcome.UNSUPPORTED)
        val yInt = y.toInt()
        fun s(n: Int): Int = (n * uiScale).roundToInt().coerceAtLeast(1)
        val rowHeight = s(10).coerceAtLeast(6)

        // 1px left rail for confidence / unsupported state
        if (confStyle.showRail) {
            context.fill(startX - 2, yInt, startX - 1, yInt + rowHeight, confStyle.railColor)
        }

        val trFont = MinecraftClient.getInstance().textRenderer
        val measuredKo = ceil(trFont.getWidth(row.koText) * scale).toInt()
        val measuredDamage = ceil(trFont.getWidth(row.damageText) * scale).toInt()

        val layout = CalcMoveRowLayout.calculate(
            availableWidth = availableWidth,
            measuredDamageWidth = measuredDamage,
            measuredKoWidth = measuredKo
        )

        val moveTextColor = if (confStyle.isErrorLike) UIUtils.color(160, 112, 112) else color

        // Truncate move name to allocated region
        if (layout.moveNameWidth > 0) {
            val safeName = truncateToWidth(row.moveName, layout.moveNameWidth, scale)
            if (safeName.isNotEmpty()) {
                UIUtils.drawText(context, safeName, (startX + layout.moveNameX).toFloat(), y, moveTextColor, scale)
            }
        }

        // Truncate damage text to allocated region
        if (layout.damageVisible && layout.damageWidth > 0) {
            val safeDamage = truncateToWidth(row.damageText, layout.damageWidth, scale)
            if (safeDamage.isNotEmpty()) {
                UIUtils.drawText(context, safeDamage, (startX + layout.damageX).toFloat(), y, V2_TEXT_DIM, scale)
            }
        }

        // Truncate KO text to allocated region
        if (layout.koVisible && layout.koWidth > 0) {
            val safeKo = truncateToWidth(row.koText, layout.koWidth, scale)
            if (safeKo.isNotEmpty()) {
                UIUtils.drawText(context, safeKo, (startX + layout.koX).toFloat(), y, presentation.labelColor, scale)
            }
        }

        // Damage-vs-HP bar under the name (never for STATUS or UNSUPPORTED)
        if (presentation.showDamageBar && row.minPercent != null && row.maxPercent != null) {
            val barYOffset = (7 * uiScale).roundToInt().coerceAtLeast(3)
            val desiredBarWidth = (78 * uiScale).roundToInt().coerceAtLeast(20)
            val barWidth = minOf(desiredBarWidth, availableWidth)
            if (barWidth >= 4) {
                drawDamageBar(
                    context = context,
                    x = startX,
                    y = yInt + barYOffset,
                    w = barWidth,
                    minDmgPct = row.minPercent,
                    maxDmgPct = row.maxPercent,
                    defenderHpPct = defenderHpPct,
                    presentation = presentation,
                    uiScale = uiScale
                )
            }
        }
    }

    /**
     * Damage-vs-HP bar: shows guaranteed remaining HP and risk zone.
     * Guaranteed OHKO uses solid red; Likely OHKO uses an alternating hatched risk fill.
     */
    private fun drawDamageBar(
        context: DrawContext, x: Int, y: Int, w: Int,
        minDmgPct: Double, maxDmgPct: Double,
        defenderHpPct: Int,
        presentation: CalcOutcomePresentation,
        uiScale: Float = 1.0f
    ) {
        val width = w.coerceAtLeast(4)
        val h = (4 * uiScale).roundToInt().coerceAtLeast(2)
        context.fill(x, y, x + width, y + h, HP_TRACK)

        val currentHp = defenderHpPct.coerceIn(0, 100).toDouble()
        val hpAfterMax = (currentHp - maxDmgPct).coerceAtLeast(0.0)
        val currentEnd = ((width * currentHp) / 100.0).roundToInt().coerceIn(0, width)
        val greenEnd = ((width * hpAfterMax) / 100.0).roundToInt().coerceIn(0, currentEnd)

        // Guaranteed remaining HP (solid, HP-colored by post-hit HP%).
        if (greenEnd > 0) {
            val hpCol = when {
                hpAfterMax > 50 -> HP_GOOD
                hpAfterMax > 20 -> HP_MID
                else -> HP_LOW
            }
            context.fill(x, y, x + greenEnd, y + h, hpCol)
        }

        // HP at risk (severity color, slightly dimmed so it reads as "damage zone").
        if (currentEnd > greenEnd) {
            val sevColor = presentation.barColor
            val dimmed = (sevColor and 0x00FFFFFF) or (0xC0 shl 24)
            if (presentation.fillStyle == CalcRiskFillStyle.HATCHED) {
                val startRiskX = x + greenEnd
                val endRiskX = x + currentEnd
                val hatchBg = (sevColor and 0x00FFFFFF) or (0x40 shl 24)
                var stripeX = startRiskX
                while (stripeX < endRiskX) {
                    val stripeW = minOf(2, endRiskX - stripeX)
                    val isEven = ((stripeX - startRiskX) / 2) % 2 == 0
                    val stripeCol = if (isEven) dimmed else hatchBg
                    context.fill(stripeX, y, stripeX + stripeW, y + h, stripeCol)
                    stripeX += stripeW
                }
            } else {
                context.fill(x + greenEnd, y, x + currentEnd, y + h, dimmed)
            }
        }

        // 100% benchmark tick at right edge
        if (defenderHpPct >= 99) {
            context.fill(x + width - 1, y - 1, x + width, y + h, UIUtils.color(255, 255, 255, 64))
        }
    }

    private fun drawHpBar(context: DrawContext, x: Int, y: Int, w: Int, pct: Double, uiScale: Float = 1.0f) {
        val width = w.coerceAtLeast(4)
        val h = (4 * uiScale).roundToInt().coerceAtLeast(2)
        context.fill(x, y, x + width, y + h, HP_TRACK)
        val clamped = pct.coerceIn(0.0, 100.0)
        val fill = ((width * clamped) / 100.0).roundToInt().coerceAtLeast(if (pct > 0) 1 else 0)
        val col = when {
            clamped > 50 -> HP_GOOD
            clamped > 20 -> HP_MID
            else -> HP_LOW
        }
        context.fill(x, y, x + fill, y + h, col)
    }

    private fun drawMatchupSection(
        context: DrawContext,
        cellX: Int,
        y: Int,
        cellW: Int,
        player: CalcPokemonSnapshot?,
        opponent: CalcPokemonSnapshot?,
        speedText: String?,
        textScale: Float,
        uiScale: Float,
        compact: Boolean
    ): Int {
        fun s(n: Int): Int = (n * uiScale).roundToInt().coerceAtLeast(1)
        val youPct = hpPercent(player)
        val oppPct = hpPercent(opponent)
        val (arrow, arrowColor) = speedArrow(speedText)
        val trFont = MinecraftClient.getInstance().textRenderer

        if (compact) {
            val compactAvailableW = maxOf(0, cellW - 12)
            val measurer = { text: String -> (trFont.getWidth(text) * textScale).toInt() }
            val compactMatchup = CalcCompactMatchupLayout.calculate(
                availableWidth = compactAvailableW,
                youPct = youPct,
                oppPct = oppPct,
                arrow = arrow,
                measurer = measurer,
                youWord = tr("deltacalc.calc.matchup.you"),
                oppWord = tr("deltacalc.calc.matchup.opp"),
                gap = s(4)
            )

            if (compactMatchup.youWidth > 0) {
                val safeYou = truncateToWidth(compactMatchup.youText, compactMatchup.youWidth, textScale)
                UIUtils.drawText(context, safeYou, (cellX + 6 + compactMatchup.youX).toFloat(), (y + 1).toFloat(), V2_TEXT, textScale)
            }
            if (compactMatchup.arrowWidth > 0) {
                val safeArrow = truncateToWidth(compactMatchup.arrowText, compactMatchup.arrowWidth, textScale)
                UIUtils.drawText(context, safeArrow, (cellX + 6 + compactMatchup.arrowX).toFloat(), (y + 1).toFloat(), arrowColor, textScale)
            }
            if (compactMatchup.oppWidth > 0) {
                val safeOpp = truncateToWidth(compactMatchup.oppText, compactMatchup.oppWidth, textScale)
                UIUtils.drawText(context, safeOpp, (cellX + 6 + compactMatchup.oppX).toFloat(), (y + 1).toFloat(), V2_TEXT, textScale)
            }
            return y + s(12).coerceAtLeast(7)
        }

        val colW = ((cellW - 12 - 3) / 2).coerceAtLeast(40)
        val youX = cellX + 6
        val oppX = cellX + 6 + colW + 3
        UIUtils.drawText(context, tr("deltacalc.calc.matchup.you_wide"), youX.toFloat(), y.toFloat(), V2_TEXT_LABEL, textScale)
        UIUtils.drawText(context, tr("deltacalc.calc.matchup.opp_wide"), oppX.toFloat(), y.toFloat(), V2_TEXT_LABEL, textScale)

        val arrowW = (trFont.getWidth(arrow) * textScale).toInt()
        UIUtils.drawText(context, arrow, (cellX + 6 + colW + 3 - (arrowW / 2) - s(2)).toFloat(), y.toFloat(), arrowColor, textScale)

        val nameY = y + s(9)
        val nameW = (colW - s(4)).coerceAtLeast(s(20).coerceAtLeast(12))
        player?.let {
            UIUtils.drawText(context, truncateToWidth(it.displayName, nameW, textScale), youX.toFloat(), nameY.toFloat(), V2_TEXT, textScale)
        }
        opponent?.let {
            UIUtils.drawText(context, truncateToWidth(it.displayName, nameW, textScale), oppX.toFloat(), nameY.toFloat(), V2_TEXT, textScale)
        }

        val hpY = y + s(19)
        val barW = (colW - s(24)).coerceAtLeast(s(20).coerceAtLeast(12))
        drawHpBar(context, youX, hpY, barW, youPct.toDouble(), uiScale)
        UIUtils.drawText(context, "${youPct}%", (youX + barW + s(2)).toFloat(), (hpY - 1).toFloat(), V2_TEXT_DIM, textScale)
        drawHpBar(context, oppX, hpY, barW, oppPct.toDouble(), uiScale)
        UIUtils.drawText(context, "${oppPct}%", (oppX + barW + s(2)).toFloat(), (hpY - 1).toFloat(), V2_TEXT_DIM, textScale)
        UIUtils.drawPopupRowDivider(context, cellX, cellW, y + s(26))
        return y + s(30)
    }

    private fun speedArrow(speedText: String?): Pair<String, Int> {
        if (speedText == null) return ">" to V2_TEXT_LABEL
        val lc = speedText.lowercase()
        return when {
            "you likely move first" in lc -> ">" to V2_ACCENT_YELLOW
            "you move first" in lc -> ">" to HP_GOOD
            "opponent likely moves first" in lc -> "<" to V2_ACCENT_YELLOW
            "opponent moves first" in lc -> "<" to HP_LOW
            else -> "=" to V2_TEXT_LABEL
        }
    }

    private fun hpPercent(p: CalcPokemonSnapshot?): Int {
        if (p == null) return 0
        val max = p.maxHp.coerceAtLeast(1)
        return ((p.currentHp * 100.0) / max).roundToInt().coerceIn(0, 100)
    }

    private fun truncateToWidth(text: String, maxScaledWidth: Int, scale: Float): String {
        if (maxScaledWidth <= 0 || text.isEmpty()) return ""
        val trFont = MinecraftClient.getInstance().textRenderer
        if (ceil(trFont.getWidth(text) * scale).toInt() <= maxScaledWidth) return text
        var out = text
        while (out.isNotEmpty()) {
            val candidate = "$out…"
            if (ceil(trFont.getWidth(candidate) * scale).toInt() <= maxScaledWidth) return candidate
            out = out.substring(0, out.length - 1)
        }
        return ""
    }

    private fun pillLabel(state: InferenceValueState, isOverride: Boolean): String {
        return when {
            isOverride -> tr("deltacalc.calc.pill.manual")
            state == InferenceValueState.REVEALED -> tr("deltacalc.calc.pill.seen")
            state == InferenceValueState.GUESSED -> tr("deltacalc.calc.pill.likely")
            else -> tr("deltacalc.calc.pill.unknown")
        }
    }

    private fun drawStatePill(
        context: DrawContext,
        x: Int,
        y: Int,
        state: InferenceValueState,
        scale: Float,
        uiScale: Float = 1.0f,
        isOverride: Boolean = false
    ) {
        val (bg, fg) = when {
            isOverride -> PILL_MANUAL_BG to V2_ACCENT_BLUE
            state == InferenceValueState.REVEALED -> PILL_SEEN_BG to V2_ACCENT_GREEN
            state == InferenceValueState.GUESSED -> PILL_LIKELY_BG to V2_ACCENT_YELLOW
            else -> PILL_UNK_BG to V2_TEXT_DIM
        }
        val label = pillLabel(state, isOverride)
        val pillScale = (scale - 0.15f).coerceAtLeast(0.42f)
        val tw = (MinecraftClient.getInstance().textRenderer.getWidth(label) * pillScale).toInt()
        val padX = CalcPresentation.pillPaddingX(uiScale)
        val padY = (1 * uiScale).roundToInt().coerceAtLeast(1)
        val pillW = CalcPresentation.pillTotalWidth(tw, uiScale)
        val pillH = (8 * uiScale).roundToInt().coerceAtLeast(5)
        context.fill(x, y, x + pillW, y + pillH, bg)
        UIUtils.drawText(context, label, (x + padX).toFloat(), (y + padY).toFloat(), fg, pillScale)
    }

    private fun drawResizeHandle(context: DrawContext, x: Int, y: Int, width: Int, height: Int) {
        val isHovered = interaction.isResizing || interaction.hoveredZone != UIUtils.ResizeZone.NONE
        val color = if (isHovered) V2_ACCENT_BLUE else UIUtils.color(120, 140, 160, 255)
        UIUtils.drawCornerHandle(
            context = context,
            cornerX = x + width - 5,
            cornerY = y + height - 5,
            length = 6,
            thickness = 2,
            color = color,
            bottomRight = true
        )
    }

    private fun findTabAt(mouseX: Int, mouseY: Int, tabs: List<TabBounds>): TabBounds? {
        return tabs.firstOrNull { contains(mouseX, mouseY, it.x, it.y, it.width, it.height) }
    }

    private fun contains(mouseX: Int, mouseY: Int, x: Int, y: Int, width: Int, height: Int): Boolean {
        return CalcPresentation.containsHalfOpen(mouseX, mouseY, x, y, width, height)
    }

    private fun formatInference(value: String?): String {
        return value ?: tr("deltacalc.calc.unknown")
    }

    private fun normalizeForUsageLookup(value: String?): String {
        return value.orEmpty().lowercase()
            .replace(" ", "")
            .replace("-", "")
            .replace("_", "")
            .replace("'", "")
            .replace(".", "")
    }

    private fun buildOverrideRowText(value: String, usagePercent: Double?): String {
        val pct = usagePercent?.takeIf { it > 0.0 }?.let { " (${it.roundToInt()}%)" } ?: ""
        return "$value$pct"
    }

    private fun speedColor(speedText: String): Int {
        val lc = speedText.lowercase()
        return when {
            "you move first" in lc -> UIUtils.color(120, 235, 170)
            "you likely move first" in lc -> UIUtils.color(150, 220, 175)
            "opponent moves first" in lc -> UIUtils.color(255, 145, 125)
            "opponent likely moves first" in lc -> UIUtils.color(245, 175, 120)
            else -> UIUtils.color(220, 205, 120)
        }
    }

    private data class TabBounds(
        val uuid: UUID,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val disabled: Boolean
    )

    private data class PendingTabSelection(
        val uuid: UUID,
        val isPlayerSide: Boolean
    )

    private enum class SectionToggle {
        TEAM,
        MOVES
    }
}
