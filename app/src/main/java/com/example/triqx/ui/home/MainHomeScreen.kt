package com.example.triqx.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.triqx.ui.theme.Dimens

@Composable
fun MainHomeScreen(
    onNavigateToPriorityContacts: () -> Unit,
    onNavigateToImportantApps: () -> Unit,
    onNavigateToNotificationHistory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = Dimens.ScreenTopPadding)
            .padding(horizontal = Dimens.SpacingStandard),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to Triqx",
            style = MaterialTheme.typography.headlineLarge
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Button(
            onClick = onNavigateToPriorityContacts,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Manage Priority Contacts")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onNavigateToImportantApps,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Manage Important Apps")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onNavigateToNotificationHistory,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Notification History")
        }
    }
}
