package com.lawrefbook.unified.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lawrefbook.unified.R
import com.lawrefbook.unified.ui.favorites.FavoritesScreen
import com.lawrefbook.unified.ui.history.HistoryScreen
import com.lawrefbook.unified.ui.home.HomeScreen
import com.lawrefbook.unified.ui.lawlist.LawListScreen
import com.lawrefbook.unified.ui.reader.ReaderScreen
import com.lawrefbook.unified.ui.search.SearchScreen
import com.lawrefbook.unified.ui.categories.LawCategoriesScreen
import com.lawrefbook.unified.ui.cases.RulesScreen
import com.lawrefbook.unified.ui.cases.SimilarCasesScreen
import com.lawrefbook.unified.ui.cases.CaseDetailScreen
import com.lawrefbook.unified.ui.settings.SettingsScreen
import com.lawrefbook.unified.ui.settings.AboutScreen
import com.lawrefbook.unified.ui.settings.ThemeSettingsScreen
import com.lawrefbook.unified.ui.settings.ReaderSettingsScreen
import com.lawrefbook.unified.ui.settings.DataSettingsScreen

sealed class Screen(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : Screen("home", "首页", Icons.Filled.Home)
    object Search : Screen("search", "检索", Icons.Filled.Search)
    object Favorites : Screen("favorites", "收藏", Icons.Filled.Favorite)
    object History : Screen("history", "历史", Icons.Filled.History)
    object Settings : Screen("settings", "设置", Icons.Filled.Settings)
}

// 底部导航栏（含宽屏侧栏）：仅保留 4 项。检索入口已移至首页顶部搜索框。
private val topLevel = listOf(Screen.Home, Screen.Favorites, Screen.History, Screen.Settings)

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val navBackStackEntry by nav.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val useRail = LocalConfiguration.current.screenWidthDp >= 600

    val showBar = topLevel.any { it.route == currentDestination?.route }

    val onNav: (Screen) -> Unit = { screen ->
        nav.navigate(screen.route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    if (useRail) {
        Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxHeight()
            ) {
                Spacer(Modifier.height(24.dp))
                topLevel.forEach { screen ->
                    NavigationRailItem(
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = { onNav(screen) },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        alwaysShowLabel = true
                    )
                }
            }
            Box(Modifier.fillMaxHeight().weight(1f)) {
                AppNavHost(nav = nav, modifier = Modifier.fillMaxSize())
            }
        }
    } else {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            bottomBar = {
                if (showBar) {
                    NavigationBar {
                        topLevel.forEach { screen ->
                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = screen.label) },
                                label = { Text(screen.label) },
                                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                onClick = { onNav(screen) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            AppNavHost(nav = nav, modifier = Modifier.fillMaxSize().padding(innerPadding))
        }
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = nav,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) { HomeScreen(nav) }
        composable(Screen.Search.route) { SearchScreen(nav) }
        composable(Screen.Favorites.route) { FavoritesScreen(nav) }
        composable(Screen.History.route) { HistoryScreen(nav) }
        composable(Screen.Settings.route) { SettingsScreen(nav) }
        composable("theme_settings") { ThemeSettingsScreen(nav) }
        composable("reader_settings") { ReaderSettingsScreen(nav) }
        composable("data_settings") { DataSettingsScreen(nav) }
        composable("about") { AboutScreen(nav) }
        composable("law_categories") { LawCategoriesScreen(nav) }
        composable("rules") { RulesScreen(nav) }
        composable("similar_cases") { SimilarCasesScreen(nav) }
        composable(
            route = "case/{caseId}",
            arguments = listOf(navArgument("caseId") { type = NavType.StringType })
        ) {
            CaseDetailScreen(nav, it.arguments?.getString("caseId") ?: "")
        }
        composable(
            route = "lawlist/{categoryId}",
            arguments = listOf(navArgument("categoryId") { type = NavType.StringType })
        ) {
            LawListScreen(nav, it.arguments?.getString("categoryId") ?: "")
        }
        composable(
            route = "reader/{lawId}?article={article}",
            arguments = listOf(
                navArgument("lawId") { type = NavType.StringType },
                navArgument("article") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            ReaderScreen(
                nav,
                it.arguments?.getString("lawId") ?: "",
                it.arguments?.getString("article")
            )
        }
    }
}
