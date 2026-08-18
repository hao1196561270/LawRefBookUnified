package com.lawrefbook.unified.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import android.content.Intent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.lawrefbook.unified.data.FavoritesEntity
import com.lawrefbook.unified.data.HistoryEntity
import com.lawrefbook.unified.data.model.FlatItem
import com.lawrefbook.unified.data.model.LawEntity
import com.lawrefbook.unified.data.model.LawGroup
import com.lawrefbook.unified.data.model.displayName
import com.lawrefbook.unified.data.model.flatten
import com.lawrefbook.unified.data.model.lawYear
import com.lawrefbook.unified.ui.components.BottomSheet
import com.lawrefbook.unified.ui.rememberRepository
import com.lawrefbook.unified.ui.rememberSettings
import kotlinx.coroutines.launch

/** 阅读页渲染节点：标题或法条 */
sealed class RenderNode {
    data class Heading(val level: Int, val title: String) : RenderNode()
    data class Item(val article: String, val content: String) : RenderNode()
}

/** 阿拉伯数字 → 中文数字（1-99） */
private fun arabicToChinese(n: Int): String {
    val digits = listOf("零", "一", "二", "三", "四", "五", "六", "七", "八", "九")
    val units = listOf("", "十", "二十", "三十", "四十", "五十", "六十", "七十", "八十", "九十")
    return when {
        n in 1..9 -> digits[n]
        n in 10..19 -> "十" + if (n % 10 == 0) "" else digits[n % 10]
        n in 20..99 -> {
            val tens = n / 10
            val ones = n % 10
            units[tens] + if (ones == 0) "" else digits[ones]
        }
        else -> n.toString()
    }
}

/** 中文数字 → 阿拉伯数字（支持 一~九十九） */
private fun chineseToArabic(s: String): Int? {
    val digitMap = mapOf('一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5,
        '六' to 6, '七' to 7, '八' to 8, '九' to 9, '零' to 0)
    if (s.isEmpty()) return null
    var result = 0
    var hasTens = false
    for (ch in s) {
        when {
            ch == '十' -> {
                hasTens = true
                if (result == 0) result = 1 // 十 = 10
                result *= 10
            }
            digitMap.containsKey(ch) -> {
                if (hasTens && result > 10) return null // invalid
                result += digitMap[ch]!!
            }
            else -> return null
        }
    }
    return if (result in 1..99) result else null
}

/** 将搜索关键词智能转换为条文号：纯数字/中文数字 → 第X条 */
private fun expandArticleKeyword(kw: String): String {
    val num = kw.toIntOrNull() ?: chineseToArabic(kw) ?: return kw
    return "第${arabicToChinese(num)}条"
}

/** 将解析后的嵌套 Group 树扁平为有序渲染节点 */
private fun buildNodes(group: LawGroup): List<RenderNode> {
    val out = mutableListOf<RenderNode>()
    for (g in group.groups) {
        out.add(RenderNode.Heading(g.level, g.title))
        out.addAll(buildNodes(g))
    }
    for (it in group.items) {
        out.add(RenderNode.Item(it.article, it.content))
    }
    return out
}

