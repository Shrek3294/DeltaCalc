package com.cobblemonextendedbattleui.pokemon.render

import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemonextendedbattleui.PanelConfig
import com.cobblemonextendedbattleui.UIUtils
import com.cobblemonextendedbattleui.pokemon.tooltip.TooltipBoundsData
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text

/**
 * Renders team indicator panel backgrounds, borders, corners, and help icons.
 */
object TeamPanelRenderer {

    // Background panel settings
    private const val PANEL_PADDING_V = 2
    private const val PANEL_PADDING_H = 5
    private const val PANEL_CORNER = 3
    private val PANEL_BG = color(15, 20, 25, 180)
    private val PANEL_BORDER = color(60, 70, 85, 200)

    // Help icon settings
    private const val HELP_ICON_SIZE = 8
    private const val HELP_ICON_MARGIN = 2

    /**
     * Calculate panel dimensions based on team size and current orientation/scale.
     */
    fun calculatePanelDimensions(teamSize: Int, modelSize: Int, modelSpacing: Int): Pair<Int, Int> {
        if (teamSize <= 0) return Pair(0, 0)

        val isVertical = PanelConfig.teamIndicatorOrientation == PanelConfig.TeamIndicatorOrientation.VERTICAL
        return if (isVertical) {
            val panelWidth = modelSize + PANEL_PADDING_H * 2
            val panelHeight = teamSize * modelSize + (teamSize - 1) * modelSpacing + PANEL_PADDING_V * 2
            Pair(panelWidth, panelHeight)
        } else {
            val panelWidth = teamSize * modelSize + (teamSize - 1) * modelSpacing + PANEL_PADDING_H * 2
            val panelHeight = modelSize + PANEL_PADDING_V * 2
            Pair(panelWidth, panelHeight)
        }
    }

    /**
     * Draw a background panel behind the team's Pokemon models.
     */
    fun drawTeamPanel(
        context: DrawContext,
        x: Int,
        y: Int,
        teamSize: Int,
        modelSize: Int,
        modelSpacing: Int,
        applyOpacity: (Int) -> Int
    ) {
        if (teamSize <= 0) return

        val (panelWidth, panelHeight) = calculatePanelDimensions(teamSize, modelSize, modelSpacing)

        val panelX = x - PANEL_PADDING_H
        val panelY = y - PANEL_PADDING_V

        val bg = applyOpacity(PANEL_BG)
        val border = applyOpacity(PANEL_BORDER)

        // Draw main background (cross pattern for rounded corners)
        context.fill(panelX + PANEL_CORNER, panelY, panelX + panelWidth - PANEL_CORNER, panelY + panelHeight, bg)
        context.fill(panelX, panelY + PANEL_CORNER, panelX + panelWidth, panelY + panelHeight - PANEL_CORNER, bg)

        // Fill corners with graduated rounding
        // Top-left
        context.fill(panelX + 2, panelY + 1, panelX + PANEL_CORNER, panelY + 2, bg)
        context.fill(panelX + 1, panelY + 2, panelX + PANEL_CORNER, panelY + PANEL_CORNER, bg)
        // Top-right
        context.fill(panelX + panelWidth - PANEL_CORNER, panelY + 1, panelX + panelWidth - 2, panelY + 2, bg)
        context.fill(panelX + panelWidth - PANEL_CORNER, panelY + 2, panelX + panelWidth - 1, panelY + PANEL_CORNER, bg)
        // Bottom-left
        context.fill(panelX + 2, panelY + panelHeight - 2, panelX + PANEL_CORNER, panelY + panelHeight - 1, bg)
        context.fill(panelX + 1, panelY + panelHeight - PANEL_CORNER, panelX + PANEL_CORNER, panelY + panelHeight - 2, bg)
        // Bottom-right
        context.fill(panelX + panelWidth - PANEL_CORNER, panelY + panelHeight - 2, panelX + panelWidth - 2, panelY + panelHeight - 1, bg)
        context.fill(panelX + panelWidth - PANEL_CORNER, panelY + panelHeight - PANEL_CORNER, panelX + panelWidth - 1, panelY + panelHeight - 2, bg)

        // Draw borders
        // Top
        context.fill(panelX + PANEL_CORNER, panelY, panelX + panelWidth - PANEL_CORNER, panelY + 1, border)
        // Bottom
        context.fill(panelX + PANEL_CORNER, panelY + panelHeight - 1, panelX + panelWidth - PANEL_CORNER, panelY + panelHeight, border)
        // Left
        context.fill(panelX, panelY + PANEL_CORNER, panelX + 1, panelY + panelHeight - PANEL_CORNER, border)
        // Right
        context.fill(panelX + panelWidth - 1, panelY + PANEL_CORNER, panelX + panelWidth, panelY + panelHeight - PANEL_CORNER, border)

        // Rounded corner borders
        drawCornerBorders(context, panelX, panelY, panelWidth, panelHeight, border)
    }

