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

data class PodcastProgressOutcome(
    val played: Boolean,
    val playbackPositionTicks: Long,
)

fun resolvePodcastProgress(
    positionMs: Long,
    durationMs: Long,
    isEnded: Boolean,
    wasPlayed: Boolean,
): PodcastProgressOutcome? {
    val safePos = positionMs.coerceAtLeast(0L)
    val completed =
        isEnded ||
            (durationMs > 0L &&
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
    private var cachedPodcastAlbumIds: Set<UUID>? = null

    private suspend fun podcastAlbumIds(): Set<UUID> {
        val session = sessionManager.currentSession.value ?: return emptySet()
        val sessionKey = "${session.serverId}_${session.userId}"
        return cacheMutex.withLock {
            val cached = cachedPodcastAlbumIds
            if (cachedSessionKey == sessionKey && cached != null) {
                return@withLock cached
            }
            val persisted =
                autoDownloadPreferences.getCachedPodcastAlbumIds(session.serverId, session.userId)
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
                cachedPodcastAlbumIds = emptySet()
                autoDownloadPreferences.setCachedPodcastAlbumIds(
                    session.serverId,
                    session.userId,
                    emptySet(),
                )
                return@withLock emptySet()
            }
            val ids = HashSet<UUID>()
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
                            ids.add(dto.id)
                        }
                    }
                }
            }
            if (allSucceeded) {
                cachedSessionKey = sessionKey
                cachedPodcastAlbumIds = ids
                autoDownloadPreferences.setCachedPodcastAlbumIds(
                    session.serverId,
                    session.userId,
                    ids,
                )
                ids
            } else {
                persisted + ids
            }
        }
    }

    suspend fun isPodcastTrack(track: AfinityTrack): Boolean {
        if (track.playbackPositionTicks > 0L) return true
        val albumId = track.albumId ?: return false
        return albumId in podcastAlbumIds()
    }

    suspend fun syncProgress(
        track: AfinityTrack,
        positionMs: Long,
        isEnded: Boolean,
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
