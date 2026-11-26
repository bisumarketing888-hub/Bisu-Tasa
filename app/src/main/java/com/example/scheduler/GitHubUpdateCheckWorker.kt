package com.example.scheduler

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.BuildConfig
import com.example.data.local.SettingsStore
import com.example.data.network.GitHubUpdateChecker
import java.util.concurrent.TimeUnit

class GitHubUpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settingsStore = SettingsStore(applicationContext)
        val settings = settingsStore.getSettingsSnapshot()

        if (!settings.autoCheckUpdates) return Result.success()
        val repoSlug = settings.githubRepoSlug.ifBlank { BuildConfig.DEFAULT_GITHUB_REPO }
        if (repoSlug.isBlank()) return Result.success()

        val checker = GitHubUpdateChecker()
        val release = checker.fetchLatestRelease(repoSlug, settings.githubToken).getOrNull()
            ?: return Result.success()

        val isNewer = GitHubUpdateChecker.isNewerVersion(
            latestTag = release.tagName,
            installedVersion = BuildConfig.VERSION_NAME,
            acknowledgedTag = settings.acknowledgedReleaseTag,
        )
        if (!isNewer) return Result.success()

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
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "tasa_bisu_github_update_check"

        fun syncPeriodicSchedule(context: Context, enabled: Boolean, repoSlug: String) {
            val workManager = WorkManager.getInstance(context)
            if (!enabled || repoSlug.isBlank()) {
                workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<GitHubUpdateCheckWorker>(
                6,
                TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
