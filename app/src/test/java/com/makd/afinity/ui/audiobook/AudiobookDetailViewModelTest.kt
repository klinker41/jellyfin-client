package com.makd.afinity.ui.audiobook

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.makd.afinity.data.audiobooks.JellyfinAudiobookLoader
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.media.AfinityAudiobook
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.store.ItemStore
import com.makd.afinity.testutil.MainDispatcherRule
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AudiobookDetailViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private fun testAudiobook(id: UUID, positionTicks: Long): AfinityAudiobook =
        AfinityAudiobook(
            id = id,
            name = "Book",
            originalTitle = null,
            overview = "",
            played = false,
            favorite = false,
            liked = false,
            canPlay = true,
            canDownload = true,
            runtimeTicks = 36_000_000_000L,
            playbackPositionTicks = positionTicks,
            unplayedItemCount = null,
            images = AfinityImages(),
            providerIds = null,
            externalUrls = null,
            album = null,
            people = emptyList(),
            publisher = null,
            premiereDate = null,
            productionYear = null,
            genres = emptyList(),
            communityRating = null,
            dateCreated = null,
            parentId = null,
            trackNumber = 1,
            discNumber = 1,
            container = "m4b",
        )

    @Test
    fun `pre-seeded stale ItemStore entry does not clobber loaded audiobook progress`() = runTest {
        val bookId = testUuid(401)
        val itemStore = ItemStore()
        itemStore.put(testAudiobook(bookId, positionTicks = 400_000_000L))

        val loader: JellyfinAudiobookLoader = mockk {
            coEvery { loadParts(bookId, offline = false) } returns
                Result.success(listOf(testAudiobook(bookId, positionTicks = 10_800_000_000L)))
        }
        val vm =
            AudiobookDetailViewModel(
                audiobookLoader = loader,
                userDataRepository = mockk(relaxed = true),
                itemUserDataDelegate = mockk(relaxed = true),
                mediaChangeManager = mockk(relaxed = true),
                itemStore = itemStore,
                downloadRepository =
                    mockk(relaxed = true) {
                        every { getAllDownloadsFlow() } returns
                            MutableStateFlow<List<DownloadInfo>>(emptyList())
                    },
                offlineModeManager =
                    mockk(relaxed = true) {
                        every { isOffline } returns MutableStateFlow(false)
                        coEvery { isCurrentlyOffline() } returns false
                    },
                downloadPermissions =
                    mockk(relaxed = true) {
                        every { isAllowedByServer } returns MutableStateFlow(true)
                        every { isAllowedOnNetwork } returns MutableStateFlow(true)
                    },
                savedStateHandle = SavedStateHandle(mapOf("itemId" to bookId.toString())),
            )

        advanceUntilIdle()

        assertEquals(10_800_000_000L, vm.uiState.value.book?.playbackPositionTicks)
        assertEquals(
            10_800_000_000L,
            itemStore.getAs<AfinityAudiobook>(bookId)?.playbackPositionTicks,
        )
    }
}
