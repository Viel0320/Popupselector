package com.viel.compose.popupselector

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavioural tests for the expanded panel.
 *
 * These cover the interaction contract — what happens when a row is clicked — and the composition
 * context the panel content sees. Neither can be verified by geometry unit tests, and both have
 * regressed before.
 *
 * Rows are located by their text rather than by test tag: `selectable` merges descendant semantics,
 * so a tag placed on the row's text would only be visible in the unmerged tree, where the node
 * carries no click action.
 */
/** Distinct from row labels so the collapsed anchor never matches a row query. */
private const val anchorLabel = "SELECT"

@OptIn(ExperimentalTestApi::class)
class PopupPanelUiTest : BaseComposeUiTest() {

    private val anchorTag = "popup-anchor"

    private fun rowLabel(index: Int) = "Item $index"

    /** Rows whose label carries a marker so call-site styling can be asserted through text. */
    private fun probeItems(count: Int = 3): List<PopupItem> = List(count) { index ->
        PopupItem(key = index, content = { ProbeLabel(index) })
    }

    /**
     * Probes [LocalTextStyle], which the panel does not override.
     *
     * [LocalContentColor] would be a poor probe: `PopupRowBody` deliberately provides it from
     * `style.colors.content`, so row content always resolves it against the configured colour
     * rather than the ambient one. The ambient text style is not overridden anywhere, which is
     * exactly what makes it leak the host's context when the panel is re-hosted.
     */
    @Composable
    private fun RowScope.ProbeLabel(index: Int) {
        Text("${rowLabel(index)} ${LocalTextStyle.current.color.value}")
    }

    @Composable
    private fun RowScope.AnchorLabel() {
        Text(anchorLabel)
    }

    private fun fixedLayout(host: PopupHostPolicy = PopupHostPolicy.Overlay()) = PopupLayout(
        width = PopupWidth.Fixed(240.dp),
        verticalAlignment = PopupVerticalAlignment.Auto,
                horizontalAlignment = PopupHorizontalAlignment.Auto,
        host = host,
    )

    /** Lets the morph animation settle so rows are laid out and readable. */
    private fun ComposeUiTest.settle() {
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
    }

    private fun ComposeUiTest.row(index: Int) =
        onNodeWithText(rowLabel(index), substring = true)

    @Test
    fun anchorClickTogglesExpansion() = runComposeUiTest {
        var expanded by mutableStateOf(false)

        setContent {
            PopupSelector(
                items = probeItems(),
                expanded = expanded,
                selectedIndex = null,
                onExpandedChange = { expanded = it },
                onSelect = {},
                modifier = Modifier.testTag(anchorTag),
                collapsedContent = { AnchorLabel() },
                layout = fixedLayout(),
            )
        }

        onNodeWithTag(anchorTag).performClick()
        settle()
        assertTrue(expanded, "first anchor click should expand")

        onNodeWithTag(anchorTag).performClick()
        settle()
        assertTrue(!expanded, "second anchor click should collapse")
    }

    @Test
    fun clickingARowSelectsItAndDismissesThePanel() = runComposeUiTest {
        var expanded by mutableStateOf(false)
        var selectedIndex by mutableStateOf<Int?>(null)
        var dismissCount = 0

        setContent {
            PopupSelector(
                items = probeItems(),
                expanded = expanded,
                selectedIndex = selectedIndex,
                onExpandedChange = {
                    expanded = it
                    if (!it) dismissCount++
                },
                onSelect = { selectedIndex = it },
                modifier = Modifier.testTag(anchorTag),
                collapsedContent = { AnchorLabel() },
                layout = fixedLayout(),
            )
        }

        onNodeWithTag(anchorTag).performClick()
        settle()
        assertTrue(expanded, "anchor click should expand the panel")

        row(1).performClick()
        settle()

        // Selecting must commit the selection and close the panel in the same interaction.
        // Regression: the click path once called onSelect without dismissing, which left the panel
        // open and re-resolved its position against the new index.
        assertEquals(1, selectedIndex)
        assertTrue(dismissCount > 0, "selecting a row must dismiss the panel")
    }

