package com.makd.afinity.tvlauncher

import android.content.Context
import com.makd.afinity.testutil.MainDispatcherRule
import com.makd.afinity.util.NetworkConnectivityMonitor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TvLauncherViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private class Harness {
        val statusFlow = MutableStateFlow(TvLauncherStatus())
        val appsFlow = MutableStateFlow<List<TvInstalledApp>>(emptyList())
        val networkFlow = MutableStateFlow(true)

        val tvLauncherManager: TvLauncherManager =
            mockk(relaxed = true) { every { status } returns statusFlow }
        val installedAppsRepository: TvInstalledAppsRepository =
            mockk(relaxed = true) { every { apps } returns appsFlow }
        val networkConnectivityMonitor: NetworkConnectivityMonitor = mockk {
            every { isNetworkAvailable } returns networkFlow
        }

        fun createViewModel(): TvLauncherViewModel =
            TvLauncherViewModel(
                tvLauncherManager = tvLauncherManager,
                installedAppsRepository = installedAppsRepository,
                networkConnectivityMonitor = networkConnectivityMonitor,
            )
    }

    @Test
    fun `onToggleLauncher true enables alias, refreshes apps, and clears blocked dialog`() {
        val harness = Harness()
        val blocked =
            DisableLauncherResult.BlockedNoOtherLauncher(
                stockLauncherPackage = "com.google.android.apps.tv.launcherx",
                restoreCommand = "adb shell pm enable com.google.android.apps.tv.launcherx",
            )
        every { harness.tvLauncherManager.disableAlias(any()) } returns blocked
        val viewModel = harness.createViewModel()

        viewModel.onToggleLauncher(false)
        assertEquals(blocked, viewModel.disableBlocked.value)

        viewModel.onToggleLauncher(true)
        verify(exactly = 1) { harness.tvLauncherManager.enableAlias() }
        verify(exactly = 1) { harness.installedAppsRepository.refresh() }
        assertNull(viewModel.disableBlocked.value)
    }

    @Test
    fun `onToggleLauncher false clears dialog on success and shows dialog when blocked`() {
        val harness = Harness()
        val blocked =
            DisableLauncherResult.BlockedNoOtherLauncher(
                stockLauncherPackage = "com.google.android.apps.tv.launcherx",
                restoreCommand = "adb shell pm enable com.google.android.apps.tv.launcherx",
            )
        every { harness.tvLauncherManager.disableAlias(any()) } returnsMany
            listOf(blocked, DisableLauncherResult.Disabled)
        val viewModel = harness.createViewModel()

        viewModel.onToggleLauncher(false)
        assertEquals(blocked, viewModel.disableBlocked.value)

        viewModel.retryDisableAfterCheck()
        assertNull(viewModel.disableBlocked.value)
    }

    @Test
    fun `retryDisableAfterCheck keeps dialog when still blocked and dismiss clears it`() {
        val harness = Harness()
        val blocked =
            DisableLauncherResult.BlockedNoOtherLauncher(
                stockLauncherPackage = "com.google.android.apps.tv.launcherx",
                restoreCommand = "adb shell pm enable com.google.android.apps.tv.launcherx",
            )
        every { harness.tvLauncherManager.disableAlias(any()) } returns blocked
        val viewModel = harness.createViewModel()

        viewModel.onToggleLauncher(false)
        assertEquals(blocked, viewModel.disableBlocked.value)

        viewModel.retryDisableAfterCheck()
        assertEquals(blocked, viewModel.disableBlocked.value)

        viewModel.dismissDisableBlockedDialog()
        assertNull(viewModel.disableBlocked.value)
    }

    @Test
    fun `refresh and launchApp delegate to manager and repository`() {
        val harness = Harness()
        val viewModel = harness.createViewModel()
        val context = mockk<Context>(relaxed = true)
        val app =
            TvInstalledApp(
                packageName = "com.example.app",
                activityName = "MainActivity",
                label = "Example",
                isLeanback = true,
                isSystemApp = false,
            )

        viewModel.refreshStatus()
        viewModel.refreshApps()
        viewModel.launchApp(context, app)

        verify(exactly = 1) { harness.tvLauncherManager.refresh() }
        verify(exactly = 1) { harness.installedAppsRepository.refresh() }
        verify(exactly = 1) { harness.installedAppsRepository.launchApp(context, app) }
    }

    @Test
    fun `exposed state flows reflect manager, repository, and network monitor`() {
        val harness = Harness()
        val viewModel = harness.createViewModel()
        val expectedApp =
            TvInstalledApp(
                packageName = "com.example.tv",
                activityName = "Main",
                label = "TV App",
                isLeanback = true,
                isSystemApp = false,
            )

        harness.statusFlow.value = TvLauncherStatus(aliasEnabled = true, isDefaultLauncher = true)
        harness.appsFlow.value = listOf(expectedApp)
        harness.networkFlow.value = false

        assertTrue(viewModel.status.value.isDefaultLauncher)
        assertEquals(listOf(expectedApp), viewModel.apps.value)
        assertEquals(false, viewModel.isNetworkAvailable.value)
    }
}
