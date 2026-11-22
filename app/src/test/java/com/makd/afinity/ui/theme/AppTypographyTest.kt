package com.makd.afinity.ui.theme

import androidx.compose.ui.text.font.FontFamily
import org.junit.Assert.assertEquals
import org.junit.Test

class AppTypographyTest {

    @Test
    fun `getAppTypography maps each AppFont to its expected FontFamily`() {
        val expectedFamilies =
            mapOf(
                AppFont.DEFAULT to FontFamily.Default,
                AppFont.GOOGLE_SANS to GoogleSansFlex,
                AppFont.QUICKSAND to Quicksand,
                AppFont.IBM_PLEX_SANS to IBMPlexSans,
                AppFont.IBM_PLEX_SANS_CONDENSED to IBMPlexSansCondensed,
            )

        assertEquals(AppFont.entries.toSet(), expectedFamilies.keys)

        expectedFamilies.forEach { (font, expectedFamily) ->
            val typography = getAppTypography(font)
            assertEquals(expectedFamily, typography.displayLarge.fontFamily)
            assertEquals(expectedFamily, typography.headlineMedium.fontFamily)
            assertEquals(expectedFamily, typography.titleMedium.fontFamily)
            assertEquals(expectedFamily, typography.bodyLarge.fontFamily)
            assertEquals(expectedFamily, typography.labelSmall.fontFamily)
        }
    }
}
