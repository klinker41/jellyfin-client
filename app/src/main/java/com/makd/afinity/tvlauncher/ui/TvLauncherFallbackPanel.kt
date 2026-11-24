package com.makd.afinity.tvlauncher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.tvlauncher.TvLauncherManager
import com.makd.afinity.ui.components.LocalPinnedDrawerStartInset
import com.makd.afinity.ui.components.afinityTopAppBarHeight
import com.makd.afinity.ui.theme.focusOutline

@Composable
fun TvLauncherFallbackPanel(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    onOpenSystemSettings: (() -> Unit)? = null,
    appsContent: @Composable () -> Unit = {
        TvAppsRowSection(
            modifier =
                Modifier.fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
                    ),
        )
    },
) {
    val context = LocalContext.current
    val handleSystemSettings: () -> Unit =
        onOpenSystemSettings ?: { TvLauncherManager.openSystemSettings(context) }
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = statusBarHeight + afinityTopAppBarHeight() + 24.dp,
                    bottom = bottomPadding + 24.dp,
                ),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(start = LocalPinnedDrawerStartInset.current)
                    .windowInsetsPadding(
                        WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
                    )
                    .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.tv_launcher_offline_apps_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (actionText != null && onActionClick != null) {
                    Button(
                        onClick = onActionClick,
                        modifier = Modifier.focusOutline(),
                    ) {
                        Text(text = actionText)
                    }
                }
                OutlinedButton(
                    onClick = handleSystemSettings,
                    modifier = Modifier.focusOutline(),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_settings),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.tv_launcher_system_settings))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        appsContent()
    }
}

@Composable
fun TvLauncherLoginBar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var showAppsDialog by remember { mutableStateOf(false) }

    if (showAppsDialog) {
        TvLauncherAppsDialog(onDismiss = { showAppsDialog = false })
    }

    TvLauncherLoginBarContent(
        onAppsClick = { showAppsDialog = true },
        onSystemSettingsClick = { TvLauncherManager.openSystemSettings(context) },
        modifier = modifier,
    )
}

@Composable
fun TvLauncherLoginBarContent(
    onAppsClick: () -> Unit,
    onSystemSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onAppsClick,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            modifier = Modifier.focusOutline(),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_view_module),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = stringResource(R.string.tv_launcher_all_apps_button))
        }

        OutlinedButton(
            onClick = onSystemSettingsClick,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            modifier = Modifier.focusOutline(),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_settings),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = stringResource(R.string.tv_launcher_system_settings))
        }
    }
}
