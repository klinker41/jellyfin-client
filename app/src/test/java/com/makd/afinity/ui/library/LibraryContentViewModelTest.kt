package com.makd.afinity.ui.library

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.makd.afinity.data.manager.AdminChange
import com.makd.afinity.data.manager.AdminChangeBroadcaster
import com.makd.afinity.data.manager.DownloadPermissions
import com.makd.afinity.data.manager.ItemDeletedEvent
import com.makd.afinity.data.manager.LibraryContentChangeEvent
import com.makd.afinity.data.manager.MediaChangeManager
import com.makd.afinity.data.models.CustomHomeSection
import com.makd.afinity.data.models.CustomSectionSourceType
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.media.AfinitySource
import com.makd.afinity.data.models.media.AfinityVideo
import com.makd.afinity.data.models.media.LibraryFilterOptions
import com.makd.afinity.data.models.media.LibraryFilters
import com.makd.afinity.data.models.user.AfinityUserDataDto
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.repository.home.CustomHomeSectionsRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.data.store.ItemStore
import com.makd.afinity.testutil.MainDispatcherRule
import com.makd.afinity.testutil.TestMediaFixtures.testCollection
import com.makd.afinity.testutil.TestMediaFixtures.testEpisode
import com.makd.afinity.testutil.TestMediaFixtures.testSource
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import com.makd.afinity.ui.item.delegates.ItemUserDataDelegate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryContentViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private class Harness(
        savedArgs: Map<String, Any?>,
        libraries: List<com.makd.afinity.data.models.media.AfinityCollection> = emptyList(),
        customSection: CustomHomeSection? = null,
    ) {
        val isInitialLoadedFlow = MutableStateFlow(false)
        val userProfileUrlFlow = MutableStateFlow<String?>(null)
        val userNameFlow = MutableStateFlow<String?>("Alex")
        val lastResyncAtFlow = MutableStateFlow(0L)
        val userDataOverlayFlow = MutableStateFlow<Map<UUID, AfinityUserDataDto>>(emptyMap())
        val adminChangesFlow = MutableSharedFlow<AdminChange>(extraBufferCapacity = 4)
        val itemDeletedFlow = MutableSharedFlow<ItemDeletedEvent>(extraBufferCapacity = 4)
        val itemsRemovedFlow = MutableSharedFlow<List<String>>(extraBufferCapacity = 4)
        val itemsAddedFlow = MutableSharedFlow<List<String>>(extraBufferCapacity = 4)
        val libraryChangesFlow =
            MutableSharedFlow<LibraryContentChangeEvent>(extraBufferCapacity = 4)
        val mediaChangesFlow =
            MutableSharedFlow<com.makd.afinity.data.manager.MediaChangeEvent>(
                extraBufferCapacity = 4,
            )

        val storedPrefs = mutableMapOf<String, String?>()
        val itemStore = ItemStore()

        val context: Context =
            mockk(relaxed = true) { every { getString(any()) } returns "Content unavailable" }
        val mediaRepository: MediaRepository =
            mockk(relaxed = true) {
                coEvery { getLibraries() } returns libraries
                coEvery {
                    getItemsPaging(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                    )
                } returns emptyFlow()
                coEvery { getFilterOptions(any(), any(), any()) } returns
                    LibraryFilterOptions(genres = listOf("Sci-Fi"), years = listOf(2024))
            }
        val appDataRepository: AppDataRepository =
            mockk(relaxed = true) {
                every { isInitialDataLoaded } returns isInitialLoadedFlow
                every { userProfileImageUrl } returns userProfileUrlFlow
                every { userName } returns userNameFlow
                every { lastResyncAt } returns lastResyncAtFlow
                every { userDataOverlay } returns userDataOverlayFlow
            }
        val adminChangeBroadcaster: AdminChangeBroadcaster = mockk {
            every { changes } returns adminChangesFlow
            every { itemDeleted } returns itemDeletedFlow
        }
        val mediaChangeManager: MediaChangeManager = mockk {
            every { itemsRemoved } returns itemsRemovedFlow
            every { itemsAdded } returns itemsAddedFlow
            every { libraryContentChanges } returns libraryChangesFlow
            every { mediaChanges } returns mediaChangesFlow
        }
        val preferencesRepository: PreferencesRepository =
            mockk(relaxed = true) {
                coEvery { getDefaultSortBy() } returns SortBy.NAME
                coEvery { getSortDescending() } returns false
                coEvery { getStringPreference(any()) } answers { storedPrefs[firstArg()] }
                coEvery { setStringPreference(any(), any()) } answers
                    {
                        storedPrefs[firstArg()] = secondArg()
                    }
            }
        val customHomeSectionsRepository: CustomHomeSectionsRepository = mockk {
            coEvery { this@mockk.get(any()) } returns customSection
        }
        val downloadRepository: DownloadRepository =
            mockk(relaxed = true) { coEvery { getDownloadByItemId(any()) } returns null }
        val userDataRepository: UserDataRepository = mockk(relaxed = true)
        val itemUserDataDelegate: ItemUserDataDelegate = mockk(relaxed = true)
        val downloadPermissions: DownloadPermissions = mockk {
            every { isAllowedByServer } returns MutableStateFlow(true)
            every { isAllowedOnNetwork } returns MutableStateFlow(true)
        }

        val viewModel =
            LibraryContentViewModel(
                context = context,
                mediaRepository = mediaRepository,
                appDataRepository = appDataRepository,
                adminChangeBroadcaster = adminChangeBroadcaster,
                mediaChangeManager = mediaChangeManager,
                preferencesRepository = preferencesRepository,
                customHomeSectionsRepository = customHomeSectionsRepository,
                downloadRepository = downloadRepository,
                userDataRepository = userDataRepository,
                itemUserDataDelegate = itemUserDataDelegate,
                downloadPermissions = downloadPermissions,
                itemStore = itemStore,
                savedStateHandle = SavedStateHandle(savedArgs),
            )
    }

    @Test
    fun `loads library content and persists sort and filter updates`() = runTest {
        val libId = testUuid(10)
        val harness =
            Harness(
                savedArgs =
                    mapOf("libraryId" to libId.toString(), "libraryName" to "Sci-Fi%2FFilms"),
                libraries =
                    listOf(
                        testCollection(id = libId, name = "Movies", type = CollectionType.Movies),
                    ),
            )

        assertEquals("Sci-Fi/Films", harness.viewModel.uiState.value.libraryName)

        harness.isInitialLoadedFlow.value = true
        advanceUntilIdle()

        assertEquals(CollectionType.Movies, harness.viewModel.uiState.value.libraryType)
        assertFalse(harness.viewModel.uiState.value.isLoading)
        assertFalse(harness.viewModel.uiState.value.filtersLocked)

        val newFilters = LibraryFilters(played = true, genres = setOf("Sci-Fi"))
        harness.viewModel.updateFilters(newFilters)
        harness.viewModel.updateSort(SortBy.RELEASE_DATE, descending = true)
        advanceUntilIdle()

        assertEquals(newFilters, harness.viewModel.uiState.value.currentFilters)
        assertEquals(SortBy.RELEASE_DATE, harness.viewModel.uiState.value.currentSortBy)
        assertTrue(harness.viewModel.uiState.value.currentSortDescending)
        assertTrue(harness.storedPrefs.containsKey("library_filters_$libId"))
        assertTrue(harness.storedPrefs.containsKey("library_sort_$libId"))
    }

    @Test
    fun `custom section locks filters and missing section sets error`() = runTest {
        val libId = testUuid(11)
        val section =
            CustomHomeSection(
                id = "sec-1",
                position = 0,
                title = "Top Comedy",
                sourceType = CustomSectionSourceType.GENRE,
                sourceValues = listOf("Comedy"),
                includeItemTypes = listOf("MOVIE"),
                sortBy = SortBy.IMDB_RATING,
                sortDescending = true,
                filters = LibraryFilters(unplayed = true),
            )
        val harness =
            Harness(
                savedArgs = mapOf("sectionId" to "sec-1", "libraryId" to libId.toString()),
                customSection = section,
            )

        harness.isInitialLoadedFlow.value = true
        advanceUntilIdle()

        val state = harness.viewModel.uiState.value
        assertEquals("Top Comedy", state.libraryName)
        assertTrue(state.filtersLocked)
        assertEquals(setOf("Comedy"), state.currentFilters.genres)
        assertEquals(SortBy.IMDB_RATING, state.currentSortBy)

        harness.viewModel.updateFilters(LibraryFilters(favorites = true))
        advanceUntilIdle()
        assertEquals(setOf("Comedy"), harness.viewModel.uiState.value.currentFilters.genres)

        val missingHarness =
            Harness(savedArgs = mapOf("sectionId" to "missing"), customSection = null)
        missingHarness.isInitialLoadedFlow.value = true
        advanceUntilIdle()
        assertEquals("Content unavailable", missingHarness.viewModel.uiState.value.error)
    }

    @Test
    fun `alphabet scroll toggles and filter options load once`() = runTest {
        val libId = testUuid(12)
        val harness =
            Harness(
                savedArgs = mapOf("libraryId" to libId.toString(), "libraryName" to "Movies"),
                libraries =
                    listOf(
                        testCollection(id = libId, name = "Movies", type = CollectionType.Movies),
                    ),
            )
        harness.isInitialLoadedFlow.value = true
        advanceUntilIdle()

        harness.viewModel.ensureFilterOptionsLoaded()
        advanceUntilIdle()
        assertEquals(listOf("Sci-Fi"), harness.viewModel.uiState.value.filterOptions.genres)

        harness.viewModel.ensureFilterOptionsLoaded()
        advanceUntilIdle()
        coVerify(exactly = 1) { harness.mediaRepository.getFilterOptions(any(), any(), any()) }

        harness.viewModel.scrollToLetter("#")
        advanceUntilIdle()
        assertEquals("#", harness.viewModel.uiState.value.selectedLetter)

        harness.viewModel.scrollToLetter("#")
        advanceUntilIdle()
        assertNull(harness.viewModel.uiState.value.selectedLetter)

        harness.viewModel.selectEpisode(testEpisode())
        advanceUntilIdle()
        assertEquals(testEpisode().id, harness.viewModel.selectedEpisode.value?.id)
        harness.viewModel.clearSelectedEpisode()
        assertNull(harness.viewModel.selectedEpisode.value)
    }

    @Test
    fun `resolveVideoPlayback uses detailed item first source`() = runTest {
        val libId = testUuid(13)
        val harness = Harness(savedArgs = mapOf("libraryId" to libId.toString()))
        val videoId = testUuid(99)
        fun makeVideo(sources: List<AfinitySource>, pos: Long) =
            AfinityVideo(
                id = videoId,
                name = "Clip",
                originalTitle = null,
                overview = "",
                played = false,
                favorite = false,
                liked = false,
                canPlay = true,
                canDownload = true,
                sources = sources,
                runtimeTicks = 1000L,
                playbackPositionTicks = pos,
                unplayedItemCount = null,
                images = AfinityImages(),
                chapters = emptyList(),
                providerIds = null,
                externalUrls = null,
                premiereDate = null,
                people = emptyList(),
                genres = emptyList(),
                communityRating = null,
                officialRating = null,
                criticRating = null,
                status = "Released",
                productionYear = 2024,
                endDate = null,
                trailer = null,
                tagline = null,
                trickplayInfo = null,
                extraType = null,
            )
        val baseVideo = makeVideo(emptyList(), 50_000L)
        val detailedVideo = makeVideo(listOf(testSource(id = "src-detailed")), 120_000L)
        coEvery { harness.mediaRepository.getItemById(videoId) } returns detailedVideo

        var resolvedId: String? = null
        var resolvedPos = -1L
        harness.viewModel.resolveVideoPlayback(baseVideo) { id, posMs ->
            resolvedId = id
            resolvedPos = posMs
        }
        advanceUntilIdle()

        assertEquals("src-detailed", resolvedId)
        assertEquals(12L, resolvedPos)
    }
}
