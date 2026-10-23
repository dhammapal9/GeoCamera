package com.idp.geostampcamera.ui.gallery

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.idp.geostampcamera.R
import com.idp.geostampcamera.databinding.FragmentGalleryBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GalleryFragment : Fragment() {
    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GalleryViewModel by viewModels()
    private var gridView: Boolean = true
    private lateinit var videoAdapter: VideoGalleryAdapter
    private var currentTab: Int = TAB_PHOTOS
    @javax.inject.Inject lateinit var adsManager: com.idp.geostampcamera.ads.AdsManager

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { viewModel.exitSelection() }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        binding.galleryToolbar.inflateMenu(R.menu.gallery)
        binding.galleryToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_toggle_view -> {
                    gridView = !gridView
                    item.setIcon(if (gridView) R.drawable.ic_grid else R.drawable.ic_gallery)
                    applyLayoutMode()
                    true
                }
                R.id.action_sort -> { showSortMenu(binding.galleryToolbar); true }
                else -> false
            }
        }

        binding.gallerySelectionBar.setNavigationOnClickListener { viewModel.exitSelection() }
        binding.gallerySelectionBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_selection_select_all -> {
                    val adapter = binding.galleryGrid.adapter as? GallerySectionedAdapter
                    if (currentTab == TAB_PHOTOS && adapter != null) {
                        viewModel.selectAllPhotos(adapter.allPhotoIds())
                    } else if (currentTab == TAB_VIDEOS) {
                        viewModel.selectAllVideos(videoAdapter.allVideoIds())
                    }
                    true
                }
                R.id.action_selection_delete -> { confirmBulkDelete(); true }
                else -> false
            }
        }

        val photoAdapter = GallerySectionedAdapter(
            adsManager = adsManager,
            adInterval = 6,
            onClick = { photo ->
                findNavController().navigate(
                    R.id.action_gallery_to_photoViewer,
                    Bundle().apply { putLong("photoId", photo.id) }
                )
            },
            onLongClick = { photo -> viewModel.togglePhotoSelection(photo.id) }
        )
        binding.galleryGrid.adapter = photoAdapter
        applyLayoutMode()
        binding.galleryRefresh.setOnRefreshListener {
            if (currentTab == TAB_VIDEOS) loadVideos()
            binding.galleryRefresh.isRefreshing = false
        }

        videoAdapter = VideoGalleryAdapter(
            onClick = { video -> playVideo(video) },
            onLongClick = { video -> viewModel.toggleVideoSelection(video.id) }
        )
        binding.videosGrid.adapter = videoAdapter
        binding.videosGrid.layoutManager = GridLayoutManager(requireContext(), 2)

        binding.galleryTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = tab.position
                // Switching tabs during multi-select exits selection so the user isn't left
                // with a hidden selection in the other tab.
                viewModel.exitSelection()
                switchTab(tab.position)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {
                if (tab.position == TAB_VIDEOS) loadVideos()
            }
        })
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { viewModel.search(s?.toString().orEmpty()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.photos.collect { photos ->
                        photoAdapter.submit(photos)
                        binding.galleryEmpty.visibility =
                            if (photos.isEmpty() && currentTab == TAB_PHOTOS) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.selectionScope.collect { scope ->
                        val inSelection = scope != null
                        backCallback.isEnabled = inSelection
                        binding.gallerySelectionBar.visibility = if (inSelection) View.VISIBLE else View.GONE
                        binding.galleryToolbar.visibility = if (inSelection) View.GONE else View.VISIBLE
                        photoAdapter.selectionMode = scope == GalleryViewModel.Scope.PHOTOS
                        videoAdapter.selectionMode = scope == GalleryViewModel.Scope.VIDEOS
                    }
                }
                launch {
                    viewModel.selectedPhotoIds.collect { ids ->
                        photoAdapter.submitSelection(ids)
                        updateSelectionTitle()
                    }
                }
                launch {
                    viewModel.selectedVideoIds.collect { ids ->
                        videoAdapter.submitSelection(ids)
                        updateSelectionTitle()
                    }
                }
            }
        }
    }

    private fun updateSelectionTitle() {
        val count = when (viewModel.selectionScope.value) {
            GalleryViewModel.Scope.PHOTOS -> viewModel.selectedPhotoIds.value.size
            GalleryViewModel.Scope.VIDEOS -> viewModel.selectedVideoIds.value.size
            null -> 0
        }
        binding.gallerySelectionBar.title = getString(R.string.gallery_selection_title, count)
    }

    private fun confirmBulkDelete() {
        val count = when (viewModel.selectionScope.value) {
            GalleryViewModel.Scope.PHOTOS -> viewModel.selectedPhotoIds.value.size
            GalleryViewModel.Scope.VIDEOS -> viewModel.selectedVideoIds.value.size
            null -> 0
        }
        if (count == 0) return
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(getString(R.string.gallery_delete_many_confirm, count))
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.common_delete) { _, _ -> performBulkDelete(count) }
            .show()
    }

    private fun performBulkDelete(expected: Int) {
        when (viewModel.selectionScope.value) {
            GalleryViewModel.Scope.PHOTOS -> {
                viewModel.deleteSelectedPhotos()
                Toast.makeText(requireContext(), getString(R.string.gallery_delete_many_done, expected), Toast.LENGTH_SHORT).show()
            }
            GalleryViewModel.Scope.VIDEOS -> {
                viewModel.deleteSelectedVideos { deleted, _ ->
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), getString(R.string.gallery_delete_many_done, deleted), Toast.LENGTH_SHORT).show()
                        loadVideos()
                    }
                }
            }
            null -> {}
        }
    }

    private fun applyLayoutMode() {
        val adapter = binding.galleryGrid.adapter as? GallerySectionedAdapter ?: return
        if (gridView) {
            val lm = GridLayoutManager(requireContext(), 2)
            lm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int =
                    if (adapter.isHeaderAt(position) || adapter.isNativeAdAt(position)) 2 else 1
            }
            binding.galleryGrid.layoutManager = lm
        } else {
            binding.galleryGrid.layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun switchTab(position: Int) {
        val showingVideos = position == TAB_VIDEOS
        binding.galleryGrid.visibility = if (showingVideos) View.GONE else View.VISIBLE
        binding.videosGrid.visibility = if (showingVideos) View.VISIBLE else View.GONE
        binding.searchLayout.visibility = if (showingVideos) View.GONE else View.VISIBLE
        if (showingVideos) {
            binding.galleryEmpty.visibility = View.GONE
            loadVideos()
        } else {
            binding.videosEmpty.visibility = View.GONE
        }
    }

    private fun loadVideos() {
        viewLifecycleOwner.lifecycleScope.launch {
            val videos = runCatching { VideoLibrary.loadGeoStampVideos(requireContext()) }.getOrDefault(emptyList())
            if (_binding == null) return@launch
            videoAdapter.submitList(videos)
            binding.videosEmpty.visibility = if (videos.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun playVideo(video: VideoItem) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(video.uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        }
    }

    private fun showSortMenu(anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menu.add(0, 0, 0, R.string.gallery_sort_newest)
            menu.add(0, 1, 1, R.string.gallery_sort_oldest)
            setOnMenuItemClickListener { item ->
                viewModel.setSort(if (item.itemId == 1) GalleryViewModel.Sort.OLDEST else GalleryViewModel.Sort.NEWEST)
                true
            }
        }.show()
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        com.idp.geostampcamera.ui.common.AdBanner.install(this)
    }

    override fun onDestroyView() {
        (binding.galleryGrid.adapter as? GallerySectionedAdapter)?.destroy()
        videoAdapter.destroy()
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val TAB_PHOTOS = 0
        const val TAB_VIDEOS = 1
    }
}
