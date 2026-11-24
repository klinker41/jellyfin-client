package com.makd.afinity.data.repository

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.makd.afinity.data.database.dao.DeletedItemDao
import com.makd.afinity.data.manager.AdminChangeBroadcaster
import com.makd.afinity.data.manager.BackgroundWorkQueue
import com.makd.afinity.data.manager.MediaChangeManager
import com.makd.afinity.data.manager.MediaRefreshBus
import com.makd.afinity.data.manager.Session
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.manager.UserImageStore
import com.makd.afinity.data.models.HeroContentSource
import com.makd.afinity.data.models.HomeRow
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.repository.home.HomeCacheRepository
import com.makd.afinity.data.repository.home.HomeLayoutPreferencesRepository
import com.makd.afinity.data.repository.home.HomeSectionsRepository
import com.makd.afinity.data.repository.livetv.LiveTvRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.music.MusicRepository
import com.makd.afinity.data.repository.server.ServerRepository
import com.makd.afinity.data.repository.watchlist.WatchlistRepository
import com.makd.afinity.data.store.ItemStore
import com.makd.afinity.testutil.MainDispatcherRule
import com.makd.afinity.testutil.TestMediaFixtures.testCollection
import com.makd.afinity.testutil.TestMediaFixtures.testEpisode
import com.makd.afinity.testutil.TestMediaFixtures.testMovie
import com.makd.afinity.testutil.TestMediaFixtures.testShow
import com.makd.afinity.testutil.TestMediaFixtures.testTrack
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import com.makd.afinity.util.JellyfinImageUrlBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AppDataRepositoryTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private inner class Harness {
        val context: Context = ApplicationProvider.getApplicationContext()
        val userId = testUuid(1)
        val serverId = "srv-1"
        val baseUrl = "https://jf.example.com"
        val sessionFlow = MutableStateFlow<Session?>(Session(serverId, userId, baseUrl))
        val sessionManager: SessionManager =
            mockk(relaxed = true) { every { currentSession } returns sessionFlow }

        val mediaRepository: MediaRepository =
            mockk(relaxed = true) {
                every { getBaseUrl() } returns baseUrl
                every { getContinueWatchingFlow() } returns MutableStateFlow(emptyList())
                every { getNextUpFlow() } returns MutableStateFlow(emptyList())
                every { getLibrariesFlow() } returns MutableStateFlow(emptyList())
                every { continueReading } returns MutableStateFlow(emptyList())
                every { continueListening } returns MutableStateFlow(emptyList())
                every { continueListeningPodcasts } returns MutableStateFlow(emptyList())
                every { libraries } returns MutableStateFlow(emptyList())
                coEvery { getFavoritesCountResult() } returns Result.success(0)
                coEvery { getUserDataByIds(any()) } returns emptyList()
            }
        val preferencesRepository: PreferencesRepository =
            mockk(relaxed = true) {
                every { getHeroContentSourceFlow(any(), any()) } returns emptyFlow()
                coEvery { getHeroContentSource(any(), any()) } returns HeroContentSource()
                coEvery { getHomeSortByDateAdded() } returns true
            }
        val userImageStore: UserImageStore = mockk(relaxed = true)
        val imageUrlBuilder: JellyfinImageUrlBuilder = mockk(relaxed = true)
        val watchlistRepository: WatchlistRepository = mockk(relaxed = true)
        val liveTvRepository: LiveTvRepository =
            mockk(relaxed = true) { every { channelFavoriteChanges } returns MutableSharedFlow() }
        val genreRepository: GenreRepository = mockk(relaxed = true)
        val peopleRepository: PeopleRepository = mockk(relaxed = true)
        val serverRepository: ServerRepository =
            mockk(relaxed = true) { every { currentBaseUrl } returns MutableStateFlow(baseUrl) }
        val mediaRefreshBus = MediaRefreshBus()
        val itemStore = ItemStore()
        val scope = CoroutineScope(SupervisorJob() + mainDispatcherRule.testDispatcher)
        val databaseRepository: DatabaseRepository =
            mockk(relaxed = true) {
                every { getAllUserDataFlow(any()) } returns MutableStateFlow(emptyList())
            }
        val mediaChangeManager =
            MediaChangeManager(
                mediaRepository = mediaRepository,
                databaseRepository = databaseRepository,
                sessionManager = sessionManager,
                mediaRefreshBus = mediaRefreshBus,
                itemStore = itemStore,
                scope = scope,
            )
        val musicRepository: MusicRepository = mockk(relaxed = true)
        val homeCacheRepository: HomeCacheRepository = mockk(relaxed = true)
        val homeSectionsRepository: HomeSectionsRepository =
            mockk(relaxed = true) {
                every { watchAgain } returns MutableStateFlow(emptyList())
                every { criticsChoice } returns MutableStateFlow(emptyList())
            }
        val homeLayoutPreferencesRepository: HomeLayoutPreferencesRepository =
            mockk(relaxed = true) {
                every { hiddenRows } returns MutableStateFlow(emptySet())
                coEvery { getHiddenRows() } returns emptySet()
            }
        val adminChangeBroadcaster = AdminChangeBroadcaster()
        val persistedDeletedIds = mutableSetOf<String>()
        val deletedItemDao: DeletedItemDao =
            mockk(relaxed = true) {
                coEvery { getAllIds() } answers { persistedDeletedIds.toList() }
                coEvery { insertAll(any()) } answers
                    {
                        firstArg<List<com.makd.afinity.data.database.entities.DeletedItemEntity>>()
                            .forEach { persistedDeletedIds.add(it.itemId) }
                    }
            }
        val deletedItemsRepository = DeletedItemsRepository(deletedItemDao)
        val backgroundWorkQueue = BackgroundWorkQueue()

        fun createRepository(): AppDataRepository =
            AppDataRepository(
                context = context,
                mediaRepository = mediaRepository,
                preferencesRepository = preferencesRepository,
                sessionManager = sessionManager,
                userImageStore = userImageStore,
                jellyfinImageUrlBuilder = imageUrlBuilder,
                watchlistRepository = watchlistRepository,
                liveTvRepository = liveTvRepository,
                genreRepository = genreRepository,
                peopleRepository = peopleRepository,
                serverRepository = serverRepository,
                mediaRefreshBus = mediaRefreshBus,
                mediaChangeManager = mediaChangeManager,
                musicRepository = musicRepository,
                homeCacheRepository = homeCacheRepository,
                homeSectionsRepository = homeSectionsRepository,
                homeLayoutPreferencesRepository = homeLayoutPreferencesRepository,
                itemStore = itemStore,
                adminChangeBroadcaster = adminChangeBroadcaster,
                deletedItemsRepository = deletedItemsRepository,
                databaseRepository = databaseRepository,
                backgroundWorkQueue = backgroundWorkQueue,
                scope = scope,
            )

        fun dispose() {
            scope.cancel()
        }
    }

    @Test
    fun `loadInitialData handles cold load, populates flows, and clears cleanly`() =
        runTest(timeout = 30.seconds) {
            val h = Harness()
            try {
                val libMovies = testCollection(testUuid(10), "Movies", CollectionType.Movies)
                val libShows = testCollection(testUuid(11), "Shows", CollectionType.TvShows)
                val movie1 = testMovie(testUuid(20), "Inception")
                val show1 = testShow(testUuid(21), "Severance")
                val ep1 = testEpisode(id = testUuid(22), seriesId = show1.id, name = "Pilot")

                coEvery { h.homeCacheRepository.getLatestMovies(any(), any()) } returns null
                coEvery { h.mediaRepository.getLibrariesResult() } returns
                    Result.success(listOf(libMovies, libShows))
                coEvery {
                    h.mediaRepository.getLatestMedia(
                        parentId = any(),
                        limit = any(),
                        fields = any(),
                        groupItems = any(),
                        includeItemTypes = any(),
                    )
                } answers
                    {
                        val parentId = firstArg<UUID?>()
                        if (parentId == libMovies.id) listOf(movie1) else listOf(show1)
                    }
                coEvery { h.mediaRepository.getContinueWatching(any()) } returns listOf(ep1)
                coEvery {
                    h.mediaRepository.getNextUp(
                        seriesId = null,
                        limit = any(),
                        fields = any(),
                        enableResumable = any(),
                    )
                } returns listOf(ep1)
                coEvery {
                    h.mediaRepository.getMovies(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                    )
                } returns listOf(movie1)

                val repo = h.createRepository()
                runCurrent()

                repo.loadInitialData()
                runCurrent()

                assertTrue(repo.isInitialDataLoaded.value)
                assertTrue(repo.homeEssentialsReady.value)
                assertEquals(2, repo.libraries.value.size)
                assertEquals(listOf(movie1), repo.latestMovies.value)
                assertEquals(listOf(show1), repo.latestTvSeries.value)

                repo.clearAllData(sessionEnded = true)
                runCurrent()

                assertFalse(repo.isInitialDataLoaded.value)
                assertEquals(0, repo.libraries.value.size)
                assertEquals(0, repo.latestMovies.value.size)
            } finally {
                h.dispose()
            }
        }

    @Test
    fun `cache-hit boot, admin metadata update, and deletion update state and tombstones`() =
        runTest(timeout = 30.seconds) {
            val h = Harness()
            try {
                val cachedMovie = testMovie(testUuid(30), "Cached Old Title")
                val updatedMovie = cachedMovie.copy(name = "Fresh New Title")
                val libMovies = testCollection(testUuid(10), "Movies", CollectionType.Movies)

                coEvery { h.homeCacheRepository.getLatestMovies(any(), any()) } returns
                    listOf(cachedMovie)
                coEvery { h.homeCacheRepository.getLatestShows(any(), any()) } returns emptyList()
                coEvery { h.mediaRepository.restoreCachedLibraries() } returns listOf(libMovies)
                coEvery { h.mediaRepository.getLibrariesResult() } returns
                    Result.success(listOf(libMovies))
                coEvery { h.mediaRepository.getItemById(cachedMovie.id) } returns updatedMovie
                coEvery {
                    h.mediaRepository.getLatestMedia(
                        parentId = any(),
                        limit = any(),
                        fields = any(),
                        groupItems = any(),
                        includeItemTypes = any(),
                    )
                } returns listOf(cachedMovie)

                val repo = h.createRepository()
                runCurrent()

                repo.loadInitialData()
                runCurrent()
                assertTrue(repo.isInitialDataLoaded.value)
                assertEquals("Cached Old Title", repo.latestMovies.value.firstOrNull()?.name)

                h.adminChangeBroadcaster.notifyItemChanged(cachedMovie.id.toString())
                runCurrent()
                assertEquals("Fresh New Title", repo.latestMovies.value.firstOrNull()?.name)
                coVerify { h.homeSectionsRepository.updateItem(updatedMovie) }

                coEvery {
                    h.mediaRepository.getLatestMedia(
                        parentId = any(),
                        limit = any(),
                        fields = any(),
                        groupItems = any(),
                        includeItemTypes = any(),
                    )
                } returns emptyList()
                repo.onItemDeleted(cachedMovie.id.toString())
                runCurrent()
                withContext(Dispatchers.Default) {
                    withTimeout(2_000L) { repo.latestMovies.first { it.isEmpty() } }
                }
                assertEquals(0, repo.latestMovies.value.size)
                assertTrue(
                    com.makd.afinity.util.ItemIds.normalize(cachedMovie.id.toString()) in
                        h.deletedItemsRepository.deletedIds(),
                )
                verify { h.homeSectionsRepository.removeItem(cachedMovie.id.toString()) }
            } finally {
                h.dispose()
            }
        }

    @Test
    fun `latestPodcastSections loads unplayed episodes only from podcast libraries and updates continue listening filled state`() =
        runTest(timeout = 30.seconds) {
            val h = Harness()
            try {
                val podcastLib = testCollection(testUuid(80), "Podcasts", CollectionType.Music)
                val musicLib = testCollection(testUuid(81), "Music", CollectionType.Music)
                val episodeId = testUuid(82)
                val podcastDto =
                    BaseItemDto(
                        id = episodeId,
                        type = BaseItemKind.AUDIO,
                        name = "Unplayed Episode",
                        userData =
                            UserItemDataDto(
                                playbackPositionTicks = 0L,
                                playCount = 0,
                                isFavorite = false,
                                played = false,
                                key = episodeId.toString(),
                                itemId = episodeId,
                            ),
                    )
                val resumedPodcast =
                    testTrack(
                        id = testUuid(83),
                        name = "In Progress",
                        playbackPositionTicks = 500_000L,
                    )

                every { h.mediaRepository.continueListeningPodcasts } returns
                    MutableStateFlow(listOf(resumedPodcast))
                coEvery { h.mediaRepository.getLibrariesResult() } returns
                    Result.success(listOf(podcastLib, musicLib))
                coEvery {
                    h.mediaRepository.getItemsResult(
                        parentId = podcastLib.id,
                        sortBy = any(),
                        sortDescending = any(),
                        limit = any(),
                        includeItemTypes = listOf("AUDIO"),
                        fields = any(),
                        recursive = true,
                        criteria = match { it.isPlayed == false },
                        enableImages = true,
                        enableImageTypes = listOf("PRIMARY"),
                        enableUserData = true,
                    )
                } returns
                    Result.success(
                        BaseItemDtoQueryResult(
                            items = listOf(podcastDto),
                            startIndex = 0,
                            totalRecordCount = 1,
                        ),
                    )

                val repo = h.createRepository()
                runCurrent()
                repo.loadInitialData()
                runCurrent()

                val sections = repo.latestPodcastSections.value
                assertEquals(1, sections.size)
                assertEquals(podcastLib.id, sections.single().first.id)
                assertEquals(listOf(episodeId), sections.single().second.map { it.id })
                coVerify {
                    h.homeSectionsRepository.recordRowFilled(HomeRow.CONTINUE_LISTENING, true)
                }
            } finally {
                h.dispose()
            }
        }

    @Test
    fun `latestPodcastSections and latestAudiobookSections sort newest first and oldest last`() =
        runTest(timeout = 30.seconds) {
            val h = Harness()
            try {
                val podcastLib = testCollection(testUuid(85), "Podcasts", CollectionType.Music)
                val bookLib = testCollection(testUuid(86), "Audiobooks", CollectionType.Books)

                val podOld =
                    BaseItemDto(
                        id = testUuid(87),
                        type = BaseItemKind.AUDIO,
                        name = "Old Pod",
                        dateCreated = java.time.LocalDateTime.of(2026, 10, 1, 10, 0),
                        userData =
                            UserItemDataDto(
                                playbackPositionTicks = 0L,
                                playCount = 0,
                                isFavorite = false,
                                played = false,
                                key = testUuid(87).toString(),
                                itemId = testUuid(87),
                            ),
                    )
                val podNew =
                    BaseItemDto(
                        id = testUuid(88),
                        type = BaseItemKind.AUDIO,
                        name = "New Pod",
                        dateCreated = java.time.LocalDateTime.of(2026, 11, 20, 10, 0),
                        userData =
                            UserItemDataDto(
                                playbackPositionTicks = 0L,
                                playCount = 0,
                                isFavorite = false,
                                played = false,
                                key = testUuid(88).toString(),
                                itemId = testUuid(88),
                            ),
                    )
                val podMid =
                    BaseItemDto(
                        id = testUuid(89),
                        type = BaseItemKind.AUDIO,
                        name = "Mid Pod",
                        dateCreated = java.time.LocalDateTime.of(2026, 11, 5, 10, 0),
                        userData =
                            UserItemDataDto(
                                playbackPositionTicks = 0L,
                                playCount = 0,
                                isFavorite = false,
                                played = false,
                                key = testUuid(89).toString(),
                                itemId = testUuid(89),
                            ),
                    )

                val abOld =
                    BaseItemDto(
                        id = testUuid(90),
                        type = BaseItemKind.AUDIO_BOOK,
                        name = "Old Audiobook",
                        parentId = bookLib.id,
                        dateCreated = java.time.LocalDateTime.of(2026, 9, 1, 10, 0),
                    )
                val abNewPart1 =
                    BaseItemDto(
                        id = testUuid(91),
                        type = BaseItemKind.AUDIO_BOOK,
                        name = "Part 1",
                        album = "Multi-Part Book",
                        indexNumber = 1,
                        parentId = bookLib.id,
                        dateCreated = java.time.LocalDateTime.of(2026, 11, 22, 10, 0),
                    )
                val abNewPart2 =
                    BaseItemDto(
                        id = testUuid(92),
                        type = BaseItemKind.AUDIO_BOOK,
                        name = "Part 2",
                        album = "Multi-Part Book",
                        indexNumber = 2,
                        parentId = bookLib.id,
                        dateCreated = java.time.LocalDateTime.of(2026, 11, 23, 10, 0),
                    )

                coEvery { h.mediaRepository.getLibrariesResult() } returns
                    Result.success(listOf(podcastLib, bookLib))
                coEvery {
                    h.mediaRepository.getItemsResult(
                        parentId = podcastLib.id,
                        sortBy = any(),
                        sortDescending = any(),
                        limit = any(),
                        includeItemTypes = listOf("AUDIO"),
                        fields = any(),
                        recursive = true,
                        criteria = match { it.isPlayed == false },
                        enableImages = true,
                        enableImageTypes = listOf("PRIMARY"),
                        enableUserData = true,
                    )
                } returns
                    Result.success(
                        BaseItemDtoQueryResult(
                            items = listOf(podOld, podNew, podMid),
                            startIndex = 0,
                            totalRecordCount = 3,
                        ),
                    )
                coEvery {
                    h.mediaRepository.getLatestDtos(
                        parentId = bookLib.id,
                        limit = any(),
                        fields = any(),
                        groupItems = false,
                        includeItemTypes = listOf(BaseItemKind.AUDIO_BOOK),
                    )
                } returns listOf(abOld, abNewPart1, abNewPart2)

                val repo = h.createRepository()
                runCurrent()
                repo.loadInitialData()
                runCurrent()

                assertEquals(
                    listOf(testUuid(88), testUuid(89), testUuid(87)),
                    repo.latestPodcastSections.value.single().second.map { it.id },
                )
                assertEquals(
                    listOf("Multi-Part Book", "Old Audiobook"),
                    repo.latestAudiobookSections.value.single().second.map { it.name },
                )
            } finally {
                h.dispose()
            }
        }

    @Test
    fun `refreshPlaybackSections refreshes latestPodcastSections when podcast library exists and row is visible`() =
        runTest(timeout = 30.seconds) {
            val h = Harness()
            try {
                val podcastLib = testCollection(testUuid(95), "Podcasts", CollectionType.Music)
                val firstEp =
                    BaseItemDto(
                        id = testUuid(96),
                        type = BaseItemKind.AUDIO,
                        name = "First Unplayed",
                        dateCreated = java.time.LocalDateTime.of(2026, 11, 20, 10, 0),
                    )
                val secondEp =
                    BaseItemDto(
                        id = testUuid(97),
                        type = BaseItemKind.AUDIO,
                        name = "Backfilled Unplayed",
                        dateCreated = java.time.LocalDateTime.of(2026, 11, 19, 10, 0),
                    )
                var currentItems = listOf(firstEp)

                coEvery { h.mediaRepository.getLibrariesResult() } returns
                    Result.success(listOf(podcastLib))
                coEvery {
                    h.mediaRepository.getItemsResult(
                        parentId = podcastLib.id,
                        sortBy = any(),
                        sortDescending = any(),
                        limit = any(),
                        includeItemTypes = listOf("AUDIO"),
                        fields = any(),
                        recursive = true,
                        criteria = match { it.isPlayed == false },
                        enableImages = true,
                        enableImageTypes = listOf("PRIMARY"),
                        enableUserData = true,
                    )
                } answers
                    {
                        Result.success(
                            BaseItemDtoQueryResult(
                                items = currentItems,
                                startIndex = 0,
                                totalRecordCount = currentItems.size,
                            ),
                        )
                    }

                val repo = h.createRepository()
                runCurrent()
                repo.loadInitialData()
                runCurrent()
                assertEquals(
                    listOf(testUuid(96)),
                    repo.latestPodcastSections.value.single().second.map { it.id },
                )

                currentItems = listOf(secondEp)
                repo.refreshPlaybackSections()
                runCurrent()
                assertEquals(
                    listOf(testUuid(97)),
                    repo.latestPodcastSections.value.single().second.map { it.id },
                )
            } finally {
                h.dispose()
            }
        }
}