    @Test
    fun panelContentSeesTheCallSiteTextStyleUnderWindowHost() = runComposeUiTest {
        // The system popup host inherits the caller's context on its own, so this case documents
        // the behaviour that must hold for both hosts rather than guarding against a known bug.
        val probeColor = Color(0xFF00FF00)
        var expanded by mutableStateOf(false)

        setContent {
            // A text style set only at the call site. Without the panel restoring its composition
            // context, the expanded rows would resolve the ambient style from wherever the host
            // happens to sit in the tree instead.
            CompositionLocalProvider(LocalTextStyle provides TextStyle(color = probeColor)) {
                PopupSelector(
                    items = probeItems(),
                    expanded = expanded,
                    selectedIndex = null,
                    onExpandedChange = { expanded = it },
                    onSelect = {},
                    modifier = Modifier.testTag(anchorTag),
                    collapsedContent = { AnchorLabel() },
                    layout = fixedLayout(host = PopupHostPolicy.Window),
                )
            }
        }

        onNodeWithTag(anchorTag).performClick()
        settle()

        val probeText = probeColor.value.toString()
        row(0).assertTextContains(probeText, substring = true)
        row(1).assertTextContains(probeText, substring = true)
    }

    @Test
    fun panelContentSeesTheCallSiteTextStyleUnderOverlayHost() = runComposeUiTest {
        // Same assertion as above, but routed through an in-window overlay host, which is where the
        // composition-context mismatch actually surfaced.
        val probeColor = Color(0xFFFF0000)
        var expanded by mutableStateOf(false)

        setContent {
            PopupOverlayContainer {
                CompositionLocalProvider(LocalTextStyle provides TextStyle(color = probeColor)) {
                    PopupSelector(
                        items = probeItems(),
                        expanded = expanded,
                        selectedIndex = null,
                        onExpandedChange = { expanded = it },
                        onSelect = {},
                        modifier = Modifier.testTag(anchorTag),
                        collapsedContent = { AnchorLabel() },
                        layout = fixedLayout(host = PopupHostPolicy.Overlay()),
                    )
                }
            }
        }

        onNodeWithTag(anchorTag).performClick()
        settle()

        val probeText = probeColor.value.toString()
        row(0).assertTextContains(probeText, substring = true)
        row(1).assertTextContains(probeText, substring = true)
    }

    @Test
    fun explicitTextStyleOverridesTheAmbientOneUnderOverlayHost() = runComposeUiTest {
        // An explicit style must win over the captured ambient one even when the panel is re-hosted.
        val probeColor = Color(0xFF0000FF)
        var expanded by mutableStateOf(false)

        setContent {
            PopupOverlayContainer {
                CompositionLocalProvider(LocalTextStyle provides TextStyle(color = Color(0xFF00FF00))) {
                    PopupSelector(
                        items = probeItems(),
                        expanded = expanded,
                        selectedIndex = null,
                        onExpandedChange = { expanded = it },
                        onSelect = {},
                        modifier = Modifier.testTag(anchorTag),
                        collapsedContent = { AnchorLabel() },
                        style = PopupDefaults.style(textStyle = TextStyle(color = probeColor)),
                        layout = fixedLayout(host = PopupHostPolicy.Overlay()),
                    )
                }
            }
        }

        onNodeWithTag(anchorTag).performClick()
        settle()

        val probeText = probeColor.value.toString()
        row(0).assertTextContains(probeText, substring = true)
    }

    @Test
    fun scrollableListCentresTheSelectedRowOnTheTrigger() = runComposeUiTest {
        // End-to-end guard for the "scroll first, then position" chain. Sizing the option list to
        // its own content instead of the panel left `verticalScroll` with no scroll range, so the
        // offset resolved by the geometry layer was silently dropped and the selected row landed
        // hundreds of pixels away from where the panel had been placed to meet it.
        val selected = 10
        var expanded by mutableStateOf(false)

        setContent {
            // Offset from the top so the centred panel has room and is not clamped by the window.
            Box(Modifier.fillMaxSize().padding(top = 200.dp)) {
                PopupSelector(
                    items = probeItems(20),
                    expanded = expanded,
                    selectedIndex = selected,
                    onExpandedChange = { expanded = it },
                    onSelect = {},
                    modifier = Modifier.testTag(anchorTag),
                    collapsedContent = { AnchorLabel() },
                    layout = PopupLayout(
                        width = PopupWidth.Fixed(240.dp),
                        verticalAlignment = PopupVerticalAlignment.Auto,
                        maxHeight = 240.dp,
                    ),
                )
            }
        }

        onNodeWithTag(anchorTag).performClick()
        settle()

        fun centerY(bounds: DpRect) = ((bounds.top + bounds.bottom) / 2f).value

        val triggerCenter = centerY(onNodeWithTag(anchorTag).getUnclippedBoundsInRoot())
        val rowCenter = centerY(row(selected).getUnclippedBoundsInRoot())

        assertEquals(triggerCenter, rowCenter, absoluteTolerance = 2f)
    }

