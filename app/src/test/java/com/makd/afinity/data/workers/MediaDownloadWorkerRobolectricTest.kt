package com.makd.afinity.data.workers

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.makd.afinity.data.database.entities.DownloadDto
import com.makd.afinity.data.manager.DownloadNotificationManager
import com.makd.afinity.data.manager.DownloadSemaphoreManager
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.download.DownloadTranscodePlanner
import com.makd.afinity.data.repository.download.JellyfinDownloadRepository
import com.makd.afinity.data.repository.metadata.ItemMetadataFetcher
import com.makd.afinity.data.repository.segments.SegmentsRepository
import com.makd.afinity.data.repository.wikidata.WikidataAwardsRepository
import com.makd.afinity.testutil.FakeApiClient
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import com.makd.afinity.util.LocalNetworkPermission
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
class MediaDownloadWorkerRobolectricTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private inner class Harness {
        val context: Context = ApplicationProvider.getApplicationContext()
        val userId = testUuid(1)
        val serverId = "srv-1"
        val baseUrl = "https://jf.example.com"
        val apiClient = FakeApiClient(baseUrl = baseUrl, accessToken = "tok-1")
        val sessionManager: SessionManager =
            mockk(relaxed = true) { coEvery { getOrRestoreApiClient(serverId) } returns apiClient }
        val databaseRepository: DatabaseRepository = mockk(relaxed = true)
        val downloadRepository: JellyfinDownloadRepository = mockk(relaxed = true)
        val segmentsRepository: SegmentsRepository = mockk(relaxed = true)
        val preferencesRepository: PreferencesRepository =
            mockk(relaxed = true) { coEvery { getMaxDownloads() } returns 2 }
        val semaphoreManager = DownloadSemaphoreManager()
        val transcodePlanner: DownloadTranscodePlanner =
            mockk(relaxed = true) {
                coEvery { planVideo(any(), any()) } returns null
                coEvery { planAudio(any(), any(), any()) } returns null
            }
        val notificationManager: DownloadNotificationManager = mockk(relaxed = true)
        val localNetworkPermission: LocalNetworkPermission =
            mockk(relaxed = true) { every { blocks(any()) } returns false }
        val itemMetadataFetcher: ItemMetadataFetcher = mockk(relaxed = true)
        val wikidataAwardsRepository: WikidataAwardsRepository = mockk(relaxed = true)

        var httpHandler: (okhttp3.Request) -> Response = { req ->
            FakeApiClient.okHttpResponse(req, code = 500, message = "Unhandled")
        }