@Composable
fun ReaderScreen(nav: NavHostController, lawId: String, targetArticle: String?) {
    val repo = rememberRepository()
    val settings = rememberSettings()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val fontSize by settings.fontSize.collectAsState(initial = 17f)
    val lineSpacing by settings.lineSpacing.collectAsState(initial = 1.5f)
    val articleSpacing by settings.articleSpacing.collectAsState(initial = 8f)

    var law by remember { mutableStateOf<LawEntity?>(null) }
    var nodes by remember { mutableStateOf<List<RenderNode>>(emptyList()) }
    var flat by remember { mutableStateOf<List<FlatItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var isFav by remember { mutableStateOf(false) }
    var meta by remember { mutableStateOf<com.lawrefbook.unified.data.LawMeta?>(null) }
    var versionCount by remember { mutableStateOf(0) }
    var versionList by remember { mutableStateOf<List<LawEntity>>(emptyList()) }
    var showHistoryChange by remember { mutableStateOf(false) }
    // 历史修正（article → 修正列表），用于脚注标注
    var amendments by remember { mutableStateOf<Map<String, List<com.lawrefbook.unified.data.Amendment>>>(emptyMap()) }
    var amendDialog by remember { mutableStateOf<List<com.lawrefbook.unified.data.Amendment>?>(null) }
    // 本页搜索
    var showPageSearch by remember { mutableStateOf(false) }
    var searchKeyword by remember { mutableStateOf("") }

    val listState = rememberLazyListState()
    // 目录半屏抽屉显示状态
    var showToc by remember { mutableStateOf(false) }
    val headings = nodes.filterIsInstance<RenderNode.Heading>()

    // 长按复制：剪贴板 + 提示宿主（浮层，不影响正文布局）
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(lawId) {
        val entity = repo.getLawById(lawId)
        law = entity
        if (entity != null) {
            val tree = repo.parseLaw(entity)
            nodes = buildNodes(tree)
            flat = tree.flatten()
            meta = repo.parseLawMeta(entity)
            val versions = repo.getLawVersions(lawId)
            versionCount = versions.size
            versionList = versions
            amendments = repo.getAmendments(lawId).groupBy { it.article ?: "" }
            repo.addHistory(HistoryEntity(lawId = entity.id, lawName = entity.name))
            isFav = repo.isFavorite("$lawId|${targetArticle ?: ""}")
        }
        loading = false
    }

    LaunchedEffect(nodes, targetArticle) {
        // 深链定位到指定条文；普通进入默认从顶部开始
        if (!targetArticle.isNullOrBlank()) {
            val idx = nodes.indexOfFirst { it is RenderNode.Item && it.article == targetArticle }
            if (idx >= 0) listState.scrollToItem(idx)
        }
    }

    // 本页搜索定位：条文号或关键词（正文中第一个命中）
    fun doPageSearch() {
        val kw = searchKeyword.trim()
        if (kw.isEmpty()) return
        // 若为纯数字或中文数字，智能转换为"第X条"格式
        val expanded = expandArticleKeyword(kw)
        val idx = nodes.indexOfFirst {
            when (it) {
                is RenderNode.Item ->
                    it.article.contains(kw) || it.content.contains(kw) ||
                            (expanded != kw && (it.article.contains(expanded) || it.content.contains(expanded)))
                is RenderNode.Heading -> it.title.contains(kw) ||
                        (expanded != kw && it.title.contains(expanded))
            }
        }
        scope.launch {
            if (idx >= 0) {
                listState.scrollToItem(idx)
                val located = when (val n = nodes[idx]) {
                    is RenderNode.Item -> n.article
                    is RenderNode.Heading -> n.title
                }
                snackbarHostState.showSnackbar("已定位到 $located")
            } else {
                snackbarHostState.showSnackbar("未找到「$kw」")
            }
        }
    }

    val lawName = law?.name ?: "法条"
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(lawName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val shareText = buildString {
                            append(lawName)
                            if (targetArticle != null) {
                                val item = flat.firstOrNull { it.article == targetArticle }
                                if (item != null) {
                                    append("\n${item.article}\n${item.content}")
                                }
                            }
                        }
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "分享法条"))
                    }) {
                        Icon(Icons.Filled.Share, "分享")
                    }
                    IconButton(onClick = {
                        showPageSearch = !showPageSearch
                        if (!showPageSearch) searchKeyword = ""
                    }) {
                        Icon(Icons.Filled.Search, "本页搜索")
                    }
                    IconButton(onClick = { showToc = true }) {
                        Icon(Icons.Filled.Menu, "目录")
                    }
                    IconButton(onClick = {
                        val t = targetArticle ?: ""
                        val favId = "$lawId|$t"
                        scope.launch {
                            if (isFav) {
                                repo.removeFavorite(favId)
                                isFav = false
                            } else {
                                val item = flat.firstOrNull { it.article == t }
                                repo.addFavorite(
                                    FavoritesEntity(
                                        id = favId,
                                        lawId = lawId,
                                        lawName = lawName,
                                        article = t,
                                        content = item?.content ?: ""
                                    )
                                )
                                isFav = true
                            }
                        }
                    }) {
                        Icon(
                            if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            "收藏"
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
            if (loading) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = padding,
                    verticalArrangement = Arrangement.spacedBy(articleSpacing.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // ── 本页搜索 ──
                    if (showPageSearch) {
                        item(key = "page_search") {
                            androidx.compose.material3.OutlinedTextField(
                                value = searchKeyword,
                                onValueChange = { searchKeyword = it },
                                placeholder = { Text("输入条文号或关键词定位", style = MaterialTheme.typography.bodySmall) },
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = { doPageSearch() }) {
                                        Icon(Icons.Filled.Search, "搜索")
                                    }
                                },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Search
                                ),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                    onSearch = { doPageSearch() }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // ── 章节目录面板（AnimatedVisibility 滑入） ──
                    item(key = "toc_panel") {
                        AnimatedVisibility(
                            visible = showToc,
                            enter = expandVertically(expandFrom = Alignment.Top),
                            exit = shrinkVertically(shrinkTowards = Alignment.Top)
                        ) {
                            androidx.compose.material3.Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                                shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        "📑 章节目录",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    if (headings.isEmpty()) {
                                        Text("（暂无章节）", style = MaterialTheme.typography.bodySmall)
                                    } else {
                                        headings.forEach { h ->
                                            val idx = nodes.indexOf(h)
                                            val indent = ((h.level - 1).coerceAtLeast(0) * 16).dp
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        scope.launch {
                                                            if (idx >= 0) listState.scrollToItem(idx)
                                                            showToc = false
                                                        }
                                                    }
                                                    .padding(
                                                        start = indent + 4.dp,
                                                        end = 4.dp,
                                                        top = 6.dp,
                                                        bottom = 6.dp
                                                    ),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    Modifier
                                                        .width(3.dp)
                                                        .height(14.dp)
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .background(
                                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                                        )
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    h.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    softWrap = true
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── 元信息卡（发文字号/机关/日期/效力） ──
                    val m = meta
                    if (m != null && (m.publishDate != null || m.organ != null)) {
                        item {
                            androidx.compose.material3.Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp),
                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                                )
                            ) {
                                Column(Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)) {
                                    m.docNo?.takeIf { it.isNotBlank() }?.let {
                                        MetaRow("📄 发文字号", it)
                                    }
                                    m.organ?.takeIf { it.isNotBlank() }?.let {
                                        MetaRow("🏛️ 发布机关", it)
                                    }
                                    m.publishDate?.let { MetaRow("📅 发布日期", it) }
                                    m.effectiveDate?.let { MetaRow("📅 实施日期", it) }
                                    // 效力状态：彩色药丸徽章
                                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                        Text(
                                            "当前效力",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.width(72.dp)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    if (m.valid) Color(0xFF2E7D32).copy(alpha = 0.12f)
                                                    else MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                                )
                                                .padding(horizontal = 10.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                if (m.valid) "有效" else "失效",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (m.valid) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── 历史变更：蓝色时间线版本列表 ──
                    if (versionCount > 1) {
                        item {
                            androidx.compose.material3.Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                                ),
                                onClick = { showHistoryChange = !showHistoryChange }
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "历史变更",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            "共 $versionCount 个版本",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            Icons.Filled.KeyboardArrowDown,
                                            contentDescription = if (showHistoryChange) "收起" else "展开",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .rotate(if (showHistoryChange) 180f else 0f)
                                        )
                                    }
                                    // 蓝色时间线展开
                                    androidx.compose.animation.AnimatedVisibility(visible = showHistoryChange) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 16.dp, end = 12.dp, bottom = 8.dp)
                                        ) {
                                            val sorted = versionList.sortedWith(
                                                compareByDescending<LawEntity> { it.lawYear() ?: Int.MAX_VALUE }
                                            )
                                            sorted.forEachIndexed { index, v ->
                                                val isCurrent = v.id == lawId
                                                val year = v.lawYear()?.toString() ?: ""
                                                val publishDate = v.publish ?: ""
                                                val monthDay = if (publishDate.length >= 10) {
                                                    publishDate.substring(5, 10) // MM-dd
                                                } else ""
                                                val isLast = index == sorted.lastIndex

                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { nav.navigate("reader/${v.id}") }
                                                        .background(
                                                            if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                            else Color.Transparent
                                                        ),
                                                    verticalAlignment = Alignment.Top
                                                ) {
                                                    // 左侧：年月日
                                                    Column(
                                                        horizontalAlignment = Alignment.End,
                                                        modifier = Modifier.width(56.dp).padding(end = 8.dp, top = 8.dp)
                                                    ) {
                                                        if (year.isNotEmpty()) {
                                                            Text(
                                                                year,
                                                                style = MaterialTheme.typography.labelMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                                lineHeight = 14.sp
                                                            )
                                                        }
                                                        if (monthDay.isNotEmpty()) {
                                                            Text(
                                                                monthDay,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                lineHeight = 12.sp
                                                            )
                                                        }
                                                    }

                                                    // 中间：蓝色圆点 + 竖线
                                                    Box(
                                                        modifier = Modifier.width(12.dp),
                                                        contentAlignment = Alignment.TopCenter
                                                    ) {
                                                        // 竖线
                                                        Box(
                                                            modifier = Modifier
                                                                .width(2.dp)
                                                                .height(if (isLast) 12.dp else 52.dp)
                                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                                                .align(Alignment.TopCenter)
                                                        )
                                                        // 蓝色圆点
                                                        Box(
                                                            modifier = Modifier
                                                                .size(12.dp)
                                                                .clip(androidx.compose.foundation.shape.CircleShape)
                                                                .background(
                                                                    if (isCurrent) MaterialTheme.colorScheme.primary
                                                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                                                )
                                                                .align(Alignment.TopCenter)
                                                        )
                                                    }

                                                    // 右侧：法条名称 + 箭头
                                                    Row(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            v.displayName(),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        if (isCurrent) {
                                                            Text(
                                                                "当前",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        } else {
                                                            Icon(
                                                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                                contentDescription = "查看",
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    items(
                        items = nodes,
                        key = { n ->
                            when (n) {
                                is RenderNode.Heading -> "h:${n.title}"
                                is RenderNode.Item -> "i:${n.article}|${n.content}"
                            }
                        }
                    ) { node ->
                        when (node) {
                            is RenderNode.Heading -> {
                                val size = when (node.level) {
                                    1 -> 20.sp
                                    2 -> 18.sp
                                    3 -> 16.sp
                                    else -> 15.sp
                                }
                                Text(
                                    node.title,
                                    style = MaterialTheme.typography.titleLarge.copy(fontSize = size),
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }
                            is RenderNode.Item -> {
                                val highlight = targetArticle != null && node.article == targetArticle
                                // 整条纯文本（条号 + 正文），字符顺序与显示一致，
                                // 用于按字符索引计算选中子串。
                                val fullText = buildString {
                                    if (node.article.isNotBlank()) append(node.article)
                                    if (node.content.isNotBlank()) {
                                        if (node.article.isNotBlank()) append("\n")
                                        append(node.content)
                                    }
                                }
                                SelectableText(
                                    text = fullText,
                                    highlightBackground = highlight,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = fontSize.sp,
                                        lineHeight = (fontSize * lineSpacing).sp
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    onCopy = { txt ->
                                        clipboardManager.setText(AnnotatedString(txt))
                                        scope.launch {
                                            snackbarHostState.showSnackbar("已复制 ${txt.length} 字")
                                        }
                                    }
                                )
                                // 历史修正脚注标注：该条有被修正 → 显示标记，点击弹窗查看不同之处
                                val amends = amendments[node.article.ifBlank { "序言" }]
                                if (!amends.isNullOrEmpty()) {
                                    val years = amends.map { it.year }.distinct().sorted()
                                    androidx.compose.material3.Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 2.dp, bottom = 4.dp),
                                        onClick = { amendDialog = amends },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = androidx.compose.material3.CardDefaults.cardColors(
                                            containerColor = Color(0xFFFFF3E0)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "✎ ${years.joinToString("/")} 年修正",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFFE65100),
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                "${amends.size} 处不同",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFFBF360C)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "查看",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFFE65100)
                                            )
                                            Icon(
                                                Icons.Filled.KeyboardArrowDown,
                                                contentDescription = "查看",
                                                tint = Color(0xFFE65100),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    // 历史修正弹窗：点击脚注标注时展示不同之处
    val dialogAmends = amendDialog
    if (dialogAmends != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { amendDialog = null },
            title = {
                Text(
                    "历史修正 · ${dialogAmends.map { it.year }.distinct().sorted().joinToString("/")} 年",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    dialogAmends.forEach { a ->
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                "${a.year}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                a.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { amendDialog = null }) { Text("关闭") }
            }
        )
    }

    // 目录半屏抽屉：只露出顶部圆角、高度半屏，背景正文仍可见；
    // 目录项文字过长自动换行（softWrap 默认开启）
    BottomSheet(
        visible = showToc,
        onDismiss = { showToc = false },
        heightFraction = 0.4f
    ) {
        Text("本章目录", style = MaterialTheme.typography.titleLarge)
        if (headings.isEmpty()) {
            Text("（暂无章节）", style = MaterialTheme.typography.bodyMedium)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                headings.forEach { h ->
                    val idx = nodes.indexOf(h)
                    Text(
                        h.title,
                        style = MaterialTheme.typography.bodyLarge,
                        softWrap = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    if (idx >= 0) listState.scrollToItem(idx)
                                    showToc = false
                                }
                            }
                            .padding(horizontal = 4.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

/** 元信息卡行：标签 + 值 */
@Composable
private fun MetaRow(label: String, value: String, effective: Boolean? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = when {
                effective == true -> Color(0xFF2E7D32)
                effective == false -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

