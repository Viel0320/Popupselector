package com.viel.compose.popupselector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PopupSelectorStateTest {

    @Test
    fun state_initialState() {
        val state = PopupSelectorState()
        assertFalse(state.expanded)
        assertNull(state.selectedIndex)
    }

    @Test
    fun state_initialCustomValues() {
        val state = PopupSelectorState(initialExpanded = true, initialSelectedIndex = 2)
        assertTrue(state.expanded)
        assertEquals(2, state.selectedIndex)
    }

    @Test
    fun state_expandAndCollapseAndToggle() {
        val state = PopupSelectorState()
        state.expand()
        assertTrue(state.expanded)

        state.collapse()
        assertFalse(state.expanded)

        state.toggle()
        assertTrue(state.expanded)

        state.toggle()
        assertFalse(state.expanded)
    }

    @Test
    fun state_selectUpdatesIndexAndCollapses() {
        val state = PopupSelectorState(initialExpanded = true, initialSelectedIndex = 0)
        state.select(3)
        assertEquals(3, state.selectedIndex)
        assertFalse(state.expanded)
    }

    @Test
    fun state_saverRestoresCorrectly() {
        val state = PopupSelectorState(initialExpanded = true, initialSelectedIndex = 4)
        val saver = PopupSelectorState.Saver
        val scope = androidx.compose.runtime.saveable.SaverScope { true }
        val saved = with(saver) { scope.save(state) }
        val restored = saver.restore(saved!!)
        assertTrue(restored!!.expanded)
        assertEquals(4, restored.selectedIndex)
    }

    @Test
    fun metricsPx_structuralEquality() {
        val a = PopupMetricsPx(
            cornerPx = 10f,
            borderWidthPx = 1f,
            collapsedElevationPx = 2f,
            expandedElevationPx = 4f,
            chevronSizePx = 12f,
            chevronLeadingPaddingPx = 8f,
            chevronBlockPx = 20f,
            rowHeightPx = 48f,
            rowSpacingPx = 4f,
            panelPaddingPx = 8f,
            rowHorizontalPaddingPx = 12f,
            countSpacingPx = 8f,
            collapsedHeightPx = 40f,
            collapsedHorizontalPaddingPx = 12f,
            rowEnterTranslationPx = 16f,
            edgeMarginPx = 16f,
        )
        val b = PopupMetricsPx(
            cornerPx = 10f,
            borderWidthPx = 1f,
            collapsedElevationPx = 2f,
            expandedElevationPx = 4f,
            chevronSizePx = 12f,
            chevronLeadingPaddingPx = 8f,
            chevronBlockPx = 20f,
            rowHeightPx = 48f,
            rowSpacingPx = 4f,
            panelPaddingPx = 8f,
            rowHorizontalPaddingPx = 12f,
            countSpacingPx = 8f,
            collapsedHeightPx = 40f,
            collapsedHorizontalPaddingPx = 12f,
            rowEnterTranslationPx = 16f,
            edgeMarginPx = 16f,
        )
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    /**
     * Verifies the overlay registry's ownership lifecycle: re-registering an owner replaces its
     * entry in place instead of appending a duplicate, removal — explicit or via null content —
     * takes out only the named owner, and surviving entries keep their registration order.
     */
    @Test
    fun overlayHostState_immutableEntries() {
        val host = PopupOverlayHostState()
        assertTrue(host.entries.isEmpty())

        val owner1 = "owner1"
        val owner2 = "owner2"

        host.setContent(owner1) { }
        assertEquals(1, host.entries.size)
        assertEquals(owner1, host.entries[0].owner)

        host.setContent(owner2) { }
        assertEquals(2, host.entries.size)
        assertEquals(owner2, host.entries[1].owner)

        host.setContent(owner1) { }
        assertEquals(2, host.entries.size)
        assertEquals(owner1, host.entries[0].owner)

        host.clearContent(owner1)
        assertEquals(1, host.entries.size)
        assertEquals(owner2, host.entries[0].owner)

        host.setContent(owner2, null)
        assertTrue(host.entries.isEmpty())
    }
}
