package com.makd.afinity.ui.music.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.makd.afinity.data.manager.AdminChangeBroadcaster
import com.makd.afinity.data.manager.DownloadPermissions
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.music.MusicFilterOptions
import com.makd.afinity.data.models.music.MusicFilters
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.repository.music.MadeForYouCache
import com.makd.afinity.data.repository.music.MusicRepository
import com.makd.afinity.data.store.ItemStore
import com.makd.afinity.testutil.MainDispatcherRule
import com.makd.afinity.testutil.TestMediaFixtures.testAlbum
import com.makd.afinity.testutil.TestMediaFixtures.testCollection
import com.makd.afinity.testutil.TestMediaFixtures.testTrack
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MusicLibraryViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private inner class Harness {
        val libId = testUuid(1)
        val musicLib = testCollection(id = libId, name = "Music", type = CollectionType.Music)
        val playlistsFlow =
            MutableStateFlow<List<com.makd.afinity.data.models.music.AfinityPlaylist>>(emptyList())
        val downloadsFlow = MutableStateFlow<List<DownloadInfo>>(emptyList())

        val musicRepository: MusicRepository =
            mockk(relaxed = true) {
                every { getPlaylistsFlow(any()) } returns playlistsFlow
                coEvery { getMusicFilterOptions(libId, any()) } returns MusicFilterOptions()
                coEvery { getRecentlyPlayedTracks(any()) } returns emptyList()
                coEvery { getRecentlyAddedAlbums(any()) } returns emptyList()
                coEvery { getMostPlayedTracks(any(), any()) } returns emptyList()
                coEvery { getMusicGenres(any(), any()) } returns emptyList()
                coEvery { getRandomAlbums(any()) } returns emptyList()
                coEvery { getRandomTracks(any()) } returns emptyList()
                coEvery { getArtistAlbums(any(), any(), any()) } returns emptyList()
                coEvery { getAlbumTracks(any()) } returns emptyList()
                coEvery { getLibraryRuntimeTicks(libId) } returns 5_000_000L
                coEvery { getTrackCount(libId, any()) } returns 12
                coEvery { getTracks(any(), any(), any(), any(), any(), any()) } returns emptyList()
            }
        val appDataRepository: AppDataRepository =
            mockk(relaxed = true) {
                every { userProfileImageUrl } returns MutableStateFlow(null)
                every { userName } returns MutableStateFlow("Alex")
                every { libraries } returns MutableStateFlow(listOf(musicLib))
            }
        val downloadRepository: DownloadRepository =
            mockk(relaxed = true) {
                every { getAllDownloadsFlow() } returns downloadsFlow
                coEvery { startDownload(any(), any(), any(), any(), any()) } returns
                    Result.success(testUuid(99))
            }
        val preferencesRepository: PreferencesRepository =
            mockk(relaxed = true) { coEvery { getStringPreference(any()) } returns null }
        val adminChangeBroadcaster = AdminChangeBroadcaster()
        val madeForYouCache: MadeForYouCache = mockk(relaxed = true)
        val downloadPermissions: DownloadPermissions = mockk {
            every { isAllowedByServer } returns MutableStateFlow(true)
            every { isAllowedOnNetwork } returns MutableStateFlow(true)
        }
        val itemStore = ItemStore()
        private val store = ViewModelStore()

        fun createViewModel(): MusicLibraryViewModel {
            val factory =
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        MusicLibraryViewModel(
                            musicRepository = musicRepository,
                            appDataRepository = appDataRepository,
                            downloadRepository = downloadRepository,
                            preferencesRepository = preferencesRepository,
                            adminChangeBroadcaster = adminChangeBroadcaster,
                            madeForYouCache = madeForYouCache,
                            downloadPermissions = downloadPermissions,
                            itemStore = itemStore,
                            savedStateHandle =
                                SavedStateHandle(
                                    mapOf(
                                        "libraryId" to libId.toString(),
                                        "libraryName" to "Music",
                                    ),
                                ),
                        )
                            as T
                }
            return ViewModelProvider(store, factory)[MusicLibraryViewModel::class.java]
        }

        fun dispose() {
            store.clear()
        }
    }

    @Test
    fun `home sections load, sort and filter updates persist prefs, and track actions work`() =
        runTest(timeout = 30.seconds) {
            val h = Harness()
            try {
                val track1 = testTrack(testUuid(10), "Intro Track")
                val album1 = testAlbum(testUuid(11), "First Album")
                coEvery { h.musicRepository.getRecentlyAddedAlbums(any()) } returns listOf(album1)
                coEvery { h.musicRepository.getRecentlyPlayedTracks(any()) } returns listOf(track1)
                coEvery {
                    h.musicRepository.getTracks(h.libId, any(), any(), any(), any(), any())
                } returns listOf(track1)

                val vm = h.createViewModel()
                advanceUntilIdle()

                assertEquals(listOf(album1), vm.uiState.value.latestAlbums)
                assertEquals(listOf(track1), vm.uiState.value.recentlyPlayedTracks)

                // Sort & letter filters update state & persist preferences
                vm.setTrackSort(MusicSortField.PlayCount, descending = true)
                vm.setAlbumSort(MusicSortField.ReleaseDate, descending = true)
                vm.setTrackFilters(MusicFilters(favoritesOnly = true))
                vm.setAlbumFilters(MusicFilters(unplayedOnly = true))
                vm.filterAlbumsByLetter("A")
                assertEquals("A", vm.albumLetterFilter.value)
                vm.filterAlbumsByLetter("A")
                assertNull(vm.albumLetterFilter.value)
                advanceUntilIdle()

                assertEquals(MusicSortField.PlayCount, vm.trackSortField.value)
                assertTrue(vm.trackSortDescending.value)
                assertEquals(MusicSortField.ReleaseDate, vm.albumSortField.value)
                coVerify(atLeast = 1) {
                    h.preferencesRepository.setStringPreference(
                        "music_browse_prefs_${h.libId}",
                        any(),
                    )
                }

                // getAllFilteredTracks forwards current sort & filter state
                val allFiltered = vm.getAllFilteredTracks()
                assertEquals(listOf(track1), allFiltered)

                // toggleTrackFavorite updates override on success and rolls back on failure
                coEvery { h.musicRepository.setFavorite(track1.id, true) } returns Unit
                vm.toggleTrackFavorite(track1, currentFavorite = false)
                advanceUntilIdle()
                assertEquals(true, vm.uiState.value.trackFavoriteOverrides[track1.id])
                coVerify { h.appDataRepository.updateTrackFavoriteStatus(track1, true) }

                coEvery { h.musicRepository.setFavorite(track1.id, false) } throws
                    RuntimeException("Network down")
                vm.toggleTrackFavorite(track1, currentFavorite = true)
                advanceUntilIdle()
                assertNull(vm.uiState.value.trackFavoriteOverrides[track1.id])

                // Track download & cancel delegate to DownloadRepository
                vm.downloadTrack(track1.id)
                advanceUntilIdle()
                coVerify { h.downloadRepository.startDownload(track1.id, "", any(), any(), any()) }
            } finally {
                h.dispose()
            }
        }
}
