package com.lawrefbook.unified.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.lawrefbook.unified.data.BuiltinData
import com.lawrefbook.unified.data.settings.UpdateStatus
import com.lawrefbook.unified.ui.rememberSettings
import com.lawrefbook.unified.ui.rememberUpdate
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
private fun fmtTs(ts: Long): String = if (ts == 0L) "尚未检查" else dateFmt.format(Date(ts))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavHostController) {
    val settings = rememberSettings()
    val updater = rememberUpdate()
    val scope = rememberCoroutineScope()

    val dark by settings.darkMode.collectAsState(initial = false)
    val dynamic by settings.dynamicColor.collectAsState(initial = true)
    val fontSize by settings.fontSize.collectAsState(initial = 17f)
    val lineSpacing by settings.lineSpacing.collectAsState(initial = 1.5f)
    val articleSpacing by settings.articleSpacing.collectAsState(initial = 8f)

    val dataCommit by settings.dataCommitSha.collectAsState(initial = BuiltinData.COMMIT)
    val dataUpdatedAt by settings.dataUpdatedAt.collectAsState(initial = 0L)
    val lastCheck by settings.lastCheckAt.collectAsState(initial = 0L)
    val autoUpdate by settings.autoUpdate.collectAsState(initial = true)
    val updateStatus by settings.updateStatus.collectAsState()
    val updateProgress by settings.updateProgress.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("设置") }) }, containerColor = MaterialTheme.colorScheme.surface) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                start = 16.dp, end = 16.dp, bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                SectionTitle("外观")
                // ── 主题设置入口卡片（替代旧的主题色/动态配色/深色模式行）──
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { nav.navigate("theme_settings") },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Palette, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("主题设置", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (dark) "深色" else if (dynamic) "动态配色" else "浅色",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                SectionTitle("阅读")
                SliderItem("正文字号", fontSize, 12f..28f) { scope.launch { settings.setFontSize(it) } }
                SliderItem("行距", lineSpacing, 1f..2.5f) { scope.launch { settings.setLineSpacing(it) } }
                SliderItem("法条间距", articleSpacing, 0f..24f) { scope.launch { settings.setArticleSpacing(it) } }

                SectionTitle("法条数据")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        LabeledRow("当前数据版本", "${dataCommit.take(8)}  ·  ${if (dataUpdatedAt == 0L) BuiltinData.DATE else dayFmt.format(Date(dataUpdatedAt))}")
                        LabeledRow("上次检查", fmtTs(lastCheck))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("自动更新（每月检查）", modifier = Modifier.weight(1f))
                            Switch(checked = autoUpdate, onCheckedChange = { scope.launch { settings.setAutoUpdate(it) } })
                        }
                        Button(
                            onClick = { scope.launch { updater.checkAndApplyIfNeeded(true) } },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Text("检查更新")
                        }
                        val statusText = when (val s = updateStatus) {
                            UpdateStatus.Idle -> "未检查"
                            UpdateStatus.Checking -> "正在检查更新…"
                            UpdateStatus.NoUpdate -> "已是最新版本"
                            is UpdateStatus.Available -> "发现新版本（${s.newCommit.take(8)}），可更新"
                            UpdateStatus.Downloading -> "正在下载更新…"
                            UpdateStatus.Applying -> "正在应用更新…"
                            UpdateStatus.Updated -> "已更新至最新版本"
                            is UpdateStatus.Error -> "更新失败：${s.message}"
                        }
                        Text(
                            statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        if (updateStatus == UpdateStatus.Downloading) {
                            LinearProgressIndicator(
                                progress = { updateProgress / 100f },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            )
                        }
                    }
                }

                SectionTitle("关于")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { nav.navigate("about") },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("关于法条通", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "版本、数据来源与许可证",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun LabeledRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SliderItem(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChanged: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("$label：${String.format("%.1f", value)}", style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onChanged, valueRange = range, steps = 0)
    }
}
