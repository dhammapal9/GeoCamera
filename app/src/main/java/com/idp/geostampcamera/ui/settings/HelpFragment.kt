package com.idp.geostampcamera.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.idp.geostampcamera.databinding.FragmentHelpBinding

class HelpFragment : Fragment() {
    private var _binding: FragmentHelpBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHelpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.helpToolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        // The main bottom nav is hidden on this screen, so the fragment now extends
        // to the system nav bar. Pad the root so the ad container sits above the
        // gesture/3-button bar instead of under it.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, bars.bottom)
            insets
        }
        // Apply the current insets synchronously too — on first navigation to this
        // fragment the activity has already consumed the initial inset dispatch,
        // so without this the listener above would not fire until a config change
        // (rotation, theme toggle) and the ad would briefly overlap the nav bar.
        ViewCompat.getRootWindowInsets(binding.root)?.let { insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.root.setPadding(
                binding.root.paddingLeft,
                binding.root.paddingTop,
                binding.root.paddingRight,
                bars.bottom
            )
        }
        ViewCompat.requestApplyInsets(binding.root)
    }
    override fun onViewStateRestored(savedInstanceState: android.os.Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        com.idp.geostampcamera.ui.common.AdBanner.install(this)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
