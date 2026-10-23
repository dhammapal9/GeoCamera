package com.idp.geostampcamera.ui.gallery

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.text.format.DateFormat
import android.util.LruCache
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.idp.geostampcamera.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.concurrent.TimeUnit

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val dateAddedMillis: Long,
    val sizeBytes: Long,
)

class VideoGalleryAdapter(
    private val onClick: (VideoItem) -> Unit,
    private val onLongClick: (VideoItem) -> Unit = {},
) : ListAdapter<VideoItem, VideoGalleryAdapter.VH>(DIFF) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val thumbCache = LruCache<Long, Bitmap>(32)
    private val pending = mutableMapOf<Int, Job>()

    var selectionMode: Boolean = false
        set(value) { if (field != value) { field = value; notifyDataSetChanged() } }

    private var selectedIds: Set<Long> = emptySet()
    fun submitSelection(ids: Set<Long>) {
        if (ids == selectedIds) return
        selectedIds = ids
        notifyDataSetChanged()
    }

    fun allVideoIds(): List<Long> = currentList.map { it.id }

    inner class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val thumb: ImageView = view.findViewById(R.id.videoThumb)
        val duration: TextView = view.findViewById(R.id.videoDuration)
        val date: TextView = view.findViewById(R.id.videoDate)
        val selectCheck: ImageView = view.findViewById(R.id.videoSelectCheck)
        val selectionTint: android.view.View = view.findViewById(R.id.videoSelectionTint)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gallery_video, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val cached = thumbCache.get(item.id)
        if (cached != null) {
            holder.thumb.setImageBitmap(cached)
        } else {
            holder.thumb.setImageDrawable(null)
            pending[position]?.cancel()
            pending[position] = scope.launch {
                val bmp = fetchThumbnail(holder.itemView.context, item.uri)
                if (bmp != null) {
                    thumbCache.put(item.id, bmp)
                    if (holder.bindingAdapterPosition == position) holder.thumb.setImageBitmap(bmp)
                }
            }
        }
        holder.duration.text = formatDuration(item.durationMs)
        holder.date.text = DateFormat.getDateFormat(holder.itemView.context)
            .format(Date(item.dateAddedMillis))

        val selected = item.id in selectedIds
        holder.selectCheck.visibility = if (selectionMode) android.view.View.VISIBLE else android.view.View.GONE
        holder.selectCheck.isSelected = selected
        holder.selectionTint.visibility =
            if (selectionMode && selected) android.view.View.VISIBLE else android.view.View.GONE

        holder.itemView.setOnClickListener {
            if (selectionMode) onLongClick(item) else onClick(item)
        }
        holder.itemView.setOnLongClickListener { onLongClick(item); true }
    }

    override fun onViewRecycled(holder: VH) {
        pending[holder.bindingAdapterPosition]?.cancel()
        super.onViewRecycled(holder)
    }

    fun destroy() {
        scope.coroutineContext[Job]?.cancel()
        thumbCache.evictAll()
    }

    private suspend fun fetchThumbnail(ctx: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        runCatching {
            retriever.setDataSource(ctx, uri)
            retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }.getOrNull().also {
            runCatching { retriever.release() }
        }
    }

    private fun formatDuration(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSec = TimeUnit.MILLISECONDS.toSeconds(ms)
        val m = totalSec / 60
        val s = totalSec % 60
        return "%d:%02d".format(m, s)
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<VideoItem>() {
            override fun areItemsTheSame(a: VideoItem, b: VideoItem) = a.id == b.id
            override fun areContentsTheSame(a: VideoItem, b: VideoItem) = a == b
        }
    }
}
