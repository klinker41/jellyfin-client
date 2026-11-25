package com.makd.afinity.tvlauncher

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.util.NetworkConnectivityMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TvLauncherViewModel
@Inject
constructor(
    private val tvLauncherManager: TvLauncherManager,
    private val installedAppsRepository: TvInstalledAppsRepository,
    private val pinnedAppsStore: TvPinnedAppsStore,
    networkConnectivityMonitor: NetworkConnectivityMonitor,
) : ViewModel() {

    val status: StateFlow<TvLauncherStatus> = tvLauncherManager.status
    val apps: StateFlow<List<TvInstalledApp>> = installedAppsRepository.apps
    val pinnedPackageNames: StateFlow<Set<String>> =
        pinnedAppsStore.pinnedPackages
            .map { it.toSet() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptySet(),
            )
    val pinnedApps: StateFlow<List<TvInstalledApp>> =
        combine(installedAppsRepository.apps, pinnedAppsStore.pinnedPackages) { installed, pinned ->
                resolvePinnedApps(installed, pinned)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )
    val isNetworkAvailable: StateFlow<Boolean> = networkConnectivityMonitor.isNetworkAvailable

    private val _disableBlocked =
        MutableStateFlow<DisableLauncherResult.BlockedNoOtherLauncher?>(null)
    val disableBlocked: StateFlow<DisableLauncherResult.BlockedNoOtherLauncher?> =
        _disableBlocked.asStateFlow()

    fun onToggleLauncher(enabled: Boolean) {
        if (enabled) {
            _disableBlocked.value = null
            tvLauncherManager.enableAlias()
            installedAppsRepository.refresh()
        } else {
            when (val result = tvLauncherManager.disableAlias()) {
                is DisableLauncherResult.Disabled -> _disableBlocked.value = null
                is DisableLauncherResult.BlockedNoOtherLauncher -> _disableBlocked.value = result
            }
        }
    }

    fun retryDisableAfterCheck() {
        when (val result = tvLauncherManager.disableAlias()) {
            is DisableLauncherResult.Disabled -> _disableBlocked.value = null
            is DisableLauncherResult.BlockedNoOtherLauncher -> _disableBlocked.value = result
        }
    }

    fun dismissDisableBlockedDialog() {
        _disableBlocked.value = null
    }

    fun refreshStatus() {
        tvLauncherManager.refresh()
    }

    fun refreshApps() {
        installedAppsRepository.refresh()
    }

    fun launchApp(context: Context, app: TvInstalledApp) {
        installedAppsRepository.launchApp(context, app)
    }

    fun setAppPinned(packageName: String, pinned: Boolean) {
        viewModelScope.launch { pinnedAppsStore.setPinned(packageName, pinned) }
    }
}

internal fun resolvePinnedApps(
    installedApps: List<TvInstalledApp>,
    pinnedPackages: List<String>,
): List<TvInstalledApp> {
    if (installedApps.isEmpty() || pinnedPackages.isEmpty()) return emptyList()
    val byPackage = installedApps.associateBy { it.packageName }
    return pinnedPackages.mapNotNull { byPackage[it] }
}
