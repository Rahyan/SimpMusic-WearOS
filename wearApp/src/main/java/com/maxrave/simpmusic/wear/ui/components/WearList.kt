package com.maxrave.simpmusic.wear.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.MaterialTheme

@Composable
fun WearList(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() },
    // Leave room for `TimeText()` in `AppScaffold`.
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, end = 16.dp, top = 34.dp, bottom = 46.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    showClockScrim: Boolean = true,
    showScrollIndicator: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val rotaryBehavior = RotaryScrollableDefaults.behavior(scrollableState = state)

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .rotaryScrollable(
                        behavior = rotaryBehavior,
                        focusRequester = focusRequester,
                    ).focusRequester(focusRequester)
                    .focusable(),
            state = state,
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            content = content,
        )

        // Keep scrolled content from showing through the clock's reserved top inset.
        if (showClockScrim) {
            val background = MaterialTheme.colorScheme.background
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(34.dp).background(
                Brush.verticalGradient(0f to background, .65f to background, 1f to background.copy(alpha = 0f)),
            ))
        }

        if (showScrollIndicator) {
            ScrollIndicator(
                state = state,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}
