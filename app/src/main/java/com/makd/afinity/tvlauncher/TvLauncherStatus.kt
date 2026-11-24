package com.makd.afinity.tvlauncher

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
data class TvLauncherStatus(
    val aliasEnabled: Boolean = false,
    val isDefaultLauncher: Boolean = false,
    val otherEnabledHomePackages: List<String> = emptyList(),
    val setHomeCommand: String = "",
    val stockLauncherPackage: String? = null,
) {
    val isLauncherUiActive: Boolean
        get() = aliasEnabled || isDefaultLauncher

    val canSafelyDisableAlias: Boolean
        get() = otherEnabledHomePackages.isNotEmpty()
}

sealed interface DisableLauncherResult {
    data object Disabled : DisableLauncherResult

    data class BlockedNoOtherLauncher(
        val stockLauncherPackage: String,
        val restoreCommand: String,
    ) : DisableLauncherResult
}

@Suppress("ComposeCompositionLocalUsage")
val LocalTvLauncherStatus = staticCompositionLocalOf { TvLauncherStatus() }
