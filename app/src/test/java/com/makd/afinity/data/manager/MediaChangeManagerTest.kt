package com.makd.afinity.data.manager

import android.os.SystemClock
import app.cash.turbine.test
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.UserDataPatch
import com.makd.afinity.data.models.user.AfinityUserDataDto
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.store.ItemStore
import com.makd.afinity.testutil.TestMediaFixtures.mockUserItemDataDto
import com.makd.afinity.testutil.TestMediaFixtures.testEpisode
import com.makd.afinity.testutil.TestMediaFixtures.testMovie
import com.makd.afinity.testutil.TestMediaFixtures.testShow
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaChangeManagerTest {

    private var clockMs = 10_000L

    @Before
    fun setUp() {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } answers { clockMs }
    }

    @After
    fun tearDown() {
        unmockkStatic(SystemClock::class)
    }

    private fun createManager(
        mediaRepository: MediaRepository = mockk(relaxed = true),
        databaseRepository: DatabaseRepository = mockk(relaxed = true),
        userId: UUID = testUuid(1),
        mediaRefreshBus: MediaRefreshBus = MediaRefreshBus(),
        itemStore: ItemStore = ItemStore(),
        scope: kotlinx.coroutines.CoroutineScope,
    ): MediaChangeManager {
        val sessionManager: SessionManager = mockk {
            every { currentSession } returns
                MutableStateFlow(Session("srv-1", userId, "https://jf.example.com"))
        }
        return MediaChangeManager(
            mediaRepository = mediaRepository,
            databaseRepository = databaseRepository,
            sessionManager = sessionManager,
            mediaRefreshBus = mediaRefreshBus,
            itemStore = itemStore,
            scope = scope,
        )
    }

    @Test
    fun `publishKnownChange emits batch, updates store, and refreshes derived episode state`() =
        runTest(UnconfinedTestDispatcher()) {
            val seriesId = testUuid(100)
            val episode = testEpisode(id = testUuid(101), seriesId = seriesId, played = true)
            val show = testShow(id = seriesId)
            val mediaRepository: MediaRepository =
                mockk(relaxed = true) { coEvery { getItemById(seriesId) } returns show }
            val bus = MediaRefreshBus()
            val store = ItemStore()
            val manager =
                createManager(
                    mediaRepository = mediaRepository,
                    mediaRefreshBus = bus,
                    itemStore = store,
                    scope = backgroundScope,
                )

            bus.events.test {
                manager.batches.test {
                    manager.publishKnownChange(
                        updatedItem = episode,
                        source = MediaChangeSource.PLAYBACK,
                        patch = UserDataPatch(played = true),
                    )
                    val batch = awaitItem()
                    assertEquals(MediaChangeSource.PLAYBACK, batch.source)
                    assertEquals(1, batch.changes.size)
                    assertEquals(show, batch.changes.first().parentItem)
                    assertTrue(batch.affects(episode.id, seriesId))
                }
                assertEquals(RefreshTrigger.USER_DATA_CHANGED, awaitItem())
            }

            assertEquals(episode, store.get(episode.id))
            assertEquals(show, store.get(seriesId))
        }

    @Test
    fun `applyUserDataChangesBatch patches DB, deduplicates parents, and honors recent parent cache window`() =
        runTest(UnconfinedTestDispatcher()) {
            val seriesId = testUuid(200)
            val epId1 = testUuid(201)
            val epId2 = testUuid(202)
            val show = testShow(id = seriesId)
            val ep1 = testEpisode(id = epId1, seriesId = seriesId)
            val ep2 = testEpisode(id = epId2, seriesId = seriesId)

            val dto1 = mockUserItemDataDto(played = true).also { every { it.itemId } returns epId1 }
            val dto2 =
                mockUserItemDataDto(played = false, isFavorite = true).also {
                    every { it.itemId } returns epId2
                }

            val mediaRepository: MediaRepository =
                mockk(relaxed = true) {
                    coEvery { getItemById(epId1) } returns ep1
                    coEvery { getItemById(epId2) } returns ep2
                    coEvery { getItemById(seriesId) } returns show
                }
            val databaseRepository: DatabaseRepository = mockk(relaxed = true)
            val manager =
                createManager(
                    mediaRepository = mediaRepository,
                    databaseRepository = databaseRepository,
                    scope = backgroundScope,
                )

            // First update resolves and caches parent show at clockMs = 10_000.
            manager.applyUserDataChange(dto1)
            coVerify(exactly = 1) { mediaRepository.getItemById(seriesId) }
            coVerify { databaseRepository.patchUserDataLocally(epId1, any(), "srv-1", dto1) }

            // Second update within PARENT_REUSE_MS (< 2000ms) reuses cached parent show.
            clockMs = 11_500L
            manager.applyUserDataChange(dto2)
            coVerify(exactly = 1) { mediaRepository.getItemById(seriesId) }

            // Third update past PARENT_REUSE_MS (>= 2000ms) re-fetches parent show.
            clockMs = 12_500L
            manager.applyUserDataChange(dto2)
            coVerify(exactly = 2) { mediaRepository.getItemById(seriesId) }
        }

    @Test
    fun `reconcileUserData ignores synced or up-to-date items and applies stale ones`() =
        runTest(UnconfinedTestDispatcher()) {
            val userId = testUuid(1)
            val movieId = testUuid(300)
            val mediaRepository: MediaRepository = mockk(relaxed = true)
            val databaseRepository: DatabaseRepository = mockk(relaxed = true)
            val manager =
                createManager(
                    mediaRepository = mediaRepository,
                    databaseRepository = databaseRepository,
                    userId = userId,
                    scope = backgroundScope,
                )

            val fresh =
                mockUserItemDataDto(played = true, isFavorite = false).also {
                    every { it.itemId } returns movieId
                }

            // Pending local sync: do not overwrite from server.
            coEvery { databaseRepository.getUserData(userId, movieId) } returns
                AfinityUserDataDto(
                    userId = userId,
                    itemId = movieId,
                    serverId = "srv-1",
                    played = false,
                    favorite = false,
                    likes = false,
                    playbackPositionTicks = 0L,
                    toBeSynced = true,
                )
            manager.reconcileUserData(fresh)
            coVerify(exactly = 0) {
                databaseRepository.patchUserDataLocally(any(), any(), any(), any())
            }

            // Stale local state with toBeSynced = false triggers patch.
            coEvery { databaseRepository.getUserData(userId, movieId) } returns
                AfinityUserDataDto(
                    userId = userId,
                    itemId = movieId,
                    serverId = "srv-1",
                    played = false,
                    favorite = false,
                    likes = false,
                    playbackPositionTicks = 0L,
                    toBeSynced = false,
                )
            manager.reconcileUserData(fresh)
            coVerify(exactly = 1) {
                databaseRepository.patchUserDataLocally(movieId, userId, "srv-1", fresh)
            }
        }

    @Test
    fun `publishContentChanges patches small batches and marks large offscreen batches stale`() =
        runTest(UnconfinedTestDispatcher()) {
            val movieId = testUuid(401)
            val missingId = testUuid(402)
            val updatedMovie = testMovie(id = movieId, name = "Updated Title")
            val mediaRepository: MediaRepository =
                mockk(relaxed = true) {
                    coEvery {
                        getOwnersByIds(listOf(movieId, missingId), FieldSets.ITEM_DETAIL)
                    } returns listOf(updatedMovie)
                }
            val store = ItemStore()
            val manager =
                createManager(
                    mediaRepository = mediaRepository,
                    itemStore = store,
                    scope = backgroundScope,
                )

            manager.publishContentChanges(listOf(movieId, missingId))
            assertEquals("Updated Title", (store.get(movieId) as AfinityMovie).name)
            assertFalse(store.isContentStale(movieId))
            assertTrue(store.isContentStale(missingId))

            // Batch larger than CONTENT_CHANGE_PATCH_LIMIT (10) marks off-screen items stale.
            val largeIds = (1..12).map { testUuid(500 + it) }
            val onScreenId = largeIds.first()
            val offScreenId = largeIds.last()
            val initialOnScreenMovie = testMovie(id = onScreenId, name = "Initial On Screen")
            store.project(backgroundScope, MutableStateFlow(initialOnScreenMovie)) { live(it) }
            val onScreenMovie = testMovie(id = onScreenId, name = "Refetched On Screen")
            coEvery {
                mediaRepository.getOwnersByIds(listOf(onScreenId), FieldSets.ITEM_DETAIL)
            } returns listOf(onScreenMovie)

            manager.publishContentChanges(largeIds)
            assertFalse(store.isContentStale(onScreenId))
            assertTrue(store.isContentStale(offScreenId))
        }

    @Test
    fun `simple signal methods emit non-empty events and skip empty lists`() =
        runTest(UnconfinedTestDispatcher()) {
            val manager = createManager(scope = backgroundScope)

            manager.itemsRemoved.test {
                manager.notifyItemsRemoved(emptyList())
                expectNoEvents()
                manager.notifyItemsRemoved(listOf("id-1"))
                assertEquals(listOf("id-1"), awaitItem())
            }

            manager.itemsAdded.test {
                manager.notifyItemsAdded(emptyList())
                expectNoEvents()
                manager.notifyItemsAdded(listOf("id-2"))
                assertEquals(listOf("id-2"), awaitItem())
            }

            manager.libraryContentChanges.test {
                manager.notifyLibraryContentChanged("scan", removalOnly = true)
                assertEquals(LibraryContentChangeEvent("scan", removalOnly = true), awaitItem())
            }
        }
}
