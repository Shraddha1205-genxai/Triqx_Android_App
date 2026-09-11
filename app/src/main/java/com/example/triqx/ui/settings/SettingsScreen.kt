package com.example.triqx.ui.settings

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.triqx.data.local.EmailAccountEntity
import com.example.triqx.service.email.EmailProvider
import com.example.triqx.ui.components.TriqxCard
import com.example.triqx.ui.profile.EditProfileDialog
import com.example.triqx.ui.theme.Dimens
import com.example.triqx.ui.theme.PixelAvatarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val userProfile by viewModel.userProfile.collectAsState()
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showAiConfigDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
    var managingAccount by remember { mutableStateOf<EmailAccountEntity?>(null) }

    val savedApiKey by viewModel.apiKey.collectAsState()
    val savedModel by viewModel.selectedModel.collectAsState()
    val defaultReplyCount by viewModel.defaultReplyCount.collectAsState()
    val isTesting by viewModel.isTesting.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    val showDebugMenu by viewModel.showDebugMenu.collectAsState()
    val gmailAccounts by viewModel.gmailAccounts.collectAsState()
    val outlookAccounts by viewModel.outlookAccounts.collectAsState()
    val authStatus by viewModel.authStatus.collectAsState()

    var isPreparingProvider by remember { mutableStateOf<EmailProvider?>(null) }

    val googleAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data != null) {
                viewModel.handleGoogleAuthResult(data)
            }
        }
    }

    val outlookAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (data != null) {
            viewModel.handleOutlookAuthResult(data)
        }
    }

    var replyCountSelection by remember(defaultReplyCount) { mutableIntStateOf(defaultReplyCount) }

    var apiKeyInput by remember(savedApiKey) { mutableStateOf(savedApiKey) }
    var passwordVisible by remember { mutableStateOf(false) }
    var expandedModelMenu by remember { mutableStateOf(false) }
    var senderNameInput by remember { mutableStateOf("") }

    val availableModels = listOf("gpt-4o-mini", "gpt-4o", "gpt-3.5-turbo")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .statusBarsPadding()
        ) {
            // Header: Pixel-identical alignment with HomeScreen and PriorityFiltersScreen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.SpacingStandard, vertical = Dimens.SpacingSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.SpacingStandard)
                    .padding(top = Dimens.SpacingSmall)
                    .navigationBarsPadding()
                    .padding(bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpacingLarge)
            ) {
                // Minimal User Profile Card
                if (userProfile != null) {
                    val profile = userProfile!!
                    val avatarColors = remember(profile.fullName) {
                        PixelAvatarColors.getColorsForName(profile.fullName.ifBlank { "User" })
                    }
                    TriqxCard(
                        onClick = { showEditProfileDialog = true },
                        contentPadding = PaddingValues(horizontal = Dimens.SpacingStandard, vertical = Dimens.SpacingMedium)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingMedium)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(avatarColors.first)
                            ) {
                                Text(
                                    text = profile.initials,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = avatarColors.second
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = profile.fullName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val subtitle = when {
                                    profile.professionalDetails.isNotBlank() -> profile.professionalDetails
                                    profile.primaryPhone.isNotBlank() -> profile.primaryPhone
                                    else -> "Tap to edit profile"
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = { showEditProfileDialog = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Profile",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Section 1: AI Model & Key
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)) {
                    MinimalSectionTitle("AI Model & Key")
                    TriqxCard(
                        onClick = {
                            apiKeyInput = savedApiKey
                            showAiConfigDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = Dimens.SpacingStandard, vertical = Dimens.SpacingMedium)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingMedium),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    modifier = Modifier.size(36.dp),
                                    shape = Dimens.BadgeShape,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.SmartToy,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Column {
                                    Text(
                                        text = "AI Smart Replies",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val statusText = "Default: $defaultReplyCount ${if (defaultReplyCount == 1) "reply" else "replies"} • Backend Service"
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Configure",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Section 2: Email Accounts (Redesigned clean, uncluttered list)
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)) {
                    MinimalSectionTitle("Email Accounts")
                    TriqxCard(
                        contentPadding = PaddingValues(vertical = Dimens.SpacingMicro)
                    ) {
                        Column {
                            // Gmail Section
                            if (gmailAccounts.isEmpty()) {
                                ProviderConnectRow(
                                    name = "Gmail",
                                    iconColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    isPreparing = isPreparingProvider == EmailProvider.GMAIL,
                                    onConnect = {
                                        isPreparingProvider = EmailProvider.GMAIL
                                        viewModel.prepareGoogleAuthIntent(
                                            onReady = { intent ->
                                                isPreparingProvider = null
                                                try {
                                                    googleAuthLauncher.launch(intent)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            onError = { isPreparingProvider = null }
                                        )
                                    }
                                )
                            } else {
                                gmailAccounts.forEachIndexed { index, account ->
                                    if (index > 0) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = Dimens.SpacingStandard),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                        )
                                    }
                                    ConnectedAccountRow(
                                        account = account,
                                        providerName = "Gmail",
                                        iconColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        onClick = {
                                            managingAccount = account
                                            senderNameInput = account.displayName.orEmpty()
                                        }
                                    )
                                }
                                AddAnotherAccountButton(
                                    label = "Add another Gmail",
                                    isPreparing = isPreparingProvider == EmailProvider.GMAIL,
                                    onConnect = {
                                        isPreparingProvider = EmailProvider.GMAIL
                                        viewModel.prepareGoogleAuthIntent(
                                            onReady = { intent ->
                                                isPreparingProvider = null
                                                try {
                                                    googleAuthLauncher.launch(intent)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            onError = { isPreparingProvider = null }
                                        )
                                    }
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = Dimens.SpacingStandard),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                            )

                            // Outlook Section
                            if (outlookAccounts.isEmpty()) {
                                ProviderConnectRow(
                                    name = "Outlook",
                                    iconColor = MaterialTheme.colorScheme.secondaryContainer,
                                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    isPreparing = isPreparingProvider == EmailProvider.OUTLOOK,
                                    onConnect = {
                                        isPreparingProvider = EmailProvider.OUTLOOK
                                        viewModel.prepareOutlookAuthIntent(
                                            onReady = { intent ->
                                                isPreparingProvider = null
                                                try {
                                                    outlookAuthLauncher.launch(intent)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            onError = { isPreparingProvider = null }
                                        )
                                    }
                                )
                            } else {
                                outlookAccounts.forEachIndexed { index, account ->
                                    if (index > 0) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = Dimens.SpacingStandard),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                        )
                                    }
                                    ConnectedAccountRow(
                                        account = account,
                                        providerName = "Outlook",
                                        iconColor = MaterialTheme.colorScheme.secondaryContainer,
                                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        onClick = {
                                            managingAccount = account
                                            senderNameInput = account.displayName.orEmpty()
                                        }
                                    )
                                }
                                AddAnotherAccountButton(
                                    label = "Add another Outlook",
                                    isPreparing = isPreparingProvider == EmailProvider.OUTLOOK,
                                    onConnect = {
                                        isPreparingProvider = EmailProvider.OUTLOOK
                                        viewModel.prepareOutlookAuthIntent(
                                            onReady = { intent ->
                                                isPreparingProvider = null
                                                try {
                                                    outlookAuthLauncher.launch(intent)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            onError = { isPreparingProvider = null }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                // Auth status banner if any
                if (authStatus != null) {
                    Surface(
                        shape = Dimens.CardShape,
                        color = if (authStatus?.startsWith("Error") == true) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = Dimens.SpacingStandard, vertical = Dimens.SpacingMedium),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
                        ) {
                            Text(
                                text = authStatus ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (authStatus?.startsWith("Error") == true) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearAuthStatus() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Section 3: Preferences
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)) {
                    MinimalSectionTitle("Preferences")
                    TriqxCard(
                        contentPadding = PaddingValues(vertical = Dimens.SpacingMicro)
                    ) {
                        Column {
                            // Notification Permission row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Dimens.SpacingStandard, vertical = Dimens.SpacingMedium),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingMedium),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        modifier = Modifier.size(36.dp),
                                        shape = Dimens.BadgeShape,
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Notifications,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = "Notification Access",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Required to read & reply to notifications",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                FilledTonalButton(
                                    onClick = {
                                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        context.startActivity(intent)
                                    },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                    modifier = Modifier.height(34.dp),
                                    shape = CircleShape
                                ) {
                                    Text("Open", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = Dimens.SpacingStandard),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                            )

                            // Debug Tab row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Dimens.SpacingStandard, vertical = Dimens.SpacingMedium),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingMedium),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        modifier = Modifier.size(36.dp),
                                        shape = Dimens.BadgeShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.BugReport,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = "Debug Tab",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Show developer diagnostics & logs",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = showDebugMenu,
                                    onCheckedChange = { viewModel.setShowDebugMenu(it) }
                                )
                            }
                        }
                    }
                }

                // Sign Out Button & Version Footer
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpacingMedium)
                ) {
                    OutlinedButton(
                        onClick = { showLogoutConfirmDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Dimens.ButtonHeight),
                        shape = Dimens.SquircleShape,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(Dimens.SpacingSmall))
                        Text(
                            text = "Sign Out",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = "Triqx v1.0",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }

    // AI Configuration Dialog (Backend Service)
    if (showAiConfigDialog) {
        AlertDialog(
            onDismissRequest = { showAiConfigDialog = false },
            shape = Dimens.DialogShape,
            title = {
                Text(
                    text = "AI Smart Reply Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpacingMedium),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Configure the global default smart replies generated by the AI backend service.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Default Number of Replies
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Default Number of Replies",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "$replyCountSelection ${if (replyCountSelection == 1) "reply" else "replies"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            (1..5).forEach { count ->
                                FilterChip(
                                    selected = replyCountSelection == count,
                                    onClick = { replyCountSelection = count },
                                    label = {
                                        Text(
                                            text = "$count",
                                            fontWeight = if (replyCountSelection == count) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.testBackendConnection() },
                        enabled = !isTesting,
                        shape = Dimens.SquircleShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(Dimens.SpacingSmall))
                            Text("Testing AI Service...")
                        } else {
                            Text("Test AI Connection", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (testResult != null) {
                        Surface(
                            shape = Dimens.CardShape,
                            color = if (testResult?.startsWith("Error") == true) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = testResult ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = if (testResult?.startsWith("Error") == true) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                                modifier = Modifier.padding(Dimens.SpacingSmall)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveDefaultReplyCount(replyCountSelection)
                        showAiConfigDialog = false
                    },
                    shape = Dimens.SquircleShape
                ) {
                    Text("Save", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAiConfigDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Manage Connected Email Account Dialog
    if (managingAccount != null) {
        val account = managingAccount!!
        AlertDialog(
            onDismissRequest = { managingAccount = null },
            shape = Dimens.DialogShape,
            title = {
                Text(
                    text = "Manage Account",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpacingMedium),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = account.emailAddress,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedTextField(
                        value = senderNameInput,
                        onValueChange = { senderNameInput = it },
                        label = { Text("Sender Name") },
                        placeholder = { Text("Name shown on replies") },
                        singleLine = true,
                        shape = Dimens.InputShape,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(Dimens.SpacingMicro))

                    TextButton(
                        onClick = {
                            val emailToDisconnect = account.emailAddress
                            viewModel.disconnectAccount(emailToDisconnect)
                            managingAccount = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(Dimens.SpacingSmall))
                        Text("Disconnect Account", fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateDisplayName(account.emailAddress, senderNameInput.trim())
                        managingAccount = null
                    },
                    shape = Dimens.SquircleShape
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { managingAccount = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Profile Dialog
    if (showEditProfileDialog && userProfile != null) {
        EditProfileDialog(
            userProfile = userProfile!!,
            onDismiss = { showEditProfileDialog = false },
            onSave = { updated ->
                viewModel.updateUserProfile(updated)
                showEditProfileDialog = false
            }
        )
    }

    // Sign Out Confirmation Dialog
    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            shape = Dimens.DialogShape,
            title = { Text("Sign Out", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to sign out? All your local data (messages, contacts, accounts, and AI cache) will be removed from this device. App permissions will remain granted.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmDialog = false
                        viewModel.logout()
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = Dimens.SquircleShape
                ) {
                    Text("Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Clean, minimal section title.
 */
@Composable
private fun MinimalSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Clean provider connect row when no account is connected.
 */
@Composable
private fun ProviderConnectRow(
    name: String,
    iconColor: Color,
    iconTint: Color,
    isPreparing: Boolean,
    onConnect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isPreparing, onClick = onConnect)
            .padding(horizontal = Dimens.SpacingStandard, vertical = Dimens.SpacingMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingMedium),
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = Dimens.BadgeShape,
                color = iconColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Not connected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        FilledTonalButton(
            onClick = onConnect,
            enabled = !isPreparing,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            modifier = Modifier.height(34.dp),
            shape = CircleShape
        ) {
            if (isPreparing) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            } else {
                Text("Connect", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Clean, flat connected account row with chevron. Tapping opens management dialog.
 */
@Composable
private fun ConnectedAccountRow(
    account: EmailAccountEntity,
    providerName: String,
    iconColor: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.SpacingStandard, vertical = Dimens.SpacingMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingMedium),
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = Dimens.BadgeShape,
                color = iconColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.emailAddress,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val subtitle = if (!account.displayName.isNullOrBlank()) {
                    "${account.displayName} • $providerName"
                } else {
                    "$providerName • Tap to manage"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Manage",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Clean button to add another account for a provider, aligned with account rows.
 */
@Composable
private fun AddAnotherAccountButton(
    label: String,
    isPreparing: Boolean,
    onConnect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isPreparing, onClick = onConnect)
            .padding(horizontal = Dimens.SpacingStandard, vertical = Dimens.SpacingMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingMedium)
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = Dimens.BadgeShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isPreparing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
