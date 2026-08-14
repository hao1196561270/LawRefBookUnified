package com.lawrefbook.unified.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.lawrefbook.unified.data.model.CategoryEntity
import com.lawrefbook.unified.data.model.SearchResult
import com.lawrefbook.unified.data.search.SearchMode
import com.lawrefbook.unified.data.search.SearchQuery
import com.lawrefbook.unified.data.search.SortField
import com.lawrefbook.unified.data.search.SortOrder
import com.lawrefbook.unified.ui.components.BottomSheet
import com.lawrefbook.unified.ui.rememberRepository
import kotlinx.coroutines.launch
import java.net.URLEncoder

private val RECENT = listOf("正当防卫", "竞业限制", "工伤认定", "酒驾", "合同纠纷")

private val MODE_ITEMS = listOf(
    SearchMode.EXACT to "精确匹配",
    SearchMode.FUZZY to "模糊匹配"
)

private val SORT_ITEMS = listOf(
    SortField.RELEVANCE to "相关度",
    SortField.LAW_NAME to "法规名",
    SortField.ARTICLE to "条号",
    SortField.CATEGORY to "分类",
    SortField.LEVEL to "层级",
    SortField.PUBLISH to "发布时间"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(nav: NavHostController) {
    val repo = rememberRepository()
    val scope = rememberCoroutineScope()

    var keyword by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(SearchMode.FUZZY) }
    var categoryId by remember { mutableStateOf<String?>(null) }
    var level by remember { mutableStateOf<String?>(null) }
    var fromYear by remember { mutableStateOf("") }
    var toYear by remember { mutableStateOf("") }
    var sortField by remember { mutableStateOf(SortField.RELEVANCE) }
    var sortOrder by remember { mutableStateOf(SortOrder.ASCENDING) }

    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var categories by remember { mutableStateOf<List<CategoryEntity>>(emptyList()) }
    var levels by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        runCatching { categories = repo.getCategories() }
        runCatching { levels = repo.getLevels() }
    }

    fun buildQuery(): SearchQuery {
        val from = fromYear.trim().takeIf { it.isNotBlank() }?.let { "$it-01-01" }
        val to = toYear.trim().takeIf { it.isNotBlank() }?.let { "$it-12-31" }
        return SearchQuery(
            keyword = keyword,
            mode = mode,
            categoryId = categoryId,
            level = level,
            publishFrom = from,
            publishTo = to,
            sortField = sortField,
            sortOrder = sortOrder
        )
    }

    fun runSearch() {
        searching = true
        scope.launch {
            results = repo.search(buildQuery())
            searching = false
        }
    }

    val filterCount =
        (if (categoryId != null) 1 else 0) +
        (if (level != null) 1 else 0) +
        (if (fromYear.isNotBlank()) 1 else 0) +
        (if (toYear.isNotBlank()) 1 else 0)

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = keyword,
            onValueChange = { keyword = it },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            placeholder = { Text("搜索法条内容，如：正当防卫、赔偿") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (keyword.isNotBlank()) {
                    IconButton(onClick = {
                        keyword = ""
                        if (filterCount > 0) runSearch()
                    }) { Icon(Icons.Filled.Close, null) }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { runSearch() })
        )

        // 搜索方式：精确 / 模糊（动态切换查询逻辑）
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "方式",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
            MODE_ITEMS.forEach { (m, label) ->
                FilterChip(
                    selected = mode == m,
                    onClick = {
                        mode = m
                        if (keyword.isNotBlank() || filterCount > 0) runSearch()
                    },
                    label = { Text(label) }
                )
            }
            TextButton(onClick = { showFilter = true }) {
                Icon(Icons.Filled.FilterList, null, modifier = Modifier.width(18.dp))
                Text(if (filterCount > 0) "筛选/排序 ($filterCount)" else "筛选/排序")
            }
        }

        // 已激活筛选条件摘要（点击可清除）
        if (filterCount > 0) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categoryId?.let { cid ->
                    val name = categories.firstOrNull { it.id == cid }?.name ?: "分类"
                    ActiveChip("分类：$name") { categoryId = null; runSearch() }
                }
                level?.let { ActiveChip("层级：$it") { level = null; runSearch() } }
                fromYear.takeIf { it.isNotBlank() }?.let {
                    ActiveChip("起：$it") { fromYear = ""; runSearch() }
                }
                toYear.takeIf { it.isNotBlank() }?.let {
                    ActiveChip("止：$it") { toYear = ""; runSearch() }
                }
            }
        }

        when {
            keyword.isBlank() && filterCount == 0 -> {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("最近搜索", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RECENT.forEach { r ->
                            Box(
                                Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .clickable { keyword = r; runSearch() }
                                    .defaultMinSize(minHeight = 48.dp)
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(r, color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            searching -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            else -> {
                if (results.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("未找到匹配结果，试试调整搜索方式或筛选条件。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        items(results) { r ->
                            val enc = URLEncoder.encode(r.article, "UTF-8")
                            ResultRow(r, keyword) { nav.navigate("reader/${r.lawId}?article=$enc") }
                        }
                    }
                }
            }
        }
    }

    BottomSheet(visible = showFilter, onDismiss = { showFilter = false }) {
        Text("筛选与排序", style = MaterialTheme.typography.titleMedium)

        // 分类
        SectionTitle("分类")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = categoryId == null, onClick = { categoryId = null }, label = { Text("全部") })
            categories.forEach { c ->
                FilterChip(selected = categoryId == c.id, onClick = { categoryId = c.id }, label = { Text(c.name) })
            }
        }

        // 效力层级（标签）
        SectionTitle("效力层级")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = level == null, onClick = { level = null }, label = { Text("全部") })
            levels.forEach { lv ->
                FilterChip(selected = level == lv, onClick = { level = lv }, label = { Text(lv) })
            }
        }

        // 发布时间范围
        SectionTitle("发布时间（年份）")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = fromYear,
                onValueChange = { fromYear = it.filter { ch -> ch.isDigit() }.take(4) },
                modifier = Modifier.weight(1f),
                label = { Text("起始年份") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
            )
            OutlinedTextField(
                value = toYear,
                onValueChange = { toYear = it.filter { ch -> ch.isDigit() }.take(4) },
                modifier = Modifier.weight(1f),
                label = { Text("终止年份") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
            )
        }

        // 排序
        SectionTitle("排序")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SORT_ITEMS.forEach { (f, label) ->
                FilterChip(
                    selected = sortField == f,
                    onClick = { sortField = f },
                    label = { Text(label) }
                )
            }
            IconButton(onClick = {
                sortOrder = if (sortOrder == SortOrder.ASCENDING) SortOrder.DESCENDING else SortOrder.ASCENDING
            }) {
                Icon(
                    if (sortOrder == SortOrder.ASCENDING) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                    contentDescription = if (sortOrder == SortOrder.ASCENDING) "升序" else "降序"
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = {
                categoryId = null; level = null; fromYear = ""; toYear = ""; sortField = SortField.RELEVANCE; sortOrder = SortOrder.ASCENDING
            }) { Text("重置") }
            TextButton(onClick = {
                showFilter = false
                runSearch()
            }) { Text("完成") }
        }
    }
}

