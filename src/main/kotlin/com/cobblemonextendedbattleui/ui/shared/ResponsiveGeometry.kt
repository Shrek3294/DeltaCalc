package com.cobblemonextendedbattleui.ui.shared

/**
 * Immutable viewport representing the available screen dimensions in scaled GUI pixels.
 */
data class Viewport(
    val width: Int,
    val height: Int
)

/**
 * Immutable 2D integer point / coordinate.
 */
data class LayoutPoint(
    val x: Int,
    val y: Int
)

/**
 * Immutable 2D integer rectangle in scaled GUI pixels.
 */
data class LayoutRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
) {
    val right: Long get() = x.toLong() + width.toLong()
    val bottom: Long get() = y.toLong() + height.toLong()

    /**
     * Returns true if ([px], [py]) falls within this rectangle using standard half-open
     * geometry: `[x, x + width)` horizontally and `[y, y + height)` vertically.
     * Always returns false if [width] <= 0 or [height] <= 0.
     */
    fun contains(px: Int, py: Int): Boolean {
        if (width <= 0 || height <= 0) return false
        val pxL = px.toLong()
        val pyL = py.toLong()
        val xL = x.toLong()
        val yL = y.toLong()
        return pxL >= xL && pxL < xL + width.toLong() && pyL >= yL && pyL < yL + height.toLong()
    }
}

/**
 * Pure responsive layout and geometry calculations with zero Minecraft runtime dependencies.
 *
 * Guarantees that panel coordinates and dimensions always remain bounded, non-negative,
 * and reachable on-screen across arbitrary viewport sizes, window resizes, and GUI scales.
 * Never constructs invalid [Int.coerceIn] ranges where `max < min`.
 * Hardened against integer overflow on arbitrary persisted inputs including [Int.MIN_VALUE]
 * and [Int.MAX_VALUE].
 */
object ResponsiveGeometry {


    /**
     * Safely clamps a dimension (width or height) against a preferred minimum and a maximum allowed ceiling.
     *
     * Invariants guaranteed:
     * - Returns an integer in `[0, effectiveMax]` where `effectiveMax = max(0, maxAllowed)`.
     * - If [preferredMin] > [maxAllowed], [effectiveMax] takes precedence so content never exceeds the container.
     * - If [maxAllowed] <= 0, returns 0.
     * - Never constructs `coerceIn(min, max)` where `max < min`.
     * - Immune to integer overflow on arbitrary [preferredMin] or [maxAllowed] inputs.
     */
    fun safeClampDimension(
        value: Int,
        preferredMin: Int,
        maxAllowed: Int
    ): Int {
        if (maxAllowed <= 0) return 0
        val effectiveMax = maxAllowed
        val effectiveMin = if (preferredMin <= 0) 0 else minOf(preferredMin, effectiveMax)
        return value.coerceIn(effectiveMin, effectiveMax)
    }

    /**
     * Safely clamps a 1D coordinate ([candidate]) so that a widget of span [widgetSpan]
     * remains reachable within a viewport of span [viewportSpan].
     *
     * Invariants guaranteed:
     * - When `widgetSpan <= viewportSpan`: clamps [candidate] to `[0, viewportSpan - widgetSpan]`.
     * - When `widgetSpan > viewportSpan`: pins the coordinate to `0` (top/left edge stays in view).
     * - When `viewportSpan <= 0`: returns `0`.
     * - The returned coordinate is always >= 0.
     * - Immune to overflow on [Int.MIN_VALUE] / [Int.MAX_VALUE] inputs.
     */
    fun clampCoord(candidate: Int, widgetSpan: Int, viewportSpan: Int): Int {
        if (viewportSpan <= 0) return 0
        val safeSpan = if (widgetSpan <= 0) 0L else widgetSpan.toLong()
        val maxCoord = maxOf(0L, viewportSpan.toLong() - safeSpan)
        return candidate.toLong().coerceIn(0L, maxCoord).toInt()
    }

    /**
     * Clamps 2D coordinates so the widget stays within the viewport.
     */
    fun clampPosition(
        candidateX: Int,
        candidateY: Int,
        widgetWidth: Int,
        widgetHeight: Int,
        viewportWidth: Int,
        viewportHeight: Int
    ): LayoutPoint {
        return LayoutPoint(
            x = clampCoord(candidateX, widgetWidth, viewportWidth),
            y = clampCoord(candidateY, widgetHeight, viewportHeight)
        )
    }

