package com.viel.compose.popupselector

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp

/**
 * The resting collapsed button: an outlined surface with the current selection (filter)
 * or a custom [collapsedContent] (menu), plus a chevron.
 */
@Composable
internal fun CollapsedAnchor(
    items: List<PopupItem>,
    selectedIndex: Int?,
    collapsedContent: (@Composable RowScope.() -> Unit)?,
    colors: PopupColors,
    shapes: PopupShapes,
    expanded: Boolean,
    anchorModifier: Modifier,
    onClick: () -> Unit,
    onVisualBoundsMeasured: (Rect) -> Unit,
    collapsedHeight: Dp,
    sizeAnimationSpec: androidx.compose.animation.core.FiniteAnimationSpec<androidx.compose.ui.unit.IntSize>,
    expandedAccessibilityDescription: String = "Expanded",
    collapsedAccessibilityDescription: String = "Collapsed",
    autoMarquee: Boolean = true,
) {
    val shape = RoundedCornerShape(shapes.corner)
    val stateDesc = if (expanded) expandedAccessibilityDescription else collapsedAccessibilityDescription

    val containerColor = colors.collapsedContainer
    val borderColor = colors.border
    val contentColor = colors.content

    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = PopupCollapsedTouchTarget)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.DropdownList,
                onClick = onClick,
            )
            .semantics { stateDescription = stateDesc },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .onGloballyPositioned { onVisualBoundsMeasured(it.boundsInWindow()) }
                .animateContentSize(animationSpec = sizeAnimationSpec)
                .height(collapsedHeight)
                .clip(shape)
                .then(anchorModifier)
                .background(containerColor, shape)
                .border(PopupBorderWidth, borderColor, shape)
                .padding(start = PopupSpacingBase, end = PopupCollapsedHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PopupSpacingBase),
        ) {
            Icon(
                imageVector = PopupIcons.ChevronDown,
                contentDescription = null,
                modifier = Modifier.size(PopupChevronSize),
                tint = contentColor,
            )
            if (collapsedContent != null) {
                val marqueeModifier = if (autoMarquee) {
                    Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1000)
                } else {
                    Modifier
                }
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    Row(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .then(marqueeModifier),
                        verticalAlignment = Alignment.CenterVertically,
                        content = collapsedContent,
                    )
                }
            } else {
                val item = selectedIndex?.let(items::getOrNull)
                if (item != null) {
                    val itemModifier = if (item.enabled) Modifier else Modifier.graphicsLayer { alpha = PopupDisabledAlpha }
                    PopupRowBody(
                        item = item,
                        contentColor = contentColor,
                        modifier = itemModifier.weight(1f, fill = false),
                        weightContent = true,
                        autoMarquee = autoMarquee,
                    )
                }
            }
        }
    }
}

/**
 * Shared row body: the caller's content slot plus the optional trailing count slot,
 * wrapped so the content color is provided once.
 */
@Composable
internal fun PopupRowBody(
    item: PopupItem,
    contentColor: Color,
    modifier: Modifier = Modifier,
    weightContent: Boolean = false,
    autoMarquee: Boolean = true,
) {
    val marqueeModifier = if (autoMarquee) {
        Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1000)
    } else {
        Modifier
    }

    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PopupCountSpacing),
        ) {
            Row(
                modifier = (if (weightContent) Modifier.weight(1f, fill = false) else Modifier)
                    .then(marqueeModifier),
                verticalAlignment = Alignment.CenterVertically,
                content = item.content,
            )
            item.count?.invoke()
        }
    }
}

/** The in-panel row body: full width with the content slot taking the free space. */
@Composable
internal fun PopupRowContent(
    item: PopupItem,
    contentColor: Color,
    autoMarquee: Boolean = true,
) {
    PopupRowBody(
        item = item,
        contentColor = contentColor,
        modifier = Modifier.fillMaxWidth(),
        weightContent = true,
        autoMarquee = autoMarquee,
    )
}
