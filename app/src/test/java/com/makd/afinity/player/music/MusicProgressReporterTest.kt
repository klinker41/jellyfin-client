package com.makd.afinity.player.music

import com.makd.afinity.data.repository.playback.PlaybackRepository
import com.makd.afinity.testutil.TestMediaFixtures.testTrack
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test

class MusicProgressReporterTest {

    private var activeReporter: MusicProgressReporter? = null

    @After
    fun tearDown() {
        activeReporter?.stopProgressUpdates()
    }

    @Test
    fun `onPlaybackStarted and onPlaybackStopped preserve ordering and playCountedOnServer`() =
        runTest {
            val stopReported = CompletableDeferred<Unit>()
            val repo: PlaybackRepository =
                mockk(relaxed = true) {
                    coEvery {
                        reportPlaybackStart(
                            itemId = any(),
                            sessionId = any(),
                            mediaSourceId = any(),
                            playMethod = any(),
                            canSeek = true,
                        )
                    } returns true
                    coEvery {
                        reportPlaybackStop(
                            itemId = any(),
                            sessionId = any(),
                            positionTicks = any(),
                            mediaSourceId = any(),
                            isEnded = any(),
                            isMusic = true,
                            playCountedOnServer = any(),
                        )
                    } coAnswers
                        {
                            stopReported.complete(Unit)
                            true
                        }
                }
            val trackId = testUuid(1)
            val track = testTrack(id = trackId)
            val updatedTrack = track.copy(playbackPositionTicks = 50_000_000L)
            val tracker: PodcastProgressTracker =
                mockk(relaxed = true) {
                    coEvery { isPodcastTrack(any()) } returns false
                    coEvery {
                        syncProgress(
                            track,
                            positionMs = 5_000L,
                            isEnded = false,
                            allowCompletion = false,
                        )
                    } returns updatedTrack
                }
            val reporter = MusicProgressReporter(repo, tracker).also { activeReporter = it }

            reporter.onPlaybackStarted(
                trackId,
                playSessionId = "ps-1",
                playMethod = "DirectPlay",
                track = track,
            )
            reporter.onPlaybackPaused(positionMs = 5_000L)
            reporter.onPlaybackStopped(positionMs = 12_345L, isEnded = true)

            withContext(Dispatchers.Default) { withTimeout(2_000L) { stopReported.await() } }

            coVerifyOrder {
                repo.reportPlaybackStart(
                    itemId = trackId,
                    sessionId = "ps-1",
                    mediaSourceId = trackId.toString(),
                    playMethod = "DirectPlay",
                    canSeek = true,
                )
                tracker.syncProgress(
                    track,
                    positionMs = 5_000L,
                    isEnded = false,
                    allowCompletion = false,
                )
                repo.reportPlaybackStop(
                    itemId = trackId,
                    sessionId = "ps-1",
                    positionTicks = 123_450_000L,
                    mediaSourceId = trackId.toString(),
                    isEnded = true,
                    isMusic = true,
                    playCountedOnServer = true,
                )
            }
        }

    @Test
    fun `onTrackTransition reports stop once with false playCountedOnServer when start failed`() =
        runTest {
            val stopReported = CompletableDeferred<Unit>()
            val repo: PlaybackRepository =
                mockk(relaxed = true) {
                    coEvery {
                        reportPlaybackStart(
                            itemId = any(),
                            sessionId = any(),
                            mediaSourceId = any(),
                            playMethod = any(),
                            canSeek = true,
                        )
                    } throws RuntimeException("offline")
                    coEvery {
                        reportPlaybackStop(
                            itemId = any(),
                            sessionId = any(),
                            positionTicks = any(),
                            mediaSourceId = any(),
                            isEnded = any(),
                            isMusic = true,
                            playCountedOnServer = false,
                        )
                    } coAnswers
                        {
                            stopReported.complete(Unit)
                            false
                        }
                }
            val tracker: PodcastProgressTracker =
                mockk(relaxed = true) { coEvery { isPodcastTrack(any()) } returns false }
            val reporter = MusicProgressReporter(repo, tracker).also { activeReporter = it }
            val trackId = testUuid(5)

            reporter.onPlaybackStarted(trackId, playSessionId = "ps-2", playMethod = "DirectPlay")
            reporter.onTrackTransition(previousPositionMs = 5_000L, previousEnded = false)
            // Duplicate stop when currentTrackId is already cleared does nothing
            reporter.onPlaybackStopped(positionMs = 9_000L, isEnded = true)

            withContext(Dispatchers.Default) { withTimeout(2_000L) { stopReported.await() } }

            coVerify(exactly = 1) {
                repo.reportPlaybackStop(
                    itemId = trackId,
                    sessionId = "ps-2",
                    positionTicks = 50_000_000L,
                    mediaSourceId = trackId.toString(),
                    isEnded = false,
                    isMusic = true,
                    playCountedOnServer = false,
                )
            }
        }

