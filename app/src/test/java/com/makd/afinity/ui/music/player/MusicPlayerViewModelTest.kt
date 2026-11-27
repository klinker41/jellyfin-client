package com.makd.afinity.ui.music.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.makd.afinity.cast.CastEvent
import com.makd.afinity.cast.CastManager
import com.makd.afinity.cast.CastSessionState
import com.makd.afinity.cast.MAX_CAST_SPEED
import com.makd.afinity.data.manager.AdminChange
import com.makd.afinity.data.manager.AdminChangeBroadcaster
import com.makd.afinity.data.manager.AdminChangeKind
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.music.AfinityLyricLine
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.models.music.MusicPlaybackState
import com.makd.afinity.data.models.music.RadioMode
import com.makd.afinity.data.models.music.RadioSeed
import com.makd.afinity.data.models.music.RadioState
import com.makd.afinity.data.models.music.RepeatMode
import com.makd.afinity.data.models.player.MusicQuality
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.music.MusicRepository
import com.makd.afinity.data.repository.playback.PlaybackRepository
import com.makd.afinity.player.ContinueListeningQueue
import com.makd.afinity.player.LibraryPlaybackSpeedStore
import com.makd.afinity.player.common.EqualizerPreset
import com.makd.afinity.player.common.EqualizerState
import com.makd.afinity.player.music.MusicEqualizerManager
import com.makd.afinity.player.music.MusicPlaybackManager
import com.makd.afinity.player.music.MusicQueueManager
import com.makd.afinity.player.music.RadioManager
import com.makd.afinity.testutil.MainDispatcherRule
import com.makd.afinity.testutil.TestMediaFixtures.testTrack
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalCoroutinesApi::class)
class MusicPlayerViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private class Harness {
        val playbackStateFlow = MutableStateFlow(MusicPlaybackState())
        val queueFlow = MutableStateFlow<List<AfinityTrack>>(emptyList())
        val currentIndexFlow = MutableStateFlow(-1)
        val neverTranscodeFlow = MutableStateFlow(false)
        val castStateFlow = MutableStateFlow(CastSessionState())
        val castEventsFlow = MutableSharedFlow<CastEvent>(extraBufferCapacity = 4)
        val radioStateFlow = MutableStateFlow(RadioState())
        val equalizerStateFlow = MutableStateFlow(EqualizerState())
        val adminChangesFlow = MutableSharedFlow<AdminChange>(extraBufferCapacity = 4)
        var currentQuality = MusicQuality.ORIGINAL

        val playbackManager: MusicPlaybackManager =
            mockk(relaxed = true) {
                every { state } returns playbackStateFlow
                every { getPlayer() } returns null
            }
        val queueManager: MusicQueueManager =
            mockk(relaxed = true) {
                every { queue } returns queueFlow
                every { currentIndex } returns currentIndexFlow
                every { neverTranscode } returns neverTranscodeFlow
                every { musicQuality } answers { currentQuality }
                every { setSessionMusicQuality(any()) } answers { currentQuality = firstArg() }
            }
        val musicRepository: MusicRepository =
            mockk(relaxed = true) { coEvery { getLyrics(any()) } returns emptyList() }
        val castManager: CastManager =
            mockk(relaxed = true) {
                every { castState } returns castStateFlow
                every { castEvents } returns castEventsFlow
            }
        val sessionManager: SessionManager =
            mockk(relaxed = true) { every { currentSession } returns MutableStateFlow(null) }
        val offlineModeManager: OfflineModeManager = mockk {
            every { isOffline } returns MutableStateFlow(false)
        }
        val radioManager: RadioManager =
            mockk(relaxed = true) { every { radioState } returns radioStateFlow }
        val equalizerManager: MusicEqualizerManager =
            mockk(relaxed = true) {
                every { state } returns equalizerStateFlow
                every { availablePresets } returns EqualizerPreset.MUSIC
            }
        val appDataRepository: AppDataRepository = mockk(relaxed = true)
        val playbackRepository: PlaybackRepository = mockk(relaxed = true)
        val preferencesRepository: PreferencesRepository =
            mockk(relaxed = true) {
                every { getMusicSwipeToSkipFlow() } returns MutableStateFlow(true)
            }
        val adminChangeBroadcaster: AdminChangeBroadcaster = mockk {
            every { changes } returns adminChangesFlow
        }
        val libraryPlaybackSpeedStore: LibraryPlaybackSpeedStore = mockk(relaxed = true)
        val context: Context = mockk(relaxed = true)

