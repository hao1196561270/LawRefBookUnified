package com.lawrefbook.unified.ui.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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

    Scaffold(topBar = { TopAppBar(title = { Text("收藏") }) }, containerColor = MaterialTheme.colorScheme.surface) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("暂无收藏", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items) { f ->
                    val enc = URLEncoder.encode(f.article, "UTF-8")
                    ListItem(
                        headlineContent = {
                            Text(
                                "${f.lawName}${if (f.article.isNotBlank()) " · ${f.article}" else ""}",
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        supportingContent = if (f.content.isNotBlank())
                            ({ Text(f.content, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall) }) else null,
                        trailingContent = {
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
