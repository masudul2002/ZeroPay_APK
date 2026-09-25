package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Security
import com.example.R
import com.example.ui.MainViewModel
import com.example.ui.permission.SmsPermissionRationaleDialog
import com.example.ui.permission.SmsPermissionSettingsDialog
import com.example.ui.permission.rememberSmsPermissionState
import com.example.ui.theme.ZeroGreenSuccess

@Composable
fun SetupScreen(
    viewModel: MainViewModel,
    onNavigateToScanner: () -> Unit,
    onSetupComplete: () -> Unit
) {
    val config by viewModel.config.collectAsState()
    val isTesting by viewModel.isTesting.collectAsState()
    val testStatus by viewModel.testStatus.collectAsState()
    val lastError by viewModel.lastError.collectAsState()

    var manualWebhookUrl by remember(config.webhookUrl) { mutableStateOf(config.webhookUrl) }
    var manualDeviceSecret by remember(config.deviceSecret) { mutableStateOf(config.deviceSecret) }
    var manualDeviceId by remember(config.deviceId) { mutableStateOf(config.deviceId.ifBlank { "sim-gateway-01" }) }
    var isSecretVisible by remember { mutableStateOf(false) }
    var isManualExpanded by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val permissionState = rememberSmsPermissionState()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        permissionState.handlePermissionResult(perms)
    }

    // If config becomes configured, automatically route to dashboard
    LaunchedEffect(config.isConfigured) {
        if (config.isConfigured) {
            onSetupComplete()
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Official Logo
        Surface(
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 3.dp,
            modifier = Modifier.size(92.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "ZeroPay Logo",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Fit
            )
        }

        // Welcome / Brand Title
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Zero",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0052FF)
                )
                Text(
                    text = "Pay",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00D2FF)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Gateway",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Connect this device to your ZeroPay server to start automated SMS payment forwarding.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        // Ready to Connect Status Badge
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF64748B))
                    )
                    Text(
                        text = "STATUS: READY TO CONNECT",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B),
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "Offline",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // SMS Permission Status Card (Setup checklist)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("setup_sms_permission_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (permissionState.hasSmsPermission) ZeroGreenSuccess.copy(alpha = 0.08f) else Color(0xFFFFF7ED)
            ),
            border = BorderStroke(
                1.dp,
                if (permissionState.hasSmsPermission) ZeroGreenSuccess.copy(alpha = 0.3f) else Color(0xFFFED7AA)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (permissionState.hasSmsPermission) ZeroGreenSuccess.copy(alpha = 0.15f) else Color(0xFFEA580C).copy(alpha = 0.15f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = if (permissionState.hasSmsPermission) Icons.Default.CheckCircle else Icons.Default.Security,
                        contentDescription = null,
                        tint = if (permissionState.hasSmsPermission) ZeroGreenSuccess else Color(0xFFEA580C),
                        modifier = Modifier.padding(8.dp).fillMaxSize()
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SMS Forwarding Permission",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (permissionState.hasSmsPermission) ZeroGreenSuccess else Color(0xFF9A3412)
                    )
                    Text(
                        text = if (permissionState.hasSmsPermission) "Access granted. Ready to detect payment notifications." else "Required to forward incoming payment SMS alerts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (permissionState.hasSmsPermission) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFC2410C)
                    )
                }
                if (!permissionState.hasSmsPermission) {
                    Button(
                        onClick = {
                            permissionState.requestPermissions { perms ->
                                launcher.launch(perms)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("setup_grant_permission_button")
                    ) {
                        Text(
                            text = if (permissionState.isPermanentlyDenied()) "Settings" else "Grant",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Primary Connection Method: QR Code Scanner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("qr_setup_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan QR",
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxSize(),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Scan Setup QR Code",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Open ZeroPay Admin Dashboard on your PC, click 'Add SMS Device' and scan the QR code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Button(
                    onClick = onNavigateToScanner,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("scan_qr_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Open Camera Scanner",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Manual Setup Fallback
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isManualExpanded = !isManualExpanded }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Manual Setup Fallback",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(
                        imageVector = if (isManualExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(visible = isManualExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Enter server credentials manually if camera scanning is not available:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = manualWebhookUrl,
                            onValueChange = {
                                manualWebhookUrl = it
                                validationError = null
                            },
                            label = { Text("Webhook URL") },
                            placeholder = { Text("https://api.yourdomain.com/sms/callback") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("webhook_url_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = manualDeviceSecret,
                            onValueChange = {
                                manualDeviceSecret = it
                                validationError = null
                            },
                            label = { Text("Device Secret / API Key") },
                            placeholder = { Text("e.g. sec_live_...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("device_secret_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            visualTransformation = if (isSecretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isSecretVisible = !isSecretVisible }) {
                                    Icon(
                                        imageVector = if (isSecretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (isSecretVisible) "Hide secret" else "Show secret"
                                    )
                                }
                            }
                        )

                        OutlinedTextField(
                            value = manualDeviceId,
                            onValueChange = { manualDeviceId = it },
                            label = { Text("Device Identifier (Optional)") },
                            placeholder = { Text("sim-gateway-01") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("device_id_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (validationError != null) {
                            Text(
                                text = validationError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (manualWebhookUrl.isBlank()) {
                                        validationError = "Please enter Webhook URL first"
                                        return@OutlinedButton
                                    }
                                    viewModel.saveConfigManual(
                                        webhookUrl = manualWebhookUrl.trim(),
                                        deviceSecret = manualDeviceSecret.trim(),
                                        deviceId = manualDeviceId.trim().ifBlank { "sim-gateway-01" }
                                    )
                                    viewModel.sendTestWebhook()
                                },
                                enabled = !isTesting && manualWebhookUrl.isNotBlank(),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isTesting) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test URL")
                            }

                            Button(
                                onClick = {
                                    val url = manualWebhookUrl.trim()
                                    val secret = manualDeviceSecret.trim()
                                    val devId = manualDeviceId.trim().ifBlank { "sim-gateway-01" }

                                    if (url.isBlank()) {
                                        validationError = "Webhook URL is required"
                                        return@Button
                                    }
                                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                        validationError = "URL must start with http:// or https://"
                                        return@Button
                                    }
                                    if (secret.isBlank()) {
                                        validationError = "Device Secret is required"
                                        return@Button
                                    }

                                    viewModel.saveConfigManual(url, secret, devId)
                                    onSetupComplete()
                                },
                                modifier = Modifier
                                    .weight(1.3f)
                                    .testTag("save_config_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save & Connect")
                            }
                        }
                    }
                }
            }
        }

        // Test status message or error alert if any
        if (testStatus != null || lastError != null) {
            val isError = lastError != null
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (isError) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = testStatus ?: lastError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }

    if (permissionState.showRationaleDialog) {
        SmsPermissionRationaleDialog(
            onConfirm = {
                permissionState.proceedToSystemPrompt { perms ->
                    launcher.launch(perms)
                }
            },
            onDismiss = {
                permissionState.showRationaleDialog = false
            }
        )
    }

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
