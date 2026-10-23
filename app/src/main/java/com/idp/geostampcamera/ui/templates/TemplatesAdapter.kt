package com.idp.geostampcamera.ui.templates

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.idp.geostampcamera.R
import com.idp.geostampcamera.databinding.ItemTemplateBinding
import com.idp.geostampcamera.domain.model.AddressDetails
import com.idp.geostampcamera.domain.model.LocationSnapshot
import com.idp.geostampcamera.domain.model.StampContext
import com.idp.geostampcamera.domain.model.StampTemplate
import com.idp.geostampcamera.util.setDebouncedClickListener

class TemplatesAdapter(
    private val onApply: (StampTemplate) -> Unit,
    private val onEdit: (StampTemplate) -> Unit,
    private val onDuplicate: (StampTemplate) -> Unit,
    private val onDelete: (StampTemplate) -> Unit
) : RecyclerView.Adapter<TemplatesAdapter.ViewHolder>() {

    private val items = mutableListOf<StampTemplate>()

    fun submit(list: List<StampTemplate>) { items.clear(); items.addAll(list); notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTemplateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size
    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])

    inner class ViewHolder(private val binding: ItemTemplateBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(template: StampTemplate) {
            binding.templateName.text = template.getLocalizedName(binding.root.context)
            binding.templateBadge.text = if (template.isBuiltIn) "Built-in" else "Custom"
            val stamp = StampContext(
                template = template,
                captureAtMillis = System.currentTimeMillis(),
                location = SAMPLE,
                compass = null, magnetic = null, weather = null, mapBitmap = null
            )
            binding.templatePreview.bind(stamp)
            binding.useTemplate.setDebouncedClickListener { onApply(template) }
            binding.templateMenu.setDebouncedClickListener { anchor ->
                PopupMenu(anchor.context, anchor).apply {
                    menu.add(0, 1, 0, anchor.context.getString(R.string.common_edit))
                    menu.add(0, 2, 1, anchor.context.getString(R.string.common_duplicate))
                    if (!template.isBuiltIn) menu.add(0, 3, 2, anchor.context.getString(R.string.common_delete))
                    setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            1 -> { onEdit(template); true }
                            2 -> { onDuplicate(template); true }
                            3 -> { onDelete(template); true }
                            else -> false
                        }
                    }
                }.show()
            }
        }
    }

    private companion object {
        val SAMPLE = LocationSnapshot(
            latitude = 18.626936, longitude = 73.710798,
            accuracyMeters = 8f, altitudeMeters = 582.0,
            speedMps = null, bearingDegrees = null,
            acquiredAtMillis = System.currentTimeMillis(),
            source = LocationSnapshot.Source.FUSED,
            address = AddressDetails(
                shortAddress = "Jambe, Maharashtra, India",
                fullAddress = "R10, Tathawade, Jambe, Maharashtra 411033, India",
                city = "Jambe", state = "Maharashtra", country = "India",
                countryCode = "IN", postalCode = "411033"
            )
        )
    }
}
