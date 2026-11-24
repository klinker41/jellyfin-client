package com.makd.afinity.tvlauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

@Singleton
class TvLauncherManager
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val _status = MutableStateFlow(queryStatus())
    val status: StateFlow<TvLauncherStatus> = _status.asStateFlow()

    fun refresh(): TvLauncherStatus {
        val updated = queryStatus()
        _status.value = updated
        return updated
    }

    fun enableAlias(): TvLauncherStatus {
        val component = ComponentName(context.packageName, ALIAS_CLASS_NAME)
        context.packageManager.setComponentEnabledSetting(
            component,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        return refresh()
    }

    fun disableAlias(force: Boolean = false): DisableLauncherResult {
        val current = refresh()
        if (!force && !current.canSafelyDisableAlias) {
            val stockPkg = current.stockLauncherPackage ?: DEFAULT_GOOGLE_TV_LAUNCHER
            return DisableLauncherResult.BlockedNoOtherLauncher(
                stockLauncherPackage = stockPkg,
                restoreCommand = buildEnableStockLauncherCommand(stockPkg),
            )
        }
        val component = ComponentName(context.packageName, ALIAS_CLASS_NAME)
        context.packageManager.setComponentEnabledSetting(
            component,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        refresh()
        return DisableLauncherResult.Disabled
    }

    private fun queryStatus(): TvLauncherStatus {
        val pm = context.packageManager
        val pkg = context.packageName
        val aliasEnabled = queryIsAliasEnabled(pm, pkg)
        val isDefault = queryIsDefaultHomeLauncher(pm, pkg)
        val otherHomes = queryOtherEnabledHomePackages(pm, pkg)
        val stockLauncher = detectStockLauncherPackage(pm, otherHomes)
        return TvLauncherStatus(
            aliasEnabled = aliasEnabled,
            isDefaultLauncher = isDefault,
            otherEnabledHomePackages = otherHomes,
            setHomeCommand = buildSetHomeActivityCommand(pkg),
            stockLauncherPackage = stockLauncher,
        )
    }

    companion object {
        const val ALIAS_CLASS_NAME = "com.makd.afinity.TvLauncherAlias"
        const val DEFAULT_GOOGLE_TV_LAUNCHER = "com.google.android.apps.tv.launcherx"
        val KNOWN_STOCK_TV_LAUNCHERS =
            listOf(
                DEFAULT_GOOGLE_TV_LAUNCHER,
                "com.google.android.tvlauncher",
            )

        fun openSystemSettings(context: Context): Boolean = runCatching {
            val intent =
                Intent(Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
        }
            .onFailure { Timber.w(it, "Failed to open Android Settings") }
            .isSuccess

        fun openAppInfo(context: Context, packageName: String): Boolean = runCatching {
            val intent =
                Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null),
                    )
                    .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
        }
            .onFailure { Timber.w(it, "Failed to open App Info for $packageName") }
            .isSuccess

        fun uninstallApp(context: Context, packageName: String): Boolean = runCatching {
            val intent =
                Intent(
                        Intent.ACTION_DELETE,
                        Uri.fromParts("package", packageName, null),
                    )
                    .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
        }
            .onFailure { Timber.w(it, "Failed to launch uninstall for $packageName") }
            .isSuccess
    }
}

internal fun buildSetHomeActivityCommand(packageName: String): String =
    "adb shell cmd package set-home-activity $packageName/${TvLauncherManager.ALIAS_CLASS_NAME}"

internal fun buildDisableStockLauncherCommand(stockPackage: String): String =
    "adb shell pm disable-user --user 0 $stockPackage"

internal fun buildEnableStockLauncherCommand(stockPackage: String): String =
    "adb shell pm enable $stockPackage"

internal fun queryIsAliasEnabled(packageManager: PackageManager, packageName: String): Boolean {
    val state =
        packageManager.getComponentEnabledSetting(
            ComponentName(packageName, TvLauncherManager.ALIAS_CLASS_NAME),
        )
    return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
}

internal fun queryIsDefaultHomeLauncher(
    packageManager: PackageManager,
    packageName: String,
): Boolean {
    val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
    val resolved =
        packageManager.resolveActivity(
            intent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
        ) ?: return false
    return resolved.activityInfo?.packageName == packageName
}

internal fun queryOtherEnabledHomePackages(
    packageManager: PackageManager,
    packageName: String,
): List<String> {
    val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
    return packageManager
        .queryIntentActivities(
            intent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
        )
        .mapNotNull { it.activityInfo?.packageName?.takeIf { pkg -> pkg.isNotBlank() } }
        .filter { pkg -> pkg != packageName && pkg != "android" }
        .distinct()
        .sorted()
}

internal fun detectStockLauncherPackage(
    packageManager: PackageManager,
    otherEnabledHomes: List<String>,
): String {
    val activeKnown =
        TvLauncherManager.KNOWN_STOCK_TV_LAUNCHERS.firstOrNull { it in otherEnabledHomes }
    if (activeKnown != null) return activeKnown
    if (otherEnabledHomes.isNotEmpty()) return otherEnabledHomes.first()

    val installedKnown =
        TvLauncherManager.KNOWN_STOCK_TV_LAUNCHERS.firstOrNull { pkg ->
            runCatching {
                packageManager.getPackageInfo(
                    pkg,
                    PackageManager.PackageInfoFlags.of(
                        PackageManager.MATCH_DISABLED_COMPONENTS.toLong(),
                    ),
                )
            }
                .isSuccess
        }
    return installedKnown ?: TvLauncherManager.DEFAULT_GOOGLE_TV_LAUNCHER
}
