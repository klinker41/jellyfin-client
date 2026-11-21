package com.makd.afinity.ui.settings.update

import com.makd.afinity.data.updater.GitHubApiService
import com.makd.afinity.data.updater.models.GitHubRelease
import com.makd.afinity.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CurrentChangelogViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadEmitsLoadedNotPublishedAndFailedStates() =
        runTest(mainDispatcherRule.testDispatcher) {
            val api: GitHubApiService = mockk()
            val release =
                GitHubRelease(
                    tagName = "v1.0.0",
                    name = "v1.0.0",
                    body = "Notes",
                    htmlUrl = "https://example.com",
                    publishedAt = "2026-11-21T00:00:00Z",
                    assets = emptyList(),
                )
            coEvery { api.getReleaseByTag(any()) } returns Result.success(release)

            val loadedVm = CurrentChangelogViewModel(api)
            loadedVm.load()
            advanceUntilIdle()
            assertEquals(CurrentChangelogState.Loaded(release), loadedVm.state.value)

            coEvery { api.getReleaseByTag(any()) } returns Result.success(null)
            val missingVm = CurrentChangelogViewModel(api)
            missingVm.load()
            advanceUntilIdle()
            assertTrue(missingVm.state.value is CurrentChangelogState.NotPublished)

            coEvery { api.getReleaseByTag(any()) } returns
                Result.failure(RuntimeException("network"))
            val failedVm = CurrentChangelogViewModel(api)
            failedVm.load()
            advanceUntilIdle()
            assertTrue(failedVm.state.value is CurrentChangelogState.Failed)
        }
}
