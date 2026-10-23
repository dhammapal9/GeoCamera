package com.idp.geostampcamera.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.annotation.DrawableRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.idp.geostampcamera.R
import com.idp.geostampcamera.databinding.FragmentHomeBinding
import com.idp.geostampcamera.ui.gallery.RecentPhotosAdapter
import com.idp.geostampcamera.ui.templates.HomeTemplatesAdapter
import com.idp.geostampcamera.util.CoordinateFormatter
import com.idp.geostampcamera.util.DateFormatter
import com.idp.geostampcamera.util.setDebouncedClickListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recentAdapter = RecentPhotosAdapter { photo ->
            // Navigate directly to the photoViewer destination — the gallery-scoped action
            // `action_gallery_to_photoViewer` is only valid from the gallery fragment.
            findNavController().navigate(
                R.id.photoViewerFragment,
                android.os.Bundle().apply { putLong("photoId", photo.id) }
            )
        }
        val templatesAdapter = HomeTemplatesAdapter { _ ->
            findNavController().navigate(R.id.action_global_cameraFragment)
        }
        binding.homeRecentList.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.homeRecentList.adapter = recentAdapter
        binding.homeTemplatesList.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.homeTemplatesList.adapter = templatesAdapter

        // Staggered entrance: each section slides-up + fades in one after another.
        playEntranceAnimation()

        binding.openCameraButton.setDebouncedClickListener {
            // Little scale-pop for the button itself when tapped.
            it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).withEndAction {
                it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                findNavController().navigate(R.id.action_global_cameraFragment)
            }.start()
        }
        binding.seeAllPhotos.setDebouncedClickListener {
            findNavController().navigate(R.id.action_global_gallery)
        }

        bindShortcut(
            binding.shortcutGallery.root,
            R.drawable.ic_gallery,
            R.string.home_shortcut_gallery,
            R.drawable.bg_shortcut_tile_teal
        ) { findNavController().navigate(R.id.action_global_gallery) }
        bindShortcut(
            binding.shortcutMap.root,
            R.drawable.ic_map,
            R.string.home_shortcut_map_view,
            R.drawable.bg_shortcut_tile_amber
        ) { findNavController().navigate(R.id.action_global_mapView) }
        bindShortcut(
            binding.shortcutQr.root,
            R.drawable.ic_qr,
            R.string.home_shortcut_qr,
            R.drawable.bg_shortcut_tile_pink
        ) { findNavController().navigate(R.id.action_global_qr) }
        bindShortcut(
            binding.shortcutSettings.root,
            R.drawable.ic_settings,
            R.string.home_shortcut_settings,
            R.drawable.bg_shortcut_tile_violet
        ) { findNavController().navigate(R.id.action_global_settings) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.recent.collect {
                        recentAdapter.submit(it)
                        binding.homeRecentEmpty.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch { viewModel.templates.collect { templatesAdapter.submit(it) } }
                launch {
                    viewModel.lastLocation.collect { triple ->
                        if (triple != null) {
                            val (lat, lng, ts) = triple
                            binding.lastLocationText.text = CoordinateFormatter.formatDecimal(lat, lng) +
                                "\n" + DateFormatter.format(ts, DateFormatter.FORMAT_SHORT)
                            val isStale = System.currentTimeMillis() - ts > 10 * 60_000
                            binding.lastLocationStale.visibility = if (isStale) View.VISIBLE else View.GONE
                        } else {
                            binding.lastLocationText.setText(R.string.camera_location_waiting)
                            binding.lastLocationStale.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun bindShortcut(
        root: View,
        iconRes: Int,
        labelRes: Int,
        @DrawableRes tileBackgroundRes: Int,
        action: () -> Unit
    ) {
        val tile = root.findViewById<View>(R.id.shortcutTile)
        val icon = root.findViewById<android.widget.ImageView>(R.id.shortcutIcon)
        val label = root.findViewById<android.widget.TextView>(R.id.shortcutLabel)
        tile.setBackgroundResource(tileBackgroundRes)
        icon.setImageResource(iconRes)
        label.setText(labelRes)
        root.setDebouncedClickListener {
            // Satisfying scale-pop before navigating.
            root.animate().scaleX(0.94f).scaleY(0.94f).setDuration(80).withEndAction {
                root.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                action()
            }.start()
        }
    }

    /**
     * Staggers the slide-up fade-in across sections so the home screen reveals itself
     * section by section — hero first, then each row ~60ms behind the previous.
     */
    private fun playEntranceAnimation() {
        val ctx = requireContext()
        val targets = listOf(
            binding.homeHeaderRow,
            binding.heroPanel,
            binding.lastLocationHeader,
            binding.lastLocationCard,
            binding.shortcutsHeader,
            binding.shortcutsRow,
            binding.templatesHeader,
            binding.homeTemplatesList,
            binding.recentHeader,
            binding.homeRecentList
        )
        targets.forEachIndexed { index, view ->
            val anim = AnimationUtils.loadAnimation(ctx, R.anim.slide_up_fade_in)
            anim.startOffset = index * 60L
            view.startAnimation(anim)
        }
    }

    override fun onViewStateRestored(savedInstanceState: android.os.Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        com.idp.geostampcamera.ui.common.AdBanner.install(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
