package com.makd.afinity.data.repository.download

import android.app.Application
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.makd.afinity.data.audiobooks.JellyfinAudiobookLoader
import com.makd.afinity.data.database.entities.DownloadDto
import com.makd.afinity.data.manager.DownloadNotificationManager
import com.makd.afinity.data.manager.Session
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.download.DownloadQuality
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.lastfm.LastFmRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.music.MusicRepository
import com.makd.afinity.data.storage.StorageLocationProvider
import com.makd.afinity.data.storage.VolumeUnavailableException
import com.makd.afinity.testutil.FakeApiClient
import com.makd.afinity.testutil.MainDispatcherRule
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class JellyfinDownloadRepositoryRobolectricTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    @get:Rule val tempFolder = TemporaryFolder()

    private inner class Harness {
        val userId = testUuid(1)
        val serverId = "srv-1"
        val baseUrl = "https://jf.example.com"
        val primaryDir = tempFolder.newFolder("primary-downloads")
        val sessionFlow = MutableStateFlow<Session?>(Session(serverId, userId, baseUrl))
        val sessionManager: SessionManager =
            mockk(relaxed = true) { every { currentSession } returns sessionFlow }
        val mediaRepository: MediaRepository = mockk(relaxed = true)
        val musicRepository: MusicRepository = mockk(relaxed = true)
        val databaseRepository: DatabaseRepository = mockk(relaxed = true)
        val preferencesRepository: PreferencesRepository =
            mockk(relaxed = true) {
                coEvery { getDownloadStorageVolumeId() } returns
                    StorageLocationProvider.PRIMARY_VOLUME_ID
                coEvery { getVideoDownloadQuality() } returns 0
                coEvery { getMusicDownloadQuality() } returns 0
                coEvery { getDownloadOverWifiOnly() } returns false
            }
        val storageLocationProvider: StorageLocationProvider =
            mockk(relaxed = true) {
                every { isVolumeAvailable(StorageLocationProvider.PRIMARY_VOLUME_ID) } returns true
                every { isVolumeAvailable("sd-missing") } returns false
                every { displayNameFor("sd-missing") } returns "Missing SD"
                every { resolveBaseDir(any()) } returns primaryDir
                every { primaryBaseDir() } returns primaryDir
            }
        val downloadNotificationManager: DownloadNotificationManager = mockk(relaxed = true)
        val workManager: WorkManager = mockk(relaxed = true)
        val lastFmRepository: LastFmRepository = mockk(relaxed = true)
        val audiobookLoader: JellyfinAudiobookLoader = mockk(relaxed = true)
        val scope = CoroutineScope(SupervisorJob() + mainDispatcherRule.testDispatcher)

        fun createRepository(): JellyfinDownloadRepository =
            JellyfinDownloadRepository(
                sessionManager = sessionManager,
                mediaRepository = mediaRepository,
                musicRepository = musicRepository,
                databaseRepository = databaseRepository,
                preferencesRepository = preferencesRepository,
                storageLocationProvider = storageLocationProvider,
                downloadNotificationManager = downloadNotificationManager,
                workManager = workManager,
                lastFmRepository = lastFmRepository,
                audiobookLoader = dagger.Lazy { audiobookLoader },
                applicationScope = scope,
            )

        fun dispose() {
            scope.cancel()
        }
    }

    private fun movieDto(id: UUID, sourceId: String): BaseItemDto =
        FakeApiClient.decodeJson(
            """{
                "Id":"$id",
                "Name":"Downloadable Movie",
                "Type":"Movie",
                "IsFolder":false,
                "ProductionYear":2024,
                "RunTimeTicks":50000000000,
                "CanDownload":true,
                "MediaSources":[${
                    FakeApiClient.mediaSourceJson(
                        id = sourceId,
                        name = "1080p",
                        container = "mkv",
                        size = 104857600,
                        supportsDirectStream = false,
                    )
                }],
                "UserData":{"Key":"$id","ItemId":"$id","PlaybackPositionTicks":0,"PlayCount":0,"IsFavorite":false,"Played":false}
            }""",
        )

    @Test
    fun `startDownload validates volumes, prevents duplicates, and enqueues worker`() =
        runTest(timeout = 30.seconds) {
            val h = Harness()
            try {
                val repo = h.createRepository()
                val movieId = testUuid(10)

                // Unavailable explicit volume fails with VolumeUnavailableException
                val unavailable = repo.startDownload(movieId, "src-1", volumeId = "sd-missing")
                assertTrue(unavailable.isFailure)
                assertTrue(unavailable.exceptionOrNull() is VolumeUnavailableException)

                // Existing completed download fails cleanly
                coEvery {
                    h.databaseRepository.getDownloadByItemIdScoped(movieId, h.serverId, h.userId)
                } returns
                    DownloadDto(
                        id = testUuid(99),
                        itemId = movieId,
                        itemName = "Downloadable Movie",
                        itemType = "Movie",
                        sourceId = "src-1",
                        sourceName = "1080p",
                        status = DownloadStatus.COMPLETED,
                        progress = 1f,
                        bytesDownloaded = 100L,
                        totalBytes = 100L,
                        filePath = null,
                        error = null,
                        createdAt = 1L,
                        updatedAt = 1L,
                        serverId = h.serverId,
                        userId = h.userId,
                        imageUrl = null,
                        seriesImageUrl = null,
                        seriesName = null,
                        seasonNumber = null,
                        episodeNumber = null,
                        releaseYear = null,
                        runtimeTicks = null,
                    )
                val dup = repo.startDownload(movieId, "src-1")
                assertTrue(dup.isFailure)

                // Fresh movie inserts QUEUED row and enqueues MediaDownloadWorker
                coEvery {
                    h.databaseRepository.getDownloadByItemIdScoped(movieId, h.serverId, h.userId)
                } returns null
                coEvery { h.mediaRepository.getItem(movieId, any()) } returns
                    movieDto(movieId, "src-1")

                val inserted = slot<DownloadDto>()
                coEvery { h.databaseRepository.insertDownload(capture(inserted)) } returns Unit

                val started =
                    repo.startDownload(
                        itemId = movieId,
                        sourceId = "src-1",
                        quality = DownloadQuality(bitrate = 4_000_000),
                    )
                assertTrue(started.isSuccess)
                assertEquals(DownloadStatus.QUEUED, inserted.captured.status)
                assertEquals("${h.serverId}/movies/$movieId", inserted.captured.folderPath)
                verify {
                    h.workManager.beginUniqueWork(
                        "download_${started.getOrNull()}",
                        ExistingWorkPolicy.KEEP,
                        any<OneTimeWorkRequest>(),
                    )
                }
            } finally {
                h.dispose()
            }
        }

    @Test
    fun `pause, resume, and cancelDownload update status and remove partial files`() =
        runTest(timeout = 30.seconds) {
            val h = Harness()
            try {
                val repo = h.createRepository()
                val downloadId = testUuid(20)
                val itemId = testUuid(21)
                val folderPath = "${h.serverId}/movies/$itemId"
                val mediaDir = File(File(h.primaryDir, folderPath), "media").apply { mkdirs() }
                val partialFile =
                    File(mediaDir, "src-1.mkv.download").apply { writeText("partial") }

                val initial =
                    DownloadDto(
                        id = downloadId,
                        itemId = itemId,
                        itemName = "Movie",
                        itemType = "Movie",
                        sourceId = "src-1",
                        sourceName = "1080p",
                        status = DownloadStatus.DOWNLOADING,
                        progress = 0.5f,
                        bytesDownloaded = 50L,
                        totalBytes = 100L,
                        filePath = null,
                        error = null,
                        createdAt = 1L,
                        updatedAt = 1L,
                        serverId = h.serverId,
                        userId = h.userId,
                        imageUrl = null,
                        seriesImageUrl = null,
                        seriesName = null,
                        seasonNumber = null,
                        episodeNumber = null,
                        releaseYear = null,
                        runtimeTicks = null,
                        folderPath = folderPath,
                    )
                coEvery { h.databaseRepository.getDownload(downloadId) } returns initial

                assertTrue(repo.pauseDownload(downloadId).isSuccess)
                coVerify {
                    h.databaseRepository.insertDownload(
                        match { it.id == downloadId && it.status == DownloadStatus.PAUSED },
                    )
                }
                verify { h.workManager.cancelUniqueWork("download_$downloadId") }

                coEvery { h.databaseRepository.getDownload(downloadId) } returns
                    initial.copy(status = DownloadStatus.PAUSED)
                assertTrue(repo.resumeDownload(downloadId).isSuccess)
                coVerify {
                    h.databaseRepository.insertDownload(
                        match { it.id == downloadId && it.status == DownloadStatus.QUEUED },
                    )
                }
                verify {
                    h.workManager.beginUniqueWork(
                        "download_$downloadId",
                        ExistingWorkPolicy.REPLACE,
                        any<OneTimeWorkRequest>(),
                    )
                }

                assertTrue(repo.cancelDownload(downloadId).isSuccess)
                assertFalse(partialFile.exists())
                coVerify { h.databaseRepository.deleteDownload(downloadId) }
            } finally {
                h.dispose()
            }
        }
}
