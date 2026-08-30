package com.viel.compose.popupselector

import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * Styling values captured at the [PopupSelector] call site and re-established around the expanded
 * panel.
 *
 * The expanded panel is composed at whichever host renders it — an in-window overlay usually sits
 * near the layout root, and a system popup opens its own composition. Either way, composables
 * inside the panel would otherwise resolve locals against the *host's* position in the tree rather
 * than the caller's, so a caller-provided content color, text style, text direction or ambient
 * alignment would silently change the moment the panel opened.
 *
 * [LocalLayoutDirection] belongs here despite being an environment local: it does not change the
 * coordinate space (window coordinates are window coordinates in either direction) but it does
 * govern how the panel's rows and sections are arranged. [LocalDensity] and window info are still
 * deliberately left alone — those do affect measured geometry, which must stay in the host's
 * coordinate space.
 */
@Immutable
internal data class PopupContentContext(
    val contentColor: Color,
    val textStyle: TextStyle,
    val absoluteTonalElevation: Dp,
    val layoutDirection: LayoutDirection,
    val popupAlignment: Alignment?,
) {
    /** Restores the captured values around [content]. */
    @Composable
    fun Provide(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalContentColor provides contentColor,
            LocalTextStyle provides textStyle,
            LocalAbsoluteTonalElevation provides absoluteTonalElevation,
            LocalLayoutDirection provides layoutDirection,
            LocalPopupAlignment provides popupAlignment,
            content = content,
        )
    }

    companion object {
        /** Captures the current values. Must be called at the [PopupSelector] call site. */
        @Composable
        fun capture(): PopupContentContext = PopupContentContext(
            contentColor = LocalContentColor.current,
            textStyle = LocalTextStyle.current,
            absoluteTonalElevation = LocalAbsoluteTonalElevation.current,
            layoutDirection = LocalLayoutDirection.current,
            popupAlignment = LocalPopupAlignment.current,
        )
    }
}
