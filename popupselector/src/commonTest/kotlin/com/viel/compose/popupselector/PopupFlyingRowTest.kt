package com.viel.compose.popupselector

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Covers the selection's journey between the trigger and the panel.
 *
 * The flying row is what makes the selection look like it travels rather than cross-fading, so both
 * halves are pinned down here: where it starts and ends ([FlyingRowGeometry]), and which geometry
 * it is flown against while the panel is closing ([resolveActivePlacement]).
 */
class PopupFlyingRowTest {

    private val density = Density(density = 2f, fontScale = 1f)
    private val metrics = PopupMetrics()
    private val windowSize = IntSize(800, 1000)

    private val rowHeightPx = with(density) { metrics.rowTouchTargetHeight.toPx() }
    private val rowSpacingPx = with(density) { metrics.rowVerticalSpacing.toPx() }
    private val panelPaddingPx = with(density) { metrics.panelVerticalPadding.toPx() }
    private val rowHorizontalPaddingPx = with(density) { metrics.rowHorizontalPadding.toPx() }

    /** An anchor whose height matches the collapsed surface, so the content box is unambiguous. */
    private val collapsedHeightPx = with(density) { metrics.collapsedHeight.toPx() }
    private val anchor = Rect(100f, 200f, 300f, 200f + collapsedHeightPx)

    private fun placement(
        rowCount: Int,
        selectedIndex: Int?,
        maxHeight: Dp = Dp.Unspecified,
        verticalAlignment: PopupVerticalAlignment = PopupVerticalAlignment.Auto,
        horizontalAlignment: PopupHorizontalAlignment = PopupHorizontalAlignment.Auto,
    ): PanelPlacement = resolvePlacement(
        anchor = anchor,
        windowSize = windowSize,
        rowCount = rowCount,
        selectedIndex = selectedIndex,
        width = PopupWidth.MatchAnchor,
        maxHeight = maxHeight,
        verticalAlignment = verticalAlignment,
        horizontalAlignment = horizontalAlignment,
        metrics = metrics,
        layoutDirection = androidx.compose.ui.unit.LayoutDirection.Ltr,
        density = density,
    )

    private fun geometry(
        p: PanelPlacement,
        index: Int,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ): FlyingRowGeometry = FlyingRowGeometry.of(
        anchor = p.anchorRect,
        panelRect = p.panelRect,
        index = index,
        px = metrics.toPx(density),
        layoutDirection = layoutDirection,
    )

    private fun flyingRect(
        p: PanelPlacement,
        index: Int,
        fraction: Float,
        scrollOffsetPx: Float = p.initialScrollPx,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ): Rect = geometry(p, index, layoutDirection).contentRect(fraction, scrollOffsetPx)

    // region FlyingRowGeometry content rects

    @Test
    fun testAtRestItOccupiesTheCollapsedContentBox() {
        // The slot the selection occupies while the panel is closed: past the chevron, inside the
        // trailing padding, centred on the collapsed surface.
        val expected = Rect(
            left = anchor.left +
                with(density) { metrics.chevronLeadingPadding.toPx() } * 2f +
                with(density) { metrics.chevronSize.toPx() },
            top = anchor.top,
            right = anchor.right - with(density) { metrics.collapsedHorizontalPadding.toPx() },
            bottom = anchor.top + collapsedHeightPx,
        )

        val p = placement(rowCount = 4, selectedIndex = 1)
        assertEquals(expected, flyingRect(p, index = 1, fraction = 0f))
    }

    @Test
    fun testAtRestItIsIndependentOfTheSelectedIndex() {
        // Every row collapses to the same slot, so the landing point cannot drift with the choice.
        val p = placement(rowCount = 4, selectedIndex = 1)
        val forRow0 = flyingRect(p, index = 0, fraction = 0f)
        val forRow3 = flyingRect(p, index = 3, fraction = 0f)
        assertEquals(forRow0, forRow3)
    }

