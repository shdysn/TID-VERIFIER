package com.example.data.model

data class SmsRecord(
    val id: String,
    val address: String,
    val body: String,
    val date: Long,
    val formattedDate: String,
    val detectedProvider: PaymentProvider,
    val extractedTid: String?,
    val extractedAmount: Double?,
    val isIncomingPayment: Boolean
)
