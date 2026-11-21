package com.makd.afinity.data.models.media

import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.mdblist.MdbListRatingBadges
import com.makd.afinity.data.models.player.MusicQuality
import com.makd.afinity.data.models.tmdb.TmdbRegionProviders
import com.makd.afinity.data.models.tmdb.TmdbWatchProvider
import com.makd.afinity.data.models.tmdb.TmdbWatchProvidersResponse
import com.makd.afinity.data.models.user.AfinityUserDataDto
import com.makd.afinity.data.models.wikidata.AwardResult
import com.makd.afinity.data.models.wikidata.WikidataAward
import com.makd.afinity.data.models.wikidata.WikidataAwards
import com.makd.afinity.data.models.wikidata.WikidataSubjectType
import com.makd.afinity.testutil.TestMediaFixtures.mockUserItemDataDto
import com.makd.afinity.testutil.TestMediaFixtures.testAlbum
import com.makd.afinity.testutil.TestMediaFixtures.testArtist
import com.makd.afinity.testutil.TestMediaFixtures.testMovie
import com.makd.afinity.testutil.TestMediaFixtures.testSource
import com.makd.afinity.testutil.TestMediaFixtures.testStream
import com.makd.afinity.testutil.TestMediaFixtures.testTrack
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.VideoRangeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaModelsTest {

    @Test
    fun userDataPatchAndExtensionsApplyAcrossMoviesMusicAndLists() {
        val id1 = testUuid(1)
        val id2 = testUuid(2)
        val movie = testMovie(id = id1, played = false, favorite = false, liked = false)
        val samePatch = UserDataPatch(played = false)
        assertSame(movie, movie.withUserDataPatch(samePatch))

        val patched =
            movie.withUserDataPatch(
                UserDataPatch(played = true, favorite = true, playbackPositionTicks = 1234L),
            ) as AfinityMovie
        assertTrue(patched.played)
        assertTrue(patched.favorite)
        assertFalse(patched.liked)
        assertEquals(1234L, patched.playbackPositionTicks)

        val dto =
            mockUserItemDataDto(
                played = true,
                isFavorite = false,
                likes = true,
                playbackPositionTicks = 999L,
                playCount = 4,
            )
        val track = testTrack(id = id1).withUserData(dto)
        val album = testAlbum(id = id1).withUserData(dto)
        val artist = testArtist(id = id1).withUserData(dto)
        assertTrue(track.played && track.liked && track.playCount == 4)
        assertTrue(album.played && album.liked && album.playCount == 4)
        assertTrue(artist.played && artist.liked)

        val localDto =
            AfinityUserDataDto(
                userId = testUuid(9),
                itemId = id1,
                serverId = "srv",
                played = false,
                favorite = true,
                likes = true,
                playbackPositionTicks = 50L,
                unplayedItemCount = 7,
            )
        val fromLocal = movie.withUserData(localDto) as AfinityMovie
        assertTrue(fromLocal.favorite && fromLocal.liked)
        assertEquals(7, fromLocal.unplayedItemCount)

        val list = listOf(movie, testMovie(id = id2))
        assertSame(list, list.withUserData(testUuid(999), dto))
        val updatedList = list.withUserData(id1, dto)
        assertTrue(updatedList[0].played)
        assertFalse(updatedList[1].played)
    }

    @Test
    fun downloadStateChecksDistinguishInProgressFromCompletedLocalSources() {
        val downloading =
            testMovie(
                sources =
                    listOf(testSource(type = AfinitySourceType.LOCAL, path = "/dl/a.download")),
            )
        val downloaded =
            testMovie(
                sources = listOf(testSource(type = AfinitySourceType.LOCAL, path = "/dl/a.mkv")),
            )
        val remoteOnly =
            testMovie(
                sources =
                    listOf(testSource(type = AfinitySourceType.REMOTE, path = "/remote/a.mkv")),
            )

        assertTrue(downloading.isDownloading())
        assertFalse(downloading.isDownloaded())
        assertFalse(downloaded.isDownloading())
        assertTrue(downloaded.isDownloaded())
        assertFalse(remoteOnly.isDownloading())
        assertFalse(remoteOnly.isDownloaded())
    }

    @Test
    fun mediaStreamDolbyVisionAndHdrLabelDetectAllFormats() {
        val doviTitleOnly = testStream(0, MediaStreamType.VIDEO, videoDoViTitle = "Profile 8.1")
        val doviRange =
            testStream(
                1,
                MediaStreamType.VIDEO,
                videoRangeType = VideoRangeType.DOVI_WITH_HDR10_PLUS,
            )
        val hdr10PlusViaFlag =
            testStream(
                2,
                MediaStreamType.VIDEO,
                videoRangeType = VideoRangeType.HDR10,
                hdr10PlusPresentFlag = true,
            )
        val hdr10 = testStream(3, MediaStreamType.VIDEO, videoRangeType = VideoRangeType.HDR10)
        val hlg = testStream(4, MediaStreamType.VIDEO, videoRangeType = VideoRangeType.HLG)
        val sdr = testStream(5, MediaStreamType.VIDEO, videoRangeType = VideoRangeType.SDR)

        assertTrue(doviTitleOnly.isDolbyVision())
        assertTrue(doviRange.isDolbyVision())
        assertFalse(hdr10.isDolbyVision())

        assertEquals("HDR10+", hdr10PlusViaFlag.hdrLabel())
        assertEquals("HDR10", hdr10.hdrLabel())
        assertEquals("HLG", hlg.hdrLabel())
        assertNull(sdr.hdrLabel())
    }

    @Test
    fun mergeOverlappingSegmentsMergesAdjacentSameTypeAndPreservesDistinctIntervals() {
        val segments =
            listOf(
                AfinitySegment(AfinitySegmentType.OUTRO, 900L, 1000L),
                AfinitySegment(AfinitySegmentType.INTRO, 100L, 200L),
                AfinitySegment(AfinitySegmentType.INTRO, 200L, 350L),
                AfinitySegment(AfinitySegmentType.INTRO, 50L, 120L),
                AfinitySegment(AfinitySegmentType.INTRO, 500L, 600L),
            )

        assertEquals(
            listOf(
                AfinitySegment(AfinitySegmentType.INTRO, 50L, 350L),
                AfinitySegment(AfinitySegmentType.INTRO, 500L, 600L),
                AfinitySegment(AfinitySegmentType.OUTRO, 900L, 1000L),
            ),
            segments.mergeOverlapping(),
        )
    }

    @Test
    fun chapterImageUrlCollectionTypeMusicQualityAwardsAndProvidersWorkCleanly() {
        val id = testUuid(1)
        assertEquals(
            "/local/ch1.jpg",
            AfinityChapter(1000L, imageIndex = 0, localImagePath = "/local/ch1.jpg")
                .getChapterImageUrl("https://jf", id),
        )
        assertEquals(
            "https://jf/Items/$id/Images/Chapter/2?tag=abc",
            AfinityChapter(2000L, imageIndex = 2, imageTag = "abc")
                .getChapterImageUrl("https://jf", id),
        )
        assertNull(AfinityChapter(3000L).getChapterImageUrl("https://jf", id))

        assertEquals(CollectionType.Mixed, CollectionType.fromString(null))
        assertEquals(CollectionType.TvShows, CollectionType.fromString("tvshows"))
        assertEquals(CollectionType.Unknown, CollectionType.fromString("something_else"))

        assertTrue(MusicQuality.fromBitrate(null).isOriginal)
        assertTrue(MusicQuality.fromBitrate(MusicQuality.ORIGINAL_BITRATE).isOriginal)
        assertEquals(MusicQuality.ORIGINAL_BITRATE, MusicQuality.ORIGINAL.streamingBitrate)
        assertEquals(134_400, MusicQuality.fromBitrate(128_000).streamingBitrate)
        assertEquals(7, MusicQuality.options().size)

        val awards =
            WikidataAwards(
                awards =
                    listOf(
                        WikidataAward("Oscar", AwardResult.WON, 2024),
                        WikidataAward("BAFTA", AwardResult.NOMINATED, 2024),
                    ),
                confirmed = true,
            )
        assertEquals(1, awards.wins)
        assertEquals(1, awards.nominations)
        assertTrue(awards.found)
        assertEquals(WikidataSubjectType.TV, WikidataSubjectType.fromValue("tv"))
        assertNull(WikidataSubjectType.fromValue("album"))

        assertFalse(MdbListRatingBadges().hasAny)
        assertTrue(MdbListRatingBadges(certifiedFresh = true).hasAny)

        val provider = TmdbWatchProvider(8, "Netflix", "/nf.png")
        assertEquals("https://image.tmdb.org/t/p/w154/nf.png", provider.logoUrl())
        val response =
            TmdbWatchProvidersResponse(
                results =
                    mapOf(
                        "US" to TmdbRegionProviders(flatrate = listOf(provider)),
                        "FR" to TmdbRegionProviders(),
                    ),
            )
        assertEquals("US", response.forRegion("US")?.region)
        assertNull(response.forRegion("FR"))
    }
}
