package com.viel.compose.popupselector

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * Everything the expanded panel needs to render, captured once at the [PopupSelector] call site.
 *
 * Threading these as a single value keeps every layer of the host -> content -> layout -> surface
 * chain down to three parameters (`expanded`, `anchor`, context) instead of a dozen, and lets the
 * overlay host forward changing inputs with one [rememberUpdatedState].
 *
 * [PopupSelector] remembers the instance and replaces it only when a panel input changes, so the
 * per-frame anchor tracking during the collapse does not rebuild it. Marked [Stable] on that
 * contract: the properties are read-only vals, identity equality is the observable one, and a new
 * instance therefore means an input actually changed — which is what lets the host chain skip
 * recomposition while the anchor animates.
 */
@Stable
internal class PanelContext(
    val items: List<PopupItem>,
    val selectedIndex: Int?,
    val morph: PopupMorphState,
    val style: PopupStyle,
    val layout: PopupLayout,
    val contentContext: PopupContentContext,
    val surfaceModifier: Modifier,
    val onSelect: (Int) -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * Renders the expanded panel into whichever host the [PopupLayout.host] policy resolves to.
 *
 * The panel content itself is host-agnostic; only the surrounding container differs.
 */
@Composable
internal fun PopupPanelHost(
    expanded: Boolean,
    anchor: Rect,
    context: PanelContext,
) {
    val localHost = LocalPopupOverlayHost.current
    val targetHostState = when (val host = context.layout.host) {
        PopupHostPolicy.Window -> null
        is PopupHostPolicy.Overlay -> host.hostState ?: localHost
    }

    if (targetHostState != null) {
        OverlayHostedPanel(
            hostState = targetHostState,
            expanded = expanded,
            anchor = anchor,
            context = context,
        )
    } else {
        WindowHostedPanel(
            expanded = expanded,
            anchor = anchor,
            context = context,
        )
    }
}

/**
 * Publishes the panel into an in-window overlay host.
 *
 * Registration happens once per host/owner pair; changing inputs are forwarded through
 * [rememberUpdatedState] so the overlay is not torn down and rebuilt on every recomposition.
 */
@Composable
private fun OverlayHostedPanel(
    hostState: PopupOverlayHostState,
    expanded: Boolean,
    anchor: Rect,
    context: PanelContext,
) {
    val owner = remember { Any() }

    val currentExpanded by rememberUpdatedState(expanded)
    val currentAnchor by rememberUpdatedState(anchor)
    val currentContext by rememberUpdatedState(context)

    DisposableEffect(hostState, owner) {
        hostState.setContent(owner) {
            PopupPanelContent(
                expanded = currentExpanded,
                anchor = currentAnchor,
                context = currentContext,
            )
        }
        onDispose {
            hostState.clearContent(owner)
        }
    }
}

/**
 * Hosts the morph surface in a full-screen [Popup] with clipping disabled, so the surface can
 * paint above or below the anchor.
 */
@Composable
private fun WindowHostedPanel(
    expanded: Boolean,
    anchor: Rect,
    context: PanelContext,
) {
    Popup(
        popupPositionProvider = TopLeftPositionProvider,
        onDismissRequest = context.onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = false,
        ),
    ) {
        PopupPanelContent(
            expanded = expanded,
            anchor = anchor,
            context = context,
        )
    }
}

/**
 * Restores the caller's styling context, then measures, resolves geometry and renders the panel.
 *
 * The restore matters for the overlay host in particular: the panel is composed at the host's
 * position in the tree, so without it the caller's content color and text style would be replaced
 * by whatever the host inherits. All layout decisions are then delegated to [PopupPanelLayout].
 */
@Composable
internal fun PopupPanelContent(
    expanded: Boolean,
    anchor: Rect,
    context: PanelContext,
) {
    context.contentContext.Provide {
        PopupPanelLayout(
            expanded = expanded,
            anchor = anchor,
            context = context,
        )
    }
}

