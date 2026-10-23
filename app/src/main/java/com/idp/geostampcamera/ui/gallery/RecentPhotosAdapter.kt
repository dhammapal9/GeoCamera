package com.idp.geostampcamera.ui.gallery

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.idp.geostampcamera.databinding.ItemPhotoRecentBinding
import com.idp.geostampcamera.domain.model.CapturedPhoto
import com.idp.geostampcamera.util.DateFormatter
import com.idp.geostampcamera.util.setDebouncedClickListener
import java.io.File

class RecentPhotosAdapter(
    private val onClick: (CapturedPhoto) -> Unit
) : RecyclerView.Adapter<RecentPhotosAdapter.ViewHolder>() {

    private val items = mutableListOf<CapturedPhoto>()

    fun submit(list: List<CapturedPhoto>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPhotoRecentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class ViewHolder(val binding: ItemPhotoRecentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(photo: CapturedPhoto) {
            val path = photo.stampedPath ?: photo.originalPath
            path?.let { binding.recentThumb.load(File(it)) }
            binding.recentCaption.text =
                photo.shortAddress ?: DateFormatter.format(photo.timestampMillis, DateFormatter.FORMAT_SHORT)
            binding.root.setDebouncedClickListener { onClick(photo) }
        }
    }
}
