package com.idp.geostampcamera.stamp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.util.Log
import androidx.core.content.FileProvider
import com.idp.geostampcamera.data.repository.PhotoRepository
import com.idp.geostampcamera.util.AppDispatchers
import com.idp.geostampcamera.util.CoordinateFormatter
import com.idp.geostampcamera.util.DateFormatter
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class PhotoReportGenerator @Inject constructor(
    private val context: Context,
    private val dispatchers: AppDispatchers,
    private val photoRepository: PhotoRepository
) {

    suspend fun generate(photoId: Long): Result<android.net.Uri> = withContext(dispatchers.io) {
        runCatching { generateInternal(photoId) }
            .onFailure { Log.e(TAG, "PDF report generation failed", it) }
    }

    private suspend fun generateInternal(photoId: Long): android.net.Uri {
        val photo = photoRepository.get(photoId)
            ?: error("Photo id $photoId not found in Room")
        val path = photo.stampedPath ?: photo.originalPath
            ?: error("Photo $photoId has no image path")
        val sourceFile = File(path)
        require(sourceFile.exists()) { "Image file does not exist: $path" }

        val pdf = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create() // A4 72dpi
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas

            val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK; textSize = 20f; isFakeBoldText = true
            }
            val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.DKGRAY; textSize = 11f
            }
            canvas.drawText("${context.getString(com.idp.geostampcamera.R.string.app_name)} ${context.getString(com.idp.geostampcamera.R.string.camera_report)}", MARGIN, 48f, title)

            val lines = buildList<Pair<String, String>> {
                add(context.getString(com.idp.geostampcamera.R.string.camera_mode_photo) to DateFormatter.format(photo.timestampMillis, DateFormatter.FORMAT_FULL))
                if (photo.latitude != null && photo.longitude != null) {
                    add(context.getString(com.idp.geostampcamera.R.string.editor_show_coordinates) to CoordinateFormatter.formatDecimal(photo.latitude, photo.longitude))
                }
                photo.shortAddress?.let { add(context.getString(com.idp.geostampcamera.R.string.editor_show_address) to it) }
                photo.fullAddress?.let { add(context.getString(com.idp.geostampcamera.R.string.editor_show_address) to it) }
                photo.altitudeMeters?.let { add(context.getString(com.idp.geostampcamera.R.string.field_altitude) to "${"%.1f".format(it)} m") }
                photo.accuracyMeters?.let { add(context.getString(com.idp.geostampcamera.R.string.field_accuracy) to "±${"%.0f".format(it)} m") }
                photo.projectName?.let { add(context.getString(com.idp.geostampcamera.R.string.editor_project) to it) }
                photo.note?.let { add(context.getString(com.idp.geostampcamera.R.string.field_note) to it) }
                photo.templateId?.let { add(context.getString(com.idp.geostampcamera.R.string.camera_template) to it) }
            }

            var y = 86f
            for ((k, v) in lines) {
                canvas.drawText("$k: $v", MARGIN, y, body)
                y += 18f
            }

            // Decode the image downsampled to the available width so we don't OOM.
            val availableWidth = (PAGE_W - 2 * MARGIN.toInt())
            val availableHeight = (PAGE_H - y.toInt() - 2 * MARGIN.toInt()).coerceAtLeast(240)
            val bmp = decodeDownsampled(sourceFile, availableWidth, availableHeight)
                ?: error("Could not decode photo at $path")
            try {
                val scale = minOf(
                    availableWidth.toFloat() / bmp.width,
                    availableHeight.toFloat() / bmp.height
                )
                val drawW = (bmp.width * scale).toInt()
                val drawH = (bmp.height * scale).toInt()
                val left = MARGIN.toInt() + (availableWidth - drawW) / 2
                val top = (y + 12f).toInt()
                canvas.drawBitmap(
                    bmp, null,
                    Rect(left, top, left + drawW, top + drawH),
                    null
                )
            } finally {
                bmp.recycle()
            }

            pdf.finishPage(page)

            val outputDir = File(context.filesDir, "reports").apply { if (!exists()) mkdirs() }
            val output = File(outputDir, "report_${System.currentTimeMillis()}.pdf")
            FileOutputStream(output).use { pdf.writeTo(it) }
            // Final existence check so we fail here rather than when the Intent fires.
            require(output.exists() && output.length() > 0) { "PDF was not written: ${output.absolutePath}" }

            return FileProvider.getUriForFile(
                context, context.packageName + ".fileprovider", output
            )
        } finally {
            runCatching { pdf.close() }
        }
    }

    private fun decodeDownsampled(file: File, maxW: Int, maxH: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while ((bounds.outWidth / sample) > maxW * 2 || (bounds.outHeight / sample) > maxH * 2) {
            sample *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
    }

    companion object {
        private const val TAG = "PhotoReportGenerator"
        private const val PAGE_W = 595   // A4 72dpi width
        private const val PAGE_H = 842   // A4 72dpi height
        private const val MARGIN = 36f
    }
}
