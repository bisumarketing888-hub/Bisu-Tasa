package com.example.scheduler

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.DispatchSettings
import com.example.domain.RateCalculator
import java.util.Calendar
import java.util.Locale

object DailyScheduleManager {

    const val ACTION_DAILY_QUOTE_FETCH = "com.example.action.DAILY_QUOTE_FETCH"
    const val EXTRA_QUICK_ACTION = "extra_quick_action"
    const val QUICK_ACTION_SEND_IMAGE = "quick_send_image"
    const val QUICK_ACTION_SEND_DIFF = "quick_send_diff"

    const val NOTIFICATION_CHANNEL_ID = "daily_bcv_quote_channel"
    private const val ALARM_REQUEST_CODE = 8881
    private const val NOTIFICATION_ID = 8882
    private const val COMPLETED_NOTIFICATION_ID = 8883

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Schedules the daily fetch + unattended WhatsApp Business auto-send at the configured
     * exact time (e.g., 8:55 AM, configurable between 8:50 and 8:59 AM). Uses
     * setExactAndAllowWhileIdle when permitted so it fires reliably on the dot before 9:00 AM,
     * falling back to setWindow otherwise.
     */
    fun scheduleNextDailyWindow(
        context: Context,
        settings: DispatchSettings,
        forceNextDay: Boolean = false,
    ): Long {
        cancelScheduledWindow(context)
        if (!settings.scheduleEnabled) return 0L

        val now = Calendar.getInstance()
        val targetStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, settings.exactSendHour)
            set(Calendar.MINUTE, settings.exactSendMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (forceNextDay || !targetStart.after(now)) {
            targetStart.add(Calendar.DAY_OF_YEAR, 1)
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildAlarmPendingIntent(context)

        if (canScheduleExactAlarms(context)) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                targetStart.timeInMillis,
                pendingIntent,
            )
        } else {
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                targetStart.timeInMillis,
                5 * 60_000L,
                pendingIntent,
            )
        }
        return targetStart.timeInMillis
    }

    fun cancelScheduledWindow(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(buildAlarmPendingIntent(context))
    }

    private fun buildAlarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, DailyScheduleReceiver::class.java).apply {
            action = ACTION_DAILY_QUOTE_FETCH
        }
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun postReadyNotification(
        context: Context,
        bcvRate: Double,
        differentialPercent: Int,
        targetGroupName: String,
        customPrefix: String = "",
    ): Boolean {
        ensureNotificationChannel(context)
        if (!hasNotificationPermission(context)) return false

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            101,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val sendImageIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_QUICK_ACTION, QUICK_ACTION_SEND_IMAGE)
        }
        val sendImagePending = PendingIntent.getActivity(
            context,
            102,
            sendImageIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val sendDiffIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_QUICK_ACTION, QUICK_ACTION_SEND_DIFF)
        }
        val sendDiffPending = PendingIntent.getActivity(
            context,
            103,
            sendDiffIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val formattedBcv = RateCalculator.formatVenezuelanNumber(
            RateCalculator.roundToTwoDecimals(bcvRate),
            decimals = 2,
        )
        val groupHint = targetGroupName.ifBlank { "WhatsApp Business" }

        val title = "${customPrefix}Cotización lista · BCV Bs. $formattedBcv"
        val body = "Diferencial $differentialPercent% listo para \"$groupHint\" antes de las 9:00 AM."

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .addAction(0, "1. Enviar Foto", sendImagePending)
            .addAction(0, "2. Diferencial $differentialPercent%", sendDiffPending)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun postAutoSendCompletedNotification(
        context: Context,
        bcvRate: Double,
        differentialPercent: Int,
        targetGroupName: String,
    ) {
        ensureNotificationChannel(context)
        if (!hasNotificationPermission(context)) return

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            104,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val formattedBcv = RateCalculator.formatVenezuelanNumber(
            RateCalculator.roundToTwoDecimals(bcvRate),
            decimals = 2,
        )
        val title = "Enviado automáticamente a \"$targetGroupName\""
        val body = "Foto BCV Bs. $formattedBcv y Diferencial $differentialPercent% enviados con éxito."

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(COMPLETED_NOTIFICATION_ID, notification)
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun formatWindowLabel(settings: DispatchSettings): String {
        return "Auto ${formatClockTime(settings.exactSendHour, settings.exactSendMinute)}"
    }

    fun formatClockTime(hour24: Int, minute: Int): String {
        val amPm = if (hour24 >= 12) "PM" else "AM"
        val hour12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        return String.format(Locale.US, "%d:%02d %s", hour12, minute, amPm)
    }
}
