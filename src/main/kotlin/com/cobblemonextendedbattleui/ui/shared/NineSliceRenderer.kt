package com.cobblemonextendedbattleui.ui.shared

import net.minecraft.client.gui.DrawContext
import net.minecraft.util.Identifier

/**
 * General-purpose 9-slice texture renderer.
 *
 * Splits a texture into 9 regions (4 corners, 4 edges, 1 center) and renders
 * them at any target size while preserving corner/edge fidelity. Edges stretch
 * in one direction, the center stretches in both.
 */
object NineSliceRenderer {

    /**
     * Describes the slice insets for a 9-slice texture.
     */
    data class SliceInsets(
        val left: Int,
        val right: Int,
        val top: Int,
        val bottom: Int
    ) {
        constructor(uniform: Int) : this(uniform, uniform, uniform, uniform)
    }

    /**
     * Renders a texture using 9-slice scaling.
     *
     * @param context   The draw context
     * @param texture   The texture identifier
     * @param x         Render X position
     * @param y         Render Y position
     * @param width     Target render width
     * @param height    Target render height
     * @param texWidth  Full texture width in pixels
     * @param texHeight Full texture height in pixels
     * @param insets    Slice border sizes
     */
    fun render(
        context: DrawContext,
        texture: Identifier,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        texWidth: Int,
        texHeight: Int,
        insets: SliceInsets
    ) {
        val sl = insets.left
        val sr = insets.right
        val st = insets.top
        val sb = insets.bottom

        val centerTexW = texWidth - sl - sr
        val centerTexH = texHeight - st - sb
        val centerW = width - sl - sr
        val centerH = height - st - sb

        // Top-left corner
        context.drawTexture(
            texture, x, y, sl, st,
            0f, 0f, sl, st, texWidth, texHeight
        )

        // Top-right corner
        context.drawTexture(
            texture, x + width - sr, y, sr, st,
            (texWidth - sr).toFloat(), 0f, sr, st, texWidth, texHeight
        )

        // Bottom-left corner
        context.drawTexture(
            texture, x, y + height - sb, sl, sb,
            0f, (texHeight - sb).toFloat(), sl, sb, texWidth, texHeight
        )

        // Bottom-right corner
        context.drawTexture(
            texture, x + width - sr, y + height - sb, sr, sb,
            (texWidth - sr).toFloat(), (texHeight - sb).toFloat(), sr, sb, texWidth, texHeight
        )

        // Top edge (stretched horizontally)
        if (centerW > 0) {
            context.drawTexture(
                texture, x + sl, y, centerW, st,
                sl.toFloat(), 0f, centerTexW, st, texWidth, texHeight
            )
        }

        // Bottom edge (stretched horizontally)
        if (centerW > 0) {
            context.drawTexture(
                texture, x + sl, y + height - sb, centerW, sb,
                sl.toFloat(), (texHeight - sb).toFloat(), centerTexW, sb, texWidth, texHeight
            )
        }

        // Left edge (stretched vertically)
        if (centerH > 0) {
            context.drawTexture(
                texture, x, y + st, sl, centerH,
                0f, st.toFloat(), sl, centerTexH, texWidth, texHeight
            )
        }

        // Right edge (stretched vertically)
        if (centerH > 0) {
            context.drawTexture(
                texture, x + width - sr, y + st, sr, centerH,
                (texWidth - sr).toFloat(), st.toFloat(), sr, centerTexH, texWidth, texHeight
            )
        }

        // Center (stretched both ways)
        if (centerW > 0 && centerH > 0) {
            context.drawTexture(
                texture, x + sl, y + st, centerW, centerH,
                sl.toFloat(), st.toFloat(), centerTexW, centerTexH, texWidth, texHeight
            )
        }
    }
}
