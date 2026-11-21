package com.makd.afinity.ui.preview

import androidx.core.net.toUri
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityExternalUrl
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.media.AfinityMediaStream
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityPerson
import com.makd.afinity.data.models.media.AfinityPersonImage
import com.makd.afinity.data.models.media.AfinitySegment
import com.makd.afinity.data.models.media.AfinitySegmentType
import com.makd.afinity.data.models.media.AfinitySource
import com.makd.afinity.data.models.media.AfinitySourceType
import com.makd.afinity.data.models.media.AfinityStudio
import java.time.LocalDateTime
import java.util.UUID
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PersonKind
import org.jellyfin.sdk.model.api.VideoRangeType

object ScreenshotPreviewFixtures {

    const val TICKS_PER_MINUTE = 600_000_000L

    val AIRED_DATE: LocalDateTime = LocalDateTime.of(2026, 5, 14, 20, 0)
    val UPCOMING_DATE: LocalDateTime = LocalDateTime.of(2099, 1, 15, 20, 0)

    private fun uuid(seed: String): UUID = UUID.nameUUIDFromBytes(seed.toByteArray())

    private fun images(seed: String): AfinityImages =
        AfinityImages(
            primary = "preview://primary/$seed".toUri(),
            backdrop = "preview://backdrop/$seed".toUri(),
            thumb = "preview://thumb/$seed".toUri(),
        )

    val collections: List<AfinityCollection> =
        listOf(
            CollectionType.Movies to "Movies",
            CollectionType.TvShows to "TV Shows",
            CollectionType.Music to "Music",
            CollectionType.Books to "Books",
            CollectionType.LiveTv to "Live TV",
            CollectionType.Mixed to "Documentaries",
        ).map { (type, title) ->
            AfinityCollection(
                id = uuid("col-$title"),
                name = title,
                type = type,
                images = images("col-$title"),
                providerIds = null,
                externalUrls = null,
            )
        }

    val episodes: List<AfinityEpisode> =
        listOf(
            PreviewData.episode(
                name = "Good News About Hell",
                season = 1,
                index = 1,
                progress = 0.45f,
                premiereDate = AIRED_DATE,
            ),
            PreviewData.episode(
                name = "Half Loop",
                season = 1,
                index = 2,
                progress = 0f,
                played = true,
                premiereDate = AIRED_DATE.plusDays(7),
            ).copy(indexNumberEnd = 3),
            PreviewData.episode(
                name = "The We We Are",
                season = 1,
                index = 9,
                progress = 0f,
                missing = true,
                premiereDate = AIRED_DATE.plusDays(14),
            ),
            PreviewData.episode(
                name = "Cold Harbor",
                season = 2,
                index = 10,
                progress = 0f,
                missing = true,
                premiereDate = UPCOMING_DATE,
            ),
        )

    private fun videoStream(
        width: Int,
        height: Int,
        codec: String,
        range: VideoRangeType = VideoRangeType.SDR,
        doviTitle: String? = null,
    ) =
        AfinityMediaStream(
            title = "Video",
            displayTitle = null,
            language = "eng",
            type = MediaStreamType.VIDEO,
            codec = codec,
            isExternal = false,
            path = null,
            channelLayout = null,
            videoRangeType = range,
            height = height,
            width = width,
            videoDoViTitle = doviTitle,
            index = 0,
            channels = null,
            isDefault = true,
        )

    private fun audioStream(codec: String, layout: String, profile: String? = null) =
        AfinityMediaStream(
            title = "Audio",
            displayTitle = null,
            language = "eng",
            type = MediaStreamType.AUDIO,
            codec = codec,
            isExternal = false,
            path = null,
            channelLayout = layout,
            videoRangeType = null,
            height = null,
            width = null,
            videoDoViTitle = null,
            index = 1,
            channels = 6,
            isDefault = true,
            profile = profile,
        )

