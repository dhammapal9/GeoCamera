package com.idp.geostampcamera.stamp

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.idp.geostampcamera.domain.model.StampContext
import com.idp.geostampcamera.util.AppDispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.roundToInt

@Singleton
class PhotoStampRenderer @Inject constructor(
    private val context: Context,
    private val dispatchers: AppDispatchers
) {

    data class RenderResult(
        val stampedFile: File,
        val originalFile: File?,
        val publicUri: Uri?,
        val widthPx: Int,
        val heightPx: Int
    )

    suspend fun render(
        sourceFile: File,
        stamp: StampContext,
        keepOriginal: Boolean,
        exportToMediaStore: Boolean = true,
        maxLongSide: Int = 2560
    ): RenderResult = withContext(dispatchers.default) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        FileInputStream(sourceFile).use { BitmapFactory.decodeStream(it, null, options) }
        val sample = calculateInSampleSize(options.outWidth, options.outHeight, maxLongSide)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw: Bitmap = FileInputStream(sourceFile).use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: error("Image decode failed")

        val oriented: Bitmap = applyExifOrientation(sourceFile, raw)
        val stamped = drawStamp(oriented, stamp)

        val outputDir = outputDir()
        if (!outputDir.exists()) outputDir.mkdirs()
        val stampedFile = File(outputDir, "stamped_${System.currentTimeMillis()}.jpg")
        FileOutputStream(stampedFile).use { stream ->
            stamped.compress(Bitmap.CompressFormat.JPEG, 92, stream)
        }

        val originalPreserved = if (keepOriginal) sourceFile else null
        if (!keepOriginal && sourceFile.exists() && sourceFile != stampedFile) {
            // Keep original anyway if different directory, so edits can regenerate.
            // We don't delete when it is a user capture.
        }

        val publicUri = if (exportToMediaStore) exportToMediaStore(stampedFile) else null
        val widthPx = stamped.width
        val heightPx = stamped.height

        if (stamped !== oriented && !stamped.isRecycled) stamped.recycle()
        if (oriented !== raw && !oriented.isRecycled) oriented.recycle()
        if (!raw.isRecycled) raw.recycle()

        RenderResult(
            stampedFile = stampedFile,
            originalFile = originalPreserved,
            publicUri = publicUri,
            widthPx = widthPx,
            heightPx = heightPx
        )
    }

    private fun applyExifOrientation(file: File, bitmap: Bitmap): Bitmap {
        val exif = runCatching { ExifInterface(file.absolutePath) }.getOrNull() ?: return bitmap
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxLong: Int): Int {
        var sample = 1
        val long = maxOf(width, height)
        while ((long / sample) > maxLong) sample *= 2
        return sample
    }

    private fun outputDir(): File = File(context.filesDir, "photos")

    private fun exportToMediaStore(file: File): Uri? = try {
        val resolver = context.contentResolver
        val name = file.name
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/GeoStamp")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                resolver.openOutputStream(it)?.use { out ->
                    FileInputStream(file).use { input: InputStream -> input.copyTo(out) }
                }
                val finished = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                resolver.update(it, finished, null, null)
            }
            uri
        } else {
            null
        }
    } catch (_: Exception) { null }

    // ---------- Drawing ----------

    /**
     * Builds a fully-transparent bitmap at [widthPx] × [heightPx] with the stamp card drawn
     * at the bottom — same layout as the photo stamp. Used by the video OverlayEffect so the
     * stamp is composited onto every recorded video frame without the rest of the frame being
     * tinted. [cardAlpha] (0f..1f) multiplies the template's card background alpha so video
     * overlays can let more of the frame show through than the opaque photo stamp card.
     */
    fun renderOverlayBitmap(
        widthPx: Int,
        heightPx: Int,
        stamp: StampContext,
        cardAlpha: Float = 1f
    ): Bitmap {
        val canvas = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val scaled = if (cardAlpha >= 0.999f) stamp else {
            val t = stamp.template
            stamp.copy(
                template = t.copy(
                    backgroundAlpha = (t.backgroundAlpha * cardAlpha).toInt().coerceIn(0, 255)
                )
            )
        }
        return drawStamp(canvas, scaled)
    }

    private fun drawStamp(canvasBitmap: Bitmap, stamp: StampContext): Bitmap {
        val output = if (canvasBitmap.isMutable) canvasBitmap
        else canvasBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val lines = StampTextBuilder.build(stamp)
        val template = stamp.template
        val photoShort = min(output.width, output.height).toFloat()
        val padding = (photoShort * 0.025f).coerceAtLeast(16f)
        val corner = template.cornerRadiusDp.toFloat().coerceAtLeast(4f) * density()
        val baseTextSize = (photoShort * 0.028f) * template.fontScale

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(template.backgroundArgb, template.backgroundAlpha)
        }
        val baseTypeface = when (template.fontFamily) {
            com.idp.geostampcamera.domain.model.StampTemplate.FontFamily.SERIF -> Typeface.SERIF
            com.idp.geostampcamera.domain.model.StampTemplate.FontFamily.MONO -> Typeface.MONOSPACE
            com.idp.geostampcamera.domain.model.StampTemplate.FontFamily.CONDENSED -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            else -> Typeface.DEFAULT
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = template.textArgb
            textSize = baseTextSize
            typeface = Typeface.create(baseTypeface, Typeface.NORMAL)
            isAntiAlias = true
        }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = template.accentArgb
            textSize = baseTextSize * 1.15f
            typeface = Typeface.create(baseTypeface, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(template.textArgb, 0xCC)
            textSize = baseTextSize * 0.9f
        }

        val mapSize = mapSizePx(template, photoShort)
        val mapBitmap = if (template.showMap && mapSize > 0) stamp.mapBitmap else null

        val textLines = buildVisibleLines(lines, template)
        val textPaints = textLines.map { line ->
            when (line.kind) {
                LineKind.HEADLINE -> accentPaint
                LineKind.SECONDARY -> subPaint
                else -> textPaint
            }
        }
        val availableTextWidth = (output.width - (padding * 3) - mapSize).coerceAtLeast(photoShort * 0.4f)
        val wrappedLines = textLines.zip(textPaints).flatMap { (line, paint) ->
            wrap(line.text, paint, availableTextWidth).map { line.copy(text = it) }
        }

        val lineHeight = textPaint.textSize * 1.3f
        val headlineHeight = accentPaint.textSize * 1.35f
        val textBlockHeight = wrappedLines.sumOf { line ->
            when (line.kind) { LineKind.HEADLINE -> headlineHeight.toDouble(); else -> lineHeight.toDouble() }
        }.toFloat()

        val stampHeight = maxOf(textBlockHeight + padding, mapSize.toFloat() + padding)
        val top = output.height - stampHeight - padding
        val left = padding
        val right = output.width - padding
        val bottom = output.height - padding
        val cardRect = RectF(left, top, right, bottom)

        canvas.drawRoundRect(cardRect, corner, corner, bgPaint)
        if (template.borderDp > 0) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = template.borderArgb
                strokeWidth = template.borderDp * density()
            }
            canvas.drawRoundRect(cardRect, corner, corner, borderPaint)
        }

        val contentLeft = cardRect.left + padding * 0.75f
        val contentTop = cardRect.top + padding * 0.75f
        if (mapBitmap != null && mapSize > 0) {
            val mapRect = RectF(
                contentLeft, contentTop,
                contentLeft + mapSize, contentTop + mapSize
            )
            val scaledMap = Bitmap.createScaledBitmap(mapBitmap, mapSize, mapSize, true)
            canvas.drawBitmap(scaledMap, null, mapRect, null)
            if (scaledMap != mapBitmap) scaledMap.recycle()
        }

        val textStartX = contentLeft + (if (mapBitmap != null && mapSize > 0) mapSize + padding * 0.6f else 0f)
        var textY = contentTop
        for (line in wrappedLines) {
            val p = when (line.kind) {
                LineKind.HEADLINE -> accentPaint
                LineKind.SECONDARY -> subPaint
                else -> textPaint
            }
            textY += p.textSize
            canvas.drawText(line.text, textStartX, textY, p)
            textY += when (line.kind) { LineKind.HEADLINE -> headlineHeight * 0.15f; else -> lineHeight * 0.2f }
        }

        if (template.showLogo && template.logoUri != null) {
            drawLogo(canvas, template.logoUri, cardRect, padding)
        }
        return output
    }

    private fun drawLogo(canvas: Canvas, logoUri: String, cardRect: RectF, padding: Float) {
        val uri = runCatching { Uri.parse(logoUri) }.getOrNull() ?: return
        val input = runCatching {
            if (uri.scheme == "content") context.contentResolver.openInputStream(uri)
            else FileInputStream(uri.path ?: return)
        }.getOrNull() ?: return
        input.use {
            val logo = BitmapFactory.decodeStream(it) ?: return
            val size = (cardRect.height() * 0.6f).coerceAtMost(logo.width.toFloat())
            val dst = RectF(
                cardRect.right - padding - size,
                cardRect.top + padding * 0.5f,
                cardRect.right - padding,
                cardRect.top + padding * 0.5f + size
            )
            canvas.drawBitmap(logo, null, dst, null)
            logo.recycle()
        }
    }

    private fun density(): Float = context.resources.displayMetrics.density

    private fun mapSizePx(template: com.idp.geostampcamera.domain.model.StampTemplate, photoShort: Float): Int {
        if (!template.showMap) return 0
        val base = when (template.mapSize) {
            com.idp.geostampcamera.domain.model.StampTemplate.MapSize.HIDDEN -> 0f
            com.idp.geostampcamera.domain.model.StampTemplate.MapSize.SMALL -> photoShort * 0.14f
            com.idp.geostampcamera.domain.model.StampTemplate.MapSize.MEDIUM -> photoShort * 0.19f
            com.idp.geostampcamera.domain.model.StampTemplate.MapSize.LARGE -> photoShort * 0.26f
        }
        return base.roundToInt().coerceAtLeast(0)
    }

    private data class StampLine(val text: String, val kind: LineKind)
    private enum class LineKind { HEADLINE, PRIMARY, SECONDARY }

    private fun buildVisibleLines(
        lines: StampTextBuilder.StampLines,
        template: com.idp.geostampcamera.domain.model.StampTemplate
    ): List<StampLine> = buildList {
        lines.headline?.let { add(StampLine(it, LineKind.HEADLINE)) }
        lines.shortAddress?.let { add(StampLine(it, LineKind.PRIMARY)) }
        lines.fullAddress?.let { add(StampLine(it, LineKind.SECONDARY)) }
        lines.coordinates?.let { add(StampLine(it, LineKind.SECONDARY)) }
        lines.plusCode?.let { add(StampLine(it, LineKind.SECONDARY)) }
        lines.dateTime?.let { add(StampLine(it, LineKind.SECONDARY)) }
        lines.weather?.let { add(StampLine(it, LineKind.SECONDARY)) }
        if (lines.extras.isNotEmpty()) add(StampLine(lines.extras.joinToString("  •  "), LineKind.SECONDARY))
        lines.note?.let { add(StampLine(it, LineKind.SECONDARY)) }
        lines.hashtag?.let { add(StampLine(it, LineKind.SECONDARY)) }
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return emptyList()
        val words = text.split(" ")
        val result = mutableListOf<String>()
        val current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            val bounds = Rect()
            paint.getTextBounds(candidate, 0, candidate.length, bounds)
            if (bounds.width() > maxWidth && current.isNotEmpty()) {
                result.add(current.toString())
                current.clear()
                current.append(word)
            } else {
                current.clear().append(candidate)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }

    private fun withAlpha(argb: Int, alpha: Int): Int {
        val a = alpha.coerceIn(0, 255)
        return Color.argb(a, Color.red(argb), Color.green(argb), Color.blue(argb))
    }
}
