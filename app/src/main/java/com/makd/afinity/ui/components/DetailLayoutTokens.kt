package com.makd.afinity.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.makd.afinity.navigation.LocalPlayerOffset

val DetailGutter = 16.dp

val DetailCoverSize = 200.dp

@Immutable
data class DetailEdgePadding(val start: Dp = 0.dp, val end: Dp = 0.dp) {
    fun asPaddingValues(): PaddingValues = PaddingValues(start = start, end = end)

    companion object {
        val Zero = DetailEdgePadding()
    }
}

@Suppress("ComposeCompositionLocalUsage")
val LocalDetailEdgePadding = staticCompositionLocalOf { DetailEdgePadding.Zero }

@Composable
fun bottomClearance(): Dp {
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return max(navBarBottom, LocalPlayerOffset.current)
}

@Composable
fun playerClearance(): Dp {
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return (LocalPlayerOffset.current - navBarBottom).coerceAtLeast(0.dp)
}

@Composable fun detailListBottomPadding(): Dp = bottomClearance() + 16.dp
