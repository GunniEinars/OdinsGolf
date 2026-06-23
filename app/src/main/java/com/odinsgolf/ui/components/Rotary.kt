package com.odinsgolf.ui.components

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable

/**
 * Lets the Galaxy Watch's rotating/touch bezel scroll a list or scrollable column.
 * Apply to the same element that owns [scrollableState] (a ScalingLazyListState or
 * a ScrollState). Uses the active focus requester so the screen receives rotary input.
 */
@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun Modifier.rotaryScroll(scrollableState: ScrollableState): Modifier {
    val focusRequester = rememberActiveFocusRequester()
    return this.rotaryScrollable(
        RotaryScrollableDefaults.behavior(scrollableState = scrollableState),
        focusRequester = focusRequester,
    )
}
