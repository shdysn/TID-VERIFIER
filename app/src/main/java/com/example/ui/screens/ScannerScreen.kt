package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FraudVerdict
import com.example.data.model.VerificationResult
import com.example.forensics.SampleScenario
import com.example.ui.ScannerState
import com.example.ui.VerifierViewModel
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.AlertRed
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SirenFlashingRed

@Composable
fun ScannerScreen(
    viewModel: VerifierViewModel,
    onNavigateToSms: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scannerState by viewModel.scannerState.collectAsState()
    val isSirenActive by viewModel.isSirenActive.collectAsState()
    val hasSmsPermission = viewModel.hasSmsPermission()

    // Photo Picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.processImageUri(it) }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            val uri = android.net.Uri.parse("bitmap://temp") // Handled or convert
            // We can add a direct bitmap processing helper
        }
    }

    // Permission request launcher
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.refreshSmsInbox()
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // SMS Permission Warning Banner if missing
        if (!hasSmsPermission) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF3B1D1D)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clickable { smsPermissionLauncher.launch(android.Manifest.permission.READ_SMS) }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "SMS Permission Needed",
                        tint = AlertAmber,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SMS Permission Required",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Tap to allow reading 3737/8558 merchant SMS to verify real payments.",
                            color = Color(0xFFD1D5DB),
                            fontSize = 12.sp
                        )
                    }
                    Button(
                        onClick = { smsPermissionLauncher.launch(android.Manifest.permission.READ_SMS) },
                        colors = ButtonDefaults.buttonColors(containerColor = AlertAmber)
                    ) {
                        Text("Allow", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Active Siren Mute Banner
        AnimatedVisibility(visible = isSirenActive) {
            SirenEmergencyBanner(
                onStopSiren = { viewModel.stopSiren() },
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        when (val state = scannerState) {
            is ScannerState.Idle -> {
                IdleScannerView(
                    onPickScreenshot = { photoPickerLauncher.launch("image/*") },
                    onSelectScenario = { scenario -> viewModel.processTestScenario(scenario) },
                    hasSmsPermission = hasSmsPermission,
                    onRequestSmsPermission = { smsPermissionLauncher.launch(android.Manifest.permission.READ_SMS) }
                )
            }

            is ScannerState.Scanning -> {
                ScanningProgressView(
                    step = state.step,
                    progress = state.progress
                )
            }

            is ScannerState.Finished -> {
                VerificationResultView(
                    result = state.result,
                    originalBitmap = state.originalBitmap,
                    heatmapBitmap = state.heatmapBitmap,
                    isSirenRinging = state.isSirenRinging,
                    onStopSiren = { viewModel.stopSiren() },
                    onScanAnother = { viewModel.resetScanner() },
                    onNavigateToSms = onNavigateToSms
                )
            }

            is ScannerState.Error -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2C1517)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            tint = AlertRed,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Verification Error",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = state.message,
                            color = Color(0xFFD1D5DB),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.resetScanner() },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                        ) {
                            Text("Try Again", color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SirenEmergencyBanner(
    onStopSiren: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "siren_pulse")
    val animatedBg by infiniteTransition.animateColor(
        initialValue = Color(0xFF7F1D1D),
        targetValue = SirenFlashingRed,
        animationSpec = infiniteRepeatable(
            animation = tween(400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "siren_color"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = animatedBg),
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, Color.White, RoundedCornerShape(12.dp))
            .testTag("siren_emergency_banner")
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Siren Blaring",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "EMERGENCY ALARM ACTIVE",
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    fontSize = 15.sp
                )
                Text(
                    text = "TID not found in your SMS inbox!",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
            Button(
                onClick = onStopSiren,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                modifier = Modifier.testTag("stop_siren_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeOff, contentDescription = null, tint = AlertRed)
                Spacer(modifier = Modifier.width(4.dp))
                Text("MUTE", color = AlertRed, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun IdleScannerView(
    onPickScreenshot: () -> Unit,
    onSelectScenario: (SampleScenario) -> Unit,
    hasSmsPermission: Boolean,
    onRequestSmsPermission: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero Card
        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .background(Color(0xFF0F291E), CircleShape)
                        .border(1.dp, EmeraldPrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Security Shield",
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Verify Payment Screenshot",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Detect fake EasyPaisa, JazzCash & Bank receipts in 2 seconds via pixel compression forensics and official SMS matching.",
                    fontSize = 13.sp,
                    color = Color(0xFF9CA3AF),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Primary Import Button
                Button(
                    onClick = onPickScreenshot,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("scan_screenshot_button")
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Import Payment Screenshot",
                        color = Color.Black,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Instant Counter Test Scenarios
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Instant 1-Tap Test Scenarios",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Demo Slips",
                fontSize = 12.sp,
                color = CyanAccent
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Test how the app catches forged slips vs authentic payments instantly:",
            fontSize = 12.sp,
            color = Color(0xFF9CA3AF),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SampleScenario.values().forEach { scenario ->
                ScenarioCard(scenario = scenario, onClick = { onSelectScenario(scenario) })
            }
        }
    }
}

@Composable
fun ScenarioCard(
    scenario: SampleScenario,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("scenario_card_${scenario.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        if (scenario.isFake) Color(0xFF3F1316) else Color(0xFF0F3123),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (scenario.isFake) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (scenario.isFake) AlertRed else EmeraldPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = scenario.title,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = if (scenario.isFake) AlertRed.copy(alpha = 0.2f) else EmeraldPrimary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (scenario.isFake) "FORGERY TEST" else "GENUINE",
                            color = if (scenario.isFake) AlertRed else EmeraldPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = scenario.description,
                    color = Color(0xFF9CA3AF),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Run Test",
                tint = CyanAccent,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun ScanningProgressView(
    step: String,
    progress: Float
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyanAccent.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = CyanAccent,
                strokeWidth = 4.dp,
                modifier = Modifier.size(60.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Forensic Verification Active",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = step,
                fontSize = 13.sp,
                color = CyanAccent,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            LinearProgressIndicator(
                progress = { progress },
                color = CyanAccent,
                trackColor = Color(0xFF1E293B),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
fun VerificationResultView(
    result: VerificationResult,
    originalBitmap: Bitmap,
    heatmapBitmap: Bitmap,
    isSirenRinging: Boolean,
    onStopSiren: () -> Unit,
    onScanAnother: () -> Unit,
    onNavigateToSms: () -> Unit
) {
    var selectedImageTab by remember { mutableIntStateOf(0) } // 0 = Original, 1 = Forensic ELA Heatmap

    val verdictColor = when (result.verdict) {
        FraudVerdict.VERIFIED_GENUINE -> EmeraldPrimary
        FraudVerdict.COUNTERFEIT_NO_SMS -> AlertRed
        FraudVerdict.AMOUNT_MISMATCH -> AlertAmber
        FraudVerdict.SUSPICIOUS_TAMPERED_IMAGE -> AlertAmber
        FraudVerdict.UNRESOLVED -> Color.Gray
    }

    val verdictIcon = when (result.verdict) {
        FraudVerdict.VERIFIED_GENUINE -> Icons.Default.CheckCircle
        FraudVerdict.COUNTERFEIT_NO_SMS -> Icons.Default.Warning
        FraudVerdict.AMOUNT_MISMATCH -> Icons.Default.Warning
        FraudVerdict.SUSPICIOUS_TAMPERED_IMAGE -> Icons.Default.Layers
        FraudVerdict.UNRESOLVED -> Icons.Default.Error
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // High-Impact Verdict Card
        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(
                containerColor = if (result.verdict == FraudVerdict.COUNTERFEIT_NO_SMS) Color(0xFF381014) else MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, verdictColor, RoundedCornerShape(18.dp))
                .testTag("verification_result_card")
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(verdictColor.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = verdictIcon,
                            contentDescription = null,
                            tint = verdictColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (result.verdict) {
                                FraudVerdict.VERIFIED_GENUINE -> "VERIFIED & AUTHENTIC"
                                FraudVerdict.COUNTERFEIT_NO_SMS -> "FAKE PAYMENT DETECTED!"
                                FraudVerdict.AMOUNT_MISMATCH -> "AMOUNT TAMPERED!"
                                FraudVerdict.SUSPICIOUS_TAMPERED_IMAGE -> "DIGITAL EDIT DETECTED"
                                FraudVerdict.UNRESOLVED -> "VERIFICATION INCOMPLETE"
                            },
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = verdictColor
                        )

                        Text(
                            text = "${result.provider.displayName} • Rs. %,.0f".format(result.extractedAmount),
                            fontSize = 14.sp,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = result.explanation,
                    fontSize = 13.sp,
                    color = Color(0xFFF3F4F6),
                    lineHeight = 18.sp
                )

                if (isSirenRinging) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onStopSiren,
                        colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("result_stop_siren_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeOff, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("STOP SIREN ALARM", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Image Viewer: Original vs Forensic ELA Heatmap
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                TabRow(
                    selectedTabIndex = selectedImageTab,
                    containerColor = Color(0xFF1E293B),
                    contentColor = CyanAccent,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                ) {
                    Tab(
                        selected = selectedImageTab == 0,
                        onClick = { selectedImageTab = 0 },
                        text = { Text("Original Slip") }
                    )
                    Tab(
                        selected = selectedImageTab == 1,
                        onClick = { selectedImageTab = 1 },
                        text = { Text("ELA Forensic Heatmap") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .background(Color.Black, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val displayBitmap = if (selectedImageTab == 0) originalBitmap else heatmapBitmap
                    Image(
                        bitmap = displayBitmap.asImageBitmap(),
                        contentDescription = "Payment Screenshot / ELA Heatmap",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (selectedImageTab == 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Forensic Guide: Dark areas represent natural uniform compression. Bright Neon Red / Yellow areas highlight spliced text or edited amount numbers.",
                        fontSize = 11.sp,
                        color = Color(0xFF9CA3AF),
                        lineHeight = 15.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Forensic Metrics Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Transaction Forensics",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 15.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                MetricRow(label = "Extracted TID", value = result.extractedTid, isMonospace = true)
                MetricRow(label = "Claimed Amount", value = "Rs. %,.0f".format(result.extractedAmount))
                MetricRow(label = "Wallet / Bank", value = result.provider.displayName)
                MetricRow(label = "ELA Anomaly Score", value = "${result.elaAnomalyScore}%")
                MetricRow(
                    label = "SMS Inbox Match",
                    value = if (result.smsMatchFound) "MATCHED (${result.matchedSmsSender})" else "NOT IN INBOX",
                    highlightColor = if (result.smsMatchFound) EmeraldPrimary else AlertRed
                )
            }
        }

        // Matched SMS snippet if available
        if (result.smsMatchFound && !result.matchedSmsBody.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F261F)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, EmeraldPrimary.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Sms, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Official Bank SMS (${result.matchedSmsSender})",
                            color = EmeraldPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = result.matchedSmsBody,
                        color = Color(0xFFD1D5DB),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action Buttons
        Button(
            onClick = onScanAnother,
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("scan_another_button")
        ) {
            Text("Scan Another Screenshot", color = Color.Black, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onNavigateToSms,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(Icons.Default.Sms, contentDescription = null, tint = CyanAccent)
            Spacer(modifier = Modifier.width(8.dp))
            Text("View Merchant SMS Inbox", color = CyanAccent)
        }
    }
}

@Composable
fun MetricRow(
    label: String,
    value: String,
    isMonospace: Boolean = false,
    highlightColor: Color? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color(0xFF9CA3AF), fontSize = 13.sp)
        Text(
            text = value,
            color = highlightColor ?: Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default
        )
    }
}
