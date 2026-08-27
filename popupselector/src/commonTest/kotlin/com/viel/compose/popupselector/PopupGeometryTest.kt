package com.viel.compose.popupselector

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PopupGeometryTest {

    @Test
    fun testLerp() {
        assertEquals(0f, lerp(0f, 100f, 0f))
        assertEquals(50f, lerp(0f, 100f, 0.5f))
        assertEquals(100f, lerp(0f, 100f, 1f))
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

    @Test
    fun testResolveDirectionAutoPrefersTopLeftWhenSpaceAvailable() {
        val anchor = Rect(100f, 100f, 200f, 140f)
        val dir = resolveDirection(
            anchor = anchor,
            panelHeightPx = 200f,
            panelWidthPx = 200f,
            windowHeightPx = 800f,
            windowWidthPx = 600f,
            edgeMarginPx = 16f,
            requested = PopupAlignment.Auto,
            layoutDirection = LayoutDirection.Ltr,
        )
        assertEquals(OpenDirection.TopLeft, dir)
        assertTrue(dir.opensDown)
    }

    @Test
    fun testResolveDirectionAutoFlipsUpWhenBottomSpaceInsufficient() {
        val anchor = Rect(100f, 700f, 200f, 740f)
        val dir = resolveDirection(
            anchor = anchor,
            panelHeightPx = 200f,
            panelWidthPx = 200f,
            windowHeightPx = 800f,
            windowWidthPx = 600f,
            edgeMarginPx = 16f,
            requested = PopupAlignment.Auto,
            layoutDirection = LayoutDirection.Ltr,
        )
        assertEquals(OpenDirection.BottomLeft, dir)
        assertFalse(dir.opensDown)
    }

    @Test
    fun testResolveDirectionAutoFlipsLeftWhenRightSpaceInsufficient() {
        val anchor = Rect(500f, 100f, 580f, 140f)
        val dir = resolveDirection(
            anchor = anchor,
            panelHeightPx = 200f,
            panelWidthPx = 200f,
            windowHeightPx = 800f,
            windowWidthPx = 600f,
            edgeMarginPx = 16f,
            requested = PopupAlignment.Auto,
            layoutDirection = LayoutDirection.Ltr,
        )
        assertEquals(OpenDirection.TopRight, dir)
        assertTrue(dir.opensDown)
    }

    @Test
    fun testResolveDirectionExplicitCorners() {
        val anchor = Rect(100f, 100f, 200f, 140f)
        val baseArgs = { alignment: PopupAlignment ->
            resolveDirection(
                anchor = anchor,
                panelHeightPx = 100f,
                panelWidthPx = 100f,
                windowHeightPx = 500f,
                windowWidthPx = 500f,
                edgeMarginPx = 10f,
                requested = alignment,
            )
        }

        assertEquals(OpenDirection.TopLeft, baseArgs(PopupAlignment.TopLeft))
        assertEquals(OpenDirection.TopRight, baseArgs(PopupAlignment.TopRight))
        assertEquals(OpenDirection.BottomLeft, baseArgs(PopupAlignment.BottomLeft))
        assertEquals(OpenDirection.BottomRight, baseArgs(PopupAlignment.BottomRight))
    }

    @Test
    fun testResolveDirectionRtlMapping() {
        val anchor = Rect(100f, 100f, 200f, 140f)
        fun resolveWithRtl(align: PopupAlignment, isRtl: Boolean): OpenDirection =
            resolveDirection(
                anchor = anchor,
                panelHeightPx = 100f,
                panelWidthPx = 100f,
                windowHeightPx = 500f,
                windowWidthPx = 500f,
                edgeMarginPx = 10f,
                requested = align,
                layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            )

        // TopStart: LTR -> TopLeft, RTL -> TopRight
        assertEquals(OpenDirection.TopLeft, resolveWithRtl(PopupAlignment.TopStart, isRtl = false))
        assertEquals(OpenDirection.TopRight, resolveWithRtl(PopupAlignment.TopStart, isRtl = true))

        // TopEnd: LTR -> TopRight, RTL -> TopLeft
        assertEquals(OpenDirection.TopRight, resolveWithRtl(PopupAlignment.TopEnd, isRtl = false))
        assertEquals(OpenDirection.TopLeft, resolveWithRtl(PopupAlignment.TopEnd, isRtl = true))

        // BottomStart: LTR -> BottomLeft, RTL -> BottomRight
        assertEquals(OpenDirection.BottomLeft, resolveWithRtl(PopupAlignment.BottomStart, isRtl = false))
        assertEquals(OpenDirection.BottomRight, resolveWithRtl(PopupAlignment.BottomStart, isRtl = true))

        // BottomEnd: LTR -> BottomRight, RTL -> BottomLeft
        assertEquals(OpenDirection.BottomRight, resolveWithRtl(PopupAlignment.BottomEnd, isRtl = false))
        assertEquals(OpenDirection.BottomLeft, resolveWithRtl(PopupAlignment.BottomEnd, isRtl = true))
    }

    @Test
    fun testResolvePanelRectPlacementAndClamping() {
        val anchor = Rect(100f, 150f, 250f, 190f)

        // Normal TopLeft
        val rectTopLeft = resolvePanelRect(
            anchor = anchor,
            direction = OpenDirection.TopLeft,
            panelWidthPx = 200f,
            panelHeightPx = 300f,
            windowWidthPx = 800f,
            edgeMarginPx = 16f,
        )
        assertEquals(100f, rectTopLeft.left)
        assertEquals(150f, rectTopLeft.top)
        assertEquals(300f, rectTopLeft.right)
        assertEquals(450f, rectTopLeft.bottom)

        // Normal BottomLeft (opens up)
        val rectBottomLeft = resolvePanelRect(
            anchor = anchor,
            direction = OpenDirection.BottomLeft,
            panelWidthPx = 200f,
            panelHeightPx = 300f,
            windowWidthPx = 800f,
            edgeMarginPx = 16f,
        )
        assertEquals(100f, rectBottomLeft.left)
        assertEquals(190f - 300f, rectBottomLeft.top)
        assertEquals(300f, rectBottomLeft.right)
        assertEquals(190f, rectBottomLeft.bottom)

        // Right Edge Clamping
        val overflowAnchor = Rect(700f, 150f, 780f, 190f)
        val rectClamped = resolvePanelRect(
            anchor = overflowAnchor,
            direction = OpenDirection.TopLeft,
            panelWidthPx = 200f,
            panelHeightPx = 300f,
            windowWidthPx = 800f,
            edgeMarginPx = 16f,
        )
        val expectedMaxLeft = 800f - 16f - 200f
        assertEquals(expectedMaxLeft, rectClamped.left)
        assertEquals(expectedMaxLeft + 200f, rectClamped.right)

        // Oversized Width Clamping
        val rectOversized = resolvePanelRect(
            anchor = anchor,
            direction = OpenDirection.TopLeft,
            panelWidthPx = 9999f,
            panelHeightPx = 300f,
            windowWidthPx = 800f,
            edgeMarginPx = 16f,
        )
        assertEquals(16f, rectOversized.left)
        assertEquals(800f - 16f, rectOversized.right)
        assertEquals(800f - 32f, rectOversized.width)
    }

    @Test
    fun testResolveFlyingSelectedRowTargetTop() {
        val targetTop = resolveFlyingSelectedRowTargetTop(
            panelTop = 100f,
            panelBottom = 400f,
            panelPadding = 8f,
            selectedIndex = 2,
            rowHeight = 40f,
            rowSpacing = 4f,
            scrollOffset = 0f,
        )
        // 100 + 8 + 2 * (40 + 4) = 108 + 88 = 196
        assertEquals(196f, targetTop)
    }

    @Test
    fun testIsFlyingSelectedRowVisible() {
        assertTrue(isFlyingSelectedRowVisible(0f))
        assertTrue(isFlyingSelectedRowVisible(0.5f))
        assertTrue(isFlyingSelectedRowVisible(0.998f))
        assertFalse(isFlyingSelectedRowVisible(0.999f))
        assertFalse(isFlyingSelectedRowVisible(1f))
    }

    @Test
    fun testResolvePanelRectCenteredVerticalAlignment() {
        // Anchor top = 200, bottom = 240 -> Center = 220
        val anchor = Rect(100f, 200f, 250f, 240f)
        val panelPadding = 8f
        val rowHeight = 40f
        val rowSpacing = 4f
        val panelHeight = 200f
        val panelWidth = 150f

        // selectedIndex = 0: selected row center = 8 + 0 + 20 = 28
        // preferredTop = 220 - 28 = 192
        val rect0 = resolvePanelRectCentered(
            anchor = anchor,
            panelWidthPx = panelWidth,
            panelHeightPx = panelHeight,
            windowWidthPx = 800f,
            windowHeightPx = 1000f,
            edgeMarginPx = 16f,
            selectedIndex = 0,
            rowHeightPx = rowHeight,
            rowSpacingPx = rowSpacing,
            panelPaddingPx = panelPadding,
            layoutDirection = LayoutDirection.Ltr,
        )
        assertEquals(192f, rect0.top)
        assertEquals(392f, rect0.bottom)
        assertEquals(100f, rect0.left)
        assertEquals(250f, rect0.right)

        // selectedIndex = 2: selected row center = 8 + 2 * 44 + 20 = 8 + 88 + 20 = 116
        // preferredTop = 220 - 116 = 104
        val rect2 = resolvePanelRectCentered(
            anchor = anchor,
            panelWidthPx = panelWidth,
            panelHeightPx = panelHeight,
            windowWidthPx = 800f,
            windowHeightPx = 1000f,
            edgeMarginPx = 16f,
            selectedIndex = 2,
            rowHeightPx = rowHeight,
            rowSpacingPx = rowSpacing,
            panelPaddingPx = panelPadding,
            layoutDirection = LayoutDirection.Ltr,
        )
        assertEquals(104f, rect2.top)
        assertEquals(304f, rect2.bottom)
    }

    @Test
    fun testResolvePanelRectCenteredVerticalClamping() {
        val anchorTop = Rect(100f, 20f, 250f, 60f) // Center = 40
        val panelPadding = 8f
        val rowHeight = 40f
        val rowSpacing = 4f
        val panelHeight = 200f
        val panelWidth = 150f

        // selectedIndex = 2: selected row center = 116 -> preferredTop = 40 - 116 = -76 < minTop (16)
        val rectClampedTop = resolvePanelRectCentered(
            anchor = anchorTop,
            panelWidthPx = panelWidth,
            panelHeightPx = panelHeight,
            windowWidthPx = 800f,
            windowHeightPx = 1000f,
            edgeMarginPx = 16f,
            selectedIndex = 2,
            rowHeightPx = rowHeight,
            rowSpacingPx = rowSpacing,
            panelPaddingPx = panelPadding,
            layoutDirection = LayoutDirection.Ltr,
        )
        assertEquals(16f, rectClampedTop.top)
        assertEquals(216f, rectClampedTop.bottom)

        // Anchor near bottom: center = 980
        val anchorBottom = Rect(100f, 960f, 250f, 1000f)
        // selectedIndex = 0: center = 28 -> preferredTop = 980 - 28 = 952 > maxTop (1000 - 16 - 200 = 784)
        val rectClampedBottom = resolvePanelRectCentered(
            anchor = anchorBottom,
            panelWidthPx = panelWidth,
            panelHeightPx = panelHeight,
            windowWidthPx = 800f,
            windowHeightPx = 1000f,
            edgeMarginPx = 16f,
            selectedIndex = 0,
            rowHeightPx = rowHeight,
            rowSpacingPx = rowSpacing,
            panelPaddingPx = panelPadding,
            layoutDirection = LayoutDirection.Ltr,
        )
        assertEquals(784f, rectClampedBottom.top)
        assertEquals(984f, rectClampedBottom.bottom)
    }

    @Test
    fun testResolvePanelRectCenteredHorizontalFollowsLayoutDirection() {
        val anchor = Rect(100f, 200f, 250f, 240f) // left = 100, right = 250
        val panelWidth = 180f

        // LTR: left aligns with anchor.left (100)
        val rectLtr = resolvePanelRectCentered(
            anchor = anchor,
            panelWidthPx = panelWidth,
            panelHeightPx = 200f,
            windowWidthPx = 800f,
            windowHeightPx = 1000f,
            edgeMarginPx = 16f,
            selectedIndex = 0,
            rowHeightPx = 40f,
            rowSpacingPx = 4f,
            panelPaddingPx = 8f,
            layoutDirection = LayoutDirection.Ltr,
        )
        assertEquals(100f, rectLtr.left)
        assertEquals(280f, rectLtr.right)

        // RTL: right aligns with anchor.right (250) -> left = 250 - 180 = 70
        val rectRtl = resolvePanelRectCentered(
            anchor = anchor,
            panelWidthPx = panelWidth,
            panelHeightPx = 200f,
            windowWidthPx = 800f,
            windowHeightPx = 1000f,
            edgeMarginPx = 16f,
            selectedIndex = 0,
            rowHeightPx = 40f,
            rowSpacingPx = 4f,
            panelPaddingPx = 8f,
            layoutDirection = LayoutDirection.Rtl,
        )
        assertEquals(70f, rectRtl.left)
        assertEquals(250f, rectRtl.right)
    }
}
