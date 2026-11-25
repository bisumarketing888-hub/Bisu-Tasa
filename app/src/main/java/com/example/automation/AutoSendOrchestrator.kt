package com.example.automation

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.example.data.local.AppDatabase
import com.example.data.local.SettingsStore
import com.example.domain.PreferredWhatsAppApp
import com.example.domain.RateCalculator
import com.example.scheduler.DailyScheduleManager
import com.example.whatsapp.WhatsAppDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AutoSendPhase {
    IDLE,
    STEP1_SELECTING_GROUP,
    STEP1_CONFIRMING_MEDIA,
    STEP1_SENT_WAITING_STEP2,
    STEP2_SENDING_DIFF_IN_CHAT,
    STEP2_SELECTING_GROUP_FALLBACK,
    STEP2_CONFIRMING_TEXT_FALLBACK,
}

data class AutoSendJob(
    val targetGroupName: String,
    val targetPackage: String,
    val imageUri: Uri,
    val imageCaption: String,
    val differentialMessage: String,
    val singleMessageMode: Boolean,
    val bcvRate: Double,
    val differentialPercent: Int,
    val startedAtMillis: Long = System.currentTimeMillis(),
)

data class AutoSendStatus(
    val phase: AutoSendPhase = AutoSendPhase.IDLE,
    val statusText: String = "",
    val lastCompletedMillis: Long = 0L,
    val lastError: String? = null,
) {
    val isRunning: Boolean get() = phase != AutoSendPhase.IDLE
}

/**
 * Coordinates unattended WhatsApp Business sending:
 * - Single-message mode (recommended): sends Image + BCV + 1 blank line + Differential in 1 atomic message.
 * - Dual-message mode: sends Image + BCV first, then Differential text right after in the same group.
 */
object AutoSendOrchestrator {

    private const val JOB_TIMEOUT_MS = 45_000L
    private const val STEP2_DELAY_MS = 2_200L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(Dispatchers.IO)

    private val _status = MutableStateFlow(AutoSendStatus())
    val status: StateFlow<AutoSendStatus> = _status.asStateFlow()

    @Volatile
    var currentJob: AutoSendJob? = null
        private set

    @Volatile
    var lastActionTimestampMs: Long = 0L

    @Volatile
    var searchTriggeredInCurrentPhase: Boolean = false

    @Volatile
    var searchTextTypedInCurrentPhase: Boolean = false

    @Volatile
    var groupRowClickedInCurrentPhase: Boolean = false

    @Volatile
    var groupRowTapAttempts: Int = 0

    @Volatile
    var lastRowTapTimeMs: Long = 0L

    @Volatile
    var step2TextTypedInChat: Boolean = false

    private var appContextRef: Context? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val timeoutRunnable = Runnable {
        if (currentJob != null) {
            val phaseHint = _status.value.statusText.ifBlank { "sin respuesta de WhatsApp Business" }
            failJob("Tiempo agotado ($phaseHint). Verifica que la pantalla esté desbloqueada y el nombre del grupo coincida.")
        }
    }

    fun isRunning(): Boolean = currentJob != null && _status.value.isRunning

    fun updateStatusText(text: String) {
        _status.value = _status.value.copy(statusText = text)
    }

