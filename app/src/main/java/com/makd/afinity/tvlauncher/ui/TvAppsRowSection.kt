package com.makd.afinity.tvlauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
import com.makd.afinity.ui.components.windowHeightDp
import com.makd.afinity.ui.home.components.HomeSectionHeader
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.focusOutline
import com.makd.afinity.ui.utils.rememberTvInitialFocusRequester

private val TvAppTileWidth = 176.dp

@Composable
fun TvAppsRowSection(
    modifier: Modifier = Modifier,
    viewModel: TvLauncherViewModel = hiltViewModel(),
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    if (apps.isEmpty()) return

    val pinnedApps by viewModel.pinnedApps.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAllAppsDialog by remember { mutableStateOf(false) }

    if (showAllAppsDialog) {
        TvLauncherAppsDialog(onDismiss = { showAllAppsDialog = false })
    }

    TvAppsRowContent(
        apps = pinnedApps,
        onAppClick = { app -> viewModel.launchApp(context, app) },
        onAllAppsClick = { showAllAppsDialog = true },
        onUnpinClick = { app -> viewModel.setAppPinned(app.packageName, pinned = false) },
        onAppInfoClick = { app -> TvLauncherManager.openAppInfo(context, app.packageName) },
        onUninstallClick = { app -> TvLauncherManager.uninstallApp(context, app.packageName) },
        modifier = modifier,
    )
}

