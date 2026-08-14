package com.lawrefbook.unified.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
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
import com.lawrefbook.unified.data.HistoryEntity
import com.lawrefbook.unified.ui.rememberRepository

@Composable
fun HistoryScreen(nav: NavHostController) {
    val repo = rememberRepository()
    val items by repo.historyFlow().collectAsState(initial = emptyList())

    Scaffold(topBar = { TopAppBar(title = { Text("历史") }) }, containerColor = MaterialTheme.colorScheme.surface) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("暂无浏览历史", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items) { h ->
                    ListItem(
                        headlineContent = { Text(h.lawName, style = MaterialTheme.typography.titleMedium) },
                        modifier = Modifier.fillMaxWidth().clickable { nav.navigate("reader/${h.lawId}") }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
