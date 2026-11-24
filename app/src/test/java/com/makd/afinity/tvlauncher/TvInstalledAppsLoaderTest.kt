package com.makd.afinity.tvlauncher

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import app.cash.turbine.test
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class TvInstalledAppsLoaderTest {

    private val selfPkg = "xyz.klinker.jellyfin"

    @Test
    fun `loadInstalledApps skips self package from both queries`() {
        val pm = mockk<PackageManager>()
        every { pm.queryIntentActivities(any(), any<PackageManager.ResolveInfoFlags>()) } answers
            {
                val intent = firstArg<Intent>()
                if (intent.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)) {
                    listOf(
                        appResolveInfo(selfPkg, "com.makd.afinity.MainActivity", "Jellyfin", false),
                        appResolveInfo(
                            "com.google.android.youtube.tv",
                            "MainActivity",
                            "YouTube",
                            true,
                        ),
                    )
                } else {
                    listOf(
                        appResolveInfo(selfPkg, "com.makd.afinity.MainActivity", "Jellyfin", false),
                        appResolveInfo("com.example.sideload", "Entry", "Sideload", false),
                    )
                }
            }

        val apps = loadInstalledApps(pm, selfPkg)

        assertEquals(
            listOf("com.google.android.youtube.tv", "com.example.sideload"),
            apps.map { it.packageName },
        )
        assertFalse(apps.any { it.packageName == selfPkg })
    }

    @Test
    fun `loadInstalledApps deduplicates and prefers leanback entry`() {
        val pm = mockk<PackageManager>()
        every { pm.queryIntentActivities(any(), any<PackageManager.ResolveInfoFlags>()) } answers
            {
                val intent = firstArg<Intent>()
                if (intent.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)) {
                    listOf(appResolveInfo("com.netflix.ninja", "TvActivity", "Netflix", true))
                } else {
                    listOf(appResolveInfo("com.netflix.ninja", "PhoneActivity", "Netflix", false))
                }
            }

        val apps = loadInstalledApps(pm, selfPkg)

        assertEquals(1, apps.size)
        assertTrue(apps.first().isLeanback)
        assertEquals("TvActivity", apps.first().activityName)
        assertTrue(apps.first().isSystemApp)
    }

    @Test
    fun `loadInstalledApps filters invalid entries and falls back to packageName when label blank`() {
        val pm = mockk<PackageManager>()
        every { pm.queryIntentActivities(any(), any<PackageManager.ResolveInfoFlags>()) } answers
            {
                val intent = firstArg<Intent>()
                if (intent.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)) {
                    listOf(
                        ResolveInfo(), // null activityInfo
                        appResolveInfo("", "Main", "NoPkg", false),
                        appResolveInfo("pkg.noact", "", "NoAct", false),
                        appResolveInfo("pkg.blanklabel", "Main", "   ", false),
                    )
                } else {
                    emptyList()
                }
            }

        val apps = loadInstalledApps(pm, selfPkg)

        assertEquals(1, apps.size)
        assertEquals("pkg.blanklabel", apps.first().packageName)
        assertEquals("pkg.blanklabel", apps.first().label)
        assertFalse(apps.first().isSystemApp)
    }

    @Test
    fun `loadInstalledApps handles loadLabel exception by falling back to packageName`() {
        val pm = mockk<PackageManager>()
        val brokenInfo =
            mockk<ResolveInfo>().apply {
                activityInfo =
                    ActivityInfo().apply {
                        packageName = "pkg.broken"
                        name = "Main"
                        applicationInfo = ApplicationInfo().apply { packageName = "pkg.broken" }
                    }
                every { loadLabel(pm) } throws RuntimeException("broken resource")
            }
        every { pm.queryIntentActivities(any(), any<PackageManager.ResolveInfoFlags>()) } answers
            {
                val intent = firstArg<Intent>()
                if (intent.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)) listOf(brokenInfo)
                else emptyList()
            }

        val apps = loadInstalledApps(pm, selfPkg)

        assertEquals(1, apps.size)
        assertEquals("pkg.broken", apps.first().label)
    }

    @Test
    fun `loadInstalledApps sorts leanback apps first then alphabetically`() {
        val pm = mockk<PackageManager>()
        every { pm.queryIntentActivities(any(), any<PackageManager.ResolveInfoFlags>()) } answers
            {
                val intent = firstArg<Intent>()
                if (intent.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)) {
                    listOf(
                        appResolveInfo("pkg.yt", "Main", "YouTube", false),
                        appResolveInfo("pkg.disney", "Main", "disney+", false),
                    )
                } else {
                    listOf(
                        appResolveInfo("pkg.alpha", "Main", "Alpha Tool", false),
                        appResolveInfo("pkg.zeta", "Main", "zeta", false),
                    )
                }
            }

        val apps = loadInstalledApps(pm, selfPkg)

        assertEquals(
            listOf("disney+", "YouTube", "Alpha Tool", "zeta"),
            apps.map { it.label },
        )
    }

    @Test
    fun `launchApp starts explicit leanback and standard intents`() {
        val pm = mockk<PackageManager>(relaxed = true)
        val appContext = mockk<Context>(relaxed = true)
        every { appContext.packageName } returns selfPkg
        every { appContext.packageManager } returns pm
        val repo = TvInstalledAppsRepository(appContext, TestScope(UnconfinedTestDispatcher()))

        val intentSlot = slot<Intent>()
        val launchContext = mockk<Context>()
        every { launchContext.packageManager } returns pm
        every { launchContext.startActivity(capture(intentSlot)) } just runs

        val leanbackApp =
            TvInstalledApp(
                packageName = "com.example.tv",
                activityName = "com.example.tv.TvMain",
                label = "TV App",
                isLeanback = true,
                isSystemApp = false,
            )
        assertTrue(repo.launchApp(launchContext, leanbackApp))
        assertEquals(Intent.ACTION_MAIN, intentSlot.captured.action)
        assertEquals(
            ComponentName("com.example.tv", "com.example.tv.TvMain"),
            intentSlot.captured.component,
        )
        assertTrue(intentSlot.captured.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER))
        assertTrue((intentSlot.captured.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
        assertTrue((intentSlot.captured.flags and Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) != 0)

        val standardApp = leanbackApp.copy(isLeanback = false)
        assertTrue(repo.launchApp(launchContext, standardApp))
        assertTrue(intentSlot.captured.hasCategory(Intent.CATEGORY_LAUNCHER))
    }

    @Test
    fun `launchApp falls back to PackageManager launch intent and handles missing intent`() {
        val pm = mockk<PackageManager>()
        val appContext = mockk<Context>(relaxed = true)
        every { appContext.packageName } returns selfPkg
        every { appContext.packageManager } returns pm
        val repo = TvInstalledAppsRepository(appContext, TestScope(UnconfinedTestDispatcher()))

        val fallbackIntent = Intent(Intent.ACTION_MAIN).setPackage("com.example.tv")
        every { pm.getLeanbackLaunchIntentForPackage("com.example.tv") } returns fallbackIntent
        every { pm.getLeanbackLaunchIntentForPackage("com.example.missing") } returns null
        every { pm.getLaunchIntentForPackage("com.example.missing") } returns null

        val launchedIntents = mutableListOf<Intent>()
        val launchContext = mockk<Context>()
        every { launchContext.packageManager } returns pm
        every { launchContext.startActivity(capture(launchedIntents)) } answers
            {
                val intent = firstArg<Intent>()
                if (intent.component != null) {
                    throw ActivityNotFoundException("explicit not exported")
                }
            }

        val app =
            TvInstalledApp(
                packageName = "com.example.tv",
                activityName = "com.example.tv.PrivateMain",
                label = "TV App",
                isLeanback = true,
                isSystemApp = false,
            )
        assertTrue(repo.launchApp(launchContext, app))
        assertEquals(2, launchedIntents.size)
        assertEquals(fallbackIntent, launchedIntents.last())

        val missingApp = app.copy(packageName = "com.example.missing")
        assertFalse(repo.launchApp(launchContext, missingApp))
    }

    @Test
    fun `apps StateFlow emits initial apps and reloads on refresh`() = runTest {
        val pm = mockk<PackageManager>()
        var installedCount = 1
        every { pm.queryIntentActivities(any(), any<PackageManager.ResolveInfoFlags>()) } answers
            {
                val intent = firstArg<Intent>()
                if (intent.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)) {
                    if (installedCount == 1) {
                        listOf(appResolveInfo("pkg.one", "Main", "One", false))
                    } else {
                        listOf(
                            appResolveInfo("pkg.one", "Main", "One", false),
                            appResolveInfo("pkg.two", "Main", "Two", false),
                        )
                    }
                } else {
                    emptyList()
                }
            }

        val appContext = mockk<Context>(relaxed = true)
        every { appContext.packageName } returns selfPkg
        every { appContext.packageManager } returns pm
        every { appContext.getSystemService(android.content.pm.LauncherApps::class.java) } returns
            null

        val repoScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val repo = TvInstalledAppsRepository(appContext, repoScope)

        try {
            repo.apps.test {
                // WhileSubscribed starts with emptyList(), then emits the IO-loaded list
                var latest = awaitItem()
                if (latest.isEmpty()) {
                    latest = awaitItem()
                }
                assertEquals(listOf("One"), latest.map { it.label })

                installedCount = 2
                repo.refresh()
                assertEquals(listOf("One", "Two"), awaitItem().map { it.label })
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            repoScope.cancel()
        }
    }

    private fun appResolveInfo(
        pkg: String,
        cls: String,
        label: String,
        systemApp: Boolean,
    ): ResolveInfo =
        ResolveInfo().apply {
            nonLocalizedLabel = label
            activityInfo =
                ActivityInfo().apply {
                    packageName = pkg
                    name = cls
                    applicationInfo =
                        ApplicationInfo().apply {
                            packageName = pkg
                            flags = if (systemApp) ApplicationInfo.FLAG_SYSTEM else 0
                        }
                }
        }
}
