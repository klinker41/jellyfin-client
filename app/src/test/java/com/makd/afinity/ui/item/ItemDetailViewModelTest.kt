package com.makd.afinity.ui.item

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.makd.afinity.data.manager.AdminChange
import com.makd.afinity.data.manager.AdminChangeBroadcaster
import com.makd.afinity.data.manager.Connectivity
import com.makd.afinity.data.manager.DownloadPermissions
import com.makd.afinity.data.manager.MediaChangeEvent
import com.makd.afinity.data.manager.MediaChangeManager
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.manager.PlaybackEvent
import com.makd.afinity.data.manager.PlaybackStateManager
import com.makd.afinity.data.manager.Session
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.admin.AdminPermissionDeniedException
import com.makd.afinity.data.models.admin.AdminRequestStillRunningException
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.models.user.AfinityUserDataDto
import com.makd.afinity.data.models.user.User
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.admin.AdminRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.data.store.ItemStore
import com.makd.afinity.data.store.QueryInvalidator
import com.makd.afinity.data.store.QueryKey
import com.makd.afinity.testutil.MainDispatcherRule
import com.makd.afinity.testutil.TestMediaFixtures.testMovie
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import com.makd.afinity.ui.item.components.shared.MediaSourceOption
import com.makd.afinity.ui.item.delegates.ItemUserDataDelegate
import com.makd.afinity.util.NetworkConnectivityMonitor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ItemDetailViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private class Harness(
        itemId: UUID? = testUuid(1),
        itemType: String? = "MOVIE",
        offline: Boolean = true,
        isAdmin: Boolean = false,
        canManageCollections: Boolean = false,
        canDeleteMedia: Boolean = false,
    ) {
        val userId = testUuid(77)
        val server = Server(id = "srv-1", name = "Home", address = "https://jf.example.com")
        val sessionFlow =
            MutableStateFlow<Session?>(
                Session(
                    serverId = "srv-1",
                    userId = userId,
                    serverUrl = "https://jf.example.com",
                    server = server,
                    isAdmin = isAdmin,
                    canManageCollections = canManageCollections,
                    canDeleteMedia = canDeleteMedia,
                ),
            )
        val connectivityFlow =
            MutableStateFlow<Connectivity>(
                if (offline) Connectivity.ForcedOffline else Connectivity.Online,
            )
        val isOfflineFlow = MutableStateFlow(offline)
        val isNetworkAvailableFlow = MutableStateFlow(!offline)
        val allDownloadsFlow = MutableStateFlow<List<DownloadInfo>>(emptyList())
        val itemStore = ItemStore()

        val context: Context =
            mockk(relaxed = true) { every { getString(any()) } returns "Error loading item" }
        val appDataRepository: AppDataRepository =
            mockk(relaxed = true) {
                every { lastResyncAt } returns MutableStateFlow(0L)
                every { lastUserDataChangedAt } returns MutableStateFlow(0L)
                every { userDataOverlay } returns
                    MutableStateFlow(emptyMap<UUID, AfinityUserDataDto>())
            }
        val userDataRepository: UserDataRepository =
            mockk(relaxed = true) {
                coEvery { markWatched(any()) } returns true
                coEvery { markUnwatched(any()) } returns true
            }
        val mediaRepository: MediaRepository =
            mockk(relaxed = true) {
                every { getBaseUrl() } returns "https://jf.example.com"
                coEvery { getItemDetail(any()) } returns null
                coEvery { getBoxSetsContaining(any(), any()) } returns emptyList()
            }
        val sessionManager: SessionManager =
            mockk(relaxed = true) { every { currentSession } returns sessionFlow }
        val offlineModeManager: OfflineModeManager =
            mockk(relaxed = true) {
                every { connectivity } returns connectivityFlow
                every { isOffline } returns isOfflineFlow
                coEvery { isCurrentlyOffline() } answers { isOfflineFlow.value }
                coEvery { isInternetAvailable() } answers { isNetworkAvailableFlow.value }
            }
        val databaseRepository: DatabaseRepository =
            mockk(relaxed = true) {
                coEvery { getMovie(any(), any()) } returns null
                coEvery { getShow(any(), any()) } returns null
                coEvery { getEpisode(any(), any()) } returns null
            }
        val downloadRepository: DownloadRepository =
            mockk(relaxed = true) {
                every { getAllDownloadsFlow() } returns allDownloadsFlow
                coEvery { getDownloadByItemId(any()) } returns null
            }
        val adminChangeBroadcaster: AdminChangeBroadcaster = mockk {
            every { changes } returns MutableSharedFlow<AdminChange>()
            every { itemDeleted } returns MutableSharedFlow()
        }
        val adminRepository: AdminRepository = mockk(relaxed = true)
        val mediaChangeManager: MediaChangeManager =
            mockk(relaxed = true) {
                every { mediaChanges } returns MutableSharedFlow<MediaChangeEvent>()
                every { batches } returns MutableSharedFlow()
            }
        val playbackStateManager: PlaybackStateManager = mockk {
            every { playbackEvents } returns MutableSharedFlow<PlaybackEvent>()
        }
        val queryInvalidator: QueryInvalidator =
            mockk(relaxed = true) {
                every { invalidations } returns MutableSharedFlow<Set<QueryKey>>()
            }
        val networkMonitor: NetworkConnectivityMonitor = mockk {
            every { isNetworkAvailable } returns isNetworkAvailableFlow
        }
        val downloadPermissions: DownloadPermissions = mockk {
            every { isAllowedByServer } returns MutableStateFlow(true)
            every { isAllowedOnNetwork } returns MutableStateFlow(true)
        }
        val preferencesRepository: PreferencesRepository =
            mockk(relaxed = true) { coEvery { getVideoDownloadQuality() } returns 0 }
        val itemUserDataDelegate =
            ItemUserDataDelegate(
                userDataRepository = userDataRepository,
                mediaChangeManager = mediaChangeManager,
                appDataRepository = appDataRepository,
                watchlistRepository = mockk(relaxed = true),
                mediaRefreshBus = mockk(relaxed = true),
                seerrWatchlistMirror = mockk(relaxed = true),
            )
        val savedStateHandle =
            SavedStateHandle(
                buildMap {
                    if (itemId != null) put("itemId", itemId.toString())
                    if (itemType != null) put("itemType", itemType)
                },
            )

        fun createViewModel() =
            ItemDetailViewModel(
                context = context,
                appDataRepository = appDataRepository,
                userDataRepository = userDataRepository,
                mediaRepository = mediaRepository,
                sessionManager = sessionManager,
                itemMetadataFetcher = mockk(relaxed = true),
                downloadRepository = downloadRepository,
                databaseRepository = databaseRepository,
                offlineModeManager = offlineModeManager,
                authRepository =
                    mockk(relaxed = true) {
                        every { currentUser } returns
                            MutableStateFlow(User(id = userId, name = "Alex", serverId = "srv-1"))
                    },
                playbackStateManager = playbackStateManager,
                adminChangeBroadcaster = adminChangeBroadcaster,
                adminRepository = adminRepository,
                mediaChangeManager = mediaChangeManager,
                serverRepository = mockk(relaxed = true),
                itemDownloadDelegate = mockk(relaxed = true),
                itemUserDataDelegate = itemUserDataDelegate,
                preferencesRepository = preferencesRepository,
                storageLocationProvider = mockk(relaxed = true),
                networkMonitor = networkMonitor,
                downloadPermissions = downloadPermissions,
                itemStore = itemStore,
                queryInvalidator = queryInvalidator,
                wikidataAwardsRepository = mockk(relaxed = true),
                externalTitlesRepository = mockk(relaxed = true),
                themeMusicPlayer = mockk(relaxed = true),
                savedStateHandle = savedStateHandle,
            )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `missing itemId in SavedStateHandle throws IllegalArgumentException`() {
        Harness(itemId = null).createViewModel()
    }

    @Test
    fun `offline load resolves cached movie and supports watched favorite watchlist toggles`() =
        runTest {
            val movieId = testUuid(42)
            val movie = testMovie(id = movieId, played = false, favorite = false)
            val harness = Harness(itemId = movieId, offline = true, isAdmin = true)
            coEvery { harness.databaseRepository.getMovie(movieId, harness.userId) } returns movie

            val viewModel = harness.createViewModel()
            advanceUntilIdle()

            assertTrue(viewModel.isAdmin.value)
            assertTrue(viewModel.mayDeleteMedia.value)
            assertEquals(movie, viewModel.uiState.value.item)
            assertFalse(viewModel.uiState.value.isLoading)

            viewModel.toggleWatched()
            advanceUntilIdle()
            coVerify { harness.userDataRepository.markWatched(movieId) }
            assertTrue((viewModel.uiState.value.item as AfinityMovie).played)

            coEvery { harness.userDataRepository.addToFavorites(movieId) } returns true
            viewModel.toggleFavorite()
            advanceUntilIdle()
            coVerify { harness.userDataRepository.addToFavorites(movieId) }
            assertTrue((viewModel.uiState.value.item as AfinityMovie).favorite)

            coEvery { harness.userDataRepository.setLike(movieId, true) } returns true
            viewModel.toggleWatchlist()
            advanceUntilIdle()
            coVerify { harness.userDataRepository.setLike(movieId, true) }
            assertTrue((viewModel.uiState.value.item as AfinityMovie).liked)
        }

    @Test
    fun `missing item sets error and media source selection updates state`() = runTest {
        val movieId = testUuid(43)
        val harness = Harness(itemId = movieId, offline = false)
        val viewModel = harness.createViewModel()
        advanceUntilIdle()

        assertEquals("Error loading item", viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.item)

        val sourceOpt =
            MediaSourceOption(
                id = "src-1",
                name = "4K HDR",
                quality = "4K",
                codec = "HEVC",
                size = 1024L,
                isDefault = true,
            )
        viewModel.selectMediaSource(sourceOpt)
        assertEquals(sourceOpt, viewModel.selectedMediaSource.value)
    }

    @Test
    fun `toDeleteOutcome maps admin result exceptions cleanly`() {
        assertEquals(DeleteOutcome.DELETED, Result.success(Unit).toDeleteOutcome())
        assertEquals(
            DeleteOutcome.STILL_RUNNING,
            Result.failure<Unit>(AdminRequestStillRunningException(Exception("running")))
                .toDeleteOutcome(),
        )
        assertEquals(
            DeleteOutcome.NOT_ALLOWED,
            Result.failure<Unit>(AdminPermissionDeniedException(Exception("denied")))
                .toDeleteOutcome(),
        )
        assertEquals(
            DeleteOutcome.FAILED,
            Result.failure<Unit>(RuntimeException("boom")).toDeleteOutcome(),
        )
    }

    @Test
    fun `download observation exposes matching active or completed DownloadInfo`() = runTest {
        val movieId = testUuid(44)
        val harness = Harness(itemId = movieId, offline = true)
        val movie = testMovie(id = movieId)
        coEvery { harness.databaseRepository.getMovie(movieId, harness.userId) } returns movie

        val dl =
            DownloadInfo(
                id = testUuid(45),
                itemId = movieId,
                itemName = "Movie",
                itemType = "Movie",
                sourceId = "src-1",
                sourceName = "1080p",
                status = DownloadStatus.COMPLETED,
                progress = 1f,
                bytesDownloaded = 100L,
                totalBytes = 100L,
                filePath = "/tmp/m.mkv",
                error = null,
                createdAt = 1L,
                updatedAt = 2L,
                serverId = "srv-1",
                userId = harness.userId,
            )
        harness.allDownloadsFlow.value = listOf(dl)

        val viewModel = harness.createViewModel()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.downloadInfo)
        assertEquals(dl.id, viewModel.uiState.value.downloadInfo?.id)
    }
}
