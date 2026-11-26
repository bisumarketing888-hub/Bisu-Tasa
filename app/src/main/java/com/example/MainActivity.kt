package com.example

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.scheduler.DailyScheduleManager
import com.example.ui.TasaBisuViewModel
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ScheduleSettingsScreen
import com.example.ui.theme.TasaBisuTheme

private enum class AppTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
) {
    HOME(
        labelRes = R.string.nav_home,
        selectedIcon = Icons.Rounded.CurrencyExchange,
        unselectedIcon = Icons.Outlined.CurrencyExchange,
        testTag = "nav_tab_home",
    ),
    HISTORY(
        labelRes = R.string.nav_history,
        selectedIcon = Icons.Rounded.History,
        unselectedIcon = Icons.Outlined.History,
        testTag = "nav_tab_history",
    ),
    SCHEDULE(
        labelRes = R.string.nav_schedule,
        selectedIcon = Icons.Rounded.Schedule,
        unselectedIcon = Icons.Outlined.Schedule,
        testTag = "nav_tab_schedule",
    ),
}

class MainActivity : ComponentActivity() {

    private val viewModel: TasaBisuViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        enableEdgeToEdge()
        handleIntentQuickAction(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            TasaBisuTheme(darkTheme = uiState.settings.darkThemeEnabled) {
                TasaBisuAppRoot(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSystemPermissionsStatus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentQuickAction(intent)
    }

    private fun handleIntentQuickAction(intent: Intent?) {
        val quickAction = intent?.getStringExtra(DailyScheduleManager.EXTRA_QUICK_ACTION)
        if (!quickAction.isNullOrBlank()) {
            intent.removeExtra(DailyScheduleManager.EXTRA_QUICK_ACTION)
            viewModel.handleQuickAction(quickAction)
        }
    }
}

@Composable
private fun TasaBisuAppRoot(viewModel: TasaBisuViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var currentTab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Return to Home tab on back press from secondary tabs
    BackHandler(enabled = currentTab != AppTab.HOME) {
        currentTab = AppTab.HOME
    }

    LaunchedEffect(uiState.statusBannerMessage) {
        val msg = uiState.statusBannerMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissBanner()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                    ) {
                        AppTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes),
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                modifier = Modifier.testTag(tab.testTag),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        AppTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes),
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag),
                            )
                        }
                    }
                }

                when (currentTab) {
                    AppTab.HOME -> HomeScreen(
                        uiState = uiState,
                        onRefresh = { viewModel.refreshRates() },
                        onStartFullAutoSend = { viewModel.startFullAutoSendNow() },
                        onCancelAutoSend = { viewModel.cancelAutoSend() },
                        onOpenAccessibilitySettings = { viewModel.openAccessibilitySettings() },
                        onSendStep1 = { viewModel.sendStep1ImageToWhatsApp() },
                        onSendStep2 = { viewModel.sendStep2DifferentialToWhatsApp() },
                        onCopyCaption = { viewModel.copyCaption() },
                        onCopyDifferential = { viewModel.copyDifferential() },
                        onSaveGallery = { viewModel.saveCardToGallery() },
                        onSelectBinanceOffer = { viewModel.selectBinanceOffer(it) },
                        onApplyManualRates = { bcv, binance, date ->
                            viewModel.applyManualRates(bcv, binance, date)
                        },
                        onOpenBcvWeb = { viewModel.openBcvWebsite() },
                        onOpenBinanceWeb = { viewModel.openBinanceWebsite() },
                        onOpenGitHubUpdate = { viewModel.openAvailableUpdateInBrowser(it) },
                        onDismissGitHubUpdate = { viewModel.dismissAvailableUpdate(it) },
                        modifier = Modifier.weight(1f),
                    )

                    AppTab.HISTORY -> HistoryScreen(
                        history = uiState.history,
                        onDeleteEntry = { viewModel.deleteHistoryEntry(it) },
                        modifier = Modifier.weight(1f),
                    )

                    AppTab.SCHEDULE -> ScheduleSettingsScreen(
                        settings = uiState.settings,
                        whatsAppAvailability = uiState.whatsAppAvailability,
                        automationReadiness = uiState.automationReadiness,
                        onUpdateTargetGroup = { viewModel.updateTargetGroup(it) },
                        onUpdatePreferredApp = { viewModel.updatePreferredApp(it) },
                        onToggleSchedule = { viewModel.updateScheduleEnabled(it) },
                        onToggleAutoSend = { viewModel.updateAutoSendEnabled(it) },
                        onToggleSingleMessageMode = { viewModel.updateSingleMessageMode(it) },
                        onUpdateExactSendTime = { h, m -> viewModel.updateExactSendTime(h, m) },
                        onUpdateDisplayTimeLabel = { viewModel.updateDisplayTimeLabel(it) },
                        onUpdateCustomTemplateUri = { viewModel.updateCustomTemplateUri(it) },
                        onToggleDarkTheme = { viewModel.updateDarkTheme(it) },
                        onOpenAccessibilitySettings = { viewModel.openAccessibilitySettings() },
                        onOpenExactAlarmSettings = { viewModel.openExactAlarmSettings() },
                        onRequestBatteryUnrestricted = { viewModel.requestIgnoreBatteryOptimizations() },
                        onOpenAppDetailsSettings = { viewModel.openAppDetailsSettings() },
                        onTestAutoSendNow = { viewModel.startFullAutoSendNow() },
                        onTestNotification = { viewModel.sendTestMorningNotification() },
                        availableUpdate = uiState.availableUpdate,
                        isCheckingUpdate = uiState.isCheckingUpdate,
                        updateStatusText = uiState.updateStatusText,
                        onUpdateGitHubRepo = { viewModel.updateGitHubRepoSlug(it) },
                        onUpdateGitHubToken = { viewModel.updateGitHubToken(it) },
                        onToggleAutoCheckUpdates = { viewModel.updateAutoCheckUpdates(it) },
                        onCheckGitHubUpdatesNow = { viewModel.checkForGitHubUpdates(manual = true) },
                        onOpenGitHubUpdate = { viewModel.openAvailableUpdateInBrowser(it) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