    /**
     * Safely clamps a 1D coordinate ([candidate]) so that a widget of span [widgetSpan]
     * remains reachable within a viewport of span [viewportSpan], with a preferred [margin]
     * from the viewport boundaries.
     *
     * Invariants guaranteed:
     * - When `widgetSpan + 2 * margin <= viewportSpan`: clamps [candidate] to
     *   `[margin, viewportSpan - widgetSpan - margin]`.
     * - When `widgetSpan <= viewportSpan` but slack is insufficient for full margin on both sides:
     *   clamps [candidate] within `[0, viewportSpan - widgetSpan]` so the widget remains entirely in view.
     * - When `widgetSpan > viewportSpan`: pins the coordinate to `0` (top/left edge stays in view).
     * - When `viewportSpan <= 0`: returns `0`.
     * - Never constructs invalid clamp ranges where max < min.
     * - Immune to integer overflow on arbitrary inputs including [Int.MIN_VALUE] and [Int.MAX_VALUE].
     */
    fun clampCoordWithMargin(
        candidate: Int,
        widgetSpan: Int,
        viewportSpan: Int,
        margin: Int = 0
    ): Int {
        if (viewportSpan <= 0) return 0
        val safeSpan = if (widgetSpan <= 0) 0L else widgetSpan.toLong()
        val vp = viewportSpan.toLong()

        if (safeSpan >= vp) {
            return 0
        }

        val safeMargin = if (margin <= 0) 0L else margin.toLong()
        val totalSlack = vp - safeSpan

        val minCoord: Long
        val maxCoord: Long

        if (totalSlack >= 2 * safeMargin) {
            minCoord = safeMargin
            maxCoord = vp - safeSpan - safeMargin
        } else {
            minCoord = 0L
            maxCoord = totalSlack
        }

        return candidate.toLong().coerceIn(minCoord, maxCoord).toInt()
    }

    /**
     * Resolves a 1D coordinate between a preferred position and a fallback position
     * (e.g. below vs above, or right vs left), preserving intent where space exists,
     * and otherwise clamping to the nearest reachable in-viewport position with [margin].
     *
     * Invariants guaranteed:
     * - Returns [preferredCoord] if it fits within `[margin, viewportSpan - margin]`.
     * - Otherwise returns [fallbackCoord] (if provided) if it fits within `[margin, viewportSpan - margin]`.
     * - Otherwise clamps [preferredCoord] via [clampCoordWithMargin].
     * - Never constructs invalid ranges where max < min.
     * - Immune to overflow on arbitrary inputs.
     */
    fun resolvePlacementWithFallback(
        preferredCoord: Int,
        widgetSpan: Int,
        viewportSpan: Int,
        margin: Int = 0,
        fallbackCoord: Int? = null
    ): Int {
        if (viewportSpan <= 0) return 0
        val safeSpan = if (widgetSpan <= 0) 0L else widgetSpan.toLong()
        val safeMargin = if (margin <= 0) 0L else margin.toLong()
        val vp = viewportSpan.toLong()

        val pref = preferredCoord.toLong()
        val prefFits = pref >= safeMargin && (pref + safeSpan) <= (vp - safeMargin)
        if (prefFits) {
            return preferredCoord
        }

        if (fallbackCoord != null) {
            val fb = fallbackCoord.toLong()
            val fbFits = fb >= safeMargin && (fb + safeSpan) <= (vp - safeMargin)
            if (fbFits) {
                return fallbackCoord
            }
        }

        return clampCoordWithMargin(preferredCoord, widgetSpan, viewportSpan, margin)
    }

