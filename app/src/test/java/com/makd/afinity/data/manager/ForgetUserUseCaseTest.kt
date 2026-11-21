package com.makd.afinity.data.manager

import com.makd.afinity.data.database.dao.ServerDatabaseDao
import com.makd.afinity.data.database.entities.DownloadDto
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.data.repository.audiobookshelf.AbsDownloadRepository
import com.makd.afinity.data.repository.auth.AuthRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class ForgetUserUseCaseTest {

    private class Harness(
        activeServerId: String = "srv-1",
        activeUserId: java.util.UUID = testUuid(1),
    ) {
        val serverDatabaseDao: ServerDatabaseDao = mockk(relaxed = true)
        val downloadRepository: DownloadRepository = mockk(relaxed = true)
        val absDownloadRepository: AbsDownloadRepository = mockk(relaxed = true)
        val securePreferencesRepository: SecurePreferencesRepository = mockk(relaxed = true)
        val sessionManager: SessionManager = mockk {
            every { currentSession } returns
                MutableStateFlow(Session(activeServerId, activeUserId, "https://jf.example.com"))
        }
        val authRepository: AuthRepository = mockk(relaxed = true)
        val useCase =
            ForgetUserUseCase(
                serverDatabaseDao = serverDatabaseDao,
                downloadRepository = downloadRepository,
                absDownloadRepository = absDownloadRepository,
                securePreferencesRepository = securePreferencesRepository,
                sessionManager = sessionManager,
                authRepository = authRepository,
            )
    }

    @Test
    fun `invoke rejects currently active session without deleting any state`() = runTest {
        val activeUser = testUuid(1)
        val h = Harness(activeServerId = "srv-1", activeUserId = activeUser)

        val result = h.useCase("srv-1", activeUser)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        coVerify(exactly = 0) { h.serverDatabaseDao.getDownloadsForUser(any(), any()) }
        coVerify(exactly = 0) { h.serverDatabaseDao.clearAllDataForUser(any(), any()) }
    }

    @Test
    fun `invoke deletes downloads, revokes tokens, and clears user DB even on partial errors`() =
        runTest {
            val h = Harness(activeServerId = "srv-1", activeUserId = testUuid(1))
            val targetUser = testUuid(2)
            val dlId1 = testUuid(50)
            val dlId2 = testUuid(51)
            val absId = testUuid(52)

            val downloadDto =
                DownloadDto(
                    id = dlId1,
                    itemId = testUuid(60),
                    itemName = "Movie",
                    itemType = "Movie",
                    sourceId = "src",
                    sourceName = "Default",
                    status = DownloadStatus.COMPLETED,
                    progress = 1.0f,
                    bytesDownloaded = 100L,
                    totalBytes = 100L,
                    filePath = "/tmp/movie.mkv",
                    error = null,
                    createdAt = 1L,
                    updatedAt = 2L,
                    serverId = "srv-1",
                    userId = targetUser,
                    imageUrl = null,
                    seriesImageUrl = null,
                    seriesName = null,
                    seasonNumber = null,
                    episodeNumber = null,
                    releaseYear = null,
                    runtimeTicks = null,
                )

            coEvery { h.serverDatabaseDao.getDownloadsForUser("srv-1", targetUser) } returns
                listOf(downloadDto, downloadDto.copy(id = dlId2))
            coEvery {
                h.serverDatabaseDao.getAbsDownloadIdsForUser("srv-1", targetUser.toString())
            } returns listOf(absId)
            coEvery { h.downloadRepository.deleteDownload(dlId1) } returns
                Result.failure(RuntimeException("disk locked"))
            coEvery { h.downloadRepository.deleteDownload(dlId2) } returns Result.success(Unit)
            coEvery { h.absDownloadRepository.deleteDownload(absId) } returns Result.success(Unit)
            coEvery { h.authRepository.revokeToken("srv-1", targetUser) } returns
                Result.failure(RuntimeException("offline"))

            val result = h.useCase("srv-1", targetUser)

            assertTrue(result.isSuccess)
            coVerifyOrder {
                h.downloadRepository.deleteDownload(dlId1)
                h.downloadRepository.deleteDownload(dlId2)
                h.absDownloadRepository.deleteDownload(absId)
                h.authRepository.revokeToken("srv-1", targetUser)
                h.securePreferencesRepository.clearServerUserToken("srv-1", targetUser)
                h.securePreferencesRepository.clearJellyseerrAuthForUser("srv-1", targetUser)
                h.securePreferencesRepository.clearAudiobookshelfAuthForUser("srv-1", targetUser)
                h.serverDatabaseDao.clearAllDataForUser("srv-1", targetUser)
            }
        }
}
