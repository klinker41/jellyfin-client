package com.makd.afinity.ui.utils

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Suppress("ComposeCompositionLocalUsage")
val LocalCardRowInitialFocusRequester = staticCompositionLocalOf<FocusRequester?> { null }

@Suppress("ComposeCompositionLocalUsage")
val LocalCardRowFocusFirstOnEnter = staticCompositionLocalOf { false }

fun Modifier.tvFocusFirstOnEnter(
    firstItemFocusRequester: FocusRequester,
    coroutineScope: CoroutineScope,
    isScrolledPastFirst: () -> Boolean,
    scrollToFirst: suspend () -> Unit,
): Modifier =
    this.focusProperties {
            onEnter = {
                val focusedFirst =
                    try {
                        firstItemFocusRequester.requestFocus()
                    } catch (_: IllegalStateException) {
                        false
                    }
                if (!focusedFirst && isScrolledPastFirst()) {
                    cancelFocusChange()
                    coroutineScope.launch {
                        scrollToFirst()
                        requestFocusSafely(firstItemFocusRequester)
                    }
                }
            }
        }
        .focusGroup()

internal suspend fun requestFocusSafely(focusRequester: FocusRequester): Boolean {
    val focused =
        try {
            focusRequester.requestFocus()
        } catch (_: IllegalStateException) {
            false
        }
    if (focused) return true
    withFrameNanos {}
    return try {
        focusRequester.requestFocus()
    } catch (_: IllegalStateException) {
        false
    }
}

@Composable
fun rememberTvInitialFocusRequester(enabled: Boolean = true, key: Any? = Unit): FocusRequester? {
    if (!LocalIsTelevision.current) return null

    val focusRequester = remember(key) { FocusRequester() }
    val isWindowFocused = LocalWindowInfo.current.isWindowFocused
    var hasFocusedInActiveWindow by rememberSaveable(key) { mutableStateOf(false) }

    LaunchedEffect(enabled, key, isWindowFocused) {
        if (!enabled || hasFocusedInActiveWindow) return@LaunchedEffect
        val focused = requestFocusSafely(focusRequester)
        if (focused && isWindowFocused) {
            hasFocusedInActiveWindow = true
        }
    }

    return focusRequester
}
