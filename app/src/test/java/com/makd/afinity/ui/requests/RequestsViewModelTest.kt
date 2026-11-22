package com.makd.afinity.ui.requests

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.makd.afinity.data.manager.AdminChangeBroadcaster
import com.makd.afinity.data.models.jellyseerr.DiscoverSlider
import com.makd.afinity.data.models.jellyseerr.JellyseerrRequest
import com.makd.afinity.data.models.jellyseerr.JellyseerrUser
import com.makd.afinity.data.models.jellyseerr.MediaInfo
import com.makd.afinity.data.models.jellyseerr.MediaType
import com.makd.afinity.data.models.jellyseerr.PublicSettings
import com.makd.afinity.data.models.jellyseerr.RequestUser
import com.makd.afinity.data.models.jellyseerr.SearchResultItem
import com.makd.afinity.data.models.jellyseerr.WatchlistEvent
import com.makd.afinity.data.repository.JellyseerrRepository
import com.makd.afinity.data.repository.RequestEvent
import com.makd.afinity.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class RequestsViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private inner class Harness {
        val context: Context = ApplicationProvider.getApplicationContext()
        val sessionIdFlow = MutableStateFlow<String?>("seerr-session-1")
        val isAuthFlow = MutableStateFlow(false)
        val isReachableFlow = MutableStateFlow(true)
        val requestsFlow = MutableStateFlow<List<JellyseerrRequest>>(emptyList())
        val requestEventsFlow = MutableSharedFlow<RequestEvent>(extraBufferCapacity = 4)
        val watchlistEventsFlow = MutableSharedFlow<WatchlistEvent>(extraBufferCapacity = 4)
        val watchlistArtworkFlow = MutableStateFlow<Map<String, SearchResultItem>>(emptyMap())

        val jellyseerrRepository: JellyseerrRepository =
            mockk(relaxed = true) {
                every { currentSessionId } returns sessionIdFlow
                every { isAuthenticated } returns isAuthFlow
                every { isReachable } returns isReachableFlow
                every { observeRequests() } returns requestsFlow
                every { requestEvents } returns requestEventsFlow
                every { watchlistEvents } returns watchlistEventsFlow
                every { watchlistArtwork } returns watchlistArtworkFlow
                coEvery { getServerUrl() } returns "https://seerr.example.com"
                coEvery { getCurrentUser() } returns
                    Result.success(
                        JellyseerrUser(
                            id = 1,
                            username = "alex",
                            displayName = "Alex",
                            permissions = 2,
                        ),
                    )
                coEvery { getPublicSettings() } returns
                    Result.success(PublicSettings(applicationTitle = "Seerr"))
                coEvery { getRequests(any(), any(), any()) } returns Result.success(emptyList())
                coEvery { getDiscoverSliders() } returns
                    Result.success(
                        listOf(
                            DiscoverSlider(
                                id = 1,
                                type = DiscoverSlider.Type.STUDIOS,
                                enabled = true,
                                order = 0,
                            ),
                        ),
                    )
                coEvery { findMediaByName(any(), any()) } returns Result.success(emptyList())
            }
        val adminChangeBroadcaster = AdminChangeBroadcaster()
        private val store = ViewModelStore()

        fun createViewModel(): RequestsViewModel {
            val factory =
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        RequestsViewModel(
                            context = context,
                            jellyseerrRepository = jellyseerrRepository,
                            adminChangeBroadcaster = adminChangeBroadcaster,
                        )
                            as T
                }
            return ViewModelProvider(store, factory)[RequestsViewModel::class.java]
        }

        fun dispose() {
            store.clear()
        }
    }

    private fun sampleRequest(
        id: Int,
        status: Int = 1,
        tmdbId: Int = 100 + id,
        jellyfinMediaId: String? = null,
    ) =
        JellyseerrRequest(
            id = id,
            status = status,
            media =
                MediaInfo(
                    id = 500 + id,
                    mediaType = "movie",
                    tmdbId = tmdbId,
                    tvdbId = null,
                    status = 5,
                    jellyfinMediaId = jellyfinMediaId,
                    title = "Movie $id",
                ),
            requestedBy = RequestUser(id = 1, displayName = "Alex", avatar = null),
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z",
        )

    @Test
    fun `request moderation actions, delete, and resolveAndNavigate update state`() =
        runTest(timeout = 30.seconds) {
            val h = Harness()
            try {
                val pendingReq = sampleRequest(id = 10, status = 1, jellyfinMediaId = "jf-movie-10")
                h.requestsFlow.value = listOf(pendingReq)

                val vm = h.createViewModel()
                h.isAuthFlow.value = true
                advanceUntilIdle()

                assertEquals("https://seerr.example.com", vm.uiState.value.jellyseerrUrl)
                assertEquals(1, vm.uiState.value.requests.size)

                // Approve request updates matching item in uiState.requests
                val approved = pendingReq.copy(status = 2)
                coEvery { h.jellyseerrRepository.approveRequest(10) } returns
                    Result.success(approved)
                vm.approveRequest(10)
                advanceUntilIdle()
                assertEquals(2, vm.uiState.value.requests.first().status)

                // Decline request updates matching item in uiState.requests
                val declined = approved.copy(status = 3)
                coEvery { h.jellyseerrRepository.declineRequest(10) } returns
                    Result.success(declined)
                vm.declineRequest(10)
                advanceUntilIdle()
                assertEquals(3, vm.uiState.value.requests.first().status)

                // resolveAndNavigate emits navigateToItem event with Jellyfin media id
                vm.navigateToItem.test {
                    vm.resolveAndNavigate(pendingReq)
                    advanceUntilIdle()
                    assertEquals("jf-movie-10" to "Movie", awaitItem())
                    cancelAndIgnoreRemainingEvents()
                }

                // Delete failure sets error, clearError clears it, delete success succeeds
                coEvery { h.jellyseerrRepository.deleteRequest(10) } returnsMany
                    listOf(
                        Result.failure(IllegalStateException("Cannot delete")),
                        Result.success(Unit),
                    )
                vm.deleteRequest(10)
                advanceUntilIdle()
                assertFalse(vm.uiState.value.isDeletingRequest)
                assertTrue(vm.uiState.value.error?.contains("Cannot delete") == true)

                vm.clearError()
                assertNull(vm.uiState.value.error)

                vm.deleteRequest(10)
                advanceUntilIdle()
                assertFalse(vm.uiState.value.isDeletingRequest)
                coVerify(exactly = 2) { h.jellyseerrRepository.deleteRequest(10) }
            } finally {
                h.dispose()
            }
        }

    @Test
    fun `searchForContent, createRequest, retryConnection, and session loss reset state`() =
        runTest(timeout = 30.seconds) {
            val h = Harness()
            try {
                val vm = h.createViewModel()
                h.isAuthFlow.value = true
                advanceUntilIdle()

                val hit = SearchResultItem(id = 42, mediaType = "movie", title = "Dune: Part Two")
                coEvery { h.jellyseerrRepository.findMediaByName("Dune", null) } returns
                    Result.success(listOf(hit))

                vm.searchForContent("Dune")
                advanceUntilIdle()
                assertEquals(listOf(hit), vm.uiState.value.searchResults)
                assertTrue(vm.uiState.value.showSearchDialog)

                coEvery {
                    h.jellyseerrRepository.createRequest(
                        mediaId = 42,
                        mediaType = MediaType.MOVIE,
                        seasons = null,
                        is4k = any(),
                        serverId = any(),
                        profileId = any(),
                        rootFolder = any(),
                        tvdbId = any(),
                        languageProfileId = any(),
                        tags = any(),
                        userId = any(),
                    )
                } returns Result.success(sampleRequest(99))

                vm.createRequest(42, MediaType.MOVIE)
                advanceUntilIdle()
                assertFalse(vm.uiState.value.isCreatingRequest)
                assertFalse(vm.uiState.value.showSearchDialog)
                assertTrue(vm.uiState.value.searchResults.isEmpty())

                vm.retryConnection()
                advanceUntilIdle()
                coVerify { h.jellyseerrRepository.retryConnection() }

                // Auth loss clears cached requests & URL
                h.isAuthFlow.value = false
                advanceUntilIdle()
                assertNull(vm.uiState.value.jellyseerrUrl)
                assertTrue(vm.uiState.value.requests.isEmpty())
            } finally {
                h.dispose()
            }
        }
}
