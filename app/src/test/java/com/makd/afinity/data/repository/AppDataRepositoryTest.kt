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
}