/** Panel rendering, composed inside the styling context restored by [PopupPanelContent]. */
@Composable
private fun PopupPanelLayout(
    expanded: Boolean,
    anchor: Rect,
    context: PanelContext,
) {
    val style = context.style
    val layout = context.layout
    val metrics = style.metrics
    val px = rememberMetricsPx(metrics)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    val windowSize = windowSize()

    // Anything that can change the intrinsic section widths must invalidate the cached
    // measurement. The probe constraint is the consumed slice of the window size and metrics, so
    // it — not its inputs — keys the cache. The width strategy does not affect intrinsic widths
    // and deliberately stays out.
    val maxWrapWidthPx = safeContentWidthPx(windowSize.width.toFloat(), px.edgeMarginPx)
    var measuredSections by remember(
        context.items,
        style.textStyle,
        style.colors.content,
        density,
        maxWrapWidthPx,
    ) { mutableStateOf<RowSectionWidths?>(null) }

    if (measuredSections == null) {
        PanelMeasurer(
            items = context.items,
            contentColor = style.colors.content,
            textStyle = style.textStyle,
            maxWrapWidthPx = maxWrapWidthPx,
            onMeasured = { measuredSections = it },
        )
    }

    // Cross-platform safe-drawing insets. Under edge-to-edge these cover the status/navigation
    // bars, so the vertical clamping keeps the panel clear of them with no caller configuration;
    // on platforms without insets they are zero and only the margins apply.
    val safeDrawing = WindowInsets.safeDrawing

    val alignments = resolvePanelAlignments(
        layout = layout,
        ambientAlignment = LocalPopupAlignment.current,
        layoutDirection = layoutDirection,
    )

    val placement = remember(
        anchor,
        windowSize,
        context.items.size,
        context.selectedIndex,
        layout.width,
        layout.maxHeight,
        alignments.vertical,
        alignments.horizontal,
        metrics,
        layoutDirection,
        density,
        measuredSections,
        safeDrawing,
    ) {
        resolvePlacement(
            anchor = anchor,
            windowSize = windowSize,
            rowCount = context.items.size,
            selectedIndex = context.selectedIndex,
            width = layout.width,
            maxHeight = layout.maxHeight,
            verticalAlignment = alignments.vertical,
            horizontalAlignment = alignments.horizontal,
            metrics = metrics,
            layoutDirection = layoutDirection,
            density = density,
            measuredSections = measuredSections,
            // With no selection, vertical Auto centres the panel on the keyboard cursor's default
            // row (the first enabled item) — the same row the cursor rests on, so the panel and
            // the cursor agree about where "the current row" is.
            defaultCentringRow = initialFocusIndex(context.items, context.selectedIndex),
            safeDrawingTopPx = safeDrawing.getTop(density).toFloat(),
            safeDrawingBottomPx = safeDrawing.getBottom(density).toFloat(),
        )
    }

    // Selecting an item collapses the panel and changes `selectedIndex` in the same frame, so the
    // geometry from the last expanded frame is held for the duration of the collapse. Only the
    // geometry is held — the selection stays live, because the item that has to fly home is the one
    // the user just picked, not the one that was selected when the panel opened.
    var frozenPlacement by remember { mutableStateOf<PanelPlacement?>(null) }
    SideEffect {
        if (expanded && frozenPlacement != placement) frozenPlacement = placement
    }
    val activePlacement = resolveActivePlacement(
        frozen = frozenPlacement,
        expanded = expanded,
        placement = placement,
    )

    val rowSections = measuredSections?.let { sections ->
        val contentAreaPx = (activePlacement.panelRect.width - px.rowHorizontalPaddingPx * 2f)
            .coerceAtLeast(0f)
        sections.clampedToContentArea(contentAreaPx, px.countSpacingPx)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Tap-outside-to-dismiss surface. It is deliberately invisible to accessibility: an
            // unlabelled full-screen clickable would otherwise shadow every row for screen readers,
            // while dismissal remains available via Esc (the surface holds focus) and Back.
            .clearAndSetSemantics { }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = context.onDismiss,
            ),
    )

    PopupMorphSurface(
        expanded = expanded,
        placement = activePlacement,
        context = context,
        sections = rowSections,
    )
}

/**
 * Decides which geometry the panel renders this frame.
 *
 * While expanding or open, the geometry tracks its inputs. While collapsing, the panel-side
 * geometry (rectangle, rows, scroll) is held at the last expanded value, because re-resolving it
 * against a newly selected index would move the panel at the exact moment it is supposed to be
 * shrinking back into the trigger.
 *
 * The anchor rectangle is the one exception and always stays live: selecting an item re-measures
 * the trigger to the new content's width, and the morph's landing rect must be wherever the trigger
 * actually is on the handover frame. Holding the stale anchor rect would make the surface land at
 * the old width and the newly sized trigger would pop into view — a visible size jump.
 *
 * The selection is deliberately not part of this decision: the item being flown home must be the
 * one the user just picked, so it is always read live.
 */
internal fun resolveActivePlacement(
    frozen: PanelPlacement?,
    expanded: Boolean,
    placement: PanelPlacement,
): PanelPlacement = when {
    expanded -> placement
    frozen == null -> placement
    else -> frozen.copy(anchorRect = placement.anchorRect)
}

/**
 * The two placement axes of a panel, with [PopupVerticalAlignment.Inherit] and
 * [PopupHorizontalAlignment.Inherit] resolved against the ambient alignment context, falling back
 * to the library defaults when the caller provided none.
 */
private data class PanelAlignments(
    val vertical: PopupVerticalAlignment,
    val horizontal: PopupHorizontalAlignment,
)

