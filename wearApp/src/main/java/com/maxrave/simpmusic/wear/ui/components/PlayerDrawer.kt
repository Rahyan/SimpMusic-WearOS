package com.maxrave.simpmusic.wear.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/** One continuous position shared by the collapsed handle, expanded handle and list overscroll. */
@Stable
class PlayerDrawerState(initialExpanded: Boolean = false) {
    var fraction by mutableFloatStateOf(if (initialExpanded) 1f else 0f)
        private set
    var expanded by mutableStateOf(initialExpanded)
        private set
    private var startedExpanded = initialExpanded
    var dragging by mutableStateOf(false)
        private set
    private var animation: Job? = null

    fun startDrag() {
        animation?.cancel()
        startedExpanded = fraction >= .5f
        dragging = true
    }

    fun dragBy(pixels: Float, height: Float): Float {
        if (height <= 0f) return 0f
        if (!dragging) startDrag()
        val previous = fraction
        fraction = (fraction - pixels / height).coerceIn(0f, 1f)
        return (previous - fraction) * height
    }

    fun settle(scope: CoroutineScope, height: Float, velocity: Float, flingThreshold: Float): Job {
        val open = if (abs(velocity) >= flingThreshold) velocity < 0f
            else if (startedExpanded) fraction > .78f else fraction > .22f
        return moveTo(scope, open, -velocity / height.coerceAtLeast(1f))
    }

    fun moveTo(scope: CoroutineScope, open: Boolean, velocity: Float = 0f): Job {
        animation?.cancel()
        dragging = false
        expanded = open
        return scope.launch {
            animate(fraction, if (open) 1f else 0f, velocity,
                spring(dampingRatio = 1f, stiffness = 500f)) { value, _ -> fraction = value.coerceIn(0f, 1f) }
        }.also { animation = it }
    }

    companion object {
        val Saver = Saver<PlayerDrawerState, Boolean>(save = { it.expanded }, restore = { PlayerDrawerState(it) })
    }
}

/** Slides the whole sheet with the finger; its list only hands off downward drags at the top. */
@Composable
fun PlayerDrawer(state: PlayerDrawerState, listState: LazyListState, color: Color,
    player: @Composable (Modifier) -> Unit, content: LazyListScope.() -> Unit) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val visible by remember(state) { derivedStateOf { state.fraction > 0f || state.dragging } }
    val betweenAnchors by remember(state) { derivedStateOf { state.fraction > 0f && state.fraction < 1f } }
    val touchContact = remember { mutableStateOf(false) }
    BackHandler(visible) { state.moveTo(scope, false) }
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val height = with(density) { maxHeight.toPx() }
        val sidePadding = if (maxWidth < 210.dp) 16.dp else 24.dp
        val flingThreshold = with(density) { 400.dp.toPx() }
        val drag = Modifier.draggable(
            state = rememberDraggableState { state.dragBy(it, height) }, orientation = Orientation.Vertical,
            startDragImmediately = betweenAnchors,
            onDragStarted = { state.startDrag() },
            onDragStopped = { state.settle(scope, height, it, flingThreshold).join() })
        Box(if (visible) Modifier.clearAndSetSemantics {} else Modifier) {
            player(drag.graphicsLayer { alpha = 1f - state.fraction })
        }
        if (visible) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = state.fraction * .25f }
                .background(Color.Black).clickable { state.moveTo(scope, false) })
            val connection = remember(state, listState, height, flingThreshold) {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (source != NestedScrollSource.UserInput || !touchContact.value) return Offset.Zero
                        val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                        return if ((available.y < 0 && state.fraction < 1f) || (available.y > 0 && atTop))
                            Offset(0f, state.dragBy(available.y, height)) else Offset.Zero
                    }
                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                        // The list can reach its top midway through a drag. Use that same drag's
                        // remaining distance instead of requiring a second gesture to move the sheet.
                        return if (source == NestedScrollSource.UserInput && touchContact.value && available.y > 0f)
                            Offset(0f, state.dragBy(available.y, height)) else Offset.Zero
                    }
                    override suspend fun onPreFling(available: Velocity): Velocity {
                        if (state.dragging || (state.fraction > 0f && state.fraction < 1f)) {
                            state.settle(scope, height, available.y, flingThreshold).join()
                            return Velocity(0f, available.y)
                        }
                        return Velocity.Zero
                    }
                }
            }
            Column(Modifier.fillMaxSize().graphicsLayer { translationY = height * (1f - state.fraction) }
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(color)
                .pointerInput(touchContact) {
                    // A rotary scroll at the list boundary must not pull the drawer closed.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        touchContact.value = true
                        try {
                            do { val event = awaitPointerEvent(PointerEventPass.Initial) }
                            while (event.changes.any { it.pressed })
                        } finally { touchContact.value = false }
                    }
                }
                .semantics { paneTitle = "Player controls" }) {
                // Keep a generous, fixed handle target even when lyrics/the queue are scrolled.
                Box(Modifier.fillMaxWidth().height(44.dp).then(drag)
                    .clickable(role = Role.Button, onClickLabel = "Close player controls") { state.moveTo(scope, false) }
                    .semantics { contentDescription = "Player drawer handle" }
                    .padding(top = 22.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(28.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = .65f)))
                }
                WearList(Modifier.weight(1f).padding(horizontal = sidePadding).nestedScroll(connection),
                    state = listState, contentPadding = PaddingValues(top = 0.dp, bottom = 26.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
            }
        }
    }
}
