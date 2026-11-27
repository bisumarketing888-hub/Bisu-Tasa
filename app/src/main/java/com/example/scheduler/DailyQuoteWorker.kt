package com.example.scheduler

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.BuildConfig
import com.example.R
import com.example.automation.AutoSendOrchestrator
import com.example.automation.WhatsAppAutoSendService
import com.example.data.local.AppDatabase
import com.example.data.local.RateHistoryEntity
import com.example.data.local.SettingsStore
import com.example.data.media.QuoteCardGenerator
import com.example.data.network.GitHubUpdateChecker
import com.example.data.network.RateScraperService
import com.example.domain.RateCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.Calendar

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
        val nowCal = Calendar.getInstance()
        val dayOfWeek = nowCal.get(Calendar.DAY_OF_WEEK)
        // Only send Monday through Saturday; skip Sunday completely
        if (dayOfWeek == Calendar.SUNDAY) {
            return@coroutineScope Result.success()
        }

        val scraper = RateScraperService()
        val settingsStore = SettingsStore(applicationContext)
        val settings = settingsStore.getSettingsSnapshot()
        val cachedSnapshot = settingsStore.getCachedQuoteSnapshot()

        val bcvDeferred = async { scraper.fetchBcvRate() }
        val binanceDeferred = async { scraper.fetchBinanceBanescoSellOffers() }

        val bcvResult = bcvDeferred.await().getOrNull()
        val binanceResult = binanceDeferred.await().getOrNull()

        if (bcvResult == null || binanceResult == null || binanceResult.offers.isEmpty()) {
            return@coroutineScope if (runAttemptCount < 3) Result.retry() else Result.failure()
        }

        val firstOffer = binanceResult.offers.first()
        val nowMillis = System.currentTimeMillis()
        val todayFormatted = RateCalculator.formatTodayDate(nowMillis)
        val todayKey = RateCalculator.formatDateKey(nowMillis)

        val dao = AppDatabase.getInstance(applicationContext).rateHistoryDao()
        val priorHistory = dao.getLatestBeforeDate(todayKey)
        val previousActiveRate = when {
            cachedSnapshot.lastActiveBcvRate > 0.0 -> cachedSnapshot.lastActiveBcvRate
            priorHistory != null && priorHistory.bcvRate > 0.0 -> priorHistory.bcvRate
            cachedSnapshot.bcvRate > 0.0 -> cachedSnapshot.bcvRate
            else -> 0.0
        }
        val previousActiveDateKey = when {
            cachedSnapshot.lastActiveBcvRate > 0.0 -> cachedSnapshot.lastActiveBcvValueDateKey
            priorHistory != null -> priorHistory.dateKey
            else -> ""
        }
        val previousActiveDateLabel = when {
            cachedSnapshot.lastActiveBcvRate > 0.0 -> cachedSnapshot.lastActiveBcvDateLabel
            priorHistory != null -> priorHistory.dateFormatted
            else -> cachedSnapshot.bcvDateLabel
        }

        val resolvedBcv = RateCalculator.resolveEffectiveBcvRate(
            fetched = bcvResult,
            todayKey = todayKey,
            dayOfWeek = dayOfWeek,
            previousActiveRate = previousActiveRate,
            previousActiveDateKey = previousActiveDateKey,
            previousActiveDateLabel = previousActiveDateLabel,
        )

        if (resolvedBcv.shouldUpdateLastActiveBcv) {
            settingsStore.saveLastActiveBcvRate(
                bcvRate = resolvedBcv.effectiveRate,
                valueDateKey = resolvedBcv.effectiveValueDateKey,
                valueDateLabel = resolvedBcv.effectiveDateLabel,
            )
        }

        val effectiveBcvRate = resolvedBcv.effectiveRate
        val diff = RateCalculator.calculateDifferential(
            binancePrice = firstOffer.price,
            bcvRawRate = effectiveBcvRate,
        )

        // Generate and cache today's ready-to-share PNG card (always with today's date + effective BCV rate)
        val bitmap = QuoteCardGenerator.generateQuoteBitmap(
            context = applicationContext,
            bcvRate = effectiveBcvRate,
            dateFormatted = todayFormatted,
            customTemplateUri = settings.customTemplateUri,
        )
        val shareUri = QuoteCardGenerator.saveForSharing(applicationContext, bitmap, todayKey)
        bitmap.recycle()

        // Save snapshot in DataStore & Room history
        settingsStore.saveFetchedSnapshot(
            bcvRate = effectiveBcvRate,
            bcvSource = resolvedBcv.effectiveSourceName,
            bcvDateLabel = resolvedBcv.effectiveDateLabel,
            binanceRate = firstOffer.price,
            binanceMerchant = firstOffer.advertiserName,
            quoteDateFormatted = todayFormatted,
            fetchedAtMillis = nowMillis,
        )

        val existing = dao.getByDateKey(todayKey)
        dao.upsert(
            RateHistoryEntity(
                dateKey = todayKey,
                dateFormatted = todayFormatted,
                bcvRate = RateCalculator.roundToTwoDecimals(effectiveBcvRate),
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
                bcvRate = effectiveBcvRate,
                differentialPercent = diff.percent,
            )
        } else {
            RateCalculator.buildImageCaption(
                dateFormatted = todayFormatted,
                timeLabel = settings.displayTimeLabel,
                bcvRate = effectiveBcvRate,
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
                    bcvRate = effectiveBcvRate,
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
                bcvRate = effectiveBcvRate,
                differentialPercent = diff.percent,
                targetGroupName = settings.targetGroupName,
            )
        }

        // Also check GitHub Releases for app updates in the background if configured
        val repoSlug = settings.githubRepoSlug
            .ifBlank { BuildConfig.DEFAULT_GITHUB_REPO }
            .ifBlank { com.example.domain.DispatchSettings.DEFAULT_GITHUB_REPO }
        if (settings.autoCheckUpdates && repoSlug.isNotBlank()) {
            runCatching {
                val release = GitHubUpdateChecker()
                    .fetchLatestRelease(repoSlug)
                    .getOrNull()
                if (release != null && GitHubUpdateChecker.isNewerVersion(
                        latestTag = release.tagName,
                        installedVersion = BuildConfig.VERSION_NAME,
                        acknowledgedTag = settings.acknowledgedReleaseTag,
                    )
                ) {
                    val lastNotified = settingsStore.getLastNotifiedReleaseTag()
                    if (!lastNotified.equals(release.tagName, ignoreCase = true)) {
                        val posted = DailyScheduleManager.postUpdateAvailableNotification(
                            context = applicationContext,
                            releaseTag = release.tagName,
                            releaseNotes = release.releaseNotes,
                            releaseHtmlUrl = release.htmlUrl,
                        )
                        if (posted) {
                            settingsStore.markReleaseNotified(release.tagName)
                        }
                    }
                }
            }
        }

        Result.success()
    }
}
