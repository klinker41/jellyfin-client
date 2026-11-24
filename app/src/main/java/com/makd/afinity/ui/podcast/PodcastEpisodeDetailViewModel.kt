package com.makd.afinity.ui.podcast

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.R
import com.makd.afinity.data.autodownload.PlayedItemEventBus
import com.makd.afinity.data.manager.DownloadPermissions
import com.makd.afinity.data.manager.MediaRefreshBus
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.manager.RefreshTrigger
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.models.extensions.toAfinityTrack
import com.makd.afinity.data.models.media.UserDataPatch
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.podcasts.PODCAST_EPISODE_DETAIL_FIELDS
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.NoActiveSessionException
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.music.MusicRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.data.store.ItemStore
import com.makd.afinity.ui.components.DownloadConfirm
import com.makd.afinity.ui.components.singleDownloadConfirm
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

private const val TICKS_PER_MS = 10_000L

@Immutable
data class PodcastEpisodeDetailUiState(
    val episode: AfinityTrack? = null,
    val isLoading: Boolean = true,
    @param:StringRes val errorRes: Int? = null,
)

@HiltViewModel
class PodcastEpisodeDetailViewModel
@Inject
constructor(
    private val mediaRepository: MediaRepository,
    private val musicRepository: MusicRepository,
    private val userDataRepository: UserDataRepository,
    private val databaseRepository: DatabaseRepository,
    private val appDataRepository: AppDataRepository,
    private val sessionManager: SessionManager,
    private val mediaRefreshBus: MediaRefreshBus,
    private val playedItemEventBus: PlayedItemEventBus,
    private val itemStore: ItemStore,
    private val downloadRepository: DownloadRepository,
    private val offlineModeManager: OfflineModeManager,
    downloadPermissions: DownloadPermissions,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val itemId: UUID = UUID.fromString(savedStateHandle.get<String>("itemId")!!)

    val isOffline: StateFlow<Boolean> = offlineModeManager.isOffline

    val isDownloadAllowedByServer: StateFlow<Boolean> = downloadPermissions.isAllowedByServer

    val canDownloadOnNetwork: StateFlow<Boolean> = downloadPermissions.isAllowedOnNetwork

    private val _uiState = MutableStateFlow(PodcastEpisodeDetailUiState())
    val uiState: StateFlow<PodcastEpisodeDetailUiState> =
        itemStore.project(viewModelScope, _uiState) { state ->
            val episode = state.episode?.let { liveOwner(it) }
            if (episode === state.episode) state else state.copy(episode = episode)
        }

    val downloadInfo: StateFlow<DownloadInfo?> =
        downloadRepository
            .getAllDownloadsFlow()
            .map { downloads -> downloads.find { it.itemId == itemId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        load()
    }

    fun retry() {
        load()
    }

    private suspend fun loadCachedTrack(): AfinityTrack? {
        val session = sessionManager.currentSession.value ?: return null
        return databaseRepository.getMusicTrack(itemId, session.serverId, session.userId.toString())
    }

    private suspend fun applyPendingLocalProgress(track: AfinityTrack): AfinityTrack {
        val userId = sessionManager.currentSession.value?.userId ?: return track
        val local = databaseRepository.getUserData(userId, track.id)
        return if (local != null && local.toBeSynced) {
            track.copy(played = local.played, playbackPositionTicks = local.playbackPositionTicks)
        } else {
            track
        }
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorRes = null) }
            if (offlineModeManager.isCurrentlyOffline()) {
                val cached = loadCachedTrack()
                _uiState.update {
                    if (cached != null) {
                        it.copy(episode = cached, isLoading = false, errorRes = null)
                    } else {
                        it.copy(isLoading = false, errorRes = R.string.item_error_not_found)
                    }
                }
                return@launch
            }
            mediaRepository
                .getItemResult(itemId, fields = PODCAST_EPISODE_DETAIL_FIELDS)
                .onSuccess { dto ->
                    val rawTrack =
                        dto?.toAfinityTrack(mediaRepository.getBaseUrl()) ?: loadCachedTrack()
                    val track = rawTrack?.let { applyPendingLocalProgress(it) }
                    _uiState.update {
                        if (track != null) {
                            it.copy(episode = track, isLoading = false, errorRes = null)
                        } else {
                            it.copy(isLoading = false, errorRes = R.string.item_error_not_found)
                        }
                    }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    if (e !is NoActiveSessionException) {
                        Timber.e(e, "Failed to load podcast episode $itemId")
                    }
                    val cached = loadCachedTrack()
                    _uiState.update {
                        if (cached != null) {
                            it.copy(episode = cached, isLoading = false, errorRes = null)
                        } else {
                            it.copy(
                                isLoading = false,
                                errorRes = R.string.error_load_content_generic,
                            )
                        }
                    }
                }
        }
    }

    fun togglePlayed() {
        val episode = uiState.value.episode ?: return
        val target = !episode.played
        val patch = UserDataPatch(played = target, playbackPositionTicks = 0L)
        val revert =
            UserDataPatch(
                played = episode.played,
                playbackPositionTicks = episode.playbackPositionTicks,
            )
        itemStore.putIfAbsent(listOf(episode))
        itemStore.applyPatch(episode.id, patch)
        viewModelScope.launch {
            try {
                val success =
                    if (target) userDataRepository.markWatched(episode.id)
                    else userDataRepository.markUnwatched(episode.id)
                if (success) {
                    mediaRepository.updateContinueListeningPodcast(
                        episode.copy(played = target, playbackPositionTicks = 0L),
                    )
                    mediaRefreshBus.emit(RefreshTrigger.USER_DATA_CHANGED)
                    if (target) {
                        playedItemEventBus.emitPlayed(episode.id)
                    }
                } else {
                    itemStore.applyPatch(episode.id, revert)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle played state for podcast episode ${episode.id}")
                itemStore.applyPatch(episode.id, revert)
            }
        }
    }

    fun toggleFavorite() {
        val episode = uiState.value.episode ?: return
        val target = !episode.favorite
        itemStore.putIfAbsent(listOf(episode))
        itemStore.applyPatch(episode.id, UserDataPatch(favorite = target))
        viewModelScope.launch {
            try {
                musicRepository.setFavorite(episode.id, target)
                appDataRepository.updateTrackFavoriteStatus(episode, target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle favorite state for podcast episode ${episode.id}")
                itemStore.applyPatch(episode.id, UserDataPatch(favorite = episode.favorite))
            }
        }
    }

    fun startDownload(volumeId: String? = null) {
        viewModelScope.launch {
            downloadRepository.startDownload(itemId, "", volumeId = volumeId).onFailure { e ->
                if (e is CancellationException) throw e
                Timber.w(e, "Failed to start podcast episode download for $itemId")
            }
        }
    }

    fun pauseDownload() {
        val info = downloadInfo.value ?: return
        viewModelScope.launch { downloadRepository.pauseDownload(info.id) }
    }

    fun resumeDownload() {
        val info = downloadInfo.value ?: return
        viewModelScope.launch { downloadRepository.resumeDownload(info.id) }
    }

    fun cancelOrDeleteDownload() {
        val info = downloadInfo.value ?: return
        viewModelScope.launch {
            if (info.status == DownloadStatus.COMPLETED) {
                downloadRepository.deleteDownload(info.id)
            } else {
                downloadRepository.cancelDownload(info.id)
            }
        }
    }

    fun downloadConfirm(title: String): DownloadConfirm? =
        downloadInfo.value?.singleDownloadConfirm(title)
}

fun AfinityTrack.resumePositionMs(): Long =
    if (!played && playbackPositionTicks > 0L) {
        (playbackPositionTicks / TICKS_PER_MS).coerceAtLeast(0L)
    } else {
        0L
    }
