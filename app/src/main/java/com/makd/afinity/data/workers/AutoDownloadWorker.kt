package com.makd.afinity.data.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.makd.afinity.data.autodownload.AutoDownloadManager
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.auth.AuthRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltWorker
class AutoDownloadWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository,
    private val preferencesRepository: PreferencesRepository,
    private val autoDownloadManager: AutoDownloadManager,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            try {
                if (preferencesRepository.getOfflineMode()) {
                    Timber.d("AutoDownloadWorker skipped: offline mode is on")
                    return@withContext Result.success()
                }

                if (sessionManager.currentSession.value == null) {
                    when (authRepository.restoreAuthenticationState()) {
                        AuthRepository.RestoreResult.Failed -> {
                            Timber.d("AutoDownloadWorker skipped: no saved session")
                            return@withContext Result.success()
                        }
                        is AuthRepository.RestoreResult.Degraded -> {
                            return@withContext if (runAttemptCount >= MAX_RUN_ATTEMPTS) {
                                Result.success()
                            } else {
                                Result.retry()
                            }
                        }
                        AuthRepository.RestoreResult.Success -> Unit
                    }
                }

                val ok = autoDownloadManager.syncEnabledLibraries()
                if (ok || runAttemptCount >= MAX_RUN_ATTEMPTS) {
                    Result.success()
                } else {
                    Result.retry()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "AutoDownloadWorker failed")
                if (runAttemptCount >= MAX_RUN_ATTEMPTS) Result.failure() else Result.retry()
            }
        }

    private companion object {
        const val MAX_RUN_ATTEMPTS = 4
    }
}