    @Test
    fun disabledRowsRejectClicks() = runComposeUiTest {
        var expanded by mutableStateOf(false)
        var selectedIndex by mutableStateOf<Int?>(null)

        val list = listOf(
            PopupItem(key = 0, enabled = true, content = { ProbeLabel(0) }),
            PopupItem(key = 1, enabled = false, content = { ProbeLabel(1) }),
        )

        setContent {
            PopupSelector(
                items = list,
                expanded = expanded,
                selectedIndex = selectedIndex,
                onExpandedChange = { expanded = it },
                onSelect = { selectedIndex = it },
                modifier = Modifier.testTag(anchorTag),
                collapsedContent = { AnchorLabel() },
                layout = fixedLayout(),
            )
        }

        onNodeWithTag(anchorTag).performClick()
        settle()

        row(1).performClick()
        settle()

        assertEquals(null, selectedIndex, "disabled rows must not be selectable")
    }

    @Test
    fun collapsedSizeMatchesTheNewlySelectedContent() = runComposeUiTest {
        // Regression for the collapse size jump: picking a longer (or shorter) item while the panel
        // is open re-measures the trigger, and the collapsing surface must land on that new width.
        // The reference width is what the trigger settles at when the same item is selected without
        // any morph involved; the post-collapse width has to match it exactly.
        val shortLabel = "S"
        val longLabel = "A much longer option label for width probing"

        var expanded by mutableStateOf(false)
        var selectedIndex by mutableStateOf<Int?>(0)

        setContent {
            PopupSelector(
                items = listOf(
                    PopupItem(key = 0, content = { Text(shortLabel) }),
                    PopupItem(key = 1, content = { Text(longLabel) }),
                ),
                expanded = expanded,
                selectedIndex = selectedIndex,
                onExpandedChange = { expanded = it },
                onSelect = { selectedIndex = it },
                modifier = Modifier.testTag(anchorTag),
                layout = fixedLayout(),
            )
        }

        fun anchorWidth() =
            with(onNodeWithTag(anchorTag).getUnclippedBoundsInRoot()) { right - left }

        waitForIdle()
        val shortWidth = anchorWidth()

        selectedIndex = 1
        mainClock.advanceTimeBy(2_000)
        waitForIdle()
        val longWidth = anchorWidth()
        assertTrue(
            longWidth > shortWidth + 40.dp,
            "expected the trigger to be wider with the longer item selected ($longWidth vs $shortWidth)",
        )


        selectedIndex = 0
        mainClock.advanceTimeBy(2_000)
        waitForIdle()
        assertEquals(shortWidth.value, anchorWidth().value, absoluteTolerance = 1f)

        onNodeWithTag(anchorTag).performClick()
        settle()
        assertTrue(expanded, "anchor click should expand the panel")

        onNodeWithText(longLabel).performClick()
        mainClock.advanceTimeBy(2_000)
        waitForIdle()

        assertTrue(!expanded, "selecting a row must dismiss the panel")
        assertEquals(
            longWidth.value,
            anchorWidth().value,
            absoluteTolerance = 1f,
            "the collapsed trigger must end at the newly selected item's width",
        )
    }

