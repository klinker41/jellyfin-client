package com.makd.afinity.player.music

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.media3.common.util.UnstableApi
import com.makd.afinity.data.database.dao.MusicQueueDao
import com.makd.afinity.data.database.entities.MusicQueueEntity
import com.makd.afinity.data.manager.Session
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.models.player.MusicQuality
import com.makd.afinity.data.models.player.StreamDecision
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.playback.PlaybackRepository
import com.makd.afinity.player.ContinueListeningQueue
import com.makd.afinity.player.ContinueListeningQueueItem
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import com.makd.afinity.util.NetworkConnectivityMonitor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(UnstableApi::class)
class MusicQueueManagerTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private fun buildMockUri(raw: String): Uri {
        val builder: Uri.Builder = mockk(relaxed = true)
        val uri: Uri = mockk(relaxed = true)
        every { uri.toString() } returns raw
        every { uri.scheme } returns raw.substringBefore("://", "").takeIf { "://" in raw }
        every { uri.buildUpon() } returns builder
        every { builder.appendEncodedPath(any()) } returns builder
        every { builder.appendQueryParameter(any(), any()) } returns builder
        every { builder.build() } returns uri
        return uri
    }

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers { buildMockUri(firstArg()) }
        every { Uri.fromFile(any()) } answers { buildMockUri("file://${firstArg<File>().path}") }
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    private fun track(seed: Int, localPath: String? = null): AfinityTrack =
        AfinityTrack(
            id = testUuid(seed),
            name = "Track $seed",
            albumId = testUuid(600),
            album = "Album",
            artistId = testUuid(700),
            artist = "Artist",
            artists = listOf("Artist"),
            indexNumber = seed,
            discNumber = 1,
            productionYear = 2024,
            runtimeTicks = 1_800_000_000L,
            playbackPositionTicks = 0L,
            played = false,
            favorite = false,
            playCount = 0,
            normalizationGain = null,
            images = AfinityImages(),
            localFilePath = localPath,
        )

    private inner class Harness(
        scope: CoroutineScope,
        restoredQueue: List<MusicQueueEntity> = emptyList(),
    ) {
        val context: Context = mockk(relaxed = true)
        val persistSignal = CompletableDeferred<Unit>()
        val clearSignal = CompletableDeferred<Unit>()
        val musicQueueDao: MusicQueueDao =
            mockk(relaxed = true) {
                coEvery { getQueue() } returns restoredQueue
                coEvery { insertAll(any()) } coAnswers { persistSignal.complete(Unit) }
                coEvery { clearQueue() } coAnswers { clearSignal.complete(Unit) }
            }
        val sessionFlow =
            MutableStateFlow<Session?>(Session("srv-1", testUuid(1), "https://jf.example.com"))
        val sessionManager: SessionManager =
            mockk(relaxed = true) { every { currentSession } returns sessionFlow }
        val playbackManager: MusicPlaybackManager =
            mockk(relaxed = true) {
                every { state } returns
                    MutableStateFlow(com.makd.afinity.data.models.music.MusicPlaybackState())
            }
        val dataStore =
            PreferenceDataStoreFactory.create(scope = scope) {
                tempFolder.newFolder().resolve("music_queue.preferences_pb")
            }
        val wifiQualityFlow = MutableStateFlow(MusicQuality.ORIGINAL_BITRATE)
        val cellularQualityFlow = MutableStateFlow(128_000)
        val neverTranscodeFlow = MutableStateFlow(false)
        val preferencesRepository: PreferencesRepository = mockk {
            every { getMusicQualityWifiFlow() } returns wifiQualityFlow
            every { getMusicQualityCellularFlow() } returns cellularQualityFlow
            every { getMusicNeverTranscodeFlow() } returns neverTranscodeFlow
        }
        val networkMonitor: NetworkConnectivityMonitor = mockk {
            every { isOnWifi() } returns false
        }
        val playbackRepository: PlaybackRepository = mockk(relaxed = true)
        val databaseRepository: DatabaseRepository =
            mockk(relaxed = true) {
                every { getDownloadsByStatusFlowScoped(any(), any(), any()) } returns
                    flowOf(emptyList())
            }

        val continueListeningQueue = ContinueListeningQueue()
        val manager =
            MusicQueueManager(
                context = context,
                musicQueueDao = musicQueueDao,
                sessionManager = sessionManager,
                playbackManager = playbackManager,
                dataStore = dataStore,
                preferencesRepository = preferencesRepository,
                networkConnectivityMonitor = networkMonitor,
                playbackRepository = playbackRepository,
                databaseRepository = databaseRepository,
                continueListeningQueue = continueListeningQueue,
            )
    }

    @Test
    fun `loadQueue, addNext, addLast, removeAt, and moveTrack keep queue and currentIndex consistent`() =
        runTest {
            val h = Harness(backgroundScope)
            val t1 = track(1)
            val t2 = track(2)
            val t3 = track(3)
            val t4 = track(4)
            val t5 = track(5)

            h.manager.loadQueue(listOf(t1, t2, t3), startIndex = 1)
            assertEquals(listOf(t1, t2, t3), h.manager.queue.value)
            assertEquals(1, h.manager.currentIndex.value)
            assertEquals(t2, h.manager.currentTrack)
            withContext(Dispatchers.Default) {
                withTimeout(2_000L) {
                    h.persistSignal.await()
                    h.manager.loadQueueEvents.first()
                }
            }

            // addNext inserts right after currentIndex (index 2)
            h.manager.addNext(listOf(t4))
            assertEquals(listOf(t1, t2, t4, t3), h.manager.queue.value)
            assertEquals(1, h.manager.currentIndex.value)

            // addLast appends at tail
            h.manager.addLast(listOf(t5))
            assertEquals(listOf(t1, t2, t4, t3, t5), h.manager.queue.value)

            // removeAt before currentIndex decrements currentIndex
            h.manager.removeAt(0)
            assertEquals(listOf(t2, t4, t3, t5), h.manager.queue.value)
            assertEquals(0, h.manager.currentIndex.value)

            // moveTrack moves current track (0 -> 2) and adjusts index along with it
            h.manager.moveTrack(from = 0, to = 2)
            assertEquals(listOf(t4, t3, t2, t5), h.manager.queue.value)
            assertEquals(2, h.manager.currentIndex.value)
            assertEquals(t2, h.manager.currentTrack)
        }

    @Test
    fun `toggleShuffle pins active track at index 0 and unshuffle restores original order`() =
        runTest {
            val h = Harness(backgroundScope)
            val tracks = (1..5).map { track(it) }
            h.manager.loadQueue(tracks, startIndex = 2)
            withContext(Dispatchers.Default) {
                withTimeout(2_000L) { h.manager.loadQueueEvents.first() }
            }

            h.manager.toggleShuffle()
            assertTrue(h.manager.shuffled.value)
            assertEquals(0, h.manager.currentIndex.value)
            assertEquals(tracks[2], h.manager.queue.value.first())
            assertEquals(tracks.toSet(), h.manager.queue.value.toSet())

            h.manager.toggleShuffle()
            assertFalse(h.manager.shuffled.value)
            assertEquals(tracks, h.manager.queue.value)
            assertEquals(2, h.manager.currentIndex.value)
        }

    @Test
    fun `ensureResolved caches stream decision, respects neverTranscode and local files`() =
        runTest {
            val h = Harness(backgroundScope)
            val localTrack = track(10, localPath = "file:///storage/song.flac")
            assertFalse(h.manager.ensureResolved(localTrack))
            coVerify(exactly = 0) {
                h.playbackRepository.resolveAudioStream(localTrack.id, any(), any())
            }

            val remoteTrack = track(11)
            coEvery {
                h.playbackRepository.resolveAudioStream(remoteTrack.id, any(), any())
            } returns
                StreamDecision.Transcode(
                    url = "https://jf.example.com/Audio/11/universal",
                    protocol = MediaStreamProtocol.HTTP,
                    transcodeReasons = emptyList(),
                    burnedInSubtitleIndex = null,
                )

            assertTrue(h.manager.ensureResolved(remoteTrack))
            assertTrue(h.manager.isServerTranscode(remoteTrack.id))
            assertEquals("Transcode", h.manager.playMethodFor(remoteTrack.id))
            assertNotNull(h.manager.playSessionIdFor(remoteTrack.id))

            // Second resolve call uses cached stream without hitting PlaybackRepository again.
            assertFalse(h.manager.ensureResolved(remoteTrack))
            coVerify(exactly = 1) {
                h.playbackRepository.resolveAudioStream(remoteTrack.id, any(), any())
            }

            // neverTranscode bypasses server stream resolution
            h.neverTranscodeFlow.value = true
            h.manager.neverTranscode.first { it }
            val originalTrack = track(12)
            assertFalse(h.manager.ensureResolved(originalTrack))
            coVerify(exactly = 0) {
                h.playbackRepository.resolveAudioStream(originalTrack.id, any(), any())
            }
        }

    @Test
    fun `resumptionQueue restores persisted entities and session change clears queue`() = runTest {
        val id1 = testUuid(31)
        val entity =
            MusicQueueEntity(
                position = 0,
                trackId = id1.toString(),
                name = "Restored Song",
                artist = "Band",
                albumId = null,
                album = "Record",
                durationMs = 200_000L,
                imageUrl = null,
                normalizationGain = null,
                albumNormalizationGain = null,
                indexNumber = 1,
                discNumber = 1,
                serverId = "srv-1",
                artistId = null,
            )
        val h = Harness(backgroundScope, restoredQueue = listOf(entity))

        val resumed = withContext(Dispatchers.Default) { h.manager.resumptionQueue() }
        assertNotNull(resumed)
        assertEquals(1, resumed!!.mediaItems.size)
        assertEquals(id1.toString(), resumed.mediaItems.first().mediaId)
        assertEquals(0, resumed.startIndex)

        // Switching to a different user session clears the queue
        h.sessionFlow.value = Session("srv-2", testUuid(9), "https://other.example.com")
        h.manager.queue.first { it.isEmpty() }
        withContext(Dispatchers.Default) { withTimeout(2_000L) { h.clearSignal.await() } }
        assertNull(h.manager.currentTrack)
        coVerify(atLeast = 1) { h.musicQueueDao.clearQueue() }
    }

    @Test
    fun `loadQueue preserves matching single podcast in ContinueListeningQueue and clears unrelated queues`() =
        runTest {
            val h = Harness(backgroundScope)
            val ep1 = track(1)
            val ep2 = track(2)
            val other = track(3)

            h.continueListeningQueue.startFromItems(
                listOf(
                    ContinueListeningQueueItem.PodcastEpisode(ep1),
                    ContinueListeningQueueItem.PodcastEpisode(ep2),
                ),
                clickedIndex = 0,
            )

            h.manager.loadQueue(listOf(ep1), startIndex = 0)
            withContext(Dispatchers.Default) {
                withTimeout(2_000L) { h.manager.loadQueueEvents.first() }
            }
            val next = h.continueListeningQueue.consumeNextForPodcast(ep1.id)
            assertNotNull(next)
            assertEquals(ContinueListeningQueueItem.PodcastEpisode(ep2), next!!.item)

            h.continueListeningQueue.startFromItems(
                listOf(
                    ContinueListeningQueueItem.PodcastEpisode(ep1),
                    ContinueListeningQueueItem.PodcastEpisode(ep2),
                ),
                clickedIndex = 0,
            )
            h.manager.loadQueue(listOf(other), startIndex = 0)
            withContext(Dispatchers.Default) {
                withTimeout(2_000L) { h.manager.loadQueueEvents.first() }
            }
            assertNull(h.continueListeningQueue.consumeNextForPodcast(ep1.id))
            assertNull(h.continueListeningQueue.consumeNextForPodcast(other.id))
        }
}
