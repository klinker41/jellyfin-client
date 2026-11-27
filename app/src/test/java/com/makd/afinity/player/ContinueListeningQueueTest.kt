package com.makd.afinity.player

import com.makd.afinity.data.models.media.AfinityAudiobook
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.podcasts.ContinueListeningEntry
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinueListeningQueueTest {

    private fun testUuid(n: Int): UUID = UUID(0L, n.toLong())

    private fun testTrack(id: UUID): AfinityTrack =
        AfinityTrack(
            id = id,
            name = "Episode $id",
            albumId = null,
            album = "Podcast",
            artistId = null,
            artist = "Host",
            artists = emptyList(),
            indexNumber = null,
            discNumber = null,
            productionYear = null,
            runtimeTicks = 600_000_000L,
            playbackPositionTicks = 100_000_000L,
            played = false,
            favorite = false,
            playCount = null,
            normalizationGain = null,
            albumNormalizationGain = null,
            images = AfinityImages(),
        )

    private fun testAudiobook(
        id: UUID,
        partIds: List<UUID> = emptyList(),
        lastPlayed: LocalDateTime? = null,
    ): AfinityAudiobook {
        val parts =
            if (partIds.size > 1) {
                partIds.map { partId ->
                    mockk<AfinityAudiobook>(relaxed = true) {
                        every { this@mockk.id } returns partId
                    }
                }
            } else {
                emptyList()
            }
        return AfinityAudiobook(
            id = id,
            name = "Book $id",
            originalTitle = null,
            overview = "",
            played = false,
            favorite = false,
            liked = false,
            canPlay = true,
            canDownload = true,
            runtimeTicks = 1_000_000_000L,
            playbackPositionTicks = 200_000_000L,
            unplayedItemCount = null,
            images = AfinityImages(),
            providerIds = null,
            externalUrls = null,
            album = null,
            people = emptyList(),
            publisher = null,
            premiereDate = null,
            productionYear = null,
            genres = emptyList(),
            communityRating = null,
            dateCreated = null,
            parentId = null,
            trackNumber = null,
            discNumber = null,
            container = null,
            lastPlayedDate = lastPlayed,
            parts = parts,
        )
    }

    @Test
    fun `startFrom chains mixed audiobook and podcast items from clicked index`() {
        val queue = ContinueListeningQueue()
        val book1 = testAudiobook(testUuid(1))
        val track1 = testTrack(testUuid(2))
        val book2 = testAudiobook(testUuid(3), partIds = listOf(testUuid(30), testUuid(31)))
        val entries =
            listOf(
                ContinueListeningEntry.Audiobook(book1),
                ContinueListeningEntry.PodcastEpisode(track1),
                ContinueListeningEntry.Audiobook(book2),
            )

        queue.startFrom(entries, clickedIndex = 0)

        val firstAdvance = queue.consumeNextForAudiobook(testUuid(1).toString())
        assertNotNull(firstAdvance)
        assertEquals(ContinueListeningQueueItem.PodcastEpisode(track1), firstAdvance!!.item)
        assertTrue(queue.isAdvancing.value)

        val secondAdvance = queue.consumeNextForPodcast(track1.id)
        assertNotNull(secondAdvance)
        assertEquals(ContinueListeningQueueItem.Audiobook(testUuid(30)), secondAdvance!!.item)

        assertNull(queue.consumeNextForAudiobook(testUuid(30).toString()))
    }

    @Test
    fun `startFrom on last item or invalid index leaves no pending items`() {
        val queue = ContinueListeningQueue()
        val entries =
            listOf(
                ContinueListeningEntry.Audiobook(testAudiobook(testUuid(1))),
                ContinueListeningEntry.PodcastEpisode(testTrack(testUuid(2))),
            )

        queue.startFrom(entries, clickedIndex = 1)
        assertNull(queue.consumeNextForPodcast(testUuid(2)))

        queue.startFrom(entries, clickedIndex = -1)
        assertNull(queue.consumeNextForAudiobook(testUuid(1).toString()))

        queue.startFrom(entries, clickedIndex = 5)
        assertNull(queue.consumeNextForAudiobook(testUuid(1).toString()))
    }

    @Test
    fun `startFromAudiobooks uses first playablePart id`() {
        val queue = ContinueListeningQueue()
        val multiPartBook =
            testAudiobook(id = testUuid(10), partIds = listOf(testUuid(11), testUuid(12)))
        val singlePartBook = testAudiobook(id = testUuid(20))

        queue.startFromAudiobooks(listOf(multiPartBook, singlePartBook), clickedIndex = 0)

        assertNull(queue.consumeNextForAudiobook(testUuid(10).toString()))
        val advance = queue.consumeNextForAudiobook(testUuid(11).toString())
        assertNotNull(advance)
        assertEquals(ContinueListeningQueueItem.Audiobook(testUuid(20)), advance!!.item)
    }

    @Test
    fun `consumeNext returns null when ended item does not match active item`() {
        val queue = ContinueListeningQueue()
        val entries =
            listOf(
                ContinueListeningEntry.Audiobook(testAudiobook(testUuid(1))),
                ContinueListeningEntry.PodcastEpisode(testTrack(testUuid(2))),
            )
        queue.startFrom(entries, clickedIndex = 0)

        assertNull(queue.consumeNextForAudiobook(testUuid(99).toString()))
        assertNull(queue.consumeNextForPodcast(testUuid(1)))
        assertFalse(queue.isAdvancing.value)

        assertNotNull(queue.consumeNextForAudiobook(testUuid(1).toString()))
    }

    @Test
    fun `consumeNextAfterFailure skips failed item and clears isAdvancing when exhausted`() {
        val queue = ContinueListeningQueue()
        val track2 = testTrack(testUuid(2))
        val track3 = testTrack(testUuid(3))
        val entries =
            listOf(
                ContinueListeningEntry.Audiobook(testAudiobook(testUuid(1))),
                ContinueListeningEntry.PodcastEpisode(track2),
                ContinueListeningEntry.PodcastEpisode(track3),
            )
        queue.startFrom(entries, clickedIndex = 0)

        val first = queue.consumeNextForAudiobook(testUuid(1).toString())!!
        assertTrue(queue.isAdvancing.value)

        val fallback = queue.consumeNextAfterFailure(first.generation)
        assertNotNull(fallback)
        assertEquals(ContinueListeningQueueItem.PodcastEpisode(track3), fallback!!.item)
        assertTrue(queue.isAdvancing.value)

        assertNull(queue.consumeNextAfterFailure(first.generation))
        assertFalse(queue.isAdvancing.value)
    }

    @Test
    fun `notifyAdvanced emits on advancedTo, resets isAdvancing, and ignores stale generations`() =
        runTest {
            val queue = ContinueListeningQueue()
            val track2 = testTrack(testUuid(2))
            val entries =
                listOf(
                    ContinueListeningEntry.Audiobook(testAudiobook(testUuid(1))),
                    ContinueListeningEntry.PodcastEpisode(track2),
                )
            queue.startFrom(entries, clickedIndex = 0)
            val pending = queue.consumeNextForAudiobook(testUuid(1).toString())!!

            val collected = async(start = CoroutineStart.UNDISPATCHED) { queue.advancedTo.first() }
            queue.notifyAdvanced(pending.generation, pending.item)

            assertEquals(pending, collected.await())
            assertTrue(queue.isAdvancing.value)

            queue.finishAdvancing(pending.generation)
            assertFalse(queue.isAdvancing.value)

            queue.startFrom(entries, clickedIndex = 0)
            val second = queue.consumeNextForAudiobook(testUuid(1).toString())!!
            assertFalse(queue.isStillActive(pending.generation))
            assertTrue(queue.isStillActive(second.generation))
            queue.notifyAdvanced(pending.generation, pending.item)
            assertTrue(queue.isAdvancing.value)

            // With no active collector, notifyAdvanced clears isAdvancing immediately.
            queue.notifyAdvanced(second.generation, second.item)
            assertFalse(queue.isAdvancing.value)
        }

    @Test
    fun `clearIfDifferent preserves matching item and clears mismatched items`() {
        val queue = ContinueListeningQueue()
        val track1 = testTrack(testUuid(1))
        val track2 = testTrack(testUuid(2))
        val entries =
            listOf(
                ContinueListeningEntry.PodcastEpisode(track1),
                ContinueListeningEntry.Audiobook(testAudiobook(testUuid(3))),
            )
        queue.startFrom(entries, clickedIndex = 0)

        queue.clearIfDifferentPodcast(listOf(track1))
        assertNotNull(queue.consumeNextForPodcast(track1.id))

        queue.startFrom(entries, clickedIndex = 0)
        queue.clearIfDifferentPodcast(listOf(track1, track2))
        assertNull(queue.consumeNextForPodcast(track1.id))

        queue.startFrom(entries, clickedIndex = 0)
        queue.clearIfDifferentAudiobook(testUuid(3).toString())
        assertNull(queue.consumeNextForPodcast(track1.id))

        val entries2 =
            listOf(
                ContinueListeningEntry.Audiobook(testAudiobook(testUuid(3))),
                ContinueListeningEntry.PodcastEpisode(track1),
            )
        queue.startFrom(entries2, clickedIndex = 0)
        queue.clearIfDifferentAudiobook(testUuid(3).toString())
        assertNotNull(queue.consumeNextForAudiobook(testUuid(3).toString()))
    }
}
