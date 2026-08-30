package com.viel.compose.popupselector

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Paints the morphing surface and hosts the option list plus the flying selected row inside it.
 *
 * Every visual layer derives its geometry from a single [PanelPlacement] and a single morph
 * fraction, so the surface, rows and flying selection can never disagree about where things are.
 */
@Composable
internal fun PopupMorphSurface(
    expanded: Boolean,
    placement: PanelPlacement,
    context: PanelContext,
    sections: RowSectionWidths?,
) {
    val style = context.style
    val metrics = style.metrics
    val timing = style.timing
    val colors = style.colors
    val items = context.items
    val selectedIndex = context.selectedIndex

    val px = rememberMetricsPx(metrics)
    val fractionProvider = context.morph.provider()
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // Keyboard cursor, independent of the selection so arrow keys can preview without committing.
    val cursor = remember(items) { PopupKeyboardCursor(items, selectedIndex) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(expanded) {
        if (expanded) focusRequester.requestFocus()
    }

    /** Moves the keyboard cursor and scrolls just enough to keep it visible. */
    suspend fun moveFocus(moveDown: Boolean) {
        val next = cursor.move(items, moveDown) ?: return
        val deltaPx = scrollDeltaToReveal(
            index = next,
            rowHeightPx = px.rowHeightPx,
            rowSpacingPx = px.rowSpacingPx,
            panelPaddingPx = px.panelPaddingPx,
            viewportHeightPx = placement.panelRect.height,
            scrollOffsetPx = scrollState.value.toFloat(),
            maxScrollPx = placement.maxScrollPx,
        ) ?: return
        scrollState.animateScrollBy(deltaPx)
    }

    val anchor = placement.anchorRect
    val panelRect = placement.panelRect

    Box(
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusTarget()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionDown, Key.Tab -> {
                        coroutineScope.launch { moveFocus(moveDown = true) }
                        true
                    }
                    Key.DirectionUp -> {
                        coroutineScope.launch { moveFocus(moveDown = false) }
                        true
                    }
                    Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                        cursor.activatable(items)?.let { index ->
                            context.onSelect(index)
                            context.onDismiss()
                        }
                        true
                    }
                    Key.Escape -> {
                        context.onDismiss()
                        true
                    }
                    else -> false
                }
            }
            .layout { measurable, _ ->
                val f = fractionProvider().coerceIn(0f, 1f)
                val rect = lerpRect(anchor, panelRect, f)
                val w = rect.width.roundToInt().coerceAtLeast(0)
                val h = rect.height.roundToInt().coerceAtLeast(0)
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(w, h) {
                    placeable.place(rect.left.roundToInt(), rect.top.roundToInt())
                }
            }
            .graphicsLayer {
                val f = fractionProvider().coerceIn(0f, 1f)
                shadowElevation = lerp(
                    px.collapsedElevationPx,
                    px.expandedElevationPx,
                    shadowFraction(f, timing.shadowStartFraction),
                )
                shape = RoundedCornerShape(px.cornerPx)
                clip = true
            }
            .then(context.surfaceModifier)
            .drawBehind {
                val f = fractionProvider().coerceIn(0f, 1f)
                val corner = CornerRadius(px.cornerPx, px.cornerPx)

                val bg = lerp(colors.collapsedContainer, colors.expandedContainer, f)
                if (bg.alpha > timing.containerMinAlpha) {
                    drawRoundRect(color = bg, cornerRadius = corner)
                }

                val borderPx = lerp(px.borderWidthPx, 0f, f)
                if (borderPx > timing.borderMinWidth && colors.border.alpha > 0.01f) {
                    val inset = borderPx / 2f
                    val innerCornerPx = (px.cornerPx - inset).coerceAtLeast(0f)
                    drawRoundRect(
                        color = colors.border,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - borderPx, size.height - borderPx),
                        cornerRadius = CornerRadius(innerCornerPx, innerCornerPx),
                        style = Stroke(width = borderPx),
                    )
                }
            },
    ) {
        PopupChevron(
            expanded = expanded,
            anchor = anchor,
            panelRect = panelRect,
            fractionProvider = fractionProvider,
            metrics = metrics,
            timing = timing,
            contentColor = colors.content,
            chevronLeadingPaddingPx = px.chevronLeadingPaddingPx,
            chevronSizePx = px.chevronSizePx,
            layoutDirection = LocalLayoutDirection.current,
        )

        PopupOptionList(
            expanded = expanded,
            items = items,
            selectedIndex = selectedIndex,
            focusedIndex = cursor.index,
            focusIndicatorVisible = selectedIndex != null || cursor.engaged,
            placement = placement,
            fractionProvider = fractionProvider,
            scrollState = scrollState,
            style = style,
            sections = sections,
            onSelect = context.onSelect,
            onDismiss = context.onDismiss,
        )

        if (selectedIndex != null) {
            FlyingSelectedRow(
                item = items[selectedIndex],
                selectedIndex = selectedIndex,
                expanded = expanded,
                placement = placement,
                fractionProvider = fractionProvider,
                style = style,
                sections = sections,
                layoutDirection = LocalLayoutDirection.current,
                scrollState = scrollState,
            )
        }
    }
}

