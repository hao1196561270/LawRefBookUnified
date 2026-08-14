package com.lawrefbook.unified.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.lawrefbook.unified.data.BuiltinData
import com.lawrefbook.unified.data.FavoritesEntity
import com.lawrefbook.unified.data.HistoryEntity
import com.lawrefbook.unified.data.model.FlatItem
import com.lawrefbook.unified.data.model.LawEntity
import com.lawrefbook.unified.data.model.LawGroup
import com.lawrefbook.unified.data.model.flatten
import com.lawrefbook.unified.ui.rememberRepository
import com.lawrefbook.unified.ui.rememberSettings
import kotlinx.coroutines.launch

/** 阅读页渲染节点：标题或法条 */
sealed class RenderNode {
    data class Heading(val level: Int, val title: String) : RenderNode()
    data class Item(val article: String, val content: String) : RenderNode()
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

    val fontSize by settings.fontSize.collectAsState(initial = 17f)
    val lineSpacing by settings.lineSpacing.collectAsState(initial = 1.5f)
    val articleSpacing by settings.articleSpacing.collectAsState(initial = 8f)
    val dataUpdatedAt by settings.dataUpdatedAt.collectAsState(initial = 0L)

    var law by remember { mutableStateOf<LawEntity?>(null) }
    var nodes by remember { mutableStateOf<List<RenderNode>>(emptyList()) }
    var flat by remember { mutableStateOf<List<FlatItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var isFav by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
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
            repo.addHistory(HistoryEntity(lawId = entity.id, lawName = entity.name))
            isFav = repo.isFavorite("$lawId|${targetArticle ?: ""}")
        }
        loading = false
    }

    LaunchedEffect(nodes, targetArticle) {
        if (targetArticle.isNullOrBlank()) return@LaunchedEffect
        val idx = nodes.indexOfFirst { it is RenderNode.Item && it.article == targetArticle }
        if (idx >= 0) listState.scrollToItem(idx)
    }

    val lawName = law?.name ?: "法条"
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet {
                Text("本章目录", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
                if (headings.isEmpty()) {
                    Text("（暂无章节）", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                }
                headings.forEach { h ->
                    val idx = nodes.indexOf(h)
                    Text(
                        h.title,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    if (idx >= 0) listState.scrollToItem(idx)
                                    drawerState.close()
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    ) {
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
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
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
                    item {
                        androidx.compose.material3.Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Text(
                                "💡 数据来源：LawRefBook/Laws · 最近同步 ${
                                    if (dataUpdatedAt == 0L) BuiltinData.DATE
                                    else java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(java.util.Date(dataUpdatedAt))
                                }",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(12.dp)
                            )
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
                            }
                        }
                    }
                }
            }
        }
    }
}
