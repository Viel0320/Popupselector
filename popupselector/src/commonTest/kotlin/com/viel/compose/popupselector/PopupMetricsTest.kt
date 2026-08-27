package com.viel.compose.popupselector

import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PopupMetricsTest {

    private val density = Density(density = 2f, fontScale = 1f)

    @Test
    fun testChevronBlockWidth() {
        val result = with(density) { chevronBlockWidth() }
        val expected = with(density) { (PopupChevronSize + PopupSpacingBase).toPx() }
        assertEquals(expected, result)
    }

    @Test
    fun testCalculatePanelHeightSingleRow() {
        val result = with(density) { calculatePanelHeight(1) }
        val expected = with(density) {
            PopupPanelVerticalPadding.toPx() * 2f + PopupRowTouchTargetHeight.toPx()
        }
        assertEquals(expected, result)
    }

    @Test
    fun testCalculatePanelHeightMultipleRows() {
        val rowCount = 5
        val result = with(density) { calculatePanelHeight(rowCount) }
        val expected = with(density) {
            PopupPanelVerticalPadding.toPx() * 2f +
                rowCount * PopupRowTouchTargetHeight.toPx() +
                (rowCount - 1) * PopupRowVerticalSpacing.toPx()
        }
        assertEquals(expected, result)
    }

    @Test
    fun testCalculatePanelHeightZeroRows() {
        val result = with(density) { calculatePanelHeight(0) }
        val expected = with(density) {
            PopupPanelVerticalPadding.toPx() * 2f
        }
        assertEquals(expected, result)
    }

    @Test
    fun testCalculatePanelHeightIncreases() {
        val h3 = with(density) { calculatePanelHeight(3) }
        val h5 = with(density) { calculatePanelHeight(5) }
        assertTrue(h5 > h3)
    }
}
