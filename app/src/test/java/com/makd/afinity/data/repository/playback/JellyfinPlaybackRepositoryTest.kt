package com.makd.afinity.data.repository.playback

import com.makd.afinity.data.autodownload.PlayedItemEventBus
import com.makd.afinity.data.manager.Session
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.player.StreamDecision
import com.makd.afinity.data.models.player.VideoQuality
import com.makd.afinity.data.models.user.AfinityUserDataDto
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.player.profile.AndroidDeviceProfileFactory
import com.makd.afinity.player.profile.DeviceCodecs
import com.makd.afinity.testutil.FakeApiClient
import com.makd.afinity.testutil.TestMediaFixtures.testMovie
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.TranscodeReason
import org.jellyfin.sdk.model.api.VideoRangeType
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JellyfinPlaybackRepositoryTest {

    @Before
    fun setUp() {
        mockkObject(DeviceCodecs)
        every { DeviceCodecs.detect() } returns
            DeviceCodecs.Capabilities(
                videoCodecs = setOf("h264", "hevc"),
                audioCodecs = setOf("aac", "ac3", "opus"),
                videoProfiles = mapOf("h264" to setOf("high")),
                videoLevels = mapOf("h264" to 51),
                videoRanges = setOf(VideoRangeType.SDR, VideoRangeType.HDR10),
            )
    }

    @After
    fun tearDown() {
        unmockkObject(DeviceCodecs)
    }

    private class Harness(hasSession: Boolean = true, offlineMode: Boolean = false) {
        val userId = testUuid(1)
        val serverId = "srv-1"
        val apiClient = FakeApiClient(baseUrl = "https://jf.example.com", accessToken = "tok-1")
        val sessionFlow =
            MutableStateFlow(
                if (hasSession) Session(serverId, userId, "https://jf.example.com") else null,
            )
        val sessionManager: SessionManager = mockk {
            every { currentSession } returns sessionFlow
            every { getCurrentApiClient() } answers
                {
                    if (sessionFlow.value != null) apiClient else null
                }
        }
        val databaseRepository: DatabaseRepository = mockk(relaxed = true)
        val preferencesRepository: PreferencesRepository =
            mockk(relaxed = true) {
                coEvery { getTranscodeMaxAudioChannels() } returns 6
                coEvery { getAllowHdrPassthrough() } returns true
                coEvery { getOfflineMode() } returns offlineMode
            }
        val deviceProfileFactory = AndroidDeviceProfileFactory()
        val playedItemEventBus = PlayedItemEventBus()

        val repository =
            JellyfinPlaybackRepository(
                sessionManager = sessionManager,
                databaseRepository = databaseRepository,
                preferencesRepository = preferencesRepository,
                deviceProfileFactory = deviceProfileFactory,
                playedItemEventBus = playedItemEventBus,
            )
    }

    private fun mediaSourceJson(
        id: String = "src-1",
        container: String = "mkv",
        supportsDirectPlay: Boolean = true,
        supportsDirectStream: Boolean = true,
        supportsTranscoding: Boolean = false,
        transcodingUrl: String? = null,
        transcodingSubProtocol: String = "http",
        eTag: String? = "etag-1",
    ): String =
        FakeApiClient.mediaSourceJson(
            id = id,
            container = container,
            supportsDirectPlay = supportsDirectPlay,
            supportsDirectStream = supportsDirectStream,
            supportsTranscoding = supportsTranscoding,
            transcodingUrl = transcodingUrl,
            transcodingSubProtocol = transcodingSubProtocol,
            eTag = eTag,
        )

    @Test
    fun `getPlaybackInfo and getMediaSources send PlaybackInfoDto and handle failures`() = runTest {
        val noSession = Harness(hasSession = false)
        assertNull(noSession.repository.getPlaybackInfo(testUuid(10)))
        assertTrue(noSession.repository.getMediaSources(testUuid(10)).isEmpty())

        val h = Harness()
        val itemId = testUuid(10)
        h.apiClient.onPost("/Items/{itemId}/PlaybackInfo") {
            FakeApiClient.rawJsonResponse(
                """{
                "MediaSources":[${mediaSourceJson(id = "src-4k")}],
                "PlaySessionId":"ps-123"
            }""",
            )
        }

        val info =
            h.repository.getPlaybackInfo(
                itemId = itemId,
                quality = VideoQuality(20_000_000, 1920),
                maxAudioChannels = 6,
                audioStreamIndex = 2,
                subtitleStreamIndex = 4,
                mediaSourceId = "src-4k",
                startTimeTicks = 500_000L,
                enableDirectPlay = true,
                allowTranscoding = false,
            )

        assertNotNull(info)
        assertEquals("ps-123", info!!.playSessionId)
        assertEquals(listOf("src-4k"), info.mediaSources.map { it.id })

        val req = h.apiClient.requests.single()
        assertEquals(itemId, req.pathParameters["itemId"])
        val dto = req.requestBody as PlaybackInfoDto
        assertEquals(h.userId, dto.userId)
        assertEquals(20_000_000, dto.maxStreamingBitrate)
        assertEquals(2, dto.audioStreamIndex)
        assertEquals(4, dto.subtitleStreamIndex)
        assertEquals(false, dto.enableTranscoding)
        assertTrue(dto.deviceProfile!!.transcodingProfiles.isEmpty())

        val sources = h.repository.getMediaSources(itemId)
        assertEquals(1, sources.size)
        assertEquals("src-4k", sources.first().id)

        // ApiClientException returns null / emptyList cleanly
        h.apiClient.onPost("/Items/{itemId}/PlaybackInfo") { throw ApiClientException("bad") }
        assertNull(h.repository.getPlaybackInfo(itemId))
        assertTrue(h.repository.getMediaSources(itemId).isEmpty())

        // CancellationException is rethrown
        h.apiClient.onPost("/Items/{itemId}/PlaybackInfo") {
            throw CancellationException("cancelled")
        }
        val cancelled = runCatching { h.repository.getPlaybackInfo(itemId) }.exceptionOrNull()
        assertTrue(cancelled is CancellationException)
    }

    @Test
    fun `getStreamUrl, resolveStream, and resolveAudioStream cover direct play, direct stream, transcode, and errors`() =
        runTest {
            val noSession = Harness(hasSession = false)
            assertNull(noSession.repository.getStreamUrl(testUuid(1), "src-1"))

            val h = Harness()
            val itemId = testUuid(20)
            val streamUrl =
                h.repository.getStreamUrl(
                    itemId = itemId,
                    mediaSourceId = "src-1",
                    audioStreamIndex = 1,
                    subtitleStreamIndex = 3,
                    startTimeTicks = 10_000L,
                    playSessionId = "ps-1",
                    tag = "etag-1",
                )
            assertNotNull(streamUrl)
            assertTrue(streamUrl!!.startsWith("https://jf.example.com/Videos/$itemId/stream"))
            assertTrue(streamUrl.contains("mediaSourceId=src-1"))
            assertTrue(streamUrl.contains("audioStreamIndex=1"))
            assertTrue(streamUrl.contains("subtitleStreamIndex=3"))
            assertTrue(streamUrl.contains("playSessionId=ps-1"))

            val directSource =
                FakeApiClient.decodeJson<org.jellyfin.sdk.model.api.MediaSourceInfo>(
                    mediaSourceJson(id = "src-dp", supportsDirectPlay = true),
                )
            val directDecision =
                h.repository.resolveStream(
                    itemId = itemId,
                    source = directSource,
                    audioStreamIndex = 1,
                    subtitleStreamIndex = 2,
                    playSessionId = "ps-dp",
                )
            assertTrue(directDecision is StreamDecision.DirectPlay)

            val directStreamSource =
                FakeApiClient.decodeJson<org.jellyfin.sdk.model.api.MediaSourceInfo>(
                    mediaSourceJson(
                        id = "src-ds",
                        supportsDirectPlay = false,
                        supportsDirectStream = true,
                        supportsTranscoding = true,
                        transcodingUrl =
                            "/Videos/$itemId/master.m3u8?TranscodeReasons=ContainerNotSupported&SubtitleStreamIndex=0",
                        transcodingSubProtocol = "hls",
                    ),
                )
            val dsDecision =
                h.repository.resolveStream(
                    itemId = itemId,
                    source = directStreamSource,
                    audioStreamIndex = 2,
                    subtitleStreamIndex = 4,
                    startTimeTicks = 250_000L,
                ) as StreamDecision.DirectStream
            assertEquals(MediaStreamProtocol.HLS, dsDecision.protocol)
            assertEquals(
                listOf(TranscodeReason.CONTAINER_NOT_SUPPORTED),
                dsDecision.transcodeReasons,
            )
            assertTrue(
                dsDecision.url.startsWith("https://jf.example.com/Videos/$itemId/master.m3u8"),
            )
            assertEquals(2, TranscodingUrl.audioStreamIndex(dsDecision.url))
            assertEquals(4, TranscodingUrl.subtitleStreamIndex(dsDecision.url))

            val transcodeSource =
                FakeApiClient.decodeJson<org.jellyfin.sdk.model.api.MediaSourceInfo>(
                    mediaSourceJson(
                        id = "src-tr",
                        supportsDirectPlay = false,
                        supportsDirectStream = false,
                        supportsTranscoding = true,
                        transcodingUrl =
                            "/Videos/$itemId/master.m3u8?TranscodeReasons=VideoCodecNotSupported&SubtitleStreamIndex=5&SubtitleMethod=Encode",
                        transcodingSubProtocol = "hls",
                    ),
                )
            val trDecision =
                h.repository.resolveStream(
                    itemId = itemId,
                    source = transcodeSource,
                    subtitleStreamIndex = 5,
                ) as StreamDecision.Transcode
            assertEquals(5, trDecision.burnedInSubtitleIndex)
            assertEquals(
                listOf(TranscodeReason.VIDEO_CODEC_NOT_SUPPORTED),
                trDecision.transcodeReasons,
            )

            val unsupportedSource =
                FakeApiClient.decodeJson<org.jellyfin.sdk.model.api.MediaSourceInfo>(
                    mediaSourceJson(
                        id = "src-none",
                        supportsDirectPlay = false,
                        supportsDirectStream = false,
                        supportsTranscoding = false,
                        transcodingUrl = null,
                    ),
                )
            assertNull(h.repository.resolveStream(itemId, unsupportedSource))

            // resolveAudioStream: direct play then transcode then null
            val trackId = testUuid(25)
            h.apiClient.onPost("/Items/{itemId}/PlaybackInfo") {
                FakeApiClient.rawJsonResponse(
                    """{"MediaSources":[${mediaSourceJson(id = "aud-dp", container = "flac", supportsDirectPlay = true)}]}""",
                )
            }
            val audDirect = h.repository.resolveAudioStream(trackId, playSessionId = "aps-1")
            assertTrue(audDirect is StreamDecision.DirectPlay)
            assertTrue(audDirect!!.url.contains("/Audio/$trackId/stream"))

            h.apiClient.onPost("/Items/{itemId}/PlaybackInfo") {
                FakeApiClient.rawJsonResponse(
                    """{"MediaSources":[${mediaSourceJson(id = "aud-tr", container = "flac", supportsDirectPlay = false, supportsDirectStream = false, supportsTranscoding = true, transcodingUrl = "/Audio/$trackId/universal?TranscodeReasons=AudioBitrateNotSupported", transcodingSubProtocol = "http")}]}""",
                )
            }
            val audTranscode =
                h.repository.resolveAudioStream(trackId, maxStreamingBitrate = 128_000)
                    as StreamDecision.Transcode
            assertEquals(MediaStreamProtocol.HTTP, audTranscode.protocol)
            assertEquals(
                listOf(TranscodeReason.AUDIO_BITRATE_NOT_SUPPORTED),
                audTranscode.transcodeReasons,
            )
        }

    @Test
    fun `reportPlaybackStart, reportPlaybackProgress, and reportPlaybackStop handle online, offline, and error fallback`() =
        runTest {
            val h = Harness()
            val itemId = testUuid(30)
            h.apiClient.onPost("/Sessions/Playing") { FakeApiClient.emptyResponse(204) }
            h.apiClient.onPost("/Sessions/Playing/Progress") { FakeApiClient.emptyResponse(204) }
            h.apiClient.onPost("/Sessions/Playing/Stopped") { FakeApiClient.emptyResponse(204) }

            assertTrue(
                h.repository.reportPlaybackStart(
                    itemId = itemId,
                    sessionId = "sess-1",
                    mediaSourceId = "src-1",
                    audioStreamIndex = 1,
                    subtitleStreamIndex = 2,
                    positionTicks = 45_000_000L,
                ),
            )
            val startBody = h.apiClient.requests.last().requestBody as PlaybackStartInfo
            assertEquals(itemId, startBody.itemId)
            assertEquals("sess-1", startBody.playSessionId)
            assertEquals(45_000_000L, startBody.positionTicks)

            assertTrue(
                h.repository.reportPlaybackProgress(
                    itemId = itemId,
                    sessionId = "sess-1",
                    positionTicks = 60_000_000L,
                    isPaused = true,
                ),
            )
            val progBody = h.apiClient.requests.last().requestBody as PlaybackProgressInfo
            assertEquals(60_000_000L, progBody.positionTicks)
            assertTrue(progBody.isPaused)

            assertTrue(
                h.repository.reportPlaybackStop(
                    itemId = itemId,
                    sessionId = "sess-1",
                    positionTicks = 90_000_000L,
                    mediaSourceId = "src-1",
                    runtimeTicks = 100_000_000L,
                ),
            )
            val stopBody = h.apiClient.requests.last().requestBody as PlaybackStopInfo
            assertEquals(90_000_000L, stopBody.positionTicks)
            coVerify { h.databaseRepository.markUserDataSynced(h.userId, itemId, h.serverId) }

            // Music stop does NOT clear pending sync on video user data
            val trackId = testUuid(31)
            assertTrue(
                h.repository.reportPlaybackStop(
                    itemId = trackId,
                    sessionId = "sess-m",
                    positionTicks = 5_000_000L,
                    mediaSourceId = "src-m",
                    isMusic = true,
                ),
            )
            coVerify(exactly = 0) { h.databaseRepository.markUserDataSynced(any(), trackId, any()) }

            // Offline / API failure saves progress locally with toBeSynced = true
            val existingUserData =
                AfinityUserDataDto(
                    userId = h.userId,
                    itemId = itemId,
                    serverId = h.serverId,
                    played = false,
                    favorite = true,
                    likes = true,
                    playbackPositionTicks = 10_000_000L,
                    toBeSynced = false,
                )
            coEvery { h.databaseRepository.getUserData(h.userId, itemId) } returns existingUserData
            coEvery { h.databaseRepository.getEpisode(itemId, h.userId) } returns null
            coEvery { h.databaseRepository.getMovie(itemId, h.userId) } returns
                testMovie(id = itemId, runtimeTicks = 10_000_000_000L)

            val offline = Harness(offlineMode = true)
            assertFalse(
                offline.repository.reportPlaybackStart(itemId, "sess-1", "src-1"),
            )

            h.apiClient.onPost("/Sessions/Playing/Progress") { throw ApiClientException("503") }
            assertFalse(
                h.repository.reportPlaybackProgress(
                    itemId = itemId,
                    sessionId = "sess-1",
                    positionTicks = 120_000_000L,
                    audioStreamIndex = 3,
                    subtitleStreamIndex = 5,
                ),
            )
            val savedProgress = slot<AfinityUserDataDto>()
            coVerify { h.databaseRepository.insertUserData(capture(savedProgress)) }
            assertTrue(savedProgress.captured.toBeSynced)
            assertTrue(savedProgress.captured.favorite)
            assertEquals(120_000_000L, savedProgress.captured.playbackPositionTicks)
            assertEquals(3, savedProgress.captured.audioStreamIndex)

            // Stop error resolves completion (>90% marks played = true, position = 0)
            val playedEmitted = mutableListOf<java.util.UUID>()
            val playedCollector =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    h.playedItemEventBus.playedItems.take(1).toList(playedEmitted)
                }
            h.apiClient.onPost("/Sessions/Playing/Stopped") { throw ApiClientException("503") }
            assertFalse(
                h.repository.reportPlaybackStop(
                    itemId = itemId,
                    sessionId = "sess-1",
                    positionTicks = 9_500_000_000L,
                    mediaSourceId = "src-1",
                    runtimeTicks = 0L, // resolved via databaseRepository.getMovie
                    isEnded = false,
                ),
            )
            playedCollector.join()
            assertEquals(listOf(itemId), playedEmitted)
            val saved = mutableListOf<AfinityUserDataDto>()
            coVerify(atLeast = 2) { h.databaseRepository.insertUserData(capture(saved)) }
            val savedStop = saved.last()
            assertTrue(savedStop.played)
            assertEquals(0L, savedStop.playbackPositionTicks)
            assertTrue(savedStop.toBeSynced)

            // Offline music stop records offline play unless already counted on server
            assertFalse(
                h.repository.reportPlaybackStop(
                    itemId = trackId,
                    sessionId = "sess-m",
                    positionTicks = 2_000_000L,
                    mediaSourceId = "src-m",
                    isMusic = true,
                    playCountedOnServer = false,
                ),
            )
            coVerify(exactly = 1) {
                h.databaseRepository.insertOfflinePlay(trackId, h.userId, h.serverId, any())
            }

            assertFalse(
                h.repository.reportPlaybackStop(
                    itemId = trackId,
                    sessionId = "sess-m",
                    positionTicks = 2_000_000L,
                    mediaSourceId = "src-m",
                    isMusic = true,
                    playCountedOnServer = true,
                ),
            )
            coVerify(exactly = 1) {
                h.databaseRepository.insertOfflinePlay(trackId, h.userId, h.serverId, any())
            }
        }

    @Test
    fun `pingSession, getActiveSession, getTranscodingInfo, getBitrateTestBytes, and getPlaybackInfoForCast work as expected`() =
        runTest {
            val h = Harness()
            h.apiClient.onPost("/Sessions/Playing/Ping") { FakeApiClient.emptyResponse(204) }
            assertTrue(h.repository.pingSession("ps-1"))

            h.apiClient.onGet("/Sessions") {
                FakeApiClient.rawJsonResponse(
                    """[
                    {"Id":"sess-other","UserId":"00000000-0000-0000-0000-000000000001","DeviceId":"other-dev","PlayableMediaTypes":[],"LastActivityDate":"2026-01-01T00:00:00Z","LastPlaybackCheckIn":"2026-01-01T00:00:00Z","IsActive":true,"SupportsMediaControl":false,"SupportsRemoteControl":false,"HasCustomDeviceName":false,"SupportedCommands":[]},
                    {"Id":"sess-ours","UserId":"00000000-0000-0000-0000-000000000001","DeviceId":"device-1","PlayableMediaTypes":[],"LastActivityDate":"2026-01-01T00:00:00Z","LastPlaybackCheckIn":"2026-01-01T00:00:00Z","IsActive":true,"SupportsMediaControl":false,"SupportsRemoteControl":false,"HasCustomDeviceName":false,"SupportedCommands":[],"TranscodingInfo":{"IsVideoDirect":false,"IsAudioDirect":true,"Bitrate":6000000,"CompletionPercentage":42.0,"TranscodeReasons":[]}}
                ]""",
                )
            }
            assertEquals("sess-ours", h.repository.getActiveSession())

            val transcodeInfo = h.repository.getTranscodingInfo()
            assertNotNull(transcodeInfo)
            assertEquals(6_000_000, transcodeInfo!!.bitrate)
            assertEquals(false, transcodeInfo.isVideoDirect)

            val payload = byteArrayOf(1, 2, 3, 4)
            h.apiClient.onGet("/Playback/BitrateTest") { FakeApiClient.bytesResponse(payload) }
            val bytes = h.repository.getBitrateTestBytes(500)
            assertArrayEquals(payload, bytes)

            val itemId = testUuid(40)
            h.apiClient.onPost("/Items/{itemId}/PlaybackInfo") {
                FakeApiClient.rawJsonResponse(
                    """{"MediaSources":[${mediaSourceJson(id = "src-cast")}],"PlaySessionId":"cast-ps"}""",
                )
            }
            val profile =
                h.deviceProfileFactory.createExoPlayerProfile(
                    VideoQuality.ORIGINAL,
                    6,
                    allowHdrPassthrough = true,
                    allowTranscoding = true,
                )
            val castInfo =
                h.repository.getPlaybackInfoForCast(
                    itemId = itemId,
                    deviceProfile = profile,
                    maxStreamingBitrate = 10_000_000,
                    maxAudioChannels = 6,
                    audioStreamIndex = null,
                    subtitleStreamIndex = null,
                    mediaSourceId = "src-cast",
                )
            assertNotNull(castInfo)
            assertEquals("cast-ps", castInfo!!.playSessionId)
        }
}
