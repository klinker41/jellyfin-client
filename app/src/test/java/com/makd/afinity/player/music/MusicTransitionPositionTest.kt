package com.makd.afinity.player.music

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicTransitionPositionTest {

    private fun positionInfo(
        mediaItem: MediaItem?,
        mediaItemIndex: Int,
        positionMs: Long,
    ): Player.PositionInfo =
        Player.PositionInfo(
            null,
            mediaItemIndex,
            mediaItem,
            null,
            mediaItemIndex,
            positionMs,
            positionMs,
            C.INDEX_UNSET,
            C.INDEX_UNSET,
        )

    @Test
    fun `returns outgoing position when media items are cleared`() {
        val oldItem = MediaItem.Builder().setMediaId("ep-1").build()
        val pos =
            outgoingMusicTransitionPositionMs(
                oldPosition = positionInfo(oldItem, 0, 540_000L),
                newPosition = positionInfo(null, 0, 0L),
                reason = Player.DISCONTINUITY_REASON_REMOVE,
                fallbackPositionMs = 500_000L,
            )
        assertEquals(540_000L, pos)
    }

    @Test
    fun `returns outgoing position when switching to a different media item`() {
        val oldItem = MediaItem.Builder().setMediaId("ep-1").build()
        val newItem = MediaItem.Builder().setMediaId("ep-2").build()
        val pos =
            outgoingMusicTransitionPositionMs(
                oldPosition = positionInfo(oldItem, 0, 250_000L),
                newPosition = positionInfo(newItem, 1, 0L),
                reason = Player.DISCONTINUITY_REASON_SEEK,
                fallbackPositionMs = 240_000L,
            )
        assertEquals(250_000L, pos)
    }

    @Test
    fun `returns outgoing position on auto transition even when item matches`() {
        val item = MediaItem.Builder().setMediaId("ep-1").build()
        val pos =
            outgoingMusicTransitionPositionMs(
                oldPosition = positionInfo(item, 0, 600_000L),
                newPosition = positionInfo(item, 0, 0L),
                reason = Player.DISCONTINUITY_REASON_AUTO_TRANSITION,
                fallbackPositionMs = 599_000L,
            )
        assertEquals(600_000L, pos)
    }

    @Test
    fun `returns outgoing position when seeking across indices with identical MediaItem`() {
        val item = MediaItem.Builder().setMediaId("ep-1").build()
        val pos =
            outgoingMusicTransitionPositionMs(
                oldPosition = positionInfo(item, 0, 120_000L),
                newPosition = positionInfo(item, 1, 0L),
                reason = Player.DISCONTINUITY_REASON_SEEK,
                fallbackPositionMs = 110_000L,
            )
        assertEquals(120_000L, pos)
    }

    @Test
    fun `returns null when seeking within same item and index`() {
        val item = MediaItem.Builder().setMediaId("ep-1").build()
        val pos =
            outgoingMusicTransitionPositionMs(
                oldPosition = positionInfo(item, 0, 120_000L),
                newPosition = positionInfo(item, 0, 300_000L),
                reason = Player.DISCONTINUITY_REASON_SEEK,
                fallbackPositionMs = 120_000L,
            )
        assertNull(pos)
    }

    @Test
    fun `returns null on non-transition internal discontinuity or index shift from queue rearrange`() {
        val item = MediaItem.Builder().setMediaId("ep-1").build()
        assertNull(
            outgoingMusicTransitionPositionMs(
                oldPosition = positionInfo(item, 1, 120_000L),
                newPosition = positionInfo(item, 0, 120_000L),
                reason = Player.DISCONTINUITY_REASON_INTERNAL,
                fallbackPositionMs = 120_000L,
            ),
        )
    }

    @Test
    fun `returns null on initial load when old mediaItem is null`() {
        val newItem = MediaItem.Builder().setMediaId("ep-1").build()
        val pos =
            outgoingMusicTransitionPositionMs(
                oldPosition = positionInfo(null, 0, 0L),
                newPosition = positionInfo(newItem, 0, 0L),
                reason = Player.DISCONTINUITY_REASON_REMOVE,
                fallbackPositionMs = 0L,
            )
        assertNull(pos)
    }

    @Test
    fun `falls back to fallbackPositionMs when old positionMs is negative or unset`() {
        val oldItem = MediaItem.Builder().setMediaId("ep-1").build()
        val pos =
            outgoingMusicTransitionPositionMs(
                oldPosition = positionInfo(oldItem, 0, C.TIME_UNSET),
                newPosition = positionInfo(null, 0, 0L),
                reason = Player.DISCONTINUITY_REASON_REMOVE,
                fallbackPositionMs = 420_000L,
            )
        assertEquals(420_000L, pos)
    }
}
