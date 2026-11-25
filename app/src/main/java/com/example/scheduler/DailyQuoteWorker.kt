package com.example.scheduler

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.R
import com.example.automation.AutoSendOrchestrator
import com.example.automation.WhatsAppAutoSendService
import com.example.data.local.AppDatabase
import com.example.data.local.RateHistoryEntity
import com.example.data.local.SettingsStore
import com.example.data.media.QuoteCardGenerator
import com.example.data.network.RateScraperService
import com.example.domain.RateCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class DailyQuoteWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        DailyScheduleManager.ensureNotificationChannel(applicationContext)
        val notification = NotificationCompat.Builder(
            applicationContext,
            DailyScheduleManager.NOTIFICATION_CHANNEL_ID,
        )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Tasa Bisu · Consultando BCV y Binance P2P")
            .setContentText("Preparando cotización y diferencial del día...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        return ForegroundInfo(8889, notification)
    }

    override suspend fun doWork(): Result = coroutineScope {
        val scraper = RateScraperService()
        val settingsStore = SettingsStore(applicationContext)
        val settings = settingsStore.getSettingsSnapshot()

        val bcvDeferred = async { scraper.fetchBcvRate() }
        val binanceDeferred = async { scraper.fetchBinanceBanescoSellOffers() }

        val bcvResult = bcvDeferred.await().getOrNull()
        val binanceResult = binanceDeferred.await().getOrNull()

        if (bcvResult == null || binanceResult == null || binanceResult.offers.isEmpty()) {
            return@coroutineScope if (runAttemptCount < 3) Result.retry() else Result.failure()
        }

        val firstOffer = binanceResult.offers.first()
        val todayFormatted = RateCalculator.formatTodayDate()
        val todayKey = RateCalculator.formatDateKey()
        val nowMillis = System.currentTimeMillis()
        val diff = RateCalculator.calculateDifferential(
            binancePrice = firstOffer.price,
            bcvRawRate = bcvResult.rateRaw,
        )

        // Generate and cache today's ready-to-share PNG card
        val bitmap = QuoteCardGenerator.generateQuoteBitmap(
            context = applicationContext,
            bcvRate = bcvResult.rateRaw,
            dateFormatted = todayFormatted,
            customTemplateUri = settings.customTemplateUri,
        )
        val shareUri = QuoteCardGenerator.saveForSharing(applicationContext, bitmap, todayKey)
        bitmap.recycle()

        // Save snapshot in DataStore & Room history
        settingsStore.saveFetchedSnapshot(
            bcvRate = bcvResult.rateRaw,
            bcvSource = bcvResult.sourceName,
            bcvDateLabel = bcvResult.valueDateLabel,
            binanceRate = firstOffer.price,
            binanceMerchant = firstOffer.advertiserName,
            quoteDateFormatted = todayFormatted,
            fetchedAtMillis = nowMillis,
        )

        val dao = AppDatabase.getInstance(applicationContext).rateHistoryDao()
        val existing = dao.getByDateKey(todayKey)
        dao.upsert(
            RateHistoryEntity(
                dateKey = todayKey,
                dateFormatted = todayFormatted,
                bcvRate = RateCalculator.roundToTwoDecimals(bcvResult.rateRaw),
                binanceRate = firstOffer.price,
                binanceMerchant = firstOffer.advertiserName,
                differentialPercent = diff.percent,
                imageSent = existing?.imageSent ?: false,
                differentialSent = existing?.differentialSent ?: false,
                updatedAtMillis = nowMillis,
            ),
        )

        val caption = if (settings.singleMessageMode) {
            RateCalculator.buildCombinedMessageCaption(
                dateFormatted = todayFormatted,
                timeLabel = settings.displayTimeLabel,
                bcvRate = bcvResult.rateRaw,
                differentialPercent = diff.percent,
            )
        } else {
            RateCalculator.buildImageCaption(
                dateFormatted = todayFormatted,
                timeLabel = settings.displayTimeLabel,
                bcvRate = bcvResult.rateRaw,
            )
        }
        val diffMessage = RateCalculator.buildDifferentialMessage(diff.percent)

        // Trigger hands-free WhatsApp Business auto-send if configured & Accessibility is active
        val startedAutoSend = if (
            settings.autoSendEnabled &&
            settings.targetGroupName.isNotBlank() &&
            WhatsAppAutoSendService.isServiceEnabled(applicationContext)
        ) {
            withContext(Dispatchers.Main) {
                AutoSendOrchestrator.startAutoSend(
                    context = applicationContext,
                    targetGroupName = settings.targetGroupName,
                    preferredApp = settings.preferredApp,
                    imageUri = shareUri,
                    imageCaption = caption,
                    differentialMessage = diffMessage,
                    singleMessageMode = settings.singleMessageMode,
                    bcvRate = bcvResult.rateRaw,
                    differentialPercent = diff.percent,
                )
            }
        } else {
            false
        }

        // Fallback to interactive notification if unattended auto-send is paused or not yet enabled
        if (!startedAutoSend) {
            DailyScheduleManager.postReadyNotification(
                context = applicationContext,
                bcvRate = bcvResult.rateRaw,
                differentialPercent = diff.percent,
                targetGroupName = settings.targetGroupName,
            )
        }

        Result.success()
    }
}