        val viewModel =
            MusicPlayerViewModel(
                playbackManager = playbackManager,
                queueManager = queueManager,
                musicRepository = musicRepository,
                castManager = castManager,
                sessionManager = sessionManager,
                offlineModeManager = offlineModeManager,
                radioManager = radioManager,
                equalizerManager = equalizerManager,
                appDataRepository = appDataRepository,
                playbackRepository = playbackRepository,
                preferencesRepository = preferencesRepository,
                adminChangeBroadcaster = adminChangeBroadcaster,
                libraryPlaybackSpeedStore = libraryPlaybackSpeedStore,
                continueListeningQueue = ContinueListeningQueue(),
                context = context,
            )
    }

    @Test
    fun `lyrics load on track change, reload on metadata change, and clear when track is null`() =
        runTest {
            val harness = Harness()
            val track = testTrack(id = testUuid(1))
            val lines = listOf(AfinityLyricLine("Line one", 1.0))
            val updatedLines = listOf(AfinityLyricLine("Line updated", 2.0))
            coEvery { harness.musicRepository.getLyrics(track.id) } returnsMany
                listOf(lines, updatedLines)

            harness.playbackStateFlow.value = MusicPlaybackState(currentTrack = track)
            advanceUntilIdle()
            assertEquals(lines, harness.viewModel.lyrics.value)

            harness.adminChangesFlow.tryEmit(
                AdminChange(track.id.toString(), AdminChangeKind.METADATA),
            )
            advanceUntilIdle()
            assertEquals(updatedLines, harness.viewModel.lyrics.value)

            harness.playbackStateFlow.value = MusicPlaybackState(currentTrack = null)
            advanceUntilIdle()
            assertTrue(harness.viewModel.lyrics.value.isEmpty())
        }

    @Test
    fun `transport and queue controls route to local player vs cast manager`() = runTest {
        val harness = Harness()
        val track = testTrack(id = testUuid(5))
        val track2 = testTrack(id = testUuid(6))

        harness.viewModel.playQueue(listOf(track, track2), startIndex = 1, shuffle = true)
        verify { harness.radioManager.stopRadio() }
        verify { harness.queueManager.loadQueue(listOf(track, track2), 1, shuffle = true) }

        harness.playbackStateFlow.value =
            MusicPlaybackState(
                currentTrack = track,
                isPlaying = false,
                positionMs = 20_000L,
                durationMs = 100_000L,
                repeatMode = RepeatMode.OFF,
            )
        advanceUntilIdle()

        harness.viewModel.togglePlayPause()
        verify { harness.playbackManager.play() }

        harness.viewModel.seekForward(10_000L)
        verify { harness.playbackManager.seekTo(30_000L) }

        harness.viewModel.cycleRepeatMode()
        verify { harness.playbackManager.updateRepeatMode(RepeatMode.ALL) }

        harness.castStateFlow.value =
            CastSessionState(
                isConnected = true,
                isMusicCasting = true,
                isPlaying = true,
                currentPosition = 40_000L,
            )
        advanceUntilIdle()

        harness.viewModel.togglePlayPause()
        verify { harness.castManager.pause() }

        harness.viewModel.seekBackward(15_000L)
        verify { harness.castManager.seekTo(25_000L) }

        harness.viewModel.skipNext()
        verify { harness.castManager.skipMusicTrack(forward = true) }
    }

    @Test
    fun `setMusicQuality and queue mutations delegate cleanly`() = runTest {
        val harness = Harness()
        val tracks = listOf(testTrack(id = testUuid(7)))

        val quality320 = MusicQuality(320_000)
        harness.viewModel.setMusicQuality(quality320)
        assertEquals(quality320, harness.viewModel.musicQuality.value)

        harness.viewModel.addNext(tracks)
        harness.viewModel.addLast(tracks)
        harness.viewModel.removeFromQueue(0)
        harness.viewModel.moveInQueue(0, 1)
        harness.viewModel.clearQueue()
        val seed =
            RadioSeed(trackId = tracks.first().id, albumId = null, sourceTracks = emptyList())
        harness.viewModel.startRadio(seed, RadioMode.SIMILAR)

        verify { harness.queueManager.addNext(tracks) }
        verify { harness.queueManager.addLast(tracks) }
        verify { harness.queueManager.removeAt(0) }
        verify { harness.queueManager.moveTrack(0, 1) }
        verify { harness.queueManager.clearQueue() }
        verify {
            harness.radioManager.startRadio(
                seed,
                RadioMode.SIMILAR,
            )
        }
    }

    @Test
    fun `toggleCurrentTrackFavorite reverts optimistic update when API fails`() = runTest {
        val harness = Harness()
        val track = testTrack(id = testUuid(9), favorite = false)
        harness.playbackStateFlow.value = MusicPlaybackState(currentTrack = track)
        coEvery { harness.musicRepository.setFavorite(track.id, true) } throws
            RuntimeException("failed")

        harness.viewModel.toggleCurrentTrackFavorite()
        advanceUntilIdle()

        verify { harness.playbackManager.updateTrack(track.copy(favorite = true)) }
        verify { harness.playbackManager.updateTrack(track) }
        verify { harness.queueManager.updateTrackInQueue(track) }
    }

    @Test
    fun `togglePlaybackStats populates fallback stats and stops polling when toggled off`() =
        runTest {
            val harness = Harness()

            harness.viewModel.togglePlaybackStats()
            runCurrent()
            assertTrue(harness.viewModel.showPlaybackStats.value)
            assertEquals(
                "Music Service (Initializing)",
                harness.viewModel.playbackStats.value.playerType,
            )

            harness.viewModel.togglePlaybackStats()
            advanceTimeBy(1500)
            assertFalse(harness.viewModel.showPlaybackStats.value)
        }

    @Test
    fun `setPlaybackSpeed updates and persists speed for podcasts only and routes to cast`() =
        runTest {
            val harness = Harness()
            val podcastKey = "playback_speed_jf_${testUuid(42)}"

            // Regular music track (librarySpeedKey == null) is a no-op
            harness.playbackStateFlow.value = MusicPlaybackState(librarySpeedKey = null)
            harness.viewModel.setPlaybackSpeed(1.5f)
            verify(exactly = 0) { harness.playbackManager.setPlaybackSpeed(any()) }
            verify(exactly = 0) { harness.libraryPlaybackSpeedStore.saveSpeed(any(), any()) }

            // Local podcast track updates playbackManager and persists under librarySpeedKey
            harness.playbackStateFlow.value =
                MusicPlaybackState(librarySpeedKey = podcastKey, playbackSpeed = 1.0f)
            harness.viewModel.setPlaybackSpeed(1.5f)
            verify(exactly = 1) { harness.playbackManager.setPlaybackSpeed(1.5f) }
            verify(exactly = 1) { harness.libraryPlaybackSpeedStore.saveSpeed(podcastKey, 1.5f) }

            // Casting routes through castManager and saves applied cast speed
            every { harness.castManager.setPlaybackSpeed(2.5f) } returns MAX_CAST_SPEED
            harness.castStateFlow.value =
                CastSessionState(isConnected = true, isMusicCasting = true)
            advanceUntilIdle()
            harness.viewModel.setPlaybackSpeed(2.5f)
            verify(exactly = 1) { harness.castManager.setPlaybackSpeed(2.5f) }
            verify(exactly = 1) { harness.playbackManager.updatePlaybackSpeed(MAX_CAST_SPEED) }
            verify(exactly = 1) {
                harness.libraryPlaybackSpeedStore.saveSpeed(podcastKey, MAX_CAST_SPEED)
            }
        }

    @Test
    fun `MusicPlaybackState positionAt scales elapsed time by playbackSpeed`() {
        val state =
            MusicPlaybackState(
                isPlaying = true,
                positionMs = 10_000L,
                durationMs = 100_000L,
                positionSampledAtMs = 1_000L,
                playbackSpeed = 1.5f,
            )
        assertEquals(25_000L, state.positionAt(11_000L))
    }

    @Test
    fun `playOrResumeTrack starts new queue when track is not currently loaded`() = runTest {
        val harness = Harness()
        val track = testTrack(id = testUuid(701))
        var beforeCalled = 0

        harness.viewModel.playOrResumeTrack(
            track = track,
            startPositionMs = 45_000L,
            onBeforeNewQueue = { beforeCalled++ },
        )

        assertEquals(1, beforeCalled)
        verify(exactly = 1) { harness.radioManager.stopRadio() }
        verify(exactly = 1) {
            harness.queueManager.loadQueue(
                tracks = listOf(track),
                startIndex = 0,
                startPositionMs = 45_000L,
                shuffle = false,
            )
        }
    }

    @Test
    fun `playOrResumeTrack resumes paused local track without reloading queue`() = runTest {
        val harness = Harness()
        val track = testTrack(id = testUuid(702))
        val player: ExoPlayer = mockk(relaxed = true)
        every { harness.playbackManager.getPlayer() } returns player
        harness.playbackStateFlow.value =
            MusicPlaybackState(currentTrack = track, isPlaying = false)
        var beforeCalled = 0

        harness.viewModel.playOrResumeTrack(
            track = track,
            startPositionMs = 45_000L,
            onBeforeNewQueue = { beforeCalled++ },
        )

        assertEquals(0, beforeCalled)
        verify(exactly = 1) { harness.playbackManager.play() }
        verify(exactly = 0) { harness.queueManager.loadQueue(any(), any(), any(), any()) }
    }

    @Test
    fun `playOrResumeTrack does not toggle or reload when local track is already playing`() =
        runTest {
            val harness = Harness()
            val track = testTrack(id = testUuid(703))
            val player: ExoPlayer = mockk(relaxed = true)
            every { harness.playbackManager.getPlayer() } returns player
            harness.playbackStateFlow.value =
                MusicPlaybackState(currentTrack = track, isPlaying = true)
            var beforeCalled = 0

            harness.viewModel.playOrResumeTrack(
                track = track,
                startPositionMs = 45_000L,
                onBeforeNewQueue = { beforeCalled++ },
            )

            assertEquals(0, beforeCalled)
            verify(exactly = 0) { harness.playbackManager.play() }
            verify(exactly = 0) { harness.queueManager.loadQueue(any(), any(), any(), any()) }
        }

    @Test
    fun `playOrResumeTrack reloads queue when same track is stale with null player`() = runTest {
        val harness = Harness()
        val track = testTrack(id = testUuid(704))
        every { harness.playbackManager.getPlayer() } returns null
        harness.playbackStateFlow.value =
            MusicPlaybackState(currentTrack = track, isPlaying = false)
        var beforeCalled = 0

        harness.viewModel.playOrResumeTrack(
            track = track,
            startPositionMs = 30_000L,
            onBeforeNewQueue = { beforeCalled++ },
        )

        assertEquals(1, beforeCalled)
        verify(exactly = 0) { harness.playbackManager.play() }
        verify(exactly = 1) {
            harness.queueManager.loadQueue(
                tracks = listOf(track),
                startIndex = 0,
                startPositionMs = 30_000L,
                shuffle = false,
            )
        }
    }

    @Test
    fun `playOrResumeTrack resumes paused cast track and leaves playing cast track alone`() =
        runTest {
            val harness = Harness()
            val track = testTrack(id = testUuid(705))
            harness.playbackStateFlow.value = MusicPlaybackState(currentTrack = track)
            harness.castStateFlow.value =
                CastSessionState(isConnected = true, isMusicCasting = true, isPlaying = false)
            var beforeCalled = 0

            harness.viewModel.playOrResumeTrack(
                track = track,
                startPositionMs = 20_000L,
                onBeforeNewQueue = { beforeCalled++ },
            )

            assertEquals(0, beforeCalled)
            verify(exactly = 1) { harness.castManager.play() }
            verify(exactly = 0) { harness.queueManager.loadQueue(any(), any(), any(), any()) }

            harness.castStateFlow.value =
                CastSessionState(isConnected = true, isMusicCasting = true, isPlaying = true)

            harness.viewModel.playOrResumeTrack(track = track, startPositionMs = 20_000L)

            verify(exactly = 1) { harness.castManager.play() }
            verify(exactly = 0) { harness.queueManager.loadQueue(any(), any(), any(), any()) }
        }
}
