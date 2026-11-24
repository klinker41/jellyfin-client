package com.makd.afinity.data.autodownload

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.makd.afinity.data.audiobooks.AUDIOBOOK_DOWNLOAD_TYPE
import com.makd.afinity.data.database.entities.DownloadDto
import com.makd.afinity.data.manager.MediaChangeEvent
import com.makd.afinity.data.manager.MediaChangeManager
import com.makd.afinity.data.manager.Session
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.models.media.ItemFilterCriteria
import com.makd.afinity.data.models.music.MusicPlaybackState
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.storage.VolumeUnavailableException
import com.makd.afinity.data.store.ItemStore
import com.makd.afinity.player.audiobookshelf.AudiobookSource
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlaybackManager
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlaybackState
import com.makd.afinity.player.music.MusicPlaybackManager
import com.makd.afinity.testutil.TestMediaFixtures.mockUserItemDataDto
import com.makd.afinity.testutil.TestMediaFixtures.testCollection
import com.makd.afinity.testutil.TestMediaFixtures.testTrack
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class AutoDownloadManagerTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private class Harness(
        tempRoot: File,
        scope: CoroutineScope,
        canDownload: Boolean = true,
        offlineMode: Boolean = false,
    ) {
        val serverId = "srv-1"
        val userId = testUuid(1)
        val audiobooksLibId = testUuid(10)
        val podcastsLibId = testUuid(11)

        val sessionFlow =
            MutableStateFlow<Session?>(
                Session(
                    serverId = serverId,
                    userId = userId,
                    serverUrl = "https://jf.example.com",
                    canDownload = canDownload,
                ),
            )
        val sessionManager: SessionManager =
            mockk(relaxed = true) {
                every { currentSession } returns sessionFlow
                every { isServerReachable } returns MutableStateFlow(true)
            }

        val librariesFlow =
            MutableStateFlow(
                listOf(
                    testCollection(
                        id = audiobooksLibId,
                        name = "Audiobooks",
                        type = CollectionType.Books,
                    ),
                    testCollection(
                        id = podcastsLibId,
                        name = "Podcasts",
                        type = CollectionType.Music,
                    ),
                ),
            )
        val mediaRepository: MediaRepository =
            mockk(relaxed = true) { every { libraries } returns librariesFlow }
        val downloadRepository: DownloadRepository =
            mockk(relaxed = true) {
                coEvery { startDownload(any(), any(), any(), any(), any()) } answers
                    {
                        Result.success(UUID.randomUUID())
                    }
                coEvery { deleteDownload(any()) } returns Result.success(Unit)
                coEvery { cancelDownload(any()) } returns Result.success(Unit)
            }
        val downloadsFlow = MutableStateFlow<List<DownloadDto>>(emptyList())
        val databaseRepository: DatabaseRepository =
            mockk(relaxed = true) {
                every { getAllDownloadsFlowScoped(serverId, userId) } returns downloadsFlow
            }
        val preferencesRepository: PreferencesRepository =
            mockk(relaxed = true) {
                coEvery { getOfflineMode() } returns offlineMode
                every { getOfflineModeFlow() } returns flowOf(offlineMode)
                coEvery { getDownloadOverWifiOnly() } returns true
                every { getDownloadWifiOnlyFlow() } returns flowOf(true)
            }
        val dataStore =
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { File(tempRoot, "prefs_${System.nanoTime()}.preferences_pb") },
            )
        val autoDownloadPreferences = AutoDownloadPreferences(dataStore)
        val mediaChangesFlow = MutableSharedFlow<MediaChangeEvent>()
        val mediaChangeManager: MediaChangeManager =
            mockk(relaxed = true) { every { mediaChanges } returns mediaChangesFlow }
        val playedItemEventBus = PlayedItemEventBus()
        val itemStore = ItemStore()
        val scheduler: AutoDownloadScheduler = mockk(relaxed = true)
        val musicPlaybackState = MutableStateFlow(MusicPlaybackState())
        val musicPlaybackManager: MusicPlaybackManager =
            mockk(relaxed = true) { every { state } returns musicPlaybackState }
        val absPlaybackState = MutableStateFlow(AudiobookshelfPlaybackState())
        val audiobookshelfPlaybackManager: AudiobookshelfPlaybackManager =
            mockk(relaxed = true) { every { playbackState } returns absPlaybackState }

        val manager =
            AutoDownloadManager(
                sessionManager = sessionManager,
                mediaRepository = mediaRepository,
                downloadRepository = downloadRepository,
                databaseRepository = databaseRepository,
                preferencesRepository = preferencesRepository,
                autoDownloadPreferences = autoDownloadPreferences,
                mediaChangeManager = mediaChangeManager,
                playedItemEventBus = playedItemEventBus,
                itemStore = itemStore,
                scheduler = scheduler,
                musicPlaybackManager = musicPlaybackManager,
                audiobookshelfPlaybackManager = audiobookshelfPlaybackManager,
                scope = scope,
            )
    }

    private fun downloadDto(
        id: UUID,
        itemId: UUID,
        itemType: String = "Audio",
        status: DownloadStatus = DownloadStatus.COMPLETED,
        updatedAt: Long = System.currentTimeMillis(),
        seriesId: String? = null,
    ) =
        DownloadDto(
            id = id,
            itemId = itemId,
            itemName = "Item $itemId",
            itemType = itemType,
            sourceId = "src",
            sourceName = "mp3",
            status = status,
            progress = if (status == DownloadStatus.COMPLETED) 1f else 0f,
            bytesDownloaded = 100L,
            totalBytes = 100L,
            filePath = "/tmp/$itemId",
            error = null,
            createdAt = 1L,
            updatedAt = updatedAt,
            serverId = "srv-1",
            userId = testUuid(1),
            imageUrl = null,
            seriesImageUrl = null,
            seriesName = null,
            seasonNumber = null,
            episodeNumber = null,
            releaseYear = null,
            runtimeTicks = null,
            seriesId = seriesId,
        )

    private fun stubLibraryQueries(
        mediaRepository: MediaRepository,
        libraryId: UUID,
        resumable: List<BaseItemDto>,
        unplayed: List<BaseItemDto>,
    ) {
        coEvery {
            mediaRepository.getItemsResult(
                parentId = libraryId,
                collectionTypes = any(),
                sortBy = SortBy.DATE_PLAYED,
                sortDescending = true,
                limit = any(),
                startIndex = 0,
                searchTerm = any(),
                includeItemTypes = any(),
                nameStartsWith = any(),
                fields = any(),
                imageTypes = any(),
                recursive = true,
                criteria = ItemFilterCriteria(isResumable = true),
                enableTotalRecordCount = false,
                enableImageTypes = any(),
                enableImages = false,
                enableUserData = true,
            )
        } returns Result.success(BaseItemDtoQueryResult(resumable, 0, resumable.size))

        coEvery {
            mediaRepository.getItemsResult(
                parentId = libraryId,
                collectionTypes = any(),
                sortBy = SortBy.DATE_ADDED,
                sortDescending = true,
                limit = any(),
                startIndex = 0,
                searchTerm = any(),
                includeItemTypes = any(),
                nameStartsWith = any(),
                fields = any(),
                imageTypes = any(),
                recursive = true,
                criteria = ItemFilterCriteria(isPlayed = false),
                enableTotalRecordCount = false,
                enableImageTypes = any(),
                enableImages = false,
                enableUserData = true,
            )
        } returns Result.success(BaseItemDtoQueryResult(unplayed, 0, unplayed.size))
    }

    @Test
    fun `syncEnabledLibraries queues resumable and unplayed items and respects skips and cooldowns`() =
        runTest {
            val h = Harness(tempFolder.root, backgroundScope)
            h.autoDownloadPreferences.setLibraryEnabled(
                h.serverId,
                h.userId,
                h.podcastsLibId,
                enabled = true,
            )

            val resumableEp = testUuid(50)
            val unplayedEp = testUuid(51)
            val alreadyCompletedEp = testUuid(52)
            val recentFailedEp = testUuid(53)
            val staleFailedEp = testUuid(54)
            val disallowedEp = testUuid(55)
            val podcastAlbumId = testUuid(60)

            h.downloadsFlow.value =
                listOf(
                    downloadDto(
                        id = testUuid(152),
                        itemId = alreadyCompletedEp,
                        status = DownloadStatus.COMPLETED,
                    ),
                    downloadDto(
                        id = testUuid(153),
                        itemId = recentFailedEp,
                        status = DownloadStatus.FAILED,
                        updatedAt = System.currentTimeMillis() - 60_000L,
                    ),
                    downloadDto(
                        id = testUuid(154),
                        itemId = staleFailedEp,
                        status = DownloadStatus.FAILED,
                        updatedAt = System.currentTimeMillis() - FAILED_RETRY_COOLDOWN_MS - 1_000L,
                    ),
                )

            val resumableDto =
                BaseItemDto(
                    id = resumableEp,
                    type = BaseItemKind.AUDIO,
                    albumId = podcastAlbumId,
                    canDownload = true,
                    userData =
                        mockUserItemDataDto(
                            played = false,
                            playbackPositionTicks = 10_000_000L,
                        ),
                )
            val unplayedDtos =
                listOf(
                    BaseItemDto(
                        id = unplayedEp,
                        type = BaseItemKind.AUDIO,
                        albumId = podcastAlbumId,
                        canDownload = true,
                        userData = mockUserItemDataDto(played = false, playbackPositionTicks = 0L),
                    ),
                    BaseItemDto(
                        id = alreadyCompletedEp,
                        type = BaseItemKind.AUDIO,
                        albumId = podcastAlbumId,
                        canDownload = true,
                        userData = mockUserItemDataDto(played = false, playbackPositionTicks = 0L),
                    ),
                    BaseItemDto(
                        id = recentFailedEp,
                        type = BaseItemKind.AUDIO,
                        albumId = podcastAlbumId,
                        canDownload = true,
                        userData = mockUserItemDataDto(played = false, playbackPositionTicks = 0L),
                    ),
                    BaseItemDto(
                        id = staleFailedEp,
                        type = BaseItemKind.AUDIO,
                        albumId = podcastAlbumId,
                        canDownload = true,
                        userData = mockUserItemDataDto(played = false, playbackPositionTicks = 0L),
                    ),
                    BaseItemDto(
                        id = disallowedEp,
                        type = BaseItemKind.AUDIO,
                        albumId = podcastAlbumId,
                        canDownload = false,
                        userData = mockUserItemDataDto(played = false, playbackPositionTicks = 0L),
                    ),
                )
            stubLibraryQueries(
                h.mediaRepository,
                h.podcastsLibId,
                listOf(resumableDto),
                unplayedDtos,
            )

            val ok = h.manager.syncEnabledLibraries()
            assertTrue(ok)

            coVerify(exactly = 1) { h.downloadRepository.startDownload(resumableEp, "") }
            coVerify(exactly = 1) { h.downloadRepository.startDownload(unplayedEp, "") }
            coVerify(exactly = 1) { h.downloadRepository.startDownload(staleFailedEp, "") }
            coVerify(exactly = 0) { h.downloadRepository.startDownload(alreadyCompletedEp, any()) }
            coVerify(exactly = 0) { h.downloadRepository.startDownload(recentFailedEp, any()) }
            coVerify(exactly = 0) { h.downloadRepository.startDownload(disallowedEp, any()) }

            assertEquals(
                setOf(podcastAlbumId),
                h.autoDownloadPreferences.getCachedPodcastAlbumIds(h.serverId, h.userId),
            )
            assertEquals(
                setOf(resumableEp, unplayedEp, alreadyCompletedEp, recentFailedEp, staleFailedEp),
                h.autoDownloadPreferences.getManagedItemIds(h.serverId, h.userId, h.podcastsLibId),
            )
        }

    @Test
    fun `syncEnabledLibraries removes items marked played on server and skips offline or forbidden`() =
        runTest {
            val h = Harness(tempFolder.root, backgroundScope)
            h.autoDownloadPreferences.setLibraryEnabled(
                h.serverId,
                h.userId,
                h.podcastsLibId,
                enabled = true,
            )
            val finishedId = testUuid(70)
            val finishedDlId = testUuid(170)
            h.autoDownloadPreferences.setManagedItemIds(
                h.serverId,
                h.userId,
                h.podcastsLibId,
                setOf(finishedId),
            )
            h.downloadsFlow.value =
                listOf(
                    downloadDto(
                        id = finishedDlId,
                        itemId = finishedId,
                        status = DownloadStatus.COMPLETED,
                    ),
                )
            stubLibraryQueries(h.mediaRepository, h.podcastsLibId, emptyList(), emptyList())
            val playedUserData =
                mockUserItemDataDto(played = true, playbackPositionTicks = 0L).also {
                    every { it.itemId } returns finishedId
                }
            coEvery { h.mediaRepository.getUserDataByIds(listOf(finishedId)) } returns
                listOf(playedUserData)

            assertTrue(h.manager.syncEnabledLibraries())
            coVerify(exactly = 1) { h.downloadRepository.deleteDownload(finishedDlId) }
            assertFalse(
                h.autoDownloadPreferences.isItemManagedInEnabledLibraries(
                    h.serverId,
                    h.userId,
                    finishedId,
                ),
            )

            // Offline mode skips without calling API
            val offlineH = Harness(tempFolder.root, backgroundScope, offlineMode = true)
            offlineH.autoDownloadPreferences.setLibraryEnabled(
                offlineH.serverId,
                offlineH.userId,
                offlineH.podcastsLibId,
                enabled = true,
            )
            assertTrue(offlineH.manager.syncEnabledLibraries())
            coVerify(exactly = 0) {
                offlineH.mediaRepository.getItemsResult(
                    parentId = any(),
                    collectionTypes = any(),
                    sortBy = any(),
                    sortDescending = any(),
                    limit = any(),
                    startIndex = any(),
                    searchTerm = any(),
                    includeItemTypes = any(),
                    nameStartsWith = any(),
                    fields = any(),
                    imageTypes = any(),
                    recursive = any(),
                    criteria = any(),
                    enableTotalRecordCount = any(),
                    enableImageTypes = any(),
                    enableImages = any(),
                    enableUserData = any(),
                )
            }

            // canDownload = false skips without calling API
            val restrictedH = Harness(tempFolder.root, backgroundScope, canDownload = false)
            restrictedH.autoDownloadPreferences.setLibraryEnabled(
                restrictedH.serverId,
                restrictedH.userId,
                restrictedH.podcastsLibId,
                enabled = true,
            )
            assertTrue(restrictedH.manager.syncEnabledLibraries())
            coVerify(exactly = 0) { restrictedH.downloadRepository.startDownload(any(), any()) }
        }

    @Test
    fun `onItemMarkedPlayed deletes managed items, ignores unmanaged, and handles multi-part audiobooks`() =
        runTest {
            val h = Harness(tempFolder.root, backgroundScope)
            h.autoDownloadPreferences.setLibraryEnabled(
                h.serverId,
                h.userId,
                h.podcastsLibId,
                enabled = true,
            )
            h.autoDownloadPreferences.setLibraryEnabled(
                h.serverId,
                h.userId,
                h.audiobooksLibId,
                enabled = true,
            )

            val podId = testUuid(80)
            val podDlId = testUuid(180)
            val unmanagedId = testUuid(81)
            val part1Id = testUuid(82)
            val part2Id = testUuid(83)
            val part1DlId = testUuid(182)
            val part2DlId = testUuid(183)

            h.autoDownloadPreferences.setManagedItemIds(
                h.serverId,
                h.userId,
                h.podcastsLibId,
                setOf(podId),
            )
            h.autoDownloadPreferences.setManagedItemIds(
                h.serverId,
                h.userId,
                h.audiobooksLibId,
                setOf(part1Id, part2Id),
            )

            val podDownload = downloadDto(podDlId, podId, itemType = "Audio")
            val part1Download =
                downloadDto(
                    part1DlId,
                    part1Id,
                    itemType = AUDIOBOOK_DOWNLOAD_TYPE,
                    seriesId = "book-1",
                )
            val part2Download =
                downloadDto(
                    part2DlId,
                    part2Id,
                    itemType = AUDIOBOOK_DOWNLOAD_TYPE,
                    seriesId = "book-1",
                )
            h.downloadsFlow.value = listOf(podDownload, part1Download, part2Download)

            coEvery {
                h.databaseRepository.getDownloadByItemIdScoped(podId, h.serverId, h.userId)
            } returns podDownload
            coEvery {
                h.databaseRepository.getDownloadByItemIdScoped(part1Id, h.serverId, h.userId)
            } returns part1Download
            coEvery {
                h.databaseRepository.getDownloadByItemIdScoped(part2Id, h.serverId, h.userId)
            } returns part2Download

            // Unmanaged item is ignored
            h.manager.onItemMarkedPlayed(unmanagedId)
            coVerify(exactly = 0) { h.downloadRepository.deleteDownload(any()) }

            // Managed podcast episode is deleted immediately
            h.manager.onItemMarkedPlayed(podId)
            coVerify(exactly = 1) { h.downloadRepository.deleteDownload(podDlId) }
            assertFalse(
                h.autoDownloadPreferences.isItemManagedInEnabledLibraries(
                    h.serverId,
                    h.userId,
                    podId,
                ),
            )

            // Multi-part audiobook: finishing part 1 leaves both parts while part 2 is unplayed
            coEvery { h.mediaRepository.getUserDataByIds(listOf(part2Id)) } returns emptyList()
            h.manager.onItemMarkedPlayed(part1Id)
            coVerify(exactly = 0) { h.downloadRepository.deleteDownload(part1DlId) }
            coVerify(exactly = 0) { h.downloadRepository.deleteDownload(part2DlId) }

            // Finishing part 2 deletes both parts
            h.manager.onItemMarkedPlayed(part2Id)
            coVerify(exactly = 1) { h.downloadRepository.deleteDownload(part1DlId) }
            coVerify(exactly = 1) { h.downloadRepository.deleteDownload(part2DlId) }
            assertFalse(
                h.autoDownloadPreferences.isItemManagedInEnabledLibraries(
                    h.serverId,
                    h.userId,
                    part1Id,
                ),
            )
            assertFalse(
                h.autoDownloadPreferences.isItemManagedInEnabledLibraries(
                    h.serverId,
                    h.userId,
                    part2Id,
                ),
            )
        }

    @Test
    fun `onItemMarkedPlayed retains managed state on VolumeUnavailableException and cancels active download`() =
        runTest {
            val h = Harness(tempFolder.root, backgroundScope)
            h.autoDownloadPreferences.setLibraryEnabled(
                h.serverId,
                h.userId,
                h.podcastsLibId,
                enabled = true,
            )
            val sdItem = testUuid(90)
            val sdDlId = testUuid(190)
            val queuedItem = testUuid(91)
            val queuedDlId = testUuid(191)
            h.autoDownloadPreferences.setManagedItemIds(
                h.serverId,
                h.userId,
                h.podcastsLibId,
                setOf(sdItem, queuedItem),
            )
            val sdDl = downloadDto(sdDlId, sdItem, status = DownloadStatus.COMPLETED)
            val queuedDl = downloadDto(queuedDlId, queuedItem, status = DownloadStatus.DOWNLOADING)
            coEvery {
                h.databaseRepository.getDownloadByItemIdScoped(sdItem, h.serverId, h.userId)
            } returns sdDl
            coEvery {
                h.databaseRepository.getDownloadByItemIdScoped(queuedItem, h.serverId, h.userId)
            } returns queuedDl
            coEvery { h.downloadRepository.deleteDownload(sdDlId) } returns
                Result.failure(VolumeUnavailableException("sd-1", "SD Card"))

            h.manager.onItemMarkedPlayed(sdItem)
            assertTrue(
                h.autoDownloadPreferences.isItemManagedInEnabledLibraries(
                    h.serverId,
                    h.userId,
                    sdItem,
                ),
            )

            h.manager.onItemMarkedPlayed(queuedItem)
            coVerify(exactly = 1) { h.downloadRepository.cancelDownload(queuedDlId) }
            assertFalse(
                h.autoDownloadPreferences.isItemManagedInEnabledLibraries(
                    h.serverId,
                    h.userId,
                    queuedItem,
                ),
            )
        }

    @Test
    fun `setLibraryEnabled schedules periodic sync and cancels when all disabled`() = runTest {
        val workerScope =
            CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined)
        val h = Harness(tempFolder.root, workerScope)
        stubLibraryQueries(h.mediaRepository, h.podcastsLibId, emptyList(), emptyList())

        h.manager.setLibraryEnabled(h.podcastsLibId, enabled = true)
        advanceUntilIdle()
        verify(exactly = 1) { h.scheduler.ensurePeriodicSync(true) }

        h.manager.setLibraryEnabled(h.podcastsLibId, enabled = false)
        verify(exactly = 1) { h.scheduler.cancelAll() }
    }

    @Test
    fun `syncEnabledLibraries preserves existing managed items on VolumeUnavailableException`() =
        runTest {
            val h = Harness(tempFolder.root, backgroundScope)
            h.autoDownloadPreferences.setLibraryEnabled(
                h.serverId,
                h.userId,
                h.podcastsLibId,
                enabled = true,
            )
            val newItem = testUuid(301)
            val alreadyDownloadedItem = testUuid(302)
            h.autoDownloadPreferences.setManagedItemIds(
                h.serverId,
                h.userId,
                h.podcastsLibId,
                setOf(alreadyDownloadedItem),
            )
            h.downloadsFlow.value =
                listOf(
                    downloadDto(
                        id = testUuid(402),
                        itemId = alreadyDownloadedItem,
                        status = DownloadStatus.COMPLETED,
                    ),
                )
            coEvery { h.downloadRepository.startDownload(newItem, "") } returns
                Result.failure(VolumeUnavailableException("sd-1", "SD Card"))

            val dtos =
                listOf(
                    BaseItemDto(
                        id = newItem,
                        type = BaseItemKind.AUDIO,
                        canDownload = true,
                        userData = mockUserItemDataDto(played = false, playbackPositionTicks = 0L),
                    ),
                    BaseItemDto(
                        id = alreadyDownloadedItem,
                        type = BaseItemKind.AUDIO,
                        canDownload = true,
                        userData = mockUserItemDataDto(played = false, playbackPositionTicks = 0L),
                    ),
                )
            stubLibraryQueries(h.mediaRepository, h.podcastsLibId, emptyList(), dtos)

            assertFalse(h.manager.syncEnabledLibraries())
            assertTrue(
                h.autoDownloadPreferences.isItemManagedInEnabledLibraries(
                    h.serverId,
                    h.userId,
                    alreadyDownloadedItem,
                ),
            )
        }

    @Test
    fun `active podcast and Jellyfin audiobook downloads are not deleted via WebSocket or reconciliation while playing`() =
        runTest {
            val h = Harness(tempFolder.root, backgroundScope)
            h.manager.initMonitoring()
            h.autoDownloadPreferences.setLibraryEnabled(
                h.serverId,
                h.userId,
                h.podcastsLibId,
                enabled = true,
            )
            h.autoDownloadPreferences.setLibraryEnabled(
                h.serverId,
                h.userId,
                h.audiobooksLibId,
                enabled = true,
            )

            val podId = testUuid(501)
            val podDlId = testUuid(601)
            val bookPart1 = testUuid(502)
            val bookPart2 = testUuid(503)
            val bookPart1DlId = testUuid(602)
            val bookPart2DlId = testUuid(603)

            h.autoDownloadPreferences.setManagedItemIds(
                h.serverId,
                h.userId,
                h.podcastsLibId,
                setOf(podId),
            )
            h.autoDownloadPreferences.setManagedItemIds(
                h.serverId,
                h.userId,
                h.audiobooksLibId,
                setOf(bookPart1, bookPart2),
            )

            val podDl = downloadDto(podDlId, podId, itemType = "Audio")
            val part1Dl =
                downloadDto(
                    bookPart1DlId,
                    bookPart1,
                    itemType = AUDIOBOOK_DOWNLOAD_TYPE,
                    seriesId = "well-of-ascension",
                )
            val part2Dl =
                downloadDto(
                    bookPart2DlId,
                    bookPart2,
                    itemType = AUDIOBOOK_DOWNLOAD_TYPE,
                    seriesId = "well-of-ascension",
                )
            h.downloadsFlow.value = listOf(podDl, part1Dl, part2Dl)
            coEvery {
                h.databaseRepository.getDownloadByItemIdScoped(podId, h.serverId, h.userId)
            } returns podDl
            coEvery {
                h.databaseRepository.getDownloadByItemIdScoped(bookPart1, h.serverId, h.userId)
            } returns part1Dl
            coEvery {
                h.databaseRepository.getDownloadByItemIdScoped(bookPart2, h.serverId, h.userId)
            } returns part2Dl

            h.musicPlaybackState.value = MusicPlaybackState(currentTrack = testTrack(id = podId))
            h.absPlaybackState.value =
                AudiobookshelfPlaybackState(
                    source = AudiobookSource.JELLYFIN,
                    sessionId = "jf-session",
                    itemId = bookPart1.toString(),
                    jellyfinPartIds = listOf(bookPart1, bookPart2),
                )

            // WebSocket UserDataChanged(played=true) is ignored while actively playing
            h.mediaChangesFlow.emit(
                MediaChangeEvent(
                    itemId = podId,
                    source = com.makd.afinity.data.manager.MediaChangeSource.WEBSOCKET,
                    userData = mockUserItemDataDto(played = true, playbackPositionTicks = 0L),
                ),
            )
            h.mediaChangesFlow.emit(
                MediaChangeEvent(
                    itemId = bookPart2,
                    source = com.makd.afinity.data.manager.MediaChangeSource.WEBSOCKET,
                    userData = mockUserItemDataDto(played = true, playbackPositionTicks = 0L),
                ),
            )
            withContext(Dispatchers.Default) { delay(50) }
            advanceUntilIdle()
            coVerify(exactly = 0) { h.downloadRepository.deleteDownload(any()) }

            // Reconciliation during sync also preserves active downloads
            stubLibraryQueries(h.mediaRepository, h.podcastsLibId, emptyList(), emptyList())
            stubLibraryQueries(h.mediaRepository, h.audiobooksLibId, emptyList(), emptyList())
            coEvery { h.mediaRepository.getUserDataByIds(any()) } answers
                {
                    firstArg<List<UUID>>().map { id ->
                        mockUserItemDataDto(played = true, playbackPositionTicks = 0L).also {
                            every { it.itemId } returns id
                        }
                    }
                }
            assertTrue(h.manager.syncEnabledLibraries())
            coVerify(exactly = 0) { h.downloadRepository.deleteDownload(any()) }

            // Genuine completion on stop via PlayedItemEventBus still cleans up
            h.playedItemEventBus.emitPlayed(podId)
            coVerify(timeout = 2_000L, exactly = 1) { h.downloadRepository.deleteDownload(podDlId) }

            // Once audiobook session ends, reconciliation removes played parts
            h.absPlaybackState.value = AudiobookshelfPlaybackState()
            assertTrue(h.manager.syncEnabledLibraries())
            coVerify(exactly = 1) { h.downloadRepository.deleteDownload(bookPart1DlId) }
            coVerify(exactly = 1) { h.downloadRepository.deleteDownload(bookPart2DlId) }
        }
}
