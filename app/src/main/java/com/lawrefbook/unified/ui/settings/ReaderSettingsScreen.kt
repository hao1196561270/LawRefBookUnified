package com.lawrefbook.unified.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.lawrefbook.unified.ui.rememberSettings
import kotlinx.coroutines.launch

/** 阅读设置二级页：正文字号、行距、法条间距 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsScreen(nav: NavHostController) {
    val settings = rememberSettings()
    val scope = rememberCoroutineScope()

    val fontSize by settings.fontSize.collectAsState(initial = 17f)
    val lineSpacing by settings.lineSpacing.collectAsState(initial = 1.5f)
    val articleSpacing by settings.articleSpacing.collectAsState(initial = 8f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("阅读设置") },
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
                        SliderItem("正文字号", fontSize, 12f..28f) { scope.launch { settings.setFontSize(it) } }
                        SliderItem("行距", lineSpacing, 1f..2.5f) { scope.launch { settings.setLineSpacing(it) } }
                        SliderItem("法条间距", articleSpacing, 0f..24f) { scope.launch { settings.setArticleSpacing(it) } }
                    }
                }
            }
            item {
                Text(
                    "字号与行距实时生效；法条间距控制条文之间的空白间隔。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
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
        Text(
            "$label：${String.format("%.1f", value)}",
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(value = value, onValueChange = onChanged, valueRange = range, steps = 0)
    }
}
