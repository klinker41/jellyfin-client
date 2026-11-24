package com.makd.afinity.tvlauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.tvlauncher.TvAppArtwork
import com.makd.afinity.tvlauncher.TvAppArtworkCache
import com.makd.afinity.tvlauncher.TvInstalledApp
import com.makd.afinity.tvlauncher.TvLauncherManager
import com.makd.afinity.tvlauncher.TvLauncherViewModel
import com.makd.afinity.ui.components.CardRow
import com.makd.afinity.ui.components.afinityDialogProperties
import com.makd.afinity.ui.home.components.HomeSectionHeader
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.focusOutline

private val TvAppTileWidth = 176.dp

@Composable
fun TvAppsRowSection(
    modifier: Modifier = Modifier,
    viewModel: TvLauncherViewModel = hiltViewModel(),
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    if (apps.isEmpty()) return

    val context = LocalContext.current
    TvAppsRowContent(
        apps = apps,
        onAppClick = { app -> viewModel.launchApp(context, app) },
        onAppInfoClick = { app -> TvLauncherManager.openAppInfo(context, app.packageName) },
        onUninstallClick = { app -> TvLauncherManager.uninstallApp(context, app.packageName) },
        modifier = modifier,
    )
}

@Composable
fun TvAppsRowContent(
    apps: List<TvInstalledApp>,
    onAppClick: (TvInstalledApp) -> Unit,
    onAppInfoClick: (TvInstalledApp) -> Unit,
    onUninstallClick: (TvInstalledApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedAppForMenu by remember { mutableStateOf<TvInstalledApp?>(null) }

    selectedAppForMenu?.let { app ->
        TvAppActionsDialog(
            app = app,
            onOpen = {
                selectedAppForMenu = null
                onAppClick(app)
            },
            onAppInfo = {
                selectedAppForMenu = null
                onAppInfoClick(app)
            },
            onUninstall = {
                selectedAppForMenu = null
                onUninstallClick(app)
            },
            onDismiss = { selectedAppForMenu = null },
        )
    }

    val fixedRowHeight =
        CardDimensions.rowHeight(
            cardWidth = TvAppTileWidth,
            aspectRatio = CardDimensions.ASPECT_RATIO_LANDSCAPE,
            metadataHeight = 0.dp,
        )

    Column(modifier = modifier) {
        HomeSectionHeader(
            title = stringResource(R.string.tv_launcher_home_apps_title),
            modifier = Modifier.padding(horizontal = CardDimensions.RowGutter),
        )

        CardRow(
            modifier = Modifier.fillMaxWidth().height(fixedRowHeight),
            contentPadding = CardDimensions.RowContentPadding,
        ) {
            items(
                items = apps,
                key = { app -> "tv_app_${app.packageName}" },
                contentType = { "tv_app_tile" },
            ) { app ->
                TvAppTile(
                    app = app,
                    cardWidth = TvAppTileWidth,
                    onClick = { onAppClick(app) },
                    onLongClick = { selectedAppForMenu = app },
                )
            }
        }
    }
}

@Composable
fun TvLauncherAppsDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TvLauncherViewModel = hiltViewModel(),
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val context = LocalContext.current

    AlertDialog(
        properties = afinityDialogProperties(),
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(R.string.tv_launcher_apps_dialog_title)) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 152.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().height(340.dp),
            ) {
                items(
                    items = apps,
                    key = { app -> "dialog_tv_app_${app.packageName}" },
                ) { app ->
                    TvAppTile(
                        app = app,
                        cardWidth = 152.dp,
                        onClick = {
                            onDismiss()
                            viewModel.launchApp(context, app)
                        },
                        onLongClick = {
                            onDismiss()
                            TvLauncherManager.openAppInfo(context, app.packageName)
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun TvAppTile(
    app: TvInstalledApp,
    cardWidth: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val cached = remember(app.packageName) { TvAppArtworkCache.getCached(app.packageName) }
    val artwork by
        produceState<TvAppArtwork?>(initialValue = cached, key1 = app.packageName) {
            if (value == null) {
                value = TvAppArtworkCache.load(context, app)
            }
        }

    Column(modifier = modifier.width(cardWidth)) {
        Surface(
            shape = CardDefaults.shape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier =
                Modifier.fillMaxWidth()
                    .aspectRatio(CardDimensions.ASPECT_RATIO_LANDSCAPE)
                    .focusOutline(CardDefaults.shape)
                    .clip(CardDefaults.shape)
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                    ),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                val loaded = artwork
                when {
                    loaded != null && loaded.isBanner -> {
                        Image(
                            bitmap = loaded.image,
                            contentDescription = app.label,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    loaded != null -> {
                        Box(
                            modifier =
                                Modifier.fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                bitmap = loaded.image,
                                contentDescription = app.label,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(52.dp),
                            )
                        }
                    }
                    else -> {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_view_module),
                            contentDescription = app.label,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(CardDimensions.CardTextSpacing))

        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TvAppActionsDialog(
    app: TvInstalledApp,
    onOpen: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        properties = afinityDialogProperties(),
        onDismissRequest = onDismiss,
        title = { Text(app.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onOpen,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.tv_launcher_app_action_open))
                }
                TextButton(
                    onClick = onAppInfo,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.tv_launcher_app_action_info))
                }
                if (!app.isSystemApp) {
                    TextButton(
                        onClick = onUninstall,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.tv_launcher_app_action_uninstall),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}
