package com.example.triqx

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.triqx.service.TriqxAssistantNotificationManager
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
import com.example.triqx.data.local.UserSessionManager
import com.example.triqx.ui.auth.LoginScreen
import com.example.triqx.ui.auth.LoginViewModel
import com.example.triqx.ui.auth.ProfileSetupScreen
import com.example.triqx.ui.auth.ProfileSetupViewModel
import com.example.triqx.ui.apps.AppSelectionScreen
import com.example.triqx.ui.apps.ImportantAppsScreen
import com.example.triqx.ui.contacts.ContactDetailsScreen
import com.example.triqx.ui.contacts.PriorityContactsScreen
import com.example.triqx.ui.home.ChatScreen
import com.example.triqx.ui.home.HomeScreen
import com.example.triqx.ui.navigation.TriqxBottomNavigationBar
import com.example.triqx.ui.notifications.NotificationDetailsScreen
import com.example.triqx.ui.notifications.NotificationHistoryScreen
import com.example.triqx.ui.notifications.NotificationViewModel
import com.example.triqx.ui.settings.SettingsScreen
import com.example.triqx.ui.settings.SettingsViewModel
import com.example.triqx.ui.theme.TriqxTheme
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var gmailOAuthManager: com.example.triqx.auth.GmailOAuthManager
    @Inject lateinit var userSessionManager: UserSessionManager

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Create Assistant Notification Channel early
        TriqxAssistantNotificationManager.createNotificationChannel(this)

        // Request POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        handleOAuthRedirect(intent)

        setContent {
            TriqxTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val isLoggedIn by userSessionManager.isLoggedIn.collectAsState()
                val userProfile by userSessionManager.userProfile.collectAsState()

                val initialDestination = remember(isLoggedIn, userProfile?.isFirstLogin) {
                    when {
                        !isLoggedIn -> "login"
                        userProfile?.isFirstLogin != false -> "profile_setup"
                        else -> "home"
                    }
                }

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
                        startDestination = initialDestination,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding())
                    ) {
                        composable("login") {
                            val loginViewModel: LoginViewModel = hiltViewModel()
                            LoginScreen(
                                viewModel = loginViewModel,
                                onLoginSuccess = { isFirstLogin ->
                                    if (isFirstLogin) {
                                        navController.navigate("profile_setup") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    } else {
                                        navController.navigate("home") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    }
                                }
                            )
                        }
                        composable("profile_setup") {
                            val profileViewModel: ProfileSetupViewModel = hiltViewModel()
                            ProfileSetupScreen(
                                viewModel = profileViewModel,
                                onSetupComplete = {
                                    navController.navigate("home") {
                                        popUpTo("profile_setup") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("home") {
                            val scope = rememberCoroutineScope()
                            val notificationViewModel: NotificationViewModel = hiltViewModel()
                            HomeScreen(
                                viewModel = notificationViewModel,
                                userInitials = userProfile?.initials ?: "U",
                                onNavigateToSettings = { navController.navigate("settings") },
                                onConversationClick = { groupKey ->
                                    val encoded = Uri.encode(groupKey)
                                    navController.navigate("chat/$encoded")
                                },
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
                        composable(
                            route = "chat/{groupKey}",
                            arguments = listOf(navArgument("groupKey") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val encodedKey = backStackEntry.arguments?.getString("groupKey") ?: ""
                            val groupKey = Uri.decode(encodedKey)
                            val notificationViewModel: NotificationViewModel = hiltViewModel()
                            ChatScreen(
                                groupKey = groupKey,
                                viewModel = notificationViewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onViewContact = { uri ->
                                    val contactId = uri.substringAfter("contact_id_").toIntOrNull()
                                    if (contactId != null) {
                                        navController.navigate("contact_details/$contactId")
                                    }
                                },
                                onNavigateToSettings = { navController.navigate("settings") }
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
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onLogout = {
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthRedirect(intent)
    }

    private fun handleOAuthRedirect(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme.equals("com.example.triqx", ignoreCase = true)) {
            lifecycleScope.launch {
                gmailOAuthManager.handleAuthorizationResult(intent)
            }
        }
    }
}
