package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.FraudVerdict
import com.example.data.model.PaymentProvider
import com.example.data.model.VerificationResult

@Entity(tableName = "verification_logs")
data class VerificationLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val providerName: String,
    val extractedTid: String,
    val extractedAmount: Double,
    val senderOrReceiver: String,
    val dateTimeStr: String,
    val elaAnomalyScore: Float,
    val isTampered: Boolean,
    val tamperedAreasCount: Int,
    val smsMatchFound: Boolean,
    val matchedSmsSender: String?,
    val matchedSmsBody: String?,
    val matchedSmsAmount: Double?,
    val verdictName: String,
    val explanation: String,
    val timestamp: Long
) {
    fun toDomain(): VerificationResult {
        val provider = try {
            PaymentProvider.valueOf(providerName)
        } catch (_: Exception) {
            PaymentProvider.UNKNOWN
        }
        val verdict = try {
            FraudVerdict.valueOf(verdictName)
        } catch (_: Exception) {
            FraudVerdict.UNRESOLVED
        }
        return VerificationResult(
            id = id,
            provider = provider,
            extractedTid = extractedTid,
            extractedAmount = extractedAmount,
            senderOrReceiver = senderOrReceiver,
            dateTimeStr = dateTimeStr,
            elaAnomalyScore = elaAnomalyScore,
            isTampered = isTampered,
            tamperedAreasCount = tamperedAreasCount,
            smsMatchFound = smsMatchFound,
            matchedSmsSender = matchedSmsSender,
            matchedSmsBody = matchedSmsBody,
            matchedSmsAmount = matchedSmsAmount,
            matchedSmsTimestamp = null,
            verdict = verdict,
            explanation = explanation,
            timestamp = timestamp
        )
    }

    companion object {
        fun fromDomain(result: VerificationResult): VerificationLogEntity {
            return VerificationLogEntity(
                id = result.id,
                providerName = result.provider.name,
                extractedTid = result.extractedTid,
                extractedAmount = result.extractedAmount,
                senderOrReceiver = result.senderOrReceiver,
                dateTimeStr = result.dateTimeStr,
                elaAnomalyScore = result.elaAnomalyScore,
                isTampered = result.isTampered,
                tamperedAreasCount = result.tamperedAreasCount,
                smsMatchFound = result.smsMatchFound,
                matchedSmsSender = result.matchedSmsSender,
                matchedSmsBody = result.matchedSmsBody,
                matchedSmsAmount = result.matchedSmsAmount,
                verdictName = result.verdict.name,
                explanation = result.explanation,
                timestamp = result.timestamp
            )
        }
    }
}
