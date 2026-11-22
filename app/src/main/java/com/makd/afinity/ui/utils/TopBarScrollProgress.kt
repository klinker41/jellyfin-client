package com.makd.afinity.ui.utils

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val DefaultHeroTopBarBlurThreshold = 450.dp
internal val DefaultNoHeroTopBarBlurThreshold = 64.dp

internal fun calculateTopBarScrollProgress(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    firstVisibleItemSizePx: Int,
    hasHeroSlot: Boolean,
    heroThresholdPx: Float,
    noHeroThresholdPx: Float,
    rampItemIndex: Int = if (hasHeroSlot) 0 else 1,
): Float {
    val thresholdPx = if (hasHeroSlot) heroThresholdPx else noHeroThresholdPx
    return when {
        firstVisibleItemIndex < rampItemIndex -> 0f
        firstVisibleItemIndex > rampItemIndex -> 1f
        firstVisibleItemScrollOffset <= 0 -> 0f
        else -> {
            val effectiveThreshold =
                if (firstVisibleItemSizePx > 0) {
                    minOf(thresholdPx, firstVisibleItemSizePx.toFloat())
                } else {
                    thresholdPx
                }
            if (effectiveThreshold <= 0f) {
                1f
            } else {
                (firstVisibleItemScrollOffset / effectiveThreshold).coerceIn(0f, 1f)
            }
        }
    }
}

/**
 * Computes a compact, allocation-free scroll token for [rememberTopBarBlurState] so the cropped
 * blur backdrop continues refreshing as the list scrolls past the `1f` threshold.
 */
fun LazyListState.topBarScrollKey(): Int =
    (firstVisibleItemIndex shl 16) xor firstVisibleItemScrollOffset

/**
 * Tracks scroll progress (`0f..1f`) for the top app bar backdrop blur on a [LazyListState].
 *
 * When [hasHeroSlot] is `true`, progress ramps over [heroScrollThreshold] as [rampItemIndex]
 * (default `0`, the hero item) scrolls off-screen. When [hasHeroSlot] is `false`, [rampItemIndex]
 * defaults to `1` to match layouts that keep an empty index-`0` hero item slot and ramps over
 * [noHeroScrollThreshold] (pass `rampItemIndex = 0` for lists without a placeholder item).
 */
@Composable
fun rememberTopBarScrollProgress(
    state: LazyListState,
    hasHeroSlot: Boolean,
    heroScrollThreshold: Dp = DefaultHeroTopBarBlurThreshold,
    noHeroScrollThreshold: Dp = DefaultNoHeroTopBarBlurThreshold,
    rampItemIndex: Int = if (hasHeroSlot) 0 else 1,
): State<Float> {
    val density = LocalDensity.current
    val heroThresholdPx =
        remember(density, heroScrollThreshold) { with(density) { heroScrollThreshold.toPx() } }
    val noHeroThresholdPx =
        remember(density, noHeroScrollThreshold) { with(density) { noHeroScrollThreshold.toPx() } }

    return remember(state, hasHeroSlot, heroThresholdPx, noHeroThresholdPx, rampItemIndex) {
        derivedStateOf {
            val firstIndex = state.firstVisibleItemIndex
            val firstOffset = state.firstVisibleItemScrollOffset
            val firstSizePx =
                if (firstIndex == rampItemIndex && firstOffset > 0) {
                    state.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.index == rampItemIndex }
                        ?.size ?: 0
                } else {
                    0
                }

            calculateTopBarScrollProgress(
                firstVisibleItemIndex = firstIndex,
                firstVisibleItemScrollOffset = firstOffset,
                firstVisibleItemSizePx = firstSizePx,
                hasHeroSlot = hasHeroSlot,
                heroThresholdPx = heroThresholdPx,
                noHeroThresholdPx = noHeroThresholdPx,
                rampItemIndex = rampItemIndex,
            )
        }
    }
}
