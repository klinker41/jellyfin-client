package com.makd.afinity.data.database

import com.makd.afinity.data.models.jellyseerr.SeasonRequest
import com.makd.afinity.data.models.lastfm.LastFmTrackPopularity
import com.makd.afinity.data.models.mdblist.MdbListRating
import com.makd.afinity.data.models.mdblist.MdbListRatingBadges
import com.makd.afinity.data.models.media.AfinityChapter
import com.makd.afinity.data.models.media.AfinityExternalUrl
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityMusicVideoArtist
import com.makd.afinity.data.models.media.AfinityPerson
import com.makd.afinity.data.models.media.AfinityPersonImage
import com.makd.afinity.data.models.media.AfinitySegmentType
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.AfinitySourceType
import com.makd.afinity.data.models.media.AfinityStudio
import com.makd.afinity.data.models.tmdb.TmdbAuthorDetails
import com.makd.afinity.data.models.tmdb.TmdbRegionProviders
import com.makd.afinity.data.models.tmdb.TmdbReview
import com.makd.afinity.data.models.tmdb.TmdbWatchProvider
import com.makd.afinity.data.models.wikidata.AwardResult
import com.makd.afinity.data.models.wikidata.WikidataAward
import com.makd.afinity.testutil.TestMediaFixtures.testMovie
import com.makd.afinity.testutil.TestMediaFixtures.testSeason
import com.makd.afinity.testutil.TestMediaFixtures.testShow
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import java.time.LocalDateTime
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PersonKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AfinityTypeConvertersTest {

    private val converters = AfinityTypeConverters()

    @Test
    fun primitiveDateUuidAndEnumConvertersRoundTripAndRejectInvalidValues() {
        val id = testUuid(7)
        assertEquals(id, converters.toUUID(converters.fromUUID(id)))
        assertNull(converters.toUUID(converters.fromUUID(null)))

        val now = LocalDateTime.of(2026, 5, 14, 21, 30, 0)
        assertEquals(now, converters.toLocalDateTime(converters.fromLocalDateTime(now)))
        assertNull(converters.toLocalDateTime(converters.fromLocalDateTime(null)))

        assertEquals(
            AfinitySourceType.LOCAL,
            converters.toSourceType(converters.fromSourceType(AfinitySourceType.LOCAL)),
        )
        assertNull(converters.toSourceType("BOGUS"))

        assertEquals(
            AfinitySegmentType.INTRO,
            converters.toSegmentType(converters.fromSegmentType(AfinitySegmentType.INTRO)),
        )
        assertNull(converters.toSegmentType("BOGUS"))

        assertEquals(
            MediaStreamType.SUBTITLE,
            converters.toMediaStreamType(converters.fromMediaStreamType(MediaStreamType.SUBTITLE)),
        )
        assertNull(converters.toMediaStreamType("BOGUS"))
        assertNull(converters.fromUri(null))
        assertNull(converters.toUri(null))
    }

    @Test
    fun collectionAndNestedModelConvertersRoundTripAndReturnNullOnMalformedJson() {
        val chapters = listOf(AfinityChapter(1000L, "Opening", 0, "tag1"))
        assertEquals(chapters, converters.toChapterList(converters.fromChapterList(chapters)))
        assertNull(converters.toChapterList("not-json"))

        val strings = listOf("Sci-Fi", "Thriller")
        assertEquals(strings, converters.toStringList(converters.fromStringList(strings)))
        assertNull(converters.toStringList("{bad"))

        val stringMap = mapOf("imdb" to "tt123456")
        assertEquals(stringMap, converters.toStringMap(converters.fromStringMap(stringMap)))
        assertNull(converters.toStringMap("not-map"))

        val urls = listOf(AfinityExternalUrl("TMDB", "https://tmdb.org/1"))
        assertEquals(urls, converters.toExternalUrlList(converters.fromExternalUrlList(urls)))
        assertNull(converters.toExternalUrlList("[bad]"))

        val mvArtists = listOf(AfinityMusicVideoArtist(testUuid(3), "Artist"))
        assertEquals(
            mvArtists,
            converters.toMusicVideoArtistList(converters.fromMusicVideoArtistList(mvArtists)),
        )
        assertNull(converters.toMusicVideoArtistList("bad"))

        val images = AfinityImages(primaryImageBlurHash = "L6PZfSi_.AyE_3t7t7R**0o#DgR4")
        assertEquals(images, converters.toAfinityImages(converters.fromAfinityImages(images)))
        assertNull(converters.toAfinityImages("bad"))

        val people =
            listOf(
                AfinityPerson(
                    id = testUuid(4),
                    name = "Lead",
                    type = PersonKind.ACTOR,
                    role = "Hero",
                    image = AfinityPersonImage(uri = null, blurHash = "blur"),
                ),
            )
        assertEquals(
            people,
            converters.toAfinityPersonList(converters.fromAfinityPersonList(people)),
        )
        assertNull(converters.toAfinityPersonList("bad"))

        val studio = AfinityStudio(testUuid(5), "A24", "https://img/a24.png", 12)
        assertEquals(studio, converters.toAfinityStudio(converters.fromAfinityStudio(studio)))
        assertNull(converters.toAfinityStudio("bad"))
    }

    @Test
    fun movieShowAndPolymorphicItemConvertersPreservePrefixedAndLegacyFormats() {
        val movie =
            testMovie(
                id = testUuid(11),
                name = "Dune",
                overview = "Desert planet",
                played = true,
                favorite = true,
                images = AfinityImages(backdropImageBlurHash = "hash"),
            )
        val roundTrippedMovie = converters.toAfinityMovie(converters.fromAfinityMovie(movie))
        assertEquals("Dune", roundTrippedMovie?.name)
        assertEquals("Desert planet", roundTrippedMovie?.overview)
        assertTrue(roundTrippedMovie?.played == true)
        assertTrue(roundTrippedMovie?.favorite == true)
        assertEquals("hash", roundTrippedMovie?.images?.backdropImageBlurHash)
        assertNull(converters.toAfinityMovie("bad"))

        val show = testShow(id = testUuid(12), name = "Severance", overview = "Lumon")
        val roundTrippedShow = converters.toAfinityShow(converters.fromAfinityShow(show))
        assertEquals("Severance", roundTrippedShow?.name)
        assertEquals("Lumon", roundTrippedShow?.overview)
        assertNull(converters.toAfinityShow("bad"))

        val movieEncoded = converters.fromAfinityItem(movie)!!
        val showEncoded = converters.fromAfinityItem(show)!!
        assertTrue(movieEncoded.startsWith("MOVIE::"))
        assertTrue(showEncoded.startsWith("SHOW::"))
        assertEquals("Dune", (converters.toAfinityItem(movieEncoded) as? AfinityMovie)?.name)
        assertEquals("Severance", (converters.toAfinityItem(showEncoded) as? AfinityShow)?.name)
        // Legacy unprefixed payload falls back to movie deserialization.
        assertEquals("Dune", converters.toAfinityItem(converters.fromAfinityMovie(movie))?.name)
        // Unsupported subtypes encode to null.
        assertNull(converters.fromAfinityItem(testSeason()))
        assertNull(converters.toAfinityItem(null))
    }

    @Test
    fun metadataAndExternalApiConvertersRoundTripAndRejectMalformedInput() {
        val seasons = listOf(SeasonRequest(id = 1, seasonNumber = 2, status = 3))
        assertEquals(
            seasons,
            converters.toSeasonRequestList(converters.fromSeasonRequestList(seasons)),
        )
        assertNull(converters.toSeasonRequestList("bad"))

        val reviews =
            listOf(
                TmdbReview(
                    id = "r1",
                    author = "Alex",
                    content = "Great",
                    author_details = TmdbAuthorDetails(rating = 9.0),
                ),
            )
        assertEquals(reviews, converters.toTmdbReviewList(converters.fromTmdbReviewList(reviews)))
        assertNull(converters.toTmdbReviewList("bad"))

        val providers =
            TmdbRegionProviders(
                region = "US",
                flatrate = listOf(TmdbWatchProvider(8, "Netflix", "/n.png", 1)),
            )
        assertEquals(
            providers,
            converters.toTmdbRegionProviders(converters.fromTmdbRegionProviders(providers)),
        )
        assertNull(converters.toTmdbRegionProviders("bad"))

        val ratings = listOf(MdbListRating(source = "imdb", value = 8.4, score = 84.0))
        assertEquals(ratings, converters.toMdbRatingList(converters.fromMdbRatingList(ratings)))
        assertNull(converters.toMdbRatingList("bad"))

        val badges = MdbListRatingBadges(certifiedFresh = true, verifiedHot = false)
        assertEquals(badges, converters.toMdbRatingBadges(converters.fromMdbRatingBadges(badges)))
        assertNull(converters.toMdbRatingBadges("bad"))

        val awards = listOf(WikidataAward("Oscar", AwardResult.WON, 2024))
        assertEquals(
            awards,
            converters.toWikidataAwardList(converters.fromWikidataAwardList(awards)),
        )
        assertNull(converters.toWikidataAwardList("bad"))

        val tracks = listOf(LastFmTrackPopularity("Time", 1_500_000L))
        assertEquals(
            tracks,
            converters.toLastFmTrackPopularityList(
                converters.fromLastFmTrackPopularityList(tracks),
            ),
        )
        assertNull(converters.toLastFmTrackPopularityList("bad"))
    }
}
