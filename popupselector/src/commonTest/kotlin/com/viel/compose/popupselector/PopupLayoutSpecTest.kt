package com.viel.compose.popupselector

import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PopupLayoutSpecTest {

    private val density = Density(density = 2f, fontScale = 1f)
    private val metrics = PopupMetrics()
    private val windowSize = IntSize(800, 1000)

    // Note: the placements default to Auto here, not to the library defaults (Centered + Start),
    // so that existing cases keep exercising corner placement explicitly. Tests that cover the
    // defaults read them from PopupLayout() instead.
    private fun placement(
        anchor: Rect,
        rowCount: Int = 4,
        selectedIndex: Int? = null,
        width: PopupWidth = PopupWidth.MatchAnchor,
        maxHeight: Dp = Dp.Unspecified,
        verticalAlignment: PopupVerticalAlignment = PopupVerticalAlignment.Auto,
        horizontalAlignment: PopupHorizontalAlignment = PopupHorizontalAlignment.Auto,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        measuredSections: RowSectionWidths? = null,
        window: IntSize = windowSize,
    ): PanelPlacement = resolvePlacement(
        anchor = anchor,
        windowSize = window,
        rowCount = rowCount,
        selectedIndex = selectedIndex,
        width = width,
        maxHeight = maxHeight,
        verticalAlignment = verticalAlignment,
        horizontalAlignment = horizontalAlignment,
        metrics = metrics,
        layoutDirection = layoutDirection,
        density = density,
        measuredSections = measuredSections,
    )

    // region lerp

    @Test
    fun testLerp() {
        // The interpolation every geometry helper builds on; the androidx util is the single
        // definition the library relies on.
        assertEquals(0f, lerp(0f, 100f, 0f))
        assertEquals(50f, lerp(0f, 100f, 0.5f))
        assertEquals(100f, lerp(0f, 100f, 1f))
    }

    @Test
    fun testShadowFractionStaysZeroUntilTheStart() {
        // A low-elevation shadow around a transparent surface reads as a dark rim, so the shadow
        // must be fully off for the first part of the morph.
        assertEquals(0f, shadowFraction(fraction = 0f, startFraction = 0.5f))
        assertEquals(0f, shadowFraction(fraction = 0.5f, startFraction = 0.5f))
        assertEquals(0f, shadowFraction(fraction = 0.2f, startFraction = 0.5f))
    }

    @Test
    fun testShadowFractionRescalesTheRemainingProgress() {
        assertEquals(0.5f, shadowFraction(fraction = 0.75f, startFraction = 0.5f))
        assertEquals(1f, shadowFraction(fraction = 1f, startFraction = 0.5f))
    }

    @Test
    fun testShadowFractionStartZeroRampsAcrossTheWholeMorph() {
        // Opting out of the delay restores a shadow over the full morph.
        assertEquals(0.25f, shadowFraction(fraction = 0.25f, startFraction = 0f))
        assertEquals(1f, shadowFraction(fraction = 1f, startFraction = 0f))
    }

    @Test
    fun testShadowFractionIsSafeWhenTheStartReachesOne() {
        assertEquals(0f, shadowFraction(fraction = 1f, startFraction = 1f))
    }

    @Test
    fun testLerpRect() {
        val start = Rect(10f, 20f, 110f, 60f)
        val stop = Rect(50f, 80f, 250f, 180f)

        val mid = lerpRect(start, stop, 0.5f)
        assertEquals(30f, mid.left)
        assertEquals(50f, mid.top)
        assertEquals(180f, mid.right)
        assertEquals(120f, mid.bottom)
    }

    // endregion

    // region resolveVerticalOpenDown

    @Test
    fun testVerticalAutoOpensDownWhenSpaceAvailable() {
        val opensDown = resolveVerticalOpenDown(
            vertical = PopupVerticalAlignment.Auto,
            anchor = Rect(100f, 100f, 200f, 140f),
            panelHeightPx = 200f,
            windowHeightPx = 800f,
            topMarginPx = 16f,
            bottomMarginPx = 16f,
        )
        assertTrue(opensDown)
    }

    @Test
    fun testVerticalAutoFlipsUpWhenBottomSpaceInsufficient() {
        val opensDown = resolveVerticalOpenDown(
            vertical = PopupVerticalAlignment.Auto,
            anchor = Rect(100f, 700f, 200f, 740f),
            panelHeightPx = 200f,
            windowHeightPx = 800f,
            topMarginPx = 16f,
            bottomMarginPx = 16f,
        )
        assertFalse(opensDown)
    }

    @Test
    fun testVerticalExplicitSidesAreHonoured() {
        val anchor = Rect(100f, 100f, 200f, 140f)
        assertTrue(
            resolveVerticalOpenDown(PopupVerticalAlignment.Top, anchor, 100f, 500f, 10f, 10f),
        )
        assertFalse(
            resolveVerticalOpenDown(PopupVerticalAlignment.Bottom, anchor, 100f, 500f, 10f, 10f),
        )
    }

    // endregion

    // region resolveHorizontalOpensRight

    @Test
    fun testHorizontalAutoFlipsLeftWhenRightSpaceInsufficient() {
        val opensRight = resolveHorizontalOpensRight(
            horizontal = PopupHorizontalAlignment.Auto,
            anchor = Rect(500f, 100f, 580f, 140f),
            panelWidthPx = 200f,
            windowWidthPx = 600f,
            edgeMarginPx = 16f,
            layoutDirection = LayoutDirection.Ltr,
        )
        assertFalse(opensRight)
    }

    @Test
    fun testHorizontalAutoFallsBackToTheLeadingSideWhenBothFit() {
        val anchor = Rect(100f, 100f, 200f, 140f)
        assertTrue(
            resolveHorizontalOpensRight(
                PopupHorizontalAlignment.Auto, anchor, 100f, 500f, 10f, LayoutDirection.Ltr,
            ),
        )
        assertFalse(
            resolveHorizontalOpensRight(
                PopupHorizontalAlignment.Auto, anchor, 100f, 500f, 10f, LayoutDirection.Rtl,
            ),
        )
    }

    @Test
    fun testHorizontalExplicitSidesAndDirectionAwareness() {
        val anchor = Rect(100f, 100f, 200f, 140f)
        fun resolve(horizontal: PopupHorizontalAlignment, isRtl: Boolean) =
            resolveHorizontalOpensRight(
                horizontal = horizontal,
                anchor = anchor,
                panelWidthPx = 100f,
                windowWidthPx = 500f,
                edgeMarginPx = 10f,
                layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            )

        assertTrue(resolve(PopupHorizontalAlignment.Start, isRtl = false))
        assertFalse(resolve(PopupHorizontalAlignment.Start, isRtl = true))
        assertFalse(resolve(PopupHorizontalAlignment.End, isRtl = false))
        assertTrue(resolve(PopupHorizontalAlignment.End, isRtl = true))
    }

    // endregion

    // region ambient alignment decomposition

    @Test
    fun testAmbientAlignmentDecomposesIntoBothAxes() {
        fun components(alignment: Alignment) = alignment.toPopupVerticalAlignment() to
            alignment.toPopupHorizontalAlignment(LayoutDirection.Ltr)

        assertEquals(
            PopupVerticalAlignment.Top to PopupHorizontalAlignment.Start,
            components(Alignment.TopStart),
        )
        assertEquals(
            PopupVerticalAlignment.Top to PopupHorizontalAlignment.Auto,
            components(Alignment.TopCenter),
        )
        assertEquals(
            PopupVerticalAlignment.Top to PopupHorizontalAlignment.End,
            components(Alignment.TopEnd),
        )
        assertEquals(
            PopupVerticalAlignment.Auto to PopupHorizontalAlignment.Start,
            components(Alignment.CenterStart),
        )
        assertEquals(
            PopupVerticalAlignment.Auto to PopupHorizontalAlignment.Auto,
            components(Alignment.Center),
        )
        assertEquals(
            PopupVerticalAlignment.Auto to PopupHorizontalAlignment.End,
            components(Alignment.CenterEnd),
        )
        assertEquals(
            PopupVerticalAlignment.Bottom to PopupHorizontalAlignment.Start,
            components(Alignment.BottomStart),
        )
        assertEquals(
            PopupVerticalAlignment.Bottom to PopupHorizontalAlignment.Auto,
            components(Alignment.BottomCenter),
        )
        assertEquals(
            PopupVerticalAlignment.Bottom to PopupHorizontalAlignment.End,
            components(Alignment.BottomEnd),
        )
    }

    @Test
    fun testAmbientAlignmentDecompositionMirrorsWithTheLayoutDirection() {
        // Compose's Alignment vocabulary is direction-aware (Start/End), so decomposition mirrors:
        // the same instance resolves to the opposite popup side under RTL.
        assertEquals(
            PopupHorizontalAlignment.End,
            Alignment.CenterEnd.toPopupHorizontalAlignment(LayoutDirection.Rtl),
        )
        assertEquals(
            PopupHorizontalAlignment.Start,
            Alignment.CenterStart.toPopupHorizontalAlignment(LayoutDirection.Rtl),
        )
    }

    @Test
    fun testCustomAmbientAlignmentDecomposesByResolvedPosition() {
        // Alignment is a fun interface; a custom implementation is decomposed by where its probe
        // lands rather than by identity.
        val custom = Alignment { _, space, _ -> IntOffset(space.width, space.height / 2) }
        assertEquals(PopupVerticalAlignment.Auto, custom.toPopupVerticalAlignment())
        assertEquals(PopupHorizontalAlignment.End, custom.toPopupHorizontalAlignment(LayoutDirection.Ltr))
    }

    // endregion

    // region resolvePanelRect

    @Test
    fun testResolvePanelRectPlacementAndClamping() {
        val anchor = Rect(100f, 150f, 250f, 190f)

        val rectTopLeft = resolvePanelRect(
            anchor = anchor,
            opensDown = true,
            opensRight = true,
            panelWidthPx = 200f,
            panelHeightPx = 300f,
            windowWidthPx = 800f,
            windowHeightPx = 1000f,
            edgeMarginPx = 16f,
        )
        assertEquals(100f, rectTopLeft.left)
        assertEquals(150f, rectTopLeft.top)
        assertEquals(300f, rectTopLeft.right)
        assertEquals(450f, rectTopLeft.bottom)

    }

    @Test
    fun testResolvePanelRectOpensUpFromAnchorBottom() {
        // Anchor low enough that opening upward keeps the panel inside the safe area.
        val anchor = Rect(100f, 500f, 250f, 540f)
        val rect = resolvePanelRect(
            anchor = anchor,
            opensDown = false,
            opensRight = true,
            panelWidthPx = 200f,
            panelHeightPx = 300f,
            windowWidthPx = 800f,
            windowHeightPx = 1000f,
            edgeMarginPx = 16f,
        )
        assertEquals(100f, rect.left)
        assertEquals(300f, rect.right)
        assertEquals(540f - 300f, rect.top)
        assertEquals(540f, rect.bottom)
    }

    @Test
    fun testResolvePanelRectOpensUpClampedWhenAnchorTooHigh() {
        // Opening upward from a high anchor would exit the top edge, so it is clamped.
        val rect = resolvePanelRect(
            anchor = Rect(100f, 150f, 250f, 190f),
            opensDown = false,
            opensRight = true,
            panelWidthPx = 200f,
            panelHeightPx = 300f,
            windowWidthPx = 800f,
            windowHeightPx = 1000f,
            edgeMarginPx = 16f,
        )
        assertEquals(100f, rect.left)
        assertEquals(300f, rect.right)
        assertEquals(16f, rect.top)
        assertEquals(316f, rect.bottom)
    }

    @Test
    fun testResolvePanelRectRightEdgeClamping() {
        val rectClamped = resolvePanelRect(
            anchor = Rect(700f, 150f, 780f, 190f),
            opensDown = true,
            opensRight = true,
            panelWidthPx = 200f,
            panelHeightPx = 300f,
            windowWidthPx = 800f,
            windowHeightPx = 1000f,
            edgeMarginPx = 16f,
        )
        val expectedMaxLeft = 800f - 16f - 200f
        assertEquals(expectedMaxLeft, rectClamped.left)
        assertEquals(expectedMaxLeft + 200f, rectClamped.right)
    }

    @Test
    fun testResolvePanelRectOversizedWidthClamping() {
        val rectOversized = resolvePanelRect(
            anchor = Rect(100f, 150f, 250f, 190f),
            opensDown = true,
            opensRight = true,
            panelWidthPx = 9999f,
            panelHeightPx = 300f,
            windowWidthPx = 800f,
            windowHeightPx = 1000f,
            edgeMarginPx = 16f,
        )
        assertEquals(16f, rectOversized.left)
        assertEquals(800f - 16f, rectOversized.right)
        assertEquals(800f - 32f, rectOversized.width)
    }

    @Test
    fun testResolvePanelRectPanelWiderThanWindowDegradesToLeftEdge() {
        // maxLeft < edgeMargin: the coerceIn branch must be skipped, not throw.
        val rect = resolvePanelRect(
            anchor = Rect(100f, 150f, 250f, 190f),
            opensDown = true,
            opensRight = true,
            panelWidthPx = 900f,
            panelHeightPx = 300f,
            windowWidthPx = 800f,
            windowHeightPx = 1000f,
            edgeMarginPx = 16f,
        )
        assertEquals(16f, rect.left)
        assertTrue(rect.right <= 800f - 16f)
    }

    @Test
    fun testResolvePanelRectVerticalClampingWhenAnchorNearBottomEdge() {
        // Regression: an anchor inside the edge margin must not push the panel past the safe area.
        val rect = resolvePanelRect(
            anchor = Rect(100f, 960f, 200f, 990f),
            opensDown = false,
            opensRight = true,
            panelWidthPx = 200f,
            panelHeightPx = 300f,
            windowWidthPx = 800f,
            windowHeightPx = 1000f,
            edgeMarginPx = 16f,
        )
        assertTrue(rect.bottom <= 1000f - 16f + 0.5f)
        assertTrue(rect.top >= 16f - 0.5f)
    }

    // endregion

    // region resolvePanelRectCentered

    @Test
    fun testResolvePanelRectCenteredVerticalAlignment() {
        val anchor = Rect(100f, 200f, 250f, 240f)

        // Row 0 centre within the panel = 8 + 0 + 20 = 28; top = 220 - 28 = 192.
        val rect0 = resolvePanelRectCentered(
            anchor = anchor,
            panelWidthPx = 150f,
            panelHeightPx = 200f,
            windowWidthPx = 800f,
            windowHeightPx = 1000f,
            edgeMarginPx = 16f,
            selectedRowCenterInViewportPx = 28f,
            opensRight = true,
        )
        assertEquals(192f, rect0.top)
        assertEquals(392f, rect0.bottom)
        assertEquals(100f, rect0.left)
        assertEquals(250f, rect0.right)

        // Row 2 centre = 8 + 2 * 44 + 20 = 116; top = 220 - 116 = 104.
        val rect2 = resolvePanelRectCentered(
            anchor = anchor,
            panelWidthPx = 150f,
            panelHeightPx = 200f,
            windowWidthPx = 800f,
            windowHeightPx = 1000f,
            edgeMarginPx = 16f,
            selectedRowCenterInViewportPx = 116f,
            opensRight = true,
        )
        assertEquals(104f, rect2.top)
        assertEquals(304f, rect2.bottom)
    }

    @Test
    fun testResolvePanelRectCenteredVerticalClamping() {
        val rectClampedTop = resolvePanelRectCentered(
            anchor = Rect(100f, 20f, 250f, 60f),
            panelWidthPx = 150f,
            panelHeightPx = 200f,
            windowWidthPx = 800f,
            windowHeightPx = 1000f,
            edgeMarginPx = 16f,
            selectedRowCenterInViewportPx = 116f,
            opensRight = true,
        )
        assertEquals(16f, rectClampedTop.top)
        assertEquals(216f, rectClampedTop.bottom)

        val rectClampedBottom = resolvePanelRectCentered(
            anchor = Rect(100f, 960f, 250f, 1000f),
            panelWidthPx = 150f,
            panelHeightPx = 200f,
            windowWidthPx = 800f,
            windowHeightPx = 1000f,
            edgeMarginPx = 16f,
            selectedRowCenterInViewportPx = 28f,
            opensRight = true,
        )
        assertEquals(784f, rectClampedBottom.top)
        assertEquals(984f, rectClampedBottom.bottom)
    }

    @Test
    fun testResolvePanelRectCenteredHorizontalFollowsOpensRight() {
        val anchor = Rect(100f, 200f, 250f, 240f)

        val rectLtr = resolvePanelRectCentered(
            anchor = anchor,
            panelWidthPx = 180f,
            panelHeightPx = 200f,
            windowWidthPx = 800f,
            windowHeightPx = 1000f,
            edgeMarginPx = 16f,
            selectedRowCenterInViewportPx = 28f,
            opensRight = true,
        )
        assertEquals(100f, rectLtr.left)
        assertEquals(280f, rectLtr.right)

        val rectRtl = resolvePanelRectCentered(
            anchor = anchor,
            panelWidthPx = 180f,
            panelHeightPx = 200f,
            windowWidthPx = 800f,
            windowHeightPx = 1000f,
            edgeMarginPx = 16f,
            selectedRowCenterInViewportPx = 28f,
            opensRight = false,
        )
        assertEquals(70f, rectRtl.left)
        assertEquals(250f, rectRtl.right)
    }

    // endregion

    @Test
    fun testPlacementAutoCentringHonoursTheHorizontalPlacement() {
        // Centered governs the vertical axis only; the horizontal placement still picks the side.
        val anchor = Rect(100f, 500f, 300f, 540f)
        val end = placement(
            anchor = anchor,
            rowCount = 3,
            selectedIndex = 1,
            verticalAlignment = PopupVerticalAlignment.Auto,
            horizontalAlignment = PopupHorizontalAlignment.End,
        )
        assertEquals(anchor.right, end.panelRect.right)

        val start = placement(
            anchor = anchor,
            rowCount = 3,
            selectedIndex = 1,
            verticalAlignment = PopupVerticalAlignment.Auto,
            horizontalAlignment = PopupHorizontalAlignment.Start,
        )
        assertEquals(anchor.left, start.panelRect.left)

        // The vertical centring contract must hold in both cases.
        for (result in listOf(end, start)) {
            val rowCenter = result.rowRects[1].top + result.rowRects[1].height / 2f
            val anchorCenter = (anchor.top + anchor.bottom) / 2f
            assertEquals(anchorCenter, rowCenter, absoluteTolerance = 0.5f)
        }
    }

    // region resolvePlacement

    @Test
    fun testPlacementRowCountMatchesRowRects() {
        val result = placement(anchor = Rect(100f, 200f, 300f, 240f), rowCount = 5)
        assertEquals(5, result.rowRects.size)
    }

    @Test
    fun testPlacementZeroRowsProducesEmptyLayout() {
        val result = placement(anchor = Rect(100f, 200f, 300f, 240f), rowCount = 0)
        assertTrue(result.rowRects.isEmpty())
        assertEquals(0f, result.maxScrollPx)
    }

    @Test
    fun testPlacementPanelRespectsMaxHeight() {
        val anchor = Rect(100f, 100f, 300f, 140f)
        val unconstrained = placement(anchor = anchor, rowCount = 20)
        val constrained = placement(anchor = anchor, rowCount = 20, maxHeight = 120.dp)

        assertTrue(constrained.panelRect.height <= with(density) { 120.dp.toPx() } + 0.5f)
        assertTrue(constrained.panelRect.height < unconstrained.panelRect.height)
        assertTrue(constrained.maxScrollPx > 0f)
    }

    @Test
    fun testPlacementMaxHeightDefaultUsesWindowFraction() {
        val result = placement(anchor = Rect(100f, 100f, 300f, 140f), rowCount = 100)
        val expected = windowSize.height * metrics.maxHeightFraction
        assertTrue(result.panelRect.height <= expected + 0.5f)
    }

    @Test
    fun testPlacementMatchAnchorUsesAnchorWidth() {
        val anchor = Rect(100f, 200f, 340f, 240f)
        val result = placement(anchor = anchor, width = PopupWidth.MatchAnchor)
        assertEquals(anchor.width, result.panelRect.width)
    }

    @Test
    fun testPlacementFixedWidthUsesRequestedWidth() {
        val result = placement(
            anchor = Rect(100f, 200f, 300f, 240f),
            width = PopupWidth.Fixed(280.dp),
        )
        assertEquals(with(density) { 280.dp.toPx() }, result.panelRect.width)
    }

    @Test
    fun testPlacementWrapSumsTheSectionMaxima() {
        // With aligned sections the panel width is the sum of the per-section maxima — not the
        // widest row — so the shared count column can start at the same offset in every row.
        val sections = RowSectionWidths(contentWidthPx = 200f, countWidthPx = 100f)
        val result = placement(
            anchor = Rect(100f, 200f, 300f, 240f),
            width = PopupWidth.Wrap,
            measuredSections = sections,
        )
        val expected = sections.totalWidthPx(
            rowHorizontalPaddingPx = with(density) { metrics.rowHorizontalPadding.toPx() },
            countSpacingPx = with(density) { metrics.countSpacing.toPx() },
        )
        assertEquals(expected, result.panelRect.width)
    }

    @Test
    fun testPlacementWrapWithoutCountsOmitsTheCountSection() {
        val sections = RowSectionWidths(contentWidthPx = 200f, countWidthPx = 0f)
        val result = placement(
            anchor = Rect(100f, 200f, 300f, 240f),
            width = PopupWidth.Wrap,
            measuredSections = sections,
        )
        // Only padding twice plus the content section: no gap is reserved for an empty column.
        val expected = with(density) { metrics.rowHorizontalPadding.toPx() } * 2f + 200f
        assertEquals(expected, result.panelRect.width)
    }

    @Test
    fun testPlacementWrapNeverNarrowerThanAnchor() {
        val anchor = Rect(100f, 200f, 300f, 240f)
        val result = placement(
            anchor = anchor,
            width = PopupWidth.Wrap,
            measuredSections = RowSectionWidths(contentWidthPx = 20f, countWidthPx = 10f),
        )
        assertEquals(anchor.width, result.panelRect.width)
    }

    @Test
    fun testPlacementWrapClampsToWindowWidth() {
        val result = placement(
            anchor = Rect(100f, 200f, 300f, 240f),
            width = PopupWidth.Wrap,
            measuredSections = RowSectionWidths(contentWidthPx = 4000f, countWidthPx = 900f),
        )
        val maxWidth = windowSize.width - with(density) { metrics.edgeMargin.toPx() } * 2f
        assertEquals(maxWidth, result.panelRect.width)
    }

    @Test
    fun testPlacementPanelStaysInsideWindow() {
        val edgeMarginPx = with(density) { metrics.edgeMargin.toPx() }
        val result = placement(
            anchor = Rect(760f, 960f, 790f, 990f),
            rowCount = 12,
            width = PopupWidth.Fixed(300.dp),
        )
        assertTrue(result.panelRect.left >= edgeMarginPx - 0.5f)
        assertTrue(result.panelRect.right <= windowSize.width - edgeMarginPx + 0.5f)
        assertTrue(result.panelRect.top >= edgeMarginPx - 0.5f)
        assertTrue(result.panelRect.bottom <= windowSize.height - edgeMarginPx + 0.5f)
    }

    @Test
    fun testSafeDrawingInsetsApplyPerSideWithoutAnyConfiguration() {
        // Cross-platform safe-drawing insets are honoured automatically, per side and without any
        // configuration: a large top inset (status bar) pushes the panel down, a large bottom
        // inset (navigation bar) keeps it above the bar — each side clamps independently.
        fun placement(anchor: Rect): PanelPlacement = resolvePlacement(
            anchor = anchor,
            windowSize = windowSize,
            rowCount = 4,
            selectedIndex = null,
            width = PopupWidth.Fixed(240.dp),
            maxHeight = Dp.Unspecified,
            verticalAlignment = PopupVerticalAlignment.Auto,
            horizontalAlignment = PopupHorizontalAlignment.Auto,
            metrics = metrics,
            layoutDirection = LayoutDirection.Ltr,
            density = density,
            safeDrawingTopPx = 100f,
            safeDrawingBottomPx = 40f,
        )

        val fromTop = placement(Rect(20f, 30f, 320f, 62f))
        assertEquals(
            100f,
            fromTop.panelRect.top,
            absoluteTolerance = 0.5f,
            "the panel must clear the top safe-drawing inset",
        )

        val fromBottom = placement(Rect(20f, 940f, 320f, 990f))
        assertEquals(
            windowSize.height - 40f,
            fromBottom.panelRect.bottom,
            absoluteTolerance = 0.5f,
            "the panel must clear the bottom safe-drawing inset",
        )
    }

    @Test
    fun testTheEdgeMarginIsTheFloorForTheSafeDrawingInsets() {
        // Where the insets are smaller than the configured margin (or zero, as on desktop), the
        // margin still applies as the floor — small insets never pull the panel closer.
        val result = resolvePlacement(
            anchor = Rect(20f, 4f, 320f, 36f),
            windowSize = windowSize,
            rowCount = 4,
            selectedIndex = null,
            width = PopupWidth.Fixed(240.dp),
            maxHeight = Dp.Unspecified,
            verticalAlignment = PopupVerticalAlignment.Auto,
            horizontalAlignment = PopupHorizontalAlignment.Auto,
            metrics = metrics,
            layoutDirection = LayoutDirection.Ltr,
            density = density,
            safeDrawingTopPx = 4f,
            safeDrawingBottomPx = 0f,
        )
        assertEquals(
            with(density) { metrics.edgeMargin.toPx() },
            result.panelRect.top,
            absoluteTolerance = 0.5f,
            "the plain edge margin must remain the floor when the insets are smaller",
        )
    }

    @Test
    fun testPlacementExplicitAlignmentHonouredWithoutSelection() {
        val anchor = Rect(100f, 400f, 300f, 440f)
        val result = placement(
            anchor = anchor,
            rowCount = 3,
            verticalAlignment = PopupVerticalAlignment.Bottom,
            horizontalAlignment = PopupHorizontalAlignment.End,
        )
        // End in Ltr means: opens upward, right edge aligned to anchor.right.
        assertFalse(result.opensDown)
        assertFalse(result.opensRight)
        assertEquals(anchor.right, result.panelRect.right)
        assertEquals(anchor.bottom, result.panelRect.bottom)
    }

    @Test
    fun testPlacementExplicitAlignmentHonouredWithSelection() {
        // Regression: an explicit side must not be silently replaced by centred placement
        // just because a selected index exists.
        val anchor = Rect(100f, 400f, 300f, 440f)
        val result = placement(
            anchor = anchor,
            rowCount = 3,
            selectedIndex = 1,
            verticalAlignment = PopupVerticalAlignment.Top,
            horizontalAlignment = PopupHorizontalAlignment.Start,
        )
        assertTrue(result.opensDown)
        assertTrue(result.opensRight)
        assertEquals(anchor.top, result.panelRect.top)
        assertEquals(anchor.left, result.panelRect.left)
    }

    @Test
    fun testDefaultAlignmentIsAutoOnBothAxes() {
        // The public defaults must stay Auto on both axes; this locks the API contract.
        assertEquals(PopupVerticalAlignment.Auto, PopupLayout().verticalAlignment)
        assertEquals(PopupHorizontalAlignment.Auto, PopupLayout().horizontalAlignment)
        assertEquals(PopupWidth.Wrap, PopupLayout().width)
    }

    @Test
    fun testDefaultLayoutWithSelectionCentresTheSelectedRow() {
        // With a selection, vertical Auto centres the selected row rather than comparing sides.
        val anchor = Rect(100f, 500f, 300f, 540f)
        val anchorCenter = (anchor.top + anchor.bottom) / 2f

        // Read the defaults from the public API so this test tracks them, not local constants.
        val default = placement(
            anchor = anchor,
            rowCount = 6,
            selectedIndex = 2,
            verticalAlignment = PopupLayout().verticalAlignment,
            horizontalAlignment = PopupLayout().horizontalAlignment,
        )
        val explicitTop = placement(
            anchor = anchor,
            rowCount = 6,
            selectedIndex = 2,
            verticalAlignment = PopupVerticalAlignment.Top,
            horizontalAlignment = PopupHorizontalAlignment.Auto,
        )

        fun rowCenter(p: PanelPlacement) = p.rowRects[2].top + p.rowRects[2].height / 2f

        // The default (Auto with a selection) must satisfy the centring constraint.
        assertEquals(anchorCenter, rowCenter(default), absoluteTolerance = 0.5f)
        // An explicit side anchors to the trigger's edge, so it must not satisfy the same
        // constraint in this scenario.
        assertTrue(
            kotlin.math.abs(anchorCenter - rowCenter(explicitTop)) > 0.5f,
            "Auto and Top were expected to produce different placements here",
        )
    }

    @Test
    fun testDefaultLayoutWithoutSelectionComparesSpaceOnTheVerticalAxis() {
        // Without a selection, vertical Auto is a pure space comparison — the panel opens on the
        // roomier side, exactly like an explicit Auto request.
        val anchor = Rect(100f, 700f, 300f, 740f)
        val default = placement(
            anchor = anchor,
            rowCount = 6,
            selectedIndex = null,
            verticalAlignment = PopupLayout().verticalAlignment,
            horizontalAlignment = PopupLayout().horizontalAlignment,
        )
        val explicit = placement(
            anchor = anchor,
            rowCount = 6,
            selectedIndex = null,
            verticalAlignment = PopupVerticalAlignment.Auto,
            horizontalAlignment = PopupLayout().horizontalAlignment,
        )
        assertEquals(explicit.panelRect, default.panelRect)
        // The six-row panel is taller than the room below the anchor, so Auto must open upward:
        // the panel's bottom edge sits at the anchor's bottom edge, extending up.
        assertEquals(anchor.bottom, default.panelRect.bottom, absoluteTolerance = 0.5f)
        assertTrue(default.panelRect.top < anchor.top, "the panel must extend above the anchor")
    }

    @Test
    fun testPlacementAutoWithSelectionCentresTheSelectedRow() {
        val anchor = Rect(100f, 500f, 300f, 540f)
        val selectedIndex = 2
        val result = placement(
            anchor = anchor,
            rowCount = 6,
            selectedIndex = selectedIndex,
            verticalAlignment = PopupVerticalAlignment.Auto,
        )

        val rowCenter = result.rowRects[selectedIndex].top + result.rowRects[selectedIndex].height / 2f
        val anchorCenter = (anchor.top + anchor.bottom) / 2f
        assertEquals(anchorCenter, rowCenter, absoluteTolerance = 0.5f)
    }

    @Test
    fun testPlacementAutoWithSelectionSeedsScrollForOffscreenSelection() {
        val anchor = Rect(100f, 500f, 300f, 540f)
        val result = placement(
            anchor = anchor,
            rowCount = 20,
            selectedIndex = 18,
            verticalAlignment = PopupVerticalAlignment.Auto,
            maxHeight = 200.dp,
        )
        assertTrue(result.maxScrollPx > 0f)
        assertTrue(result.initialScrollPx > 0f)
        // The selected row must be positioned inside the panel viewport, not beyond it.
        val selected = result.rowRects[18]
        assertTrue(selected.top >= result.panelRect.top)
        assertTrue(selected.bottom <= result.panelRect.bottom + 0.5f)
    }

    @Test
    fun testPlacementAutoWithSelectionStaysAlignedForScrollableList() {
        // Regression: with a scrollable list the panel must still centre the selected row.
        // Previously the scroll was applied after positioning, which pushed the row off-centre.
        val anchor = Rect(100f, 500f, 300f, 540f)
        val anchorCenter = (anchor.top + anchor.bottom) / 2f

        val result = placement(
            anchor = anchor,
            rowCount = 20,
            selectedIndex = 10,
            verticalAlignment = PopupVerticalAlignment.Auto,
            maxHeight = 200.dp,
        )

        val rowCenter = result.rowRects[10].top + result.rowRects[10].height / 2f
        assertEquals(anchorCenter, rowCenter, absoluteTolerance = 0.5f)
    }

    @Test
    fun testPlacementAutoWithSelectionAlignsForEveryRow() {
        // Every selectable row should centre, not just the ones that happen to fit.
        val anchor = Rect(100f, 600f, 300f, 640f)
        val anchorCenter = (anchor.top + anchor.bottom) / 2f

        for (index in 0 until 20) {
            val result = placement(
                anchor = anchor,
                rowCount = 20,
                selectedIndex = index,
                verticalAlignment = PopupVerticalAlignment.Auto,
                maxHeight = 200.dp,
            )
            val rowCenter = result.rowRects[index].top + result.rowRects[index].height / 2f
            assertEquals(
                anchorCenter,
                rowCenter,
                absoluteTolerance = 0.5f,
            )
        }
    }

    @Test
    fun testPlacementAutoWithSelectionKeepsTheRowInsideViewport() {
        val anchor = Rect(100f, 500f, 300f, 540f)
        for (index in 0 until 20) {
            val result = placement(
                anchor = anchor,
                rowCount = 20,
                selectedIndex = index,
                verticalAlignment = PopupVerticalAlignment.Auto,
                maxHeight = 200.dp,
            )
            val row = result.rowRects[index]
            assertTrue(row.top >= result.panelRect.top, "row $index escaped the panel top")
            assertTrue(
                row.bottom <= result.panelRect.bottom + 0.5f,
                "row $index escaped the panel bottom",
            )
        }
    }

    @Test
    fun testPlacementAutoWithSelectionPanelStaysInWindow() {
        val edgeMarginPx = with(density) { metrics.edgeMargin.toPx() }
        for (index in 0 until 20) {
            val result = placement(
                anchor = Rect(100f, 500f, 300f, 540f),
                rowCount = 20,
                selectedIndex = index,
                verticalAlignment = PopupVerticalAlignment.Auto,
                maxHeight = 200.dp,
            )
            assertTrue(result.panelRect.top >= edgeMarginPx - 0.5f)
            assertTrue(result.panelRect.bottom <= windowSize.height - edgeMarginPx + 0.5f)
        }
    }

    @Test
    fun testPlacementRtlMirrorsHorizontalPlacement() {
        // Use a width that fits so the mirror is observable rather than clamped to the edge.
        val width = PopupWidth.Fixed(60.dp)
        val anchor = Rect(300f, 400f, 400f, 440f)

        val ltr = placement(
            anchor = anchor,
            width = width,
            verticalAlignment = PopupVerticalAlignment.Top,
            horizontalAlignment = PopupHorizontalAlignment.Start,
            layoutDirection = LayoutDirection.Ltr,
        )
        val rtl = placement(
            anchor = anchor,
            width = width,
            verticalAlignment = PopupVerticalAlignment.Top,
            horizontalAlignment = PopupHorizontalAlignment.Start,
            layoutDirection = LayoutDirection.Rtl,
        )

        val widthPx = with(density) { 60.dp.toPx() }
        // TopStart in Ltr resolves to TopLeft: the panel's left aligns with the anchor's left.
        assertEquals(anchor.left, ltr.panelRect.left)
        // TopStart in Rtl resolves to TopRight: the panel's right aligns with the anchor's right.
        assertEquals(anchor.right, rtl.panelRect.right)
        assertEquals(anchor.right - widthPx, rtl.panelRect.left)
        // The two placements must be genuinely different, not both clamped to the same edge.
        assertTrue(rtl.panelRect.left < ltr.panelRect.left)
    }

    @Test
    fun testPlacementRowRectsAreOrderedAndNonOverlapping() {
        val result = placement(anchor = Rect(100f, 200f, 300f, 240f), rowCount = 5)
        result.rowRects.zipWithNext { upper, lower ->
            assertTrue(lower.top >= upper.bottom)
        }
    }

    @Test
    fun testPlacementRowRectsSharePanelHorizontalBounds() {
        val result = placement(anchor = Rect(100f, 200f, 300f, 240f), rowCount = 4)
        result.rowRects.forEach { rect ->
            assertEquals(result.panelRect.left, rect.left)
            assertEquals(result.panelRect.right, rect.right)
        }
    }

    @Test
    fun testPlacementAnchorRectIsPreserved() {
        val anchor = Rect(100f, 200f, 300f, 240f)
        val result = placement(anchor = anchor)
        assertEquals(anchor, result.anchorRect)
    }

    // endregion

    // region helpers

    @Test
    fun testContentHeightPx() {
        val rowHeightPx = with(density) { metrics.rowTouchTargetHeight.toPx() }
        val rowSpacingPx = with(density) { metrics.rowVerticalSpacing.toPx() }
        val panelPaddingPx = with(density) { metrics.panelVerticalPadding.toPx() }

        assertEquals(panelPaddingPx * 2f, with(density) { contentHeightPx(0, metrics) })
        assertEquals(
            panelPaddingPx * 2f + rowHeightPx,
            with(density) { contentHeightPx(1, metrics) },
        )
        assertEquals(
            panelPaddingPx * 2f + 5 * rowHeightPx + 4 * rowSpacingPx,
            with(density) { contentHeightPx(5, metrics) },
        )
    }

    @Test
    fun testRowRectAt() {
        val panelRect = Rect(0f, 100f, 200f, 400f)
        val rect = rowRectAt(
            panelRect = panelRect,
            index = 2,
            rowHeightPx = 40f,
            rowSpacingPx = 4f,
            panelPaddingPx = 8f,
            scrollOffsetPx = 0f,
        )
        assertEquals(196f, rect.top)
        assertEquals(236f, rect.bottom)
    }

    @Test
    fun testClampRowToPanel() {
        val panelRect = Rect(0f, 100f, 200f, 300f)
        val above = Rect(0f, 0f, 200f, 40f)
        val clampedAbove = clampRowToPanel(above, panelRect, panelPaddingPx = 8f)
        assertEquals(108f, clampedAbove.top)

        val below = Rect(0f, 900f, 200f, 940f)
        val clampedBelow = clampRowToPanel(below, panelRect, panelPaddingPx = 8f)
        assertEquals(252f, clampedBelow.top)

        val inside = Rect(0f, 150f, 200f, 190f)
        assertEquals(inside, clampRowToPanel(inside, panelRect, panelPaddingPx = 8f))
    }

    @Test
    fun testScrollDeltaToRevealRowAlreadyVisible() {
        val delta = scrollDeltaToRevealRow(
            rowTopPx = 100f,
            rowHeightPx = 48f,
            viewportHeightPx = 400f,
            panelPaddingPx = 16f,
            scrollOffsetPx = 0f,
            maxScrollPx = 600f,
        )
        assertEquals(0f, delta)
    }

    @Test
    fun testScrollDeltaToRevealRowBelowViewport() {
        // Row bottom at 500 exceeds the visible bottom (400 - 16 = 384) by 116.
        val delta = scrollDeltaToRevealRow(
            rowTopPx = 452f,
            rowHeightPx = 48f,
            viewportHeightPx = 400f,
            panelPaddingPx = 16f,
            scrollOffsetPx = 0f,
            maxScrollPx = 600f,
        )
        assertEquals(116f, delta)
    }

    @Test
    fun testScrollDeltaToRevealRowClampedToMaxScroll() {
        val delta = scrollDeltaToRevealRow(
            rowTopPx = 9000f,
            rowHeightPx = 48f,
            viewportHeightPx = 400f,
            panelPaddingPx = 16f,
            scrollOffsetPx = 0f,
            maxScrollPx = 600f,
        )
        assertEquals(600f, delta)
    }

    @Test
    fun testCenteredScrollOffsetPxNoScroll() {
        assertEquals(
            0f,
            centeredScrollOffsetPx(
                rowCenterInContentPx = 100f,
                viewportHeightPx = 400f,
                maxScrollPx = 0f,
            ),
        )
    }

    @Test
    fun testCenteredScrollOffsetPxRowNearTop() {
        // Row centre at 60 is already in the upper half of a 400px viewport; cannot scroll up.
        assertEquals(
            0f,
            centeredScrollOffsetPx(
                rowCenterInContentPx = 60f,
                viewportHeightPx = 400f,
                maxScrollPx = 900f,
            ),
        )
    }

    @Test
    fun testCenteredScrollOffsetPxCentresRow() {
        // Row centre at 500 in a 400px viewport: scroll 300 to bring it to the middle.
        assertEquals(
            300f,
            centeredScrollOffsetPx(
                rowCenterInContentPx = 500f,
                viewportHeightPx = 400f,
                maxScrollPx = 900f,
            ),
        )
    }

    @Test
    fun testCenteredScrollOffsetPxClampedToMaxScroll() {
        assertEquals(
            120f,
            centeredScrollOffsetPx(
                rowCenterInContentPx = 5000f,
                viewportHeightPx = 400f,
                maxScrollPx = 120f,
            ),
        )
    }

    @Test
    fun testCenteredScrollOffsetPxCentresPanelWhenRowIsCentral() {
        // With the row centred in the viewport, the panel's centre should land on the anchor.
        val rowCenterInContentPx = 800f
        val viewportHeightPx = 400f
        val scroll = centeredScrollOffsetPx(
            rowCenterInContentPx = rowCenterInContentPx,
            viewportHeightPx = viewportHeightPx,
            maxScrollPx = 2000f,
        )
        val rowCenterInViewport = rowCenterInContentPx - scroll
        assertEquals(viewportHeightPx / 2f, rowCenterInViewport, absoluteTolerance = 0.5f)
    }

    @Test
    fun testScrollDeltaToRevealRowNoScrollableContent() {
        val delta = scrollDeltaToRevealRow(
            rowTopPx = 200f,
            rowHeightPx = 48f,
            viewportHeightPx = 400f,
            panelPaddingPx = 16f,
            scrollOffsetPx = 0f,
            maxScrollPx = 0f,
        )
        assertEquals(0f, delta)
    }

    @Test
    fun testPlacementTinyViewportRevealsTheSelectedRowByItsBottomEdge() {
        // A max height smaller than one row plus the padding puts the reveal in the regime where
        // a row violates both edges of the viewport. The placement path can never trip the
        // top-edge branch — its row tops are panelPadding + index * stride by construction — so
        // the reveal aligns the row's bottom edge with the visible bottom, leaving the row's top
        // necessarily inside the padding.
        val result = placement(
            anchor = Rect(100f, 200f, 300f, 240f),
            rowCount = 20,
            selectedIndex = 5,
            maxHeight = 60.dp,
            verticalAlignment = PopupVerticalAlignment.Top,
        )
        val rowHeightPx = with(density) { metrics.rowTouchTargetHeight.toPx() }
        val rowSpacingPx = with(density) { metrics.rowVerticalSpacing.toPx() }
        val panelPaddingPx = with(density) { metrics.panelVerticalPadding.toPx() }
        val viewportHeightPx = with(density) { 60.dp.toPx() }
        val rowTopPx = panelPaddingPx + 5 * (rowHeightPx + rowSpacingPx)
        val maxScrollPx = with(density) {
            contentHeightPx(20, metrics) - 60.dp.toPx()
        }

        assertEquals(
            (rowTopPx + rowHeightPx - (viewportHeightPx - panelPaddingPx)).coerceAtMost(maxScrollPx),
            result.initialScrollPx,
        )
    }

    // endregion

    private fun assertEquals(expected: Float, actual: Float, absoluteTolerance: Float) {
        assertTrue(
            kotlin.math.abs(expected - actual) <= absoluteTolerance,
            "Expected $expected but was $actual (tolerance $absoluteTolerance)",
        )
    }
}
