package com.makd.afinity.player.music

import com.makd.afinity.data.repository.playback.PlaybackRepository
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
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
            val reporter = MusicProgressReporter(repo).also { activeReporter = it }
            val trackId = testUuid(1)

            reporter.onPlaybackStarted(trackId, playSessionId = "ps-1", playMethod = "DirectPlay")
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
            val reporter = MusicProgressReporter(repo).also { activeReporter = it }
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
}
