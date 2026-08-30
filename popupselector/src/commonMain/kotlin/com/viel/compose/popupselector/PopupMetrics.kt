package com.viel.compose.popupselector

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Timing constants for the morph animation.
 *
 * Values are expressed as fractions of the single morph progress in `[0, 1]` so every animated
 * part of the popup derives its timing from one shared source instead of hard-coded literals.
 * [anchorHighlightFadeMillis] is the one exception: it is wall-clock milliseconds, because the
 * collapsed anchor's press/hover feedback runs independently of the morph.
 */
@Immutable
data class PopupTiming(
    /** Progress below which the panel is considered fully collapsed and stops being composed. */
    val visibleEpsilon: Float = 0.0001f,
    /** Progress at which the flying selected row hands over to the real list row. */
    val morphCompleteThreshold: Float = 0.999f,
    /** Chevron fade span while expanding, as a fraction of the total progress. */
    val chevronExpandFadeSpan: Float = 0.25f,
    /** Chevron fade span while collapsing, as a fraction of the total progress. */
    val chevronCollapseFadeSpan: Float = 0.25f,
    /** Progress at which the staggered row reveal starts. */
    val staggerBase: Float = 0.15f,
    /** Additional stagger delay added per row, measured from the expansion origin. */
    val staggerStep: Float = 0.06f,
    /** Upper bound for the accumulated stagger delay. */
    val staggerMax: Float = 0.6f,
    /** Minimum progress a border must retain to stay visible while morphing. */
    val borderMinWidth: Float = 0.25f,
    /** Minimum alpha a container color must retain to be painted. */
    val containerMinAlpha: Float = 0.001f,
    /**
     * Progress at which the surface shadow starts ramping in.
     *
     * A shadow at low elevation is a tight dark rim around the surface outline; on a transparent
     * container nothing covers that rim, so it reads as a dark edge rather than depth. Holding the
     * shadow until the panel is partway open lets it spread like a shadow from the start. Set to
     * `0f` to ramp the shadow across the whole morph.
     */
    val shadowStartFraction: Float = 0.85f,
    /**
     * Fade duration of the collapsed anchor's press/hover highlight, in milliseconds.
     *
     * Wall-clock rather than a morph fraction: the anchor's feedback plays before the morph starts
     * (press) and on hover, when no morph is running at all.
     */
    val anchorHighlightFadeMillis: Int = 90,
)

/**
 * The complete set of sizing and spacing values used by a popup.
 *
 * Grouped into a single immutable value so a caller can override the whole metric set at once
 * instead of relying on module-level constants.
 */
@Immutable
data class PopupMetrics(
    /** Visual height of the collapsed surface. */
    val collapsedHeight: Dp = 32.dp,
    /** Minimum touch target height of the collapsed surface. */
    val collapsedTouchTarget: Dp = 48.dp,
    /** Minimum touch target height of an option row. */
    val rowTouchTargetHeight: Dp = 48.dp,
    /** Vertical gap between option rows. */
    val rowVerticalSpacing: Dp = 4.dp,
    /** Vertical padding inside the expanded panel. */
    val panelVerticalPadding: Dp = 8.dp,
    /** Horizontal padding at the trailing end of the collapsed surface. */
    val collapsedHorizontalPadding: Dp = 16.dp,
    /** Horizontal padding on the sides of each option row. */
    val rowHorizontalPadding: Dp = 12.dp,
    /** Leading padding before the chevron inside the collapsed surface. */
    val chevronLeadingPadding: Dp = 8.dp,
    /** Space between the primary text and the optional count slot. */
    val countSpacing: Dp = 12.dp,
    /** Width of the collapsed surface outline border. */
    val borderWidth: Dp = 1.dp,
    /** Size of the chevron icon. */
    val chevronSize: Dp = 18.dp,
    /** Vertical translation offset for the staggered entry animation. */
    val rowEnterTranslation: Dp = 14.dp,
    /** Margin kept between the panel and the window edges. */
    val edgeMargin: Dp = 8.dp,
    /** Maximum panel height as a fraction of the window height. */
    val maxHeightFraction: Float = 0.6f,
    /** Standard disabled opacity factor (Material 3). */
    val disabledAlpha: Float = 0.38f,
    /** Corner radius of popup surfaces. */
    val corner: Dp = 8.dp,
    /** Elevation of the collapsed surface. */
    val collapsedElevation: Dp = 0.dp,
    /** Elevation of the expanded surface. */
    val expandedElevation: Dp = 6.dp,
) {
    companion object {
        /** The metrics used when no override is supplied. */
        val Default: PopupMetrics = PopupMetrics()
    }
}

