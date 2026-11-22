package com.makd.afinity.player.profile

import com.makd.afinity.data.models.player.VideoQuality
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.jellyfin.sdk.model.api.CodecType
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.ProfileConditionValue
import org.jellyfin.sdk.model.api.VideoRangeType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeviceCodecsAndProfileFactoryTest {

    @Before
    fun setUp() {
        mockkObject(DeviceCodecs)
    }

    @After
    fun tearDown() {
        unmockkObject(DeviceCodecs)
    }

    private fun sampleCapabilities(
        audioCodecs: Set<String> = setOf("aac", "ac3", "opus", "flac", "mp3"),
    ) =
        DeviceCodecs.Capabilities(
            videoCodecs = setOf("h264", "hevc", "av1"),
            audioCodecs = audioCodecs,
            videoProfiles = mapOf("h264" to setOf("high", "main"), "hevc" to setOf("Main 10")),
            videoLevels = mapOf("h264" to 51, "hevc" to 153),
            videoRanges = setOf(VideoRangeType.SDR, VideoRangeType.HDR10, VideoRangeType.DOVI),
        )

    @Test
    fun `Capabilities filters video and audio codecs per container index`() {
        val caps = sampleCapabilities(audioCodecs = setOf("aac", "vorbis", "opus", "ac3"))
        val webmIndex = DeviceCodecs.CONTAINERS.indexOf("webm")
        val mp4Index = DeviceCodecs.CONTAINERS.indexOf("mp4")

        assertEquals(listOf("av1"), caps.videoCodecsFor(webmIndex))
        assertEquals(listOf("vorbis", "opus"), caps.audioCodecsFor(webmIndex))
        assertEquals(listOf("h264", "hevc", "av1"), caps.videoCodecsFor(mp4Index))
        assertEquals(listOf("aac", "ac3", "opus"), caps.audioCodecsFor(mp4Index))
    }

    @Test
    fun `createExoPlayerProfile configures direct play, codec limits, HDR filtering, and transcoding`() {
        every { DeviceCodecs.detect() } returns
            sampleCapabilities(audioCodecs = setOf("opus", "mp3"))
        val factory = AndroidDeviceProfileFactory()

        val hdrProfile =
            factory.createExoPlayerProfile(
                quality = VideoQuality(20_000_000, 1920),
                maxAudioChannels = 6,
                allowHdrPassthrough = true,
                allowTranscoding = true,
            )

        assertEquals(20_000_000, hdrProfile.maxStreamingBitrate)
        assertTrue(
            hdrProfile.directPlayProfiles.any {
                it.type == DlnaProfileType.VIDEO && it.container == "mp4"
            },
        )
        assertTrue(
            hdrProfile.directPlayProfiles.any {
                it.type == DlnaProfileType.AUDIO && it.container == "ogg"
            },
        )

        val h264Profile =
            hdrProfile.codecProfiles.first { it.type == CodecType.VIDEO && it.codec == "h264" }
        assertTrue(
            h264Profile.conditions.any {
                it.property == ProfileConditionValue.VIDEO_PROFILE && it.value == "high|main"
            },
        )
        assertTrue(
            h264Profile.conditions.any {
                it.property == ProfileConditionValue.VIDEO_LEVEL && it.value == "51"
            },
        )

        val globalProfile = hdrProfile.codecProfiles.first { it.codec == null }
        val widthCond =
            globalProfile.conditions.first { it.property == ProfileConditionValue.WIDTH }
        assertEquals("1920", widthCond.value)
        val hdrRangeCond =
            globalProfile.conditions.first { it.property == ProfileConditionValue.VIDEO_RANGE_TYPE }
        assertTrue(hdrRangeCond.value.orEmpty().contains(VideoRangeType.HDR10.serialName))
        assertTrue(hdrRangeCond.value.orEmpty().contains(VideoRangeType.DOVI.serialName))

        // Transcoding fallback always ensures aac is included in video TS audioCodec list
        val videoTranscode =
            hdrProfile.transcodingProfiles.first { it.type == DlnaProfileType.VIDEO }
        assertEquals("6", videoTranscode.maxAudioChannels)
        assertTrue(videoTranscode.audioCodec.orEmpty().split(",").contains("aac"))

        // Disabling HDR passthrough restricts global video range condition to SDR only
        val sdrOnlyNoTranscode =
            factory.createExoPlayerProfile(
                quality = VideoQuality.ORIGINAL,
                maxAudioChannels = 2,
                allowHdrPassthrough = false,
                allowTranscoding = false,
            )
        assertTrue(sdrOnlyNoTranscode.transcodingProfiles.isEmpty())
        val sdrRange =
            sdrOnlyNoTranscode.codecProfiles
                .first { it.codec == null }
                .conditions
                .first { it.property == ProfileConditionValue.VIDEO_RANGE_TYPE }
        assertEquals(VideoRangeType.SDR.serialName, sdrRange.value)
    }

    @Test
    fun `createDownloadProfile and createMusicProfile set expected profiles`() {
        every { DeviceCodecs.detect() } returns sampleCapabilities()
        val factory = AndroidDeviceProfileFactory()

        val download =
            factory.createDownloadProfile(
                quality = VideoQuality(8_000_000, 1920),
                maxAudioChannels = 2,
                allowHdrPassthrough = false,
            )
        assertEquals(1, download.transcodingProfiles.size)
        assertEquals(
            AndroidDeviceProfileFactory.DOWNLOAD_TRANSCODE_CONTAINER,
            download.transcodingProfiles.first().container,
        )

        val music = factory.createMusicProfile(maxStreamingBitrate = 320_000)
        assertEquals(320_000, music.maxStreamingBitrate)
        assertNotNull(music.directPlayProfiles.firstOrNull { it.container == "m4a,m4b" })
        assertFalse(music.transcodingProfiles.isEmpty())
        assertEquals("aac", music.transcodingProfiles.first().audioCodec)
    }
}
