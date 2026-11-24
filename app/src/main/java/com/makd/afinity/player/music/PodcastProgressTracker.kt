package com.makd.afinity.player.music

import com.makd.afinity.data.autodownload.AutoDownloadPreferences
import com.makd.afinity.data.autodownload.PlayedItemEventBus
import com.makd.afinity.data.manager.MediaRefreshBus
import com.makd.afinity.data.manager.RefreshTrigger
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.media.UserDataPatch
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.models.user.AfinityUserDataDto
import com.makd.afinity.data.podcasts.podcastLibraries
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.store.ItemStore
import com.makd.afinity.data.sync.UserDataSyncScheduler
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.api.operations.UserDataApi
import org.jellyfin.sdk.model.DateTime
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UpdateUserItemDataDto
import timber.log.Timber

private const val TICKS_PER_MS = 10_000L
private const val MIN_RESUME_POSITION_MS = 10_000L
private const val COMPLETION_REMAINING_MS = 30_000L
private const val COMPLETION_FRACTION = 0.95
private const val PODCAST_ALBUMS_LIMIT = 500
private const val PODCAST_PROGRESS_SYNC_TICKS = 3

sealed interface PodcastTrackInfo {
    data object NotPodcast : PodcastTrackInfo

    data class Podcast(val libraryId: UUID?) : PodcastTrackInfo
}

data class PodcastProgressOutcome(
    val played: Boolean,
    val playbackPositionTicks: Long,
)

fun resolvePodcastProgress(
    positionMs: Long,
    durationMs: Long,
    isEnded: Boolean,
    wasPlayed: Boolean,
    allowCompletion: Boolean = true,
): PodcastProgressOutcome? {
    val safePos = positionMs.coerceAtLeast(0L)
    val completed =
        isEnded ||
            (allowCompletion &&
                durationMs > 0L &&
                (safePos >= durationMs - COMPLETION_REMAINING_MS ||
                    safePos.toDouble() / durationMs >= COMPLETION_FRACTION))
    return when {
        completed -> PodcastProgressOutcome(played = true, playbackPositionTicks = 0L)
        safePos >= MIN_RESUME_POSITION_MS ->
            PodcastProgressOutcome(played = false, playbackPositionTicks = safePos * TICKS_PER_MS)
        !wasPlayed -> PodcastProgressOutcome(played = false, playbackPositionTicks = 0L)
        else -> null
    }
}

