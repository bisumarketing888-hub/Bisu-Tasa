package com.example

import com.example.data.network.GitHubUpdateChecker
import com.example.data.network.RateScraperService
import com.example.domain.BcvFetchResult
import com.example.domain.RateCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RateCalculatorTest {

    @Test
    fun differentialMatchesUserCalculatorScreenshot() {
        val result = RateCalculator.calculateDifferential(
            binancePrice = 1104.110,
            bcvRawRate = 922.56370000,
        )
        assertEquals(922.56, result.bcvUsed, 0.001)
        assertEquals(19, result.percent)
        assertEquals("1", result.integerPart)
        assertEquals("19", result.highlightedTwoDigits)
    }

    @Test
    fun cardAndWhatsAppMessagesMatchExactFormat() {
        val cardText = RateCalculator.formatCardBcvText(922.56370000)
        assertEquals("Bs.922,56", cardText)

        val caption = RateCalculator.buildImageCaption(
            dateFormatted = "25/11/2026",
            timeLabel = "9:00 am",
            bcvRate = 922.56370000,
        )
        assertEquals("25/11/2026\n🕘 9:00 am\n*BCV Bs. 922,56*", caption)

        val diffMessage = RateCalculator.buildDifferentialMessage(19)
        assertEquals("⚠️ *DIFERENCIAL 19%* ⚠️", diffMessage)

        val combined = RateCalculator.buildCombinedMessageCaption(
            dateFormatted = "25/11/2026",
            timeLabel = "9:00 am",
            bcvRate = 922.56370000,
            differentialPercent = 19,
        )
        assertEquals("25/11/2026\n🕘 9:00 am\n*BCV Bs. 922,56*\n\n⚠️ *DIFERENCIAL 19%* ⚠️", combined)
    }

    @Test
    fun parsesBcvHtmlUsdBlock() {
        val sampleHtml = """
            <div id="dolar" class="col-sm-12 col-xs-12">
              <div class="field-content">
                <div class="row recuadrotsmc">
                  <div class="col-sm-6 col-xs-6"><span> USD</span></div>
                  <div class="col-sm-6 col-xs-6 centrado"><strong> 922,56370000 </strong></div>
                </div>
              </div>
            </div>
            <span class="date-display-single">Miércoles, 25 Noviembre 2026</span>
        """.trimIndent()

        val scraper = RateScraperService()
        val usd = scraper.extractUsdFromBcvHtml(sampleHtml)
        val dateLabel = scraper.extractValueDateFromBcvHtml(sampleHtml)

        assertNotNull(usd)
        assertEquals(922.5637, usd!!, 0.0001)
        assertEquals("Miércoles, 25 Noviembre 2026", dateLabel)
    }

    @Test
    fun parsesGitHubRepoSlugAndComparesReleaseTags() {
        assertEquals("bisuteria888/tasa-bisu", GitHubUpdateChecker.normalizeRepoSlug("bisuteria888/tasa-bisu"))
        assertEquals(
            "bisuteria888/tasa-bisu",
            GitHubUpdateChecker.normalizeRepoSlug("https://github.com/bisuteria888/tasa-bisu.git"),
        )
        assertEquals(
            "bisuteria888/tasa-bisu",
            GitHubUpdateChecker.normalizeRepoSlug("github.com/bisuteria888/tasa-bisu/releases"),
        )
        assertNull(GitHubUpdateChecker.normalizeRepoSlug("solo-un-segmento"))

        assertTrue(GitHubUpdateChecker.isNewerVersion("v1.0.5", "1.0.0"))
        assertTrue(GitHubUpdateChecker.isNewerVersion("v1.0.12", "1.0.9"))
        assertFalse(GitHubUpdateChecker.isNewerVersion("v1.0.5", "1.0.5"))
        assertFalse(GitHubUpdateChecker.isNewerVersion("v1.0.4", "1.0.5"))
        assertFalse(GitHubUpdateChecker.isNewerVersion("v1.0.6", "1.0.5", acknowledgedTag = "v1.0.6"))
    }

    @Test
    fun parsesBcvSpanishValueDatesAndAppliesSaturdayAndLunesBancarioRules() {
        assertEquals("2026-11-27", RateCalculator.parseBcvValueDateKey("Viernes, 27 Noviembre 2026"))
        assertEquals("2026-12-01", RateCalculator.parseBcvValueDateKey("Martes, 01 Diciembre 2026"))
        assertEquals("2026-11-27", RateCalculator.parseBcvValueDateKey("2026-11-27T00:00:00-04:00"))
        assertEquals("2026-11-27", RateCalculator.parseBcvValueDateKey("27/11/2026"))

        val fridayRate = 926.3592
        val tuesdayNewRate = 935.1200

        // 1. Friday 27/11/2026: BCV shows Viernes, 27 Noviembre 2026 -> adopts Friday rate and stores it
        val fridayResolved = RateCalculator.resolveEffectiveBcvRate(
            fetched = BcvFetchResult(
                rateRaw = fridayRate,
                valueDateLabel = "Viernes, 27 Noviembre 2026",
                sourceName = "bcv.org.ve (Oficial)",
                isFallbackSource = false,
                valueDateKey = "2026-11-27",
            ),
            todayKey = "2026-11-27",
            dayOfWeek = Calendar.FRIDAY,
            previousActiveRate = 922.56,
            previousActiveDateKey = "2026-11-26",
        )
        assertEquals(fridayRate, fridayResolved.effectiveRate, 0.0001)
        assertTrue(fridayResolved.shouldUpdateLastActiveBcv)
        assertFalse(fridayResolved.isHoldingPreviousBankingRate)

        // 2. Saturday 28/11/2026: BCV already shows Martes 01 Dic -> keeps Friday's rate (926.3592)
        val saturdayResolved = RateCalculator.resolveEffectiveBcvRate(
            fetched = BcvFetchResult(
                rateRaw = tuesdayNewRate,
                valueDateLabel = "Martes, 01 Diciembre 2026",
                sourceName = "bcv.org.ve (Oficial)",
                isFallbackSource = false,
                valueDateKey = "2026-12-01",
            ),
            todayKey = "2026-11-28",
            dayOfWeek = Calendar.SATURDAY,
            previousActiveRate = fridayRate,
            previousActiveDateKey = "2026-11-27",
            previousActiveDateLabel = "Viernes, 27 Noviembre 2026",
        )
        assertEquals(fridayRate, saturdayResolved.effectiveRate, 0.0001)
        assertTrue(saturdayResolved.isHoldingPreviousBankingRate)
        assertFalse(saturdayResolved.shouldUpdateLastActiveBcv)

        // 3. Lunes Bancario 30/11/2026: BCV shows Martes 01 Dic (future) -> STILL keeps Friday's rate
        val lunesBancarioResolved = RateCalculator.resolveEffectiveBcvRate(
            fetched = BcvFetchResult(
                rateRaw = tuesdayNewRate,
                valueDateLabel = "Martes, 01 Diciembre 2026",
                sourceName = "bcv.org.ve (Oficial)",
                isFallbackSource = false,
                valueDateKey = "2026-12-01",
            ),
            todayKey = "2026-11-30",
            dayOfWeek = Calendar.MONDAY,
            previousActiveRate = fridayRate,
            previousActiveDateKey = "2026-11-27",
            previousActiveDateLabel = "Viernes, 27 Noviembre 2026",
        )
        assertEquals(fridayRate, lunesBancarioResolved.effectiveRate, 0.0001)
        assertTrue(lunesBancarioResolved.isHoldingPreviousBankingRate)
        assertFalse(lunesBancarioResolved.shouldUpdateLastActiveBcv)
        assertTrue(lunesBancarioResolved.effectiveSourceName.contains("Lunes bancario"))

        // 4. Tuesday 01/12/2026: BCV shows Martes 01 Dic (today!) -> adopts Tuesday's new rate
        val tuesdayResolved = RateCalculator.resolveEffectiveBcvRate(
            fetched = BcvFetchResult(
                rateRaw = tuesdayNewRate,
                valueDateLabel = "Martes, 01 Diciembre 2026",
                sourceName = "bcv.org.ve (Oficial)",
                isFallbackSource = false,
                valueDateKey = "2026-12-01",
            ),
            todayKey = "2026-12-01",
            dayOfWeek = Calendar.TUESDAY,
            previousActiveRate = fridayRate,
            previousActiveDateKey = "2026-11-27",
            previousActiveDateLabel = "Viernes, 27 Noviembre 2026",
        )
        assertEquals(tuesdayNewRate, tuesdayResolved.effectiveRate, 0.0001)
        assertFalse(tuesdayResolved.isHoldingPreviousBankingRate)
        assertTrue(tuesdayResolved.shouldUpdateLastActiveBcv)
    }
}
