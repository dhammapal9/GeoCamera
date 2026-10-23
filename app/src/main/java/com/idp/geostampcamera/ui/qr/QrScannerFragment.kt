package com.idp.geostampcamera.ui.qr

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.idp.geostampcamera.R
import com.idp.geostampcamera.databinding.FragmentQrScannerBinding
import com.idp.geostampcamera.qr.QrAnalyzer
import com.idp.geostampcamera.util.PermissionUtils
import com.idp.geostampcamera.util.setDebouncedClickListener
import java.util.concurrent.Executors

class QrScannerFragment : Fragment() {

    private var _binding: FragmentQrScannerBinding? = null
    private val binding get() = _binding!!
    private var torchOn: Boolean = false
    private var camera: androidx.camera.core.Camera? = null
    private var hasDetected = false

    private val requestCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else {
            Toast.makeText(requireContext(), R.string.perm_denied_body, Toast.LENGTH_LONG).show()
            findNavController().popBackStack()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentQrScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.qrToolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        binding.btnTorch.setDebouncedClickListener { toggleTorch() }
        if (PermissionUtils.hasCamera(requireContext())) startCamera()
        else requestCamera.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(requireContext())
        future.addListener({
            val provider = runCatching { future.get() }.getOrNull() ?: return@addListener
            val preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build().also {
                it.setSurfaceProvider(binding.qrPreview.surfaceProvider)
            }
            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also { analysis ->
                    analysis.setAnalyzer(Executors.newSingleThreadExecutor(), QrAnalyzer { value ->
                        if (!hasDetected) {
                            hasDetected = true
                            binding.qrFrame.post { showResult(value) }
                        }
                    })
                }
            provider.unbindAll()
            camera = provider.bindToLifecycle(
                viewLifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA, preview, analyzer
            )
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun toggleTorch() {
        val cam = camera ?: return
        if (cam.cameraInfo.hasFlashUnit()) {
            torchOn = !torchOn
            cam.cameraControl.enableTorch(torchOn)
        }
    }

    private fun showResult(value: String) {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.qr_title)
            .setMessage(value)
            .setPositiveButton(R.string.qr_result_copy) { _, _ ->
                val cb = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cb.setPrimaryClip(ClipData.newPlainText("qr", value))
                Toast.makeText(requireContext(), R.string.common_copied, Toast.LENGTH_SHORT).show()
                hasDetected = false
            }
            .setNegativeButton(R.string.qr_result_share) { _, _ ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, value)
                }
                startActivity(Intent.createChooser(send, getString(R.string.common_share)))
                hasDetected = false
            }
            .setNeutralButton(R.string.qr_result_open) { _, _ ->
                val uri = runCatching { Uri.parse(value) }.getOrNull()
                if (uri != null && (uri.scheme == "https" || uri.scheme == "http")) {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                } else {
                    Toast.makeText(requireContext(), R.string.common_not_available, Toast.LENGTH_SHORT).show()
                }
                hasDetected = false
            }
            .setOnDismissListener { hasDetected = false }
            .create()
        dialog.show()
    }
    override fun onViewStateRestored(savedInstanceState: android.os.Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        com.idp.geostampcamera.ui.common.AdBanner.install(this)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
