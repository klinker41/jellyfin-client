package com.makd.afinity.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.makd.afinity.R
import com.makd.afinity.ui.components.SettingsDivider
import com.makd.afinity.ui.components.SettingsGroup
import com.makd.afinity.ui.components.SettingsItem
import com.makd.afinity.ui.components.SettingsSwitchItem
import com.makd.afinity.ui.preview.ScreenshotComponentPreviews
import com.makd.afinity.ui.preview.ScreenshotPreviewHost

@PreviewTest
@ScreenshotComponentPreviews
@Composable
fun SettingsBuildingBlocksScreenshotTest() {
    ScreenshotPreviewHost {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsGroup(title = "Playback") {
                SettingsItem(
                    title = "Preferred Player",
                    subtitle = "ExoPlayer",
                    icon = painterResource(id = R.drawable.ic_player_play_filled),
                    onClick = {},
                )
                SettingsDivider()
                SettingsSwitchItem(
                    title = "Auto-skip intros",
                    subtitle = "Automatically skip detected intro segments",
                    checked = true,
                    onCheckedChange = {},
                    icon = painterResource(id = R.drawable.ic_player_skip_forward),
                )
                SettingsDivider()
                SettingsSwitchItem(
                    title = "Force transcode",
                    subtitle = "Disabled while offline",
                    checked = false,
                    enabled = false,
                    onCheckedChange = {},
                    icon = painterResource(id = R.drawable.ic_cloud_off),
                )
            }

            SettingsGroup(title = "Connections") {
                SettingsItem(
                    title = "Home Server",
                    subtitle = "Connected via Local Network",
                    subtitleColor = MaterialTheme.colorScheme.primary,
                    icon = painterResource(id = R.drawable.ic_server),
                    onClick = {},
                )
            }
        }
    }
}
