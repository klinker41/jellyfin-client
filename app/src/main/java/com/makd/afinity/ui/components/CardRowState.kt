package com.makd.afinity.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Returns horizontal nested prefetch count tuned for folded/phone vs unfolded/tablet widths. */
fun nestedPrefetchCountForWidth(screenWidthDp: Int): Int =
    when {
        screenWidthDp < 600 -> 3
        screenWidthDp < 840 -> 5
        else -> 6
    }

/** Remembers a [LazyListState] for horizontal card rows with adaptive nested prefetching. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberCardRowListState(
    nestedPrefetchItemCount: Int = nestedPrefetchCountForWidth(windowWidthDp().value.toInt()),
): LazyListState {
    val prefetchStrategy =
        remember(nestedPrefetchItemCount) {
            LazyListPrefetchStrategy(nestedPrefetchItemCount = nestedPrefetchItemCount)
        }
    return rememberLazyListState(prefetchStrategy = prefetchStrategy)
}
