package com.makd.afinity.ui.settings.downloads

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.makd.afinity.data.autodownload.AutoDownloadManager
import com.makd.afinity.data.autodownload.AutoDownloadPreferences
import com.makd.afinity.data.manager.Session
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.testutil.MainDispatcherRule
import com.makd.afinity.testutil.TestMediaFixtures.testCollection
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class AutoDownloadSettingsViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    @get:Rule val tempFolder = TemporaryFolder()

    @Test
    fun `uiState combines libraries, enabled flags, podcast tag, canDownload, and syncing state`() =
        runTest {
            val serverId = "srv-1"
            val userId = testUuid(1)
            val audiobooksId = testUuid(10)
            val podcastsId = testUuid(11)
            val liveTvId = testUuid(12)

            val sessionFlow =
                MutableStateFlow<Session?>(
                    Session(serverId, userId, "https://jf.example.com", canDownload = true),
                )
            val sessionManager: SessionManager =
                mockk(relaxed = true) { every { currentSession } returns sessionFlow }

            val librariesFlow =
                MutableStateFlow(
                    listOf(
                        testCollection(audiobooksId, "Audiobooks", CollectionType.Books),
                        testCollection(podcastsId, "Podcasts", CollectionType.Music),
                        testCollection(liveTvId, "Live TV", CollectionType.LiveTv),
                    ),
                )
            val mediaRepository: MediaRepository =
                mockk(relaxed = true) { every { libraries } returns librariesFlow }

            val dataStore =
                PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { File(tempFolder.root, "vm_prefs.preferences_pb") },
                )
            val prefs = AutoDownloadPreferences(dataStore)
            prefs.setLibraryEnabled(serverId, userId, podcastsId, enabled = true)

            val isSyncingFlow = MutableStateFlow(false)
            val autoDownloadManager: AutoDownloadManager =
                mockk(relaxed = true) {
                    every { isSyncing } returns isSyncingFlow
                    coEvery { setLibraryEnabled(any(), any()) } returns Unit
                    coEvery { syncEnabledLibraries() } returns true
                }

            val viewModel =
                AutoDownloadSettingsViewModel(
                    sessionManager = sessionManager,
                    mediaRepository = mediaRepository,
                    autoDownloadPreferences = prefs,
                    autoDownloadManager = autoDownloadManager,
                )

            viewModel.uiState.test {
                var state = awaitItem()
                if (state.libraries.isEmpty()) state = awaitItem()

                assertTrue(state.canDownload)
                assertFalse(state.isSyncing)
                assertEquals(2, state.libraries.size)
                assertEquals(
                    AutoDownloadLibraryOption(
                        id = audiobooksId,
                        name = "Audiobooks",
                        type = CollectionType.Books,
                        isPodcast = false,
                        enabled = false,
                    ),
                    state.libraries[0],
                )
                assertEquals(
                    AutoDownloadLibraryOption(
                        id = podcastsId,
                        name = "Podcasts",
                        type = CollectionType.Music,
                        isPodcast = true,
                        enabled = true,
                    ),
                    state.libraries[1],
                )

                isSyncingFlow.value = true
                assertTrue(awaitItem().isSyncing)

                sessionFlow.value =
                    Session(serverId, userId, "https://jf.example.com", canDownload = false)
                assertFalse(awaitItem().canDownload)
            }

            viewModel.toggleLibrary(audiobooksId, true)
            viewModel.syncNow()
            advanceUntilIdle()

            coVerify(exactly = 1) { autoDownloadManager.setLibraryEnabled(audiobooksId, true) }
            coVerify(exactly = 1) { autoDownloadManager.syncEnabledLibraries() }
        }
}
