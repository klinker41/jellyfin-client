package com.makd.afinity.ui.preview

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

private const val NIGHT = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL

@Preview(name = "Phone 411dp", group = "Components", widthDp = 411)
@Preview(name = "Phone 411dp dark", group = "Components", widthDp = 411, uiMode = NIGHT)
@Preview(name = "Foldable 673dp", group = "Components", widthDp = 673)
@Preview(name = "Tablet 840dp", group = "Components", widthDp = 840)
@Preview(name = "320dp font 150pct", group = "Components", widthDp = 320, fontScale = 1.5f)
annotation class ScreenshotComponentPreviews

@Preview(name = "Phone portrait", group = "Windows", device = "spec:width=411dp,height=891dp")
@Preview(
    name = "Phone portrait dark",
    group = "Windows",
    device = "spec:width=411dp,height=891dp",
    uiMode = NIGHT,
)
@Preview(name = "Phone landscape", group = "Windows", device = "spec:width=891dp,height=411dp")
@Preview(name = "Tablet landscape", group = "Windows", device = "spec:width=1280dp,height=800dp")
@Preview(name = "Foldable unfolded", group = "Windows", device = "spec:width=673dp,height=841dp", uiMode = NIGHT)
@Preview(
    name = "Phone font 150pct dark",
    group = "Windows",
    device = "spec:width=411dp,height=891dp",
    fontScale = 1.5f,
    uiMode = NIGHT,
)
annotation class ScreenshotWindowPreviews

@Preview(name = "Phone portrait", group = "FullScreens", device = "spec:width=411dp,height=891dp")
@Preview(
    name = "Phone portrait dark",
    group = "FullScreens",
    device = "spec:width=411dp,height=891dp",
    uiMode = NIGHT,
)
@Preview(
    name = "Phone landscape",
    group = "FullScreens",
    device = "spec:width=891dp,height=411dp",
    uiMode = NIGHT,
)
@Preview(
    name = "Foldable unfolded",
    group = "FullScreens",
    device = "spec:width=673dp,height=841dp",
    uiMode = NIGHT,
)
@Preview(
    name = "Tablet portrait",
    group = "FullScreens",
    device = "spec:width=800dp,height=1280dp",
)
@Preview(
    name = "Tablet landscape",
    group = "FullScreens",
    device = "spec:width=1280dp,height=800dp",
    uiMode = NIGHT,
)
@Preview(
    name = "Phone font 150pct dark",
    group = "FullScreens",
    device = "spec:width=411dp,height=891dp",
    fontScale = 1.5f,
    uiMode = NIGHT,
)
annotation class ScreenshotFullScreenPreviews

@Preview(name = "Phone 411dp", group = "Phone", widthDp = 411)
annotation class ScreenshotPhonePreview
