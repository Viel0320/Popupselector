# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.1.0] - 2026-09-13

### Added

- `PopupColors.activeContent` — the selected row's content colour in the expanded panel, the
  selected counterpart of the `content` role that every other row uses. Applied through
  `LocalContentColor`, so it covers the whole row content (label and count slot); the highlight
  pill is unchanged. `PopupDefaults.colors()` and `blurColors()` resolve the default to
  `MaterialTheme.colorScheme.primary`; palettes constructed directly keep the previous behaviour
  (the row falls back to `content`) until they set the role. The colour rides the morph itself:
  the flying selected row's label blends from the resting trigger's `content` colour at the start
  of the animation to `activeContent` when the panel settles, so the label never changes colour in
  a single frame at either morph handover.

## [2.0.2] - 2026-08-30

### Added

- Publications are now also pushed to GitHub Packages (`maven.pkg.github.com/Viel0320/Popupselector`)
  alongside Maven Central.

### Changed

- Move the Maven coordinates to `io.github.viel0320:popupselector`. The Maven Central Portal only
  accepts GitHub-verified namespaces of the form `io.github.<username>`; the previous
  `com.github.Viel0320.Popupselector` group was rejected at component validation and was never
  published, so no migration is needed.

## [2.0.1] - 2026-08-30

### Fixed

- Pin the Maven group to `com.github.Viel0320.Popupselector` in `gradle.properties`. JitPack no
  longer rewrites publication coordinates, which previously stripped the `-metadata`/`-sources`
  classifiers from Gradle Module Metadata and broke Kotlin/Native (iOS) dependency resolution.

## [2.0.0] - 2026-08-30

This release restructures the public API around two configuration objects and fixes several
correctness defects in geometry and hosting. It is a **breaking release**; see the migration
guide below.

### Added

- `PopupVerticalAlignment` and `PopupHorizontalAlignment` — the expansion placement, split into two
  independent axes, both defaulting to `Auto`. Vertical `Auto` aims the panel at the current row —
  the selected one, or the first enabled row (where the keyboard cursor rests) when nothing is
  selected — centring it on the trigger; `Top`/`Bottom` pin a side. Horizontal `Auto` hugs the leading side when both sides fit (or opens toward the only side
  with room); `Start`/`End` pin a direction-aware side. Both axes also offer `Inherit`, which
  follows the corresponding component of `LocalPopupAlignment` — the ambient alignment context a
  subtree provides with `CompositionLocalProvider` — and falls back to `Auto` when none is
  provided. Explicit placements are honoured even when an item is selected.
- `PopupStyle` — aggregates metrics, timing, colours, typography and animation specs. `colors` is
  the only required parameter and comes first; `PopupDefaults.style()` resolves it from the
  current theme.
- `PopupLayout` — aggregates width strategy, max height, vertical and horizontal placement, and
  host policy.
- `PopupMetrics` and `PopupTiming` — all sizing and animation constants are now overridable
  data classes instead of module-level constants.
- Keyboard navigation: `↑`/`↓` (and `Tab`) move the cursor, `Enter`/`Space` select, `Esc` dismisses.
  Focus moves into the panel when it expands. Without a selection the keyboard cursor does not
  exist: `Enter` is a no-op until an arrow key engages the cursor (down from the first enabled
  item, up from the last), so the visible state never hides a committable row.
- **The panel keeps clear of the status and navigation bars automatically.** The vertical clamping
  respects the cross-platform `WindowInsets.safeDrawing` per side, so an edge-to-edge app — where
  the window extends behind the system bars — never draws the panel over them, with no per-component
  configuration. Desktop, where the insets are zero, is unaffected: `edgeMargin` remains the floor.
- Press feedback on the collapsed trigger: pressing it immediately shows the selection highlight —
  the same translucent indicator the panel uses, across the whole trigger, fading in over ~90 ms.
  That rectangle is exactly where the flying highlight starts when the panel opens, so the press
  feedback hands over to the morph seamlessly.
- **The selection highlight fades out across the whole collapse.** During the return animation the
  flying highlight tracks the morph fraction, dissolving with the settling panel; by the time the
  surface unmounts it is fully gone, so the resting trigger — which carries no highlight — takes
  over without any visible jump.
