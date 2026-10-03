package com.viel.compose.popupselector

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.viel.compose.popupselector.PopupDefaults.blurColors
import com.viel.compose.popupselector.PopupDefaults.colors
import com.viel.compose.popupselector.PopupDefaults.style

/**
 * One popup option.
 *
 * @param key Stable identity for the option. Used for list diffing and as the default selection key.
 * @param enabled Whether the option can be selected. Disabled rows are rendered dimmed and reject clicks.
 * @param count Optional trailing slot, typically a badge or count. Also [RowScope] scoped.
 * @param content The primary row content, scoped to a [RowScope] so it can use `weight` and `align`.
 */
@Stable
data class PopupItem(
    val key: Any? = null,
    val enabled: Boolean = true,
    val count: (@Composable RowScope.() -> Unit)? = null,
    val content: @Composable RowScope.() -> Unit,
)

/**
 * Convenience builder for the common "label + optional count" row, such as "All 75".
 *
 * @param key Stable identity for the option.
 * @param label The row label.
 * @param count Optional trailing count rendered in the `count` slot.
 * @param enabled Whether the option can be selected.
 * @param textStyle Overrides the ambient text style for the label.
 * @param countTextStyle Overrides the ambient text style for the count. Falls back to [textStyle].
 */
fun textPopupItem(
    key: Any,
    label: String,
    count: Int? = null,
    enabled: Boolean = true,
    textStyle: TextStyle? = null,
    countTextStyle: TextStyle? = null,
): PopupItem = PopupItem(
    key = key,
    enabled = enabled,
    content = {
        Text(
            text = label,
            style = textStyle ?: LocalTextStyle.current,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    },
    count = count?.let { value ->
        {
            Text(
                text = value.toString(),
                style = countTextStyle ?: textStyle ?: LocalTextStyle.current,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    },
)

/**
 * Vertical placement of the expanded panel relative to the trigger.
 *
 * - [Auto] *(default)* centres the selected row's vertical axis on the trigger's centre when a
 *   `selectedIndex` is given — so the current selection stays under the cursor as the panel opens
 *   — and otherwise picks the side with the most available room.
 * - [Top] and [Bottom] pin a side.
 * - [Inherit] follows the vertical component of [LocalPopupAlignment], falling back to [Auto]
 *   when no ambient alignment is provided.
 */
enum class PopupVerticalAlignment {
    Auto,
    Top,
    Bottom,

    /** Follows the vertical component of [LocalPopupAlignment]; [Auto] when none is provided. */
    Inherit,
}

/**
 * Horizontal placement of the expanded panel relative to the trigger.
 *
 * - [Auto] *(default)* hugs the trigger's leading side when both sides fit — or, when only one
 *   side has room, opens toward that side. Mirrors with the layout direction.
 * - [Start] hugs the trigger's leading edge and [End] its trailing edge; both mirror with the
 *   layout direction.
 * - [Inherit] follows the horizontal component of [LocalPopupAlignment], falling back to [Auto]
 *   when no ambient alignment is provided.
 */
enum class PopupHorizontalAlignment {
    Auto,
    Start,
    End,

    /** Follows the horizontal component of [LocalPopupAlignment]; [Auto] when none is provided. */
    Inherit,
}

/**
 * Ambient alignment context that [PopupVerticalAlignment.Inherit] and
 * [PopupHorizontalAlignment.Inherit] follow.
 *
 * Compose does not expose a layout's `contentAlignment` to its children, so a subtree that wants
 * its popups to follow along provides the container's alignment explicitly, typically next to it:
 *
 * ```
 * Box(contentAlignment = Alignment.BottomEnd) {
 *     CompositionLocalProvider(LocalPopupAlignment provides Alignment.BottomEnd) {
 *         PopupSelector(...)
 *     }
 * }
 * ```
 *
 * The two components map independently onto the popup's own vocabulary: the vertical component
 * becomes `Top`/`Auto`/`Bottom` and the horizontal one `Start`/`Auto`/`End` (direction-aware;
 * absolute `Left`/`Right` cannot be recovered from a resolved position). When no value is
 * provided, `Inherit` falls back to `Auto` on both axes.
 */
val LocalPopupAlignment = compositionLocalOf<Alignment?> { null }

/** Expanded panel width strategy. */
sealed interface PopupWidth {
    /** *(default)* Fits the intrinsic content width, never narrower than the anchor. */
    data object Wrap : PopupWidth

    /** Panel width matches the measured width of the collapsed anchor. */
    data object MatchAnchor : PopupWidth

    /** Panel width is fixed to [width], clamped to the window's safe area. */
    data class Fixed(val width: Dp) : PopupWidth
}

/**
 * Color roles for both morph endpoints. Interpolated continuously by the expand fraction.
 *
 * @param activeContent Content colour of the selected row in the expanded panel — the selected
 *   counterpart of [content], which every other row (and the collapsed trigger) resolves against.
 *   The whole row content (label and count slot) resolves against it through `LocalContentColor`.
 *   [Color.Unspecified] — the constructor default, so palettes built before this role existed
 *   keep their look — falls back to [content]. `PopupDefaults.colors()` resolves the default to
 *   `MaterialTheme.colorScheme.primary`.
 */
@Stable
data class PopupColors(
    val collapsedContainer: Color,
    val expandedContainer: Color,
    val border: Color,
    val content: Color,
    val activeContent: Color = Color.Unspecified,
)

/** Default spring driving the morph animation and the staggered row reveal. */
private val DefaultMorphAnimationSpec: AnimationSpec<Float> = spring(
    dampingRatio = 0.9f,
    stiffness = Spring.StiffnessMediumLow,
)

/** Default spring for the collapsed anchor's size changes. */
private val DefaultMorphSizeAnimationSpec: FiniteAnimationSpec<IntSize> = spring(
    dampingRatio = 0.9f,
    stiffness = Spring.StiffnessMediumLow,
)

/**
 * Visual configuration shared by the collapsed anchor and the expanded panel.
 *
 * @param colors Colour roles. The only required parameter: theme-dependent defaults resolve in
 *  [PopupDefaults.style] against the current `MaterialTheme`, which a plain constructor cannot do.
 * @param metrics Sizing and spacing values.
 * @param timing Morph animation thresholds and stagger constants.
 * @param textStyle Optional typography style for labels. Defaults to the ambient [LocalTextStyle].
 * @param animationSpec Main morph and reveal animation specification.
 * @param sizeAnimationSpec Size change animation specification for the collapsed anchor.
 * @param autoMarquee Whether option rows overflowing their container scroll with a marquee.
 *
 * Note on equality: [animationSpec] and [sizeAnimationSpec] have no structural equals
 * implementations, so two structurally identical styles never compare equal. Read individual
 * fields into `remember` keys instead of keying on a `PopupStyle` instance.
 */
@Stable
data class PopupStyle(
    val colors: PopupColors,
    val metrics: PopupMetrics = PopupMetrics.Default,
    val timing: PopupTiming = PopupTiming(),
    val textStyle: TextStyle? = null,
    val animationSpec: AnimationSpec<Float> = DefaultMorphAnimationSpec,
    val sizeAnimationSpec: FiniteAnimationSpec<IntSize> = DefaultMorphSizeAnimationSpec,
    val autoMarquee: Boolean = true,
)

/**
 * Layout configuration for the expanded panel.
 *
 * @param width Panel width strategy.
 * @param maxHeight Maximum panel height. [Dp.Unspecified] uses a fraction of the window height.
 * @param verticalAlignment Vertical placement. Defaults to [PopupVerticalAlignment.Auto], which
 *  centres the selected row when a selection exists and otherwise picks the roomier side.
 * @param horizontalAlignment Horizontal placement. Defaults to [PopupHorizontalAlignment.Auto],
 *  which hugs the leading side when both sides fit.
 * @param host Where the expanded panel is hosted.
 */
@Stable
data class PopupLayout(
    val width: PopupWidth = PopupWidth.Wrap,
    val maxHeight: Dp = Dp.Unspecified,
    val verticalAlignment: PopupVerticalAlignment = PopupVerticalAlignment.Auto,
    val horizontalAlignment: PopupHorizontalAlignment = PopupHorizontalAlignment.Auto,
    val host: PopupHostPolicy = PopupHostPolicy.Overlay(),
)

/**
 * Theme-aligned defaults for [PopupSelector].
 *
 * Only the defaults that must resolve against the current theme live here: [colors] and
 * [blurColors] read `MaterialTheme`, and [style] wires them into a [PopupStyle]. Every other
 * default belongs to its data class directly — see [PopupLayout], [PopupMetrics] and [PopupTiming].
 */
object PopupDefaults {
    /**
     * Default colors. Containers are opaque. For frosted-glass styling use [blurColors] and
     * supply the blur effect through `anchorModifier` / `surfaceModifier`; with transparent
     * containers the surface shadow waits until [PopupTiming.shadowStartFraction] before ramping
     * in, so the low-elevation shadow cannot read as a dark rim around the see-through surface.
     */
    @Composable
    fun colors(
        collapsedContainer: Color = Color.Transparent,
        expandedContainer: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border: Color = MaterialTheme.colorScheme.outline,
        content: Color = MaterialTheme.colorScheme.onSurface,
        activeContent: Color = MaterialTheme.colorScheme.primary,
    ): PopupColors = PopupColors(
        collapsedContainer = collapsedContainer,
        expandedContainer = expandedContainer,
        border = border,
        content = content,
        activeContent = activeContent,
    )

    /**
     * Transparent color palette for frosted glass (blur/Haze) styling.
     *
     * Container backgrounds are transparent so underlying blurred surfaces can be sampled.
     */
    @Composable
    fun blurColors(
        border: Color = MaterialTheme.colorScheme.outline,
        content: Color = MaterialTheme.colorScheme.onSurface,
        activeContent: Color = MaterialTheme.colorScheme.primary,
    ): PopupColors = PopupColors(
        collapsedContainer = Color.Transparent,
        expandedContainer = Color.Transparent,
        border = border,
        content = content,
        activeContent = activeContent,
    )

    /** Builds a [PopupStyle] from individual overrides. */
    @Composable
    fun style(
        colors: PopupColors = colors(),
        metrics: PopupMetrics = PopupMetrics.Default,
        timing: PopupTiming = PopupTiming(),
        textStyle: TextStyle? = null,
        animationSpec: AnimationSpec<Float> = DefaultMorphAnimationSpec,
        sizeAnimationSpec: FiniteAnimationSpec<IntSize> = DefaultMorphSizeAnimationSpec,
        autoMarquee: Boolean = true,
    ): PopupStyle = PopupStyle(
        colors = colors,
        metrics = metrics,
        timing = timing,
        textStyle = textStyle,
        animationSpec = animationSpec,
        sizeAnimationSpec = sizeAnimationSpec,
        autoMarquee = autoMarquee,
    )
}

/**
 * State holder for [PopupSelector] controlling expansion and selection.
 *
 * Use [rememberPopupSelectorState] to create and remember an instance.
 *
 * @param initialExpanded Initial expanded state of the selector.
 * @param initialSelectedIndex Initial index of the selected option, or null if none is selected.
 */
@Stable
class PopupSelectorState(
    initialExpanded: Boolean = false,
    initialSelectedIndex: Int? = null,
) {
    var expanded: Boolean by mutableStateOf(initialExpanded)
    var selectedIndex: Int? by mutableStateOf(initialSelectedIndex)

    fun expand() {
        expanded = true
    }

    fun collapse() {
        expanded = false
    }

    fun toggle() {
        expanded = !expanded
    }

    /**
     * Selects the option at [index] and collapses the selector.
     *
     * The index is stored as given; an out-of-bounds index does not throw, but the collapsed
     * trigger will not display any selected option until a valid index is selected.
     */
    fun select(index: Int) {
        selectedIndex = index
        collapse()
    }

    companion object {
        val Saver: Saver<PopupSelectorState, Any> = listSaver(
            save = { listOf(it.expanded, it.selectedIndex) },
            restore = {
                PopupSelectorState(
                    initialExpanded = it[0] as Boolean,
                    initialSelectedIndex = it[1] as? Int,
                )
            },
        )
    }
}

/**
 * Creates and remembers a [PopupSelectorState] with [rememberSaveable].
 *
 * @param initialExpanded Initial expanded state of the selector.
 * @param initialSelectedIndex Initial index of the selected option, or null if none is selected.
 */
@Composable
fun rememberPopupSelectorState(
    initialExpanded: Boolean = false,
    initialSelectedIndex: Int? = null,
): PopupSelectorState = rememberSaveable(saver = PopupSelectorState.Saver) {
    PopupSelectorState(
        initialExpanded = initialExpanded,
        initialSelectedIndex = initialSelectedIndex,
    )
}

/**
 * A fluid popup selector that morphs between a collapsed button and an expanded floating panel,
 * driven by a [PopupSelectorState].
 *
 * @param items The options to display. Supply a stable list — one built inline per recomposition
 *  re-runs the panel's section-width measurement and can make the aligned columns visibly jump.
 *  `remember` the list, as the demo app does.
 * @param modifier Modifier for the root container.
 * @param state State holder controlling the expanded state and selected index.
 * @param onSelect Optional callback invoked when an option is selected.
 * @param style Visual configuration: metrics, timing, colors, typography and animation specs.
 * @param layout Layout configuration: width strategy, max height, vertical and horizontal
 *  placement, and host policy.
 * @param anchorModifier Optional modifier applied to the collapsed trigger surface.
 * @param surfaceModifier Optional modifier applied to the expanded floating surface.
 * @param collapsedContent Optional custom content for the collapsed button. When null, the
 *  selected option's content is shown instead. Last so it can be passed as a trailing lambda.
 */
@Composable
fun PopupSelector(
    items: List<PopupItem>,
    modifier: Modifier = Modifier,
    state: PopupSelectorState = rememberPopupSelectorState(),
    onSelect: ((index: Int) -> Unit)? = null,
    style: PopupStyle = style(),
    layout: PopupLayout = PopupLayout(),
    anchorModifier: Modifier = Modifier,
    surfaceModifier: Modifier = Modifier,
    collapsedContent: (@Composable RowScope.() -> Unit)? = null,
) {
    PopupSelector(
        items = items,
        expanded = state.expanded,
        onExpandedChange = { state.expanded = it },
        onSelect = { index ->
            state.select(index)
            onSelect?.invoke(index)
        },
        modifier = modifier,
        selectedIndex = state.selectedIndex,
        style = style,
        layout = layout,
        anchorModifier = anchorModifier,
        surfaceModifier = surfaceModifier,
        collapsedContent = collapsedContent,
    )
}

/**
 * A fluid popup selector that morphs between a collapsed button and an expanded floating panel.
 *
 * @param items The options to display. Supply a stable list — one built inline per recomposition
 *  re-runs the panel's section-width measurement and can make the aligned columns visibly jump.
 *  `remember` the list, as the demo app does.
 * @param expanded Whether the popup is currently expanded.
 * @param onExpandedChange Called when the expansion state changes.
 * @param onSelect Called with the index of the selected option.
 * @param modifier Modifier for the root container.
 * @param selectedIndex Index of the currently selected option, or null if none.
 * @param style Visual configuration: metrics, timing, colors, typography and animation specs.
 * @param layout Layout configuration: width strategy, max height, vertical and horizontal
 *  placement, and host policy.
 * @param anchorModifier Optional modifier applied to the collapsed trigger surface. Intended for
 *  frosted-glass effects, where the sampling source sits on the background layer behind the anchor.
 * @param surfaceModifier Optional modifier applied to the expanded floating surface. Intended for
 *  frosted-glass effects, where the sampling source covers a broader screen area.
 * @param collapsedContent Optional custom content for the collapsed button. When null, the
 *  selected option's content is shown instead. Last so it can be passed as a trailing lambda.
 */
@Composable
fun PopupSelector(
    items: List<PopupItem>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    style: PopupStyle = style(),
    layout: PopupLayout = PopupLayout(),
    anchorModifier: Modifier = Modifier,
    surfaceModifier: Modifier = Modifier,
    collapsedContent: (@Composable RowScope.() -> Unit)? = null,
) {
    val safeSelectedIndex = selectedIndex?.takeIf { it in items.indices }

    val morph = rememberMorphState(expanded)
    // Read through rememberUpdatedState rather than keying the effect on the spec: AnimationSpec
    // has no structural equality, and callers commonly build their style inline, so a spec key
    // would restart the morph on every recomposition. The latest spec and timing are picked up
    // each time the animation (re)starts, i.e. on every expand/collapse.
    val currentAnimationSpec by rememberUpdatedState(style.animationSpec)
    val currentTiming by rememberUpdatedState(style.timing)
    LaunchedEffect(expanded) {
        morph.animateTo(
            target = if (expanded) 1f else 0f,
            animationSpec = currentAnimationSpec,
            timing = currentTiming,
        )
    }

    var anchorBounds by remember { mutableStateOf<Rect?>(null) }

    // Stabilized callbacks: a caller passing fresh lambdas every recomposition must not force the
    // panel context (and with it the whole host chain) to rebuild. The wrappers read the latest
    // values at invocation time.
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnExpandedChange by rememberUpdatedState(onExpandedChange)
    val selectHandler = remember { { index: Int -> currentOnSelect(index) } }
    val dismissHandler = remember { { currentOnExpandedChange(false) } }

    val contentScope: @Composable () -> Unit = {
        Box(
            modifier = modifier,
            propagateMinConstraints = true,
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer { alpha = if (morph.isVisible) 0f else 1f },
                propagateMinConstraints = true,
            ) {
                CollapsedAnchor(
                    items = items,
                    selectedIndex = safeSelectedIndex,
                    collapsedContent = collapsedContent,
                    style = style,
                    expanded = expanded,
                    anchorModifier = anchorModifier,
                    onClick = { onExpandedChange(!expanded) },
                    onVisualBoundsMeasured = { anchorBounds = it },
                )
            }

            val bounds = anchorBounds
            if (bounds != null && morph.isVisible) {
                // Captured inside contentScope so it reflects any ProvideTextStyle wrapping us.
                val contentContext = PopupContentContext.capture()
                // Remembered so the per-frame anchor tracking during the collapse — which
                // recomposes this scope without touching any panel input — reuses the instance
                // and lets the host chain skip recomposition.
                val context = remember(
                    items,
                    safeSelectedIndex,
                    morph,
                    style,
                    layout,
                    contentContext,
                    surfaceModifier,
                    selectHandler,
                    dismissHandler,
                ) {
                    PanelContext(
                        items = items,
                        selectedIndex = safeSelectedIndex,
                        morph = morph,
                        style = style,
                        layout = layout,
                        contentContext = contentContext,
                        surfaceModifier = surfaceModifier,
                        onSelect = selectHandler,
                        onDismiss = dismissHandler,
                    )
                }
                PopupPanelHost(
                    expanded = expanded,
                    anchor = bounds,
                    context = context,
                )
            }
        }
    }

    if (style.textStyle != null) {
        ProvideTextStyle(style.textStyle, content = contentScope)
    } else {
        contentScope()
    }
}

/**
 * Tracks the collapsed surface bounds in window coordinates.
 *
 * Measured from the unclipped layout bounds ([positionInWindow] + size) rather than
 * [androidx.compose.ui.layout.boundsInWindow], which reports the visible slice when the chip sticks
 * out of the window or sits under a clipping ancestor — the morph would then start and land on that
 * slice instead of the real chip shape.
 */
internal fun Modifier.trackAnchorBounds(onMeasured: (Rect) -> Unit): Modifier =
    this.onGloballyPositioned { coordinates ->
        val position = coordinates.positionInWindow()
        val size = coordinates.size
        onMeasured(
            Rect(
                left = position.x,
                top = position.y,
                right = position.x + size.width,
                bottom = position.y + size.height,
            ),
        )
    }
