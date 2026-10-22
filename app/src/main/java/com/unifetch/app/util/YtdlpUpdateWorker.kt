package com.unifetch.app.util

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.unifetch.app.App
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Background yt-dlp check every 12 hours, only on a network connection and when battery isn't low. */
class YtdlpUpdateWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // The app sets up yt-dlp in the background when it starts; wait for that first.
        withTimeoutOrNull(TimeUnit.MINUTES.toMillis(2)) { App.ytdlpReady.await() }
            ?: return Result.retry()
        val result =
            withContext(Dispatchers.IO) {
                YtdlpUpdateManager.checkAndUpdate(YtdlpUpdateManager.Trigger.BACKGROUND)
            }
        Log.i(TAG, "background yt-dlp check: $result")
        return Result.success()
    }

    companion object {
        private const val TAG = "YtdlpUpdateWorker"
        private const val WORK_NAME = "ytdlp-background-update"

        /** Starts or stops the background check to match the update setting. */
        fun schedule(context: Context) {
            val workManager = WorkManager.getInstance(context)
            if (YtdlpUpdateManager.mode() == YT_DLP_MODE_OFF) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            val request =
                PeriodicWorkRequestBuilder<YtdlpUpdateWorker>(
                        YtdlpUpdatePolicy.BACKGROUND_INTERVAL_HOURS,
                        TimeUnit.HOURS,
                    )
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .setRequiresBatteryNotLow(true)
                            .build()
                    )
                    .build()
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
