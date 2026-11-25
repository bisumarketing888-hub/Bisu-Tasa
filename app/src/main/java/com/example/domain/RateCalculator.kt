package com.example.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor

object RateCalculator {

    private val venezuelanSymbols = DecimalFormatSymbols(Locale("es", "VE")).apply {
        decimalSeparator = ','
        groupingSeparator = '.'
    }

    /**
     * Rounds a raw BCV rate (e.g. 922.56370000) to 2 decimal places (922.56).
     */
    fun roundToTwoDecimals(value: Double): Double {
        if (!value.isFinite() || value <= 0.0) return 0.0
        return BigDecimal.valueOf(value)
            .setScale(2, RoundingMode.HALF_UP)
            .toDouble()
    }

    /**
     * Exact differential calculation requested:
     * ratio = binancePrice / bcvRateTwoDecimals
     * e.g. 1104.11 / 922.56 = 1.196789... -> take the 2 digits after the comma -> 19% (floor, not rounded).
     * When ratio >= 2.0 (differential >= 100%), floor((ratio - 1) * 100) stays mathematically accurate.
     */
    fun calculateDifferential(binancePrice: Double, bcvRawRate: Double): DifferentialResult {
        val bcvTwoDec = roundToTwoDecimals(bcvRawRate)
        if (bcvTwoDec <= 0.0 || binancePrice <= 0.0) {
            return DifferentialResult(
                bcvUsed = bcvTwoDec,
                binanceUsed = binancePrice,
                ratio = 0.0,
                percent = 0,
                integerPart = "0",
                highlightedTwoDigits = "00",
                remainingDecimals = "00",
            )
        }

        val ratio = binancePrice / bcvTwoDec
        val percent = if (ratio >= 1.0) {
            floor((ratio - 1.0) * 100.0 + 1e-9).toInt()
        } else {
            0
        }

        val ratioFormatted = String.format(Locale.US, "%.6f", ratio)
        val parts = ratioFormatted.split('.')
        val integerPart = parts.getOrElse(0) { "1" }
        val fractional = parts.getOrElse(1) { "000000" }.padEnd(6, '0')
        val twoDigits = fractional.substring(0, 2)
        val remaining = fractional.substring(2, 6)

        return DifferentialResult(
            bcvUsed = bcvTwoDec,
            binanceUsed = binancePrice,
            ratio = ratio,
            percent = percent,
            integerPart = integerPart,
            highlightedTwoDigits = twoDigits,
            remainingDecimals = remaining,
        )
    }

    /**
     * Formats rate for the BCV image card: e.g., "Bs.922,56" (no space after Bs. matching template)
     */
    fun formatCardBcvText(bcvRate: Double): String {
        val rounded = roundToTwoDecimals(bcvRate)
        val pattern = if (rounded >= 1000.0) "#,##0.00" else "0.00"
        val formatter = DecimalFormat(pattern, venezuelanSymbols)
        return "Bs.${formatter.format(rounded)}"
    }

    /**
     * Formats rate for captions and displays: e.g., "922,56" or "1.104,11"
     */
    fun formatVenezuelanNumber(value: Double, decimals: Int = 2): String {
        if (!value.isFinite()) return "0,00"
        val decimalZeros = if (decimals > 0) "." + "0".repeat(decimals) else ""
        val formatter = DecimalFormat("#,##0$decimalZeros", venezuelanSymbols)
        return formatter.format(value)
    }

    /**
     * Formats Venezuelan date matching template: "25/11/2026"
     */
    fun formatTodayDate(timestampMillis: Long = System.currentTimeMillis()): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("es", "VE"))
        return sdf.format(Date(timestampMillis))
    }

    fun formatDateKey(timestampMillis: Long = System.currentTimeMillis()): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date(timestampMillis))
    }

    fun formatHourMinute(timestampMillis: Long): String {
        if (timestampMillis <= 0L) return "--:--"
        val sdf = SimpleDateFormat("h:mm a", Locale("es", "VE"))
        return sdf.format(Date(timestampMillis))
    }

    /**
     * Message 1 caption sent attached to the generated BCV card image.
     * Matches screenshot structure exactly:
     * 25/11/2026
     * 🕘 9:00 am
     * *BCV Bs. 922,56*
     */
    fun buildImageCaption(
        dateFormatted: String,
        timeLabel: String,
        bcvRate: Double,
    ): String {
        val formattedRate = formatVenezuelanNumber(roundToTwoDecimals(bcvRate), decimals = 2)
        return buildString {
            append(dateFormatted.trim())
            append('\n')
            append("🕘 ")
            append(timeLabel.trim())
            append('\n')
            append("*BCV Bs. ")
            append(formattedRate)
            append('*')
        }
    }

    /**
     * Message 2 text sent right after Message 1 (when using 2-message mode).
     * Matches screenshot structure exactly:
     * ⚠️ *DIFERENCIAL 19%* ⚠️
     */
    fun buildDifferentialMessage(differentialPercent: Int): String {
        return "⚠️ *DIFERENCIAL ${differentialPercent}%* ⚠️"
    }

    /**
     * Single combined message caption (image + BCV info + 1 blank line + Differential).
     * Recommended for 100% reliable unattended WhatsApp Business sending:
     * 25/11/2026
     * 🕘 9:00 am
     * *BCV Bs. 922,56*
     *
     * ⚠️ *DIFERENCIAL 19%* ⚠️
     */
    fun buildCombinedMessageCaption(
        dateFormatted: String,
        timeLabel: String,
        bcvRate: Double,
        differentialPercent: Int,
    ): String {
        val mainBlock = buildImageCaption(dateFormatted, timeLabel, bcvRate)
        val diffBlock = buildDifferentialMessage(differentialPercent)
        return "$mainBlock\n\n$diffBlock"
    }

    /**
     * Parses a user-entered Venezuelan or US numeric string (e.g. "922,56", "1.104,11", "922.56").
     */
    fun parseFlexibleNumber(raw: String): Double? {
        val cleaned = raw.trim()
            .replace("Bs.", "", ignoreCase = true)
            .replace("Bs", "", ignoreCase = true)
            .replace(" ", "")
        if (cleaned.isEmpty()) return null

        val normalized = when {
            cleaned.contains(',') && cleaned.contains('.') -> {
                if (cleaned.lastIndexOf(',') > cleaned.lastIndexOf('.')) {
                    cleaned.replace(".", "").replace(',', '.')
                } else {
                    cleaned.replace(",", "")
                }
            }
            cleaned.contains(',') -> cleaned.replace(',', '.')
            else -> cleaned
        }
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
    }
}
