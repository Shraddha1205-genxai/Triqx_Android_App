package com.example.triqx.ui.profile

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.triqx.data.local.UserProfile
import com.example.triqx.ui.components.*
import com.example.triqx.ui.theme.Dimens

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditProfileDialog(
    userProfile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (UserProfile) -> Unit
) {
    var firstName by remember { mutableStateOf(userProfile.firstName) }
    var lastName by remember { mutableStateOf(userProfile.lastName) }
    var additionalPhones by remember { mutableStateOf(userProfile.phoneNumbers.drop(1)) }
    val primaryPhone = remember { userProfile.primaryPhone }
    var emails by remember { mutableStateOf(userProfile.emails) }
    var aboutMe by remember { mutableStateOf(userProfile.aboutMe) }
    var professionalDetails by remember { mutableStateOf(userProfile.professionalDetails) }
    var newEmailInput by remember { mutableStateOf("") }
    var firstNameError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .clip(Dimens.DialogShape),
            shape = Dimens.DialogShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Dimens.SpacingStandard)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Edit Profile",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Dimens.SpacingStandard))

                // Scrollable Form
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpacingStandard)
                ) {
                    // First Name
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpacingMicro)) {
                        Text(
                            text = "First name *",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        M3ExpressiveTextField(
                            value = firstName,
                            onValueChange = {
                                firstName = it
                                firstNameError = null
                            },
                            placeholder = "First name",
                            isError = firstNameError != null,
                            supportingText = {
                                if (firstNameError != null) {
                                    Text(
                                        firstNameError!!,
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
                            value = lastName,
                            onValueChange = { lastName = it },
                            placeholder = "Last name"
                        )
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
                            value = professionalDetails,
                            onValueChange = { professionalDetails = it },
                            placeholder = "e.g. Senior Software Engineer"
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
                            value = aboutMe,
                            onValueChange = { aboutMe = it },
                            placeholder = {
                                Text("Brief bio...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            minLines = 2,
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

                    // Primary Phone (Locked card)
                    if (primaryPhone.isNotBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = Dimens.CardShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = Dimens.SpacingStandard, vertical = Dimens.SpacingMedium),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        "Primary number",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        primaryPhone,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "Locked",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Emails
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)) {
                        Text(
                            "Email addresses",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (emails.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall),
                                verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
                            ) {
                                emails.forEach { email ->
                                    M3ExpressiveChip(
                                        text = email,
                                        selected = true,
                                        onClick = { emails = emails.filter { e -> e != email } }
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
                                value = newEmailInput,
                                onValueChange = { newEmailInput = it.trim() },
                                placeholder = "Add email",
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    val em = newEmailInput.trim()
                                    if (em.contains("@") && em.contains(".") && !emails.contains(em)) {
                                        emails = emails + em
                                        newEmailInput = ""
                                    }
                                },
                                enabled = newEmailInput.contains("@") && newEmailInput.contains("."),
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Add",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Dimens.SpacingStandard))

                // Actions: Pill Button matching standard design
                M3ExpressiveButton(
                    text = "Save changes",
                    onClick = {
                        if (firstName.trim().isBlank()) {
                            firstNameError = "First name is mandatory"
                            return@M3ExpressiveButton
                        }
                        val allPhones = if (primaryPhone.isNotBlank()) {
                            listOf(primaryPhone) + additionalPhones
                        } else {
                            additionalPhones
                        }
                        val updated = userProfile.copy(
                            firstName = firstName.trim(),
                            lastName = lastName.trim(),
                            phoneNumbers = allPhones,
                            emails = emails,
                            aboutMe = aboutMe.trim(),
                            professionalDetails = professionalDetails.trim(),
                            isFirstLogin = false
                        )
                        onSave(updated)
                    }
                )
            }
        }
    }
}