    @Test
    fun inheritFollowsTheAmbientAlignmentContext() = runComposeUiTest {
        // Both Inherit placements read LocalPopupAlignment: BottomEnd on the ambient context must
        // open the panel upward with its right edge on the anchor's right edge.
        var expanded by mutableStateOf(false)

        setContent {
            PopupOverlayContainer {
                CompositionLocalProvider(LocalPopupAlignment provides Alignment.BottomEnd) {
                    Box(Modifier.fillMaxSize().padding(top = 300.dp, start = 300.dp)) {
                        PopupSelector(
                        items = probeItems(3),
                        expanded = expanded,
                        selectedIndex = 0,
                        onExpandedChange = { expanded = it },
                        onSelect = {},
                        modifier = Modifier.testTag(anchorTag),
                        collapsedContent = { AnchorLabel() },
                            layout = PopupLayout(
                                width = PopupWidth.Fixed(240.dp),
                                verticalAlignment = PopupVerticalAlignment.Inherit,
                                horizontalAlignment = PopupHorizontalAlignment.Inherit,
                            ),
                        )
                    }
                }
            }
        }

        onNodeWithTag(anchorTag).performClick()
        settle()

        val anchor = onNodeWithTag(anchorTag).getUnclippedBoundsInRoot()
        val lastRow = row(2).getUnclippedBoundsInRoot()
        val firstRow = row(0).getUnclippedBoundsInRoot()
        println("PROBE anchor=$anchor visual=${onNodeWithTag(visualAnchorTag, useUnmergedTree = true).getUnclippedBoundsInRoot()} firstRow=$firstRow")

        // Bottom: the panel sits directly above the trigger (the trigger's row is centred in its
        // 48dp touch target, and the panel keeps its own 8dp padding above its bottom edge).
        assertEquals(anchor.bottom.value - 16f, lastRow.bottom.value, absoluteTolerance = 1f)
        // End: the panel's right edge is flush with the anchor's right edge.
        assertEquals(anchor.right.value, firstRow.right.value, absoluteTolerance = 1f)
    }

    @Test
    fun inheritFallsBackToTheLibraryDefaultsWithoutAmbient() = runComposeUiTest {
        // Without a provided ambient alignment, Inherit degrades to the library defaults:
        // vertically centred on the trigger, horizontally hugging its leading edge.
        var expanded by mutableStateOf(false)

        setContent {
            Box(Modifier.fillMaxSize().padding(top = 100.dp, start = 100.dp)) {
                PopupSelector(
                    items = probeItems(3),
                    expanded = expanded,
                    selectedIndex = 0,
                    onExpandedChange = { expanded = it },
                    onSelect = {},
                    modifier = Modifier.testTag(anchorTag),
                    collapsedContent = { AnchorLabel() },
                    layout = PopupLayout(
                        width = PopupWidth.Fixed(240.dp),
                        verticalAlignment = PopupVerticalAlignment.Inherit,
                        horizontalAlignment = PopupHorizontalAlignment.Inherit,
                    ),
                )
            }
        }

        onNodeWithTag(anchorTag).performClick()
        settle()

        val anchor = onNodeWithTag(anchorTag).getUnclippedBoundsInRoot()
        val firstRow = row(0).getUnclippedBoundsInRoot()

        val anchorCenter = (anchor.top.value + anchor.bottom.value) / 2f
        val rowCenter = (firstRow.top.value + firstRow.bottom.value) / 2f
        assertEquals(anchorCenter, rowCenter, absoluteTolerance = 1f)
        // Start: the panel's left edge is flush with the anchor's left edge.
        assertEquals(anchor.left.value, firstRow.left.value, absoluteTolerance = 1f)
    }

    @Test
    fun enterDoesNothingUntilTheCursorIsEngaged() = runComposeUiTest {
        // With no selection the keyboard cursor does not exist: Enter is a no-op until an arrow
        // key engages the cursor (down from the first item), so nothing invisible can be
        // committed. The panel still dismisses via the tap-outside surface, not Enter.
        var expanded by mutableStateOf(false)
        var selected by mutableStateOf<Int?>(null)

        setContent {
            PopupOverlayContainer {
                PopupSelector(
                    items = probeItems(3),
                    expanded = expanded,
                    selectedIndex = null,
                    onExpandedChange = { expanded = it },
                    onSelect = { selected = it },
                    modifier = Modifier.testTag(anchorTag),
                    collapsedContent = { AnchorLabel() },
                    layout = fixedLayout(),
                )
            }
        }

        onNodeWithTag(anchorTag).performClick()
        settle()
        assertTrue(expanded)

        onNodeWithTag(anchorTag).performKeyInput { pressKey(Key.Enter) }
        settle()
        assertNull(selected, "with no cursor there is nothing for Enter to commit")

        onNodeWithTag(anchorTag).performKeyInput { pressKey(Key.DirectionDown) }
        settle()
        onNodeWithTag(anchorTag).performKeyInput { pressKey(Key.Enter) }
        settle()
        assertEquals(0, selected, "arrow-down engages the cursor on the first item")
        assertTrue(!expanded, "committing a row dismisses the panel")

        // Reopen: the cursor resets again, so the first down lands on the first item once more.
        onNodeWithTag(anchorTag).performClick()
        settle()
        onNodeWithTag(anchorTag).performKeyInput { pressKey(Key.DirectionDown) }
        settle()
        onNodeWithTag(anchorTag).performKeyInput { pressKey(Key.DirectionDown) }
        settle()
        onNodeWithTag(anchorTag).performKeyInput { pressKey(Key.Enter) }
        settle()
        assertEquals(1, selected, "two moves rest the cursor on the second item")
    }

