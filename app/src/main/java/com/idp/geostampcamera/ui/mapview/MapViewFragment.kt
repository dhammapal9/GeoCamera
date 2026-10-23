package com.idp.geostampcamera.ui.mapview

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.idp.geostampcamera.R
import com.idp.geostampcamera.databinding.FragmentMapBinding
import com.idp.geostampcamera.location.LocationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MapViewFragment : Fragment(), OnMapReadyCallback {

    @Inject lateinit var locationRepository: LocationRepository

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MapViewModel by viewModels()
    private var map: GoogleMap? = null
    // First callback to place the camera wins (current-location vs photos-bounds race).
    // The My Location FAB always animates regardless — it's an explicit user action.
    private var cameraPlaced = false

    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            enableMyLocationLayer()
            animateToCurrentLocation()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val host = childFragmentManager.findFragmentById(R.id.mapContainer) as? SupportMapFragment
        if (host == null) {
            binding.mapFallback.visibility = View.VISIBLE
            return
        }
        host.getMapAsync(this)

        binding.btnMyLocation.setOnClickListener {
            if (hasLocationPermission()) {
                enableMyLocationLayer()
                animateToCurrentLocation()
            } else {
                requestLocationPermission.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
        binding.btnMapType.setOnClickListener { anchor -> showMapTypeMenu(anchor) }
        binding.btnZoomIn.setOnClickListener { map?.animateCamera(CameraUpdateFactory.zoomIn()) }
        binding.btnZoomOut.setOnClickListener { map?.animateCamera(CameraUpdateFactory.zoomOut()) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.photos.collect { photos ->
                    val geo = photos.filter { it.latitude != null && it.longitude != null }
                    binding.mapEmptyText.visibility = if (geo.isEmpty()) View.VISIBLE else View.GONE
                    map?.let { m ->
                        m.clear()
                        if (geo.isNotEmpty()) {
                            val builder = LatLngBounds.builder()
                            geo.forEach { p ->
                                val latLng = LatLng(p.latitude!!, p.longitude!!)
                                builder.include(latLng)
                                m.addMarker(
                                    MarkerOptions()
                                        .position(latLng)
                                        .title(p.shortAddress ?: "Photo")
                                        .snippet(p.id.toString())
                                )
                            }
                            // Only auto-fit bounds on the FIRST emission — later photo updates
                            // (new captures, deletions) shouldn't jerk the camera away from
                            // wherever the user has panned/zoomed to.
                            if (!cameraPlaced) {
                                cameraPlaced = true
                                runCatching {
                                    m.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 160))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onMapReady(gmap: GoogleMap) {
        map = gmap
        // Hide the SDK's built-in zoom + my-location controls — we draw our own FABs so they
        // all sit in one neat column on the right instead of overlapping Google's defaults.
        gmap.uiSettings.isZoomControlsEnabled = false
        gmap.uiSettings.isMyLocationButtonEnabled = false
        if (hasLocationPermission()) {
            enableMyLocationLayer()
            // Centre the camera on the user's current location on first open — gated by
            // cameraPlaced so the async photos-bounds fit (if photos exist) can win instead
            // without jarring the user by jumping twice.
            centerOnCurrentLocationIfPending()
        }
        gmap.setOnInfoWindowClickListener { marker ->
            marker.snippet?.toLongOrNull()?.let { id ->
                findNavController().navigate(
                    R.id.photoViewerFragment,
                    Bundle().apply { putLong("photoId", id) }
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocationLayer() {
        val gmap = map ?: return
        if (hasLocationPermission()) gmap.isMyLocationEnabled = true
    }

    /** Explicit user action (My Location FAB / permission grant) — always moves the camera. */
    private fun animateToCurrentLocation() {
        viewLifecycleOwner.lifecycleScope.launch {
            val snap = runCatching { locationRepository.currentLocation() }.getOrNull()
            if (snap == null) {
                Toast.makeText(requireContext(), R.string.map_location_unavailable, Toast.LENGTH_SHORT).show()
                return@launch
            }
            cameraPlaced = true
            map?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(snap.latitude, snap.longitude), 16f)
            )
        }
    }

    /** Called once on map ready — defers to the photos-bounds fit if that lands first. */
    private fun centerOnCurrentLocationIfPending() {
        viewLifecycleOwner.lifecycleScope.launch {
            val snap = runCatching { locationRepository.currentLocation() }.getOrNull() ?: return@launch
            if (cameraPlaced) return@launch
            cameraPlaced = true
            map?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(snap.latitude, snap.longitude), 16f)
            )
        }
    }

    private fun showMapTypeMenu(anchor: View) {
        val gmap = map ?: return
        val popup = PopupMenu(requireContext(), anchor)
        val menu = popup.menu
        val normal = menu.add(0, GoogleMap.MAP_TYPE_NORMAL, 0, R.string.map_type_normal)
        val satellite = menu.add(0, GoogleMap.MAP_TYPE_SATELLITE, 1, R.string.map_type_satellite)
        val terrain = menu.add(0, GoogleMap.MAP_TYPE_TERRAIN, 2, R.string.map_type_terrain)
        val hybrid = menu.add(0, GoogleMap.MAP_TYPE_HYBRID, 3, R.string.map_type_hybrid)
        // Checkmark the current type so the user sees what's active.
        listOf(normal, satellite, terrain, hybrid).forEach { item ->
            item.isCheckable = true
            item.isChecked = item.itemId == gmap.mapType
        }
        popup.setOnMenuItemClickListener { item ->
            gmap.mapType = item.itemId
            true
        }
        popup.show()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        com.idp.geostampcamera.ui.common.AdBanner.install(this)
    }

    override fun onDestroyView() { super.onDestroyView(); map = null; _binding = null }
}
