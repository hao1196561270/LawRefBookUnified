package com.lawrefbook.unified.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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

/** 法条数据二级页：数据版本、更新状态与检查 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSettingsScreen(nav: NavHostController) {
    val settings = rememberSettings()
    val updater = rememberUpdate()
    val scope = rememberCoroutineScope()

    val dataCommit by settings.dataCommitSha.collectAsState(initial = BuiltinData.COMMIT)
    val dataUpdatedAt by settings.dataUpdatedAt.collectAsState(initial = 0L)
    val lastCheck by settings.lastCheckAt.collectAsState(initial = 0L)
    val autoUpdate by settings.autoUpdate.collectAsState(initial = true)
    val updateStatus by settings.updateStatus.collectAsState()
    val updateProgress by settings.updateProgress.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("法条数据") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                start = 16.dp, end = 16.dp, bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        LabeledRow("当前数据版本", "${dataCommit.take(8)}  ·  ${if (dataUpdatedAt == 0L) BuiltinData.DATE else dayFmt.format(Date(dataUpdatedAt))}")
                        LabeledRow("上次检查", fmtTs(lastCheck))
                        LabeledRow("数据规模", "1688 部法规 / 74,326 条法条")
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
            }
        }
    }
}

@Composable
private fun LabeledRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
