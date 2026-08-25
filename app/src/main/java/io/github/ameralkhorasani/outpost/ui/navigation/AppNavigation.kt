package io.github.ameralkhorasani.outpost.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import io.github.ameralkhorasani.outpost.ui.screens.docker.DockerScreen
import io.github.ameralkhorasani.outpost.ui.screens.logs.LogsScreen
import io.github.ameralkhorasani.outpost.ui.screens.monitor.MonitorScreen
import io.github.ameralkhorasani.outpost.ui.screens.overview.OverviewScreen
import io.github.ameralkhorasani.outpost.ui.screens.ports.PortsScreen
import io.github.ameralkhorasani.outpost.ui.screens.settings.SettingsScreen
import io.github.ameralkhorasani.outpost.ui.screens.terminal.TerminalScreen
import io.github.ameralkhorasani.outpost.ui.theme.NavBackground
import io.github.ameralkhorasani.outpost.ui.theme.PrimaryAccent
import io.github.ameralkhorasani.outpost.ui.theme.TextDisabled
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

/**
 * The bottom bar carries the dashboard plus the per-server views that have something to
 * show on their own. The terminal is opened from a server card instead: it is a session,
 * not a view, and a tab that reconnects a shell every time it is tapped is worse than a
 * deliberate button.
 */
val bottomNavItems = listOf(
    Screen.Overview,
    Screen.Monitor,
    Screen.Logs,
    Screen.Docker
)

/** Routes that keep the bottom bar on screen. */
private val BOTTOM_BAR_PREFIXES = listOf("monitor/", "logs/", "terminal/", "docker/")

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

    val showBottomBar = currentRoute == Screen.Overview.route ||
        BOTTOM_BAR_PREFIXES.any { currentRoute?.startsWith(it) == true }

    Scaffold(
        bottomBar = {
            if (!keyboardVisible && showBottomBar) {
                NavigationBar(
                    containerColor = NavBackground
                ) {
                    bottomNavItems.forEach { item ->
                        val targetRoute = when (item) {
                            Screen.Overview -> Screen.Overview.route
                            Screen.Monitor -> selectedServerId?.let { Screen.Monitor.createRoute(it) }
                            Screen.Logs -> selectedServerId?.let { Screen.Logs.createRoute(it) }
                            Screen.Docker -> selectedServerId?.let { Screen.Docker.createRoute(it) }
                            else -> item.route
                        }

                        // The per-server tabs have nowhere to go until a server has been
                        // opened. Greying them out is honest; sending the tap to a blank
                        // screen, as this used to, is not.
                        val enabled = targetRoute != null

                        val isSelected = currentRoute != null &&
                            currentRoute.startsWith(item.route.substringBefore("/"))

                        NavigationBarItem(
                            icon = { item.icon?.let { Icon(it, contentDescription = item.title) } },
                            label = { Text(item.title) },
                            selected = isSelected,
                            enabled = enabled,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PrimaryAccent,
                                selectedTextColor = PrimaryAccent,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                                disabledIconColor = TextDisabled,
                                disabledTextColor = TextDisabled,
                                indicatorColor = NavBackground
                            ),
                            onClick = {
                                if (item == Screen.Overview) {
                                    navigateHome(navController, currentRoute)
                                } else if (targetRoute != null && targetRoute != currentRoute) {
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
                            "Docker" -> navController.navigate(Screen.Docker.createRoute(serverId))
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

/**
 * Returns to the dashboard from anywhere in the graph.
 *
 * Overview is the start destination, so it always sits below the current screen: popping
 * back to it is what "go home" means here. navigate() alone could not be relied on for
 * this - with saveState/restoreState in play it would rebuild the dashboard on top of the
 * stack instead of returning to it, which is why the tab sometimes appeared to do nothing.
 * The navigate() branch below is only for the impossible case where the dashboard is not
 * on the stack at all, so the tap still lands somewhere sensible.
 */
private fun navigateHome(navController: NavHostController, currentRoute: String?) {
    if (currentRoute == Screen.Overview.route) return
    if (navController.popBackStack(Screen.Overview.route, inclusive = false)) return

    navController.navigate(Screen.Overview.route) {
        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
        launchSingleTop = true
    }
}
