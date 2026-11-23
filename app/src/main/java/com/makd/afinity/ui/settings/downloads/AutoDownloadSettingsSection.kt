package com.makd.afinity.ui.settings.downloads

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.ui.components.AfinityModalBottomSheet
import com.makd.afinity.ui.components.SettingsDivider
import com.makd.afinity.ui.components.SettingsGroup
import com.makd.afinity.ui.components.SettingsItem
import com.makd.afinity.ui.components.SettingsSwitchItem
import java.util.UUID

@Composable
fun AutoDownloadSettingsSection(
    modifier: Modifier = Modifier,
    viewModel: AutoDownloadSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AutoDownloadSettingsContent(
        uiState = uiState,
        onToggleLibrary = viewModel::toggleLibrary,
        onSyncNow = viewModel::syncNow,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoDownloadSettingsContent(
    uiState: AutoDownloadSettingsUiState,
    onToggleLibrary: (UUID, Boolean) -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLibraryPicker by rememberSaveable { mutableStateOf(false) }

    val enabledLibraries = uiState.libraries.filter { it.enabled }
    val librariesSubtitle =
        when {
            !uiState.canDownload -> stringResource(R.string.pref_auto_download_not_allowed)
            enabledLibraries.isEmpty() -> stringResource(R.string.pref_auto_download_libraries_off)
            else -> enabledLibraries.joinToString(", ") { it.name }
        }

    SettingsGroup(
        title = stringResource(R.string.section_auto_download),
        modifier = modifier,
    ) {
        SettingsItem(
            icon = painterResource(id = R.drawable.ic_download),
            title = stringResource(R.string.pref_auto_download_libraries_title),
            subtitle = librariesSubtitle,
            enabled = uiState.canDownload,
            onClick = { showLibraryPicker = true },
        )

        if (uiState.canDownload && enabledLibraries.isNotEmpty()) {
            SettingsDivider()
            SettingsItem(
                icon = painterResource(id = R.drawable.ic_download_arrow),
                title = stringResource(R.string.pref_auto_download_sync_now_title),
                subtitle =
                    stringResource(
                        if (uiState.isSyncing) R.string.pref_auto_download_syncing
                        else R.string.pref_auto_download_sync_now_summary,
                    ),
                enabled = !uiState.isSyncing,
                onClick = onSyncNow,
                trailing =
                    if (uiState.isSyncing) {
                        {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    } else null,
            )
        }
    }

    if (showLibraryPicker) {
        AfinityModalBottomSheet(onDismissRequest = { showLibraryPicker = false }) {
            AutoDownloadLibrariesSheetContent(
                libraries = uiState.libraries,
                onToggleLibrary = onToggleLibrary,
            )
        }
    }
}

@Composable
fun AutoDownloadLibrariesSheetContent(
    libraries: List<AutoDownloadLibraryOption>,
    onToggleLibrary: (UUID, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(
            text = stringResource(R.string.pref_auto_download_libraries_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Text(
            text = stringResource(R.string.pref_auto_download_libraries_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )

        if (libraries.isEmpty()) {
            Text(
                text = stringResource(R.string.pref_auto_download_no_libraries),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                itemsIndexed(libraries, key = { _, item -> item.id }) { index, library ->
                    SettingsSwitchItem(
                        icon = painterResource(id = libraryIconRes(library)),
                        title = library.name,
                        checked = library.enabled,
                        onCheckedChange = { checked -> onToggleLibrary(library.id, checked) },
                    )
                    if (index < libraries.lastIndex) {
                        SettingsDivider()
                    }
                }
            }
        }
    }
}

@DrawableRes
private fun libraryIconRes(library: AutoDownloadLibraryOption): Int =
    when {
        library.isPodcast -> R.drawable.ic_apple_podcast
        library.type == CollectionType.Books -> R.drawable.ic_book_audio
        library.type == CollectionType.Music -> R.drawable.ic_music
        library.type == CollectionType.Movies -> R.drawable.ic_movie
        library.type == CollectionType.TvShows -> R.drawable.ic_tv
        library.type == CollectionType.MusicVideos -> R.drawable.ic_music_video
        else -> R.drawable.ic_folder
    }
