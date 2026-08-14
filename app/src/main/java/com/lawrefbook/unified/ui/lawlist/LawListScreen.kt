package com.lawrefbook.unified.ui.lawlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.lawrefbook.unified.data.model.LawEntity
import com.lawrefbook.unified.ui.components.BottomSheet
import com.lawrefbook.unified.ui.rememberRepository

private val LEVELS = listOf("全部", "法律", "行政法规", "司法解释", "部门规章")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LawListScreen(nav: NavHostController, categoryId: String) {
    val repo = rememberRepository()
    var laws by remember { mutableStateOf<List<LawEntity>>(emptyList()) }
    var title by remember { mutableStateOf("法规") }
    var loading by remember { mutableStateOf(true) }
    var selectedLevel by remember { mutableStateOf("全部") }
    var sheetVisible by remember { mutableStateOf(false) }

    LaunchedEffect(categoryId) {
        val cat = repo.getCategories().firstOrNull { it.id == categoryId }
        title = cat?.name ?: "法规"
        laws = repo.getLaws(categoryId)
        loading = false
    }

    val filtered = if (selectedLevel == "全部") laws else laws.filter { it.level.contains(selectedLevel) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { sheetVisible = true }) {
                        Icon(Icons.Filled.Tune, "筛选")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LEVELS.forEach { lv ->
                    FilterChip(
                        selected = selectedLevel == lv,
                        onClick = { selectedLevel = lv },
                        label = { Text(lv) }
                    )
                }
            }
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(filtered) { law ->
                        LawRow(law) { nav.navigate("reader/${law.id}") }
                    }
                }
            }
        }
    }

    BottomSheet(visible = sheetVisible, onDismiss = { sheetVisible = false }) {
        Text("按效力层级筛选", style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LEVELS.forEach { lv ->
                FilterChip(
                    selected = selectedLevel == lv,
                    onClick = { selectedLevel = lv; sheetVisible = false },
                    label = { Text(lv) },
                    modifier = Modifier.fillMaxWidth()
                )
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
