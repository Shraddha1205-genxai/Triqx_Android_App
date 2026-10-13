package com.example.triqx.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun TriqxBottomNavigationBar(
    navController: NavController,
    showDebug: Boolean = false,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val startRoute = navController.graph.findStartDestination().route ?: "home"
    val items = remember(showDebug) { BottomNavItem.getItems(showDebug) }

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f),
            tonalElevation = 0.dp,
            windowInsets = NavigationBarDefaults.windowInsets,
            modifier = Modifier.fillMaxWidth()
        ) {
        items.forEach { item ->
            val currentBaseRoute = currentRoute?.substringBefore('?')
            val selected = currentBaseRoute == item.route
            val iconScale by animateFloatAsState(
                targetValue = if (selected) 1.15f else 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "navIconScale"
            )

            NavigationBarItem(
                icon = {
                    Crossfade(
                        targetState = if (selected) item.selectedIcon else item.unselectedIcon,
                        animationSpec = tween(durationMillis = 200),
                        label = "navIconFade"
                    ) { iconVector ->
                        Icon(
                            imageVector = iconVector,
                            contentDescription = item.title,
                            modifier = Modifier
                                .scale(iconScale)
                                .size(24.dp)
                        )
                    }
                },
                label = {
                    Text(
                        text = item.title,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                selected = selected,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                onClick = {
                    if (currentBaseRoute != item.route) {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = (item.route != startRoute)
                            }
                            launchSingleTop = true
                            restoreState = (item.route != startRoute)
                        }
                    } else if (item.route == startRoute) {
                        navController.popBackStack(startRoute, inclusive = false)
                    }
                }
            )
        }
    }

    // Subtle top border hairline
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
    )
}
}

