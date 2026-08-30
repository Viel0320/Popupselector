package com.viel.compose.popupselector

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier

/**
 * Where the expanded popup panel is hosted and rendered.
 *
 * - [Overlay] *(default)* draws inside the current window. It shares the drawing tree with the
 *   underlying content, which is what makes background sampling possible.
 * - [Window] uses a system-level popup. It escapes any parent clipping with no setup required,
 *   but lives in a separate window and therefore cannot sample the pixels behind it. Effects that
 *   need background sampling (such as blur) will not work in this mode.
 *
 * [Overlay] with a null [hostState] and no ambient [LocalPopupOverlayHost] falls back to [Window],
 * so the default `PopupHostPolicy.Overlay()` works with zero configuration.
 */
sealed interface PopupHostPolicy {
    /**
     * Hosts the expanded surface in an in-window [PopupOverlayHost].
     *
     * @param hostState Explicit host state. When null, [LocalPopupOverlayHost] is used; when that
     *  is also absent, the panel falls back to [Window].
     */
    data class Overlay(val hostState: PopupOverlayHostState? = null) : PopupHostPolicy

    /** Hosts the expanded surface in a system-level popup window. */
    data object Window : PopupHostPolicy
}

/**
 * State holder for [PopupOverlayHost], managing any number of concurrently active overlays.
 *
 * Each overlay registers under its own owner key, so multiple popups can be expanded at the same
 * time without overwriting one another. Overlays are drawn in registration order.
 */
@Stable
class PopupOverlayHostState {
    internal data class Entry(
        val owner: Any,
        val content: @Composable () -> Unit,
    )

    internal val entries: SnapshotStateList<Entry> = mutableStateListOf()

    /**
     * Registers or replaces the content for [owner].
     *
     * Passing null content removes the entry.
     */
    internal fun setContent(owner: Any, content: (@Composable () -> Unit)?) {
        if (content == null) {
            entries.removeAll { it.owner == owner }
            return
        }
        val index = entries.indexOfFirst { it.owner == owner }
        val entry = Entry(owner = owner, content = content)
        if (index >= 0) {
            entries[index] = entry
        } else {
            entries.add(entry)
        }
    }

    /** Removes the entry owned by [owner], if any. */
    internal fun clearContent(owner: Any) {
        entries.removeAll { it.owner == owner }
    }
}

/** Creates and remembers a [PopupOverlayHostState]. */
@Composable
fun rememberPopupOverlayHostState(): PopupOverlayHostState = remember { PopupOverlayHostState() }

/** Ambient composition local providing the nearest [PopupOverlayHostState]. */
val LocalPopupOverlayHost: ProvidableCompositionLocal<PopupOverlayHostState?> = compositionLocalOf { null }

/**
 * Container placed at the top of a layout to host in-window popup overlays.
 *
 * Every registered overlay is composed as a sibling, so a popup can visually escape the bounds of
 * its anchor's parent while still sharing the window's drawing tree.
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
    val entries = hostState.entries
    if (entries.isEmpty()) return

    Box(
        modifier = modifier.fillMaxSize(),
        propagateMinConstraints = false,
    ) {
        // Snapshot the list so a mutation during composition cannot invalidate the iteration.
        entries.toList().forEach { entry ->
            Box(modifier = Modifier.fillMaxSize()) {
                entry.content()
            }
        }
    }
}

/**
 * Convenience root container that creates and provides a [PopupOverlayHostState], placing a
 * [PopupOverlayHost] above [content].
 *
 * Wrap your screen in this to enable in-window hosting for every [PopupSelector] inside it.
 *
 * The container must sit at the window's root and fill the window (as it does by default):
 * panel geometry is resolved in window coordinates, so hosting overlays inside a padded or
 * otherwise offset container would shift every panel — and its full-screen dismiss scrim —
 * by the container's own offset relative to the window origin.
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
