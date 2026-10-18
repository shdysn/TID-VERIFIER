package com.example.sms

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.example.data.model.PaymentProvider
import com.example.data.model.SmsRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

data class SmsMatchResult(
    val isMatched: Boolean,
    val matchedRecord: SmsRecord? = null,
    val isAmountMatching: Boolean = false,
    val amountDifference: Double = 0.0,
    val note: String = ""
)

class SmsInboxReader(private val context: Context) {

    // Common Pakistani Merchant Sender Codes
    private val merchantSenderPatterns = listOf(
        "3737", "8558", "SADAPAY", "NAYAPAY", "RAAST", "1LINK",
        "MEEZAN", "8222", "HBL", "4250", "UBL", "8257", "ALFALAH", "2252",
        "MCB", "FAYSAL", "ASKARI", "ALLIED", "BAHL", "STANDARD CHARTERED"
    )

    fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun readRecentMerchantSms(limit: Int = 100): List<SmsRecord> = withContext(Dispatchers.IO) {
        if (!hasSmsPermission()) {
            return@withContext emptyList()
        }

        val records = mutableListOf<SmsRecord>()
        val uri: Uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )

            cursor?.let {
                val idIdx = it.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addrIdx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndexOrThrow(Telephony.Sms.DATE)

                val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                var count = 0

                while (it.moveToNext() && count < limit) {
                    val id = it.getString(idIdx)
                    val rawAddress = it.getString(addrIdx) ?: ""
                    val body = it.getString(bodyIdx) ?: ""
                    val dateMillis = it.getLong(dateIdx)

                    if (isMerchantMessage(rawAddress, body)) {
                        val provider = identifyProvider(rawAddress, body)
                        val tid = extractTid(body)
                        val amount = extractAmount(body)
                        val isIncoming = isIncomingPaymentText(body)

                        records.add(
                            SmsRecord(
                                id = id,
                                address = rawAddress,
                                body = body,
                                date = dateMillis,
                                formattedDate = dateFormat.format(Date(dateMillis)),
                                detectedProvider = provider,
                                extractedTid = tid,
                                extractedAmount = amount,
                                isIncomingPayment = isIncoming
                            )
                        )
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }

        records
    }

    suspend fun crossMatch(
        provider: PaymentProvider,
        targetTid: String,
        targetAmount: Double
    ): SmsMatchResult = withContext(Dispatchers.IO) {
        if (!hasSmsPermission()) {
            return@withContext SmsMatchResult(
                isMatched = false,
                note = "SMS permission not granted. Cannot verify against merchant inbox."
            )
        }

        val cleanTargetTid = normalizeTid(targetTid)
        val targetDigitsOnly = cleanTargetTid.filter { it.isDigit() }

        if (cleanTargetTid.isBlank() && targetDigitsOnly.length < 6) {
            return@withContext SmsMatchResult(
                isMatched = false,
                note = "No valid Transaction ID could be extracted from screenshot."
            )
        }

        val recentSms = readRecentMerchantSms(150)

        // Search for TID in SMS records using multi-tiered normalization
        for (sms in recentSms) {
            val normalizedBody = normalizeTid(sms.body)
            val extractedSmsTid = sms.extractedTid?.let { normalizeTid(it) } ?: ""
            val smsDigitsOnly = extractedSmsTid.filter { it.isDigit() }
            val bodyDigitsOnly = normalizedBody.filter { it.isDigit() }

            // Match exact alphanumeric string
            val directMatch = (extractedSmsTid.isNotEmpty() && extractedSmsTid == cleanTargetTid) ||
                    normalizedBody.contains(cleanTargetTid)

            // Match numeric core (e.g. EP8391028471 matches 8391028471)
            val digitsMatch = targetDigitsOnly.length >= 7 && (
                    (smsDigitsOnly.isNotEmpty() && smsDigitsOnly == targetDigitsOnly) ||
                            bodyDigitsOnly.contains(targetDigitsOnly) ||
                            (smsDigitsOnly.isNotEmpty() && targetDigitsOnly.contains(smsDigitsOnly)) ||
                            (smsDigitsOnly.length >= 8 && targetDigitsOnly.takeLast(8) == smsDigitsOnly.takeLast(8))
                    )

            if (directMatch || digitsMatch) {
                val smsAmount = sms.extractedAmount ?: 0.0
                val amountDiff = Math.abs(smsAmount - targetAmount)
                val amountMatches = targetAmount <= 0.0 || smsAmount <= 0.0 || amountDiff < 2.0 ||
                        (targetAmount > 0.0 && amountDiff / targetAmount < 0.02)

                return@withContext SmsMatchResult(
                    isMatched = true,
                    matchedRecord = sms,
                    isAmountMatching = amountMatches,
                    amountDifference = amountDiff,
                    note = if (amountMatches) {
                        "Official SMS found from ${sms.address} with matching TID and Amount."
                    } else {
                        "TID matched, but amount in official SMS (Rs. $smsAmount) differs from screenshot (Rs. $targetAmount)!"
                    }
                )
            }
        }

        // If not found in inbox
        SmsMatchResult(
            isMatched = false,
            note = "No incoming SMS with TID '$targetTid' found in merchant inbox."
        )
    }

    fun isMerchantMessage(address: String, body: String): Boolean {
        val upperAddr = address.uppercase(Locale.ROOT)
        val upperBody = body.uppercase(Locale.ROOT)

        for (pattern in merchantSenderPatterns) {
            if (upperAddr.contains(pattern)) return true
        }

        val triggers = listOf("TRX ID", "TID", "TRANS ID", "RECEIVED RS", "SENT RS", "DEBITED", "CREDITED", "BALANCE")
        for (trigger in triggers) {
            if (upperBody.contains(trigger)) return true
        }

        return false
    }

    fun identifyProvider(address: String, body: String): PaymentProvider {
        val upperAddr = address.uppercase(Locale.ROOT)
        val upperBody = body.uppercase(Locale.ROOT)

        return when {
            upperAddr.contains("3737") || upperBody.contains("EASYPAISA") -> PaymentProvider.EASYPAISA
            upperAddr.contains("8558") || upperBody.contains("JAZZCASH") -> PaymentProvider.JAZZCASH
            upperAddr.contains("SADAPAY") || upperBody.contains("SADAPAY") -> PaymentProvider.SADAPAY
            upperAddr.contains("NAYAPAY") || upperBody.contains("NAYAPAY") -> PaymentProvider.NAYAPAY
            upperAddr.contains("RAAST") || upperBody.contains("RAAST") -> PaymentProvider.RAAST
            upperAddr.contains("MEEZAN") || upperAddr.contains("8222") ||
                    upperAddr.contains("HBL") || upperAddr.contains("4250") ||
                    upperAddr.contains("UBL") || upperAddr.contains("8257") ||
                    upperAddr.contains("ALFALAH") || upperAddr.contains("2252") -> PaymentProvider.BANK_TRANSFER
            else -> PaymentProvider.UNKNOWN
        }
    }

    fun extractTid(body: String): String? {
        val patterns = listOf(
            Pattern.compile("""(?:Trx\s*ID|TID|Trans(?:action)?\s*ID|Ref(?:erence)?\s*(?:No|ID)?)\s*[:#-]?\s*([A-Za-z0-9_-]{6,25})""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:ID|TRX)\s*[:#-]?\s*([0-9]{8,20})""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(202\d{7,15}|\d{10,14})\b""")
        )

        for (p in patterns) {
            val matcher = p.matcher(body)
            if (matcher.find()) {
                val candidate = matcher.group(1) ?: matcher.group(0)
                if (!candidate.isNullOrBlank() && candidate.length >= 6) {
                    return candidate.trim()
                }
            }
        }
        return null
    }

    fun extractAmount(body: String): Double? {
        val patterns = listOf(
            Pattern.compile("""(?:Rs\.?|PKR)\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)\s*(?:Rs\.?|PKR)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""received\s+([0-9]{1,3}(?:,[0-9]{3})*)""", Pattern.CASE_INSENSITIVE)
        )

        for (p in patterns) {
            val matcher = p.matcher(body)
            if (matcher.find()) {
                val numStr = matcher.group(1)?.replace(",", "")?.trim()
                try {
                    return numStr?.toDouble()
                } catch (_: Exception) {}
            }
        }
        return null
    }

    private fun isIncomingPaymentText(body: String): Boolean {
        val upper = body.uppercase(Locale.ROOT)
        return upper.contains("RECEIVED") || upper.contains("CREDITED") || upper.contains("DEPOSITED")
    }

    private fun normalizeTid(raw: String): String {
        return raw.replace(Regex("[^A-Za-z0-9]"), "").uppercase(Locale.ROOT)
    }
}
