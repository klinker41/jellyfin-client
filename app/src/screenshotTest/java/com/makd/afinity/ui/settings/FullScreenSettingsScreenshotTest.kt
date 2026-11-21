package com.makd.afinity.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.makd.afinity.R
import com.makd.afinity.data.models.server.ConnectionType
import com.makd.afinity.ui.components.AfinityBackButton
import com.makd.afinity.ui.components.AfinityScreen
import com.makd.afinity.ui.components.AfinityTopAppBarTitle
import com.makd.afinity.ui.components.AfinityTopBar
import com.makd.afinity.ui.components.SettingsDivider
import com.makd.afinity.ui.components.SettingsGroup
import com.makd.afinity.ui.components.SettingsItem
import com.makd.afinity.ui.components.SettingsSwitchItem
import com.makd.afinity.ui.preview.ScreenshotFullScreenPreviews
import com.makd.afinity.ui.preview.ScreenshotPreviewHost

@PreviewTest
@ScreenshotFullScreenPreviews
@Composable
fun SettingsFullScreenScreenshotTest() {
    ScreenshotPreviewHost(modifier = Modifier.fillMaxSize()) {
        AfinityScreen(
            topBar = { backgroundOpacity ->
                AfinityTopBar(
                    title = { AfinityTopAppBarTitle(stringResource(R.string.settings_title)) },
                    navigationIcon = { AfinityBackButton(onClick = {}) },
                    backgroundOpacity = backgroundOpacity,
                )
            },
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxSize(),
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        top = innerPadding.calculateTopPadding() + 16.dp,
                        bottom = innerPadding.calculateBottomPadding() + 24.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item(key = "profile") {
                    ProfileHeader(
                        userName = "Alex Mercer",
                        serverName = "Home Jellyfin",
                        serverUrl = "https://jellyfin.example.com",
                        serverVersion = "10.10.3",
                        userProfileImageUrl = "preview://avatar/alex",
                        connectionType = ConnectionType.LOCAL,
                    )
                }

                item(key = "general") {
                    SettingsGroup(title = stringResource(R.string.pref_group_general)) {
                        SettingsSwitchItem(
                            icon = painterResource(id = R.drawable.ic_cloud_off),
                            title = stringResource(R.string.pref_offline_mode),
                            subtitle = "Stream cached media only",
                            checked = false,
                            onCheckedChange = {},
                        )
                        SettingsDivider()
                        SettingsItem(
                            icon = painterResource(id = R.drawable.ic_server),
                            title = "Server Management",
                            subtitle = "2 servers configured",
                            onClick = {},
                        )
                    }
                }

                item(key = "playback") {
                    SettingsGroup(title = "Playback & Appearance") {
                        SettingsItem(
                            icon = painterResource(id = R.drawable.ic_player_play_filled),
                            title = "Player Options",
                            subtitle = "ExoPlayer · Auto-skip enabled",
                            onClick = {},
                        )
                        SettingsDivider()
                        SettingsItem(
                            icon = painterResource(id = R.drawable.ic_color_swatch),
                            title = "Appearance",
                            subtitle = "Material You · Dynamic theme",
                            onClick = {},
                        )
                        SettingsDivider()
                        SettingsItem(
                            icon = painterResource(id = R.drawable.ic_download),
                            title = "Downloads & Storage",
                            subtitle = "42.1 GB used",
                            onClick = {},
                        )
                    }
                }
            }
        }
    }
}
