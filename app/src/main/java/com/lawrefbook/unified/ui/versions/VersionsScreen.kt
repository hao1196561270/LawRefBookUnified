package com.lawrefbook.unified.ui.versions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.lawrefbook.unified.data.model.LawEntity
import com.lawrefbook.unified.ui.rememberRepository

/** 从版本名提取年份（X修正案（2018年）→ "2018"；主体无年份 → null） */
private fun yearOf(name: String): String? =
    Regex("（(\\d{4})年）").find(name)?.groupValues?.get(1)

/**
 * 法规版本时间线页：展示某部法规的主体 + 历次修正版本，
 * 按时间（旧→新）排列，主体（现行）置底，点击版本进入阅读。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionsScreen(nav: NavHostController, lawId: String) {
    val repo = rememberRepository()
    var versions by remember { mutableStateOf<List<LawEntity>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var title by remember { mutableStateOf("历史版本") }

    LaunchedEffect(lawId) {
        val list = repo.getLawVersions(lawId)
        versions = list
        title = list.firstOrNull()?.name?.let { stripVersionSuffix(it) } ?: "历史版本"
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                versions.isEmpty() -> Text(
                    "未找到该法规",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                versions.size == 1 -> {
                    // 无版本族：直接进入阅读（理论上前置判断已拦截，这里兜底）
                    LaunchedEffect(Unit) {
                        nav.navigate("reader/$lawId") { popUpTo("versions/$lawId") { inclusive = true } }
                    }
                }
                else -> TimelineList(versions, onOpen = { nav.navigate("reader/${it.id}") })
            }
        }
    }
}

@Composable
private fun TimelineList(versions: List<LawEntity>, onOpen: (LawEntity) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text(
                "历史版本 · 共 ${versions.size} 个",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
        }
        items(versions, key = { it.id }) { law ->
            val year = yearOf(law.name)
            val isCurrent = year == null   // 无年份 = 主体（现行）
            TimelineRow(
                law = law,
                year = year,
                isCurrent = isCurrent,
                isLast = law.id == versions.last().id,
                onClick = { onOpen(law) }
            )
        }
    }
}

@Composable
private fun TimelineRow(
    law: LawEntity,
    year: String?,
    isCurrent: Boolean,
    isLast: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalAlignment = Alignment.Top
    ) {
        // 左侧时间轴：圆点 + 竖线
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(
                        if (isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primaryContainer,
                        CircleShape
                    )
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(72.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        // 右侧版本卡片
        Card(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 12.dp)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLowest
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        year ?: "现行版本",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        law.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                    law.publish?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            "发布于 $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "阅读",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
    if (!isLast) HorizontalDivider(
        modifier = Modifier.padding(start = 60.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    )
}

/** 去掉版本后缀（修正案（2018年）/（2018年）），得到主体名 */
private fun stripVersionSuffix(name: String): String =
    Regex("(?:修正案)?（\\d{4}年）$").replace(name, "").trim()
