package com.idp.geostampcamera.ui.gallery

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.idp.geostampcamera.databinding.ItemPhotoBinding
import com.idp.geostampcamera.domain.model.CapturedPhoto
import com.idp.geostampcamera.util.DateFormatter
import com.idp.geostampcamera.util.setDebouncedClickListener
import java.io.File

class GalleryAdapter(
    private val onClick: (CapturedPhoto) -> Unit
) : ListAdapter<CapturedPhoto, GalleryAdapter.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<CapturedPhoto>() {
        override fun areItemsTheSame(a: CapturedPhoto, b: CapturedPhoto) = a.id == b.id
        override fun areContentsTheSame(a: CapturedPhoto, b: CapturedPhoto) = a == b
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemPhotoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(photo: CapturedPhoto) {
            val path = photo.stampedPath ?: photo.originalPath
            if (path != null) binding.photoThumb.load(File(path))
            binding.photoBadge.text = if (photo.stampedPath != null) "GPS" else "RAW"
            binding.photoCaption.text = photo.shortAddress
                ?: DateFormatter.format(photo.timestampMillis, DateFormatter.FORMAT_SHORT)
            binding.root.setDebouncedClickListener { onClick(photo) }
        }
    }
}
