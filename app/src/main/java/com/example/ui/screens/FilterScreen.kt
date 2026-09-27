package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CustomFilterRule
import com.example.data.model.PredefinedSenders
import com.example.data.model.SenderFilter
import com.example.ui.MainViewModel
import com.example.ui.theme.ZeroGreenSuccess

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilterScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val allowedSenders by viewModel.allowedSenders.collectAsState()
    val isWhitelistEnabled by viewModel.isWhitelistEnabled.collectAsState()
    val customRules by viewModel.customFilterRules.collectAsState()

    var showAddRuleDialog by remember { mutableStateOf(false) }

    // Group predefined senders into ordered categories: Top MFS, Payment Gateways, Top Banks
    val categories = remember {
        listOf(
            PredefinedSenders.CATEGORY_MFS to PredefinedSenders.ALL.filter { it.category == PredefinedSenders.CATEGORY_MFS },
            PredefinedSenders.CATEGORY_GATEWAYS to PredefinedSenders.ALL.filter { it.category == PredefinedSenders.CATEGORY_GATEWAYS },
            PredefinedSenders.CATEGORY_BANKS to PredefinedSenders.ALL.filter { it.category == PredefinedSenders.CATEGORY_BANKS }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("filter_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Whitelist Master Toggle Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("whitelist_master_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isWhitelistEnabled) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                color = if (isWhitelistEnabled) ZeroGreenSuccess.copy(alpha = 0.15f) else Color(0xFF64748B).copy(alpha = 0.15f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = if (isWhitelistEnabled) ZeroGreenSuccess else Color(0xFF64748B),
                                    modifier = Modifier.padding(8.dp).fillMaxSize()
                                )
                            }
                            Column {
                                Text(
                                    text = "Sender Whitelist Filters",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isWhitelistEnabled) "ENABLED (${allowedSenders.size + customRules.count { it.enabled }} active)" else "DISABLED (forwarding all)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isWhitelistEnabled) ZeroGreenSuccess else Color(0xFF64748B)
                                )
                            }
                        }

                        Switch(
                            checked = isWhitelistEnabled,
                            onCheckedChange = { viewModel.setWhitelistEnabled(it) },
                            modifier = Modifier.testTag("whitelist_master_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ZeroGreenSuccess
                            )
                        )
                    }

                    Text(
                        text = "Toggle authorized MFS, Gateways, and Banks below. Only enabled senders will have their transaction SMS forwarded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Quick Batch Action Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        val allIds = PredefinedSenders.ALL.map { it.id }.toSet()
                        viewModel.setAllSenders(allIds)
                    },
                    modifier = Modifier.weight(1f).testTag("select_all_senders_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Select All", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        viewModel.setAllSenders(PredefinedSenders.DEFAULT_ENABLED_IDS)
                    },
                    modifier = Modifier.weight(1.3f).testTag("reset_default_senders_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("bKash & Nagad", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        viewModel.setAllSenders(emptySet())
                    },
                    modifier = Modifier.weight(1f).testTag("deselect_all_senders_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Deselect All", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Dynamic Custom Gateway Rules Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Custom Gateway Rules",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = { showAddRuleDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Custom Regex", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (customRules.isEmpty()) {
                        Text(
                            text = "No custom rules configured. You can add dynamic regex patterns for any new bank or MFS without app re-compilation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            customRules.forEach { rule ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 1.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = rule.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                ) {
                                                    Text(
                                                        text = "Custom",
                                                        fontSize = 9.sp,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "Sender: ${rule.senderPattern.ifBlank { "Any" }}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "Regex: ${rule.bodyRegex}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFF0284C7),
                                                maxLines = 1
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Switch(
                                                checked = rule.enabled,
                                                onCheckedChange = { enabled ->
                                                    viewModel.toggleCustomFilterRule(rule.id, enabled)
                                                },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color.White,
                                                    checkedTrackColor = ZeroGreenSuccess
                                                )
                                            )
                                            IconButton(
                                                onClick = { viewModel.deleteCustomFilterRule(rule.id) }
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Grouped Sender Categories with Sticky Headers
        categories.forEach { (categoryName, sendersInCategory) ->
            val activeInCategory = sendersInCategory.count { allowedSenders.contains(it.id) }

            // Sticky Category Header
            stickyHeader(key = categoryName) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = getCategoryIcon(categoryName),
                                contentDescription = null,
                                tint = getCategoryColor(categoryName),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = categoryName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = if (activeInCategory > 0) ZeroGreenSuccess.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text(
                                text = "$activeInCategory / ${sendersInCategory.size} active",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (activeInCategory > 0) ZeroGreenSuccess else Color(0xFF64748B),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // Sender Items in this category
            items(sendersInCategory, key = { it.id }) { sender ->
                val isChecked = allowedSenders.contains(sender.id)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sender_toggle_${sender.id}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = getCategoryColor(sender.category).copy(alpha = 0.12f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = getCategoryIcon(sender.category),
                                    contentDescription = null,
                                    tint = getCategoryColor(sender.category),
                                    modifier = Modifier.padding(9.dp).fillMaxSize()
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = sender.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (sender.defaultEnabled) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = ZeroGreenSuccess.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "Default",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp,
                                                color = ZeroGreenSuccess,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = sender.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Matches: ${sender.matchSenders.joinToString(", ")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Toggle Switch
                        Switch(
                            checked = isChecked,
                            onCheckedChange = { enabled ->
                                viewModel.toggleSender(sender.id, enabled)
                            },
                            modifier = Modifier.testTag("switch_${sender.id}"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ZeroGreenSuccess
                            )
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    if (showAddRuleDialog) {
        AddCustomRuleDialog(
            onDismiss = { showAddRuleDialog = false },
            onSave = { rule ->
                viewModel.addCustomFilterRule(rule)
                showAddRuleDialog = false
            }
        )
    }
}

@Composable
fun AddCustomRuleDialog(
    onDismiss: () -> Unit,
    onSave: (CustomFilterRule) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var senderPattern by remember { mutableStateOf("") }
    var bodyRegex by remember { mutableStateOf("(?i)credited.*?(?:Tk\\.?|BDT)\\s*([0-9,.]+).*?(?:TrxID|Ref)[:\\s]*([A-Za-z0-9]+)") }
    var amountGroupStr by remember { mutableStateOf("1") }
    var trxIdGroupStr by remember { mutableStateOf("2") }
    var senderGroupStr by remember { mutableStateOf("0") }
    var testSms by remember { mutableStateOf("A/C ...456 credited with Tk 1,500.00 from 01712345678. TrxID: FT998877") }

    var testResult by remember { mutableStateOf<String?>(null) }
    var isTestSuccess by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom Bank/MFS Filter Rule", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Gateway/Bank Name") },
                    placeholder = { Text("e.g. City Touch Bank") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = senderPattern,
                    onValueChange = { senderPattern = it },
                    label = { Text("Sender Senders / IDs (comma separated)") },
                    placeholder = { Text("e.g. CITYBANK, 16234, CityTouch") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = bodyRegex,
                    onValueChange = { bodyRegex = it },
                    label = { Text("Receipt Body Regex (with capture groups)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = amountGroupStr,
                        onValueChange = { amountGroupStr = it },
                        label = { Text("Amt Group") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = trxIdGroupStr,
                        onValueChange = { trxIdGroupStr = it },
                        label = { Text("Trx Group") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = senderGroupStr,
                        onValueChange = { senderGroupStr = it },
                        label = { Text("Phone Group") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = testSms,
                    onValueChange = {
                        testSms = it
                        testResult = null
                    },
                    label = { Text("Test Sample SMS") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            try {
                                val regex = Regex(bodyRegex, RegexOption.IGNORE_CASE)
                                val match = regex.find(testSms)
                                if (match != null) {
                                    val amtG = amountGroupStr.toIntOrNull() ?: 1
                                    val trxG = trxIdGroupStr.toIntOrNull() ?: 2
                                    val amt = match.groups[amtG]?.value
                                    val trx = match.groups[trxG]?.value
                                    testResult = "Matched! Amount=$amt, TrxID=$trx"
                                    isTestSuccess = true
                                } else {
                                    testResult = "Regex did not match test SMS"
                                    isTestSuccess = false
                                }
                            } catch (e: Exception) {
                                testResult = "Invalid Regex: ${e.message}"
                                isTestSuccess = false
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Test Pattern", fontSize = 11.sp)
                    }

                    if (testResult != null) {
                        Text(
                            text = testResult!!,
                            color = if (isTestSuccess) ZeroGreenSuccess else MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && bodyRegex.isNotBlank()) {
                        val rule = CustomFilterRule(
                            name = name.trim(),
                            senderPattern = senderPattern.trim(),
                            bodyRegex = bodyRegex.trim(),
                            amountGroup = amountGroupStr.toIntOrNull() ?: 1,
                            trxIdGroup = trxIdGroupStr.toIntOrNull() ?: 2,
                            senderGroup = senderGroupStr.toIntOrNull() ?: 0,
                            enabled = true
                        )
                        onSave(rule)
                    }
                },
                enabled = name.isNotBlank() && bodyRegex.isNotBlank()
            ) {
                Text("Save Rule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun getCategoryIcon(category: String): ImageVector {
    return when (category) {
        PredefinedSenders.CATEGORY_MFS -> Icons.Default.PhoneAndroid
        PredefinedSenders.CATEGORY_GATEWAYS -> Icons.Default.Payment
        else -> Icons.Default.AccountBalance
    }
}

private fun getCategoryColor(category: String): Color {
    return when (category) {
        PredefinedSenders.CATEGORY_MFS -> Color(0xFF0052FF)
        PredefinedSenders.CATEGORY_GATEWAYS -> Color(0xFF7C3AED)
        else -> Color(0xFF059669)
    }
}
