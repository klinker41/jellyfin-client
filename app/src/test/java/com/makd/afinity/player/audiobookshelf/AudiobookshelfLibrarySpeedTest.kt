package com.makd.afinity.player.audiobookshelf

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.makd.afinity.cast.CastManager
import com.makd.afinity.cast.CastSessionState
import com.makd.afinity.data.audiobooks.AudiobookDownloadStore
import com.makd.afinity.data.audiobooks.JellyfinAudiobookLoader
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.audiobookshelf.AudioTrack
import com.makd.afinity.data.models.audiobookshelf.PlaybackSession
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.media.AfinityAudiobook
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.player.ContinueListeningQueue
import com.makd.afinity.player.LibraryPlaybackSpeedStore
import com.makd.afinity.player.audiobook.JellyfinAudiobookReporter
import com.makd.afinity.player.audiobook.JellyfinAudiobookStarter
import com.makd.afinity.testutil.MainDispatcherRule
import com.makd.afinity.testutil.TestMediaFixtures.testCollection
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import com.makd.afinity.util.NetworkConnectivityMonitor
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AudiobookshelfLibrarySpeedTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    @get:Rule val tempFolder = TemporaryFolder()

    private inner class Harness(scope: TestScope) {
        val context: Context = ApplicationProvider.getApplicationContext()
        val dataStore =
            PreferenceDataStoreFactory.create(scope = scope.backgroundScope) {
                tempFolder.newFolder().resolve("abs_speed.preferences_pb")
            }
        val speedStore = LibraryPlaybackSpeedStore(dataStore, scope.backgroundScope)
        val playbackManager = AudiobookshelfPlaybackManager()
        val castStateFlow = MutableStateFlow(CastSessionState())
        val castManager: CastManager =
            mockk(relaxed = true) { every { castState } returns castStateFlow }
        val sessionManager: SessionManager =
            mockk(relaxed = true) { every { currentSession } returns MutableStateFlow(null) }
        val absRepository: AudiobookshelfRepository =
            mockk(relaxed = true) {
                every { isAuthenticated } returns MutableStateFlow(true)
                coEvery { getCachedItem(any()) } returns null
            }
        val player =
            AudiobookshelfPlayer(
                context = context,
                playbackManager = playbackManager,
                securePreferencesRepository = mockk(relaxed = true),
                audiobookshelfRepository = absRepository,
                sessionManager = sessionManager,
                absSyncScheduler = mockk(relaxed = true),
                musicPlaybackManager = mockk(relaxed = true),
                progressSyncer = mockk(relaxed = true),
                preferencesRepository = mockk(relaxed = true),
                networkConnectivityMonitor = mockk<NetworkConnectivityMonitor>(relaxed = true),
                castManager = dagger.Lazy { castManager },
                jellyfinReporter = mockk<JellyfinAudiobookReporter>(relaxed = true),
                libraryPlaybackSpeedStore = speedStore,
                continueListeningQueue = ContinueListeningQueue(),
            )
    }

    private fun testSession(id: String, libraryId: String, mediaType: String) =
        PlaybackSession(
            id = id,
            userId = "u1",
            libraryId = libraryId,
            libraryItemId = "item-$id",
            mediaType = mediaType,
            displayTitle = "Title $id",
            duration = 120.0,
            playMethod = 0,
            startTime = 0.0,
            currentTime = 0.0,
            startedAt = 1L,
            updatedAt = 2L,
            audioTracks =
                listOf(
                    AudioTrack(
                        index = 0,
                        startOffset = 0.0,
                        duration = 120.0,
                        contentUrl = "/audio.mp3",
                    ),
                ),
        )

    private fun testAudiobook(id: UUID, parentId: UUID?) =
        AfinityAudiobook(
            id = id,
            name = "Book",
            originalTitle = null,
            overview = "",
            played = false,
            favorite = false,
            liked = false,
            canPlay = true,
            canDownload = false,
            runtimeTicks = 600_000_000L,
            playbackPositionTicks = 0L,
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
            parentId = parentId,
            trackNumber = null,
            discNumber = null,
            container = "m4b",
        )

    @Test
    fun `ABS book and podcast libraries store and restore independent playback speeds`() = runTest {
        val h = Harness(this)

        val bookSession = testSession("s-book", "abs-lib-books", "book")
        val podcastSession = testSession("s-pod", "abs-lib-podcasts", "podcast")

        // Book library starts at 1.0x and user sets 1.25x
        val preparedBook1 = h.player.prepareSession(bookSession, "https://abs.example.com")!!
        assertEquals(1.0f, preparedBook1.playbackSpeed, 0.001f)
        h.player.setPlaybackSpeed(1.25f)
        advanceUntilIdle()
        assertEquals(1.25f, h.playbackManager.playbackState.value.playbackSpeed, 0.001f)

        // Switching to podcast library does NOT inherit book speed; starts at 1.0x and sets 1.8x
        val preparedPodcast = h.player.prepareSession(podcastSession, "https://abs.example.com")!!
        assertEquals(1.0f, preparedPodcast.playbackSpeed, 0.001f)
        assertEquals(1.0f, h.playbackManager.playbackState.value.playbackSpeed, 0.001f)
        h.player.setPlaybackSpeed(1.8f)
        advanceUntilIdle()

        // Returning to book library restores 1.25x even after clearSession()
        h.playbackManager.clearSession()
        assertEquals(1.0f, h.playbackManager.playbackState.value.playbackSpeed, 0.001f)
        val preparedBook2 = h.player.prepareSession(bookSession, "https://abs.example.com")!!
        assertEquals(1.25f, preparedBook2.playbackSpeed, 0.001f)
        assertEquals(1.25f, h.playbackManager.playbackState.value.playbackSpeed, 0.001f)
    }

    @Test
    fun `Jellyfin audiobook libraries store independent speeds and resolve via ancestors when nested`() =
        runTest {
            val h = Harness(this)
            val libA = testUuid(10)
            val libB = testUuid(11)
            val folderId = testUuid(12)
            val bookAId = testUuid(20)
            val bookBId = testUuid(21)

            val bookA = testAudiobook(bookAId, parentId = libA)
            val bookB = testAudiobook(bookBId, parentId = folderId)

            val loader: JellyfinAudiobookLoader = mockk {
                coEvery { loadBook(bookAId) } returns Result.success(bookA)
                coEvery { loadBook(bookBId) } returns Result.success(bookB)
            }
            val downloadStore: AudiobookDownloadStore = mockk {
                coEvery { localFile(any()) } returns null
            }
            val mediaRepository: MediaRepository =
                mockk(relaxed = true) {
                    every { getBaseUrl() } returns "https://jf.example.com"
                    every { libraries } returns
                        MutableStateFlow(
                            listOf(
                                testCollection(
                                    id = libA,
                                    name = "Books A",
                                    type = CollectionType.Books,
                                ),
                                testCollection(
                                    id = libB,
                                    name = "Books B",
                                    type = CollectionType.Books,
                                ),
                            ),
                        )
                    coEvery { getItemLibraryId(bookBId, setOf(libA, libB)) } returns libB
                }
            val spiedPlayer = spyk(h.player) { coEvery { connectController() } returns false }
            val starter =
                JellyfinAudiobookStarter(
                    loader = loader,
                    downloadStore = downloadStore,
                    mediaRepository = mediaRepository,
                    audiobookshelfPlayer = spiedPlayer,
                    libraryPlaybackSpeedStore = h.speedStore,
                )

            // Save different speeds for Jellyfin audiobook libraries A and B
            h.speedStore.setSpeed(LibraryPlaybackSpeedStore.jellyfinAudiobookKey(libA), 1.35f)
            h.speedStore.setSpeed(LibraryPlaybackSpeedStore.jellyfinAudiobookKey(libB), 1.6f)

            val preparedA = starter.prepareForResumption(bookAId.toString())!!
            assertEquals(1.35f, preparedA.playbackSpeed, 0.001f)
            assertEquals(1.35f, h.playbackManager.playbackState.value.playbackSpeed, 0.001f)

            val preparedB = starter.prepareForResumption(bookBId.toString())!!
            assertEquals(1.6f, preparedB.playbackSpeed, 0.001f)
            assertEquals(1.6f, h.playbackManager.playbackState.value.playbackSpeed, 0.001f)
        }
}
