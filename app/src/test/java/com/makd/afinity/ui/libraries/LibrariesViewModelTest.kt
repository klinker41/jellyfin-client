package com.makd.afinity.ui.libraries

import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.testutil.MainDispatcherRule
import com.makd.afinity.testutil.TestMediaFixtures.testCollection
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibrariesViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `collects libraries updates into uiState`() = runTest {
        val movies = testCollection(id = testUuid(1), name = "Movies", type = CollectionType.Movies)
        val shows = testCollection(id = testUuid(2), name = "Shows", type = CollectionType.TvShows)
        val librariesFlow = MutableStateFlow(listOf(movies))
        val appDataRepository: AppDataRepository = mockk {
            every { libraries } returns librariesFlow
        }

        val viewModel = LibrariesViewModel(appDataRepository)
        advanceUntilIdle()

        assertEquals(listOf(movies), viewModel.uiState.value.libraries)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)

        librariesFlow.value = listOf(movies, shows)
        advanceUntilIdle()

        assertEquals(listOf(movies, shows), viewModel.uiState.value.libraries)

        viewModel.onLibraryClick(shows)
        assertEquals(listOf(movies, shows), viewModel.uiState.value.libraries)
    }
}
