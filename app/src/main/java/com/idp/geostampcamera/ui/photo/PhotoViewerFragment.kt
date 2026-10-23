package com.idp.geostampcamera.ui.photo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.idp.geostampcamera.R
import com.idp.geostampcamera.data.repository.PhotoRepository
import com.idp.geostampcamera.databinding.FragmentPhotoViewerBinding
import com.idp.geostampcamera.util.CoordinateFormatter
import com.idp.geostampcamera.util.DateFormatter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class PhotoViewerFragment : Fragment() {

    private var _binding: FragmentPhotoViewerBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var photoRepository: PhotoRepository
    private val viewModel: PhotoViewerViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPhotoViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val id = arguments?.getLong("photoId") ?: run {
            findNavController().popBackStack(); return
        }
        viewModel.load(id)
        binding.viewerToolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.photo.collect { photo ->
                photo ?: return@collect
                val stampPath = photo.stampedPath ?: photo.originalPath
                stampPath?.let { binding.photoImage.load(File(it)) }
                binding.infoAddress.text = photo.shortAddress ?: getString(R.string.common_not_available)
                binding.infoCoords.text = if (photo.latitude != null && photo.longitude != null)
                    CoordinateFormatter.formatDecimal(photo.latitude, photo.longitude) else getString(R.string.common_not_available)
                binding.infoTimestamp.text = DateFormatter.format(photo.timestampMillis, DateFormatter.FORMAT_FULL)

                // Enable each tab only when its backing file is present and readable.
                val stampedFileOk = photo.stampedPath?.let { File(it).exists() } == true
                val originalFileOk = photo.originalPath?.let { File(it).exists() } == true
                binding.btnStamped.isEnabled = stampedFileOk
                binding.btnOriginal.isEnabled = originalFileOk
                // Hide the whole toggle bar when only one representation exists.
                binding.toggleBar.visibility =
                    if (stampedFileOk && originalFileOk) View.VISIBLE else View.GONE

                // Clear previous listener before re-arming (collect may run multiple times).
                binding.toggleBar.clearOnButtonCheckedListeners()

                // Pick initial tab based on what actually exists.
                val initialId = when {
                    stampedFileOk -> R.id.btnStamped
                    originalFileOk -> R.id.btnOriginal
                    else -> View.NO_ID
                }
                if (initialId != View.NO_ID && binding.toggleBar.checkedButtonId != initialId) {
                    binding.toggleBar.check(initialId)
                } else {
                    // check(id) only fires on change — load the initial image manually.
                    val path = if (initialId == R.id.btnOriginal) photo.originalPath else photo.stampedPath
                    path?.let { binding.photoImage.load(File(it)) }
                }

                binding.toggleBar.addOnButtonCheckedListener { _, checkedId, isChecked ->
                    if (!isChecked) return@addOnButtonCheckedListener
                    val path = when (checkedId) {
                        R.id.btnOriginal -> photo.originalPath
                        else -> photo.stampedPath
                    }
                    if (path != null && File(path).exists()) {
                        binding.photoImage.load(File(path))
                    }
                }

                binding.viewerToolbar.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_share -> { share(photo.stampedPath ?: photo.originalPath); true }
                        R.id.action_open_map -> {
                            openMap(photo.latitude, photo.longitude); true
                        }
                        R.id.action_delete -> {
                            MaterialAlertDialogBuilder(requireContext())
                                .setMessage(R.string.gallery_delete_confirm)
                                .setPositiveButton(R.string.common_delete) { _, _ ->
                                    viewModel.delete(photo.id)
                                    findNavController().popBackStack()
                                }
                                .setNegativeButton(R.string.common_cancel, null)
                                .show()
                            true
                        }
                        R.id.action_edit -> {
                            viewModel.regenerateWithCurrentDefault()
                            true
                        }
                        else -> false
                    }
                }
            }
        }
    }

    private fun share(path: String?) {
        val file = path?.let { File(it) } ?: return
        if (!file.exists()) {
            Toast.makeText(requireContext(), R.string.error_photo_save_failed, Toast.LENGTH_SHORT).show(); return
        }
        val uri: Uri = FileProvider.getUriForFile(
            requireContext(), requireContext().packageName + ".fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.viewer_share)))
    }

    private fun openMap(lat: Double?, lng: Double?) {
        if (lat == null || lng == null) {
            Toast.makeText(requireContext(), R.string.common_not_available, Toast.LENGTH_SHORT).show(); return
        }
        val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(Captured)")
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        com.idp.geostampcamera.ui.common.AdBanner.install(this)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
