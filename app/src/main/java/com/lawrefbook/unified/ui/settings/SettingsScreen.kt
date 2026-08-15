package com.lawrefbook.unified.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.lawrefbook.unified.data.BuiltinData
import com.lawrefbook.unified.ui.rememberSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavHostController) {
    val settings = rememberSettings()

    val dark by settings.darkMode.collectAsState(initial = false)
    val dynamic by settings.dynamicColor.collectAsState(initial = true)
    val fontSize by settings.fontSize.collectAsState(initial = 17f)
    val lineSpacing by settings.lineSpacing.collectAsState(initial = 1.5f)
    val articleSpacing by settings.articleSpacing.collectAsState(initial = 8f)
    val dataCommit by settings.dataCommitSha.collectAsState(initial = BuiltinData.COMMIT)

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
                SectionTitle("设置")
                SettingsEntryCard(
                    icon = Icons.Filled.Palette,
                    title = "主题设置",
                    subtitle = if (dark) "深色模式" else if (dynamic) "动态配色" else "浅色模式",
                    onClick = { nav.navigate("theme_settings") }
                )
                SettingsEntryCard(
                    icon = Icons.Filled.MenuBook,
                    title = "阅读设置",
                    subtitle = "字号 ${fontSize.toInt()} · 行距 ${"%.1f".format(lineSpacing)} · 法条间距 ${articleSpacing.toInt()}",
                    onClick = { nav.navigate("reader_settings") }
                )
                SettingsEntryCard(
                    icon = Icons.Filled.Description,
                    title = "法条数据",
                    subtitle = "数据版本 ${dataCommit.take(8)} · 自动更新",
                    onClick = { nav.navigate("data_settings") }
                )
                SettingsEntryCard(
                    icon = Icons.Filled.Info,
                    title = "关于法条通",
                    subtitle = "版本、数据来源与许可证",
                    onClick = { nav.navigate("about") }
                )
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
private fun SettingsEntryCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
