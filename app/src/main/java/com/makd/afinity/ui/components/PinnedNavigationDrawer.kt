package com.makd.afinity.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val PinnedDrawerMinWindowWidth = 600.dp
internal val PinnedDrawerMinWindowHeight = 500.dp

val PinnedDrawerWidth = 240.dp
val PinnedDrawerInset = 16.dp
val PinnedDrawerCornerRadius = 24.dp
val PinnedDrawerStartOccupancy = PinnedDrawerInset + PinnedDrawerWidth
val PinnedDrawerShape = RoundedCornerShape(PinnedDrawerCornerRadius)

@Suppress("ComposeCompositionLocalUsage")
val LocalPinnedDrawerStartInset = staticCompositionLocalOf { 0.dp }

internal fun shouldPinNavigationDrawer(
    navigationDrawerEnabled: Boolean,
    windowWidth: Dp,
    windowHeight: Dp,
): Boolean =
    navigationDrawerEnabled &&
        windowWidth >= PinnedDrawerMinWindowWidth &&
        windowHeight >= PinnedDrawerMinWindowHeight

@Composable
@ReadOnlyComposable
fun shouldPinNavigationDrawer(navigationDrawerEnabled: Boolean): Boolean =
    shouldPinNavigationDrawer(
        navigationDrawerEnabled = navigationDrawerEnabled,
        windowWidth = windowWidthDp(),
        windowHeight = windowHeightDp(),
    )

@Composable
fun AppPinnedNavigationDrawer(
    blurState: PinnedDrawerBlurState,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    fallbackBackgroundColor: Color = MaterialTheme.colorScheme.background,
    tintAlpha: Float = DefaultPinnedDrawerTintAlpha,
    content: @Composable () -> Unit,
) {
    val safeInsets =
        WindowInsets.statusBars
            .union(WindowInsets.navigationBars)
            .union(WindowInsets.displayCutout.only(WindowInsetsSides.Start))

    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .windowInsetsPadding(safeInsets)
                .padding(
                    start = PinnedDrawerInset,
                    top = PinnedDrawerInset,
                    bottom = PinnedDrawerInset,
                ),
    ) {
        Box(
            modifier =
                Modifier.width(PinnedDrawerWidth)
                    .fillMaxHeight()
                    .shadow(elevation = 10.dp, shape = PinnedDrawerShape, clip = false)
                    .pinnedDrawerBlurBackdrop(
                        state = blurState,
                        shape = PinnedDrawerShape,
                        containerColor = containerColor,
                        fallbackBackgroundColor = fallbackBackgroundColor,
                        tintAlpha = tintAlpha,
                        borderColor =
                            MaterialTheme.colorScheme.onSurface.copy(
                                alpha = DefaultPinnedDrawerBorderAlpha,
                            ),
                        borderWidth = 1.dp,
                    )
                    .clip(PinnedDrawerShape)
                    .pointerInput(Unit) {},
        ) {
            content()
        }
    }
}
