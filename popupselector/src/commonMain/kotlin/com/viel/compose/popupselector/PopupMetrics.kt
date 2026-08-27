package com.viel.compose.popupselector

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
/** Single source of truth for the collapse <-> expand morph animation. */
@Stable
internal class PopupMorphState(
    initial: Float,
) {
    val fraction: Animatable<Float, *> = Animatable(initial)

    fun provider(): () -> Float = { fraction.value }

    private val _isVisible = mutableStateOf(initial > 0.0001f)
    val isVisible: Boolean
        get() = _isVisible.value

    suspend fun animateTo(
        target: Float,
        animationSpec: androidx.compose.animation.core.AnimationSpec<Float> = spring(
            dampingRatio = SPRING_DAMPING,
            stiffness = Spring.StiffnessMediumLow,
        ),
    ) {
        if (target > 0.0001f) {
            _isVisible.value = true
        }
        fraction.animateTo(
            targetValue = target,
            animationSpec = animationSpec,
        )
        if (target <= 0.0001f) {
            _isVisible.value = false
        }
    }

    private companion object {
        const val SPRING_DAMPING = 0.9f
    }
}

/** Always positions the popup at the window's top-left so window coords map directly to popup coords. */
internal object TopLeftPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(0, 0)
}

/** Measures the chevron icon plus spacing block used by collapsed and flying rows. */
internal fun Density.chevronBlockWidth(): Float =
    (PopupChevronSize + PopupSpacingBase).toPx()

/** Calculates total panel height for a fixed number of rows. */
internal fun Density.calculatePanelHeight(rowCount: Int): Float {
    val rowHeightPx = PopupRowTouchTargetHeight.toPx()
    val rowSpacingPx = PopupRowVerticalSpacing.toPx()
    return PopupPanelVerticalPadding.toPx() * 2f +
        rowCount * rowHeightPx +
        (rowCount - 1).coerceAtLeast(0) * rowSpacingPx
}

/** The compact visual height used by the collapsed selector surface. */
internal val PopupUnifiedHeight = 32.dp

/**
 * Keeps the popup row's semantic and pointer target at the accessibility minimum
 * while the row content retains its compact visual height and alignment.
 */
internal val PopupRowTouchTargetHeight = 48.dp

/** Vertical gap between items in the Popup. */
internal val PopupRowVerticalSpacing = 4.dp

/** Default spacing metric used for margins, padding, and layout offsets. */
internal val PopupSpacingBase = 8.dp

/** Vertical padding inside the expanded panel. */
internal val PopupPanelVerticalPadding = PopupSpacingBase

/** Horizontal padding at the trailing end of the collapsed button. */
internal val PopupCollapsedHorizontalPadding = 16.dp

/** Horizontal padding on the sides of each item row in the expanded panel. */
internal val PopupRowHorizontalPadding = 20.dp

/** Minimum touch target height for the collapsed button. */
internal val PopupCollapsedTouchTarget = 48.dp

/** The width of the collapsed button's outline border. */
internal val PopupBorderWidth = 1.dp

/** Size of the Popup chevron arrow icon. */
internal val PopupChevronSize = 18.dp

/** Space between the primary text and the optional count text inside item rows. */
internal val PopupCountSpacing = 12.dp

/** Vertical translation offset for the staggered entry animation of list options. */
internal val PopupRowEnterTranslation = 14.dp

/** Maximum height of the Popup panel relative to the window height. */
internal const val PopupMaxHeightFraction = 0.6f

/** Start fraction of the Popup stagger reveal animation. */
internal const val PopupStaggerBase = 0.15f

/** The animation stagger step duration added per row. */
internal const val PopupStaggerStep = 0.06f

/** Upper ceiling limit for the stagger animation delay fraction. */
internal const val PopupStaggerMax = 0.6f

/** Standard disabled opacity factor (Material 3 standard). */
internal const val PopupDisabledAlpha = 0.38f
