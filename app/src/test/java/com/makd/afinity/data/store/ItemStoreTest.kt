package com.makd.afinity.data.store

import app.cash.turbine.test
import com.makd.afinity.data.models.media.AfinityExternalUrl
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.AfinitySourceType
import com.makd.afinity.data.models.media.UserDataPatch
import com.makd.afinity.data.models.music.AfinityAlbum
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.testutil.TestMediaFixtures.mockUserItemDataDto
import com.makd.afinity.testutil.TestMediaFixtures.testAlbum
import com.makd.afinity.testutil.TestMediaFixtures.testMovie
import com.makd.afinity.testutil.TestMediaFixtures.testPerson
import com.makd.afinity.testutil.TestMediaFixtures.testShow
import com.makd.afinity.testutil.TestMediaFixtures.testSource
import com.makd.afinity.testutil.TestMediaFixtures.testTrack
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemStoreTest {

    @Test
    fun putGetAndGetAsRespectTypeSafety() {
        val store = ItemStore()
        val id = testUuid(1)
        val movie = testMovie(id = id, name = "Arrival")

        store.put(movie)

        assertSame(movie, store.get(id))
        assertSame(movie, store.getAs<AfinityMovie>(id))
        assertNull(store.getAs<AfinityShow>(id))
    }

    @Test
    fun richnessScoringMatchesExpectedWeights() {
        val empty = testMovie(id = testUuid(1))
        val withOverviewAndPeople = empty.copy(overview = "Summary", people = listOf(testPerson()))
        val withLocalSource =
            empty.copy(sources = listOf(testSource(type = AfinitySourceType.LOCAL)))
        val withRemoteAndLinks =
            empty.copy(
                sources = listOf(testSource(type = AfinitySourceType.REMOTE)),
                externalUrls = listOf(AfinityExternalUrl("IMDb", "https://imdb.com")),
                overview = "Detailed",
                people = listOf(testPerson()),
            )

        assertEquals(0, empty.richness())
        assertEquals(2, withOverviewAndPeople.richness())
        assertEquals(0, withLocalSource.richness())
        assertEquals(8, withRemoteAndLinks.richness())
    }

    @Test
    fun putDoesNotDowngradeRicherItemButTakesIncomingUserData() {
        val store = ItemStore()
        val id = testUuid(1)
        val rich =
            testMovie(
                id = id,
                name = "Rich Title",
                overview = "Full overview",
                sources = listOf(testSource(type = AfinitySourceType.REMOTE)),
                people = listOf(testPerson()),
                played = false,
                favorite = false,
            )
        val leanIncoming =
            testMovie(
                id = id,
                name = "Lean Title",
                overview = "",
                played = true,
                favorite = true,
                playbackPositionTicks = 500L,
            )

        store.put(rich)
        store.put(listOf(leanIncoming))

        val stored = store.getAs<AfinityMovie>(id)!!
        assertEquals("Rich Title", stored.name)
        assertEquals("Full overview", stored.overview)
        assertEquals(1, stored.sources.size)
        assertTrue(stored.played)
        assertTrue(stored.favorite)
        assertEquals(500L, stored.playbackPositionTicks)
    }

    @Test
    fun putLoadedPrefersExistingUserDataWhenUpgradingRichness() {
        val store = ItemStore()
        val id = testUuid(1)
        val leanPlayed = testMovie(id = id, overview = "", played = true, favorite = true)
        val richUnplayed =
            testMovie(
                id = id,
                overview = "Full overview",
                sources = listOf(testSource(type = AfinitySourceType.REMOTE)),
                played = false,
                favorite = false,
            )

        store.put(leanPlayed)
        store.putLoaded(listOf(richUnplayed))

        val stored = store.getAs<AfinityMovie>(id)!!
        assertEquals("Full overview", stored.overview)
        assertEquals(1, stored.sources.size)
        assertTrue(stored.played)
        assertTrue(stored.favorite)
    }

    @Test
    fun putIfAbsentKeepsExistingEntryIntact() {
        val store = ItemStore()
        val id = testUuid(1)
        val first = testMovie(id = id, name = "First", favorite = true)
        val second = testMovie(id = id, name = "Second", favorite = false)

        store.put(first)
        store.putIfAbsent(listOf(second))

        val stored = store.getAs<AfinityMovie>(id)!!
        assertEquals("First", stored.name)
        assertTrue(stored.favorite)
    }

    @Test
    fun applyPatchAndApplyUserDataUpdateExistingEntriesOnly() {
        val store = ItemStore()
        val id = testUuid(1)
        val missingId = testUuid(2)
        store.put(testMovie(id = id, played = false, favorite = false))

        store.applyPatch(id, UserDataPatch(played = true, playbackPositionTicks = 42_000L))
        store.applyPatch(missingId, UserDataPatch(played = true))

        assertNull(store.get(missingId))
        val afterPatch = store.getAs<AfinityMovie>(id)!!
        assertTrue(afterPatch.played)
        assertFalse(afterPatch.favorite)
        assertEquals(42_000L, afterPatch.playbackPositionTicks)

        val dto =
            mockUserItemDataDto(
                played = false,
                isFavorite = true,
                likes = true,
                playbackPositionTicks = 99L,
                unplayedItemCount = 2,
            )
        store.applyUserData(id, dto)

        val afterDto = store.getAs<AfinityMovie>(id)!!
        assertFalse(afterDto.played)
        assertTrue(afterDto.favorite)
        assertTrue(afterDto.liked)
        assertEquals(99L, afterDto.playbackPositionTicks)
        assertEquals(2, afterDto.unplayedItemCount)
    }

    @Test
    fun putContentAndMergeReplaceContentUnlessSourcesWouldBeLost() {
        val store = ItemStore()
        val richId = testUuid(1)
        val keepSourcesId = testUuid(2)

        val updatedRich =
            testMovie(
                id = richId,
                name = "Edited Movie",
                overview = "New overview",
                sources = listOf(testSource(id = "new-src")),
            )
        val editedWithoutSources = testMovie(id = keepSourcesId, name = "Edited No Sources")
        store.putContent(listOf(updatedRich, editedWithoutSources))

        val base1 = testMovie(id = richId, name = "Old Movie", overview = "Old")
        val base2 =
            testMovie(
                id = keepSourcesId,
                name = "Original With Sources",
                sources = listOf(testSource(id = "keep-me")),
                played = false,
            )
        store.applyPatch(keepSourcesId, UserDataPatch(played = true))

        val merged = store.merge(listOf(base1, base2))
        assertEquals("Edited Movie", merged[0].name)
        assertEquals("Original With Sources", merged[1].name)
        assertEquals("keep-me", merged[1].sources.single().id)
        assertTrue(merged[1].played)
    }

    @Test
    fun markStaleAndMarkAllStaleControlContentAndUserDataMerging() {
        val store = ItemStore()
        val id = testUuid(1)
        val cached = testMovie(id = id, name = "Cached", played = true)
        val freshFromServer = testMovie(id = id, name = "Fresh", played = false)
        store.put(cached)

        store.markStale(listOf(id))
        assertTrue(store.isContentStale(id))
        // Stale user data skips overlay until refreshed.
        assertSame(freshFromServer, store.merge(listOf(freshFromServer)).single())
        assertSame(freshFromServer, store.pagingMerge(freshFromServer, cached))

        store.putLoaded(listOf(freshFromServer))
        assertFalse(store.isContentStale(id))
        assertEquals("Fresh", store.getAs<AfinityMovie>(id)!!.name)

        store.markAllStale()
        assertTrue(store.isContentStale(id))
    }

    @Test
    fun mergeOwnersPatchesTracksAndAlbums() {
        val store = ItemStore()
        val trackId = testUuid(10)
        val albumId = testUuid(11)
        val storedTrack =
            testTrack(id = trackId, played = true, favorite = true, playbackPositionTicks = 777L)
        val storedAlbum = testAlbum(id = albumId, played = false, favorite = true, liked = true)
        store.put(listOf(storedTrack, storedAlbum))

        val baseTrack = testTrack(id = trackId, played = false, favorite = false)
        val baseAlbum = testAlbum(id = albumId, played = false, favorite = false, liked = false)
        val merged = store.mergeOwners(listOf(baseTrack, baseAlbum))

        val mergedTrack = merged[0] as AfinityTrack
        val mergedAlbum = merged[1] as AfinityAlbum
        assertTrue(mergedTrack.played)
        assertTrue(mergedTrack.favorite)
        assertEquals(777L, mergedTrack.playbackPositionTicks)
        assertTrue(mergedAlbum.favorite)
        assertTrue(mergedAlbum.liked)
    }

    @Test
    fun observeAndRemoveAndClearEmitExpectedSnapshots() = runTest {
        val store = ItemStore()
        val id1 = testUuid(1)
        val id2 = testUuid(2)
        val movie1 = testMovie(id = id1, played = false)
        val movie2 = testMovie(id = id2, favorite = true)

        store.observe(id1).test {
            assertNull(awaitItem())
            store.put(movie1)
            assertEquals(false, (awaitItem() as AfinityMovie).played)

            store.applyPatch(id1, UserDataPatch(played = true))
            assertEquals(true, (awaitItem() as AfinityMovie).played)

            store.remove(listOf(id1))
            assertNull(awaitItem())
        }

        store.observe(listOf(id1, id2)).test {
            assertEquals(emptyList<Any>(), awaitItem())
            store.put(listOf(movie1, movie2))
            assertEquals(2, awaitItem().size)
            store.clear()
            assertEquals(emptyList<Any>(), awaitItem())
        }
    }

    @Test
    fun putUserDataFromUpdatesExistingUserDataWithoutReplacingContent() {
        val store = ItemStore()
        val id = testUuid(1)
        val rich = testMovie(id = id, name = "Rich", overview = "Keep me", played = false)
        val incoming =
            testMovie(id = id, name = "Lean", overview = "", played = true, favorite = true)

        store.put(rich)
        store.putUserDataFrom(listOf(incoming))

        val result = store.getAs<AfinityMovie>(id)!!
        assertEquals("Rich", result.name)
        assertEquals("Keep me", result.overview)
        assertTrue(result.played)
        assertTrue(result.favorite)
    }

    @Test
    fun putDifferentItemClassOnlyOverwritesWhenStale() {
        val store = ItemStore()
        val id = testUuid(1)
        val movie = testMovie(id = id, name = "Movie")
        val show = testShow(id = id, name = "Show")

        store.put(movie)
        store.putLoaded(listOf(show))
        assertTrue(store.get(id) is AfinityMovie)

        store.markStale(listOf(id))
        store.putLoaded(listOf(show))
        assertNotNull(store.getAs<AfinityShow>(id))
    }

    @Test
    fun evictionTrimsUnobservedEntriesAboveMaxSizeWhileRetainingProjectedIds() = runTest {
        val store = ItemStore()
        val retainedId = testUuid(99_999)
        val retainedMovie = testMovie(id = retainedId, name = "Pinned On Screen")
        store.project(backgroundScope, MutableStateFlow(retainedMovie)) { live(it) }
        assertEquals(listOf(retainedId), store.onScreen(listOf(retainedId, testUuid(1))))

        val bulk = (1..2_005).map { idx -> testMovie(id = testUuid(idx), name = "Bulk $idx") }
        store.put(bulk)

        assertNotNull("Retained entry must survive eviction", store.get(retainedId))
        // Eviction trims unobserved entries down to TRIM_TARGET (1500) total store size (plus
        // retained = 1500 total).
        assertEquals(1_500, store.overlay.value.size)
        // Oldest bulk entry was evicted while newest remained.
        assertNull(store.get(testUuid(1)))
        assertNotNull(store.get(testUuid(2_005)))
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun projectSkipsReexecutionOnDisjointMutationsAndSelfPutLoaded() =
        runTest(UnconfinedTestDispatcher()) {
            val store = ItemStore()
            val watchedId = testUuid(10)
            val staleMovie = testMovie(id = watchedId, name = "Initial", played = false)
            store.put(staleMovie)

            val base = MutableStateFlow(staleMovie)
            var transformCount = 0
            val projected =
                store.project(backgroundScope, base) { state ->
                    transformCount++
                    live(state)
                }

            assertEquals(1, transformCount)
            assertFalse(projected.value.played)

            // Disjoint store insertions and patches must not re-run this projection.
            repeat(10) { offset ->
                val otherId = testUuid(1000 + offset)
                store.put(testMovie(id = otherId, name = "Other $offset"))
                store.applyPatch(otherId, UserDataPatch(played = true))
            }
            assertEquals(1, transformCount)

            // Stale refresh triggering fresh() self-writes to store without a second transform
            // pass.
            store.markStale(listOf(watchedId))
            base.value =
                testMovie(
                    id = watchedId,
                    name = "Refreshed",
                    played = false,
                    sources = listOf(testSource()),
                )
            assertEquals(2, transformCount)
            assertEquals("Refreshed", projected.value.name)

            // Mutating the watched item itself re-runs the projection.
            store.applyPatch(watchedId, UserDataPatch(played = true))
            assertEquals(3, transformCount)
            assertTrue(projected.value.played)
        }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun projectAdvancesSkipVersionAcrossUnrelatedWritesAndHandlesBatchHistoryOverflow() = runTest {
        val store = ItemStore()
        val watchedId = testUuid(20)
        val movie = testMovie(id = watchedId, played = false)
        store.put(movie)

        var transformCount = 0
        val projected =
            store.project(backgroundScope, MutableStateFlow(movie)) { state ->
                transformCount++
                live(state)
            }
        assertEquals(1, transformCount)

        // Sequential unrelated writes advance lastVersion on skip without overflow.
        repeat(5) { idx ->
            store.put(testMovie(id = testUuid(2000 + idx), name = "Seq$idx"))
            testScheduler.advanceUntilIdle()
        }
        assertEquals(1, transformCount)

        // Burst >128 writes before collection causes versionHistory overflow -> conservative
        // resync.
        repeat(135) { idx -> store.put(testMovie(id = testUuid(3000 + idx), name = "Burst$idx")) }
        testScheduler.advanceUntilIdle()
        testScheduler.runCurrent()
        assertEquals(2, transformCount)

        store.applyPatch(watchedId, UserDataPatch(favorite = true))
        testScheduler.advanceUntilIdle()
        testScheduler.runCurrent()
        assertEquals(3, transformCount)
        assertTrue(projected.value.favorite)
    }
}
