package com.idp.geostampcamera.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Publishes a recorded mp4 from the app's private storage to the public Movies/<App>
 *  directory so it shows up in Photos / Gallery / File manager. Returns the public
 *  content URI (or null if publishing failed). */
object VideoPublisher {

    private const val FOLDER = "GeoStamp"

    fun publish(context: Context, file: File): Uri? {
        if (!file.exists()) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) publishViaMediaStore(context, file)
        else publishLegacy(context, file)
    }

    private fun publishViaMediaStore(context: Context, file: File): Uri? = runCatching {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/" + FOLDER)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: return@runCatching null
        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        }
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        uri
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun publishLegacy(context: Context, file: File): Uri? = runCatching {
        val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), FOLDER)
        if (!publicDir.exists()) publicDir.mkdirs()
        val target = File(publicDir, file.name)
        file.inputStream().use { input ->
            target.outputStream().use { out -> input.copyTo(out) }
        }
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DATA, target.absolutePath)
            put(MediaStore.Video.Media.DISPLAY_NAME, target.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        }
        context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
    }.getOrNull()
}
