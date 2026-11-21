package com.makd.afinity.data.repository.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleLanguageCodesTest {

    @Test
    fun `toLanguageTag maps special SubtitleDB codes and bibliographic ISO3 codes`() {
        assertEquals("pt-BR", SubtitleLanguageCodes.toLanguageTag("PB"))
        assertEquals("zh-Hant", SubtitleLanguageCodes.toLanguageTag(" zt "))
        assertEquals("es-419", SubtitleLanguageCodes.toLanguageTag("ea"))
        assertEquals("fr", SubtitleLanguageCodes.toLanguageTag("fre"))
        assertEquals("de", SubtitleLanguageCodes.toLanguageTag("ger"))
        assertEquals("ja", SubtitleLanguageCodes.toLanguageTag("jpn"))
        assertEquals("ko", SubtitleLanguageCodes.toLanguageTag("ko"))
    }

    @Test
    fun `toServer normalizes ISO1 and terminology codes to Jellyfin bibliographic ISO3`() {
        assertEquals("fre", SubtitleLanguageCodes.toServer("fr"))
        assertEquals("fre", SubtitleLanguageCodes.toServer("fra"))
        assertEquals("ger", SubtitleLanguageCodes.toServer("de"))
        assertEquals("eng", SubtitleLanguageCodes.toServer("en"))
        assertEquals("chi", SubtitleLanguageCodes.toServer("zt"))
        assertEquals("spa", SubtitleLanguageCodes.toServer("es-419"))
    }

    @Test
    fun `toSubtitleDb and sameLanguage handle regional variants and special codes`() {
        assertEquals("pb", SubtitleLanguageCodes.toSubtitleDb("pt-BR"))
        assertEquals("zt", SubtitleLanguageCodes.toSubtitleDb("zt"))
        assertEquals("en", SubtitleLanguageCodes.toSubtitleDb("eng"))
        assertEquals("es", SubtitleLanguageCodes.toSubtitleDb("es-MX"))

        assertTrue(SubtitleLanguageCodes.sameLanguage("en", "eng"))
        assertTrue(SubtitleLanguageCodes.sameLanguage("fra", "FRE"))
        assertFalse(SubtitleLanguageCodes.sameLanguage("eng", "spa"))
    }
}
