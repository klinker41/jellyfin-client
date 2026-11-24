package com.makd.afinity.ui.utils

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.staticCompositionLocalOf

fun Context.isTelevision(): Boolean {
    if (packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
        return true
    }
    val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}

@Suppress("ComposeCompositionLocalUsage") val LocalIsTelevision = staticCompositionLocalOf { false }
