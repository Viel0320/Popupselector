package com.viel.compose.popupselector

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.LayoutDirection

/** Resolved expansion origin after the anchor's available room has been measured. */
internal enum class OpenDirection {
    TopLeft,
    TopRight,
    BottomLeft,
    BottomRight,
}

/** Reports whether option reveal order starts at the panel top for the resolved origin. */
internal val OpenDirection.opensDown: Boolean
    get() = this == OpenDirection.TopLeft || this == OpenDirection.TopRight

/** Interpolates scalar layout coordinates without introducing animation state into geometry policy. */
internal fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

/** Interpolates each rectangle edge for the container and selected-row morphs. */
internal fun lerpRect(start: Rect, stop: Rect, fraction: Float): Rect = Rect(
    left = lerp(start.left, stop.left, fraction),
    top = lerp(start.top, stop.top, fraction),
    right = lerp(start.right, stop.right, fraction),
    bottom = lerp(start.bottom, stop.bottom, fraction),
)

/**
 * Chooses vertical and horizontal expansion origins independently from measured available room,
 * unless the caller explicitly pins one of the four corners.
 */
internal fun resolveDirection(
    anchor: Rect,
    panelHeightPx: Float,
    panelWidthPx: Float,
    windowHeightPx: Float,
    windowWidthPx: Float,
    edgeMarginPx: Float,
    requested: PopupAlignment,
    layoutDirection: LayoutDirection = LayoutDirection.Ltr,
): OpenDirection {
    val normalizedRequested = when (requested) {
        PopupAlignment.TopStart -> if (layoutDirection == LayoutDirection.Ltr) PopupAlignment.TopLeft else PopupAlignment.TopRight
        PopupAlignment.TopEnd -> if (layoutDirection == LayoutDirection.Ltr) PopupAlignment.TopRight else PopupAlignment.TopLeft
        PopupAlignment.BottomStart -> if (layoutDirection == LayoutDirection.Ltr) PopupAlignment.BottomLeft else PopupAlignment.BottomRight
        PopupAlignment.BottomEnd -> if (layoutDirection == LayoutDirection.Ltr) PopupAlignment.BottomRight else PopupAlignment.BottomLeft
        else -> requested
    }

    val spaceBelow = windowHeightPx - anchor.bottom - edgeMarginPx
    val spaceAbove = anchor.top - edgeMarginPx
    val opensDown = when (normalizedRequested) {
        PopupAlignment.TopLeft, PopupAlignment.TopRight -> true
        PopupAlignment.BottomLeft, PopupAlignment.BottomRight -> false
        PopupAlignment.Auto -> when {
            spaceBelow >= panelHeightPx -> true
            spaceAbove >= panelHeightPx -> false
            else -> spaceBelow >= spaceAbove
        }
        else -> true
    }

    val maxRight = windowWidthPx - edgeMarginPx
    val spaceToRight = maxRight - anchor.left
    val spaceToLeft = anchor.right - edgeMarginPx
    val opensRight = when (normalizedRequested) {
        PopupAlignment.TopLeft, PopupAlignment.BottomLeft -> true
        PopupAlignment.TopRight, PopupAlignment.BottomRight -> false
        PopupAlignment.Auto -> when {
            spaceToRight >= panelWidthPx && spaceToLeft >= panelWidthPx -> layoutDirection == LayoutDirection.Ltr
            spaceToRight >= panelWidthPx -> true
            spaceToLeft >= panelWidthPx -> false
            else -> spaceToRight >= spaceToLeft
        }
        else -> true
    }

    return when {
        opensDown && opensRight -> OpenDirection.TopLeft
        opensDown -> OpenDirection.TopRight
        opensRight -> OpenDirection.BottomLeft
        else -> OpenDirection.BottomRight
    }
}

/**
 * Resolves the expanded panel rectangle in window coordinates and clamps horizontal placement
 * to the available edge margins while preserving the chosen vertical origin.
 */
