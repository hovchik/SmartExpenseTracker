package com.smartexpense.tracker.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex

/**
 * State holder for drag-and-drop reordering within a LazyColumn.
 *
 * Long-press an item to start dragging, then move up/down to reorder.
 * The [onMove] callback is invoked each time two items should swap positions.
 */
class DragDropListState(
    val lazyListState: LazyListState,
    internal var onMove: (Int, Int) -> Unit
) {
    /** Index of the currently dragged item, or null if no drag is active. */
    var draggedIndex by mutableStateOf<Int?>(null)
        private set

    /** Accumulated vertical pixel offset of the dragged item from its layout position. */
    var draggedOffset by mutableFloatStateOf(0f)
        private set

    fun onDragStart(index: Int) {
        draggedIndex = index
        draggedOffset = 0f
    }

    fun onDrag(delta: Float) {
        draggedOffset += delta

        val dragged = draggedIndex ?: return
        val layoutInfo = lazyListState.layoutInfo
        val draggedItem = layoutInfo.visibleItemsInfo.find { it.index == dragged } ?: return

        val draggedCenter = draggedItem.offset + draggedItem.size / 2 + draggedOffset.toInt()

        val targetItem = layoutInfo.visibleItemsInfo
            .filter { it.index != dragged }
            .find { item ->
                draggedCenter in item.offset..(item.offset + item.size)
            }

        if (targetItem != null) {
            onMove(dragged, targetItem.index)
            // Adjust offset so the visual position stays consistent after the swap
            draggedOffset += (draggedItem.offset - targetItem.offset)
            draggedIndex = targetItem.index
        }
    }

    fun onDragEnd() {
        draggedIndex = null
        draggedOffset = 0f
    }
}

/**
 * Creates and remembers a [DragDropListState] tied to the given [lazyListState].
 */
@Composable
fun rememberDragDropListState(
    lazyListState: LazyListState,
    onMove: (Int, Int) -> Unit
): DragDropListState {
    val state = remember(lazyListState) {
        DragDropListState(lazyListState, onMove)
    }
    // Keep the closure reference up-to-date across recompositions
    state.onMove = onMove
    return state
}

/**
 * Modifier that makes a LazyColumn item draggable via long-press.
 *
 * Applies visual feedback (scale, elevation, z-order) while dragging
 * and translates the item by the accumulated drag offset.
 */
fun Modifier.dragDropItem(
    state: DragDropListState,
    index: Int
): Modifier = this
    .zIndex(if (state.draggedIndex == index) 1f else 0f)
    .graphicsLayer {
        if (state.draggedIndex == index) {
            translationY = state.draggedOffset
            scaleX = 1.03f
            scaleY = 1.03f
            shadowElevation = 16f
            alpha = 0.92f
        }
    }
    .pointerInput(index) {
        detectDragGesturesAfterLongPress(
            onDragStart = { state.onDragStart(index) },
            onDrag = { change, offset ->
                change.consume()
                state.onDrag(offset.y)
            },
            onDragEnd = { state.onDragEnd() },
            onDragCancel = { state.onDragEnd() }
        )
    }