    /**
     * Draw corner overlays AFTER models to ensure rounded corners appear on top of model overflow.
     */
    fun drawPanelCornerOverlays(
        context: DrawContext,
        x: Int,
        y: Int,
        teamSize: Int,
        modelSize: Int,
        modelSpacing: Int,
        applyOpacity: (Int) -> Int
    ) {
        if (teamSize <= 0) return

        val (panelWidth, panelHeight) = calculatePanelDimensions(teamSize, modelSize, modelSpacing)
        val panelX = x - PANEL_PADDING_H
        val panelY = y - PANEL_PADDING_V
        val border = applyOpacity(PANEL_BORDER)

        val matrices = context.matrices
        matrices.push()
        matrices.translate(0.0, 0.0, 200.0)

        drawCornerBorders(context, panelX, panelY, panelWidth, panelHeight, border)

        matrices.pop()
    }

    /**
     * Draw a small help icon ("?") in the corner of the panel.
     */
    fun drawHelpIcon(
        context: DrawContext,
        panelX: Int,
        panelY: Int,
        panelWidth: Int,
        panelHeight: Int,
        isLeftSide: Boolean,
        applyOpacity: (Int) -> Int
    ): TooltipBoundsData {
        val mc = MinecraftClient.getInstance()
        val mouseX = (mc.mouse.x * mc.window.scaledWidth / mc.window.width).toInt()
        val mouseY = (mc.mouse.y * mc.window.scaledHeight / mc.window.height).toInt()

        val iconX = if (isLeftSide) {
            panelX + panelWidth - HELP_ICON_SIZE - HELP_ICON_MARGIN
        } else {
            panelX + HELP_ICON_MARGIN
        }
        val iconY = panelY + panelHeight - HELP_ICON_SIZE - HELP_ICON_MARGIN

        val bounds = TooltipBoundsData(iconX, iconY, HELP_ICON_SIZE, HELP_ICON_SIZE)

        val isHovered = mouseX >= iconX && mouseX <= iconX + HELP_ICON_SIZE &&
            mouseY >= iconY && mouseY <= iconY + HELP_ICON_SIZE

        val bgColor = if (isHovered) color(70, 85, 105, 230) else color(45, 55, 70, 180)
        val borderColor = if (isHovered) color(100, 120, 150, 255) else color(70, 80, 100, 200)
        val textColor = if (isHovered) color(240, 245, 250, 255) else color(140, 155, 175, 220)

        val matrices = context.matrices
        matrices.push()
        matrices.translate(0.0, 0.0, 250.0)

        // Draw circular background
        val bg = applyOpacity(bgColor)
        context.fill(iconX, iconY + 2, iconX + HELP_ICON_SIZE, iconY + 6, bg)
        context.fill(iconX + 1, iconY + 1, iconX + HELP_ICON_SIZE - 1, iconY + 2, bg)
        context.fill(iconX + 1, iconY + 6, iconX + HELP_ICON_SIZE - 1, iconY + 7, bg)
        context.fill(iconX + 2, iconY, iconX + HELP_ICON_SIZE - 2, iconY + 1, bg)
        context.fill(iconX + 2, iconY + 7, iconX + HELP_ICON_SIZE - 2, iconY + 8, bg)

        // Draw circular border
        val border = applyOpacity(borderColor)
        context.fill(iconX + 2, iconY, iconX + HELP_ICON_SIZE - 2, iconY + 1, border)
        context.fill(iconX + 2, iconY + HELP_ICON_SIZE - 1, iconX + HELP_ICON_SIZE - 2, iconY + HELP_ICON_SIZE, border)
        context.fill(iconX, iconY + 2, iconX + 1, iconY + HELP_ICON_SIZE - 2, border)
        context.fill(iconX + HELP_ICON_SIZE - 1, iconY + 2, iconX + HELP_ICON_SIZE, iconY + HELP_ICON_SIZE - 2, border)
        // Corner pixels
        context.fill(iconX + 1, iconY + 1, iconX + 2, iconY + 2, border)
        context.fill(iconX + HELP_ICON_SIZE - 2, iconY + 1, iconX + HELP_ICON_SIZE - 1, iconY + 2, border)
        context.fill(iconX + 1, iconY + HELP_ICON_SIZE - 2, iconX + 2, iconY + HELP_ICON_SIZE - 1, border)
        context.fill(iconX + HELP_ICON_SIZE - 2, iconY + HELP_ICON_SIZE - 2, iconX + HELP_ICON_SIZE - 1, iconY + HELP_ICON_SIZE - 1, border)

        // Draw "?" text
        val helpText = "?"
        val textRenderer = mc.textRenderer
        val textScale = 0.7f
        val textWidth = textRenderer.getWidth(helpText) * textScale
        val textHeight = textRenderer.fontHeight * textScale
        val textXPos = iconX + (HELP_ICON_SIZE / 2.0f) - (textWidth / 2.0f) + 0.5f
        val textYPos = iconY + (HELP_ICON_SIZE / 2.0f) - (textHeight / 2.0f) + 1.0f

        drawScaledText(
            context = context,
            text = Text.literal(helpText),
            x = textXPos,
            y = textYPos,
            colour = applyOpacity(textColor),
            scale = textScale,
            shadow = false
        )

        matrices.pop()

        return bounds
    }

