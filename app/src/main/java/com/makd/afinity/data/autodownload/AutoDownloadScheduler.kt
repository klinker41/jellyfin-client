package com.makd.afinity.data.autodownload

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.makd.afinity.data.workers.AutoDownloadWorker
import com.makd.afinity.util.requireServerNetwork
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import timber.log.Timber

@Singleton
class AutoDownloadScheduler
@Inject
constructor(@param:ApplicationContext private val context: Context) {

    fun ensurePeriodicSync(wifiOnly: Boolean, updateConstraints: Boolean = false) {
        try {
            val constraints = buildConstraints(wifiOnly)
            val request =
                PeriodicWorkRequestBuilder<AutoDownloadWorker>(
                        SYNC_INTERVAL_HOURS,
                        TimeUnit.HOURS,
                    )
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                    .addTag(WORK_TAG)
                    .build()
            val policy =
                if (updateConstraints) ExistingPeriodicWorkPolicy.UPDATE
                else ExistingPeriodicWorkPolicy.KEEP
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, policy, request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to schedule periodic auto-download sync")
        }
    }

    fun triggerSyncNow(wifiOnly: Boolean, replace: Boolean = false) {
        try {
            val constraints = buildConstraints(wifiOnly)
            val request =
                OneTimeWorkRequestBuilder<AutoDownloadWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .addTag(WORK_TAG)
                    .build()
            val policy = if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
            WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE_WORK_NAME, policy, request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to trigger immediate auto-download sync")
        }
    }

    fun cancelAll() {
        try {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(PERIODIC_WORK_NAME)
            wm.cancelUniqueWork(IMMEDIATE_WORK_NAME)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to cancel auto-download work")
        }
    }

    private fun buildConstraints(wifiOnly: Boolean): Constraints =
        Constraints.Builder()
            .requireServerNetwork(unmeteredOnly = wifiOnly)
            .setRequiresStorageNotLow(true)
            .build()

    companion object {
        const val PERIODIC_WORK_NAME = "auto_download_sync_periodic"
        const val IMMEDIATE_WORK_NAME = "auto_download_sync_now"
        const val WORK_TAG = "auto_download_sync"
        const val SYNC_INTERVAL_HOURS = 4L
    }
}
