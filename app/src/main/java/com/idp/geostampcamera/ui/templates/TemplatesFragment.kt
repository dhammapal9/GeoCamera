package com.idp.geostampcamera.ui.templates

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.idp.geostampcamera.R
import com.idp.geostampcamera.databinding.FragmentTemplatesBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TemplatesFragment : Fragment() {
    private var _binding: FragmentTemplatesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TemplatesViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTemplatesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.templatesToolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        val adapter = TemplatesAdapter(
            onApply = { template ->
                // Persist as the default so CameraViewModel picks it up on activation.
                viewModel.apply(template)
                findNavController().navigate(R.id.cameraFragment)
            },
            onEdit = { template ->
                findNavController().navigate(
                    R.id.action_templates_to_editor,
                    Bundle().apply { putString("templateId", template.id) }
                )
            },
            onDuplicate = { template ->
                val locName = template.getLocalizedName(requireContext())
                val input = EditText(requireContext()).apply {
                    setText("$locName copy")
                }
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.common_duplicate)
                    .setView(input)
                    .setPositiveButton(R.string.common_save) { _, _ ->
                        viewModel.duplicate(template, input.text.toString().ifBlank { locName })
                    }
                    .setNegativeButton(R.string.common_cancel, null)
                    .show()
            },
            onDelete = { template ->
                MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.gallery_delete_confirm)
                    .setPositiveButton(R.string.common_delete) { _, _ -> viewModel.delete(template) }
                    .setNegativeButton(R.string.common_cancel, null)
                    .show()
            }
        )
        binding.templatesList.layoutManager = LinearLayoutManager(requireContext())
        binding.templatesList.adapter = adapter
        binding.addTemplate.setOnClickListener {
            findNavController().navigate(R.id.action_templates_to_editor)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.templates.collect { adapter.submit(it) }
        }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        com.idp.geostampcamera.ui.common.AdBanner.install(this)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
