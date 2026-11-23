package com.makd.afinity.ui.settings.downloads

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.autodownload.AutoDownloadManager
import com.makd.afinity.data.autodownload.AutoDownloadPreferences
import com.makd.afinity.data.autodownload.autoDownloadableLibraries
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.podcasts.isPodcastLibrary
import com.makd.afinity.data.repository.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

@Immutable
data class AutoDownloadLibraryOption(
    val id: UUID,
    val name: String,
    val type: CollectionType,
    val isPodcast: Boolean,
    val enabled: Boolean,
)

@Immutable
data class AutoDownloadSettingsUiState(
    val libraries: List<AutoDownloadLibraryOption> = emptyList(),
    val isSyncing: Boolean = false,
    val canDownload: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AutoDownloadSettingsViewModel
@Inject
constructor(
    sessionManager: SessionManager,
    mediaRepository: MediaRepository,
    autoDownloadPreferences: AutoDownloadPreferences,
    private val autoDownloadManager: AutoDownloadManager,
) : ViewModel() {

    private val enabledLibraryIdsFlow =
        sessionManager.currentSession.flatMapLatest { session ->
            if (session == null) {
                flowOf(emptySet())
            } else {
                autoDownloadPreferences.getEnabledLibraryIdsFlow(session.serverId, session.userId)
            }
        }

    val uiState: StateFlow<AutoDownloadSettingsUiState> =
        combine(
                sessionManager.currentSession,
                mediaRepository.libraries,
                enabledLibraryIdsFlow,
                autoDownloadManager.isSyncing,
            ) { session, libraries, enabledIds, isSyncing ->
                val options =
                    libraries.autoDownloadableLibraries().map { lib ->
                        AutoDownloadLibraryOption(
                            id = lib.id,
                            name = lib.name,
                            type = lib.type,
                            isPodcast = lib.isPodcastLibrary(),
                            enabled = lib.id in enabledIds,
                        )
                    }
                AutoDownloadSettingsUiState(
                    libraries = options,
                    isSyncing = isSyncing,
                    canDownload = session?.canDownload != false,
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = AutoDownloadSettingsUiState(),
            )

    init {
        viewModelScope.launch {
            try {
                if (mediaRepository.libraries.first().isEmpty()) {
                    mediaRepository.getLibraries()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to load libraries for auto-download settings")
            }
        }
    }

    fun toggleLibrary(libraryId: UUID, enabled: Boolean) {
        viewModelScope.launch { autoDownloadManager.setLibraryEnabled(libraryId, enabled) }
    }

    fun syncNow() {
        viewModelScope.launch { autoDownloadManager.syncEnabledLibraries() }
    }
}
