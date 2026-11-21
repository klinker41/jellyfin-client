package com.makd.afinity.data.manager

import app.cash.turbine.test
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.playback.PlaybackRepository
import com.makd.afinity.data.sync.UserDataSyncScheduler
import com.makd.afinity.testutil.TestMediaFixtures.testEpisode
import com.makd.afinity.testutil.TestMediaFixtures.testMovie
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackStateManagerTest {

    private class Harness(scope: kotlinx.coroutines.CoroutineScope) {
        val mediaRepository: MediaRepository = mockk(relaxed = true)
        val appDataRepository: AppDataRepository = mockk(relaxed = true)
        val playbackRepository: PlaybackRepository = mockk(relaxed = true)
        val syncScheduler: UserDataSyncScheduler = mockk(relaxed = true)
        val mediaChangeManager: MediaChangeManager = mockk(relaxed = true)
        val mediaRefreshBus = MediaRefreshBus()
        val manager =
            PlaybackStateManager(
                mediaRepository = mediaRepository,
                appDataRepository = appDataRepository,
                playbackRepository = playbackRepository,
                syncScheduler = syncScheduler,
                mediaChangeManager = mediaChangeManager,
                mediaRefreshBus = mediaRefreshBus,
                scope = scope,
            )
    }

    @Test
    fun `notifyPlaybackStopped reports stop, refreshes episode, and publishes change on success`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness(backgroundScope)
            val episodeId = testUuid(1)
            val seriesId = testUuid(2)
            val seasonId = testUuid(3)
            val refreshed =
                testEpisode(id = episodeId, seriesId = seriesId, seasonId = seasonId, played = true)

            coEvery {
                harness.playbackRepository.reportPlaybackStop(
                    itemId = episodeId,
                    sessionId = "sess-1",
                    positionTicks = 45_000_000L,
                    mediaSourceId = "src-1",
                    liveStreamId = "live-1",
                    runtimeTicks = 90_000_000L,
                    isEnded = true,
                )
            } returns true
            coEvery {
                harness.mediaRepository.refreshItemUserData(episodeId, FieldSets.REFRESH_USER_DATA)
            } returns refreshed

            harness.manager.trackPlaybackSession("sess-1", episodeId, "src-1", "live-1")
            harness.manager.updatePlaybackPosition(1_000L)

            harness.mediaRefreshBus.events.test {
                harness.manager.playbackEvents.test {
                    harness.manager.notifyPlaybackStopped(
                        itemId = episodeId,
                        positionMs = 4_500L,
                        runtimeTicks = 90_000_000L,
                        isEnded = true,
                    )
                    assertEquals(PlaybackEvent.Stopped(episodeId, 45_000_000L), awaitItem())
                }
                assertEquals(RefreshTrigger.USER_DATA_CHANGED, awaitItem())
            }

            coVerify(exactly = 1) { harness.appDataRepository.updateItemInCaches(refreshed) }
            coVerify(exactly = 1) {
                harness.mediaChangeManager.publishKnownChange(
                    updatedItem = refreshed,
                    knownSeriesId = seriesId,
                    knownSeasonId = seasonId,
                    source = MediaChangeSource.PLAYBACK,
                )
            }
            verify(exactly = 0) { harness.syncScheduler.triggerSync() }
        }

    @Test
    fun `notifyPlaybackStopped triggers sync when server stop report returns false`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness(backgroundScope)
            val movieId = testUuid(10)
            val movie = testMovie(id = movieId)

            coEvery {
                harness.playbackRepository.reportPlaybackStop(
                    itemId = movieId,
                    sessionId = "sess-2",
                    positionTicks = 20_000_000L,
                    mediaSourceId = "src-2",
                    liveStreamId = null,
                    runtimeTicks = 0L,
                    isEnded = false,
                )
            } returns false
            coEvery {
                harness.mediaRepository.refreshItemUserData(movieId, FieldSets.REFRESH_USER_DATA)
            } returns movie

            harness.manager.trackPlaybackSession("sess-2", movieId, "src-2")
            harness.manager.notifyPlaybackStopped(movieId, positionMs = 2_000L)

            verify(exactly = 1) { harness.syncScheduler.triggerSync() }
            coVerify(exactly = 0) {
                harness.playbackRepository.savePlaybackStopOffline(any(), any(), any(), any())
            }
        }

    @Test
    fun `notifyPlaybackStopped saves offline and schedules sync after clearSession or missing source`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = Harness(backgroundScope)
            val movieId = testUuid(20)
            harness.manager.trackPlaybackSession("sess-3", movieId, "src-3")
            harness.manager.clearSession()
            harness.manager.trackCurrentItem(movieId)

            harness.manager.notifyPlaybackStopped(
                itemId = movieId,
                positionMs = 3_000L,
                runtimeTicks = 60_000_000L,
                isEnded = false,
            )

            coVerify(exactly = 1) {
                harness.playbackRepository.savePlaybackStopOffline(
                    itemId = movieId,
                    positionTicks = 30_000_000L,
                    runtimeTicks = 60_000_000L,
                    isEnded = false,
                )
            }
            verify(exactly = 1) { harness.syncScheduler.triggerSync() }
        }
}
