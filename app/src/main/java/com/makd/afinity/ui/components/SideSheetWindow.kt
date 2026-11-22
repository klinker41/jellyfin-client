package com.makd.afinity.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.navigation.LocalSideSheetEnabled

internal val SideSheetWideWindowMinWidth = 600.dp

internal fun shouldUseSideSheet(
    sideSheetEnabled: Boolean,
    windowWidth: Dp,
    windowHeight: Dp,
): Boolean =
    sideSheetEnabled && (windowWidth > windowHeight || windowWidth >= SideSheetWideWindowMinWidth)

@Composable
@ReadOnlyComposable
fun shouldUseSideSheet(): Boolean =
    shouldUseSideSheet(
        sideSheetEnabled = LocalSideSheetEnabled.current,
        windowWidth = windowWidthDp(),
        windowHeight = windowHeightDp(),
    )
