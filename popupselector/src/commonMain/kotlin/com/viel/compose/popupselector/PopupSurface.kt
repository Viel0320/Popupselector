package com.viel.compose.popupselector

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

/** Paints the morphing surface and hosts the option list plus the flying selected row inside it. */
@Composable
internal fun PopupMorphSurface(
    expanded: Boolean,
    anchor: Rect,
    panelRect: Rect,
    direction: OpenDirection,
    items: List<PopupItem>,
    selectedIndex: Int?,
    morph: PopupMorphState,
    colors: PopupColors,
    shapes: PopupShapes,
    surfaceModifier: Modifier,
    density: Density,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    collapsedHeight: Dp,
    autoMarquee: Boolean = true,
) {
    val fractionProvider = morph.provider()
    val scrollState = rememberScrollState()
    val cornerPx = with(density) { shapes.corner.toPx() }
    val collapsedBorderPx = with(density) { PopupBorderWidth.toPx() }
    val collapsedElevationPx = with(density) { PopupDefaults.CollapsedElevation.toPx() }
    val expandedElevationPx = with(density) { PopupDefaults.ExpandedElevation.toPx() }
    val panelPaddingPx = with(density) { PopupPanelVerticalPadding.toPx() }
    val chevronLeadingPaddingPx = with(density) { PopupSpacingBase.toPx() }
    val chevronSizePx = with(density) { PopupChevronSize.toPx() }

    Box(
        modifier = Modifier
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
                shadowElevation = lerp(collapsedElevationPx, expandedElevationPx, f)
                shape = RoundedCornerShape(cornerPx)
                clip = true
            }
            .then(surfaceModifier)
            .drawBehind {
                val f = fractionProvider().coerceIn(0f, 1f)
                val corner = CornerRadius(cornerPx, cornerPx)
                
                val bg = lerp(colors.collapsedContainer, colors.expandedContainer, f)
                if (bg.alpha > 0.001f) {
                    drawRoundRect(color = bg, cornerRadius = corner)
                }

                val borderPx = lerp(collapsedBorderPx, 0f, f)
                if (borderPx > 0.25f && colors.border.alpha > 0.01f) {
                    val inset = borderPx / 2f
                    val innerCornerPx = (cornerPx - inset).coerceAtLeast(0f)
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
        Box(
            modifier = Modifier
                .clearAndSetSemantics { }
                .graphicsLayer {
                    val f = fractionProvider().coerceIn(0f, 1f)
                    val chevronAlpha = if (expanded) {
                        (1f - f * 4f).coerceIn(0f, 1f)
                    } else {
                        (1f - f / 0.25f).coerceIn(0f, 1f)
                    }
                    alpha = chevronAlpha
                }
                .layout { measurable, constraints ->
                    val f = fractionProvider().coerceIn(0f, 1f)
                    val surfaceLeft = lerp(anchor.left, panelRect.left, f)
                    val surfaceTop = lerp(anchor.top, panelRect.top, f)
                    val iconLeft = anchor.left + chevronLeadingPaddingPx
                    val iconTop = anchor.top + (anchor.height - chevronSizePx) / 2f
                    val placeable = measurable.measure(
                        Constraints.fixed(
                            chevronSizePx.roundToInt(),
                            chevronSizePx.roundToInt()
                        )
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(
                            (iconLeft - surfaceLeft).roundToInt(),
                            (iconTop - surfaceTop).roundToInt(),
                        )
                    }
                }
        ) {
            Icon(
                imageVector = PopupIcons.ChevronDown,
                contentDescription = null,
                modifier = Modifier.size(PopupChevronSize),
                tint = colors.content,
            )
        }

        PopupOptionList(
            expanded = expanded,
            items = items,
            selectedIndex = selectedIndex,
            direction = direction,
            fractionProvider = fractionProvider,
            scrollState = scrollState,
            contentColor = colors.content,
            verticalPadding = PopupPanelVerticalPadding,
            onSelect = onSelect,
            onDismiss = onDismiss,
            autoMarquee = autoMarquee,
        )

        if (selectedIndex != null) {
            val rowHeightPx = with(density) { PopupRowTouchTargetHeight.toPx() }
            FlyingSelectedRow(
                item = items[selectedIndex],
                selectedIndex = selectedIndex,
                anchor = anchor,
                panelRect = panelRect,
                fractionProvider = fractionProvider,
                contentColor = colors.content,
                collapsedVisualHeightPx = with(density) { collapsedHeight.toPx() },
                expandedRowHeightPx = rowHeightPx,
                panelPaddingPx = panelPaddingPx,
                scrollState = scrollState,
                autoMarquee = autoMarquee,
            )
        }
    }
}

@Composable
private fun PopupOptionList(
    expanded: Boolean,
    items: List<PopupItem>,
    selectedIndex: Int?,
    direction: OpenDirection,
    fractionProvider: () -> Float,
    scrollState: ScrollState,
    contentColor: androidx.compose.ui.graphics.Color,
    verticalPadding: Dp,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    autoMarquee: Boolean = true,
) {
    val rowEnterTranslationPx = with(LocalDensity.current) { PopupRowEnterTranslation.toPx() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(PopupRowVerticalSpacing),
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = index == selectedIndex
            val orderFromOrigin = if (direction.opensDown) index else items.lastIndex - index
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = PopupRowTouchTargetHeight)
                    .graphicsLayer {
                        val f = fractionProvider()
                        val baseAlpha = if (isSelected) {
                            if (f >= 0.999f) 1f else 0f
                        } else if (expanded) {
                            val startAt = (PopupStaggerBase + orderFromOrigin * PopupStaggerStep)
                                .coerceAtMost(PopupStaggerMax)
                            val raw = ((f - startAt) / (1f - startAt)).coerceIn(0f, 1f)
                            val eased = FastOutSlowInEasing.transform(raw)
                            val dir = if (direction.opensDown) 1f else -1f
                            translationY = (1f - eased) * rowEnterTranslationPx * dir
                            eased
                        } else {
                            f.coerceIn(0f, 1f)
                        }
                        alpha = if (item.enabled) baseAlpha else baseAlpha * PopupDisabledAlpha
                    }
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
                    .padding(horizontal = PopupRowHorizontalPadding),
                contentAlignment = Alignment.CenterStart,
            ) {
                PopupRowContent(item = item, contentColor = contentColor, autoMarquee = autoMarquee)
            }
        }
    }
}

