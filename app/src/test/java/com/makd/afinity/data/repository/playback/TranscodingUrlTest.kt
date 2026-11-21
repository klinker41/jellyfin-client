package com.makd.afinity.data.repository.playback

import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.TranscodeReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranscodingUrlTest {

    @Test
    fun `query accessors parse transcodeReasons, subtitle, audio, and burn-in parameters`() {
        val url =
            "https://jf.example.com/Videos/1/master.m3u8" +
                "?TranscodeReasons=${TranscodeReason.CONTAINER_NOT_SUPPORTED.serialName}%2C${TranscodeReason.VIDEO_CODEC_NOT_SUPPORTED.serialName},UnknownReason" +
                "&SubtitleStreamIndex=3&SubtitleMethod=${SubtitleDeliveryMethod.ENCODE.serialName}&AudioStreamIndex=2"

        assertEquals(
            listOf(
                TranscodeReason.CONTAINER_NOT_SUPPORTED,
                TranscodeReason.VIDEO_CODEC_NOT_SUPPORTED,
            ),
            TranscodingUrl.transcodeReasons(url),
        )
        assertEquals(3, TranscodingUrl.subtitleStreamIndex(url))
        assertEquals(SubtitleDeliveryMethod.ENCODE, TranscodingUrl.subtitleMethod(url))
        assertEquals(3, TranscodingUrl.burnedInSubtitleIndex(url))
        assertEquals(2, TranscodingUrl.audioStreamIndex(url))

        val externalSubs =
            "https://jf.example.com/Videos/1/master.m3u8?SubtitleStreamIndex=3&SubtitleMethod=${SubtitleDeliveryMethod.EXTERNAL.serialName}"
        assertNull(TranscodingUrl.burnedInSubtitleIndex(externalSubs))
    }

    @Test
    fun `mutators append and replace query parameters while preserving existing params`() {
        val bare = "https://jf.example.com/Videos/1/master.m3u8"
        val withSub = TranscodingUrl.withSubtitleStreamIndex(bare, 4)
        assertEquals("$bare?SubtitleStreamIndex=4", withSub)

        val cleared = TranscodingUrl.withSubtitleStreamIndex(withSub, null)
        assertEquals("$bare?SubtitleStreamIndex=-1", cleared)

        val burned = TranscodingUrl.withBurnedInSubtitle("$bare?AudioStreamIndex=1", 5)
        assertEquals(5, TranscodingUrl.burnedInSubtitleIndex(burned))
        assertEquals(1, TranscodingUrl.audioStreamIndex(burned))

        val burnDisabled = TranscodingUrl.withBurnedInSubtitle(burned, -1)
        assertEquals(TranscodingUrl.NO_SUBTITLE, TranscodingUrl.subtitleStreamIndex(burnDisabled))

        val withAudioAndStart =
            TranscodingUrl.withStartTimeTicks(
                TranscodingUrl.withAudioStreamIndex(burned, 7),
                120_000_000L,
            )
        assertEquals(7, TranscodingUrl.audioStreamIndex(withAudioAndStart))
        assertEquals(5, TranscodingUrl.burnedInSubtitleIndex(withAudioAndStart))
        assertEquals(
            "$bare?AudioStreamIndex=7&SubtitleStreamIndex=5&SubtitleMethod=Encode&StartTimeTicks=120000000",
            withAudioAndStart,
        )
    }
}
