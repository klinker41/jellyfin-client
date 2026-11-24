package com.makd.afinity.tvlauncher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.tvlauncher.DisableLauncherResult
import com.makd.afinity.tvlauncher.TvLauncherManager
import com.makd.afinity.tvlauncher.TvLauncherStatus
import com.makd.afinity.tvlauncher.TvLauncherViewModel
import com.makd.afinity.tvlauncher.buildDisableStockLauncherCommand
import com.makd.afinity.ui.components.SETTINGS_TEXT_START_PADDING
import com.makd.afinity.ui.components.SettingsDivider
import com.makd.afinity.ui.components.SettingsGroup
import com.makd.afinity.ui.components.SettingsItem
import com.makd.afinity.ui.components.SettingsSwitchItem
import com.makd.afinity.ui.components.afinityDialogProperties

@Composable
fun TvLauncherSettingsSection(
    modifier: Modifier = Modifier,
    endPadding: Dp = 16.dp,
    viewModel: TvLauncherViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val disableBlocked by viewModel.disableBlocked.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    TvLauncherSettingsContent(
        status = status,
        disableBlocked = disableBlocked,
        endPadding = endPadding,
        onToggleLauncher = viewModel::onToggleLauncher,
        onRefreshStatus = viewModel::refreshStatus,
        onOpenSystemSettings = { TvLauncherManager.openSystemSettings(context) },
        onRetryDisable = viewModel::retryDisableAfterCheck,
        onDismissDisableBlocked = viewModel::dismissDisableBlockedDialog,
        modifier = modifier,
    )
}

@Composable
fun TvLauncherSettingsContent(
    status: TvLauncherStatus,
    disableBlocked: DisableLauncherResult.BlockedNoOtherLauncher?,
    endPadding: Dp,
    onToggleLauncher: (Boolean) -> Unit,
    onRefreshStatus: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onRetryDisable: () -> Unit,
    onDismissDisableBlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (disableBlocked != null) {
        AlertDialog(
            properties = afinityDialogProperties(),
            onDismissRequest = onDismissDisableBlocked,
            title = { Text(stringResource(R.string.tv_launcher_disable_blocked_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.tv_launcher_disable_blocked_message),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    AdbCommandBox(command = disableBlocked.restoreCommand)
                }
            },
            confirmButton = {
                Button(onClick = onRetryDisable) {
                    Text(stringResource(R.string.tv_launcher_check_again))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDisableBlocked) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    SettingsGroup(
        title = stringResource(R.string.pref_group_tv_launcher),
        endPadding = endPadding,
        modifier = modifier,
    ) {
        val subtitle =
            when {
                status.isDefaultLauncher ->
                    stringResource(R.string.pref_tv_launcher_subtitle_active)
                status.aliasEnabled -> stringResource(R.string.pref_tv_launcher_subtitle_pending)
                else -> stringResource(R.string.pref_tv_launcher_subtitle_off)
            }

        SettingsSwitchItem(
            icon = painterResource(id = R.drawable.ic_tv),
            title = stringResource(R.string.pref_tv_launcher_title),
            subtitle = subtitle,
            checked = status.aliasEnabled,
            onCheckedChange = onToggleLauncher,
        )

        if (status.aliasEnabled) {
            SettingsDivider()
            AdbSetupInstructionsBlock(
                setHomeCommand = status.setHomeCommand,
                stockLauncherPackage = status.stockLauncherPackage,
            )

            SettingsDivider()
            SettingsItem(
                icon = painterResource(id = R.drawable.ic_refresh),
                title = stringResource(R.string.tv_launcher_refresh_status),
                subtitle = stringResource(R.string.tv_launcher_refresh_status_subtitle),
                onClick = onRefreshStatus,
            )

            SettingsDivider()
            SettingsItem(
                icon = painterResource(id = R.drawable.ic_settings),
                title = stringResource(R.string.tv_launcher_system_settings),
                subtitle = stringResource(R.string.tv_launcher_system_settings_subtitle),
                onClick = onOpenSystemSettings,
            )
        }
    }
}

@Composable
private fun AdbSetupInstructionsBlock(
    setHomeCommand: String,
    stockLauncherPackage: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = SETTINGS_TEXT_START_PADDING,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 12.dp,
                ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.tv_launcher_adb_title),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.tv_launcher_adb_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AdbCommandBox(command = setHomeCommand)

        val stockPkg = stockLauncherPackage ?: TvLauncherManager.DEFAULT_GOOGLE_TV_LAUNCHER
        Text(
            text = stringResource(R.string.tv_launcher_adb_fallback_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AdbCommandBox(command = buildDisableStockLauncherCommand(stockPkg))
    }
}

@Composable
private fun AdbCommandBox(command: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = command,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}