/**
 * The selected row drawn as an independent child that lerps from the collapsed button's content
 * box to its computed in-list slot, so the current selection visibly moves into place instead of cross-fading.
 */
@Composable
private fun FlyingSelectedRow(
    item: PopupItem,
    selectedIndex: Int,
    anchor: Rect,
    panelRect: Rect,
    fractionProvider: () -> Float,
    contentColor: androidx.compose.ui.graphics.Color,
    collapsedVisualHeightPx: Float,
    expandedRowHeightPx: Float,
    panelPaddingPx: Float,
    scrollState: ScrollState,
    autoMarquee: Boolean = true,
) {
    val density = LocalDensity.current
    val horizontalPaddingPx = with(density) { PopupRowHorizontalPadding.toPx() }
    val collapsedPaddingPx = with(density) { PopupCollapsedHorizontalPadding.toPx() }
    val collapsedLeadingPaddingPx = with(density) { PopupSpacingBase.toPx() }
    val rowSpacingPx = with(density) { PopupRowVerticalSpacing.toPx() }
    val chevronBlockPx = with(density) { chevronBlockWidth() }

    Box(
        modifier = Modifier
            .clearAndSetSemantics { }
            .graphicsLayer {
                val base = if (isFlyingSelectedRowVisible(fractionProvider())) 1f else 0f
                alpha = if (item.enabled) base else base * PopupDisabledAlpha
            }
            .layout { measurable, constraints ->
                val f = fractionProvider().coerceIn(0f, 1f)
                val srcLeft = anchor.left + collapsedLeadingPaddingPx + chevronBlockPx
                val srcTop = anchor.top + (anchor.height - collapsedVisualHeightPx) / 2f
                val src = Rect(srcLeft, srcTop, anchor.right - collapsedPaddingPx, srcTop + collapsedVisualHeightPx)
                val tgtLeft = panelRect.left + horizontalPaddingPx
                val tgtTop = resolveFlyingSelectedRowTargetTop(
                    panelTop = panelRect.top,
                    panelBottom = panelRect.bottom,
                    panelPadding = panelPaddingPx,
                    selectedIndex = selectedIndex,
                    rowHeight = expandedRowHeightPx,
                    rowSpacing = rowSpacingPx,
                    scrollOffset = scrollState.value.toFloat(),
                )
                val tgt = Rect(tgtLeft, tgtTop, panelRect.right - horizontalPaddingPx, tgtTop + expandedRowHeightPx)
                val rect = lerpRect(src, tgt, f)
                val surfaceLeft = lerp(anchor.left, panelRect.left, f)
                val surfaceTop = lerp(anchor.top, panelRect.top, f)
                val w = rect.width.roundToInt().coerceAtLeast(0)
                val h = rect.height.roundToInt().coerceAtLeast(0)
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(
                        (rect.left - surfaceLeft).roundToInt(),
                        (rect.top - surfaceTop).roundToInt(),
                    )
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        PopupRowContent(item = item, contentColor = contentColor, autoMarquee = autoMarquee)
    }
}