        val okHttpClient =
            OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain -> httpHandler(chain.request()) })
                .build()

        fun buildWorker(
            input: androidx.work.Data,
            runAttemptCount: Int = 0,
        ): MediaDownloadWorker {
            val factory =
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker =
                        MediaDownloadWorker(
                            appContext = appContext,
                            workerParams = workerParameters,
                            sessionManager = sessionManager,
                            databaseRepository = databaseRepository,
                            downloadRepository = downloadRepository,
                            segmentsRepository = segmentsRepository,
                            preferencesRepository = preferencesRepository,
                            downloadSemaphoreManager = semaphoreManager,
                            transcodePlanner = transcodePlanner,
                            downloadNotificationManager = notificationManager,
                            okHttpClient = okHttpClient,
                            localNetworkPermission = localNetworkPermission,
                            itemMetadataFetcher = itemMetadataFetcher,
                            wikidataAwardsRepository = wikidataAwardsRepository,
                        )
                }
            return TestListenableWorkerBuilder<MediaDownloadWorker>(context, input)
                .setWorkerFactory(factory)
                .setRunAttemptCount(runAttemptCount)
                .build()
        }
    }

    private fun downloadRecord(
        downloadId: UUID,
        itemId: UUID,
        sourceId: String,
        serverId: String,
        userId: UUID,
    ) =
        DownloadDto(
            id = downloadId,
            itemId = itemId,
            itemName = "Test Movie",
            itemType = "Movie",
            sourceId = sourceId,
            sourceName = "1080p",
            status = DownloadStatus.QUEUED,
            progress = 0f,
            bytesDownloaded = 0L,
            totalBytes = 0L,
            filePath = null,
            error = null,
            createdAt = 1L,
            updatedAt = 1L,
            serverId = serverId,
            userId = userId,
            imageUrl = null,
            seriesImageUrl = null,
            seriesName = null,
            seasonNumber = null,
            episodeNumber = null,
            releaseYear = "2024",
            runtimeTicks = 60_000_000_000L,
        )

    private fun registerMovieItemRoute(h: Harness, itemId: UUID, sourceId: String) {
        h.apiClient.onGet("/Items") {
            FakeApiClient.rawJsonResponse(
                """{
                    "Items":[{
                        "Id":"$itemId",
                        "Name":"Test Movie",
                        "Type":"Movie",
                        "IsFolder":false,
                        "ProductionYear":2024,
                        "RunTimeTicks":60000000000,
                        "MediaSources":[${
                            FakeApiClient.mediaSourceJson(
                                id = sourceId,
                                name = "1080p",
                                container = "mkv",
                                size = 1024,
                                supportsDirectStream = false,
                            )
                        }],
                        "UserData":{"Key":"$itemId","ItemId":"$itemId","PlaybackPositionTicks":0,"PlayCount":0,"IsFavorite":false,"Played":false}
                    }],
                    "TotalRecordCount":1,
                    "StartIndex":0
                }""",
            )
        }
    }

    @Test
    fun `missing inputs and local network permission gating return expected results`() =
        runTest(timeout = 30.seconds) {
            val h = Harness()
            val missingIdWorker = h.buildWorker(workDataOf())
            assertTrue(missingIdWorker.doWork() is ListenableWorker.Result.Failure)

            val downloadId = testUuid(10)
            val itemId = testUuid(11)
            val sourceId = testUuid(12).toString().replace("-", "")
            coEvery { h.databaseRepository.getDownload(downloadId) } returns
                downloadRecord(downloadId, itemId, sourceId, h.serverId, h.userId)
            coEvery { h.databaseRepository.getServer(h.serverId) } returns
                com.makd.afinity.data.models.server.Server(
                    h.serverId,
                    "Home",
                    "10.10.3",
                    "http://192.168.1.10:8096",
                )
            every { h.localNetworkPermission.blocks("http://192.168.1.10:8096") } returns true

            val input =
                workDataOf(
                    MediaDownloadWorker.KEY_DOWNLOAD_ID to downloadId.toString(),
                    MediaDownloadWorker.KEY_ITEM_ID to itemId.toString(),
                    MediaDownloadWorker.KEY_SOURCE_ID to sourceId,
                    MediaDownloadWorker.KEY_ITEM_NAME to "Test Movie",
                    MediaDownloadWorker.KEY_ITEM_TYPE to "Movie",
                )
            assertEquals(ListenableWorker.Result.retry(), h.buildWorker(input, 0).doWork())

            val maxAttemptsResult =
                h.buildWorker(input, MediaDownloadWorker.MAX_PERMISSION_RETRY_ATTEMPTS).doWork()
            assertTrue(maxAttemptsResult is ListenableWorker.Result.Failure)
            coVerify {
                h.databaseRepository.insertDownload(
                    match { it.id == downloadId && it.status == DownloadStatus.FAILED },
                )
            }
        }

    @Test
    fun `HTTP 200 full download, HTTP 206 Range resume, HTTP 416, and HTTP 500 failure`() =
        runTest(timeout = 30.seconds) {
            val h = Harness()
            val downloadId = testUuid(20)
            val itemId = testUuid(21)
            val sourceId = testUuid(22).toString().replace("-", "")
            val itemDir = tempFolder.newFolder("item-$itemId")
            val record = downloadRecord(downloadId, itemId, sourceId, h.serverId, h.userId)

            coEvery { h.databaseRepository.getDownload(downloadId) } returns record
            coEvery { h.downloadRepository.getItemDownloadDirectory(any<DownloadDto>()) } returns
                itemDir
            registerMovieItemRoute(h, itemId, sourceId)

            val input =
                workDataOf(
                    MediaDownloadWorker.KEY_DOWNLOAD_ID to downloadId.toString(),
                    MediaDownloadWorker.KEY_ITEM_ID to itemId.toString(),
                    MediaDownloadWorker.KEY_SOURCE_ID to sourceId,
                    MediaDownloadWorker.KEY_ITEM_NAME to "Test Movie",
                    MediaDownloadWorker.KEY_ITEM_TYPE to "Movie",
                )

            // 1. HTTP 200 full download
            val payload = "hello-movie-bytes".toByteArray()
            h.httpHandler = { req ->
                FakeApiClient.okHttpResponse(
                    req,
                    code = 200,
                    body = payload,
                    contentType = "video/x-matroska",
                )
            }
            val res200 = h.buildWorker(input).doWork()
            assertTrue(res200 is ListenableWorker.Result.Success)
            val finalFile = File(File(itemDir, "media"), "$sourceId.mkv")
            assertEquals("hello-movie-bytes", finalFile.readText())
            coVerify {
                h.databaseRepository.insertDownload(
                    match { it.id == downloadId && it.status == DownloadStatus.COMPLETED },
                )
            }
            coVerify {
                h.databaseRepository.insertSource(match { it.id == "${sourceId}_local" }, itemId)
            }

            // 2. HTTP 206 resume appends remaining bytes using Range header
            finalFile.delete()
            val partialFile = File(File(itemDir, "media"), "$sourceId.mkv.download")
            partialFile.writeText("part1-")
            var seenRange: String? = null
            h.httpHandler = { req ->
                if (req.url.encodedPath.contains("/Download")) {
                    seenRange = req.header("Range")
                    FakeApiClient.okHttpResponse(
                        req,
                        code = 206,
                        body = "part2".toByteArray(),
                        contentType = "application/octet-stream",
                    )
                } else {
                    FakeApiClient.okHttpResponse(req, code = 404)
                }
            }
            val res206 = h.buildWorker(input).doWork()
            assertTrue(res206 is ListenableWorker.Result.Success)
            assertEquals("bytes=6-", seenRange)
            assertEquals("part1-part2", finalFile.readText())

            // 3. HTTP 416 range satisfied renames existing partial file into place cleanly
            finalFile.delete()
            partialFile.writeText("already-full")
            h.httpHandler = { req ->
                if (req.url.encodedPath.contains("/Download")) {
                    FakeApiClient.okHttpResponse(req, code = 416)
                } else {
                    FakeApiClient.okHttpResponse(req, code = 404)
                }
            }
            val res416 = h.buildWorker(input).doWork()
            assertTrue(res416 is ListenableWorker.Result.Success)
            assertEquals("already-full", finalFile.readText())

            // 4. HTTP 500 marks download FAILED and returns Failure
            finalFile.delete()
            h.httpHandler = { req -> FakeApiClient.okHttpResponse(req, code = 500) }
            val res500 = h.buildWorker(input).doWork()
            assertTrue(res500 is ListenableWorker.Result.Failure)
            coVerify {
                h.databaseRepository.insertDownload(
                    match { it.id == downloadId && it.status == DownloadStatus.FAILED },
                )
            }
        }
}
