package com.makd.afinity.testutil

import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityExternalUrl
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.media.AfinityMediaStream
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityPerson
import com.makd.afinity.data.models.media.AfinityPersonImage
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.AfinitySource
import com.makd.afinity.data.models.media.AfinitySourceType
import com.makd.afinity.data.models.music.AfinityAlbum
import com.makd.afinity.data.models.music.AfinityArtist
import com.makd.afinity.data.models.music.AfinityTrack
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PersonKind
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.jellyfin.sdk.model.api.VideoRangeType

/** Deterministic test factories with null Android Uris so tests run on the plain JVM. */
object TestMediaFixtures {

    fun testUuid(seed: Int): UUID = UUID(0L, seed.toLong())

    fun testSource(
        id: String = "src-1",
        name: String = "Primary",
        type: AfinitySourceType = AfinitySourceType.REMOTE,
        path: String = "/media/movie.mkv",
        mediaStreams: List<AfinityMediaStream> = emptyList(),
    ): AfinitySource =
        AfinitySource(
            id = id,
            name = name,
            type = type,
            path = path,
            size = 1024L,
            mediaStreams = mediaStreams,
        )

    fun testStream(
        index: Int,
        type: MediaStreamType,
        language: String = "eng",
        codec: String = if (type == MediaStreamType.AUDIO) "aac" else "srt",
        isExternal: Boolean = false,
        isDefault: Boolean = false,
        isForced: Boolean = false,
        isHearingImpaired: Boolean = false,
        videoRangeType: VideoRangeType? = null,
        videoDoViTitle: String? = null,
        hdr10PlusPresentFlag: Boolean = false,
        title: String = "Track $index",
    ): AfinityMediaStream =
        AfinityMediaStream(
            title = title,
            displayTitle = title,
            language = language,
            type = type,
            codec = codec,
            isExternal = isExternal,
            path = if (isExternal) "/subs/track_$index.$codec" else null,
            channelLayout = if (type == MediaStreamType.AUDIO) "stereo" else null,
            videoRangeType = videoRangeType,
            height = null,
            width = null,
            videoDoViTitle = videoDoViTitle,
            hdr10PlusPresentFlag = hdr10PlusPresentFlag,
            index = index,
            channels = if (type == MediaStreamType.AUDIO) 2 else null,
            isDefault = isDefault,
            isForced = isForced,
            isHearingImpaired = isHearingImpaired,
        )

    fun testPerson(
        id: UUID = testUuid(900),
        name: String = "Test Actor",
        type: PersonKind = PersonKind.ACTOR,
        role: String = "Lead",
    ): AfinityPerson =
        AfinityPerson(
            id = id,
            name = name,
            type = type,
            role = role,
            image = AfinityPersonImage(uri = null, blurHash = "LEHV6nWB2yk8pyo0adR*.7kCMdnj"),
        )

    fun testMovie(
        id: UUID = testUuid(1),
        name: String = "Movie ${id.leastSignificantBits}",
        overview: String = "",
        sources: List<AfinitySource> = emptyList(),
        played: Boolean = false,
        favorite: Boolean = false,
        liked: Boolean = false,
        runtimeTicks: Long = 7_200_000_0000L,
        playbackPositionTicks: Long = 0L,
        people: List<AfinityPerson> = emptyList(),
        externalUrls: List<AfinityExternalUrl>? = null,
        unplayedItemCount: Int? = null,
        images: AfinityImages = AfinityImages(),
        genres: List<String> = listOf("Sci-Fi"),
    ): AfinityMovie =
        AfinityMovie(
            id = id,
            name = name,
            originalTitle = null,
            overview = overview,
            sources = sources,
            played = played,
            favorite = favorite,
            liked = liked,
            canPlay = true,
            canDownload = true,
            runtimeTicks = runtimeTicks,
            playbackPositionTicks = playbackPositionTicks,
            premiereDate = null,
            dateCreated = null,
            people = people,
            genres = genres,
            communityRating = 8.0f,
            officialRating = "PG-13",
            criticRating = 85f,
            status = "Ended",
            productionYear = 2024,
            endDate = null,
            trailer = null,
            tagline = null,
            unplayedItemCount = unplayedItemCount,
            images = images,
            chapters = emptyList(),
            trickplayInfo = null,
            providerIds = null,
            externalUrls = externalUrls,
        )

    fun testShow(
        id: UUID = testUuid(100),
        name: String = "Show ${id.leastSignificantBits}",
        overview: String = "",
        played: Boolean = false,
        favorite: Boolean = false,
        liked: Boolean = false,
        unplayedItemCount: Int? = 5,
        people: List<AfinityPerson> = emptyList(),
        externalUrls: List<AfinityExternalUrl>? = null,
        images: AfinityImages = AfinityImages(),
    ): AfinityShow =
        AfinityShow(
            id = id,
            name = name,
            originalTitle = null,
            overview = overview,
            sources = emptyList(),
            seasons = emptyList(),
            played = played,
            favorite = favorite,
            liked = liked,
            canPlay = true,
            canDownload = false,
            unplayedItemCount = unplayedItemCount,
            genres = listOf("Drama"),
            people = people,
            runtimeTicks = 0L,
            communityRating = 8.4f,
            officialRating = "TV-MA",
            status = "Continuing",
            productionYear = 2023,
            premiereDate = null,
            dateCreated = null,
            dateLastContentAdded = null,
            endDate = null,
            trailer = null,
            tagline = null,
            seasonCount = 2,
            episodeCount = 16,
            images = images,
            providerIds = null,
            externalUrls = externalUrls,
        )

