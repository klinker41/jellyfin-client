package com.makd.afinity.ui.player

import android.view.KeyEvent

sealed interface PlayerRemoteAction {
    data object Play : PlayerRemoteAction

    data object Pause : PlayerRemoteAction

    data class SeekRelative(val deltaMs: Long) : PlayerRemoteAction

    data object NextItem : PlayerRemoteAction

    data object PreviousItem : PlayerRemoteAction

    data object SkipPrompt : PlayerRemoteAction

    data object UndoSkip : PlayerRemoteAction

    data object ShowControls : PlayerRemoteAction

    data object KeepControlsAlive : PlayerRemoteAction
}

object PlayerRemoteKeyHandler {

    private val repeatableSeekKeys =
        setOf(
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_REWIND,
        )

    fun resolveMediaKey(
        keyCode: Int,
        isPlaying: Boolean,
        playWhenReady: Boolean,
        isEnded: Boolean,
        canSeek: Boolean,
        seekBackMs: Long,
        seekForwardMs: Long,
        hasNext: Boolean,
        hasPrevious: Boolean,
    ): PlayerRemoteAction? =
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ->
                if (isPlaying || (playWhenReady && !isEnded)) {
                    PlayerRemoteAction.Pause
                } else {
                    PlayerRemoteAction.Play
                }

            KeyEvent.KEYCODE_MEDIA_PLAY -> PlayerRemoteAction.Play
            KeyEvent.KEYCODE_MEDIA_PAUSE -> PlayerRemoteAction.Pause
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD ->
                if (canSeek) PlayerRemoteAction.SeekRelative(seekForwardMs)
                else PlayerRemoteAction.ShowControls

            KeyEvent.KEYCODE_MEDIA_REWIND ->
                if (canSeek) PlayerRemoteAction.SeekRelative(-seekBackMs)
                else PlayerRemoteAction.ShowControls

            KeyEvent.KEYCODE_MEDIA_NEXT ->
                if (canSeek && hasNext) PlayerRemoteAction.NextItem else null

            KeyEvent.KEYCODE_MEDIA_PREVIOUS ->
                if (canSeek && hasPrevious) PlayerRemoteAction.PreviousItem else null

            else -> null
        }

    fun resolveRemoteNavigationKey(
        keyCode: Int,
        showControls: Boolean,
        canSeek: Boolean,
        seekBackMs: Long,
        seekForwardMs: Long,
        hasSkipPrompt: Boolean,
        hasUndoSkip: Boolean,
        sleepTimerExpired: Boolean,
        isHoldRepeat: Boolean = false,
    ): PlayerRemoteAction? =
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BUTTON_A ->
                when {
                    showControls -> PlayerRemoteAction.KeepControlsAlive
                    !sleepTimerExpired && hasSkipPrompt -> PlayerRemoteAction.SkipPrompt
                    !sleepTimerExpired && hasUndoSkip -> PlayerRemoteAction.UndoSkip
                    else -> PlayerRemoteAction.ShowControls
                }

            KeyEvent.KEYCODE_DPAD_LEFT ->
                when {
                    canSeek && (isHoldRepeat || !showControls) ->
                        PlayerRemoteAction.SeekRelative(-seekBackMs)
                    showControls -> PlayerRemoteAction.KeepControlsAlive
                    else -> PlayerRemoteAction.ShowControls
                }

            KeyEvent.KEYCODE_DPAD_RIGHT ->
                when {
                    canSeek && (isHoldRepeat || !showControls) ->
                        PlayerRemoteAction.SeekRelative(seekForwardMs)
                    showControls -> PlayerRemoteAction.KeepControlsAlive
                    else -> PlayerRemoteAction.ShowControls
                }

            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN ->
                if (showControls) PlayerRemoteAction.KeepControlsAlive
                else PlayerRemoteAction.ShowControls

            else -> null
        }

    fun isRepeatableRemoteKey(keyCode: Int, isInSyncPlay: Boolean): Boolean =
        !isInSyncPlay && keyCode in repeatableSeekKeys
}
