package com.viel.compose.popupselector

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * Dispatches popup rendering to either a system [Popup] or an in-window [PopupOverlayHost].
 */
@Composable
internal fun PopupPanelHost(
    expanded: Boolean,
    hostPolicy: PopupHostPolicy,
    anchor: Rect,
    items: List<PopupItem>,
    selectedIndex: Int?,
    morph: PopupMorphState,
    colors: PopupColors,
    shapes: PopupShapes,
    alignment: PopupAlignment,
    panelWidth: PopupWidth,
    panelMaxHeight: Dp,
    surfaceModifier: Modifier,
    density: Density,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    collapsedHeight: Dp,
    autoMarquee: Boolean = true,
) {
    val localHost = LocalPopupOverlayHost.current
    val targetHostState = when (hostPolicy) {
        PopupHostPolicy.Auto -> localHost
        is PopupHostPolicy.Overlay -> hostPolicy.hostState ?: localHost
        PopupHostPolicy.Window -> null
    }

    if (targetHostState != null) {
        DisposableEffect(targetHostState) {
            onDispose {
                targetHostState.setContent(null)
            }
        }
        SideEffect {
            targetHostState.setContent {
                PopupPanelContent(
                    expanded = expanded,
                    anchor = anchor,
                    items = items,
                    selectedIndex = selectedIndex,
                    morph = morph,
                    colors = colors,
                    shapes = shapes,
                    alignment = alignment,
                    panelWidth = panelWidth,
                    panelMaxHeight = panelMaxHeight,
                    surfaceModifier = surfaceModifier,
                    density = density,
                    onSelect = onSelect,
                    onDismiss = onDismiss,
                    collapsedHeight = collapsedHeight,
                    autoMarquee = autoMarquee,
                )
            }
        }
    } else {
        PopupPanel(
            expanded = expanded,
            anchor = anchor,
            items = items,
            selectedIndex = selectedIndex,
            morph = morph,
            colors = colors,
            shapes = shapes,
            alignment = alignment,
            panelWidth = panelWidth,
            panelMaxHeight = panelMaxHeight,
            surfaceModifier = surfaceModifier,
            density = density,
            onSelect = onSelect,
            onDismiss = onDismiss,
            collapsedHeight = collapsedHeight,
            autoMarquee = autoMarquee,
        )
    }
}

/**
 * Hosts the morph surface in a full-screen [Popup] with clipping disabled,
 * so the surface can paint above or below the anchor.
 */
@Composable
internal fun PopupPanel(
    expanded: Boolean,
    anchor: Rect,
    items: List<PopupItem>,
    selectedIndex: Int?,
    morph: PopupMorphState,
    colors: PopupColors,
    shapes: PopupShapes,
    alignment: PopupAlignment,
    panelWidth: PopupWidth,
    panelMaxHeight: Dp,
    surfaceModifier: Modifier,
    density: Density,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    collapsedHeight: Dp,
    autoMarquee: Boolean = true,
) {
    Popup(
        popupPositionProvider = TopLeftPositionProvider,
        onDismissRequest = onDismiss,
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
            items = items,
            selectedIndex = selectedIndex,
            morph = morph,
            colors = colors,
            shapes = shapes,
            alignment = alignment,
            panelWidth = panelWidth,
            panelMaxHeight = panelMaxHeight,
            surfaceModifier = surfaceModifier,
            density = density,
            onSelect = onSelect,
            onDismiss = onDismiss,
            collapsedHeight = collapsedHeight,
            autoMarquee = autoMarquee,
        )
    }
}

/**
 * Pure composable representing the full-screen interactive layout and morph surface.
 */
