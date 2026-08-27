package com.viel.compose.popupselector.sample.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

enum class ThemeMode(val displayName: String) {
    System("跟随系统"),
    Light("浅色模式"),
    Dark("深色模式");

    @Composable
    @ReadOnlyComposable
    fun isDark(): Boolean = when (this) {
        System -> isSystemInDarkTheme()
        Light -> false
        Dark -> true
    }
}
