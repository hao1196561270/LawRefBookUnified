package com.lawrefbook.unified.ui.lawlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.lawrefbook.unified.data.model.LawEntity
import com.lawrefbook.unified.data.model.displayName
import com.lawrefbook.unified.data.model.sortedByTime
import com.lawrefbook.unified.ui.rememberRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LawListScreen(nav: NavHostController, categoryId: String) {
    val repo = rememberRepository()
    var laws by remember { mutableStateOf<List<LawEntity>>(emptyList()) }
    var title by remember { mutableStateOf("法规") }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(categoryId) {
        val cat = repo.getCategories().firstOrNull { it.id == categoryId }
        title = cat?.name ?: "法规"
        laws = repo.getLaws(categoryId).sortedByTime()
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
    ) { contentPadding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 4.dp)
            ) {
                items(laws) { law ->
                    LawRow(law) { nav.navigate("reader/${law.id}") }
                }
            }
        }
    }
}

@Composable
private fun LawRow(law: LawEntity, onClick: () -> Unit) {
    val sub = (law.subTitle ?: law.level).takeIf { it.isNotBlank() }
    ListItem(
        headlineContent = { Text(law.displayName(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = sub?.let { { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    )
    HorizontalDivider()
}
