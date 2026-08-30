package com.viel.compose.popupselector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PopupNavigationTest {

    private fun items(vararg enabled: Boolean): List<PopupItem> = enabled.map { isEnabled ->
        PopupItem(key = isEnabled, enabled = isEnabled, content = {})
    }

    // region nextFocusIndex

    @Test
    fun testNextFocusIndexMovesDown() {
        val list = items(true, true, true)
        assertEquals(1, nextFocusIndex(list, from = 0, moveDown = true))
        assertEquals(2, nextFocusIndex(list, from = 1, moveDown = true))
    }

    @Test
    fun testNextFocusIndexWrapsAround() {
        val list = items(true, true, true)
        assertEquals(0, nextFocusIndex(list, from = 2, moveDown = true))
        assertEquals(2, nextFocusIndex(list, from = 0, moveDown = false))
    }

    @Test
    fun testNextFocusIndexSkipsDisabledRows() {
        val list = items(true, false, false, true)
        assertEquals(3, nextFocusIndex(list, from = 0, moveDown = true))
        assertEquals(0, nextFocusIndex(list, from = 3, moveDown = true))
        assertEquals(0, nextFocusIndex(list, from = 3, moveDown = false))
    }

    @Test
    fun testNextFocusIndexAllDisabledReturnsNull() {
        val list = items(false, false)
        assertNull(nextFocusIndex(list, from = 0, moveDown = true))
        assertNull(nextFocusIndex(list, from = 0, moveDown = false))
    }

    @Test
    fun testNextFocusIndexEmptyList() {
        assertNull(nextFocusIndex(emptyList(), from = 0, moveDown = true))
    }

    @Test
    fun testNextFocusIndexSingleEnabledRowStays() {
        val list = items(false, true, false)
        assertEquals(1, nextFocusIndex(list, from = 1, moveDown = true))
        assertEquals(1, nextFocusIndex(list, from = 1, moveDown = false))
    }

    // endregion

    // region initialFocusIndex

    @Test
    fun testInitialFocusIndexPrefersSelected() {
        val list = items(true, true, true)
        assertEquals(2, initialFocusIndex(list, preferred = 2))
    }

    @Test
    fun testInitialFocusIndexSkipsDisabledPreferred() {
        val list = items(true, true, false)
        assertEquals(0, initialFocusIndex(list, preferred = 2))
    }

    @Test
    fun testInitialFocusIndexFallsBackToFirstEnabled() {
        val list = items(false, false, true, true)
        assertEquals(2, initialFocusIndex(list, preferred = null))
    }

    @Test
    fun testInitialFocusIndexAllDisabled() {
        assertNull(initialFocusIndex(items(false, false), preferred = null))
    }

    @Test
    fun testInitialFocusIndexEmptyList() {
        assertNull(initialFocusIndex(emptyList(), preferred = 0))
    }

    @Test
    fun testInitialFocusIndexIgnoresOutOfRangePreferred() {
        val list = items(true, true)
        assertEquals(0, initialFocusIndex(list, preferred = 99))
        assertEquals(0, initialFocusIndex(list, preferred = -1))
    }

    // endregion

    // region scrollDeltaToReveal

    @Test
    fun testScrollDeltaToRevealNoChangeWhenVisible() {
        assertNull(
            scrollDeltaToReveal(
                index = 1,
                rowHeightPx = 48f,
                rowSpacingPx = 4f,
                panelPaddingPx = 8f,
                viewportHeightPx = 300f,
                scrollOffsetPx = 0f,
                maxScrollPx = 500f,
            ),
        )
    }

    @Test
    fun testScrollDeltaToRevealScrollsDownForRowBelowViewport() {
        // Row 10 top = 8 + 10*52 = 528, bottom = 576. Visible bottom = 300 - 8 = 292.
        // Delta = 576 - 292 = 284.
        val delta = scrollDeltaToReveal(
            index = 10,
            rowHeightPx = 48f,
            rowSpacingPx = 4f,
            panelPaddingPx = 8f,
            viewportHeightPx = 300f,
            scrollOffsetPx = 0f,
            maxScrollPx = 500f,
        )
        assertEquals(284f, delta)
    }

    @Test
    fun testScrollDeltaToRevealScrollsUpForRowAboveViewport() {
        // With scroll = 200, row 0 top = 8 - 200 = -192 < top bound 8. Delta = -200.
        val delta = scrollDeltaToReveal(
            index = 0,
            rowHeightPx = 48f,
            rowSpacingPx = 4f,
            panelPaddingPx = 8f,
            viewportHeightPx = 300f,
            scrollOffsetPx = 200f,
            maxScrollPx = 500f,
        )
        assertEquals(-200f, delta)
    }

    @Test
    fun testScrollDeltaToRevealClampedAtMaxScroll() {
        // Requesting a delta beyond maxScroll must be clamped, not overshoot.
        val delta = scrollDeltaToReveal(
            index = 100,
            rowHeightPx = 48f,
            rowSpacingPx = 4f,
            panelPaddingPx = 8f,
            viewportHeightPx = 300f,
            scrollOffsetPx = 0f,
            maxScrollPx = 120f,
        )
        assertEquals(120f, delta)
    }

    @Test
    fun testScrollDeltaToRevealNoChangeWhenAlreadyAtMaxScroll() {
        assertNull(
            scrollDeltaToReveal(
                index = 100,
                rowHeightPx = 48f,
                rowSpacingPx = 4f,
                panelPaddingPx = 8f,
                viewportHeightPx = 300f,
                scrollOffsetPx = 120f,
                maxScrollPx = 120f,
            ),
        )
    }

    @Test
    fun testScrollDeltaToRevealUnscrollableContent() {
        assertNull(
            scrollDeltaToReveal(
                index = 50,
                rowHeightPx = 48f,
                rowSpacingPx = 4f,
                panelPaddingPx = 8f,
                viewportHeightPx = 300f,
                scrollOffsetPx = 0f,
                maxScrollPx = 0f,
            ),
        )
    }

    // endregion

    // region scrollDeltaToRevealRow

    @Test
    fun testScrollDeltaToRevealRowBothEdgesViolatedAlignsTheTop() {
        // A row taller than the padded viewport violates both edges at once: its top (4) is above
        // the padding (8) while its bottom (52) is past the visible bottom (50 - 8 = 42). The
        // keyboard reveal must keep its historical preference and align the row's TOP with the
        // padding — scrolling up — not push it down to the bottom edge.
        val delta = scrollDeltaToRevealRow(
            rowTopPx = 4f,
            rowHeightPx = 48f,
            viewportHeightPx = 50f,
            panelPaddingPx = 8f,
            scrollOffsetPx = 20f,
            maxScrollPx = 100f,
        )
        assertEquals(-4f, delta)
    }

    @Test
    fun testScrollDeltaToRevealRowBothEdgesViolatedClampedAtZero() {
        // With nothing to scroll up to, the aligned-top target clamps to offset 0.
        val delta = scrollDeltaToRevealRow(
            rowTopPx = 2f,
            rowHeightPx = 48f,
            viewportHeightPx = 50f,
            panelPaddingPx = 8f,
            scrollOffsetPx = 0f,
            maxScrollPx = 100f,
        )
        assertEquals(0f, delta)
    }

    @Test
    fun testScrollDeltaToRevealKeepsTheTopEdgePreferenceForTallRows() {
        // End to end through the keyboard wrapper: row 1's content-space top is 8 + 52 = 60; at
        // scroll 56 its top sits at 4 — above the padding — while its bottom (52) is past the
        // visible bottom (42) because the row is taller than the viewport. Both edges violated,
        // so the top edge wins and the delta is negative.
        val delta = scrollDeltaToReveal(
            index = 1,
            rowHeightPx = 48f,
            rowSpacingPx = 4f,
            panelPaddingPx = 8f,
            viewportHeightPx = 50f,
            scrollOffsetPx = 56f,
            maxScrollPx = 100f,
        )
        assertEquals(-4f, delta)
    }

    // endregion
}