    @Test
    fun testAtRestItMirrorsWithTheTextDirection() {
        // In RTL the chevron sits on the right of the trigger, so the resting slot is the content
        // box on the other side: trailing padding on the left, chevron block on the right.
        val p = placement(rowCount = 4, selectedIndex = 1)
        val rtl = flyingRect(p, index = 1, fraction = 0f, layoutDirection = LayoutDirection.Rtl)

        val expected = Rect(
            left = anchor.left + with(density) { metrics.collapsedHorizontalPadding.toPx() },
            top = anchor.top,
            right = anchor.right -
                with(density) { metrics.chevronLeadingPadding.toPx() } * 2f -
                with(density) { metrics.chevronSize.toPx() },
            bottom = anchor.top + collapsedHeightPx,
        )
        assertEquals(expected, rtl)
    }

    @Test
    fun testTheHighlightContainerStartsOnTheWholeTrigger() {
        // The highlight travels with the selection, so its container starts as the entire
        // collapsed trigger — not the content box — and must not jump at the morph's first frame.
        val p = placement(rowCount = 4, selectedIndex = 1)
        val container = geometry(p, index = 1).containerRect(
            fraction = 0f,
            scrollOffsetPx = p.initialScrollPx,
        )
        assertEquals(p.anchorRect, container)
    }

    @Test
    fun testTheHighlightContainerLandsOnTheFullRowSlot() {
        // At handover the container must coincide with the list row's own highlight: full row
        // width, row height — while the content box stays inset by the row's horizontal padding.
        val p = placement(rowCount = 4, selectedIndex = 1)
        val container = geometry(p, index = 1).containerRect(
            fraction = 1f,
            scrollOffsetPx = p.initialScrollPx,
        )
        val slot = rowRectAt(p, 1)

        assertEquals(slot.left, container.left)
        assertEquals(slot.right, container.right)
        assertEquals(slot.top, container.top)
        assertEquals(slot.top + rowHeightPx, container.bottom)

        val content = flyingRect(p, index = 1, fraction = 1f)
        assertEquals(container.left + rowHorizontalPaddingPx, content.left)
        assertEquals(container.right - rowHorizontalPaddingPx, content.right)
    }

    @Test
    fun testFullyExpandedItOccupiesTheRowSlot() {
        val p = placement(rowCount = 4, selectedIndex = 1)
        val rect = flyingRect(p, index = 1, fraction = 1f)

        assertEquals(rowRectAt(p, 1).top, rect.top)
        assertEquals(rowRectAt(p, 1).top + rowHeightPx, rect.bottom)
        assertEquals(p.panelRect.left + rowHorizontalPaddingPx, rect.left)
        assertEquals(p.panelRect.right - rowHorizontalPaddingPx, rect.right)
    }

    private fun rowRectAt(p: PanelPlacement, index: Int): Rect = rowRectAt(
        panelRect = p.panelRect,
        index = index,
        rowHeightPx = rowHeightPx,
        rowSpacingPx = rowSpacingPx,
        panelPaddingPx = panelPaddingPx,
        scrollOffsetPx = p.initialScrollPx,
    )

    @Test
    fun testItTravelsBetweenTheTwoSlots() {
        // An explicit side keeps the panel strictly below the trigger, so the descent is
        // deterministic; Auto with a selection would centre the row on the trigger instead.
        val p = placement(rowCount = 4, selectedIndex = 1, verticalAlignment = PopupVerticalAlignment.Top)
        val start = flyingRect(p, index = 1, fraction = 0f)
        val end = flyingRect(p, index = 1, fraction = 1f)

        // The panel opens below the trigger, so the row must descend monotonically.
        assertTrue(end.top > start.top, "expected the row to travel down into the panel")

        var previousTop = start.top
        for (step in 1..10) {
            val top = flyingRect(p, index = 1, fraction = step / 10f).top
            assertTrue(top >= previousTop - 0.5f, "row moved backwards at step $step")
            previousTop = top
        }
    }

    @Test
    fun testItFollowsTheCurrentScrollNotTheResolvedOne() {
        // A scrollable list: if the user scrolls before dismissing, the row has to start from where
        // it is actually drawn, not from the offset resolved when the panel was placed.
        val p = placement(
            rowCount = 20,
            selectedIndex = 10,
            maxHeight = 400.dp,
            verticalAlignment = PopupVerticalAlignment.Auto,
        )
        assertTrue(p.maxScrollPx > 0f)
        assertTrue(p.initialScrollPx > 40f)

        // Both offsets keep the row inside the viewport, so no clamping masks the difference.
        val atResolvedScroll = flyingRect(p, index = 10, fraction = 1f)
        val afterScrollingUp = flyingRect(
            p,
            index = 10,
            fraction = 1f,
            scrollOffsetPx = p.initialScrollPx - 40f,
        )

        assertEquals(40f, afterScrollingUp.top - atResolvedScroll.top, absoluteTolerance = 0.5f)
    }

