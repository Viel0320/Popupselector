package com.viel.compose.popupselector

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp

/**
 * The complete, window-space geometry of an expanded popup.
 *
 * Produced by [resolvePlacement] from immutable inputs so the rendering layer never re-derives
 * layout decisions and never mixes coordinate spaces.
 */
@Immutable
internal data class PanelPlacement(
    /** Final panel rectangle in window coordinates. */
    val panelRect: Rect,
    /** Whether the panel opens downward; orders the staggered row reveal. */
    val opensDown: Boolean,
    /** Whether the panel hugs the anchor's left side; the horizontal mirror of the reveal. */
    val opensRight: Boolean,
    /** Window-space rectangle of every option row, in list order. */
    val rowRects: List<Rect>,
    /** Maximum scroll offset the content can reach. */
    val maxScrollPx: Float,
    /**
     * Scroll offset the list should adopt on first composition.
     *
     * The row rectangles in [rowRects] already account for this offset, so the list and the flying
     * selected row must both use it, or they resolve different positions for the same row.
     */
    val initialScrollPx: Float,
    /** Rectangle the collapsed surface occupies in window coordinates. */
    val anchorRect: Rect,
)

/**
 * Measured widths of the aligned item sections, shared by every row of a panel.
 *
 * Items render as two sections — the content slot and the optional count slot — and these are the
 * per-section maxima across all items, so every row's sections line up instead of each count
 * hugging its own label. The per-item lists keep each item's own section widths: only the flying
 * selected row consumes them, to interpolate between the trigger's concatenated layout (its own
 * widths) and the panel's aligned layout (the shared widths) during the morph.
 */
@Immutable
internal data class RowSectionWidths(
    /** Widest content slot across all items, excluding row padding. */
    val contentWidthPx: Float,
    /** Widest count slot across all items, excluding row padding; zero when no item has a count. */
    val countWidthPx: Float,
    /** Each item's own content slot width, index-aligned with the items list. */
    val itemContentWidthsPx: List<Float> = emptyList(),
    /** Each item's own count slot width (zero where an item has no count), index-aligned. */
    val itemCountWidthsPx: List<Float> = emptyList(),
) {
    /** Full intrinsic row width: the shared sections plus row padding and the intersection gap. */
    fun totalWidthPx(rowHorizontalPaddingPx: Float, countSpacingPx: Float): Float =
        rowHorizontalPaddingPx * 2f + contentWidthPx +
            (if (countWidthPx > 0f) countSpacingPx + countWidthPx else 0f)

    /**
     * The content section clamped so both sections fit [contentAreaPx] — the panel's content area
     * between the row padding. When the widest label would push the count section out of the
     * clipped surface, only the label truncates (marquee); the count section stays reserved and
     * visible.
     */
    fun clampedToContentArea(contentAreaPx: Float, countSpacingPx: Float): RowSectionWidths {
        val maxContentPx = if (countWidthPx > 0f) {
            (contentAreaPx - countWidthPx - countSpacingPx).coerceAtLeast(0f)
        } else {
            contentAreaPx
        }
        return if (contentWidthPx > maxContentPx) {
            copy(contentWidthPx = maxContentPx)
        } else {
            this
        }
    }
}

/** Reference space a probe is aligned into when decomposing an ambient [Alignment]. */
private const val AlignmentProbeSize = 100

/**
 * Width available between the two edge margins — the widest a panel or a measurement probe may be.
 */
internal fun safeContentWidthPx(windowWidthPx: Float, edgeMarginPx: Float): Float =
    (windowWidthPx - edgeMarginPx * 2f).coerceAtLeast(0f)

/**
 * Rescales the progress past [start] onto the full `[0, 1]` range: zero until [start], then the
 * remaining progress renormalized and clamped. Degenerate at `start >= 1`, where it stays zero.
 */
internal fun progressPast(fraction: Float, start: Float): Float =
    if (fraction <= start) {
        0f
    } else {
        ((fraction - start) / (1f - start)).coerceIn(0f, 1f)
    }

/**
 * Vertical component of an ambient [Alignment], in the popup's own vocabulary.
 *
 * A zero-size probe is aligned inside a reference space and its landing position decides the side,
 * so custom `Alignment` implementations work without enumerating the standard instances.
 */
