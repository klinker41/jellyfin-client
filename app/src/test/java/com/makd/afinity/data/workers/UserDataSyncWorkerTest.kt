package com.makd.afinity.data.workers

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.makd.afinity.data.manager.MediaRefreshBus
import com.makd.afinity.data.manager.RefreshTrigger
import com.makd.afinity.data.manager.Session
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.models.user.AfinityUserDataDto
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.data.repository.ServerUserToken
import com.makd.afinity.data.store.ItemStore
import com.makd.afinity.testutil.FakeApiClient
import com.makd.afinity.testutil.TestMediaFixtures.testTrack
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class UserDataSyncWorkerTest {

    private inner class Harness {
        val userId = testUuid(1)
        val serverId = "srv-1"
        val apiClient = FakeApiClient()
        val sessionFlow =
            MutableStateFlow<Session?>(Session(serverId, userId, "https://jf.example.com"))
        val sessionManager: SessionManager =
            mockk(relaxed = true) {
                every { currentSession } returns sessionFlow
                coEvery { getDetachedApiClient(serverId, userId) } returns apiClient
            }
        val databaseRepository: DatabaseRepository = mockk(relaxed = true)
        val securePreferencesRepository: SecurePreferencesRepository =
            mockk(relaxed = true) {
                coEvery { getAllServerUserTokens() } returns
                    listOf(
                        ServerUserToken(
                            serverId = serverId,
                            userId = userId,
                            accessToken = "tok-1",
                            serverUrl = "https://jf.example.com",
                            username = "tester",
                        ),
                    )
            }
        val preferencesRepository: PreferencesRepository =
            mockk(relaxed = true) { coEvery { getOfflineMode() } returns false }
        val itemStore = ItemStore()
        val mediaRefreshBus = MediaRefreshBus()

        fun buildWorker(): UserDataSyncWorker {
            val context: Context = ApplicationProvider.getApplicationContext()
            val factory =
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker =
                        UserDataSyncWorker(
                            appContext = appContext,
                            workerParams = workerParameters,
                            sessionManager = sessionManager,
                            databaseRepository = databaseRepository,
                            securePreferencesRepository = securePreferencesRepository,
                            preferencesRepository = preferencesRepository,
                            itemStore = itemStore,
                            mediaRefreshBus = mediaRefreshBus,
                        )
                }
            return TestListenableWorkerBuilder<UserDataSyncWorker>(context)
                .setWorkerFactory(factory)
                .build()
        }
    }

    @Test
    fun `successful upload marks row synced, updates ItemStore, and emits USER_DATA_CHANGED`() =
        runTest {
            val h = Harness()
            val itemId = testUuid(10)
            h.itemStore.put(testTrack(id = itemId, playbackPositionTicks = 400_000_000L))
            val pendingRow =
                AfinityUserDataDto(
                    userId = h.userId,
                    itemId = itemId,
                    serverId = h.serverId,
                    played = false,
                    favorite = false,
                    likes = false,
                    playbackPositionTicks = 10_800_000_000L,
                    toBeSynced = true,
                    lastPlayedAt = 1_795_500_000_000L,
                )
            coEvery { h.databaseRepository.getAllUserDataToSync(h.userId, h.serverId) } returns
                listOf(pendingRow)
            coEvery { h.databaseRepository.getOfflinePlays(h.userId, h.serverId) } returns
                emptyList()
            coEvery { h.databaseRepository.getUserData(h.userId, itemId, h.serverId) } returns
                pendingRow
            coEvery {
                h.databaseRepository.markUserDataSyncedIfUnchanged(
                    userId = h.userId,
                    itemId = itemId,
                    serverId = h.serverId,
                    positionTicks = pendingRow.playbackPositionTicks,
                    played = pendingRow.played,
                    lastPlayedAt = pendingRow.lastPlayedAt,
                )
            } returns 1
            h.apiClient.onPost("/UserItems/{itemId}/UserData") { req ->
                val id = req.pathParameters["itemId"] as UUID
                FakeApiClient.rawJsonResponse(
                    """{"ItemId":"$id","Key":"$id","Played":false,"PlaybackPositionTicks":10800000000,"PlayCount":0,"IsFavorite":false}""",
                )
            }

            var refreshEvent: RefreshTrigger? = null
            val collectJob = launch { refreshEvent = h.mediaRefreshBus.events.first() }
            advanceUntilIdle()

            val result = h.buildWorker().doWork()
            advanceUntilIdle()

            assertEquals(
                ListenableWorker.Result.success(
                    androidx.work.workDataOf("synced_count" to 1, "failed_count" to 0),
                ),
                result,
            )
            coVerify(exactly = 1) {
                h.databaseRepository.markUserDataSyncedIfUnchanged(
                    userId = h.userId,
                    itemId = itemId,
                    serverId = h.serverId,
                    positionTicks = pendingRow.playbackPositionTicks,
                    played = pendingRow.played,
                    lastPlayedAt = pendingRow.lastPlayedAt,
                )
            }
            assertEquals(
                10_800_000_000L,
                h.itemStore.getAs<AfinityTrack>(itemId)?.playbackPositionTicks,
            )
            assertEquals(RefreshTrigger.USER_DATA_CHANGED, refreshEvent)
            collectJob.cancel()
        }

    @Test
    fun `skips upload when row was already synced concurrently before upload`() = runTest {
        val h = Harness()
        val itemId = testUuid(11)
        val initialSnapshot =
            AfinityUserDataDto(
                userId = h.userId,
                itemId = itemId,
                serverId = h.serverId,
                played = false,
                favorite = false,
                likes = false,
                playbackPositionTicks = 400_000_000L,
                toBeSynced = true,
                lastPlayedAt = 1_795_500_000_000L,
            )
        val alreadySynced =
            initialSnapshot.copy(playbackPositionTicks = 10_800_000_000L, toBeSynced = false)
        coEvery { h.databaseRepository.getAllUserDataToSync(h.userId, h.serverId) } returns
            listOf(initialSnapshot)
        coEvery { h.databaseRepository.getOfflinePlays(h.userId, h.serverId) } returns emptyList()
        coEvery { h.databaseRepository.getUserData(h.userId, itemId, h.serverId) } returns
            alreadySynced

        val result = h.buildWorker().doWork()

        assertEquals(
            ListenableWorker.Result.success(
                androidx.work.workDataOf("synced_count" to 0, "failed_count" to 0),
            ),
            result,
        )
        assertEquals(0, h.apiClient.requests.size)
        coVerify(exactly = 0) {
            h.databaseRepository.markUserDataSyncedIfUnchanged(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }
    }

    @Test
    fun `keeps toBeSynced and schedules retry when newer local progress arrives mid-upload`() =
        runTest {
            val h = Harness()
            val itemId = testUuid(12)
            h.itemStore.put(testTrack(id = itemId, playbackPositionTicks = 12_000_000_000L))
            val initialSnapshot =
                AfinityUserDataDto(
                    userId = h.userId,
                    itemId = itemId,
                    serverId = h.serverId,
                    played = false,
                    favorite = false,
                    likes = false,
                    playbackPositionTicks = 5_000_000_000L,
                    toBeSynced = true,
                    lastPlayedAt = 1_795_500_000_000L,
                )
            val newerSnapshot =
                initialSnapshot.copy(
                    playbackPositionTicks = 12_000_000_000L,
                    lastPlayedAt = 1_795_500_050_000L,
                )
            coEvery { h.databaseRepository.getAllUserDataToSync(h.userId, h.serverId) } returns
                listOf(initialSnapshot)
            coEvery { h.databaseRepository.getOfflinePlays(h.userId, h.serverId) } returns
                emptyList()
            coEvery { h.databaseRepository.getUserData(h.userId, itemId, h.serverId) } returnsMany
                listOf(initialSnapshot, newerSnapshot)
            coEvery {
                h.databaseRepository.markUserDataSyncedIfUnchanged(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns 0
            h.apiClient.onPost("/UserItems/{itemId}/UserData") { req ->
                val id = req.pathParameters["itemId"] as UUID
                FakeApiClient.rawJsonResponse(
                    """{"ItemId":"$id","Key":"$id","Played":false,"PlaybackPositionTicks":5000000000,"PlayCount":0,"IsFavorite":false}""",
                )
            }

            val result = h.buildWorker().doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
            assertEquals(
                12_000_000_000L,
                h.itemStore.getAs<AfinityTrack>(itemId)?.playbackPositionTicks,
            )
        }

    @Test
    fun `does not overwrite ItemStore or retry when concurrent online sync clears toBeSynced mid-upload`() =
        runTest {
            val h = Harness()
            val itemId = testUuid(13)
            h.itemStore.put(testTrack(id = itemId, playbackPositionTicks = 12_000_000_000L))
            val initialSnapshot =
                AfinityUserDataDto(
                    userId = h.userId,
                    itemId = itemId,
                    serverId = h.serverId,
                    played = false,
                    favorite = false,
                    likes = false,
                    playbackPositionTicks = 5_000_000_000L,
                    toBeSynced = true,
                    lastPlayedAt = 1_795_500_000_000L,
                )
            val concurrentOnlineSync =
                initialSnapshot.copy(
                    playbackPositionTicks = 12_000_000_000L,
                    toBeSynced = false,
                    lastPlayedAt = 1_795_500_050_000L,
                )
            coEvery { h.databaseRepository.getAllUserDataToSync(h.userId, h.serverId) } returns
                listOf(initialSnapshot)
            coEvery { h.databaseRepository.getOfflinePlays(h.userId, h.serverId) } returns
                emptyList()
            coEvery { h.databaseRepository.getUserData(h.userId, itemId, h.serverId) } returnsMany
                listOf(initialSnapshot, concurrentOnlineSync)
            coEvery {
                h.databaseRepository.markUserDataSyncedIfUnchanged(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns 0
            h.apiClient.onPost("/UserItems/{itemId}/UserData") { req ->
                val id = req.pathParameters["itemId"] as UUID
                FakeApiClient.rawJsonResponse(
                    """{"ItemId":"$id","Key":"$id","Played":false,"PlaybackPositionTicks":5000000000,"PlayCount":0,"IsFavorite":false}""",
                )
            }

            val result = h.buildWorker().doWork()

            assertEquals(
                ListenableWorker.Result.success(
                    androidx.work.workDataOf("synced_count" to 1, "failed_count" to 0),
                ),
                result,
            )
            assertEquals(
                12_000_000_000L,
                h.itemStore.getAs<AfinityTrack>(itemId)?.playbackPositionTicks,
            )
        }
}
