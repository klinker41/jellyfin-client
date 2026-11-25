package com.makd.afinity.tvlauncher

import android.app.Application
import android.content.Context
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.makd.afinity.R
import com.makd.afinity.data.models.server.ConnectionType
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.navigation.Destination
import com.makd.afinity.tvlauncher.ui.TvAppsRowContent
import com.makd.afinity.tvlauncher.ui.TvLauncherAppsDialogContent
import com.makd.afinity.tvlauncher.ui.TvLauncherFallbackPanel
import com.makd.afinity.tvlauncher.ui.TvLauncherLoginBarContent
import com.makd.afinity.tvlauncher.ui.TvLauncherSettingsContent
import com.makd.afinity.tvlauncher.ui.TvLauncherTopBarActionsContent
import com.makd.afinity.ui.components.AppNavigationDrawerContent
import com.makd.afinity.ui.components.LocalPinnedDrawerStartInset
import com.makd.afinity.ui.preview.PreviewHost
import com.makd.afinity.ui.settings.SessionSwitcherState
import com.makd.afinity.ui.utils.LocalIsTelevision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class TvLauncherUiRobolectricTest {

    @get:Rule val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `TvLauncherSettingsContent hides ADB setup when disabled and toggles on click`() {
        var toggledTo: Boolean? = null

        composeRule.setContent {
            PreviewHost {
                TvLauncherSettingsContent(
                    status =
                        TvLauncherStatus(
                            aliasEnabled = false,
                            setHomeCommand = "adb shell cmd package set-home-activity test/alias",
                        ),
                    disableBlocked = null,
                    endPadding = 16.dp,
                    onToggleLauncher = { toggledTo = it },
                    onRefreshStatus = {},
                    onOpenSystemSettings = {},
                    onRetryDisable = {},
                    onDismissDisableBlocked = {},
                )
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.pref_tv_launcher_title))
            .assertIsDisplayed()
            .performClick()
        assertEquals(true, toggledTo)

        composeRule
            .onNodeWithText(context.getString(R.string.pref_tv_launcher_subtitle_off))
            .assertIsDisplayed()
        composeRule
            .onAllNodesWithText(context.getString(R.string.tv_launcher_adb_title))
            .assertCountEquals(0)
        composeRule
            .onAllNodesWithText(context.getString(R.string.tv_launcher_refresh_status))
            .assertCountEquals(0)
    }

    @Test
    fun `TvLauncherSettingsContent shows ADB instructions and action callbacks when enabled`() {
        var refreshed = false
        var openedSettings = false
        val setHomeCmd =
            "adb shell cmd package set-home-activity xyz.klinker.jellyfin/com.makd.afinity.TvLauncherAlias"

        composeRule.setContent {
            PreviewHost {
                TvLauncherSettingsContent(
                    status =
                        TvLauncherStatus(
                            aliasEnabled = true,
                            isDefaultLauncher = false,
                            setHomeCommand = setHomeCmd,
                            stockLauncherPackage = "com.google.android.apps.tv.launcherx",
                        ),
                    disableBlocked = null,
                    endPadding = 16.dp,
                    onToggleLauncher = {},
                    onRefreshStatus = { refreshed = true },
                    onOpenSystemSettings = { openedSettings = true },
                    onRetryDisable = {},
                    onDismissDisableBlocked = {},
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.pref_tv_launcher_subtitle_pending))
            .assertIsDisplayed()
        composeRule.onNodeWithText(setHomeCmd).assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx",
            )
            .performScrollTo()
            .assertIsDisplayed()

        composeRule
            .onNodeWithText(context.getString(R.string.tv_launcher_refresh_status))
            .performScrollTo()
            .performClick()
        assertTrue(refreshed)

        composeRule
            .onNodeWithText(context.getString(R.string.tv_launcher_system_settings))
            .performScrollTo()
            .performClick()
        assertTrue(openedSettings)
    }

    @Test
    fun `TvLauncherSettingsContent shows active subtitle and disable-blocked dialog actions`() {
        var retried = false
        var dismissed = false
        val restoreCmd = "adb shell pm enable com.google.android.apps.tv.launcherx"

        composeRule.setContent {
            PreviewHost {
                TvLauncherSettingsContent(
                    status =
                        TvLauncherStatus(
                            aliasEnabled = true,
                            isDefaultLauncher = true,
                            setHomeCommand = "adb shell cmd package set-home-activity test/alias",
                        ),
                    disableBlocked =
                        DisableLauncherResult.BlockedNoOtherLauncher(
                            stockLauncherPackage = "com.google.android.apps.tv.launcherx",
                            restoreCommand = restoreCmd,
                        ),
                    endPadding = 16.dp,
                    onToggleLauncher = {},
                    onRefreshStatus = {},
                    onOpenSystemSettings = {},
                    onRetryDisable = { retried = true },
                    onDismissDisableBlocked = { dismissed = true },
                )
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.pref_tv_launcher_subtitle_active))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.tv_launcher_disable_blocked_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(restoreCmd).assertIsDisplayed()

        composeRule
            .onNodeWithText(context.getString(R.string.tv_launcher_check_again))
            .performClick()
        assertTrue(retried)

        composeRule.onNodeWithText(context.getString(R.string.action_cancel)).performClick()
        assertTrue(dismissed)
    }

    @Test
    fun `TvAppsRowContent launches on click, opens all apps, and unpins on long click`() {
        var launchedApp: TvInstalledApp? = null
        var allAppsClicked = false
        var unpinnedApp: TvInstalledApp? = null
        var infoApp: TvInstalledApp? = null
        var uninstalledApp: TvInstalledApp? = null
        val userApp =
            TvInstalledApp(
                packageName = "com.example.user",
                activityName = "MainActivity",
                label = "User App",
                isLeanback = true,
                isSystemApp = false,
            )
        val systemApp =
            TvInstalledApp(
                packageName = "com.example.system",
                activityName = "MainActivity",
                label = "System App",
                isLeanback = true,
                isSystemApp = true,
            )

        composeRule.setContent {
            PreviewHost {
                CompositionLocalProvider(LocalPinnedDrawerStartInset provides 0.dp) {
                    TvAppsRowContent(
                        apps = listOf(userApp, systemApp),
                        onAppClick = { launchedApp = it },
                        onAllAppsClick = { allAppsClicked = true },
                        onUnpinClick = { unpinnedApp = it },
                        onAppInfoClick = { infoApp = it },
                        onUninstallClick = { uninstalledApp = it },
                    )
                }
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.tv_launcher_home_apps_title))
            .assertIsDisplayed()

        composeRule.onNodeWithContentDescription("User App").performClick()
        assertEquals(userApp, launchedApp)

        val allAppsLabel = context.getString(R.string.tv_launcher_all_apps_tile)
        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasContentDescription(allAppsLabel))
        composeRule.onNodeWithContentDescription(allAppsLabel).performClick()
        assertTrue(allAppsClicked)

        // Long click non-system app -> Open, Unpin, App info, Uninstall all shown
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("User App"))
        composeRule.onNodeWithContentDescription("User App").performTouchInput { longClick() }
        composeRule
            .onNodeWithText(context.getString(R.string.tv_launcher_app_action_open))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.tv_launcher_app_action_unpin))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.tv_launcher_app_action_uninstall))
            .assertIsDisplayed()
            .performClick()
        assertEquals(userApp, uninstalledApp)

        // Long click pinned app -> Unpin fires callback
        composeRule.onNodeWithContentDescription("User App").performTouchInput { longClick() }
        composeRule
            .onNodeWithText(context.getString(R.string.tv_launcher_app_action_unpin))
            .performClick()
        assertEquals(userApp, unpinnedApp)

        // Long click system app -> Uninstall omitted, App info fires callback
        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasContentDescription("System App"))
        composeRule.onNodeWithContentDescription("System App").performTouchInput { longClick() }
        composeRule
            .onAllNodesWithText(context.getString(R.string.tv_launcher_app_action_uninstall))
            .assertCountEquals(0)
        composeRule
            .onNodeWithText(context.getString(R.string.tv_launcher_app_action_info))
            .assertIsDisplayed()
            .performClick()
        assertEquals(systemApp, infoApp)
    }

    @Test
    fun `TvAppActionsDialog ignores repeated key-down and release from opening long press and allows dpad navigation`() {
        var launchedApp: TvInstalledApp? = null
        var infoApp: TvInstalledApp? = null
        val userApp =
            openTvAppActionsDialogViaLongPress(
                onAppClick = { launchedApp = it },
                onAppInfoClick = { infoApp = it },
            )

        val openText = context.getString(R.string.tv_launcher_app_action_open)
        val unpinText = context.getString(R.string.tv_launcher_app_action_unpin)
        val infoText = context.getString(R.string.tv_launcher_app_action_info)

        // Simulate held DPAD_CENTER repeat events + release arriving in the dialog window
        composeRule.onNodeWithText(openText).apply {
            performKeyPress(
                keyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_DPAD_CENTER, 1),
            )
            performKeyPress(
                keyEvent(AndroidKeyEvent.ACTION_UP, AndroidKeyEvent.KEYCODE_DPAD_CENTER),
            )
        }
        assertNull(launchedApp)
        composeRule.onNodeWithText(openText).assertIsDisplayed()
        composeRule.onNodeWithText(infoText).assertIsDisplayed()

        // Standalone stale KeyUp with no fresh KeyDown is also ignored
        composeRule
            .onNodeWithText(openText)
            .performKeyPress(
                keyEvent(AndroidKeyEvent.ACTION_UP, AndroidKeyEvent.KEYCODE_DPAD_CENTER),
            )
        assertNull(launchedApp)

        // Navigate down from "Open" -> "Unpin from Home" -> "App info" and activate with a fresh
        // press
        composeRule
            .onNodeWithText(openText)
            .performKeyPress(
                keyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_DPAD_DOWN),
            )
        composeRule.onNodeWithText(unpinText).apply {
            performKeyPress(
                keyEvent(AndroidKeyEvent.ACTION_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN),
            )
            assertIsFocused()
            performKeyPress(
                keyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_DPAD_DOWN),
            )
        }
        composeRule.onNodeWithText(infoText).apply {
            performKeyPress(
                keyEvent(AndroidKeyEvent.ACTION_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN),
            )
            assertIsFocused()
            performKeyPress(
                keyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_DPAD_CENTER),
            )
            performKeyPress(
                keyEvent(AndroidKeyEvent.ACTION_UP, AndroidKeyEvent.KEYCODE_DPAD_CENTER),
            )
        }

        assertEquals(userApp, infoApp)
        assertNull(launchedApp)
    }

    @Test
    fun `TvAppActionsDialog activates first option on fresh press after ignoring stale activation keys`() {
        var launchedApp: TvInstalledApp? = null
        val userApp = openTvAppActionsDialogViaLongPress(onAppClick = { launchedApp = it })
        val openText = context.getString(R.string.tv_launcher_app_action_open)

        val staleKeyCodes =
            listOf(
                AndroidKeyEvent.KEYCODE_ENTER,
                AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                AndroidKeyEvent.KEYCODE_SPACE,
            )
        staleKeyCodes.forEach { keyCode ->
            composeRule.onNodeWithText(openText).apply {
                performKeyPress(keyEvent(AndroidKeyEvent.ACTION_DOWN, keyCode, 1))
                performKeyPress(keyEvent(AndroidKeyEvent.ACTION_UP, keyCode))
            }
            assertNull(launchedApp)
        }

        // Fresh press (including a held repeat after fresh down) still triggers Open on release
        composeRule.onNodeWithText(openText).apply {
            performKeyPress(
                keyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_DPAD_CENTER),
            )
            performKeyPress(
                keyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_DPAD_CENTER, 1),
            )
            performKeyPress(
                keyEvent(AndroidKeyEvent.ACTION_UP, AndroidKeyEvent.KEYCODE_DPAD_CENTER),
            )
        }
        assertEquals(userApp, launchedApp)
    }

    private fun openTvAppActionsDialogViaLongPress(
        onAppClick: (TvInstalledApp) -> Unit = {},
        onAppInfoClick: (TvInstalledApp) -> Unit = {},
    ): TvInstalledApp {
        val userApp =
            TvInstalledApp(
                packageName = "com.example.user",
                activityName = "MainActivity",
                label = "User App",
                isLeanback = true,
                isSystemApp = false,
            )

        composeRule.setContent {
            PreviewHost {
                val inputModeManager = LocalInputModeManager.current
                LaunchedEffect(Unit) { inputModeManager.requestInputMode(InputMode.Keyboard) }
                CompositionLocalProvider(
                    LocalIsTelevision provides true,
                    LocalPinnedDrawerStartInset provides 0.dp,
                ) {
                    TvAppsRowContent(
                        apps = listOf(userApp),
                        onAppClick = onAppClick,
                        onAllAppsClick = {},
                        onUnpinClick = {},
                        onAppInfoClick = onAppInfoClick,
                        onUninstallClick = {},
                    )
                }
            }
        }

        val openText = context.getString(R.string.tv_launcher_app_action_open)
        val tile = composeRule.onNodeWithContentDescription("User App")
        tile.performSemanticsAction(SemanticsActions.RequestFocus)
        tile.assertIsFocused()
        tile.performKeyInput { keyDown(Key.DirectionCenter) }
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.waitForIdle()

        val dialogView =
            (composeRule.onNode(isDialog()).fetchSemanticsNode().root as ViewRootForTest).view
        composeRule.runOnIdle { dialogView.requestFocusFromTouch() }

        composeRule
            .onNodeWithText(openText)
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()

        return userApp
    }

    private fun keyEvent(action: Int, keyCode: Int, repeatCount: Int = 0): KeyEvent =
        KeyEvent(AndroidKeyEvent(0L, 0L, action, keyCode, repeatCount))

    @Test
    fun `TvAppsRowContent shows All apps button when no apps are pinned`() {
        var allAppsClicked = false

        composeRule.setContent {
            PreviewHost {
                CompositionLocalProvider(LocalPinnedDrawerStartInset provides 0.dp) {
                    TvAppsRowContent(
                        apps = emptyList(),
                        onAppClick = {},
                        onAllAppsClick = { allAppsClicked = true },
                        onUnpinClick = {},
                        onAppInfoClick = {},
                        onUninstallClick = {},
                    )
                }
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.tv_launcher_home_apps_title))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.tv_launcher_all_apps_tile))
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.tv_launcher_all_apps_tile))
            .performClick()
        assertTrue(allAppsClicked)
    }

    @Test
    fun `TvLauncherAppsDialogContent shows pin state, pins on long click, and handles actions`() {
        var launchedApp: TvInstalledApp? = null
        var toggledPin: Pair<TvInstalledApp, Boolean>? = null
        var infoApp: TvInstalledApp? = null
        var uninstalledApp: TvInstalledApp? = null
        var dismissed = false
        val pinnedApp =
            TvInstalledApp(
                packageName = "com.example.pinned",
                activityName = "MainActivity",
                label = "Pinned App",
                isLeanback = true,
                isSystemApp = false,
            )
        val unpinnedSystemApp =
            TvInstalledApp(
                packageName = "com.example.unpinned",
                activityName = "MainActivity",
                label = "Unpinned App",
                isLeanback = true,
                isSystemApp = true,
            )

        composeRule.setContent {
            PreviewHost {
                TvLauncherAppsDialogContent(
                    apps = listOf(pinnedApp, unpinnedSystemApp),
                    pinnedPackageNames = setOf(pinnedApp.packageName),
                    onAppClick = { launchedApp = it },
                    onTogglePin = { app, pinned -> toggledPin = app to pinned },
                    onAppInfoClick = { infoApp = it },
                    onUninstallClick = { uninstalledApp = it },
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.tv_launcher_apps_dialog_title))
            .assertIsDisplayed()
        composeRule.onAllNodesWithText(pinnedApp.label).assertCountEquals(1)
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.tv_launcher_cd_pinned_app))
            .assertIsDisplayed()

        // Long click unpinned system app -> Pin to Home keeps dialog open, Uninstall omitted
        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasContentDescription(unpinnedSystemApp.label))
        composeRule.onNodeWithContentDescription(unpinnedSystemApp.label).performTouchInput {
            longClick()
        }
        composeRule
            .onAllNodesWithText(context.getString(R.string.tv_launcher_app_action_uninstall))
            .assertCountEquals(0)
        composeRule
            .onNodeWithText(context.getString(R.string.tv_launcher_app_action_pin))
            .assertIsDisplayed()
            .performClick()
        assertEquals(unpinnedSystemApp to true, toggledPin)
        assertFalse(dismissed)
        composeRule
            .onNodeWithText(context.getString(R.string.tv_launcher_apps_dialog_title))
            .assertIsDisplayed()

        // Long click pinned app -> Unpin from Home
        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasContentDescription(pinnedApp.label))
        composeRule.onNodeWithContentDescription(pinnedApp.label).performTouchInput { longClick() }
        composeRule
            .onNodeWithText(context.getString(R.string.tv_launcher_app_action_unpin))
            .assertIsDisplayed()
            .performClick()
        assertEquals(pinnedApp to false, toggledPin)
        assertFalse(dismissed)

        // Long click non-system app -> Uninstall triggers callback and dismisses
        composeRule.onNodeWithContentDescription(pinnedApp.label).performTouchInput { longClick() }
        composeRule
            .onNodeWithText(context.getString(R.string.tv_launcher_app_action_uninstall))
            .assertIsDisplayed()
            .performClick()
        assertEquals(pinnedApp, uninstalledApp)
        assertTrue(dismissed)

        // Long click -> App info triggers callback and dismisses
        dismissed = false
        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasContentDescription(unpinnedSystemApp.label))
        composeRule.onNodeWithContentDescription(unpinnedSystemApp.label).performTouchInput {
            longClick()
        }
        composeRule
            .onNodeWithText(context.getString(R.string.tv_launcher_app_action_info))
            .performClick()
        assertEquals(unpinnedSystemApp, infoApp)
        assertTrue(dismissed)

        // Click app -> launches app
        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasContentDescription(pinnedApp.label))
        composeRule.onNodeWithContentDescription(pinnedApp.label).performClick()
        assertEquals(pinnedApp, launchedApp)

        // Close button -> dismisses
        dismissed = false
        composeRule.onNodeWithText(context.getString(R.string.action_close)).performClick()
        assertTrue(dismissed)
    }

    @Test
    fun `TvLauncherTopBarActionsContent displays clock, network state, and settings button`() {
        var openedSettings = false

        composeRule.setContent {
            PreviewHost {
                TvLauncherTopBarActionsContent(
                    isNetworkAvailable = false,
                    formattedTime = "9:41 PM",
                    onOpenSystemSettings = { openedSettings = true },
                )
            }
        }

        composeRule.onNodeWithText("9:41 PM").assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(
                context.getString(R.string.tv_launcher_cd_network_disconnected),
            )
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(
                context.getString(R.string.tv_launcher_cd_system_settings),
            )
            .performClick()
        assertTrue(openedSettings)
    }

    @Test
    fun `TvLauncherFallbackPanel renders retry, settings, and embedded apps`() {
        var retried = false
        var openedSettings = false

        composeRule.setContent {
            PreviewHost {
                TvLauncherFallbackPanel(
                    title = "Server unreachable",
                    message = "Waiting for Wi-Fi",
                    actionText = "Retry",
                    onActionClick = { retried = true },
                    onOpenSystemSettings = { openedSettings = true },
                    appsContent = { Text("Embedded Apps Row") },
                )
            }
        }

        composeRule.onNodeWithText("Server unreachable").assertIsDisplayed()
        composeRule.onNodeWithText("Waiting for Wi-Fi").assertIsDisplayed()
        composeRule.onNodeWithText("Embedded Apps Row").assertIsDisplayed()

        composeRule.onNodeWithText("Retry").performClick()
        assertTrue(retried)

        composeRule
            .onNodeWithText(context.getString(R.string.tv_launcher_system_settings))
            .performClick()
        assertTrue(openedSettings)
    }

    @Test
    fun `TvLauncherLoginBarContent invokes apps and settings callbacks`() {
        var appsClicked = false
        var settingsClicked = false

        composeRule.setContent {
            PreviewHost {
                TvLauncherLoginBarContent(
                    onAppsClick = { appsClicked = true },
                    onSystemSettingsClick = { settingsClicked = true },
                )
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.tv_launcher_all_apps_button))
            .performClick()
        assertTrue(appsClicked)

        composeRule
            .onNodeWithText(context.getString(R.string.tv_launcher_system_settings))
            .performClick()
        assertTrue(settingsClicked)
    }

    @Test
    fun `AppNavigationDrawerContent renders Android Settings only when callback provided`() {
        var systemSettingsClicked = false

        composeRule.setContent {
            PreviewHost {
                AppNavigationDrawerContent(
                    currentDestination = null,
                    userName = "Alex",
                    userProfileImageUrl = null,
                    serverName = "Home",
                    connectionType = ConnectionType.LOCAL,
                    isAdmin = false,
                    isOffline = false,
                    favoritesCount = 0,
                    watchlistCount = 0,
                    isJellyseerrAuthenticated = false,
                    isAudiobookshelfAuthenticated = false,
                    hasLiveTvAccess = false,
                    librariesInDrawer = false,
                    libraries = emptyList(),
                    selectedLibraryId = null,
                    sessionState = SessionSwitcherState(),
                    onDestinationClick = { _: Destination -> },
                    onLibraryClick = {},
                    onSettingsClick = {},
                    onAddAccountClick = { _: Server -> },
                    onSwitchSession = { _, _ -> },
                    onForgetSession = {},
                    onCloseDrawer = {},
                    onSystemSettingsClick = { systemSettingsClicked = true },
                )
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.tv_launcher_system_settings))
            .assertIsDisplayed()
            .performClick()
        assertTrue(systemSettingsClicked)
    }

    @Test
    fun `AppNavigationDrawerContent omits Android Settings when callback is null`() {
        composeRule.setContent {
            PreviewHost {
                AppNavigationDrawerContent(
                    currentDestination = null,
                    userName = "Alex",
                    userProfileImageUrl = null,
                    serverName = "Home",
                    connectionType = ConnectionType.LOCAL,
                    isAdmin = false,
                    isOffline = false,
                    favoritesCount = 0,
                    watchlistCount = 0,
                    isJellyseerrAuthenticated = false,
                    isAudiobookshelfAuthenticated = false,
                    hasLiveTvAccess = false,
                    librariesInDrawer = false,
                    libraries = emptyList(),
                    selectedLibraryId = null,
                    sessionState = SessionSwitcherState(),
                    onDestinationClick = { _: Destination -> },
                    onLibraryClick = {},
                    onSettingsClick = {},
                    onAddAccountClick = { _: Server -> },
                    onSwitchSession = { _, _ -> },
                    onForgetSession = {},
                    onCloseDrawer = {},
                    onSystemSettingsClick = null,
                )
            }
        }

        composeRule
            .onAllNodesWithText(context.getString(R.string.tv_launcher_system_settings))
            .assertCountEquals(0)
    }
}
