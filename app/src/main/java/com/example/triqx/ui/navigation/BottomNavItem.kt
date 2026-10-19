package com.example.triqx.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AssignmentLate
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AssignmentLate
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home : BottomNavItem(
        route = "home",
        title = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    )

    object Priority : BottomNavItem(
        route = "priority",
        title = "Priority",
        selectedIcon = Icons.Filled.AssignmentLate,
        unselectedIcon = Icons.Outlined.AssignmentLate
    )

    object Filters : BottomNavItem(
        route = "filters",
        title = "Filters",
        selectedIcon = Icons.Filled.Tune,
        unselectedIcon = Icons.Outlined.Tune
    )

    object Contacts : BottomNavItem(
        route = "contacts",
        title = "Contacts",
        selectedIcon = Icons.Filled.Contacts,
        unselectedIcon = Icons.Outlined.Contacts
    )

    object Apps : BottomNavItem(
        route = "apps",
        title = "Apps",
        selectedIcon = Icons.Filled.Apps,
        unselectedIcon = Icons.Outlined.Apps
    )

    object Debug : BottomNavItem(
        route = "debug",
        title = "Debug",
        selectedIcon = Icons.Filled.BugReport,
        unselectedIcon = Icons.Outlined.BugReport
    )

    object Settings : BottomNavItem(
        route = "settings",
        title = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )

    companion object {
        fun getItems(showDebug: Boolean = false): List<BottomNavItem> {
            return if (showDebug) {
                listOf(Home, Priority, Filters, Debug, Settings)
            } else {
                listOf(Home, Priority, Filters, Settings)
            }
        }
    }
}
