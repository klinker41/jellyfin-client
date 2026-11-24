package com.makd.afinity.data.autodownload

import com.makd.afinity.data.audiobooks.AUDIOBOOK_DOWNLOAD_TYPE
import com.makd.afinity.data.database.entities.DownloadDto
import com.makd.afinity.data.manager.MediaChangeManager
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.media.ItemFilterCriteria
import com.makd.afinity.data.podcasts.isPodcastLibrary
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.storage.VolumeUnavailableException
import com.makd.afinity.data.store.ItemStore
import com.makd.afinity.di.ApplicationScope
import com.makd.afinity.player.audiobookshelf.AudiobookSource
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlaybackManager
import com.makd.afinity.player.music.MusicPlaybackManager
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ItemFields
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class AutoDownloadManager
@Inject
constructor(
    private val sessionManager: SessionManager,
    private val mediaRepository: MediaRepository,
    private val downloadRepository: DownloadRepository,
    private val databaseRepository: DatabaseRepository,
    private val preferencesRepository: PreferencesRepository,
    private val autoDownloadPreferences: AutoDownloadPreferences,
    private val mediaChangeManager: MediaChangeManager,
    private val playedItemEventBus: PlayedItemEventBus,
    private val itemStore: ItemStore,
    private val scheduler: AutoDownloadScheduler,
    private val musicPlaybackManager: MusicPlaybackManager,
    private val audiobookshelfPlaybackManager: AudiobookshelfPlaybackManager,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val syncMutex = Mutex()
    private val cleanupMutex = Mutex()
    private val started = AtomicBoolean(false)
    private val recentlyPlayedKeys = ConcurrentHashMap.newKeySet<ScopedItemKey>()
    private val clearedDuringSync = ConcurrentHashMap.newKeySet<ScopedItemKey>()

    @Volatile private var lastAutoSyncAt = 0L

    fun initMonitoring() {
        if (!started.compareAndSet(false, true)) return

        scope.launch(Dispatchers.IO) {
            playedItemEventBus.playedItems.collect { itemId -> onItemMarkedPlayed(itemId) }
        }

        scope.launch(Dispatchers.IO) {
            mediaChangeManager.mediaChanges.collect { event ->
                when {
                    isMarkedPlayedEvent(event) -> {
                        if (!isItemActiveInPlayback(event.itemId)) {
                            onItemMarkedPlayed(event.itemId)
                        }
                    }
                    isMarkedUnplayedEvent(event) -> {
                        val session = sessionManager.currentSession.value ?: return@collect
                        recentlyPlayedKeys.remove(
                            ScopedItemKey(session.serverId, session.userId, event.itemId),
                        )
                    }
                }
            }
        }

        scope.launch(Dispatchers.IO) {
            var lastSessionKey: Pair<String, UUID>? = null
            combine(
                    sessionManager.currentSession,
                    sessionManager.isServerReachable,
                    preferencesRepository.getOfflineModeFlow(),
                ) { session, reachable, offline ->
                    Triple(session, reachable, offline)
                }
                .flatMapLatest { (session, reachable, offline) ->
                    if (session == null) {
                        flowOf(null)
                    } else {
                        autoDownloadPreferences
                            .getEnabledLibraryIdsFlow(session.serverId, session.userId)
                            .map { enabledIds ->
                                AutoSyncTrigger(session, reachable, offline, enabledIds)
                            }
                    }
                }
                .collect { trigger ->
                    if (trigger == null) {
                        lastSessionKey = null
                        lastAutoSyncAt = 0L
                        return@collect
                    }
                    val sessionKey = trigger.session.serverId to trigger.session.userId
                    if (sessionKey != lastSessionKey) {
                        lastSessionKey = sessionKey
                        lastAutoSyncAt = 0L
                    }
                    if (trigger.enabledIds.isEmpty() || trigger.session.canDownload == false) {
                        scheduler.cancelAll()
                    } else {
                        val wifiOnly = preferencesRepository.getDownloadOverWifiOnly()
                        scheduler.ensurePeriodicSync(wifiOnly)
                        val now = System.currentTimeMillis()
                        if (
                            trigger.reachable &&
                                !trigger.offline &&
                                now - lastAutoSyncAt >= FOREGROUND_SYNC_THROTTLE_MS
                        ) {
                            lastAutoSyncAt = now
                            scheduler.triggerSyncNow(wifiOnly)
                        }
                    }
                }
        }

        scope.launch(Dispatchers.IO) {
            preferencesRepository
                .getDownloadWifiOnlyFlow()
                .distinctUntilChanged()
                .drop(1)
                .collect { wifiOnly ->
                    val session = sessionManager.currentSession.value ?: return@collect
                    if (
                        session.canDownload != false &&
                            autoDownloadPreferences
                                .getEnabledLibraryIds(session.serverId, session.userId)
                                .isNotEmpty()
                    ) {
                        scheduler.ensurePeriodicSync(wifiOnly, updateConstraints = true)
                    }
                }
        }
    }

    suspend fun setLibraryEnabled(libraryId: UUID, enabled: Boolean) {
        val session = sessionManager.currentSession.value ?: return
        val offline = preferencesRepository.getOfflineMode()
        if (enabled && !offline) {
            lastAutoSyncAt = System.currentTimeMillis()
        }
        autoDownloadPreferences.setLibraryEnabled(
            session.serverId,
            session.userId,
            libraryId,
            enabled,
        )
        val remaining =
            autoDownloadPreferences.getEnabledLibraryIds(session.serverId, session.userId)
        if (remaining.isEmpty() || session.canDownload == false) {
            scheduler.cancelAll()
        } else {
            val wifiOnly = preferencesRepository.getDownloadOverWifiOnly()
            scheduler.ensurePeriodicSync(wifiOnly)
            if (enabled) {
                if (!offline) {
                    scope.launch(Dispatchers.IO) { syncEnabledLibraries() }
                } else {
                    scheduler.triggerSyncNow(wifiOnly)
                }
            }
        }
    }

    suspend fun syncEnabledLibraries(): Boolean = syncMutex.withLock {
        _isSyncing.value = true
        clearedDuringSync.clear()
        try {
            if (preferencesRepository.getOfflineMode()) {
                Timber.d("Auto-download sync skipped: offline mode enabled")
                return@withLock true
            }

            val session = sessionManager.currentSession.value ?: return@withLock false
            if (session.canDownload == false) {
                Timber.d("Auto-download sync skipped: account cannot download")
                return@withLock true
            }

            val enabledIds =
                autoDownloadPreferences.getEnabledLibraryIds(session.serverId, session.userId)
            if (enabledIds.isEmpty()) {
                scheduler.cancelAll()
                return@withLock true
            }

            val allLibraries =
                mediaRepository.libraries.first().ifEmpty { mediaRepository.getLibraries() }
            val enabledLibraries =
                allLibraries.autoDownloadableLibraries().filter { it.id in enabledIds }
            if (enabledLibraries.isEmpty()) {
                return@withLock true
            }

            val existingDownloads =
                databaseRepository
                    .getAllDownloadsFlowScoped(session.serverId, session.userId)
                    .first()
                    .associateBy { it.itemId }
                    .toMutableMap()

            var hadFailure = false
            for (library in enabledLibraries) {
                if (!isCurrentSession(session.serverId, session.userId)) {
                    Timber.d("Auto-download sync aborted: active session changed")
                    return@withLock false
                }
                val ok =
                    syncLibrary(
                        serverId = session.serverId,
                        userId = session.userId,
                        library = library,
                        existingDownloads = existingDownloads,
                    )
                if (!ok) hadFailure = true
            }
            !hadFailure
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Auto-download sync failed")
            false
        } finally {
            clearedDuringSync.clear()
            _isSyncing.value = false
        }
    }

    private fun isCurrentSession(serverId: String, userId: UUID): Boolean =
        sessionManager.currentSession.value?.let {
            it.serverId == serverId && it.userId == userId
        } == true

    private suspend fun syncLibrary(
        serverId: String,
        userId: UUID,
        library: AfinityCollection,
        existingDownloads: MutableMap<UUID, DownloadDto>,
    ): Boolean {
        val itemTypes = library.type.autoDownloadItemTypes()
        if (itemTypes.isEmpty()) return true

        val resumable =
            fetchLibraryItems(
                libraryId = library.id,
                itemTypes = itemTypes,
                sortBy = SortBy.DATE_PLAYED,
                criteria = ItemFilterCriteria(isResumable = true),
            )
        val unplayed =
            fetchLibraryItems(
                libraryId = library.id,
                itemTypes = itemTypes,
                sortBy = SortBy.DATE_ADDED,
                criteria = ItemFilterCriteria(isPlayed = false),
            )

        if (resumable == null && unplayed == null) {
            return false
        }

        val combined =
            (resumable.orEmpty() + unplayed.orEmpty())
                .distinctBy { it.id }
                .filter { dto ->
                    dto.canDownload != false &&
                        isEligibleForAutoDownload(
                            played = dto.userData?.played == true,
                            playbackPositionTicks = dto.userData?.playbackPositionTicks ?: 0L,
                        )
                }

        if (library.isPodcastLibrary()) {
            val discoveredAlbums = combined.mapNotNull { it.albumId }.toSet()
            if (discoveredAlbums.isNotEmpty()) {
                val cached = autoDownloadPreferences.getCachedPodcastAlbumIds(serverId, userId)
                autoDownloadPreferences.setCachedPodcastAlbumIds(
                    serverId,
                    userId,
                    cached + discoveredAlbums,
                )
            }
        }

        val previouslyManaged =
            autoDownloadPreferences.getManagedItemIds(serverId, userId, library.id)
        val nextManaged = HashSet<UUID>()
        val processedDuringLoop = HashSet<UUID>()
        val nowMs = System.currentTimeMillis()
        var volumeUnavailable = false

        for (dto in combined) {
            if (volumeUnavailable || !isCurrentSession(serverId, userId)) break
            val key = ScopedItemKey(serverId, userId, dto.id)
            if (key in clearedDuringSync) {
                processedDuringLoop.add(dto.id)
                continue
            }
            val existing = existingDownloads[dto.id]
            if (shouldSkipExistingDownload(existing?.status, existing?.updatedAt ?: 0L, nowMs)) {
                nextManaged.add(dto.id)
                processedDuringLoop.add(dto.id)
                continue
            }

            downloadRepository
                .startDownload(dto.id, "")
                .onSuccess {
                    nextManaged.add(dto.id)
                    processedDuringLoop.add(dto.id)
                    databaseRepository.getDownloadByItemIdScoped(dto.id, serverId, userId)?.let {
                        existingDownloads[dto.id] = it
                    }
                }
                .onFailure { error ->
                    if (error is VolumeUnavailableException) {
                        Timber.w(
                            "Auto-download paused for ${library.name}: storage volume unavailable",
                        )
                        volumeUnavailable = true
                    } else {
                        Timber.w(
                            error,
                            "Auto-download failed to queue ${dto.name} (${dto.id})",
                        )
                        if (existing != null) {
                            nextManaged.add(dto.id)
                        }
                        processedDuringLoop.add(dto.id)
                    }
                }
        }

        if (!isCurrentSession(serverId, userId)) {
            return false
        }

        val bothQueriesSucceeded = resumable != null && unplayed != null
        if (bothQueriesSucceeded && !volumeUnavailable) {
            reconcileRemovedItems(
                serverId = serverId,
                userId = userId,
                previouslyManaged = previouslyManaged,
                eligibleIds = combined.mapTo(HashSet()) { it.id },
                existingDownloads = existingDownloads,
                nextManaged = nextManaged,
            )
        } else {
            previouslyManaged
                .filter { it !in processedDuringLoop && it in existingDownloads }
                .forEach { nextManaged.add(it) }
        }

        nextManaged.removeAll { ScopedItemKey(serverId, userId, it) in clearedDuringSync }
        val stillEnabled =
            library.id in autoDownloadPreferences.getEnabledLibraryIds(serverId, userId)
        if (stillEnabled && isCurrentSession(serverId, userId)) {
            autoDownloadPreferences.setManagedItemIds(serverId, userId, library.id, nextManaged)
        }
        return bothQueriesSucceeded && !volumeUnavailable
    }

    private suspend fun reconcileRemovedItems(
        serverId: String,
        userId: UUID,
        previouslyManaged: Set<UUID>,
        eligibleIds: Set<UUID>,
        existingDownloads: Map<UUID, DownloadDto>,
        nextManaged: MutableSet<UUID>,
    ) {
        val missingFromEligible = previouslyManaged.filter {
            it !in eligibleIds && it in existingDownloads
        }
        if (missingFromEligible.isEmpty()) return

        val freshById =
            try {
                missingFromEligible
                    .chunked(USER_DATA_BATCH_SIZE)
                    .flatMap { chunk -> mediaRepository.getUserDataByIds(chunk) }
                    .associateBy { it.itemId }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Could not verify played state for previously managed items")
                missingFromEligible.forEach { nextManaged.add(it) }
                return
            }

        val pendingRemoval = HashSet<UUID>()
        missingFromEligible.forEach { id ->
            val fresh = freshById[id]
            if (
                fresh != null &&
                    fresh.played &&
                    fresh.playbackPositionTicks == 0L &&
                    !isItemActiveInPlayback(id)
            ) {
                pendingRemoval.add(id)
            } else {
                nextManaged.add(id)
            }
        }

        val processedAudiobookGroups = HashSet<String>()
        for (id in pendingRemoval) {
            val download = existingDownloads[id] ?: continue
            if (
                download.itemType == AUDIOBOOK_DOWNLOAD_TYPE && !download.seriesId.isNullOrBlank()
            ) {
                if (!processedAudiobookGroups.add(download.seriesId)) continue
                val siblings =
                    existingDownloads.values.filter {
                        it.itemType == AUDIOBOOK_DOWNLOAD_TYPE && it.seriesId == download.seriesId
                    }
                val allSiblingsPlayed =
                    siblings.none { isItemActiveInPlayback(it.itemId) } &&
                        siblings.all { sibling ->
                            if (sibling.itemId in eligibleIds) return@all false
                            if (sibling.itemId in pendingRemoval) return@all true
                            isKnownPlayedLocally(serverId, userId, sibling.itemId)
                        }
                if (allSiblingsPlayed) {
                    siblings.forEach { sibling ->
                        val removed = removeDownloadIfSafe(sibling)
                        if (!removed && sibling.itemId in previouslyManaged) {
                            nextManaged.add(sibling.itemId)
                        }
                    }
                } else {
                    siblings
                        .filter { it.itemId in previouslyManaged }
                        .forEach { sibling -> nextManaged.add(sibling.itemId) }
                }
            } else {
                val removed = removeDownloadIfSafe(download)
                if (!removed) nextManaged.add(id)
            }
        }
    }

    private suspend fun fetchLibraryItems(
        libraryId: UUID,
        itemTypes: List<String>,
        sortBy: SortBy,
        criteria: ItemFilterCriteria,
    ): List<BaseItemDto>? {
        val collected = ArrayList<BaseItemDto>()
        var startIndex = 0
        while (collected.size < MAX_AUTO_DOWNLOAD_ITEMS_PER_LIBRARY) {
            val remaining = MAX_AUTO_DOWNLOAD_ITEMS_PER_LIBRARY - collected.size
            val pageSize = minOf(AUTO_DOWNLOAD_PAGE_SIZE, remaining)
            val result =
                mediaRepository.getItemsResult(
                    parentId = libraryId,
                    sortBy = sortBy,
                    sortDescending = true,
                    limit = pageSize,
                    startIndex = startIndex,
                    includeItemTypes = itemTypes,
                    fields = listOf(ItemFields.CAN_DOWNLOAD),
                    recursive = true,
                    criteria = criteria,
                    enableTotalRecordCount = false,
                    enableImages = false,
                    enableUserData = true,
                )
            val page = result.getOrElse { error ->
                if (error is CancellationException) throw error
                Timber.w(error, "Auto-download query failed for library $libraryId")
                return null
            }
            if (page.items.isEmpty()) break
            collected.addAll(page.items)
            if (page.items.size < pageSize) break
            startIndex += page.items.size
        }
        return collected
    }

    suspend fun onItemMarkedPlayed(itemId: UUID) {
        cleanupMutex.withLock {
            val session = sessionManager.currentSession.value ?: return

            val isManaged =
                autoDownloadPreferences.isItemManagedInEnabledLibraries(
                    session.serverId,
                    session.userId,
                    itemId,
                )
            if (!isManaged) return

            val scopedKey = ScopedItemKey(session.serverId, session.userId, itemId)
            recentlyPlayedKeys.add(scopedKey)

            val download =
                databaseRepository.getDownloadByItemIdScoped(
                    itemId,
                    session.serverId,
                    session.userId,
                )
            if (download == null) {
                clearedDuringSync.add(scopedKey)
                autoDownloadPreferences.removeManagedItem(session.serverId, session.userId, itemId)
                return
            }

            if (
                download.itemType == AUDIOBOOK_DOWNLOAD_TYPE && !download.seriesId.isNullOrBlank()
            ) {
                val siblings =
                    databaseRepository
                        .getAllDownloadsFlowScoped(session.serverId, session.userId)
                        .first()
                        .filter {
                            it.itemType == AUDIOBOOK_DOWNLOAD_TYPE &&
                                it.seriesId == download.seriesId
                        }
                if (siblings.size > 1) {
                    if (
                        !areAllAudiobookPartsPlayed(
                            siblings,
                            itemId,
                            session.serverId,
                            session.userId,
                        )
                    ) {
                        return
                    }
                    siblings.forEach { sibling ->
                        val siblingKey =
                            ScopedItemKey(session.serverId, session.userId, sibling.itemId)
                        if (removeDownloadIfSafe(sibling)) {
                            clearedDuringSync.add(siblingKey)
                            autoDownloadPreferences.removeManagedItem(
                                session.serverId,
                                session.userId,
                                sibling.itemId,
                            )
                            recentlyPlayedKeys.remove(siblingKey)
                        }
                    }
                    return
                }
            }

            if (removeDownloadIfSafe(download)) {
                clearedDuringSync.add(scopedKey)
                autoDownloadPreferences.removeManagedItem(session.serverId, session.userId, itemId)
                recentlyPlayedKeys.remove(scopedKey)
            }
        }
    }

    private suspend fun areAllAudiobookPartsPlayed(
        siblings: List<DownloadDto>,
        triggerItemId: UUID,
        serverId: String,
        userId: UUID,
    ): Boolean {
        val uncertainIds = ArrayList<UUID>()
        for (sibling in siblings) {
            if (
                sibling.itemId == triggerItemId ||
                    isKnownPlayedLocally(serverId, userId, sibling.itemId)
            ) {
                continue
            }
            uncertainIds.add(sibling.itemId)
        }
        if (uncertainIds.isEmpty()) return true
        if (preferencesRepository.getOfflineMode()) return false

        val freshById =
            try {
                mediaRepository.getUserDataByIds(uncertainIds).associateBy { it.itemId }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to verify sibling audiobook part played state")
                return false
            }
        return uncertainIds.all { freshById[it]?.played == true }
    }

    private fun isItemActiveInPlayback(itemId: UUID): Boolean {
        if (musicPlaybackManager.state.value.currentTrack?.id == itemId) return true
        val absState = audiobookshelfPlaybackManager.playbackState.value
        if (absState.source == AudiobookSource.JELLYFIN && absState.sessionId != null) {
            if (itemId in absState.jellyfinPartIds || absState.itemId == itemId.toString()) {
                return true
            }
        }
        return false
    }

    private suspend fun isKnownPlayedLocally(
        serverId: String,
        userId: UUID,
        itemId: UUID,
    ): Boolean {
        if (ScopedItemKey(serverId, userId, itemId) in recentlyPlayedKeys) return true
        if (itemStore.get(itemId)?.played == true) return true
        if (databaseRepository.getUserData(userId, itemId)?.played == true) return true
        if (databaseRepository.getAudiobookPart(itemId, userId)?.played == true) return true
        return false
    }

    private suspend fun removeDownloadIfSafe(download: DownloadDto): Boolean {
        val result =
            if (download.status == DownloadStatus.COMPLETED) {
                downloadRepository.deleteDownload(download.id)
            } else {
                downloadRepository.cancelDownload(download.id)
            }
        return result
            .onFailure { error ->
                if (error is VolumeUnavailableException) {
                    Timber.w(
                        "Cannot auto-remove ${download.itemName}: volume ${error.volumeId} unavailable",
                    )
                } else {
                    Timber.w(error, "Failed to auto-remove download ${download.itemName}")
                }
            }
            .isSuccess
    }

    private data class ScopedItemKey(
        val serverId: String,
        val userId: UUID,
        val itemId: UUID,
    )

    private data class AutoSyncTrigger(
        val session: com.makd.afinity.data.manager.Session,
        val reachable: Boolean,
        val offline: Boolean,
        val enabledIds: Set<UUID>,
    )

    private companion object {
        const val FOREGROUND_SYNC_THROTTLE_MS = 15L * 60 * 1000
        const val USER_DATA_BATCH_SIZE = 100
    }
}