/**
 * [PopupMetrics] resolved to pixels for one [Density].
 *
 * Render-layer code reads these instead of repeating `with(density) { …toPx() }` conversions;
 * [rememberMetricsPx] builds the instance once per metrics/density pair.
 */
@Immutable
internal class PopupMetricsPx(
    /** Corner radius of popup surfaces. */
    val cornerPx: Float,
    /** Width of the collapsed surface outline border. */
    val borderWidthPx: Float,
    /** Elevation of the collapsed surface. */
    val collapsedElevationPx: Float,
    /** Elevation of the expanded surface. */
    val expandedElevationPx: Float,
    /** Size of the chevron icon. */
    val chevronSizePx: Float,
    /** Leading padding before the chevron inside the collapsed surface. */
    val chevronLeadingPaddingPx: Float,
    /** The chevron plus the gap before it — the trigger's leading block ahead of the content box. */
    val chevronBlockPx: Float,
    /** Minimum touch target height of an option row. */
    val rowHeightPx: Float,
    /** Vertical gap between option rows. */
    val rowSpacingPx: Float,
    /** Vertical padding inside the expanded panel. */
    val panelPaddingPx: Float,
    /** Horizontal padding on the sides of each option row. */
    val rowHorizontalPaddingPx: Float,
    /** Space between the primary text and the optional count slot. */
    val countSpacingPx: Float,
    /** Visual height of the collapsed surface. */
    val collapsedHeightPx: Float,
    /** Horizontal padding at the trailing end of the collapsed surface. */
    val collapsedHorizontalPaddingPx: Float,
    /** Vertical translation offset for the staggered entry animation. */
    val rowEnterTranslationPx: Float,
    /** Margin kept between the panel and the window edges. */
    val edgeMarginPx: Float,
)

/** Resolves every metric to pixels once, for use across a whole render pass. */
internal fun PopupMetrics.toPx(density: Density): PopupMetricsPx = with(density) {
    PopupMetricsPx(
        cornerPx = corner.toPx(),
        borderWidthPx = borderWidth.toPx(),
        collapsedElevationPx = collapsedElevation.toPx(),
        expandedElevationPx = expandedElevation.toPx(),
        chevronSizePx = chevronSize.toPx(),
        chevronLeadingPaddingPx = chevronLeadingPadding.toPx(),
        chevronBlockPx = chevronSize.toPx() + chevronLeadingPadding.toPx(),
        rowHeightPx = rowTouchTargetHeight.toPx(),
        rowSpacingPx = rowVerticalSpacing.toPx(),
        panelPaddingPx = panelVerticalPadding.toPx(),
        rowHorizontalPaddingPx = rowHorizontalPadding.toPx(),
        countSpacingPx = countSpacing.toPx(),
        collapsedHeightPx = collapsedHeight.toPx(),
        collapsedHorizontalPaddingPx = collapsedHorizontalPadding.toPx(),
        rowEnterTranslationPx = rowEnterTranslation.toPx(),
        edgeMarginPx = edgeMargin.toPx(),
    )
}

/** Remembers [metrics] resolved to pixels, rebuilt only when the metrics or density change. */
@Composable
internal fun rememberMetricsPx(metrics: PopupMetrics): PopupMetricsPx {
    val density = LocalDensity.current
    return remember(metrics, density) { metrics.toPx(density) }
}
