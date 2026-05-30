package org.assistix.proto.nativeapp.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        try {
            if (WidgetUpdateCoordinator.hasAnyWidgets(applicationContext)) {
                WidgetRepository.refresh(applicationContext)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
}
