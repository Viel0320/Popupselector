package com.viel.compose.popupselector.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.viel.compose.popupselector.LocalPopupAlignment
import com.viel.compose.popupselector.PopupDefaults
import com.viel.compose.popupselector.PopupHorizontalAlignment
import com.viel.compose.popupselector.PopupItem
import com.viel.compose.popupselector.PopupLayout
import com.viel.compose.popupselector.PopupMetrics
import com.viel.compose.popupselector.PopupOverlayContainer
import com.viel.compose.popupselector.PopupSelector
import com.viel.compose.popupselector.PopupVerticalAlignment
import com.viel.compose.popupselector.PopupWidth
import com.viel.compose.popupselector.sample.theme.AppTheme
import com.viel.compose.popupselector.sample.theme.ThemeMode
import com.viel.compose.popupselector.textPopupItem
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/** Which item set the demo target shows. */
private enum class ItemsKind { Basic, Counts, LongLabels, Disabled, Export }

/** Persistent keeps a selection; Menu runs with `selectedIndex = null` (no current item). */
private enum class SelectionKind { Persistent, Menu }

/** Which engine (if any) renders the frosted-glass surfaces of the demo target. */
private enum class SurfaceKind { Plain, Haze, Miuix }

private fun itemsFor(kind: ItemsKind): List<PopupItem> = when (kind) {
    ItemsKind.Basic -> listOf(
        textPopupItem("a", "Alpha"),
        textPopupItem("b", "Beta"),
        textPopupItem("c", "Gamma"),
    )
    ItemsKind.Counts -> listOf(
        textPopupItem("all", "All items", count = 128),
        textPopupItem("fav", "Favorites", count = 12),
        textPopupItem("new", "New today", count = 5),
    )
    ItemsKind.LongLabels -> listOf(
        textPopupItem(
            "long",
            "A very long label that overflows its row and marquees across",
            count = 1,
        ),
        textPopupItem("short", "Short", count = 2),
    )
    ItemsKind.Disabled -> listOf(
        textPopupItem("a", "Alpha", count = 1),
        textPopupItem("b", "Beta (disabled)", count = 2, enabled = false),
        textPopupItem("c", "Gamma", count = 3),
    )
    ItemsKind.Export -> listOf(
        textPopupItem("jpg", "Export as JPG"),
        textPopupItem("png", "Export as PNG"),
        textPopupItem("webp", "Export as WebP"),
    )
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun App() {
    var themeMode by remember { mutableStateOf(ThemeMode.System) }
    val rootHazeState = remember { HazeState() }
    val rootBackdrop = rememberLayerBackdrop()

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
                        .hazeSource(rootHazeState)
                        .layerBackdrop(rootBackdrop)
                        .verticalScroll(rememberScrollState())
                        .padding(
                            top = innerPadding.calculateTopPadding(),
                            start = 24.dp,
                            end = 24.dp,
                            bottom = innerPadding.calculateBottomPadding()
                        ),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Text(
                        text = "PopupSelector Demo",
                        style = MaterialTheme.typography.headlineMedium,
                    )

                    Playground(
                        rootHazeState = rootHazeState,
                        rootBackdrop = rootBackdrop,
                        themeMode = themeMode,
                        onThemeModeChange = { themeMode = it },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun Playground(
    rootHazeState: HazeState,
    rootBackdrop: LayerBackdrop,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    var width by remember { mutableStateOf<PopupWidth>(PopupWidth.Wrap) }
    var vertical by remember { mutableStateOf(PopupVerticalAlignment.Auto) }
    var horizontal by remember { mutableStateOf(PopupHorizontalAlignment.Auto) }
    var itemsKind by remember { mutableStateOf(ItemsKind.Basic) }
    var selection by remember { mutableStateOf(SelectionKind.Persistent) }
    var ambient by remember { mutableStateOf(Alignment.TopStart) }
    var rtl by remember { mutableStateOf(false) }
    var surface by remember { mutableStateOf(SurfaceKind.Plain) }

    val parentHazeState = remember { HazeState() }
    val parentBackdrop = rememberLayerBackdrop()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
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

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

        // The single demo target every option above drives. The blur sources (haze and layer
        // backdrop) are always active; the Surface branch only chooses which modifier, if any,
        // samples them.
        val popupShape = RoundedCornerShape(PopupMetrics.Default.corner)
        val anchorModifier = when (surface) {
            SurfaceKind.Plain -> Modifier
            SurfaceKind.Haze -> Modifier.hazeEffect(state = parentHazeState, style = HazeMaterials.ultraThin())
            SurfaceKind.Miuix -> Modifier.drawBackdrop(
                backdrop = parentBackdrop,
                shape = { popupShape },
                effects = { blur(50f * density) },
            )
        }
        val surfaceModifier = when (surface) {
            SurfaceKind.Plain -> Modifier
            SurfaceKind.Haze -> Modifier.hazeEffect(state = rootHazeState, style = HazeMaterials.ultraThin())
            SurfaceKind.Miuix -> Modifier.drawBackdrop(
                backdrop = rootBackdrop,
                shape = { popupShape },
                effects = { blur(50f * density) },
            )
        }
        val style = PopupDefaults.style(
            colors = if (surface != SurfaceKind.Plain) PopupDefaults.blurColors() else PopupDefaults.colors(),
        )

        CompositionLocalProvider(
            LocalPopupAlignment provides ambient,
            LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        ) {
            key(width, vertical, horizontal, itemsKind, ambient, rtl, surface, isDark) {
                // A stable instance per kind: an inline itemsFor() call would allocate a new list on
                // every recomposition and wipe the library's section-width measurement cache on each
                // click, making the count column visibly jump to the concatenated layout and back.
                val items = remember(itemsKind) { itemsFor(itemsKind) }
                var expanded by remember { mutableStateOf(false) }
                var selectedIndex by remember { mutableStateOf<Int?>(0) }
                val targetModifier = Modifier
                val menuMode = selection == SelectionKind.Menu
                // Keyed to the item set: switching Items invalidates a recorded action from the previous set.
                var lastAction by remember(items) { mutableStateOf<String?>(null) }
                val selector: @Composable (Modifier) -> Unit = { modifier ->
                    PopupSelector(
                        items = items,
                        expanded = expanded,
                        selectedIndex = if (menuMode) null else selectedIndex,
                        onExpandedChange = { expanded = it },
                        onSelect = { index ->
                            if (menuMode) {
                                lastAction = items[index].key as String
                            } else {
                                selectedIndex = index
                            }
                        },
                        collapsedContent = if (menuMode) {
                            {
                                Text("Export")
                            }
                        } else {
                            null
                        },
                        modifier = modifier,
                        style = style,
                        layout = PopupLayout(
                            width = width,
                            verticalAlignment = vertical,
                            horizontalAlignment = horizontal,
                        ),
                        anchorModifier = anchorModifier,
                        surfaceModifier = surfaceModifier,
                    )
                }

                // The gradient backdrop is always on: the Surface branch only swaps the target's
                // material (blur sampling vs plain colours), never the background itself.
                Box(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .hazeSource(parentHazeState)
                            .layerBackdrop(parentBackdrop)
                            .background(
                                brush = Brush.horizontalGradient(gradientColors),
                                shape = RoundedCornerShape(12.dp),
                            )
                    )
                    // The chip sits where the ambient alignment says, so the Ambient row
                    // visibly moves it around the cell — and the Inherit panel opens from
                    // that same corner.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(60.dp),
                        contentAlignment = ambient,
                    ) {
                        selector(targetModifier)
                    }
                }
            }
        }

        OptionRow(
            title = "Theme",
            options = ThemeMode.entries.map { it.displayName to it },
            selected = themeMode,
            onSelect = onThemeModeChange,
        )
        OptionRow(
            title = "Width",
            options = listOf(
                "Wrap" to PopupWidth.Wrap,
                "Fixed 200dp" to PopupWidth.Fixed(200.dp),
                "MatchAnchor" to PopupWidth.MatchAnchor,
            ),
            selected = width,
            onSelect = { width = it },
        )
        OptionRow(
            title = "Vertical",
            options = listOf(
                "Auto (centers on selection)" to PopupVerticalAlignment.Auto,
                "Top" to PopupVerticalAlignment.Top,
                "Bottom" to PopupVerticalAlignment.Bottom,
                "Inherit" to PopupVerticalAlignment.Inherit,
            ),
            selected = vertical,
            onSelect = { vertical = it },
        )
        OptionRow(
            title = "Horizontal",
            options = listOf(
                "Auto (leads when both fit)" to PopupHorizontalAlignment.Auto,
                "Start" to PopupHorizontalAlignment.Start,
                "End" to PopupHorizontalAlignment.End,
                "Inherit" to PopupHorizontalAlignment.Inherit,
            ),
            selected = horizontal,
            onSelect = { horizontal = it },
        )
        OptionRow(
            title = "Items",
            options = listOf(
                "Basic" to ItemsKind.Basic,
                "Counts" to ItemsKind.Counts,
                "Long labels" to ItemsKind.LongLabels,
                "Disabled" to ItemsKind.Disabled,
                "Export" to ItemsKind.Export,
            ),
            selected = itemsKind,
            onSelect = { itemsKind = it },
        )
        OptionRow(
            title = "Selection — with none, Auto compares space instead of centring the row",
            options = listOf(
                "Persistent" to SelectionKind.Persistent,
                "Menu (none)" to SelectionKind.Menu,
            ),
            selected = selection,
            onSelect = { selection = it },
        )
        OptionRow(
            title = "Ambient — moves the chip in this cell; Inherit opens the panel from that corner",
            options = listOf(
                "TopStart" to Alignment.TopStart,
                "Center" to Alignment.Center,
                "BottomEnd" to Alignment.BottomEnd,
            ),
            selected = ambient,
            onSelect = { ambient = it },
        )
        OptionRow(
            title = "Direction",
            options = listOf("LTR" to false, "RTL" to true),
            selected = rtl,
            onSelect = { rtl = it },
        )
        OptionRow(
            title = "Surface",
            options = listOf(
                "Plain" to SurfaceKind.Plain,
                "Haze" to SurfaceKind.Haze,
                "miuix-blur" to SurfaceKind.Miuix,
            ),
            selected = surface,
            onSelect = { surface = it },
        )
    }
}

/** A titled row of single-choice chips that drives one branch of the demo target. */
@Composable
private fun <T> OptionRow(
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (label, value) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(label) },
                )
            }
        }
    }
}
