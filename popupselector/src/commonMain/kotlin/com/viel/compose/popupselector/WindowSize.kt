package com.viel.compose.popupselector

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize

/**
 * The size of the window hosting the popup, in pixels.
 *
 * Geometry is always computed in window coordinates, so the window size must come from the window
 * itself rather than from the constraints of whichever container happens to be composing the panel.
 * Deriving it from local constraints breaks as soon as the overlay host is not full-screen.
 *
 * [WindowInfo.containerSize] is backed by snapshot state, so it must be read directly in the
 * composable body for resize changes to invalidate. Caching it in `remember` freezes the value at
 * the first composition: the read is tracked once, but on invalidation `remember` returns the stale
 * entry without re-running its calculation, and the size never refreshes again.
 */
@Composable
internal fun windowSize(): IntSize {
    val size = LocalWindowInfo.current.containerSize
    return IntSize(size.width.coerceAtLeast(0), size.height.coerceAtLeast(0))
}
