package com.makd.afinity.data.models

import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.media.LibraryFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomHomeSectionTest {

    @Test
    fun sourceTypeCapabilitiesMatchSemantics() {
        assertTrue(CustomSectionSourceType.GENRE.supportsMultipleSources)
        assertTrue(CustomSectionSourceType.STUDIO.supportsMultipleSources)
        assertTrue(CustomSectionSourceType.TAG.supportsMultipleSources)
        assertFalse(CustomSectionSourceType.COLLECTION.supportsMultipleSources)
        assertFalse(CustomSectionSourceType.PLAYLIST.supportsMultipleSources)
        assertFalse(CustomSectionSourceType.LIBRARY.supportsMultipleSources)

        assertTrue(CustomSectionSourceType.COLLECTION.usesItemIds)
        assertTrue(CustomSectionSourceType.PLAYLIST.usesItemIds)
        assertTrue(CustomSectionSourceType.LIBRARY.usesItemIds)
        assertFalse(CustomSectionSourceType.GENRE.usesItemIds)

        assertFalse(CustomSectionSourceType.PLAYLIST.supportsRefinement)
        assertFalse(CustomSectionSourceType.PLAYLIST.supportsFullList)
        assertFalse(CustomSectionSourceType.GENRE.refinesGenres)
        assertTrue(CustomSectionSourceType.STUDIO.refinesGenres)
        assertFalse(CustomSectionSourceType.TAG.refinesTags)
        assertTrue(CustomSectionSourceType.GENRE.refinesTags)
    }

    @Test
    fun libraryFiltersScopedToSourceTypeClearsDisallowedRefinements() {
        val filters =
            LibraryFilters(
                favorites = true,
                genres = setOf("Sci-Fi"),
                tags = setOf("4k"),
                years = setOf(2024),
            )

        assertEquals(LibraryFilters(), filters.scopedTo(CustomSectionSourceType.PLAYLIST))
        assertEquals(
            filters.copy(genres = emptySet()),
            filters.scopedTo(CustomSectionSourceType.GENRE),
        )
        assertEquals(
            filters.copy(tags = emptySet()),
            filters.scopedTo(CustomSectionSourceType.TAG),
        )
        assertEquals(filters, filters.scopedTo(CustomSectionSourceType.STUDIO))
    }

    @Test
    fun availableItemTypesAndSanitizationEnforceSourceAndLibraryConstraints() {
        assertEquals(
            listOf(CustomSectionItemType.MOVIE),
            CustomSectionItemType.availableFor(
                CustomSectionSourceType.LIBRARY,
                CollectionType.Movies,
            ),
        )
        assertEquals(
            listOf(
                CustomSectionItemType.SERIES,
                CustomSectionItemType.SEASON,
                CustomSectionItemType.EPISODE,
            ),
            CustomSectionItemType.availableFor(
                CustomSectionSourceType.LIBRARY,
                CollectionType.TvShows,
            ),
        )
        assertEquals(
            listOf(CustomSectionItemType.BOX_SET),
            CustomSectionItemType.availableFor(
                CustomSectionSourceType.LIBRARY,
                CollectionType.BoxSets,
            ),
        )
        assertFalse(
            CustomSectionItemType.SEASON in
                CustomSectionItemType.availableFor(CustomSectionSourceType.GENRE),
        )
        assertFalse(
            CustomSectionItemType.SERIES in
                CustomSectionItemType.availableFor(CustomSectionSourceType.PLAYLIST),
        )
        assertNull(CustomSectionItemType.fromKey("UNKNOWN"))
    }

    @Test
    fun cardStyleLockingSeasonalAndItemTypeMutationsWorkAsExpected() {
        val base =
            CustomHomeSection(
                id = "sec-1",
                position = 0,
                title = "Halloween",
                sourceType = CustomSectionSourceType.TAG,
                sourceValues = listOf("spooky", "horror"),
                includeItemTypes = listOf(CustomSectionItemType.MOVIE.key),
                cardStyle = CustomSectionCardStyle.PORTRAIT,
                seasonStart = "10-01",
                seasonEnd = "10-31",
                filters = LibraryFilters(genres = setOf("Horror"), tags = setOf("spooky")),
            )

        assertTrue(base.isSeasonal)
        assertEquals("spooky", base.primarySourceValue)
        assertEquals(CustomSectionTypeGroup.VIDEO, base.typeGroup)
        assertFalse(base.isEpisodeSection)
        assertNull(base.lockedCardStyle)
        assertEquals(CustomSectionCardStyle.PORTRAIT, base.effectiveCardStyle)

        // Cannot remove the last remaining item type.
        assertEquals(base, base.withItemTypeToggled(CustomSectionItemType.MOVIE))

        // Adding another item in the same group preserves both.
        val movieAndSeries = base.withItemTypeToggled(CustomSectionItemType.SERIES)
        assertEquals(
            listOf(CustomSectionItemType.MOVIE.key, CustomSectionItemType.SERIES.key),
            movieAndSeries.includeItemTypes,
        )

        // Switching to an item in a different group replaces selection and locks card style.
        val episodesOnly = movieAndSeries.withItemTypeToggled(CustomSectionItemType.EPISODE)
        assertEquals(listOf(CustomSectionItemType.EPISODE.key), episodesOnly.includeItemTypes)
        assertTrue(episodesOnly.isEpisodeSection)
        assertEquals(CustomSectionCardStyle.LANDSCAPE, episodesOnly.lockedCardStyle)
        assertEquals(CustomSectionCardStyle.LANDSCAPE, episodesOnly.effectiveCardStyle)

        // Switching source type clears sourceValues, scopes filters, and sanitizes item types.
        val switchedToPlaylist = base.withSourceType(CustomSectionSourceType.PLAYLIST)
        assertTrue(switchedToPlaylist.sourceValues.isEmpty())
        assertEquals(LibraryFilters(), switchedToPlaylist.filters)
        assertSame(
            episodesOnly,
            episodesOnly.withItemTypesLimitedTo(listOf(CustomSectionItemType.EPISODE)),
        )
    }
}