    /**
     * Render control hints below the panel when hovering the help icon.
     */
    fun renderControlHints(
        context: DrawContext,
        panelBounds: TooltipBoundsData,
        isLeftSide: Boolean,
        isCustomized: Boolean,
        repositioningEnabled: Boolean,
        applyOpacity: (Int) -> Int
    ) {
        val mc = MinecraftClient.getInstance()
        val textRenderer = mc.textRenderer
        val screenWidth = mc.window.scaledWidth
        val screenHeight = mc.window.scaledHeight

        val hints = buildList {
            add(Pair("Shift+Click", ": Flip"))
            add(Pair("  \u2022  ", ""))
            if (repositioningEnabled) {
                add(Pair("Drag", ": Move"))
                add(Pair("  \u2022  ", ""))
                add(Pair("Dbl-Click", ": Reset"))
                add(Pair("  \u2022  ", ""))
            }
            add(Pair("Ctrl+Scroll", ": Scale"))
            add(Pair("  \u2022  ", ""))
            add(Pair("Alt", ": Both"))
        }

        val hintScale = 0.7f
        val hintText = hints.joinToString("") { it.first + it.second }
        val hintWidth = (textRenderer.getWidth(hintText) * hintScale).toInt() + 8
        val hintHeight = (textRenderer.fontHeight * hintScale).toInt() + 4

        var hintX = panelBounds.x + (panelBounds.width / 2) - (hintWidth / 2)
        var hintY = panelBounds.y + panelBounds.height + 2

        hintX = hintX.coerceIn(2, screenWidth - hintWidth - 2)
        if (hintY + hintHeight > screenHeight - 2) {
            hintY = panelBounds.y - hintHeight - 2
        }

        val bgColor = color(15, 20, 25, 200)
        val borderColor = color(50, 60, 70, 200)
        val keyColor = if (isCustomized) color(180, 200, 140, 255) else color(140, 160, 180, 255)
        val textColor = color(100, 110, 120, 255)
        val separatorColor = color(70, 80, 90, 255)

        val matrices = context.matrices
        matrices.push()
        matrices.translate(0.0, 0.0, 400.0)

        context.fill(hintX, hintY, hintX + hintWidth, hintY + hintHeight, applyOpacity(bgColor))
        context.fill(hintX, hintY, hintX + hintWidth, hintY + 1, applyOpacity(borderColor))
        context.fill(hintX, hintY + hintHeight - 1, hintX + hintWidth, hintY + hintHeight, applyOpacity(borderColor))

        var textX = (hintX + 4).toFloat()
        val textY = (hintY + 2).toFloat()

        for ((key, action) in hints) {
            val clr = when {
                key.contains("\u2022") -> separatorColor
                action.isEmpty() -> separatorColor
                else -> keyColor
            }
            drawScaledText(
                context = context,
                text = Text.literal(key),
                x = textX,
                y = textY,
                colour = applyOpacity(clr),
                scale = hintScale,
                shadow = false
            )
            textX += textRenderer.getWidth(key) * hintScale

            if (action.isNotEmpty()) {
                drawScaledText(
                    context = context,
                    text = Text.literal(action),
                    x = textX,
                    y = textY,
                    colour = applyOpacity(textColor),
                    scale = hintScale,
                    shadow = false
                )
                textX += textRenderer.getWidth(action) * hintScale
            }
        }

        matrices.pop()
    }

