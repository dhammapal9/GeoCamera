package com.idp.geostampcamera.ui.editor

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.idp.geostampcamera.databinding.ItemStampSettingBinding
import com.idp.geostampcamera.domain.model.StampTemplate

class StampToggleAdapter(
    private val onChange: (StampTemplate) -> Unit
) : RecyclerView.Adapter<StampToggleAdapter.ViewHolder>() {

    data class Toggle(
        val key: String,
        val labelResId: Int,
        val iconResId: Int,
        val read: (StampTemplate) -> Boolean,
        val write: (StampTemplate, Boolean) -> StampTemplate
    )

    private var template: StampTemplate? = null
    private val baseToggles: List<Toggle> = listOf(
        Toggle("map", com.idp.geostampcamera.R.string.editor_show_map, com.idp.geostampcamera.R.drawable.ic_map,
            { it.showMap }, { t, v -> t.copy(showMap = v) }),
        Toggle("address", com.idp.geostampcamera.R.string.editor_show_address, com.idp.geostampcamera.R.drawable.ic_location,
            { it.showShortAddress }, { t, v -> t.copy(showShortAddress = v, showFullAddress = v) }),
        Toggle("coords", com.idp.geostampcamera.R.string.editor_show_coordinates, com.idp.geostampcamera.R.drawable.ic_compass,
            { it.showCoordinates }, { t, v -> t.copy(showCoordinates = v) }),
        Toggle("pluscode", com.idp.geostampcamera.R.string.editor_show_plus_code, com.idp.geostampcamera.R.drawable.ic_qr,
            { it.showPlusCode }, { t, v -> t.copy(showPlusCode = v) }),
        Toggle("datetime", com.idp.geostampcamera.R.string.editor_show_datetime, com.idp.geostampcamera.R.drawable.ic_timer,
            { it.showDateTime }, { t, v -> t.copy(showDateTime = v) }),
        Toggle("altitude", com.idp.geostampcamera.R.string.editor_show_altitude, com.idp.geostampcamera.R.drawable.ic_info,
            { it.showAltitude }, { t, v -> t.copy(showAltitude = v) }),
        Toggle("accuracy", com.idp.geostampcamera.R.string.editor_show_accuracy, com.idp.geostampcamera.R.drawable.ic_info,
            { it.showAccuracy }, { t, v -> t.copy(showAccuracy = v) }),
        Toggle("speed", com.idp.geostampcamera.R.string.editor_show_speed, com.idp.geostampcamera.R.drawable.ic_arrow_right,
            { it.showSpeed }, { t, v -> t.copy(showSpeed = v) }),
        Toggle("bearing", com.idp.geostampcamera.R.string.editor_show_bearing, com.idp.geostampcamera.R.drawable.ic_arrow_right,
            { it.showBearing }, { t, v -> t.copy(showBearing = v) }),
        Toggle("compass", com.idp.geostampcamera.R.string.editor_show_compass, com.idp.geostampcamera.R.drawable.ic_compass,
            { it.showCompass }, { t, v -> t.copy(showCompass = v) }),
        Toggle("magnetic", com.idp.geostampcamera.R.string.editor_show_magnetic, com.idp.geostampcamera.R.drawable.ic_compass,
            { it.showMagnetic }, { t, v -> t.copy(showMagnetic = v) }),
        Toggle("weather", com.idp.geostampcamera.R.string.editor_show_weather, com.idp.geostampcamera.R.drawable.ic_weather,
            { it.showWeather }, { t, v -> t.copy(showWeather = v) }),
        Toggle("logo", com.idp.geostampcamera.R.string.editor_show_logo, com.idp.geostampcamera.R.drawable.ic_gallery,
            { it.showLogo }, { t, v -> t.copy(showLogo = v) }),
        Toggle("note", com.idp.geostampcamera.R.string.editor_show_note, com.idp.geostampcamera.R.drawable.ic_edit,
            { it.showNote }, { t, v -> t.copy(showNote = v) })
    )

    private val orderedToggles: MutableList<Toggle> = baseToggles.toMutableList()

    fun bind(template: StampTemplate) {
        this.template = template
        val keys = template.fieldOrder.split(',').filter { it.isNotBlank() }
        val sorted = baseToggles.sortedBy {
            val idx = keys.indexOf(it.key)
            if (idx < 0) Int.MAX_VALUE else idx
        }
        orderedToggles.clear()
        orderedToggles.addAll(sorted)
        notifyDataSetChanged()
    }

    private fun swap(from: Int, to: Int) {
        val item = orderedToggles.removeAt(from)
        orderedToggles.add(to, item)
        notifyItemMoved(from, to)
        val current = template ?: return
        val keys = orderedToggles.joinToString(",") { it.key }
        val updated = current.copy(fieldOrder = keys)
        template = updated
        onChange(updated)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStampSettingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }
    override fun getItemCount(): Int = orderedToggles.size
    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(orderedToggles[position])

    inner class ViewHolder(private val binding: ItemStampSettingBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(toggle: Toggle) {
            val current = template ?: return
            binding.stampSettingIcon.setImageResource(toggle.iconResId)
            binding.stampSettingLabel.setText(toggle.labelResId)
            binding.stampSettingSwitch.setOnCheckedChangeListener(null)
            binding.stampSettingSwitch.isChecked = toggle.read(current)
            binding.stampSettingSwitch.setOnCheckedChangeListener { _, isChecked ->
                val updated = toggle.write(current, isChecked)
                template = updated
                onChange(updated)
            }
        }
    }

    fun attachDragHelper(recyclerView: RecyclerView) {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                swap(vh.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            override fun isLongPressDragEnabled(): Boolean = true
        }
        ItemTouchHelper(callback).attachToRecyclerView(recyclerView)
    }
}