/**
 * The chevron, drawn once and positioned continuously between its collapsed and expanded slots.
 *
 * Rendering a single instance avoids the positional jump that occurs when the collapsed anchor and
 * the expanded surface each draw their own copy and rely on matching constants to stay aligned.
 */
@Composable
private fun PopupChevron(
    expanded: Boolean,
    anchor: Rect,
    panelRect: Rect,
    fractionProvider: () -> Float,
    metrics: PopupMetrics,
    timing: PopupTiming,
    contentColor: Color,
    chevronLeadingPaddingPx: Float,
    chevronSizePx: Float,
    layoutDirection: LayoutDirection,
) {
    Box(
        modifier = Modifier
            .clearAndSetSemantics { }
            .graphicsLayer {
                val f = fractionProvider().coerceIn(0f, 1f)
                val span = if (expanded) timing.chevronExpandFadeSpan else timing.chevronCollapseFadeSpan
                alpha = if (span <= 0f) 0f else (1f - f / span).coerceIn(0f, 1f)
            }
            .layout { measurable, constraints ->
                val f = fractionProvider().coerceIn(0f, 1f)
                val origin = surfaceOrigin(anchor, panelRect, f)
                // The chevron sits on the leading side of the trigger, which mirrors with the
                // text direction.
                val iconLeft = when (layoutDirection) {
                    LayoutDirection.Ltr -> anchor.left + chevronLeadingPaddingPx
                    LayoutDirection.Rtl -> anchor.right - chevronLeadingPaddingPx - chevronSizePx
                }
                val iconTop = anchor.top + (anchor.height - chevronSizePx) / 2f
                val placeable = measurable.measure(
                    Constraints.fixed(chevronSizePx.roundToInt(), chevronSizePx.roundToInt()),
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(
                        (iconLeft - origin.x).roundToInt(),
                        (iconTop - origin.y).roundToInt(),
                    )
                }
            },
    ) {
        Icon(
            imageVector = PopupIcons.ChevronDown,
            contentDescription = null,
            modifier = Modifier.height(metrics.chevronSize),
            tint = contentColor,
        )
    }
}

/**
 * The scrollable option list.
 *
 * Rows are laid out at their natural content height; their on-screen position during the morph is
 * controlled entirely by alpha and translation, never by the layout, so the list geometry stays
 * stable while the surface around it animates.
 */
