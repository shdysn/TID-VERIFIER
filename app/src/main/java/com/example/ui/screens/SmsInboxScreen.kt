package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PaymentProvider
import com.example.data.model.SmsRecord
import com.example.ui.VerifierViewModel
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldPrimary

@Composable
fun SmsInboxScreen(
    viewModel: VerifierViewModel,
    modifier: Modifier = Modifier
) {
    val smsList by viewModel.recentSmsList.collectAsState()
    val hasPermission = viewModel.hasSmsPermission()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.refreshSmsInbox()
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedProviderFilter by remember { mutableStateOf<PaymentProvider?>(null) }
    var showSimulateDialog by remember { mutableStateOf(false) }

    val filteredList = smsList.filter { record ->
        val matchesSearch = searchQuery.isBlank() ||
                record.body.contains(searchQuery, ignoreCase = true) ||
                record.address.contains(searchQuery, ignoreCase = true) ||
                (record.extractedTid?.contains(searchQuery, ignoreCase = true) == true)

        val matchesProvider = selectedProviderFilter == null || record.detectedProvider == selectedProviderFilter

        matchesSearch && matchesProvider
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Merchant SMS Alerts",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Official 3737, 8558 & Bank receipts",
                        fontSize = 12.sp,
                        color = Color(0xFF9CA3AF)
                    )
                }

                IconButton(
                    onClick = { viewModel.refreshSmsInbox() },
                    modifier = Modifier.testTag("refresh_sms_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = CyanAccent)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Permission banner if not granted
            if (!hasPermission) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF351A1C)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AlertAmber)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SMS Permission Needed",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Grant permission to cross-match incoming transaction receipts.",
                                color = Color(0xFFD1D5DB),
                                fontSize = 11.sp
                            )
                        }
                        Button(
                            onClick = { permissionLauncher.launch(android.Manifest.permission.READ_SMS) },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                        ) {
                            Text("Grant", color = Color.Black, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by TID, amount, or sender...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sms_search_field")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedProviderFilter == null,
                        onClick = { selectedProviderFilter = null },
                        label = { Text("All (${smsList.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldPrimary,
                            selectedLabelColor = Color.Black
                        )
                    )
                }

                val providersWithCounts = listOf(
                    PaymentProvider.EASYPAISA to "EasyPaisa",
                    PaymentProvider.JAZZCASH to "JazzCash",
                    PaymentProvider.SADAPAY to "SadaPay",
                    PaymentProvider.NAYAPAY to "NayaPay",
                    PaymentProvider.BANK_TRANSFER to "Banks"
                )

                items(providersWithCounts) { (provider, label) ->
                    val count = smsList.count { it.detectedProvider == provider }
                    FilterChip(
                        selected = selectedProviderFilter == provider,
                        onClick = {
                            selectedProviderFilter = if (selectedProviderFilter == provider) null else provider
                        },
                        label = { Text("$label ($count)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldPrimary,
                            selectedLabelColor = Color.Black
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // SMS List
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Sms,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (hasPermission) "No Merchant SMS Found" else "Grant SMS permission to view alerts",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Incoming EasyPaisa (3737), JazzCash (8558) and Bank SMS alerts will appear here.",
                            color = Color(0xFF9CA3AF),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredList, key = { it.id }) { record ->
                        SmsRecordCard(record)
                    }
                }
            }
        }

        // Floating button to simulate SMS for testing
        FloatingActionButton(
            onClick = { showSimulateDialog = true },
            containerColor = EmeraldPrimary,
            contentColor = Color.Black,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("simulate_sms_fab")
        ) {
            Row(modifier = Modifier.padding(horizontal = 14.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Simulate SMS")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Test SMS", fontWeight = FontWeight.Bold)
            }
        }

        // Simulation Dialog
        if (showSimulateDialog) {
            SimulateSmsDialog(
                onDismiss = { showSimulateDialog = false },
                onInject = { address, tid, amount ->
                    viewModel.injectSimulatedSms(address, tid, amount)
                    showSimulateDialog = false
                }
            )
        }
    }
}

@Composable
fun SmsRecordCard(record: SmsRecord) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = record.detectedProvider.brandColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = record.address,
                            color = record.detectedProvider.brandColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = record.detectedProvider.displayName,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                Text(
                    text = record.formattedDate,
                    color = Color(0xFF9CA3AF),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = record.body,
                color = Color(0xFFE5E7EB),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontFamily = FontFamily.Monospace
            )

            if (!record.extractedTid.isNullOrBlank() || record.extractedAmount != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    record.extractedTid?.let { tid ->
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "TID: $tid",
                                color = CyanAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    record.extractedAmount?.let { amt ->
                        Surface(
                            color = Color(0xFF0F2D1F),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Rs. %,.0f".format(amt),
                                color = EmeraldPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SimulateSmsDialog(
    onDismiss: () -> Unit,
    onInject: (address: String, tid: String, amount: Double) -> Unit
) {
    var selectedSender by remember { mutableStateOf("3737") }
    var tidText by remember { mutableStateOf("EP8391028471") }
    var amountText by remember { mutableStateOf("2500") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Simulate Official Merchant SMS", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Add a test SMS into the verifier cache to test genuine payment matching on an emulator or without actual incoming SMS:",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )

                OutlinedTextField(
                    value = selectedSender,
                    onValueChange = { selectedSender = it },
                    label = { Text("Sender Code (e.g. 3737, 8558, SadaPay)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tidText,
                    onValueChange = { tidText = it },
                    label = { Text("Transaction ID (TID)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (PKR)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 1000.0
                    onInject(selectedSender, tidText, amt)
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text("Add Test SMS", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