@Composable
private fun ActiveChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 36.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text, color = MaterialTheme.colorScheme.onTertiaryContainer, style = MaterialTheme.typography.labelMedium)
            Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.width(14.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}

@Composable
private fun ResultRow(r: SearchResult, kw: String, onClick: () -> Unit) {
    val crumb = r.breadcrumb.joinToString(" / ")
    val meta = listOfNotNull(r.level.takeIf { it.isNotBlank() }, r.publish.takeIf { it.isNotBlank() })
        .joinToString(" · ")
    ListItem(
        headlineContent = {
            Text(
                "${r.lawName}${if (r.article.isNotBlank()) " · ${r.article}" else ""}",
                style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Column {
                if (crumb.isNotBlank()) {
                    Text(crumb, style = MaterialTheme.typography.labelSmall)
                }
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HighlightText(r.content, kw)
            }
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    )
    HorizontalDivider()
}

/** 在内容中以高亮背景标出命中关键词 */
@Composable
private fun HighlightText(text: String, kw: String) {
    if (kw.isBlank() || !text.contains(kw)) {
        Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        return
    }
    val annotated = buildAnnotatedString {
        var start = 0
        var idx = text.indexOf(kw, startIndex = 0)
        while (idx >= 0) {
            append(text.substring(start, idx))
            withStyle(
                SpanStyle(
                    background = MaterialTheme.colorScheme.primaryContainer,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) { append(text.substring(idx, idx + kw.length)) }
            start = idx + kw.length
            idx = text.indexOf(kw, startIndex = start)
        }
        append(text.substring(start))
    }
    Text(annotated, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
}
