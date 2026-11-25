package com.makd.afinity.ui.player

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerRemoteKeyHandlerTest {

    @Test
    fun `resolveMediaKey maps play pause based on playback state`() {
        assertEquals(
            PlayerRemoteAction.Pause,
            resolveMedia(keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, isPlaying = true),
        )
        assertEquals(
            PlayerRemoteAction.Pause,
            resolveMedia(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                isPlaying = false,
                playWhenReady = true,
                isEnded = false,
            ),
        )
        assertEquals(
            PlayerRemoteAction.Play,
            resolveMedia(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                isPlaying = false,
                playWhenReady = true,
                isEnded = true,
            ),
        )
        assertEquals(
            PlayerRemoteAction.Play,
            resolveMedia(keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, isPlaying = false),
        )
        assertEquals(
            PlayerRemoteAction.Play,
            resolveMedia(keyCode = KeyEvent.KEYCODE_MEDIA_PLAY, isPlaying = true),
        )
        assertEquals(
            PlayerRemoteAction.Pause,
            resolveMedia(keyCode = KeyEvent.KEYCODE_MEDIA_PAUSE, isPlaying = false),
        )
    }

    @Test
    fun `resolveMediaKey handles rewind fast forward next and previous`() {
        assertEquals(
            PlayerRemoteAction.SeekRelative(15_000L),
            resolveMedia(keyCode = KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, seekForwardMs = 15_000L),
        )
        assertEquals(
            PlayerRemoteAction.SeekRelative(-10_000L),
            resolveMedia(keyCode = KeyEvent.KEYCODE_MEDIA_REWIND, seekBackMs = 10_000L),
        )
        assertEquals(
            PlayerRemoteAction.ShowControls,
            resolveMedia(keyCode = KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, canSeek = false),
        )
        assertEquals(
            PlayerRemoteAction.ShowControls,
            resolveMedia(keyCode = KeyEvent.KEYCODE_MEDIA_REWIND, canSeek = false),
        )
        assertEquals(
            PlayerRemoteAction.NextItem,
            resolveMedia(keyCode = KeyEvent.KEYCODE_MEDIA_NEXT, hasNext = true),
        )
        assertNull(resolveMedia(keyCode = KeyEvent.KEYCODE_MEDIA_NEXT, hasNext = false))
        assertNull(
            resolveMedia(keyCode = KeyEvent.KEYCODE_MEDIA_NEXT, canSeek = false, hasNext = true),
        )
        assertEquals(
            PlayerRemoteAction.PreviousItem,
            resolveMedia(keyCode = KeyEvent.KEYCODE_MEDIA_PREVIOUS, hasPrevious = true),
        )
        assertNull(resolveMedia(keyCode = KeyEvent.KEYCODE_MEDIA_PREVIOUS, hasPrevious = false))
        assertNull(resolveMedia(keyCode = KeyEvent.KEYCODE_VOLUME_UP))
    }

    @Test
    fun `resolveRemoteNavigationKey wakes controls or skips when controls hidden`() {
        val confirmKeys =
            listOf(
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_BUTTON_A,
            )
        confirmKeys.forEach { key ->
            assertEquals(
                PlayerRemoteAction.ShowControls,
                resolveNav(keyCode = key, showControls = false),
            )
            assertEquals(
                PlayerRemoteAction.SkipPrompt,
                resolveNav(keyCode = key, showControls = false, hasSkipPrompt = true),
            )
            assertEquals(
                PlayerRemoteAction.UndoSkip,
                resolveNav(keyCode = key, showControls = false, hasUndoSkip = true),
            )
            assertEquals(
                PlayerRemoteAction.ShowControls,
                resolveNav(
                    keyCode = key,
                    showControls = false,
                    hasSkipPrompt = true,
                    hasUndoSkip = true,
                    sleepTimerExpired = true,
                ),
            )
        }

        assertEquals(
            PlayerRemoteAction.ShowControls,
            resolveNav(keyCode = KeyEvent.KEYCODE_DPAD_UP, showControls = false),
        )
        assertEquals(
            PlayerRemoteAction.ShowControls,
            resolveNav(keyCode = KeyEvent.KEYCODE_DPAD_DOWN, showControls = false),
        )
        assertEquals(
            PlayerRemoteAction.SeekRelative(-10_000L),
            resolveNav(
                keyCode = KeyEvent.KEYCODE_DPAD_LEFT,
                showControls = false,
                seekBackMs = 10_000L,
            ),
        )
        assertEquals(
            PlayerRemoteAction.SeekRelative(30_000L),
            resolveNav(
                keyCode = KeyEvent.KEYCODE_DPAD_RIGHT,
                showControls = false,
                seekForwardMs = 30_000L,
            ),
        )
        assertEquals(
            PlayerRemoteAction.ShowControls,
            resolveNav(keyCode = KeyEvent.KEYCODE_DPAD_LEFT, showControls = false, canSeek = false),
        )
        assertEquals(
            PlayerRemoteAction.ShowControls,
            resolveNav(
                keyCode = KeyEvent.KEYCODE_DPAD_RIGHT,
                showControls = false,
                canSeek = false,
            ),
        )
        assertEquals(
            PlayerRemoteAction.SeekRelative(-10_000L),
            resolveNav(
                keyCode = KeyEvent.KEYCODE_DPAD_LEFT,
                showControls = true,
                seekBackMs = 10_000L,
                isHoldRepeat = true,
            ),
        )
        assertEquals(
            PlayerRemoteAction.SeekRelative(30_000L),
            resolveNav(
                keyCode = KeyEvent.KEYCODE_DPAD_RIGHT,
                showControls = true,
                seekForwardMs = 30_000L,
                isHoldRepeat = true,
            ),
        )
        assertEquals(
            PlayerRemoteAction.KeepControlsAlive,
            resolveNav(
                keyCode = KeyEvent.KEYCODE_DPAD_LEFT,
                showControls = true,
                canSeek = false,
                isHoldRepeat = true,
            ),
        )
    }

    @Test
    fun `resolveRemoteNavigationKey keeps controls alive when controls visible`() {
        val navKeys =
            listOf(
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_BUTTON_A,
            )
        navKeys.forEach { key ->
            assertEquals(
                PlayerRemoteAction.KeepControlsAlive,
                resolveNav(keyCode = key, showControls = true, hasSkipPrompt = true),
            )
        }
        assertNull(resolveNav(keyCode = KeyEvent.KEYCODE_BACK, showControls = true))
    }

    @Test
    fun `isRepeatableRemoteKey allows seek keys outside syncplay only`() {
        assertTrue(
            PlayerRemoteKeyHandler.isRepeatableRemoteKey(
                KeyEvent.KEYCODE_DPAD_LEFT,
                isInSyncPlay = false,
            ),
        )
        assertTrue(
            PlayerRemoteKeyHandler.isRepeatableRemoteKey(
                KeyEvent.KEYCODE_DPAD_RIGHT,
                isInSyncPlay = false,
            ),
        )
        assertTrue(
            PlayerRemoteKeyHandler.isRepeatableRemoteKey(
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                isInSyncPlay = false,
            ),
        )
        assertTrue(
            PlayerRemoteKeyHandler.isRepeatableRemoteKey(
                KeyEvent.KEYCODE_MEDIA_REWIND,
                isInSyncPlay = false,
            ),
        )
        assertFalse(
            PlayerRemoteKeyHandler.isRepeatableRemoteKey(
                KeyEvent.KEYCODE_DPAD_LEFT,
                isInSyncPlay = true,
            ),
        )
        assertFalse(
            PlayerRemoteKeyHandler.isRepeatableRemoteKey(
                KeyEvent.KEYCODE_DPAD_CENTER,
                isInSyncPlay = false,
            ),
        )
    }

    @Test
    fun `resolveLockedControlsKey wakes or keeps controls alive for dpad keys only`() {
        val wakeKeys =
            listOf(
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_BUTTON_A,
            )
        wakeKeys.forEach { key ->
            assertEquals(
                PlayerRemoteAction.ShowControls,
                PlayerRemoteKeyHandler.resolveLockedControlsKey(
                    keyCode = key,
                    showControls = false,
                ),
            )
            assertEquals(
                PlayerRemoteAction.KeepControlsAlive,
                PlayerRemoteKeyHandler.resolveLockedControlsKey(keyCode = key, showControls = true),
            )
        }
        assertNull(
            PlayerRemoteKeyHandler.resolveLockedControlsKey(
                keyCode = KeyEvent.KEYCODE_BACK,
                showControls = false,
            ),
        )
    }

    @Test
    fun `isPlayPauseMediaKey matches play pause and dedicated play or pause keys`() {
        assertTrue(PlayerRemoteKeyHandler.isPlayPauseMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        assertTrue(PlayerRemoteKeyHandler.isPlayPauseMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY))
        assertTrue(PlayerRemoteKeyHandler.isPlayPauseMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE))
        assertFalse(PlayerRemoteKeyHandler.isPlayPauseMediaKey(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD))
        assertFalse(PlayerRemoteKeyHandler.isPlayPauseMediaKey(KeyEvent.KEYCODE_DPAD_CENTER))
    }

    private fun resolveMedia(
        keyCode: Int,
        isPlaying: Boolean = false,
        playWhenReady: Boolean = false,
        isEnded: Boolean = false,
        canSeek: Boolean = true,
        seekBackMs: Long = 10_000L,
        seekForwardMs: Long = 10_000L,
        hasNext: Boolean = false,
        hasPrevious: Boolean = false,
    ): PlayerRemoteAction? =
        PlayerRemoteKeyHandler.resolveMediaKey(
            keyCode = keyCode,
            isPlaying = isPlaying,
            playWhenReady = playWhenReady,
            isEnded = isEnded,
            canSeek = canSeek,
            seekBackMs = seekBackMs,
            seekForwardMs = seekForwardMs,
            hasNext = hasNext,
            hasPrevious = hasPrevious,
        )

    private fun resolveNav(
        keyCode: Int,
        showControls: Boolean,
        canSeek: Boolean = true,
        seekBackMs: Long = 10_000L,
        seekForwardMs: Long = 10_000L,
        hasSkipPrompt: Boolean = false,
        hasUndoSkip: Boolean = false,
        sleepTimerExpired: Boolean = false,
        isHoldRepeat: Boolean = false,
    ): PlayerRemoteAction? =
        PlayerRemoteKeyHandler.resolveRemoteNavigationKey(
            keyCode = keyCode,
            showControls = showControls,
            canSeek = canSeek,
            seekBackMs = seekBackMs,
            seekForwardMs = seekForwardMs,
            hasSkipPrompt = hasSkipPrompt,
            hasUndoSkip = hasUndoSkip,
            sleepTimerExpired = sleepTimerExpired,
            isHoldRepeat = isHoldRepeat,
        )
}
