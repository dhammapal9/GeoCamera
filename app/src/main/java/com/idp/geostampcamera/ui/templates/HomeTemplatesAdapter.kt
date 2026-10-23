package com.idp.geostampcamera.ui.templates

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.idp.geostampcamera.databinding.ItemTemplateHomeBinding
import com.idp.geostampcamera.domain.model.LocationSnapshot
import com.idp.geostampcamera.domain.model.StampContext
import com.idp.geostampcamera.domain.model.StampTemplate
import com.idp.geostampcamera.util.setDebouncedClickListener

class HomeTemplatesAdapter(
    private val onSelect: (StampTemplate) -> Unit
) : RecyclerView.Adapter<HomeTemplatesAdapter.ViewHolder>() {

    private val items = mutableListOf<StampTemplate>()

    fun submit(list: List<StampTemplate>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTemplateHomeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])

    inner class ViewHolder(val binding: ItemTemplateHomeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(template: StampTemplate) {
            binding.homeTemplateName.text = template.getLocalizedName(binding.root.context)
            val sample = StampContext(
                template = template,
                captureAtMillis = System.currentTimeMillis(),
                location = SAMPLE_LOCATION,
                compass = null, magnetic = null, weather = null, mapBitmap = null
            )
            binding.homeTemplatePreview.bind(sample)
            binding.root.setDebouncedClickListener { onSelect(template) }
        }
    }

    private companion object {
        val SAMPLE_LOCATION = LocationSnapshot(
            latitude = 18.626936,
            longitude = 73.710798,
            accuracyMeters = 8f,
            altitudeMeters = 582.0,
            speedMps = null, bearingDegrees = null,
            acquiredAtMillis = System.currentTimeMillis(),
            source = LocationSnapshot.Source.FUSED,
            address = com.idp.geostampcamera.domain.model.AddressDetails(
                shortAddress = "Jambe, Maharashtra, India",
                fullAddress = "R10, Tathawade, Jambe, Maharashtra 411033, India",
                city = "Jambe", state = "Maharashtra", country = "India",
                countryCode = "IN", postalCode = "411033"
            )
        )
    }
}
