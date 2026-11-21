package com.makd.afinity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.makd.afinity.R
import com.makd.afinity.data.models.server.ConnectionType
import com.makd.afinity.ui.preview.ScreenshotComponentPreviews
import com.makd.afinity.ui.preview.ScreenshotPreviewHost

@OptIn(ExperimentalLayoutApi::class)
@PreviewTest
@ScreenshotComponentPreviews
@Composable
fun BadgesAndAvatarsScreenshotTest() {
    ScreenshotPreviewHost {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AfinityBadge(text = "4K")
                AfinityBadge(
                    text = "HDR10+",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                AfinityBadge(
                    text = "TRANSCODING",
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier.size(width = 110.dp, height = 72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    PlayedBadge(modifier = Modifier.align(Alignment.TopEnd))
                    FourKBadge(modifier = Modifier.align(Alignment.TopStart))
                    MediaCountBadge(text = "12", modifier = Modifier.align(Alignment.BottomEnd))
                    UnavailableBadge(modifier = Modifier.align(Alignment.BottomStart))
                }
                Box(
                    modifier =
                        Modifier.size(width = 110.dp, height = 72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    AudiobookBadge(modifier = Modifier.align(Alignment.TopStart))
                    MediaStatusBadge(
                        text = "UPCOMING",
                        containerColor = Color(0xFF2E7D32),
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
                // Narrow card (< 66dp) triggers dot-indicator fallback in CardPill
                Box(
                    modifier =
                        Modifier.size(width = 56.dp, height = 72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    MediaCountBadge(text = "8", modifier = Modifier.align(Alignment.TopEnd))
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConnectionType.entries.forEach { type ->
                    ConnectionIndicatorBadge(connectionType = type, size = 24.dp, iconSize = 14.dp)
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(imageUrl = "preview://user/alex", name = "Alex", size = 48.dp)
                UserAvatar(imageUrl = null, name = "Maya", size = 48.dp)
                UserAvatar(imageUrl = null, name = "Sam", size = 36.dp)
                UserAvatar(imageUrl = null, name = null, size = 48.dp)
            }
        }
    }
}

@PreviewTest
@ScreenshotComponentPreviews
@Composable
fun TabsChipsAndFieldsScreenshotTest() {
    ScreenshotPreviewHost {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AfinitySegmentedTabs(
                tabs =
                    listOf(
                        SegmentedTab("Movies", 12),
                        SegmentedTab("Shows", 5),
                        SegmentedTab("Audio"),
                    ),
                selectedIndex = 0,
                onSelect = {},
            )

            AfinitySegmentedTabs(
                tabs =
                    listOf(
                        SegmentedTab("All"),
                        SegmentedTab("Action", 4),
                        SegmentedTab("Sci-Fi", 9),
                        SegmentedTab("Drama", 7),
                        SegmentedTab("Comedy", 3),
                    ),
                selectedIndex = 2,
                onSelect = {},
                scrollable = true,
            )

            AfinityToggleChipFlow {
                AfinityToggleChip(label = "4K UHD", selected = true, onSelectedChange = {})
                AfinityToggleChip(label = "Subtitles", selected = false, onSelectedChange = {})
                AfinityToggleChip(
                    label = "Downloaded",
                    selected = false,
                    enabled = false,
                    onSelectedChange = {},
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AfinityTextField(
                    value = "https://jellyfin.home.arpa",
                    onValueChange = {},
                    label = "Server URL",
                    leadingIcon = painterResource(id = R.drawable.ic_server),
                    modifier = Modifier.fillMaxWidth(),
                )
                AfinityTextField(
                    value = "bad-host",
                    onValueChange = {},
                    label = "Address",
                    isError = true,
                    supportingText = "Must start with http:// or https://",
                    trailingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_alert_triangle),
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                ServiceUnreachableBanner(serviceName = "Jellyseerr", onRetry = {})
                Text(
                    text = "End of controls preview",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
