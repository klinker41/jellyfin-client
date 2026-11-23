package com.makd.afinity.data.autodownload

import com.makd.afinity.data.manager.MediaChangeEvent
import com.makd.afinity.data.manager.MediaChangeSource
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.models.media.UserDataPatch
import com.makd.afinity.testutil.TestMediaFixtures.mockUserItemDataDto
import com.makd.afinity.testutil.TestMediaFixtures.testCollection
import com.makd.afinity.testutil.TestMediaFixtures.testMovie
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoDownloadPolicyTest {

    @Test
    fun `autoDownloadableLibraries filters supported library types and excludes unsupported ones`() {
        val books =
            testCollection(id = testUuid(1), name = "Audiobooks", type = CollectionType.Books)
        val music = testCollection(id = testUuid(2), name = "Podcasts", type = CollectionType.Music)
        val movies = testCollection(id = testUuid(3), name = "Movies", type = CollectionType.Movies)
        val shows =
            testCollection(id = testUuid(4), name = "TV Shows", type = CollectionType.TvShows)
        val homeVideos =
            testCollection(id = testUuid(5), name = "Home Videos", type = CollectionType.HomeVideos)
        val musicVideos =
            testCollection(
                id = testUuid(6),
                name = "Music Videos",
                type = CollectionType.MusicVideos,
            )
        val mixed = testCollection(id = testUuid(7), name = "Mixed", type = CollectionType.Mixed)
        val liveTv =
            testCollection(id = testUuid(8), name = "Live TV", type = CollectionType.LiveTv)
        val photos = testCollection(id = testUuid(9), name = "Photos", type = CollectionType.Photos)
        val boxSets =
            testCollection(id = testUuid(10), name = "Collections", type = CollectionType.BoxSets)
        val playlists =
            testCollection(id = testUuid(11), name = "Playlists", type = CollectionType.Playlists)
        val unknown =
            testCollection(id = testUuid(12), name = "Unknown", type = CollectionType.Unknown)

        val filtered =
            listOf(
                    books,
                    music,
                    movies,
                    shows,
                    homeVideos,
                    musicVideos,
                    mixed,
                    liveTv,
                    photos,
                    boxSets,
                    playlists,
                    unknown,
                )
                .autoDownloadableLibraries()

        assertEquals(
            listOf(books, music, movies, shows, homeVideos, musicVideos, mixed),
            filtered,
        )
    }

    @Test
    fun `autoDownloadItemTypes maps every CollectionType`() {
        assertEquals(listOf("AUDIO_BOOK", "BOOK"), CollectionType.Books.autoDownloadItemTypes())
        assertEquals(listOf("AUDIO"), CollectionType.Music.autoDownloadItemTypes())
        assertEquals(listOf("MOVIE"), CollectionType.Movies.autoDownloadItemTypes())
        assertEquals(listOf("EPISODE"), CollectionType.TvShows.autoDownloadItemTypes())
        assertEquals(listOf("VIDEO"), CollectionType.HomeVideos.autoDownloadItemTypes())
        assertEquals(listOf("MUSIC_VIDEO"), CollectionType.MusicVideos.autoDownloadItemTypes())
        assertEquals(
            listOf("MOVIE", "EPISODE", "VIDEO", "MUSIC_VIDEO", "AUDIO", "AUDIO_BOOK", "BOOK"),
            CollectionType.Mixed.autoDownloadItemTypes(),
        )
        assertEquals(emptyList<String>(), CollectionType.LiveTv.autoDownloadItemTypes())
        assertEquals(emptyList<String>(), CollectionType.Photos.autoDownloadItemTypes())
        assertEquals(emptyList<String>(), CollectionType.BoxSets.autoDownloadItemTypes())
        assertEquals(emptyList<String>(), CollectionType.Playlists.autoDownloadItemTypes())
        assertEquals(emptyList<String>(), CollectionType.Unknown.autoDownloadItemTypes())
    }

    @Test
    fun `isEligibleForAutoDownload includes unplayed and in-progress items`() {
        assertTrue(isEligibleForAutoDownload(played = false, playbackPositionTicks = 0L))
        assertTrue(isEligibleForAutoDownload(played = false, playbackPositionTicks = 50_000L))
        assertTrue(isEligibleForAutoDownload(played = true, playbackPositionTicks = 50_000L))
        assertFalse(isEligibleForAutoDownload(played = true, playbackPositionTicks = 0L))
    }

    @Test
    fun `shouldSkipExistingDownload respects status and failed retry cooldown`() {
        val now = 1_000_000_000L
        assertFalse(shouldSkipExistingDownload(null, 0L, now))
        assertFalse(shouldSkipExistingDownload(DownloadStatus.CANCELLED, 0L, now))
        assertTrue(shouldSkipExistingDownload(DownloadStatus.COMPLETED, 0L, now))
        assertTrue(shouldSkipExistingDownload(DownloadStatus.DOWNLOADING, 0L, now))
        assertTrue(shouldSkipExistingDownload(DownloadStatus.QUEUED, 0L, now))
        assertTrue(shouldSkipExistingDownload(DownloadStatus.PAUSED, 0L, now))

        assertTrue(
            shouldSkipExistingDownload(
                DownloadStatus.FAILED,
                updatedAt = now - FAILED_RETRY_COOLDOWN_MS + 1L,
                nowMs = now,
            ),
        )
        assertFalse(
            shouldSkipExistingDownload(
                DownloadStatus.FAILED,
                updatedAt = now - FAILED_RETRY_COOLDOWN_MS,
                nowMs = now,
            ),
        )
    }

    @Test
    fun `isMarkedPlayedEvent handles patch, userData, updatedItem, and in-progress edge cases`() {
        val id = testUuid(1)
        assertTrue(
            isMarkedPlayedEvent(
                MediaChangeEvent(
                    itemId = id,
                    source = MediaChangeSource.MANUAL,
                    patch = UserDataPatch(played = true, playbackPositionTicks = 0L),
                ),
            ),
        )
        assertFalse(
            isMarkedPlayedEvent(
                MediaChangeEvent(
                    itemId = id,
                    source = MediaChangeSource.MANUAL,
                    patch = UserDataPatch(played = true, playbackPositionTicks = 123_000L),
                ),
            ),
        )
        assertTrue(
            isMarkedPlayedEvent(
                MediaChangeEvent(
                    itemId = id,
                    source = MediaChangeSource.WEBSOCKET,
                    userData = mockUserItemDataDto(played = true, playbackPositionTicks = 0L),
                ),
            ),
        )
        assertFalse(
            isMarkedPlayedEvent(
                MediaChangeEvent(
                    itemId = id,
                    source = MediaChangeSource.WEBSOCKET,
                    userData = mockUserItemDataDto(played = true, playbackPositionTicks = 999L),
                ),
            ),
        )
        assertTrue(
            isMarkedPlayedEvent(
                MediaChangeEvent(
                    itemId = id,
                    source = MediaChangeSource.PLAYBACK,
                    updatedItem = testMovie(id = id, played = true),
                ),
            ),
        )
        assertFalse(
            isMarkedPlayedEvent(
                MediaChangeEvent(
                    itemId = id,
                    source = MediaChangeSource.PLAYBACK,
                    updatedItem =
                        testMovie(id = id, played = true, playbackPositionTicks = 500_000L),
                ),
            ),
        )
        assertFalse(
            isMarkedPlayedEvent(
                MediaChangeEvent(
                    itemId = id,
                    source = MediaChangeSource.PLAYBACK,
                    updatedItem = testMovie(id = id, played = false),
                ),
            ),
        )
        assertTrue(
            isMarkedUnplayedEvent(
                MediaChangeEvent(
                    itemId = id,
                    source = MediaChangeSource.MANUAL,
                    patch = UserDataPatch(played = false, playbackPositionTicks = 0L),
                ),
            ),
        )
        assertFalse(
            isMarkedUnplayedEvent(
                MediaChangeEvent(
                    itemId = id,
                    source = MediaChangeSource.MANUAL,
                    patch = UserDataPatch(played = true, playbackPositionTicks = 0L),
                ),
            ),
        )
    }
}
