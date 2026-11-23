package com.makd.afinity.player.music

import com.makd.afinity.data.manager.MediaRefreshBus
import com.makd.afinity.data.manager.RefreshTrigger
import com.makd.afinity.data.manager.Session
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.store.ItemStore
import com.makd.afinity.testutil.FakeApiClient
import com.makd.afinity.testutil.TestMediaFixtures.testCollection
import com.makd.afinity.testutil.TestMediaFixtures.testTrack
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UpdateUserItemDataDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastProgressTrackerTest {

    @Test
    fun `resolvePodcastProgress handles resume, completion, and early stop`() {
        assertEquals(
            PodcastProgressOutcome(played = false, playbackPositionTicks = 600_000_000L),
            resolvePodcastProgress(
                positionMs = 60_000L,
                durationMs = 600_000L,
                isEnded = false,
                wasPlayed = false,
            ),
        )
        assertEquals(
            PodcastProgressOutcome(played = true, playbackPositionTicks = 0L),
            resolvePodcastProgress(
                positionMs = 580_000L,
                durationMs = 600_000L,
                isEnded = false,
                wasPlayed = false,
            ),
        )
        assertEquals(
            PodcastProgressOutcome(played = true, playbackPositionTicks = 0L),
            resolvePodcastProgress(
                positionMs = 100_000L,
                durationMs = 600_000L,
                isEnded = true,
                wasPlayed = false,
            ),
        )
        assertEquals(
            PodcastProgressOutcome(played = false, playbackPositionTicks = 0L),
            resolvePodcastProgress(
                positionMs = 3_000L,
                durationMs = 600_000L,
                isEnded = false,
                wasPlayed = false,
            ),
        )
        assertNull(
            resolvePodcastProgress(
                positionMs = 3_000L,
                durationMs = 600_000L,
                isEnded = false,
                wasPlayed = true,
            ),
        )
    }

    @Test
    fun `syncProgress skips non-podcast tracks and updates Jellyfin UserData for podcasts`() =
        runTest {
            val userId = testUuid(1)
            val podcastLibId = testUuid(10)
            val podcastAlbumId = testUuid(20)
            val musicAlbumId = testUuid(21)
            val episodeId = testUuid(30)
            val songId = testUuid(31)

            val apiClient = FakeApiClient()
            apiClient.onPost("/Users/{userId}/Items/{itemId}/UserData") { req ->
                val itemId = req.pathParameters["itemId"] as UUID
                FakeApiClient.rawJsonResponse(
                    """{"ItemId":"$itemId","Key":"$itemId","Played":false,"PlaybackPositionTicks":900000000,"PlayCount":1,"IsFavorite":false}""",
                )
            }
            apiClient.onPost("/UserItems/{itemId}/UserData") { req ->
                val itemId = req.pathParameters["itemId"] as UUID
                FakeApiClient.rawJsonResponse(
                    """{"ItemId":"$itemId","Key":"$itemId","Played":false,"PlaybackPositionTicks":900000000,"PlayCount":1,"IsFavorite":false}""",
                )
            }

            val sessionManager: SessionManager =
                mockk(relaxed = true) {
                    every { currentSession } returns
                        MutableStateFlow(Session("srv-1", userId, "https://jf.example.com"))
                    coEvery { getCurrentApiClient() } returns apiClient
                }
            val podcastLib =
                testCollection(id = podcastLibId, name = "Podcasts", type = CollectionType.Music)
            val musicLib =
                testCollection(id = testUuid(11), name = "Music", type = CollectionType.Music)
            val mediaRepository: MediaRepository =
                mockk(relaxed = true) {
                    every { libraries } returns MutableStateFlow(listOf(podcastLib, musicLib))
                    coEvery {
                        getItemsResult(
                            parentId = podcastLibId,
                            includeItemTypes = listOf("MUSIC_ALBUM"),
                            limit = any(),
                            fields = any(),
                            recursive = true,
                            enableImages = false,
                            enableUserData = false,
                        )
                    } returns
                        Result.success(
                            BaseItemDtoQueryResult(
                                items =
                                    listOf(
                                        BaseItemDto(
                                            id = podcastAlbumId,
                                            type = BaseItemKind.MUSIC_ALBUM,
                                        ),
                                    ),
                                startIndex = 0,
                                totalRecordCount = 1,
                            ),
                        )
                }
            val itemStore = ItemStore()
            val refreshBus = MediaRefreshBus()
            val tracker =
                PodcastProgressTracker(sessionManager, mediaRepository, itemStore, refreshBus)

            val song =
                testTrack(id = songId).copy(albumId = musicAlbumId, runtimeTicks = 2_400_000_000L)
            tracker.syncProgress(song, positionMs = 90_000L, isEnded = false)
            assertTrue(apiClient.requests.isEmpty())

            val episode =
                testTrack(id = episodeId, played = false)
                    .copy(
                        albumId = podcastAlbumId,
                        runtimeTicks = 18_000_000_000L,
                    )
            itemStore.put(episode)
            tracker.syncProgress(episode, positionMs = 90_000L, isEnded = false)

            assertEquals(1, apiClient.requests.size)
            val body = apiClient.requests.single().requestBody as UpdateUserItemDataDto
            assertEquals(false, body.played)
            assertEquals(900_000_000L, body.playbackPositionTicks)
            verify {
                mediaRepository.updateContinueListeningPodcast(
                    match {
                        it.id == episodeId && it.playbackPositionTicks == 900_000_000L && !it.played
                    },
                )
            }
            val patched =
                itemStore.getAs<com.makd.afinity.data.models.music.AfinityTrack>(episodeId)
            assertEquals(900_000_000L, patched?.playbackPositionTicks)

            var observedTrigger: RefreshTrigger? = null
            val collector =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    observedTrigger = refreshBus.events.first()
                }
            tracker.syncProgress(episode, positionMs = 1_800_000L, isEnded = true)
            collector.join()
            assertEquals(RefreshTrigger.USER_DATA_CHANGED, observedTrigger)
        }

    @Test
    fun `podcastAlbumIds caches empty library result and retries after fetch failure`() = runTest {
        val userId = testUuid(2)
        val podcastLibId = testUuid(12)
        val podcastLib =
            testCollection(id = podcastLibId, name = "Podcasts", type = CollectionType.Music)
        val sessionManager: SessionManager =
            mockk(relaxed = true) {
                every { currentSession } returns
                    MutableStateFlow(Session("srv-1", userId, "https://jf.example.com"))
            }
        val mediaRepository: MediaRepository =
            mockk(relaxed = true) {
                every { libraries } returns MutableStateFlow(listOf(podcastLib))
                coEvery {
                    getItemsResult(
                        parentId = podcastLibId,
                        includeItemTypes = listOf("MUSIC_ALBUM"),
                        limit = any(),
                        fields = any(),
                        recursive = true,
                        enableImages = false,
                        enableUserData = false,
                    )
                } returnsMany
                    listOf(
                        Result.failure(RuntimeException("network error")),
                        Result.success(
                            BaseItemDtoQueryResult(
                                items = emptyList(),
                                startIndex = 0,
                                totalRecordCount = 0,
                            ),
                        ),
                    )
            }
        val tracker =
            PodcastProgressTracker(sessionManager, mediaRepository, ItemStore(), MediaRefreshBus())
        val track = testTrack(id = testUuid(40)).copy(albumId = testUuid(41))

        assertFalse(tracker.isPodcastTrack(track))
        assertFalse(tracker.isPodcastTrack(track))
        assertFalse(tracker.isPodcastTrack(track))

        // 1st call failed (not cached), 2nd call succeeded with empty set (cached), 3rd hit cache
        coVerify(exactly = 2) {
            mediaRepository.getItemsResult(
                parentId = podcastLibId,
                includeItemTypes = listOf("MUSIC_ALBUM"),
                limit = any(),
                fields = any(),
                recursive = true,
                enableImages = false,
                enableUserData = false,
            )
        }
    }
}
