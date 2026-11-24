package com.makd.afinity.tvlauncher

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.Settings
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class TvLauncherManagerTest {

    private val releasePkg = "xyz.klinker.jellyfin"
    private val debugPkg = "xyz.klinker.jellyfin.debug"

    @Test
    fun `TvLauncherStatus computed flags reflect alias, default, and other homes`() {
        assertFalse(TvLauncherStatus().isLauncherUiActive)
        assertFalse(TvLauncherStatus().canSafelyDisableAlias)

        assertTrue(TvLauncherStatus(aliasEnabled = true).isLauncherUiActive)
        assertTrue(TvLauncherStatus(isDefaultLauncher = true).isLauncherUiActive)
        assertTrue(
            TvLauncherStatus(aliasEnabled = true, isDefaultLauncher = true).isLauncherUiActive,
        )
        assertTrue(
            TvLauncherStatus(
                    otherEnabledHomePackages = listOf("com.google.android.apps.tv.launcherx"),
                )
                .canSafelyDisableAlias,
        )
    }

    @Test
    fun `command builders format expected adb commands`() {
        assertEquals(
            "adb shell cmd package set-home-activity xyz.klinker.jellyfin/com.makd.afinity.TvLauncherAlias",
            buildSetHomeActivityCommand(releasePkg),
        )
        assertEquals(
            "adb shell cmd package set-home-activity xyz.klinker.jellyfin.debug/com.makd.afinity.TvLauncherAlias",
            buildSetHomeActivityCommand(debugPkg),
        )
        assertEquals(
            "adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx",
            buildDisableStockLauncherCommand("com.google.android.apps.tv.launcherx"),
        )
        assertEquals(
            "adb shell pm enable com.google.android.apps.tv.launcherx",
            buildEnableStockLauncherCommand("com.google.android.apps.tv.launcherx"),
        )
    }

    @Test
    fun `queryIsAliasEnabled returns true only when component is enabled`() {
        val pm = mockk<PackageManager>()
        val alias = ComponentName(releasePkg, TvLauncherManager.ALIAS_CLASS_NAME)

        every { pm.getComponentEnabledSetting(alias) } returns
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        assertTrue(queryIsAliasEnabled(pm, releasePkg))

        every { pm.getComponentEnabledSetting(alias) } returns
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        assertFalse(queryIsAliasEnabled(pm, releasePkg))

        every { pm.getComponentEnabledSetting(alias) } returns
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
        assertFalse(queryIsAliasEnabled(pm, releasePkg))

        every { pm.getComponentEnabledSetting(alias) } returns
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        assertFalse(queryIsAliasEnabled(pm, releasePkg))
    }

    @Test
    fun `queryIsDefaultHomeLauncher matches self packageName only`() {
        val pm = mockk<PackageManager>()

        every { pm.resolveActivity(any(), any<PackageManager.ResolveInfoFlags>()) } returns
            resolveInfo(releasePkg, TvLauncherManager.ALIAS_CLASS_NAME)
        assertTrue(queryIsDefaultHomeLauncher(pm, releasePkg))
        assertFalse(queryIsDefaultHomeLauncher(pm, debugPkg))

        every { pm.resolveActivity(any(), any<PackageManager.ResolveInfoFlags>()) } returns
            resolveInfo("com.google.android.apps.tv.launcherx", "HomeActivity")
        assertFalse(queryIsDefaultHomeLauncher(pm, releasePkg))

        every { pm.resolveActivity(any(), any<PackageManager.ResolveInfoFlags>()) } returns
            resolveInfo("android", "com.android.internal.app.ResolverActivity")
        assertFalse(queryIsDefaultHomeLauncher(pm, releasePkg))

        every { pm.resolveActivity(any(), any<PackageManager.ResolveInfoFlags>()) } returns null
        assertFalse(queryIsDefaultHomeLauncher(pm, releasePkg))
    }

    @Test
    fun `queryOtherEnabledHomePackages excludes self and android and deduplicates`() {
        val pm = mockk<PackageManager>()
        every { pm.queryIntentActivities(any(), any<PackageManager.ResolveInfoFlags>()) } returns
            listOf(
                resolveInfo(releasePkg, TvLauncherManager.ALIAS_CLASS_NAME),
                resolveInfo("android", "ResolverActivity"),
                resolveInfo("com.google.android.apps.tv.launcherx", "HomeA"),
                resolveInfo("com.google.android.apps.tv.launcherx", "HomeB"),
                resolveInfo("com.example.otherlauncher", "Main"),
            )

        assertEquals(
            listOf("com.example.otherlauncher", "com.google.android.apps.tv.launcherx"),
            queryOtherEnabledHomePackages(pm, releasePkg),
        )
    }

    @Test
    fun `detectStockLauncherPackage covers known, custom, and default fallbacks`() {
        val pm = mockk<PackageManager>()
        every { pm.getPackageInfo(any<String>(), any<PackageManager.PackageInfoFlags>()) } throws
            PackageManager.NameNotFoundException()

        // No known or other launchers installed -> Google TV default
        assertEquals(
            TvLauncherManager.DEFAULT_GOOGLE_TV_LAUNCHER,
            detectStockLauncherPackage(pm, emptyList()),
        )

        // Custom other launcher when no known stock launcher is active
        assertEquals(
            "com.example.custom",
            detectStockLauncherPackage(pm, listOf("com.example.custom")),
        )

        // Known stock launcher preferred over custom when both are present
        assertEquals(
            "com.google.android.apps.tv.launcherx",
            detectStockLauncherPackage(
                pm,
                listOf("com.example.custom", "com.google.android.apps.tv.launcherx"),
            ),
        )

        // Disabled known stock launcher detected via MATCH_DISABLED_COMPONENTS
        every {
            pm.getPackageInfo(
                "com.google.android.tvlauncher",
                any<PackageManager.PackageInfoFlags>(),
            )
        } returns PackageInfo()
        assertEquals(
            "com.google.android.tvlauncher",
            detectStockLauncherPackage(pm, emptyList()),
        )
    }

    @Test
    fun `enableAlias enables component and updates status`() {
        val pm = mockk<PackageManager>()
        val alias = ComponentName(releasePkg, TvLauncherManager.ALIAS_CLASS_NAME)
        every { pm.getComponentEnabledSetting(alias) } returnsMany
            listOf(
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            )
        every { pm.resolveActivity(any(), any<PackageManager.ResolveInfoFlags>()) } returns
            resolveInfo(TvLauncherManager.DEFAULT_GOOGLE_TV_LAUNCHER, "HomeActivity")
        every { pm.queryIntentActivities(any(), any<PackageManager.ResolveInfoFlags>()) } returns
            listOf(resolveInfo(TvLauncherManager.DEFAULT_GOOGLE_TV_LAUNCHER, "HomeActivity"))
        every { pm.setComponentEnabledSetting(alias, any(), any()) } just runs

        val context =
            mockk<Context> {
                every { packageName } returns releasePkg
                every { packageManager } returns pm
            }
        val manager = TvLauncherManager(context)
        assertFalse(manager.status.value.aliasEnabled)

        val updated = manager.enableAlias()

        verify(exactly = 1) {
            pm.setComponentEnabledSetting(
                alias,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        assertTrue(updated.aliasEnabled)
        assertTrue(updated.isLauncherUiActive)
        assertFalse(updated.isDefaultLauncher)
    }

    @Test
    fun `refresh updates status when external ADB command sets default launcher`() {
        val pm = mockk<PackageManager>()
        val alias = ComponentName(releasePkg, TvLauncherManager.ALIAS_CLASS_NAME)
        every { pm.getComponentEnabledSetting(alias) } returns
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        every { pm.resolveActivity(any(), any<PackageManager.ResolveInfoFlags>()) } returnsMany
            listOf(
                resolveInfo(TvLauncherManager.DEFAULT_GOOGLE_TV_LAUNCHER, "HomeActivity"),
                resolveInfo(releasePkg, TvLauncherManager.ALIAS_CLASS_NAME),
            )
        every { pm.queryIntentActivities(any(), any<PackageManager.ResolveInfoFlags>()) } returns
            listOf(
                resolveInfo(releasePkg, TvLauncherManager.ALIAS_CLASS_NAME),
                resolveInfo(TvLauncherManager.DEFAULT_GOOGLE_TV_LAUNCHER, "HomeActivity"),
            )

        val context =
            mockk<Context> {
                every { packageName } returns releasePkg
                every { packageManager } returns pm
            }
        val manager = TvLauncherManager(context)
        assertFalse(manager.status.value.isDefaultLauncher)

        val refreshed = manager.refresh()
        assertTrue(refreshed.isDefaultLauncher)
    }

    @Test
    fun `disableAlias blocks when no other enabled launcher exists unless forced`() {
        val pm = mockk<PackageManager>()
        val alias = ComponentName(releasePkg, TvLauncherManager.ALIAS_CLASS_NAME)

        every { pm.getComponentEnabledSetting(alias) } returnsMany
            listOf(
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            )
        every { pm.resolveActivity(any(), any<PackageManager.ResolveInfoFlags>()) } returns
            resolveInfo(releasePkg, TvLauncherManager.ALIAS_CLASS_NAME)
        every { pm.queryIntentActivities(any(), any<PackageManager.ResolveInfoFlags>()) } returns
            listOf(resolveInfo(releasePkg, TvLauncherManager.ALIAS_CLASS_NAME))
        every { pm.getPackageInfo(any<String>(), any<PackageManager.PackageInfoFlags>()) } throws
            PackageManager.NameNotFoundException()
        every {
            pm.getPackageInfo(
                TvLauncherManager.DEFAULT_GOOGLE_TV_LAUNCHER,
                any<PackageManager.PackageInfoFlags>(),
            )
        } returns PackageInfo()
        every { pm.setComponentEnabledSetting(alias, any(), any()) } just runs

        val context =
            mockk<Context> {
                every { packageName } returns releasePkg
                every { packageManager } returns pm
            }
        val manager = TvLauncherManager(context)

        val blockedResult = manager.disableAlias()
        assertTrue(blockedResult is DisableLauncherResult.BlockedNoOtherLauncher)
        val blocked = blockedResult as DisableLauncherResult.BlockedNoOtherLauncher
        assertEquals(TvLauncherManager.DEFAULT_GOOGLE_TV_LAUNCHER, blocked.stockLauncherPackage)
        assertEquals(
            "adb shell pm enable com.google.android.apps.tv.launcherx",
            blocked.restoreCommand,
        )
        verify(exactly = 0) { pm.setComponentEnabledSetting(any(), any(), any()) }

        val forcedResult = manager.disableAlias(force = true)
        assertEquals(DisableLauncherResult.Disabled, forcedResult)
        verify(exactly = 1) {
            pm.setComponentEnabledSetting(
                alias,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    @Test
    fun `disableAlias succeeds when another enabled launcher exists`() {
        val pm = mockk<PackageManager>()
        val alias = ComponentName(releasePkg, TvLauncherManager.ALIAS_CLASS_NAME)

        every { pm.getComponentEnabledSetting(alias) } returnsMany
            listOf(
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            )
        every { pm.resolveActivity(any(), any<PackageManager.ResolveInfoFlags>()) } returns
            resolveInfo(releasePkg, TvLauncherManager.ALIAS_CLASS_NAME) andThen
            resolveInfo(releasePkg, TvLauncherManager.ALIAS_CLASS_NAME) andThen
            resolveInfo(TvLauncherManager.DEFAULT_GOOGLE_TV_LAUNCHER, "HomeActivity")
        every { pm.queryIntentActivities(any(), any<PackageManager.ResolveInfoFlags>()) } returns
            listOf(
                resolveInfo(releasePkg, TvLauncherManager.ALIAS_CLASS_NAME),
                resolveInfo(TvLauncherManager.DEFAULT_GOOGLE_TV_LAUNCHER, "HomeActivity"),
            )
        every { pm.setComponentEnabledSetting(alias, any(), any()) } just runs

        val context =
            mockk<Context> {
                every { packageName } returns releasePkg
                every { packageManager } returns pm
            }
        val manager = TvLauncherManager(context)

        val result = manager.disableAlias()

        assertEquals(DisableLauncherResult.Disabled, result)
        verify(exactly = 1) {
            pm.setComponentEnabledSetting(
                alias,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        assertFalse(manager.status.value.aliasEnabled)
    }

    @Test
    fun `companion intent helpers start expected activities and handle failures`() {
        val intentSlot = slot<Intent>()
        val context = mockk<Context>()
        every { context.startActivity(capture(intentSlot)) } just runs

        assertTrue(TvLauncherManager.openSystemSettings(context))
        assertEquals(Settings.ACTION_SETTINGS, intentSlot.captured.action)
        assertTrue((intentSlot.captured.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)

        assertTrue(TvLauncherManager.openAppInfo(context, "com.example.app"))
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intentSlot.captured.action)
        assertEquals(Uri.fromParts("package", "com.example.app", null), intentSlot.captured.data)
        assertTrue((intentSlot.captured.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)

        assertTrue(TvLauncherManager.uninstallApp(context, "com.example.app"))
        assertEquals(Intent.ACTION_DELETE, intentSlot.captured.action)
        assertEquals(Uri.fromParts("package", "com.example.app", null), intentSlot.captured.data)
        assertTrue((intentSlot.captured.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)

        val failingContext = mockk<Context>()
        every { failingContext.startActivity(any()) } throws ActivityNotFoundException("no handler")
        assertFalse(TvLauncherManager.openSystemSettings(failingContext))
        assertFalse(TvLauncherManager.openAppInfo(failingContext, "com.example.app"))
        assertFalse(TvLauncherManager.uninstallApp(failingContext, "com.example.app"))
    }

    private fun resolveInfo(pkg: String, cls: String): ResolveInfo =
        ResolveInfo().apply {
            activityInfo =
                ActivityInfo().apply {
                    packageName = pkg
                    name = cls
                }
        }
}
