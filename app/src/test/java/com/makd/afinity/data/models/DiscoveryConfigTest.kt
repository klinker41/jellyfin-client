package com.makd.afinity.data.models

import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.media.LibraryFilters
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryConfigTest {

    @Test
    fun discoveryDensityAndSectionLookupsHandleValidAndUnknownKeys() {
        assertEquals(DiscoveryDensity.OFF, DiscoveryDensity.default)
        assertEquals(DiscoveryDensity.LIGHT, DiscoveryDensity.fromKey("light"))
        assertEquals(DiscoveryDensity.default, DiscoveryDensity.fromKey("invalid"))
        assertEquals(DiscoveryDensity.default, DiscoveryDensity.fromKey(null))

        val defaultConfig = DiscoveryConfig()
        assertEquals(DiscoveryDensity.OFF, defaultConfig.density)
        assertTrue(defaultConfig.isDiscoveryOff)
        assertFalse(defaultConfig.isEnabled(DiscoverySection.GENRES))
        assertEquals(0, defaultConfig.countFor(DiscoverySection.GENRES))

        assertEquals(DiscoverySection.GENRES, DiscoverySection.fromKey("discovery_genres"))
        assertNull(DiscoverySection.fromKey("nope"))
        assertTrue(DiscoverySection.TOP_IMDB_MOVIES.isToggle)
        assertFalse(DiscoverySection.GENRES.isToggle)
    }

    @Test
    fun discoveryConfigCountsRespectDensityDisabledSectionsAndClampedOverrides() {
        val off = DiscoveryConfig(density = DiscoveryDensity.OFF)
        assertTrue(off.isDiscoveryOff)
        assertFalse(off.isEnabled(DiscoverySection.GENRES))
        assertEquals(0, off.countFor(DiscoverySection.GENRES))

        val light = DiscoveryConfig(density = DiscoveryDensity.LIGHT)
        // 15 * 0.5f = 7.5 -> roundToInt = 8
        assertEquals(8, light.countFor(DiscoverySection.GENRES))
        // Toggle section with defaultCount=1, factor=0.5 -> coerced to min 1
        assertEquals(1, light.countFor(DiscoverySection.TOP_IMDB_MOVIES))

        val fullWithOverrides =
            DiscoveryConfig(
                density = DiscoveryDensity.FULL,
                disabled = setOf(DiscoverySection.WRITTEN_BY),
                overrides =
                    mapOf(
                        DiscoverySection.GENRES to 999,
                        DiscoverySection.SPOTLIGHTS to -5,
                    ),
            )
        assertFalse(fullWithOverrides.isEnabled(DiscoverySection.WRITTEN_BY))
        assertEquals(0, fullWithOverrides.countFor(DiscoverySection.WRITTEN_BY))
        assertEquals(
            DiscoverySection.GENRES.ceiling,
            fullWithOverrides.countFor(DiscoverySection.GENRES),
        )
        assertEquals(0, fullWithOverrides.countFor(DiscoverySection.SPOTLIGHTS))

        val custom = DiscoveryConfig(density = DiscoveryDensity.CUSTOM)
        assertEquals(
            DiscoverySection.BECAUSE_YOU_WATCHED.defaultCount,
            custom.countFor(DiscoverySection.BECAUSE_YOU_WATCHED),
        )
    }

    @Test
    fun homeRowAndSettingsExportRoundTripCorrectly() {
        assertEquals(HomeRow.CONTINUE_WATCHING, HomeRow.fromKey("continue_watching"))
        assertNull(HomeRow.fromKey("missing"))
        assertTrue(HomeRow.LIBRARIES in HomeRow.alwaysOn)
        assertTrue(HomeRow.CONTINUE_WATCHING in HomeRow.alwaysOn)
        assertFalse(HomeRow.LIBRARIES in HomeRow.configurable)
        val expectedDefaultHidden =
            setOf(
                HomeRow.LATEST_BOOKS,
                HomeRow.LATEST_AUDIOBOOKS,
                HomeRow.LATEST_HOME_VIDEOS,
                HomeRow.LATEST_MUSIC_VIDEOS,
                HomeRow.UPCOMING_EPISODES,
                HomeRow.CRITICS_CHOICE,
                HomeRow.FRANCHISES,
                HomeRow.WATCH_AGAIN,
                HomeRow.POPULAR_STUDIOS,
            )
        assertEquals(expectedDefaultHidden, HomeRow.defaultHidden)
        val expectedDefaultVisible =
            listOf(
                HomeRow.HERO_CAROUSEL,
                HomeRow.LIBRARIES,
                HomeRow.CONTINUE_WATCHING,
                HomeRow.NEXT_UP,
                HomeRow.CONTINUE_READING,
                HomeRow.CONTINUE_LISTENING,
                HomeRow.LATEST_MOVIES,
                HomeRow.LATEST_TV,
            )
        expectedDefaultVisible.forEach { row -> assertFalse(row in HomeRow.defaultHidden) }
        assertEquals(SettingsSection.SUBTITLES, SettingsSection.fromKey("subtitles"))
        assertNull(SettingsSection.fromKey("other"))

        val export =
            AfinitySettingsExport(
                exportedAt = "2026-11-21T12:00:00Z",
                appVersion = "1.2.3",
                home =
                    HomePayload(
                        hiddenRows = listOf(HomeRow.FRANCHISES.key),
                        discovery =
                            DiscoveryExport(
                                density = DiscoveryDensity.FULL.key,
                                disabled = listOf(DiscoverySection.TRENDING_SHOWS.key),
                                overrides = mapOf(DiscoverySection.GENRES.key to 12),
                            ),
                        customSections =
                            listOf(
                                CustomSectionExport(
                                    title = "Sci-Fi",
                                    sourceType = CustomSectionSourceType.GENRE.name,
                                    sourceValues = listOf("Science Fiction"),
                                    includeItemTypes = listOf(CustomSectionItemType.MOVIE.key),
                                    sortBy = SortBy.RELEASE_DATE.name,
                                    sortDescending = true,
                                    cardStyle = CustomSectionCardStyle.SPOTLIGHT.name,
                                    filters = LibraryFilters(favorites = true),
                                ),
                            ),
                    ),
            )

        val encoded = Json.encodeToString(export)
        val decoded = Json.decodeFromString<AfinitySettingsExport>(encoded)
        assertEquals(export, decoded)
    }
}
