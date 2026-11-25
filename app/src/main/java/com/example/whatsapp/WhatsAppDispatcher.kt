package com.example.whatsapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.example.domain.PreferredWhatsAppApp

data class WhatsAppAvailability(
    val isBusinessInstalled: Boolean,
    val isStandardInstalled: Boolean,
) {
    val anyInstalled: Boolean get() = isBusinessInstalled || isStandardInstalled
}

sealed class DispatchOutcome {
    data class Launched(val appLabel: String) : DispatchOutcome()
    data class LaunchedChooser(val reason: String) : DispatchOutcome()
    data class Failed(val message: String) : DispatchOutcome()
}

object WhatsAppDispatcher {

    fun checkAvailability(context: Context): WhatsAppAvailability {
        return WhatsAppAvailability(
            isBusinessInstalled = isPackageInstalled(
                context,
                PreferredWhatsAppApp.WHATSAPP_BUSINESS.packageName,
            ),
            isStandardInstalled = isPackageInstalled(
                context,
                PreferredWhatsAppApp.WHATSAPP_STANDARD.packageName,
            ),
        )
    }

    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun resolveTargetPackage(
        context: Context,
        preferred: PreferredWhatsAppApp,
    ): PreferredWhatsAppApp? {
        if (isPackageInstalled(context, preferred.packageName)) {
            return preferred
        }
        val alternate = if (preferred == PreferredWhatsAppApp.WHATSAPP_BUSINESS) {
            PreferredWhatsAppApp.WHATSAPP_STANDARD
        } else {
            PreferredWhatsAppApp.WHATSAPP_BUSINESS
        }
        return if (isPackageInstalled(context, alternate.packageName)) alternate else null
    }

    /**
     * Launches WhatsApp Business (or fallback) with the generated BCV PNG image and its exact caption.
     */
    fun sendImageWithCaption(
        context: Context,
        imageUri: Uri,
        caption: String,
        preferred: PreferredWhatsAppApp,
    ): DispatchOutcome {
        return try {
            val resolved = resolveTargetPackage(context, preferred)
            val baseIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                putExtra(Intent.EXTRA_TEXT, caption)
                clipData = ClipData.newRawUri("cotizacion_bcv", imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (resolved != null) {
                baseIntent.setPackage(resolved.packageName)
                context.startActivity(baseIntent)
                DispatchOutcome.Launched(resolved.displayName)
            } else {
                val chooser = Intent.createChooser(
                    baseIntent,
                    "Enviar imagen BCV a WhatsApp Business",
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(chooser)
                DispatchOutcome.LaunchedChooser(
                    "WhatsApp Business no está instalado en este dispositivo; se abrió el selector.",
                )
            }
        } catch (e: Exception) {
            DispatchOutcome.Failed("No se pudo abrir WhatsApp: ${e.localizedMessage ?: "error"}")
        }
    }

    /**
     * Launches WhatsApp Business (or fallback) with the differential text message.
     */
    fun sendTextMessage(
        context: Context,
        message: String,
        preferred: PreferredWhatsAppApp,
    ): DispatchOutcome {
        return try {
            val resolved = resolveTargetPackage(context, preferred)
            val baseIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (resolved != null) {
                baseIntent.setPackage(resolved.packageName)
                context.startActivity(baseIntent)
                DispatchOutcome.Launched(resolved.displayName)
            } else {
                val chooser = Intent.createChooser(
                    baseIntent,
                    "Enviar diferencial a WhatsApp Business",
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
                DispatchOutcome.LaunchedChooser(
                    "WhatsApp Business no está instalado; se abrió el selector.",
                )
            }
        } catch (e: Exception) {
            DispatchOutcome.Failed("No se pudo abrir WhatsApp: ${e.localizedMessage ?: "error"}")
        }
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun openExternalUrl(context: Context, url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
