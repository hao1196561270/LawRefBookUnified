package com.lawrefbook.unified.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.lawrefbook.unified.MyApplication
import com.lawrefbook.unified.data.LawRepository
import com.lawrefbook.unified.data.settings.SettingsRepository
import com.lawrefbook.unified.data.update.UpdateRepository

/**
 * 便捷组合：从 Application 取得统一的仓库与设置仓库。
 * 整合版不引入 Hilt，保持依赖最小、便于阅读源码。
 */
@Composable
fun rememberRepository(): LawRepository =
    (LocalContext.current.applicationContext as MyApplication).repository

@Composable
fun rememberSettings(): SettingsRepository =
    (LocalContext.current.applicationContext as MyApplication).settings

@Composable
fun rememberUpdate(): UpdateRepository =
    (LocalContext.current.applicationContext as MyApplication).updateRepository
