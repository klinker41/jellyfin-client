package com.makd.afinity.tvlauncher

import android.content.Context
import com.makd.afinity.testutil.MainDispatcherRule
import com.makd.afinity.util.NetworkConnectivityMonitor
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TvLauncherViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private class Harness {
        val statusFlow = MutableStateFlow(TvLauncherStatus())
        val appsFlow = MutableStateFlow<List<TvInstalledApp>>(emptyList())
        val pinnedPackagesFlow = MutableStateFlow<List<String>>(emptyList())
        val networkFlow = MutableStateFlow(true)

        val tvLauncherManager: TvLauncherManager =
            mockk(relaxed = true) { every { status } returns statusFlow }
        val installedAppsRepository: TvInstalledAppsRepository =
            mockk(relaxed = true) { every { apps } returns appsFlow }
        val pinnedAppsStore: TvPinnedAppsStore =
            mockk(relaxed = true) { every { pinnedPackages } returns pinnedPackagesFlow }
        val networkConnectivityMonitor: NetworkConnectivityMonitor = mockk {
            every { isNetworkAvailable } returns networkFlow
        }

        fun createViewModel(): TvLauncherViewModel =
            TvLauncherViewModel(
                tvLauncherManager = tvLauncherManager,
                installedAppsRepository = installedAppsRepository,
                pinnedAppsStore = pinnedAppsStore,
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

    @Test
    fun `pinnedApps is empty by default, preserves pin order, and ignores uninstalled packages`() =
        runTest {
            val harness = Harness()
            val viewModel = harness.createViewModel()
            val appA =
                TvInstalledApp(
                    packageName = "com.example.a",
                    activityName = "MainA",
                    label = "App A",
                    isLeanback = true,
                    isSystemApp = false,
                )
            val appB =
                TvInstalledApp(
                    packageName = "com.example.b",
                    activityName = "MainB",
                    label = "App B",
                    isLeanback = true,
                    isSystemApp = false,
                )

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.pinnedApps.collect {}
            }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.pinnedPackageNames.collect {}
            }

            harness.appsFlow.value = listOf(appA, appB)
            assertEquals(emptyList<TvInstalledApp>(), viewModel.pinnedApps.value)
            assertEquals(emptySet<String>(), viewModel.pinnedPackageNames.value)

            harness.pinnedPackagesFlow.value =
                listOf("com.example.b", "com.example.missing", "com.example.a")
            assertEquals(listOf(appB, appA), viewModel.pinnedApps.value)
            assertEquals(
                setOf("com.example.b", "com.example.missing", "com.example.a"),
                viewModel.pinnedPackageNames.value,
            )
        }

    @Test
    fun `setAppPinned delegates to pinnedAppsStore`() = runTest {
        val harness = Harness()
        val viewModel = harness.createViewModel()

        viewModel.setAppPinned("com.example.app", pinned = true)
        viewModel.setAppPinned("com.example.app", pinned = false)

        coVerify(exactly = 1) { harness.pinnedAppsStore.setPinned("com.example.app", true) }
        coVerify(exactly = 1) { harness.pinnedAppsStore.setPinned("com.example.app", false) }
    }
}
