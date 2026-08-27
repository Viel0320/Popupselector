package com.viel.compose.popupselector

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Strategy determining where the expanding popup panel is hosted and rendered.
 */
sealed interface PopupHostPolicy {
    /**
     * Automatically uses [Overlay] if a [LocalPopupOverlayHost] is available in the composition tree,
     * otherwise falls back to [Window]. This provides zero-configuration for both native and frosted-glass usage.
     */
    data object Auto : PopupHostPolicy

    /**
     * Hosts the expanded surface in a system-level [androidx.compose.ui.window.Popup].
     * Zero-configuration and able to escape parent clip bounds, but isolated in a separate window.
     */
    data object Window : PopupHostPolicy

    /**
     * Hosts the expanded surface in an in-window [PopupOverlayHost].
     * Shares the same window and drawing tree with underlying content, enabling seamless
     * background sampling for frosted glass blur effects (e.g. Haze).
     *
     * @param hostState Optional explicit host state. If null, [LocalPopupOverlayHost] will be used.
     */
    data class Overlay(val hostState: PopupOverlayHostState? = null) : PopupHostPolicy
}

/**
 * State holder for [PopupOverlayHost], managing active in-window popup overlay content.
 */
@Stable
class PopupOverlayHostState {
    internal var currentContent by mutableStateOf<(@Composable () -> Unit)?>(null)
        private set

    /** Updates or clears the currently active overlay content. */
    internal fun setContent(content: (@Composable () -> Unit)?) {
        currentContent = content
    }
}

/** Creates and remembers a [PopupOverlayHostState]. */
@Composable
fun rememberPopupOverlayHostState(): PopupOverlayHostState = remember { PopupOverlayHostState() }

/** Ambient composition local providing the nearest [PopupOverlayHostState]. */
val LocalPopupOverlayHost: ProvidableCompositionLocal<PopupOverlayHostState?> = compositionLocalOf { null }

/**
 * Container placed at the root or top of a layout to host in-window popup overlays.
 *
 * @param modifier Modifier applied to the host container.
 * @param state Host state to observe. Defaults to [LocalPopupOverlayHost.current] if present.
 */
@Composable
fun PopupOverlayHost(
    modifier: Modifier = Modifier,
    state: PopupOverlayHostState? = LocalPopupOverlayHost.current,
) {
    val hostState = state ?: return
    val content = hostState.currentContent
    if (content != null) {
        Box(
            modifier = modifier.fillMaxSize(),
            propagateMinConstraints = false,
        ) {
            content()
        }
    }
}

/**
 * Convenience root container that automatically creates and provides a [PopupOverlayHostState],
 * and places a [PopupOverlayHost] at the top layer.
 *
 * @param modifier Modifier applied to the root container.
 * @param state Host state to manage overlays. Defaults to a remembered state.
 * @param content The underlying application or screen layout content.
 */
@Composable
fun PopupOverlayContainer(
    modifier: Modifier = Modifier,
    state: PopupOverlayHostState = rememberPopupOverlayHostState(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalPopupOverlayHost provides state) {
        Box(modifier = modifier, propagateMinConstraints = true) {
            content()
            PopupOverlayHost(state = state)
        }
    }
}
