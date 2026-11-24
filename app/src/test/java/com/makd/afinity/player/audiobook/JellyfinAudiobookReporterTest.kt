package com.makd.afinity.player.audiobook

import com.makd.afinity.data.autodownload.PlayedItemEventBus
import com.makd.afinity.data.models.audiobookshelf.AudioTrack
import com.makd.afinity.data.models.media.AfinityAudiobook
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.repository.playback.PlaybackRepository
import com.makd.afinity.data.store.ItemStore
import com.makd.afinity.data.sync.UserDataSyncScheduler
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlaybackManager
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlaybackState
import com.makd.afinity.player.audiobookshelf.JellyfinAudiobookSession
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JellyfinAudiobookReporterTest {

    private fun testPart(id: UUID, runtimeTicks: Long = 3_600 * 10_000_000L) =
        AfinityAudiobook(
            id = id,
            name = "Part",
            originalTitle = null,
            overview = "",
            played = false,
            favorite = false,
            liked = false,
            canPlay = true,
            canDownload = true,
            runtimeTicks = runtimeTicks,
            playbackPositionTicks = 0L,
            unplayedItemCount = null,
            images = AfinityImages(),
            providerIds = null,
            externalUrls = null,
            album = "Book",
            people = emptyList(),
            publisher = null,
            premiereDate = null,
            productionYear = null,
            genres = emptyList(),
            communityRating = null,
            dateCreated = null,
            parentId = null,
            trackNumber = 1,
            discNumber = 1,
            container = "m4b",
        )

    @Test
    fun `emits played event only when audiobook part finishes or enters completion window`() =
        runTest {
            val part1Id = testUuid(1)
            val part2Id = testUuid(2)
            val playbackRepository: PlaybackRepository = mockk(relaxed = true)
            coEvery {
                playbackRepository.reportPlaybackStart(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns true
            coEvery {
                playbackRepository.reportPlaybackProgress(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns true
            coEvery {
                playbackRepository.reportPlaybackStop(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns true
            val playbackState =
                mockk<AudiobookshelfPlaybackState>(relaxed = true) {
                    every { isPlaying } returns true
                }
            val playbackManager: AudiobookshelfPlaybackManager =
                mockk(relaxed = true) {
                    every { this@mockk.playbackState } returns MutableStateFlow(playbackState)
                }
            val itemStore = ItemStore()
            itemStore.put(listOf(testPart(part1Id), testPart(part2Id)))
            val playedBus = PlayedItemEventBus()
            val syncScheduler: UserDataSyncScheduler = mockk(relaxed = true)

            val reporter =
                JellyfinAudiobookReporter(
                    playbackRepository = playbackRepository,
                    playbackManager = playbackManager,
                    itemStore = itemStore,
                    playedItemEventBus = playedBus,
                    userDataSyncScheduler = syncScheduler,
                )

            val session =
                JellyfinAudiobookSession(
                    sessionId = "s-1",
                    bookId = part1Id.toString(),
                    title = "Book",
                    author = null,
                    coverUrl = null,
                    tracks =
                        listOf(
                            AudioTrack(0, 0.0, 3600.0, "Part 1", "https://example/1", null),
                            AudioTrack(1, 3600.0, 3600.0, "Part 2", "https://example/2", null),
                        ),
                    chapters = emptyList(),
                    partIds = listOf(part1Id, part2Id),
                    partRuntimeTicks = listOf(3_600 * 10_000_000L, 3_600 * 10_000_000L),
                    localUris = listOf(null, null),
                )

            val emitted = mutableListOf<UUID>()
            val collectJob =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    playedBus.playedItems.take(2).toList(emitted)
                }

            reporter.begin(session, 0)
            reporter.onLocalPlayingChanged(true)

            // Transition before end does NOT emit played
            reporter.onPartChanged(
                oldIndex = 0,
                oldPositionMs = 600_000L,
                newIndex = 1,
                ended = false,
            )
            assertFalse(itemStore.get(part1Id)?.played == true)
            assertTrue(emitted.isEmpty())

            // Stopping near end of part 2 (within 5-min window) emits played for part 2
            reporter.end(globalSeconds = 7_100.0)
            assertTrue(itemStore.get(part2Id)?.played == true)

            // Auto-transition ending part 1 emits played for part 1
            reporter.begin(session, 0)
            reporter.onLocalPlayingChanged(true)
            reporter.onPartChanged(
                oldIndex = 0,
                oldPositionMs = 3_600_000L,
                newIndex = 1,
                ended = true,
            )
            assertTrue(itemStore.get(part1Id)?.played == true)

            advanceUntilIdle()
            collectJob.join()
            assertEquals(listOf(part2Id, part1Id), emitted)
        }

    @Test
    fun `start includes positionTicks, resume reports unpaused progress, and early stop on short part does not mark played`() =
        runTest {
            val shortPartId = testUuid(10)
            val shortRuntimeTicks = 6L * 60L * 10_000_000L // 6 minutes (< 10 min)
            val playbackRepository: PlaybackRepository = mockk(relaxed = true)
            coEvery {
                playbackRepository.reportPlaybackStart(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns true
            coEvery {
                playbackRepository.reportPlaybackProgress(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns true
            coEvery {
                playbackRepository.reportPlaybackStop(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns true
            val stateFlow =
                MutableStateFlow(
                    AudiobookshelfPlaybackState(isPlaying = true, currentTime = 60.0),
                )
            val playbackManager: AudiobookshelfPlaybackManager =
                mockk(relaxed = true) { every { playbackState } returns stateFlow }
            val itemStore = ItemStore()
            itemStore.put(testPart(shortPartId, runtimeTicks = shortRuntimeTicks))
            val playedBus = PlayedItemEventBus()
            val syncScheduler: UserDataSyncScheduler = mockk(relaxed = true)

            val reporter =
                JellyfinAudiobookReporter(
                    playbackRepository = playbackRepository,
                    playbackManager = playbackManager,
                    itemStore = itemStore,
                    playedItemEventBus = playedBus,
                    userDataSyncScheduler = syncScheduler,
                )

            val session =
                JellyfinAudiobookSession(
                    sessionId = "s-short",
                    bookId = shortPartId.toString(),
                    title = "Short Book",
                    author = null,
                    coverUrl = null,
                    tracks = listOf(AudioTrack(0, 0.0, 360.0, "Part 1", "https://example/1", null)),
                    chapters = emptyList(),
                    partIds = listOf(shortPartId),
                    partRuntimeTicks = listOf(shortRuntimeTicks),
                    localUris = listOf(null),
                )

            val emitted = mutableListOf<UUID>()
            val collectJob =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    playedBus.playedItems.toList(emitted)
                }

            reporter.begin(session, 0)
            reporter.onLocalPlayingChanged(true)
            stateFlow.value = AudiobookshelfPlaybackState(isPlaying = false, currentTime = 75.0)
            reporter.onLocalPlayingChanged(false)
            stateFlow.value = AudiobookshelfPlaybackState(isPlaying = true, currentTime = 75.0)
            reporter.onLocalPlayingChanged(true)
            // Stopping at 90s in a 6-min part has < 5 min remaining, so previous window ordering
            // falsely marked it played; PlaybackCompletion checks position < 5 min first.
            reporter.end(globalSeconds = 90.0)
            advanceUntilIdle()
            collectJob.cancel()

            assertFalse(itemStore.get(shortPartId)?.played == true)
            assertEquals(0L, itemStore.get(shortPartId)?.playbackPositionTicks)
            assertTrue(emitted.isEmpty())

            coVerify(exactly = 1) {
                playbackRepository.reportPlaybackStart(
                    itemId = shortPartId,
                    sessionId = any(),
                    mediaSourceId = shortPartId.toString(),
                    playMethod = "DirectPlay",
                    canSeek = true,
                    positionTicks = 60L * 10_000_000L,
                )
            }
            coVerify(exactly = 1) {
                playbackRepository.reportPlaybackProgress(
                    itemId = shortPartId,
                    sessionId = any(),
                    positionTicks = 75L * 10_000_000L,
                    isPaused = true,
                    playMethod = "DirectPlay",
                )
            }
            coVerify(exactly = 1) {
                playbackRepository.reportPlaybackProgress(
                    itemId = shortPartId,
                    sessionId = any(),
                    positionTicks = 75L * 10_000_000L,
                    isPaused = false,
                    playMethod = "DirectPlay",
                )
            }
        }

    @Test
    fun `patches itemStore on pause, schedules sync on failure, retries start, and rotates after long pause`() =
        runTest {
            val partId = testUuid(20)
            val runtimeTicks = 3_600L * 10_000_000L
            val playbackRepository: PlaybackRepository = mockk(relaxed = true)
            val stateFlow =
                MutableStateFlow(
                    AudiobookshelfPlaybackState(isPlaying = true, currentTime = 600.0),
                )
            val playbackManager: AudiobookshelfPlaybackManager =
                mockk(relaxed = true) { every { playbackState } returns stateFlow }
            val itemStore = ItemStore()
            itemStore.put(testPart(partId, runtimeTicks = runtimeTicks))
            val playedBus = PlayedItemEventBus()
            val syncScheduler: UserDataSyncScheduler = mockk(relaxed = true)
            var now = 1_000L

            val reporter =
                JellyfinAudiobookReporter(
                    playbackRepository = playbackRepository,
                    playbackManager = playbackManager,
                    itemStore = itemStore,
                    playedItemEventBus = playedBus,
                    userDataSyncScheduler = syncScheduler,
                    nowMs = { now },
                )

            val session =
                JellyfinAudiobookSession(
                    sessionId = "s-retry",
                    bookId = partId.toString(),
                    title = "Long Book",
                    author = null,
                    coverUrl = null,
                    tracks =
                        listOf(AudioTrack(0, 0.0, 3600.0, "Part 1", "https://example/1", null)),
                    chapters = emptyList(),
                    partIds = listOf(partId),
                    partRuntimeTicks = listOf(runtimeTicks),
                    localUris = listOf(null),
                )

            // 1. Offline start & pause: reportPlaybackStart and reportPlaybackProgress return false
            coEvery {
                playbackRepository.reportPlaybackStart(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns false
            coEvery {
                playbackRepository.reportPlaybackProgress(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns false

            reporter.begin(session, 0)
            reporter.onLocalPlayingChanged(true)

            stateFlow.value = AudiobookshelfPlaybackState(isPlaying = false, currentTime = 650.0)
            reporter.onLocalPlayingChanged(false)

            assertEquals(650L * 10_000_000L, itemStore.get(partId)?.playbackPositionTicks)
            verify(exactly = 1) { syncScheduler.triggerSync() }

            // 2. Network returns and user resumes after short pause -> retries reportPlaybackStart
            coEvery {
                playbackRepository.reportPlaybackStart(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns true
            coEvery {
                playbackRepository.reportPlaybackProgress(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns true

            now += 60_000L
            stateFlow.value = AudiobookshelfPlaybackState(isPlaying = true, currentTime = 650.0)
            reporter.onLocalPlayingChanged(true)

            coVerify(exactly = 2) {
                playbackRepository.reportPlaybackStart(
                    itemId = partId,
                    sessionId = any(),
                    mediaSourceId = partId.toString(),
                    playMethod = "DirectPlay",
                    canSeek = true,
                    positionTicks = any(),
                )
            }

            // 3. Long pause >= 15 min rotates sessionId and sends fresh reportPlaybackStart
            stateFlow.value = AudiobookshelfPlaybackState(isPlaying = false, currentTime = 700.0)
            reporter.onLocalPlayingChanged(false)

            now += 15L * 60L * 1000L
            stateFlow.value = AudiobookshelfPlaybackState(isPlaying = true, currentTime = 700.0)
            reporter.onLocalPlayingChanged(true)

            val sessionIds = mutableListOf<String>()
            coVerify(exactly = 3) {
                playbackRepository.reportPlaybackStart(
                    itemId = partId,
                    sessionId = capture(sessionIds),
                    mediaSourceId = partId.toString(),
                    playMethod = "DirectPlay",
                    canSeek = true,
                    positionTicks = any(),
                )
            }
            assertEquals(sessionIds[0], sessionIds[1])
            assertTrue(sessionIds[2] != sessionIds[1])

            // 4. Offline stop triggers immediate UserDataSyncScheduler.triggerSync()
            coEvery {
                playbackRepository.reportPlaybackStop(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns false
            reporter.end(globalSeconds = 720.0)
            verify(exactly = 2) { syncScheduler.triggerSync() }
        }
}
