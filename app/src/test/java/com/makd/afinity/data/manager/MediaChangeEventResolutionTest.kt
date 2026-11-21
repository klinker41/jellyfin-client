package com.makd.afinity.data.manager

import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.UserDataPatch
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.store.ItemStore
import com.makd.afinity.testutil.TestMediaFixtures.mockUserItemDataDto
import com.makd.afinity.testutil.TestMediaFixtures.testEpisode
import com.makd.afinity.testutil.TestMediaFixtures.testMovie
import com.makd.afinity.testutil.TestMediaFixtures.testSeason
import com.makd.afinity.testutil.TestMediaFixtures.testShow
import com.makd.afinity.testutil.TestMediaFixtures.testSource
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaChangeEventResolutionTest {

    @Test
    fun `putFrom routes user data events to putUserDataFrom and full events to put`() {
        val store = ItemStore()
        val movieId = testUuid(1)
        val cachedWithSources =
            testMovie(id = movieId, played = false, sources = listOf(testSource(id = "src-keep")))
        store.put(cachedWithSources)

        val sparseUserUpdate = testMovie(id = movieId, played = true, sources = emptyList())
        val userDataEvent =
            MediaChangeEvent(
                itemId = movieId,
                source = MediaChangeSource.PLAYBACK,
                patch = UserDataPatch(played = true),
            )
        store.putFrom(userDataEvent, listOf(sparseUserUpdate))
        val afterUserData = store.getAs<AfinityMovie>(movieId)!!
        assertTrue(afterUserData.played)
        assertEquals(listOf("src-keep"), afterUserData.sources.map { it.id })

        val fullReplacement =
            testMovie(id = movieId, played = false, sources = listOf(testSource(id = "src-new")))
        val contentEvent = MediaChangeEvent(itemId = movieId, source = MediaChangeSource.WEBSOCKET)
        store.putFrom(contentEvent, listOf(fullReplacement))
        val afterPut = store.getAs<AfinityMovie>(movieId)!!
        assertFalse(afterPut.played)
        assertEquals(listOf("src-new"), afterPut.sources.map { it.id })
    }

    @Test
    fun `resolveChangedItems prefers held patch, provided items, and filters by displayedIds`() =
        runTest {
            val episodeId = testUuid(10)
            val seasonId = testUuid(11)
            val seriesId = testUuid(12)
            val mediaRepository: MediaRepository = mockk()
            val heldEpisode = testEpisode(id = episodeId, seriesId = seriesId, seasonId = seasonId)
            val providedShow = testShow(id = seriesId)
            val fetchedSeason = testSeason(id = seasonId, seriesId = seriesId)
            coEvery { mediaRepository.getItemById(seasonId) } returns fetchedSeason

            val event =
                MediaChangeEvent(
                    itemId = episodeId,
                    parentItem = providedShow,
                    seriesId = seriesId,
                    seasonId = seasonId,
                    source = MediaChangeSource.MANUAL,
                    patch = UserDataPatch(played = true, playbackPositionTicks = 500L),
                )

            val visibleOnly =
                event.resolveChangedItems(
                    mediaRepository = mediaRepository,
                    displayedIds = setOf(episodeId, seasonId),
                    heldItem = { id -> if (id == episodeId) heldEpisode else null },
                )

            assertEquals(2, visibleOnly.size)
            val patchedEp = visibleOnly[0] as AfinityEpisode
            assertTrue(patchedEp.played)
            assertEquals(500L, patchedEp.playbackPositionTicks)
            assertEquals(fetchedSeason, visibleOnly[1])
            coVerify(exactly = 1) { mediaRepository.getItemById(seasonId) }

            // When displayedIds is null and season is unprovided, seasonId fetch is skipped.
            val withoutDisplayedFilter =
                event.resolveChangedItems(
                    mediaRepository = mediaRepository,
                    displayedIds = null,
                    heldItem = { id -> if (id == episodeId) heldEpisode else null },
                )
            assertEquals(listOf(episodeId, seriesId), withoutDisplayedFilter.map { it.id })
        }

    @Test
    fun `resolveChangedItems skips parent and season candidates for content changes`() = runTest {
        val episodeId = testUuid(20)
        val seasonId = testUuid(21)
        val seriesId = testUuid(22)
        val mediaRepository: MediaRepository = mockk()
        val fetchedEpisode = testEpisode(id = episodeId, seriesId = seriesId, seasonId = seasonId)
        coEvery { mediaRepository.getItemById(episodeId) } returns fetchedEpisode

        val contentEvent =
            MediaChangeEvent(
                itemId = episodeId,
                seriesId = seriesId,
                seasonId = seasonId,
                source = MediaChangeSource.WEBSOCKET,
                isContentChange = true,
            )

        val resolved = contentEvent.resolveChangedItems(mediaRepository)
        assertEquals(listOf(fetchedEpisode), resolved)
        coVerify(exactly = 1) { mediaRepository.getItemById(episodeId) }
        coVerify(exactly = 0) { mediaRepository.getItemById(seriesId) }
        coVerify(exactly = 0) { mediaRepository.getItemById(seasonId) }
    }

    @Test
    fun `resolveChangedItems and resolveTargetItem swallow errors and rethrow CancellationException`() =
        runTest {
            val itemId = testUuid(30)
            val mediaRepository: MediaRepository = mockk()
            coEvery { mediaRepository.getItemById(itemId) } throws RuntimeException("network")

            val event = MediaChangeEvent(itemId = itemId, source = MediaChangeSource.MANUAL)
            assertTrue(event.resolveChangedItems(mediaRepository).isEmpty())
            assertNull(event.resolveTargetItem(mediaRepository))

            // Held userData takes precedence in resolveTargetItem
            val heldMovie = testMovie(id = itemId, favorite = false)
            val userData = mockUserItemDataDto(played = true, isFavorite = true)
            val patchedTarget =
                event.copy(userData = userData).resolveTargetItem(mediaRepository) { heldMovie }
                    as AfinityMovie
            assertTrue(patchedTarget.played)
            assertTrue(patchedTarget.favorite)

            coEvery { mediaRepository.getItemById(itemId) } throws CancellationException("stop")
            var threwForChanged = false
            try {
                event.resolveChangedItems(mediaRepository)
            } catch (_: CancellationException) {
                threwForChanged = true
            }
            assertTrue(threwForChanged)

            var threwForTarget = false
            try {
                event.resolveTargetItem(mediaRepository)
            } catch (_: CancellationException) {
                threwForTarget = true
            }
            assertTrue(threwForTarget)
        }
}
