package com.idp.geostampcamera.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.idp.geostampcamera.databinding.ItemSettingHeaderBinding
import com.idp.geostampcamera.databinding.ItemSettingRowBinding
import com.idp.geostampcamera.util.setDebouncedClickListener

class SettingsAdapter(
    private val onToggle: (SettingsRow, Boolean) -> Unit,
    private val onPick: (SettingsRow) -> Unit,
    private val onAction: (SettingsRow) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    data class Entry(val row: SettingsRow?, val header: String?)
    private val items = mutableListOf<Entry>()

    fun submit(sections: List<Pair<String, List<SettingsRow>>>) {
        items.clear()
        sections.forEach { (title, rows) ->
            items.add(Entry(null, title))
            rows.forEach { items.add(Entry(it, null)) }
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = if (items[position].header != null) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
        0 -> HeaderVH(ItemSettingHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        else -> RowVH(ItemSettingRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val entry = items[position]
        when (holder) {
            is HeaderVH -> holder.binding.sectionTitle.text = entry.header
            is RowVH -> holder.bind(entry.row!!)
        }
    }

    inner class HeaderVH(val binding: ItemSettingHeaderBinding) : RecyclerView.ViewHolder(binding.root)

    inner class RowVH(val binding: ItemSettingRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: SettingsRow) {
            binding.settingIcon.setImageResource(row.iconRes)
            binding.settingTitle.text = row.title
            binding.settingSummary.visibility = if (row.summary.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE
            binding.settingSummary.text = row.summary
            binding.settingValue.text = row.valueLabel.orEmpty()
            if (row.toggle != null) {
                binding.settingSwitch.visibility = android.view.View.VISIBLE
                binding.settingArrow.visibility = android.view.View.GONE
                binding.settingSwitch.setOnCheckedChangeListener(null)
                binding.settingSwitch.isChecked = row.toggle
                binding.settingSwitch.setOnCheckedChangeListener { _, isChecked -> onToggle(row, isChecked) }
                binding.root.setDebouncedClickListener { binding.settingSwitch.isChecked = !binding.settingSwitch.isChecked }
            } else {
                binding.settingSwitch.visibility = android.view.View.GONE
                binding.settingArrow.visibility = android.view.View.VISIBLE
                binding.root.setDebouncedClickListener {
                    if (row.choices != null) onPick(row) else onAction(row)
                }
            }
        }
    }
}