    @Test
    fun noSelectionCentresTheFirstRowOnTheTrigger() = runComposeUiTest {
        // Vertical Auto aims the panel at the current row: the selected one, or — when nothing is
        // selected — the first enabled row the keyboard cursor rests on.
        var expanded by mutableStateOf(false)

        setContent {
            // Room above the trigger: centring needs the panel to extend past its top edge.
            Box(Modifier.fillMaxSize().padding(top = 100.dp, start = 100.dp)) {
                PopupSelector(
                    items = probeItems(3),
                    expanded = expanded,
                    selectedIndex = null,
                    onExpandedChange = { expanded = it },
                    onSelect = {},
                    modifier = Modifier.testTag(anchorTag),
                    collapsedContent = { AnchorLabel() },
                    layout = fixedLayout(),
                )
            }
        }

        onNodeWithTag(anchorTag).performClick()
        settle()

        val anchor = onNodeWithTag(anchorTag).getUnclippedBoundsInRoot()
        val firstRow = row(0).getUnclippedBoundsInRoot()
        val anchorCenter = (anchor.top.value + anchor.bottom.value) / 2f
        val rowCenter = (firstRow.top.value + firstRow.bottom.value) / 2f
        assertEquals(anchorCenter, rowCenter, absoluteTolerance = 1f)
    }

