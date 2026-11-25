package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import com.example.domain.CachedQuoteData
import com.example.domain.DispatchSettings
import com.example.domain.PreferredWhatsAppApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.tasaBisuDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "tasa_bisu_prefs",
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val TARGET_GROUP = stringPreferencesKey("target_group_name")
        val PREFERRED_PACKAGE = stringPreferencesKey("preferred_wa_package")
        val SCHEDULE_ENABLED = booleanPreferencesKey("schedule_enabled")
        val AUTO_SEND_ENABLED = booleanPreferencesKey("auto_send_enabled")
        val SINGLE_MESSAGE_MODE = booleanPreferencesKey("single_message_mode")
        val EXACT_SEND_HOUR = intPreferencesKey("exact_send_hour")
        val EXACT_SEND_MINUTE = intPreferencesKey("exact_send_minute")
        val WINDOW_START_HOUR = intPreferencesKey("window_start_hour")
        val WINDOW_START_MINUTE = intPreferencesKey("window_start_minute")
        val WINDOW_END_HOUR = intPreferencesKey("window_end_hour")
        val WINDOW_END_MINUTE = intPreferencesKey("window_end_minute")
        val DISPLAY_TIME_LABEL = stringPreferencesKey("display_time_label")
        val CUSTOM_TEMPLATE_URI = stringPreferencesKey("custom_template_uri")
        val DARK_THEME = booleanPreferencesKey("dark_theme_enabled")
        val GITHUB_REPO_SLUG = stringPreferencesKey("github_repo_slug")
        val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
        val ACKNOWLEDGED_RELEASE_TAG = stringPreferencesKey("acknowledged_release_tag")
        val LAST_NOTIFIED_RELEASE_TAG = stringPreferencesKey("last_notified_release_tag")

        // Cached last fetched rates
        val CACHED_BCV_RATE = doublePreferencesKey("cached_bcv_rate")
        val CACHED_BCV_SOURCE = stringPreferencesKey("cached_bcv_source")
        val CACHED_BCV_DATE = stringPreferencesKey("cached_bcv_date")
        val CACHED_BINANCE_RATE = doublePreferencesKey("cached_binance_rate")
        val CACHED_BINANCE_MERCHANT = stringPreferencesKey("cached_binance_merchant")
        val CACHED_QUOTE_DATE = stringPreferencesKey("cached_quote_date")
        val CACHED_FETCHED_AT = longPreferencesKey("cached_fetched_at")
        val IMAGE_SENT_DATE_KEY = stringPreferencesKey("image_sent_date_key")
        val DIFF_SENT_DATE_KEY = stringPreferencesKey("diff_sent_date_key")
    }

    val settingsFlow: Flow<DispatchSettings> = context.tasaBisuDataStore.data.map { prefs ->
        val pkg = prefs[Keys.PREFERRED_PACKAGE]
            ?: PreferredWhatsAppApp.WHATSAPP_BUSINESS.packageName
        val preferred = PreferredWhatsAppApp.entries.firstOrNull { it.packageName == pkg }
            ?: PreferredWhatsAppApp.WHATSAPP_BUSINESS

        DispatchSettings(
            targetGroupName = prefs[Keys.TARGET_GROUP] ?: "Grupo Diferencial Bisu",
            preferredApp = preferred,
            scheduleEnabled = prefs[Keys.SCHEDULE_ENABLED] ?: true,
            autoSendEnabled = prefs[Keys.AUTO_SEND_ENABLED] ?: true,
            singleMessageMode = prefs[Keys.SINGLE_MESSAGE_MODE] ?: true,
            exactSendHour = prefs[Keys.EXACT_SEND_HOUR] ?: 9,
            exactSendMinute = prefs[Keys.EXACT_SEND_MINUTE] ?: 0,
            windowStartHour = prefs[Keys.WINDOW_START_HOUR] ?: 8,
            windowStartMinute = prefs[Keys.WINDOW_START_MINUTE] ?: 50,
            windowEndHour = prefs[Keys.WINDOW_END_HOUR] ?: 8,
            windowEndMinute = prefs[Keys.WINDOW_END_MINUTE] ?: 59,
            displayTimeLabel = prefs[Keys.DISPLAY_TIME_LABEL] ?: "9:00 am",
            customTemplateUri = prefs[Keys.CUSTOM_TEMPLATE_URI] ?: "",
            darkThemeEnabled = prefs[Keys.DARK_THEME] ?: true,
            githubRepoSlug = prefs[Keys.GITHUB_REPO_SLUG]?.ifBlank { BuildConfig.DEFAULT_GITHUB_REPO }
                ?: BuildConfig.DEFAULT_GITHUB_REPO,
            autoCheckUpdates = prefs[Keys.AUTO_CHECK_UPDATES] ?: true,
            acknowledgedReleaseTag = prefs[Keys.ACKNOWLEDGED_RELEASE_TAG] ?: "",
        )
    }

    val cachedQuoteFlow: Flow<CachedQuoteData> = context.tasaBisuDataStore.data.map { prefs ->
        CachedQuoteData(
            bcvRate = prefs[Keys.CACHED_BCV_RATE] ?: 0.0,
            bcvSource = prefs[Keys.CACHED_BCV_SOURCE] ?: "",
            bcvDateLabel = prefs[Keys.CACHED_BCV_DATE] ?: "",
            binanceRate = prefs[Keys.CACHED_BINANCE_RATE] ?: 0.0,
            binanceMerchant = prefs[Keys.CACHED_BINANCE_MERCHANT] ?: "",
            quoteDateFormatted = prefs[Keys.CACHED_QUOTE_DATE] ?: "",
            fetchedAtMillis = prefs[Keys.CACHED_FETCHED_AT] ?: 0L,
            imageSentDateKey = prefs[Keys.IMAGE_SENT_DATE_KEY] ?: "",
            diffSentDateKey = prefs[Keys.DIFF_SENT_DATE_KEY] ?: "",
        )
    }

    suspend fun getSettingsSnapshot(): DispatchSettings = settingsFlow.first()

    suspend fun getCachedQuoteSnapshot(): CachedQuoteData = cachedQuoteFlow.first()

    suspend fun updateTargetGroup(name: String) {
        context.tasaBisuDataStore.edit { it[Keys.TARGET_GROUP] = name.trim() }
    }

    suspend fun updatePreferredApp(app: PreferredWhatsAppApp) {
        context.tasaBisuDataStore.edit { it[Keys.PREFERRED_PACKAGE] = app.packageName }
    }

    suspend fun updateScheduleEnabled(enabled: Boolean) {
        context.tasaBisuDataStore.edit { it[Keys.SCHEDULE_ENABLED] = enabled }
    }

    suspend fun updateAutoSendEnabled(enabled: Boolean) {
        context.tasaBisuDataStore.edit { it[Keys.AUTO_SEND_ENABLED] = enabled }
    }

    suspend fun updateSingleMessageMode(singleMessage: Boolean) {
        context.tasaBisuDataStore.edit { it[Keys.SINGLE_MESSAGE_MODE] = singleMessage }
    }

    suspend fun updateExactSendTime(hour: Int, minute: Int) {
        context.tasaBisuDataStore.edit {
            it[Keys.EXACT_SEND_HOUR] = hour
            it[Keys.EXACT_SEND_MINUTE] = minute
            it[Keys.WINDOW_START_HOUR] = hour
            it[Keys.WINDOW_START_MINUTE] = minute
            val endTotal = hour * 60 + minute + 4
            it[Keys.WINDOW_END_HOUR] = (endTotal / 60) % 24
            it[Keys.WINDOW_END_MINUTE] = endTotal % 60
        }
    }

    suspend fun updateScheduleWindow(
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
    ) {
        context.tasaBisuDataStore.edit {
            it[Keys.WINDOW_START_HOUR] = startHour
            it[Keys.WINDOW_START_MINUTE] = startMinute
            it[Keys.WINDOW_END_HOUR] = endHour
            it[Keys.WINDOW_END_MINUTE] = endMinute
        }
    }

    suspend fun updateDisplayTimeLabel(label: String) {
        context.tasaBisuDataStore.edit {
            it[Keys.DISPLAY_TIME_LABEL] = label.trim().ifEmpty { "9:00 am" }
        }
    }

    suspend fun updateCustomTemplateUri(uri: String) {
        context.tasaBisuDataStore.edit { it[Keys.CUSTOM_TEMPLATE_URI] = uri }
    }

    suspend fun updateDarkTheme(enabled: Boolean) {
        context.tasaBisuDataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun saveFetchedSnapshot(
        bcvRate: Double,
        bcvSource: String,
        bcvDateLabel: String,
        binanceRate: Double,
        binanceMerchant: String,
        quoteDateFormatted: String,
        fetchedAtMillis: Long,
    ) {
        context.tasaBisuDataStore.edit {
            it[Keys.CACHED_BCV_RATE] = bcvRate
            it[Keys.CACHED_BCV_SOURCE] = bcvSource
            it[Keys.CACHED_BCV_DATE] = bcvDateLabel
            it[Keys.CACHED_BINANCE_RATE] = binanceRate
            it[Keys.CACHED_BINANCE_MERCHANT] = binanceMerchant
            it[Keys.CACHED_QUOTE_DATE] = quoteDateFormatted
            it[Keys.CACHED_FETCHED_AT] = fetchedAtMillis
        }
    }

    suspend fun markImageSentForDate(dateKey: String) {
        context.tasaBisuDataStore.edit { it[Keys.IMAGE_SENT_DATE_KEY] = dateKey }
    }

    suspend fun markDiffSentForDate(dateKey: String) {
        context.tasaBisuDataStore.edit { it[Keys.DIFF_SENT_DATE_KEY] = dateKey }
    }

    suspend fun updateGitHubRepoSlug(slug: String) {
        context.tasaBisuDataStore.edit { it[Keys.GITHUB_REPO_SLUG] = slug.trim() }
    }

    suspend fun updateAutoCheckUpdates(enabled: Boolean) {
        context.tasaBisuDataStore.edit { it[Keys.AUTO_CHECK_UPDATES] = enabled }
    }

    suspend fun acknowledgeReleaseTag(tag: String) {
        context.tasaBisuDataStore.edit { it[Keys.ACKNOWLEDGED_RELEASE_TAG] = tag.trim() }
    }

    suspend fun getLastNotifiedReleaseTag(): String {
        return context.tasaBisuDataStore.data.first()[Keys.LAST_NOTIFIED_RELEASE_TAG] ?: ""
    }

    suspend fun markReleaseNotified(tag: String) {
        context.tasaBisuDataStore.edit { it[Keys.LAST_NOTIFIED_RELEASE_TAG] = tag.trim() }
    }
}
