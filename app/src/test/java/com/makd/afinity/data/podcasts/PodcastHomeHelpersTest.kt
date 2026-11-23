package com.makd.afinity.data.podcasts

import android.app.Application
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.extensions.toAfinityTrack
import com.makd.afinity.data.models.media.AfinityAudiobook
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.testutil.TestMediaFixtures.testCollection
import com.makd.afinity.testutil.TestMediaFixtures.testTrack
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import java.time.LocalDateTime
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class PodcastHomeHelpersTest {

    private fun testAudiobook(
        id: UUID,
        name: String,
        played: Boolean = false,
        playbackPositionTicks: Long = 1_000_000L,
        lastPlayedDate: LocalDateTime? = null,
    ): AfinityAudiobook =
        AfinityAudiobook(
            id = id,
            name = name,
            originalTitle = null,
            overview = "",
            played = played,
            favorite = false,
            liked = false,
            canPlay = true,
            canDownload = false,
            runtimeTicks = 10_000_000_000L,
            playbackPositionTicks = playbackPositionTicks,
            unplayedItemCount = null,
            images = AfinityImages(),
            providerIds = null,
            externalUrls = null,
            album = null,
            people = emptyList(),
            publisher = null,
            premiereDate = null,
            productionYear = null,
            genres = emptyList(),
            communityRating = null,
            dateCreated = null,
            parentId = null,
            trackNumber = null,
            discNumber = null,
            container = null,
            lastPlayedDate = lastPlayedDate,
        )

    @Test
    fun `isPodcastLibrary matches music libraries with podcast in name`() {
        assertTrue(
            testCollection(name = "Podcasts", type = CollectionType.Music).isPodcastLibrary(),
        )
        assertTrue(
            testCollection(name = "My Podcast Feed", type = CollectionType.Music)
                .isPodcastLibrary(),
        )
        assertFalse(testCollection(name = "Music", type = CollectionType.Music).isPodcastLibrary())
        assertFalse(
            testCollection(name = "Podcasts", type = CollectionType.Books).isPodcastLibrary(),
        )
    }

    @Test
    fun `mergeContinueListening interleaves, filters, deduplicates, and sorts by lastPlayedDate`() {
        val t1 = LocalDateTime.of(2026, 11, 20, 10, 0)
        val t2 = LocalDateTime.of(2026, 11, 21, 10, 0)
        val t3 = LocalDateTime.of(2026, 11, 22, 10, 0)

        val bookOld = testAudiobook(testUuid(1), "Book Old", lastPlayedDate = t1)
        val bookPlayed =
            testAudiobook(testUuid(2), "Book Played", played = true, lastPlayedDate = t3)
        val bookZero =
            testAudiobook(testUuid(3), "Book Zero", playbackPositionTicks = 0L, lastPlayedDate = t3)
        val bookNoDate = testAudiobook(testUuid(4), "Book NoDate", lastPlayedDate = null)

        val podNewest =
            testTrack(id = testUuid(10), name = "Pod Newest", playbackPositionTicks = 500_000L)
                .copy(lastPlayedDate = t3)
        val podMid =
            testTrack(id = testUuid(11), name = "Pod Mid", playbackPositionTicks = 500_000L)
                .copy(lastPlayedDate = t2)
        val podDup =
            testTrack(id = testUuid(11), name = "Pod Dup", playbackPositionTicks = 500_000L)
                .copy(lastPlayedDate = t1)
        val podPlayed =
            testTrack(
                    id = testUuid(12),
                    name = "Pod Played",
                    played = true,
                    playbackPositionTicks = 500_000L,
                )
                .copy(lastPlayedDate = t3)

        val merged =
            mergeContinueListening(
                books = listOf(bookOld, bookPlayed, bookZero, bookNoDate),
                podcasts = listOf(podNewest, podMid, podDup, podPlayed),
                limit = 4,
            )

        assertEquals(
            listOf(testUuid(10), testUuid(11), testUuid(1), testUuid(4)),
            merged.map { it.id },
        )
    }

    @Test
    fun `toAfinityTrack prefers episode embedded primary image over album image`() {
        val epId = testUuid(20)
        val albumId = testUuid(21)
        val playedAt = LocalDateTime.of(2026, 11, 23, 8, 30)
        val dtoWithEpisodeArt =
            BaseItemDto(
                id = epId,
                type = BaseItemKind.AUDIO,
                name = "Episode With Art",
                albumId = albumId,
                albumPrimaryImageTag = "album-tag",
                imageTags = mapOf(ImageType.PRIMARY to "ep-tag"),
                imageBlurHashes =
                    mapOf(
                        ImageType.PRIMARY to
                            mapOf("ep-tag" to "ep-blur", "album-tag" to "album-blur"),
                    ),
                userData =
                    UserItemDataDto(
                        playbackPositionTicks = 123_000L,
                        playCount = 1,
                        isFavorite = false,
                        played = false,
                        key = epId.toString(),
                        itemId = epId,
                        lastPlayedDate = playedAt,
                    ),
            )

        val trackWithArt = dtoWithEpisodeArt.toAfinityTrack("https://jf.example.com")
        assertEquals(
            "https://jf.example.com/Items/$epId/Images/Primary?tag=ep-tag",
            trackWithArt.images.primary.toString(),
        )
        assertEquals("ep-blur", trackWithArt.images.primaryImageBlurHash)
        assertEquals(playedAt, trackWithArt.lastPlayedDate)

        val dtoFallback =
            dtoWithEpisodeArt.copy(
                imageTags = emptyMap(),
                imageBlurHashes = mapOf(ImageType.PRIMARY to mapOf("album-tag" to "album-blur")),
            )
        val trackFallback = dtoFallback.toAfinityTrack("https://jf.example.com")
        assertEquals(
            "https://jf.example.com/Items/$albumId/Images/Primary?tag=album-tag",
            trackFallback.images.primary.toString(),
        )
        assertEquals("album-blur", trackFallback.images.primaryImageBlurHash)
    }
}