    /**
     * Reconciles a requested rectangle with a viewport.
     *
     * Ensures that:
     * 1. Output width and height never exceed the viewport dimensions or optional maximums.
     * 2. Output width and height are at least the preferred minimums, unless the viewport
     *    itself is smaller than the minimum, in which case they are capped at the viewport size.
     * 3. Output x and y are clamped so the rectangle is fully reachable on-screen.
     * 4. Zero or negative viewport dimensions produce a safe [LayoutRect] of (0, 0, 0, 0).
     * 5. The operation is strictly idempotent: `reconcile(reconcile(r, v), v) == reconcile(r, v)`.
     */
    fun reconcile(
        requested: LayoutRect,
        viewport: Viewport,
        minWidth: Int = 0,
        minHeight: Int = 0,
        maxWidth: Int = viewport.width,
        maxHeight: Int = viewport.height
    ): LayoutRect {
        if (viewport.width <= 0 || viewport.height <= 0) {
            return LayoutRect(x = 0, y = 0, width = 0, height = 0)
        }

        val maxAllowedW = if (maxWidth <= 0) 0 else minOf(viewport.width, maxWidth)
        val maxAllowedH = if (maxHeight <= 0) 0 else minOf(viewport.height, maxHeight)

        val finalWidth = safeClampDimension(
            value = requested.width,
            preferredMin = minWidth,
            maxAllowed = maxAllowedW
        )
        val finalHeight = safeClampDimension(
            value = requested.height,
            preferredMin = minHeight,
            maxAllowed = maxAllowedH
        )

        val finalX = clampCoord(requested.x, finalWidth, viewport.width)
        val finalY = clampCoord(requested.y, finalHeight, viewport.height)

        return LayoutRect(
            x = finalX,
            y = finalY,
            width = finalWidth,
            height = finalHeight
        )
    }

    /**
     * Convenience overload for [reconcile] with raw viewport integer dimensions.
     */
    fun reconcile(
        requested: LayoutRect,
        viewportWidth: Int,
        viewportHeight: Int,
        minWidth: Int = 0,
        minHeight: Int = 0,
        maxWidth: Int = viewportWidth,
        maxHeight: Int = viewportHeight
    ): LayoutRect = reconcile(
        requested = requested,
        viewport = Viewport(viewportWidth, viewportHeight),
        minWidth = minWidth,
        minHeight = minHeight,
        maxWidth = maxWidth,
        maxHeight = maxHeight
    )

    /**
     * Reconciles nullable persisted/configured values with default values and viewport constraints.
     */
    fun reconcile(
        x: Int?,
        y: Int?,
        width: Int?,
        height: Int?,
        viewportWidth: Int,
        viewportHeight: Int,
        defaultWidth: Int,
        defaultHeight: Int,
        minWidth: Int = 0,
        minHeight: Int = 0,
        maxWidth: Int = viewportWidth,
        maxHeight: Int = viewportHeight,
        defaultX: ((resolvedWidth: Int, viewportWidth: Int) -> Int)? = null,
        defaultY: ((resolvedHeight: Int, viewportHeight: Int) -> Int)? = null
    ): LayoutRect {
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            return LayoutRect(x = 0, y = 0, width = 0, height = 0)
        }

        val maxAllowedW = if (maxWidth <= 0) 0 else minOf(viewportWidth, maxWidth)
        val maxAllowedH = if (maxHeight <= 0) 0 else minOf(viewportHeight, maxHeight)

        val finalWidth = safeClampDimension(
            value = width ?: defaultWidth,
            preferredMin = minWidth,
            maxAllowed = maxAllowedW
        )
        val finalHeight = safeClampDimension(
            value = height ?: defaultHeight,
            preferredMin = minHeight,
            maxAllowed = maxAllowedH
        )

        val candidateX = x ?: defaultX?.invoke(finalWidth, viewportWidth) ?: 0
        val candidateY = y ?: defaultY?.invoke(finalHeight, viewportHeight) ?: 0

        val finalX = clampCoord(candidateX, finalWidth, viewportWidth)
        val finalY = clampCoord(candidateY, finalHeight, viewportHeight)