private fun resolvePanelAlignments(
    layout: PopupLayout,
    ambientAlignment: Alignment?,
    layoutDirection: LayoutDirection,
): PanelAlignments = PanelAlignments(
    vertical = if (layout.verticalAlignment == PopupVerticalAlignment.Inherit) {
        ambientAlignment?.toPopupVerticalAlignment() ?: PopupVerticalAlignment.Auto
    } else {
        layout.verticalAlignment
    },
    horizontal = if (layout.horizontalAlignment == PopupHorizontalAlignment.Inherit) {
        ambientAlignment?.toPopupHorizontalAlignment(layoutDirection) ?: PopupHorizontalAlignment.Auto
    } else {
        layout.horizontalAlignment
    },
)

/**
 * Measures the intrinsic width of each item section for aligned rows without compositing a visible
 * row.
 *
 * Two transparent probe columns — one per section — record every item's own section width, so the
 * reported [RowSectionWidths] carries both the shared maxima and the per-item widths the flying
 * selected row needs for its handover interpolation. With aligned sections the panel width is the
 * sum of the section maxima, not the widest row.
 *
 * The probes are fully transparent and carry no semantics, so they never participate in the
 * accessibility tree or produce visible frames.
 */
@Composable
private fun PanelMeasurer(
    items: List<PopupItem>,
    contentColor: Color,
    textStyle: TextStyle?,
    maxWrapWidthPx: Float,
    onMeasured: (RowSectionWidths) -> Unit,
) {
    val density = LocalDensity.current
    val maxWrapWidth = with(density) { maxWrapWidthPx.toDp() }
    val contentWidths = remember(items) {
        mutableStateListOf<Float?>().apply { repeat(items.size) { add(null) } }
    }
    // Items without a count never compose a probe, so their width is finalized up front.
    val countWidths = remember(items) {
        mutableStateListOf<Float?>().apply {
            repeat(items.size) { add(if (items[it].count == null) 0f else null) }
        }
    }

    val probes: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .alpha(0f)
                .clearAndSetSemantics { },
        ) {
            SectionWidthProbe(
                items = items,
                maxWrapWidth = maxWrapWidth,
                contentColor = contentColor,
                slot = { it.content },
                onWidth = { index, widthPx -> contentWidths[index] = widthPx },
            )
            SectionWidthProbe(
                items = items,
                maxWrapWidth = maxWrapWidth,
                contentColor = contentColor,
                slot = { it.count },
                onWidth = { index, widthPx -> countWidths[index] = widthPx },
            )
        }
    }

    if (textStyle != null) {
        ProvideTextStyle(textStyle) { probes() }
    } else {
        probes()
    }

    // Probe rows report through onGloballyPositioned, so the width lists are rewritten one row
    // at a time before measurement completes. Reading them directly from composition would
    // recompose the measurer on every write, and keying an effect on the list contents would
    // restart — and re-run onMeasured — per write. derivedStateOf collapses that into a single
    // boolean: this scope only recomposes when completion actually flips, and the effect fires
    // once. One fire suffices, because onMeasured makes the caller set measuredSections, which
    // unmounts this measurer and its probes entirely. remember(items) re-captures the fresh
    // width lists when items change, and onMeasured goes through rememberUpdatedState because
    // the effect may outlive the recomposition that supplied it.
    val isAllMeasured by remember(items) {
        derivedStateOf {
            contentWidths.all { it != null } && countWidths.all { it != null }
        }
    }
    val currentOnMeasured by rememberUpdatedState(onMeasured)
    LaunchedEffect(isAllMeasured, items) {
        if (isAllMeasured) {
            val content = contentWidths.map { it ?: 0f }
            val counts = countWidths.map { it ?: 0f }
            currentOnMeasured(
                RowSectionWidths(
                    contentWidthPx = content.maxOrNull() ?: 0f,
                    countWidthPx = counts.maxOrNull() ?: 0f,
                    itemContentWidthsPx = content,
                    itemCountWidthsPx = counts,
                ),
            )
        }
    }
}

/**
 * One transparent probe column recording every item's own width for a single section slot.
 *
 * Items whose [slot] is null (an item without a count, say) never compose a probe row; their
 * width keeps the finalized value the caller seeded.
 */
@Composable
private fun SectionWidthProbe(
    items: List<PopupItem>,
    maxWrapWidth: Dp,
    contentColor: Color,
    slot: (PopupItem) -> (@Composable RowScope.() -> Unit)?,
    onWidth: (index: Int, widthPx: Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .wrapContentWidth()
            .widthIn(max = maxWrapWidth),
    ) {
        items.forEachIndexed { index, item ->
            val rowContent = slot(item) ?: return@forEachIndexed
            Box(
                modifier = Modifier
                    .wrapContentWidth()
                    .onGloballyPositioned { coordinates ->
                        onWidth(index, coordinates.size.width.toFloat())
                    },
            ) {
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        content = rowContent,
                    )
                }
            }
        }
    }
}
