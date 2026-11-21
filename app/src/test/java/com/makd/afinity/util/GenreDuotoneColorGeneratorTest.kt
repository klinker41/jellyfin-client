package com.makd.afinity.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenreDuotoneColorGeneratorTest {

    private val hexRegex = Regex("^[0-9a-fA-F]{6}$")

    @Test
    fun getColorsForGenreIsDeterministicAndValidHexIncludingZeroAndNegatives() {
        for (genreId in listOf(-42, -1, 0, 12, 28, 878, 10749)) {
            val first = GenreDuotoneColorGenerator.getColorsForGenre(genreId)
            val second = GenreDuotoneColorGenerator.getColorsForGenre(genreId)
            assertEquals(first, second)
            assertTrue("Invalid primary hex: ${first.first}", hexRegex.matches(first.first))
            assertTrue("Invalid secondary hex: ${first.second}", hexRegex.matches(first.second))
        }
    }

    @Test
    fun getDuotoneFilterUrlFormatsWidthAndPaletteColors() {
        val (primary, secondary) = GenreDuotoneColorGenerator.getColorsForGenre(28)
        assertEquals(
            "https://image.tmdb.org/t/p/w1280_filter(duotone,$primary,$secondary)",
            GenreDuotoneColorGenerator.getDuotoneFilterUrl(28),
        )
        assertEquals(
            "https://image.tmdb.org/t/p/w780_filter(duotone,$primary,$secondary)",
            GenreDuotoneColorGenerator.getDuotoneFilterUrl(28, width = "w780"),
        )
    }

    @Test
    fun getDuotoneFilterUrlByIndexWrapsAroundPaletteCount() {
        assertEquals(
            GenreDuotoneColorGenerator.getDuotoneFilterUrlByIndex(0),
            GenreDuotoneColorGenerator.getDuotoneFilterUrlByIndex(14),
        )
        assertEquals(
            "https://image.tmdb.org/t/p/w500_filter(duotone,991B1B,FCA5A5)",
            GenreDuotoneColorGenerator.getDuotoneFilterUrlByIndex(0, width = "w500"),
        )
    }
}
