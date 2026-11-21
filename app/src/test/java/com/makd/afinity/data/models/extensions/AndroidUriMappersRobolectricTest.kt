package com.makd.afinity.data.models.extensions

import android.app.Application
import androidx.core.net.toUri
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.withBaseUrl
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.ImageFormat
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.PersonKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AndroidUriMappersRobolectricTest {

    private val baseUrl = "https://jf.example.com"

    @Test
    fun `toAfinityMovie and toAfinityImages construct expected Jellyfin image URIs and blurhashes`() {
        val movieId = testUuid(10)
        val dto =
            BaseItemDto(
                id = movieId,
                type = BaseItemKind.MOVIE,
                name = "Dune",
                overview = "Desert epic",
                genres = listOf("Sci-Fi"),
                imageTags =
                    mapOf(
                        ImageType.PRIMARY to "p-tag",
                        ImageType.THUMB to "t-tag",
                        ImageType.LOGO to "l-tag",
                    ),
                backdropImageTags = listOf("b-tag"),
                imageBlurHashes =
                    mapOf(
                        ImageType.PRIMARY to mapOf("p-tag" to "hash-p"),
                        ImageType.BACKDROP to mapOf("b-tag" to "hash-b"),
                    ),
            )

        val images = dto.toAfinityImages(baseUrl, imageFormat = ImageFormat.WEBP)
        assertEquals(
            "https://jf.example.com/Items/$movieId/Images/Primary?tag=p-tag&format=Webp",
            images.primary?.toString(),
        )
        assertEquals(
            "https://jf.example.com/Items/$movieId/Images/Backdrop/0?tag=b-tag&format=Webp",
            images.backdrop?.toString(),
        )
        assertEquals(
            "https://jf.example.com/Items/$movieId/Images/Thumb?tag=t-tag&format=Webp",
            images.thumb?.toString(),
        )
        assertEquals(
            "https://jf.example.com/Items/$movieId/Images/Logo?tag=l-tag",
            images.logo?.toString(),
        )
        assertEquals("hash-p", images.primaryBlurHash)
        assertEquals("hash-b", images.backdropBlurHash)

        val movie = dto.toAfinityItem(baseUrl) as AfinityMovie
        assertEquals("Dune", movie.name)
        assertEquals(listOf("Sci-Fi"), movie.genres)
    }

    @Test
    fun `COLLECTION_FOLDER falls back to parentPrimaryImageItemId and episode includes show images`() {
        val folderId = testUuid(20)
        val parentOwnerId = testUuid(21)
        val folderDto =
            BaseItemDto(
                id = folderId,
                type = BaseItemKind.COLLECTION_FOLDER,
                name = "Movies",
                parentPrimaryImageItemId = parentOwnerId,
                parentPrimaryImageTag = "parent-tag",
            )
        val folderImages = folderDto.toAfinityImages(baseUrl)
        assertEquals(
            "https://jf.example.com/Items/$parentOwnerId/Images/Primary?tag=parent-tag",
            folderImages.primary?.toString(),
        )

        val showId = testUuid(22)
        val epId = testUuid(23)
        val epDto =
            BaseItemDto(
                id = epId,
                type = BaseItemKind.EPISODE,
                seriesId = showId,
                seriesPrimaryImageTag = "show-p",
                parentBackdropItemId = showId,
                parentBackdropImageTags = listOf("show-b"),
                parentLogoItemId = showId,
                parentLogoImageTag = "show-l",
            )
        val epImages = epDto.toAfinityImages(baseUrl)
        assertEquals(
            "https://jf.example.com/Items/$showId/Images/Primary?tag=show-p",
            epImages.showPrimary?.toString(),
        )
        assertEquals(
            "https://jf.example.com/Items/$showId/Images/Backdrop/0?tag=show-b",
            epImages.showBackdrop?.toString(),
        )
        assertEquals(
            "https://jf.example.com/Items/$showId/Images/Logo?tag=show-l",
            epImages.showLogo?.toString(),
        )
    }

    @Test
    fun `toAfinityTrack, toAfinityProgram, and toAfinityPerson map album fallback and channel logo URIs`() {
        val trackId = testUuid(30)
        val albumId = testUuid(31)
        val trackDto =
            BaseItemDto(
                id = trackId,
                type = BaseItemKind.AUDIO,
                name = "Intro",
                albumId = albumId,
                album = "Discovery",
                albumPrimaryImageTag = "alb-tag",
                albumArtist = "Daft Punk",
            )
        val track = trackDto.toAfinityTrack("$baseUrl/")
        assertEquals(
            "https://jf.example.com/Items/$albumId/Images/Primary?tag=alb-tag",
            track.images.primary?.toString(),
        )

        val programId = testUuid(32)
        val channelId = testUuid(33)
        val programDto =
            BaseItemDto(
                id = programId,
                type = BaseItemKind.PROGRAM,
                name = "Evening News",
                channelId = channelId,
                channelPrimaryImageTag = "ch-tag",
            )
        val program = programDto.toAfinityProgram(baseUrl)
        assertEquals(
            "https://jf.example.com/Items/$channelId/Images/Primary?tag=ch-tag",
            program.channelLogo?.toString(),
        )

        val personId = testUuid(34)
        val person =
            BaseItemPerson(
                    id = personId,
                    name = "Rebecca Ferguson",
                    role = "Jessica",
                    type = PersonKind.ACTOR,
                    primaryImageTag = "act-tag",
                    imageBlurHashes = mapOf(ImageType.PRIMARY to mapOf("act-tag" to "bh-1")),
                )
                .toAfinityPerson(baseUrl)
        assertEquals(
            "https://jf.example.com/Items/$personId/Images/Primary?tag=act-tag",
            person.image.uri?.toString(),
        )
        assertEquals("bh-1", person.image.blurHash)
    }

    @Test
    fun `AfinityImages extension helpers rewrite base URLs, resolve fallbacks, and format logo`() {
        val images =
            AfinityImages(
                primary = "http://old.local:8096/Items/1/Images/Primary?tag=a".toUri(),
                thumb = "http://old.local:8096/Items/1/Images/Thumb".toUri(),
                logo = "http://old.local:8096/Items/1/Images/Logo".toUri(),
            )

        assertEquals(
            "http://old.local:8096/Items/1/Images/Thumb",
            images.landscapeImageUrl,
        )
        assertEquals(
            "http://old.local:8096/Items/1/Images/Primary?tag=a",
            images.posterImageUrl,
        )
        assertEquals(
            "http://old.local:8096/Items/1/Images/Logo?format=webp",
            images.logoImageUrlWithTransparency(),
        )

        val rewritten = images.withBaseUrl("https://cdn.example.com:8443")
        assertEquals(
            "https://cdn.example.com:8443/Items/1/Images/Primary?tag=a",
            rewritten.primary?.toString(),
        )
        assertEquals("cdn.example.com:8443", rewritten.thumb?.encodedAuthority)
        assertEquals("https", rewritten.logo?.scheme)
        assertNull(AfinityImages().withBaseUrl("https://cdn.example.com").primary)
        assertEquals(
            "https://cdn.example.com/Items/1/Images/Logo?tag=x&format=webp",
            "https://cdn.example.com/Items/1/Images/Logo?tag=x".withLogoFormat(),
        )
    }
}
