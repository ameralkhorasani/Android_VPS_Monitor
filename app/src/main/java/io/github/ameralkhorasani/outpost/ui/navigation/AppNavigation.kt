package io.github.ameralkhorasani.outpost.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.ameralkhorasani.outpost.ui.screens.addserver.AddServerScreen
import io.github.ameralkhorasani.outpost.ui.screens.alerts.AlertsScreen
import io.github.ameralkhorasani.outpost.ui.screens.code.CodeScreen
import io.github.ameralkhorasani.outpost.ui.screens.docker.DockerScreen
import io.github.ameralkhorasani.outpost.ui.screens.logs.LogsScreen
import io.github.ameralkhorasani.outpost.ui.screens.monitor.MonitorScreen
import io.github.ameralkhorasani.outpost.ui.screens.overview.OverviewScreen
import io.github.ameralkhorasani.outpost.ui.screens.ports.PortsScreen
import io.github.ameralkhorasani.outpost.ui.screens.settings.SettingsScreen
import io.github.ameralkhorasani.outpost.ui.screens.terminal.TerminalScreen
import io.github.ameralkhorasani.outpost.ui.theme.NavBackground
import io.github.ameralkhorasani.outpost.ui.theme.PrimaryAccent
import io.github.ameralkhorasani.outpost.ui.theme.TextSecondary

sealed class Screen(val route: String, val title: String, val icon: ImageVector? = null) {
    object Overview : Screen("overview", "Overview", Icons.Default.Dashboard)
    object AddServer : Screen("add_server", "Add Server", Icons.Default.Add)
    object Monitor : Screen("monitor/{serverId}", "Monitor", Icons.Default.ShowChart) {
        fun createRoute(serverId: String) = "monitor/$serverId"
    }
    object Terminal : Screen("terminal/{serverId}", "Terminal", Icons.Default.Terminal) {
        fun createRoute(serverId: String) = "terminal/$serverId"
    }
    object Logs : Screen("logs/{serverId}", "Logs", Icons.Default.MonitorWeight) {
        fun createRoute(serverId: String) = "logs/$serverId"
    }
    object Code : Screen("code/{serverId}", "Code", Icons.Default.Code) {
        fun createRoute(serverId: String) = "code/$serverId"
    }
    object Docker : Screen("docker/{serverId}", "Docker", Icons.Default.Widgets) {
        fun createRoute(serverId: String) = "docker/$serverId"
    }
    object Ports : Screen("ports/{serverId}", "Ports", Icons.Default.SettingsEthernet) {
        fun createRoute(serverId: String) = "ports/$serverId"
    }
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object Alerts : Screen("alerts/{serverId}", "Alerts", Icons.Default.Notifications) {
        fun createRoute(serverId: String) = "alerts/$serverId"
    }
}

