package com.lawrefbook.unified

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.lawrefbook.unified.data.LawRepository
import com.lawrefbook.unified.data.settings.SettingsRepository
import com.lawrefbook.unified.data.update.MonthlyUpdateWorker
import com.lawrefbook.unified.data.update.UpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MyApplication : Application() {

    val repository by lazy { LawRepository(this) }
    val settings by lazy { SettingsRepository(this) }
    val updateRepository by lazy { UpdateRepository(this, settings) }

    override fun onCreate() {
        super.onCreate()
        // 首次启动在后台构建检索索引（一次性；约数千部法规，需数秒）
        val repo = repository
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { repo.ensureSearchIndex() }
        }
        // 注册月度法条更新检查（每 30 天，仅非计费网络）
        enqueueMonthlyUpdate()
        // 启动后若超过 7 天未检查，则顺带检查一次
        CoroutineScope(Dispatchers.IO).launch {
            val last = settings.lastCheckAt.first()
            val auto = settings.autoUpdate.first()
            if (auto && System.currentTimeMillis() - last > 7L * 24 * 3600 * 1000) {
                runCatching { updateRepository.checkAndApplyIfNeeded() }
            }
        }
    }

    private fun enqueueMonthlyUpdate() {
        val request = PeriodicWorkRequestBuilder<MonthlyUpdateWorker>(30, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "monthly_law_update",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