- `PopupOverlayHostState` now supports **multiple concurrent overlays**, keyed by owner.
- `PopupTiming.anchorHighlightFadeMillis` — the collapsed anchor's press/hover highlight fade
  duration, previously hard-coded to 90 ms.

### Changed

- **Breaking:** `PopupSelector` no longer takes `panelWidth`, `panelMaxHeight`, `alignment`,
  `hostPolicy`, `textStyle`, `colors`, `shapes`, `collapsedHeight`, `animationSpec`,
  `sizeAnimationSpec`, `autoMarquee`, and the two accessibility description strings as separate
  parameters. They are now grouped into `style` and `layout`. The `collapsedContent` slot moved to
  the last parameter, so it can be passed as a trailing lambda.
- **Breaking:** `PopupHostPolicy` is reduced to `Window` and `Overlay(hostState?)`. The old `Auto`
  is expressed as `Overlay(hostState = null)`, which falls back to `Window` when no ambient host
  exists.
- **Breaking:** the default expansion origin is now `Auto` on both axes. Vertical `Auto` centres
  the selected row when a selection exists and otherwise compares the room on each side; horizontal
  `Auto` hugs the leading side when both sides fit. This matches the old `Centered`-with-selection
  behaviour and restores the pre-2.0 corner-selection behaviour when nothing is selected.
- **Breaking:** the default panel width is now `PopupWidth.Wrap` (was `MatchAnchor`): the panel
  fits the widest row's sections and is never narrower than the trigger. Pass
  `PopupWidth.MatchAnchor` explicitly to keep the trigger-matched width.
- **Breaking:** `PopupAlignment` is replaced by `PopupVerticalAlignment` and
  `PopupHorizontalAlignment` (both defaulting to `Auto`; `Inherit` on both). Map old values as
  follows: `Centered` → defaults (its centring now lives inside vertical `Auto`); `Auto` →
  defaults; `TopStart` → `Top` + `Start`; `TopEnd` → `Top` + `End`; `BottomStart` → `Bottom` +
  `Start`; `BottomEnd` → `Bottom` + `End`; `TopLeft`/`BottomLeft` → `Top`/`Bottom` + `Start` (LTR)
  or + `End` (RTL); `TopRight`/`BottomRight` → the mirrored pair. There are no absolute
  `Left`/`Right` placements; pin the side with `Start`/`End` instead.
- **Breaking:** `PopupShapes` is removed; corner radius now lives in `PopupMetrics.corner`.
- **Breaking:** `PopupItem.count` is now `@Composable RowScope.() -> Unit` instead of
  `@Composable () -> Unit`, so it can use `weight` and `align` like the content slot.
- **Breaking:** `PopupItem` is now a `data class` annotated `@Stable` (was a plain class annotated
  `@Immutable`, which was an incorrect promise for a type holding composable lambdas). Its
  `content` slot moved to the last parameter, so it can be passed as a trailing lambda.
- **Breaking:** `PopupDefaults` now carries only the theme-dependent defaults — `colors()`,
  `blurColors()` and `style()`. The redundant `layout()` builder (identical to the `PopupLayout`
  constructor) and the `metrics`, `timing`, `animationSpec`, `sizeAnimationSpec` and `autoMarquee`
  re-exports are removed. Construct the data classes directly instead: `PopupLayout(...)`,
  `PopupMetrics.Default`, `PopupTiming()`, or a `spring(...)` spec of your choice.
- **Breaking:** Material 3 is now an `implementation` dependency instead of `api`. No public
  signature references a material3 type, so consumers no longer get Material 3 on their compile
  classpath transitively — declare it yourself if your code uses it (verified against a consumer
  module with no material3 dependency, which compiles against the full default-parameters path).
  Runtime resolution is unaffected: material3 remains on the transitive runtime classpath.
- Internal restructure, no API change: the expanded panel's parameter plumbing is consolidated
  into a single context object shared by the host/content/layout/surface chain; the flying
  selected row derives its per-frame geometry from one precomputed endpoint set instead of
  re-deriving it in every layout pass; the two panel-rectangle resolvers share a single clamping
  body; and the "reveal a row" scroll logic is unified between keyboard navigation and placement.
  Behaviour-preserving, including the edge case of a row taller than the viewport, which keeps
  aligning its top edge (the placement path can never reach that branch).