internal fun Alignment.toPopupVerticalAlignment(): PopupVerticalAlignment {
    val y = align(IntSize.Zero, IntSize(0, AlignmentProbeSize), LayoutDirection.Ltr).y
    return when {
        y <= 0f -> PopupVerticalAlignment.Top
        y >= AlignmentProbeSize -> PopupVerticalAlignment.Bottom
        // A centred ambient component maps to Auto: with a selection it centres the row, which is
        // the closest match to what the container's centre alignment conveys.
        else -> PopupVerticalAlignment.Auto
    }
}

/**
 * Horizontal component of an ambient [Alignment], in the popup's own vocabulary.
 *
 * Resolved positions cannot recover whether the original was direction-aware or absolute, so the
 * result is always expressed as [PopupVerticalAlignment]-style relative sides: start, end, or
 * [PopupHorizontalAlignment.Auto] for a centred component.
 */
internal fun Alignment.toPopupHorizontalAlignment(layoutDirection: LayoutDirection): PopupHorizontalAlignment {
    val x = align(IntSize.Zero, IntSize(AlignmentProbeSize, 0), layoutDirection).x
    return when {
        x <= 0f -> if (layoutDirection == LayoutDirection.Ltr) {
            PopupHorizontalAlignment.Start
        } else {
            PopupHorizontalAlignment.End
        }
        x >= AlignmentProbeSize -> if (layoutDirection == LayoutDirection.Ltr) {
            PopupHorizontalAlignment.End
        } else {
            PopupHorizontalAlignment.Start
        }
        else -> PopupHorizontalAlignment.Auto
    }
}

/**
 * Remaps the morph fraction onto the shadow's ramp: zero until [startFraction], then the remaining
 * progress rescaled to `[0, 1]`. Degenerate at `startFraction >= 1`, where the shadow stays off.
 */
internal fun shadowFraction(fraction: Float, startFraction: Float): Float =
    progressPast(fraction, startFraction)

/** Interpolates each edge of a rectangle. */
internal fun lerpRect(start: Rect, stop: Rect, fraction: Float): Rect = Rect(
    left = lerp(start.left, stop.left, fraction),
    top = lerp(start.top, stop.top, fraction),
    right = lerp(start.right, stop.right, fraction),
    bottom = lerp(start.bottom, stop.bottom, fraction),
)

/**
 * Top-left of the morphing surface in window coordinates: the anchor's at fraction `0`, the
 * panel's at `1`. The surface's children (chevron, flying selected row) are placed relative to
 * this origin, so every child derives it through this one definition.
 */
internal fun surfaceOrigin(anchor: Rect, panelRect: Rect, fraction: Float): Offset =
    Offset(
        lerp(anchor.left, panelRect.left, fraction),
        lerp(anchor.top, panelRect.top, fraction),
    )

/**
 * Chooses whether the panel opens downward.
 *
 * [PopupVerticalAlignment.Auto] compares the room on each side; explicit sides are honoured
 * verbatim. The centred behaviour for `Auto` with a selection is resolved by the caller into the
 * centred placement before reaching this function.
 */
internal fun resolveVerticalOpenDown(
    vertical: PopupVerticalAlignment,
    anchor: Rect,
    panelHeightPx: Float,
    windowHeightPx: Float,
    topMarginPx: Float,
    bottomMarginPx: Float,
): Boolean = when (vertical) {
    PopupVerticalAlignment.Top -> true
    PopupVerticalAlignment.Bottom -> false
    else -> {
        // Above/below room is measured against the vertical margins so an edge-to-edge app can
        // reserve the status/navigation bars here too.
        val spaceBelow = windowHeightPx - anchor.bottom - bottomMarginPx
        val spaceAbove = anchor.top - topMarginPx
        when {
            spaceBelow >= panelHeightPx -> true
            spaceAbove >= panelHeightPx -> false
            else -> spaceBelow >= spaceAbove
        }
    }
}

/**
 * Chooses whether the panel opens to the right.
 *
 * [PopupHorizontalAlignment.Start]/[End] follow the layout direction, and [Auto] compares the room
 * on each side, falling back to the direction's leading side when both fit.
 */
