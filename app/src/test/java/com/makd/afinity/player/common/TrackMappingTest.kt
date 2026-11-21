package com.makd.afinity.player.common

import com.makd.afinity.testutil.TestMediaFixtures.testStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMappingTest {

    @Test
    fun deviceSubtitleIndexHelpersIdentifyAndUnmapSyntheticIndices() {
        assertEquals(4096, TrackMapping.deviceSubtitleIndex(0))
        assertEquals(4099, TrackMapping.deviceSubtitleIndex(3))

        assertTrue(TrackMapping.isDeviceSubtitleIndex(4096))
        assertFalse(TrackMapping.isDeviceSubtitleIndex(4095))
        assertFalse(TrackMapping.isDeviceSubtitleIndex(null))

        assertNull(TrackMapping.serverSubtitleIndex(4096))
        assertEquals(5, TrackMapping.serverSubtitleIndex(5))
        assertNull(TrackMapping.serverSubtitleIndex(null))
    }

    @Test
    fun embeddedOrdinalIgnoresExternalStreamsAndMapsRoundTrip() {
        val streams =
            listOf(
                testStream(index = 2, type = MediaStreamType.SUBTITLE, isExternal = true),
                testStream(index = 4, type = MediaStreamType.SUBTITLE, isExternal = false),
                testStream(index = 6, type = MediaStreamType.SUBTITLE, isExternal = false),
            )

        assertNull(TrackMapping.embeddedOrdinal(streams, 2))
        assertEquals(0, TrackMapping.embeddedOrdinal(streams, 4))
        assertEquals(1, TrackMapping.embeddedOrdinal(streams, 6))
        assertNull(TrackMapping.embeddedOrdinal(streams, 99))

        assertEquals(4, TrackMapping.streamIndexAtEmbeddedOrdinal(streams, 0))
        assertEquals(6, TrackMapping.streamIndexAtEmbeddedOrdinal(streams, 1))
        assertNull(TrackMapping.streamIndexAtEmbeddedOrdinal(streams, 2))
    }

    @Test
    fun audioSortKeyPadsNumericIdsAndStripsSourcePrefix() {
        assertNull(TrackMapping.audioSortKey(null))
        assertEquals("00003", TrackMapping.audioSortKey("3"))
        assertEquals("00012", TrackMapping.audioSortKey("1:12"))
        assertEquals("00001", TrackMapping.audioSortKey("0:1"))
        assertEquals("audio_eng", TrackMapping.audioSortKey("1:audio_eng"))
        assertEquals("raw", TrackMapping.audioSortKey("raw"))
    }

    @Test
    fun sideLoadedIdRoundTripsWithAndWithoutSourcePrefix() {
        assertEquals("133", TrackMapping.sideLoadedId(5))
        assertEquals(5, TrackMapping.streamIndexFromSideLoadedId("133"))
        assertEquals(5, TrackMapping.streamIndexFromSideLoadedId("1:133"))
        assertNull(TrackMapping.streamIndexFromSideLoadedId("0:133"))
        assertNull(TrackMapping.streamIndexFromSideLoadedId("127"))
        assertNull(TrackMapping.streamIndexFromSideLoadedId("abc:133"))
        assertNull(TrackMapping.streamIndexFromSideLoadedId(null))
    }

    @Test
    fun streamIndexFromExternalUriMatchesExactUri() {
        val map = mapOf(7 to "https://server/sub/7.srt", 8 to "https://server/sub/8.ass")
        assertEquals(8, TrackMapping.streamIndexFromExternalUri("https://server/sub/8.ass", map))
        assertNull(TrackMapping.streamIndexFromExternalUri("https://server/sub/9.srt", map))
        assertNull(TrackMapping.streamIndexFromExternalUri(null, map))
    }
}