- **The collapsed trigger's visual now fills any forced minimum width.** With a `fillMaxWidth`
  root the hit area was always the whole row while the chip hugged the start edge; the chip now
  stretches to match, so visual, hit area and `MatchAnchor` panel width all agree. Ordinary
  wrap-content usage is unchanged.
- Geometry is resolved by a single `resolvePlacement()` entry point that returns an immutable
  `PanelPlacement`. Rendering no longer performs its own measurement or direction resolution.
- Window size is read from `LocalWindowInfo` rather than inferred from the composing container's
  constraints.

### Removed

- Dead `Rect.roundOut()` helper and the unreferenced `popupselector/consumer-rules.pro`.
- `PopupMorphState` no longer carries its own default spring or timing: the animation spec and
  timing always come from `PopupStyle`, removing the duplicated default definitions.

### Fixed

- **The morph now lands on the real chip shape even when the trigger sticks out of the screen.**
  The anchor was measured with `boundsInWindow()`, which reports only the on-screen slice of a
  clipped chip — so the expand animation started from, and the collapse landed on, that slice
  instead of the full chip. The anchor is now measured from its unclipped layout bounds; the
  expanded panel is still clamped into the window's safe area as before.
- **No more dark-edge shadow artifact with transparent containers.** The surface shadow used to
  ramp from the very start of the morph, and at low elevation it renders as a tight dark rim
  around the outline — clearly visible when the container is transparent. The shadow now waits
  until `PopupTiming.shadowStartFraction` (default `0.85f`) and ramps over the rest of the morph,
  when the panel is large enough for the shadow to spread as depth; set it to `0f` for the old
  behaviour.
- **Multiple selectors no longer overwrite one another.** The in-window overlay host previously
  held a single content slot, so expanding a second selector cleared the first, and dismissing
  either one cleared whichever content happened to be registered.
- **Panels no longer overflow the window's bottom edge.** `resolvePanelRect` clamped only the
  horizontal axis; an anchor near the bottom edge produced a panel extending past the safe margin.
- **Explicit `alignment` is no longer ignored when an item is selected.** Previously any non-null
  `selectedIndex` silently switched to centred placement, discarding the requested corner.
- **The flying selected row tracks the option list's live scroll position.** It is resolved against
  the list's current scroll offset rather than the offset captured when the panel was placed, so it
  still starts from the row the user actually clicked after they scroll.
- **The selection flies home when collapsing after a different pick.** Picking a new option while the
  panel is open freezes the last expanded geometry for the duration of the morph, so the chosen row
  animates back into the trigger instead of the panel jumping to re-centre on the new selection.
- **The intrinsic-width cache no longer goes stale.** Changing text style, density or window size
  now invalidates it.
- **Overlay content is registered once per owner** instead of being rebuilt on every
  recomposition.
- The chevron is drawn as a single instance, removing the positional jump between the collapsed
  and expanded copies.
- **The collapsed chevron is always visible.** The resting trigger previously reserved the icon's
  slot without drawing it, so the chevron disappeared the moment the collapse morph finished and
  the morph surface unmounted.
- **The collapsed trigger no longer jumps in size when a different option is picked.** While the
  panel is closing, the morph now lands on the trigger's live measured rectangle — which has
  re-measured to the newly selected content — while the panel-side geometry stays held, so the
  handover from surface to trigger is pixel-aligned instead of popping to the new width.
- **The selection highlight travels with the selection throughout the morph.** The highlight used
  to live on the list row, whose coordinate frame sweeps with the expanding panel — so the
  indicator slid a large distance while the selection content itself barely moved (the two only
  met when the morph finished). The highlight is now carried by the flying selected row: its
  container interpolates from the whole trigger to the whole row slot, matching the list row's
  highlight geometry exactly at the handover, and the list row's own highlight only appears at
  that handover.
- **Labels are clamped to a single line in both states.** A caller content composable that wants
  to wrap (a plain `Text`, say) is constrained to one line's height inside the collapsed trigger
  and the expanded rows — the row geometry never grows and no second line bleeds out.