internal fun resolveHorizontalOpensRight(
    horizontal: PopupHorizontalAlignment,
    anchor: Rect,
    panelWidthPx: Float,
    windowWidthPx: Float,
    edgeMarginPx: Float,
    layoutDirection: LayoutDirection,
): Boolean = when (horizontal) {
    PopupHorizontalAlignment.Start -> layoutDirection == LayoutDirection.Ltr
    PopupHorizontalAlignment.End -> layoutDirection != LayoutDirection.Ltr
    // Inherit is resolved against the ambient context before geometry runs; defensively degrade
    // to the leading side rather than failing if one slips through.
    PopupHorizontalAlignment.Inherit -> layoutDirection == LayoutDirection.Ltr
    PopupHorizontalAlignment.Auto -> {
        val maxRight = windowWidthPx - edgeMarginPx
        val spaceToRight = maxRight - anchor.left
        val spaceToLeft = anchor.right - edgeMarginPx
        when {
            spaceToRight >= panelWidthPx && spaceToLeft >= panelWidthPx -> layoutDirection == LayoutDirection.Ltr
            spaceToRight >= panelWidthPx -> true
            spaceToLeft >= panelWidthPx -> false
            else -> spaceToRight >= spaceToLeft
        }
    }
}

/**
 * Resolves the panel rectangle for a corner-anchored expansion.
 *
 * The vertical origin follows [opensDown] and the horizontal origin follows [opensRight];
 * placement is clamped into the window's safe area, degrading to the left edge when the panel is
 * wider than the available space.
 */
internal fun resolvePanelRect(
    anchor: Rect,
    opensDown: Boolean,
    opensRight: Boolean,
    panelWidthPx: Float,
    panelHeightPx: Float,
    windowWidthPx: Float,
    windowHeightPx: Float,
    edgeMarginPx: Float,
    topMarginPx: Float = edgeMarginPx,
    bottomMarginPx: Float = edgeMarginPx,
): Rect = resolvePanelRectFromTop(
    anchor = anchor,
    preferredTop = if (opensDown) anchor.top else anchor.bottom - panelHeightPx,
    opensRight = opensRight,
    panelWidthPx = panelWidthPx,
    panelHeightPx = panelHeightPx,
    windowWidthPx = windowWidthPx,
    windowHeightPx = windowHeightPx,
    edgeMarginPx = edgeMarginPx,
    topMarginPx = topMarginPx,
    bottomMarginPx = bottomMarginPx,
)

/**
 * Resolves the panel rectangle for a centred expansion.
 *
 * The panel is placed so that [selectedRowCenterInViewportPx] — the selected row's centre measured
 * from the panel's top edge *after* scrolling — lands on the anchor's vertical centre. The result
 * is clamped into the window's safe area, which is the only reason the alignment can be lost.
 *
 * Horizontally the panel follows [opensRight] — the resolved horizontal placement — so, say,
 * `End` hugs the anchor's trailing edge just as it does for corner placements. The caller is
 * responsible for having already scrolled the list, so this function never needs to know about row
 * indices, heights or scroll state.
 */
internal fun resolvePanelRectCentered(
    anchor: Rect,
    panelWidthPx: Float,
    panelHeightPx: Float,
    windowWidthPx: Float,
    windowHeightPx: Float,
    edgeMarginPx: Float,
    selectedRowCenterInViewportPx: Float,
    opensRight: Boolean,
    topMarginPx: Float = edgeMarginPx,
    bottomMarginPx: Float = edgeMarginPx,
): Rect = resolvePanelRectFromTop(
    anchor = anchor,
    preferredTop = (anchor.top + anchor.bottom) / 2f - selectedRowCenterInViewportPx,
    opensRight = opensRight,
    panelWidthPx = panelWidthPx,
    panelHeightPx = panelHeightPx,
    windowWidthPx = windowWidthPx,
    windowHeightPx = windowHeightPx,
    edgeMarginPx = edgeMarginPx,
    topMarginPx = topMarginPx,
    bottomMarginPx = bottomMarginPx,
)

/**
 * Shared body of [resolvePanelRect] and [resolvePanelRectCentered]: identical clamping, only the
 * preferred top edge differs between the two.
 */