    // ─── Internal Helpers ───────────────────────────────────────────────────

    private fun drawCornerBorders(
        context: DrawContext,
        panelX: Int,
        panelY: Int,
        panelWidth: Int,
        panelHeight: Int,
        border: Int
    ) {
        // Top-left
        context.fill(panelX + 2, panelY, panelX + PANEL_CORNER, panelY + 1, border)
        context.fill(panelX + 1, panelY + 1, panelX + 2, panelY + 2, border)
        context.fill(panelX, panelY + 2, panelX + 1, panelY + PANEL_CORNER, border)
        // Top-right
        context.fill(panelX + panelWidth - PANEL_CORNER, panelY, panelX + panelWidth - 2, panelY + 1, border)
        context.fill(panelX + panelWidth - 2, panelY + 1, panelX + panelWidth - 1, panelY + 2, border)
        context.fill(panelX + panelWidth - 1, panelY + 2, panelX + panelWidth, panelY + PANEL_CORNER, border)
        // Bottom-left
        context.fill(panelX + 2, panelY + panelHeight - 1, panelX + PANEL_CORNER, panelY + panelHeight, border)
        context.fill(panelX + 1, panelY + panelHeight - 2, panelX + 2, panelY + panelHeight - 1, border)
        context.fill(panelX, panelY + panelHeight - PANEL_CORNER, panelX + 1, panelY + panelHeight - 2, border)
        // Bottom-right
        context.fill(panelX + panelWidth - PANEL_CORNER, panelY + panelHeight - 1, panelX + panelWidth - 2, panelY + panelHeight, border)
        context.fill(panelX + panelWidth - 2, panelY + panelHeight - 2, panelX + panelWidth - 1, panelY + panelHeight - 1, border)
        context.fill(panelX + panelWidth - 1, panelY + panelHeight - PANEL_CORNER, panelX + panelWidth, panelY + panelHeight - 2, border)
    }

    private fun color(r: Int, g: Int, b: Int, a: Int = 255): Int = UIUtils.color(r, g, b, a)
}
