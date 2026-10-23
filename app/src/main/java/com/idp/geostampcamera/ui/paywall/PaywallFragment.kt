package com.idp.geostampcamera.ui.paywall

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.idp.geostampcamera.R
import com.idp.geostampcamera.data.repository.SettingsRepository
import com.idp.geostampcamera.databinding.FragmentPaywallBinding
import com.idp.geostampcamera.util.setDebouncedClickListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PaywallFragment : Fragment() {

    private var _binding: FragmentPaywallBinding? = null
    private val binding get() = _binding!!
    @Inject lateinit var settings: SettingsRepository

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPaywallBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.paywallToolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        bindBullet(binding.bullet1.root, R.string.paywall_bullet_adfree)
        bindBullet(binding.bullet2.root, R.string.paywall_bullet_templates)
        bindBullet(binding.bullet3.root, R.string.paywall_bullet_drive)
        bindBullet(binding.bullet4.root, R.string.paywall_bullet_pdf)
        bindBullet(binding.bullet5.root, R.string.paywall_bullet_video)
        binding.btnMonthly.setDebouncedClickListener {
            Toast.makeText(requireContext(), R.string.paywall_restore, Toast.LENGTH_SHORT).show()
        }
        binding.btnLifetime.setDebouncedClickListener {
            Toast.makeText(requireContext(), R.string.paywall_restore, Toast.LENGTH_SHORT).show()
        }
        binding.btnRestore.setDebouncedClickListener {
            Toast.makeText(requireContext(), R.string.paywall_restore, Toast.LENGTH_SHORT).show()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            binding.switchSimulatePro.isChecked = settings.isProFlow().first()
            binding.switchSimulatePro.setOnCheckedChangeListener { _, isChecked ->
                viewLifecycleOwner.lifecycleScope.launch { settings.setIsPro(isChecked) }
            }
        }
    }

    private fun bindBullet(root: View, labelRes: Int) {
        root.findViewById<TextView>(R.id.bulletText).setText(labelRes)
    }
    override fun onViewStateRestored(savedInstanceState: android.os.Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        com.idp.geostampcamera.ui.common.AdBanner.install(this)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
