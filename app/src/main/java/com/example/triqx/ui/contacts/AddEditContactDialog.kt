package com.example.triqx.ui.contacts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.triqx.data.local.ContactEntity

@Composable
fun AddEditContactDialog(
    contactToEdit: ContactEntity? = null,
    onDismiss: () -> Unit,
    onSave: (displayName: String, officialName: String?, phoneNumbers: List<String>, emails: List<String>, about: String?) -> Unit
) {
    var displayName by remember { mutableStateOf(contactToEdit?.displayName ?: "") }
    var officialName by remember { mutableStateOf(contactToEdit?.officialName ?: "") }
    val phoneNumbers = remember {
        mutableStateListOf<String>().apply {
            if (contactToEdit != null && contactToEdit.phoneNumbers.isNotEmpty()) {
                addAll(contactToEdit.phoneNumbers)
            } else {
                add("")
            }
        }
    }
    val emails = remember {
        mutableStateListOf<String>().apply {
            if (contactToEdit != null && contactToEdit.emails.isNotEmpty()) {
                addAll(contactToEdit.emails)
            } else {
                add("")
            }
        }
    }
    var about by remember { mutableStateOf(contactToEdit?.about ?: "") }
    var showError by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .padding(8.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(22.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (contactToEdit == null) "Add Priority Contact" else "Edit Contact",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Scrollable Form Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Name Field
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = {
                            displayName = it
                            if (showError && it.isNotBlank()) showError = false
                        },
                        shape = RoundedCornerShape(14.dp),
                        label = { Text("Name *") },
                        placeholder = { Text("e.g. John Doe / Mom") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        isError = showError && displayName.isBlank(),
                        supportingText = {
                            if (showError && displayName.isBlank()) {
                                Text("Name is required", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Official Name Field
                    OutlinedTextField(
                        value = officialName,
                        onValueChange = { officialName = it },
                        shape = RoundedCornerShape(14.dp),
                        label = { Text("Official Name (Optional)") },
                        placeholder = { Text("e.g. Jonathan Doe") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Phone Numbers Section
                    Text(
                        text = "Phone Numbers",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    phoneNumbers.forEachIndexed { index, phone ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phoneNumbers[index] = it },
                                shape = RoundedCornerShape(14.dp),
                                label = { Text("Phone ${index + 1}") },
                                placeholder = { Text("+1 234 567 8900") },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            if (phoneNumbers.size > 1) {
                                IconButton(
                                    onClick = { phoneNumbers.removeAt(index) },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remove Phone",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = { phoneNumbers.add("") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Phone Number", fontWeight = FontWeight.Bold)
                    }

                    // Emails Section
                    Text(
                        text = "Emails",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    emails.forEachIndexed { index, email ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = email,
                                onValueChange = { emails[index] = it },
                                shape = RoundedCornerShape(14.dp),
                                label = { Text("Email ${index + 1}") },
                                placeholder = { Text("name@example.com") },
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            if (emails.size > 1) {
                                IconButton(
                                    onClick = { emails.removeAt(index) },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remove Email",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = { emails.add("") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Email", fontWeight = FontWeight.Bold)
                    }

                    // About Field
                    OutlinedTextField(
                        value = about,
                        onValueChange = { about = it },
                        shape = RoundedCornerShape(14.dp),
                        label = { Text("About / Notes (Optional)") },
                        placeholder = { Text("Important details, relationship notes...") },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Footer Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (displayName.isBlank()) {
                                showError = true
                            } else {
                                val validPhones = phoneNumbers.map { it.trim() }.filter { it.isNotEmpty() }
                                val validEmails = emails.map { it.trim() }.filter { it.isNotEmpty() }
                                onSave(
                                    displayName.trim(),
                                    officialName.trim().takeIf { it.isNotEmpty() },
                                    validPhones,
                                    validEmails,
                                    about.trim().takeIf { it.isNotEmpty() }
                                )
                            }
                        },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(if (contactToEdit == null) "Add Contact" else "Save Changes", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
