package com.viel.compose.popupselector

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider

/**
 * Single source of truth for the collapse <-> expand morph animation.
 *
 * The animation drives one scalar progress in `[0, 1]` that every visual layer reads from, so the
 * surface, the option rows and the flying selected row can never drift out of sync.
 *
 * The state owns no timing or spec of its own: both are supplied per [animateTo] call from
 * [PopupStyle], so the style is the only place the defaults are defined.
 */
@Stable
internal class PopupMorphState(initial: Float) {
    val fraction: Animatable<Float, *> = Animatable(initial)

    /** Read callback for use inside `graphicsLayer` and `layout` lambdas. */
    fun provider(): () -> Float = { fraction.value }

    private var _isVisible by mutableStateOf(initial > 0f)
    val isVisible: Boolean
        get() = _isVisible

    suspend fun animateTo(
        target: Float,
        animationSpec: AnimationSpec<Float>,
        timing: PopupTiming,
    ) {
        if (target > timing.visibleEpsilon) {
            _isVisible = true
        }
        try {
            fraction.animateTo(targetValue = target, animationSpec = animationSpec)
        } finally {
            if (target <= timing.visibleEpsilon) {
                _isVisible = false
            }
        }
    }
}

/** Creates a morph state seeded from the initial expansion flag. */
@Composable
internal fun rememberMorphState(expanded: Boolean): PopupMorphState =
    remember { PopupMorphState(if (expanded) 1f else 0f) }

/**
 * Positions the popup at the window's top-left with zero size constraints, so window coordinates
 * map directly onto popup coordinates and the surface can be placed by hand.
 */
internal object TopLeftPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(0, 0)
}
