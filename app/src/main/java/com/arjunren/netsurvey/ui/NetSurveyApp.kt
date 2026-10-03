package com.arjunren.netsurvey.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch

enum class AppScreen(val route: String, val title: String, val icon: ImageVector) {
    Dashboard("dashboard", "Dashboard", Icons.Default.Dashboard),
    Projects("projects", "Projects", Icons.Default.Apartment),
    Scanner("scanner", "Wi-Fi Scanner", Icons.Default.WifiFind),
    Signal("signal", "Signal Meter", Icons.Default.Speed),
    Survey("survey", "Survey", Icons.Default.Map),
    Heatmaps("heatmaps", "Heatmaps", Icons.Default.Analytics),
    Channels("channels", "Channel Analyzer", Icons.Default.NetworkWifi),
    Planner("planner", "AP Planner", Icons.Default.Assessment),
    Assets("assets", "AP Inventory", Icons.Default.Inventory2),
    Reports("reports", "Reports & Backup", Icons.Default.Assessment),
    Settings("settings", "Settings", Icons.Default.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetSurveyApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: AppScreen.Dashboard.route
    val activeScreen = AppScreen.entries.firstOrNull { it.route == route } ?: AppScreen.Dashboard
    val activeProject by viewModel.activeProject.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbar.showSnackbar(when (message) {
                is UiMessage.Error -> message.text
                is UiMessage.Success -> message.text
            })
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 22.dp)) {
                    Text("NETSURVEY", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Offline RF field toolkit", style = MaterialTheme.typography.bodyMedium)
                    activeProject?.let { Text(it.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp)) }
                }
                HorizontalDivider()
                AppScreen.entries.forEach { screen ->
                    NavigationDrawerItem(
                        label = { Text(screen.title) },
                        icon = { Icon(screen.icon, contentDescription = null) },
                        selected = screen.route == route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        },
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(Modifier.fillMaxWidth()) {
                            Column {
                                Text(activeScreen.title)
                                activeProject?.let { Text(it.name, style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open navigation")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                )
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = AppScreen.Dashboard.route,
                modifier = Modifier.padding(padding).safeDrawingPadding(),
            ) {
                composable(AppScreen.Dashboard.route) { DashboardScreen(viewModel) { navController.navigate(it.route) } }
                composable(AppScreen.Projects.route) { ProjectsScreen(viewModel) }
                composable(AppScreen.Scanner.route) { ScannerScreen(viewModel) }
                composable(AppScreen.Signal.route) { SignalMeterScreen(viewModel) }
                composable(AppScreen.Survey.route) { SurveyScreen(viewModel) }
                composable(AppScreen.Heatmaps.route) { HeatmapScreen(viewModel) }
                composable(AppScreen.Channels.route) { ChannelAnalyzerScreen(viewModel) }
                composable(AppScreen.Planner.route) { PlannerScreen(viewModel) }
                composable(AppScreen.Assets.route) { AssetsScreen(viewModel) }
                composable(AppScreen.Reports.route) { ReportsScreen(viewModel) }
                composable(AppScreen.Settings.route) { SettingsScreen(viewModel) }
            }
        }
    }
}
