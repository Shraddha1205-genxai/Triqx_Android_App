package com.example.triqx.ui.filters

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.triqx.data.local.ContactEntity
import com.example.triqx.ui.apps.AppInfo
import com.example.triqx.ui.apps.AppViewModel
import com.example.triqx.ui.apps.GoogleAppCard
import com.example.triqx.ui.components.M3ExpressivePillTabSwitcher
import com.example.triqx.ui.contacts.AddEditContactDialog
import com.example.triqx.ui.contacts.ContactViewModel
import com.example.triqx.ui.contacts.GoogleContactCard
import com.example.triqx.ui.theme.Dimens

enum class FilterTab(val title: String) {
    APPS("Apps"),
    CONTACTS("Contacts")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PriorityFiltersScreen(
    appViewModel: AppViewModel,
    contactViewModel: ContactViewModel,
    initialTab: String = "apps",
    onNavigateToAppSelection: () -> Unit,
    onNavigateToContactDetail: (Int) -> Unit,
    onNavigateBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var selectedTab by remember {
        mutableStateOf(
            if (initialTab.equals("contacts", ignoreCase = true)) FilterTab.CONTACTS else FilterTab.APPS
        )
    }

    // Apps State
    val apps by appViewModel.savedImportantApps.collectAsState()
    var editingPromptApp by remember { mutableStateOf<AppInfo?>(null) }

    val distinctApps = remember(apps) {
        apps.distinctBy { it.packageName }
    }

    // Contacts State
    val contacts by contactViewModel.priorityContacts.collectAsState()
    var showAddChoiceSheet by remember { mutableStateOf(false) }
    var showFormDialog by remember { mutableStateOf(false) }
    var contactBeingEdited by remember { mutableStateOf<ContactEntity?>(null) }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        if (uri != null) {
            val cr = context.contentResolver
            val cursor = cr.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
                    val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    val keyIndex = it.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
                    val id = if (idIndex >= 0) it.getString(idIndex) else null
                    val name = if (nameIndex >= 0) it.getString(nameIndex) else "Unknown"
                    val key = if (keyIndex >= 0) it.getString(keyIndex) else null

                    val phoneList = mutableListOf<String>()
                    if (id != null) {
                        val pCur = cr.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            arrayOf(id),
                            null
                        )
                        pCur?.use { pc ->
                            val pIdx = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            while (pc.moveToNext()) {
                                if (pIdx >= 0) phoneList.add(pc.getString(pIdx))
                            }
                        }
                    }

                    val emailList = mutableListOf<String>()
                    if (id != null) {
                        val eCur = cr.query(
                            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?",
                            arrayOf(id),
                            null
                        )
                        eCur?.use { ec ->
                            val eIdx = ec.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                            while (ec.moveToNext()) {
                                if (eIdx >= 0) emailList.add(ec.getString(eIdx))
                            }
                        }
                    }

                    contactViewModel.addContact(
                        displayName = name,
                        officialName = null,
                        phoneNumbers = phoneList,
                        emails = emailList,
                        about = null,
                        lookupKey = key
                    )
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            contactPickerLauncher.launch(null)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 80.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        if (selectedTab == FilterTab.APPS) {
                            onNavigateToAppSelection()
                        } else {
                            showAddChoiceSheet = true
                        }
                    },
                    shape = Dimens.CardShape,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                ) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
                        },
                        label = "fabIconAnim"
                    ) { tab ->
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = if (tab == FilterTab.APPS) "Add Important App" else "Add Priority Contact",
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .statusBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.SpacingStandard, vertical = Dimens.SpacingSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onNavigateBack != null) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(Dimens.SpacingSmall))
                }
                Text(
                    text = "Priority Filters",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(Dimens.SpacingSmall))

            // Pill Shaped Tab Switcher (Material 3 Expressive Style)
            M3ExpressivePillTabSwitcher(
                tabs = FilterTab.values().toList(),
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                tabLabel = { it.title },
                tabBadgeCount = { tab ->
                    if (tab == FilterTab.APPS) distinctApps.size else contacts.size
                },
                modifier = Modifier.padding(horizontal = Dimens.SpacingStandard)
            )

            Spacer(modifier = Modifier.height(Dimens.SpacingMedium))

            // Tab Content with Animated Transition
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    if (targetState.ordinal > initialState.ordinal) {
                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> -width } + fadeOut()
                        )
                    } else {
                        (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> width } + fadeOut()
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
                label = "tabContentTransition"
            ) { currentTab ->
                when (currentTab) {
                    FilterTab.APPS -> {
                        if (apps.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .navigationBarsPadding()
                                    .padding(bottom = 80.dp)
                                    .offset(y = (-16).dp)
                                    .padding(horizontal = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Surface(
                                        modifier = Modifier.size(72.dp),
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Apps,
                                                contentDescription = null,
                                                modifier = Modifier.size(36.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(Dimens.SpacingStandard))

                                    Text(
                                        text = "No Apps Added",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(Dimens.SpacingSmall))
                                    Text(
                                        text = "Add apps to prioritize notifications.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = Dimens.SpacingStandard),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(Dimens.SpacingLarge))
                                    Button(
                                        onClick = onNavigateToAppSelection,
                                        shape = Dimens.SquircleShape,
                                        modifier = Modifier.height(46.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Add Apps", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = Dimens.SpacingNano, bottom = 140.dp)
                            ) {
                                items(distinctApps, key = { it.packageName }) { app ->
                                    GoogleAppCard(
                                        appName = app.appName,
                                        packageName = app.packageName,
                                        prompt = app.prompt,
                                        replyStyle = app.replyStyle,
                                        onEditPrompt = { editingPromptApp = app },
                                        onDelete = { appViewModel.toggleImportant(app) }
                                    )
                                }
                            }
                        }
                    }

                    FilterTab.CONTACTS -> {
                        if (contacts.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .navigationBarsPadding()
                                    .padding(bottom = 80.dp)
                                    .offset(y = (-16).dp)
                                    .padding(horizontal = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Surface(
                                        modifier = Modifier.size(72.dp),
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Contacts,
                                                contentDescription = null,
                                                modifier = Modifier.size(36.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(Dimens.SpacingStandard))

                                    Text(
                                        text = "No Contacts",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(Dimens.SpacingSmall))
                                    Text(
                                        text = "Add contacts you want prioritized.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = Dimens.SpacingStandard),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(Dimens.SpacingLarge))
                                    Button(
                                        onClick = { showAddChoiceSheet = true },
                                        shape = Dimens.SquircleShape,
                                        modifier = Modifier.height(46.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Add Contact", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = Dimens.SpacingNano, bottom = 140.dp)
                            ) {
                                items(contacts, key = { it.id }) { contact ->
                                    GoogleContactCard(
                                        contact = contact,
                                        onClick = { onNavigateToContactDetail(contact.id) },
                                        onEdit = {
                                            contactBeingEdited = contact
                                            showFormDialog = true
                                        },
                                        onDelete = { contactViewModel.removeContact(contact) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // App AI Reply Configuration Dialog
    if (editingPromptApp != null) {
        val targetApp = editingPromptApp!!
        val presetStyles = remember { listOf("Concise", "Professional", "Friendly", "Casual", "Detailed") }
        var promptText by remember(targetApp) { mutableStateOf(targetApp.prompt ?: "") }
        var isCustomStyle by remember(targetApp) {
            mutableStateOf(
                targetApp.replyStyle != null &&
                !presetStyles.any { it.equals(targetApp.replyStyle, ignoreCase = true) }
            )
        }
        var selectedStyle by remember(targetApp) {
            val initial = targetApp.replyStyle ?: "Concise"
            mutableStateOf(if (presetStyles.any { it.equals(initial, ignoreCase = true) }) initial else "Concise")
        }
        var customStyleText by remember(targetApp) {
            mutableStateOf(
                if (targetApp.replyStyle != null && !presetStyles.any { it.equals(targetApp.replyStyle, ignoreCase = true) }) {
                    targetApp.replyStyle ?: ""
                } else ""
            )
        }

        AlertDialog(
            onDismissRequest = { editingPromptApp = null },
            shape = Dimens.DialogShape,
            title = {
                Text(
                    text = "AI Reply Configuration",
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
                        text = "Customize AI replies generated for notifications from ${targetApp.appName}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Reply Style Section
                    Text(
                        text = "Reply Style",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        presetStyles.forEach { style ->
                            FilterChip(
                                selected = !isCustomStyle && selectedStyle.equals(style, ignoreCase = true),
                                onClick = {
                                    isCustomStyle = false
                                    selectedStyle = style
                                },
                                label = { Text(style, style = MaterialTheme.typography.labelSmall) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }

                        FilterChip(
                            selected = isCustomStyle,
                            onClick = { isCustomStyle = true },
                            label = { Text("Custom", style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    if (isCustomStyle) {
                        OutlinedTextField(
                            value = customStyleText,
                            onValueChange = { customStyleText = it },
                            placeholder = { Text("e.g. Sarcastic, Poetic, Executive Brief...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = Dimens.InputShape,
                            singleLine = true
                        )
                    }

                    // Custom Instructions Prompt
                    Text(
                        text = "Custom Instructions (Prompt)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        placeholder = { Text("e.g. Always respond in Hindi, keep answers under 10 words, never use emojis...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        shape = Dimens.InputShape,
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalStyle = if (isCustomStyle) {
                            customStyleText.trim().ifBlank { null }
                        } else {
                            selectedStyle.trim().ifBlank { null }
                        }
                        val finalPrompt = promptText.trim().ifBlank { null }
                        appViewModel.updateAppConfig(targetApp.packageName, finalPrompt, finalStyle)
                        editingPromptApp = null
                    },
                    shape = Dimens.SquircleShape
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { editingPromptApp = null }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Options Choice Modal Bottom Sheet
    if (showAddChoiceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddChoiceSheet = false },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Add Priority Contact",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Surface(
                    shape = Dimens.CardShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showAddChoiceSheet = false
                            contactBeingEdited = null
                            showFormDialog = true
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(42.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Create Contact Manually",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Enter names, multiple numbers, and notes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Surface(
                    shape = Dimens.CardShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showAddChoiceSheet = false
                            when {
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.READ_CONTACTS
                                ) == PackageManager.PERMISSION_GRANTED -> {
                                    contactPickerLauncher.launch(null)
                                }
                                else -> {
                                    permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                }
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(42.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Contacts,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Select from Contacts",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Choose directly from device address book",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Manual Form Add/Edit Dialog
    if (showFormDialog) {
        AddEditContactDialog(
            contactToEdit = contactBeingEdited,
            onDismiss = {
                showFormDialog = false
                contactBeingEdited = null
            },
            onSave = { formData ->
                val editing = contactBeingEdited
                if (editing != null) {
                    contactViewModel.updateContact(
                        editing.copy(
                            displayName = formData.displayName,
                            officialName = formData.officialName,
                            phoneNumbers = formData.phoneNumbers,
                            emails = formData.emails,
                            about = formData.about
                        )
                    )
                } else {
                    contactViewModel.addContact(
                        displayName = formData.displayName,
                        officialName = formData.officialName,
                        phoneNumbers = formData.phoneNumbers,
                        emails = formData.emails,
                        about = formData.about,
                        lookupKey = null
                    )
                }
                showFormDialog = false
                contactBeingEdited = null
            }
        )
    }
}
