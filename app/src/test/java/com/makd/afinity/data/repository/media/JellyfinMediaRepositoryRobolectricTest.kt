package com.makd.afinity.data.repository.media

import android.app.Application
import com.makd.afinity.data.manager.Session
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.mdblist.MdbListApiResult
import com.makd.afinity.data.models.mdblist.MdbListRating
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityVideo
import com.makd.afinity.data.models.omdb.OmdbApiResult
import com.makd.afinity.data.network.MdbListApiService
import com.makd.afinity.data.network.OmdbApiService
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.JellyfinApiInvoker
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.data.repository.home.HomeCacheRepository
import com.makd.afinity.data.storage.StorageLocationProvider
import com.makd.afinity.testutil.FakeApiClient
import com.makd.afinity.testutil.MainDispatcherRule
import com.makd.afinity.testutil.TestMediaFixtures.testTrack
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class JellyfinMediaRepositoryRobolectricTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    @get:Rule val tempFolder = TemporaryFolder()

    private inner class Harness {
        val userId = testUuid(1)
        val serverId = "srv-1"
        val baseUrl = "https://jf.example.com"
        val apiClient = FakeApiClient(baseUrl = baseUrl, accessToken = "tok-1")
        val sessionFlow = MutableStateFlow<Session?>(Session(serverId, userId, baseUrl))
        val sessionManager: SessionManager =
            mockk(relaxed = true) {
                every { currentSession } returns sessionFlow
                coEvery { getCurrentApiClient() } returns apiClient
            }
        val mdbListApiService: MdbListApiService = mockk(relaxed = true)
        val omdbApiService: OmdbApiService = mockk(relaxed = true)
        val securePreferencesRepository: SecurePreferencesRepository = mockk(relaxed = true)
        val preferencesRepository: PreferencesRepository = mockk(relaxed = true)
        val databaseRepository: DatabaseRepository = mockk(relaxed = true)
        val storageLocationProvider: StorageLocationProvider = mockk(relaxed = true)
        val apiInvoker = JellyfinApiInvoker(sessionManager)
        val homeCacheRepository: HomeCacheRepository = mockk(relaxed = true)
        val scope = CoroutineScope(SupervisorJob() + mainDispatcherRule.testDispatcher)

        fun createRepository(): JellyfinMediaRepository =
            JellyfinMediaRepository(
                sessionManager = sessionManager,
                mdbListApiService = mdbListApiService,
                omdbApiService = omdbApiService,
                securePreferencesRepository = securePreferencesRepository,
                preferencesRepository = preferencesRepository,
                databaseRepository = databaseRepository,
                storageLocationProvider = storageLocationProvider,
                apiInvoker = apiInvoker,
                homeCacheRepository = homeCacheRepository,
                scope = scope,
            )

        fun dispose() {
            scope.cancel()
        }
    }

    private fun movieJson(
        id: UUID,
        name: String,
        seriesId: UUID? = null,
        premiereDate: String = "2024-05-01T00:00:00Z",
        tag: String = "tag-$id",
    ): String =
        """{
            "Id":"$id",
            "Name":"$name",
            "Type":"Movie",
            "IsFolder":false,
            "RunTimeTicks":60000000000,
            "PremiereDate":"$premiereDate",
            "ImageTags":{"Primary":"$tag"},
            ${seriesId?.let { "\"SeriesId\":\"$it\"," } ?: ""}
            "UserData":{"Key":"$id","ItemId":"$id","PlaybackPositionTicks":0,"PlayCount":0,"IsFavorite":false,"Played":false}
        }"""

    private fun showJson(id: UUID, name: String): String =
        """{
            "Id":"$id",
            "Name":"$name",
            "Type":"Series",
            "IsFolder":true,
            "ImageTags":{"Primary":"tag-$id"},
            "UserData":{"Key":"$id","ItemId":"$id","PlaybackPositionTicks":0,"PlayCount":0,"IsFavorite":true,"Played":false}
        }"""

    private fun episodeJson(
        id: UUID,
        name: String,
        seriesId: UUID,
        seasonId: UUID,
        seasonNum: Int = 1,
        epNum: Int = 1,
        played: Boolean = false,
        posTicks: Long = 0L,
        hasSource: Boolean = true,
    ): String =
        """{
            "Id":"$id",
            "Name":"$name",
            "Type":"Episode",
            "IsFolder":false,
            "SeriesId":"$seriesId",
            "SeriesName":"Test Series",
            "SeasonId":"$seasonId",
            "ParentIndexNumber":$seasonNum,
            "IndexNumber":$epNum,
            "RunTimeTicks":12000000000,
            ${
                if (hasSource) {
                    "\"MediaSources\":[${FakeApiClient.mediaSourceJson(id = "src-$id", name = "1080p", supportsDirectStream = false)}],"
                } else ""
            }
            "UserData":{"Key":"$id","ItemId":"$id","PlaybackPositionTicks":$posTicks,"PlayCount":${if (played) 1 else 0},"IsFavorite":false,"Played":$played}
        }"""

    @Test
    fun `libraries, latest media, continue watching, and cache invalidation work via FakeApiClient`() =
        runTest(timeout = 30.seconds) {
            val h = Harness()
            try {
                val repo = h.createRepository()
                val movieLibId = testUuid(10)
                val tvLibId = testUuid(11)
                val liveTvLibId = testUuid(12)
                val movieId = testUuid(20)
                val epId = testUuid(21)
                val seriesId = testUuid(22)
                val seasonId = testUuid(23)

                h.apiClient.onGet("/UserViews") {
                    FakeApiClient.rawJsonResponse(
                        """{
                            "Items":[
                                {"Id":"$movieLibId","Name":"Movies","Type":"CollectionFolder","CollectionType":"movies","IsFolder":true},
                                {"Id":"$tvLibId","Name":"Shows","Type":"CollectionFolder","CollectionType":"tvshows","IsFolder":true},
                                {"Id":"$liveTvLibId","Name":"Live TV","Type":"CollectionFolder","CollectionType":"livetv","IsFolder":true}
                            ],
                            "TotalRecordCount":3,
                            "StartIndex":0
                        }""",
                    )
                }
                h.apiClient.onGet("/Items/Latest") {
                    FakeApiClient.rawJsonResponse("[${movieJson(movieId, "Latest Movie")}]")
                }
                h.apiClient.onGet("/UserItems/Resume") {
                    FakeApiClient.rawJsonResponse(
                        """{
                            "Items":[${episodeJson(epId, "Pilot", seriesId, seasonId, posTicks = 50000L)}],
                            "TotalRecordCount":1,
                            "StartIndex":0
                        }""",
                    )
                }
                h.apiClient.onGet("/Shows/NextUp") {
                    FakeApiClient.rawJsonResponse(
                        """{
                            "Items":[${episodeJson(epId, "Next Ep", seriesId, seasonId, epNum = 2)}],
                            "TotalRecordCount":1,
                            "StartIndex":0
                        }""",
                    )
                }

                val libs = repo.getLibraries()
                assertEquals(2, libs.size)
                assertEquals(CollectionType.Movies, libs[0].type)
                assertEquals(CollectionType.TvShows, libs[1].type)
                assertEquals(true, repo.hasLiveTvLibrary.first())
                coVerify { h.homeCacheRepository.putRaw(any(), any()) }

                val latest = repo.getLatestMedia(parentId = movieLibId, limit = 10)
                assertEquals(1, latest.size)
                assertEquals("Latest Movie", latest.first().name)

                val resume = repo.getContinueWatching(limit = 5)
                assertEquals(1, resume.size)
                assertTrue(resume.first() is AfinityEpisode)

                val nextUp = repo.getNextUp(limit = 5)
                assertEquals(1, nextUp.size)
                assertEquals(2, nextUp.first().indexNumber)

                // removeItemFromCache filters out item from continueWatching & nextUp flows
                repo.removeItemFromCache(epId.toString())
                assertEquals(0, (repo.getContinueWatchingFlow() as StateFlow).value.size)
                assertEquals(0, (repo.getNextUpFlow() as StateFlow).value.size)

                // invalidateContinueWatchingCache refills flow; clearPlaybackCaches clears flows
                repo.invalidateContinueWatchingCache()
                assertEquals(1, (repo.getContinueWatchingFlow() as StateFlow).value.size)

                repo.clearPlaybackCaches()
                assertEquals(0, (repo.getContinueWatchingFlow() as StateFlow).value.size)
            } finally {
                h.dispose()
            }
        }

    @Test
    fun `item detail queries, seasons, episodes, genres, studios, and episode selection`() =
        runTest(timeout = 30.seconds) {
            val h = Harness()
            try {
                val repo = h.createRepository()
                val movieId = testUuid(30)
                val seriesId = testUuid(31)
                val season1Id = testUuid(32)
                val ep1Id = testUuid(33)
                val ep2Id = testUuid(34)
                val studioId = testUuid(35)

                h.apiClient.onMatch(
                    { req ->
                        req.pathTemplate == "/Items" &&
                            (req.queryParameters["ids"] as? Collection<*>)?.any {
                                it.toString() == movieId.toString()
                            } == true
                    },
                ) {
                    FakeApiClient.rawJsonResponse(
                        """{"Items":[],"TotalRecordCount":0,"StartIndex":0}""",
                    )
                }
                h.apiClient.onGet("/Items/{itemId}") { req ->
                    val requestedId = req.pathParameters["itemId"]?.toString()
                    if (requestedId == movieId.toString()) {
                        FakeApiClient.rawJsonResponse(movieJson(movieId, "Fallback Hit"))
                    } else {
                        throw InvalidStatusException(404)
                    }
                }
                h.apiClient.onGet("/Items/{itemId}/Similar") {
                    FakeApiClient.rawJsonResponse(
                        """{"Items":[${movieJson(testUuid(36), "Similar One")}],"TotalRecordCount":1,"StartIndex":0}""",
                    )
                }
                h.apiClient.onGet("/Items/{itemId}/SpecialFeatures") {
                    val sfId = testUuid(37)
                    FakeApiClient.rawJsonResponse(
                        """[{"Id":"$sfId","Name":"Making Of","Type":"Video","IsFolder":false,"UserData":{"Key":"$sfId","ItemId":"$sfId","PlaybackPositionTicks":0,"PlayCount":0,"IsFavorite":false,"Played":false}}]""",
                    )
                }
                h.apiClient.onGet("/Shows/{seriesId}/Seasons") {
                    FakeApiClient.rawJsonResponse(
                        """{"Items":[{"Id":"$season1Id","Name":"Season 1","Type":"Season","IsFolder":true,"SeriesId":"$seriesId","IndexNumber":1,"UserData":{"Key":"$season1Id","ItemId":"$season1Id","PlaybackPositionTicks":0,"PlayCount":0,"IsFavorite":false,"Played":false}}],"TotalRecordCount":1,"StartIndex":0}""",
                    )
                }
                h.apiClient.onGet("/Shows/{seriesId}/Episodes") {
                    FakeApiClient.rawJsonResponse(
                        """{"Items":[
                            ${episodeJson(ep1Id, "Ep 1", seriesId, season1Id, epNum = 1, played = true)},
                            ${episodeJson(ep2Id, "Ep 2", seriesId, season1Id, epNum = 2, played = false)}
                        ],"TotalRecordCount":2,"StartIndex":0}""",
                    )
                }
                h.apiClient.onGet("/Genres") {
                    FakeApiClient.rawJsonResponse(
                        """{"Items":[{"Id":"${testUuid(38)}","Name":"Sci-Fi","Type":"Genre"},{"Id":"${testUuid(39)}","Name":"Action","Type":"Genre"}],"TotalRecordCount":2,"StartIndex":0}""",
                    )
                }
                h.apiClient.onGet("/Studios") {
                    FakeApiClient.rawJsonResponse(
                        """{"Items":[{"Id":"$studioId","Name":"Pixar","Type":"Studio","ChildCount":12,"ImageTags":{"Thumb":"t1"}},{"Id":"${testUuid(40)}","Name":"Tiny","Type":"Studio","ChildCount":1}],"TotalRecordCount":2,"StartIndex":0}""",
                    )
                }

                // Fallback to /Items/{itemId} when batch ids lookup returns empty
                val fetched = repo.getItemById(movieId)
                assertTrue(fetched is AfinityMovie)
                assertEquals("Fallback Hit", fetched?.name)

                val similar = repo.getSimilarItems(movieId, limit = 5)
                assertEquals(1, similar.size)

                val specials = repo.getSpecialFeatures(movieId, h.userId)
                assertEquals(1, specials.size)
                assertTrue(specials.first() is AfinityVideo)

                val seasons = repo.getSeasons(seriesId)
                assertEquals(1, seasons.size)

                // Next episode / episode to play picks ep2 (first unplayed) and enriches sources
                // via /Items?ids=...
                h.apiClient.onMatch(
                    { req ->
                        req.pathTemplate == "/Items" &&
                            (req.queryParameters["ids"] as? Collection<*>)?.any {
                                it.toString() == ep2Id.toString()
                            } == true
                    },
                ) {
                    FakeApiClient.rawJsonResponse(
                        """{"Items":[${episodeJson(ep2Id, "Ep 2", seriesId, season1Id, epNum = 2, played = false, hasSource = true)}],"TotalRecordCount":1,"StartIndex":0}""",
                    )
                }
                val toPlay = repo.getEpisodeToPlay(seriesId)
                assertNotNull(toPlay)
                assertEquals(ep2Id, toPlay?.id)

                h.apiClient.onGet("/Shows/NextUp") {
                    FakeApiClient.rawJsonResponse(
                        """{"Items":[${episodeJson(ep2Id, "Ep 2", seriesId, season1Id, epNum = 2, played = false, hasSource = true)}],"TotalRecordCount":1,"StartIndex":0}""",
                    )
                }
                val nextEp = repo.getSeriesNextEpisode(seriesId)
                assertEquals(ep2Id, nextEp?.id)

                val genres =
                    repo.getGenres(
                        parentId = null,
                        limit = null,
                        includeItemTypes = listOf("MOVIE"),
                    )
                assertEquals(listOf("Sci-Fi", "Action"), genres)

                val studios =
                    repo.getStudios(
                        includeItemTypes = emptyList(),
                        requireImages = true,
                        minItemCount = 5,
                    )
                assertEquals(1, studios.size)
                assertEquals("Pixar", studios.first().name)
                assertTrue(
                    studios.first().primaryImageUrl!!.contains("Items/$studioId/Images/Thumb"),
                )
            } finally {
                h.dispose()
            }
        }

    @Test
    fun `getShows, external ratings, trickplay local dir, and offline fallback behave correctly`() =
        runTest(timeout = 30.seconds) {
            val h = Harness()
            try {
                val repo = h.createRepository()
                val showId = testUuid(50)
                val itemId = testUuid(51)

                h.apiClient.onGet("/Items") {
                    FakeApiClient.rawJsonResponse(
                        """{"Items":[${showJson(showId, "My Show")}],"TotalRecordCount":1,"StartIndex":0}""",
                    )
                }
                val shows = repo.getShows(sortBy = SortBy.NAME, limit = 10)
                assertEquals(1, shows.size)
                assertEquals("My Show", shows.first().name)

                // MDBList ratings fetch succeeds when key present
                coEvery {
                    h.securePreferencesRepository.getMdbListApiKey(h.serverId, h.userId.toString())
                } returns "mdb-key"
                coEvery {
                    h.mdbListApiService.getRatings("movie", "12345", "mdb-key", any())
                } returns
                    MdbListApiResult(
                        ratings = listOf(MdbListRating(source = "imdb", value = 8.5, score = 85.0)),
                    )
                val mdbResult = repo.getMdbListRatings("12345", isMovie = true)
                assertEquals(1, mdbResult.ratings.size)

                // OMDb returns null without key, succeeds with key
                coEvery {
                    h.securePreferencesRepository.getOmdbApiKey(h.serverId, h.userId.toString())
                } returns null
                assertNull(repo.getOmdbDetails("tt1234567"))

                coEvery {
                    h.securePreferencesRepository.getOmdbApiKey(h.serverId, h.userId.toString())
                } returns "omdb-key"
                coEvery { h.omdbApiService.getTitleDetails("tt1234567", "omdb-key") } returns
                    OmdbApiResult(response = "True", awards = "Won 2 Oscars")
                assertEquals("Won 2 Oscars", repo.getOmdbDetails("tt1234567")?.awards)

                // Local trickplay directory lookup returns matching trickplay folder
                val baseDir = tempFolder.newFolder("downloads")
                val trickDir =
                    File(File(baseDir, itemId.toString()), "trickplay").apply { mkdirs() }
                coEvery { h.databaseRepository.getDownloadByItemId(itemId) } returns null
                coEvery { h.storageLocationProvider.resolveBaseDir(any()) } returns baseDir
                coEvery { h.storageLocationProvider.primaryBaseDir() } returns baseDir
                val resolvedDir = repo.getTrickplayLocalDir(itemId, "src-9")
                assertEquals(trickDir.absolutePath, resolvedDir?.absolutePath)

                // API failure leaves getLibrariesResult as failure without crashing
                h.apiClient.onGet("/UserViews") { throw InvalidStatusException(500) }
                val failedLibs = repo.getLibrariesResult()
                assertTrue(failedLibs.isFailure)
                assertFalse(failedLibs.isSuccess)
            } finally {
                h.dispose()
            }
        }

    @Test
    fun `invalidateContinueListeningCache loads resumable audiobooks and podcast episodes`() =
        runTest(timeout = 30.seconds) {
            val h = Harness()
            try {
                val repo = h.createRepository()
                val bookId = testUuid(70)
                val podcastId = testUuid(71)
                val albumId = testUuid(72)

                h.apiClient.onGet("/Items") { req ->
                    val types = req.queryParameters["includeItemTypes"]?.toString().orEmpty()
                    val filters = req.queryParameters["filters"]?.toString().orEmpty()
                    val itemsJson =
                        when {
                            "AudioBook" in types && "IsResumable" in filters ->
                                """[{
                                    "Id":"$bookId",
                                    "Name":"The Well of Ascension",
                                    "Type":"AudioBook",
                                    "IsFolder":false,
                                    "RunTimeTicks":1042122000000,
                                    "ImageTags":{"Primary":"book-tag"},
                                    "UserData":{"Key":"$bookId","ItemId":"$bookId","PlaybackPositionTicks":189160000000,"PlayCount":1,"IsFavorite":false,"Played":false,"LastPlayedDate":"2026-11-23T14:12:56Z"}
                                }]"""
                            "AudioBook" !in types && "Audio" in types && "IsResumable" in filters ->
                                """[{
                                    "Id":"$podcastId",
                                    "Name":"Chapter 12 - Magic is Might",
                                    "Type":"Audio",
                                    "IsFolder":false,
                                    "AlbumId":"$albumId",
                                    "Album":"Behind the Portrait",
                                    "AlbumPrimaryImageTag":"alb-tag",
                                    "RunTimeTicks":30244800000,
                                    "ImageTags":{"Primary":"ep-tag"},
                                    "UserData":{"Key":"$podcastId","ItemId":"$podcastId","PlaybackPositionTicks":7000000000,"PlayCount":1,"IsFavorite":false,"Played":false,"LastPlayedDate":"2026-11-23T14:10:00Z"}
                                }]"""
                            else -> "[]"
                        }
                    FakeApiClient.rawJsonResponse(
                        """{"Items":$itemsJson,"TotalRecordCount":1,"StartIndex":0}""",
                    )
                }

                repo.invalidateContinueListeningCache()

                val books = repo.continueListening.first()
                assertEquals(1, books.size)
                assertEquals(bookId, books.single().id)

                val podcasts = repo.continueListeningPodcasts.first()
                assertEquals(1, podcasts.size)
                assertEquals(podcastId, podcasts.single().id)
                assertTrue(
                    podcasts
                        .single()
                        .images
                        .primary
                        .toString()
                        .contains("Items/$podcastId/Images/Primary"),
                )

                val updatedTrack =
                    testTrack(
                        id = testUuid(73),
                        name = "New Pod",
                        playbackPositionTicks = 1_000_000L,
                    )
                repo.updateContinueListeningPodcast(updatedTrack)
                assertEquals(2, repo.continueListeningPodcasts.first().size)

                repo.updateContinueListeningPodcast(updatedTrack.copy(played = true))
                assertEquals(1, repo.continueListeningPodcasts.first().size)
            } finally {
                h.dispose()
            }
        }
}
