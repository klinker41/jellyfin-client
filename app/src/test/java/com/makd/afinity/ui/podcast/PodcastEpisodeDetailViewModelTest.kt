package com.makd.afinity.ui.podcast

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.makd.afinity.R
import com.makd.afinity.data.autodownload.PlayedItemEventBus
import com.makd.afinity.data.manager.DownloadPermissions
import com.makd.afinity.data.manager.MediaRefreshBus
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.manager.RefreshTrigger
import com.makd.afinity.data.manager.Session
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.models.user.AfinityUserDataDto
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.music.MusicRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.data.store.ItemStore
import com.makd.afinity.testutil.MainDispatcherRule
import com.makd.afinity.testutil.TestMediaFixtures.testTrack
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class PodcastEpisodeDetailViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private inner class Harness(val itemId: UUID = testUuid(200)) {
        val userId = testUuid(1)
        val serverId = "srv-1"
        val baseUrl = "https://jf.example.com"
        val isOfflineFlow = MutableStateFlow(false)
        val downloadsFlow = MutableStateFlow<List<DownloadInfo>>(emptyList())
        val sessionManager: SessionManager =
            mockk(relaxed = true) {
                every { currentSession } returns
                    MutableStateFlow(Session(serverId, userId, baseUrl))
            }
        val mediaRepository: MediaRepository =
            mockk(relaxed = true) { every { getBaseUrl() } returns baseUrl }
        val musicRepository: MusicRepository = mockk(relaxed = true)
        val userDataRepository: UserDataRepository =
            mockk(relaxed = true) {
                coEvery { markWatched(any()) } returns true
                coEvery { markUnwatched(any()) } returns true
            }
        val databaseRepository: DatabaseRepository = mockk(relaxed = true)
        val appDataRepository: AppDataRepository = mockk(relaxed = true)
        val mediaRefreshBus = MediaRefreshBus()
        val playedItemEventBus = PlayedItemEventBus()
        val itemStore = ItemStore()
        val downloadRepository: DownloadRepository =
            mockk(relaxed = true) { every { getAllDownloadsFlow() } returns downloadsFlow }
        val offlineModeManager: OfflineModeManager =
            mockk(relaxed = true) {
                every { isOffline } returns isOfflineFlow
                coEvery { isCurrentlyOffline() } answers { isOfflineFlow.value }
            }
        val downloadPermissions: DownloadPermissions =
            mockk(relaxed = true) {
                every { isAllowedByServer } returns MutableStateFlow(true)
                every { isAllowedOnNetwork } returns MutableStateFlow(true)
            }

        fun createViewModel(): PodcastEpisodeDetailViewModel =
            PodcastEpisodeDetailViewModel(
                mediaRepository = mediaRepository,
                musicRepository = musicRepository,
                userDataRepository = userDataRepository,
                databaseRepository = databaseRepository,
                appDataRepository = appDataRepository,
                sessionManager = sessionManager,
                mediaRefreshBus = mediaRefreshBus,
                playedItemEventBus = playedItemEventBus,
                itemStore = itemStore,
                downloadRepository = downloadRepository,
                offlineModeManager = offlineModeManager,
                downloadPermissions = downloadPermissions,
                savedStateHandle = SavedStateHandle(mapOf("itemId" to itemId.toString())),
            )
    }

    @Test
    fun `loads online episode details and projects ItemStore updates`() = runTest {
        val h = Harness()
        val epId = h.itemId
        val created = LocalDateTime.of(2026, 11, 22, 10, 0)
        val dto =
            BaseItemDto(
                id = epId,
                type = BaseItemKind.AUDIO,
                name = "Q&A Episode",
                album = "The Running Public",
                albumId = testUuid(201),
                overview = "Listener questions answered.",
                dateCreated = created,
                runTimeTicks = 36_000_000_000L,
                userData =
                    UserItemDataDto(
                        playbackPositionTicks = 5_000_000_000L,
                        playCount = 0,
                        isFavorite = false,
                        played = false,
                        key = epId.toString(),
                        itemId = epId,
                    ),
            )
        coEvery { h.mediaRepository.getItemResult(epId, any()) } returns Result.success(dto)

        val vm = h.createViewModel()
        advanceUntilIdle()

        val loaded = vm.uiState.value.episode!!
        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.errorRes)
        assertEquals("Q&A Episode", loaded.name)
        assertEquals("Listener questions answered.", loaded.overview)
        assertEquals(created, loaded.dateCreated)
        assertEquals(500_000L, loaded.resumePositionMs())
    }

    @Test
    fun `online load overlays unsynced local userData onto episode`() = runTest {
        val h = Harness()
        val epId = h.itemId
        val dto =
            BaseItemDto(
                id = epId,
                type = BaseItemKind.AUDIO,
                name = "Stale Server Episode",
                runTimeTicks = 36_000_000_000L,
                userData =
                    UserItemDataDto(
                        playbackPositionTicks = 1_000_000_000L,
                        playCount = 0,
                        isFavorite = false,
                        played = false,
                        key = epId.toString(),
                        itemId = epId,
                    ),
            )
        coEvery { h.mediaRepository.getItemResult(epId, any()) } returns Result.success(dto)
        coEvery { h.databaseRepository.getUserData(h.userId, epId) } returns
            AfinityUserDataDto(
                userId = h.userId,
                itemId = epId,
                serverId = h.serverId,
                played = false,
                favorite = false,
                likes = false,
                playbackPositionTicks = 9_500_000_000L,
                toBeSynced = true,
            )

        val vm = h.createViewModel()
        advanceUntilIdle()

        val loaded = vm.uiState.value.episode!!
        assertEquals(9_500_000_000L, loaded.playbackPositionTicks)
        assertEquals(950_000L, loaded.resumePositionMs())
    }

    @Test
    fun `pre-seeded stale ItemStore entry does not clobber pending local or fresh online progress`() =
        runTest {
            val h = Harness()
            val epId = h.itemId
            val stalePositionTicks = 400_000_000L // 40s
            val pendingPositionTicks = 10_800_000_000L // 18m
            h.itemStore.put(
                testTrack(
                    id = epId,
                    name = "Walk Episode",
                    playbackPositionTicks = stalePositionTicks,
                ),
            )
            val staleServerDto =
                BaseItemDto(
                    id = epId,
                    type = BaseItemKind.AUDIO,
                    name = "Walk Episode",
                    runTimeTicks = 36_000_000_000L,
                    userData =
                        UserItemDataDto(
                            playbackPositionTicks = stalePositionTicks,
                            playCount = 0,
                            isFavorite = false,
                            played = false,
                            key = epId.toString(),
                            itemId = epId,
                        ),
                )
            coEvery { h.mediaRepository.getItemResult(epId, any()) } returns
                Result.success(staleServerDto)
            coEvery { h.databaseRepository.getUserData(h.userId, epId) } returns
                AfinityUserDataDto(
                    userId = h.userId,
                    itemId = epId,
                    serverId = h.serverId,
                    played = false,
                    favorite = false,
                    likes = false,
                    playbackPositionTicks = pendingPositionTicks,
                    toBeSynced = true,
                )

            val vm = h.createViewModel()
            advanceUntilIdle()

            assertEquals(pendingPositionTicks, vm.uiState.value.episode?.playbackPositionTicks)
            assertEquals(1_080_000L, vm.uiState.value.episode?.resumePositionMs())
            assertEquals(
                pendingPositionTicks,
                h.itemStore.getAs<AfinityTrack>(epId)?.playbackPositionTicks,
            )

            // Post-sync: server now has the 18m position and local toBeSynced was cleared
            val freshServerDto =
                staleServerDto.copy(
                    userData =
                        staleServerDto.userData!!.copy(
                            playbackPositionTicks = pendingPositionTicks,
                        ),
                )
            h.itemStore.put(
                testTrack(
                    id = epId,
                    name = "Walk Episode",
                    playbackPositionTicks = stalePositionTicks,
                ),
            )
            coEvery { h.mediaRepository.getItemResult(epId, any()) } returns
                Result.success(freshServerDto)
            coEvery { h.databaseRepository.getUserData(h.userId, epId) } returns
                AfinityUserDataDto(
                    userId = h.userId,
                    itemId = epId,
                    serverId = h.serverId,
                    played = false,
                    favorite = false,
                    likes = false,
                    playbackPositionTicks = pendingPositionTicks,
                    toBeSynced = false,
                )

            vm.retry()
            advanceUntilIdle()

            assertEquals(pendingPositionTicks, vm.uiState.value.episode?.playbackPositionTicks)
            assertEquals(
                pendingPositionTicks,
                h.itemStore.getAs<AfinityTrack>(epId)?.playbackPositionTicks,
            )
        }

    @Test
    fun `pre-seeded stale ItemStore entry does not clobber offline cached progress`() = runTest {
        val h = Harness()
        val epId = h.itemId
        h.isOfflineFlow.value = true
        h.itemStore.put(
            testTrack(id = epId, name = "Offline Pod", playbackPositionTicks = 400_000_000L),
        )
        coEvery {
            h.databaseRepository.getMusicTrack(epId, h.serverId, h.userId.toString())
        } returns
            testTrack(id = epId, name = "Offline Pod", playbackPositionTicks = 10_800_000_000L)

        val vm = h.createViewModel()
        advanceUntilIdle()

        assertEquals(10_800_000_000L, vm.uiState.value.episode?.playbackPositionTicks)
        assertEquals(
            10_800_000_000L,
            h.itemStore.getAs<AfinityTrack>(epId)?.playbackPositionTicks,
        )
    }

    @Test
    fun `offline mode loads cached track and sets not found error when missing`() = runTest {
        val h = Harness()
        h.isOfflineFlow.value = true
        val cached = testTrack(id = h.itemId, name = "Offline Episode")
        coEvery {
            h.databaseRepository.getMusicTrack(h.itemId, h.serverId, h.userId.toString())
        } returns cached

        val vm = h.createViewModel()
        advanceUntilIdle()
        assertEquals("Offline Episode", vm.uiState.value.episode?.name)

        val missingHarness = Harness(testUuid(202))
        missingHarness.isOfflineFlow.value = true
        coEvery { missingHarness.databaseRepository.getMusicTrack(any(), any(), any()) } returns
            null
        val missingVm = missingHarness.createViewModel()
        advanceUntilIdle()
        assertNull(missingVm.uiState.value.episode)
        assertEquals(R.string.item_error_not_found, missingVm.uiState.value.errorRes)
    }

    @Test
    fun `togglePlayed marks watched emits buses and reverts on failure`() = runTest {
        val h = Harness()
        val dto =
            BaseItemDto(
                id = h.itemId,
                type = BaseItemKind.AUDIO,
                name = "Episode 1",
                runTimeTicks = 20_000_000_000L,
                userData =
                    UserItemDataDto(
                        playbackPositionTicks = 4_000_000_000L,
                        playCount = 0,
                        isFavorite = false,
                        played = false,
                        key = h.itemId.toString(),
                        itemId = h.itemId,
                    ),
            )
        coEvery { h.mediaRepository.getItemResult(h.itemId, any()) } returns Result.success(dto)

        val vm = h.createViewModel()
        advanceUntilIdle()

        var refreshEvent: RefreshTrigger? = null
        var playedEvent: UUID? = null
        val refreshJob = launch { refreshEvent = h.mediaRefreshBus.events.first() }
        val playedJob = launch { playedEvent = h.playedItemEventBus.playedItems.first() }
        advanceUntilIdle()

        vm.togglePlayed()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.episode!!.played)
        assertEquals(0L, vm.uiState.value.episode!!.playbackPositionTicks)
        assertEquals(0L, vm.uiState.value.episode!!.resumePositionMs())
        coVerify { h.userDataRepository.markWatched(h.itemId) }
        verify {
            h.mediaRepository.updateContinueListeningPodcast(
                match { it.id == h.itemId && it.played && it.playbackPositionTicks == 0L },
            )
        }
        assertEquals(RefreshTrigger.USER_DATA_CHANGED, refreshEvent)
        assertEquals(h.itemId, playedEvent)
        refreshJob.cancel()
        playedJob.cancel()

        coEvery { h.userDataRepository.markUnwatched(h.itemId) } returns false
        vm.togglePlayed()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.episode!!.played)
    }

    @Test
    fun `toggleFavorite updates state and reverts when api call throws`() = runTest {
        val h = Harness()
        val dto =
            BaseItemDto(
                id = h.itemId,
                type = BaseItemKind.AUDIO,
                name = "Episode Fav",
                userData =
                    UserItemDataDto(
                        playbackPositionTicks = 0L,
                        playCount = 0,
                        isFavorite = false,
                        played = false,
                        key = h.itemId.toString(),
                        itemId = h.itemId,
                    ),
            )
        coEvery { h.mediaRepository.getItemResult(h.itemId, any()) } returns Result.success(dto)

        val vm = h.createViewModel()
        advanceUntilIdle()

        vm.toggleFavorite()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.episode!!.favorite)
        coVerify { h.musicRepository.setFavorite(h.itemId, true) }
        verify { h.appDataRepository.updateTrackFavoriteStatus(any(), true) }

        coEvery { h.musicRepository.setFavorite(h.itemId, false) } throws RuntimeException("boom")
        vm.toggleFavorite()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.episode!!.favorite)
    }

    @Test
    fun `downloadConfirm builds delete confirmation for completed download`() = runTest {
        val h = Harness()
        val dlId = testUuid(300)
        h.downloadsFlow.value =
            listOf(
                DownloadInfo(
                    id = dlId,
                    itemId = h.itemId,
                    itemName = "Episode 1",
                    itemType = "Audio",
                    sourceId = "src",
                    sourceName = "src",
                    status = DownloadStatus.COMPLETED,
                    progress = 1f,
                    bytesDownloaded = 1024L,
                    totalBytes = 1024L,
                    filePath = "/tmp/ep.mp3",
                    error = null,
                    createdAt = 1L,
                    updatedAt = 1L,
                    serverId = h.serverId,
                    userId = h.userId,
                ),
            )
        coEvery { h.mediaRepository.getItemResult(h.itemId, any()) } returns Result.success(null)

        val vm = h.createViewModel()
        val collectJob = launch { vm.downloadInfo.collect {} }
        advanceUntilIdle()

        val confirm = vm.downloadConfirm("Episode 1")
        assertEquals("Episode 1", confirm?.title)
        collectJob.cancel()
    }

    @Test
    fun `cancelOrDeleteDownload deletes completed download and cancels in-progress download`() =
        runTest {
            val h = Harness()
            val completedId = testUuid(301)
            val activeId = testUuid(302)
            fun makeDownload(id: UUID, status: DownloadStatus) =
                DownloadInfo(
                    id = id,
                    itemId = h.itemId,
                    itemName = "Episode 1",
                    itemType = "Audio",
                    sourceId = "src",
                    sourceName = "src",
                    status = status,
                    progress = if (status == DownloadStatus.COMPLETED) 1f else 0.5f,
                    bytesDownloaded = 512L,
                    totalBytes = 1024L,
                    filePath = if (status == DownloadStatus.COMPLETED) "/tmp/ep.mp3" else null,
                    error = null,
                    createdAt = 1L,
                    updatedAt = 1L,
                    serverId = h.serverId,
                    userId = h.userId,
                )

            h.downloadsFlow.value = listOf(makeDownload(completedId, DownloadStatus.COMPLETED))
            coEvery { h.mediaRepository.getItemResult(h.itemId, any()) } returns
                Result.success(null)

            val vm = h.createViewModel()
            val collectJob = launch { vm.downloadInfo.collect {} }
            advanceUntilIdle()

            vm.cancelOrDeleteDownload()
            advanceUntilIdle()
            coVerify { h.downloadRepository.deleteDownload(completedId) }

            h.downloadsFlow.value = listOf(makeDownload(activeId, DownloadStatus.DOWNLOADING))
            advanceUntilIdle()

            vm.cancelOrDeleteDownload()
            advanceUntilIdle()
            coVerify { h.downloadRepository.cancelDownload(activeId) }
            collectJob.cancel()
        }

    @Test
    fun `online failure falls back to cached track or shows error and retry recovers`() = runTest {
        val h = Harness()
        val cached = testTrack(id = h.itemId, name = "Cached Pod")
        coEvery { h.mediaRepository.getItemResult(h.itemId, any()) } returns
            Result.failure(RuntimeException("network error"))
        coEvery {
            h.databaseRepository.getMusicTrack(h.itemId, h.serverId, h.userId.toString())
        } returns cached

        val fallbackVm = h.createViewModel()
        advanceUntilIdle()
        assertEquals("Cached Pod", fallbackVm.uiState.value.episode?.name)
        assertNull(fallbackVm.uiState.value.errorRes)

        val uncachedHarness = Harness(testUuid(303))
        coEvery {
            uncachedHarness.mediaRepository.getItemResult(uncachedHarness.itemId, any())
        } returns Result.failure(RuntimeException("network error"))
        coEvery { uncachedHarness.databaseRepository.getMusicTrack(any(), any(), any()) } returns
            null

        val errVm = uncachedHarness.createViewModel()
        advanceUntilIdle()
        assertNull(errVm.uiState.value.episode)
        assertEquals(R.string.error_load_content_generic, errVm.uiState.value.errorRes)

        val recoveredDto =
            BaseItemDto(
                id = uncachedHarness.itemId,
                type = BaseItemKind.AUDIO,
                name = "Recovered Pod",
            )
        coEvery {
            uncachedHarness.mediaRepository.getItemResult(uncachedHarness.itemId, any())
        } returns Result.success(recoveredDto)

        errVm.retry()
        advanceUntilIdle()
        assertEquals("Recovered Pod", errVm.uiState.value.episode?.name)
        assertNull(errVm.uiState.value.errorRes)
    }

    @Test
    fun `formatPodcastEpisodeNumber combines episode and disc numbers when present`() {
        assertEquals("#665 · Disc 1", formatPodcastEpisodeNumber(665, "Disc 1"))
        assertEquals("#0 · Disc 2", formatPodcastEpisodeNumber(0, "Disc 2"))
        assertEquals("#665", formatPodcastEpisodeNumber(665, null))
        assertEquals("#665", formatPodcastEpisodeNumber(665, "   "))
        assertEquals("Disc 2", formatPodcastEpisodeNumber(null, "Disc 2"))
        assertEquals("Disc 2", formatPodcastEpisodeNumber(-1, "Disc 2"))
        assertNull(formatPodcastEpisodeNumber(null, null))
        assertNull(formatPodcastEpisodeNumber(-1, " "))
    }
}
