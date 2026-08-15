package com.lawrefbook.unified.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.lawrefbook.unified.ui.rememberSettings
import kotlinx.coroutines.launch

/** 主题色预设：名称 + seed 主色 */
private data class ColorPreset(val name: String, val seed: Long)

private val COLOR_PRESETS = listOf(
    ColorPreset("经典紫", 0xFF6750A4L),
    ColorPreset("经典蓝", 0xFF1565C0L),
    ColorPreset("司法绿", 0xFF2E7D32L),
    ColorPreset("中国红", 0xFFC62828L),
    ColorPreset("石墨", 0xFF37474FL)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(nav: NavHostController) {
    val settings = rememberSettings()
    val scope = rememberCoroutineScope()

    val dark by settings.darkMode.collectAsState(initial = false)
    val dynamic by settings.dynamicColor.collectAsState(initial = true)
    val seed by settings.themeSeed.collectAsState(initial = 0xFF6750A4L)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("主题设置") },
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
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item { SectionTitle("外观") }

            item {
                SettingSwitchItem(
                    title = "深色模式",
                    subtitle = "启用后界面切换为护眼暗色",
                    checked = dark,
                    onCheckedChange = { scope.launch { settings.setDarkMode(it) } }
                )
            }
            item {
                SettingSwitchItem(
                    title = "动态配色",
                    subtitle = "Material You：依据壁纸自动生成主题色（Android 12+）",
                    checked = dynamic,
                    onCheckedChange = { scope.launch { settings.setDynamicColor(it) } }
                )
            }

            item { SectionTitle("主题色") }
            items(COLOR_PRESETS) { preset ->
                val selected = seed == preset.seed
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { scope.launch { settings.setThemeSeed(preset.seed) } },
                    leadingContent = {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(preset.seed.toInt()))
                        )
                    },
                    headlineContent = { Text(preset.name) },
                    trailingContent = if (selected) {
                        {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null
                )
            }
            item {
                Text(
                    "主题色在关闭“动态配色”后生效。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp)
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
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        modifier = Modifier.fillMaxWidth(),
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) }
    )
}
