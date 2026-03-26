package com.flowsense.app.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

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
): Modifier {
    val isDragged = state.draggedIndex == index
    return this
        .zIndex(if (isDragged) 1f else 0f)
        .then(if (isDragged) Modifier.shadow(8.dp) else Modifier)
        .offset { IntOffset(0, if (isDragged) state.draggedOffset.roundToInt() else 0) }
        .scale(if (isDragged) 1.03f else 1f)
        .alpha(if (isDragged) 0.92f else 1f)
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
}
