package com.example

import com.example.data.network.RateScraperService
import com.example.domain.RateCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

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
}
