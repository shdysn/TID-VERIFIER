package com.example.ai

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import com.example.data.model.PaymentProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ParsedPaymentScreenshot(
    val provider: PaymentProvider,
    val tid: String,
    val amount: Double,
    val recipientOrSender: String,
    val dateStr: String,
    val detectedFraudFlags: List<String>,
    val confidence: String,
    val analysisEngine: String,
    val rawExtractedText: String = ""
)

class ScreenshotPaymentParser {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun parse(bitmap: Bitmap): ParsedPaymentScreenshot = withContext(Dispatchers.IO) {
        // STEP 1: On-Device Google ML Kit OCR (fast, reliable, offline, works for all screenshots)
        val ocrResult = runMlKitOcr(bitmap)
        if (ocrResult.tid.isNotBlank() && ocrResult.amount > 0.0 && ocrResult.provider != PaymentProvider.UNKNOWN) {
            return@withContext ocrResult
        }

        // STEP 2: If OCR missed some fields and Gemini API key is configured, query Gemini Vision
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (_: Exception) { "" }
        if (!apiKey.isNullOrBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val aiResult = callGeminiVision(bitmap, apiKey)
                if (aiResult != null && aiResult.tid.isNotBlank()) {
                    return@withContext aiResult
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Return best OCR extraction result (even if partial, with fallback color detection)
        val fallbackProvider = if (ocrResult.provider != PaymentProvider.UNKNOWN) {
            ocrResult.provider
        } else {
            detectProviderFromColors(bitmap)
        }

        ocrResult.copy(provider = fallbackProvider)
    }

    private suspend fun runMlKitOcr(bitmap: Bitmap): ParsedPaymentScreenshot = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val visionText: Text = suspendCancellableCoroutine { cont ->
            textRecognizer.process(image)
                .addOnSuccessListener { text -> cont.resume(text) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }

        val rawText = visionText.text
        val detectedProvider = detectProviderFromText(rawText)
        val tid = extractTidFromText(rawText)
        val amount = extractAmountFromText(rawText)
        val recipient = extractRecipientFromText(rawText)

        ParsedPaymentScreenshot(
            provider = detectedProvider,
            tid = tid,
            amount = amount,
            recipientOrSender = recipient,
            dateStr = "Today",
            detectedFraudFlags = emptyList(),
            confidence = if (tid.isNotBlank() && amount > 0) "HIGH" else "MEDIUM",
            analysisEngine = "Google ML Kit OCR",
            rawExtractedText = rawText
        )
    }

    private fun detectProviderFromText(text: String): PaymentProvider {
        val upper = text.uppercase(Locale.ROOT)
        return when {
            upper.contains("EASYPAISA") || upper.contains("TELENOR") || upper.contains("3737") -> PaymentProvider.EASYPAISA
            upper.contains("JAZZCASH") || upper.contains("MOBILINK") || upper.contains("8558") -> PaymentProvider.JAZZCASH
            upper.contains("SADAPAY") || upper.contains("SADABIZ") -> PaymentProvider.SADAPAY
            upper.contains("NAYAPAY") -> PaymentProvider.NAYAPAY
            upper.contains("RAAST") -> PaymentProvider.RAAST
            upper.contains("MEEZAN") || upper.contains("HBL") || upper.contains("UBL") ||
                    upper.contains("ALFALAH") || upper.contains("MCB") || upper.contains("BANK") -> PaymentProvider.BANK_TRANSFER
            else -> PaymentProvider.UNKNOWN
        }
    }

    private fun extractTidFromText(text: String): String {
        val patterns = listOf(
            // EasyPaisa / JazzCash / SadaPay format (e.g. Trx ID: 2948192841, TID: 1029384756, Ref: SP123481)
            Pattern.compile("""(?:Trx\s*ID|TID|Transaction\s*ID|Trans\s*ID|Ref(?:erence)?\s*(?:No|ID)?)\s*[:#-]?\s*([A-Za-z0-9_-]{7,25})""", Pattern.CASE_INSENSITIVE),
            // Standalone prefix like EP1029384756 or JC1029384756 or SP1029384756
            Pattern.compile("""\b(EP[0-9]{8,14}|JC[0-9]{8,14}|SP[0-9]{8,14}|NP[0-9]{8,14})\b""", Pattern.CASE_INSENSITIVE),
            // Standalone 10 to 14 digit transaction reference
            Pattern.compile("""\b(202\d{8,12}|\d{10,13})\b""")
        )

        for (p in patterns) {
            val matcher = p.matcher(text)
            if (matcher.find()) {
                val candidate = matcher.group(1) ?: matcher.group(0)
                if (!candidate.isNullOrBlank() && candidate.length >= 7) {
                    return candidate.trim().replace(Regex("[^A-Za-z0-9]"), "")
                }
            }
        }
        return ""
    }

    private fun extractAmountFromText(text: String): Double {
        val patterns = listOf(
            Pattern.compile("""(?:Rs\.?|PKR)\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)\s*(?:Rs\.?|PKR)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:Amount|Paid|Sent|Received)\s*[:#-]?\s*Rs\.?\s*([0-9]{1,3}(?:,[0-9]{3})*)""", Pattern.CASE_INSENSITIVE)
        )

        for (p in patterns) {
            val matcher = p.matcher(text)
            if (matcher.find()) {
                val rawVal = matcher.group(1)?.replace(",", "")?.trim()
                val parsed = rawVal?.toDoubleOrNull()
                if (parsed != null && parsed > 0) {
                    return parsed
                }
            }
        }
        return 0.0
    }

    private fun extractRecipientFromText(text: String): String {
        val pattern = Pattern.compile("""(?:To|Sent To|Receiver|Recipient)\s*[:#-]?\s*([A-Za-z0-9\s-]{3,30})""", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            return matcher.group(1)?.lines()?.firstOrNull()?.trim() ?: "Recipient"
        }
        return "Merchant Shop"
    }

    private fun callGeminiVision(bitmap: Bitmap, apiKey: String): ParsedPaymentScreenshot? {
        val baos = ByteArrayOutputStream()
        val maxDim = 800
        val scale = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            maxDim.toFloat() / Math.max(bitmap.width, bitmap.height)
        } else 1.0f

        val scaled = if (scale < 1.0f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else bitmap

        scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
        val base64Image = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

        val prompt = """
            Analyze this Pakistani payment screenshot (EasyPaisa, JazzCash, SadaPay, NayaPay, Raast, or Bank transfer).
            Return ONLY a JSON object with this exact structure:
            {
              "provider": "EASYPAISA" | "JAZZCASH" | "SADAPAY" | "NAYAPAY" | "RAAST" | "BANK_TRANSFER" | "UNKNOWN",
              "tid": "extracted transaction id string without spaces",
              "amount": 0.0,
              "recipient": "recipient shop or person name",
              "date": "transaction date and time",
              "fraudFlags": [],
              "confidence": "HIGH" | "MEDIUM" | "LOW"
            }
        """.trimIndent()

        val jsonPayload = JSONObject().apply {
            val contents = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                        put(JSONObject().apply {
                            put("inline_data", JSONObject().apply {
                                put("mime_type", "image/jpeg")
                                put("data", base64Image)
                            })
                        })
                    }
                    put("parts", parts)
                }
                put(contentObj)
            }
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("response_mime_type", "application/json")
                put("temperature", 0.1)
            })
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
        val body = jsonPayload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: return null

        val root = JSONObject(responseBody)
        val candidates = root.optJSONArray("candidates") ?: return null
        if (candidates.length() == 0) return null

        val text = candidates.getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")

        val data = JSONObject(text)
        val provStr = data.optString("provider", "UNKNOWN")
        val provider = try { PaymentProvider.valueOf(provStr) } catch (_: Exception) { PaymentProvider.UNKNOWN }
        val tid = data.optString("tid", "")
        val amount = data.optDouble("amount", 0.0)
        val recipient = data.optString("recipient", "Unknown")
        val dateStr = data.optString("date", "Recent")

        return ParsedPaymentScreenshot(
            provider = provider,
            tid = tid,
            amount = amount,
            recipientOrSender = recipient,
            dateStr = dateStr,
            detectedFraudFlags = emptyList(),
            confidence = "HIGH",
            analysisEngine = "Gemini Vision"
        )
    }

    private fun detectProviderFromColors(bitmap: Bitmap): PaymentProvider {
        var greenCount = 0
        var redCount = 0
        var orangeCount = 0
        var blueCount = 0

        val sampleStep = 10
        val sampleHeight = (bitmap.height * 0.35).toInt().coerceAtLeast(10)

        for (y in 0 until sampleHeight step sampleStep) {
            for (x in 0 until bitmap.width step sampleStep) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                if (g > 130 && g > r * 1.3 && g > b * 1.3) greenCount++
                if (r > 150 && r > g * 1.5 && r > b * 1.5) redCount++
                if (r > 180 && g in 70..140 && b < 50) orangeCount++
                if (b > 140 && b > r * 1.2 && g > 100) blueCount++
            }
        }

        return when {
            greenCount > redCount && greenCount > orangeCount && greenCount > blueCount -> PaymentProvider.EASYPAISA
            redCount > greenCount && redCount > orangeCount -> PaymentProvider.JAZZCASH
            orangeCount > greenCount && orangeCount > redCount -> PaymentProvider.NAYAPAY
            blueCount > greenCount && blueCount > redCount -> PaymentProvider.RAAST
            else -> PaymentProvider.UNKNOWN
        }
    }
}
