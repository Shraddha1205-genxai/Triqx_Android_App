package com.example.triqx.ui.priority

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.triqx.data.local.ObligationCategory
import com.example.triqx.data.local.ObligationEntity
import com.example.triqx.data.local.ObligationUrgency
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriorityScreen(
    viewModel: PriorityViewModel,
    onOpenChat: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val obligations by viewModel.activeObligations.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Priority Manager",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 80.dp)
            ) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add obligation")
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        if (obligations.isEmpty()) {
            EmptyPriorityState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(obligations, key = { it.id }) { obligation ->
                    PriorityObligationCard(
                        obligation = obligation,
                        onMarkDone = { viewModel.markDone(obligation.id) },
                        onSnooze = {
                            val newDue = (obligation.dueTimestamp ?: System.currentTimeMillis()) +
                                    TimeUnit.HOURS.toMillis(24)
                            viewModel.snooze(obligation.id, newDue)
                        },
                        onDelete = { viewModel.delete(obligation.id) },
                        onOpenChat = {
                            obligation.conversationKey?.let { onOpenChat(it) }
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddObligationDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { obligation ->
                viewModel.addObligation(obligation)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun EmptyPriorityState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 80.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Assignment,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                "No pending obligations",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Your tracked tasks and deadlines will appear here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun PriorityObligationCard(
    obligation: ObligationEntity,
    onMarkDone: () -> Unit,
    onSnooze: () -> Unit,
    onDelete: () -> Unit,
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val now = System.currentTimeMillis()
    val isOverdue = obligation.dueTimestamp != null && obligation.dueTimestamp < now
    val urgencyColor = when (obligation.urgency) {
        ObligationUrgency.Critical -> Color(0xFFD32F2F)
        ObligationUrgency.High -> Color(0xFFE53935)
        ObligationUrgency.Medium -> Color(0xFFFFA726)
        ObligationUrgency.Low -> Color(0xFF66BB6A)
    }
    val cardContainerColor by animateColorAsState(
        targetValue = if (isOverdue)
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        else
            MaterialTheme.colorScheme.surfaceContainerLow,
        label = "cardColor"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardContainerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Row 1: Title + Urgency pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = obligation.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (obligation.dueTimestamp != null) {
                        Text(
                            text = "Due: ${formatDate(obligation.dueTimestamp)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOverdue) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                // Urgency pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = urgencyColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = obligation.urgency.name.uppercase(),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = urgencyColor
                    )
                }
            }

            // Row 2: Identity + Category + Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(obligation.identity, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(28.dp)
                )
                AssistChip(
                    onClick = {},
                    label = { Text(obligation.category.displayName, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(28.dp)
                )
                if (obligation.amount != null) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "\u20B9${String.format("%.0f", obligation.amount)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Row 3: Overdue progress bar (only if overdue)
            if (isOverdue && obligation.dueTimestamp != null) {
                val overdueDays = TimeUnit.MILLISECONDS.toDays(now - obligation.dueTimestamp)
                    .coerceAtLeast(1)
                val overdueProgress by animateFloatAsState(
                    targetValue = (overdueDays.toFloat() / 7f).coerceAtMost(1f),
                    label = "overdueProgress"
                )
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Overdue by",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            "$overdueDays day(s)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { overdueProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.error,
                        trackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    )
                }
            }

            // Row 4: Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onMarkDone,
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Mark Done", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(
                        Icons.Filled.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Snooze", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.weight(1f))
                if (obligation.conversationKey != null) {
                    IconButton(onClick = onOpenChat, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open chat",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddObligationDialog(
    onDismiss: () -> Unit,
    onAdd: (ObligationEntity) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var identity by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ObligationCategory.Personal) }
    var urgency by remember { mutableStateOf(ObligationUrgency.Medium) }
    var amountText by remember { mutableStateOf("") }

    val categories = ObligationCategory.entries
    val urgencies = ObligationUrgency.entries

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Obligation", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    placeholder = { Text("e.g. Electricity bill payment") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = identity,
                    onValueChange = { identity = it },
                    label = { Text("Identity") },
                    placeholder = { Text("e.g. Tata Power, Rahul, HR") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // Category chips
                Text("Category", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.take(3).forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.displayName, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.drop(3).forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.displayName, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }
                // Urgency chips
                Text("Urgency", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    urgencies.forEach { urg ->
                        FilterChip(
                            selected = urgency == urg,
                            onClick = { urgency = urg },
                            label = { Text(urg.displayName, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount (optional)") },
                    placeholder = { Text("e.g. 3200") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && identity.isNotBlank()) {
                        onAdd(
                            ObligationEntity(
                                title = title.trim(),
                                category = category,
                                identity = identity.trim(),
                                createdAt = System.currentTimeMillis(),
                                urgency = urgency,
                                amount = amountText.toDoubleOrNull()
                            )
                        )
                    }
                },
                enabled = title.isNotBlank() && identity.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
