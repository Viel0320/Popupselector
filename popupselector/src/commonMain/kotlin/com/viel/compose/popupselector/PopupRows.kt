package com.viel.compose.popupselector

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt

/** Tag of the visible collapsed chip, so tests can assert its visual geometry. */
internal const val visualAnchorTag = "popup-anchor-visual"

/**
 * The resting collapsed button: an outlined surface showing the current selection, or a custom
 * [collapsedContent] for menu-style usage, plus a chevron.
 *
 * The chevron here is the resting-state icon; during the morph the anchor is hidden and the single
 * animated instance drawn by the morph surface takes over, so the icon never double-draws.
 *
 * Pressing the trigger shows the selection highlight immediately — the same indicator the panel
 * uses, covering the chip's visual rect only, not the surrounding touch target. On pointer
 * platforms hovering shows the same fill, fading in and out over
 * [PopupTiming.anchorHighlightFadeMillis]. That is exactly the rectangle the flying highlight
 * starts from when the panel opens, so the press feedback hands over seamlessly. On the way back
 * the flying highlight fades across the whole collapse and is already gone at the handover, so the
 * resting trigger never has to carry it.
 */
@Composable
internal fun CollapsedAnchor(
    items: List<PopupItem>,
    selectedIndex: Int?,
    collapsedContent: (@Composable RowScope.() -> Unit)?,
    style: PopupStyle,
    expanded: Boolean,
    anchorModifier: Modifier,
    onClick: () -> Unit,
    onVisualBoundsMeasured: (Rect) -> Unit,
) {
    val metrics = style.metrics
    val colors = style.colors
    val shape = RoundedCornerShape(metrics.corner)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    // Press shows the highlight at once; on desktop the same fill doubles as hover feedback, so
    // entering/leaving with the pointer is the fade path. On release the panel takes over and the
    // anchor is hidden before any fade is visible — the fade-out only plays for canceled presses
    // and unhover.
    val highlight by animateFloatAsState(
        targetValue = if (pressed || hovered) 1f else 0f,
        animationSpec = tween(durationMillis = style.timing.anchorHighlightFadeMillis),
        label = "collapsedHoverHighlight",
    )

    // The outer box is the hit area and inherits any minimum width the caller forces (a
    // fillMaxWidth root propagates it here); both inner levels propagate that minimum so the
    // visible chip fills it too — the visual must never be smaller than the hit area.
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = metrics.collapsedTouchTarget)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.DropdownList,
                onClick = onClick,
            )
            .semantics {
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            },
        contentAlignment = Alignment.CenterStart,
        propagateMinConstraints = true,
    ) {
        Box(
            modifier = Modifier,
            contentAlignment = Alignment.CenterStart,
            propagateMinConstraints = true,
        ) {
            Row(
                modifier = Modifier
                    .testTag(visualAnchorTag)
                    // Outside animateContentSize, so the tracked rect is the animating node's
                    // bounds — the size the morph should land on. Measured from inside, the
                    // content snaps to its natural width as soon as it fits the animated
                    // constraints and then slides with the node's leading edge, so an end-aligned
                    // chip reports a full-size rect gliding across its parent while it resizes,
                    // and the morph chases it instead of shrinking onto the chip.
                    .trackAnchorBounds(onVisualBoundsMeasured)
                    // required, not height, and outside animateContentSize: the propagated
                    // touch-target minimum (48dp) must neither stretch the chip (its visual
                    // height stays collapsedHeight) nor coerce the tracked rect above it.
                    .requiredHeight(metrics.collapsedHeight)
                    .animateContentSize(animationSpec = style.sizeAnimationSpec)
                    .clip(shape)
                    .then(anchorModifier)
                    .background(colors.collapsedContainer, shape)
                    // Press feedback paints inside the visual chip's own bounds — the 48dp touch
                    // target around it stays free of highlight — using the same fill and corner
                    // radius the panel's focused rows use, so the morph handover is seamless.
                    .drawBehind {
                        if (highlight > 0f) {
                            val base = focusedRowHighlightColor(style)
                            drawRoundRect(
                                color = base.copy(alpha = base.alpha * highlight),
                                cornerRadius = CornerRadius(metrics.corner.toPx()),
                            )
                        }
                    }
                    .border(metrics.borderWidth, colors.border, shape)
                    .padding(
                        start = metrics.chevronLeadingPadding,
                        end = metrics.collapsedHorizontalPadding,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(metrics.chevronLeadingPadding),
            ) {
                // Resting-state chevron. It fills the same slot the morph surface's animated chevron
                // lands in, so the handover at the end of the collapse is pixel-aligned; the surface
                // is unmounted once the morph finishes, so the two never draw at the same time.
                Icon(
                    imageVector = PopupIcons.ChevronDown,
                    contentDescription = null,
                    modifier = Modifier.size(metrics.chevronSize),
                    tint = colors.content,
                )

                if (collapsedContent != null) {
                    CompositionLocalProvider(LocalContentColor provides colors.content) {
                        Row(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .then(marqueeModifier(style.autoMarquee)),
                            verticalAlignment = Alignment.CenterVertically,
                            content = collapsedContent,
                        )
                    }
                } else {
                    val item = selectedIndex?.let(items::getOrNull)
                    if (item != null) {
                        PopupRowBody(
                            item = item,
                            contentColor = colors.content,
                            metrics = metrics,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .then(if (item.enabled) Modifier else Modifier.disabledLook(metrics)),
                            autoMarquee = style.autoMarquee,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Shared row body: the caller's content slot plus the optional trailing count slot.
 *
 * The content color is provided once so callers do not need to thread it through their own slots.
 * The content row is weighted (without filling) so a count hugs the trailing edge whenever the row
 * is given more width than its content needs.
 */
@Composable
internal fun PopupRowBody(
    item: PopupItem,
    contentColor: Color,
    metrics: PopupMetrics,
    modifier: Modifier = Modifier,
    autoMarquee: Boolean = true,
) {
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.countSpacing),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .then(marqueeModifier(autoMarquee)),
                verticalAlignment = Alignment.CenterVertically,
                content = item.content,
            )
            // The count slot shares the row scope so it can align and weight like the content.
            item.count?.let { count -> count() }
        }
    }
}

/**
 * How a panel row's content and count sections are laid out.
 *
 * @see PopupRowContent
 */
@Immutable
internal sealed interface RowSectionLayout {
    /** A resting row: both slots occupy the shared section widths measured across all items. */
    data object Static : RowSectionLayout

    /**
     * The flying selected row: the content section interpolates from the item's own width — the
     * trigger's concatenated layout — to the shared width, so the count lands exactly where the
     * anchor and the list row each put it at the two handovers. A static layout would offset one
     * of them for every item that is not the widest.
     *
     * @param itemIndex Index of the item into the sections' per-item width lists.
     * @param morphFraction The morph progress provider, read during layout.
     * @param collapsedContentWidthPx The trigger's content-box width, when known. A label longer
     *   than that is clamped by the trigger itself (count pinned to the trailing edge), so the
     *   interpolation must start from the clamped width too — starting from the natural width
     *   would place the count far outside the surface mid-morph and visibly jump it at the
     *   handover. Null falls back to the item's natural width.
     * @param rowContentWidthPx The panel row's content-box width — the flying row's landing
     *   width. The count's flight interpolates linearly between the two boxes; deriving it from
     *   the *current* container width instead makes the path dip inward while the container is
     *   still narrower than the row it is growing into. Null keeps that container-coupled path.
     */
    data class Flying(
        val itemIndex: Int,
        val morphFraction: () -> Float,
        val collapsedContentWidthPx: Float? = null,
        val rowContentWidthPx: Float? = null,
    ) : RowSectionLayout
}

/**
 * The in-panel row body: full width with the content and count slots laid out as justified
 * sections.
 *
 * When [sections] is supplied, both slots occupy their shared section width — measured as the
 * per-slot maximum across all items — with the content section hugging the start edge and the
 * count section pinned to the trailing edge. On a panel exactly wide enough for the sections
 * (Wrap) the two sit adjacent; any spare width opens up between them.
 */
@Composable
internal fun PopupRowContent(
    item: PopupItem,
    contentColor: Color,
    metrics: PopupMetrics,
    autoMarquee: Boolean = true,
    sections: RowSectionWidths? = null,
    layout: RowSectionLayout = RowSectionLayout.Static,
) {
    if (sections == null) {
        PopupRowBody(
            item = item,
            contentColor = contentColor,
            metrics = metrics,
            modifier = Modifier.fillMaxWidth(),
            autoMarquee = autoMarquee,
        )
        return
    }

    val density = LocalDensity.current
    val contentWidth = with(density) { sections.contentWidthPx.toDp() }
    val countWidth = with(density) { sections.countWidthPx.toDp() }
    // The flying layout only applies when the sections carry this item's own widths; anything
    // else falls back to the shared static layout.
    val flying = (layout as? RowSectionLayout.Flying)
        ?.takeIf { sections.itemContentWidthsPx.getOrNull(it.itemIndex) != null }

    // The interpolation's starting width, clamped to what the trigger could actually give the
    // label: its content box minus the reserved count slot. Without this, a label whose natural
    // width exceeds the trigger sends the section (and its count) far outside the surface
    // mid-morph and pops the count at the handover.
    val flyingOwnWidthPx: Float = if (flying != null) {
        val ownPx = sections.itemContentWidthsPx[flying.itemIndex]
        val collapsedWidth = flying.collapsedContentWidthPx
        if (collapsedWidth != null) {
            val reservedPx = if (item.count != null) {
                with(density) { metrics.countSpacing.toPx() } +
                    (sections.itemCountWidthsPx.getOrNull(flying.itemIndex) ?: 0f)
            } else {
                0f
            }
            minOf(ownPx, (collapsedWidth - reservedPx).coerceAtLeast(0f))
        } else {
            ownPx
        }
    } else {
        0f
    }

    val contentSectionModifier = if (flying != null) {
        val shared = sections.contentWidthPx
        Modifier.layout { measurable, constraints ->
            val fraction = flying.morphFraction().coerceIn(0f, 1f)
            val width = lerp(flyingOwnWidthPx, shared, fraction).roundToInt().coerceAtLeast(0)
            val placeable = measurable.measure(Constraints(maxWidth = width))
            layout(width, placeable.height) { placeable.placeRelative(0, 0) }
        }
    } else {
        Modifier.width(contentWidth)
    }

    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = contentSectionModifier) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(marqueeModifier(autoMarquee)),
                    verticalAlignment = Alignment.CenterVertically,
                    content = item.content,
                )
            }
            item.count?.let { count ->
                val countRow: @Composable () -> Unit = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        content = count,
                    )
                }
                Spacer(modifier = Modifier.width(metrics.countSpacing))
                if (flying != null) {
                    // The count slides from "right after its own (clamped) label" to flush with
                    // the trailing edge, mirroring how the sectioned layout opens up in between.
                    val shared = sections.contentWidthPx
                    val ownCount = sections.itemCountWidthsPx.getOrNull(flying.itemIndex) ?: 0f
                    val countSpacingPx = with(density) { metrics.countSpacing.toPx() }
                    val rowContentWidthPx = flying.rowContentWidthPx
                    Box(modifier = Modifier.weight(1f)) {
                        // The count rides a linear path between its start slot (right after its
                        // own clamped label) and its landing slot (flush with the final row's
                        // trailing edge). The fallback keeps the container-coupled path for any
                        // caller that does not supply the landing width.
                        Box(
                            modifier = Modifier.layout { measurable, constraints ->
                                val fraction = flying.morphFraction().coerceIn(0f, 1f)
                                val x = if (rowContentWidthPx != null && rowContentWidthPx > 0f) {
                                    fraction * (rowContentWidthPx - ownCount - countSpacingPx - shared)
                                } else {
                                    fraction * (constraints.maxWidth +
                                        lerp(flyingOwnWidthPx, shared, fraction) - ownCount - shared)
                                }
                                val placeable = measurable.measure(constraints)
                                layout(placeable.width, placeable.height) {
                                    placeable.placeRelative(x.roundToInt(), 0)
                                }
                            },
                        ) {
                            countRow()
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier.width(countWidth),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        countRow()
                    }
                }
            }
        }
    }
}

/** Marquee behavior for overflowing row content. */
private fun marqueeModifier(enabled: Boolean): Modifier =
    if (enabled) {
        Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1000)
    } else {
        Modifier
    }

/** Dims a row to the standard disabled opacity without changing its layout. */
private fun Modifier.disabledLook(metrics: PopupMetrics): Modifier =
    this.graphicsLayer { alpha = metrics.disabledAlpha }
