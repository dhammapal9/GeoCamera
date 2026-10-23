package com.idp.geostampcamera.ui.gallery

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.idp.geostampcamera.R
import com.idp.geostampcamera.ads.AdsManager
import com.idp.geostampcamera.databinding.ItemGallerySectionBinding
import com.idp.geostampcamera.databinding.ItemPhotoBinding
import com.idp.geostampcamera.databinding.ViewNativeAdBinding
import com.idp.geostampcamera.domain.model.CapturedPhoto
import com.idp.geostampcamera.util.DateFormatter
import com.idp.geostampcamera.util.setDebouncedClickListener
import java.io.File
import java.util.Calendar

/**
 * Sectioned gallery adapter with native-ad rows inserted every [adInterval] photos.
 * - Header rows are view type 0 (full-width).
 * - Photo rows are view type 1 (single grid cell).
 * - Native ad rows are view type 2 (full-width).
 */
class GallerySectionedAdapter(
    private val adsManager: AdsManager? = null,
    private val adInterval: Int = 6,
    private val onClick: (CapturedPhoto) -> Unit,
    private val onLongClick: (CapturedPhoto) -> Unit = {},
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class Entry {
        data class Header(val title: String, val count: Int) : Entry()
        data class Photo(val photo: CapturedPhoto) : Entry()
        data class Native(val ad: NativeAd) : Entry()
    }

    private val items = mutableListOf<Entry>()
    private val boundNativeAds = mutableListOf<NativeAd>()

    /** True when the fragment is in multi-select mode. Shows a tappable check circle on every photo. */
    var selectionMode: Boolean = false
        set(value) { if (field != value) { field = value; notifyDataSetChanged() } }

    /** IDs of the currently selected photos. Updating this triggers a UI refresh for affected items. */
    private var selectedIds: Set<Long> = emptySet()
    fun submitSelection(ids: Set<Long>) {
        if (ids == selectedIds) return
        selectedIds = ids
        notifyDataSetChanged()
    }

    /** Returns every photo id currently backed by the adapter — used for "select all". */
    fun allPhotoIds(): List<Long> = items.mapNotNull { (it as? Entry.Photo)?.photo?.id }

    fun submit(photos: List<CapturedPhoto>) {
        // Release any previously bound native ads before rebuilding the list.
        boundNativeAds.forEach { runCatching { it.destroy() } }
        boundNativeAds.clear()
        items.clear()

        val now = Calendar.getInstance()
        val today = now.get(Calendar.DAY_OF_YEAR) to now.get(Calendar.YEAR)
        now.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = now.get(Calendar.DAY_OF_YEAR) to now.get(Calendar.YEAR)

        val buckets = linkedMapOf<String, MutableList<CapturedPhoto>>()
        photos.forEach { photo ->
            val cal = Calendar.getInstance().apply { timeInMillis = photo.timestampMillis }
            val key = when (cal.get(Calendar.DAY_OF_YEAR) to cal.get(Calendar.YEAR)) {
                today -> "today"
                yesterday -> "yesterday"
                else -> DateFormatter.format(photo.timestampMillis, "MMM d, yyyy")
            }
            buckets.getOrPut(key) { mutableListOf() }.add(photo)
        }

        var photoCount = 0
        buckets.forEach { (key, list) ->
            val label = when (key) {
                "today" -> "Today"
                "yesterday" -> "Yesterday"
                else -> key
            }
            items.add(Entry.Header(label, list.size))
            list.forEach { photo ->
                items.add(Entry.Photo(photo))
                photoCount++
                if (photoCount % adInterval == 0) {
                    val ad = adsManager?.takeNativeAd()
                    if (ad != null) {
                        items.add(Entry.Native(ad))
                        boundNativeAds.add(ad)
                    }
                }
            }
        }
        notifyDataSetChanged()
    }

    fun isHeaderAt(position: Int): Boolean = items[position] is Entry.Header
    fun isNativeAdAt(position: Int): Boolean = items[position] is Entry.Native

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is Entry.Header -> VIEW_HEADER
        is Entry.Photo -> VIEW_PHOTO
        is Entry.Native -> VIEW_NATIVE
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
        VIEW_HEADER -> HeaderVH(ItemGallerySectionBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        VIEW_NATIVE -> NativeAdVH(ViewNativeAdBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        else -> PhotoVH(ItemPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val entry = items[position]) {
            is Entry.Header -> (holder as HeaderVH).bind(entry)
            is Entry.Photo -> (holder as PhotoVH).bind(entry.photo)
            is Entry.Native -> (holder as NativeAdVH).bind(entry.ad)
        }
    }

    fun destroy() {
        boundNativeAds.forEach { runCatching { it.destroy() } }
        boundNativeAds.clear()
    }

    inner class HeaderVH(val binding: ItemGallerySectionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(header: Entry.Header) {
            binding.gallerySectionTitle.text = header.title
            binding.gallerySectionCount.text =
                binding.root.context.getString(R.string.gallery_section_count, header.count)
        }
    }

    inner class PhotoVH(val binding: ItemPhotoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(photo: CapturedPhoto) {
            val path = photo.stampedPath ?: photo.originalPath
            if (path != null) binding.photoThumb.load(File(path))
            binding.photoBadge.text = if (photo.stampedPath != null) "GPS" else "RAW"
            binding.photoCaption.text = photo.shortAddress
                ?: DateFormatter.format(photo.timestampMillis, DateFormatter.FORMAT_SHORT)

            val selected = photo.id in selectedIds
            // Show the check circle whenever we're in multi-select mode so users have an
            // affordance to toggle items; tint it when the item is actually selected.
            binding.photoSelectCheck.visibility = if (selectionMode) android.view.View.VISIBLE else android.view.View.GONE
            binding.photoSelectCheck.isSelected = selected
            binding.photoSelectionTint.visibility =
                if (selectionMode && selected) android.view.View.VISIBLE else android.view.View.GONE

            if (selectionMode) {
                binding.root.setOnClickListener { onLongClick(photo) }
            } else {
                binding.root.setDebouncedClickListener { onClick(photo) }
            }
            binding.root.setOnLongClickListener { onLongClick(photo); true }
        }
    }

    inner class NativeAdVH(val binding: ViewNativeAdBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(ad: NativeAd) {
            val root = binding.root as NativeAdView
            binding.nativeAdHeadline.text = ad.headline
            binding.nativeAdAdvertiser.text = ad.advertiser ?: ad.store ?: ""
            binding.nativeAdBody.text = ad.body ?: ""
            ad.icon?.drawable?.let { binding.nativeAdIcon.setImageDrawable(it) }
            if (ad.callToAction.isNullOrBlank()) {
                binding.nativeAdCta.visibility = android.view.View.GONE
            } else {
                binding.nativeAdCta.visibility = android.view.View.VISIBLE
                binding.nativeAdCta.text = ad.callToAction
            }
            root.headlineView = binding.nativeAdHeadline
            root.bodyView = binding.nativeAdBody
            root.iconView = binding.nativeAdIcon
            root.callToActionView = binding.nativeAdCta
            root.advertiserView = binding.nativeAdAdvertiser
            root.mediaView = binding.nativeAdMedia
            root.setNativeAd(ad)
        }
    }

    companion object {
        private const val VIEW_HEADER = 0
        private const val VIEW_PHOTO = 1
        private const val VIEW_NATIVE = 2
    }
}
