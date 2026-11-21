package com.makd.afinity.data.store

import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.UserDataPatch
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.testutil.TestMediaFixtures.testMovie
import com.makd.afinity.testutil.TestMediaFixtures.testSource
import com.makd.afinity.testutil.TestMediaFixtures.testTrack
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ItemProjectionTest {

    private data class SectionWrapper(
        val title: String,
        val lead: AfinityMovie?,
        val items: List<AfinityMovie>,
        val tracks: List<AfinityTrack> = emptyList(),
        val buckets: Map<String, List<AfinityMovie>> = emptyMap(),
    )

    @Test
    fun projectUpdatesLiveItemsAndPreservesListReferenceAcrossUnrelatedUpdates() =
        runTest(UnconfinedTestDispatcher()) {
            val store = ItemStore()
            val movie1 = testMovie(id = testUuid(1), played = false)
            val movie2 = testMovie(id = testUuid(2), favorite = false)
            val unrelatedId = testUuid(99)
            val base = MutableStateFlow(SectionWrapper("Featured", movie1, listOf(movie1, movie2)))

            val projected =
                store.project(backgroundScope, base) { s ->
                    s.copy(
                        lead = liveOrNull(s.lead),
                        items = live(s.items),
                    )
                }

            val initialItems = projected.value.items
            assertFalse(projected.value.lead!!.played)

            // Unrelated patch re-runs transform due to _items emission, but remembered() keeps list
            // identity.
            store.put(testMovie(id = unrelatedId, played = true))
            assertSame(initialItems, projected.value.items)

            // Patching a projected item emits updated state.
            store.applyPatch(movie1.id, UserDataPatch(played = true))
            assertTrue(projected.value.lead!!.played)
            assertTrue(projected.value.items[0].played)
            assertNotSame(initialItems, projected.value.items)
        }

    @Test
    fun projectResolvesLiveEachLiveNestedLiveNestedOwnersAndLiveValues() =
        runTest(UnconfinedTestDispatcher()) {
            val store = ItemStore()
            val m1 = testMovie(id = testUuid(1), played = false)
            val t1 = testTrack(id = testUuid(2), favorite = false)
            val m2 = testMovie(id = testUuid(3), favorite = false)

            data class Holder(val movie: AfinityMovie)
            data class UiState(
                val holders: List<Holder>,
                val sections: List<SectionWrapper>,
                val map: Map<String, List<AfinityMovie>>,
            )

            val initial =
                UiState(
                    holders = listOf(Holder(m1)),
                    sections = listOf(SectionWrapper("Row", m1, listOf(m1), listOf(t1))),
                    map = mapOf("genre" to listOf(m2)),
                )
            val base = MutableStateFlow(initial)

            val projected =
                store.project(backgroundScope, base) { state ->
                    state.copy(
                        holders =
                            liveEach(state.holders, { it.movie }) { h, next ->
                                h.copy(movie = next)
                            },
                        sections =
                            liveNestedOwners(
                                liveNested(state.sections, { it.items }) { sec, items ->
                                    sec.copy(items = items)
                                },
                                { it.tracks },
                            ) { sec, tracks ->
                                sec.copy(tracks = tracks)
                            },
                        map = liveValues(state.map),
                    )
                }

            // Re-running with unrelated item keeps outer collection identity.
            val beforeHolders = projected.value.holders
            val beforeMap = projected.value.map
            store.put(testMovie(id = testUuid(555)))
            assertSame(beforeHolders, projected.value.holders)
            assertSame(beforeMap, projected.value.map)

            store.applyPatch(m1.id, UserDataPatch(played = true))
            store.applyPatch(t1.id, UserDataPatch(favorite = true))
            store.applyPatch(m2.id, UserDataPatch(favorite = true))

            assertTrue(projected.value.holders.single().movie.played)
            assertTrue(projected.value.sections.single().items.single().played)
            assertTrue(projected.value.sections.single().tracks.single().favorite)
            assertTrue(projected.value.map["genre"]!!.single().favorite)
        }

    @Test
    fun freshEmissionOnStaleItemBypassesStaleSnapshotAndClearsStaleFlag() =
        runTest(UnconfinedTestDispatcher()) {
            val store = ItemStore()
            val id = testUuid(1)
            val staleMovie = testMovie(id = id, name = "Stale Name", overview = "Old")
            store.put(staleMovie)
            store.markStale(listOf(id))

            val freshMovie =
                testMovie(
                    id = id,
                    name = "Refreshed Name",
                    overview = "Updated",
                    sources = listOf(testSource()),
                )
            val base = MutableStateFlow(freshMovie)
            val projected = store.project(backgroundScope, base) { live(it) }

            assertEquals("Refreshed Name", projected.value.name)
            assertFalse(store.isContentStale(id))
            assertEquals("Refreshed Name", store.getAs<AfinityMovie>(id)!!.name)
        }

    @Test
    fun cancellingProjectionScopeReleasesRetainedIds() =
        runTest(UnconfinedTestDispatcher()) {
            val store = ItemStore()
            val id = testUuid(42)
            val movie = testMovie(id = id)

            val childScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            store.project(childScope, MutableStateFlow(movie)) { live(it) }
            assertEquals(listOf(id), store.onScreen(listOf(id)))

            childScope.cancel()
            assertEquals(emptyList<Any>(), store.onScreen(listOf(id)))
        }
}
