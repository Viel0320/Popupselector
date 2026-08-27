package com.viel.compose.popupselector

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/** One Popup option. */
@Immutable
class PopupItem(
    val key: Any? = null,
    val enabled: Boolean = true,
    val content: @Composable RowScope.() -> Unit,
    val count: (@Composable () -> Unit)? = null,
)

/** Convenience builder for the common "label + optional count" row, such as "All 75". */
fun textPopupItem(
    key: Any,
    label: String,
    count: Int? = null,
    enabled: Boolean = true,
    textStyle: TextStyle? = null,
    countTextStyle: TextStyle? = null,
): PopupItem = PopupItem(
    key = key,
    enabled = enabled,
    content = {
        Text(
            text = label,
            style = textStyle ?: LocalTextStyle.current,
            color = Color.Unspecified,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    },
    count = count?.let {
        {
            Text(
                text = it.toString(),
                style = countTextStyle ?: textStyle ?: LocalTextStyle.current,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    },
)

/** Requested expansion origin. [Auto] picks the corner with the most available room. */
enum class PopupAlignment {
    Auto,
    TopStart,
    TopEnd,
    BottomStart,
    BottomEnd,
    TopLeft,
    TopRight,
    BottomLeft,
    BottomRight,
}

/** Expanded panel width strategy. */
sealed interface PopupWidth {
    data object MatchAnchor : PopupWidth
    data object Wrap : PopupWidth
    data class Fixed(val width: Dp) : PopupWidth
}

/** Color roles for both morph endpoints. Interpolated continuously by the expand fraction. */
@Immutable
data class PopupColors(
    val collapsedContainer: Color,
    val expandedContainer: Color,
    val border: Color,
    val content: Color,
)

/** Corner radius for popup surfaces. */
@Immutable
data class PopupShapes(
    val corner: Dp = 8.dp,
)

/** Theme-aligned defaults for [PopupSelector]. */
object PopupDefaults {
    val CollapsedElevation = 0.dp
    val ExpandedElevation = 6.dp

    @Composable
    fun colors(
        // Container colors must be opaque; transparent values cause shadow flickering during animation.
        // To achieve frosted-glass styling, match the underlying background color and apply blur via anchorModifier / surfaceModifier.
        collapsedContainer: Color = MaterialTheme.colorScheme.surfaceContainer,
        expandedContainer: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border: Color = MaterialTheme.colorScheme.outline,
        content: Color = MaterialTheme.colorScheme.onSurface,
    ): PopupColors = PopupColors(
        collapsedContainer = collapsedContainer,
        expandedContainer = expandedContainer,
        border = border,
        content = content,
    )

    /**
     * Preconfigured transparent color palette suitable for frosted glass (blur/Haze) styling.
     * Container backgrounds are transparent so underlying blurred surfaces can be sampled.
     */
    @Composable
    fun blurColors(
        border: Color = MaterialTheme.colorScheme.outline,
        content: Color = MaterialTheme.colorScheme.onSurface,
    ): PopupColors = PopupColors(
        collapsedContainer = Color.Transparent,
        expandedContainer = Color.Transparent,
        border = border,
        content = content,
    )

    @Composable
    fun shapes(corner: Dp = 8.dp): PopupShapes = PopupShapes(corner = corner)

    /** Standard morph animation spec with aligned spring dynamics. */
    fun animationSpec(): AnimationSpec<Float> = spring(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Aligned size animation spec for anchor morphing. */
    fun sizeAnimationSpec(): FiniteAnimationSpec<IntSize> = spring(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Default host strategy for expanded popup surfaces (automatically uses Overlay if present). */
    fun hostPolicy(): PopupHostPolicy = PopupHostPolicy.Auto

    /** Whether text overflowing option rows automatically uses smooth marquee scrolling. */
    fun autoMarquee(): Boolean = true
}

/**
 * A fluid popup selector that morphs between a collapsed button and an expanded floating panel.
 *
 * @param items The list of options to display.
 * @param expanded Whether the popup is currently expanded.
 * @param onExpandedChange Callback triggered when expansion state changes.
 * @param onSelect Callback invoked with the index of the selected item.
 * @param modifier Modifier for the root container.
 * @param selectedIndex The index of the currently selected item, or null if none.
 * @param collapsedContent Optional custom content for the collapsed button.
 * @param alignment Preferred alignment origin when expanding.
 * @param panelWidth Strategy for panel width calculation.
 * @param panelMaxHeight Maximum allowed panel height.
 * @param hostPolicy Strategy for hosting the expanded panel (defaults to [PopupHostPolicy.Auto]).
 * @param autoMarquee Whether option rows overflowing their container automatically scroll marquee.
 * @param anchorModifier Optional modifier applied to the collapsed trigger surface (e.g. for frosted glass blur via Haze, where sampling source is placed on background layer behind anchor to avoid flicker).
 * @param surfaceModifier Optional modifier applied to the expanded floating surface (e.g. for frosted glass blur via Haze, where sampling source covers broader screen area).
 * @param textStyle Optional typography style for selector labels (defaults to ambient [LocalTextStyle]).
 * @param colors Color styling configuration for collapsed and expanded states.
 * @param shapes Corner radius configuration.
 * @param collapsedHeight Height of the collapsed button surface.
 * @param animationSpec Main morph and reveal animation specification.
 * @param sizeAnimationSpec Size change animation specification for the collapsed anchor button.
 * @param expandedAccessibilityDescription Accessibility text for expanded state.
 * @param collapsedAccessibilityDescription Accessibility text for collapsed state.
 */
@Composable
fun PopupSelector(
    items: List<PopupItem>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    collapsedContent: (@Composable RowScope.() -> Unit)? = null,
    alignment: PopupAlignment = PopupAlignment.Auto,
    panelWidth: PopupWidth = PopupWidth.MatchAnchor,
    panelMaxHeight: Dp = Dp.Unspecified,
    hostPolicy: PopupHostPolicy = PopupDefaults.hostPolicy(),
    autoMarquee: Boolean = PopupDefaults.autoMarquee(),
    anchorModifier: Modifier = Modifier,
    surfaceModifier: Modifier = Modifier,
    textStyle: TextStyle? = null,
    colors: PopupColors = PopupDefaults.colors(),
    shapes: PopupShapes = PopupDefaults.shapes(),
    collapsedHeight: Dp = PopupUnifiedHeight,
    animationSpec: AnimationSpec<Float> = PopupDefaults.animationSpec(),
    sizeAnimationSpec: FiniteAnimationSpec<IntSize> = PopupDefaults.sizeAnimationSpec(),
    expandedAccessibilityDescription: String = "Expanded",
    collapsedAccessibilityDescription: String = "Collapsed",
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val morph = remember { PopupMorphState(if (expanded) 1f else 0f) }
    LaunchedEffect(expanded, animationSpec) { 
        morph.animateTo(if (expanded) 1f else 0f, animationSpec = animationSpec) 
    }

    val safeSelectedIndex = selectedIndex?.takeIf { it in items.indices }

    var anchorBounds by remember { mutableStateOf<Rect?>(null) }

    val contentScope: @Composable () -> Unit = {
        Box(
            modifier = modifier,
            propagateMinConstraints = true,
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer { alpha = if (morph.isVisible) 0f else 1f },
                propagateMinConstraints = true,
            ) {
                CollapsedAnchor(
                    items = items,
                    selectedIndex = safeSelectedIndex,
                    collapsedContent = collapsedContent,
                    colors = colors,
                    shapes = shapes,
                    expanded = expanded,
                    anchorModifier = anchorModifier,
                    onClick = { onExpandedChange(!expanded) },
                    onVisualBoundsMeasured = { anchorBounds = it },
                    collapsedHeight = collapsedHeight,
                    sizeAnimationSpec = sizeAnimationSpec,
                    expandedAccessibilityDescription = expandedAccessibilityDescription,
                    collapsedAccessibilityDescription = collapsedAccessibilityDescription,
                    autoMarquee = autoMarquee,
                )
            }

            val bounds = anchorBounds
            if (bounds != null && morph.isVisible) {
                PopupPanelHost(
                    expanded = expanded,
                    hostPolicy = hostPolicy,
                    anchor = bounds,
                    items = items,
                    selectedIndex = safeSelectedIndex,
                    morph = morph,
                    colors = colors,
                    shapes = shapes,
                    alignment = alignment,
                    panelWidth = panelWidth,
                    panelMaxHeight = panelMaxHeight,
                    surfaceModifier = surfaceModifier,
                    density = density,
                    onSelect = onSelect,
                    onDismiss = { onExpandedChange(false) },
                    collapsedHeight = collapsedHeight,
                    autoMarquee = autoMarquee,
                )
            }
        }
    }

    if (textStyle != null) {
        ProvideTextStyle(textStyle, content = contentScope)
    } else {
        contentScope()
    }
}
