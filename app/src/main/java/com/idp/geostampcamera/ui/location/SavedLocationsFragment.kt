package com.idp.geostampcamera.ui.location

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.idp.geostampcamera.R
import com.idp.geostampcamera.data.local.SavedLocationEntity
import com.idp.geostampcamera.data.repository.SavedLocationRepository
import com.idp.geostampcamera.databinding.FragmentSavedLocationsBinding
import com.idp.geostampcamera.databinding.ItemSavedLocationBinding
import com.idp.geostampcamera.util.setDebouncedClickListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SavedLocationsFragment : Fragment() {

    private var _binding: FragmentSavedLocationsBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var repository: SavedLocationRepository

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSavedLocationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.savedToolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        val adapter = Adapter(emptyList(),
            onPick = { entry ->
                findNavController().previousBackStackEntry?.savedStateHandle?.set(
                    "manualLocation", doubleArrayOf(entry.latitude, entry.longitude)
                )
                findNavController().popBackStack()
            },
            onDelete = { entry ->
                viewLifecycleOwner.lifecycleScope.launch { repository.delete(entry.id) }
            })
        binding.savedList.layoutManager = LinearLayoutManager(requireContext())
        binding.savedList.adapter = adapter
        binding.btnAdd.setOnClickListener { showAddDialog() }
        viewLifecycleOwner.lifecycleScope.launch {
            repository.observeAll().collect { list ->
                adapter.submit(list)
                binding.savedEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showAddDialog() {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_saved_location, null, false)
        val nameInput = view.findViewById<EditText>(R.id.nameInput)
        val latInput = view.findViewById<EditText>(R.id.latInput)
        val lngInput = view.findViewById<EditText>(R.id.lngInput)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.saved_locations_add)
            .setView(view)
            .setPositiveButton(R.string.common_save) { _, _ ->
                val name = nameInput.text?.toString().orEmpty().ifBlank { getString(R.string.saved_locations_manual) }
                val lat = latInput.text?.toString()?.toDoubleOrNull() ?: return@setPositiveButton
                val lng = lngInput.text?.toString()?.toDoubleOrNull() ?: return@setPositiveButton
                viewLifecycleOwner.lifecycleScope.launch {
                    repository.save(
                        SavedLocationEntity(
                            name = name,
                            latitude = lat,
                            longitude = lng,
                            shortAddress = null,
                            fullAddress = null,
                            createdAtMillis = System.currentTimeMillis()
                        )
                    )
                }
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }
    override fun onViewStateRestored(savedInstanceState: android.os.Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        com.idp.geostampcamera.ui.common.AdBanner.install(this)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    private class Adapter(
        private var items: List<SavedLocationEntity>,
        val onPick: (SavedLocationEntity) -> Unit,
        val onDelete: (SavedLocationEntity) -> Unit
    ) : RecyclerView.Adapter<Adapter.VH>() {

        fun submit(list: List<SavedLocationEntity>) { items = list; notifyDataSetChanged() }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemSavedLocationBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.b.savedName.text = item.name
            holder.b.savedAddress.text = item.shortAddress.orEmpty()
            holder.b.savedCoords.text = com.idp.geostampcamera.util.CoordinateFormatter
                .formatDecimal(item.latitude, item.longitude)
            holder.b.root.setDebouncedClickListener { onPick(item) }
            holder.b.savedDelete.setOnClickListener { onDelete(item) }
        }

        class VH(val b: ItemSavedLocationBinding) : RecyclerView.ViewHolder(b.root)
    }
}
