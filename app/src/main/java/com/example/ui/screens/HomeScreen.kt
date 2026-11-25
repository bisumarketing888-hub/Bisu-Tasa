package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.domain.BinanceOffer
import com.example.domain.RateCalculator
import com.example.scheduler.DailyScheduleManager
import com.example.ui.TasaBisuUiState
import com.example.ui.theme.BrightGold
import com.example.ui.theme.BurnishedGold
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.DiffAmber
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MontserratFontFamily
import com.example.ui.theme.WhatsAppGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: TasaBisuUiState,
    onRefresh: () -> Unit,
    onStartFullAutoSend: () -> Unit,
    onCancelAutoSend: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onSendStep1: () -> Unit,
    onSendStep2: () -> Unit,
    onCopyCaption: () -> Unit,
    onCopyDifferential: () -> Unit,
    onSaveGallery: () -> Unit,
    onSelectBinanceOffer: (Int) -> Unit,
    onApplyManualRates: (String, String, String) -> Unit,
    onOpenBcvWeb: () -> Unit,
    onOpenBinanceWeb: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showOffersSheet by remember { mutableStateOf(false) }
    var showZoomDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                TopBrandHeader(
                    uiState = uiState,
                    onRefresh = onRefresh,
                    onOpenEdit = { showEditDialog = true },
                )
            }

            // Live error strip if network fetch had issues
            if (uiState.bcvError != null || uiState.binanceError != null) {
                item {
                    ErrorNoticeCard(
                        bcvError = uiState.bcvError,
                        binanceError = uiState.binanceError,
                        onRetry = onRefresh,
                        onEditManual = { showEditDialog = true },
                    )
                }
            }

            // Hero Generated Card Preview (exact Bisutería 888 template with live rate & date)
            item {
                HeroQuoteCardSection(
                    uiState = uiState,
                    onSaveGallery = onSaveGallery,
                    onZoomClick = { showZoomDialog = true },
                )
            }

            // Live Rates Pair: BCV Official + Binance P2P #1 Banesco Sell
            item {
                LiveRatesRow(
                    uiState = uiState,
                    onOpenBcvWeb = onOpenBcvWeb,
                    onOpenOffers = { showOffersSheet = true },
                    onEditManual = { showEditDialog = true },
                )
            }

            // Calculator Verification Strip (matching Windows Calculator screenshot)
            item {
                DifferentialFormulaCard(uiState = uiState)
            }

            // Guided & Full Hands-Free WhatsApp Business Dispatch Panel
            item {
                WhatsAppDispatchPanel(
                    uiState = uiState,
                    onStartFullAutoSend = onStartFullAutoSend,
                    onCancelAutoSend = onCancelAutoSend,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onSendStep1 = onSendStep1,
                    onSendStep2 = onSendStep2,
                    onCopyCaption = onCopyCaption,
                    onCopyDifferential = onCopyDifferential,
                )
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }

    if (showEditDialog) {
        ManualRatesDialog(
            initialBcv = if (uiState.bcvRateRaw > 0.0) {
                RateCalculator.formatVenezuelanNumber(
                    RateCalculator.roundToTwoDecimals(uiState.bcvRateRaw),
                    2,
                )
            } else {
                ""
            },
            initialBinance = if (uiState.activeBinanceRate > 0.0) {
                RateCalculator.formatVenezuelanNumber(uiState.activeBinanceRate, 2)
            } else {
                ""
            },
            initialDate = uiState.quoteDateFormatted,
            onDismiss = { showEditDialog = false },
            onSave = { bcv, binance, date ->
                showEditDialog = false
                onApplyManualRates(bcv, binance, date)
            },
        )
    }

    if (showOffersSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOffersSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            BinanceOffersSheetContent(
                offers = uiState.binanceOffers,
                selectedIndex = uiState.selectedOfferIndex,
                onSelect = { index ->
                    onSelectBinanceOffer(index)
                    showOffersSheet = false
                },
                onOpenBinanceWeb = {
                    showOffersSheet = false
                    onOpenBinanceWeb()
                },
            )
        }
    }

    if (showZoomDialog && uiState.cardBitmap != null) {
        Dialog(
            onDismissRequest = { showZoomDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .clickable { showZoomDialog = false }
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = uiState.cardBitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.cd_quote_card),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Fit,
                )
                IconButton(
                    onClick = { showZoomDialog = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 24.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Cerrar vista previa",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBrandHeader(
    uiState: TasaBisuUiState,
    onRefresh: () -> Unit,
    onOpenEdit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (uiState.settings.scheduleEnabled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = if (uiState.settings.scheduleEnabled) {
                                DailyScheduleManager.formatWindowLabel(uiState.settings)
                            } else {
                                stringResource(R.string.status_schedule_paused)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            val updatedText = if (uiState.lastFetchedMillis > 0L) {
                "Actualizado ${RateCalculator.formatHourMinute(uiState.lastFetchedMillis)} · ${uiState.quoteDateFormatted}"
            } else {
                "Cotización diaria BCV y Binance P2P"
            }
            Text(
                text = updatedText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(
                onClick = onOpenEdit,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("edit_rates_button"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.cd_edit_rates),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onRefresh,
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        CircleShape,
                    )
                    .testTag("refresh_rates_button"),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.cd_refresh_rates),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroQuoteCardSection(
    uiState: TasaBisuUiState,
    onSaveGallery: () -> Unit,
    onZoomClick: () -> Unit,
) {
    val goldBorderBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                ChampagneGold.copy(alpha = 0.7f),
                BurnishedGold.copy(alpha = 0.25f),
                BrightGold.copy(alpha = 0.65f),
            ),
        )
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(1.2.dp, goldBorderBrush),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Imagen lista para enviar",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onZoomClick,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("zoom_card_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ZoomIn,
                            contentDescription = "Ampliar imagen",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(
                        onClick = onSaveGallery,
                        enabled = uiState.cardBitmap != null,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("save_gallery_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = stringResource(R.string.cd_save_image),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
                    .clickable(enabled = uiState.cardBitmap != null) { onZoomClick() }
                    .testTag("generated_quote_card_image"),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = uiState.cardBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_quote_card),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.5.dp,
                        )
                        Text(
                            text = "Generando imagen BCV...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveRatesRow(
    uiState: TasaBisuUiState,
    onOpenBcvWeb: () -> Unit,
    onOpenOffers: () -> Unit,
    onEditManual: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // BCV Official Card
        Card(
            onClick = onEditManual,
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .weight(1f)
                .testTag("bcv_rate_card"),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.label_bcv_official),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = "Abrir bcv.org.ve",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onOpenBcvWeb() },
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (uiState.bcvRateRaw > 0.0) {
                        "Bs. ${RateCalculator.formatVenezuelanNumber(RateCalculator.roundToTwoDecimals(uiState.bcvRateRaw), 2)}"
                    } else {
                        "Bs. --,--"
                    },
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = uiState.bcvSourceName.ifBlank { "bcv.org.ve" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Binance P2P #1 Banesco Sell Card
        Card(
            onClick = onOpenOffers,
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .weight(1f)
                .testTag("binance_rate_card"),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Binance · Banesco",
                        style = MaterialTheme.typography.labelMedium,
                        color = DiffAmber,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = Icons.Rounded.Storefront,
                        contentDescription = stringResource(R.string.cd_select_offer),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (uiState.activeBinanceRate > 0.0) {
                        "Bs. ${RateCalculator.formatVenezuelanNumber(uiState.activeBinanceRate, 2)}"
                    } else {
                        "Bs. --,--"
                    },
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = uiState.binanceMerchantDisplay
                        .takeIf { it.isNotBlank() }
                        ?.let { "#${uiState.selectedOfferIndex + 1} $it" }
                        ?: "Tocar para ver anuncios",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DifferentialFormulaCard(uiState: TasaBisuUiState) {
    val diff = uiState.differential

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("differential_formula_card"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Calculate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Cálculo del Diferencial",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                val equation = if (uiState.hasValidRates) {
                    "${RateCalculator.formatVenezuelanNumber(diff.binanceUsed, 2)} ÷ ${
                        RateCalculator.formatVenezuelanNumber(diff.bcvUsed, 2)
                    }"
                } else {
                    "Binance ÷ BCV"
                }
                Text(
                    text = equation,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${diff.integerPart},",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f),
                                RoundedCornerShape(6.dp),
                            )
                            .border(
                                1.5.dp,
                                MaterialTheme.colorScheme.tertiary,
                                RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = diff.highlightedTwoDigits,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    Text(
                        text = diff.remainingDecimals,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "${diff.percent}%",
                        fontFamily = MontserratFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        text = "DIFERENCIAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WhatsAppDispatchPanel(
    uiState: TasaBisuUiState,
    onStartFullAutoSend: () -> Unit,
    onCancelAutoSend: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onSendStep1: () -> Unit,
    onSendStep2: () -> Unit,
    onCopyCaption: () -> Unit,
    onCopyDifferential: () -> Unit,
) {
    val autoStatus = uiState.autoSendStatus
    val readiness = uiState.automationReadiness

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Envío Automático · WhatsApp Business",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Groups,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Grupo: ${uiState.settings.targetGroupName.ifBlank { "Sin asignar" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (readiness.accessibilityEnabled) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    },
                ) {
                    Text(
                        text = if (readiness.accessibilityEnabled) {
                            "Auto-Envío ✓"
                        } else {
                            "Activar Auto"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (readiness.accessibilityEnabled) {
                            WhatsAppGreen
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        },
                        modifier = Modifier
                            .clickable {
                                if (!readiness.accessibilityEnabled) {
                                    onOpenAccessibilitySettings()
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            // Full Unattended 2-Message Auto-Send Hero CTA
            if (autoStatus.isRunning) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = WhatsAppGreen,
                            )
                            Text(
                                text = autoStatus.statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        TextButton(onClick = onCancelAutoSend) {
                            Text("Detener", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            } else {
                Button(
                    onClick = onStartFullAutoSend,
                    enabled = uiState.cardBitmap != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WhatsAppGreen,
                        contentColor = Color(0xFF04200E),
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("start_full_auto_send_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (readiness.accessibilityEnabled) {
                            "Enviar Todo Automático (Foto + Dif. ${uiState.differential.percent}%)"
                        } else {
                            "Activar Envío Automático en WhatsApp Business"
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = "Vista previa y envío manual por paso:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Smart prompt between Step 1 and Step 2 (dual-message mode only)
            AnimatedVisibility(
                visible = !uiState.settings.singleMessageMode && uiState.waitingForStep2Prompt,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "¡Paso 1 enviado! Toca ahora el Paso 2 para mandar el diferencial.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }

            // STEP 1 (or Single Combined Message)
            DispatchStepBlock(
                stepNumber = if (uiState.settings.singleMessageMode) "1 MENSAJE" else "PASO 1",
                title = if (uiState.settings.singleMessageMode) {
                    "Foto + BCV + Diferencial"
                } else {
                    "Foto + Descripción BCV"
                },
                previewText = uiState.imageCaption,
                isSentToday = uiState.imageSentToday,
                onCopy = onCopyCaption,
                copyContentDescription = stringResource(R.string.cd_copy_caption),
                copyTestTag = "copy_caption_button",
            ) {
                OutlinedButton(
                    onClick = onSendStep1,
                    enabled = uiState.cardBitmap != null,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("send_step1_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Image,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.settings.singleMessageMode) {
                            "Envío manual (Foto + Texto completo)"
                        } else if (uiState.imageSentToday) {
                            "Reenviar Foto + BCV a WhatsApp"
                        } else {
                            "1. Enviar Foto + Cotización BCV"
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            // STEP 2: Differential Message (shown only when user chooses 2 separate messages)
            if (!uiState.settings.singleMessageMode) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                DispatchStepBlock(
                    stepNumber = "PASO 2",
                    title = "Mensaje de Diferencial",
                    previewText = uiState.differentialMessage,
                    isSentToday = uiState.diffSentToday,
                    onCopy = onCopyDifferential,
                    copyContentDescription = stringResource(R.string.cd_copy_diff),
                    copyTestTag = "copy_diff_button",
                ) {
                    Button(
                        onClick = onSendStep2,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("send_step2_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.diffSentToday) {
                                "Reenviar Diferencial ${uiState.differential.percent}%"
                            } else {
                                "2. Enviar Diferencial ${uiState.differential.percent}%"
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DispatchStepBlock(
    stepNumber: String,
    title: String,
    previewText: String,
    isSentToday: Boolean,
    onCopy: () -> Unit,
    copyContentDescription: String,
    copyTestTag: String,
    actionButton: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = stepNumber,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSentToday) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Enviado hoy",
                        tint = WhatsAppGreen,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Listo",
                        style = MaterialTheme.typography.labelSmall,
                        color = WhatsAppGreen,
                    )
                }
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag(copyTestTag),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = copyContentDescription,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // Message Bubble Preview
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = previewText.replace("*", ""),
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }

        actionButton()
    }
}

@Composable
private fun ErrorNoticeCard(
    bcvError: String?,
    binanceError: String?,
    onRetry: () -> Unit,
    onEditManual: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Aviso de conexión",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            if (bcvError != null) {
                Text(
                    text = "• BCV: $bcvError",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            if (binanceError != null) {
                Text(
                    text = "• Binance P2P: $binanceError",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRetry) {
                    Text("Reintentar", color = MaterialTheme.colorScheme.onErrorContainer)
                }
                TextButton(onClick = onEditManual) {
                    Text("Ingresar manual", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

@Composable
private fun BinanceOffersSheetContent(
    offers: List<BinanceOffer>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onOpenBinanceWeb: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Binance P2P · Vender USDT",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Fiat VES · Banco Banesco · Se toma el #1 automáticamente",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onOpenBinanceWeb) {
                Text("Web")
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = "Abrir Binance P2P",
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        if (offers.isEmpty()) {
            Text(
                text = "Aún no se cargaron anuncios de Binance P2P. Toca Actualizar en la pantalla principal.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 20.dp),
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                itemsIndexed(offers) { idx, offer ->
                    val selected = idx == selectedIndex
                    Card(
                        onClick = { onSelect(idx) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                        ),
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = "#${idx + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = offer.advertiserName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = "${offer.monthOrders} órdenes · ${
                                        RateCalculator.formatVenezuelanNumber(offer.finishRatePercent, 1)
                                    }% completado",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = "Bs ${RateCalculator.formatVenezuelanNumber(offer.price, 3)}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualRatesDialog(
    initialBcv: String,
    initialBinance: String,
    initialDate: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var bcvText by remember { mutableStateOf(initialBcv) }
    var binanceText by remember { mutableStateOf(initialBinance) }
    var dateText by remember { mutableStateOf(initialDate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Ajuste manual de tasas",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Útil si deseas probar una cotización o si bcv.org.ve se encuentra temporalmente caído.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = bcvText,
                    onValueChange = { bcvText = it },
                    label = { Text("Tasa BCV (ej. 922,56)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_bcv_input"),
                )
                OutlinedTextField(
                    value = binanceText,
                    onValueChange = { binanceText = it },
                    label = { Text("Tasa Binance Banesco (ej. 1104,11)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_binance_input"),
                )
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("Fecha en imagen (dd/MM/yyyy)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_date_input"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(bcvText, binanceText, dateText) },
                modifier = Modifier.testTag("save_manual_rates_button"),
            ) {
                Text("Aplicar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}
