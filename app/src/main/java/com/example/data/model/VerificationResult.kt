package com.example.data.model

enum class FraudVerdict {
    VERIFIED_GENUINE,
    COUNTERFEIT_NO_SMS,
    AMOUNT_MISMATCH,
    SUSPICIOUS_TAMPERED_IMAGE,
    UNRESOLVED
}

data class VerificationResult(
    val id: Long = 0L,
    val provider: PaymentProvider,
    val extractedTid: String,
    val extractedAmount: Double,
    val senderOrReceiver: String,
    val dateTimeStr: String,
    val elaAnomalyScore: Float, // 0.0 to 100.0%
    val isTampered: Boolean,
    val tamperedAreasCount: Int = 0,
    val smsMatchFound: Boolean,
    val matchedSmsSender: String? = null,
    val matchedSmsBody: String? = null,
    val matchedSmsAmount: Double? = null,
    val matchedSmsTimestamp: Long? = null,
    val verdict: FraudVerdict,
    val explanation: String,
    val timestamp: Long = System.currentTimeMillis()
)
