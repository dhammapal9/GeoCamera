package com.idp.geostampcamera.ui.gallery

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads videos the app published into Movies/GeoStamp. */
object VideoLibrary {

    private const val FOLDER = "GeoStamp"

    suspend fun loadGeoStampVideos(context: Context): List<VideoItem> = withContext(Dispatchers.IO) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.SIZE,
        )
        // On API 29+ RELATIVE_PATH scoping identifies the folder cleanly; older APIs
        // fall back to a filename-prefix filter (our videos start with "video_").
        val (selection, args) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?" to arrayOf("%${FOLDER}%")
        } else {
            "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?" to arrayOf("video_%")
        }
        val sort = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        val list = mutableListOf<VideoItem>()
        runCatching {
            context.contentResolver.query(uri, projection, selection, args, sort)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    list += VideoItem(
                        id = id,
                        uri = ContentUris.withAppendedId(uri, id),
                        displayName = cursor.getString(nameCol).orEmpty(),
                        durationMs = cursor.getLong(durationCol),
                        // DATE_ADDED is in seconds, convert to ms.
                        dateAddedMillis = cursor.getLong(dateCol) * 1000L,
                        sizeBytes = cursor.getLong(sizeCol),
                    )
                }
            }
        }
        list
    }
}
