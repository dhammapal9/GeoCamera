package com.idp.geostampcamera.map

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.idp.geostampcamera.domain.model.StampTemplate
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class MapSnapshotProvider @Inject constructor(private val context: Context) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** Cached once; read from AndroidManifest `com.google.android.geo.API_KEY` meta-data. */
    private val apiKey: String? by lazy {
        runCatching {
            val info = context.packageManager.getApplicationInfo(
                context.packageName, PackageManager.GET_META_DATA
            )
            info.metaData?.getString("com.google.android.geo.API_KEY")?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    /**
     * Produce a map bitmap for the stamp.
     *
     * Preference order:
     *   1. **Static Maps HTTP API** — reliable, bakes the Google logo in by itself, no
     *      off-screen MapView gymnastics. Requires Maps Static API enabled on the key's project.
     *   2. **Google Maps SDK snapshot** — fallback if the static call failed (API disabled,
     *      offline, key restricted). Rendered through a programmatic lite-mode MapView.
     *   3. **Programmatic placeholder** — grey grid + yellow dot so the stamp still has something.
     */
    suspend fun snapshot(
        lat: Double,
        lng: Double,
        widthPx: Int,
        heightPx: Int,
        mapType: Int,
        zoom: Float = 18f,
        timeoutMs: Long = 6_000
    ): Bitmap {
        val fromStatic = withTimeoutOrNull(timeoutMs) {
            staticMapsSnapshot(lat, lng, widthPx, heightPx, mapType, zoom)
        }
        if (fromStatic != null) return fromStatic

        val fromSdk = withContext(Dispatchers.Main) {
            withTimeoutOrNull(timeoutMs) {
                runCatching { googleSnapshot(lat, lng, widthPx, heightPx, mapType, zoom) }
                    .onFailure { Log.w("MapSnapshot", "SDK snapshot failed", it) }
                    .getOrNull()
            }
        }
        if (fromSdk != null) return fromSdk

        Log.w("MapSnapshot", "All map providers failed; using placeholder for $lat,$lng")
        return placeholder(widthPx, heightPx)
    }

    /**
     * HTTP fetch to Google's Static Maps API. Returns the decoded bitmap, or null if the key
     * is missing, the HTTP call failed, or the response wasn't a decodable image.
     */
    private suspend fun staticMapsSnapshot(
        lat: Double, lng: Double, widthPx: Int, heightPx: Int, mapType: Int, zoom: Float
    ): Bitmap? = withContext(Dispatchers.IO) {
        val key = apiKey ?: run {
            Log.w("MapSnapshot", "Maps API key missing from manifest; skipping Static Maps")
            return@withContext null
        }
        // Static Maps caps individual axes at 640; scale=2 doubles the rendered resolution.
        val reqW = widthPx.coerceAtMost(640)
        val reqH = heightPx.coerceAtMost(640)
        val mapTypeName = staticMapTypeName(mapType)
        // `size:tiny` keeps the red pin minimal on the ~200px stamp thumbnail so the map
        // imagery and Google watermark stay fully visible. The default marker is ~30x50 px
        // (doubled at scale=2) which would cover most of the box.
        val marker = URLEncoder.encode("size:tiny|color:red|$lat,$lng", "UTF-8")
        val url = buildString {
            append("https://maps.googleapis.com/maps/api/staticmap")
            append("?center=").append(lat).append(",").append(lng)
            append("&zoom=").append(zoom.toInt())
            append("&size=").append(reqW).append("x").append(reqH)
            append("&scale=2")
            append("&maptype=").append(mapTypeName)
            append("&markers=").append(marker)
            append("&key=").append(key)
        }
        val req = Request.Builder().url(url).get().build()
        runCatching {
            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    Log.w("MapSnapshot", "Static Maps HTTP ${res.code}: ${res.message}")
                    return@use null
                }
                val bytes = res.body?.bytes() ?: return@use null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }.onFailure { Log.w("MapSnapshot", "Static Maps call failed", it) }.getOrNull()
    }

    private suspend fun googleSnapshot(
        lat: Double, lng: Double, widthPx: Int, heightPx: Int, mapType: Int, zoom: Float
    ): Bitmap = suspendCancellableCoroutine { cont: CancellableContinuation<Bitmap> ->
        val mapView = MapView(
            context,
            GoogleMapOptions().liteMode(true).mapType(mapType)
        )
        mapView.layoutParams = ViewGroup.LayoutParams(widthPx, heightPx)
        mapView.onCreate(null)
        mapView.onStart()
        mapView.onResume()
        mapView.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        )
        mapView.layout(0, 0, widthPx, heightPx)
        mapView.getMapAsync { map: GoogleMap ->
            val latLng = LatLng(lat, lng)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom))
            map.addMarker(MarkerOptions().position(latLng))
            map.setOnMapLoadedCallback {
                map.snapshot { bmp ->
                    try {
                        if (bmp != null) cont.resume(bmp) else cont.resume(placeholder(widthPx, heightPx))
                    } finally {
                        mapView.onPause()
                        mapView.onStop()
                        mapView.onDestroy()
                    }
                }
            }
        }
        cont.invokeOnCancellation {
            runCatching { mapView.onPause(); mapView.onStop(); mapView.onDestroy() }
        }
    }

    fun placeholder(widthPx: Int, heightPx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val base = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#334A5A6E") }
        canvas.drawRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), base)
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#404A5A6E"); strokeWidth = 1f
        }
        val step = (minOf(widthPx, heightPx) / 6f).coerceAtLeast(8f)
        var x = step
        while (x < widthPx) { canvas.drawLine(x, 0f, x, heightPx.toFloat(), grid); x += step }
        var y = step
        while (y < heightPx) { canvas.drawLine(0f, y, widthPx.toFloat(), y, grid); y += step }
        val pin = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F2B134") }
        val cx = widthPx / 2f
        val cy = heightPx / 2f
        canvas.drawCircle(cx, cy, minOf(widthPx, heightPx) * 0.1f, pin)
        pin.color = Color.WHITE
        canvas.drawCircle(cx, cy, minOf(widthPx, heightPx) * 0.05f, pin)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#55FFFFFF"); style = Paint.Style.STROKE; strokeWidth = 2f
        }
        canvas.drawRoundRect(RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat()), 6f, 6f, border)
        return bmp
    }

    fun mapTypeFromTemplate(template: StampTemplate, defaultType: String): Int {
        return when (defaultType.lowercase()) {
            "satellite" -> GoogleMap.MAP_TYPE_SATELLITE
            "terrain" -> GoogleMap.MAP_TYPE_TERRAIN
            "hybrid" -> GoogleMap.MAP_TYPE_HYBRID
            else -> GoogleMap.MAP_TYPE_NORMAL
        }
    }

    private fun staticMapTypeName(mapType: Int): String = when (mapType) {
        GoogleMap.MAP_TYPE_SATELLITE -> "satellite"
        GoogleMap.MAP_TYPE_TERRAIN -> "terrain"
        GoogleMap.MAP_TYPE_HYBRID -> "hybrid"
        else -> "roadmap"
    }
}
