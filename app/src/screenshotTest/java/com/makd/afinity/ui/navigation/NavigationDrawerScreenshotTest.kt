package com.makd.afinity.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import com.android.tools.screenshot.PreviewTest
import com.makd.afinity.data.models.server.ConnectionType
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.components.AppModalNavigationDrawer
import com.makd.afinity.ui.components.AppNavigationDrawerContent
import com.makd.afinity.ui.preview.ScreenshotPreviewFixtures
import com.makd.afinity.ui.preview.ScreenshotPreviewHost
import com.makd.afinity.ui.preview.ScreenshotWindowPreviews
import com.makd.afinity.ui.settings.SessionSwitcherState

@PreviewTest
@ScreenshotWindowPreviews
@Composable
fun NavigationDrawerScreenshotTest() {
    ScreenshotPreviewHost(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val railState = rememberWideNavigationRailState(WideNavigationRailValue.Expanded)
            val homeDestination = remember {
                NavDestination("composable").apply { route = Destination.HOME.route }
            }

            AppModalNavigationDrawer(state = railState) {
                AppNavigationDrawerContent(
                    currentDestination = homeDestination,
                    userName = "jklinker",
                    userProfileImageUrl = "preview://avatar/jklinker",
                    serverName = "unraid-server",
                    connectionType = ConnectionType.REMOTE,
                    isAdmin = true,
                    isOffline = false,
                    favoritesCount = 2,
                    watchlistCount = 0,
                    isJellyseerrAuthenticated = false,
                    isAudiobookshelfAuthenticated = true,
                    hasLiveTvAccess = true,
                    librariesInDrawer = false,
                    libraries = ScreenshotPreviewFixtures.collections,
                    selectedLibraryId = null,
                    sessionState = SessionSwitcherState(),
                    onDestinationClick = {},
                    onLibraryClick = {},
                    onSettingsClick = {},
                    onAddAccountClick = {},
                    onSwitchSession = { _, _ -> },
                    onForgetSession = {},
                    onCloseDrawer = {},
                    appVersionName = "0.12.10",
                )
            }
        }
    }
}