    fun startAutoSend(
        context: Context,
        targetGroupName: String,
        preferredApp: PreferredWhatsAppApp,
        imageUri: Uri,
        imageCaption: String,
        differentialMessage: String,
        singleMessageMode: Boolean,
        bcvRate: Double,
        differentialPercent: Int,
    ): Boolean {
        val appCtx = context.applicationContext
        appContextRef = appCtx

        if (targetGroupName.isBlank()) {
            _status.value = _status.value.copy(
                lastError = "Configura primero el nombre exacto del grupo destino.",
            )
            return false
        }

        if (!WhatsAppAutoSendService.isServiceEnabled(appCtx)) {
            _status.value = _status.value.copy(
                lastError = "Activa primero el servicio de Accesibilidad de Tasa Bisu.",
            )
            return false
        }

        val resolvedPackage = when {
            WhatsAppDispatcher.isPackageInstalled(appCtx, preferredApp.packageName) ->
                preferredApp.packageName
            WhatsAppDispatcher.isPackageInstalled(
                appCtx,
                PreferredWhatsAppApp.WHATSAPP_BUSINESS.packageName,
            ) -> PreferredWhatsAppApp.WHATSAPP_BUSINESS.packageName
            WhatsAppDispatcher.isPackageInstalled(
                appCtx,
                PreferredWhatsAppApp.WHATSAPP_STANDARD.packageName,
            ) -> PreferredWhatsAppApp.WHATSAPP_STANDARD.packageName
            else -> {
                _status.value = _status.value.copy(
                    lastError = "WhatsApp Business no está instalado en este dispositivo.",
                )
                return false
            }
        }

        acquireShortWakeLock(appCtx)

        resetPhaseFlags()
        val job = AutoSendJob(
            targetGroupName = targetGroupName.trim(),
            targetPackage = resolvedPackage,
            imageUri = imageUri,
            imageCaption = imageCaption,
            differentialMessage = differentialMessage,
            singleMessageMode = singleMessageMode,
            bcvRate = bcvRate,
            differentialPercent = differentialPercent,
        )
        currentJob = job

        _status.value = AutoSendStatus(
            phase = AutoSendPhase.STEP1_SELECTING_GROUP,
            statusText = "Buscando grupo \"${job.targetGroupName}\"...",
            lastCompletedMillis = _status.value.lastCompletedMillis,
            lastError = null,
        )

        mainHandler.removeCallbacks(timeoutRunnable)
        mainHandler.postDelayed(timeoutRunnable, JOB_TIMEOUT_MS)

        val launched = launchStep1ImageIntent(appCtx, job)
        if (launched) {
            WhatsAppAutoSendService.instance?.startActivePollingLoop()
        }
        return launched
    }

