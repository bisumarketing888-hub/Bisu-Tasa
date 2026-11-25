package com.example.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.example.data.local.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class DailyScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settingsStore = SettingsStore(context.applicationContext)
                val settings = settingsStore.getSettingsSnapshot()

                when (action) {
                    Intent.ACTION_BOOT_COMPLETED -> {
                        if (settings.scheduleEnabled) {
                            DailyScheduleManager.scheduleNextDailyWindow(
                                context = context.applicationContext,
                                settings = settings,
                                forceNextDay = false,
                            )
                        }
                    }

                    DailyScheduleManager.ACTION_DAILY_QUOTE_FETCH -> {
                        // Enqueue expedited network-aware worker (runs immediately over Wi-Fi or mobile data)
                        val constraints = Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                        val workRequest = OneTimeWorkRequestBuilder<DailyQuoteWorker>()
                            .setConstraints(constraints)
                            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                            .setBackoffCriteria(
                                BackoffPolicy.EXPONENTIAL,
                                15,
                                TimeUnit.SECONDS,
                            )
                            .build()
                        WorkManager.getInstance(context.applicationContext)
                            .enqueueUniqueWork(
                                "daily_bcv_quote_work",
                                ExistingWorkPolicy.REPLACE,
                                workRequest,
                            )

                        // Schedule tomorrow's alarm automatically
                        if (settings.scheduleEnabled) {
                            DailyScheduleManager.scheduleNextDailyWindow(
                                context = context.applicationContext,
                                settings = settings,
                                forceNextDay = true,
                            )
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
