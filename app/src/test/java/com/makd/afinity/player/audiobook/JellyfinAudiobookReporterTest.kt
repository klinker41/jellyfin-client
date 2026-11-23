package com.makd.afinity.player.audiobook

import com.makd.afinity.data.autodownload.PlayedItemEventBus
import com.makd.afinity.data.models.audiobookshelf.AudioTrack
import com.makd.afinity.data.models.media.AfinityAudiobook
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.repository.playback.PlaybackRepository
import com.makd.afinity.data.store.ItemStore
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlaybackManager
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlaybackState
import com.makd.afinity.player.audiobookshelf.JellyfinAudiobookSession
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.every
import io.mockk.mockk
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

            val reporter =
                JellyfinAudiobookReporter(
                    playbackRepository = playbackRepository,
                    playbackManager = playbackManager,
                    itemStore = itemStore,
                    playedItemEventBus = playedBus,
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
}
