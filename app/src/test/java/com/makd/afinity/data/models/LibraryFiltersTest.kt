package com.makd.afinity.data.models.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFiltersTest {

    @Test
    fun activeCountAndEmptyStateTrackAllFilterDimensions() {
        val empty = LibraryFilters()
        assertTrue(empty.isEmpty)
        assertEquals(0, empty.activeCount)
        assertTrue(LibraryFilterOptions().isEmpty)

        val populated =
            LibraryFilters(
                played = true,
                favorites = true,
                features = setOf(LibraryFeature.SUBTITLES, LibraryFeature.TRAILER),
                genres = setOf("Sci-Fi"),
                officialRatings = setOf("PG-13"),
                tags = setOf("4k"),
                videoTypes = setOf(VideoTypeFilter.UHD_4K),
                seriesStatuses = setOf(SeriesStatusFilter.CONTINUING),
                years = setOf(2024, 2025),
                audioLanguages = setOf("eng"),
                subtitleLanguages = setOf("eng"),
                studios = setOf("A24"),
                formats = setOf(LibraryFormatFilter.VIDEOS),
            )
        assertFalse(populated.isEmpty)
        assertEquals(15, populated.activeCount)
        assertFalse(LibraryFilterOptions(genres = listOf("Sci-Fi")).isEmpty)
    }

    @Test
    fun galleryAndBookCompositionHelpersFilterCorrectly() {
        assertEquals(listOf("PHOTO"), galleryItemTypes(setOf(LibraryFormatFilter.PHOTOS)))
        assertEquals(
            listOf("PHOTO_ALBUM"),
            galleryItemTypes(setOf(LibraryFormatFilter.PHOTO_ALBUMS)),
        )
        assertEquals(listOf("VIDEO"), galleryItemTypes(setOf(LibraryFormatFilter.VIDEOS)))
        assertEquals(listOf("PHOTO", "VIDEO"), galleryItemTypes(emptySet()))

        assertTrue(BookLibraryComposition.BOOKS.includesBooks(LibraryFilters()))
        assertFalse(BookLibraryComposition.BOOKS.includesAudiobooks(LibraryFilters()))
        assertTrue(BookLibraryComposition.AUDIOBOOKS.includesAudiobooks(LibraryFilters()))
        assertFalse(BookLibraryComposition.AUDIOBOOKS.includesBooks(LibraryFilters()))

        val booksOnlyFilter = LibraryFilters(formats = setOf(LibraryFormatFilter.BOOKS))
        assertTrue(BookLibraryComposition.MIXED.includesBooks(booksOnlyFilter))
        assertFalse(BookLibraryComposition.MIXED.includesAudiobooks(booksOnlyFilter))
    }

    @Test
    fun toItemFilterCriteriaMapsFlagsMutuallyExclusiveOptionsAndStudios() {
        val criteria =
            LibraryFilters(
                    played = true,
                    unplayed = false,
                    favorites = true,
                    watchlist = true,
                    resumable = true,
                    features =
                        setOf(
                            LibraryFeature.SUBTITLES,
                            LibraryFeature.TRAILER,
                            LibraryFeature.SPECIAL_FEATURE,
                            LibraryFeature.THEME_SONG,
                            LibraryFeature.THEME_VIDEO,
                        ),
                    genres = setOf("Drama"),
                    officialRatings = setOf("R"),
                    tags = setOf("remux"),
                    videoTypes =
                        setOf(
                            VideoTypeFilter.BLU_RAY,
                            VideoTypeFilter.DVD,
                            VideoTypeFilter.HD,
                            VideoTypeFilter.UHD_4K,
                            VideoTypeFilter.THREE_D,
                        ),
                    seriesStatuses = setOf(SeriesStatusFilter.ENDED),
                    years = setOf(2021),
                    audioLanguages = setOf("jpn"),
                    subtitleLanguages = setOf("eng"),
                    studios = setOf("Studio Ghibli"),
                )
                .toItemFilterCriteria(studios = listOf("Studio Ghibli", "Neon"))

        assertEquals(true, criteria.isPlayed)
        assertEquals(true, criteria.isFavorite)
        assertEquals(true, criteria.isLiked)
        assertEquals(true, criteria.isResumable)
        assertEquals(true, criteria.hasSubtitles)
        assertEquals(true, criteria.hasTrailer)
        assertEquals(true, criteria.hasSpecialFeature)
        assertEquals(true, criteria.hasThemeSong)
        assertEquals(true, criteria.hasThemeVideo)
        assertEquals(true, criteria.isHd)
        assertEquals(true, criteria.is4k)
        assertEquals(true, criteria.is3d)
        assertEquals(listOf("BluRay", "Dvd"), criteria.videoTypes)
        assertEquals(listOf("Ended"), criteria.seriesStatuses)
        assertEquals(listOf("Studio Ghibli", "Neon"), criteria.studios)

        // Conflicting pairs cancel out to null.
        val ambiguous =
            LibraryFilters(
                    played = true,
                    unplayed = true,
                    videoTypes = setOf(VideoTypeFilter.HD, VideoTypeFilter.SD),
                )
                .toItemFilterCriteria()
        assertNull(ambiguous.isPlayed)
        assertNull(ambiguous.isHd)

        // Unplayed only & SD only set false.
        val unplayedSd =
            LibraryFilters(unplayed = true, videoTypes = setOf(VideoTypeFilter.SD))
                .toItemFilterCriteria()
        assertEquals(false, unplayedSd.isPlayed)
        assertEquals(false, unplayedSd.isHd)
    }
}
