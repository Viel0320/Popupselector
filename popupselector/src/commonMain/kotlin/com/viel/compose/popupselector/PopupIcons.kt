package com.viel.compose.popupselector

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object PopupIcons {
    private var _chevronDown: ImageVector? = null

    val ChevronDown: ImageVector
        get() {
            if (_chevronDown != null) return _chevronDown!!
            _chevronDown = ImageVector.Builder(
                name = "ChevronDown",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    fill = SolidColor(Color.Black),
                ) {
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
            return _chevronDown!!
        }
}
