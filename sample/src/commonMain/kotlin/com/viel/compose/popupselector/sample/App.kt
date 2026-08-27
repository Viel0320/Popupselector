package com.viel.compose.popupselector.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.viel.compose.popupselector.PopupAlignment
import com.viel.compose.popupselector.PopupDefaults
import com.viel.compose.popupselector.PopupItem
import com.viel.compose.popupselector.PopupOverlayContainer
import com.viel.compose.popupselector.PopupSelector
import com.viel.compose.popupselector.PopupWidth
import com.viel.compose.popupselector.sample.theme.AppTheme
import com.viel.compose.popupselector.sample.theme.ThemeMode
import com.viel.compose.popupselector.textPopupItem
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun App() {
    var themeMode by remember { mutableStateOf(ThemeMode.System) }
    val rootHazeState = remember { HazeState() }

    AppTheme(themeMode = themeMode) {
        PopupOverlayContainer(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Scaffold { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .hazeSource(rootHazeState)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PopupSelector Demo",
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            Text(
                                text = "模式: ${themeMode.displayName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        var themeExpanded by remember { mutableStateOf(false) }
                        val themeItems = remember {
                            ThemeMode.entries.map { mode ->
                                textPopupItem(mode, mode.displayName)
                            }
                        }

                        PopupSelector(
                            items = themeItems,
                            expanded = themeExpanded,
                            selectedIndex = ThemeMode.entries.indexOf(themeMode),
                            onExpandedChange = { themeExpanded = it },
                            onSelect = { index ->
                                themeMode = ThemeMode.entries[index]
                            },
                            panelWidth = PopupWidth.Wrap,
                            colors = PopupDefaults.colors(
                                collapsedContainer = MaterialTheme.colorScheme.background,
                            ),
                        )
                    }

                    // 1. Pure Native / Zero Dependency Demo (Default Window Popup when no blur is needed)
                    SampleSection(title = "1. Pure Native (Zero External Dependencies)") {
                        var expanded by remember { mutableStateOf(false) }
                        var selectedIndex by remember { mutableStateOf<Int?>(0) }
                        val items = remember {
                            listOf(
                                textPopupItem("all", "All Items", count = 128),
                                textPopupItem("favorites", "Favorites", count = 12),
                                textPopupItem("archived", "Archived", count = 3),
                                textPopupItem("trash", "Trash", enabled = false),
                            )
                        }

                        PopupSelector(
                            items = items,
                            expanded = expanded,
                            selectedIndex = selectedIndex,
                            onExpandedChange = { expanded = it },
                            onSelect = { selectedIndex = it },
                            panelWidth = PopupWidth.Wrap,
                            colors = PopupDefaults.colors(
                                collapsedContainer = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ),
                        )
                    }

                    // 2. Frosted Glass with Haze (Auto-routes to In-Window Overlay Host)
                    val parentHazeState = remember { HazeState() }
                    SampleSection(
                        title = "2. Frosted Glass (Haze Blur via PopupDefaults.blurColors)",
                        hasVibrantBackground = true,
                        containerHazeState = parentHazeState,
                    ) {
                        var expanded by remember { mutableStateOf(false) }
                        var selectedIndex by remember { mutableStateOf<Int?>(1) }
                        val items = remember {
                            listOf(
                                PopupItem(
                                    key = "daily",
                                    content = { Text("Daily Digest (7:00 AM)") },
                                    ),
                                PopupItem(
                                    key = "weekly",
                                    content = { Text("Weekly Summary (Monday)") },
                                ),
                                PopupItem(
                                    key = "monthly",
                                    content = { Text("Monthly Report (1st)") },
                                ),
                            )
                        }

                        PopupSelector(
                            items = items,
                            expanded = expanded,
                            selectedIndex = selectedIndex,
                            onExpandedChange = { expanded = it },
                            onSelect = { selectedIndex = it },
                            colors = PopupDefaults.blurColors(),
                            anchorModifier = Modifier.hazeEffect(state = parentHazeState, style = HazeMaterials.ultraThin()),
                            surfaceModifier = Modifier.hazeEffect(state = rootHazeState, style = HazeMaterials.ultraThin()),
                            panelWidth = PopupWidth.Fixed(280.dp),
                            alignment = PopupAlignment.Auto,
                        )
                    }

                    // 3. Match Anchor Width
                    SampleSection(title = "3. Match Anchor Width") {
                        var expanded by remember { mutableStateOf(false) }
                        var selectedIndex by remember { mutableStateOf<Int?>(0) }
                        val items = remember {
                            listOf(
                                textPopupItem("asc", "Sort by Name (Ascending)"),
                                textPopupItem("desc", "Sort by Name (Descending)"),
                                textPopupItem("date", "Sort by Created Date"),
                                textPopupItem("size", "Sort by File Size"),
                            )
                        }

                        PopupSelector(
                            modifier = Modifier.fillMaxWidth(),
                            items = items,
                            expanded = expanded,
                            selectedIndex = selectedIndex,
                            onExpandedChange = { expanded = it },
                            onSelect = { selectedIndex = it },
                            panelWidth = PopupWidth.MatchAnchor,
                            colors = PopupDefaults.colors(
                                collapsedContainer = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SampleSection(
    title: String,
    hasVibrantBackground: Boolean = false,
    containerHazeState: HazeState? = null,
    content: @Composable () -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)

            val gradientColors = if (isDark) {
                listOf(
                    Color(0xFF4A3780).copy(alpha = 0.45f),
                    Color(0xFF241E3A).copy(alpha = 0.75f),
                    Color(0xFF381E2E).copy(alpha = 0.55f),
                )
            } else {
                listOf(
                    Color(0xFF6750A4).copy(alpha = 0.35f),
                    Color(0xFFE8DEF8).copy(alpha = 0.6f),
                    Color(0xFFFFD8E4).copy(alpha = 0.4f),
                )
            }

            if (hasVibrantBackground) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .then(
                                if (containerHazeState != null) {
                                    Modifier.hazeSource(containerHazeState)
                                } else {
                                    Modifier
                                }
                            )
                            .background(
                                brush = Brush.horizontalGradient(gradientColors),
                                shape = RoundedCornerShape(12.dp),
                            )
                    )
                    Box(modifier = Modifier.padding(16.dp)) {
                        content()
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    content()
                }
            }
        }
    }
}
