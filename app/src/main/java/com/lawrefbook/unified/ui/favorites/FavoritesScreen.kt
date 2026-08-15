package com.lawrefbook.unified.ui.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.lawrefbook.unified.data.FavoritesEntity
import com.lawrefbook.unified.ui.rememberRepository
import kotlinx.coroutines.launch
import java.net.URLEncoder

@Composable
fun FavoritesScreen(nav: NavHostController) {
    val repo = rememberRepository()
    val scope = rememberCoroutineScope()
    val items by repo.favoritesFlow().collectAsState(initial = emptyList())

    // 分类筛选（从已有收藏的分类去重得到）
    var classifyFilter by remember { mutableStateOf<String?>(null) }
    // 正在编辑分类的条目
    var editingItem by remember { mutableStateOf<FavoritesEntity?>(null) }
    var newClassify by remember { mutableStateOf("") }

    val distinctClassifies = remember(items) {
        items.map { it.classify }.filter { it.isNotBlank() }.distinct()
    }
    // 若某分类已无收藏，自动清除筛选
    if (classifyFilter != null && classifyFilter !in distinctClassifies) {
        classifyFilter = null
    }
    val filtered = classifyFilter?.let { c -> items.filter { it.classify == c } } ?: items

    Scaffold(topBar = { TopAppBar(title = { Text("收藏") }) }, containerColor = MaterialTheme.colorScheme.surface) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            // 分类筛选行（仅当存在分类时显示）
            if (distinctClassifies.isNotEmpty()) {
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(selected = classifyFilter == null, onClick = { classifyFilter = null }, label = { Text("全部") })
                    distinctClassifies.forEach { c ->
                        FilterChip(selected = classifyFilter == c, onClick = { classifyFilter = c }, label = { Text(c) })
                    }
                }
            }

            if (filtered.isEmpty() && items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无收藏", style = MaterialTheme.typography.bodyLarge)
                }
            } else if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("该分类暂无收藏", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    contentPadding = padding,
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filtered) { f ->
                        val enc = URLEncoder.encode(f.article, "UTF-8")
                        ListItem(
                            headlineContent = {
                                Text(
                                    "${f.lawName}${if (f.article.isNotBlank()) " · ${f.article}" else ""}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            },
                            supportingContent = {
                                f.classify.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                                if (f.content.isNotBlank()) {
                                    Text(f.content, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            trailingContent = {
                                IconButton(onClick = { editingItem = f; newClassify = f.classify }) {
                                    Icon(Icons.Filled.Create, "编辑分类")
                                }
                                IconButton(onClick = { scope.launch { repo.removeFavorite(f.id) } }) {
                                    Icon(Icons.Filled.Delete, "删除")
                                }
                            },
                            modifier = Modifier.fillMaxWidth().clickable {
                                nav.navigate("reader/${f.lawId}?article=$enc")
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    // 编辑分类对话框：可选已有分类或输入新分类
    editingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = { Text("编辑分类") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("当前：${item.lawName}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (distinctClassifies.isNotEmpty()) {
                        Text("选择已有分类", style = MaterialTheme.typography.labelMedium)
                        androidx.compose.foundation.layout.Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            distinctClassifies.forEach { c ->
                                FilterChip(selected = newClassify == c, onClick = { newClassify = c }, label = { Text(c) })
                            }
                        }
                    }
                    OutlinedTextField(
                        value = newClassify,
                        onValueChange = { newClassify = it },
                        label = { Text("新分类 / 无分类则留空") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.updateFavoriteClassify(item.id, newClassify.trim()) }
                    editingItem = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editingItem = null }) { Text("取消") }
            }
        )
    }
}
