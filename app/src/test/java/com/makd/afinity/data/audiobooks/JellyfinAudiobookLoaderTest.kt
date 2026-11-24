package com.makd.afinity.data.audiobooks

import android.app.Application
import com.makd.afinity.data.manager.Session
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.media.AfinityAudiobook
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.user.AfinityUserDataDto
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class JellyfinAudiobookLoaderTest {

    @Test
    fun `online load overlays unsynced local userData and ignores already synced rows`() = runTest {
        val userId = testUuid(1)
        val serverId = "srv-1"
        val partId = testUuid(10)
        val syncedPartId = testUuid(11)

        val sessionManager: SessionManager =
            mockk(relaxed = true) {
                every { currentSession } returns
                    MutableStateFlow(Session(serverId, userId, "https://jf.example.com"))
            }
        val mediaRepository: MediaRepository =
            mockk(relaxed = true) { every { getBaseUrl() } returns "https://jf.example.com" }
        val downloadStore: AudiobookDownloadStore = mockk(relaxed = true)
        val databaseRepository: DatabaseRepository = mockk(relaxed = true)

        coEvery { mediaRepository.getItemResult(partId, any()) } returns
            Result.success(
                BaseItemDto(
                    id = partId,
                    type = BaseItemKind.AUDIO_BOOK,
                    name = "Part 1",
                    runTimeTicks = 36_000_000_000L,
                    userData =
                        UserItemDataDto(
                            playbackPositionTicks = 1_000_000_000L,
                            playCount = 0,
                            isFavorite = false,
                            played = false,
                            key = partId.toString(),
                            itemId = partId,
                        ),
                ),
            )
        coEvery { databaseRepository.getUserData(userId, partId) } returns
            AfinityUserDataDto(
                userId = userId,
                itemId = partId,
                serverId = serverId,
                played = false,
                favorite = false,
                likes = false,
                playbackPositionTicks = 8_000_000_000L,
                toBeSynced = true,
            )

        val loader =
            JellyfinAudiobookLoader(
                mediaRepository = mediaRepository,
                downloadStore = downloadStore,
                sessionManager = sessionManager,
                databaseRepository = databaseRepository,
            )

        val pendingBook = loader.loadBook(partId).getOrThrow()!!
        assertEquals(8_000_000_000L, pendingBook.playbackPositionTicks)
        assertFalse(pendingBook.played)

        coEvery { mediaRepository.getItemResult(syncedPartId, any()) } returns
            Result.success(
                BaseItemDto(
                    id = syncedPartId,
                    type = BaseItemKind.AUDIO_BOOK,
                    name = "Part 2",
                    runTimeTicks = 36_000_000_000L,
                    userData =
                        UserItemDataDto(
                            playbackPositionTicks = 5_000_000_000L,
                            playCount = 0,
                            isFavorite = false,
                            played = false,
                            key = syncedPartId.toString(),
                            itemId = syncedPartId,
                        ),
                ),
            )
        coEvery { databaseRepository.getUserData(userId, syncedPartId) } returns
            AfinityUserDataDto(
                userId = userId,
                itemId = syncedPartId,
                serverId = serverId,
                played = true,
                favorite = false,
                likes = false,
                playbackPositionTicks = 2_000_000_000L,
                toBeSynced = false,
            )

        val syncedBook = loader.loadBook(syncedPartId).getOrThrow()!!
        assertEquals(5_000_000_000L, syncedBook.playbackPositionTicks)
        assertFalse(syncedBook.played)
    }

    @Test
    fun `falls back to downloaded parts when online request fails`() = runTest {
        val partId = testUuid(20)
        val offlinePart =
            AfinityAudiobook(
                id = partId,
                name = "Offline Book",
                originalTitle = null,
                overview = "",
                played = false,
                favorite = false,
                liked = false,
                canPlay = true,
                canDownload = true,
                runtimeTicks = 10_000_000_000L,
                playbackPositionTicks = 4_000_000_000L,
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
                trackNumber = null,
                discNumber = null,
                container = "m4b",
            )
        val mediaRepository: MediaRepository =
            mockk(relaxed = true) {
                every { getBaseUrl() } returns "https://jf.example.com"
                coEvery { getItemResult(partId, any()) } returns
                    Result.failure(RuntimeException("offline"))
            }
        val downloadStore: AudiobookDownloadStore =
            mockk(relaxed = true) {
                coEvery { downloadedBookParts(partId) } returns listOf(offlinePart)
            }
        val loader =
            JellyfinAudiobookLoader(
                mediaRepository = mediaRepository,
                downloadStore = downloadStore,
                sessionManager = mockk(relaxed = true),
                databaseRepository = mockk(relaxed = true),
            )

        val loaded = loader.loadBook(partId).getOrThrow()!!
        assertEquals(partId, loaded.id)
        assertEquals(4_000_000_000L, loaded.playbackPositionTicks)
    }
}
