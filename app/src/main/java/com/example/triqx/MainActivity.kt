package com.example.triqx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.triqx.ui.auth.AuthViewModel
import com.example.triqx.ui.auth.LoginScreen
import com.example.triqx.ui.auth.RegisterScreen
import com.example.triqx.ui.theme.TriqxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TriqxTheme {
                val navController = rememberNavController()
                val authViewModel: AuthViewModel = viewModel()
                
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "login",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("login") {
                            LoginScreen(
                                viewModel = authViewModel,
                                onNavigateToRegister = { navController.navigate("register") },
                                onLoginSuccess = { navController.navigate("home") {
                                    popUpTo("login") { inclusive = true }
                                } }
                            )
                        }
                        composable("register") {
                            RegisterScreen(
                                viewModel = authViewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onRegisterSuccess = { navController.navigate("home") {
                                    popUpTo("login") { inclusive = true }
                                } }
                            )
                        }
                        composable("home") {
                            HomeScreen(name = authViewModel.name.ifEmpty { authViewModel.email })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(name: String) {
    Text(text = "Welcome, $name!", modifier = Modifier.fillMaxSize())
}
