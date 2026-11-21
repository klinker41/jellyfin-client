package com.makd.afinity.player.music

import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.models.music.RadioMode
import com.makd.afinity.data.models.music.RadioSeed
import com.makd.afinity.data.repository.music.MusicRepository
import com.makd.afinity.testutil.TestMediaFixtures.testAlbum
import com.makd.afinity.testutil.TestMediaFixtures.testTrack
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RadioManagerTest {

    private class Harness {
        val queueFlow = MutableStateFlow<List<AfinityTrack>>(emptyList())
        val indexFlow = MutableStateFlow(0)
        val musicRepository: MusicRepository = mockk(relaxed = true)
        val queueManager: MusicQueueManager =
            mockk(relaxed = true) {
                every { queue } returns queueFlow
                every { currentIndex } returns indexFlow
            }
        val manager = RadioManager(musicRepository, queueManager)
    }

    private suspend fun awaitCondition(predicate: () -> Boolean) {
        withContext(Dispatchers.Default) {
            withTimeout(2_000L) {
                while (!predicate()) {
                    kotlinx.coroutines.delay(10L)
                }
            }
        }
    }

    @Test
    fun `startRadio SIMILAR excludes existing queue tracks and loads queue`() = runTest {
        val h = Harness()
        val seedId = testUuid(1)
        val inQueue = testTrack(id = testUuid(10), name = "Already Queued")
        val fresh1 = testTrack(id = testUuid(11), name = "Fresh 1")
        val fresh2 = testTrack(id = testUuid(12), name = "Fresh 2")
        h.queueFlow.value = listOf(inQueue)

        coEvery { h.musicRepository.getInstantMix(seedId, any()) } returns
            listOf(inQueue, fresh1, fresh2)

        h.manager.startRadio(
            RadioSeed(trackId = seedId, albumId = testUuid(600), sourceTracks = emptyList()),
            RadioMode.SIMILAR,
        )

        awaitCondition { h.manager.radioState.value.let { it.isActive && !it.isGenerating } }
        verify { h.queueManager.loadQueue(listOf(fresh1, fresh2), startIndex = 0) }
    }

    @Test
    fun `startRadio CONTINUOUS chains seeds and ALBUM_MIX skips already-queued albums`() = runTest {
        val h = Harness()
        val seedId = testUuid(2)
        val steps = (1..5).map { testTrack(id = testUuid(20 + it)) }
        coEvery { h.musicRepository.getInstantMix(seedId, 15) } returns listOf(steps[0])
        for (i in 0 until 4) {
            coEvery { h.musicRepository.getInstantMix(steps[i].id, 15) } returns
                listOf(steps[i + 1])
        }

        h.manager.startRadio(
            RadioSeed(trackId = seedId, albumId = null, sourceTracks = emptyList()),
            RadioMode.CONTINUOUS,
        )
        awaitCondition { h.manager.radioState.value.let { it.isActive && !it.isGenerating } }
        verify { h.queueManager.loadQueue(steps, startIndex = 0) }
        assertEquals(steps.last().id, h.manager.radioState.value.continuousSeedId)

        // ALBUM_MIX picks similar album whose ID is not in the current queue.
        val queuedAlbumId = testUuid(600)
        val pickedAlbumId = testUuid(601)
        val albumTrack = testTrack(id = testUuid(23))
        h.queueFlow.value = listOf(testTrack(id = testUuid(24))) // albumId = testUuid(600)
        coEvery { h.musicRepository.getSimilarAlbums(queuedAlbumId, 15) } returns
            listOf(testAlbum(id = queuedAlbumId), testAlbum(id = pickedAlbumId))
        coEvery { h.musicRepository.getAlbumTracks(pickedAlbumId) } returns listOf(albumTrack)

        h.manager.startRadio(
            RadioSeed(trackId = seedId, albumId = queuedAlbumId, sourceTracks = emptyList()),
            RadioMode.ALBUM_MIX,
        )
        awaitCondition {
            h.manager.radioState.value.let {
                it.mode == RadioMode.ALBUM_MIX && it.isActive && !it.isGenerating
            }
        }
        verify { h.queueManager.loadQueue(listOf(albumTrack), startIndex = 0) }
    }

    @Test
    fun `RESHUFFLE, RANDOM, onTrackChanged auto-replenish, and stopRadio behave properly`() =
        runTest {
            val h = Harness()
            val sources = (1..7).map { testTrack(id = testUuid(30 + it)) }

            val loadedQueues = mutableListOf<List<AfinityTrack>>()
            h.manager.startRadio(
                RadioSeed(trackId = sources.first().id, albumId = null, sourceTracks = sources),
                RadioMode.RANDOM,
            )
            awaitCondition { h.manager.radioState.value.let { it.isActive && !it.isGenerating } }
            verify(exactly = 1) { h.queueManager.loadQueue(capture(loadedQueues), startIndex = 0) }
            assertEquals(sources.toSet(), loadedQueues.last().toSet())

            // RESHUFFLE yields permutation of all sourceTracks
            h.manager.startRadio(
                RadioSeed(trackId = sources.first().id, albumId = null, sourceTracks = sources),
                RadioMode.RESHUFFLE,
            )
            awaitCondition {
                h.manager.radioState.value.let {
                    it.mode == RadioMode.RESHUFFLE && !it.isGenerating
                }
            }
            verify(exactly = 2) { h.queueManager.loadQueue(capture(loadedQueues), startIndex = 0) }
            assertEquals(sources.toSet(), loadedQueues.last().toSet())

            // Switch to SIMILAR and simulate track approaching end (< 5 upcoming tracks)
            val seedId = testUuid(50)
            val appendedSignal = CompletableDeferred<Unit>()
            coEvery { h.musicRepository.getInstantMix(seedId, any()) } returns
                (1..6).map { testTrack(id = testUuid(60 + it)) }
            h.manager.startRadio(RadioSeed(seedId, null, emptyList()), RadioMode.SIMILAR)
            awaitCondition {
                h.manager.radioState.value.let { it.mode == RadioMode.SIMILAR && !it.isGenerating }
            }

            h.queueFlow.value = (1..6).map { testTrack(id = testUuid(70 + it)) }
            h.indexFlow.value = 0 // 5 upcoming -> should NOT append
            h.manager.onTrackChanged(h.queueFlow.value[0])
            coVerify(exactly = 1) { h.musicRepository.getInstantMix(seedId, any()) }

            every { h.queueManager.addLast(any()) } answers { appendedSignal.complete(Unit) }
            h.indexFlow.value = 3 // 2 upcoming (< 5) -> SHOULD append
            h.manager.onTrackChanged(h.queueFlow.value[3])
            withContext(Dispatchers.Default) { withTimeout(2_000L) { appendedSignal.await() } }

            h.manager.stopRadio()
            assertFalse(h.manager.radioState.value.isActive)
            assertNull(h.manager.radioState.value.seedTrackId)
        }
}
