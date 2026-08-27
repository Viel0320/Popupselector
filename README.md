# PopupSelector

[![JitPack](https://jitpack.io/v/Viel0320/Popupselector.svg)](https://jitpack.io/#Viel0320/Popupselector)
[![License](<https://img.shields.io/badge/License-Apache%202.0-blue.svg>)](https://opensource.org/licenses/Apache-2.0)
[![Platform](<https://img.shields.io/badge/Platform-Android%20%7C%20Desktop%20%7C%20iOS-green.svg>)](https://github.com/Viel0320/Popupselector)

A sleek, fluid morphing popup selector component for **Compose Multiplatform** (Android, JVM/Desktop, iOS).

---

## ✨ Features

- 🪶 **Zero External Dependencies**: Built exclusively on Compose Foundation and Material 3.
- 🪄 **Fluid Morphing Animation**: Seamless continuous transition between the collapsed button and the expanded floating surface.
- 🛫 **Flying Selected Item**: The selected item visibly flies from the trigger button into its position inside the expanded list.
- 🧭 **Smart Direction Resolution**: Automatically picks the best expansion corner (`TopLeft`, `TopRight`, `BottomLeft`, `BottomRight`) based on available screen space and window edges.
- 🎨 **Deep Customization**: Fully customizable colors, shapes, heights, widths, and item slots.
- 🧩 **Pluggable Visual Effects**: Attach frosted-glass blur (Haze / Blur) or custom backgrounds via `anchorModifier` and `surfaceModifier`.

---

## 📦 Installation

### Step 1: Add JitPack repository

In your root `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

### Step 2: Add dependency

In your module's `build.gradle.kts` (e.g., `commonMain`):

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.github.Viel0320.Popupselector:popupselector:1.0.0")
        }
    }
}
```

---

## 🚀 Usage Guide

### 1. Basic Text Selector with Count Badges

```kotlin
import androidx.compose.runtime.*
import com.viel.compose.popupselector.*

@Composable
fun FilterSelector() {
    var expanded by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableStateOf<Int?>(0) }

    val items = remember {
        listOf(
            textPopupItem(key = "all", label = "All Items", count = 128),
            textPopupItem(key = "favorites", label = "Favorites", count = 12),
            textPopupItem(key = "archived", label = "Archived", count = 3),
            textPopupItem(key = "trash", label = "Trash", enabled = false),
        )
    }

    PopupSelector(
        items = items,
        expanded = expanded,
        selectedIndex = selectedIndex,
        onExpandedChange = { expanded = it },
        onSelect = { selectedIndex = it },
        panelWidth = PopupWidth.Wrap,
    )
}
```

---

### 2. Custom Item Layout

You can provide fully customized item composables for each option:

```kotlin
val items = remember {
    listOf(
        PopupItem(
            key = "daily",
            content = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Today, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Daily Digest")
                }
            },
            count = { Text("7:00 AM", style = MaterialTheme.typography.bodySmall) }
        ),
        PopupItem(
            key = "weekly",
            content = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DateRange, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Weekly Summary")
                }
            },
        )
    )
}

PopupSelector(
    items = items,
    expanded = expanded,
    selectedIndex = selectedIndex,
    onExpandedChange = { expanded = it },
    onSelect = { selectedIndex = it },
    panelWidth = PopupWidth.Fixed(280.dp),
)
```

---

### 3. Width Strategies (`PopupWidth`)

`PopupSelector` supports 3 width calculation strategies:

| Strategy                    | Description                                                                              |
| :-------------------------- | :--------------------------------------------------------------------------------------- |
| `PopupWidth.MatchAnchor`  | *(Default)* Expands to the exact measured width of the collapsed trigger button.       |
| `PopupWidth.Wrap`         | Fits the natural intrinsic width of the widest row item (constrained by screen margins). |
| `PopupWidth.Fixed(width)` | Uses an explicit fixed width (e.g. `PopupWidth.Fixed(260.dp)`).                         |

---

### 4. Expansion Origin (`PopupAlignment`)

Controls the corner from which the panel expands:

- `PopupAlignment.Auto`: *(Default)* Dynamically inspects available screen space and chooses the best corner.
- `PopupAlignment.TopStart` / `PopupAlignment.TopEnd`: Anchors to top edges (layout-direction aware).
- `PopupAlignment.BottomStart` / `PopupAlignment.BottomEnd`: Anchors to bottom edges (layout-direction aware).
- `PopupAlignment.TopLeft` / `PopupAlignment.TopRight`: Anchors to top edges (absolute).
- `PopupAlignment.BottomLeft` / `PopupAlignment.BottomRight`: Anchors to bottom edges (absolute).

---

### 5. Custom Collapsed Trigger Button (Menu Mode)

You can customize the content inside the collapsed button (ideal for dropdown action menus):

```kotlin
PopupSelector(
    items = menuItems,
    expanded = expanded,
    onExpandedChange = { expanded = it },
    onSelect = { index -> handleMenuAction(index) },
    collapsedContent = {
        Text("Actions Menu", fontWeight = FontWeight.Bold)
    }
)
```

---

### 6. Frosted Glass / Background Blur (Optional)

To use real-time blur libraries like [Haze](https://github.com/chrisbanes/haze), wrap your root layout in `PopupOverlayContainer` (or `PopupOverlayHost`) so the panel shares the same window and drawing tree with `hazeSource` (isolated native `Popup` windows cannot sample parent pixels). Combine with `PopupDefaults.blurColors()` for preconfigured transparent surfaces.

> [!IMPORTANT]
> **Dual-State Background Sampling Setup (Avoiding Color Flickering / Flashing)**
>
> During morph animations, the selector smoothly transitions from the collapsed trigger to the expanded panel:
>
> - **Collapsed State**: Provide a background sampling source (`hazeSource`) beneath the anchor button and pass `anchorModifier = Modifier.hazeEffect(...)`. Note that the button must be layered *on top of* the background layer (not as a child inside the `hazeSource` composable) so Haze can sample behind it.
> - **Expanded State**: Provide a broader page/screen-level background sampling source (`hazeSource`) so the floating panel can sample correctly even when expanding outside local cards, using `surfaceModifier = Modifier.hazeEffect(...)`.
>
> Setting up sampling sources for both layers ensures seamless color and blur interpolation during the morph transition without visual glitches or color flashing.

```kotlin
val rootHazeState = remember { HazeState() }
val parentHazeState = remember { HazeState() }

// Wrap your root/screen in PopupOverlayContainer to enable seamless in-window blur sampling
PopupOverlayContainer(Modifier.fillMaxSize()) {
    // 1. Root layer registered as hazeSource (for expanded panel & full-screen blur)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(rootHazeState)
            .padding(16.dp)
    ) {
        // 2. Direct parent container with background registered as hazeSource (for collapsed anchor button)
        Box(
            modifier = Modifier
                .background(Color.Blue.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .hazeSource(parentHazeState)
                .padding(16.dp)
        ) {
            PopupSelector(
                items = items,
                expanded = expanded,
                selectedIndex = selectedIndex,
                onExpandedChange = { expanded = it },
                onSelect = { selectedIndex = it },
                colors = PopupDefaults.blurColors(),
                anchorModifier = Modifier.hazeEffect(state = parentHazeState, style = HazeMaterials.ultraThin()),
                surfaceModifier = Modifier.hazeEffect(state = rootHazeState, style = HazeMaterials.ultraThin()),
            )
        }
    }
}
```

---

## 🛠️ API Reference

### `PopupSelector` Parameters

| Parameter                            | Type                                   | Default                               | Description                                                                                               |
| :----------------------------------- | :------------------------------------- | :------------------------------------ | :-------------------------------------------------------------------------------------------------------- |
| `items`                              | `List<PopupItem>`                      | *(Required)*                          | List of options to display in the selector.                                                               |
| `expanded`                           | `Boolean`                              | *(Required)*                          | Whether the floating panel is currently expanded.                                                         |
| `onExpandedChange`                   | `(Boolean) -> Unit`                    | *(Required)*                          | Callback when the expansion state changes.                                                                |
| `onSelect`                           | `(index: Int) -> Unit`                 | *(Required)*                          | Callback invoked when an item is selected.                                                                |
| `modifier`                           | `Modifier`                             | `Modifier`                            | Modifier applied to the outer layout container.                                                           |
| `selectedIndex`                      | `Int?`                                 | `null`                                | Index of the currently selected option.                                                                   |
| `collapsedContent`                   | `(@Composable RowScope.() -> Unit)?`   | `null`                                | Custom composable slot for the collapsed trigger button.                                                  |
| `alignment`                          | `PopupAlignment`                       | `PopupAlignment.Auto`                 | Preferred origin corner for panel expansion.                                                              |
| `panelWidth`                         | `PopupWidth`                           | `PopupWidth.MatchAnchor`              | Panel width sizing strategy.                                                                              |
| `panelMaxHeight`                     | `Dp`                                   | `Dp.Unspecified`                      | Maximum height constraint for the expanded panel list.                                                    |
| `hostPolicy`                         | `PopupHostPolicy`                      | `PopupDefaults.hostPolicy()`          | Strategy for hosting the expanded surface (`Auto`, `Window`, or `Overlay`).                               |
| `autoMarquee`                        | `Boolean`                              | `PopupDefaults.autoMarquee()` (`true`)| Whether option rows overflowing available container width automatically use smooth marquee scrolling.     |
| `anchorModifier`                     | `Modifier`                             | `Modifier`                            | Modifier applied to the resting collapsed button surface.                                                 |
| `surfaceModifier`                    | `Modifier`                             | `Modifier`                            | Modifier applied to the expanding floating surface.                                                       |
| `textStyle`                          | `TextStyle?`                           | `null`                                | Optional typography style for selector labels (defaults to ambient `LocalTextStyle`).                     |
| `colors`                             | `PopupColors`                          | `PopupDefaults.colors()`              | Color palette for collapsed/expanded containers, border and text.                                         |
| `shapes`                             | `PopupShapes`                          | `PopupDefaults.shapes()`              | Corner radius for popup surfaces.                                                                         |
| `collapsedHeight`                    | `Dp`                                   | `32.dp`                               | Height of the collapsed button surface.                                                                   |
| `animationSpec`                      | `AnimationSpec<Float>`                 | `PopupDefaults.animationSpec()`       | Main morph and reveal animation spring specification.                                                     |
| `sizeAnimationSpec`                  | `FiniteAnimationSpec<IntSize>`         | `PopupDefaults.sizeAnimationSpec()`   | Size change animation specification for the collapsed anchor button.                                      |
| `expandedAccessibilityDescription`   | `String`                               | `"Expanded"`                          | Accessibility text for expanded state.                                                                    |
| `collapsedAccessibilityDescription`  | `String`                               | `"Collapsed"`                         | Accessibility text for collapsed state.                                                                   |

---

## 📄 License

```
Copyright 2026 Viel

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
