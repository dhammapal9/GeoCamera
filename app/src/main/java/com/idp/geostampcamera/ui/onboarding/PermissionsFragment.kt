package com.idp.geostampcamera.ui.onboarding

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.idp.geostampcamera.R
import com.idp.geostampcamera.databinding.FragmentPermissionsBinding
import com.idp.geostampcamera.util.PermissionUtils
import com.idp.geostampcamera.util.setDebouncedClickListener

class PermissionsFragment : Fragment() {

    private var _binding: FragmentPermissionsBinding? = null
    private val binding get() = _binding!!

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            refreshStatus()
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPermissionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        refreshStatus()
        binding.btnAllow.setDebouncedClickListener {
            if (allGranted()) {
                findNavController().navigate(R.id.action_global_cameraFragment)
            } else {
                requestPermissions.launch(arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
        }
    }

    override fun onResume() { super.onResume(); refreshStatus() }

    private fun allGranted(): Boolean =
        PermissionUtils.hasCamera(requireContext()) && PermissionUtils.hasAnyLocation(requireContext())

    private fun refreshStatus() {
        val cam = PermissionUtils.hasCamera(requireContext())
        val loc = PermissionUtils.hasAnyLocation(requireContext())
        binding.cameraStatus.text = getString(if (cam) R.string.perm_status_allowed else R.string.perm_status_needed)
        binding.cameraStatus.setTextColor(
            androidx.core.content.ContextCompat.getColor(requireContext(),
                if (cam) R.color.success_500 else R.color.warning_500)
        )
        binding.locationStatus.text = getString(if (loc) R.string.perm_status_allowed else R.string.perm_status_needed)
        binding.locationStatus.setTextColor(
            androidx.core.content.ContextCompat.getColor(requireContext(),
                if (loc) R.color.success_500 else R.color.warning_500)
        )
        binding.btnAllow.setText(if (allGranted()) R.string.perm_open_camera else R.string.perm_allow)
    }
    override fun onViewStateRestored(savedInstanceState: android.os.Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        com.idp.geostampcamera.ui.common.AdBanner.install(this)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
