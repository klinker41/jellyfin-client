package com.makd.afinity.tvlauncher

import android.app.Application
import android.content.Context
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.makd.afinity.R
import com.makd.afinity.data.models.server.ConnectionType
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.navigation.Destination
import com.makd.afinity.tvlauncher.ui.TvAppsRowContent
import com.makd.afinity.tvlauncher.ui.TvLauncherFallbackPanel
import com.makd.afinity.tvlauncher.ui.TvLauncherLoginBarContent
import com.makd.afinity.tvlauncher.ui.TvLauncherSettingsContent
import com.makd.afinity.tvlauncher.ui.TvLauncherTopBarActionsContent
import com.makd.afinity.ui.components.AppNavigationDrawerContent
import com.makd.afinity.ui.components.LocalPinnedDrawerStartInset
import com.makd.afinity.ui.preview.PreviewHost
import com.makd.afinity.ui.settings.SessionSwitcherState
import org.junit.Assert.assertEquals
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
    fun `TvAppsRowContent launches on click and shows actions dialog on long click`() {
        var launchedApp: TvInstalledApp? = null
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

        // Long click non-system app -> Open, App info, Uninstall all shown
        composeRule.onNodeWithContentDescription("User App").performTouchInput { longClick() }
        composeRule
            .onNodeWithText(context.getString(R.string.tv_launcher_app_action_open))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.tv_launcher_app_action_uninstall))
            .assertIsDisplayed()
            .performClick()
        assertEquals(userApp, uninstalledApp)

        // Long click system app -> Uninstall omitted, App info fires callback
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
