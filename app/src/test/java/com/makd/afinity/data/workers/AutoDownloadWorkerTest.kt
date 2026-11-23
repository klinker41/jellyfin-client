package com.makd.afinity.data.workers

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.makd.afinity.data.autodownload.AutoDownloadManager
import com.makd.afinity.data.manager.Session
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.auth.AuthRepository
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AutoDownloadWorkerTest {

    private fun buildWorker(
        sessionManager: SessionManager,
        authRepository: AuthRepository,
        preferencesRepository: PreferencesRepository,
        autoDownloadManager: AutoDownloadManager,
        runAttemptCount: Int = 0,
    ): AutoDownloadWorker {
        val context: Context = ApplicationProvider.getApplicationContext()
        val factory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker =
                    AutoDownloadWorker(
                        appContext = appContext,
                        workerParams = workerParameters,
                        sessionManager = sessionManager,
                        authRepository = authRepository,
                        preferencesRepository = preferencesRepository,
                        autoDownloadManager = autoDownloadManager,
                    )
            }
        return TestListenableWorkerBuilder<AutoDownloadWorker>(context)
            .setWorkerFactory(factory)
            .setRunAttemptCount(runAttemptCount)
            .build()
    }

    @Test
    fun `skips when offline mode is enabled`() = runTest {
        val sessionManager: SessionManager = mockk(relaxed = true)
        val authRepository: AuthRepository = mockk(relaxed = true)
        val prefs: PreferencesRepository =
            mockk(relaxed = true) { coEvery { getOfflineMode() } returns true }
        val manager: AutoDownloadManager = mockk(relaxed = true)

        val worker = buildWorker(sessionManager, authRepository, prefs, manager)
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        coVerify(exactly = 0) { manager.syncEnabledLibraries() }
    }

    @Test
    fun `restores session on cold start and handles success, degraded, and failed`() = runTest {
        val prefs: PreferencesRepository =
            mockk(relaxed = true) { coEvery { getOfflineMode() } returns false }
        val manager: AutoDownloadManager =
            mockk(relaxed = true) { coEvery { syncEnabledLibraries() } returns true }

        // Failed auth restore returns success (not logged in)
        val noSession: SessionManager =
            mockk(relaxed = true) { every { currentSession } returns MutableStateFlow(null) }
        val failedAuth: AuthRepository =
            mockk(relaxed = true) {
                coEvery { restoreAuthenticationState() } returns AuthRepository.RestoreResult.Failed
            }
        assertEquals(
            ListenableWorker.Result.success(),
            buildWorker(noSession, failedAuth, prefs, manager).doWork(),
        )
        coVerify(exactly = 0) { manager.syncEnabledLibraries() }

        // Degraded auth restore retries until max attempts
        val degradedAuth: AuthRepository =
            mockk(relaxed = true) {
                coEvery { restoreAuthenticationState() } returns
                    AuthRepository.RestoreResult.Degraded(RuntimeException("unreachable"))
            }
        assertEquals(
            ListenableWorker.Result.retry(),
            buildWorker(noSession, degradedAuth, prefs, manager, runAttemptCount = 0).doWork(),
        )
        assertEquals(
            ListenableWorker.Result.success(),
            buildWorker(noSession, degradedAuth, prefs, manager, runAttemptCount = 4).doWork(),
        )

        // Successful auth restore syncs enabled libraries
        val successAuth: AuthRepository =
            mockk(relaxed = true) {
                coEvery { restoreAuthenticationState() } returns
                    AuthRepository.RestoreResult.Success
            }
        assertEquals(
            ListenableWorker.Result.success(),
            buildWorker(noSession, successAuth, prefs, manager).doWork(),
        )
        coVerify(exactly = 1) { manager.syncEnabledLibraries() }
    }

    @Test
    fun `retries when sync fails and gives up after max attempts`() = runTest {
        val sessionManager: SessionManager =
            mockk(relaxed = true) {
                every { currentSession } returns
                    MutableStateFlow(Session("srv-1", testUuid(1), "https://jf.example.com"))
            }
        val authRepository: AuthRepository = mockk(relaxed = true)
        val prefs: PreferencesRepository =
            mockk(relaxed = true) { coEvery { getOfflineMode() } returns false }
        val manager: AutoDownloadManager =
            mockk(relaxed = true) { coEvery { syncEnabledLibraries() } returns false }

        assertEquals(
            ListenableWorker.Result.retry(),
            buildWorker(sessionManager, authRepository, prefs, manager, runAttemptCount = 0)
                .doWork(),
        )
        assertEquals(
            ListenableWorker.Result.success(),
            buildWorker(sessionManager, authRepository, prefs, manager, runAttemptCount = 4)
                .doWork(),
        )
    }
}