    private val subtitleStream =
        AfinityMediaStream(
            title = "English",
            displayTitle = null,
            language = "eng",
            type = MediaStreamType.SUBTITLE,
            codec = "subrip",
            isExternal = false,
            path = null,
            channelLayout = null,
            videoRangeType = null,
            height = null,
            width = null,
            videoDoViTitle = null,
            index = 2,
            channels = null,
            isDefault = false,
        )

    val source4kDvAtmos =
        AfinitySource(
            id = "src-4k",
            name = "4K HDR",
            type = AfinitySourceType.REMOTE,
            path = "/media/dune.mkv",
            size = 24_000_000_000L,
            bitrate = 24_500_000L,
            mediaStreams =
                listOf(
                    videoStream(3840, 2160, "hevc", VideoRangeType.DOVI, "Dolby Vision"),
                    audioStream("eac3", "7.1", profile = "Dolby Digital Plus + Dolby Atmos"),
                    subtitleStream,
                ),
        )

    val source1080pHdrDts =
        AfinitySource(
            id = "src-1080p",
            name = "1080p",
            type = AfinitySourceType.REMOTE,
            path = "/media/interstellar.mkv",
            size = 8_000_000_000L,
            bitrate = 9_200_000L,
            mediaStreams =
                listOf(
                    videoStream(1920, 1080, "h264", VideoRangeType.HDR10),
                    audioStream("dts", "5.1"),
                ),
        )

    val sourceSdStereo =
        AfinitySource(
            id = "src-sd",
            name = "SD",
            type = AfinitySourceType.REMOTE,
            path = "/media/classic.mp4",
            size = 900_000_000L,
            bitrate = 1_500_000L,
            mediaStreams = listOf(videoStream(720, 480, "mpeg4"), audioStream("aac", "stereo")),
        )

    val people: List<AfinityPerson> =
        listOf(
            AfinityPerson(
                id = uuid("person-1"),
                name = "Timothee Chalamet",
                type = PersonKind.ACTOR,
                role = "Paul Atreides",
                image = AfinityPersonImage("preview://person/paul".toUri(), null),
            ),
            AfinityPerson(
                id = uuid("person-2"),
                name = "Rebecca Ferguson",
                type = PersonKind.ACTOR,
                role = "Lady Jessica",
                image = AfinityPersonImage("preview://person/jessica".toUri(), null),
            ),
            AfinityPerson(
                id = uuid("person-3"),
                name = "Oscar Isaac",
                type = PersonKind.ACTOR,
                role = "Duke Leto",
                image = AfinityPersonImage(null, null),
            ),
        )

    val externalUrls: List<AfinityExternalUrl> =
        listOf(
            AfinityExternalUrl("IMDb", "https://www.imdb.com/title/tt1160419/"),
            AfinityExternalUrl("TMDB", "https://www.themoviedb.org/movie/438631"),
            AfinityExternalUrl("TheTVDB", "https://thetvdb.com/movies/dune"),
            AfinityExternalUrl("Trakt", "https://trakt.tv/movies/dune-2021"),
            AfinityExternalUrl("Official Site", "https://www.dunemovie.com"),
        )

    val studios: List<AfinityStudio> =
        listOf(
            AfinityStudio(uuid("studio-a24"), "A24", "preview://studio/a24", 42),
            AfinityStudio(uuid("studio-legendary"), "Legendary", "preview://studio/legendary", 18),
            AfinityStudio(uuid("studio-unknown"), "Indie Studio", null, 7),
        )

    val introSegment =
        AfinitySegment(
            type = AfinitySegmentType.INTRO,
            startTicks = 10_000L,
            endTicks = 95_000L,
        )

    val outroSegment =
        AfinitySegment(
            type = AfinitySegmentType.OUTRO,
            startTicks = 3_200_000L,
            endTicks = 3_400_000L,
        )

    fun movieForMetadata(): AfinityMovie =
        PreviewData.movie(progress = 0f).copy(
            runtimeTicks = 0L,
            sources = listOf(source4kDvAtmos),
            people = people,
            externalUrls = externalUrls,
        )
}
