package com.idp.geostampcamera.drive

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.idp.geostampcamera.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uploads stamped JPEGs to the signed-in user's Google Drive. Uses the drive.file OAuth
 * scope, which only grants access to files our app creates — so we can never read the
 * user's other Drive content. OAuth tokens are fetched via GoogleAuthUtil (short-lived,
 * refreshed on each use); a 401 response causes a one-shot invalidate + retry.
 */
@Singleton
class DriveSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository
) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    sealed class Result {
        data object NotConnected : Result()
        data class Success(val fileId: String) : Result()
        data class NeedsReauth(val message: String) : Result()
        data class Failure(val message: String) : Result()
    }

    /** Uploads [stampedFile] into the "GPS Map Camera - GeoTag" folder of the signed-in account. */
    suspend fun upload(stampedFile: File): Result = withContext(Dispatchers.IO) {
        val email = settings.driveAccountEmailFlow().first()
            ?: return@withContext Result.NotConnected
        val account = Account(email, "com.google")

        runCatching {
            val token = fetchToken(account)
            val folderId = ensureFolder(account, token)
            uploadInto(account, token, folderId, stampedFile)
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { throwable ->
                when (throwable) {
                    is UserRecoverableAuthException ->
                        Result.NeedsReauth(throwable.message ?: "Re-authorise Google Drive access")
                    is TokenRevokedException ->
                        Result.NeedsReauth("Google sign-in expired. Reconnect to continue syncing.")
                    else -> Result.Failure(throwable.message ?: "Drive upload failed")
                }
            }
        )
    }

    private fun fetchToken(account: Account): String =
        GoogleAuthUtil.getToken(context, account, OAUTH_SCOPE)

    private fun invalidateToken(token: String) {
        runCatching { GoogleAuthUtil.clearToken(context, token) }
    }

    /** Returns the cached folder ID, verifying it still exists; otherwise finds-or-creates. */
    private suspend fun ensureFolder(account: Account, initialToken: String): String {
        val cached = settings.driveFolderIdFlow().first()
        if (!cached.isNullOrBlank() && folderExists(account, initialToken, cached)) return cached
        val found = findFolder(account, initialToken) ?: createFolder(account, initialToken)
        settings.setDriveFolderId(found)
        return found
    }

    private fun folderExists(account: Account, token: String, folderId: String): Boolean {
        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$folderId?fields=id,trashed")
            .get()
            .build()
        return call(account, token, req) { res ->
            if (!res.isSuccessful) false
            else {
                val body = res.body?.string().orEmpty()
                val json = runCatching { JSONObject(body) }.getOrNull()
                json != null && !json.optBoolean("trashed", false)
            }
        } == true
    }

    private fun findFolder(account: Account, token: String): String? {
        val q = "mimeType='application/vnd.google-apps.folder' and name='$FOLDER_NAME' and trashed=false"
        val url = "https://www.googleapis.com/drive/v3/files?spaces=drive&fields=files(id,name)&q=" +
            java.net.URLEncoder.encode(q, "UTF-8")
        val req = Request.Builder().url(url).get().build()
        return call(account, token, req) { res ->
            if (!res.isSuccessful) null
            else {
                val body = res.body?.string().orEmpty()
                val arr = JSONObject(body).optJSONArray("files") ?: JSONArray()
                if (arr.length() > 0) arr.getJSONObject(0).optString("id").takeIf { it.isNotBlank() } else null
            }
        }
    }

    private fun createFolder(account: Account, token: String): String {
        val metadata = JSONObject()
            .put("name", FOLDER_NAME)
            .put("mimeType", "application/vnd.google-apps.folder")
            .toString()
        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?fields=id")
            .post(metadata.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()
        return call(account, token, req) { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw RuntimeException("Create folder failed: HTTP ${res.code} $body")
            JSONObject(body).optString("id").ifBlank { throw RuntimeException("Drive did not return a folder id") }
        } ?: throw RuntimeException("Drive create folder returned no body")
    }

    private fun uploadInto(account: Account, token: String, folderId: String, file: File): String {
        val metadata = JSONObject()
            .put("name", file.name)
            .put("parents", JSONArray().put(folderId))
            .toString()
        val metadataPart = metadata.toRequestBody("application/json; charset=UTF-8".toMediaType())
        val filePart = file.asRequestBody("image/jpeg".toMediaType())
        val multipart = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadataPart)
            .addPart(filePart)
            .build()
        val req = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id")
            .post(multipart)
            .build()
        return call(account, token, req) { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw RuntimeException("Upload failed: HTTP ${res.code} $body")
            JSONObject(body).optString("id").ifBlank { throw RuntimeException("Drive did not return a file id") }
        } ?: throw RuntimeException("Drive upload returned no body")
    }

    /**
     * Attaches the bearer token to [requestNoAuth], executes it, and on 401 invalidates the token,
     * fetches a fresh one for [account], and retries once. A second 401 is treated as revoked.
     */
    private fun <T> call(
        account: Account,
        token: String,
        requestNoAuth: Request,
        read: (okhttp3.Response) -> T?
    ): T? {
        val first = requestNoAuth.newBuilder().header("Authorization", "Bearer $token").build()
        http.newCall(first).execute().use { res ->
            if (res.code != 401) return read(res)
        }
        invalidateToken(token)
        val fresh = fetchToken(account)
        val retry = requestNoAuth.newBuilder().header("Authorization", "Bearer $fresh").build()
        http.newCall(retry).execute().use { res ->
            if (res.code == 401) throw TokenRevokedException()
            return read(res)
        }
    }

    private class TokenRevokedException : RuntimeException("Drive token revoked")

    companion object {
        const val OAUTH_SCOPE = "oauth2:https://www.googleapis.com/auth/drive.file"
        const val FOLDER_NAME = "GPS Map Camera - GeoTag"
    }
}
