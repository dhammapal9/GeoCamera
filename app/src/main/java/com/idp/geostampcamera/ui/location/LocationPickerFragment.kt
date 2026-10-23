package com.idp.geostampcamera.ui.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.idp.geostampcamera.R
import com.idp.geostampcamera.databinding.FragmentLocationPickerBinding
import com.idp.geostampcamera.location.LocationRepository
import com.idp.geostampcamera.util.CoordinateFormatter
import com.idp.geostampcamera.util.setDebouncedClickListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LocationPickerFragment : Fragment(), OnMapReadyCallback {

    @Inject lateinit var locationRepository: LocationRepository

    private var _binding: FragmentLocationPickerBinding? = null
    private val binding get() = _binding!!
    private var map: GoogleMap? = null
    private var selected: LatLng? = null
    private var selectionMarker: Marker? = null
    private var selectionHalo: Circle? = null
    private var geocodeJob: kotlinx.coroutines.Job? = null

    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants.values.any { it }
        if (granted) {
            enableMyLocationLayer()
            fetchAndApplyCurrentLocation()
        } else {
            Toast.makeText(requireContext(), R.string.perm_location_card_body, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLocationPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.pickerToolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        (childFragmentManager.findFragmentById(R.id.pickerMap) as? SupportMapFragment)
            ?.getMapAsync(this)

        binding.btnCurrent.setDebouncedClickListener {
            if (hasLocationPermission()) {
                fetchAndApplyCurrentLocation()
            } else {
                requestLocationPermission.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
        binding.btnUse.setDebouncedClickListener {
            val typedLat = binding.inputLat.text?.toString()?.trim().orEmpty()
            val typedLng = binding.inputLng.text?.toString()?.trim().orEmpty()
            val typed = CoordinateFormatter.parseDecimal(typedLat, typedLng)
            // Typed coordinates take precedence over any tap-selected point so the user can
            // override a tap by editing the fields.
            val chosen = typed?.let { LatLng(it.first, it.second) } ?: selected
            if (chosen == null) {
                Toast.makeText(requireContext(), R.string.location_picker_invalid, Toast.LENGTH_SHORT).show()
                return@setDebouncedClickListener
            }
            findNavController().previousBackStackEntry?.savedStateHandle?.set(
                "manualLocation", doubleArrayOf(chosen.latitude, chosen.longitude)
            )
            findNavController().popBackStack()
        }
    }

    override fun onMapReady(gmap: GoogleMap) {
        map = gmap
        gmap.uiSettings.isMapToolbarEnabled = false
        gmap.uiSettings.isZoomControlsEnabled = true
        val initial = LatLng(20.5937, 78.9629)
        gmap.moveCamera(CameraUpdateFactory.newLatLngZoom(initial, 4f))
        gmap.setOnMapClickListener { latLng -> applySelection(latLng, zoom = null) }
        if (hasLocationPermission()) enableMyLocationLayer()
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocationLayer() {
        val gmap = map ?: return
        if (hasLocationPermission()) {
            gmap.isMyLocationEnabled = true
            gmap.uiSettings.isMyLocationButtonEnabled = true
        }
    }

    private fun fetchAndApplyCurrentLocation() {
        viewLifecycleOwner.lifecycleScope.launch {
            val snap = runCatching { locationRepository.currentLocation() }.getOrNull()
            if (snap == null) {
                Toast.makeText(requireContext(), R.string.camera_location_waiting, Toast.LENGTH_SHORT).show()
                return@launch
            }
            applySelection(LatLng(snap.latitude, snap.longitude), zoom = 16f)
        }
    }

    private fun applySelection(latLng: LatLng, zoom: Float?) {
        selected = latLng
        map?.let { gmap ->
            // Remove previous overlays — keep the my-location blue dot (that's a UI setting).
            selectionMarker?.remove()
            selectionHalo?.remove()
            // Translucent highlight circle underneath the marker for visual emphasis.
            selectionHalo = gmap.addCircle(
                CircleOptions()
                    .center(latLng)
                    .radius(circleRadiusFor(gmap.cameraPosition.zoom))
                    .strokeColor(0x66F2B134.toInt())
                    .strokeWidth(4f)
                    .fillColor(0x33F2B134)
            )
            // Orange marker with info window showing the formatted lat/lng.
            selectionMarker = gmap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(formatLatLng(latLng))
                    .snippet(getString(R.string.location_picker_tap_hint))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
            )
            selectionMarker?.showInfoWindow()
            if (zoom != null) gmap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom))
            else gmap.animateCamera(CameraUpdateFactory.newLatLng(latLng))
        }
        binding.inputLat.setText("%.6f".format(latLng.latitude))
        binding.inputLng.setText("%.6f".format(latLng.longitude))
        // Kick off a reverse geocode so the info window gets a human-readable address.
        geocodeJob?.cancel()
        geocodeJob = viewLifecycleOwner.lifecycleScope.launch {
            val address = runCatching {
                locationRepository.reverseGeocode(latLng.latitude, latLng.longitude)
            }.getOrNull()
            if (address != null) {
                val short = address.shortAddress ?: address.fullAddress
                if (!short.isNullOrBlank()) {
                    selectionMarker?.title = short
                    selectionMarker?.snippet = formatLatLng(latLng)
                    selectionMarker?.showInfoWindow()
                }
            }
        }
    }

    /** Circle radius in metres scaled to the current zoom level — stays visible without
     *  swallowing the whole viewport at high zooms. */
    private fun circleRadiusFor(zoom: Float): Double = when {
        zoom >= 16f -> 20.0
        zoom >= 13f -> 80.0
        zoom >= 10f -> 400.0
        else -> 2000.0
    }

    private fun formatLatLng(latLng: LatLng): String =
        "%.6f, %.6f".format(latLng.latitude, latLng.longitude)

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

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
