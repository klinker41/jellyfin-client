package com.makd.afinity.data.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.makd.afinity.data.manager.MediaRefreshBus
import com.makd.afinity.data.manager.RefreshTrigger
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.media.UserDataPatch
import com.makd.afinity.data.models.user.AfinityUserDataDto
import com.makd.afinity.data.network.userDataUtc
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.data.store.ItemStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.operations.UserDataApi
import org.jellyfin.sdk.model.DateTime
import timber.log.Timber

@HiltWorker
class UserDataSyncWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val sessionManager: SessionManager,
    private val databaseRepository: DatabaseRepository,
    private val securePreferencesRepository: SecurePreferencesRepository,
    private val preferencesRepository: PreferencesRepository,
    private val itemStore: ItemStore,
    private val mediaRefreshBus: MediaRefreshBus,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            try {
                if (preferencesRepository.getOfflineMode()) {
                    Timber.d("Offline mode on, leaving user data queued")
                    return@withContext Result.success()
                }

                Timber.d("Starting user data sync (attempt ${runAttemptCount + 1})")

                val accounts = securePreferencesRepository.getAllServerUserTokens()

                if (accounts.isEmpty()) {
                    Timber.d("No accounts found, skipping sync")
                    return@withContext Result.success()
                }

                var totalSuccess = 0
                var totalFailure = 0
                var retryNeeded = false
                var syncedCurrentSession = false

                accounts
                    .distinctBy { it.serverId to it.userId }
                    .forEach { account ->
                        val (pending, plays) =
                            try {
                                databaseRepository.getAllUserDataToSync(
                                    account.userId,
                                    account.serverId,
                                ) to
                                    databaseRepository.getOfflinePlays(
                                        account.userId,
                                        account.serverId,
                                    )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Timber.e(
                                    e,
                                    "Could not read pending user data for ${account.serverId}",
                                )
                                retryNeeded = true
                                return@forEach
                            }

                        if (pending.isEmpty() && plays.isEmpty()) return@forEach

                        Timber.i(
                            "Found ${pending.size} items and ${plays.size} offline plays to sync for user ${account.userId} on server ${account.serverId}",
                        )

                        val apiClient =
                            try {
                                sessionManager.getDetachedApiClient(
                                    account.serverId,
                                    account.userId,
                                )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Timber.w(e, "Could not build client for server ${account.serverId}")
                                null
                            }

                        if (apiClient == null) {
                            Timber.w(
                                "No client for server ${account.serverId} with ${pending.size} pending items, will retry",
                            )
                            retryNeeded = true
                            return@forEach
                        }

                        val userDataApi = UserDataApi(apiClient)
                        var accountStopped = false

                        for (userData in pending) {
                            val current =
                                databaseRepository.getUserData(
                                    account.userId,
                                    userData.itemId,
                                    account.serverId,
                                )
                            if (current == null || !current.toBeSynced) continue
                            when (
                                uploadUserData(
                                    apiClient = apiClient,
                                    userDataApi = userDataApi,
                                    userId = account.userId,
                                    userData = current,
                                )
                            ) {
                                UploadResult.SYNCED -> {
                                    when (
                                        clearSyncedSnapshot(
                                            userId = account.userId,
                                            serverId = account.serverId,
                                            uploaded = current,
                                            updateStore = true,
                                        )
                                    ) {
                                        ClearOutcome.CLEARED_AND_PATCHED ->
                                            syncedCurrentSession = true
                                        ClearOutcome.NEWER_PENDING -> retryNeeded = true
                                        ClearOutcome.ALREADY_CLEARED_OR_NOT_ACTIVE -> Unit
                                    }
                                    totalSuccess++
                                }
                                UploadResult.DISCARD -> {
                                    if (
                                        clearSyncedSnapshot(
                                            userId = account.userId,
                                            serverId = account.serverId,
                                            uploaded = current,
                                            updateStore = false,
                                        ) == ClearOutcome.NEWER_PENDING
                                    ) {
                                        retryNeeded = true
                                    }
                                    totalFailure++
                                }
                                UploadResult.UNAUTHORIZED -> {
                                    totalFailure++
                                    accountStopped = true
                                    Timber.w(
                                        "Token rejected for server ${account.serverId}, aborting its sync",
                                    )
                                    break
                                }
                                UploadResult.RETRY -> {
                                    totalFailure++
                                    retryNeeded = true
                                    accountStopped = true
                                    break
                                }
                            }
                        }

                        if (accountStopped) return@forEach

                        for (play in plays) {
                            val result =
                                upload(play.itemId) {
                                    userDataApi.markPlayedItem(
                                        itemId = play.itemId,
                                        userId = account.userId,
                                        datePlayed = play.playedAt.toDateTime(),
                                    )
                                }
                            when (result) {
                                UploadResult.SYNCED -> {
                                    databaseRepository.deleteOfflinePlay(play.id)
                                    if (isActiveSession(account.serverId, account.userId)) {
                                        syncedCurrentSession = true
                                    }
                                    totalSuccess++
                                }
                                UploadResult.DISCARD -> {
                                    databaseRepository.deleteOfflinePlay(play.id)
                                    totalFailure++
                                }
                                UploadResult.UNAUTHORIZED -> {
                                    totalFailure++
                                    Timber.w(
                                        "Token rejected for server ${account.serverId}, aborting its play sync",
                                    )
                                    break
                                }
                                UploadResult.RETRY -> {
                                    totalFailure++
                                    retryNeeded = true
                                    break
                                }
                            }
                        }
                    }

                if (syncedCurrentSession) {
                    mediaRefreshBus.emit(RefreshTrigger.USER_DATA_CHANGED)
                }

                Timber.i(
                    "Global user data sync completed. Success: $totalSuccess, Failures: $totalFailure, retryNeeded: $retryNeeded",
                )

                return@withContext when {
                    !retryNeeded ->
                        Result.success(
                            workDataOf(
                                "synced_count" to totalSuccess,
                                "failed_count" to totalFailure,
                            ),
                        )
                    runAttemptCount >= MAX_RUN_ATTEMPTS -> {
                        Timber.w(
                            "Giving up user data sync after $runAttemptCount attempts, rows stay queued",
                        )
                        Result.success(
                            workDataOf(
                                "synced_count" to totalSuccess,
                                "failed_count" to totalFailure,
                            ),
                        )
                    }
                    else -> Result.retry()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "User data sync failed with critical error")
                return@withContext if (runAttemptCount >= MAX_RUN_ATTEMPTS) {
                    Result.failure()
                } else {
                    Result.retry()
                }
            }
        }

    private suspend fun uploadUserData(
        apiClient: ApiClient,
        userDataApi: UserDataApi,
        userId: UUID,
        userData: AfinityUserDataDto,
    ): UploadResult =
        upload(userData.itemId) {
            if (userData.played) {
                userDataApi.markPlayedItem(
                    itemId = userData.itemId,
                    userId = userId,
                    datePlayed = userData.lastPlayedAt?.toDateTime(),
                )
            } else {
                apiClient.userDataUtc(
                    itemId = userData.itemId,
                    userId = userId,
                    positionTicks = userData.playbackPositionTicks,
                    lastPlayedAtMillis = userData.lastPlayedAt,
                )
            }
            Timber.d("Synced item ${userData.itemId} (played=${userData.played})")
        }

    private suspend fun upload(itemId: UUID, action: suspend () -> Unit): UploadResult {
        return try {
            action()
            UploadResult.SYNCED
        } catch (e: InvalidStatusException) {
            when (e.status) {
                401,
                403 -> UploadResult.UNAUTHORIZED
                400,
                404 -> {
                    Timber.w("Server rejected item $itemId with ${e.status}, dropping pending row")
                    UploadResult.DISCARD
                }
                else -> {
                    Timber.w(e, "Failed to sync item $itemId")
                    UploadResult.RETRY
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to sync item $itemId")
            UploadResult.RETRY
        }
    }

    private suspend fun clearSyncedSnapshot(
        userId: UUID,
        serverId: String,
        uploaded: AfinityUserDataDto,
        updateStore: Boolean,
    ): ClearOutcome {
        val clearedRows =
            databaseRepository.markUserDataSyncedIfUnchanged(
                userId = userId,
                itemId = uploaded.itemId,
                serverId = serverId,
                positionTicks = uploaded.playbackPositionTicks,
                played = uploaded.played,
                lastPlayedAt = uploaded.lastPlayedAt,
            )
        if (clearedRows > 0) {
            if (updateStore && isActiveSession(serverId, userId)) {
                itemStore.applyPatch(
                    uploaded.itemId,
                    UserDataPatch(
                        played = uploaded.played,
                        playbackPositionTicks = uploaded.playbackPositionTicks,
                    ),
                )
                return ClearOutcome.CLEARED_AND_PATCHED
            }
            return ClearOutcome.ALREADY_CLEARED_OR_NOT_ACTIVE
        }
        val latest = databaseRepository.getUserData(userId, uploaded.itemId, serverId)
        return if (latest != null && latest.toBeSynced) {
            ClearOutcome.NEWER_PENDING
        } else {
            ClearOutcome.ALREADY_CLEARED_OR_NOT_ACTIVE
        }
    }

    private fun isActiveSession(serverId: String, userId: UUID): Boolean =
        sessionManager.currentSession.value?.let {
            it.serverId == serverId && it.userId == userId
        } == true

    private enum class ClearOutcome {
        CLEARED_AND_PATCHED,
        ALREADY_CLEARED_OR_NOT_ACTIVE,
        NEWER_PENDING,
    }

    private fun Long.toDateTime(): DateTime =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()

    private enum class UploadResult {
        SYNCED,
        DISCARD,
        UNAUTHORIZED,
        RETRY,
    }

    private companion object {
        const val MAX_RUN_ATTEMPTS = 6
    }
}
