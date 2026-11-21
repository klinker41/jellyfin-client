package com.makd.afinity.ui.search

import android.content.Context
import com.makd.afinity.data.manager.DownloadPermissions
import com.makd.afinity.data.manager.MediaChangeManager
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.music.MusicSearchResults
import com.makd.afinity.data.models.user.AfinityUserDataDto
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.JellyseerrRepository
import com.makd.afinity.data.repository.auth.AuthRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.music.MusicRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.data.store.ItemStore
import com.makd.afinity.testutil.MainDispatcherRule
import com.makd.afinity.testutil.TestMediaFixtures.testAlbum
import com.makd.afinity.testutil.TestMediaFixtures.testArtist
import com.makd.afinity.testutil.TestMediaFixtures.testCollection
import com.makd.afinity.testutil.TestMediaFixtures.testEpisode
import com.makd.afinity.testutil.TestMediaFixtures.testTrack
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import com.makd.afinity.ui.item.delegates.ItemUserDataDelegate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private class Harness {
        val isInitialLoadedFlow = MutableStateFlow(false)
        val librariesFlow = MutableStateFlow<List<AfinityCollection>>(emptyList())
        val userDataOverlayFlow = MutableStateFlow<Map<UUID, AfinityUserDataDto>>(emptyMap())
        val lastUserDataChangedAtFlow = MutableStateFlow(0L)
        val jellyseerrAuthFlow = MutableStateFlow(false)
        val absAuthFlow = MutableStateFlow(false)
        val mediaChangesFlow =
            MutableSharedFlow<com.makd.afinity.data.manager.MediaChangeEvent>(
                extraBufferCapacity = 4,
            )
        val itemStore = ItemStore()

        val emptyQueryResult: BaseItemDtoQueryResult = mockk { every { items } returns emptyList() }

        val context: Context = mockk(relaxed = true)
        val mediaRepository: MediaRepository =
            mockk(relaxed = true) {
                every { getBaseUrl() } returns "https://jf.example.com"
                coEvery { getGenres(any(), any(), any()) } returns listOf("Action", "Sci-Fi")
                coEvery { getItem(any(), any()) } returns null
                coEvery {
                    getItemsResult(
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
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                    )
                } returns Result.success(emptyQueryResult)
                coEvery {
                    getItems(
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
                        any(),
                        any(),
                        any(),
                        any(),
                    )
                } returns emptyQueryResult
            }
        val jellyseerrRepository: JellyseerrRepository =
            mockk(relaxed = true) { every { isAuthenticated } returns jellyseerrAuthFlow }
        val appDataRepository: AppDataRepository =
            mockk(relaxed = true) {
                every { isInitialDataLoaded } returns isInitialLoadedFlow
                every { libraries } returns librariesFlow
                every { userDataOverlay } returns userDataOverlayFlow
                every { lastUserDataChangedAt } returns lastUserDataChangedAtFlow
            }
        val audiobookshelfRepository: AudiobookshelfRepository =
            mockk(relaxed = true) {
                every { isAuthenticated } returns absAuthFlow
                every { currentConfig } returns MutableStateFlow(null)
                every { getLibrariesFlow() } returns MutableStateFlow(emptyList())
            }
        val mediaChangeManager: MediaChangeManager = mockk {
            every { mediaChanges } returns mediaChangesFlow
        }
        val downloadRepository: DownloadRepository =
            mockk(relaxed = true) { coEvery { getDownloadByItemId(any()) } returns null }
        val userDataRepository: UserDataRepository = mockk(relaxed = true)
        val authRepository: AuthRepository =
            mockk(relaxed = true) { every { currentUser } returns MutableStateFlow(null) }
        val databaseRepository: DatabaseRepository = mockk(relaxed = true)
        val itemUserDataDelegate: ItemUserDataDelegate = mockk(relaxed = true)
        val musicRepository: MusicRepository =
            mockk(relaxed = true) {
                coEvery { searchMusic(any(), any()) } returns
                    MusicSearchResults(emptyList(), emptyList(), emptyList(), emptyList())
            }
        val downloadPermissions: DownloadPermissions = mockk {
            every { isAllowedByServer } returns MutableStateFlow(true)
            every { isAllowedOnNetwork } returns MutableStateFlow(true)
        }

        val viewModel =
            SearchViewModel(
                context = context,
                mediaRepository = mediaRepository,
                jellyseerrRepository = jellyseerrRepository,
                appDataRepository = appDataRepository,
                audiobookshelfRepository = audiobookshelfRepository,
                mediaChangeManager = mediaChangeManager,
                downloadRepository = downloadRepository,
                userDataRepository = userDataRepository,
                authRepository = authRepository,
                databaseRepository = databaseRepository,
                itemUserDataDelegate = itemUserDataDelegate,
                musicRepository = musicRepository,
                downloadPermissions = downloadPermissions,
                itemStore = itemStore,
            )
    }

    @Test
    fun `debounces search queries and skips short queries`() = runTest {
        val harness = Harness()
        harness.isInitialLoadedFlow.value = true
        advanceUntilIdle()

        harness.viewModel.updateSearchQuery("D")
        advanceTimeBy(400)
        runCurrent()
        coVerify(exactly = 0) {
            harness.mediaRepository.getItemsResult(
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
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }

        harness.viewModel.updateSearchQuery("Dune")
        advanceTimeBy(350)
        advanceUntilIdle()
        withContext(Dispatchers.Default) {
            withTimeout(2_000L) {
                while (
                    harness.viewModel.uiState.value.isSearching ||
                        harness.viewModel.uiState.value.isEpisodeSearching
                ) {
                    delay(5)
                }
            }
        }
        advanceUntilIdle()

        coVerify(atLeast = 1) {
            harness.mediaRepository.getItemsResult(
                parentId = any(),
                collectionTypes = any(),
                sortBy = any(),
                sortDescending = any(),
                limit = any(),
                startIndex = any(),
                searchTerm = "Dune",
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

        harness.viewModel.updateSearchQuery("")
        advanceUntilIdle()
        assertTrue(harness.viewModel.uiState.value.searchResults.isEmpty())
        assertNull(harness.viewModel.uiState.value.musicSearchResults)
    }

    @Test
    fun `selectLibrary updates allowed filter and clearing resets state on logout`() = runTest {
        val harness = Harness()
        val moviesLib =
            testCollection(id = testUuid(1), name = "Movies", type = CollectionType.Movies)
        val musicLib = testCollection(id = testUuid(2), name = "Music", type = CollectionType.Music)
        harness.librariesFlow.value = listOf(moviesLib, musicLib)
        harness.isInitialLoadedFlow.value = true
        advanceUntilIdle()

        assertEquals(listOf(moviesLib, musicLib), harness.viewModel.uiState.value.libraries)

        harness.viewModel.selectFilter(SearchFilter.TV_SHOWS)
        harness.viewModel.selectLibrary(moviesLib)
        advanceUntilIdle()
        assertEquals(SearchFilter.ALL, harness.viewModel.uiState.value.selectedFilter)

        harness.viewModel.selectLibrary(musicLib)
        advanceUntilIdle()
        assertEquals(SearchFilter.MUSIC, harness.viewModel.uiState.value.selectedFilter)

        harness.isInitialLoadedFlow.value = false
        advanceUntilIdle()
        assertEquals("", harness.viewModel.uiState.value.searchQuery)
        assertTrue(harness.viewModel.uiState.value.libraries.isEmpty())
    }

    @Test
    fun `music search and optimistic track favorite toggling with failure rollback`() = runTest {
        val harness = Harness()
        val track = testTrack(id = testUuid(50), favorite = false)
        val musicResults =
            MusicSearchResults(
                tracks = listOf(track),
                albums = listOf(testAlbum()),
                artists = listOf(testArtist()),
                playlists = emptyList(),
            )
        val gate = CompletableDeferred<Unit>()
        coEvery { harness.musicRepository.searchMusic("daft", null) } returns musicResults
        coEvery { harness.musicRepository.setFavorite(track.id, true) } coAnswers
            {
                gate.await()
                throw RuntimeException("offline")
            }

        harness.viewModel.selectFilter(SearchFilter.MUSIC)
        harness.viewModel.updateSearchQuery("daft")
        harness.viewModel.performMusicSearch()
        advanceUntilIdle()

        assertEquals(musicResults, harness.viewModel.uiState.value.musicSearchResults)

        harness.viewModel.toggleTrackFavorite(track.id)
        runCurrent()
        assertTrue(harness.viewModel.uiState.value.musicSearchResults!!.tracks.first().favorite)

        gate.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { harness.musicRepository.setFavorite(track.id, true) }
        assertFalse(harness.viewModel.uiState.value.musicSearchResults!!.tracks.first().favorite)
    }

    @Test
    fun `episode selection delegates state updates cleanly`() = runTest {
        val harness = Harness()
        val episode = testEpisode(id = testUuid(300))

        harness.viewModel.selectEpisode(episode)
        advanceUntilIdle()
        assertEquals(episode.id, harness.viewModel.selectedEpisode.value?.id)

        harness.viewModel.clearSelectedEpisode()
        assertNull(harness.viewModel.selectedEpisode.value)
    }
}
