package com.makd.afinity.ui.components

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.makd.afinity.navigation.Destination

internal val PinnedDrawerMinWindowWidth = 600.dp
internal val PinnedDrawerMinWindowHeight = 500.dp

val PinnedDrawerWidth = 240.dp
val PinnedDrawerInset = 16.dp
val PinnedDrawerCornerRadius = 24.dp
val PinnedDrawerStartOccupancy = PinnedDrawerInset + PinnedDrawerWidth
val PinnedDrawerShape = RoundedCornerShape(PinnedDrawerCornerRadius)

@Suppress("ComposeCompositionLocalUsage")
val LocalPinnedDrawerStartInset = staticCompositionLocalOf { 0.dp }

@Suppress("ComposeCompositionLocalUsage")
internal val LocalPinnedDrawerWindowInset = staticCompositionLocalOf { 0.dp }

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

internal fun shouldHidePinnedDrawerForRoute(route: String?): Boolean =
    route == null ||
        route == Destination.SPLASH_ROUTE ||
        route == Destination.LOGIN_ROUTE ||
        route == Destination.SERVICES_HUB_ROUTE ||
        route == Destination.AUDIOBOOKSHELF_LOGIN_ROUTE ||
        route == Destination.MUSIC_PLAYER_ROUTE ||
        route == Destination.BOOK_READER_ROUTE ||
        route == Destination.PHOTO_VIEWER_ROUTE ||
        route.startsWith("audiobookshelf/player/")

@Composable
fun PinnedDrawerContentContainer(
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val drawerStartInset = LocalPinnedDrawerStartInset.current
    if (drawerStartInset > 0.dp) {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(backgroundColor)
                    .padding(start = drawerStartInset),
        ) {
            CompositionLocalProvider(
                LocalPinnedDrawerStartInset provides 0.dp,
                LocalPinnedDrawerWindowInset provides
                    max(LocalPinnedDrawerWindowInset.current, drawerStartInset),
            ) {
                content()
            }
        }
    } else {
        Box(modifier = modifier.fillMaxSize()) { content() }
    }
}

internal fun NavGraphBuilder.pinnedDrawerComposable(
    route: String,
    backgroundColor: @Composable () -> Color,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable(route) { backStackEntry ->
        PinnedDrawerContentContainer(backgroundColor = backgroundColor()) {
            content(backStackEntry)
        }
    }
}

internal fun computePinnedDrawerTopInset(statusBarTop: Dp): Dp =
    max(statusBarTop, PinnedDrawerInset)

internal fun computePinnedDrawerBottomInset(tappableBottom: Dp): Dp =
    tappableBottom + PinnedDrawerInset

@Composable
fun AppPinnedNavigationDrawer(
    blurState: PinnedDrawerBlurState,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    fallbackBackgroundColor: Color = MaterialTheme.colorScheme.background,
    tintAlpha: Float = DefaultPinnedDrawerTintAlpha,
    content: @Composable () -> Unit,
) {
    val startCutout = WindowInsets.displayCutout.only(WindowInsetsSides.Start)
    val topInset =
        computePinnedDrawerTopInset(
            statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
        )
    val bottomInset =
        computePinnedDrawerBottomInset(
            tappableBottom =
                WindowInsets.tappableElement.asPaddingValues().calculateBottomPadding(),
        )

    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .windowInsetsPadding(startCutout)
                .padding(
                    start = PinnedDrawerInset,
                    top = topInset,
                    bottom = bottomInset,
                ),
    ) {
        Box(
            modifier =
                Modifier.width(PinnedDrawerWidth)
                    .fillMaxHeight()
                    .pinnedDrawerBlurBackdrop(
                        state = blurState,
                        shape = PinnedDrawerShape,
                        containerColor = containerColor,
                        fallbackBackgroundColor = fallbackBackgroundColor,
                        tintAlpha = tintAlpha,
                    )
                    .clip(PinnedDrawerShape)
                    .pointerInput(Unit) {},
        ) {
            content()
        }
    }
}