@Composable
private fun PopupOptionList(
    expanded: Boolean,
    items: List<PopupItem>,
    selectedIndex: Int?,
    focusedIndex: Int,
    focusIndicatorVisible: Boolean,
    placement: PanelPlacement,
    fractionProvider: () -> Float,
    scrollState: ScrollState,
    style: PopupStyle,
    sections: RowSectionWidths?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val metrics = style.metrics
    val timing = style.timing

    val rowEnterTranslationPx = with(density) { metrics.rowEnterTranslation.toPx() }

    // Adopt the placement's scroll offset once per expansion: the geometry resolved when the panel
    // opened assumed this offset, so the list has to start there for the flying selected row and
    // the real rows to agree. Re-adopting on every placement change would yank the user's scroll
    // position on a window resize, and relaunch per frame while the collapsing panel tracks the
    // live anchor — so later changes never overwrite the user's scroll.
    LaunchedEffect(expanded) {
        if (expanded) {
            val target = placement.initialScrollPx.roundToInt()
            if (scrollState.value != target) scrollState.scrollTo(target)
        }
    }

    // No explicit height: the morph surface measures this list with the surface's own size, so the
    // scroll viewport grows and shrinks with the morph and settles on the panel height when open.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(vertical = metrics.panelVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(metrics.rowVerticalSpacing),
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = index == selectedIndex
            // The focus highlight doubles as the selection indicator before the handover, so
            // with no selection it stays hidden until the keyboard cursor actually moves.
            val isFocused = index == focusedIndex && focusIndicatorVisible
            val orderFromOrigin = if (placement.opensDown) index else items.lastIndex - index

            Box(modifier = Modifier.fillMaxWidth().height(metrics.rowTouchTargetHeight)) {
                // For the selected row the highlight layer follows the row content's handover gate:
                // during the morph the flying row carries the highlight (travelling with the
                // selection), so a highlight fading in early on this stationary slot would read as
                // a second indicator moving independently of the selected item. Keyboard focus on
                // a non-selected row rides the content layer instead, so it translates and fades
                // with that row.
                if (isFocused && isSelected) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                alpha = revealAlphaFor(
                                    item = item,
                                    alpha = revealAlpha(
                                        fraction = fractionProvider().coerceIn(0f, 1f),
                                        expanded = expanded,
                                        isSelected = isSelected,
                                        orderFromOrigin = orderFromOrigin,
                                        opensDown = placement.opensDown,
                                        timing = timing,
                                    ),
                                    metrics = metrics,
                                )
                            }
                            .then(Modifier.focusedRowBackground(style)),
                    )
                }

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            val f = fractionProvider().coerceIn(0f, 1f)
                            val baseAlpha = revealAlpha(
                                fraction = f,
                                expanded = expanded,
                                isSelected = isSelected,
                                orderFromOrigin = orderFromOrigin,
                                opensDown = placement.opensDown,
                                timing = timing,
                            )
                            if (!isSelected && expanded) {
                                val eased = revealProgress(
                                    fraction = f,
                                    orderFromOrigin = orderFromOrigin,
                                    timing = timing,
                                    easing = FastOutSlowInEasing,
                                )
                                val dir = if (placement.opensDown) 1f else -1f
                                translationY = (1f - eased) * rowEnterTranslationPx * dir
                            }
                            alpha = revealAlphaFor(item, baseAlpha, metrics)
                        }
                        // While collapsing, the fading rows leave the accessibility tree with
                        // the surface; once collapsed the surface unmounts entirely.
                        .then(if (expanded) Modifier else Modifier.clearAndSetSemantics { })
                        .selectable(
                            selected = isSelected,
                            enabled = item.enabled,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = if (selectedIndex != null) Role.RadioButton else Role.Button,
                            onClick = {
                                onSelect(index)
                                onDismiss()
                            },
                        )
                        .then(
                            if (isFocused && !isSelected) Modifier.focusedRowBackground(style) else Modifier
                        )
                        .padding(horizontal = metrics.rowHorizontalPadding),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    PopupRowContent(
                        item = item,
                        contentColor = style.colors.content,
                        metrics = metrics,
                        autoMarquee = style.autoMarquee,
                        sections = sections,
                    )
                }
            }
        }
    }
}

/**
 * The selection/keyboard-cursor highlight: a translucent overlay so it reads against any container
 * colour and does not require a separate role in the colour scheme. Also used as the collapsed
 * trigger's press feedback.
 */
internal fun Modifier.focusedRowBackground(style: PopupStyle): Modifier = this
    .background(color = focusedRowHighlightColor(style), shape = RoundedCornerShape(style.metrics.corner))

/** The [focusedRowBackground] fill, exposed so draw-based callers can reuse the same colour. */
internal fun focusedRowHighlightColor(style: PopupStyle): Color =
    style.colors.content.copy(alpha = 0.12f)

/** [alpha] dimmed by the standard disabled opacity unless [item] is enabled. */
private fun revealAlphaFor(item: PopupItem, alpha: Float, metrics: PopupMetrics): Float =
    if (item.enabled) alpha else alpha * metrics.disabledAlpha

/** Fraction of the reveal animation already completed for one row. */
private fun revealProgress(
    fraction: Float,
    orderFromOrigin: Int,
    timing: PopupTiming,
    easing: Easing,
): Float {
    val startAt = (timing.staggerBase + orderFromOrigin * timing.staggerStep)
        .coerceAtMost(timing.staggerMax)
        .coerceAtMost(1f - timing.visibleEpsilon)
    if (fraction <= startAt) return 0f
    val raw = ((fraction - startAt) / (1f - startAt)).coerceIn(0f, 1f)
    return easing.transform(raw)
}

