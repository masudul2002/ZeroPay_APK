package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.ui.permission.SmsPermissionRationaleDialog
import com.example.ui.permission.SmsPermissionSettingsDialog
import com.example.ui.permission.rememberSmsPermissionState
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.local.entity.SmsLogEntity
import com.example.ui.MainViewModel
import com.example.ui.theme.ZeroGreenSuccess
import com.example.ui.theme.ZeroRedError

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
    onNavigateToLogs: () -> Unit
) {
    val context = LocalContext.current
    val config by viewModel.config.collectAsState()
    val isMonitoringActive by viewModel.isForwardingActive.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val successCount by viewModel.successCount.collectAsState()
    val failedCount by viewModel.failedCount.collectAsState()
    val lastError by viewModel.lastError.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()

    lateinit var permissionLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>

    val permissionState = rememberSmsPermissionState(
        onPermissionGranted = {
            viewModel.setForwardingActive(true)
        }
    )

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        permissionState.handlePermissionResult(perms)
        if (permissionState.hasSmsPermission) {
            viewModel.setForwardingActive(true)
            coroutineScope.launch {
                snackbarHostState.showSnackbar("SMS Monitoring enabled successfully")
            }
        } else {
            viewModel.setForwardingActive(false)
            coroutineScope.launch {
                snackbarHostState.showSnackbar("SMS permission is required to detect incoming payments")
            }
        }
    }
    permissionLauncher = launcher

    // Filter recent logs by TrxID or general search query, restricted to max 3 items
    val recentThreeLogs = remember(logs, searchQuery) {
        val filtered = if (searchQuery.isBlank()) {
            logs
        } else {
            val q = searchQuery.trim().lowercase()
            logs.filter {
                it.messageBody.lowercase().contains(q) ||
                it.sender.lowercase().contains(q)
            }
        }
        filtered.take(3)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Red alert card: shown ONLY when there is an actual system error or webhook error
        if (lastError != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("error_alert_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.padding(8.dp).fillMaxSize()
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Webhook Dispatch Error",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = lastError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
                                fontFamily = FontFamily.Monospace,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { viewModel.clearLastError() }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Dismiss error",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }

        // Permission Missing Warning (if SMS permission is not granted)
        if (!permissionState.hasSmsPermission) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sms_permission_warning_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFF7ED)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFFED7AA))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFEA580C).copy(alpha = 0.15f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = Color(0xFFEA580C),
                                    modifier = Modifier.padding(8.dp).fillMaxSize()
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "SMS Permission Required",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF9A3412)
                                )
                                Text(
                                    text = "Monitoring is paused. ZeroPay requires SMS access to detect and forward incoming transaction alerts.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFC2410C)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    permissionState.requestPermissions { perms ->
                                        permissionLauncher.launch(perms)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("enable_sms_permission_button")
                            ) {
                                Text(
                                    text = if (permissionState.isPermanentlyDenied()) "Open Settings" else "Review & Enable",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 1. Service Status Toggle (Top component)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("service_status_toggle_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isMonitoringActive) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    }
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isMonitoringActive) ZeroGreenSuccess.copy(alpha = 0.15f) else Color(0xFF64748B).copy(alpha = 0.15f),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Icon(
                                    imageVector = if (isMonitoringActive) Icons.Default.PlayCircle else Icons.Default.PauseCircle,
                                    contentDescription = null,
                                    tint = if (isMonitoringActive) ZeroGreenSuccess else Color(0xFF64748B),
                                    modifier = Modifier.padding(8.dp).fillMaxSize()
                                )
                            }
                            Column {
                                Text(
                                    text = "SMS Monitoring Service",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isMonitoringActive) ZeroGreenSuccess else Color(0xFF94A3B8))
                                    )
                                    Text(
                                        text = if (isMonitoringActive) "ACTIVE (LISTENING)" else "MONITORING STOPPED",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isMonitoringActive) ZeroGreenSuccess else Color(0xFF64748B)
                                    )
                                }
                            }
                        }

                        // Prominent Switch
                        Switch(
                            checked = isMonitoringActive,
                            onCheckedChange = { active ->
                                if (active) {
                                    if (!permissionState.hasSmsPermission) {
                                        permissionState.requestPermissions { perms ->
                                            permissionLauncher.launch(perms)
                                        }
                                    } else {
                                        viewModel.setForwardingActive(true)
                                    }
                                } else {
                                    viewModel.setForwardingActive(false)
                                }
                            },
                            modifier = Modifier.testTag("service_status_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ZeroGreenSuccess
                            )
                        )
                    }

                    // Prominent action button toggle
                    Button(
                        onClick = {
                            if (!isMonitoringActive) {
                                if (!permissionState.hasSmsPermission) {
                                    permissionState.requestPermissions { perms ->
                                        permissionLauncher.launch(perms)
                                    }
                                } else {
                                    viewModel.setForwardingActive(true)
                                }
                            } else {
                                viewModel.setForwardingActive(false)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("toggle_monitoring_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isMonitoringActive) MaterialTheme.colorScheme.error.copy(alpha = 0.85f) else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (isMonitoringActive) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isMonitoringActive) "Stop Monitoring" else "Start Monitoring",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 2. Statistics Cards: 3 summary boxes
        // 1. SMS Received (Total count)
        // 2. SMS Forwarded (Total count)
        // 3. Errors (Total count of failed webhook dispatches)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("statistics_cards_row"),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Box 1: SMS Received
                StatSummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "SMS Received",
                    value = logs.size.toString(),
                    icon = Icons.Default.Sms,
                    accentColor = Color(0xFF0052FF),
                    backgroundColor = Color(0xFFEFF6FF)
                )

                // Box 2: SMS Forwarded
                StatSummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "SMS Forwarded",
                    value = successCount.toString(),
                    icon = Icons.Default.MarkEmailRead,
                    accentColor = ZeroGreenSuccess,
                    backgroundColor = Color(0xFFF0FDF4)
                )

                // Box 3: Errors
                StatSummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "Errors",
                    value = failedCount.toString(),
                    icon = Icons.Default.Error,
                    accentColor = ZeroRedError,
                    backgroundColor = Color(0xFFFEF2F2)
                )
            }
        }

        // 3. Search Bar: "Search by TrxID"
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_trxid_input"),
                label = { Text("Search by TrxID") },
                placeholder = { Text("e.g. BL049281, TrxID, or sender...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
        }

        // 4. Recent Activity: "Last Received SMS" (Restricted to only 3 most recent SMS items)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Last Received SMS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                TextButton(
                    onClick = onNavigateToLogs,
                    modifier = Modifier.testTag("view_all_logs_button")
                ) {
                    Text(
                        text = "View All (${logs.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        if (recentThreeLogs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.HourglassEmpty,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = if (searchQuery.isBlank()) "No SMS received yet" else "No SMS matched '$searchQuery'",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (searchQuery.isBlank()) "Ensure monitoring is active and SMS permissions are granted." else "Try another TrxID or clear search.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(recentThreeLogs, key = { it.id }) { log ->
                RecentSmsItemCard(log = log)
            }
        }
    }

    // Prominent educational rationale dialog before system prompt
    if (permissionState.showRationaleDialog) {
        SmsPermissionRationaleDialog(
            onConfirm = {
                permissionState.proceedToSystemPrompt { perms ->
                    permissionLauncher.launch(perms)
                }
            },
            onDismiss = {
                permissionState.showRationaleDialog = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("SMS monitoring paused until permission is granted")
                }
            }
        )
    }

    // Direct system settings dialog if permanently denied
    if (permissionState.showSettingsDialog) {
        SmsPermissionSettingsDialog(
            onOpenSettings = {
                permissionState.openAppSettings()
            },
            onDismiss = {
                permissionState.showSettingsDialog = false
            }
        )
    }
}

@Composable
private fun StatSummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    backgroundColor: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.15f),
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.padding(7.dp).fillMaxSize()
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF475569),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RecentSmsItemCard(log: SmsLogEntity) {
    val isSuccess = log.status == "SUCCESS"
    val statusColor = if (isSuccess) ZeroGreenSuccess else if (log.status == "PAUSED") Color(0xFF64748B) else ZeroRedError

    // Extract potential TrxID
    val trxId = remember(log.messageBody) {
        val regex = Regex("""(?i)(?:TrxID|TxnId|TxID|Transaction ID|Ref:?)\s*[:#]?\s*([A-Za-z0-9]+)""")
        regex.find(log.messageBody)?.groupValues?.getOrNull(1)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = log.sender,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = log.simSlot,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Status badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = log.status,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            if (trxId != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF0052FF).copy(alpha = 0.08f)
                ) {
                    Text(
                        text = "TrxID: $trxId",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0052FF),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = log.messageBody,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = log.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (log.httpCode != null) "HTTP ${log.httpCode}" else (log.errorMessage ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
