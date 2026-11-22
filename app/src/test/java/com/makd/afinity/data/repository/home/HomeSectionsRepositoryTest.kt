package com.makd.afinity.data.repository.home

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.makd.afinity.data.manager.BackgroundWorkQueue
import com.makd.afinity.data.manager.Session
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.CustomHomeSection
import com.makd.afinity.data.models.CustomSectionSourceType
import com.makd.afinity.data.models.DiscoveryConfig
import com.makd.afinity.data.models.DiscoveryDensity
import com.makd.afinity.data.models.HomeRow
import com.makd.afinity.data.models.HomeSectionContent
import com.makd.afinity.data.models.HomeSectionType
import com.makd.afinity.data.models.PersonSection
import com.makd.afinity.data.models.PersonSectionType
import com.makd.afinity.data.models.PersonWithCount
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.media.AfinityStudio
import com.makd.afinity.data.repository.GenreRepository
import com.makd.afinity.data.repository.PeopleRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.franchise.FranchiseRepository
import com.makd.afinity.data.repository.media.LibraryIndexRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.testutil.FakeApiClient
import com.makd.afinity.testutil.MainDispatcherRule
import com.makd.afinity.testutil.TestMediaFixtures.testMovie
import com.makd.afinity.testutil.TestMediaFixtures.testPerson
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.PersonKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class HomeSectionsRepositoryTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private inner class Harness {
        val context: Context = ApplicationProvider.getApplicationContext()
        val userId = testUuid(1)
        val serverId = "srv-1"
        val baseUrl = "https://jf.example.com"
        val sessionFlow = MutableStateFlow<Session?>(Session(serverId, userId, baseUrl))
        val sessionManager: SessionManager =
            mockk(relaxed = true) { every { currentSession } returns sessionFlow }
        val mediaRepository: MediaRepository =
            mockk(relaxed = true) {
                every { getBaseUrl() } returns baseUrl
                coEvery { getFavoriteMovies(any()) } returns emptyList()
                coEvery {
                    getMovies(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
                } returns emptyList()
                coEvery {
                    getItems(
                        parentId = any(),
                        collectionTypes = any(),
                        sortBy = any(),
                        sortDescending = any(),
                        limit = any(),
                        startIndex = any(),
                        searchTerm = any(),
                        includeItemTypes = any(),
                        nameStartsWith = any(),
                        fields = any(),
                        imageTypes = any(),
                        recursive = any(),
                        criteria = any(),
                        enableTotalRecordCount = any(),
                        enableImageTypes = any(),
                    )
                } returns
                    FakeApiClient.decodeJson<BaseItemDtoQueryResult>(
                        """{"Items":[],"TotalRecordCount":0,"StartIndex":0}""",
                    )
                coEvery {
                    getItemsResult(
                        parentId = any(),
                        collectionTypes = any(),
                        sortBy = any(),
                        sortDescending = any(),
                        limit = any(),
                        startIndex = any(),
                        searchTerm = any(),
                        includeItemTypes = any(),
                        nameStartsWith = any(),
                        fields = any(),
                        imageTypes = any(),
                        recursive = any(),
                        criteria = any(),
                        enableTotalRecordCount = any(),
                        enableImageTypes = any(),
                        enableImages = any(),
                        enableUserData = any(),
                    )
                } returns
                    Result.success(
                        FakeApiClient.decodeJson<BaseItemDtoQueryResult>(
                            """{"Items":[],"TotalRecordCount":0,"StartIndex":0}""",
                        ),
                    )
                coEvery { getItemsByIds(any(), any()) } returns emptyList()
                coEvery { getStudios(any(), any(), any(), any(), any()) } returns emptyList()
                coEvery { getStudiosResult(any(), any(), any(), any(), any()) } returns
                    Result.success(emptyList())
                coEvery { getBoxSetsForSpotlight(any(), any()) } returns emptyList()
            }
        val peopleRepository: PeopleRepository =
            mockk(relaxed = true) {
                coEvery { getTopPeople(any(), any(), any(), any()) } returns emptyList()
            }
        val genreRepository: GenreRepository =
            mockk(relaxed = true) {
                every { combinedGenres } returns MutableStateFlow(emptyList())
                every { genreMovies } returns MutableStateFlow(emptyMap())
                every { genreShows } returns MutableStateFlow(emptyMap())
            }
        val homeCacheRepository: HomeCacheRepository =
            mockk(relaxed = true) {
                coEvery { getRaw(any()) } returns null
                coEvery { getRaw(any(), any()) } returns null
                coEvery { getRawStamped(any()) } returns null
                coEvery { getItems(any(), any(), any()) } returns null
                coEvery { getItems(any(), any()) } returns null
                coEvery { getItemsStamped(any(), any()) } returns null
            }
        val customSectionsFlow = MutableStateFlow<List<CustomHomeSection>>(emptyList())
        val customHomeSectionsRepository: CustomHomeSectionsRepository =
            mockk<CustomHomeSectionsRepository>(relaxed = true).also { repo ->
                every { repo.sections } returns customSectionsFlow
                coEvery { repo.get(any()) } coAnswers
                    {
                        val id = firstArg<String>()
                        customSectionsFlow.value.firstOrNull { it.id == id }
                    }
            }
        val hiddenRowsFlow = MutableStateFlow<Set<HomeRow>>(emptySet())
        val discoveryConfigFlow =
            MutableStateFlow(DiscoveryConfig(density = DiscoveryDensity.LIGHT))
        val homeLayoutPreferencesRepository: HomeLayoutPreferencesRepository =
            mockk(relaxed = true) {
                every { hiddenRows } returns hiddenRowsFlow
                every { discoveryConfig } returns discoveryConfigFlow
                coEvery { getHiddenRows() } answers { hiddenRowsFlow.value }
                coEvery { getDiscoveryConfig() } answers { discoveryConfigFlow.value }
            }
        val backgroundWorkQueue = BackgroundWorkQueue()
        val franchiseRepository: FranchiseRepository = mockk(relaxed = true)
        val libraryIndexRepository: LibraryIndexRepository =
            mockk(relaxed = true) {
                coEvery { index(any(), any()) } returns Result.success(emptyList())
            }
        val preferencesRepository: PreferencesRepository =
            mockk(relaxed = true) {
                every { getWikidataEnabledFlow(any(), any()) } returns MutableStateFlow(false)
                coEvery { getWikidataEnabled(any(), any()) } returns false
            }
        val okHttpClient: OkHttpClient = mockk(relaxed = true)
        val scope = CoroutineScope(SupervisorJob() + mainDispatcherRule.testDispatcher)

        fun createRepository(): HomeSectionsRepository =
            HomeSectionsRepository(
                context = context,
                mediaRepository = mediaRepository,
                peopleRepository = peopleRepository,
                genreRepository = genreRepository,
                sessionManager = sessionManager,
                homeCacheRepository = homeCacheRepository,
                customHomeSectionsRepository = customHomeSectionsRepository,
                homeLayoutPreferencesRepository = homeLayoutPreferencesRepository,
                backgroundWorkQueue = backgroundWorkQueue,
                franchiseRepository = franchiseRepository,
                libraryIndexRepository = libraryIndexRepository,
                preferencesRepository = preferencesRepository,
                okHttpClient = okHttpClient,
                scope = scope,
            )

        fun dispose() {
            scope.cancel()
        }
    }

    private suspend fun TestScope.awaitCondition(
        timeoutMs: Long = 2_000L,
        detail: () -> String = { "" },
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            withContext(Dispatchers.Default) { delay(10L) }
            runCurrent()
        }
        assertTrue(
            "Timed out waiting for HomeSectionsRepository condition (${detail()})",
            condition(),
        )
    }

    @Test
    fun `discovery layout stays empty when DiscoveryDensity is OFF and populates when enabled`() =
        runTest(timeout = 30.seconds) {
            val h = Harness()
            try {
                // Default DiscoveryDensity is OFF
                h.discoveryConfigFlow.value = DiscoveryConfig()
                assertTrue(DiscoveryConfig().isDiscoveryOff)

                val repo = h.createRepository()
                runCurrent()
                repo.ensureLayout(force = true)
                runCurrent()
                assertEquals(0, repo.layout.value.size)

                // Now enable discovery density with actors and studios
                val actor = testPerson(testUuid(10), "Keanu Reeves", PersonKind.ACTOR)
                val actorMovie = testMovie(testUuid(11), "Matrix")
                coEvery {
                    h.peopleRepository.getTopPeople(PersonKind.ACTOR, any(), any(), any())
                } returns listOf(PersonWithCount(actor, appearanceCount = 8))
                coEvery {
                    h.peopleRepository.getPersonSection(any(), PersonSectionType.STARRING, any())
                } returns
                    PersonSection(
                        person = actor,
                        appearanceCount = 8,
                        items = listOf(actorMovie),
                        sectionType = PersonSectionType.STARRING,
                    )
                coEvery {
                    h.mediaRepository.getPersonItems(
                        personId = actor.id,
                        includeItemTypes = any(),
                        fields = any(),
                    )
                } returns listOf(actorMovie)
                coEvery { h.mediaRepository.getStudios(any(), any(), any(), any(), any()) } returns
                    listOf(
                        AfinityStudio(testUuid(12), "Studio A", "https://img/a.jpg", 10),
                        AfinityStudio(testUuid(13), "Studio B", "https://img/b.jpg", 9),
                        AfinityStudio(testUuid(14), "Studio C", "https://img/c.jpg", 8),
                    )

                h.discoveryConfigFlow.value = DiscoveryConfig(density = DiscoveryDensity.LIGHT)
                runCurrent()
                repo.ensureLayout(force = true)
                awaitCondition(detail = { "layout=${repo.layout.value.map { it.type }}" }) {
                    runCurrent()
                    repo.layout.value.any { it.type == HomeSectionType.STARRING }
                }

                val starringKey =
                    repo.layout.value.first { it.type == HomeSectionType.STARRING }.key
                repo.onSectionVisible(starringKey)
                awaitCondition { repo.content.value[starringKey] is HomeSectionContent.Person }

                val hydrated = repo.content.value[starringKey] as HomeSectionContent.Person
                assertEquals(actorMovie.id, hydrated.section.items.first().id)

                repo.removeItem(actorMovie.id.toString())
                runCurrent()
                val afterRemoval = repo.content.value[starringKey] as HomeSectionContent.Person
                assertTrue(afterRemoval.section.items.isEmpty())
            } finally {
                h.dispose()
            }
        }

    @Test
    fun `custom home sections pin, hydrate via MediaRepository, and clear on clearAllData`() =
        runTest(timeout = 30.seconds) {
            val h = Harness()
            try {
                val repo = h.createRepository()
                val movie1Id = testUuid(40)
                val movie2Id = testUuid(41)
                val customSection =
                    CustomHomeSection(
                        id = "custom-1",
                        position = 0,
                        title = "Genre Pick",
                        sourceType = CustomSectionSourceType.GENRE,
                        sourceValues = listOf("Sci-Fi"),
                        includeItemTypes = listOf("MOVIE"),
                        itemLimit = 10,
                        sortBy = SortBy.NAME,
                        enabled = true,
                    )

                coEvery {
                    h.mediaRepository.getItems(
                        parentId = any(),
                        collectionTypes = any(),
                        sortBy = any(),
                        sortDescending = any(),
                        limit = any(),
                        startIndex = any(),
                        searchTerm = any(),
                        includeItemTypes = any(),
                        nameStartsWith = any(),
                        fields = any(),
                        imageTypes = any(),
                        recursive = any(),
                        criteria = any(),
                        enableTotalRecordCount = any(),
                        enableImageTypes = any(),
                    )
                } returns
                    FakeApiClient.decodeJson<BaseItemDtoQueryResult>(
                        """{
                            "Items":[
                                {"Id":"$movie1Id","Name":"Sci-Fi Classic","Type":"Movie","IsFolder":false,"UserData":{"Key":"$movie1Id","ItemId":"$movie1Id","PlaybackPositionTicks":0,"PlayCount":0,"IsFavorite":false,"Played":false}},
                                {"Id":"$movie2Id","Name":"Action Hit","Type":"Movie","IsFolder":false,"UserData":{"Key":"$movie2Id","ItemId":"$movie2Id","PlaybackPositionTicks":0,"PlayCount":0,"IsFavorite":false,"Played":false}}
                            ],
                            "TotalRecordCount":2,
                            "StartIndex":0
                        }""",
                    )

                h.customSectionsFlow.value = listOf(customSection)
                runCurrent()
                awaitCondition { repo.pinnedLayout.value.size == 1 }

                val pinnedKey = repo.pinnedLayout.value.first().key
                repo.onSectionVisible(pinnedKey)
                awaitCondition { repo.content.value[pinnedKey] is HomeSectionContent.Items }

                val content = repo.content.value[pinnedKey] as HomeSectionContent.Items
                assertEquals(2, content.items.size)

                val movie1Updated = testMovie(movie1Id, "Sci-Fi Remastered")
                repo.updateItem(movie1Updated)
                runCurrent()
                val updated = repo.content.value[pinnedKey] as HomeSectionContent.Items
                assertEquals("Sci-Fi Remastered", updated.items.first { it.id == movie1Id }.name)

                repo.clearAllData()
                runCurrent()
                assertTrue(repo.layout.value.isEmpty())
                assertTrue(repo.pinnedLayout.value.isEmpty())
                assertTrue(repo.content.value.isEmpty())
            } finally {
                h.dispose()
            }
        }
}