private fun resolvePanelRectFromTop(
    anchor: Rect,
    preferredTop: Float,
    opensRight: Boolean,
    panelWidthPx: Float,
    panelHeightPx: Float,
    windowWidthPx: Float,
    windowHeightPx: Float,
    edgeMarginPx: Float,
    topMarginPx: Float,
    bottomMarginPx: Float,
): Rect {
    val clampedWidth = panelWidthPx.coerceIn(0f, safeContentWidthPx(windowWidthPx, edgeMarginPx))

    val preferredLeft = if (opensRight) anchor.left else anchor.right - clampedWidth
    val left = clampLeft(
        preferredLeft = preferredLeft,
        widthPx = clampedWidth,
        windowWidthPx = windowWidthPx,
        edgeMarginPx = edgeMarginPx,
    )
    val right = (left + clampedWidth).coerceAtMost(windowWidthPx - edgeMarginPx)

    // The anchor itself may sit inside the edge margin, so the vertical edge must be clamped too.
    val maxTop = (windowHeightPx - bottomMarginPx - panelHeightPx).coerceAtLeast(topMarginPx)
    val top = preferredTop.coerceIn(topMarginPx, maxTop)

    return Rect(left, top, right, top + panelHeightPx)
}

/** Clamps a preferred left edge into the window's safe area, handling over-wide panels. */
private fun clampLeft(
    preferredLeft: Float,
    widthPx: Float,
    windowWidthPx: Float,
    edgeMarginPx: Float,
): Float {
    val maxLeft = windowWidthPx - edgeMarginPx - widthPx
    return if (maxLeft >= edgeMarginPx) {
        preferredLeft.coerceIn(edgeMarginPx, maxLeft)
    } else {
        edgeMarginPx
    }
}

/** Total content height for [rowCount] rows, excluding the scroll viewport. */
internal fun Density.contentHeightPx(rowCount: Int, metrics: PopupMetrics): Float {
    if (rowCount <= 0) return metrics.panelVerticalPadding.toPx() * 2f
    return metrics.panelVerticalPadding.toPx() * 2f +
        rowCount * metrics.rowTouchTargetHeight.toPx() +
        (rowCount - 1) * metrics.rowVerticalSpacing.toPx()
}

/** Window-space rectangle of row [index] given the panel geometry and current scroll offset. */
internal fun rowRectAt(
    panelRect: Rect,
    index: Int,
    rowHeightPx: Float,
    rowSpacingPx: Float,
    panelPaddingPx: Float,
    scrollOffsetPx: Float,
): Rect {
    val top = panelRect.top +
        rowTopInContentPx(index, rowHeightPx, rowSpacingPx, panelPaddingPx) - scrollOffsetPx
    return Rect(
        left = panelRect.left,
        top = top,
        right = panelRect.right,
        bottom = top + rowHeightPx,
    )
}

/**
 * Scroll offset that brings a row to the vertical centre of the viewport.
 *
 * Used by [PopupVerticalAlignment.Auto] with a selection, so the row sits mid-viewport before the panel is positioned.
 * Centring the row first means the panel's own centre also lands on the anchor, which keeps the
 * two independent of how far the list had to scroll.
 *
 * Returns 0 when the content is not scrollable, and clamps to [maxScrollPx] when the row is too
 * close to either end for exact centring.
 */
internal fun centeredScrollOffsetPx(
    rowCenterInContentPx: Float,
    viewportHeightPx: Float,
    maxScrollPx: Float,
): Float {
    if (maxScrollPx <= 0f) return 0f
    val target = rowCenterInContentPx - viewportHeightPx / 2f
    return target.coerceIn(0f, maxScrollPx)
}

/**
 * Clamps a row rectangle so it stays within the panel's padded content area.
 *
 * Used by the flying selected row so an off-screen selection still resolves to a stable,
 * on-screen morph target instead of flying outside the surface.
 */
internal fun clampRowToPanel(
    rowRect: Rect,
    panelRect: Rect,
    panelPaddingPx: Float,
): Rect {
    val minimumTop = panelRect.top + panelPaddingPx
    val maximumTop = (panelRect.bottom - panelPaddingPx - rowRect.height).coerceAtLeast(minimumTop)
    val top = rowRect.top.coerceIn(minimumTop, maximumTop)
    return rowRect.translate(Offset(0f, top - rowRect.top))
}