@Singleton
class PodcastProgressTracker
@Inject
constructor(
    private val sessionManager: SessionManager,
    private val mediaRepository: MediaRepository,
    private val itemStore: ItemStore,
    private val mediaRefreshBus: MediaRefreshBus,
    private val databaseRepository: DatabaseRepository,
    private val preferencesRepository: PreferencesRepository,
    private val userDataSyncScheduler: UserDataSyncScheduler,
    private val autoDownloadPreferences: AutoDownloadPreferences,
    private val playedItemEventBus: PlayedItemEventBus,
) {
    private val cacheMutex = Mutex()
    private var cachedSessionKey: String? = null
    private var cachedPodcastAlbumLibraries: Map<UUID, UUID>? = null

    private suspend fun podcastAlbumLibraries(): Map<UUID, UUID> {
        val session = sessionManager.currentSession.value ?: return emptyMap()
        val sessionKey = "${session.serverId}_${session.userId}"
        return cacheMutex.withLock {
            val cached = cachedPodcastAlbumLibraries
            if (cachedSessionKey == sessionKey && cached != null) {
                return@withLock cached
            }
            val persisted =
                autoDownloadPreferences.getCachedPodcastAlbumLibraries(
                    session.serverId,
                    session.userId,
                )
            if (preferencesRepository.getOfflineMode()) {
                return@withLock persisted
            }
            val allLibraries = mediaRepository.libraries.first()
            if (allLibraries.isEmpty()) {
                return@withLock persisted
            }
            val libs = allLibraries.podcastLibraries()
            if (libs.isEmpty()) {
                cachedSessionKey = sessionKey
                cachedPodcastAlbumLibraries = emptyMap()
                autoDownloadPreferences.setCachedPodcastAlbumLibraries(
                    session.serverId,
                    session.userId,
                    emptyMap(),
                )
                autoDownloadPreferences.setCachedPodcastAlbumIds(
                    session.serverId,
                    session.userId,
                    emptySet(),
                )
                return@withLock emptyMap()
            }
            val albumToLib = HashMap<UUID, UUID>()
            var allSucceeded = true
            for (lib in libs) {
                val result =
                    mediaRepository.getItemsResult(
                        parentId = lib.id,
                        includeItemTypes = listOf("MUSIC_ALBUM"),
                        limit = PODCAST_ALBUMS_LIMIT,
                        fields = emptyList(),
                        recursive = true,
                        enableImages = false,
                        enableUserData = false,
                    )
                if (result.isFailure) {
                    allSucceeded = false
                } else {
                    for (dto in result.getOrNull()?.items.orEmpty()) {
                        if (dto.type == BaseItemKind.MUSIC_ALBUM) {
                            albumToLib[dto.id] = lib.id
                        }
                    }
                }
            }
            if (allSucceeded) {
                cachedSessionKey = sessionKey
                cachedPodcastAlbumLibraries = albumToLib
                autoDownloadPreferences.setCachedPodcastAlbumLibraries(
                    session.serverId,
                    session.userId,
                    albumToLib,
                )
                autoDownloadPreferences.setCachedPodcastAlbumIds(
                    session.serverId,
                    session.userId,
                    albumToLib.keys,
                )
                albumToLib
            } else {
                persisted + albumToLib
            }
        }
    }

    private suspend fun hasFreshLibraryCache(): Boolean {
        val session = sessionManager.currentSession.value ?: return false
        val sessionKey = "${session.serverId}_${session.userId}"
        return cacheMutex.withLock {
            cachedSessionKey == sessionKey && cachedPodcastAlbumLibraries != null
        }
    }

    private suspend fun isLegacyCachedPodcastAlbum(albumId: UUID): Boolean {
        if (hasFreshLibraryCache()) return false
        val session = sessionManager.currentSession.value ?: return false
        return albumId in
            autoDownloadPreferences.getCachedPodcastAlbumIds(
                session.serverId,
                session.userId,
            )
    }

    suspend fun isPodcastAlbum(albumId: UUID): Boolean =
        try {
            albumId in podcastAlbumLibraries() || isLegacyCachedPodcastAlbum(albumId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to resolve whether album $albumId is a podcast")
            false
        }

    suspend fun resolvePodcastTrack(track: AfinityTrack): PodcastTrackInfo =
        try {
            val albumId = track.albumId
            if (albumId != null) {
                val libraryId = podcastAlbumLibraries()[albumId]
                if (libraryId != null) return PodcastTrackInfo.Podcast(libraryId)
            }
            val legacyCached = albumId != null && isLegacyCachedPodcastAlbum(albumId)
            if (track.playbackPositionTicks > 0L || legacyCached) {
                val singleLibId =
                    mediaRepository.libraries.first().podcastLibraries().singleOrNull()?.id
                PodcastTrackInfo.Podcast(singleLibId)
            } else {
                PodcastTrackInfo.NotPodcast
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to resolve whether track ${track.id} is a podcast")
            PodcastTrackInfo.NotPodcast
        }

    suspend fun isPodcastTrack(track: AfinityTrack): Boolean =
        resolvePodcastTrack(track) is PodcastTrackInfo.Podcast

    suspend fun syncPeriodicIfDue(
        track: AfinityTrack,
        positionMs: Long,
        isPaused: Boolean,
        tickCount: Int,
    ): AfinityTrack? {
        if (
            isPaused ||
                tickCount % PODCAST_PROGRESS_SYNC_TICKS != 0 ||
                positionMs < MIN_RESUME_POSITION_MS
        ) {
            return null
        }
        return syncProgress(track, positionMs, isEnded = false, allowCompletion = false)
    }

    suspend fun syncProgress(
        track: AfinityTrack,
        positionMs: Long,
        isEnded: Boolean,
        allowCompletion: Boolean = true,
    ): AfinityTrack? {
        try {
            if (!isPodcastTrack(track)) return null
            val durationMs = track.runtimeTicks / TICKS_PER_MS
            val outcome =
                resolvePodcastProgress(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isEnded = isEnded,
                    wasPlayed = track.played,
                    allowCompletion = allowCompletion,
                ) ?: return null

            val session = sessionManager.currentSession.value ?: return null
            val apiClient =
                if (preferencesRepository.getOfflineMode()) null
                else sessionManager.getCurrentApiClient()

            val syncedOnline =
                if (apiClient != null) {
                    try {
                        UserDataApi(apiClient)
                            .updateItemUserData(
                                itemId = track.id,
                                userId = session.userId,
                                data =
                                    UpdateUserItemDataDto(
                                        played = outcome.played,
                                        playbackPositionTicks = outcome.playbackPositionTicks,
                                        lastPlayedDate = DateTime.now(),
                                    ),
                            )
                        true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to sync podcast progress online for ${track.id}")
                        false
                    }
                } else {
                    false
                }

            if (!syncedOnline) {
                val existing = databaseRepository.getUserData(session.userId, track.id)
                databaseRepository.insertUserData(
                    AfinityUserDataDto(
                        userId = session.userId,
                        itemId = track.id,
                        serverId = session.serverId,
                        played = outcome.played,
                        favorite = existing?.favorite ?: track.favorite,
                        likes = existing?.likes ?: false,
                        playbackPositionTicks = outcome.playbackPositionTicks,
                        toBeSynced = true,
                        lastPlayedAt = System.currentTimeMillis(),
                    ),
                )
                userDataSyncScheduler.triggerSync()
            }

            itemStore.applyPatch(
                track.id,
                UserDataPatch(
                    played = outcome.played,
                    playbackPositionTicks = outcome.playbackPositionTicks,
                ),
            )
            val updatedTrack =
                track.copy(
                    played = outcome.played,
                    playbackPositionTicks = outcome.playbackPositionTicks,
                    lastPlayedDate = LocalDateTime.now(),
                )
            mediaRepository.updateContinueListeningPodcast(updatedTrack)
            if (outcome.played != track.played) {
                mediaRefreshBus.emit(RefreshTrigger.USER_DATA_CHANGED)
            }
            if (outcome.played) {
                playedItemEventBus.emitPlayed(track.id)
            }
            return updatedTrack
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to sync podcast progress for ${track.id}")
            return null
        }
    }
}
