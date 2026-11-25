package com.makd.afinity.player.music

import com.makd.afinity.data.autodownload.AutoDownloadPreferences
import com.makd.afinity.data.autodownload.PlayedItemEventBus
import com.makd.afinity.data.manager.MediaRefreshBus
import com.makd.afinity.data.manager.RefreshTrigger
import com.makd.afinity.data.manager.Session
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.models.user.AfinityUserDataDto
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.store.ItemStore
import com.makd.afinity.data.sync.UserDataSyncScheduler
import com.makd.afinity.testutil.FakeApiClient
import com.makd.afinity.testutil.TestMediaFixtures.testCollection
import com.makd.afinity.testutil.TestMediaFixtures.testTrack
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UpdateUserItemDataDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastProgressTrackerTest {

    @Test
    fun `resolvePodcastProgress handles resume, completion, and early stop`() {
        assertEquals(
            PodcastProgressOutcome(played = false, playbackPositionTicks = 600_000_000L),
            resolvePodcastProgress(
                positionMs = 60_000L,
                durationMs = 600_000L,
                isEnded = false,
                wasPlayed = false,
            ),
        )
        assertEquals(
            PodcastProgressOutcome(played = true, playbackPositionTicks = 0L),
            resolvePodcastProgress(
                positionMs = 580_000L,
                durationMs = 600_000L,
                isEnded = false,
                wasPlayed = false,
            ),
        )
        assertEquals(
            PodcastProgressOutcome(played = true, playbackPositionTicks = 0L),
            resolvePodcastProgress(
                positionMs = 100_000L,
                durationMs = 600_000L,
                isEnded = true,
                wasPlayed = false,
            ),
        )
        assertEquals(
            PodcastProgressOutcome(played = false, playbackPositionTicks = 0L),
            resolvePodcastProgress(
                positionMs = 3_000L,
                durationMs = 600_000L,
                isEnded = false,
                wasPlayed = false,
            ),
        )
        assertNull(
            resolvePodcastProgress(
                positionMs = 3_000L,
                durationMs = 600_000L,
                isEnded = false,
                wasPlayed = true,
            ),
        )
        assertEquals(
            PodcastProgressOutcome(played = false, playbackPositionTicks = 5_800_000_000L),
            resolvePodcastProgress(
                positionMs = 580_000L,
                durationMs = 600_000L,
                isEnded = false,
                wasPlayed = false,
                allowCompletion = false,
            ),
        )
        assertEquals(
            PodcastProgressOutcome(played = true, playbackPositionTicks = 0L),
            resolvePodcastProgress(
                positionMs = 580_000L,
                durationMs = 600_000L,
                isEnded = true,
                wasPlayed = false,
                allowCompletion = false,
            ),
        )
    }

    @Test
    fun `syncProgress skips non-podcast tracks and updates Jellyfin UserData for podcasts`() =
        runTest {
            val userId = testUuid(1)
            val podcastLibId = testUuid(10)
            val podcastAlbumId = testUuid(20)
            val musicAlbumId = testUuid(21)
            val episodeId = testUuid(30)
            val songId = testUuid(31)

            val apiClient = FakeApiClient()
            apiClient.onPost("/Users/{userId}/Items/{itemId}/UserData") { req ->
                val itemId = req.pathParameters["itemId"] as UUID
                FakeApiClient.rawJsonResponse(
                    """{"ItemId":"$itemId","Key":"$itemId","Played":false,"PlaybackPositionTicks":900000000,"PlayCount":1,"IsFavorite":false}""",
                )
            }
            apiClient.onPost("/UserItems/{itemId}/UserData") { req ->
                val itemId = req.pathParameters["itemId"] as UUID
                FakeApiClient.rawJsonResponse(
                    """{"ItemId":"$itemId","Key":"$itemId","Played":false,"PlaybackPositionTicks":900000000,"PlayCount":1,"IsFavorite":false}""",
                )
            }

            val sessionManager: SessionManager =
                mockk(relaxed = true) {
                    every { currentSession } returns
                        MutableStateFlow(Session("srv-1", userId, "https://jf.example.com"))
                    coEvery { getCurrentApiClient() } returns apiClient
                }
            val podcastLib =
                testCollection(id = podcastLibId, name = "Podcasts", type = CollectionType.Music)
            val musicLib =
                testCollection(id = testUuid(11), name = "Music", type = CollectionType.Music)
            val mediaRepository: MediaRepository =
                mockk(relaxed = true) {
                    every { libraries } returns MutableStateFlow(listOf(podcastLib, musicLib))
                    coEvery {
                        getItemsResult(
                            parentId = podcastLibId,
                            includeItemTypes = listOf("MUSIC_ALBUM"),
                            limit = any(),
                            fields = any(),
                            recursive = true,
                            enableImages = false,
                            enableUserData = false,
                        )
                    } returns
                        Result.success(
                            BaseItemDtoQueryResult(
                                items =
                                    listOf(
                                        BaseItemDto(
                                            id = podcastAlbumId,
                                            type = BaseItemKind.MUSIC_ALBUM,
                                        ),
                                    ),
                                startIndex = 0,
                                totalRecordCount = 1,
                            ),
                        )
                }
            val itemStore = ItemStore()
            val refreshBus = MediaRefreshBus()
            val databaseRepository: DatabaseRepository = mockk(relaxed = true)
            val preferencesRepository: PreferencesRepository =
                mockk(relaxed = true) { coEvery { getOfflineMode() } returns false }
            val userDataSyncScheduler: UserDataSyncScheduler = mockk(relaxed = true)
            val autoDownloadPreferences: AutoDownloadPreferences =
                mockk(relaxed = true) {
                    coEvery { getCachedPodcastAlbumIds(any(), any()) } returns emptySet()
                }
            val playedItemEventBus = PlayedItemEventBus()
            val tracker =
                PodcastProgressTracker(
                    sessionManager = sessionManager,
                    mediaRepository = mediaRepository,
                    itemStore = itemStore,
                    mediaRefreshBus = refreshBus,
                    databaseRepository = databaseRepository,
                    preferencesRepository = preferencesRepository,
                    userDataSyncScheduler = userDataSyncScheduler,
                    autoDownloadPreferences = autoDownloadPreferences,
                    playedItemEventBus = playedItemEventBus,
                )

            val song =
                testTrack(id = songId).copy(albumId = musicAlbumId, runtimeTicks = 2_400_000_000L)
            tracker.syncProgress(song, positionMs = 90_000L, isEnded = false)
            assertTrue(apiClient.requests.isEmpty())

            val episode =
                testTrack(id = episodeId, played = false)
                    .copy(
                        albumId = podcastAlbumId,
                        runtimeTicks = 18_000_000_000L,
                    )
            itemStore.put(episode)
            tracker.syncProgress(episode, positionMs = 90_000L, isEnded = false)

            assertEquals(1, apiClient.requests.size)
            val body = apiClient.requests.single().requestBody as UpdateUserItemDataDto
            assertEquals(false, body.played)
            assertEquals(900_000_000L, body.playbackPositionTicks)
            verify {
                mediaRepository.updateContinueListeningPodcast(
                    match {
                        it.id == episodeId && it.playbackPositionTicks == 900_000_000L && !it.played
                    },
                )
            }
            val patched =
                itemStore.getAs<com.makd.afinity.data.models.music.AfinityTrack>(episodeId)
            assertEquals(900_000_000L, patched?.playbackPositionTicks)

            var observedTrigger: RefreshTrigger? = null
            val collector =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    observedTrigger = refreshBus.events.first()
                }
            tracker.syncProgress(episode, positionMs = 1_800_000L, isEnded = true)
            collector.join()
            assertEquals(RefreshTrigger.USER_DATA_CHANGED, observedTrigger)
        }

    @Test
    fun `podcastAlbumIds caches empty library result and retries after fetch failure`() = runTest {
        val userId = testUuid(2)
        val podcastLibId = testUuid(12)
        val podcastLib =
            testCollection(id = podcastLibId, name = "Podcasts", type = CollectionType.Music)
        val sessionManager: SessionManager =
            mockk(relaxed = true) {
                every { currentSession } returns
                    MutableStateFlow(Session("srv-1", userId, "https://jf.example.com"))
            }
        val mediaRepository: MediaRepository =
            mockk(relaxed = true) {
                every { libraries } returns MutableStateFlow(listOf(podcastLib))
                coEvery {
                    getItemsResult(
                        parentId = podcastLibId,
                        includeItemTypes = listOf("MUSIC_ALBUM"),
                        limit = any(),
                        fields = any(),
                        recursive = true,
                        enableImages = false,
                        enableUserData = false,
                    )
                } returnsMany
                    listOf(
                        Result.failure(RuntimeException("network error")),
                        Result.success(
                            BaseItemDtoQueryResult(
                                items = emptyList(),
                                startIndex = 0,
                                totalRecordCount = 0,
                            ),
                        ),
                    )
            }
        val tracker =
            PodcastProgressTracker(
                sessionManager = sessionManager,
                mediaRepository = mediaRepository,
                itemStore = ItemStore(),
                mediaRefreshBus = MediaRefreshBus(),
                databaseRepository = mockk(relaxed = true),
                preferencesRepository =
                    mockk(relaxed = true) { coEvery { getOfflineMode() } returns false },
                userDataSyncScheduler = mockk(relaxed = true),
                autoDownloadPreferences =
                    mockk(relaxed = true) {
                        coEvery { getCachedPodcastAlbumIds(any(), any()) } returns emptySet()
                    },
                playedItemEventBus = PlayedItemEventBus(),
            )
        val track = testTrack(id = testUuid(40)).copy(albumId = testUuid(41))

        assertFalse(tracker.isPodcastTrack(track))
        assertFalse(tracker.isPodcastTrack(track))
        assertFalse(tracker.isPodcastTrack(track))

        // 1st call failed (not cached), 2nd call succeeded with empty set (cached), 3rd hit cache
        coVerify(exactly = 2) {
            mediaRepository.getItemsResult(
                parentId = podcastLibId,
                includeItemTypes = listOf("MUSIC_ALBUM"),
                limit = any(),
                fields = any(),
                recursive = true,
                enableImages = false,
                enableUserData = false,
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `offline syncProgress persists pending userData, schedules sync, and emits played event`() =
        runTest {
            val userId = testUuid(5)
            val podcastAlbumId = testUuid(25)
            val episodeId = testUuid(35)

            val sessionManager: SessionManager =
                mockk(relaxed = true) {
                    every { currentSession } returns
                        MutableStateFlow(Session("srv-1", userId, "https://jf.example.com"))
                    every { getCurrentApiClient() } returns null
                }
            val mediaRepository: MediaRepository =
                mockk(relaxed = true) { every { libraries } returns MutableStateFlow(emptyList()) }
            val databaseRepository: DatabaseRepository =
                mockk(relaxed = true) { coEvery { getUserData(userId, episodeId) } returns null }
            val preferencesRepository: PreferencesRepository =
                mockk(relaxed = true) { coEvery { getOfflineMode() } returns true }
            val userDataSyncScheduler: UserDataSyncScheduler = mockk(relaxed = true)
            val autoDownloadPreferences: AutoDownloadPreferences =
                mockk(relaxed = true) {
                    coEvery { getCachedPodcastAlbumIds("srv-1", userId) } returns
                        setOf(podcastAlbumId)
                }
            val playedItemEventBus = PlayedItemEventBus()
            val itemStore = ItemStore()
            val tracker =
                PodcastProgressTracker(
                    sessionManager = sessionManager,
                    mediaRepository = mediaRepository,
                    itemStore = itemStore,
                    mediaRefreshBus = MediaRefreshBus(),
                    databaseRepository = databaseRepository,
                    preferencesRepository = preferencesRepository,
                    userDataSyncScheduler = userDataSyncScheduler,
                    autoDownloadPreferences = autoDownloadPreferences,
                    playedItemEventBus = playedItemEventBus,
                )

            val episode =
                testTrack(id = episodeId, played = false)
                    .copy(albumId = podcastAlbumId, runtimeTicks = 18_000_000_000L)
            itemStore.put(episode)

            assertTrue(tracker.isPodcastTrack(episode))

            val emitted = mutableListOf<UUID>()
            val collector =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    playedItemEventBus.playedItems.take(1).toList(emitted)
                }

            tracker.syncProgress(episode, positionMs = 1_800_000L, isEnded = true)
            advanceUntilIdle()
            collector.join()

            assertEquals(listOf(episodeId), emitted)
            coVerify(exactly = 1) {
                databaseRepository.insertUserData(
                    match<AfinityUserDataDto> {
                        it.itemId == episodeId &&
                            it.userId == userId &&
                            it.serverId == "srv-1" &&
                            it.played &&
                            it.playbackPositionTicks == 0L &&
                            it.toBeSynced
                    },
                )
            }
            coVerify(exactly = 1) { userDataSyncScheduler.triggerSync() }
        }

    @Test
    fun `resolvePodcastTrack distinguishes multiple podcast libraries, non-podcasts, and offline cache`() =
        runTest {
            val userId = testUuid(8)
            val podcastLibA = testUuid(80)
            val podcastLibB = testUuid(81)
            val albumA = testUuid(82)
            val albumB = testUuid(83)
            val musicAlbum = testUuid(84)

            val sessionManager: SessionManager =
                mockk(relaxed = true) {
                    every { currentSession } returns
                        MutableStateFlow(Session("srv-1", userId, "https://jf.example.com"))
                }
            val mediaRepository: MediaRepository =
                mockk(relaxed = true) {
                    every { libraries } returns
                        MutableStateFlow(
                            listOf(
                                testCollection(
                                    id = podcastLibA,
                                    name = "Podcasts",
                                    type = CollectionType.Music,
                                ),
                                testCollection(
                                    id = podcastLibB,
                                    name = "Tech Podcasts",
                                    type = CollectionType.Music,
                                ),
                                testCollection(
                                    id = testUuid(85),
                                    name = "Music",
                                    type = CollectionType.Music,
                                ),
                            ),
                        )
                    coEvery {
                        getItemsResult(
                            parentId = podcastLibA,
                            includeItemTypes = listOf("MUSIC_ALBUM"),
                            limit = any(),
                            fields = any(),
                            recursive = true,
                            enableImages = false,
                            enableUserData = false,
                        )
                    } returns
                        Result.success(
                            BaseItemDtoQueryResult(
                                items =
                                    listOf(
                                        BaseItemDto(
                                            id = albumA,
                                            type = BaseItemKind.MUSIC_ALBUM,
                                        ),
                                    ),
                                startIndex = 0,
                                totalRecordCount = 1,
                            ),
                        )
                    coEvery {
                        getItemsResult(
                            parentId = podcastLibB,
                            includeItemTypes = listOf("MUSIC_ALBUM"),
                            limit = any(),
                            fields = any(),
                            recursive = true,
                            enableImages = false,
                            enableUserData = false,
                        )
                    } returns
                        Result.success(
                            BaseItemDtoQueryResult(
                                items =
                                    listOf(
                                        BaseItemDto(
                                            id = albumB,
                                            type = BaseItemKind.MUSIC_ALBUM,
                                        ),
                                    ),
                                startIndex = 0,
                                totalRecordCount = 1,
                            ),
                        )
                }
            val autoDownloadPreferences: AutoDownloadPreferences =
                mockk(relaxed = true) {
                    coEvery { getCachedPodcastAlbumLibraries(any(), any()) } returns emptyMap()
                    coEvery { getCachedPodcastAlbumIds(any(), any()) } returns emptySet()
                }
            val tracker =
                PodcastProgressTracker(
                    sessionManager = sessionManager,
                    mediaRepository = mediaRepository,
                    itemStore = ItemStore(),
                    mediaRefreshBus = MediaRefreshBus(),
                    databaseRepository = mockk(relaxed = true),
                    preferencesRepository =
                        mockk(relaxed = true) { coEvery { getOfflineMode() } returns false },
                    userDataSyncScheduler = mockk(relaxed = true),
                    autoDownloadPreferences = autoDownloadPreferences,
                    playedItemEventBus = PlayedItemEventBus(),
                )

            val trackA = testTrack(id = testUuid(90)).copy(albumId = albumA)
            val trackB =
                testTrack(id = testUuid(91)).copy(albumId = albumB, playbackPositionTicks = 50_000L)
            val song = testTrack(id = testUuid(92)).copy(albumId = musicAlbum)

            assertEquals(PodcastTrackInfo.Podcast(podcastLibA), tracker.resolvePodcastTrack(trackA))
            assertEquals(PodcastTrackInfo.Podcast(podcastLibB), tracker.resolvePodcastTrack(trackB))
            assertEquals(PodcastTrackInfo.NotPodcast, tracker.resolvePodcastTrack(song))
            coVerify(exactly = 1) {
                autoDownloadPreferences.setCachedPodcastAlbumLibraries(
                    "srv-1",
                    userId,
                    mapOf(albumA to podcastLibA, albumB to podcastLibB),
                )
            }

            // Offline mode uses persisted album -> library mapping
            val offlineAutoDownload: AutoDownloadPreferences =
                mockk(relaxed = true) {
                    coEvery { getCachedPodcastAlbumLibraries("srv-1", userId) } returns
                        mapOf(albumB to podcastLibB)
                    coEvery { getCachedPodcastAlbumIds(any(), any()) } returns emptySet()
                }
            val offlineTracker =
                PodcastProgressTracker(
                    sessionManager = sessionManager,
                    mediaRepository =
                        mockk(relaxed = true) {
                            every { libraries } returns MutableStateFlow(emptyList())
                        },
                    itemStore = ItemStore(),
                    mediaRefreshBus = MediaRefreshBus(),
                    databaseRepository = mockk(relaxed = true),
                    preferencesRepository =
                        mockk(relaxed = true) { coEvery { getOfflineMode() } returns true },
                    userDataSyncScheduler = mockk(relaxed = true),
                    autoDownloadPreferences = offlineAutoDownload,
                    playedItemEventBus = PlayedItemEventBus(),
                )
            assertEquals(
                PodcastTrackInfo.Podcast(podcastLibB),
                offlineTracker.resolvePodcastTrack(trackB),
            )
            assertEquals(PodcastTrackInfo.NotPodcast, offlineTracker.resolvePodcastTrack(song))
        }

    @Test
    fun `isPodcastAlbum checks podcast libraries, offline cache, legacy cache, and fails safely`() =
        runTest {
            val userId = testUuid(1)
            val podcastLibId = testUuid(10)
            val podcastAlbumId = testUuid(20)
            val musicAlbumId = testUuid(21)
            val legacyAlbumId = testUuid(22)

            val sessionManager: SessionManager =
                mockk(relaxed = true) {
                    every { currentSession } returns
                        MutableStateFlow(Session("srv-1", userId, "https://jf.example.com"))
                }
            val podcastLib =
                testCollection(id = podcastLibId, name = "Podcasts", type = CollectionType.Music)
            val mediaRepository: MediaRepository =
                mockk(relaxed = true) {
                    every { libraries } returns MutableStateFlow(listOf(podcastLib))
                    coEvery {
                        getItemsResult(
                            parentId = podcastLibId,
                            includeItemTypes = listOf("MUSIC_ALBUM"),
                            limit = any(),
                            fields = any(),
                            recursive = true,
                            enableImages = false,
                            enableUserData = false,
                        )
                    } returns
                        Result.success(
                            BaseItemDtoQueryResult(
                                items =
                                    listOf(
                                        BaseItemDto(
                                            id = podcastAlbumId,
                                            type = BaseItemKind.MUSIC_ALBUM,
                                        ),
                                    ),
                                startIndex = 0,
                                totalRecordCount = 1,
                            ),
                        )
                }
            val autoDownloadPreferences: AutoDownloadPreferences =
                mockk(relaxed = true) {
                    coEvery { getCachedPodcastAlbumLibraries(any(), any()) } returns emptyMap()
                    coEvery { getCachedPodcastAlbumIds(any(), any()) } returns emptySet()
                }
            val onlineTracker =
                PodcastProgressTracker(
                    sessionManager = sessionManager,
                    mediaRepository = mediaRepository,
                    itemStore = ItemStore(),
                    mediaRefreshBus = MediaRefreshBus(),
                    databaseRepository = mockk(relaxed = true),
                    preferencesRepository =
                        mockk(relaxed = true) { coEvery { getOfflineMode() } returns false },
                    userDataSyncScheduler = mockk(relaxed = true),
                    autoDownloadPreferences = autoDownloadPreferences,
                    playedItemEventBus = PlayedItemEventBus(),
                )

            assertTrue(onlineTracker.isPodcastAlbum(podcastAlbumId))
            assertFalse(onlineTracker.isPodcastAlbum(musicAlbumId))

            val offlinePrefs: AutoDownloadPreferences =
                mockk(relaxed = true) {
                    coEvery { getCachedPodcastAlbumLibraries("srv-1", userId) } returns
                        mapOf(podcastAlbumId to podcastLibId)
                    coEvery { getCachedPodcastAlbumIds("srv-1", userId) } returns
                        setOf(legacyAlbumId)
                }
            val offlineTracker =
                PodcastProgressTracker(
                    sessionManager = sessionManager,
                    mediaRepository =
                        mockk(relaxed = true) {
                            every { libraries } returns MutableStateFlow(emptyList())
                        },
                    itemStore = ItemStore(),
                    mediaRefreshBus = MediaRefreshBus(),
                    databaseRepository = mockk(relaxed = true),
                    preferencesRepository =
                        mockk(relaxed = true) { coEvery { getOfflineMode() } returns true },
                    userDataSyncScheduler = mockk(relaxed = true),
                    autoDownloadPreferences = offlinePrefs,
                    playedItemEventBus = PlayedItemEventBus(),
                )

            assertTrue(offlineTracker.isPodcastAlbum(podcastAlbumId))
            assertTrue(offlineTracker.isPodcastAlbum(legacyAlbumId))
            assertFalse(offlineTracker.isPodcastAlbum(musicAlbumId))

            val failingPrefs: AutoDownloadPreferences =
                mockk(relaxed = true) {
                    coEvery { getCachedPodcastAlbumLibraries(any(), any()) } throws
                        RuntimeException("datastore error")
                }
            val failingTracker =
                PodcastProgressTracker(
                    sessionManager = sessionManager,
                    mediaRepository = mediaRepository,
                    itemStore = ItemStore(),
                    mediaRefreshBus = MediaRefreshBus(),
                    databaseRepository = mockk(relaxed = true),
                    preferencesRepository =
                        mockk(relaxed = true) { coEvery { getOfflineMode() } returns false },
                    userDataSyncScheduler = mockk(relaxed = true),
                    autoDownloadPreferences = failingPrefs,
                    playedItemEventBus = PlayedItemEventBus(),
                )

            assertFalse(failingTracker.isPodcastAlbum(podcastAlbumId))
        }

    @Test
    fun `resolvePodcastTrack fails safely on exceptions and syncPeriodicIfDue respects cadence, pause, and position threshold`() =
        runTest {
            val userId = testUuid(9)
            val podcastLibId = testUuid(90)
            val podcastAlbumId = testUuid(91)
            val episodeId = testUuid(92)
            val sessionManager: SessionManager =
                mockk(relaxed = true) {
                    every { currentSession } returns
                        MutableStateFlow(Session("srv-1", userId, "https://jf.example.com"))
                }
            val throwingMediaRepo: MediaRepository =
                mockk(relaxed = true) { every { libraries } returns emptyFlow() }
            val failingTracker =
                PodcastProgressTracker(
                    sessionManager = sessionManager,
                    mediaRepository = throwingMediaRepo,
                    itemStore = ItemStore(),
                    mediaRefreshBus = MediaRefreshBus(),
                    databaseRepository = mockk(relaxed = true),
                    preferencesRepository =
                        mockk(relaxed = true) { coEvery { getOfflineMode() } returns true },
                    userDataSyncScheduler = mockk(relaxed = true),
                    autoDownloadPreferences =
                        mockk(relaxed = true) {
                            coEvery { getCachedPodcastAlbumLibraries(any(), any()) } returns
                                emptyMap()
                            coEvery { getCachedPodcastAlbumIds(any(), any()) } returns emptySet()
                        },
                    playedItemEventBus = PlayedItemEventBus(),
                )
            val orphanTrack = testTrack(id = testUuid(93)).copy(playbackPositionTicks = 50_000L)
            assertEquals(
                PodcastTrackInfo.NotPodcast,
                failingTracker.resolvePodcastTrack(orphanTrack),
            )

            val apiClient = FakeApiClient()
            apiClient.onPost("/UserItems/{itemId}/UserData") { req ->
                val itemId = req.pathParameters["itemId"] as UUID
                FakeApiClient.rawJsonResponse(
                    """{"ItemId":"$itemId","Key":"$itemId","Played":false,"PlaybackPositionTicks":450000000,"PlayCount":0,"IsFavorite":false}""",
                )
            }
            val onlineSessionManager: SessionManager =
                mockk(relaxed = true) {
                    every { currentSession } returns
                        MutableStateFlow(Session("srv-1", userId, "https://jf.example.com"))
                    coEvery { getCurrentApiClient() } returns apiClient
                }
            val podcastLib =
                testCollection(id = podcastLibId, name = "Podcasts", type = CollectionType.Music)
            val mediaRepository: MediaRepository =
                mockk(relaxed = true) {
                    every { libraries } returns MutableStateFlow(listOf(podcastLib))
                    coEvery {
                        getItemsResult(
                            parentId = podcastLibId,
                            includeItemTypes = listOf("MUSIC_ALBUM"),
                            limit = any(),
                            fields = any(),
                            recursive = true,
                            enableImages = false,
                            enableUserData = false,
                        )
                    } returns
                        Result.success(
                            BaseItemDtoQueryResult(
                                items =
                                    listOf(
                                        BaseItemDto(
                                            id = podcastAlbumId,
                                            type = BaseItemKind.MUSIC_ALBUM,
                                        ),
                                    ),
                                startIndex = 0,
                                totalRecordCount = 1,
                            ),
                        )
                }
            val tracker =
                PodcastProgressTracker(
                    sessionManager = onlineSessionManager,
                    mediaRepository = mediaRepository,
                    itemStore = ItemStore(),
                    mediaRefreshBus = MediaRefreshBus(),
                    databaseRepository = mockk(relaxed = true),
                    preferencesRepository =
                        mockk(relaxed = true) { coEvery { getOfflineMode() } returns false },
                    userDataSyncScheduler = mockk(relaxed = true),
                    autoDownloadPreferences =
                        mockk(relaxed = true) {
                            coEvery { getCachedPodcastAlbumIds(any(), any()) } returns emptySet()
                        },
                    playedItemEventBus = PlayedItemEventBus(),
                )
            val episode =
                testTrack(id = episodeId, played = false)
                    .copy(albumId = podcastAlbumId, runtimeTicks = 600_000_000L)

            // Not due on ticks 1/2, when paused, or below 10s threshold
            assertNull(tracker.syncPeriodicIfDue(episode, 45_000L, isPaused = false, tickCount = 1))
            assertNull(tracker.syncPeriodicIfDue(episode, 45_000L, isPaused = true, tickCount = 3))
            assertNull(tracker.syncPeriodicIfDue(episode, 5_000L, isPaused = false, tickCount = 3))
            assertTrue(apiClient.requests.isEmpty())

            // Syncs on tick 3 when unpaused and >= 10s without marking completed in final window
            val synced =
                tracker.syncPeriodicIfDue(episode, 59_000L, isPaused = false, tickCount = 3)
            assertFalse(synced!!.played)
            assertEquals(590_000_000L, synced.playbackPositionTicks)
            assertEquals(1, apiClient.requests.size)
        }

    @Test
    fun `online sync clears stale pending row and caches synced progress only for downloaded tracks`() =
        runTest {
            val userId = testUuid(11)
            val podcastLibId = testUuid(110)
            val podcastAlbumId = testUuid(120)
            val downloadedEpId = testUuid(130)
            val streamingEpId = testUuid(131)

            val apiClient = FakeApiClient()
            apiClient.onPost("/UserItems/{itemId}/UserData") { req ->
                val itemId = req.pathParameters["itemId"] as UUID
                FakeApiClient.rawJsonResponse(
                    """{"ItemId":"$itemId","Key":"$itemId","Played":false,"PlaybackPositionTicks":1200000000,"PlayCount":0,"IsFavorite":false}""",
                )
            }
            val sessionManager: SessionManager =
                mockk(relaxed = true) {
                    every { currentSession } returns
                        MutableStateFlow(Session("srv-1", userId, "https://jf.example.com"))
                    coEvery { getCurrentApiClient() } returns apiClient
                }
            val podcastLib =
                testCollection(id = podcastLibId, name = "Podcasts", type = CollectionType.Music)
            val mediaRepository: MediaRepository =
                mockk(relaxed = true) {
                    every { libraries } returns MutableStateFlow(listOf(podcastLib))
                    coEvery {
                        getItemsResult(
                            parentId = podcastLibId,
                            includeItemTypes = listOf("MUSIC_ALBUM"),
                            limit = any(),
                            fields = any(),
                            recursive = true,
                            enableImages = false,
                            enableUserData = false,
                        )
                    } returns
                        Result.success(
                            BaseItemDtoQueryResult(
                                items =
                                    listOf(
                                        BaseItemDto(
                                            id = podcastAlbumId,
                                            type = BaseItemKind.MUSIC_ALBUM,
                                        ),
                                    ),
                                startIndex = 0,
                                totalRecordCount = 1,
                            ),
                        )
                }
            val databaseRepository: DatabaseRepository = mockk(relaxed = true)
            coEvery {
                databaseRepository.updateSyncedPlaybackState(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns 0
            coEvery {
                databaseRepository.getMusicTrack(downloadedEpId, "srv-1", userId.toString())
            } returns testTrack(id = downloadedEpId)
            coEvery {
                databaseRepository.getMusicTrack(streamingEpId, "srv-1", userId.toString())
            } returns null
            coEvery { databaseRepository.getUserData(userId, downloadedEpId) } returns null

            val tracker =
                PodcastProgressTracker(
                    sessionManager = sessionManager,
                    mediaRepository = mediaRepository,
                    itemStore = ItemStore(),
                    mediaRefreshBus = MediaRefreshBus(),
                    databaseRepository = databaseRepository,
                    preferencesRepository =
                        mockk(relaxed = true) { coEvery { getOfflineMode() } returns false },
                    userDataSyncScheduler = mockk(relaxed = true),
                    autoDownloadPreferences =
                        mockk(relaxed = true) {
                            coEvery { getCachedPodcastAlbumIds(any(), any()) } returns emptySet()
                        },
                    playedItemEventBus = PlayedItemEventBus(),
                )

            val downloaded =
                testTrack(id = downloadedEpId, played = false)
                    .copy(albumId = podcastAlbumId, runtimeTicks = 18_000_000_000L)
            val streaming =
                testTrack(id = streamingEpId, played = false)
                    .copy(albumId = podcastAlbumId, runtimeTicks = 18_000_000_000L)

            tracker.syncProgress(downloaded, positionMs = 120_000L, isEnded = false)
            tracker.syncProgress(streaming, positionMs = 120_000L, isEnded = false)

            coVerify(exactly = 1) {
                databaseRepository.updateSyncedPlaybackState(
                    userId = userId,
                    itemId = downloadedEpId,
                    serverId = "srv-1",
                    positionTicks = 1_200_000_000L,
                    played = false,
                    lastPlayedAt = any(),
                )
            }
            coVerify(exactly = 1) {
                databaseRepository.updateSyncedPlaybackState(
                    userId = userId,
                    itemId = streamingEpId,
                    serverId = "srv-1",
                    positionTicks = 1_200_000_000L,
                    played = false,
                    lastPlayedAt = any(),
                )
            }
            coVerify(exactly = 1) {
                databaseRepository.insertUserData(
                    match {
                        it.itemId == downloadedEpId &&
                            it.playbackPositionTicks == 1_200_000_000L &&
                            !it.toBeSynced
                    },
                )
            }
            coVerify(exactly = 0) {
                databaseRepository.insertUserData(match { it.itemId == streamingEpId })
            }
        }

    @Test
    fun `syncProgress populates ItemStore even when episode was not previously cached`() = runTest {
        val userId = testUuid(1)
        val podcastLibId = testUuid(10)
        val podcastAlbumId = testUuid(20)
        val epId = testUuid(32)
        val apiClient = FakeApiClient()
        apiClient.onPost("/UserItems/{itemId}/UserData") { req ->
            val itemId = req.pathParameters["itemId"] as UUID
            FakeApiClient.rawJsonResponse(
                """{"ItemId":"$itemId","Key":"$itemId","Played":false,"PlaybackPositionTicks":1200000000,"PlayCount":0,"IsFavorite":false}""",
            )
        }
        val sessionManager: SessionManager =
            mockk(relaxed = true) {
                every { currentSession } returns
                    MutableStateFlow(Session("srv-1", userId, "https://jf.example.com"))
                coEvery { getCurrentApiClient() } returns apiClient
            }
        val podcastLib =
            testCollection(id = podcastLibId, name = "Podcasts", type = CollectionType.Music)
        val mediaRepository: MediaRepository =
            mockk(relaxed = true) {
                every { libraries } returns MutableStateFlow(listOf(podcastLib))
                coEvery {
                    getItemsResult(
                        parentId = podcastLibId,
                        includeItemTypes = listOf("MUSIC_ALBUM"),
                        limit = any(),
                        fields = any(),
                        recursive = true,
                        enableImages = false,
                        enableUserData = false,
                    )
                } returns
                    Result.success(
                        BaseItemDtoQueryResult(
                            items =
                                listOf(
                                    BaseItemDto(
                                        id = podcastAlbumId,
                                        type = BaseItemKind.MUSIC_ALBUM,
                                    ),
                                ),
                            startIndex = 0,
                            totalRecordCount = 1,
                        ),
                    )
            }
        val itemStore = ItemStore()
        val tracker =
            PodcastProgressTracker(
                sessionManager = sessionManager,
                mediaRepository = mediaRepository,
                itemStore = itemStore,
                mediaRefreshBus = MediaRefreshBus(),
                databaseRepository = mockk(relaxed = true),
                preferencesRepository =
                    mockk(relaxed = true) { coEvery { getOfflineMode() } returns false },
                userDataSyncScheduler = mockk(relaxed = true),
                autoDownloadPreferences =
                    mockk(relaxed = true) {
                        coEvery { getCachedPodcastAlbumIds(any(), any()) } returns emptySet()
                    },
                playedItemEventBus = PlayedItemEventBus(),
            )

        val track =
            testTrack(id = epId, played = false)
                .copy(albumId = podcastAlbumId, runtimeTicks = 18_000_000_000L)
        tracker.syncProgress(track, positionMs = 120_000L, isEnded = false)

        val stored = itemStore.getAs<AfinityTrack>(epId)
        assertEquals(1_200_000_000L, stored?.playbackPositionTicks)
    }
}
