package com.example.data.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.domain.RateCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object QuoteCardGenerator {

    private const val CARDS_DIR = "generated_cards"
    private const val GOLD_DATE_COLOR = 0xFFDFB859.toInt()
    private const val PURE_BLACK = 0xFF000000.toInt()

    /**
     * Generates the updated BCV quote Bitmap by taking the bundled Bisutería 888 template
     * (or a custom template chosen by the user) and seamlessly rendering the current date
     * and BCV rate in the exact template typography and coordinates.
     */
    suspend fun generateQuoteBitmap(
        context: Context,
        bcvRate: Double,
        dateFormatted: String,
        customTemplateUri: String = "",
    ): Bitmap = withContext(Dispatchers.Default) {
        val baseBitmap = loadBaseTemplate(context, customTemplateUri)
        val mutable = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
        if (baseBitmap != mutable) {
            baseBitmap.recycle()
        }

        val canvas = Canvas(mutable)
        val w = mutable.width.toFloat()
        val h = mutable.height.toFloat()

        // Sample pure black background inside the template so masking blends invisibly
        val sampleX = (w * 0.50f).toInt().coerceIn(0, mutable.width - 1)
        val sampleY = (h * 0.43f).toInt().coerceIn(0, mutable.height - 1)
        val maskColor = if (customTemplateUri.isBlank()) PURE_BLACK else mutable.getPixel(sampleX, sampleY)

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = maskColor
            style = Paint.Style.FILL
        }

        // 1. Cleanly cover old date band (25/11/2026) without touching 9:00AM-6:00PM or Cotización
        val dateMaskRect = RectF(
            w * 0.30f,
            h * 0.236f,
            w * 0.70f,
            h * 0.285f,
        )
        canvas.drawRect(dateMaskRect, maskPaint)

        // 2. Cleanly cover old rate band (Bs.922,56) inside inner gold border lines
        val rateMaskRect = RectF(
            w * 0.098f,
            h * 0.438f,
            w * 0.902f,
            h * 0.638f,
        )
        canvas.drawRect(rateMaskRect, maskPaint)

        // Load bundled Montserrat font for faithful geometric match
        val montserratBase = runCatching {
            ResourcesCompat.getFont(context, R.font.montserrat)
        }.getOrNull() ?: Typeface.SANS_SERIF
        val boldTypeface = Typeface.create(montserratBase, Typeface.BOLD)

        // 3. Draw updated date in champagne gold centered in dateMaskRect
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GOLD_DATE_COLOR
            typeface = boldTypeface
            textAlign = Paint.Align.CENTER
            textSize = h * 0.044f
            letterSpacing = 0.02f
        }
        val dateBounds = Rect()
        datePaint.getTextBounds(dateFormatted, 0, dateFormatted.length, dateBounds)
        val dateCenterY = (dateMaskRect.top + dateMaskRect.bottom) / 2f
        val dateBaseline = dateCenterY - dateBounds.exactCenterY()
        canvas.drawText(dateFormatted, w * 0.50f, dateBaseline, datePaint)

        // 4. Draw updated BCV rate (e.g., "Bs.922,56") auto-fitted inside the gold frame
        val rateText = if (bcvRate > 0.0) RateCalculator.formatCardBcvText(bcvRate) else "Bs.--,--"
        val maxRateWidth = w * 0.785f
        val ratePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = boldTypeface
            textAlign = Paint.Align.CENTER
            textSize = h * 0.165f
            letterSpacing = -0.02f
        }
        // Shrink text size smoothly if the number is wider (e.g., 4+ digits like Bs.1.245,80)
        while (ratePaint.measureText(rateText) > maxRateWidth && ratePaint.textSize > h * 0.07f) {
            ratePaint.textSize -= 2f
        }

        val rateBounds = Rect()
        ratePaint.getTextBounds(rateText, 0, rateText.length, rateBounds)
        val rateCenterY = (rateMaskRect.top + rateMaskRect.bottom) / 2f
        val rateBaseline = rateCenterY - rateBounds.exactCenterY()
        canvas.drawText(rateText, w * 0.50f, rateBaseline, ratePaint)

        mutable
    }

    /**
     * Saves the generated Bitmap into the FileProvider-backed cache directory and returns its content:// URI.
     */
    suspend fun saveForSharing(
        context: Context,
        bitmap: Bitmap,
        dateKey: String = RateCalculator.formatDateKey(),
    ): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, CARDS_DIR).apply { mkdirs() }
        // Keep cache clean by removing older generated cards
        dir.listFiles()?.forEach { existing ->
            if (existing.name.startsWith("cotizacion_bcv_")) {
                existing.delete()
            }
        }
        val outFile = File(dir, "cotizacion_bcv_${dateKey.replace("-", "")}.png")
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
        }
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outFile,
        )
    }

    /**
     * Saves a copy of the generated quote card into the device's Pictures/TasaBisu album via MediaStore.
     */
    suspend fun saveToGallery(
        context: Context,
        bitmap: Bitmap,
        dateKey: String = RateCalculator.formatDateKey(),
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = "Cotizacion_BCV_${dateKey.replace("-", "")}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/TasaBisu",
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("No se pudo crear el archivo en la galería")

            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            } ?: error("No se pudo escribir la imagen")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        }
    }

    private fun loadBaseTemplate(context: Context, customTemplateUri: String): Bitmap {
        if (customTemplateUri.isNotBlank()) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(customTemplateUri))?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()?.let { return it }
        }

        val options = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeResource(
            context.resources,
            R.drawable.bcv_template_base,
            options,
        ) ?: Bitmap.createBitmap(873, 875, Bitmap.Config.ARGB_8888)
    }
}
