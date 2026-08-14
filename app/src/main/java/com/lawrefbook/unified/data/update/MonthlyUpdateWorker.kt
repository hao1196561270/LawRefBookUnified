package com.lawrefbook.unified.data.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lawrefbook.unified.MyApplication
import com.lawrefbook.unified.data.settings.UpdateStatus

/**
 * 月度周期任务：检查上游法条更新，检测到新版本则自动下载同步。
 * 由 MyApplication 在启动时 enqueue（每 30 天执行一次，仅限非计费网络）。
 */
class MonthlyUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as MyApplication
            val status = app.updateRepository.checkAndApplyIfNeeded()
            if (status is UpdateStatus.Error) Result.retry() else Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
