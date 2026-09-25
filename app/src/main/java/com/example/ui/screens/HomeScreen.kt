package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ui.MainViewModel
import com.example.ui.theme.ZeroGreenSuccess
import com.example.ui.theme.ZeroRedError

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
    onNavigateToScanner: () -> Unit,
    onNavigateToLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val config by viewModel.config.collectAsState()
    val allowedSenders by viewModel.allowedSenders.collectAsState()
    val isForwardingActive by viewModel.isForwardingActive.collectAsState()
    val successCount by viewModel.successCount.collectAsState()
    val failedCount by viewModel.failedCount.collectAsState()
    val testStatus by viewModel.testStatus.collectAsState()
    val isTesting by viewModel.isTesting.collectAsState()

    var showAddSenderDialog by remember { mutableStateOf(false) }
    var showSimulateSmsDialog by remember { mutableStateOf(false) }
    var newSenderInput by remember { mutableStateOf("") }

    var isManualSetupExpanded by remember { mutableStateOf(!config.isConfigured) }
    var manualWebhookUrl by remember(config.webhookUrl) { mutableStateOf(config.webhookUrl) }
    var manualDeviceSecret by remember(config.deviceSecret) { mutableStateOf(config.deviceSecret) }
    var isSecretVisible by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? Activity

    // Check required permissions
    var hasSmsPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

    var showRestrictedSettingsDialog by remember { mutableStateOf(false) }
    var hasRequestedSmsPermissionsOnce by remember { mutableStateOf(false) }

    // Re-check permissions when returning from Settings or background
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasSmsPermissions =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val receiveGranted = results[Manifest.permission.RECEIVE_SMS] == true
        val readGranted = results[Manifest.permission.READ_SMS] == true
        hasSmsPermissions = receiveGranted && readGranted

        if (!hasSmsPermissions) {
            val shouldShowReceive = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECEIVE_SMS)
            } ?: false
            val shouldShowRead = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.READ_SMS)
            } ?: false

            // On Android 13+, sideloaded apps have restricted settings where standard dialog fails or is blocked
            if (!shouldShowReceive || !shouldShowRead || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                showRestrictedSettingsDialog = true
            }
        }
    }

    LaunchedEffect(testStatus) {
        testStatus?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearTestStatus()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Brand Banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("brand_banner_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        shadowElevation = 2.dp,
                        modifier = Modifier.size(54.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = "Zero Pay Logo",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Zero Pay Forwarder",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "PAYMENTS WITHOUT LIMITS",
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // 1. Connection Status Banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("connection_status_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (config.isConfigured) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(if (config.isConfigured) ZeroGreenSuccess else ZeroRedError)
                            )
                            Text(
                                text = if (config.isConfigured) "CONNECTED" else "NOT CONFIGURED",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (config.isConfigured) ZeroGreenSuccess else ZeroRedError
                            )
                        }

                        if (!config.isConfigured) {
                            Button(
                                onClick = onNavigateToScanner,
                                modifier = Modifier.testTag("scan_qr_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scan QR")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (config.isConfigured) {
                        Text(
                            text = "Zero Pay Webhook Gateway",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Device ID: ${config.deviceId}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "URL: ${config.webhookUrl}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    } else {
                        Text(
                            text = "Set up your Zero Pay connection",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Scan the QR code displayed in your Zero Pay merchant dashboard, or use manual configuration below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Manual Setup Toggle and Form
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isManualSetupExpanded = !isManualSetupExpanded }
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (config.isConfigured) "Manual Setup (Edit Credentials)" else "Manual Setup Fallback",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Icon(
                            imageVector = if (isManualSetupExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isManualSetupExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    AnimatedVisibility(visible = isManualSetupExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = manualWebhookUrl,
                                onValueChange = { manualWebhookUrl = it },
                                label = { Text("Webhook URL") },
                                placeholder = { Text("https://zeropay-dev.vercel.app/api/webhooks/sms") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("manual_webhook_url_input"),
                                shape = RoundedCornerShape(10.dp)
                            )

                            OutlinedTextField(
                                value = manualDeviceSecret,
                                onValueChange = { manualDeviceSecret = it },
                                label = { Text("Device Secret") },
                                placeholder = { Text("dev-device-secret-12345") },
                                singleLine = true,
                                visualTransformation = if (isSecretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { isSecretVisible = !isSecretVisible }) {
                                        Icon(
                                            imageVector = if (isSecretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (isSecretVisible) "Hide secret" else "Show secret"
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("manual_device_secret_input"),
                                shape = RoundedCornerShape(10.dp)
                            )

                            Button(
                                onClick = {
                                    val devId = config.deviceId.ifBlank { "android-samsung-a52-01" }
                                    viewModel.saveConfigManual(
                                        webhookUrl = manualWebhookUrl.trim(),
                                        deviceSecret = manualDeviceSecret.trim(),
                                        deviceId = devId
                                    )
                                    isManualSetupExpanded = false
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("manual_save_config_button"),
                                shape = RoundedCornerShape(10.dp),
                                enabled = manualWebhookUrl.isNotBlank() && manualDeviceSecret.isNotBlank()
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Configuration")
                            }
                        }
                    }
                }
            }
        }

        // 2. Master Forwarding Toggle & Active Service Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("forwarding_toggle_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Forwarding Service",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isForwardingActive) "Active — Forwarding allowed SMS in background" else "Paused — Incoming SMS won't be dispatched",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = isForwardingActive,
                        onCheckedChange = { viewModel.setForwardingActive(it) },
                        modifier = Modifier.testTag("forwarding_switch")
                    )
                }
            }
        }

        // 3. Permission Notice (if missing)
        if (!hasSmsPermissions) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = ZeroRedError)
                            Text(
                                text = "SMS Permissions Required",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = ZeroRedError
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "The app needs RECEIVE_SMS and READ_SMS to detect incoming payment confirmation SMS in background.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (!hasSmsPermissions) {
                                        val shouldShowReceive = activity?.let {
                                            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECEIVE_SMS)
                                        } ?: false

                                        if (hasRequestedSmsPermissionsOnce && !shouldShowReceive) {
                                            showRestrictedSettingsDialog = true
                                        } else {
                                            hasRequestedSmsPermissionsOnce = true
                                            val perms = mutableListOf(
                                                Manifest.permission.RECEIVE_SMS,
                                                Manifest.permission.READ_SMS
                                            )
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                perms.add(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                            permissionsLauncher.launch(perms.toTypedArray())
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ZeroRedError),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("grant_sms_permissions_button")
                            ) {
                                Text("Grant SMS Permissions", color = Color.White)
                            }

                            OutlinedButton(
                                onClick = { showRestrictedSettingsDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("restricted_settings_help_button")
                            ) {
                                Text("Settings Help")
                            }
                        }
                    }
                }
            }
        }

        // 4. Allowed Senders List (Section 2 of requirements)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("allowed_senders_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Allowed SMS Senders",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Only SMS from these senders will be forwarded",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { showAddSenderDialog = true },
                            modifier = Modifier.testTag("add_sender_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Sender", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Preset standard payment providers (Bangladeshi MFS & Banks)
                    val standardPresets = listOf("bKash", "Nagad", "16216", "16167", "ROCKET", "UPAY")
                    
                    Text(
                        text = "Standard Providers:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        standardPresets.forEach { sender ->
                            val isChecked = allowedSenders.contains(sender)
                            FilterChip(
                                selected = isChecked,
                                onClick = { viewModel.toggleSender(sender, !isChecked) },
                                label = { Text(sender, fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = if (isChecked) {
                                    {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Full list with checkboxes
                    Text(
                        text = "Active Filter List (${allowedSenders.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (allowedSenders.isEmpty()) {
                        Text(
                            text = "No allowed senders selected. All incoming SMS will be ignored.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        allowedSenders.sorted().forEach { sender ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Checkbox(
                                        checked = true,
                                        onCheckedChange = { viewModel.toggleSender(sender, false) },
                                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = sender,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.removeSender(sender) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove $sender",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Test Tools & Actions
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Testing & Diagnostics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Verify your webhook and simulate SMS without a physical SIM card",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { viewModel.sendTestWebhook() },
                            enabled = !isTesting && config.isConfigured,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("test_webhook_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test Ping")
                            }
                        }

                        OutlinedButton(
                            onClick = { showSimulateSmsDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("simulate_sms_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Simulate SMS")
                        }
                    }
                }
            }
        }

        // 6. Quick Stats
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "$successCount",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Forwarded",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "$failedCount",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Failed/Filtered",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    // Add Custom Sender Dialog
    if (showAddSenderDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddSenderDialog = false
                newSenderInput = ""
            },
            title = { Text("Add Allowed Sender") },
            text = {
                Column {
                    Text(
                        "Enter the exact sender name, phone number, or shortcode (e.g., 'bKash', '16216', '+8801700000000'):",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newSenderInput,
                        onValueChange = { newSenderInput = it },
                        label = { Text("Sender Name / Number") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_sender_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSenderInput.isNotBlank()) {
                            viewModel.addCustomSender(newSenderInput.trim())
                            newSenderInput = ""
                            showAddSenderDialog = false
                        }
                    },
                    modifier = Modifier.testTag("save_custom_sender_button")
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddSenderDialog = false
                    newSenderInput = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Simulate SMS Dialog for testing
    if (showSimulateSmsDialog) {
        var simSender by remember { mutableStateOf("bKash") }
        var simBody by remember {
            mutableStateOf("You have received Tk 1,500.00 from 01712345678. Ref: ORDER#9948. Fee Tk 0.00. Balance Tk 4,520.50. TrxID 9K48LA01 at 24/09/2026 19:20")
        }

        AlertDialog(
            onDismissRequest = { showSimulateSmsDialog = false },
            title = { Text("Simulate Incoming SMS") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Test how the app filters and dispatches incoming messages:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = simSender,
                        onValueChange = { simSender = it },
                        label = { Text("Sender") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("simulate_sender_input")
                    )
                    OutlinedTextField(
                        value = simBody,
                        onValueChange = { simBody = it },
                        label = { Text("SMS Message Body") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth().testTag("simulate_body_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.simulateIncomingSms(simSender, simBody)
                        showSimulateSmsDialog = false
                    },
                    modifier = Modifier.testTag("submit_simulation_button")
                ) {
                    Text("Dispatch SMS")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSimulateSmsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Android 13+ Restricted Settings Dialog for Sideloaded Apps
    if (showRestrictedSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showRestrictedSettingsDialog = false },
            title = {
                Text(
                    text = "Action Required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Because this app was installed outside the Play Store, Android restricts SMS access. To fix this:\n1. Click 'Go to Settings'.\n2. Tap the 3 dots (top right) and select 'Allow restricted settings'.\n3. Go to Permissions and allow SMS.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestrictedSettingsDialog = false
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + context.packageName)
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.testTag("go_to_settings_button")
                ) {
                    Text("Go to Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestrictedSettingsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