    @Test
    fun itemSectionsAlignAcrossRows() = runComposeUiTest {
        // Items render as justified sections, not concatenated slots: every label shares the
        // content section's leading edge, and every count is end-aligned in its section, so both
        // counts share the count section's trailing edge.
        var expanded by mutableStateOf(false)

        setContent {
            PopupSelector(
                items = listOf(
                    textPopupItem(key = 0, label = "A", count = 1),
                    textPopupItem(key = 1, label = "A much longer label", count = 45),
                ),
                expanded = expanded,
                selectedIndex = null,
                onExpandedChange = { expanded = it },
                onSelect = {},
                modifier = Modifier.testTag(anchorTag),
                layout = PopupLayout(width = PopupWidth.Wrap, verticalAlignment = PopupVerticalAlignment.Auto, horizontalAlignment = PopupHorizontalAlignment.Auto),
            )
        }

        onNodeWithTag(anchorTag).performClick()
        settle()

        // The merged row node spans the full panel width, so the text bounds must come from the
        // unmerged tree or the assertion is vacuous.
        val labelA = onNodeWithText("A", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val labelLong = onNodeWithText("A much longer label", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(
            labelA.left.value,
            labelLong.left.value,
            absoluteTolerance = 0.5f,
            "labels must share the content section's left edge",
        )

        val countOne = onNodeWithText("1", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val countFortyFive = onNodeWithText("45", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(
            countOne.right.value,
            countFortyFive.right.value,
            absoluteTolerance = 0.5f,
            "counts must share the count section's trailing edge",
        )
    }

    @Test
    fun itemSectionsFollowTheTextDirection() = runComposeUiTest {
        // Mirrored text direction flips the sections: labels share their trailing (right) edge
        // and counts share the leading edge of their section — also the right edge in RTL.
        var expanded by mutableStateOf(false)

        setContent {
            // The overlay host composes the panel at the container site, not the call site —
            // exactly where the text direction used to be lost.
            PopupOverlayContainer {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    PopupSelector(
                        items = listOf(
                            textPopupItem(key = 0, label = "A", count = 1),
                            textPopupItem(key = 1, label = "A much longer label", count = 45),
                        ),
                        expanded = expanded,
                        selectedIndex = null,
                        onExpandedChange = { expanded = it },
                        onSelect = {},
                        modifier = Modifier.testTag(anchorTag),
                        layout = PopupLayout(width = PopupWidth.Wrap, verticalAlignment = PopupVerticalAlignment.Auto, horizontalAlignment = PopupHorizontalAlignment.Auto),
                    )
                }
            }
        }

        onNodeWithTag(anchorTag).performClick()
        settle()

        // The merged row node spans the full panel width, so the text bounds must come from the
        // unmerged tree or the assertion is vacuous.
        val labelA = onNodeWithText("A", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val labelLong = onNodeWithText("A much longer label", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(
            labelA.right.value,
            labelLong.right.value,
            absoluteTolerance = 0.5f,
            "labels must share the content section's trailing edge",
        )

        val countOne = onNodeWithText("1", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val countFortyFive = onNodeWithText("45", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(
            countOne.left.value,
            countFortyFive.left.value,
            absoluteTolerance = 0.5f,
            "counts must share the count section's trailing edge in RTL",
        )
    }

    @Test
    fun sectionsAreJustifiedCountsPinnedToTheTrailingEdge() = runComposeUiTest {
        // Sections are always justified: the content section hugs the start and the count section
        // is pinned to the trailing edge. A wider-than-needed panel makes the pinning observable.
        var expanded by mutableStateOf(false)

        setContent {
            PopupSelector(
                items = listOf(
                    textPopupItem(key = 0, label = "A", count = 1),
                    textPopupItem(key = 1, label = "A much longer label", count = 45),
                ),
                expanded = expanded,
                selectedIndex = null,
                onExpandedChange = { expanded = it },
                onSelect = {},
                modifier = Modifier.testTag(anchorTag),
                layout = PopupLayout(width = PopupWidth.Fixed(300.dp), verticalAlignment = PopupVerticalAlignment.Auto, horizontalAlignment = PopupHorizontalAlignment.Auto),
            )
        }

        onNodeWithTag(anchorTag).performClick()
        settle()

        val labelA = onNodeWithText("A", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val labelLong = onNodeWithText("A much longer label", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(
            labelA.left.value,
            labelLong.left.value,
            absoluteTolerance = 0.5f,
            "the content section must still hug the start edge",
        )

        val countOne = onNodeWithText("1", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val countFortyFive = onNodeWithText("45", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(
            countOne.right.value,
            countFortyFive.right.value,
            absoluteTolerance = 0.5f,
            "justify must pin both counts to the trailing edge",
        )
    }

    @Test
    fun longLabelsClampTheContentSectionAndKeepTheCountVisible() = runComposeUiTest {
        // The shared content section is the widest label's natural width; a label longer than the
        // panel must clamp the section (the label marquees) instead of pushing the count section
        // out of the clipped surface — the count is always preserved.
        var expanded by mutableStateOf(false)

        setContent {
            Box(Modifier.fillMaxSize().padding(top = 100.dp, start = 100.dp)) {
                PopupSelector(
                    items = listOf(
                        textPopupItem(
                            "long",
                            "An extremely long label that far exceeds the panel width",
                            count = 42,
                        ),
                        textPopupItem("short", "Short", count = 7),
                    ),
                    expanded = expanded,
                    selectedIndex = null,
                    onExpandedChange = { expanded = it },
                    onSelect = {},
                    modifier = Modifier.testTag(anchorTag),
                    layout = PopupLayout(width = PopupWidth.Fixed(240.dp), verticalAlignment = PopupVerticalAlignment.Auto, horizontalAlignment = PopupHorizontalAlignment.Auto),
                )
            }
        }

        onNodeWithTag(anchorTag).performClick()
        settle()

        val anchor = onNodeWithTag(anchorTag).getUnclippedBoundsInRoot()
        val panelRight = anchor.left.value + 240f
        val countLong = onNodeWithText("42", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val countShort = onNodeWithText("7", useUnmergedTree = true).getUnclippedBoundsInRoot()

        // The count survives inside the panel, at the justified trailing position. (The label's
        // own layout bounds exceed the visual marquee clip, so only the count placement — which
        // has no marquee — is asserted here.) The margin derives from the row padding instead of
        // a literal, so metric tweaks don't invalidate the assertion. Tests run at density 1.
        val rowPaddingPx = PopupMetrics().rowHorizontalPadding.value
        assertTrue(
            countLong.right.value <= panelRight - rowPaddingPx + 0.5f,
            "the count must stay inside the panel's padded content area",
        )
        assertEquals(
            countLong.right.value,
            countShort.right.value,
            absoluteTolerance = 0.5f,
            "both counts must share the trailing position",
        )
    }

    @Test
    fun flyingRowSectionsInterpolateBetweenTheTwoHandoverLayouts() = runComposeUiTest {
        // The flying selected row is the only row whose layout morphs: at fraction 0 its count
        // sits right after its own label (the trigger's concatenated layout) and at fraction 1 at
        // the justified trailing position (row width minus the item's own count width). A static
        // layout would offset one of the two handovers for every item that is not the widest.
        var fraction by mutableStateOf(1f)

        setContent {
            Box(Modifier.width(300.dp)) {
                PopupRowContent(
                    item = PopupItem(
                        key = 0,
                        content = { Text("Lbl") },
                        count = { Text("9") },
                    ),
                    contentColor = Color.Black,
                    metrics = PopupMetrics(),
                    sections = RowSectionWidths(
                        contentWidthPx = 120f,
                        countWidthPx = 30f,
                        itemContentWidthsPx = listOf(48f),
                        itemCountWidthsPx = listOf(14f),
                    ),
                    layout = RowSectionLayout.Flying(
                        itemIndex = 0,
                        morphFraction = { fraction },
                    ),
                )
            }
        }

        fun countLeft() = onNodeWithText("9", useUnmergedTree = true).getUnclippedBoundsInRoot().left.value

        fraction = 0f
        waitForIdle()
        assertEquals(
            48f + 12f,
            countLeft(),
            absoluteTolerance = 0.5f,
            "fraction 0 must match the trigger's concatenated layout (own label + gap)",
        )

        fraction = 1f
        waitForIdle()
        assertEquals(
            300f - 14f,
            countLeft(),
            absoluteTolerance = 0.5f,
            "fraction 1 must match the justified list layout (row width - own count width)",
        )
    }

    @Test
    fun flyingRowClampsALabelWiderThanTheTrigger() = runComposeUiTest {
        // A label whose natural width exceeds the trigger is clamped by the trigger itself (count
        // pinned to the trailing edge). The flying interpolation must start from that clamped
        // width — starting from the natural width would send the count far outside the surface
        // mid-morph and pop it at the handover.
        var fraction by mutableStateOf(1f)

        setContent {
            Box(Modifier.width(300.dp)) {
                PopupRowContent(
                    item = PopupItem(
                        key = 0,
                        content = { Text("A long label") },
                        count = { Text("9") },
                    ),
                    contentColor = Color.Black,
                    metrics = PopupMetrics(),
                    sections = RowSectionWidths(
                        contentWidthPx = 200f,
                        countWidthPx = 30f,
                        itemContentWidthsPx = listOf(800f),
                        itemCountWidthsPx = listOf(14f),
                    ),
                    layout = RowSectionLayout.Flying(
                        itemIndex = 0,
                        morphFraction = { fraction },
                        collapsedContentWidthPx = 260f,
                    ),
                )
            }
        }

        fun countLeft() = onNodeWithText("9", useUnmergedTree = true).getUnclippedBoundsInRoot().left.value

        fraction = 0f
        waitForIdle()
        assertEquals(
            260f - 14f,
            countLeft(),
            absoluteTolerance = 0.5f,
            "fraction 0 must land on the trigger's clamped layout (count at the trailing edge)",
        )

        fraction = 1f
        waitForIdle()
        assertEquals(
            300f - 14f,
            countLeft(),
            absoluteTolerance = 0.5f,
            "fraction 1 must land on the justified list layout (count at the trailing edge)",
        )

        // Mid-morph the count must stay inside the row instead of excursioning out of the surface.
        fraction = 0.5f
        waitForIdle()
        val mid = countLeft()
        assertTrue(
            mid in 246f..287f,
            "mid-morph count must stay between the two trailing edges, was $mid",
        )
    }

    @Test
    fun anchorBoundsIgnoreWindowClipping() = runComposeUiTest {
        // A chip that sticks out of the window must be measured at its full layout bounds:
        // boundsInWindow() reports only the visible slice, so the morph would start and land on
        // that slice instead of the real chip shape.
        var measured by mutableStateOf<Rect?>(null)

        setContent {
            Box(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .offset(y = (-10).dp)
                        .size(width = 120.dp, height = 32.dp)
                        .trackAnchorBounds { measured = it },
                )
            }
        }

        waitForIdle()
        val rect = measured
        assertNotNull(rect)
        assertEquals(
            -10f,
            rect.top,
            absoluteTolerance = 0.5f,
            "the anchor must be measured at its full layout bounds even off-screen",
        )
        assertEquals(
            32f,
            rect.bottom - rect.top,
            absoluteTolerance = 0.5f,
            "the measured height must be the full chip height, not the visible slice",
        )
    }
}
