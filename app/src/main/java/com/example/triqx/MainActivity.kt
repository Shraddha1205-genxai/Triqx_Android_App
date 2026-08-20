package com.example.triqx

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.triqx.ui.apps.AppSelectionScreen
import com.example.triqx.ui.apps.ImportantAppsScreen
import com.example.triqx.ui.contacts.ContactDetailsScreen
import com.example.triqx.ui.contacts.PriorityContactsScreen
import com.example.triqx.ui.home.HomeScreen
import com.example.triqx.ui.navigation.TriqxBottomNavigationBar
import com.example.triqx.ui.notifications.NotificationDetailsScreen
import com.example.triqx.ui.notifications.NotificationHistoryScreen
import com.example.triqx.ui.notifications.NotificationViewModel
import com.example.triqx.ui.settings.SettingsScreen
import com.example.triqx.ui.settings.SettingsViewModel
import com.example.triqx.ui.theme.TriqxTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TriqxTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val settingsViewModel: SettingsViewModel = hiltViewModel()
                val showDebugMenu by settingsViewModel.showDebugMenu.collectAsState()

                val topLevelRoutes = remember(showDebugMenu) {
                    if (showDebugMenu) {
                        setOf("home", "contacts", "apps", "debug", "settings")
                    } else {
                        setOf("home", "contacts", "apps", "settings")
                    }
                }
                val showBottomBar = currentRoute in topLevelRoutes

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        if (showBottomBar) {
                            TriqxBottomNavigationBar(
                                navController = navController,
                                showDebug = showDebugMenu
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding())
                    ) {
                        composable("home") {
                            val scope = rememberCoroutineScope()
                            val notificationViewModel: NotificationViewModel = hiltViewModel()
                            HomeScreen(
                                viewModel = notificationViewModel,
                                onViewContact = { uri ->
                                    scope.launch {
                                        if (uri.startsWith("contact_id_")) {
                                            val contactId = uri.substringAfter("contact_id_").toIntOrNull()
                                            if (contactId != null) {
                                                navController.navigate("contact_details/$contactId")
                                            }
                                        } else {
                                            val priorityId = notificationViewModel.getPriorityContactId(uri)
                                            if (priorityId != null) {
                                                navController.navigate("contact_details/$priorityId")
                                            } else {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                                                startActivity(intent)
                                            }
                                        }
                                    }
                                },
                                onNavigateToContacts = {
                                    navController.navigate("contacts") {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onNavigateToApps = {
                                    navController.navigate("apps") {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                        composable("contacts") {
                            PriorityContactsScreen(
                                viewModel = hiltViewModel(),
                                onNavigateToDetail = { contactId ->
                                    navController.navigate("contact_details/$contactId")
                                }
                            )
                        }
                        composable("apps") {
                            ImportantAppsScreen(
                                viewModel = hiltViewModel(),
                                onNavigateToSelection = { navController.navigate("app_selection") }
                            )
                        }
                        composable("debug") {
                            val scope = rememberCoroutineScope()
                            val notificationViewModel: NotificationViewModel = hiltViewModel()
                            NotificationHistoryScreen(
                                viewModel = notificationViewModel,
                                onNavigateToDetails = { notificationId ->
                                    navController.navigate("notification_details/$notificationId")
                                },
                                onViewContact = { uri ->
                                    scope.launch {
                                        val priorityId = notificationViewModel.getPriorityContactId(uri)
                                        if (priorityId != null) {
                                            navController.navigate("contact_details/$priorityId")
                                        } else {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                                            startActivity(intent)
                                        }
                                    }
                                }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(viewModel = hiltViewModel())
                        }
                        composable(
                            route = "notification_details/{notificationId}",
                            arguments = listOf(navArgument("notificationId") { type = NavType.IntType })
                        ) { backStackEntry ->
                            val notificationId = backStackEntry.arguments?.getInt("notificationId") ?: 0
                            NotificationDetailsScreen(
                                notificationId = notificationId,
                                viewModel = hiltViewModel(),
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable("app_selection") {
                            AppSelectionScreen(
                                viewModel = hiltViewModel(),
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = "contact_details/{contactId}",
                            arguments = listOf(navArgument("contactId") { type = NavType.IntType })
                        ) { backStackEntry ->
                            val contactId = backStackEntry.arguments?.getInt("contactId") ?: 0
                            ContactDetailsScreen(
                                contactId = contactId,
                                viewModel = hiltViewModel(),
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
