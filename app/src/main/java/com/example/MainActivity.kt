package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.MainViewModel
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LogsScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ZeroGreenSuccess
import com.example.ui.theme.ZeroRedError

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Splash : Screen("splash", "Splash", Icons.Default.Home)
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Scanner : Screen("scanner", "Scanner", Icons.Default.QrCodeScanner)
    object Logs : Screen("logs", "SMS Logs", Icons.Default.History)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                val snackbarHostState = remember { SnackbarHostState() }
                val config by viewModel.config.collectAsState()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val items = listOf(
                    Screen.Home,
                    Screen.Scanner,
                    Screen.Logs,
                    Screen.Settings
                )

                val showBars = currentRoute != null && currentRoute != Screen.Splash.route

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        if (showBars) {
                            CenterAlignedTopAppBar(
                                title = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = androidx.compose.ui.graphics.Color.White,
                                            shadowElevation = 1.dp,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            androidx.compose.foundation.Image(
                                                painter = androidx.compose.ui.res.painterResource(id = R.drawable.logo),
                                                contentDescription = "Zero Pay Logo",
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(2.dp),
                                                contentScale = ContentScale.Fit
                                            )
                                        }
                                        Text(
                                            text = "Zero Pay Forwarder",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(if (config.isConfigured) ZeroGreenSuccess else ZeroRedError)
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            )
                        }
                    },
                    bottomBar = {
                        if (showBars) {
                            NavigationBar(
                                modifier = Modifier.testTag("bottom_nav_bar"),
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                items.forEach { screen ->
                                    val isSelected = currentRoute == screen.route
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = {
                                            if (currentRoute != screen.route) {
                                                navController.navigate(screen.route) {
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        },
                                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                                        label = { Text(screen.title) },
                                        modifier = Modifier.testTag("nav_tab_${screen.route}"),
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                        )
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Splash.route,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(if (showBars) innerPadding else androidx.compose.foundation.layout.PaddingValues())
                    ) {
                        composable(Screen.Splash.route) {
                            SplashScreen(
                                onSplashComplete = {
                                    navController.navigate(Screen.Home.route) {
                                        popUpTo(Screen.Splash.route) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable(Screen.Home.route) {
                            HomeScreen(
                                viewModel = viewModel,
                                snackbarHostState = snackbarHostState,
                                onNavigateToScanner = {
                                    navController.navigate(Screen.Scanner.route)
                                },
                                onNavigateToLogs = {
                                    navController.navigate(Screen.Logs.route)
                                }
                            )
                        }
                        composable(Screen.Scanner.route) {
                            ScannerScreen(
                                viewModel = viewModel,
                                onScanSuccess = {
                                    navController.navigate(Screen.Home.route) {
                                        popUpTo(Screen.Home.route) { inclusive = false }
                                    }
                                }
                            )
                        }
                        composable(Screen.Logs.route) {
                            LogsScreen(viewModel = viewModel)
                        }
                        composable(Screen.Settings.route) {
                            SettingsScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}
