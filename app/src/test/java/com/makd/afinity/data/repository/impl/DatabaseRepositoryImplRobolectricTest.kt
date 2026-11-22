package com.makd.afinity.data.repository.impl

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.makd.afinity.data.database.AfinityDatabase
import com.makd.afinity.data.database.entities.DownloadDto
import com.makd.afinity.data.manager.Session
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.models.media.AfinitySegment
import com.makd.afinity.data.models.media.AfinitySegmentType
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.models.server.ServerAddress
import com.makd.afinity.data.models.user.AfinityUserDataDto
import com.makd.afinity.data.models.user.User
import com.makd.afinity.testutil.TestMediaFixtures.testAlbum
import com.makd.afinity.testutil.TestMediaFixtures.testEpisode
import com.makd.afinity.testutil.TestMediaFixtures.testMovie
import com.makd.afinity.testutil.TestMediaFixtures.testSeason
import com.makd.afinity.testutil.TestMediaFixtures.testShow
import com.makd.afinity.testutil.TestMediaFixtures.testSource
import com.makd.afinity.testutil.TestMediaFixtures.testStream
import com.makd.afinity.testutil.TestMediaFixtures.testTrack
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.every
import io.mockk.mockk
import javax.inject.Provider
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.MediaStreamType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class DatabaseRepositoryImplRobolectricTest {

    private lateinit var db: AfinityDatabase
    private lateinit var sessionFlow: MutableStateFlow<Session?>
    private lateinit var repo: DatabaseRepositoryImpl

    private val userId = testUuid(1)
    private val otherUserId = testUuid(2)
    private val serverId = "srv-1"
    private val otherServerId = "srv-2"

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AfinityDatabase::class.java,
                )
                .allowMainThreadQueries()
                .build()
        sessionFlow = MutableStateFlow(Session(serverId, userId, "https://jf1.example.com"))
        val sessionManager: SessionManager =
            mockk(relaxed = true) { every { currentSession } returns sessionFlow }

        repo =
            DatabaseRepositoryImpl(
                sessionManagerProvider = Provider { sessionManager },
                serverDao = db.serverDao(),
                serverAddressDao = db.serverAddressDao(),
                userDao = db.userDao(),
                movieDao = db.movieDao(),
                showDao = db.showDao(),
                seasonDao = db.seasonDao(),
                episodeDao = db.episodeDao(),
                sourceDao = db.sourceDao(),
                mediaStreamDao = db.mediaStreamDao(),
                userDataDao = db.userDataDao(),
                serverDatabaseDao = db.serverDatabaseDao(),
                itemMetadataCacheDao = db.itemMetadataCacheDao(),
                wikidataAwardsDao = db.wikidataAwardsDao(),
                lastFmArtistDao = db.lastFmArtistDao(),
                musicTrackDao = db.musicTrackDao(),
                musicAlbumDao = db.musicAlbumDao(),
                musicLyricsDao = db.musicLyricsDao(),
                offlinePlayDao = db.offlinePlayDao(),
                videoDao = db.videoDao(),
                bookDao = db.bookDao(),
            )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedServersAndUsers() {
        repo.insertServer(Server(serverId, "Primary", "10.10.3", "https://jf1.example.com"))
        repo.insertServer(Server(otherServerId, "Backup", "10.10.3", "https://jf2.example.com"))
        repo.insertServerAddress(ServerAddress(testUuid(90), serverId, "https://jf1.example.com"))
        repo.insertUser(User(userId, "Alex", serverId, accessToken = "tok-1", isAdmin = true))
        repo.insertUser(User(otherUserId, "Sam", otherServerId, accessToken = "tok-2"))
    }

    @Test
    fun `server, show hierarchy, sources, streams, and segments persist cleanly`() =
        runTest(timeout = 30.seconds) {
            seedServersAndUsers()

            val showId = testUuid(10)
            val seasonId = testUuid(11)
            val epId = testUuid(12)
            val show = testShow(showId, "Severance")
            val season = testSeason(seasonId, seriesId = showId, name = "Season 1", indexNumber = 1)
            val source =
                testSource(
                    id = "src-ep-1",
                    mediaStreams =
                        listOf(
                            testStream(0, MediaStreamType.VIDEO),
                            testStream(1, MediaStreamType.AUDIO, language = "eng"),
                        ),
                )
            val ep =
                testEpisode(
                    epId,
                    seriesId = showId,
                    seasonId = seasonId,
                    name = "Good News",
                    sources = listOf(source),
                )

            repo.insertShow(show, serverId)
            repo.insertSeason(season, serverId)
            repo.insertEpisode(ep, serverId)
            repo.insertSource(source, epId)
            source.mediaStreams.forEach { repo.insertMediaStream(it, source.id) }
            repo.insertSegment(AfinitySegment(AfinitySegmentType.INTRO, 1_000L, 20_000L), epId)

            val loadedShow = repo.getShow(showId, userId)
            assertEquals("Severance", loadedShow?.name)
            assertEquals(1, repo.getSeasonsForShow(showId, userId).size)
            assertEquals(1, repo.getEpisodesForShow(showId, userId).size)
            assertEquals(2, repo.getMediaStreamsForSource("src-ep-1").size)
            assertEquals(1, repo.getSegmentsForItem(epId).size)
        }

    @Test
    fun `userData sync queue, offline plays, downloads, and server isolation behave accurately`() =
        runTest(timeout = 30.seconds) {
            seedServersAndUsers()

            val movie1 = testMovie(testUuid(20), "Movie Server 1")
            val movie2 = testMovie(testUuid(21), "Movie Server 2")
            repo.insertMovie(movie1, serverId)
            repo.insertMovie(movie2, otherServerId)

            assertEquals(1, repo.getAllMovies(userId, serverId).size)
            assertEquals(1, repo.getAllMovies(userId, otherServerId).size)

            // UserData unsynced queue tracks changes and clears per server/item after
            // markUserDataSynced
            val ud1 =
                AfinityUserDataDto(
                    userId = userId,
                    itemId = movie1.id,
                    serverId = serverId,
                    played = true,
                    favorite = true,
                    likes = false,
                    playbackPositionTicks = 123_456L,
                    toBeSynced = true,
                )
            repo.insertUserData(ud1)
            val toSync = repo.getAllUserDataToSync(userId, serverId)
            assertEquals(1, toSync.size)
            assertTrue(toSync.first().played)

            repo.markUserDataSynced(userId, movie1.id, serverId)
            assertEquals(0, repo.getAllUserDataToSync(userId, serverId).size)

            // Offline play queue scoped by userId & serverId
            repo.insertOfflinePlay(movie1.id, userId, serverId, 1_000L)
            assertEquals(1, repo.getOfflinePlays(userId, serverId).size)
            assertEquals(0, repo.getOfflinePlays(userId, otherServerId).size)

            // Download storage totals & music album/track local paths
            val dlId1 = testUuid(30)
            val dlId2 = testUuid(31)
            val now = System.currentTimeMillis()
            repo.insertDownload(
                DownloadDto(
                    id = dlId1,
                    itemId = movie1.id,
                    itemName = movie1.name,
                    itemType = "Movie",
                    sourceId = "s1",
                    sourceName = "1080p",
                    status = DownloadStatus.COMPLETED,
                    progress = 1f,
                    bytesDownloaded = 1_500L,
                    totalBytes = 1_500L,
                    filePath = "/tmp/m1.mkv",
                    error = null,
                    createdAt = now,
                    updatedAt = now,
                    serverId = serverId,
                    userId = userId,
                    imageUrl = null,
                    seriesImageUrl = null,
                    seriesName = null,
                    seasonNumber = null,
                    episodeNumber = null,
                    releaseYear = null,
                    runtimeTicks = null,
                    storageVolumeId = "primary",
                ),
            )
            repo.insertDownload(
                DownloadDto(
                    id = dlId2,
                    itemId = movie2.id,
                    itemName = movie2.name,
                    itemType = "Movie",
                    sourceId = "s2",
                    sourceName = "1080p",
                    status = DownloadStatus.COMPLETED,
                    progress = 1f,
                    bytesDownloaded = 2_500L,
                    totalBytes = 2_500L,
                    filePath = "/tmp/m2.mkv",
                    error = null,
                    createdAt = now,
                    updatedAt = now,
                    serverId = otherServerId,
                    userId = otherUserId,
                    imageUrl = null,
                    seriesImageUrl = null,
                    seriesName = null,
                    seasonNumber = null,
                    episodeNumber = null,
                    releaseYear = null,
                    runtimeTicks = null,
                    storageVolumeId = "sdcard-1",
                ),
            )

            assertEquals(1_500L, repo.getTotalBytesForServer(serverId))
            assertEquals(4_000L, repo.getTotalBytesAllServers())
            assertEquals(mapOf("primary" to 1_500L), repo.getTotalBytesPerVolumeForServer(serverId))

            val album = testAlbum(testUuid(600))
            val track = testTrack(testUuid(41))
            repo.insertMusicAlbum(album, serverId, userId.toString())
            repo.insertMusicTrack(track, serverId, userId.toString())
            repo.updateMusicTrackLocalFilePath(
                track.id,
                serverId,
                userId.toString(),
                "file:///tmp/track.flac",
            )
            val loadedTracks = repo.getMusicAlbumTracks(album.id, serverId, userId.toString())
            assertEquals(1, loadedTracks.size)

            repo.getMoviesFlow(userId).test {
                val movies = awaitItem()
                assertEquals(1, movies.size)
                assertEquals(movie1.id, movies.first().id)
                cancelAndIgnoreRemainingEvents()
            }

            assertNotNull(repo.getMovie(movie1.id, userId))
            assertNull(repo.getMovie(movie2.id, otherUserId))
            sessionFlow.value = Session(otherServerId, otherUserId, "https://jf2.example.com")
            assertNotNull(repo.getMovie(movie2.id, otherUserId))
            assertNull(repo.getMovie(movie1.id, userId))
            assertEquals(listOf(movie1.id), repo.getAllMovies(userId, serverId).map { it.id })
            assertEquals(
                listOf(movie2.id),
                repo.getAllMovies(otherUserId, otherServerId).map { it.id },
            )
        }
}