    @Test
    fun testItStaysInsideTheMorphingSurface() {
        // The surface clips its children; a row that escapes would be cut off mid-flight.
        val p = placement(rowCount = 8, selectedIndex = 4)
        for (step in 0..10) {
            val f = step / 10f
            val rect = flyingRect(p, index = 4, fraction = f)
            val surface = lerpRect(p.anchorRect, p.panelRect, f)
            assertTrue(rect.left >= surface.left - 0.5f, "escaped left at f=$f")
            assertTrue(rect.right <= surface.right + 0.5f, "escaped right at f=$f")
            assertTrue(rect.top >= surface.top - 0.5f, "escaped top at f=$f")
            assertTrue(rect.bottom <= surface.bottom + 0.5f, "escaped bottom at f=$f")
        }
    }

    @Test
    fun testAnOffScreenRowIsClampedIntoThePanel() {
        // A row outside the viewport cannot be flown from its true position; it degrades to the
        // nearest visible edge rather than flying in from outside the surface.
        val p = placement(rowCount = 20, selectedIndex = 19, maxHeight = 200.dp)
        val rect = flyingRect(p, index = 19, fraction = 1f, scrollOffsetPx = 0f)

        assertTrue(rect.top >= p.panelRect.top + panelPaddingPx - 0.5f)
        assertTrue(rect.bottom <= p.panelRect.bottom - panelPaddingPx + 0.5f)
    }

    // endregion

    // region resolveActivePlacement

    @Test
    fun testOpenPanelAlwaysUsesTheLiveGeometry() {
        val open = placement(rowCount = 4, selectedIndex = 1)
        val stale = placement(rowCount = 4, selectedIndex = 3)
        assertSame(open, resolveActivePlacement(frozen = stale, expanded = true, placement = open))
    }

    @Test
    fun testCollapsingPanelHoldsTheLastExpandedGeometry() {
        // Picking an option re-resolves the placement around that option; letting the panel adopt it
        // mid-collapse would make it jump instead of shrinking into the trigger. Only the anchor
        // rectangle is exempt — see testCollapsingPanelTracksTheLiveAnchorSize.
        val openOnRow1 = placement(rowCount = 4, selectedIndex = 1)
        val centredOnRow3 = placement(rowCount = 4, selectedIndex = 3)

        val held = resolveActivePlacement(
            frozen = openOnRow1,
            expanded = false,
            placement = centredOnRow3,
        )

        assertEquals(openOnRow1.panelRect, held.panelRect)
        assertEquals(openOnRow1.rowRects, held.rowRects)
        assertEquals(openOnRow1.opensDown, held.opensDown)
        assertEquals(openOnRow1.opensRight, held.opensRight)
        assertEquals(openOnRow1.initialScrollPx, held.initialScrollPx)
    }

    @Test
    fun testCollapsingPanelTracksTheLiveAnchorSize() {
        // The regression for the collapse size jump: selecting a different item re-measures the
        // trigger to the new content's width, and the morph must land on that measured rectangle.
        // Holding the frozen anchor rect would make the surface land at the old width while the
        // already-resized trigger pops into view.
        val wideAnchor = Rect(100f, 200f, 500f, 200f + collapsedHeightPx)
        val openOnWideAnchor = resolvePlacement(
            anchor = wideAnchor,
            windowSize = windowSize,
            rowCount = 4,
            selectedIndex = 1,
            width = PopupWidth.MatchAnchor,
            maxHeight = Dp.Unspecified,
            verticalAlignment = PopupVerticalAlignment.Auto,
                horizontalAlignment = PopupHorizontalAlignment.Auto,
            metrics = metrics,
            layoutDirection = androidx.compose.ui.unit.LayoutDirection.Ltr,
            density = density,
        )
        val liveOnNarrowAnchor = placement(rowCount = 4, selectedIndex = 3)

        val held = resolveActivePlacement(
            frozen = openOnWideAnchor,
            expanded = false,
            placement = liveOnNarrowAnchor,
        )

        assertEquals(liveOnNarrowAnchor.anchorRect, held.anchorRect)
        assertEquals(openOnWideAnchor.panelRect, held.panelRect)
    }

