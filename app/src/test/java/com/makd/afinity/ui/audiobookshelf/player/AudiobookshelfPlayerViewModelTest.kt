package com.makd.afinity.ui.audiobookshelf.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.lifecycle.SavedStateHandle
import androidx.media3.common.util.UnstableApi
import com.makd.afinity.cast.CastEvent
import com.makd.afinity.cast.CastManager
import com.makd.afinity.cast.CastSessionState
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.audiobookshelf.Bookmark
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.navigation.Destination
import com.makd.afinity.player.ContinueListeningQueue
import com.makd.afinity.player.LastPlaybackStore
import com.makd.afinity.player.audiobook.JellyfinAudiobookStarter
import com.makd.afinity.player.audiobookshelf.AbsPlaybackStarter
import com.makd.afinity.player.audiobookshelf.AudiobookshelfEqualizerManager
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlaybackManager
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlaybackState
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlayer
import com.makd.afinity.player.audiobookshelf.AudiobookshelfSkipSilenceManager
import com.makd.afinity.testutil.MainDispatcherRule
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalCoroutinesApi::class)
class AudiobookshelfPlayerViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private class Harness(
        initialPlaybackState: AudiobookshelfPlaybackState = AudiobookshelfPlaybackState(),
        initialCastState: CastSessionState = CastSessionState(),
        isPlayerPlaying: Boolean = false,
    ) {
        val playbackStateFlow = MutableStateFlow(initialPlaybackState)
        val castStateFlow = MutableStateFlow(initialCastState)
        val castEventsFlow = MutableSharedFlow<CastEvent>(extraBufferCapacity = 4)

        val context: Context = mockk(relaxed = true)
        val audiobookshelfRepository: AudiobookshelfRepository =
            mockk(relaxed = true) {
                every { currentConfig } returns MutableStateFlow(null)
                every { getBookmarksForItemFlow(any()) } returns
                    MutableStateFlow<List<Bookmark>>(emptyList())
            }
        val audiobookshelfPlayer: AudiobookshelfPlayer =
            mockk(relaxed = true) { every { isPlaying() } returns isPlayerPlaying }
        val playbackManager: AudiobookshelfPlaybackManager =
            mockk(relaxed = true) { every { playbackState } returns playbackStateFlow }
        val equalizerManager: AudiobookshelfEqualizerManager = mockk(relaxed = true)
        val skipSilenceManager: AudiobookshelfSkipSilenceManager = mockk(relaxed = true)
        val castManager: CastManager =
            mockk(relaxed = true) {
                every { castState } returns castStateFlow
                every { castEvents } returns castEventsFlow
            }
        val securePreferencesRepository: SecurePreferencesRepository = mockk(relaxed = true)
        val absPlaybackStarter: AbsPlaybackStarter =
            mockk(relaxed = true) {
                coEvery { start(any(), any(), any(), any(), any()) } returns Result.success(Unit)
            }
        val jellyfinAudiobookStarter: JellyfinAudiobookStarter =
            mockk(relaxed = true) { coEvery { start(any(), any()) } returns Result.success(Unit) }
        val lastPlaybackStore: LastPlaybackStore = mockk(relaxed = true)
        val sessionManager: SessionManager = mockk(relaxed = true)
        val preferencesRepository: PreferencesRepository =
            mockk(relaxed = true) {
                every { getAbsSwipeToSkipFlow() } returns MutableStateFlow(true)
            }

        fun createViewModel(args: Map<String, Any?>): AudiobookshelfPlayerViewModel =
            AudiobookshelfPlayerViewModel(
                context = context,
                savedStateHandle = SavedStateHandle(args),
                audiobookshelfRepository = audiobookshelfRepository,
                audiobookshelfPlayer = audiobookshelfPlayer,
                playbackManager = playbackManager,
                equalizerManager = equalizerManager,
                skipSilenceManager = skipSilenceManager,
                castManager = castManager,
                securePreferencesRepository = securePreferencesRepository,
                absPlaybackStarter = absPlaybackStarter,
                jellyfinAudiobookStarter = jellyfinAudiobookStarter,
                lastPlaybackStore = lastPlaybackStore,
                sessionManager = sessionManager,
                preferencesRepository = preferencesRepository,
                continueListeningQueue = ContinueListeningQueue(),
            )
    }

    @Test
    fun `resumes paused existing session immediately when autoPlay is true`() = runTest {
        val itemId = testUuid(11).toString()
        val harness =
            Harness(
                initialPlaybackState =
                    AudiobookshelfPlaybackState(
                        sessionId = "session-1",
                        itemId = itemId,
                        episodeId = "ep-1",
                    ),
                isPlayerPlaying = false,
            )

        harness.createViewModel(
            mapOf("itemId" to itemId, "episodeId" to "ep-1", "autoPlay" to "true"),
        )
        advanceUntilIdle()

        verify(exactly = 1) { harness.audiobookshelfPlayer.play() }
        coVerify(exactly = 0) {
            harness.absPlaybackStarter.start(any(), any(), any(), any(), any())
        }
        coVerify(exactly = 0) { harness.jellyfinAudiobookStarter.start(any(), any()) }
    }

    @Test
    fun `does not auto-play paused existing session when autoPlay is omitted`() = runTest {
        val itemId = testUuid(12).toString()
        val harness =
            Harness(
                initialPlaybackState =
                    AudiobookshelfPlaybackState(sessionId = "session-1", itemId = itemId),
                isPlayerPlaying = false,
            )

        harness.createViewModel(mapOf("itemId" to itemId))
        advanceUntilIdle()

        verify(exactly = 0) { harness.audiobookshelfPlayer.play() }
        coVerify(exactly = 0) {
            harness.absPlaybackStarter.start(any(), any(), any(), any(), any())
        }
        coVerify(exactly = 0) { harness.jellyfinAudiobookStarter.start(any(), any()) }
    }

    @Test
    fun `does not call play again when existing session is already playing`() = runTest {
        val itemId = testUuid(13).toString()
        val harness =
            Harness(
                initialPlaybackState =
                    AudiobookshelfPlaybackState(sessionId = "session-1", itemId = itemId),
                isPlayerPlaying = true,
            )

        harness.createViewModel(mapOf("itemId" to itemId, "autoPlay" to "true"))
        advanceUntilIdle()

        verify(exactly = 0) { harness.audiobookshelfPlayer.play() }
    }

    @Test
    fun `resumes paused ABS cast session only when matching item and autoPlay is true`() = runTest {
        val itemId = testUuid(14).toString()
        val otherItemId = testUuid(15).toString()
        val harness =
            Harness(
                initialPlaybackState =
                    AudiobookshelfPlaybackState(sessionId = "session-1", itemId = itemId),
                initialCastState =
                    CastSessionState(isConnected = true, isAbsCasting = true, isPlaying = false),
            )

        harness.createViewModel(mapOf("itemId" to otherItemId, "autoPlay" to "true"))
        advanceUntilIdle()
        verify(exactly = 0) { harness.castManager.play() }

        harness.createViewModel(mapOf("itemId" to itemId, "autoPlay" to "true"))
        advanceUntilIdle()
        verify(exactly = 1) { harness.castManager.play() }
    }

    @Test
    fun `starts new Jellyfin audiobook session when no matching session is active`() = runTest {
        val itemUuid = testUuid(16)
        val harness = Harness()

        harness.createViewModel(
            mapOf(
                "itemId" to itemUuid.toString(),
                "source" to Destination.AUDIOBOOK_SOURCE_JELLYFIN,
                "autoPlay" to "true",
            ),
        )
        advanceUntilIdle()

        coVerify(exactly = 1) { harness.jellyfinAudiobookStarter.start(itemUuid, null) }
        coVerify(exactly = 0) {
            harness.absPlaybackStarter.start(any(), any(), any(), any(), any())
        }
    }
}
