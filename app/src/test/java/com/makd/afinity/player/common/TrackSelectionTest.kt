package com.makd.afinity.player.common

import com.makd.afinity.testutil.TestMediaFixtures.testStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.SubtitlePlaybackMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackSelectionTest {

    private fun audio(
        index: Int,
        lang: String = "eng",
        isDefault: Boolean = false,
        isExternal: Boolean = false,
    ) =
        testStream(
            index = index,
            type = MediaStreamType.AUDIO,
            language = lang,
            isDefault = isDefault,
            isExternal = isExternal,
        )

    private fun sub(
        index: Int,
        lang: String = "eng",
        codec: String = "srt",
        isDefault: Boolean = false,
        isForced: Boolean = false,
        isExternal: Boolean = false,
        isHearingImpaired: Boolean = false,
    ) =
        testStream(
            index = index,
            type = MediaStreamType.SUBTITLE,
            language = lang,
            codec = codec,
            isDefault = isDefault,
            isForced = isForced,
            isExternal = isExternal,
            isHearingImpaired = isHearingImpaired,
        )

    @Test
    fun normalizeLanguageHandlesTwoThreeLetterBibliographicRegionAndUnknownCodes() {
        assertEquals("eng", TrackSelection.normalizeLanguage("en"))
        assertEquals("eng", TrackSelection.normalizeLanguage("en-US"))
        assertEquals("eng", TrackSelection.normalizeLanguage("EN_gb"))
        assertEquals("fra", TrackSelection.normalizeLanguage("fre"))
        assertEquals("deu", TrackSelection.normalizeLanguage("ger"))
        assertEquals("zho", TrackSelection.normalizeLanguage("chi"))
        assertEquals("nld", TrackSelection.normalizeLanguage("dut"))
        assertEquals("", TrackSelection.normalizeLanguage(null))
        assertEquals("", TrackSelection.normalizeLanguage(""))
        assertEquals("", TrackSelection.normalizeLanguage("und"))
        assertEquals("", TrackSelection.normalizeLanguage("unknown"))
        assertEquals("", TrackSelection.normalizeLanguage("zxx"))
    }

    @Test
    fun languageAliasesReturnsTerminologicBibliographicAndTwoLetterVariants() {
        val aliases = TrackSelection.languageAliases("fr")
        assertTrue(aliases.containsAll(listOf("fra", "fre", "fr")))
        assertEquals(emptyList<String>(), TrackSelection.languageAliases("und"))
    }

    @Test
    fun resolveSubtitleModeUsesOverrideThenServerThenSmartDefault() {
        assertEquals(
            SubtitlePlaybackMode.ALWAYS,
            TrackSelection.resolveSubtitleMode("Always", SubtitlePlaybackMode.NONE),
        )
        assertEquals(
            SubtitlePlaybackMode.ONLY_FORCED,
            TrackSelection.resolveSubtitleMode("", SubtitlePlaybackMode.ONLY_FORCED),
        )
        assertEquals(
            SubtitlePlaybackMode.SMART,
            TrackSelection.resolveSubtitleMode("not_a_mode", null),
        )
        assertTrue(TrackSelection.usesHearingImpairedPreference(SubtitlePlaybackMode.ALWAYS))
        assertTrue(TrackSelection.usesHearingImpairedPreference(SubtitlePlaybackMode.SMART))
        assertFalse(TrackSelection.usesHearingImpairedPreference(SubtitlePlaybackMode.ONLY_FORCED))
        assertFalse(TrackSelection.usesHearingImpairedPreference(SubtitlePlaybackMode.NONE))
    }

    @Test
    fun audioSelectionHonorsRequestedPreferredDefaultServerAndExternalRules() {
        val streams =
            listOf(
                audio(index = 1, lang = "jpn", isDefault = true),
                audio(index = 2, lang = "eng", isDefault = false, isExternal = true),
                audio(index = 3, lang = "eng", isDefault = false, isExternal = false),
            )

        // Preferred language picks internal over external for the same language.
        val preferredRes =
            TrackSelection.select(
                audioStreams = streams,
                subtitleStreams = emptyList(),
                subtitleMode = SubtitlePlaybackMode.NONE,
                preferredAudioLanguage = "en-US",
                preferredSubtitleLanguage = "",
                allowExternalAudio = true,
            )
        assertEquals(2, preferredRes.audioPosition)

        // Requested index overrides preference.
        val requestedRes =
            TrackSelection.select(
                audioStreams = streams,
                subtitleStreams = emptyList(),
                subtitleMode = SubtitlePlaybackMode.NONE,
                preferredAudioLanguage = "en",
                preferredSubtitleLanguage = "",
                requestedAudioStreamIndex = 1,
            )
        assertEquals(0, requestedRes.audioPosition)

        // External audio excluded when allowExternalAudio = false.
        val externalExcluded =
            TrackSelection.select(
                audioStreams =
                    listOf(audio(1, lang = "eng", isExternal = true), audio(2, lang = "fra")),
                subtitleStreams = emptyList(),
                subtitleMode = SubtitlePlaybackMode.NONE,
                preferredAudioLanguage = "en",
                preferredSubtitleLanguage = "",
                allowExternalAudio = false,
            )
        assertEquals(1, externalExcluded.audioPosition)

        // playDefaultAudioTrack picks default when preferred language is blank.
        val defaultRes =
            TrackSelection.select(
                audioStreams =
                    listOf(audio(1, lang = "fra"), audio(2, lang = "deu", isDefault = true)),
                subtitleStreams = emptyList(),
                subtitleMode = SubtitlePlaybackMode.NONE,
                preferredAudioLanguage = "",
                preferredSubtitleLanguage = "",
                playDefaultAudioTrack = true,
            )
        assertEquals(1, defaultRes.audioPosition)

        // serverDefaultAudioStreamIndex used when playDefaultAudioTrack is false and preferred
        // blank.
        val serverDefault =
            TrackSelection.select(
                audioStreams = listOf(audio(1, lang = "fra"), audio(5, lang = "spa")),
                subtitleStreams = emptyList(),
                subtitleMode = SubtitlePlaybackMode.NONE,
                preferredAudioLanguage = "",
                preferredSubtitleLanguage = "",
                serverDefaultAudioStreamIndex = 5,
                playDefaultAudioTrack = false,
            )
        assertEquals(1, serverDefault.audioPosition)
    }

    @Test
    fun subtitleSelectionCoversAllPlaybackModesAndCodecPriority() {
        val subs =
            listOf(
                sub(index = 10, lang = "eng", codec = "pgs", isDefault = true),
                sub(index = 11, lang = "eng", codec = "ass", isForced = false),
                sub(
                    index = 12,
                    lang = "eng",
                    codec = "srt",
                    isExternal = true,
                    isHearingImpaired = true,
                ),
                sub(index = 13, lang = "eng", codec = "srt", isForced = true),
            )
        val foreignAudio = listOf(audio(index = 1, lang = "jpn"))
        val englishAudio = listOf(audio(index = 1, lang = "eng"))

        // Requested negative subtitle index explicitly disables subtitles.
        assertEquals(
            TrackSelection.NO_SUBTITLE,
            TrackSelection.select(
                    foreignAudio,
                    subs,
                    SubtitlePlaybackMode.ALWAYS,
                    "en",
                    "en",
                    requestedSubtitleStreamIndex = -1,
                )
                .subtitlePosition,
        )

        // NONE mode disables subtitles unless explicitly requested.
        assertEquals(
            TrackSelection.NO_SUBTITLE,
            TrackSelection.select(
                    foreignAudio,
                    subs,
                    SubtitlePlaybackMode.NONE,
                    "en",
                    "en",
                )
                .subtitlePosition,
        )

        // ONLY_FORCED prefers forced track in preferred language even if audio matches.
        assertEquals(
            3,
            TrackSelection.select(
                    englishAudio,
                    subs,
                    SubtitlePlaybackMode.ONLY_FORCED,
                    "en",
                    "en",
                )
                .subtitlePosition,
        )

        // ALWAYS with preferHearingImpaired = true + external priority selects external HI srt
        // (index 2).
        assertEquals(
            2,
            TrackSelection.select(
                    englishAudio,
                    subs,
                    SubtitlePlaybackMode.ALWAYS,
                    "en",
                    "en",
                    preferHearingImpaired = true,
                )
                .subtitlePosition,
        )

        // SMART on matching audio selects only forced; SMART on foreign audio selects full
        // subtitle.
        assertEquals(
            3,
            TrackSelection.select(
                    englishAudio,
                    subs,
                    SubtitlePlaybackMode.SMART,
                    "en",
                    "en",
                )
                .subtitlePosition,
        )
        assertEquals(
            2,
            TrackSelection.select(
                    foreignAudio,
                    listOf(
                        sub(index = 10, lang = "eng", codec = "srt"),
                        sub(index = 11, lang = "eng", codec = "pgs"),
                        sub(index = 12, lang = "eng", codec = "ass"),
                    ),
                    SubtitlePlaybackMode.SMART,
                    "en",
                    "en",
                )
                .subtitlePosition,
        )

        // DEFAULT honors serverDefaultSubtitleStreamIndex.
        assertEquals(
            1,
            TrackSelection.select(
                    englishAudio,
                    subs,
                    SubtitlePlaybackMode.DEFAULT,
                    "en",
                    "en",
                    serverDefaultSubtitleStreamIndex = 11,
                )
                .subtitlePosition,
        )
    }
}
