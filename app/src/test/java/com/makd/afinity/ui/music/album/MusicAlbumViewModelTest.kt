package com.makd.afinity.ui.music.album

import androidx.lifecycle.SavedStateHandle
import com.makd.afinity.data.manager.AdminChangeBroadcaster
import com.makd.afinity.data.manager.DownloadPermissions
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.models.player.MusicQuality
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.repository.lastfm.LastFmRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.music.MusicRepository
import com.makd.afinity.data.store.ItemStore
import com.makd.afinity.player.music.PodcastProgressTracker
import com.makd.afinity.testutil.MainDispatcherRule
import com.makd.afinity.testutil.TestMediaFixtures.testAlbum
import com.makd.afinity.testutil.TestMediaFixtures.testTrack
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import com.makd.afinity.util.NetworkConnectivityMonitor
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MusicAlbumViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private fun createViewModel(
        albumId: UUID,
        isPodcast: Boolean,
    ): MusicAlbumViewModel {
        val album = testAlbum(id = albumId).copy(artistId = null, artist = null)
        val tracks = listOf(testTrack(id = testUuid(501)).copy(albumId = albumId))
        val musicRepository: MusicRepository =
            mockk(relaxed = true) {
                coEvery { getAlbumById(albumId) } returns album
                coEvery { getAlbumTracks(albumId) } returns tracks
                coEvery { getSimilarAlbums(any(), any(), any()) } returns emptyList()
            }
        val downloadRepository: DownloadRepository =
            mockk(relaxed = true) { every { getAllDownloadsFlow() } returns flowOf(emptyList()) }
        val adminChangeBroadcaster: AdminChangeBroadcaster =
            mockk(relaxed = true) { every { changes } returns MutableSharedFlow() }
        val downloadPermissions: DownloadPermissions =
            mockk(relaxed = true) {
                every { isAllowedByServer } returns MutableStateFlow(true)
                every { isAllowedOnNetwork } returns MutableStateFlow(true)
            }
        val offlineModeManager: OfflineModeManager =
            mockk(relaxed = true) {
                every { isOffline } returns MutableStateFlow(false)
                coEvery { isCurrentlyOffline() } returns false
                coEvery { isInternetAvailable() } returns true
            }
        val networkMonitor: NetworkConnectivityMonitor =
            mockk(relaxed = true) { every { isNetworkAvailable } returns MutableStateFlow(true) }
        val podcastProgressTracker: PodcastProgressTracker =
            mockk(relaxed = true) { coEvery { isPodcastAlbum(albumId) } returns isPodcast }
        val preferencesRepository: PreferencesRepository =
            mockk(relaxed = true) {
                every { getMusicDownloadQualityFlow() } returns
                    flowOf(MusicQuality.ORIGINAL_BITRATE)
            }

        return MusicAlbumViewModel(
            musicRepository = musicRepository,
            downloadRepository = downloadRepository,
            adminChangeBroadcaster = adminChangeBroadcaster,
            appDataRepository = mockk<AppDataRepository>(relaxed = true),
            downloadPermissions = downloadPermissions,
            itemStore = ItemStore(),
            lastFmRepository = mockk<LastFmRepository>(relaxed = true),
            offlineModeManager = offlineModeManager,
            networkMonitor = networkMonitor,
            mediaRepository = mockk<MediaRepository>(relaxed = true),
            podcastProgressTracker = podcastProgressTracker,
            preferencesRepository = preferencesRepository,
            savedStateHandle = SavedStateHandle(mapOf("albumId" to albumId.toString())),
        )
    }

    @Test
    fun `load sets isPodcast true when album belongs to a podcast library`() = runTest {
        val albumId = testUuid(610)
        val vm = createViewModel(albumId = albumId, isPodcast = true)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.isPodcast)
        assertEquals(1, vm.uiState.value.tracks.size)
    }

    @Test
    fun `load sets isPodcast false for non-podcast music albums`() = runTest {
        val albumId = testUuid(611)
        val vm = createViewModel(albumId = albumId, isPodcast = false)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.isPodcast)
        assertEquals(1, vm.uiState.value.tracks.size)
    }
}
