package com.makd.afinity.player.music

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
internal fun outgoingMusicTransitionPositionMs(
    oldPosition: Player.PositionInfo,
    newPosition: Player.PositionInfo,
    reason: Int,
    fallbackPositionMs: Long,
): Long? {
    if (oldPosition.mediaItem == null) return null
    val isMediaItemTransition =
        oldPosition.mediaItem != newPosition.mediaItem ||
            reason == Player.DISCONTINUITY_REASON_REMOVE ||
            reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION ||
            (reason == Player.DISCONTINUITY_REASON_SEEK &&
                oldPosition.mediaItemIndex != newPosition.mediaItemIndex)
    if (!isMediaItemTransition) return null
    return oldPosition.positionMs.takeIf { it >= 0L } ?: fallbackPositionMs.coerceAtLeast(0L)
}
