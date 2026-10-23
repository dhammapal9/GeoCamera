package com.idp.geostampcamera.ui.editor

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.idp.geostampcamera.databinding.FragmentTemplateEditorBinding
import com.idp.geostampcamera.domain.model.AddressDetails
import com.idp.geostampcamera.domain.model.LocationSnapshot
import com.idp.geostampcamera.domain.model.StampContext
import com.idp.geostampcamera.util.setDebouncedClickListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TemplateEditorFragment : Fragment() {

    private var _binding: FragmentTemplateEditorBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TemplateEditorViewModel by viewModels()
    private lateinit var togglesAdapter: StampToggleAdapter

    private val logoPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let { chosen ->
            // Photo picker URIs are only valid for the current session, so copy to app storage.
            val copied = copyLogoToPrivateStorage(chosen)
            if (copied != null) {
                viewModel.update { it.copy(logoUri = copied.toString(), showLogo = true) }
            }
        }
    }

    private fun copyLogoToPrivateStorage(uri: Uri): Uri? = runCatching {
        val dir = java.io.File(requireContext().filesDir, "logos").apply { if (!exists()) mkdirs() }
        val target = java.io.File(dir, "logo_${System.currentTimeMillis()}.png")
        requireContext().contentResolver.openInputStream(uri)?.use { input ->
            java.io.FileOutputStream(target).use { out -> input.copyTo(out) }
        }
        Uri.fromFile(target)
    }.getOrNull()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTemplateEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val templateId = arguments?.getString("templateId")
        viewModel.load(templateId)

        binding.editorToolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        togglesAdapter = StampToggleAdapter { updated ->
            viewModel.update { updated }
        }
        binding.togglesList.layoutManager = LinearLayoutManager(requireContext())
        binding.togglesList.adapter = togglesAdapter
        binding.togglesList.isNestedScrollingEnabled = false
        togglesAdapter.attachDragHelper(binding.togglesList)

        binding.fontGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val family = when (checkedId) {
                com.idp.geostampcamera.R.id.fontSerif -> com.idp.geostampcamera.domain.model.StampTemplate.FontFamily.SERIF
                com.idp.geostampcamera.R.id.fontMono -> com.idp.geostampcamera.domain.model.StampTemplate.FontFamily.MONO
                com.idp.geostampcamera.R.id.fontCondensed -> com.idp.geostampcamera.domain.model.StampTemplate.FontFamily.CONDENSED
                else -> com.idp.geostampcamera.domain.model.StampTemplate.FontFamily.SANS
            }
            viewModel.update { it.copy(fontFamily = family) }
        }

        binding.inputName.addTextChangedListener(simpleWatcher { text ->
            viewModel.update { it.copy(name = text) }
        })
        binding.inputNote.addTextChangedListener(simpleWatcher { text ->
            viewModel.update { it.copy(note = text) }
        })
        binding.inputHashtag.addTextChangedListener(simpleWatcher { text ->
            viewModel.update { it.copy(hashtag = text) }
        })
        binding.inputProject.addTextChangedListener(simpleWatcher { text ->
            viewModel.update { it.copy(projectName = text) }
        })

        binding.btnPickLogo.setDebouncedClickListener {
            logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.btnRemoveLogo.setDebouncedClickListener {
            viewModel.update { it.copy(logoUri = null, showLogo = false) }
        }
        binding.btnSaveTemplate.setDebouncedClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val saved = viewModel.save()
                if (saved != null) findNavController().popBackStack()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.editing.collect { template ->
                template ?: return@collect
                togglesAdapter.bind(template)
                val targetId = when (template.fontFamily) {
                    com.idp.geostampcamera.domain.model.StampTemplate.FontFamily.SERIF -> com.idp.geostampcamera.R.id.fontSerif
                    com.idp.geostampcamera.domain.model.StampTemplate.FontFamily.MONO -> com.idp.geostampcamera.R.id.fontMono
                    com.idp.geostampcamera.domain.model.StampTemplate.FontFamily.CONDENSED -> com.idp.geostampcamera.R.id.fontCondensed
                    else -> com.idp.geostampcamera.R.id.fontSans
                }
                if (binding.fontGroup.checkedButtonId != targetId) {
                    binding.fontGroup.check(targetId)
                }
                val locName = template.getLocalizedName(requireContext())
                if (binding.inputName.text?.toString().orEmpty() != locName)
                    binding.inputName.setText(locName)
                if (binding.inputNote.text?.toString().orEmpty() != template.note)
                    binding.inputNote.setText(template.note)
                if (binding.inputHashtag.text?.toString().orEmpty() != template.hashtag)
                    binding.inputHashtag.setText(template.hashtag)
                if (binding.inputProject.text?.toString().orEmpty() != template.projectName)
                    binding.inputProject.setText(template.projectName)
                // Logo preview tile + Remove button enable state.
                val logoUri = template.logoUri
                if (!logoUri.isNullOrBlank() && template.showLogo) {
                    binding.logoPreview.visibility = View.VISIBLE
                    binding.logoPlaceholder.visibility = View.GONE
                    binding.logoPreview.load(Uri.parse(logoUri))
                    binding.btnRemoveLogo.isEnabled = true
                    binding.btnPickLogo.setText(com.idp.geostampcamera.R.string.editor_change_logo)
                } else {
                    binding.logoPreview.visibility = View.GONE
                    binding.logoPlaceholder.visibility = View.VISIBLE
                    binding.logoPreview.setImageDrawable(null)
                    binding.btnRemoveLogo.isEnabled = false
                    binding.btnPickLogo.setText(com.idp.geostampcamera.R.string.editor_add_logo)
                }
                val stamp = StampContext(
                    template = template,
                    captureAtMillis = System.currentTimeMillis(),
                    location = SAMPLE, compass = null, magnetic = null, weather = null, mapBitmap = null
                )
                binding.livePreview.bind(stamp)
            }
        }
    }

    private fun simpleWatcher(onChanged: (String) -> Unit) = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) { onChanged(s?.toString().orEmpty()) }
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }
    override fun onViewStateRestored(savedInstanceState: android.os.Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        com.idp.geostampcamera.ui.common.AdBanner.install(this)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }

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