    @Test
    fun `podcast playback skips SessionApi start and stop and updates UserData with allowCompletion only on stop`() =
        runTest {
            val stopSynced = CompletableDeferred<Unit>()
            val repo: PlaybackRepository = mockk(relaxed = true)
            val episodeId = testUuid(9)
            val episode = testTrack(id = episodeId)
            val pausedEpisode = episode.copy(playbackPositionTicks = 450_000_000L)
            val tracker: PodcastProgressTracker =
                mockk(relaxed = true) {
                    coEvery { isPodcastTrack(any()) } returns true
                    coEvery {
                        syncProgress(
                            episode,
                            positionMs = 45_000L,
                            isEnded = false,
                            allowCompletion = false,
                        )
                    } returns pausedEpisode
                    coEvery {
                        syncProgress(
                            pausedEpisode,
                            positionMs = 90_000L,
                            isEnded = true,
                            allowCompletion = true,
                        )
                    } coAnswers
                        {
                            stopSynced.complete(Unit)
                            null
                        }
                }
            val reporter = MusicProgressReporter(repo, tracker).also { activeReporter = it }

            reporter.onPlaybackStarted(
                episodeId,
                playSessionId = "ps-pod",
                playMethod = "DirectPlay",
                track = episode,
            )
            reporter.onPlaybackPaused(positionMs = 45_000L)
            reporter.onPlaybackStopped(positionMs = 90_000L, isEnded = true)

            withContext(Dispatchers.Default) { withTimeout(2_000L) { stopSynced.await() } }

            coVerify(exactly = 0) {
                repo.reportPlaybackStart(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            }
            coVerify(exactly = 0) {
                repo.reportPlaybackProgress(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            }
            coVerify(exactly = 0) {
                repo.reportPlaybackStop(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            }
            coVerifyOrder {
                tracker.syncProgress(
                    episode,
                    positionMs = 45_000L,
                    isEnded = false,
                    allowCompletion = false,
                )
                tracker.syncProgress(
                    pausedEpisode,
                    positionMs = 90_000L,
                    isEnded = true,
                    allowCompletion = true,
                )
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `startProgressUpdates for podcast skips SessionApi progress, syncs periodically, and updates latestTrack`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val repo: PlaybackRepository = mockk(relaxed = true)
            val episodeId = testUuid(12)
            val episode = testTrack(id = episodeId)
            val periodicEpisode = episode.copy(playbackPositionTicks = 300_000_000L)
            val tracker: PodcastProgressTracker =
                mockk(relaxed = true) {
                    coEvery { isPodcastTrack(any()) } returns true
                    coEvery { syncPeriodicIfDue(any(), any(), any(), any()) } returns null
                    coEvery {
                        syncPeriodicIfDue(
                            track = episode,
                            positionMs = 30_000L,
                            isPaused = false,
                            tickCount = 3,
                        )
                    } returns periodicEpisode
                }
            val reporter =
                MusicProgressReporter(
                        playbackRepository = repo,
                        podcastProgressTracker = tracker,
                        progressInterval = 10.milliseconds,
                        dispatcher = testDispatcher,
                    )
                    .also { activeReporter = it }

            var posMs = 30_000L
            reporter.onPlaybackStarted(
                episodeId,
                playSessionId = "ps-pod-loop",
                playMethod = "DirectPlay",
                track = episode,
            )
            reporter.startProgressUpdates(getPositionMs = { posMs }, isPaused = { false })
            advanceTimeBy(35.milliseconds)
            reporter.stopProgressUpdates()
            posMs = 60_000L
            reporter.onPlaybackStopped(positionMs = posMs, isEnded = false)
            advanceUntilIdle()

            coVerify(exactly = 0) {
                repo.reportPlaybackProgress(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            }
            coVerify(exactly = 1) {
                tracker.syncPeriodicIfDue(
                    track = episode,
                    positionMs = 30_000L,
                    isPaused = false,
                    tickCount = 3,
                )
            }
            coVerify(exactly = 1) {
                tracker.syncProgress(
                    periodicEpisode,
                    positionMs = 60_000L,
                    isEnded = false,
                    allowCompletion = true,
                )
            }
        }
}