    private fun launchStep1ImageIntent(context: Context, job: AutoSendJob): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                setPackage(job.targetPackage)
                putExtra(Intent.EXTRA_STREAM, job.imageUri)
                putExtra(Intent.EXTRA_TEXT, job.imageCaption)
                clipData = ClipData.newRawUri("cotizacion_bcv", job.imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            failJob("No se pudo iniciar WhatsApp Business: ${e.localizedMessage ?: "error"}")
            false
        }
    }

    fun onStep1GroupConfirmed() {
        val job = currentJob ?: return
        resetPhaseFlags()
        _status.value = _status.value.copy(
            phase = AutoSendPhase.STEP1_CONFIRMING_MEDIA,
            statusText = if (job.singleMessageMode) {
                "Vista previa lista · Enviando imagen + BCV + Dif. ${job.differentialPercent}%..."
            } else {
                "Paso 1/2: Enviando imagen y descripción BCV..."
            },
        )
        lastActionTimestampMs = System.currentTimeMillis()
    }

    fun onStep1ImageSent() {
        val job = currentJob ?: return
        val ctx = appContextRef ?: return
        resetPhaseFlags()

        ioScope.launch {
            val todayKey = RateCalculator.formatDateKey()
            SettingsStore(ctx).markImageSentForDate(todayKey)
            AppDatabase.getInstance(ctx).rateHistoryDao().markImageSent(todayKey)
        }

        if (job.singleMessageMode) {
            completeFullJob(job, ctx)
            return
        }

        _status.value = _status.value.copy(
            phase = AutoSendPhase.STEP1_SENT_WAITING_STEP2,
            statusText = "Paso 1 enviado ✓ · Enviando diferencial ${job.differentialPercent}%...",
        )
        lastActionTimestampMs = System.currentTimeMillis()

        mainHandler.postDelayed({
            if (currentJob == job) {
                resetPhaseFlags()
                _status.value = _status.value.copy(
                    phase = AutoSendPhase.STEP2_SENDING_DIFF_IN_CHAT,
                    statusText = "Paso 2/2: Escribiendo diferencial ${job.differentialPercent}%...",
                )
                WhatsAppAutoSendService.instance?.startActivePollingLoop()

                mainHandler.postDelayed({
                    if (currentJob == job &&
                        _status.value.phase == AutoSendPhase.STEP2_SENDING_DIFF_IN_CHAT &&
                        !step2TextTypedInChat
                    ) {
                        launchStep2FallbackIntent(ctx, job)
                    }
                }, 3_500L)
            }
        }, STEP2_DELAY_MS)
    }

    private fun launchStep2FallbackIntent(context: Context, job: AutoSendJob) {
        resetPhaseFlags()
        _status.value = _status.value.copy(
            phase = AutoSendPhase.STEP2_SELECTING_GROUP_FALLBACK,
            statusText = "Paso 2/2: Seleccionando grupo para diferencial...",
        )
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage(job.targetPackage)
                putExtra(Intent.EXTRA_TEXT, job.differentialMessage)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
            WhatsAppAutoSendService.instance?.startActivePollingLoop()
        } catch (e: Exception) {
            failJob("Error al enviar diferencial: ${e.localizedMessage ?: "error"}")
        }
    }

    fun onStep2GroupConfirmedFallback() {
        resetPhaseFlags()
        _status.value = _status.value.copy(
            phase = AutoSendPhase.STEP2_CONFIRMING_TEXT_FALLBACK,
            statusText = "Paso 2/2: Confirmando envío del diferencial...",
        )
        lastActionTimestampMs = System.currentTimeMillis()
    }

    fun onStep2DifferentialSent() {
        val job = currentJob ?: return
        val ctx = appContextRef ?: return
        completeFullJob(job, ctx)
    }

    private fun completeFullJob(job: AutoSendJob, ctx: Context) {
        mainHandler.removeCallbacks(timeoutRunnable)
        currentJob = null
        resetPhaseFlags()
        releaseWakeLock()
        WhatsAppAutoSendService.instance?.stopActivePollingLoop()

        val now = System.currentTimeMillis()
        _status.value = AutoSendStatus(
            phase = AutoSendPhase.IDLE,
            statusText = "Enviado automáticamente (${RateCalculator.formatHourMinute(now)})",
            lastCompletedMillis = now,
            lastError = null,
        )

        ioScope.launch {
            val todayKey = RateCalculator.formatDateKey()
            SettingsStore(ctx).markImageSentForDate(todayKey)
            SettingsStore(ctx).markDiffSentForDate(todayKey)
            val dao = AppDatabase.getInstance(ctx).rateHistoryDao()
            dao.markImageSent(todayKey)
            dao.markDifferentialSent(todayKey)
        }

        DailyScheduleManager.postAutoSendCompletedNotification(
            context = ctx,
            bcvRate = job.bcvRate,
            differentialPercent = job.differentialPercent,
            targetGroupName = job.targetGroupName,
        )
    }

    fun cancelJob() {
        mainHandler.removeCallbacks(timeoutRunnable)
        currentJob = null
        resetPhaseFlags()
        releaseWakeLock()
        WhatsAppAutoSendService.instance?.stopActivePollingLoop()
        _status.value = _status.value.copy(
            phase = AutoSendPhase.IDLE,
            statusText = "Envío automático cancelado",
        )
    }

    fun failJob(reason: String) {
        val job = currentJob
        val ctx = appContextRef
        mainHandler.removeCallbacks(timeoutRunnable)
        currentJob = null
        resetPhaseFlags()
        releaseWakeLock()
        WhatsAppAutoSendService.instance?.stopActivePollingLoop()

        _status.value = _status.value.copy(
            phase = AutoSendPhase.IDLE,
            statusText = "",
            lastError = reason,
        )

        if (ctx != null && job != null) {
            DailyScheduleManager.postReadyNotification(
                context = ctx,
                bcvRate = job.bcvRate,
                differentialPercent = job.differentialPercent,
                targetGroupName = job.targetGroupName,
                customPrefix = "Envío automático pausado: ",
            )
        }
    }

    private fun resetPhaseFlags() {
        searchTriggeredInCurrentPhase = false
        searchTextTypedInCurrentPhase = false
        groupRowClickedInCurrentPhase = false
        groupRowTapAttempts = 0
        lastRowTapTimeMs = 0L
        step2TextTypedInChat = false
    }

    @Suppress("DEPRECATION")
    private fun acquireShortWakeLock(context: Context) {
        releaseWakeLock()
        runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "TasaBisu:AutoSendWakeLock",
            ).apply {
                setReferenceCounted(false)
                acquire(50_000L)
            }
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            wakeLock?.let { if (it.isHeld) it.release() }
        }
        wakeLock = null
    }
}
