package org.assistix.proto.nativeapp.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.assistix.proto.nativeapp.ProtoApplication
import java.util.concurrent.TimeUnit

class AppUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? ProtoApplication ?: return Result.success()
        return try {
            app.appUpdate.refresh(silent = true)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

object AppUpdateScheduler {
    private const val WORK = "proto_app_update_check"

    fun schedule(context: Context) {
        val req =
            PeriodicWorkRequestBuilder<AppUpdateWorker>(12, TimeUnit.HOURS)
                .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }
}