/**
 * The single entry point for popup geometry.
 *
 * Everything downstream consumes these values verbatim; no rendering code performs its own
 * measurement or direction resolution.
 *
 * @param anchor The collapsed surface bounds in window coordinates.
 * @param windowSize The window size in pixels. Supplied explicitly so the geometry never depends
 *  on the constraints of whichever host happens to be composing the panel.
 * @param rowCount Number of options.
 * @param selectedIndex Index of the selected option, or null when nothing is selected.
 * @param defaultCentringRow Row to centre on when nothing is selected; the caller passes the
 *  keyboard cursor's default (the first enabled item) so vertical `Auto` always aims the panel at
 *  the current row. Null keeps the plain side comparison.
 * @param width Strategy for resolving the panel width.
 * @param maxHeight Explicit maximum panel height, or [Dp.Unspecified] to use the window fraction.
 * @param verticalAlignment Vertical placement. [PopupVerticalAlignment.Auto] centres the selected
 *  row when [selectedIndex] is non-null and otherwise compares the room on each side.
 * @param horizontalAlignment Horizontal placement.
 * @param measuredSections Per-section intrinsic widths measured by [PanelMeasurer] when
 *  [PopupWidth.Wrap] is used, or null before they have been measured.
 */
internal fun resolvePlacement(
    anchor: Rect,
    windowSize: IntSize,
    rowCount: Int,
    selectedIndex: Int?,
    width: PopupWidth,
    maxHeight: Dp,
    verticalAlignment: PopupVerticalAlignment,
    horizontalAlignment: PopupHorizontalAlignment,
    metrics: PopupMetrics,
    layoutDirection: LayoutDirection,
    density: Density,
    measuredSections: RowSectionWidths? = null,
    defaultCentringRow: Int? = null,
    safeDrawingTopPx: Float = 0f,
    safeDrawingBottomPx: Float = 0f,
): PanelPlacement = with(density) {
    val px = metrics.toPx(density)
    val windowWidthPx = windowSize.width.toFloat()
    val windowHeightPx = windowSize.height.toFloat()
    val edgeMarginPx = px.edgeMarginPx
    // Per-side vertical margins: the safe-drawing insets (status/navigation bars) apply on top of
    // the plain margin, which stays as the floor.
    val topMarginPx = maxOf(edgeMarginPx, safeDrawingTopPx)
    val bottomMarginPx = maxOf(edgeMarginPx, safeDrawingBottomPx)
    val rowHeightPx = px.rowHeightPx
    val rowSpacingPx = px.rowSpacingPx
    val panelPaddingPx = px.panelPaddingPx

    val maxAllowedWidthPx = safeContentWidthPx(windowWidthPx, edgeMarginPx)
    val resolvedWidthPx = when (width) {
        PopupWidth.MatchAnchor -> anchor.width.coerceIn(0f, maxAllowedWidthPx)
        is PopupWidth.Fixed -> width.width.toPx().coerceIn(0f, maxAllowedWidthPx)
        PopupWidth.Wrap -> {
            // Sections, not rows, are the unit of width now: the panel must fit the widest
            // content section and the widest count section side by side, not merely the widest
            // single row — otherwise the aligned count column could not start at a shared offset.
            val fallback = measuredSections
                ?.totalWidthPx(px.rowHorizontalPaddingPx, px.countSpacingPx)
                ?: anchor.width
            val minWidthPx = anchor.width.coerceAtMost(maxAllowedWidthPx)
            fallback.coerceIn(minWidthPx, maxAllowedWidthPx)
        }
    }

    val rawContentHeightPx = contentHeightPx(rowCount, metrics)
    val maxHeightPx = if (maxHeight != Dp.Unspecified) {
        maxHeight.toPx()
    } else {
        windowHeightPx * metrics.maxHeightFraction
    }
    val panelHeightPx = rawContentHeightPx.coerceAtMost(maxHeightPx).coerceAtLeast(0f)

    // Auto's centring: the panel is centred on the effective row — the selected one, or the
    // caller's default (the first enabled item, matching the keyboard cursor) when nothing is
    // selected. Explicit sides never centre.
    val centringRow = selectedIndex ?: defaultCentringRow
    val wantsCentered = verticalAlignment == PopupVerticalAlignment.Auto && centringRow != null
    // For the staggered reveal the centred placement behaves like the side comparison: the panel
    // may extend in either direction from the trigger, so reveal from the nearest edge.
    val maxScrollPx = (rawContentHeightPx - panelHeightPx).coerceAtLeast(0f)

    // Step 1: scroll. The centred row is brought to the viewport centre when centring, or just
    // into view otherwise. Resolving the scroll before the panel rectangle is what keeps the row's
    // final position and the panel's position consistent for long, scrollable lists.
    val selectedRowCenterInContentPx = centringRow?.let {
        rowTopInContentPx(it, rowHeightPx, rowSpacingPx, panelPaddingPx) + rowHeightPx / 2f
    }

    val initialScrollPx = when {
        selectedRowCenterInContentPx == null -> 0f
        wantsCentered -> centeredScrollOffsetPx(
            rowCenterInContentPx = selectedRowCenterInContentPx,
            viewportHeightPx = panelHeightPx,
            maxScrollPx = maxScrollPx,
        )
        else -> scrollDeltaToRevealRow(
            rowTopPx = selectedRowCenterInContentPx - rowHeightPx / 2f,
            rowHeightPx = rowHeightPx,
            viewportHeightPx = panelHeightPx,
            panelPaddingPx = panelPaddingPx,
            scrollOffsetPx = 0f,
            maxScrollPx = maxScrollPx,
        )
    }

    // Step 2: which sides the panel opens from, used for placement and the staggered row reveal.
    // The two axes resolve independently, so a panel can open, say, downward while hugging the
    // trailing edge.
    val opensDown = resolveVerticalOpenDown(
        vertical = verticalAlignment,
        anchor = anchor,
        panelHeightPx = panelHeightPx,
        windowHeightPx = windowHeightPx,
        topMarginPx = topMarginPx,
        bottomMarginPx = bottomMarginPx,
    )
    val opensRight = resolveHorizontalOpensRight(
        horizontal = horizontalAlignment,
        anchor = anchor,
        panelWidthPx = resolvedWidthPx,
        windowWidthPx = windowWidthPx,
        edgeMarginPx = edgeMarginPx,
        layoutDirection = layoutDirection,
    )

    // Step 3: position, derived from where the selected row actually ended up after scrolling.
    val selectedRowCenterInViewportPx = (selectedRowCenterInContentPx ?: 0f) - initialScrollPx
    val panelRect = if (wantsCentered) {
        resolvePanelRectCentered(
            anchor = anchor,
            panelWidthPx = resolvedWidthPx,
            panelHeightPx = panelHeightPx,
            windowWidthPx = windowWidthPx,
            windowHeightPx = windowHeightPx,
            edgeMarginPx = edgeMarginPx,
            selectedRowCenterInViewportPx = selectedRowCenterInViewportPx,
            opensRight = opensRight,
            topMarginPx = topMarginPx,
            bottomMarginPx = bottomMarginPx,
        )
    } else {
        resolvePanelRect(
            anchor = anchor,
            opensDown = opensDown,
            opensRight = opensRight,
            panelWidthPx = resolvedWidthPx,
            panelHeightPx = panelHeightPx,
            windowWidthPx = windowWidthPx,
            windowHeightPx = windowHeightPx,
            edgeMarginPx = edgeMarginPx,
            topMarginPx = topMarginPx,
            bottomMarginPx = bottomMarginPx,
        )
    }

    val rowRects = List(rowCount) { index ->
        rowRectAt(
            panelRect = panelRect,
            index = index,
            rowHeightPx = rowHeightPx,
            rowSpacingPx = rowSpacingPx,
            panelPaddingPx = panelPaddingPx,
            scrollOffsetPx = initialScrollPx,
        )
    }

    PanelPlacement(
        panelRect = panelRect,
        opensDown = opensDown,
        opensRight = opensRight,
        rowRects = rowRects,
        maxScrollPx = maxScrollPx,
        initialScrollPx = initialScrollPx,
        anchorRect = anchor,
    )
}

