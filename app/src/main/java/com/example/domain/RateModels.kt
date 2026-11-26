package com.example.domain

enum class PreferredWhatsAppApp(val packageName: String, val displayName: String) {
    WHATSAPP_BUSINESS("com.whatsapp.w4b", "WhatsApp Business"),
    WHATSAPP_STANDARD("com.whatsapp", "WhatsApp"),
}

data class BinanceOffer(
    val advertiserName: String,
    val price: Double,
    val availableUsdt: Double,
    val minVes: Double,
    val maxVes: Double,
    val finishRatePercent: Double,
    val monthOrders: Int,
    val isPromoted: Boolean = false,
)

data class BcvFetchResult(
    val rateRaw: Double,
    val valueDateLabel: String,
    val sourceName: String,
    val isFallbackSource: Boolean,
)

data class BinanceFetchResult(
    val offers: List<BinanceOffer>,
    val sourceName: String,
)

data class DifferentialResult(
    val bcvUsed: Double,
    val binanceUsed: Double,
    val ratio: Double,
    val percent: Int,
    val integerPart: String,
    val highlightedTwoDigits: String,
    val remainingDecimals: String,
)

data class DispatchSettings(
    val targetGroupName: String = "Grupo Diferencial Bisu",
    val preferredApp: PreferredWhatsAppApp = PreferredWhatsAppApp.WHATSAPP_BUSINESS,
    val scheduleEnabled: Boolean = true,
    val autoSendEnabled: Boolean = true,
    val singleMessageMode: Boolean = true,
    val exactSendHour: Int = 9,
    val exactSendMinute: Int = 0,
    val windowStartHour: Int = 8,
    val windowStartMinute: Int = 55,
    val windowEndHour: Int = 9,
    val windowEndMinute: Int = 0,
    val displayTimeLabel: String = "9:00 am",
    val customTemplateUri: String = "",
    val darkThemeEnabled: Boolean = true,
    val githubRepoSlug: String = "",
    val githubToken: String = "",
    val autoCheckUpdates: Boolean = true,
    val acknowledgedReleaseTag: String = "",
)

data class AutomationReadiness(
    val accessibilityEnabled: Boolean = false,
    val exactAlarmGranted: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val whatsAppInstalled: Boolean = false,
    val groupConfigured: Boolean = false,
) {
    val isReadyForUnattendedSend: Boolean
        get() = accessibilityEnabled && whatsAppInstalled && groupConfigured

    val completedStepsCount: Int
        get() = listOf(
            accessibilityEnabled,
            exactAlarmGranted,
            batteryUnrestricted,
            groupConfigured,
        ).count { it }
}

data class CachedQuoteData(
    val bcvRate: Double = 0.0,
    val bcvSource: String = "",
    val bcvDateLabel: String = "",
    val binanceRate: Double = 0.0,
    val binanceMerchant: String = "",
    val quoteDateFormatted: String = "",
    val fetchedAtMillis: Long = 0L,
    val imageSentDateKey: String = "",
    val diffSentDateKey: String = "",
)
