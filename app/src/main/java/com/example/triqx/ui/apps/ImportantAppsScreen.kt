package com.example.triqx.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.triqx.ui.components.AppIcon
import com.example.triqx.ui.components.TriqxSearchBar
import com.example.triqx.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImportantAppsScreen(
    viewModel: AppViewModel,
    onNavigateToSelection: () -> Unit,
    onNavigateBack: (() -> Unit)? = null
) {
    val apps by viewModel.savedImportantApps.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var editingPromptApp by remember { mutableStateOf<AppInfo?>(null) }

    val filteredApps = remember(apps, searchQuery) {
        val list = if (searchQuery.isBlank()) {
            apps
        } else {
            apps.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
        list.distinctBy { it.packageName }
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
                    onClick = onNavigateToSelection,
                    shape = Dimens.CardShape,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Important App", modifier = Modifier.size(26.dp))
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .statusBarsPadding()
                .padding(top = Dimens.ScreenTopPadding)
        ) {
            // Google Floating Search Bar Pill
            TriqxSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search important apps...",
                onNavigateBack = onNavigateBack,
                trailingContent = {
                    Surface(
                        modifier = Modifier.size(34.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${apps.size}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(Dimens.SpacingSmall))

            if (apps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
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
                            onClick = onNavigateToSelection,
                            shape = Dimens.SquircleShape,
                            modifier = Modifier.height(46.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Apps", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 80.dp)
                        .offset(y = (-16).dp)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No apps match \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = Dimens.SpacingNano, bottom = 140.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        GoogleAppCard(
                            appName = app.appName,
                            packageName = app.packageName,
                            prompt = app.prompt,
                            replyStyle = app.replyStyle,
                            onEditPrompt = { editingPromptApp = app },
                            onDelete = { viewModel.toggleImportant(app) }
                        )
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
                                    selectedStyle = style
                                    isCustomStyle = false
                                },
                                label = { Text(style, style = MaterialTheme.typography.bodySmall) }
                            )
                        }

                        FilterChip(
                            selected = isCustomStyle,
                            onClick = { isCustomStyle = true },
                            leadingIcon = {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            label = { Text("Custom", style = MaterialTheme.typography.bodySmall) }
                        )
                    }

                    if (isCustomStyle) {
                        OutlinedTextField(
                            value = customStyleText,
                            onValueChange = { customStyleText = it },
                            modifier = Modifier.fillMaxWidth(),
                            shape = Dimens.InputShape,
                            label = { Text("Custom Reply Style") },
                            placeholder = { Text("e.g. Humorous, Sarcastic, Formal") },
                            singleLine = true
                        )
                    }

                    // Prompt Section
                    Text(
                        text = "App Prompt",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = Dimens.InputShape,
                        label = { Text("Prompt for ${targetApp.appName}") },
                        placeholder = { Text("e.g. Keep replies under 5 words, friendly tone") },
                        minLines = 3,
                        maxLines = 5
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalStyle = if (isCustomStyle) {
                            customStyleText.trim().ifBlank { "Concise" }
                        } else {
                            selectedStyle
                        }
                        viewModel.updateAppConfig(targetApp.packageName, promptText, finalStyle)
                        editingPromptApp = null
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)) {
                    if (!targetApp.prompt.isNullOrBlank()) {
                        TextButton(
                            onClick = {
                                viewModel.updateAppConfig(targetApp.packageName, null, targetApp.replyStyle ?: "Concise")
                                editingPromptApp = null
                            }
                        ) {
                            Text("Clear Prompt", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { editingPromptApp = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

@Composable
fun GoogleAppCard(
    appName: String,
    packageName: String,
    prompt: String? = null,
    replyStyle: String? = "Concise",
    onEditPrompt: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEditPrompt)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.SpacingStandard, vertical = Dimens.SpacingSemiMedium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingMedium)
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = Dimens.SquircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                AppIcon(
                    packageName = packageName,
                    appName = appName,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reply Style Chip
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = replyStyle ?: "Concise",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Prompt Chip
                    if (!prompt.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.SmartToy,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = prompt,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            IconButton(onClick = onEditPrompt, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Configure AI Reply",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove App",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 74.dp, end = Dimens.SpacingStandard),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            thickness = 0.5.dp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectionScreen(
    viewModel: AppViewModel,
    onNavigateBack: () -> Unit
) {
    val apps by viewModel.allApps.collectAsState()
    val savedApps by viewModel.savedImportantApps.collectAsState()
    val isLoading by viewModel.isLoadingApps.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    val selectedPackages = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        viewModel.loadInstalledAppsIfNeeded()
    }

    var isInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(savedApps) {
        if (!isInitialized && savedApps.isNotEmpty()) {
            selectedPackages.clear()
            selectedPackages.addAll(savedApps.map { it.packageName })
            isInitialized = true
        }
    }

    val filteredApps = remember(apps, searchQuery) {
        val list = if (searchQuery.isBlank()) {
            apps
        } else {
            apps.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
        list.distinctBy { it.packageName }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Select Important Apps", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            viewModel.saveSelectedApps(selectedPackages.toSet(), apps)
                            onNavigateBack()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save (${selectedPackages.size})", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (apps.isNotEmpty()) {
                Surface(
                    tonalElevation = 4.dp,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            viewModel.saveSelectedApps(selectedPackages.toSet(), apps)
                            onNavigateBack()
                        },
                        shape = Dimens.CardShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(50.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Selection (${selectedPackages.size} Apps)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding() + Dimens.ScreenTopPadding,
                    bottom = innerPadding.calculateBottomPadding()
                )
        ) {
            // Search Bar Pill
            TriqxSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search installed apps..."
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = Dimens.SpacingNano, bottom = 80.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isSelected = selectedPackages.contains(app.packageName)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isSelected) {
                                        selectedPackages.remove(app.packageName)
                                    } else {
                                        selectedPackages.add(app.packageName)
                                    }
                                }
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                    else Color.Transparent
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Dimens.SpacingStandard, vertical = Dimens.SpacingSemiMedium),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingMedium)
                            ) {
                                Surface(
                                    modifier = Modifier.size(42.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                                ) {
                                    AppIcon(
                                        packageName = app.packageName,
                                        appName = app.appName,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(4.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.appName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            selectedPackages.add(app.packageName)
                                        } else {
                                            selectedPackages.remove(app.packageName)
                                        }
                                    }
                                )
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 70.dp, end = Dimens.SpacingStandard),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            }
        }
    }
}
