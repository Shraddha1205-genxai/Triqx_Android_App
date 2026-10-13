package com.example.triqx.ui.contacts

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.triqx.data.local.ContactEntity
import com.example.triqx.ui.theme.Dimens
import com.example.triqx.ui.theme.PixelAvatarColors




data class ContactFormData(
    val displayName: String,
    val officialName: String?,
    val phoneNumbers: List<String>,
    val emails: List<String>,
    val about: String?
)

@Composable
fun GoogleContactCard(
    contact: ContactEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val avatarColors = remember(contact.displayName) {
        PixelAvatarColors.getColorsForName(contact.displayName)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.SpacingStandard, vertical = Dimens.SpacingSemiMedium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingMedium)
        ) {
            // Squircle Avatar
            Surface(
                modifier = Modifier.size(46.dp),
                shape = Dimens.SquircleShape,
                color = avatarColors.first
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = contact.displayName.trim().take(1).uppercase().ifEmpty { "?" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = avatarColors.second
                    )
                }
            }

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (!contact.officialName.isNullOrBlank() && contact.officialName != contact.displayName) {
                    Text(
                        text = contact.officialName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (contact.phoneNumbers.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = contact.phoneNumbers.first(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Quick Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit Contact",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Contact",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 74.dp, end = Dimens.SpacingStandard),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            thickness = 0.5.dp
        )
    }
}

@Composable
fun AddEditContactDialog(
    contactToEdit: ContactEntity?,
    onDismiss: () -> Unit,
    onSave: (ContactFormData) -> Unit
) {
    var displayName by remember { mutableStateOf(contactToEdit?.displayName ?: "") }
    var officialName by remember { mutableStateOf(contactToEdit?.officialName ?: "") }
    var phonesInput by remember { mutableStateOf(contactToEdit?.phoneNumbers?.joinToString(", ") ?: "") }
    var emailsInput by remember { mutableStateOf(contactToEdit?.emails?.joinToString(", ") ?: "") }
    var about by remember { mutableStateOf(contactToEdit?.about ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = Dimens.DialogShape,
        title = {
            Text(
                text = if (contactToEdit == null) "Add Priority Contact" else "Edit Contact",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name *") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = officialName,
                    onValueChange = { officialName = it },
                    label = { Text("Official / Legal Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phonesInput,
                    onValueChange = { phonesInput = it },
                    label = { Text("Phone Numbers (comma-separated)") },
                    placeholder = { Text("+91 9876543210, +91 9123456789") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = emailsInput,
                    onValueChange = { emailsInput = it },
                    label = { Text("Emails (comma-separated)") },
                    placeholder = { Text("name@example.com, work@company.com") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = about,
                    onValueChange = { about = it },
                    label = { Text("Notes / About") },
                    maxLines = 2,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (displayName.isNotBlank()) {
                        val parsedPhones = phonesInput.split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        val parsedEmails = emailsInput.split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }

                        onSave(
                            ContactFormData(
                                displayName = displayName.trim(),
                                officialName = officialName.trim().ifEmpty { null },
                                phoneNumbers = parsedPhones,
                                emails = parsedEmails,
                                about = about.trim().ifEmpty { null }
                            )
                        )
                    }
                },
                enabled = displayName.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun fetchContactDetails(context: Context, contactUri: Uri): ContactDetails? {
    var name = ""
    val phoneList = mutableListOf<String>()
    val emailList = mutableListOf<String>()
    var lookupKey: String? = null

    val contentResolver = context.contentResolver
    val cursor = contentResolver.query(contactUri, null, null, null, null)

    cursor?.use {
        if (it.moveToFirst()) {
            val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
            val id = if (idIndex != -1) it.getString(idIndex) else null
            val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            if (nameIndex != -1) name = it.getString(nameIndex) ?: ""
            val keyIndex = it.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
            if (keyIndex != -1) lookupKey = it.getString(keyIndex)

            if (id != null) {
                val phoneCursor = contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                    arrayOf(id),
                    null
                )
                phoneCursor?.use { pc ->
                    val phoneIndex = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (pc.moveToNext()) {
                        if (phoneIndex != -1) {
                            val phone = pc.getString(phoneIndex)?.trim()
                            if (!phone.isNullOrEmpty() && !phoneList.contains(phone)) {
                                phoneList.add(phone)
                            }
                        }
                    }
                }

                val emailCursor = contentResolver.query(
                    ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                    null,
                    ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?",
                    arrayOf(id),
                    null
                )
                emailCursor?.use { ec ->
                    val emailIndex = ec.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                    while (ec.moveToNext()) {
                        if (emailIndex != -1) {
                            val email = ec.getString(emailIndex)?.trim()
                            if (!email.isNullOrEmpty() && !emailList.contains(email)) {
                                emailList.add(email)
                            }
                        }
                    }
                }
            }
        }
    }
    return if (name.isNotEmpty()) {
        ContactDetails(name, phoneList, emailList, lookupKey)
    } else null
}

data class ContactDetails(
    val name: String,
    val phoneNumbers: List<String>,
    val emails: List<String>,
    val lookupKey: String?
)
