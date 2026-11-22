package com.makd.afinity.data.workers

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.makd.afinity.data.database.dao.AbsDownloadDao
import com.makd.afinity.data.database.dao.AudiobookshelfDao
import com.makd.afinity.data.database.entities.AbsDownloadEntity
import com.makd.afinity.data.manager.DownloadNotificationManager
import com.makd.afinity.data.manager.DownloadSemaphoreManager
import com.makd.afinity.data.models.audiobookshelf.AbsDownloadStatus
import com.makd.afinity.data.models.audiobookshelf.AudioFile
import com.makd.afinity.data.models.audiobookshelf.FileMetadata
import com.makd.afinity.data.models.audiobookshelf.ItemResponse
import com.makd.afinity.data.models.audiobookshelf.Media
import com.makd.afinity.data.models.audiobookshelf.MediaMetadata
import com.makd.afinity.data.network.AudiobookshelfApiService
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.data.repository.audiobookshelf.AbsDownloadRepositoryImpl
import com.makd.afinity.testutil.FakeApiClient
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AbsMediaDownloadWorkerRobolectricTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private inner class Harness {
        val context: Context = ApplicationProvider.getApplicationContext()
        val absDownloadDao: AbsDownloadDao = mockk(relaxed = true)
        val audiobookshelfDao: AudiobookshelfDao = mockk(relaxed = true)
        val apiService: AudiobookshelfApiService = mockk(relaxed = true)
        val securePreferencesRepository: SecurePreferencesRepository =
            mockk(relaxed = true) {
                coEvery { getCachedAudiobookshelfToken() } returns "abs-tok"
                coEvery { getCachedAudiobookshelfServerUrl() } returns "https://abs.example.com"
            }
        val preferencesRepository: PreferencesRepository =
            mockk(relaxed = true) { coEvery { getMaxDownloads() } returns 2 }
        val semaphoreManager = DownloadSemaphoreManager()
        val notificationManager: DownloadNotificationManager = mockk(relaxed = true)
        val audiobookshelfRepository: AudiobookshelfRepository = mockk(relaxed = true)

        var httpHandler: (okhttp3.Request) -> Response = { req ->
            FakeApiClient.okHttpResponse(req, code = 404)
        }

        val okHttpClient =
            OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain -> httpHandler(chain.request()) })
                .build()

        fun buildWorker(input: androidx.work.Data): AbsMediaDownloadWorker {
            val factory =
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker =
                        AbsMediaDownloadWorker(
                            appContext = appContext,
                            workerParams = workerParameters,
                            absDownloadDao = absDownloadDao,
                            audiobookshelfDao = audiobookshelfDao,
                            apiService = dagger.Lazy { apiService },
                            securePreferencesRepository = securePreferencesRepository,
                            preferencesRepository = preferencesRepository,
                            downloadSemaphoreManager = semaphoreManager,
                            downloadNotificationManager = notificationManager,
                            okHttpClient = okHttpClient,
                            audiobookshelfRepository = audiobookshelfRepository,
                        )
                }
            return TestListenableWorkerBuilder<AbsMediaDownloadWorker>(context, input)
                .setWorkerFactory(factory)
                .build()
        }
    }

    private fun entity(downloadId: UUID, localDir: File) =
        AbsDownloadEntity(
            id = downloadId,
            libraryItemId = "lib-item-1",
            episodeId = null,
            jellyfinServerId = "srv-1",
            jellyfinUserId = "usr-1",
            title = "Audiobook Title",
            authorName = "Author Name",
            mediaType = "book",
            coverUrl = null,
            duration = 120.0,
            status = AbsDownloadStatus.QUEUED,
            progress = 0f,
            bytesDownloaded = 0L,
            totalBytes = 0L,
            tracksTotal = 2,
            tracksDownloaded = 0,
            error = null,
            createdAt = 1L,
            updatedAt = 1L,
            localDirPath = localDir.absolutePath,
            serializedSession = null,
        )

    private fun libraryItemResponse(): ItemResponse =
        ItemResponse(
            id = "lib-item-1",
            libraryId = "lib-1",
            mediaType = "book",
            media =
                Media(
                    metadata =
                        MediaMetadata(
                            title = "Audiobook Title",
                            authorName = "Author Name",
                        ),
                    audioFiles =
                        listOf(
                            AudioFile(
                                index = 0,
                                ino = "ino-0",
                                metadata =
                                    FileMetadata(filename = "part0.mp3", ext = ".mp3", size = 5L),
                                duration = 60.0,
                                mimeType = "audio/mpeg",
                            ),
                            AudioFile(
                                index = 1,
                                ino = "ino-1",
                                metadata =
                                    FileMetadata(filename = "part1.mp3", ext = ".mp3", size = 6L),
                                duration = 60.0,
                                mimeType = "audio/mpeg",
                            ),
                        ),
                    duration = 120.0,
                ),
        )

    @Test
    fun `downloads book tracks, supports Range 206 resume and 200 reset, and saves session`() =
        runTest(timeout = 30.seconds) {
            val h = Harness()
            val downloadId = testUuid(30)
            val localDir = tempFolder.newFolder("abs-book")
            coEvery { h.absDownloadDao.getById(downloadId) } returns entity(downloadId, localDir)
            coEvery { h.apiService.getItem("lib-item-1", expanded = 1, include = null) } returns
                retrofit2.Response.success(libraryItemResponse())

            // Pre-seed track_0 partial for HTTP 206 append resume, and track_1 partial for HTTP
            // 200 ignored-range reset
            File(localDir, "track_0.mp3.download").writeText("part0-")
            File(localDir, "track_1.mp3.download").writeText("stale-")
            var track0RangeHeader: String? = null
            var track1RangeHeader: String? = null

            h.httpHandler = { req ->
                when {
                    req.url.encodedPath.endsWith("/cover") ->
                        FakeApiClient.okHttpResponse(
                            req,
                            code = 200,
                            body = "jpeg-cover".toByteArray(),
                            contentType = "image/jpeg",
                        )
                    req.url.encodedPath.contains("/file/ino-0/download") -> {
                        track0RangeHeader = req.header("Range")
                        FakeApiClient.okHttpResponse(
                            req,
                            code = 206,
                            body = "tail0".toByteArray(),
                            contentType = "audio/mpeg",
                        )
                    }
                    req.url.encodedPath.contains("/file/ino-1/download") -> {
                        track1RangeHeader = req.header("Range")
                        FakeApiClient.okHttpResponse(
                            req,
                            code = 200,
                            body = "track1-full".toByteArray(),
                            contentType = "audio/mpeg",
                        )
                    }
                    else -> FakeApiClient.okHttpResponse(req, code = 404)
                }
            }

            val input =
                workDataOf(
                    AbsDownloadRepositoryImpl.KEY_DOWNLOAD_ID to downloadId.toString(),
                    AbsDownloadRepositoryImpl.KEY_LIBRARY_ITEM_ID to "lib-item-1",
                )
            val result = h.buildWorker(input).doWork()
            assertTrue(result is ListenableWorker.Result.Success)
            assertEquals("bytes=6-", track0RangeHeader)
            assertEquals("bytes=6-", track1RangeHeader)
            assertEquals("part0-tail0", File(localDir, "track_0.mp3").readText())
            assertEquals("track1-full", File(localDir, "track_1.mp3").readText())
            coVerify {
                h.absDownloadDao.updateProgress(
                    id = downloadId,
                    status = AbsDownloadStatus.COMPLETED,
                    progress = 1f,
                    bytesDownloaded = any(),
                    totalBytes = any(),
                    tracksDownloaded = 2,
                    serializedSession = match { it.contains("track_0.mp3") },
                    updatedAt = any(),
                )
            }
        }

    @Test
    fun `missing URL or track HTTP 500 failure marks download FAILED`() =
        runTest(timeout = 30.seconds) {
            val h = Harness()
            val downloadId = testUuid(31)
            val localDir = tempFolder.newFolder("abs-fail")
            coEvery { h.absDownloadDao.getById(downloadId) } returns entity(downloadId, localDir)
            coEvery { h.apiService.getItem("lib-item-1", expanded = 1, include = null) } returns
                retrofit2.Response.success(libraryItemResponse())

            val input =
                workDataOf(
                    AbsDownloadRepositoryImpl.KEY_DOWNLOAD_ID to downloadId.toString(),
                    AbsDownloadRepositoryImpl.KEY_LIBRARY_ITEM_ID to "lib-item-1",
                )

            // 1. Missing cached Audiobookshelf server URL fails immediately
            coEvery { h.securePreferencesRepository.getCachedAudiobookshelfServerUrl() } returns
                null
            val missingUrlResult = h.buildWorker(input).doWork()
            assertTrue(missingUrlResult is ListenableWorker.Result.Failure)
            coVerify {
                h.absDownloadDao.updateStatus(
                    id = downloadId,
                    status = AbsDownloadStatus.FAILED,
                    error = match { it.contains("No ABS server URL") },
                    updatedAt = any(),
                )
            }

            // 2. Track HTTP 500 failure marks download FAILED
            coEvery { h.securePreferencesRepository.getCachedAudiobookshelfServerUrl() } returns
                "https://abs.example.com"
            h.httpHandler = { req ->
                if (req.url.encodedPath.endsWith("/cover")) {
                    FakeApiClient.okHttpResponse(req, code = 404)
                } else {
                    FakeApiClient.okHttpResponse(req, code = 500)
                }
            }

            val result = h.buildWorker(input).doWork()
            assertTrue(result is ListenableWorker.Result.Failure)
            coVerify {
                h.absDownloadDao.updateStatus(
                    id = downloadId,
                    status = AbsDownloadStatus.FAILED,
                    error = match { it.contains("HTTP 500") },
                    updatedAt = any(),
                )
            }
        }
}
