package com.example.ui.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * State holder for managing SMS and Notification runtime permissions
 * following Android's sensitive permission guidelines and prominent disclosure policies.
 */
@Stable
class SmsPermissionState(
    val context: Context,
    val onPermissionGranted: () -> Unit = {}
) {
    var hasSmsPermission by mutableStateOf(checkSmsGranted(context))
        private set

    var hasNotificationPermission by mutableStateOf(checkNotificationGranted(context))
        private set

    var showRationaleDialog by mutableStateOf(false)
    var showSettingsDialog by mutableStateOf(false)

    // Tracks if user has explicitly seen the prompt this session
    private var hasPromptedOnce by mutableStateOf(false)

    fun refreshPermissions() {
        hasSmsPermission = checkSmsGranted(context)
        hasNotificationPermission = checkNotificationGranted(context)
    }

    /**
     * Determines whether permissions are permanently denied.
     * In Android, if permission is not granted AND shouldShowRequestPermissionRationale returns false
     * after the user was already asked, it usually means 'Don't ask again'.
     */
    fun isPermanentlyDenied(): Boolean {
        if (hasSmsPermission) return false
        val activity = context as? Activity ?: return false
        val receiveRationale = ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECEIVE_SMS)
        val readRationale = ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_SMS)
        // If it's missing, we previously prompted, and system says no rationale -> permanently denied
        return hasPromptedOnce && !receiveRationale && !readRationale
    }

    fun requestPermissions(onLaunchSystemDialog: (Array<String>) -> Unit) {
        refreshPermissions()
        if (hasSmsPermission) {
            onPermissionGranted()
            return
        }

        if (isPermanentlyDenied()) {
            showSettingsDialog = true
        } else {
            // Show educational rationale dialog first (Android Best Practice for sensitive SMS permission)
            showRationaleDialog = true
        }
    }

    fun proceedToSystemPrompt(onLaunchSystemDialog: (Array<String>) -> Unit) {
        showRationaleDialog = false
        hasPromptedOnce = true
        val perms = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        onLaunchSystemDialog(perms.toTypedArray())
    }

    fun handlePermissionResult(results: Map<String, Boolean>) {
        val receiveGranted = results[Manifest.permission.RECEIVE_SMS] == true
        val readGranted = results[Manifest.permission.READ_SMS] == true
        hasSmsPermission = receiveGranted && readGranted

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = results[Manifest.permission.POST_NOTIFICATIONS] == true
        }

        if (hasSmsPermission) {
            onPermissionGranted()
        }
    }

    fun openAppSettings() {
        showSettingsDialog = false
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    companion object {
        fun checkSmsGranted(context: Context): Boolean {
            val receive = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED
            val read = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED
            return receive && read
        }

        fun checkNotificationGranted(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }
    }
}

@Composable
fun rememberSmsPermissionState(
    onPermissionGranted: () -> Unit = {}
): SmsPermissionState {
    val context = LocalContext.current
    val permissionState = remember(context) {
        SmsPermissionState(context = context, onPermissionGranted = onPermissionGranted)
    }

    // Automatically recheck permissions when the app resumes (e.g., returning from System Settings)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val wasGranted = permissionState.hasSmsPermission
                permissionState.refreshPermissions()
                if (!wasGranted && permissionState.hasSmsPermission) {
                    onPermissionGranted()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return permissionState
}

/**
 * Prominent In-App Educational Disclosure Dialog
 * Complies with Google Play & Android Guidelines for Sensitive Permissions.
 */
@Composable
fun SmsPermissionRationaleDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sms_permission_rationale_dialog"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF0052FF).copy(alpha = 0.12f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = Color(0xFF0052FF),
                        modifier = Modifier
                            .padding(10.dp)
                            .size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = "SMS Access Required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Prominent Privacy Disclosure",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "ZeroPay is a payment automation forwarder. To operate automatically in the background, it needs permission to detect incoming transactional SMS messages.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Feature Highlights
                RationaleFeatureItem(
                    icon = Icons.Default.Sync,
                    title = "Instant Payment Forwarding",
                    description = "Detects payment notifications (e.g., bKash, Nagad, Rocket, Upay, Banks) and forwards the details to your configured webhook URL."
                )

                RationaleFeatureItem(
                    icon = Icons.Default.Sms,
                    title = "Transaction Parsing",
                    description = "Automatically extracts the Transaction ID (TrxID), sender, and amount to prevent double payments."
                )

                RationaleFeatureItem(
                    icon = Icons.Default.Lock,
                    title = "Privacy & Security Guarantee",
                    description = "Personal chat messages, contacts, and non-financial texts are NEVER uploaded, stored externally, or shared."
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    RationaleFeatureItem(
                        icon = Icons.Default.Notifications,
                        title = "Service Notification",
                        description = "Displays a silent notification to ensure Android does not kill the listener service while running."
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag("dialog_grant_permission_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0052FF)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Continue & Allow")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dialog_dismiss_permission_button")
            ) {
                Text("Not Now")
            }
        }
    )
}

@Composable
private fun RationaleFeatureItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(6.dp)
                    .size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}

/**
 * Settings Redirect Dialog when permission was permanently denied ("Don't ask again").
 */
@Composable
fun SmsPermissionSettingsDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("sms_permission_settings_dialog"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Permission Required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SMS permissions were previously declined. Android requires you to enable SMS access manually from App Settings for forwarding to work.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Steps to enable:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "1. Tap 'Open Settings' below\n2. Select 'Permissions'\n3. Set 'SMS' to 'Allow'",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("dialog_open_settings_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Open Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