        return LayoutRect(
            x = finalX,
            y = finalY,
            width = finalWidth,
            height = finalHeight
        )
    }

    /**
     * Calculates new bounds during interactive resize, preventing negative dimensions,
     * invalid clamp ranges, integer overflow, or moving fixed edges off-screen.
     *
     * Invariants guaranteed:
     * - Returns a reachable rectangle for arbitrary input, including un-normalized/out-of-bounds start bounds.
     * - If no edges are modified, returns the start bounds reconciled against the viewport.
     * - Composes through [reconcile] as the single normalization path.
     */
    fun calculateResize(
        startX: Int,
        startY: Int,
        startWidth: Int,
        startHeight: Int,
        deltaX: Int,
        deltaY: Int,
        modifiesLeft: Boolean,
        modifiesRight: Boolean,
        modifiesTop: Boolean,
        modifiesBottom: Boolean,
        minWidth: Int,
        maxWidth: Int,
        minHeight: Int,
        maxHeight: Int,
        viewportWidth: Int,
        viewportHeight: Int
    ): LayoutRect {
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            return LayoutRect(x = 0, y = 0, width = 0, height = 0)
        }

        // If no edge is being resized, normalize the start rect against the viewport
        if (!modifiesLeft && !modifiesRight && !modifiesTop && !modifiesBottom) {
            return reconcile(
                requested = LayoutRect(startX, startY, startWidth, startHeight),
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                minWidth = minWidth,
                minHeight = minHeight,
                maxWidth = maxWidth,
                maxHeight = maxHeight
            )
        }

        // Horizontal calculation using Long to guard against overflow
        val newX: Int
        val newWidth: Int

        if (modifiesRight) {
            val anchorLeft = startX.toLong().coerceIn(0L, viewportWidth.toLong())
            val availableW = (viewportWidth.toLong() - anchorLeft).coerceAtLeast(0L)
            val maxAllowedW = if (maxWidth <= 0) 0 else minOf(maxWidth.toLong(), availableW).toInt()
            val candidateW = (startWidth.toLong() + deltaX.toLong()).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            newWidth = safeClampDimension(candidateW, minWidth, maxAllowedW)
            newX = anchorLeft.toInt()
        } else if (modifiesLeft) {
            val fixedRight = (startX.toLong() + startWidth.toLong())
            val anchorRight = fixedRight.coerceIn(0L, viewportWidth.toLong())
            val maxAllowedW = if (maxWidth <= 0) 0 else minOf(maxWidth.toLong(), anchorRight).toInt()
            val candidateW = (startWidth.toLong() - deltaX.toLong()).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            newWidth = safeClampDimension(candidateW, minWidth, maxAllowedW)
            newX = (anchorRight - newWidth.toLong()).toInt()
        } else {
            val maxAllowedW = if (maxWidth <= 0) 0 else minOf(viewportWidth, maxWidth)
            newWidth = safeClampDimension(startWidth, minWidth, maxAllowedW)
            newX = clampCoord(startX, newWidth, viewportWidth)
        }

        // Vertical calculation using Long to guard against overflow
        val newY: Int
        val newHeight: Int

        if (modifiesBottom) {
            val anchorTop = startY.toLong().coerceIn(0L, viewportHeight.toLong())
            val availableH = (viewportHeight.toLong() - anchorTop).coerceAtLeast(0L)
            val maxAllowedH = if (maxHeight <= 0) 0 else minOf(maxHeight.toLong(), availableH).toInt()
            val candidateH = (startHeight.toLong() + deltaY.toLong()).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            newHeight = safeClampDimension(candidateH, minHeight, maxAllowedH)
            newY = anchorTop.toInt()
        } else if (modifiesTop) {
            val fixedBottom = (startY.toLong() + startHeight.toLong())
            val anchorBottom = fixedBottom.coerceIn(0L, viewportHeight.toLong())
            val maxAllowedH = if (maxHeight <= 0) 0 else minOf(maxHeight.toLong(), anchorBottom).toInt()
            val candidateH = (startHeight.toLong() - deltaY.toLong()).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            newHeight = safeClampDimension(candidateH, minHeight, maxAllowedH)
            newY = (anchorBottom - newHeight.toLong()).toInt()
        } else {
            val maxAllowedH = if (maxHeight <= 0) 0 else minOf(viewportHeight, maxHeight)
            newHeight = safeClampDimension(startHeight, minHeight, maxAllowedH)
            newY = clampCoord(startY, newHeight, viewportHeight)
        }

        // Compose through the single reconcile normalization path to ensure final invariant guarantee
        return reconcile(
            requested = LayoutRect(newX, newY, newWidth, newHeight),
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            minWidth = minWidth,
            minHeight = minHeight,
            maxWidth = maxWidth,
            maxHeight = maxHeight
        )
    }

    /**
     * Checks whether [rect] is completely reachable and visible within [viewport].
     */
    fun isReachable(rect: LayoutRect, viewport: Viewport): Boolean {
        if (viewport.width <= 0 || viewport.height <= 0) {
            return rect.x == 0 && rect.y == 0 && rect.width == 0 && rect.height == 0
        }
        return rect.x >= 0 &&
               rect.y >= 0 &&
               rect.right <= viewport.width.toLong() &&
               rect.bottom <= viewport.height.toLong() &&
               rect.width >= 0 &&
               rect.height >= 0
    }
}
