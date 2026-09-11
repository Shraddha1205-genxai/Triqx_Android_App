package com.example.triqx.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.triqx.ui.components.*
import com.example.triqx.ui.theme.Dimens

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileSetupScreen(
    viewModel: ProfileSetupViewModel,
    onSetupComplete: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                is ProfileSetupEvent.SetupComplete -> onSetupComplete()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .padding(top = Dimens.ScreenTopPadding)
                .navigationBarsPadding()
        ) {
            // Top Bar with Capsule Step Indicator (No Skip - Profile is required)
            M3StepTopBar(
                totalSteps = 2,
                currentStep = 1,
                onBack = null,
                skipText = null,
                onSkip = null
            )

            // Scrollable Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.SpacingStandard)
                    .padding(bottom = Dimens.SpacingStandard),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpacingStandard)
            ) {
                Spacer(modifier = Modifier.height(Dimens.SpacingMicro))

                // Concise Headline & Subtitle
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpacingMicro)) {
                    Text(
                        text = "Complete Your Profile",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Add your details to personalize your smart replies.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Verified Primary Phone Card using TriqxCard
                TriqxCard(
                    contentPadding = PaddingValues(horizontal = Dimens.SpacingStandard, vertical = Dimens.SpacingMedium)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingMedium)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(Dimens.IconSizeMedium)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Primary Phone",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = uiState.primaryPhoneNumber.ifBlank { "Mobile Number" },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Surface(
                            shape = Dimens.BadgeShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Verified",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = Dimens.SpacingSmall, vertical = 2.dp)
                            )
                        }
                    }
                }

                // First Name (Mandatory)
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpacingMicro)) {
                    Text(
                        text = "First name *",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    M3ExpressiveTextField(
                        value = uiState.firstName,
                        onValueChange = viewModel::onFirstNameChange,
                        placeholder = "e.g. John",
                        isError = uiState.firstNameError != null,
                        supportingText = {
                            if (uiState.firstNameError != null) {
                                Text(
                                    uiState.firstNameError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    )
                }

                // Last Name
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpacingMicro)) {
                    Text(
                        text = "Last name",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    M3ExpressiveTextField(
                        value = uiState.lastName,
                        onValueChange = viewModel::onLastNameChange,
                        placeholder = "e.g. Doe"
                    )
                }

                // Additional Phone Numbers
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpacingMicro)) {
                    Text(
                        text = "Additional phones",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (uiState.additionalPhoneNumbers.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall),
                            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall),
                            modifier = Modifier.padding(bottom = Dimens.SpacingMicro)
                        ) {
                            uiState.additionalPhoneNumbers.forEach { phone ->
                                M3ExpressiveChip(
                                    text = phone,
                                    selected = true,
                                    icon = Icons.Default.Phone,
                                    onClick = { viewModel.onRemoveAdditionalPhone(phone) }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        M3ExpressiveTextField(
                            value = uiState.newPhoneInput,
                            onValueChange = viewModel::onNewPhoneInputChange,
                            placeholder = "Add phone number",
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = viewModel::onAddPhoneNumber,
                            enabled = uiState.newPhoneInput.trim().length >= 8,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add phone number",
                                tint = if (uiState.newPhoneInput.trim().length >= 8) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                }
                            )
                        }
                    }
                }

                // Professional Details
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpacingMicro)) {
                    Text(
                        text = "Headline / Role",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    M3ExpressiveTextField(
                        value = uiState.professionalDetails,
                        onValueChange = viewModel::onProfessionalDetailsChange,
                        placeholder = "e.g. Software Engineer at Tech Corp"
                    )
                }

                // About Me
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpacingMicro)) {
                    Text(
                        text = "About me / Bio",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = uiState.aboutMe,
                        onValueChange = viewModel::onAboutMeChange,
                        placeholder = {
                            Text(
                                "Brief bio, communication preferences...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        minLines = 3,
                        maxLines = 4,
                        shape = Dimens.InputShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Email Addresses
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpacingMicro)) {
                    Text(
                        text = "Email addresses",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (uiState.emails.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall),
                            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall),
                            modifier = Modifier.padding(bottom = Dimens.SpacingMicro)
                        ) {
                            uiState.emails.forEach { email ->
                                M3ExpressiveChip(
                                    text = email,
                                    selected = true,
                                    icon = Icons.Default.Email,
                                    onClick = { viewModel.onRemoveEmail(email) }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        M3ExpressiveTextField(
                            value = uiState.newEmailInput,
                            onValueChange = viewModel::onNewEmailInputChange,
                            placeholder = "Add an email",
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = viewModel::onAddEmail,
                            enabled = uiState.newEmailInput.contains("@") && uiState.newEmailInput.contains("."),
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add email",
                                tint = if (uiState.newEmailInput.contains("@") && uiState.newEmailInput.contains(".")) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                }
                            )
                        }
                    }
                }
            }

            // Fixed Bottom Pill CTA Button ("Continue")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.SpacingStandard)
                    .padding(bottom = Dimens.SpacingStandard)
            ) {
                M3ExpressiveButton(
                    text = "Continue",
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.onSaveProfile()
                    },
                    enabled = uiState.firstName.trim().isNotBlank() && !uiState.isSaving,
                    isLoading = uiState.isSaving
                )
            }
        }
    }
}