internal fun resolvePanelRect(
    anchor: Rect,
    direction: OpenDirection,
    panelWidthPx: Float,
    panelHeightPx: Float,
    windowWidthPx: Float,
    edgeMarginPx: Float,
): Rect {
    val maxAvailableWidth = (windowWidthPx - edgeMarginPx * 2f).coerceAtLeast(0f)
    val clampedWidth = panelWidthPx.coerceIn(0f, maxAvailableWidth)

    val preferredLeft = when (direction) {
        OpenDirection.TopLeft, OpenDirection.BottomLeft -> anchor.left
        OpenDirection.TopRight, OpenDirection.BottomRight -> anchor.right - clampedWidth
    }
    val maxLeft = windowWidthPx - edgeMarginPx - clampedWidth
    val left = if (maxLeft >= edgeMarginPx) {
        preferredLeft.coerceIn(edgeMarginPx, maxLeft)
    } else {
        edgeMarginPx
    }
    val right = (left + clampedWidth).coerceAtMost(windowWidthPx - edgeMarginPx)
    return if (direction.opensDown) {
        Rect(left, anchor.top, right, anchor.top + panelHeightPx)
    } else {
        Rect(left, anchor.bottom - panelHeightPx, right, anchor.bottom)
    }
}

/**
 * Maps a selected option into scrolled panel coordinates and clamps off-screen rows to the nearest
 * visible content edge so the decorative morph target remains stable.
 */
internal fun resolveFlyingSelectedRowTargetTop(
    panelTop: Float,
    panelBottom: Float,
    panelPadding: Float,
    selectedIndex: Int,
    rowHeight: Float,
    rowSpacing: Float,
    scrollOffset: Float,
): Float {
    val minimumTop = panelTop + panelPadding
    val maximumTop = (panelBottom - panelPadding - rowHeight).coerceAtLeast(minimumTop)
    val scrolledTop = minimumTop + selectedIndex * (rowHeight + rowSpacing) - scrollOffset
    return scrolledTop.coerceIn(minimumTop, maximumTop)
}

/**
 * Positions the expanded panel so that the selected row's vertical centre aligns with the
 * collapsed anchor's vertical centre, and horizontally follows [layoutDirection].
 *
 * The result is clamped to keep the panel within the window's safe area.
 */
internal fun resolvePanelRectCentered(
    anchor: Rect,
    panelWidthPx: Float,
    panelHeightPx: Float,
    windowWidthPx: Float,
    windowHeightPx: Float,
    edgeMarginPx: Float,
    selectedIndex: Int,
    rowHeightPx: Float,
    rowSpacingPx: Float,
    panelPaddingPx: Float,
    layoutDirection: LayoutDirection,
): Rect {
    val maxAvailableWidth = (windowWidthPx - edgeMarginPx * 2f).coerceAtLeast(0f)
    val clampedWidth = panelWidthPx.coerceIn(0f, maxAvailableWidth)

    // Vertical: align selected row centre with anchor centre
    val anchorCenterY = (anchor.top + anchor.bottom) / 2f
    val selectedRowCenterInPanel =
        panelPaddingPx + selectedIndex * (rowHeightPx + rowSpacingPx) + rowHeightPx / 2f
    val preferredTop = anchorCenterY - selectedRowCenterInPanel
    val maxTop = (windowHeightPx - edgeMarginPx - panelHeightPx).coerceAtLeast(edgeMarginPx)
    val top = preferredTop.coerceIn(edgeMarginPx, maxTop)

    // Horizontal: follow layout direction
    val preferredLeft = when (layoutDirection) {
        LayoutDirection.Ltr -> anchor.left
        LayoutDirection.Rtl -> anchor.right - clampedWidth
    }
    val maxLeft = windowWidthPx - edgeMarginPx - clampedWidth
    val left = if (maxLeft >= edgeMarginPx) {
        preferredLeft.coerceIn(edgeMarginPx, maxLeft)
    } else {
        edgeMarginPx
    }
    val right = (left + clampedWidth).coerceAtMost(windowWidthPx - edgeMarginPx)

    return Rect(left, top, right, top + panelHeightPx)
}

/** Keeps one selected-row representation visible until the list row owns the final expanded frame. */
internal fun isFlyingSelectedRowVisible(fraction: Float): Boolean = fraction < 0.999f
