package com.makd.afinity.ui.downloads

import android.content.Context
import app.cash.turbine.test
import com.makd.afinity.data.models.audiobookshelf.AbsDownloadInfo
import com.makd.afinity.data.models.audiobookshelf.AbsDownloadStatus
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.models.player.MusicQuality
import com.makd.afinity.data.models.player.VideoQuality
import com.makd.afinity.data.repository.CacheKind
import com.makd.afinity.data.repository.CacheMaintenance
import com.makd.afinity.data.repository.CacheSection
import com.makd.afinity.data.repository.CacheStore
import com.makd.afinity.data.repository.CacheUsage
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.audiobookshelf.AbsDownloadRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.storage.StorageLocationProvider
import com.makd.afinity.data.storage.VolumeUnavailableException
import com.makd.afinity.testutil.MainDispatcherRule
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class DownloadsViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private class Harness {
        val activeDownloadsFlow = MutableStateFlow<List<DownloadInfo>>(emptyList())
        val completedDownloadsFlow = MutableStateFlow<List<DownloadInfo>>(emptyList())
        val absActiveDownloadsFlow = MutableStateFlow<List<AbsDownloadInfo>>(emptyList())
        val absCompletedDownloadsFlow = MutableStateFlow<List<AbsDownloadInfo>>(emptyList())

        val context: Context =
            mockk(relaxed = true) { every { getString(any()) } returns "Unavailable volume" }
        val downloadRepository: DownloadRepository =
            mockk(relaxed = true) {
                every { getActiveDownloadsFlow() } returns activeDownloadsFlow
                every { getCompletedDownloadsFlow() } returns completedDownloadsFlow
                coEvery { getTotalStorageUsed() } returns 0L
                coEvery { getTotalStorageUsedAllServers() } returns 0L
                coEvery { pauseDownload(any()) } returns Result.success(Unit)
                coEvery { resumeDownload(any()) } returns Result.success(Unit)
                coEvery { cancelDownload(any()) } returns Result.success(Unit)
                coEvery { deleteDownload(any()) } returns Result.success(Unit)
                coEvery { removeDownloadRecord(any()) } returns Result.success(Unit)
            }
        val absDownloadRepository: AbsDownloadRepository =
            mockk(relaxed = true) {
                every { getActiveDownloadsFlow() } returns absActiveDownloadsFlow
                every { getCompletedDownloadsFlow() } returns absCompletedDownloadsFlow
                coEvery { getTotalStorageUsed() } returns 0L
                coEvery { getTotalStorageUsedAllServers() } returns 0L
                coEvery { deleteDownload(any()) } returns Result.success(Unit)
                coEvery { cancelDownload(any()) } returns Result.success(Unit)
            }
        val preferencesRepository: PreferencesRepository =
            mockk(relaxed = true) {
                coEvery { getDownloadOverWifiOnly() } returns true
                coEvery { getImageCacheEnabled() } returns true
                coEvery { getImageCacheSizeMb() } returns 512
                coEvery { getVideoCacheSizeMb() } returns 1024
                coEvery { getMaxDownloads() } returns 3
                coEvery { getVideoDownloadQuality() } returns VideoQuality.ORIGINAL_BITRATE
                coEvery { getMusicDownloadQuality() } returns MusicQuality.ORIGINAL_BITRATE
                coEvery { getDownloadStorageVolumeId() } returns
                    StorageLocationProvider.PRIMARY_VOLUME_ID
            }
        val storageLocationProvider: StorageLocationProvider =
            mockk(relaxed = true) {
                every { listVolumes() } returns emptyList()
                every { primaryBaseDir() } throws
                    IllegalStateException("skip android StatFs in JVM test")
            }
        val cacheMaintenance: CacheMaintenance =
            mockk(relaxed = true) {
                coEvery { usage() } returns
                    CacheUsage(
                        bytes = mapOf(CacheStore.IMAGES to 2048L),
                        entries = mapOf(CacheKind.GENRES to 5),
                    )
            }

        val viewModel =
            DownloadsViewModel(
                context = context,
                downloadRepository = downloadRepository,
                absDownloadRepository = absDownloadRepository,
                preferencesRepository = preferencesRepository,
                storageLocationProvider = storageLocationProvider,
                cacheMaintenance = cacheMaintenance,
            )
    }

    private fun downloadInfo(
        id: UUID,
        name: String,
        type: String = "Movie",
        bytes: Long = 100L,
        createdAt: Long = 100L,
        seriesId: String? = null,
    ) =
        DownloadInfo(
            id = id,
            itemId = id,
            itemName = name,
            itemType = type,
            sourceId = "src",
            sourceName = "1080p",
            status = DownloadStatus.COMPLETED,
            progress = 1f,
            bytesDownloaded = bytes,
            totalBytes = bytes,
            filePath = "/tmp/$name",
            error = null,
            createdAt = createdAt,
            updatedAt = createdAt,
            serverId = "srv-1",
            userId = testUuid(1),
            seriesId = seriesId,
            seriesName = if (seriesId != null) name else null,
        )

    private fun absDownload(
        id: UUID,
        libraryItemId: String,
        title: String,
        mediaType: String = "podcast",
        bytes: Long = 500L,
    ) =
        AbsDownloadInfo(
            id = id,
            libraryItemId = libraryItemId,
            episodeId = "ep-1",
            title = title,
            authorName = "Host",
            mediaType = mediaType,
            coverUrl = null,
            duration = 60.0,
            status = AbsDownloadStatus.COMPLETED,
            progress = 1f,
            bytesDownloaded = bytes,
            totalBytes = bytes,
            tracksTotal = 1,
            tracksDownloaded = 1,
            error = null,
            createdAt = 200L,
            updatedAt = 200L,
            localDirPath = "/tmp/abs/$title",
        )

    @Test
    fun `refreshCacheUsage and clearCachedData enforce guards and invoke callback`() = runTest {
        val harness = Harness()
        val gate = CompletableDeferred<Unit>()
        coEvery { harness.cacheMaintenance.clearCachedData(any()) } coAnswers { gate.await() }

        harness.viewModel.refreshCacheUsage()
        advanceUntilIdle()
        assertEquals(2048L, harness.viewModel.cacheUsage.value?.totalBytes)

        var doneCount = 0
        harness.viewModel.clearCachedData(emptySet()) { doneCount++ }
        coVerify(exactly = 0) { harness.cacheMaintenance.clearCachedData(any()) }

        harness.viewModel.clearCachedData(setOf(CacheSection.IMAGES)) { doneCount++ }
        assertTrue(harness.viewModel.isClearingCache.value)

        harness.viewModel.clearCachedData(setOf(CacheSection.VIDEO)) { doneCount++ }
        coVerify(exactly = 1) { harness.cacheMaintenance.clearCachedData(any()) }

        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(harness.viewModel.isClearingCache.value)
        assertEquals(1, doneCount)
        coVerify(exactly = 2) { harness.cacheMaintenance.usage() }
    }

    @Test
    fun `catalog and categoryUsage sort and filter across download categories`() = runTest {
        val harness = Harness()
        val smallNew =
            downloadInfo(testUuid(10), "Alpha Movie", "Movie", bytes = 200L, createdAt = 300L)
        val bigOld =
            downloadInfo(testUuid(11), "Zebra Book", "Book", bytes = 900L, createdAt = 100L)
        val absPod =
            absDownload(testUuid(12), "pod-1", "Daily Tech", mediaType = "podcast", bytes = 400L)

        harness.viewModel.catalog.test {
            assertEquals(emptyList<DownloadCatalogEntry>(), awaitItem())

            harness.completedDownloadsFlow.value = listOf(smallNew, bigOld)
            harness.absCompletedDownloadsFlow.value = listOf(absPod)
            val recent = expectMostRecentItem()
            assertEquals(listOf("Alpha Movie", "Host", "Zebra Book"), recent.map { it.title })

            harness.viewModel.setSortOrder(DownloadSort.LARGEST)
            val largest = expectMostRecentItem()
            assertEquals(
                listOf("Zebra Book", "Host", "Alpha Movie"),
                largest.map { it.title },
            )

            harness.viewModel.setCategoryFilter(DownloadCategory.VIDEO)
            val filtered = expectMostRecentItem()
            assertEquals(listOf("Alpha Movie"), filtered.map { it.title })
        }

        harness.viewModel.categoryUsage.test {
            val usages = expectMostRecentItem()
            val byCategory = usages.associateBy { it.category }
            assertEquals(200L, byCategory[DownloadCategory.VIDEO]?.bytes)
            assertEquals(900L, byCategory[DownloadCategory.BOOK]?.bytes)
            assertEquals(400L, byCategory[DownloadCategory.PODCAST]?.bytes)
        }
    }

    @Test
    fun `deleteDownload surfaces pendingUnavailableDelete and confirmRemoveUnavailableDelete cleans up`() =
        runTest {
            val harness = Harness()
            val dlId = testUuid(21)
            coEvery { harness.downloadRepository.deleteDownload(dlId) } returns
                Result.failure(VolumeUnavailableException("sd-1", "SD Card"))

            harness.viewModel.deleteDownload(dlId)
            advanceUntilIdle()
            assertEquals(dlId, harness.viewModel.uiState.value.pendingUnavailableDelete)

            harness.viewModel.confirmRemoveUnavailableDelete()
            advanceUntilIdle()
            assertNull(harness.viewModel.uiState.value.pendingUnavailableDelete)
            coVerify { harness.downloadRepository.removeDownloadRecord(dlId) }
        }

    @Test
    fun `deleteCatalogEntry dispatches to appropriate repository by ref type`() = runTest {
        val harness = Harness()
        val movieId = testUuid(31)
        val albumTrackId = testUuid(32)
        val absId = testUuid(33)
        harness.completedDownloadsFlow.value =
            listOf(
                downloadInfo(movieId, "Movie"),
                downloadInfo(albumTrackId, "Track 1", type = "Audio", seriesId = "album-1"),
            )
        harness.absCompletedDownloadsFlow.value =
            listOf(absDownload(absId, "pod-99", "Podcast", mediaType = "podcast"))
        advanceUntilIdle()

        harness.viewModel.deleteCatalogEntry(
            DownloadCatalogEntry(
                key = "k1",
                ref = DownloadCatalogRef.JellyfinItem(movieId),
                category = DownloadCategory.VIDEO,
                title = "Movie",
                subtitle = null,
                imageUrl = null,
                sizeBytes = 100L,
                childCount = 0,
                isAvailable = true,
                createdAt = 1L,
                childIds = listOf(movieId),
            ),
        )
        harness.viewModel.deleteCatalogEntry(
            DownloadCatalogEntry(
                key = "k2",
                ref = DownloadCatalogRef.MusicAlbum("album-1"),
                category = DownloadCategory.MUSIC,
                title = "Album",
                subtitle = null,
                imageUrl = null,
                sizeBytes = 100L,
                childCount = 1,
                isAvailable = true,
                createdAt = 1L,
                childIds = listOf(albumTrackId),
            ),
        )
        harness.viewModel.deleteCatalogEntry(
            DownloadCatalogEntry(
                key = "k3",
                ref = DownloadCatalogRef.AbsPodcast("pod-99"),
                category = DownloadCategory.PODCAST,
                title = "Podcast",
                subtitle = null,
                imageUrl = null,
                sizeBytes = 100L,
                childCount = 1,
                isAvailable = true,
                createdAt = 1L,
                childIds = listOf(absId),
            ),
        )
        advanceUntilIdle()

        coVerify { harness.downloadRepository.deleteDownload(movieId) }
        coVerify { harness.downloadRepository.deleteDownload(albumTrackId) }
        coVerify { harness.absDownloadRepository.deleteDownload(absId) }
    }
}
