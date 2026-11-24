package com.makd.afinity.tvlauncher

import android.content.Context
import androidx.lifecycle.ViewModel
import com.makd.afinity.util.NetworkConnectivityMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class TvLauncherViewModel
@Inject
constructor(
    private val tvLauncherManager: TvLauncherManager,
    private val installedAppsRepository: TvInstalledAppsRepository,
    networkConnectivityMonitor: NetworkConnectivityMonitor,
) : ViewModel() {

    val status: StateFlow<TvLauncherStatus> = tvLauncherManager.status
    val apps: StateFlow<List<TvInstalledApp>> = installedAppsRepository.apps
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
}