- **Item fields render as justified sections instead of concatenated slots.** The content slot and
  the optional count slot each get a shared width — the per-slot maximum across all items — with
  the content section hugging the start edge and the count section end-aligned in a column pinned
  to the trailing edge (a two-column layout, not each count hugging its own label). `PopupWidth.Wrap`
  now sizes the panel to the sum of the section maxima (`padding*2 + content + gap + count`) rather
  than the widest single row, and the intrinsic-width measurement probes each section separately.
  A label longer than the panel clamps the content section to the remaining space — only the label
  truncates (marquee), the count section is always reserved and stays visible. The section order
  and alignment mirror with the layout direction: in RTL the content section hugs
  the trailing edge and the section boundary mirrors with it, and the flying row's resting slot and
  the chevron position follow the same direction.
- **The flying selected row no longer lands offset at the handovers.** Since sectioned alignment,
  the trigger's concatenated layout (count right after its own label) and the panel's aligned
  layout (count at the shared section boundary) disagreed for every item that was not the widest.
  The flying row's content section now interpolates from the item's own width to the shared width
  with the morph fraction, so it matches both the anchor at fraction 0 and the list row at
  fraction 1. A label wider than the trigger starts from the trigger's clamped width — the natural
  width would place the count outside the surface mid-morph and visibly pop it at the handover.
- The panel's rows leave the accessibility tree while the panel collapses, so screen readers
  never announce the fading surface; once collapsed, the surface is unmounted entirely.
- **The expanded panel no longer resets the user's scroll position when its geometry is
  re-resolved while open** (window resize, or a selection change from outside). The placement's
  scroll offset is adopted once per expansion. Previously every geometry change restarted the
  adoption — including every frame of the anchor's size animation during the collapse, which
  churned the scroll effect and could snap the list mid-collapse while the flying selection row
  resolved against the live offset.
- **Window size is no longer frozen at the first composition.** `WindowInfo.containerSize` is
  backed by snapshot state, and the previous `remember(windowInfo)` cache returned its stale entry
  on resize without re-running its calculation, so panel placement kept using the window size from
  the first composition. The size is now read directly so resizes invalidate the geometry. Most
  visible on desktop when resizing the window.
- The morph animation is no longer restarted on every recomposition when the caller builds
  `PopupDefaults.style(...)` inline. The effect is keyed on `expanded` alone and reads the latest
  animation spec and timing through `rememberUpdatedState`.
- The count-section measurement probe now carries the same `widthIn(max)` constraint as the
  content-section probe, so an unusually wide count can no longer push the panel width past the
  window's safe area.
- Numpad `Enter` now selects the focused option, matching the main `Enter` key.
- The dismiss scrim is removed from the accessibility tree (`clearAndSetSemantics`); previously it
  presented screen readers with an unlabelled full-screen clickable. Dismissal remains available
  via `Esc`, `Back`, and selecting a row.
- Version in `gradle.properties` now matches the release tag (`2.0.0`); a mismatched version can
  break JitPack resolution against Gradle Module Metadata.

### Migration guide (1.x → 2.0)

```kotlin
// Before
PopupSelector(
    items = items,
    expanded = expanded,
    onExpandedChange = { expanded = it },
    onSelect = { selected = it },
    panelWidth = PopupWidth.Wrap,
    panelMaxHeight = 300.dp,
    alignment = PopupAlignment.Auto,
    colors = PopupDefaults.colors(),
    shapes = PopupDefaults.shapes(corner = 12.dp),
    collapsedHeight = 36.dp,
    animationSpec = spring(),
    autoMarquee = false,
)

// After
PopupSelector(
    items = items,
    expanded = expanded,
    onExpandedChange = { expanded = it },
    onSelect = { selected = it },
    style = PopupDefaults.style(
        colors = PopupDefaults.colors(),
        metrics = PopupMetrics(corner = 12.dp, collapsedHeight = 36.dp),
        animationSpec = spring(),
        autoMarquee = false,
    ),
    layout = PopupLayout(
        width = PopupWidth.Wrap,
        maxHeight = 300.dp,
        verticalAlignment = PopupVerticalAlignment.Auto,
        horizontalAlignment = PopupHorizontalAlignment.Auto,
    ),
)
```

`PopupHostPolicy.Auto` becomes `PopupHostPolicy.Overlay()` (the default), and
`PopupHostPolicy.Overlay(state)` keeps the same meaning for explicit hosts.

The example above can omit the two alignments entirely — `Auto` on both axes is the 2.0 default,
and with a selection it opens centred on the current selection, matching the old
corner-selection behaviour only when nothing is selected. To pin a side, pass `Top`/`Bottom`
(vertical) or `Start`/`End` (horizontal) explicitly.