@Composable
fun TvAppsRowContent(
    apps: List<TvInstalledApp>,
    onAppClick: (TvInstalledApp) -> Unit,
    onAllAppsClick: () -> Unit,
    onUnpinClick: (TvInstalledApp) -> Unit,
    onAppInfoClick: (TvInstalledApp) -> Unit,
    onUninstallClick: (TvInstalledApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedAppForMenu by remember { mutableStateOf<TvInstalledApp?>(null) }

    selectedAppForMenu?.let { app ->
        TvAppActionsDialog(
            app = app,
            isPinned = true,
            onOpen = {
                selectedAppForMenu = null
                onAppClick(app)
            },
            onTogglePin = {
                selectedAppForMenu = null
                onUnpinClick(app)
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
                    isPinned = false,
                    onClick = { onAppClick(app) },
                    onLongClick = { selectedAppForMenu = app },
                )
            }

            item(key = "tv_all_apps_button", contentType = "tv_all_apps_tile") {
                TvAllAppsTile(cardWidth = TvAppTileWidth, onClick = onAllAppsClick)
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
    val pinnedPackageNames by viewModel.pinnedPackageNames.collectAsStateWithLifecycle()
    val context = LocalContext.current

    TvLauncherAppsDialogContent(
        apps = apps,
        pinnedPackageNames = pinnedPackageNames,
        onAppClick = { app ->
            onDismiss()
            viewModel.launchApp(context, app)
        },
        onTogglePin = { app, pinned -> viewModel.setAppPinned(app.packageName, pinned) },
        onAppInfoClick = { app -> TvLauncherManager.openAppInfo(context, app.packageName) },
        onUninstallClick = { app -> TvLauncherManager.uninstallApp(context, app.packageName) },
        onDismiss = onDismiss,
        modifier = modifier,
    )
}

@Composable
fun TvLauncherAppsDialogContent(
    apps: List<TvInstalledApp>,
    pinnedPackageNames: Set<String>,
    onAppClick: (TvInstalledApp) -> Unit,
    onTogglePin: (TvInstalledApp, Boolean) -> Unit,
    onAppInfoClick: (TvInstalledApp) -> Unit,
    onUninstallClick: (TvInstalledApp) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedAppForMenu by remember { mutableStateOf<TvInstalledApp?>(null) }

    selectedAppForMenu?.let { app ->
        val isPinned = app.packageName in pinnedPackageNames
        TvAppActionsDialog(
            app = app,
            isPinned = isPinned,
            onOpen = {
                selectedAppForMenu = null
                onAppClick(app)
            },
            onTogglePin = {
                selectedAppForMenu = null
                onTogglePin(app, !isPinned)
            },
            onAppInfo = {
                selectedAppForMenu = null
                onDismiss()
                onAppInfoClick(app)
            },
            onUninstall = {
                selectedAppForMenu = null
                onDismiss()
                onUninstallClick(app)
            },
            onDismiss = { selectedAppForMenu = null },
        )
    }

    val maxDialogHeight = windowHeightDp() * 0.85f

    Dialog(
        onDismissRequest = onDismiss,
        properties = afinityDialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                modifier
                    .padding(16.dp)
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxDialogHeight),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                Text(
                    text = stringResource(R.string.tv_launcher_apps_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.tv_launcher_apps_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 152.dp),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                ) {
                    items(
                        items = apps,
                        key = { app -> "dialog_tv_app_${app.packageName}" },
                    ) { app ->
                        TvAppTile(
                            app = app,
                            cardWidth = null,
                            isPinned = app.packageName in pinnedPackageNames,
                            onClick = { onAppClick(app) },
                            onLongClick = { selectedAppForMenu = app },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
                }
            }
        }
    }
}

@Composable
private fun TvAllAppsTile(
    cardWidth: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.tv_launcher_all_apps_tile)
    Column(modifier = modifier.width(cardWidth)) {
        Surface(
            shape = CardDefaults.shape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier =
                Modifier.fillMaxWidth()
                    .aspectRatio(CardDimensions.ASPECT_RATIO_LANDSCAPE)
                    .focusOutline(CardDefaults.shape)
                    .clip(CardDefaults.shape)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = label,
                        onClick = onClick,
                    ),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_layout_grid),
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(CardDimensions.CardTextSpacing))

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TvAppTile(
    app: TvInstalledApp,
    cardWidth: Dp?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPinned: Boolean = false,
) {
    val context = LocalContext.current
    val cached = remember(app.packageName) { TvAppArtworkCache.getCached(app.packageName) }
    val artwork by
        produceState<TvAppArtwork?>(initialValue = cached, key1 = app.packageName) {
            if (value == null) {
                value = TvAppArtworkCache.load(context, app)
            }
        }

    Column(
        modifier = if (cardWidth != null) modifier.width(cardWidth) else modifier.fillMaxWidth(),
    ) {
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

                if (isPinned) {
                    Box(
                        modifier =
                            Modifier.align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(24.dp)
                                .background(
                                    color =
                                        MaterialTheme.colorScheme.primaryContainer.copy(
                                            alpha = 0.92f,
                                        ),
                                    shape = CircleShape,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_pin),
                            contentDescription = stringResource(R.string.tv_launcher_cd_pinned_app),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(14.dp),
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
    isPinned: Boolean,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val observedKeyDowns = remember(app.packageName) { mutableSetOf<Key>() }

    AlertDialog(
        properties = afinityDialogProperties(),
        onDismissRequest = onDismiss,
        modifier =
            Modifier.onPreviewKeyEvent { event ->
                consumeStaleActivationKey(event, observedKeyDowns)
            },
        title = { Text(app.label) },
        text = {
            val initialFocusRequester = rememberTvInitialFocusRequester(key = app.packageName)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onOpen,
                    modifier =
                        Modifier.fillMaxWidth()
                            .then(
                                if (initialFocusRequester != null) {
                                    Modifier.focusRequester(initialFocusRequester)
                                } else {
                                    Modifier
                                },
                            ),
                ) {
                    Text(stringResource(R.string.tv_launcher_app_action_open))
                }
                TextButton(
                    onClick = onTogglePin,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (isPinned) R.string.tv_launcher_app_action_unpin
                            else R.string.tv_launcher_app_action_pin,
                        ),
                    )
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

private fun consumeStaleActivationKey(
    event: KeyEvent,
    observedKeyDowns: MutableSet<Key>,
): Boolean {
    if (!event.key.isActivationKey()) return false
    return when (event.type) {
        KeyEventType.KeyDown -> {
            if (event.nativeKeyEvent.repeatCount > 0) {
                true
            } else {
                observedKeyDowns.add(event.key)
                false
            }
        }
        KeyEventType.KeyUp -> !observedKeyDowns.remove(event.key)
        else -> false
    }
}

private fun Key.isActivationKey(): Boolean =
    this == Key.DirectionCenter ||
        this == Key.Enter ||
        this == Key.NumPadEnter ||
        this == Key.Spacebar