    fun testSeason(
        id: UUID = testUuid(200),
        seriesId: UUID = testUuid(100),
        name: String = "Season 1",
        indexNumber: Int = 1,
        played: Boolean = false,
        favorite: Boolean = false,
    ): AfinitySeason =
        AfinitySeason(
            id = id,
            name = name,
            seriesId = seriesId,
            seriesName = "Show",
            originalTitle = null,
            overview = "",
            sources = emptyList(),
            indexNumber = indexNumber,
            episodes = emptyList(),
            episodeCount = 8,
            productionYear = 2023,
            premiereDate = null,
            people = emptyList(),
            played = played,
            favorite = favorite,
            liked = false,
            canPlay = true,
            canDownload = false,
            unplayedItemCount = 3,
            images = AfinityImages(),
            providerIds = null,
            externalUrls = null,
        )

    fun testEpisode(
        id: UUID = testUuid(300),
        seriesId: UUID = testUuid(100),
        seasonId: UUID = testUuid(200),
        name: String = "Pilot",
        played: Boolean = false,
        favorite: Boolean = false,
        playbackPositionTicks: Long = 0L,
        sources: List<AfinitySource> = emptyList(),
    ): AfinityEpisode =
        AfinityEpisode(
            id = id,
            name = name,
            originalTitle = null,
            overview = "",
            indexNumber = 1,
            indexNumberEnd = null,
            parentIndexNumber = 1,
            sources = sources,
            played = played,
            favorite = favorite,
            liked = false,
            canPlay = true,
            canDownload = true,
            runtimeTicks = 3_000_000_0000L,
            playbackPositionTicks = playbackPositionTicks,
            premiereDate = null,
            seriesName = "Show",
            seriesId = seriesId,
            seriesLogo = null,
            seriesLogoBlurHash = null,
            seasonId = seasonId,
            communityRating = 8.1f,
            people = emptyList(),
            images = AfinityImages(),
            chapters = emptyList(),
            trickplayInfo = null,
            providerIds = null,
            externalUrls = null,
        )

    fun testCollection(
        id: UUID = testUuid(400),
        name: String = "Library",
        type: CollectionType = CollectionType.Movies,
    ): AfinityCollection =
        AfinityCollection(
            id = id,
            name = name,
            type = type,
            images = AfinityImages(),
            providerIds = null,
            externalUrls = null,
        )

    fun testTrack(
        id: UUID = testUuid(500),
        name: String = "Track ${id.leastSignificantBits}",
        played: Boolean = false,
        favorite: Boolean = false,
        liked: Boolean = false,
        playbackPositionTicks: Long = 0L,
        playCount: Int? = 0,
    ): AfinityTrack =
        AfinityTrack(
            id = id,
            name = name,
            albumId = testUuid(600),
            album = "Test Album",
            artistId = testUuid(700),
            artist = "Test Artist",
            artists = listOf("Test Artist"),
            indexNumber = 1,
            discNumber = 1,
            productionYear = 2024,
            runtimeTicks = 1_800_000_000L,
            playbackPositionTicks = playbackPositionTicks,
            played = played,
            favorite = favorite,
            playCount = playCount,
            normalizationGain = null,
            images = AfinityImages(),
            liked = liked,
        )

    fun testAlbum(
        id: UUID = testUuid(600),
        name: String = "Album ${id.leastSignificantBits}",
        played: Boolean = false,
        favorite: Boolean = false,
        liked: Boolean = false,
        playbackPositionTicks: Long = 0L,
        playCount: Int? = 0,
    ): AfinityAlbum =
        AfinityAlbum(
            id = id,
            name = name,
            artistId = testUuid(700),
            artist = "Test Artist",
            artists = listOf("Test Artist"),
            productionYear = 2024,
            songCount = 10,
            runtimeTicks = 24_000_000_000L,
            genres = listOf("Electronic"),
            overview = null,
            favorite = favorite,
            played = played,
            playCount = playCount,
            images = AfinityImages(),
            liked = liked,
            playbackPositionTicks = playbackPositionTicks,
        )

    fun testArtist(
        id: UUID = testUuid(700),
        name: String = "Artist ${id.leastSignificantBits}",
        favorite: Boolean = false,
        played: Boolean = false,
        liked: Boolean = false,
    ): AfinityArtist =
        AfinityArtist(
            id = id,
            name = name,
            overview = null,
            albumCount = 3,
            genres = listOf("Ambient"),
            favorite = favorite,
            images = AfinityImages(),
            liked = liked,
            played = played,
        )

    fun mockUserItemDataDto(
        played: Boolean = false,
        isFavorite: Boolean = false,
        likes: Boolean? = null,
        playbackPositionTicks: Long = 0L,
        unplayedItemCount: Int? = null,
        playCount: Int = 0,
    ): UserItemDataDto = mockk {
        every { this@mockk.played } returns played
        every { this@mockk.isFavorite } returns isFavorite
        every { this@mockk.likes } returns likes
        every { this@mockk.playbackPositionTicks } returns playbackPositionTicks
        every { this@mockk.unplayedItemCount } returns unplayedItemCount
        every { this@mockk.playCount } returns playCount
    }
}