    @Test
    fun testCollapsingWithoutAFrozenGeometryFallsBackToTheLiveOne() {
        // No expanded frame has been rendered yet, so there is nothing to hold on to.
        val p = placement(rowCount = 4, selectedIndex = 1)
        assertSame(p, resolveActivePlacement(frozen = null, expanded = false, placement = p))
    }

    // endregion

    // region the collapse as a whole

    @Test
    fun testNewlySelectedItemLandsBackOnTheTrigger() {
        // The regression this file exists for: pick option 3 while option 1 was selected, and the
        // newly chosen item must still start at the row that was clicked and end on the trigger's
        // content box — using the panel geometry it was clicked in.
        val openOnRow1 = placement(rowCount = 4, selectedIndex = 1)
        val centredOnRow3 = placement(rowCount = 4, selectedIndex = 3)

        val held = resolveActivePlacement(
            frozen = openOnRow1,
            expanded = false,
            placement = centredOnRow3,
        )

        // Ends where the selection rests when the panel is closed.
        assertEquals(
            flyingRect(openOnRow1, index = 1, fraction = 0f),
            flyingRect(held, index = 3, fraction = 0f),
        )
        // Starts at the row the user actually clicked, inside the held panel.
        assertEquals(
            rowRectAt(openOnRow1, 3).top,
            flyingRect(held, index = 3, fraction = 1f).top,
            absoluteTolerance = 0.5f,
        )
    }

    // endregion

    // region the flying row's content colour

    private fun colorStyle(content: Color, activeContent: Color?) = PopupStyle(
        colors = if (activeContent == null) {
            PopupColors(
                collapsedContainer = Color.White,
                expandedContainer = Color.White,
                border = Color.Transparent,
                content = content,
            )
        } else {
            PopupColors(
                collapsedContainer = Color.White,
                expandedContainer = Color.White,
                border = Color.Transparent,
                content = content,
                activeContent = activeContent,
            )
        },
    )

    @Test
    fun testFlyingRowContentColorStartsAtTheTriggerColourAndEndsActive() {
        // Handover seamlessness: at fraction 0 the flying row replaces the hidden anchor, so its
        // label must carry exactly the resting trigger's `content` colour; at fraction 1 it has
        // fully become the panel's active colour.
        val style = colorStyle(content = Color(0xFF111111), activeContent = Color(0xFFFF00FF))

        assertEquals(Color(0xFF111111), flyingRowContentColor(style, 0f))
        assertEquals(Color(0xFFFF00FF), flyingRowContentColor(style, 1f))
    }

    @Test
    fun testFlyingRowContentColorBlendsThroughTheMorph() {
        // Mid-morph the colour sits strictly between the two endpoints and moves monotonically
        // with the fraction. Compose lerps colours in Oklab, so the midpoint's sRGB channels are
        // not the naive average — the perceptual path is the point, and it is the same path the
        // surface's container colours already follow.
        val style = colorStyle(content = Color(0xFF000000), activeContent = Color(0xFFFFFFFF))

        val quarter = flyingRowContentColor(style, 0.25f)
        val half = flyingRowContentColor(style, 0.5f)
        val threeQuarters = flyingRowContentColor(style, 0.75f)

        assertTrue(
            quarter.red in 0f..half.red && half.red in quarter.red..threeQuarters.red &&
                threeQuarters.red in half.red..1f,
            "the colour must blend monotonically between the endpoints " +
                "($quarter, $half, $threeQuarters)",
        )
        assertTrue(half != style.colors.content && half != style.colors.activeContent)
    }

    @Test
    fun testFlyingRowContentColorIgnoresTheMorphWithoutAnActiveColour() {
        // A palette without an active colour falls back to `content` at every fraction, so its
        // selected label keeps the pre-active look throughout the morph.
        val style = colorStyle(content = Color(0xFF111111), activeContent = null)

        assertEquals(Color(0xFF111111), flyingRowContentColor(style, 0f))
        assertEquals(Color(0xFF111111), flyingRowContentColor(style, 0.5f))
        assertEquals(Color(0xFF111111), flyingRowContentColor(style, 1f))
    }

    // endregion
}