/**
 * The flying selected row's interpolation geometry, precomputed per placement.
 *
 * At fraction `0` the content box ([contentRect]) is exactly the collapsed trigger's content box —
 * the slot the selection occupies when the panel is at rest — and at fraction `1` it is the row's
 * slot inside the panel. The highlight container ([containerRect]) spans the whole trigger at `0`
 * and the whole row slot at `1`. Interpolating between them is what makes the selection appear to
 * travel out of the trigger and, on collapse, fly home.
 *
 * Everything that does not depend on the per-frame inputs — the trigger-space rectangles, the
 * panel-space landing rectangles and the scroll clamping bounds — is derived once here, so a
 * frame's layout work is one clamp and two lerps. [scrollOffsetPx] must be the list's *current*
 * scroll offset rather than the one resolved by [resolvePlacement]: the user may have scrolled
 * since, and the row has to start from where it is actually drawn. A row the user can click is by
 * definition on screen, so no clamping applies in the normal path; the slot clamp only rescues
 * rows that are off-screen for other reasons.
 *
 * The two endpoint widths ([collapsedContentWidthPx], [rowContentWidthPx]) are consumed by the
 * flying row's section interpolation, which must start and land on the widths the trigger and the
 * list row actually give the label — see [RowSectionLayout.Flying].
 */