val bottomNavItems = listOf(
    Screen.Overview,
    Screen.Monitor,
    Screen.Logs,
    Screen.Terminal,
    Screen.Code
)

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    selectedServerId: String? = null,
    onServerSelected: (String) -> Unit = {}
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // With the soft keyboard up, screen height is the scarce resource. The bottom nav is
    // both unreachable behind the keyboard and a waste of the rows the terminal could be
    // using, so it steps aside until the keyboard closes.
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Scaffold(
        bottomBar = {
            if (!keyboardVisible && (currentRoute == Screen.Overview.route || currentRoute?.startsWith("monitor/") == true || currentRoute?.startsWith("logs/") == true || currentRoute?.startsWith("terminal/") == true || currentRoute?.startsWith("code/") == true)) {
                NavigationBar(
                    containerColor = NavBackground
                ) {
                    bottomNavItems.forEach { item ->
                        val targetRoute = when (item) {
                            Screen.Overview -> Screen.Overview.route
                            Screen.Monitor -> selectedServerId?.let { Screen.Monitor.createRoute(it) } ?: Screen.Overview.route
                            Screen.Logs -> selectedServerId?.let { Screen.Logs.createRoute(it) } ?: Screen.Overview.route
                            Screen.Terminal -> selectedServerId?.let { Screen.Terminal.createRoute(it) } ?: Screen.Overview.route
                            Screen.Code -> selectedServerId?.let { Screen.Code.createRoute(it) } ?: Screen.Overview.route
                            else -> item.route
                        }

                        val isSelected = currentRoute == item.route || (currentRoute != null && currentRoute.startsWith(item.route.split("/")[0]))

                        NavigationBarItem(
                            icon = { item.icon?.let { Icon(it, contentDescription = item.title) } },
                            label = { Text(item.title) },
                            selected = isSelected,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PrimaryAccent,
                                selectedTextColor = PrimaryAccent,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                                indicatorColor = NavBackground
                            ),
                            onClick = {
                                if (targetRoute != currentRoute) {
                                    navController.navigate(targetRoute) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Overview.route,
            // Consuming here stops nested Scaffolds and imePadding() from applying
            // the same system-bar insets a second time.
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            composable(Screen.Overview.route) {
                OverviewScreen(
                    onAddServerClick = { navController.navigate(Screen.AddServer.route) },
                    onServerClick = { serverId ->
                        onServerSelected(serverId)
                        navController.navigate(Screen.Monitor.createRoute(serverId))
                    },
                    onTerminalClick = { serverId ->
                        onServerSelected(serverId)
                        navController.navigate(Screen.Terminal.createRoute(serverId))
                    },
                    onAlertsClick = { serverId ->
                        navController.navigate(Screen.Alerts.createRoute(serverId))
                    },
                    onCodeClick = { serverId ->
                        onServerSelected(serverId)
                        navController.navigate(Screen.Code.createRoute(serverId))
                    },
                    onDockerClick = { serverId ->
                        onServerSelected(serverId)
                        navController.navigate(Screen.Docker.createRoute(serverId))
                    },
                    onPortsClick = { serverId ->
                        onServerSelected(serverId)
                        navController.navigate(Screen.Ports.createRoute(serverId))
                    },
                    onSettingsClick = { navController.navigate(Screen.Settings.route) }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(Screen.AddServer.route) {
                AddServerScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onServerSaved = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.Monitor.route,
                arguments = listOf(navArgument("serverId") { type = NavType.StringType })
            ) { backStackEntry ->
                val serverId = backStackEntry.arguments?.getString("serverId") ?: ""
                MonitorScreen(
                    serverId = serverId,
                    onNavigateBack = { navController.popBackStack() },
                    onTabSelected = { tab ->
                        when (tab) {
                            "Terminal" -> navController.navigate(Screen.Terminal.createRoute(serverId))
                            "Logs" -> navController.navigate(Screen.Logs.createRoute(serverId))
                        }
                    }
                )
            }

            composable(
                route = Screen.Terminal.route,
                arguments = listOf(navArgument("serverId") { type = NavType.StringType })
            ) { backStackEntry ->
                val serverId = backStackEntry.arguments?.getString("serverId") ?: ""
                TerminalScreen(
                    serverId = serverId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.Code.route,
                arguments = listOf(navArgument("serverId") { type = NavType.StringType })
            ) { backStackEntry ->
                val serverId = backStackEntry.arguments?.getString("serverId") ?: ""
                CodeScreen(
                    serverId = serverId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.Docker.route,
                arguments = listOf(navArgument("serverId") { type = NavType.StringType })
            ) { backStackEntry ->
                val serverId = backStackEntry.arguments?.getString("serverId") ?: ""
                DockerScreen(
                    serverId = serverId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.Ports.route,
                arguments = listOf(navArgument("serverId") { type = NavType.StringType })
            ) { backStackEntry ->
                val serverId = backStackEntry.arguments?.getString("serverId") ?: ""
                PortsScreen(
                    serverId = serverId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.Logs.route,
                arguments = listOf(navArgument("serverId") { type = NavType.StringType })
            ) { backStackEntry ->
                val serverId = backStackEntry.arguments?.getString("serverId") ?: ""
                LogsScreen(
                    serverId = serverId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.Alerts.route,
                arguments = listOf(navArgument("serverId") { type = NavType.StringType })
            ) { backStackEntry ->
                val serverId = backStackEntry.arguments?.getString("serverId") ?: ""
                AlertsScreen(
                    serverId = serverId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