@Composable
internal fun PopupPanelContent(
    expanded: Boolean,
    anchor: Rect,
    items: List<PopupItem>,
    selectedIndex: Int?,
    morph: PopupMorphState,
    colors: PopupColors,
    shapes: PopupShapes,
    alignment: PopupAlignment,
    panelWidth: PopupWidth,
    panelMaxHeight: Dp,
    surfaceModifier: Modifier,
    density: Density,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    collapsedHeight: Dp,
    autoMarquee: Boolean = true,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val winWPx = with(density) { maxWidth.toPx() }
        val winHPx = with(density) { maxHeight.toPx() }
        val edgeMarginPx = with(density) { PopupSpacingBase.toPx() }
        val maxWrapWidth = maxWidth - PopupSpacingBase * 2

        // Item identity and panelWidth invalidate the cached intrinsic size because labels, row count and width mode affect geometry.
        var measuredPanelHeightPx by remember(items, panelWidth) { mutableStateOf<Float?>(null) }
        var measuredPanelWidthPx by remember(items, panelWidth) { mutableStateOf<Float?>(null) }

        val fixedWidthDp: Dp? = when (panelWidth) {
            PopupWidth.MatchAnchor -> with(density) { anchor.width.toDp() }
            is PopupWidth.Fixed -> panelWidth.width
            PopupWidth.Wrap -> null
        }

        val hasMeasured = measuredPanelWidthPx != null && measuredPanelHeightPx != null
        if (!hasMeasured) {
            PanelMeasurer(
                items = items,
                contentColor = colors.content,
                fixedWidth = fixedWidthDp,
                maxWrapWidth = maxWrapWidth,
                onMeasured = { w, h ->
                    measuredPanelWidthPx = w
                    measuredPanelHeightPx = h
                },
            )
        }

        val estimatedHeightPx = with(density) { calculatePanelHeight(items.size) }
        val maxHeightPx = if (panelMaxHeight != Dp.Unspecified) {
            with(density) { panelMaxHeight.toPx() }
        } else {
            winHPx * PopupMaxHeightFraction
        }
        val panelHeightPx = (measuredPanelHeightPx ?: estimatedHeightPx).coerceAtMost(maxHeightPx)

        val maxAllowedWidthPx = (winWPx - edgeMarginPx * 2f).coerceAtLeast(0f)
        val resolvedWidthPx = when (panelWidth) {
            PopupWidth.MatchAnchor -> anchor.width.coerceIn(0f, maxAllowedWidthPx)
            is PopupWidth.Fixed -> with(density) { panelWidth.width.toPx() }.coerceIn(0f, maxAllowedWidthPx)
            PopupWidth.Wrap -> {
                val minWidthPx = anchor.width.coerceAtMost(maxAllowedWidthPx)
                (measuredPanelWidthPx ?: anchor.width)
                    .coerceIn(minWidthPx, maxAllowedWidthPx)
            }
        }

        val layoutDirection = LocalLayoutDirection.current
        val computedDirection = remember(
            anchor,
            resolvedWidthPx,
            estimatedHeightPx,
            maxHeightPx,
            winHPx,
            winWPx,
            edgeMarginPx,
            alignment,
            layoutDirection,
        ) {
            resolveDirection(
                anchor = anchor,
                panelHeightPx = estimatedHeightPx.coerceAtMost(maxHeightPx),
                panelWidthPx = resolvedWidthPx,
                windowHeightPx = winHPx,
                windowWidthPx = winWPx,
                edgeMarginPx = edgeMarginPx,
                requested = alignment,
                layoutDirection = layoutDirection,
            )
        }

        val rowHeightPx = with(density) { PopupRowTouchTargetHeight.toPx() }
        val rowSpacingPx = with(density) { PopupRowVerticalSpacing.toPx() }
        val panelPaddingPx = with(density) { PopupPanelVerticalPadding.toPx() }

        val computedPanelRect = if (selectedIndex != null) {
            resolvePanelRectCentered(
                anchor = anchor,
                panelWidthPx = resolvedWidthPx,
                panelHeightPx = panelHeightPx,
                windowWidthPx = winWPx,
                windowHeightPx = winHPx,
                edgeMarginPx = edgeMarginPx,
                selectedIndex = selectedIndex,
                rowHeightPx = rowHeightPx,
                rowSpacingPx = rowSpacingPx,
                panelPaddingPx = panelPaddingPx,
                layoutDirection = layoutDirection,
            )
        } else {
            resolvePanelRect(
                anchor = anchor,
                direction = computedDirection,
                panelWidthPx = resolvedWidthPx,
                panelHeightPx = panelHeightPx,
                windowWidthPx = winWPx,
                edgeMarginPx = edgeMarginPx,
            )
        }

        var cachedPanelRect by remember { mutableStateOf<Rect?>(null) }
        var cachedDirection by remember { mutableStateOf<OpenDirection?>(null) }

        if (expanded || cachedPanelRect == null) {
            cachedPanelRect = computedPanelRect
            cachedDirection = computedDirection
        }

        val panelRect = if (expanded) computedPanelRect else (cachedPanelRect ?: computedPanelRect)
        val direction = if (expanded) computedDirection else (cachedDirection ?: computedDirection)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
        )

        PopupMorphSurface(
            expanded = expanded,
            anchor = anchor,
            panelRect = panelRect,
            direction = direction,
            items = items,
            selectedIndex = selectedIndex,
            morph = morph,
            colors = colors,
            shapes = shapes,
            surfaceModifier = surfaceModifier,
            density = density,
            onSelect = onSelect,
            onDismiss = onDismiss,
            collapsedHeight = collapsedHeight,
            autoMarquee = autoMarquee,
        )
    }
}

@Composable
private fun PanelMeasurer(
    items: List<PopupItem>,
    contentColor: androidx.compose.ui.graphics.Color,
    fixedWidth: Dp?,
    maxWrapWidth: Dp,
    onMeasured: (widthPx: Float, heightPx: Float) -> Unit,
) {
    val widthModifier = if (fixedWidth != null) {
        Modifier.width(fixedWidth)
    } else {
        Modifier
            .wrapContentWidth()
            .widthIn(max = maxWrapWidth)
    }
    Column(
        modifier = widthModifier
            .alpha(0f)
            .onGloballyPositioned { onMeasured(it.size.width.toFloat(), it.size.height.toFloat()) }
            .padding(vertical = PopupPanelVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(PopupRowVerticalSpacing),
    ) {
        items.forEach { item ->
            Box(
                modifier = Modifier
                    .wrapContentWidth()
                    .heightIn(min = PopupRowTouchTargetHeight)
                    .padding(horizontal = PopupRowHorizontalPadding),
                contentAlignment = Alignment.CenterStart,
            ) {
                PopupRowBody(item = item, contentColor = contentColor, autoMarquee = false)
            }
        }
    }
}
