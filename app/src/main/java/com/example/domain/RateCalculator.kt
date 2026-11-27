package com.example.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
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

    private val SPANISH_MONTHS = mapOf(
        "enero" to 1,
        "febrero" to 2,
        "marzo" to 3,
        "abril" to 4,
        "mayo" to 5,
        "junio" to 6,
        "julio" to 7,
        "agosto" to 8,
        "septiembre" to 9,
        "setiembre" to 9,
        "octubre" to 10,
        "noviembre" to 11,
        "diciembre" to 12,
    )

    /**
     * Parses a BCV "Fecha Valor" string into a lexicographically comparable "yyyy-MM-dd" key.
     * Supports:
     * - "Viernes, 27 Noviembre 2026"
     * - "Martes, 01 Diciembre 2026"
     * - "2026-11-27" or "2026-11-27T00:00:00-04:00"
     * - "27/11/2026"
     */
    fun parseBcvValueDateKey(rawLabel: String): String? {
        val trimmed = rawLabel.trim()
        if (trimmed.isEmpty()) return null

        // 1. ISO yyyy-MM-dd
        Regex("""(\d{4})-(\d{2})-(\d{2})""").find(trimmed)?.let { m ->
            val y = m.groupValues[1].toIntOrNull() ?: return@let
            val mo = m.groupValues[2].toIntOrNull() ?: return@let
            val d = m.groupValues[3].toIntOrNull() ?: return@let
            if (mo in 1..12 && d in 1..31) {
                return String.format(Locale.US, "%04d-%02d-%02d", y, mo, d)
            }
        }

        // 2. dd/MM/yyyy
        Regex("""(\d{1,2})/(\d{1,2})/(\d{4})""").find(trimmed)?.let { m ->
            val d = m.groupValues[1].toIntOrNull() ?: return@let
            val mo = m.groupValues[2].toIntOrNull() ?: return@let
            val y = m.groupValues[3].toIntOrNull() ?: return@let
            if (mo in 1..12 && d in 1..31) {
                return String.format(Locale.US, "%04d-%02d-%02d", y, mo, d)
            }
        }

        // 3. Spanish long format: "Viernes, 27 Noviembre 2026" or "27 de noviembre de 2026"
        val normalized = Normalizer.normalize(trimmed.lowercase(Locale("es", "VE")), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        val spanishRegex = Regex("""(\d{1,2})\s+(?:de\s+)?([a-z]+)\s+(?:de\s+)?(\d{4})""")
        spanishRegex.find(normalized)?.let { m ->
            val d = m.groupValues[1].toIntOrNull() ?: return@let
            val monthName = m.groupValues[2]
            val y = m.groupValues[3].toIntOrNull() ?: return@let
            val mo = SPANISH_MONTHS[monthName] ?: return@let
            if (d in 1..31) {
                return String.format(Locale.US, "%04d-%02d-%02d", y, mo, d)
            }
        }

        return null
    }

    /**
     * Resolves which BCV rate applies TODAY following Venezuelan banking rules:
     * - Mon–Fri (normal banking day): adopts the BCV rate whose Fecha Valor is <= today.
     * - Saturday (Sábado): BCV does not update on Saturdays (and already publishes Monday/Tuesday
     *   future Fecha Valor on Friday afternoon). Keeps Friday's last active BCV rate while today's
     *   date and live Binance differential still update.
     * - Banking Monday / holiday (Lunes bancario or any day where BCV's published Fecha Valor is
     *   strictly AFTER today, e.g., BCV shows Tuesday while today is Monday): keeps Friday's last
     *   active BCV rate until Tuesday arrives (when Fecha Valor <= today).
     */
    fun resolveEffectiveBcvRate(
        fetched: BcvFetchResult,
        todayKey: String,
        dayOfWeek: Int,
        previousActiveRate: Double,
        previousActiveDateKey: String = "",
        previousActiveDateLabel: String = "",
    ): ResolvedBcvRate {
        val fetchedDateKey = fetched.valueDateKey?.takeIf { it.isNotBlank() }
            ?: parseBcvValueDateKey(fetched.valueDateLabel)
            ?: ""

        val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
        val isFutureValueDate = fetchedDateKey.isNotBlank() && fetchedDateKey > todayKey
        val hasValidPrevious = previousActiveRate > 0.0 &&
            (previousActiveDateKey.isBlank() || previousActiveDateKey <= todayKey)

        // Case 1: Saturday/Sunday or Lunes Bancario (BCV Fecha Valor is in the future, e.g. Martes)
        if ((isWeekend || isFutureValueDate) && hasValidPrevious) {
            // If it's Saturday and BCV website STILL shows Friday's rate (<= today), prefer the live Friday value
            if (isWeekend && !isFutureValueDate && fetchedDateKey.isNotBlank() && !fetched.isFallbackSource) {
                return ResolvedBcvRate(
                    effectiveRate = fetched.rateRaw,
                    effectiveSourceName = "Tasa vigente del viernes (${fetched.valueDateLabel})",
                    effectiveDateLabel = fetched.valueDateLabel,
                    effectiveValueDateKey = fetchedDateKey,
                    isHoldingPreviousBankingRate = true,
                    shouldUpdateLastActiveBcv = true,
                )
            }

            val reasonSource = when {
                dayOfWeek == Calendar.SATURDAY -> "Sábado · Tasa vigente del viernes"
                dayOfWeek == Calendar.SUNDAY -> "Domingo · Tasa vigente del viernes"
                dayOfWeek == Calendar.MONDAY && isFutureValueDate ->
                    "Lunes bancario · Tasa del viernes (BCV prox.: ${fetched.valueDateLabel})"
                else -> "Feriado bancario · Tasa vigente (BCV prox.: ${fetched.valueDateLabel})"
            }
            return ResolvedBcvRate(
                effectiveRate = previousActiveRate,
                effectiveSourceName = reasonSource,
                effectiveDateLabel = previousActiveDateLabel.ifBlank { fetched.valueDateLabel },
                effectiveValueDateKey = previousActiveDateKey,
                isHoldingPreviousBankingRate = true,
                shouldUpdateLastActiveBcv = false,
            )
        }

        // Case 2: No previous rate cached yet on a future-dated holiday/Saturday -> use fetched rate with clear hint
        if (isFutureValueDate && !hasValidPrevious) {
            return ResolvedBcvRate(
                effectiveRate = fetched.rateRaw,
                effectiveSourceName = "BCV prox. (${fetched.valueDateLabel}) · Sin tasa previa",
                effectiveDateLabel = fetched.valueDateLabel,
                effectiveValueDateKey = fetchedDateKey,
                isHoldingPreviousBankingRate = false,
                shouldUpdateLastActiveBcv = false,
            )
        }

        // Case 3: Normal active banking day (Fecha Valor <= today) -> adopt live rate and store as active banking rate
        val activeKey = fetchedDateKey.ifBlank { todayKey }
        return ResolvedBcvRate(
            effectiveRate = fetched.rateRaw,
            effectiveSourceName = fetched.sourceName,
            effectiveDateLabel = fetched.valueDateLabel,
            effectiveValueDateKey = activeKey,
            isHoldingPreviousBankingRate = false,
            shouldUpdateLastActiveBcv = !isWeekend && activeKey <= todayKey,
        )
    }
}
