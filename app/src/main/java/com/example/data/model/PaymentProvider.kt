package com.example.data.model

import androidx.compose.ui.graphics.Color

enum class PaymentProvider(
    val displayName: String,
    val officialSenderCodes: List<String>,
    val brandColor: Color,
    val tidPrefix: String = ""
) {
    EASYPAISA(
        displayName = "EasyPaisa",
        officialSenderCodes = listOf("3737", "EASYPAISA", "TelenorBank"),
        brandColor = Color(0xFF00A859),
        tidPrefix = "EP"
    ),
    JAZZCASH(
        displayName = "JazzCash",
        officialSenderCodes = listOf("8558", "JAZZCASH", "MobilinkBank"),
        brandColor = Color(0xFFE2001A),
        tidPrefix = "JC"
    ),
    SADAPAY(
        displayName = "SadaPay",
        officialSenderCodes = listOf("SADAPAY", "SadaBiz"),
        brandColor = Color(0xFFFF5252),
        tidPrefix = "SP"
    ),
    NAYAPAY(
        displayName = "NayaPay",
        officialSenderCodes = listOf("NAYAPAY", "NayaPayBiz"),
        brandColor = Color(0xFFFF6D00),
        tidPrefix = "NP"
    ),
    RAAST(
        displayName = "Raast (SBP)",
        officialSenderCodes = listOf("RAAST", "1-LINK", "SBP"),
        brandColor = Color(0xFF1E88E5),
        tidPrefix = "RST"
    ),
    BANK_TRANSFER(
        displayName = "Bank Transfer",
        officialSenderCodes = listOf("MEEZAN", "8222", "HBL", "4250", "UBL", "8257", "ALFALAH", "2252", "MCB", "FAYSAL"),
        brandColor = Color(0xFF5E35B1),
        tidPrefix = "BNK"
    ),
    UNKNOWN(
        displayName = "Unknown Service",
        officialSenderCodes = emptyList(),
        brandColor = Color(0xFF757575),
        tidPrefix = ""
    )
}
