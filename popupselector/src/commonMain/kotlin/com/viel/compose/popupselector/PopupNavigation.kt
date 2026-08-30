package com.viel.compose.popupselector

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Index the keyboard cursor should move to, or null if the key was not handled.
 *
 * Only enabled options can receive the cursor, so disabled rows are skipped rather than trapping
 * focus on a row the user cannot activate.
 */
internal fun nextFocusIndex(
    items: List<PopupItem>,
    from: Int,
    moveDown: Boolean,
): Int? {
    if (items.isEmpty()) return null
    val step = if (moveDown) 1 else -1
    var index = from
    repeat(items.size) {
        index = (index + step + items.size) % items.size
        if (items[index].enabled) return index
    }
    return null
}

/**
 * First index that can receive the keyboard cursor.
 *
 * Prefers [preferred] (typically the selected row) and falls back to scanning from the top.
 */
internal fun initialFocusIndex(
    items: List<PopupItem>,
    preferred: Int?,
): Int? {
    if (items.isEmpty()) return null
    preferred?.let { if (it in items.indices && items[it].enabled) return it }
    return items.indexOfFirst { it.enabled }.takeIf { it >= 0 }
}

/**
 * Content-space top of row [index]: the panel's top padding plus the row stride. Its top in
 * viewport coordinates is this minus the current scroll offset.
 *
 * The single definition of the row stride, shared by [rowRectAt], [resolvePlacement] and the
 * scroll helpers, so the row geometry can never disagree between them.
 */
internal fun rowTopInContentPx(
    index: Int,
    rowHeightPx: Float,
    rowSpacingPx: Float,
    panelPaddingPx: Float,
): Float = panelPaddingPx + index * (rowHeightPx + rowSpacingPx)

/**
 * Smallest scroll adjustment that brings a row fully into the padded viewport.
 *
 * [rowTopPx] is the row's top in viewport coordinates — its content-space top minus the current
 * scroll offset. Returns the (signed) scroll delta to apply, clamped so the resulting offset stays
 * within `[0, maxScrollPx]`, or 0 when the row is already fully visible.
 *
 * When a row violates both edges (taller than the padded viewport), aligning its top edge wins:
 * the keyboard reveal historically scrolled the row's top back to the padding. The placement path
 * can never trip this branch — its row tops are `panelPadding + index * stride` by construction,
 * so only the bottom edge can be violated there.
 */
internal fun scrollDeltaToRevealRow(
    rowTopPx: Float,
    rowHeightPx: Float,
    viewportHeightPx: Float,
    panelPaddingPx: Float,
    scrollOffsetPx: Float,
    maxScrollPx: Float,
): Float {
    val rowBottomPx = rowTopPx + rowHeightPx
    val bottomBoundPx = viewportHeightPx - panelPaddingPx

    val raw = when {
        rowTopPx < panelPaddingPx -> rowTopPx - panelPaddingPx
        rowBottomPx > bottomBoundPx -> rowBottomPx - bottomBoundPx
        else -> return 0f
    }
    val target = (scrollOffsetPx + raw).coerceIn(0f, maxScrollPx)
    return target - scrollOffsetPx
}

/**
 * Scroll offset that keeps the row at [index] visible, given the current scroll position.
 *
 * Returns null when no adjustment is needed, so callers can avoid an unnecessary animation.
 */
internal fun scrollDeltaToReveal(
    index: Int,
    rowHeightPx: Float,
    rowSpacingPx: Float,
    panelPaddingPx: Float,
    viewportHeightPx: Float,
    scrollOffsetPx: Float,
    maxScrollPx: Float,
): Float? {
    val delta = scrollDeltaToRevealRow(
        rowTopPx = rowTopInContentPx(index, rowHeightPx, rowSpacingPx, panelPaddingPx) -
            scrollOffsetPx,
        rowHeightPx = rowHeightPx,
        viewportHeightPx = viewportHeightPx,
        panelPaddingPx = panelPaddingPx,
        scrollOffsetPx = scrollOffsetPx,
        maxScrollPx = maxScrollPx,
    )
    return delta.takeIf { it != 0f }
}

/**
 * The option list's keyboard cursor, independent of the selection so arrow keys can preview
 * without committing.
 *
 * Without a selection the cursor does not exist until an arrow key is pressed — a hidden but
 * active cursor would let Enter commit a row nothing indicates. Arrow-down then lands on the
 * first enabled item, arrow-up on the last.
 */
@Stable
internal class PopupKeyboardCursor(items: List<PopupItem>, selectedIndex: Int?) {
    var index: Int by mutableIntStateOf(
        selectedIndex?.takeIf { it in items.indices && items[it].enabled } ?: -1,
    )
        private set

    /** Whether the user has moved the cursor with the keyboard, making the highlight visible. */
    var engaged: Boolean by mutableStateOf(false)
        private set

    /** Moves the cursor one step, skipping disabled rows; null when it cannot move. */
    fun move(items: List<PopupItem>, moveDown: Boolean): Int? {
        val next = nextFocusIndex(items, index, moveDown) ?: return null
        index = next
        engaged = true
        return next
    }

    /** The row Enter/Space would commit, or null when nothing enabled sits under the cursor. */
    fun activatable(items: List<PopupItem>): Int? =
        index.takeIf { it in items.indices && items[it].enabled }
}
