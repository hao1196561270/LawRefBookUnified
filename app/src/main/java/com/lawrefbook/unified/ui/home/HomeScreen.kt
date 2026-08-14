package com.lawrefbook.unified.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.lawrefbook.unified.data.model.CategoryEntity
import com.lawrefbook.unified.data.model.LawEntity
import com.lawrefbook.unified.ui.rememberRepository

private fun catEmoji(name: String): String = when {
    name.contains("宪法") -> "🛡️"
    name.contains("刑") -> "⚖️"
    name.contains("民") -> "📜"
    name.contains("行政") -> "🏛️"
    name.contains("经济") || name.contains("商") -> "💼"
    name.contains("诉讼") || name.contains("程序") -> "📋"
    name.contains("劳动") -> "🤝"
    name.contains("国际") -> "🌐"
    else -> "📁"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavHostController) {
    val repo = rememberRepository()
    var categories by remember { mutableStateOf<List<CategoryEntity>>(emptyList()) }
    var commonLaws by remember { mutableStateOf<List<LawEntity>>(emptyList()) }
    val recent by repo.historyFlow().collectAsState(initial = emptyList())
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        categories = repo.getCategories()
        val firstCat = categories.firstOrNull()?.id
        if (firstCat != null) commonLaws = repo.getLaws(firstCat).take(6)
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("法条通", style = MaterialTheme.typography.headlineSmall) },
                colors = androidx.compose.material3.TopAppBarDefaults
                    .topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    // 搜索入口
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { nav.navigate("search") }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text("搜索法律、法规、条文…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                item { SectionTitle("法律分类") }
                items(categories.chunked(2)) { pair ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { cat ->
                            CategoryCard(cat, Modifier.weight(1f)) { nav.navigate("lawlist/${cat.id}") }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }

                item { SectionTitle("最近浏览") }
                item {
                    // 有历史时点开直接进对应法规阅读页；无历史时展示默认提示词（点击进检索）
                    val recentChips = recent.take(8)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (recentChips.isNotEmpty()) {
                            recentChips.forEach { h ->
                                RecentChip(h.lawName) { nav.navigate("reader/${h.lawId}") }
                            }
                        } else {
                            listOf("刑法", "民法典", "劳动合同法").forEach { name ->
                                RecentChip(name) { nav.navigate("search") }
                            }
                        }
                    }
                }

                if (commonLaws.isNotEmpty()) {
                    item { SectionTitle("常用法规") }
                    items(commonLaws) { law ->
                        LawRow(law) { nav.navigate("reader/${law.id}") }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentChip(name: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(name, color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun CategoryCard(cat: CategoryEntity, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Box(
                Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) { Text(catEmoji(cat.name), style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(8.dp))
            Text(cat.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = cat.folder.takeIf { it.isNotBlank() }
            if (sub != null) {
                Spacer(Modifier.height(2.dp))
                Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun LawRow(law: LawEntity, onClick: () -> Unit) {
    val sub = (law.subTitle ?: law.level).takeIf { it.isNotBlank() }
    ListItem(
        headlineContent = { Text(law.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = sub?.let { { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    )
    HorizontalDivider()
}
