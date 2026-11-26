package com.example.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.automation.AutoSendOrchestrator
import com.example.automation.AutoSendStatus
import com.example.automation.WhatsAppAutoSendService
import com.example.data.local.AppDatabase
import com.example.data.local.RateHistoryEntity
import com.example.data.local.SettingsStore
import com.example.data.media.QuoteCardGenerator
import com.example.data.network.GitHubReleaseInfo
import com.example.data.network.GitHubUpdateChecker
import com.example.data.network.RateScraperService
import com.example.domain.AutomationReadiness
import com.example.domain.BinanceOffer
import com.example.domain.DifferentialResult
import com.example.domain.DispatchSettings
import com.example.domain.PreferredWhatsAppApp
import com.example.domain.RateCalculator
import com.example.scheduler.DailyScheduleManager
import com.example.scheduler.GitHubUpdateCheckWorker
import com.example.whatsapp.DispatchOutcome
import com.example.whatsapp.WhatsAppAvailability
import com.example.whatsapp.WhatsAppDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TasaBisuUiState(
    val isLoading: Boolean = false,
    val bcvRateRaw: Double = 0.0,
    val bcvSourceName: String = "",
    val bcvValueDateLabel: String = "",
    val bcvError: String? = null,
    val binanceOffers: List<BinanceOffer> = emptyList(),
    val selectedOfferIndex: Int = 0,
    val binanceManualRate: Double? = null,
    val binanceMerchantDisplay: String = "",
    val binanceError: String? = null,
    val quoteDateFormatted: String = RateCalculator.formatTodayDate(),
    val lastFetchedMillis: Long = 0L,
    val cardBitmap: Bitmap? = null,
    val cardShareUri: Uri? = null,
    val imageSentToday: Boolean = false,
    val diffSentToday: Boolean = false,
    val waitingForStep2Prompt: Boolean = false,
    val settings: DispatchSettings = DispatchSettings(),
    val whatsAppAvailability: WhatsAppAvailability = WhatsAppAvailability(false, false),
    val automationReadiness: AutomationReadiness = AutomationReadiness(),
    val autoSendStatus: AutoSendStatus = AutoSendStatus(),
    val history: List<RateHistoryEntity> = emptyList(),
    val statusBannerMessage: String? = null,
    val availableUpdate: GitHubReleaseInfo? = null,
    val isCheckingUpdate: Boolean = false,
    val updateStatusText: String? = null,
) {
    val activeBinanceRate: Double
        get() = binanceManualRate
            ?: binanceOffers.getOrNull(selectedOfferIndex)?.price
            ?: 0.0

    val differential: DifferentialResult
        get() = RateCalculator.calculateDifferential(
            binancePrice = activeBinanceRate,
            bcvRawRate = bcvRateRaw,
        )

    val imageCaption: String
        get() = if (settings.singleMessageMode) {
            RateCalculator.buildCombinedMessageCaption(
                dateFormatted = quoteDateFormatted,
                timeLabel = settings.displayTimeLabel,
                bcvRate = bcvRateRaw,
                differentialPercent = differential.percent,
            )
        } else {
            RateCalculator.buildImageCaption(
                dateFormatted = quoteDateFormatted,
                timeLabel = settings.displayTimeLabel,
                bcvRate = bcvRateRaw,
            )
        }

    val differentialMessage: String
        get() = RateCalculator.buildDifferentialMessage(differential.percent)

    val hasValidRates: Boolean
        get() = bcvRateRaw > 0.0 && activeBinanceRate > 0.0
}

class TasaBisuViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val scraper = RateScraperService()
    private val updateChecker = GitHubUpdateChecker()
    private val settingsStore = SettingsStore(appContext)
    private val historyDao = AppDatabase.getInstance(appContext).rateHistoryDao()

    private val _uiState = MutableStateFlow(
        TasaBisuUiState(
            whatsAppAvailability = WhatsAppDispatcher.checkAvailability(appContext),
        ),
    )
    val uiState: StateFlow<TasaBisuUiState> = _uiState.asStateFlow()

    private var pendingQuickAction: String? = null
    private var initialUpdateCheckTriggered = false

    init {
        DailyScheduleManager.ensureNotificationChannel(appContext)
        observePersistence()
        observeAutoSendStatus()
        refreshSystemPermissionsStatus()
        refreshRates(isInitial = true)
    }

    private fun observeAutoSendStatus() {
        viewModelScope.launch {
            AutoSendOrchestrator.status.collect { status ->
                _uiState.update { it.copy(autoSendStatus = status) }
                if (!status.lastError.isNullOrBlank()) {
                    showBanner(status.lastError)
                }
            }
        }
    }

    fun refreshSystemPermissionsStatus() {
        val wa = WhatsAppDispatcher.checkAvailability(appContext)
        val settings = _uiState.value.settings
        val readiness = AutomationReadiness(
            accessibilityEnabled = WhatsAppAutoSendService.isServiceEnabled(appContext),
            exactAlarmGranted = DailyScheduleManager.canScheduleExactAlarms(appContext),
            batteryUnrestricted = DailyScheduleManager.isIgnoringBatteryOptimizations(appContext),
            whatsAppInstalled = wa.anyInstalled,
            groupConfigured = settings.targetGroupName.isNotBlank(),
        )
        _uiState.update {
            it.copy(
                whatsAppAvailability = wa,
                automationReadiness = readiness,
            )
        }
    }

    private fun observePersistence() {
        viewModelScope.launch {
            combine(
                settingsStore.settingsFlow,
                settingsStore.cachedQuoteFlow,
                historyDao.observeAll(),
            ) { settings, cached, history ->
                Triple(settings, cached, history)
            }.collect { (settings, cached, history) ->
                val todayKey = RateCalculator.formatDateKey()
                val current = _uiState.value
                val templateChanged = current.settings.customTemplateUri != settings.customTemplateUri

                val effectiveBcv = if (current.bcvRateRaw > 0.0) current.bcvRateRaw else cached.bcvRate
                val effectiveBinance = if (current.activeBinanceRate > 0.0) {
                    current.binanceManualRate
                } else if (cached.binanceRate > 0.0) {
                    cached.binanceRate
                } else {
                    null
                }
                val effectiveMerchant = current.binanceMerchantDisplay.ifBlank { cached.binanceMerchant }
                val effectiveSource = current.bcvSourceName.ifBlank { cached.bcvSource }
                val effectiveBcvDate = current.bcvValueDateLabel.ifBlank { cached.bcvDateLabel }
                val effectiveFetched = if (current.lastFetchedMillis > 0L) {
                    current.lastFetchedMillis
                } else {
                    cached.fetchedAtMillis
                }

                val wa = WhatsAppDispatcher.checkAvailability(appContext)
                _uiState.update { state ->
                    state.copy(
                        settings = settings,
                        history = history,
                        bcvRateRaw = effectiveBcv,
                        bcvSourceName = effectiveSource,
                        bcvValueDateLabel = effectiveBcvDate,
                        binanceManualRate = effectiveBinance,
                        binanceMerchantDisplay = effectiveMerchant,
                        lastFetchedMillis = effectiveFetched,
                        imageSentToday = state.imageSentToday || cached.imageSentDateKey == todayKey,
                        diffSentToday = state.diffSentToday || cached.diffSentDateKey == todayKey,
                        whatsAppAvailability = wa,
                        automationReadiness = AutomationReadiness(
                            accessibilityEnabled = WhatsAppAutoSendService.isServiceEnabled(appContext),
                            exactAlarmGranted = DailyScheduleManager.canScheduleExactAlarms(appContext),
                            batteryUnrestricted = DailyScheduleManager.isIgnoringBatteryOptimizations(appContext),
                            whatsAppInstalled = wa.anyInstalled,
                            groupConfigured = settings.targetGroupName.isNotBlank(),
                        ),
                    )
                }

                if (templateChanged || (current.cardBitmap == null && effectiveBcv > 0.0)) {
                    regenerateCard()
                }

                if (!initialUpdateCheckTriggered) {
                    initialUpdateCheckTriggered = true
                    val repo = settings.githubRepoSlug
                        .ifBlank { BuildConfig.DEFAULT_GITHUB_REPO }
                        .ifBlank { DispatchSettings.DEFAULT_GITHUB_REPO }
                    GitHubUpdateCheckWorker.syncPeriodicSchedule(
                        context = appContext,
                        enabled = settings.autoCheckUpdates,
                        repoSlug = repo,
                    )
                    if (settings.autoCheckUpdates && repo.isNotBlank()) {
                        checkForGitHubUpdates(manual = false)
                    }
                }
            }
        }
    }

    fun refreshRates(isInitial: Boolean = false) {
        if (_uiState.value.isLoading) return
        if (!RateScraperService.isNetworkConnected(appContext)) {
            if (!isInitial) {
                showBanner("Sin conexión a internet. Conecta el Wi-Fi para actualizar BCV y Binance P2P.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    bcvError = null,
                    binanceError = null,
                    quoteDateFormatted = RateCalculator.formatTodayDate(),
                )
            }

            val bcvJob = async { scraper.fetchBcvRate() }
            val binanceJob = async { scraper.fetchBinanceBanescoSellOffers() }

            val bcvRes = bcvJob.await()
            val binanceRes = binanceJob.await()

            val bcvData = bcvRes.getOrNull()
            val binanceData = binanceRes.getOrNull()
            val nowMillis = System.currentTimeMillis()
            val todayFormatted = RateCalculator.formatTodayDate(nowMillis)

            _uiState.update { current ->
                val newBcv = bcvData?.rateRaw ?: current.bcvRateRaw
                val newOffers = binanceData?.offers ?: current.binanceOffers
                val firstOffer = newOffers.firstOrNull()
                val clearManualBinance = binanceData != null && firstOffer != null

                current.copy(
                    isLoading = false,
                    bcvRateRaw = newBcv,
                    bcvSourceName = bcvData?.sourceName ?: current.bcvSourceName,
                    bcvValueDateLabel = bcvData?.valueDateLabel ?: current.bcvValueDateLabel,
                    bcvError = bcvRes.exceptionOrNull()?.localizedMessage,
                    binanceOffers = newOffers,
                    selectedOfferIndex = 0,
                    binanceManualRate = if (clearManualBinance) null else current.binanceManualRate,
                    binanceMerchantDisplay = firstOffer?.advertiserName
                        ?: current.binanceMerchantDisplay,
                    binanceError = binanceRes.exceptionOrNull()?.localizedMessage,
                    quoteDateFormatted = todayFormatted,
                    lastFetchedMillis = if (bcvData != null || binanceData != null) {
                        nowMillis
                    } else {
                        current.lastFetchedMillis
                    },
                    whatsAppAvailability = WhatsAppDispatcher.checkAvailability(appContext),
                )
            }

            val updated = _uiState.value
            regenerateCard()

            if (updated.hasValidRates) {
                persistSnapshot(updated)
                if (!isInitial) {
                    showBanner("Tasas actualizadas · BCV Bs. ${RateCalculator.formatVenezuelanNumber(RateCalculator.roundToTwoDecimals(updated.bcvRateRaw))} · Dif. ${updated.differential.percent}%")
                }
            } else if (!isInitial) {
                showBanner("No se pudieron descargar todas las tasas. Puedes editarlas manualmente.")
            }

            // Ensure daily schedule is armed on first launch if enabled
            if (isInitial && updated.settings.scheduleEnabled) {
                DailyScheduleManager.scheduleNextDailyWindow(appContext, updated.settings)
            }

            // Execute any deferred notification quick action once card is ready
            pendingQuickAction?.let { action ->
                pendingQuickAction = null
                handleQuickAction(action)
            }
        }
    }

    fun selectBinanceOffer(index: Int) {
        val offers = _uiState.value.binanceOffers
        val chosen = offers.getOrNull(index) ?: return
        _uiState.update {
            it.copy(
                selectedOfferIndex = index,
                binanceManualRate = null,
                binanceMerchantDisplay = chosen.advertiserName,
            )
        }
        viewModelScope.launch {
            persistSnapshot(_uiState.value)
        }
    }

    fun applyManualRates(
        bcvInput: String,
        binanceInput: String,
        dateInput: String,
    ) {
        val parsedBcv = RateCalculator.parseFlexibleNumber(bcvInput)
        val parsedBinance = RateCalculator.parseFlexibleNumber(binanceInput)
        val cleanDate = dateInput.trim().ifBlank { RateCalculator.formatTodayDate() }

        _uiState.update { current ->
            current.copy(
                bcvRateRaw = parsedBcv ?: current.bcvRateRaw,
                bcvSourceName = if (parsedBcv != null) "Ingreso manual" else current.bcvSourceName,
                binanceManualRate = parsedBinance ?: current.binanceManualRate,
                binanceMerchantDisplay = if (parsedBinance != null) {
                    "Ajuste manual"
                } else {
                    current.binanceMerchantDisplay
                },
                quoteDateFormatted = cleanDate,
                lastFetchedMillis = System.currentTimeMillis(),
            )
        }

        viewModelScope.launch {
            regenerateCard()
            val state = _uiState.value
            if (state.hasValidRates) {
                persistSnapshot(state)
            }
            showBanner("Cotización actualizada manualmente")
        }
    }

    private suspend fun regenerateCard() {
        val state = _uiState.value
        val bitmap = QuoteCardGenerator.generateQuoteBitmap(
            context = appContext,
            bcvRate = state.bcvRateRaw,
            dateFormatted = state.quoteDateFormatted,
            customTemplateUri = state.settings.customTemplateUri,
        )
        val uri = QuoteCardGenerator.saveForSharing(
            context = appContext,
            bitmap = bitmap,
            dateKey = RateCalculator.formatDateKey(),
        )
        _uiState.update {
            it.copy(
                cardBitmap = bitmap,
                cardShareUri = uri,
            )
        }
    }

    private suspend fun persistSnapshot(state: TasaBisuUiState) {
        val todayKey = RateCalculator.formatDateKey()
        settingsStore.saveFetchedSnapshot(
            bcvRate = state.bcvRateRaw,
            bcvSource = state.bcvSourceName,
            bcvDateLabel = state.bcvValueDateLabel,
            binanceRate = state.activeBinanceRate,
            binanceMerchant = state.binanceMerchantDisplay,
            quoteDateFormatted = state.quoteDateFormatted,
            fetchedAtMillis = state.lastFetchedMillis,
        )
        val existing = historyDao.getByDateKey(todayKey)
        historyDao.upsert(
            RateHistoryEntity(
                dateKey = todayKey,
                dateFormatted = state.quoteDateFormatted,
                bcvRate = RateCalculator.roundToTwoDecimals(state.bcvRateRaw),
                binanceRate = state.activeBinanceRate,
                binanceMerchant = state.binanceMerchantDisplay.ifBlank { "Binance P2P" },
                differentialPercent = state.differential.percent,
                imageSent = existing?.imageSent ?: state.imageSentToday,
                differentialSent = existing?.differentialSent ?: state.diffSentToday,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    fun sendStep1ImageToWhatsApp() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.cardShareUri == null) {
                regenerateCard()
            }
            val ready = _uiState.value
            val uri = ready.cardShareUri
            if (uri == null) {
                showBanner("No se pudo generar la imagen todavía.")
                return@launch
            }

            val outcome = WhatsAppDispatcher.sendImageWithCaption(
                context = appContext,
                imageUri = uri,
                caption = ready.imageCaption,
                preferred = ready.settings.preferredApp,
            )

            when (outcome) {
                is DispatchOutcome.Launched -> {
                    markImageDispatched()
                    showBanner("Paso 1 abierto en ${outcome.appLabel}. Selecciona \"${ready.settings.targetGroupName}\".")
                }

                is DispatchOutcome.LaunchedChooser -> {
                    markImageDispatched()
                    showBanner(outcome.reason)
                }

                is DispatchOutcome.Failed -> showBanner(outcome.message)
            }
        }
    }

    fun sendStep2DifferentialToWhatsApp() {
        viewModelScope.launch {
            val state = _uiState.value
            val outcome = WhatsAppDispatcher.sendTextMessage(
                context = appContext,
                message = state.differentialMessage,
                preferred = state.settings.preferredApp,
            )

            when (outcome) {
                is DispatchOutcome.Launched -> {
                    markDifferentialDispatched()
                    showBanner("Paso 2 abierto en ${outcome.appLabel}. ¡Diferencial ${state.differential.percent}% listo!")
                }

                is DispatchOutcome.LaunchedChooser -> {
                    markDifferentialDispatched()
                    showBanner(outcome.reason)
                }

                is DispatchOutcome.Failed -> showBanner(outcome.message)
            }
        }
    }

    private suspend fun markImageDispatched() {
        val todayKey = RateCalculator.formatDateKey()
        settingsStore.markImageSentForDate(todayKey)
        historyDao.markImageSent(todayKey)
        _uiState.update {
            it.copy(
                imageSentToday = true,
                waitingForStep2Prompt = !it.diffSentToday,
            )
        }
    }

    private suspend fun markDifferentialDispatched() {
        val todayKey = RateCalculator.formatDateKey()
        settingsStore.markDiffSentForDate(todayKey)
        historyDao.markDifferentialSent(todayKey)
        _uiState.update {
            it.copy(
                diffSentToday = true,
                waitingForStep2Prompt = false,
            )
        }
    }

    fun saveCardToGallery() {
        viewModelScope.launch {
            val bmp = _uiState.value.cardBitmap ?: return@launch
            val result = QuoteCardGenerator.saveToGallery(appContext, bmp)
            if (result.isSuccess) {
                showBanner("Imagen guardada en Galería (Pictures/TasaBisu)")
            } else {
                showBanner("No se pudo guardar en galería: ${result.exceptionOrNull()?.localizedMessage ?: ""}")
            }
        }
    }

    fun copyCaption() {
        WhatsAppDispatcher.copyToClipboard(appContext, "Cotización BCV", _uiState.value.imageCaption)
        showBanner("Texto de cotización BCV copiado")
    }

    fun copyDifferential() {
        WhatsAppDispatcher.copyToClipboard(
            appContext,
            "Diferencial",
            _uiState.value.differentialMessage,
        )
        showBanner("Mensaje de diferencial copiado")
    }

    fun openBcvWebsite() {
        WhatsAppDispatcher.openExternalUrl(appContext, RateScraperService.BCV_URL)
    }

    fun openBinanceWebsite() {
        WhatsAppDispatcher.openExternalUrl(appContext, RateScraperService.BINANCE_WEB_URL)
    }

    fun handleQuickAction(action: String?) {
        if (action.isNullOrBlank()) return
        val state = _uiState.value
        if (state.isLoading || !state.hasValidRates) {
            pendingQuickAction = action
            return
        }
        when (action) {
            DailyScheduleManager.QUICK_ACTION_SEND_IMAGE -> sendStep1ImageToWhatsApp()
            DailyScheduleManager.QUICK_ACTION_SEND_DIFF -> sendStep2DifferentialToWhatsApp()
        }
    }

    fun updateTargetGroup(name: String) {
        viewModelScope.launch {
            settingsStore.updateTargetGroup(name)
        }
    }

    fun updatePreferredApp(app: PreferredWhatsAppApp) {
        viewModelScope.launch {
            settingsStore.updatePreferredApp(app)
        }
    }

    fun updateScheduleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.updateScheduleEnabled(enabled)
            val updated = settingsStore.getSettingsSnapshot()
            if (enabled) {
                DailyScheduleManager.scheduleNextDailyWindow(appContext, updated)
                showBanner("Preparación automática activada (${DailyScheduleManager.formatWindowLabel(updated)})")
            } else {
                DailyScheduleManager.cancelScheduledWindow(appContext)
                showBanner("Programación automática pausada")
            }
        }
    }

    fun startFullAutoSendNow() {
        viewModelScope.launch {
            refreshSystemPermissionsStatus()
            val current = _uiState.value
            if (!current.automationReadiness.accessibilityEnabled) {
                showBanner("Primero activa 'Tasa Bisu · Auto-Envío WhatsApp' en Accesibilidad.")
                openAccessibilitySettings()
                return@launch
            }
            if (current.settings.targetGroupName.isBlank()) {
                showBanner("Escribe el nombre exacto de tu grupo destino en Programar.")
                return@launch
            }
            if (current.cardShareUri == null) {
                regenerateCard()
            }
            val ready = _uiState.value
            val uri = ready.cardShareUri
            if (uri == null) {
                showBanner("Aún se está generando la imagen BCV. Intenta en un segundo.")
                return@launch
            }

            val started = AutoSendOrchestrator.startAutoSend(
                context = appContext,
                targetGroupName = ready.settings.targetGroupName,
                preferredApp = ready.settings.preferredApp,
                imageUri = uri,
                imageCaption = ready.imageCaption,
                differentialMessage = ready.differentialMessage,
                singleMessageMode = ready.settings.singleMessageMode,
                bcvRate = ready.bcvRateRaw,
                differentialPercent = ready.differential.percent,
            )
            if (started) {
                showBanner("Iniciando envío automático a \"${ready.settings.targetGroupName}\"...")
            }
        }
    }

    fun cancelAutoSend() {
        AutoSendOrchestrator.cancelJob()
    }

    fun updateSingleMessageMode(singleMessage: Boolean) {
        viewModelScope.launch {
            settingsStore.updateSingleMessageMode(singleMessage)
            showBanner(
                if (singleMessage) {
                    "Formato: 1 solo mensaje (Foto + BCV + Diferencial)"
                } else {
                    "Formato: 2 mensajes separados"
                },
            )
        }
    }

    fun updateAutoSendEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.updateAutoSendEnabled(enabled)
            showBanner(
                if (enabled) {
                    "Envío 100% automático activado para las ${DailyScheduleManager.formatClockTime(_uiState.value.settings.exactSendHour, _uiState.value.settings.exactSendMinute)}"
                } else {
                    "Envío desatendido pausado (solo notificación)"
                },
            )
        }
    }

    fun updateExactSendTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            settingsStore.updateExactSendTime(hour, minute)
            val updated = settingsStore.getSettingsSnapshot()
            if (updated.scheduleEnabled) {
                DailyScheduleManager.scheduleNextDailyWindow(appContext, updated)
            }
            showBanner("Hora de auto-envío actualizada: ${DailyScheduleManager.formatClockTime(hour, minute)}")
        }
    }

    fun openAccessibilitySettings() {
        runCatching {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        }
    }

    fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${appContext.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(intent)
            }
        }
    }

    fun requestIgnoreBatteryOptimizations() {
        runCatching {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${appContext.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        }.onFailure {
            runCatching {
                val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(fallback)
            }
        }
    }

    fun openAppDetailsSettings() {
        runCatching {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${appContext.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        }
    }

    fun updateScheduleWindow(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        viewModelScope.launch {
            settingsStore.updateScheduleWindow(startHour, startMinute, endHour, endMinute)
            val updated = settingsStore.getSettingsSnapshot()
            if (updated.scheduleEnabled) {
                DailyScheduleManager.scheduleNextDailyWindow(appContext, updated)
            }
            showBanner("Horario actualizado: ${DailyScheduleManager.formatWindowLabel(updated)}")
        }
    }

    fun updateDisplayTimeLabel(label: String) {
        viewModelScope.launch {
            settingsStore.updateDisplayTimeLabel(label)
        }
    }

    fun updateCustomTemplateUri(uri: String) {
        viewModelScope.launch {
            settingsStore.updateCustomTemplateUri(uri)
            showBanner(
                if (uri.isBlank()) {
                    "Plantilla oficial Bisutería 888 restaurada"
                } else {
                    "Plantilla personalizada aplicada"
                },
            )
        }
    }

    fun updateDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.updateDarkTheme(enabled)
        }
    }

    fun sendTestMorningNotification() {
        val state = _uiState.value
        val posted = DailyScheduleManager.postReadyNotification(
            context = appContext,
            bcvRate = state.bcvRateRaw.takeIf { it > 0.0 } ?: 922.56,
            differentialPercent = state.differential.percent,
            targetGroupName = state.settings.targetGroupName,
        )
        if (posted) {
            showBanner("Notificación de prueba enviada. Revisa tu barra de notificaciones.")
        } else {
            showBanner("Concede el permiso de notificaciones para recibir el aviso de las 8:50–8:59 AM.")
        }
    }

    fun updateGitHubRepoSlug(slug: String) {
        viewModelScope.launch {
            val trimmed = slug.trim()
            settingsStore.updateGitHubRepoSlug(trimmed)
            val normalized = GitHubUpdateChecker.normalizeRepoSlug(trimmed).orEmpty()
            GitHubUpdateCheckWorker.syncPeriodicSchedule(
                context = appContext,
                enabled = _uiState.value.settings.autoCheckUpdates,
                repoSlug = normalized.ifBlank {
                    BuildConfig.DEFAULT_GITHUB_REPO.ifBlank { DispatchSettings.DEFAULT_GITHUB_REPO }
                },
            )
        }
    }

    fun updateAutoCheckUpdates(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.updateAutoCheckUpdates(enabled)
            val repo = _uiState.value.settings.githubRepoSlug
                .ifBlank { BuildConfig.DEFAULT_GITHUB_REPO }
                .ifBlank { DispatchSettings.DEFAULT_GITHUB_REPO }
            GitHubUpdateCheckWorker.syncPeriodicSchedule(
                context = appContext,
                enabled = enabled,
                repoSlug = repo,
            )
            showBanner(
                if (enabled) {
                    "Aviso automático de actualizaciones de GitHub activado"
                } else {
                    "Revisión automática de actualizaciones pausada"
                },
            )
        }
    }

    fun checkForGitHubUpdates(manual: Boolean = true) {
        if (_uiState.value.isCheckingUpdate) return
        val settings = _uiState.value.settings
        val rawRepo = settings.githubRepoSlug
            .ifBlank { BuildConfig.DEFAULT_GITHUB_REPO }
            .ifBlank { DispatchSettings.DEFAULT_GITHUB_REPO }

        if (rawRepo.isBlank()) {
            if (manual) {
                showBanner("Escribe tu repositorio de GitHub (ej. bisumarketing888-hub/Bisu-Tasa) para buscar actualizaciones.")
            }
            return
        }
        if (!RateScraperService.isNetworkConnected(appContext)) {
            if (manual) {
                showBanner("Sin conexión Wi-Fi/internet para consultar GitHub.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCheckingUpdate = true,
                    updateStatusText = if (manual) "Consultando GitHub Releases..." else it.updateStatusText,
                )
            }

            val result = updateChecker.fetchLatestRelease(rawRepo)
            result.onSuccess { release ->
                val isNewer = GitHubUpdateChecker.isNewerVersion(
                    latestTag = release.tagName,
                    installedVersion = BuildConfig.VERSION_NAME,
                    acknowledgedTag = if (manual) "" else settings.acknowledgedReleaseTag,
                )
                if (isNewer) {
                    _uiState.update {
                        it.copy(
                            isCheckingUpdate = false,
                            availableUpdate = release,
                            updateStatusText = "Nueva versión disponible: ${release.tagName} (instalada: v${BuildConfig.VERSION_NAME})",
                        )
                    }
                    val lastNotified = settingsStore.getLastNotifiedReleaseTag()
                    if (manual || !lastNotified.equals(release.tagName, ignoreCase = true)) {
                        val posted = DailyScheduleManager.postUpdateAvailableNotification(
                            context = appContext,
                            releaseTag = release.tagName,
                            releaseNotes = release.releaseNotes,
                            releaseHtmlUrl = release.htmlUrl,
                        )
                        if (posted) {
                            settingsStore.markReleaseNotified(release.tagName)
                        }
                    }
                    if (manual) {
                        showBanner("¡Actualización ${release.tagName} disponible en GitHub!")
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isCheckingUpdate = false,
                            availableUpdate = null,
                            updateStatusText = "Al día · Versión instalada v${BuildConfig.VERSION_NAME} (última en GitHub: ${release.tagName})",
                        )
                    }
                    if (manual) {
                        showBanner("Tasa Bisu ya está en la última versión (${release.tagName}).")
                    }
                }
            }.onFailure { err ->
                val message = err.localizedMessage ?: "No se pudo consultar GitHub Releases."
                _uiState.update {
                    it.copy(
                        isCheckingUpdate = false,
                        updateStatusText = if (manual) message else it.updateStatusText,
                    )
                }
                if (manual) {
                    showBanner(message)
                }
            }
        }
    }

    fun openAvailableUpdateInBrowser(url: String) {
        WhatsAppDispatcher.openExternalUrl(appContext, url)
    }

    fun dismissAvailableUpdate(tag: String) {
        viewModelScope.launch {
            settingsStore.acknowledgeReleaseTag(tag)
            _uiState.update { it.copy(availableUpdate = null) }
        }
    }

    fun deleteHistoryEntry(dateKey: String) {
        viewModelScope.launch {
            historyDao.deleteByDateKey(dateKey)
        }
    }

    fun dismissBanner() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }

    private fun showBanner(message: String) {
        _uiState.update { it.copy(statusBannerMessage = message) }
    }
}
