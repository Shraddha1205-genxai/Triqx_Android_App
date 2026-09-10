package com.example.triqx.ui.settings

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    val isTesting by viewModel.isTesting.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    val showDebugMenu by viewModel.showDebugMenu.collectAsState()
    val notificationReplyStyle by viewModel.notificationReplyStyle.collectAsState()
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.SpacingStandard, vertical = Dimens.SpacingSmall)
                .padding(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingMedium)
        ) {
            // Header Title
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
            )

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
                                style = MaterialTheme.typography.titleSmall,
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
                                text = "OpenAI API",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            val statusText = if (savedApiKey.isNotBlank()) {
                                "$savedModel • Configured"
                            } else {
                                "Tap to set API key"
                            }
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (savedApiKey.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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

            // Section 2: Email Accounts (Redesigned clean, uncluttered list)
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
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
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

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

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
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
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
                        modifier = Modifier.padding(horizontal = Dimens.SpacingMedium, vertical = Dimens.SpacingSmall),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
                    ) {
                        Text(
                            text = authStatus ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearAuthStatus() },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Section 3: Reply Style
            MinimalSectionTitle("Reply Style")
            TriqxCard(
                contentPadding = PaddingValues(Dimens.SpacingSmall)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.setNotificationReplyStyle("body_numbered") }
                            .padding(horizontal = Dimens.SpacingMedium, vertical = Dimens.SpacingSmall),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
                    ) {
                        RadioButton(
                            selected = (notificationReplyStyle == "body_numbered"),
                            onClick = { viewModel.setNotificationReplyStyle("body_numbered") }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Full text in body",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Numbered actions (1, 2, 3)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.setNotificationReplyStyle("chips_native") }
                            .padding(horizontal = Dimens.SpacingMedium, vertical = Dimens.SpacingSmall),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
                    ) {
                        RadioButton(
                            selected = (notificationReplyStyle == "chips_native"),
                            onClick = { viewModel.setNotificationReplyStyle("chips_native") }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Native action chips",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Interactive reply pills",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Section 4: Preferences
            MinimalSectionTitle("Preferences")
            TriqxCard(
                contentPadding = PaddingValues(horizontal = Dimens.SpacingStandard, vertical = Dimens.SpacingSmall)
            ) {
                Column {
                    // Notification Permission row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Dimens.SpacingSmall),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingMedium)
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Notification Access",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        TextButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                context.startActivity(intent)
                            }
                        ) {
                            Text("Open", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                    // Debug Tab row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Dimens.SpacingSmall),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingMedium)
                        ) {
                            Icon(
                                Icons.Default.BugReport,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Debug Tab",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Switch(
                            checked = showDebugMenu,
                            onCheckedChange = { viewModel.setShowDebugMenu(it) }
                        )
                    }
                }
            }

            // Minimal Sign Out Button
            Spacer(modifier = Modifier.height(Dimens.SpacingSmall))

            OutlinedButton(
                onClick = { showLogoutConfirmDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(Dimens.SpacingSmall))
                Text("Sign Out", fontWeight = FontWeight.SemiBold)
            }

            // App Version Footer
            Text(
                text = "Triqx v1.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.SpacingMicro),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }

    // AI Configuration Dialog
    if (showAiConfigDialog) {
        AlertDialog(
            onDismissRequest = { showAiConfigDialog = false },
            shape = Dimens.DialogShape,
            title = {
                Text(
                    text = "AI Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpacingMedium),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = Dimens.InputShape,
                        label = { Text("OpenAI API Key") },
                        placeholder = { Text("sk-...") },
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Hide" else "Show",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true
                    )

                    ExposedDropdownMenuBox(
                        expanded = expandedModelMenu,
                        onExpandedChange = { expandedModelMenu = !expandedModelMenu }
                    ) {
                        OutlinedTextField(
                            value = savedModel,
                            onValueChange = {},
                            readOnly = true,
                            shape = Dimens.InputShape,
                            label = { Text("Model") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedModelMenu) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedModelMenu,
                            onDismissRequest = { expandedModelMenu = false },
                            shape = Dimens.InputShape
                        ) {
                            availableModels.forEach { modelName ->
                                DropdownMenuItem(
                                    text = { Text(modelName, fontWeight = if (modelName == savedModel) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = {
                                        viewModel.saveModel(modelName)
                                        expandedModelMenu = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.testConnection(apiKeyInput, savedModel) },
                        enabled = apiKeyInput.isNotBlank() && !isTesting,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(Dimens.SpacingSmall))
                            Text("Testing Connection...")
                        } else {
                            Text("Test Connection", fontWeight = FontWeight.SemiBold)
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
                        viewModel.saveApiKey(apiKeyInput)
                        showAiConfigDialog = false
                    },
                    shape = RoundedCornerShape(10.dp)
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
                    shape = RoundedCornerShape(10.dp)
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
            text = { Text("Are you sure you want to sign out?", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmDialog = false
                        viewModel.logout()
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(10.dp)
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
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = Dimens.SpacingSmall)
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
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
 * Subtle text button to add another account for a provider.
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
            .padding(horizontal = Dimens.SpacingStandard, vertical = Dimens.SpacingSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
