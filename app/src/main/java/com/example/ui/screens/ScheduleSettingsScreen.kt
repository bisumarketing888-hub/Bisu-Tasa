package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.network.GitHubReleaseInfo
import com.example.domain.AutomationReadiness
import com.example.domain.DispatchSettings
import com.example.domain.PreferredWhatsAppApp
import com.example.scheduler.DailyScheduleManager
import com.example.ui.theme.WhatsAppGreen
import com.example.whatsapp.WhatsAppAvailability

private data class ExactSendPreset(
    val label: String,
    val hour: Int,
    val minute: Int,
)

private val SEND_TIME_PRESETS = listOf(
    ExactSendPreset("8:55 AM", 8, 55),
    ExactSendPreset("8:58 AM", 8, 58),
    ExactSendPreset("8:59 AM", 8, 59),
    ExactSendPreset("9:00 AM", 9, 0),
)

@Composable
fun ScheduleSettingsScreen(
    settings: DispatchSettings,
    whatsAppAvailability: WhatsAppAvailability,
    automationReadiness: AutomationReadiness,
    onUpdateTargetGroup: (String) -> Unit,
    onUpdatePreferredApp: (PreferredWhatsAppApp) -> Unit,
    onToggleSchedule: (Boolean) -> Unit,
    onToggleAutoSend: (Boolean) -> Unit,
    onToggleSingleMessageMode: (Boolean) -> Unit,
    onUpdateExactSendTime: (Int, Int) -> Unit,
    onUpdateDisplayTimeLabel: (String) -> Unit,
    onUpdateCustomTemplateUri: (String) -> Unit,
    onToggleDarkTheme: (Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onRequestBatteryUnrestricted: () -> Unit,
    onOpenAppDetailsSettings: () -> Unit,
    onTestAutoSendNow: () -> Unit,
    onTestNotification: () -> Unit,
    availableUpdate: GitHubReleaseInfo?,
    isCheckingUpdate: Boolean,
    updateStatusText: String?,
    onUpdateGitHubRepo: (String) -> Unit,
    onUpdateGitHubToken: (String) -> Unit,
    onToggleAutoCheckUpdates: (Boolean) -> Unit,
    onCheckGitHubUpdatesNow: () -> Unit,
    onOpenGitHubUpdate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var groupInput by remember(settings.targetGroupName) { mutableStateOf(settings.targetGroupName) }
    var captionTimeInput by remember(settings.displayTimeLabel) {
        mutableStateOf(settings.displayTimeLabel)
    }
    var githubRepoInput by remember(settings.githubRepoSlug) {
        mutableStateOf(settings.githubRepoSlug)
    }
    var githubTokenInput by remember(settings.githubToken) {
        mutableStateOf(settings.githubToken)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        onToggleSchedule(true)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            onUpdateCustomTemplateUri(uri.toString())
        }
    }

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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Automatización Total",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "Configúralo una sola vez en tu teléfono de empresa y funcionará solo cada mañana.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 1. One-Time Device Setup Checklist (Accessibility + Battery + Exact Alarms)
            item {
                SettingsSectionCard(
                    title = "Configuración Única (${automationReadiness.completedStepsCount}/4)",
                    subtitle = "Concede estos permisos una sola vez para que envíe sin tocar el teléfono.",
                    icon = Icons.Rounded.AccessibilityNew,
                ) {
                    ChecklistActionRow(
                        title = "1. Accesibilidad · Auto-Envío WhatsApp",
                        subtitle = "Busca tu grupo, envía la foto + BCV y luego el diferencial",
                        isReady = automationReadiness.accessibilityEnabled,
                        actionLabel = "Activar",
                        onAction = onOpenAccessibilitySettings,
                        testTag = "setup_accessibility_button",
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ChecklistActionRow(
                        title = "2. Sin restricción de batería",
                        subtitle = "Evita que Android pause la app durante la madrugada",
                        isReady = automationReadiness.batteryUnrestricted,
                        actionLabel = "Permitir",
                        onAction = onRequestBatteryUnrestricted,
                        testTag = "setup_battery_button",
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ChecklistActionRow(
                        title = "3. Alarmas exactas (8:50–8:59 AM)",
                        subtitle = "Dispara puntualmente a la hora elegida antes de las 9:00 AM",
                        isReady = automationReadiness.exactAlarmGranted,
                        actionLabel = "Activar",
                        onAction = onOpenExactAlarmSettings,
                        testTag = "setup_exact_alarm_button",
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Dedicated device screen-unlock note
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LockOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "No necesitas dejar la pantalla encendida ni la app abierta toda la noche. Solo deja el bloqueo en 'Ninguno' (sin PIN/patrón) y activa en Xiaomi HyperOS: Inicio automático en segundo plano + Otros permisos → Mostrar ventanas emergentes en segundo plano.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onOpenAppDetailsSettings,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_hyperos_app_details_button"),
                    ) {
                        Text("Abrir permisos HyperOS (Inicio automático y ventanas)")
                    }

                    Button(
                        onClick = onTestAutoSendNow,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WhatsAppGreen,
                            contentColor = Color(0xFF04200E),
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("test_auto_send_now_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Probar envío automático ahora")
                    }
                }
            }

            // 2. Target WhatsApp Business Group Card
            item {
                SettingsSectionCard(
                    title = "Grupo Destino en WhatsApp Business",
                    subtitle = "Escribe el nombre EXACTO del grupo tal como aparece en tu WhatsApp Business.",
                    icon = Icons.Rounded.Groups,
                ) {
                    OutlinedTextField(
                        value = groupInput,
                        onValueChange = {
                            groupInput = it
                            onUpdateTargetGroup(it)
                        },
                        label = { Text("Nombre exacto del grupo destino") },
                        placeholder = { Text("Ej. Diferencial / Cotización Bisu") },
                        supportingText = {
                            Text("La app buscará este nombre exacto en WhatsApp Business y enviará ambos mensajes allí.")
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("target_group_input"),
                    )

                    Text(
                        text = "Aplicación destino:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PreferredWhatsAppApp.entries.forEach { app ->
                            val selected = settings.preferredApp == app
                            val installed = when (app) {
                                PreferredWhatsAppApp.WHATSAPP_BUSINESS ->
                                    whatsAppAvailability.isBusinessInstalled
                                PreferredWhatsAppApp.WHATSAPP_STANDARD ->
                                    whatsAppAvailability.isStandardInstalled
                            }
                            FilterChip(
                                selected = selected,
                                onClick = { onUpdatePreferredApp(app) },
                                label = {
                                    Text(
                                        text = if (installed) "${app.displayName} ✓" else app.displayName,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("preferred_app_${app.name.lowercase()}"),
                            )
                        }
                    }
                }
            }

            // 3. Daily Exact Schedule (8:50 – 8:59 AM)
            item {
                SettingsSectionCard(
                    title = "Horario Diario (Antes de las 9:00 AM)",
                    subtitle = "Ejecuta la descarga de BCV + Binance y envía ambos mensajes todos los días.",
                    icon = Icons.Rounded.Alarm,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Programación diaria activa",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Hora de disparo: ${
                                    DailyScheduleManager.formatClockTime(
                                        settings.exactSendHour,
                                        settings.exactSendMinute,
                                    )
                                }",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Switch(
                            checked = settings.scheduleEnabled,
                            onCheckedChange = { wantEnabled ->
                                if (wantEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    )
                                } else {
                                    onToggleSchedule(wantEnabled)
                                }
                            },
                            modifier = Modifier.testTag("schedule_enabled_switch"),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enviar sin intervención (Accesibilidad)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Si está desactivado, solo enviará una notificación de aviso",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.autoSendEnabled,
                            onCheckedChange = onToggleAutoSend,
                            modifier = Modifier.testTag("auto_send_enabled_switch"),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enviar todo en 1 solo mensaje (Recomendado)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Foto + BCV + 1 línea en blanco + Diferencial juntos (más rápido y fiable)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.singleMessageMode,
                            onCheckedChange = onToggleSingleMessageMode,
                            modifier = Modifier.testTag("single_message_mode_switch"),
                        )
                    }

                    Text(
                        text = "Hora exacta de envío diario:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SEND_TIME_PRESETS.forEach { preset ->
                            val isSelected =
                                settings.exactSendHour == preset.hour &&
                                    settings.exactSendMinute == preset.minute
                            FilterChip(
                                selected = isSelected,
                                onClick = { onUpdateExactSendTime(preset.hour, preset.minute) },
                                label = {
                                    Text(
                                        text = preset.label,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    OutlinedTextField(
                        value = captionTimeInput,
                        onValueChange = {
                            captionTimeInput = it
                            onUpdateDisplayTimeLabel(it)
                        },
                        label = { Text("Hora impresa en la descripción (ej. 9:00 am)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("caption_time_input"),
                    )

                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(
                                    Manifest.permission.POST_NOTIFICATIONS,
                                )
                            }
                            onTestNotification()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_notification_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Probar notificación de respaldo")
                    }
                }
            }

            // 4. Visual Template Card
            item {
                SettingsSectionCard(
                    title = "Plantilla de Cotización",
                    subtitle = "Usa tu diseño de Bisutería 888 o carga una nueva imagen cuadrada.",
                    icon = Icons.Rounded.Image,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pick_template_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Image,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cambiar fondo")
                        }

                        if (settings.customTemplateUri.isNotBlank()) {
                            OutlinedButton(
                                onClick = { onUpdateCustomTemplateUri("") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("reset_template_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Restore,
                                    contentDescription = "Restaurar plantilla",
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Original")
                            }
                        }
                    }
                }
            }

            // 5. GitHub Releases Updates
            item {
                SettingsSectionCard(
                    title = "Actualizaciones desde GitHub",
                    subtitle = "Versión instalada: v${BuildConfig.VERSION_NAME} · Avisa automáticamente al subir cambios.",
                    icon = Icons.Rounded.CloudSync,
                ) {
                    OutlinedTextField(
                        value = githubRepoInput,
                        onValueChange = {
                            githubRepoInput = it
                            onUpdateGitHubRepo(it)
                        },
                        label = { Text("Repositorio GitHub (usuario/repositorio o URL)") },
                        placeholder = { Text("Ej. bisumarketing888-hub/Bisu-Tasa") },
                        supportingText = {
                            Text("Si tu repositorio es público deja el token vacío. Si es privado, pega un Personal Access Token abajo.")
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_repo_input"),
                    )

                    OutlinedTextField(
                        value = githubTokenInput,
                        onValueChange = {
                            githubTokenInput = it
                            onUpdateGitHubToken(it)
                        },
                        label = { Text("Token de GitHub (opcional, solo repo privado)") },
                        placeholder = { Text("github_pat_... o ghp_...") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_token_input"),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Notificar actualizaciones automáticamente",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Revisa GitHub por Wi-Fi y muestra notificación cuando haya una nueva APK",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.autoCheckUpdates,
                            onCheckedChange = onToggleAutoCheckUpdates,
                            modifier = Modifier.testTag("auto_check_updates_switch"),
                        )
                    }

                    OutlinedButton(
                        onClick = onCheckGitHubUpdatesNow,
                        enabled = !isCheckingUpdate,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("check_github_updates_button"),
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Buscando en GitHub...")
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.SystemUpdate,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Buscar actualización ahora")
                        }
                    }

                    if (availableUpdate != null) {
                        Button(
                            onClick = { onOpenGitHubUpdate(availableUpdate.htmlUrl) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("settings_open_github_release_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Descargar ${availableUpdate.tagName} en GitHub")
                        }
                    }

                    if (!updateStatusText.isNullOrBlank()) {
                        Text(
                            text = updateStatusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // 6. Theme toggle
            item {
                SettingsSectionCard(
                    title = "Apariencia",
                    subtitle = "Personaliza el estilo visual de la aplicación.",
                    icon = Icons.Rounded.DarkMode,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tema Obsidiana y Oro",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Modo oscuro inspirado en tu marca",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.darkThemeEnabled,
                            onCheckedChange = onToggleDarkTheme,
                            modifier = Modifier.testTag("dark_theme_switch"),
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun ChecklistActionRow(
    title: String,
    subtitle: String,
    isReady: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = if (isReady) {
                    Icons.Rounded.CheckCircle
                } else {
                    Icons.Rounded.RadioButtonUnchecked
                },
                contentDescription = if (isReady) "Configurado" else "Pendiente",
                tint = if (isReady) WhatsAppGreen else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (isReady) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = "Listo",
                    style = MaterialTheme.typography.labelSmall,
                    color = WhatsAppGreen,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        } else {
            TextButton(
                onClick = onAction,
                modifier = Modifier.testTag(testTag),
            ) {
                Text(actionLabel, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content()
        }
    }
}
