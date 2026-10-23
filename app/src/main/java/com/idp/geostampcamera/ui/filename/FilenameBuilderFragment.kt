package com.idp.geostampcamera.ui.filename

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.idp.geostampcamera.data.repository.SettingsRepository
import com.idp.geostampcamera.databinding.FragmentFilenameBinding
import com.idp.geostampcamera.databinding.ItemFilenameTokenBinding
import com.idp.geostampcamera.domain.model.CapturedPhoto
import com.idp.geostampcamera.util.FilenameBuilder
import com.idp.geostampcamera.util.setDebouncedClickListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FilenameBuilderFragment : Fragment() {

    private var _binding: FragmentFilenameBinding? = null
    private val binding get() = _binding!!
    @Inject lateinit var settings: SettingsRepository

    private var order: MutableList<FilenameBuilder.Token> = FilenameBuilder.Token.values().toMutableList()
    private val enabled: MutableSet<FilenameBuilder.Token> = mutableSetOf()
    private var custom: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFilenameBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.filenameToolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        viewLifecycleOwner.lifecycleScope.launch {
            val saved = settings.filenameConfigFlow().first()
            if (saved != null) {
                order = saved.first.toMutableList()
                enabled.clear(); enabled.addAll(order)
                custom = saved.second
            } else {
                enabled.clear(); enabled.addAll(FilenameBuilder.DEFAULT.order)
            }
            binding.customInput.setText(custom)
            binding.tokenList.layoutManager = LinearLayoutManager(requireContext())
            val adapter = Adapter()
            binding.tokenList.adapter = adapter
            attachDrag(binding.tokenList, adapter)
            updatePreview()
        }
        binding.customInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { custom = s?.toString().orEmpty(); updatePreview() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        binding.btnSave.setDebouncedClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val ordered = order.filter { enabled.contains(it) }
                settings.setFilenameConfig(ordered, custom)
                findNavController().popBackStack()
            }
        }
    }

    private fun updatePreview() {
        val sample = CapturedPhoto(
            id = 42, originalPath = null, stampedPath = null, publicUri = null,
            timestampMillis = System.currentTimeMillis(),
            latitude = 18.626936, longitude = 73.710798,
            shortAddress = "Jambe", fullAddress = null,
            accuracyMeters = null, altitudeMeters = null,
            templateId = "classic", note = null, projectName = "Site1",
            mapType = null, widthPx = null, heightPx = null,
            status = CapturedPhoto.Status.READY
        )
        val config = FilenameBuilder.Config(order.filter { enabled.contains(it) }, custom)
        binding.filenamePreview.text = FilenameBuilder.build(config, sample, 1) + ".jpg"
    }

    private fun attachDrag(rv: RecyclerView, adapter: Adapter) {
        val cb = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = vh.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                val item = order.removeAt(from)
                order.add(to, item)
                adapter.notifyItemMoved(from, to)
                updatePreview()
                return true
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            override fun isLongPressDragEnabled(): Boolean = true
        }
        ItemTouchHelper(cb).attachToRecyclerView(rv)
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        inner class VH(val b: ItemFilenameTokenBinding) : RecyclerView.ViewHolder(b.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemFilenameTokenBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun getItemCount(): Int = order.size
        override fun onBindViewHolder(holder: VH, position: Int) {
            val token = order[position]
            holder.b.tokenLabel.text = token.label
            holder.b.tokenSwitch.setOnCheckedChangeListener(null)
            holder.b.tokenSwitch.isChecked = enabled.contains(token)
            holder.b.tokenSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) enabled.add(token) else enabled.remove(token)
                updatePreview()
            }
        }
    }
    override fun onViewStateRestored(savedInstanceState: android.os.Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        com.idp.geostampcamera.ui.common.AdBanner.install(this)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
