package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioAlertService
import com.example.ai.ScreenshotPaymentParser
import com.example.data.local.AppDatabase
import com.example.data.local.VerificationLogEntity
import com.example.data.model.FraudVerdict
import com.example.data.model.PaymentProvider
import com.example.data.model.SmsRecord
import com.example.data.model.VerificationResult
import com.example.forensics.ElaAnalysisResult
import com.example.forensics.ErrorLevelAnalyzer
import com.example.forensics.SampleScenario
import com.example.forensics.SampleSlipGenerator
import com.example.sms.SmsInboxReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

sealed interface ScannerState {
    data object Idle : ScannerState
    data class Scanning(val step: String, val progress: Float) : ScannerState
    data class Finished(
        val result: VerificationResult,
        val originalBitmap: Bitmap,
        val heatmapBitmap: Bitmap,
        val isSirenRinging: Boolean
    ) : ScannerState
    data class Error(val message: String) : ScannerState
}

class VerifierViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val dao = db.verificationDao()
    private val smsReader = SmsInboxReader(application)
    private val audioAlerts = AudioAlertService(application)
    private val parser = ScreenshotPaymentParser()

    private val _scannerState = MutableStateFlow<ScannerState>(ScannerState.Idle)
    val scannerState: StateFlow<ScannerState> = _scannerState.asStateFlow()

    private val _isSirenActive = MutableStateFlow(false)
    val isSirenActive: StateFlow<Boolean> = _isSirenActive.asStateFlow()

    private val _recentSmsList = MutableStateFlow<List<SmsRecord>>(emptyList())
    val recentSmsList: StateFlow<List<SmsRecord>> = _recentSmsList.asStateFlow()

    // Simulated virtual SMS storage for test scenarios
    private val virtualSmsCache = mutableListOf<SmsRecord>()

    val auditHistory: StateFlow<List<VerificationResult>> = dao.getAllLogs()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val fraudHistory: StateFlow<List<VerificationResult>> = dao.getFraudAlerts()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshSmsInbox()
    }

    fun hasSmsPermission(): Boolean = smsReader.hasSmsPermission()

    fun refreshSmsInbox() {
        viewModelScope.launch {
            val realSms = smsReader.readRecentMerchantSms(100)
            val combined = (virtualSmsCache + realSms).sortedByDescending { it.date }
            _recentSmsList.value = combined
        }
    }

    fun stopSiren() {
        audioAlerts.stopSiren()
        _isSirenActive.value = false
        val current = _scannerState.value
        if (current is ScannerState.Finished) {
            _scannerState.value = current.copy(isSirenRinging = false)
        }
    }

    fun resetScanner() {
        stopSiren()
        _scannerState.value = ScannerState.Idle
    }

    /**
     * Verifies an image loaded via URI (Photo Picker / Camera / Storage)
     */
    fun processImageUri(uri: Uri) {
        viewModelScope.launch {
            _scannerState.value = ScannerState.Scanning("Loading payment image...", 0.15f)
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    val input: InputStream? = getApplication<Application>().contentResolver.openInputStream(uri)
                    BitmapFactory.decodeStream(input)
                } catch (e: Exception) {
                    null
                }
            }

            if (bitmap == null) {
                _scannerState.value = ScannerState.Error("Could not load image. Please select a valid payment screenshot.")
                return@launch
            }

            executeVerificationPipeline(
                bitmap = bitmap,
                overrideTid = null,
                overrideAmount = null,
                overrideProvider = null
            )
        }
    }

    /**
     * Executes a sample test scenario
     */
    fun processTestScenario(scenario: SampleScenario) {
        viewModelScope.launch {
            _scannerState.value = ScannerState.Scanning("Generating ${scenario.title}...", 0.1f)
            val bitmap = withContext(Dispatchers.Default) {
                SampleSlipGenerator.generateSlipBitmap(scenario)
            }

            // If scenario requires virtual SMS injection, add to virtual cache
            if (scenario.injectSms) {
                val mockSms = SmsRecord(
                    id = "mock_${System.currentTimeMillis()}",
                    address = scenario.provider.officialSenderCodes.firstOrNull() ?: "3737",
                    body = "Received Rs. ${scenario.injectSmsAmount.toInt()} from 03001234567. Trx ID: ${scenario.expectedTid}. New Balance: Rs. 84,200.",
                    date = System.currentTimeMillis(),
                    formattedDate = "Just now",
                    detectedProvider = scenario.provider,
                    extractedTid = scenario.expectedTid,
                    extractedAmount = scenario.injectSmsAmount,
                    isIncomingPayment = true
                )
                virtualSmsCache.add(0, mockSms)
                refreshSmsInbox()
            }

            executeVerificationPipeline(
                bitmap = bitmap,
                overrideTid = scenario.expectedTid,
                overrideAmount = scenario.expectedAmount,
                overrideProvider = scenario.provider
            )
        }
    }

    /**
     * Injects a test merchant SMS for custom shopkeeper verification
     */
    fun injectSimulatedSms(address: String, tid: String, amount: Double) {
        val mock = SmsRecord(
            id = "sim_${System.currentTimeMillis()}",
            address = address,
            body = "Received Rs. ${amount.toInt()} from 03112345678. Trx ID: $tid. New balance: Rs. 50,000.",
            date = System.currentTimeMillis(),
            formattedDate = "Just now",
            detectedProvider = smsReader.identifyProvider(address, ""),
            extractedTid = tid,
            extractedAmount = amount,
            isIncomingPayment = true
        )
        virtualSmsCache.add(0, mock)
        refreshSmsInbox()
    }

    private suspend fun executeVerificationPipeline(
        bitmap: Bitmap,
        overrideTid: String?,
        overrideAmount: Double?,
        overrideProvider: PaymentProvider?
    ) {
        try {
            // STEP 1: Image Forensics (Error Level Analysis)
            _scannerState.value = ScannerState.Scanning("Performing Error Level Analysis (ELA)...", 0.35f)
            val elaResult: ElaAnalysisResult = ErrorLevelAnalyzer.analyze(bitmap)

            // STEP 2: Extract TID and Amount
            _scannerState.value = ScannerState.Scanning("Extracting Transaction ID & Amount...", 0.60f)
            val parsedSlip = parser.parse(bitmap)

            val finalTid = overrideTid ?: parsedSlip.tid.ifBlank { "TID-PENDING" }
            val finalAmount = overrideAmount ?: parsedSlip.amount
            val finalProvider = overrideProvider ?: parsedSlip.provider

            // STEP 3: Cross-Match against real SMS Inbox
            _scannerState.value = ScannerState.Scanning("Scanning merchant SMS inbox (3737/8558/banks)...", 0.85f)

            // Check real inbox first
            val realMatch = smsReader.crossMatch(finalProvider, finalTid, finalAmount)

            // Also check virtual cache fallback if real inbox yielded no match
            val finalMatch = if (!realMatch.isMatched) {
                val foundVirtual = virtualSmsCache.find {
                    it.extractedTid?.equals(finalTid, ignoreCase = true) == true ||
                            it.body.contains(finalTid, ignoreCase = true)
                }
                if (foundVirtual != null) {
                    val amtDiff = Math.abs((foundVirtual.extractedAmount ?: 0.0) - finalAmount)
                    val isAmtMatch = amtDiff < 1.0
                    com.example.sms.SmsMatchResult(
                        isMatched = true,
                        matchedRecord = foundVirtual,
                        isAmountMatching = isAmtMatch,
                        amountDifference = amtDiff,
                        note = if (isAmtMatch) "Matched verified SMS record." else "Amount mismatch in SMS record!"
                    )
                } else {
                    realMatch
                }
            } else {
                realMatch
            }

            // STEP 4: Synthesize Fraud Verdict
            val verdict = when {
                !finalMatch.isMatched -> FraudVerdict.COUNTERFEIT_NO_SMS
                !finalMatch.isAmountMatching -> FraudVerdict.AMOUNT_MISMATCH
                elaResult.isTampered -> FraudVerdict.SUSPICIOUS_TAMPERED_IMAGE
                else -> FraudVerdict.VERIFIED_GENUINE
            }

            val explanation = when (verdict) {
                FraudVerdict.VERIFIED_GENUINE ->
                    "100% Genuine Payment. TID '$finalTid' matched official SMS from ${finalMatch.matchedRecord?.address}. Pixel compression layers are authentic."

                FraudVerdict.COUNTERFEIT_NO_SMS ->
                    "🚨 RED SIREN FRAUD ALERT: Screenshot shows Rs. %,.0f from ${finalProvider.displayName}, but TID '$finalTid' DOES NOT EXIST in your phone's SMS inbox (3737/8558/bank)! DO NOT HAND OVER GOODS."
                        .format(finalAmount)

                FraudVerdict.AMOUNT_MISMATCH ->
                    "⚠️ AMOUNT TAMPERED: Official SMS confirms only Rs. %,.0f, but customer screenshot claims Rs. %,.0f! Difference: Rs. %,.0f."
                        .format(finalMatch.matchedRecord?.extractedAmount ?: 0.0, finalAmount, finalMatch.amountDifference)

                FraudVerdict.SUSPICIOUS_TAMPERED_IMAGE ->
                    "⚠️ IMAGE TAMPERING DETECTED: ELA forensic scan found ${elaResult.suspiciousClusterCount} suspicious pixel clusters (Anomaly score: ${elaResult.anomalyScore}%). Text was digitally overlaid."

                FraudVerdict.UNRESOLVED ->
                    "Could not conclusively verify screenshot. Please check SMS inbox manually."
            }

            val result = VerificationResult(
                provider = finalProvider,
                extractedTid = finalTid,
                extractedAmount = finalAmount,
                senderOrReceiver = parsedSlip.recipientOrSender,
                dateTimeStr = parsedSlip.dateStr,
                elaAnomalyScore = elaResult.anomalyScore,
                isTampered = elaResult.isTampered,
                tamperedAreasCount = elaResult.suspiciousClusterCount,
                smsMatchFound = finalMatch.isMatched,
                matchedSmsSender = finalMatch.matchedRecord?.address,
                matchedSmsBody = finalMatch.matchedRecord?.body,
                matchedSmsAmount = finalMatch.matchedRecord?.extractedAmount,
                matchedSmsTimestamp = finalMatch.matchedRecord?.date,
                verdict = verdict,
                explanation = explanation,
                timestamp = System.currentTimeMillis()
            )

            // STEP 5: Trigger Multi-sensory Audio Alerts
            val willRingSiren = verdict != FraudVerdict.VERIFIED_GENUINE
            if (willRingSiren) {
                _isSirenActive.value = true
                val speech = when (verdict) {
                    FraudVerdict.COUNTERFEIT_NO_SMS -> "Warning! Fake payment detected! Transaction ID $finalTid was not found in your SMS inbox!"
                    FraudVerdict.AMOUNT_MISMATCH -> "Warning! Payment amount does not match your official SMS!"
                    FraudVerdict.SUSPICIOUS_TAMPERED_IMAGE -> "Warning! Altered screenshot detected! Pixel compression is modified!"
                    else -> "Warning! Unverified transaction!"
                }
                audioAlerts.triggerFraudSiren(speech)
            } else {
                _isSirenActive.value = false
                audioAlerts.triggerVerifiedChime(finalAmount, finalProvider.displayName)
            }

            // Save to Room DB
            withContext(Dispatchers.IO) {
                dao.insertLog(VerificationLogEntity.fromDomain(result))
            }

            _scannerState.value = ScannerState.Finished(
                result = result,
                originalBitmap = bitmap,
                heatmapBitmap = elaResult.heatmapBitmap,
                isSirenRinging = willRingSiren
            )

        } catch (e: Exception) {
            e.printStackTrace()
            _scannerState.value = ScannerState.Error("Verification failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    fun clearAuditHistory() {
        viewModelScope.launch {
            dao.clearAll()
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioAlerts.release()
    }
}
