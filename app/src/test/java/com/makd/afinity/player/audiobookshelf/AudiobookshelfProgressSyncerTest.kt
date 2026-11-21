package com.makd.afinity.player.audiobookshelf

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.makd.afinity.data.models.audiobookshelf.BookChapter
import com.makd.afinity.data.models.audiobookshelf.MediaProgress
import com.makd.afinity.data.models.audiobookshelf.PlaybackSession
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.audiobookshelf.AbsProgressSyncScheduler
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AudiobookshelfProgressSyncerTest {

    private var clockMs = 10_000L
    private var activeSyncer: AudiobookshelfProgressSyncer? = null

    @Before
    fun setUp() {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } answers { clockMs }
    }

    @After
    fun tearDown() {
        activeSyncer?.stopSyncing()
        unmockkStatic(SystemClock::class)
    }

    private class Harness {
        val network: Network = mockk()
        val capabilities: NetworkCapabilities = mockk {
            every { hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        }
        val connectivityManager: ConnectivityManager = mockk {
            every { activeNetwork } returns network
            every { getNetworkCapabilities(network) } returns capabilities
        }
        val context: Context =
            mockk(relaxed = true) {
                every { getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
            }
        val userId = testUuid(1)
        val audiobookshelfRepository: AudiobookshelfRepository =
            mockk(relaxed = true) { every { currentActiveContext } returns ("abs-srv" to userId) }
        val playbackManager = AudiobookshelfPlaybackManager()
        val syncScheduler: AbsProgressSyncScheduler = mockk(relaxed = true)
        val syncer =
            AudiobookshelfProgressSyncer(
                context = context,
                audiobookshelfRepository = audiobookshelfRepository,
                playbackManager = playbackManager,
                absSyncScheduler = syncScheduler,
            )
    }

    private fun testSession(id: String, episodeId: String? = null) =
        PlaybackSession(
            id = id,
            userId = "u1",
            libraryId = "lib-1",
            libraryItemId = "item-1",
            episodeId = episodeId,
            mediaType = "book",
            duration = 100.0,
            playMethod = 0,
            startTime = 0.0,
            currentTime = 0.0,
            startedAt = 1L,
            updatedAt = 2L,
        )

    @Test
    fun `online sync accrues listened time and returns listened seconds when sync fails`() =
        runTest {
            val h = Harness().also { activeSyncer = it.syncer }
            h.playbackManager.setSession(testSession("sess-1"))
            h.playbackManager.updatePosition(25.0)

            clockMs = 10_000L
            h.syncer.startSyncing()
            clockMs = 15_000L

            coEvery {
                h.audiobookshelfRepository.syncPlaybackSession("sess-1", 5.0, 25.0, 100.0)
            } returns Result.failure(RuntimeException("timeout"))

            h.syncer.syncNow()

            // Listened seconds returned on failure remain available for the retry.
            clockMs = 17_000L
            coEvery {
                h.audiobookshelfRepository.syncPlaybackSession("sess-1", 7.0, 25.0, 100.0)
            } returns Result.success(Unit)
            h.syncer.syncNow()

            coVerify(exactly = 1) {
                h.audiobookshelfRepository.syncPlaybackSession("sess-1", 7.0, 25.0, 100.0)
            }
            assertEquals(0.0, h.syncer.takeListenedSeconds(), 0.001)
        }

    @Test
    fun `offline local session updates local progress and schedules background sync`() = runTest {
        val h = Harness().also { activeSyncer = it.syncer }
        h.playbackManager.setSession(testSession("local_item-1", episodeId = "ep-1"))
        h.playbackManager.updatePosition(99.5)

        coEvery {
            h.audiobookshelfRepository.updateProgress(
                itemId = "item-1",
                episodeId = "ep-1",
                currentTime = 99.5,
                duration = 100.0,
                isFinished = true,
                timeListened = any(),
            )
        } returns Result.success(MediaProgress(id = "mp-1", libraryItemId = "item-1"))

        h.syncer.syncNow()

        coVerify(exactly = 1) {
            h.audiobookshelfRepository.updateProgress(
                itemId = "item-1",
                episodeId = "ep-1",
                currentTime = 99.5,
                duration = 100.0,
                isFinished = true,
                timeListened = any(),
            )
        }
        verify(exactly = 1) { h.syncScheduler.scheduleSync("abs-srv", h.userId) }
    }

    @Test
    fun `playlist syncs relative episode progress and handles explicit episode switch with fallback`() =
        runTest {
            val h = Harness().also { activeSyncer = it.syncer }
            val chapters =
                listOf(
                    BookChapter(0, 0.0, 60.0, "Ep 1"),
                    BookChapter(1, 60.0, 150.0, "Ep 2"),
                )
            h.playbackManager.setSession(
                testSession("sess-ep1", episodeId = "ep-1")
                    .copy(chapters = chapters, duration = 150.0),
            )
            h.playbackManager.setPlaylistInfo(listOf("ep-1", "ep-2"))
            h.syncer.onSessionLoaded("ep-1")
            h.playbackManager.updatePosition(40.0) // inside ep-1 (duration 60.0)

            coEvery {
                h.audiobookshelfRepository.syncPlaybackSession("sess-ep1", any(), 40.0, 60.0)
            } returns Result.success(Unit)
            h.syncer.syncNow()

            coVerify(exactly = 1) {
                h.audiobookshelfRepository.syncPlaybackSession("sess-ep1", any(), 40.0, 60.0)
            }

            // Explicit episode switch closes ep-1 and falls back to local session if server start
            // fails
            coEvery { h.audiobookshelfRepository.startPlaybackSession("item-1", "ep-2") } returns
                Result.failure(RuntimeException("offline"))
            coEvery {
                h.audiobookshelfRepository.closePlaybackSession("sess-ep1", 58.0, any(), 60.0)
            } returns Result.success(Unit)

            h.syncer.onPlaylistEpisodeChanged(
                previousEpisodeId = "ep-1",
                previousTime = 58.0,
                previousDuration = 60.0,
                newEpisodeId = "ep-2",
            )
            withContext(Dispatchers.Default) {
                withTimeout(2_000L) {
                    while (h.playbackManager.playbackState.value.sessionId != "local_item-1_ep-2") {
                        kotlinx.coroutines.delay(10L)
                    }
                }
            }
            coVerify(exactly = 1) {
                h.audiobookshelfRepository.closePlaybackSession("sess-ep1", 58.0, any(), 60.0)
            }
            assertEquals("ep-2", h.playbackManager.playbackState.value.episodeId)
        }
}