internal class FlyingRowGeometry private constructor(
    private val anchor: Rect,
    private val src: Rect,
    private val slotLeft: Float,
    private val slotRight: Float,
    private val slotBaseTop: Float,
    private val slotMinTop: Float,
    private val slotMaxTop: Float,
    private val rowHeightPx: Float,
    private val rowHorizontalPaddingPx: Float,
) {
    /** Width of the content box at the trigger — the interpolation's starting width. */
    val collapsedContentWidthPx: Float = src.width

    /** Width of the content box in its row slot — the landing width. Scroll-independent. */
    val rowContentWidthPx: Float = slotRight - slotLeft - rowHorizontalPaddingPx * 2f

    /** The row's slot rectangle at [scrollOffsetPx], clamped into the panel's padded area. */
    private fun slotRect(scrollOffsetPx: Float): Rect {
        val top = (slotBaseTop - scrollOffsetPx).coerceIn(slotMinTop, slotMaxTop)
        return Rect(slotLeft, top, slotRight, top + rowHeightPx)
    }

    /** Full-row rectangle carried by the highlight: whole trigger at `0`, whole row slot at `1`. */
    fun containerRect(fraction: Float, scrollOffsetPx: Float): Rect =
        lerpRect(anchor, slotRect(scrollOffsetPx), fraction)

    /** Content-box rectangle: the trigger's content box at `0`, the inset row slot at `1`. */
    fun contentRect(fraction: Float, scrollOffsetPx: Float): Rect {
        val slot = slotRect(scrollOffsetPx)
        val target = Rect(
            left = slot.left + rowHorizontalPaddingPx,
            top = slot.top,
            right = slot.right - rowHorizontalPaddingPx,
            bottom = slot.top + rowHeightPx,
        )
        return lerpRect(src, target, fraction)
    }

    companion object {
        /**
         * Derives the geometry for flying row [index] between [anchor] and its slot inside
         * [panelRect].
         */
        fun of(
            anchor: Rect,
            panelRect: Rect,
            index: Int,
            px: PopupMetricsPx,
            layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        ): FlyingRowGeometry {
            val srcTop = anchor.top + (anchor.height - px.collapsedHeightPx) / 2f
            // The chevron sits on the leading side, so the content box mirrors with the direction.
            val src = when (layoutDirection) {
                LayoutDirection.Ltr -> Rect(
                    left = anchor.left + px.chevronLeadingPaddingPx + px.chevronBlockPx,
                    top = srcTop,
                    right = anchor.right - px.collapsedHorizontalPaddingPx,
                    bottom = srcTop + px.collapsedHeightPx,
                )
                LayoutDirection.Rtl -> Rect(
                    left = anchor.left + px.collapsedHorizontalPaddingPx,
                    top = srcTop,
                    right = anchor.right - px.chevronLeadingPaddingPx - px.chevronBlockPx,
                    bottom = srcTop + px.collapsedHeightPx,
                )
            }

            val minimumTop = panelRect.top + px.panelPaddingPx
            return FlyingRowGeometry(
                anchor = anchor,
                src = src,
                slotLeft = panelRect.left,
                slotRight = panelRect.right,
                slotBaseTop = panelRect.top +
                    rowTopInContentPx(index, px.rowHeightPx, px.rowSpacingPx, px.panelPaddingPx),
                slotMinTop = minimumTop,
                slotMaxTop = (panelRect.bottom - px.panelPaddingPx - px.rowHeightPx)
                    .coerceAtLeast(minimumTop),
                rowHeightPx = px.rowHeightPx,
                rowHorizontalPaddingPx = px.rowHorizontalPaddingPx,
            )
        }
    }
}
