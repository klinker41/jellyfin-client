package com.makd.afinity.tvlauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import androidx.compose.runtime.Immutable
import com.makd.afinity.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@Immutable
data class TvInstalledApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val isLeanback: Boolean,
    val isSystemApp: Boolean,
)

@Singleton
class TvInstalledAppsRepository
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val refreshVersion = MutableStateFlow(0L)

    val apps: StateFlow<List<TvInstalledApp>> = callbackFlow {
        fun emitLoaded() {
            val loaded = loadInstalledApps(context.packageManager, context.packageName)
            trySend(loaded)
        }

        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val callback =
            object : LauncherApps.Callback() {
                override fun onPackageRemoved(packageName: String, user: UserHandle) {
                    TvAppArtworkCache.evict(packageName)
                    refresh()
                }

                override fun onPackageAdded(packageName: String, user: UserHandle) {
                    TvAppArtworkCache.evict(packageName)
                    refresh()
                }

                override fun onPackageChanged(packageName: String, user: UserHandle) {
                    TvAppArtworkCache.evict(packageName)
                    refresh()
                }

                override fun onPackagesAvailable(
                    packageNames: Array<out String>,
                    user: UserHandle,
                    replacing: Boolean,
                ) {
                    packageNames.forEach(TvAppArtworkCache::evict)
                    refresh()
                }

                override fun onPackagesUnavailable(
                    packageNames: Array<out String>,
                    user: UserHandle,
                    replacing: Boolean,
                ) {
                    packageNames.forEach(TvAppArtworkCache::evict)
                    refresh()
                }
            }

        launcherApps?.registerCallback(callback, Handler(Looper.getMainLooper()))
        val refreshJob = launch { refreshVersion.collect { emitLoaded() } }

        awaitClose {
            refreshJob.cancel()
            launcherApps?.unregisterCallback(callback)
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun refresh() {
        refreshVersion.update { it + 1L }
    }

    fun launchApp(context: Context, app: TvInstalledApp): Boolean {
        val explicitIntent =
            Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName(app.packageName, app.activityName)
                addCategory(
                    if (app.isLeanback) Intent.CATEGORY_LEANBACK_LAUNCHER
                    else Intent.CATEGORY_LAUNCHER,
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
        val launchedExplicit = runCatching {
            context.startActivity(explicitIntent)
        }
            .onFailure {
                Timber.w(it, "Explicit launch failed for ${app.packageName}/${app.activityName}")
            }
            .isSuccess
        if (launchedExplicit) return true

        val pm = context.packageManager
        val fallbackIntent =
            pm.getLeanbackLaunchIntentForPackage(app.packageName)
                ?: pm.getLaunchIntentForPackage(app.packageName)
        if (fallbackIntent == null) {
            Timber.w("No launch intent found for ${app.packageName}")
            return false
        }
        fallbackIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
        )
        return runCatching { context.startActivity(fallbackIntent) }
            .onFailure { Timber.w(it, "Fallback launch failed for ${app.packageName}") }
            .isSuccess
    }
}

internal fun loadInstalledApps(
    packageManager: PackageManager,
    selfPackageName: String,
): List<TvInstalledApp> {
    val flags = PackageManager.ResolveInfoFlags.of(0L)
    val leanbackInfos =
        packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER) },
            flags,
        )
    val standardInfos =
        packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
            flags,
        )

    val byPackage = LinkedHashMap<String, TvInstalledApp>()

    fun addResolveInfo(resolveInfo: ResolveInfo, isLeanback: Boolean) {
        val activityInfo = resolveInfo.activityInfo ?: return
        val pkg = activityInfo.packageName
        val activityName = activityInfo.name
        if (pkg.isNullOrBlank() || activityName.isNullOrBlank()) return
        if (pkg == selfPackageName) return
        val existing = byPackage[pkg]
        if (existing != null && (existing.isLeanback || !isLeanback)) return

        val label = runCatching {
            resolveInfo.loadLabel(packageManager).toString()
        }
            .getOrNull()
            .orEmpty()
            .ifBlank { pkg }
        val appFlags = activityInfo.applicationInfo?.flags ?: 0
        val isSystemApp = (appFlags and ApplicationInfo.FLAG_SYSTEM) != 0

        byPackage[pkg] =
            TvInstalledApp(
                packageName = pkg,
                activityName = activityName,
                label = label,
                isLeanback = isLeanback,
                isSystemApp = isSystemApp,
            )
    }

    leanbackInfos.forEach { addResolveInfo(it, isLeanback = true) }
    standardInfos.forEach { addResolveInfo(it, isLeanback = false) }

    return byPackage.values.sortedWith(
        compareByDescending<TvInstalledApp> { it.isLeanback }.thenBy { it.label.lowercase() },
    )
}
