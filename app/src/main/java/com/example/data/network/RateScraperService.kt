package com.example.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.domain.BcvFetchResult
import com.example.domain.BinanceFetchResult
import com.example.domain.BinanceOffer
import com.example.domain.RateCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class RateScraperService(
    private val client: OkHttpClient = defaultClient(),
) {

    suspend fun fetchBcvRate(): Result<BcvFetchResult> = withContext(Dispatchers.IO) {
        val primary = withRetry(attempts = 2) { fetchDirectFromBcvWebsite() }
        if (primary.isSuccess) {
            return@withContext primary
        }
        val fallback = withRetry(attempts = 2) { fetchFromDolarApiMirror() }
        if (fallback.isSuccess) {
            return@withContext fallback
        }
        Result.failure(
            primary.exceptionOrNull()
                ?: fallback.exceptionOrNull()
                ?: IOException("No se pudo obtener la tasa del BCV. Verifica tu conexión Wi-Fi."),
        )
    }

    private fun fetchDirectFromBcvWebsite(): Result<BcvFetchResult> {
        return runCatching {
            val request = Request.Builder()
                .url(BCV_URL)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36",
                )
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "es-VE,es;q=0.9,en;q=0.8")
                .header("Cache-Control", "no-cache")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("BCV respondió HTTP ${response.code}")
                }
                val html = response.body?.string().orEmpty()
                if (html.isBlank()) {
                    throw IOException("Respuesta vacía de bcv.org.ve")
                }

                val parsedRate = extractUsdFromBcvHtml(html)
                    ?: throw IOException("No se encontró el bloque USD en bcv.org.ve")
                val valueDate = extractValueDateFromBcvHtml(html)
                    ?: RateCalculator.formatTodayDate()

                BcvFetchResult(
                    rateRaw = parsedRate,
                    valueDateLabel = valueDate,
                    sourceName = "bcv.org.ve (Oficial)",
                    isFallbackSource = false,
                )
            }
        }
    }

    private fun fetchFromDolarApiMirror(): Result<BcvFetchResult> {
        return runCatching {
            val request = Request.Builder()
                .url(BCV_FALLBACK_MIRROR_URL)
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Respaldo BCV respondió HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val promedio = json.optDouble("promedio", Double.NaN)
                if (promedio.isNaN() || promedio <= 0.0) {
                    throw IOException("Valor de tasa inválido en respaldo BCV")
                }
                val updated = json.optString("fechaActualizacion", "")
                    .take(10)
                    .ifBlank { RateCalculator.formatTodayDate() }

                BcvFetchResult(
                    rateRaw = promedio,
                    valueDateLabel = updated,
                    sourceName = "Espejo BCV Oficial",
                    isFallbackSource = true,
                )
            }
        }
    }

    /**
     * Fetches live SELL USDT -> VES offers filtered by Banesco from Binance P2P public API.
     * Matches https://p2p.binance.com/es/trade/sell/USDT?fiat=VES&payment=Banesco
     */
    suspend fun fetchBinanceBanescoSellOffers(): Result<BinanceFetchResult> =
        withContext(Dispatchers.IO) {
            withRetry(attempts = 2) {
                runCatching {
                    val payload = JSONObject().apply {
                        put("fiat", "VES")
                        put("page", 1)
                        put("rows", 12)
                        put("tradeType", "SELL")
                        put("asset", "USDT")
                        put("countries", JSONArray())
                        put("proMerchantAds", false)
                        put("shieldMerchantAds", false)
                        put("filterType", "all")
                        put("periods", JSONArray())
                        put("additionalKycVerifyFilter", 0)
                        put("publisherType", JSONObject.NULL)
                        put("payTypes", JSONArray().put("Banesco"))
                        put(
                            "classifies",
                            JSONArray()
                                .put("mass")
                                .put("profession")
                                .put("fiat_trade"),
                        )
                    }

                    val request = Request.Builder()
                        .url(BINANCE_P2P_SEARCH_URL)
                        .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
                        )
                        .header("Accept", "*/*")
                        .header("Accept-Language", "es-ES,es;q=0.9")
                        .header("Origin", "https://p2p.binance.com")
                        .header(
                            "Referer",
                            "https://p2p.binance.com/es/trade/sell/USDT?fiat=VES&payment=Banesco",
                        )
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IOException("Binance P2P respondió HTTP ${response.code}")
                        }
                        val body = response.body?.string().orEmpty()
                        val root = JSONObject(body)
                        val dataArray = root.optJSONArray("data")
                            ?: throw IOException("Respuesta sin anuncios de Binance P2P")

                        val offers = mutableListOf<BinanceOffer>()
                        for (i in 0 until dataArray.length()) {
                            val item = dataArray.optJSONObject(i) ?: continue
                            val adv = item.optJSONObject("adv") ?: continue
                            val advertiser = item.optJSONObject("advertiser") ?: continue

                            if (!adv.optBoolean("isTradable", true)) continue
                            val price = adv.optString("price", "").toDoubleOrNull() ?: continue
                            if (price <= 0.0) continue

                            val available =
                                adv.optString("surplusAmount", "0").toDoubleOrNull() ?: 0.0
                            val minVes =
                                adv.optString("minSingleTransAmount", "0").toDoubleOrNull() ?: 0.0
                            val maxVes = adv.optString("dynamicMaxSingleTransAmount", "")
                                .toDoubleOrNull()
                                ?: adv.optString("maxSingleTransAmount", "0").toDoubleOrNull()
                                ?: 0.0
                            val nickName = advertiser.optString("nickName", "Anunciante #${i + 1}")
                            val monthOrders = advertiser.optInt("monthOrderCount", 0)
                            val finishRate = advertiser.optDouble("monthFinishRate", 0.0) * 100.0
                            val isPromoted =
                                (!item.isNull("privilegeDesc") &&
                                    item.optString("privilegeDesc", "").isNotBlank()) ||
                                    !item.isNull("privilegeType")

                            offers.add(
                                BinanceOffer(
                                    advertiserName = nickName,
                                    price = price,
                                    availableUsdt = available,
                                    minVes = minVes,
                                    maxVes = maxVes,
                                    finishRatePercent = finishRate,
                                    monthOrders = monthOrders,
                                    isPromoted = isPromoted,
                                ),
                            )
                        }

                        if (offers.isEmpty()) {
                            throw IOException("No se encontraron ofertas USDT/VES con Banesco")
                        }

                        // Prioritize organic offers first (matching first non-promoted result on Binance web)
                        val sortedOffers = offers.sortedBy { it.isPromoted }
                        BinanceFetchResult(
                            offers = sortedOffers.take(8),
                            sourceName = "Binance P2P · Vender USDT (Banesco)",
                        )
                    }
                }
            }
        }

    private suspend fun <T> withRetry(
        attempts: Int,
        delayMs: Long = 1_200L,
        block: () -> Result<T>,
    ): Result<T> {
        var lastResult = block()
        var currentAttempt = 1
        while (lastResult.isFailure && currentAttempt < attempts) {
            delay(delayMs)
            lastResult = block()
            currentAttempt++
        }
        return lastResult
    }

    internal fun extractUsdFromBcvHtml(html: String): Double? {
        val dolarBlockRegex = Regex(
            pattern = """id=["']dolar["'][\s\S]{0,600}?<strong>\s*([0-9.,]+)\s*</strong>""",
            options = setOf(RegexOption.IGNORE_CASE),
        )
        dolarBlockRegex.find(html)?.groupValues?.getOrNull(1)?.let { match ->
            RateCalculator.parseFlexibleNumber(match)?.let { return it }
        }

        val usdLabelRegex = Regex(
            pattern = """USD[\s\S]{0,220}?<strong>\s*([0-9.,]+)\s*</strong>""",
            options = setOf(RegexOption.IGNORE_CASE),
        )
        usdLabelRegex.find(html)?.groupValues?.getOrNull(1)?.let { match ->
            RateCalculator.parseFlexibleNumber(match)?.let { return it }
        }

        return null
    }

    internal fun extractValueDateFromBcvHtml(html: String): String? {
        val dateRegex = Regex(
            pattern = """class=["'][^"']*date-display-single[^"']*["'][^>]*>([^<]+)<""",
            options = setOf(RegexOption.IGNORE_CASE),
        )
        return dateRegex.find(html)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    companion object {
        const val BCV_URL = "https://www.bcv.org.ve/"
        const val BCV_FALLBACK_MIRROR_URL = "https://ve.dolarapi.com/v1/dolares/oficial"
        const val BINANCE_P2P_SEARCH_URL =
            "https://p2p.binance.com/bapi/c2c/v2/friendly/c2c/adv/search"
        const val BINANCE_WEB_URL =
            "https://p2p.binance.com/es/trade/sell/USDT?fiat=VES&payment=Banesco"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun isNetworkConnected(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .build()
    }
}
