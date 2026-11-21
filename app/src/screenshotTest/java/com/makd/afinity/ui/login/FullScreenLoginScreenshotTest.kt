package com.makd.afinity.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.models.user.User
import com.makd.afinity.ui.preview.ScreenshotFullScreenPreviews
import com.makd.afinity.ui.preview.ScreenshotPreviewHost
import com.makd.afinity.ui.preview.previewWidthSizeClass
import java.util.UUID

@PreviewTest
@ScreenshotFullScreenPreviews
@Composable
fun LoginServerStepFullScreenScreenshotTest() {
    ScreenshotPreviewHost(modifier = Modifier.fillMaxSize()) {
        val isExpanded = previewWidthSizeClass() != WindowWidthSizeClass.Compact
        val savedServers =
            listOf(
                Server(id = "srv-1", name = "Home Jellyfin", address = "https://jf.home.local"),
                Server(id = "srv-2", name = "Remote NAS", address = "https://media.example.com"),
            )

        ResponsiveLoginLayout(
            isExpanded = isExpanded,
            compactVerticalPadding = 64.dp,
            compactSpacerHeight = 32.dp,
            expandedVerticalArrangement = Arrangement.Center,
            firstPane = { LoginHeader(isConnected = false, serverUrl = "") },
            secondPane = {
                ManualServerContent(
                    serverUrl = "",
                    savedServers = savedServers,
                    isConnecting = false,
                    showAddServerInput = false,
                    onUrlChange = {},
                    onConnectToServer = {},
                    onSavedServerSelect = {},
                    onAddNewServer = {},
                    onCancelAddServer = {},
                )
            },
        )
    }
}

@PreviewTest
@ScreenshotFullScreenPreviews
@Composable
fun LoginUserStepFullScreenScreenshotTest() {
    ScreenshotPreviewHost(modifier = Modifier.fillMaxSize()) {
        val isExpanded = previewWidthSizeClass() != WindowWidthSizeClass.Compact
        val serverUrl = "https://jellyfin.example.com"
        val users =
            listOf(
                User(
                    id = UUID.nameUUIDFromBytes("user-alex".toByteArray()),
                    name = "Alex",
                    serverId = "srv-1",
                ),
                User(
                    id = UUID.nameUUIDFromBytes("user-sam".toByteArray()),
                    name = "Sam",
                    serverId = "srv-1",
                ),
            )
        val uiState =
            LoginUiState(
                username = "Alex",
                selectedUser = users.first(),
                isConnectedToServer = true,
            )

        ResponsiveLoginLayout(
            isExpanded = isExpanded,
            compactVerticalPadding = 48.dp,
            compactSpacerHeight = 24.dp,
            firstPane = {
                LoginHeader(isConnected = true, serverUrl = serverUrl)
                Spacer(modifier = Modifier.height(24.dp))
                UserSelectionAndTabs(
                    uiState = uiState,
                    savedUsers = listOf(users.first()),
                    publicUsers = listOf(users.last()),
                    serverUrl = serverUrl,
                    selectedMethod = LoginMethod.PASSWORD,
                    onMethodChange = {},
                    onUserSelect = {},
                    onSavedUserLogin = {},
                    onForgetSavedUser = {},
                )
            },
            secondPane = {
                PasswordForm(
                    uiState = uiState,
                    onUsernameChange = {},
                    onPasswordChange = {},
                    onLogin = {},
                )
            },
        )
    }
}

@Composable
private fun ResponsiveLoginLayout(
    isExpanded: Boolean,
    compactVerticalPadding: Dp,
    compactSpacerHeight: Dp,
    expandedVerticalArrangement: Arrangement.Vertical = Arrangement.Top,
    firstPane: @Composable () -> Unit,
    secondPane: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        if (isExpanded) {
            Row(
                modifier =
                    Modifier.widthIn(max = 1000.dp)
                        .fillMaxSize()
                        .padding(horizontal = 32.dp, vertical = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = expandedVerticalArrangement,
                ) {
                    firstPane()
                }
                VerticalDivider()
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = expandedVerticalArrangement,
                ) {
                    secondPane()
                }
            }
        } else {
            Column(
                modifier =
                    Modifier.widthIn(max = 550.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = compactVerticalPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                firstPane()
                Spacer(modifier = Modifier.height(compactSpacerHeight))
                secondPane()
            }
        }
    }
}
