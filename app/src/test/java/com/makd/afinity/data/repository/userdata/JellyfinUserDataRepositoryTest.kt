package com.makd.afinity.data.repository.userdata

import com.makd.afinity.data.manager.MediaChangeManager
import com.makd.afinity.data.manager.Session
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.media.UserDataPatch
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.testutil.TestMediaFixtures.mockUserItemDataDto
import com.makd.afinity.testutil.TestMediaFixtures.testEpisode
import com.makd.afinity.testutil.TestMediaFixtures.testMovie
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.operations.LibraryApi
import org.jellyfin.sdk.api.operations.SessionApi
import org.jellyfin.sdk.api.operations.UserDataApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UpdateUserItemDataDto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JellyfinUserDataRepositoryTest {

    @Before
    fun setUp() {
        mockkConstructor(UserDataApi::class, SessionApi::class, LibraryApi::class)
    }

    @After
    fun tearDown() {
        unmockkConstructor(UserDataApi::class, SessionApi::class, LibraryApi::class)
    }

    private class Harness(hasSession: Boolean = true) {
        val userId = testUuid(1)
        val apiClient: ApiClient = mockk(relaxed = true)
        val sessionFlow =
            MutableStateFlow(
                if (hasSession) Session("srv-1", userId, "https://jf.example.com") else null,
            )
        val sessionManager: SessionManager = mockk {
            every { currentSession } returns sessionFlow
            every { getCurrentApiClient() } answers
                {
                    if (sessionFlow.value != null) apiClient else null
                }
        }
        val databaseRepository: DatabaseRepository = mockk(relaxed = true)
        val mediaChangeManager: MediaChangeManager = mockk(relaxed = true)
        val repository =
            JellyfinUserDataRepository(sessionManager, databaseRepository, mediaChangeManager)
    }

    @Test
    fun `markWatched and markUnwatched update DB, emit change, and return false without session`() =
        runTest {
            val noSession = Harness(hasSession = false)
            assertFalse(noSession.repository.markWatched(testUuid(10)))

            val h = Harness()
            val movieId = testUuid(10)
            val episodeId = testUuid(11)
            val cachedMovie = testMovie(id = movieId, played = false)
            val cachedEp = testEpisode(id = episodeId, played = true)
            coEvery { h.databaseRepository.getEpisode(movieId, h.userId) } returns null
            coEvery { h.databaseRepository.getMovie(movieId, h.userId) } returns cachedMovie
            coEvery { h.databaseRepository.getEpisode(episodeId, h.userId) } returns cachedEp
            coEvery {
                anyConstructed<UserDataApi>().markPlayedItem(movieId, h.userId, any())
            } returns Response(mockUserItemDataDto(played = true), 200, emptyMap())
            coEvery { anyConstructed<UserDataApi>().markUnplayedItem(episodeId, h.userId) } returns
                Response(mockUserItemDataDto(played = false), 200, emptyMap())

            assertTrue(h.repository.markWatched(movieId))
            coVerify {
                h.databaseRepository.updateMovie(
                    cachedMovie.copy(played = true, playbackPositionTicks = 0),
                )
            }
            coVerify { h.databaseRepository.markUserDataSynced(h.userId, movieId) }
            verify {
                h.mediaChangeManager.notifyItemChanged(
                    movieId,
                    patch = UserDataPatch(played = true, playbackPositionTicks = 0),
                )
            }

            assertTrue(h.repository.markUnwatched(episodeId))
            coVerify {
                h.databaseRepository.updateEpisode(
                    cachedEp.copy(played = false, playbackPositionTicks = 0),
                )
            }
            coVerify { h.databaseRepository.markUserDataSynced(h.userId, episodeId) }
            verify {
                h.mediaChangeManager.notifyItemChanged(
                    episodeId,
                    patch = UserDataPatch(played = false, playbackPositionTicks = 0),
                )
            }
        }

    @Test
    fun `favorites, rating clamping, likes, position, and error recovery work as expected`() =
        runTest {
            val h = Harness()
            val itemId = testUuid(20)
            val dto = mockUserItemDataDto(played = false, isFavorite = true)

            coEvery { anyConstructed<UserDataApi>().markFavoriteItem(itemId, h.userId) } returns
                Response(dto, 200, emptyMap())
            coEvery { anyConstructed<UserDataApi>().unmarkFavoriteItem(itemId, h.userId) } throws
                ApiClientException("500")
            coEvery {
                anyConstructed<UserDataApi>().updateItemUserData(itemId, h.userId, any())
            } returns Response(dto, 200, emptyMap())
            coEvery { anyConstructed<UserDataApi>().getItemUserData(itemId, h.userId) } returns
                Response(dto, 200, emptyMap())
            coEvery { anyConstructed<SessionApi>().reportPlaybackProgress(any()) } returns
                Response(Unit, 204, emptyMap())

            assertTrue(h.repository.addToFavorites(itemId))
            assertFalse(h.repository.removeFromFavorites(itemId))
            assertEquals(dto, h.repository.getUserData(itemId))
            assertTrue(h.repository.updatePlaybackPosition(itemId, 50_000L))
            assertTrue(h.repository.setLike(itemId, true))

            assertTrue(h.repository.setRating(itemId, 99))
            coVerify {
                anyConstructed<UserDataApi>()
                    .updateItemUserData(
                        itemId = itemId,
                        userId = h.userId,
                        data = match<UpdateUserItemDataDto> { it.rating == 10.0 },
                    )
            }
            assertTrue(h.repository.removeRating(itemId))

            coEvery { anyConstructed<UserDataApi>().getItemUserData(itemId, h.userId) } throws
                RuntimeException("boom")
            assertNull(h.repository.getUserData(itemId))
        }

    @Test
    fun `getFavoriteItems, syncUserData threshold, and getUserDataBatch handle partial failures`() =
        runTest {
            val h = Harness()
            val id1 = testUuid(31)
            val id2 = testUuid(32)
            val itemDto1: BaseItemDto = mockk { every { id } returns id1 }
            val queryResult: BaseItemDtoQueryResult = mockk {
                every { items } returns listOf(itemDto1)
            }
            coEvery {
                anyConstructed<LibraryApi>()
                    .getItems(
                        userId = h.userId,
                        isFavorite = true,
                        includeItemTypes = listOf(BaseItemKind.MOVIE),
                        limit = 10,
                        startIndex = 0,
                        fields = any(),
                        enableUserData = true,
                        enableTotalRecordCount = false,
                    )
            } returns Response(queryResult, 200, emptyMap())

            assertEquals(
                listOf(id1),
                h.repository.getFavoriteItems(listOf("movie", "not_a_kind"), limit = 10),
            )

            fun syncDto(itemId: UUID, played: Boolean = false, isFavorite: Boolean = false) =
                mockk<org.jellyfin.sdk.model.api.UserItemDataDto>(relaxed = true) {
                    every { this@mockk.itemId } returns itemId
                    every { this@mockk.played } returns played
                    every { this@mockk.isFavorite } returns isFavorite
                    every { rating } returns null
                    every { lastPlayedDate } returns null
                }

            val ud1 = syncDto(id1, played = true)
            val ud2 = syncDto(id2, played = false)
            val ud3 = syncDto(UUID(0L, 33L), isFavorite = true)

            coEvery {
                anyConstructed<UserDataApi>().updateItemUserData(id1, h.userId, any())
            } returns Response(ud1, 200, emptyMap())
            coEvery {
                anyConstructed<UserDataApi>().updateItemUserData(id2, h.userId, any())
            } throws RuntimeException("fail")
            coEvery {
                anyConstructed<UserDataApi>().updateItemUserData(UUID(0L, 33L), h.userId, any())
            } returns Response(ud3, 200, emptyMap())

            // 2/3 (> 50%) succeeds
            assertTrue(h.repository.syncUserData(listOf(ud1, ud2, ud3)))
            // 1/2 (== 50%, not > 50%) returns false
            assertFalse(h.repository.syncUserData(listOf(ud1, ud2)))

            coEvery { anyConstructed<UserDataApi>().getItemUserData(id1, h.userId) } returns
                Response(ud1, 200, emptyMap())
            coEvery { anyConstructed<UserDataApi>().getItemUserData(id2, h.userId) } throws
                ApiClientException("err")
            assertEquals(mapOf(id1 to ud1), h.repository.getUserDataBatch(listOf(id1, id2)))
        }
}
