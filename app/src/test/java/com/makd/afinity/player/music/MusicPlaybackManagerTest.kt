package com.makd.afinity.player.music

import android.content.Context
import android.os.SystemClock
import androidx.media3.exoplayer.ExoPlayer
import com.makd.afinity.data.models.music.MusicPlaybackState
import com.makd.afinity.data.repository.playback.PlaybackRepository
import com.makd.afinity.data.websocket.JellyfinWebSocketManager
import com.makd.afinity.testutil.TestMediaFixtures.testTrack
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MusicPlaybackManagerTest {

    @Before
    fun setUp() {
        mockkStatic(SystemClock::class)
        every { SystemClock.uptimeMillis() } returns 1_000L
    }

    @After
    fun tearDown() {
        unmockkStatic(SystemClock::class)
    }

    private fun mockWebSocketManager(): JellyfinWebSocketManager =
        mockk(relaxed = true) {
            every { remotePlaystateCommands } returns MutableSharedFlow()
            every { remoteGeneralCommands } returns MutableSharedFlow()
        }

    @Test
    fun `stop reports live player position before stopping and clearing ExoPlayer`() = runTest {
        val context: Context = mockk(relaxed = true)
        val reporter: MusicProgressReporter = mockk(relaxed = true)
        val manager =
            MusicPlaybackManager(
                scope = backgroundScope,
                context = context,
                jellyfinWebSocketManager = mockWebSocketManager(),
                musicProgressReporter = reporter,
            )
        val episode = testTrack(id = testUuid(1))
        val player: ExoPlayer =
            mockk(relaxed = true) {
                every { mediaItemCount } returns 1
                every { currentPosition } returns 900_000L
            }
        manager.setPlayer(player)
        manager.updateTrack(episode)
        manager.updatePosition(positionMs = 450_000L, durationMs = 1_800_000L)

        manager.stop()

        verifyOrder {
            reporter.onPlaybackStopped(900_000L)
            player.stop()
            player.clearMediaItems()
        }
        assertNull(manager.getPlayer())
        assertEquals(MusicPlaybackState(), manager.state.value)
    }

    @Test
    fun `paused podcast swipe dismiss reports paused position once and ignores zero from clearMediaItems`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val synced = CompletableDeferred<Unit>()
            val episode =
                testTrack(id = testUuid(2), played = false).copy(runtimeTicks = 18_000_000_000L)
            val pausedEpisode = episode.copy(playbackPositionTicks = 9_000_000_000L)
            val tracker: PodcastProgressTracker =
                mockk(relaxed = true) {
                    coEvery { isPodcastTrack(any()) } returns true
                    coEvery {
                        syncProgress(
                            episode,
                            positionMs = 900_000L,
                            isEnded = false,
                            allowCompletion = false,
                        )
                    } returns pausedEpisode
                    coEvery {
                        syncProgress(
                            pausedEpisode,
                            positionMs = 900_000L,
                            isEnded = false,
                            allowCompletion = true,
                        )
                    } coAnswers
                        {
                            synced.complete(Unit)
                            pausedEpisode
                        }
                }
            val reporter =
                MusicProgressReporter(
                    playbackRepository = mockk<PlaybackRepository>(relaxed = true),
                    podcastProgressTracker = tracker,
                    dispatcher = testDispatcher,
                )
            val manager =
                MusicPlaybackManager(
                    scope = backgroundScope,
                    context = mockk(relaxed = true),
                    jellyfinWebSocketManager = mockWebSocketManager(),
                    musicProgressReporter = reporter,
                )
            val player: ExoPlayer =
                mockk(relaxed = true) {
                    every { mediaItemCount } returns 1
                    every { currentPosition } returns 900_000L
                    every { clearMediaItems() } answers { reporter.onPlaybackStopped(0L) }
                }

            manager.setPlayer(player)
            manager.updateTrack(episode)
            manager.updatePosition(positionMs = 900_000L, durationMs = 1_800_000L)
            reporter.onPlaybackStarted(episode.id, playSessionId = "ps-pod", track = episode)
            reporter.onPlaybackPaused(positionMs = 900_000L)

            manager.stop()
            // Simulate subsequent AudioService.tearDown() after position was reset
            reporter.onPlaybackStopped(0L)
            advanceUntilIdle()
            withContext(Dispatchers.Default) { withTimeout(2_000L) { synced.await() } }

            coVerify(exactly = 0) {
                tracker.syncProgress(
                    any(),
                    positionMs = 0L,
                    isEnded = any(),
                    allowCompletion = any(),
                )
            }
            coVerify(exactly = 1) {
                tracker.syncProgress(
                    pausedEpisode,
                    positionMs = 900_000L,
                    isEnded = false,
                    allowCompletion = true,
                )
            }
        }

    @Test
    fun `stop falls back to state positionMs when player has no media items`() = runTest {
        val reporter: MusicProgressReporter = mockk(relaxed = true)
        val manager =
            MusicPlaybackManager(
                scope = backgroundScope,
                context = mockk(relaxed = true),
                jellyfinWebSocketManager = mockWebSocketManager(),
                musicProgressReporter = reporter,
            )
        val player: ExoPlayer =
            mockk(relaxed = true) {
                every { mediaItemCount } returns 0
                every { currentPosition } returns 0L
            }
        manager.setPlayer(player)
        manager.updatePosition(positionMs = 321_000L, durationMs = 1_800_000L)

        manager.stop()

        verify(exactly = 1) { reporter.onPlaybackStopped(321_000L) }
    }

    @Test
    fun `stop skips progress reporter when exoPlayer is null and still resets state`() = runTest {
        val reporter: MusicProgressReporter = mockk(relaxed = true)
        val manager =
            MusicPlaybackManager(
                scope = backgroundScope,
                context = mockk(relaxed = true),
                jellyfinWebSocketManager = mockWebSocketManager(),
                musicProgressReporter = reporter,
            )
        manager.updateTrack(testTrack(id = testUuid(3)))
        manager.updatePosition(positionMs = 123_000L, durationMs = 600_000L)

        manager.stop()

        verify(exactly = 0) { reporter.onPlaybackStopped(any(), any(), any()) }
        assertEquals(MusicPlaybackState(), manager.state.value)
    }
}
