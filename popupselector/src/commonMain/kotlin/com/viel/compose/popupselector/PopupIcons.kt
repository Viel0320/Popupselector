package com.viel.compose.popupselector

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icons used internally by the popup.
 *
 * Kept as eagerly built immutable vectors. ImageVector is thread-safe once built, so no lazy
 * caching is needed; a lazy cache would only introduce a race without measurable benefit.
 */
@Immutable
internal object PopupIcons {
    val ChevronDown: ImageVector = ImageVector.Builder(
        name = "ChevronDown",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(7.41f, 8.59f)
            lineTo(12f, 13.17f)
            lineTo(16.59f, 8.59f)
            lineTo(18f, 10f)
            lineToRelative(-6f, 6f)
            lineToRelative(-6f, -6f)
            lineToRelative(1.41f, -1.41f)
            close()
        }
    }.build()
}
