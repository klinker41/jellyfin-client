package com.makd.afinity.ui.home

import android.content.Context
import com.makd.afinity.data.manager.Connectivity
import com.makd.afinity.data.manager.DownloadPermissions
import com.makd.afinity.data.manager.MediaChangeManager
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.models.HeroContentSource
import com.makd.afinity.data.models.HomeRow
import com.makd.afinity.data.models.media.ContinueWatchingOrder
import com.makd.afinity.data.models.media.UserDataPatch
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.auth.AuthRepository
import com.makd.afinity.data.repository.home.HomeLayoutPreferencesRepository
import com.makd.afinity.data.repository.home.HomeSectionsRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.store.ItemStore
import com.makd.afinity.testutil.MainDispatcherRule
import com.makd.afinity.testutil.TestMediaFixtures.testCollection
import com.makd.afinity.testutil.TestMediaFixtures.testEpisode
import com.makd.afinity.testutil.TestMediaFixtures.testMovie
import com.makd.afinity.testutil.TestMediaFixtures.testTrack
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private class Harness {
        val isInitialLoadedFlow = MutableStateFlow(false)
        val homeEssentialsReadyFlow = MutableStateFlow(false)
        val heroItemsFlow =
            MutableStateFlow<List<com.makd.afinity.data.models.media.AfinityItem>>(emptyList())
        val heroLoadedFlow = MutableStateFlow(false)
        val continueWatchingFlow =
            MutableStateFlow<List<com.makd.afinity.data.models.media.AfinityItem>>(emptyList())
        val nextUpFlow =
            MutableStateFlow<List<com.makd.afinity.data.models.media.AfinityEpisode>>(emptyList())
        val continueListeningPodcastsFlow =
            MutableStateFlow<List<com.makd.afinity.data.models.music.AfinityTrack>>(emptyList())
        val latestPodcastSectionsFlow =
            MutableStateFlow<
                List<
                    Pair<
                        com.makd.afinity.data.models.media.AfinityCollection,
                        List<com.makd.afinity.data.models.music.AfinityTrack>,
                    >,
                >,
            >(
                emptyList(),
            )
        val latestMoviesFlow =
            MutableStateFlow<List<com.makd.afinity.data.models.media.AfinityMovie>>(emptyList())
        val librariesFlow =
            MutableStateFlow<List<com.makd.afinity.data.models.media.AfinityCollection>>(
                emptyList(),
            )
        val initialLoadFailedFlow = MutableStateFlow(false)
        val connectivityFlow = MutableStateFlow<Connectivity>(Connectivity.Online)
        val isOfflineFlow = MutableStateFlow(false)
        val filledRowsFlow = MutableStateFlow<Set<HomeRow>>(emptySet())
        val hiddenRowsFlow = MutableStateFlow<Set<HomeRow>>(emptySet())
        val mediaChangesFlow =
            MutableSharedFlow<com.makd.afinity.data.manager.MediaChangeEvent>(
                extraBufferCapacity = 4,
            )
        val itemStore = ItemStore()

        val context: Context =
            mockk(relaxed = true) { every { getString(any()) } returns "Home failed to load" }
        val appDataRepository: AppDataRepository =
            mockk(relaxed = true) {
                every { isInitialDataLoaded } returns isInitialLoadedFlow
                every { homeEssentialsReady } returns homeEssentialsReadyFlow
                every { heroCarouselItems } returns heroItemsFlow
                every { heroLoaded } returns heroLoadedFlow
                every { heroSettings } returns MutableStateFlow(HeroContentSource())
                every { loadedPlaybackRows } returns MutableStateFlow(emptySet())
                every { latestBookSections } returns MutableStateFlow(emptyList())
                every { latestAudiobookSections } returns MutableStateFlow(emptyList())
                every { latestPodcastSections } returns latestPodcastSectionsFlow
                every { latestHomeVideoSections } returns MutableStateFlow(emptyList())
                every { latestMusicVideoSections } returns MutableStateFlow(emptyList())
                every { latestMovies } returns latestMoviesFlow
                every { latestTvSeries } returns MutableStateFlow(emptyList())
                every { getCombineLibrarySectionsFlow() } returns MutableStateFlow(false)
                every { getHomeSortByDateAddedFlow() } returns MutableStateFlow(true)
                every { getMergeContinueWatchingNextUpFlow() } returns MutableStateFlow(false)
                every { getNextUpMaxDaysFlow() } returns MutableStateFlow(0)
                every { separateMovieLibrarySections } returns MutableStateFlow(emptyList())
                every { libraries } returns librariesFlow
                every { separateTvLibrarySections } returns MutableStateFlow(emptyList())
                every { separateMixedLibrarySections } returns MutableStateFlow(emptyList())
                every { genreMovies } returns MutableStateFlow(emptyMap())
                every { genreShows } returns MutableStateFlow(emptyMap())
                every { combinedGenres } returns MutableStateFlow(emptyList())
                every { initialLoadFailed } returns initialLoadFailedFlow
                every { userDataOverlay } returns MutableStateFlow(emptyMap())
            }
        val mediaRepository: MediaRepository =
            mockk(relaxed = true) {
                every { getContinueWatchingFlow() } returns continueWatchingFlow
                every { continueReading } returns MutableStateFlow(emptyList())
                every { continueListening } returns MutableStateFlow(emptyList())
                every { continueListeningPodcasts } returns continueListeningPodcastsFlow
                every { continueWatchingOrder } returns MutableStateFlow(ContinueWatchingOrder())
                every { getNextUpFlow() } returns nextUpFlow
                coEvery { getUpcomingEpisodes(any()) } returns emptyList()
            }
        val offlineModeManager: OfflineModeManager = mockk {
            every { connectivity } returns connectivityFlow
            every { isOffline } returns isOfflineFlow
        }
        val authRepository: AuthRepository = mockk {
            every { currentUser } returns MutableStateFlow(null)
        }
        val mediaChangeManager: MediaChangeManager =
            mockk(relaxed = true) {
                every { mediaChanges } returns mediaChangesFlow
                every { libraryMetadataChanges } returns MutableSharedFlow()
                every { libraryContentChanges } returns MutableSharedFlow()
            }
        val homeSectionsRepository: HomeSectionsRepository =
            mockk(relaxed = true) {
                every { filledRows } returns filledRowsFlow
                every { layout } returns MutableStateFlow(emptyList())
                every { pinnedLayout } returns MutableStateFlow(emptyList())
                every { content } returns MutableStateFlow(emptyMap())
                every { watchAgain } returns MutableStateFlow(emptyList())
                every { watchAgainLoaded } returns MutableStateFlow(false)
                every { popularStudios } returns MutableStateFlow(emptyList())
                every { popularStudiosLoaded } returns MutableStateFlow(false)
                every { criticsChoice } returns MutableStateFlow(emptyList())
                every { criticsChoiceLoaded } returns MutableStateFlow(false)
                every { franchises } returns MutableStateFlow(emptyList())
                every { franchisesLoaded } returns MutableStateFlow(false)
            }
        val homeLayoutPreferencesRepository: HomeLayoutPreferencesRepository = mockk {
            every { hiddenRows } returns hiddenRowsFlow
        }
        val downloadPermissions: DownloadPermissions = mockk {
            every { isAllowedByServer } returns MutableStateFlow(true)
            every { isAllowedOnNetwork } returns MutableStateFlow(true)
        }

        val viewModel =
            HomeViewModel(
                context = context,
                appDataRepository = appDataRepository,
                userDataRepository = mockk(relaxed = true),
                databaseRepository = mockk(relaxed = true),
                downloadRepository = mockk(relaxed = true),
                absDownloadRepository = mockk(relaxed = true),
                storageLocationProvider = mockk(relaxed = true),
                offlineModeManager = offlineModeManager,
                authRepository = authRepository,
                mediaRepository = mediaRepository,
                mediaChangeManager = mediaChangeManager,
                itemUserDataDelegate = mockk(relaxed = true),
                homeSectionsRepository = homeSectionsRepository,
                homeLayoutPreferencesRepository = homeLayoutPreferencesRepository,
                downloadPermissions = downloadPermissions,
                itemStore = itemStore,
                bookMetadataStore = mockk(relaxed = true),
                audiobookDownloadStore = mockk(relaxed = true),
            )
    }

    @Test
    fun `initial load clears loading flag and triggers ensureLayout when online`() = runTest {
        val harness = Harness()
        val movie = testMovie(id = testUuid(1), played = false)
        val watchedMovie = testMovie(id = testUuid(2), played = true)
        val hero = testMovie(id = testUuid(3))

        harness.heroItemsFlow.value = listOf(hero)
        harness.heroLoadedFlow.value = true
        harness.latestMoviesFlow.value = listOf(movie, watchedMovie)
        harness.librariesFlow.value = listOf(testCollection())
        harness.homeEssentialsReadyFlow.value = true
        harness.isInitialLoadedFlow.value = true
        advanceUntilIdle()

        val state = harness.viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.heroLoaded)
        assertEquals(listOf(hero), state.heroCarouselItems)
        assertEquals(listOf(movie), state.latestMovies)
        coVerify { harness.homeSectionsRepository.ensureLayout() }
    }

    @Test
    fun `itemStore overlay updates projected HomeUiState items`() = runTest {
        val harness = Harness()
        val movie = testMovie(id = testUuid(10), favorite = false, played = false)
        harness.continueWatchingFlow.value = listOf(movie)
        harness.isInitialLoadedFlow.value = true
        advanceUntilIdle()

        assertFalse(harness.viewModel.uiState.value.continueWatching.first().favorite)

        harness.itemStore.applyPatch(movie.id, UserDataPatch(favorite = true, played = true))
        advanceUntilIdle()

        val projected = harness.viewModel.uiState.value.continueWatching.first()
        assertTrue(projected.favorite)
        assertTrue(projected.played)
    }

    @Test
    fun `initialLoadFailed sets error when online with no libraries and session clear resets`() =
        runTest {
            val harness = Harness()
            val hero = testMovie(id = testUuid(7))
            harness.heroItemsFlow.value = listOf(hero)
            harness.isInitialLoadedFlow.value = true
            advanceUntilIdle()
            assertEquals(listOf(hero), harness.viewModel.uiState.value.heroCarouselItems)

            harness.initialLoadFailedFlow.value = true
            advanceUntilIdle()
            assertEquals("Home failed to load", harness.viewModel.uiState.value.error)

            harness.isOfflineFlow.value = true
            harness.connectivityFlow.value = Connectivity.ForcedOffline
            advanceUntilIdle()
            assertNull(harness.viewModel.uiState.value.error)
            assertTrue(harness.viewModel.uiState.value.isOffline)

            harness.isInitialLoadedFlow.value = false
            advanceUntilIdle()
            assertTrue(harness.viewModel.uiState.value.heroCarouselItems.isEmpty())
        }

    @Test
    fun `episode selection works independently`() = runTest {
        val harness = Harness()
        val ep = testEpisode(id = testUuid(88))

        harness.viewModel.selectEpisode(ep)
        advanceUntilIdle()
        assertEquals(ep.id, harness.viewModel.selectedEpisode.value?.id)

        harness.viewModel.clearSelectedEpisode()
        assertNull(harness.viewModel.selectedEpisode.value)
    }

    @Test
    fun `podcast continue listening and latest podcasts project itemStore updates`() = runTest {
        val harness = Harness()
        val podcastLib =
            testCollection(
                id = testUuid(90),
                name = "Podcasts",
                type = com.makd.afinity.data.models.common.CollectionType.Music,
            )
        val ep = testTrack(id = testUuid(91), name = "Pod Ep", playbackPositionTicks = 300_000L)

        harness.continueListeningPodcastsFlow.value = listOf(ep)
        harness.latestPodcastSectionsFlow.value = listOf(podcastLib to listOf(ep))
        harness.isInitialLoadedFlow.value = true
        advanceUntilIdle()

        assertEquals(1, harness.viewModel.uiState.value.continueListeningPodcasts.size)
        assertEquals(1, harness.viewModel.uiState.value.latestPodcastSections.size)

        harness.itemStore.applyPatch(
            ep.id,
            UserDataPatch(played = true, playbackPositionTicks = 0L),
        )
        advanceUntilIdle()

        val projectedCont = harness.viewModel.uiState.value.continueListeningPodcasts.single()
        val projectedLatest =
            harness.viewModel.uiState.value.latestPodcastSections.single().second.single()
        assertTrue(projectedCont.played)
        assertTrue(projectedLatest.played)
        assertEquals(0L, projectedCont.playbackPositionTicks)
    }
}