/** Alpha of one row at a given morph fraction. */
private fun revealAlpha(
    fraction: Float,
    expanded: Boolean,
    isSelected: Boolean,
    orderFromOrigin: Int,
    opensDown: Boolean,
    timing: PopupTiming,
): Float {
    if (isSelected) {
        // The flying row owns the selection until the morph is essentially finished.
        return if (fraction >= timing.morphCompleteThreshold) 1f else 0f
    }
    if (!expanded) return fraction.coerceIn(0f, 1f)
    return revealProgress(fraction, orderFromOrigin, timing, FastOutSlowInEasing)
}

/**
 * The selected row, drawn as an independent child that flies from the collapsed button into its
 * computed in-list slot.
 *
 * The container rectangle spans the whole row — trigger at fraction `0`, row slot at `1` — and
 * carries the selection highlight. While expanding, the highlight travels with the selection; on
 * the way back it fades across the whole collapse, so it dissolves with the settling panel instead
 * of disappearing together with the unmounting surface. The content box rides inside the container
 * via [FlyingRowGeometry.contentRect]. At [PopupTiming.morphCompleteThreshold] the container
 * coincides exactly with the list row, and the real row takes over.
 */
@Composable
private fun FlyingSelectedRow(
    item: PopupItem,
    selectedIndex: Int,
    expanded: Boolean,
    placement: PanelPlacement,
    fractionProvider: () -> Float,
    style: PopupStyle,
    sections: RowSectionWidths?,
    layoutDirection: LayoutDirection,
    scrollState: ScrollState,
) {
    val metrics = style.metrics
    val timing = style.timing
    val px = rememberMetricsPx(metrics)

    val anchor = placement.anchorRect
    val panelRect = placement.panelRect

    // Endpoint rectangles and scroll-clamp bounds are per-placement constants; a frame's layout
    // work is one clamp and two lerps.
    val geometry = remember(anchor, panelRect, selectedIndex, px, layoutDirection) {
        FlyingRowGeometry.of(
            anchor = anchor,
            panelRect = panelRect,
            index = selectedIndex,
            px = px,
            layoutDirection = layoutDirection,
        )
    }

    Box(
        modifier = Modifier
            .clearAndSetSemantics { }
            .graphicsLayer {
                val base = if (fractionProvider() < timing.morphCompleteThreshold) 1f else 0f
                alpha = revealAlphaFor(item, base, metrics)
            }
            .layout { measurable, constraints ->
                val f = fractionProvider().coerceIn(0f, 1f)
                val container = geometry.containerRect(f, scrollState.value.toFloat())
                val origin = surfaceOrigin(anchor, panelRect, f)
                val w = container.width.roundToInt().coerceAtLeast(0)
                val h = container.height.roundToInt().coerceAtLeast(0)
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(
                        (container.left - origin.x).roundToInt(),
                        (container.top - origin.y).roundToInt(),
                    )
                }
            },
    ) {
        // Full strength while expanding (the indicator travels with the selection); across the
        // collapse it tracks the morph fraction itself, so it is already gone by the time the
        // surface unmounts and the resting trigger — which carries no highlight — appears. The
        // fraction is read here, inside the layer lambda: reading it during composition would
        // recompose this row on every frame of the collapse.
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = if (expanded) 1f else fractionProvider().coerceIn(0f, 1f) }
                .then(Modifier.focusedRowBackground(style)),
        )

        Box(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val f = fractionProvider().coerceIn(0f, 1f)
                    val scrollOffsetPx = scrollState.value.toFloat()
                    val container = geometry.containerRect(f, scrollOffsetPx)
                    val content = geometry.contentRect(f, scrollOffsetPx)
                    val w = content.width.roundToInt().coerceAtLeast(0)
                    val h = content.height.roundToInt().coerceAtLeast(0)
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(
                            (content.left - container.left).roundToInt(),
                            (content.top - container.top).roundToInt(),
                        )
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            PopupRowContent(
                item = item,
                contentColor = style.colors.content,
                metrics = metrics,
                autoMarquee = style.autoMarquee,
                sections = sections,
                layout = RowSectionLayout.Flying(
                    itemIndex = selectedIndex,
                    morphFraction = fractionProvider,
                    collapsedContentWidthPx = geometry.collapsedContentWidthPx,
                    rowContentWidthPx = geometry.rowContentWidthPx,
                ),
            )
        }
    }
}
