package com.idp.geostampcamera.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.idp.geostampcamera.R
import com.idp.geostampcamera.camera.CameraController
import com.idp.geostampcamera.databinding.FragmentCameraBinding
import com.idp.geostampcamera.domain.model.StampContext
import com.idp.geostampcamera.sensors.LevelSensor
import com.idp.geostampcamera.ui.main.MainActivity
import com.idp.geostampcamera.util.PermissionUtils
import com.idp.geostampcamera.util.setDebouncedClickListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CameraViewModel by viewModels()
    private lateinit var cameraController: CameraController
    @Inject lateinit var levelSensor: LevelSensor
    @Inject lateinit var photoReportGenerator: com.idp.geostampcamera.stamp.PhotoReportGenerator
    @Inject lateinit var photoRepository: com.idp.geostampcamera.data.repository.PhotoRepository
    @Inject lateinit var adsManager: com.idp.geostampcamera.ads.AdsManager
    @Inject lateinit var mapSnapshotProvider: com.idp.geostampcamera.map.MapSnapshotProvider
    @Inject lateinit var settingsRepository: com.idp.geostampcamera.data.repository.SettingsRepository
    @Inject lateinit var photoStampRenderer: com.idp.geostampcamera.stamp.PhotoStampRenderer
    private var countdownJob: kotlinx.coroutines.Job? = null
    private var timerSeconds: Int = 0
    private var captureMode: CaptureMode = CaptureMode.PHOTO
    private var toolRailOpen = false
    // Cached map thumbnail for the stamp preview. We quantize to ~3 decimals (≈110 m) so the
    // snapshot isn't regenerated on every tiny GPS wobble, but updates when the user moves.
    private var cachedMapBitmap: android.graphics.Bitmap? = null
    private var cachedMapKey: String? = null
    private var mapGenerationJob: kotlinx.coroutines.Job? = null

    enum class CaptureMode { PHOTO, VIDEO, REPORT }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCameraIfReady() else showPermissionDenied()
        }
    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { map ->
            if (map.values.any { it }) viewModel.refreshLocation()
        }
    private var pendingVideoStart: Boolean = false
    private var recTimerJob: kotlinx.coroutines.Job? = null
    private var recStartMillis: Long = 0L
    private val requestAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (pendingVideoStart) {
                pendingVideoStart = false
                launchVideo(withAudio = granted)
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraController = CameraController(requireContext(), viewLifecycleOwner, binding.previewView)
        bindClickListeners()
        bindToolRail()
        bindZoomPills()
        bindModeSelector()
        applyEdgeToEdgeInsets()
        // Camera is pinned to portrait in onResume and no element should animate on device
        // rotation — the orientation listener used to translate/rotate stampPreview + zoomGroup
        // is intentionally not started here.
        installCameraBanner()
        if (PermissionUtils.hasCamera(requireContext())) startCameraIfReady()
        else showPermissionRationale()
        if (!PermissionUtils.hasAnyLocation(requireContext())) {
            requestLocationPermission.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        } else viewModel.refreshLocation()
        observeManualLocationResult()
        observeState()
        observeLevel()
    }

    /**
     * LocationPickerFragment and SavedLocationsFragment write "manualLocation" = [lat, lng]
     * into this fragment's NavBackStackEntry.savedStateHandle before popping. Observe it so
     * the camera screen (location chip, stamp preview, map thumbnail) refreshes immediately.
     */
    private fun observeManualLocationResult() {
        val handle = findNavController().currentBackStackEntry?.savedStateHandle ?: return
        handle.getLiveData<DoubleArray>("manualLocation")
            .observe(viewLifecycleOwner) { coords ->
                if (coords == null || coords.size < 2) return@observe
                viewModel.setManualLocation(coords[0], coords[1])
                // Invalidate the cached map thumbnail so the new location re-renders.
                cachedMapBitmap = null
                cachedMapKey = null
                handle.remove<DoubleArray>("manualLocation")
            }
    }

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.let { act ->
            // Pin the window to portrait while the camera is on-screen. The system no longer
            // rotates the whole UI when the device turns; instead the OrientationEventListener
            // installed in onViewCreated applies View.rotation to only the stamp card, zoom
            // pills, and brightness bar so they stay upright for the user.
            act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            // Camera goes true edge-to-edge: suspend the activity-wide top padding.
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(act.window, false)
            val mainRoot = act.findViewById<android.view.View>(com.idp.geostampcamera.R.id.mainRoot)
            mainRoot?.setPadding(0, 0, 0, 0)
            // Replace the activity's inset listener so it stops re-adding the top gap.
            mainRoot?.let { root ->
                androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets -> insets }
                androidx.core.view.ViewCompat.requestApplyInsets(root)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        (activity as? MainActivity)?.let { act ->
            // Release the portrait pin so other screens can rotate normally again.
            act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(act.window, true)
            // Restore the main activity's inset handling (top-padding = status bar).
            val mainRoot = act.findViewById<android.view.View>(com.idp.geostampcamera.R.id.mainRoot) ?: return@let
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(mainRoot) { v, insets ->
                val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                v.setPadding(0, bars.top, 0, 0)
                insets
            }
            androidx.core.view.ViewCompat.requestApplyInsets(mainRoot)
        }
    }

    private fun applyEdgeToEdgeInsets() {
        // Camera is true edge-to-edge. In portrait the nav bar is at the bottom; in landscape
        // it sits on the left or right edge (user-handedness). Pad the top bar for status,
        // pad the ad container bottom, and pad the whole root horizontally so the side nav
        // bar can't clip the mode selector / shutter / tool rail.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.cameraRoot) { _, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            binding.topBar.setPadding(
                bars.left,
                bars.top,
                bars.right,
                binding.topBar.paddingBottom
            )
            val adContainer = binding.root.findViewById<android.view.View>(R.id.adContainer)
            adContainer?.setPadding(
                bars.left,
                adContainer.paddingTop,
                bars.right,
                bars.bottom
            )
            binding.bottomContainer.setPadding(
                bars.left,
                binding.bottomContainer.paddingTop,
                bars.right,
                0
            )
            insets
        }
    }

    /**
     * The camera activity is pinned to portrait in [onResume], so the system never rotates
     * the window — every widget stays where XML placed it. When the sensor says the device
     * is physically landscape, we **translate** two widgets to new positions along an
     * L-shaped path (horizontal first, then vertical) instead of rotating them:
     *
     *  • `stampPreview` (location + map card) — slides down to just above the ad banner.
     *  • `zoomGroup`    (1x / 2x / 5x pills) — slides down to just above the new stamp
     *    position so the zoom pills sit directly over the location box.
     *
     * `locationStatus` (top-left pill) and `exposureStrip` (top-right ± bar) are completely
     * untouched — they neither move nor rotate. All other widgets (topBar, previewView,
     * shutter, mode selector, tool rail, ad banner, …) also stay put.
     *
     * On portrait sensor we animate the two translations back to zero along the reverse
     * L-shape (vertical first, then horizontal) so the widgets snap home in their XML spots.
     */
    private var orientationListener: android.view.OrientationEventListener? = null
    private var currentWidgetAngle: Int = 0

    private fun startWidgetRotationListener() {
        orientationListener?.disable()
        orientationListener = object : android.view.OrientationEventListener(requireContext()) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                // Sensor degrees: 0 = upright, 90 = clockwise, 180 = upside-down, 270 = counter-cw.
                val sensorDeg = when (orientation) {
                    in 45..134 -> 90
                    in 135..224 -> 180
                    in 225..314 -> 270
                    else -> 0
                }
                // Counter-angle so widget text/map still reads upright for the user.
                val widgetAngle = (360 - sensorDeg) % 360
                if (widgetAngle == currentWidgetAngle) return
                currentWidgetAngle = widgetAngle
                val landscape = sensorDeg == 90 || sensorDeg == 270
                animateWidgetsForOrientation(landscape, widgetAngle.toFloat())
            }
        }.also { it.enable() }
    }

    /**
     * In landscape the widgets stay **horizontally centred** and slide DOWN to the extreme
     * bottom of the preview area — bottom-centre, not bottom-left (user confirmed in last
     * message). They also rotate so the text is readable for a user holding the phone
     * sideways. The movement still runs as an L-shape (brief sideways nudge, then the
     * vertical slide) to make the motion feel deliberate; the sideways nudge is small
     * because we're not relocating horizontally.
     *
     * Portrait: unwind along the reverse L and rotate back to 0.
     */
    private fun animateWidgetsForOrientation(toLandscape: Boolean, widgetAngle: Float) {
        val b = _binding ?: return
        val stamp = b.stampPreview
        val zoom = b.zoomGroup
        stamp.post {
            if (_binding == null) return@post
            if (toLandscape) {
                val root = b.cameraRoot
                val adTop = resolveTopInRoot(b.adContainer, root)

                // Horizontal: keep both widgets centred (translationX = 0).
                val stampDx = 0f
                val zoomDx = 0f

                // Vertical: dock stamp just above the ad container, zoom just above the
                // stamp's new position.
                val gap = resources.getDimensionPixelSize(R.dimen.spacing_sm)
                val stampBottom = resolveBottomInRoot(stamp, root) + stamp.translationY.toInt()
                val stampDy = (adTop - stampBottom - gap).toFloat()
                val zoomBottom = resolveBottomInRoot(zoom, root) + zoom.translationY.toInt()
                val zoomTargetBottom = adTop - stamp.height - gap * 2
                val zoomDy = (zoomTargetBottom - zoomBottom).toFloat()

                lTranslate(stamp, dx = stampDx, dy = stampDy, rotation = widgetAngle)
                lTranslate(zoom, dx = zoomDx, dy = zoomDy, rotation = widgetAngle)
            } else {
                reverseLTranslate(stamp, rotation = widgetAngle)
                reverseLTranslate(zoom, rotation = widgetAngle)
            }
        }
    }

    /** X coordinate of [v]'s left edge expressed in [root]'s coordinate space. */
    private fun resolveLeftInRoot(v: View, root: View): Int {
        var x = 0
        var cur: View = v
        while (cur !== root) {
            x += cur.left
            cur = cur.parent as? View ?: break
        }
        return x
    }

    /** Animate [v] in an L-shape (translateX first, then translateY) and rotate alongside.
     *
     *  Written with [android.animation.AnimatorSet] + [android.animation.ObjectAnimator]
     *  because `view.animate()` returns a single shared [ViewPropertyAnimator] per view —
     *  calling it multiple times with different durations collapses the configurations into
     *  one run and only the last `.start()` actually fires. That's why previous attempts
     *  only rotated and never moved.
     *
     *  Timeline:
     *    t=0 … 150ms  : translationX  + rotation (both run in parallel)
     *    t=150 … 370  : translationY  (rotation still running to t=370)
     */
    private fun lTranslate(v: View, dx: Float, dy: Float, rotation: Float) {
        v.animate().cancel()
        val xAnim = android.animation.ObjectAnimator.ofFloat(v, "translationX", dx).apply { duration = 150 }
        val yAnim = android.animation.ObjectAnimator.ofFloat(v, "translationY", dy).apply { duration = 220 }
        val rotateAnim = android.animation.ObjectAnimator.ofFloat(v, "rotation", rotation).apply { duration = 370 }
        android.animation.AnimatorSet().apply {
            play(xAnim).with(rotateAnim)   // X leg + rotation together
            play(yAnim).after(xAnim)        // Y leg after X leg ends
            start()
        }
    }

    /** Animate [v] back to origin along the reverse L (translateY first, then translateX)
     *  and rotate back to [rotation] (0° for portrait). */
    private fun reverseLTranslate(v: View, rotation: Float) {
        v.animate().cancel()
        val yAnim = android.animation.ObjectAnimator.ofFloat(v, "translationY", 0f).apply { duration = 220 }
        val xAnim = android.animation.ObjectAnimator.ofFloat(v, "translationX", 0f).apply { duration = 150 }
        val rotateAnim = android.animation.ObjectAnimator.ofFloat(v, "rotation", rotation).apply { duration = 370 }
        android.animation.AnimatorSet().apply {
            play(yAnim).with(rotateAnim)
            play(xAnim).after(yAnim)
            start()
        }
    }

    /** Y coordinate of [v]'s top edge expressed in [root]'s coordinate space. */
    private fun resolveTopInRoot(v: View, root: View): Int {
        var y = 0
        var cur: View = v
        while (cur !== root) {
            y += cur.top
            cur = cur.parent as? View ?: break
        }
        return y
    }

    /** Y coordinate of [v]'s bottom edge expressed in [root]'s coordinate space. */
    private fun resolveBottomInRoot(v: View, root: View): Int =
        resolveTopInRoot(v, root) + v.height

    private fun stopWidgetRotationListener() {
        orientationListener?.disable()
        orientationListener = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopWidgetRotationListener()
        // If the user left the camera mid-recording, make sure we don't leave the activity
        // pinned to the orientation we locked for the recording.
        releaseOrientationLock()
        cameraController.release()
        countdownJob?.cancel()
        recTimerJob?.cancel()
        _binding = null
    }

    private fun installCameraBanner() {
        val ad = _binding?.root?.findViewById<android.view.ViewGroup>(R.id.adContainer) ?: return
        ad.post { adsManager.installBanner(ad) }
    }

    private fun startRecIndicator() {
        val binding = _binding ?: return
        binding.recIndicator.visibility = View.VISIBLE
        val pulse = android.view.animation.AnimationUtils.loadAnimation(requireContext(), R.anim.pulse)
        binding.recDot.startAnimation(pulse)
        recStartMillis = System.currentTimeMillis()
        recTimerJob?.cancel()
        recTimerJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isAdded) {
                val elapsedSec = ((System.currentTimeMillis() - recStartMillis) / 1000).toInt()
                val mm = elapsedSec / 60
                val ss = elapsedSec % 60
                _binding?.recElapsed?.text = String.format("%02d:%02d", mm, ss)
                kotlinx.coroutines.delay(500)
            }
        }
    }

    private fun stopRecIndicator() {
        val binding = _binding ?: return
        recTimerJob?.cancel()
        recTimerJob = null
        binding.recDot.clearAnimation()
        binding.recIndicator.visibility = View.GONE
        binding.recElapsed.setText(R.string.camera_rec_time_default)
    }

    private fun bindClickListeners() {
        binding.btnCapture.setDebouncedClickListener(400) { triggerCapture() }
        binding.btnSwitch.setDebouncedClickListener { cameraController.switch() }
        binding.btnSettings.setDebouncedClickListener { findNavController().navigate(R.id.action_global_settings) }
        binding.btnTemplate.setDebouncedClickListener { findNavController().navigate(R.id.action_global_templates) }
        binding.btnTemplates2.setDebouncedClickListener { findNavController().navigate(R.id.action_global_templates) }
        binding.btnGallery.setDebouncedClickListener { findNavController().navigate(R.id.action_global_gallery) }
        binding.btnLocationPicker.setDebouncedClickListener { findNavController().navigate(R.id.action_global_locationPicker) }
        binding.btnGrid.setDebouncedClickListener { toggleGrid() }
        binding.btnFlash.setDebouncedClickListener { toggleFlash() }
        binding.btnTimer.setDebouncedClickListener { showTimerMenu(it) }
        binding.btnTools.setDebouncedClickListener { toggleToolRail() }
        binding.btnPermissionGrant.setDebouncedClickListener {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
        binding.previewView.setOnTouchListener { v, event ->
            // If the tool rail is open, swallow preview taps and close the rail instead.
            if (binding.toolRail.visibility == View.VISIBLE && event.action == MotionEvent.ACTION_UP) {
                toggleToolRail()
                return@setOnTouchListener true
            }
            cameraController.scaleDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP && event.pointerCount == 1) {
                cameraController.focus(event.x, event.y)
                v.performClick()
            }
            true
        }

        binding.exposureBar.setOnLevelChangeListener { level ->
            val range = cameraController.exposureRange.value
            if (range.first == range.last) return@setOnLevelChangeListener
            // Map -1..+1 fraction to the integer EV range (e.g. -6..+6)
            val maxSpan = maxOf(-range.first, range.last).coerceAtLeast(1)
            val index = (level * maxSpan).toInt().coerceIn(range.first, range.last)
            cameraController.setExposureIndex(index)
            applyExposureLabel(index)
        }
        binding.exposurePlus.setOnClickListener { stepExposure(+1) }
        binding.exposureMinus.setOnClickListener { stepExposure(-1) }
    }

    private fun stepExposure(delta: Int) {
        val range = cameraController.exposureRange.value
        if (range.first == range.last) return
        val current = cameraController.exposureIndex.value
        val next = (current + delta).coerceIn(range.first, range.last)
        cameraController.setExposureIndex(next)
        val maxSpan = maxOf(-range.first, range.last).coerceAtLeast(1)
        _binding?.exposureBar?.setLevel(next.toFloat() / maxSpan, notify = false)
        applyExposureLabel(next)
    }

    /** Shows "+2 EV" / "-1 EV" / "0 EV" with colour cues so the user sees direction + magnitude. */
    private fun applyExposureLabel(index: Int) {
        val binding = _binding ?: return
        val text = when {
            index > 0 -> "+$index EV"
            index < 0 -> "$index EV"
            else -> "0 EV"
        }
        binding.exposureValue.text = text
        val amber = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.brand_accent)
        val white = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.white_pure)
        val dim = android.graphics.Color.parseColor("#88FFFFFF")
        binding.exposureValue.setTextColor(if (index == 0) white else amber)
        // Highlight + when positive, - when negative.
        binding.exposurePlus.setTextColor(if (index > 0) amber else dim)
        binding.exposureMinus.setTextColor(if (index < 0) amber else dim)
    }

    private fun bindToolRail() {
        bindTool(binding.toolAspect.root, R.drawable.ic_aspect, getString(R.string.camera_aspect_ratio),
            valueText = { if (cameraController.currentAspect == CameraController.AspectMode.RATIO_16_9) "16:9" else "4:3" }
        ) {
            val next = if (cameraController.currentAspect == CameraController.AspectMode.RATIO_4_3)
                CameraController.AspectMode.RATIO_16_9 else CameraController.AspectMode.RATIO_4_3
            cameraController.setAspect(next)
            bindToolRail()
        }
        bindTool(binding.toolMirror.root, R.drawable.ic_mirror, getString(R.string.camera_mirror),
            valueText = { if (cameraController.isMirrorSelfieOutput) getString(R.string.camera_mirror_on) else getString(R.string.camera_mirror_off) }
        ) {
            cameraController.setMirrorSelfieOutput(!cameraController.isMirrorSelfieOutput)
            bindToolRail()
        }
        bindTool(binding.toolSound.root, R.drawable.ic_sound, getString(R.string.camera_sound),
            valueText = { if (cameraController.isShutterSoundEnabled) getString(R.string.camera_sound_on) else getString(R.string.camera_sound_off) }
        ) {
            cameraController.setShutterSoundEnabled(!cameraController.isShutterSoundEnabled)
            bindToolRail()
        }
        bindTool(binding.toolLevel.root, R.drawable.ic_level, getString(R.string.camera_level_tool),
            valueText = { if (binding.levelOverlay.visibility == View.VISIBLE) getString(R.string.camera_sound_on) else getString(R.string.camera_sound_off) }
        ) {
            binding.levelOverlay.visibility =
                if (binding.levelOverlay.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            bindToolRail()
        }
        bindTool(binding.toolResolution.root, R.drawable.ic_resolution, getString(R.string.camera_resolution)) {
            PopupMenu(requireContext(), binding.toolResolution.root).apply {
                menu.add(0, 0, 0, getString(R.string.settings_quality_high))
                menu.add(0, 1, 1, getString(R.string.settings_quality_medium))
                menu.add(0, 2, 2, getString(R.string.settings_quality_low))
                setOnMenuItemClickListener { item ->
                    val quality = when (item.itemId) {
                        1 -> CameraController.CaptureQuality.MEDIUM
                        2 -> CameraController.CaptureQuality.LOW
                        else -> CameraController.CaptureQuality.HIGH
                    }
                    cameraController.setCaptureQuality(quality)
                    bindToolRail()
                    true
                }
            }.show()
        }
        bindTool(binding.toolWhiteBalance.root, R.drawable.ic_wb, getString(R.string.camera_white_balance)) {
            Toast.makeText(requireContext(), R.string.camera_white_balance, Toast.LENGTH_SHORT).show()
        }
        bindTool(binding.toolAddText.root, R.drawable.ic_text, getString(R.string.camera_add_text)) {
            toolRailOpen = false; binding.toolRail.visibility = View.GONE
            findNavController().navigate(R.id.action_global_templates)
        }
        bindTool(binding.toolFileName.root, R.drawable.ic_file_name, getString(R.string.camera_file_name)) {
            toolRailOpen = false; binding.toolRail.visibility = View.GONE
            findNavController().navigate(R.id.action_global_filename)
        }
        bindTool(binding.toolReport.root, R.drawable.ic_report, getString(R.string.camera_report)) {
            Toast.makeText(requireContext(), R.string.camera_report, Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindTool(root: View, icon: Int, label: String, valueText: (() -> String)? = null, action: () -> Unit) {
        root.findViewById<android.widget.ImageView>(R.id.toolIcon).setImageResource(icon)
        root.findViewById<android.widget.TextView>(R.id.toolLabel).text = label
        root.findViewById<android.widget.TextView>(R.id.toolValue).text = valueText?.invoke().orEmpty()
        root.setOnClickListener { action() }
    }

    private fun bindZoomPills() {
        binding.zoom1.setOnClickListener { cameraController.stepZoom(1f) }
        binding.zoom2.setOnClickListener { cameraController.stepZoom(2f) }
        binding.zoom05.setOnClickListener { cameraController.stepZoom(0.5f) }
        binding.zoom5.setOnClickListener { cameraController.stepZoom(5f) }
    }

    private fun bindModeSelector() {
        binding.modePhoto.isChecked = true
        binding.modeSelector.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            captureMode = when (checkedId) {
                R.id.modeVideo -> CaptureMode.VIDEO
                R.id.modeReports -> CaptureMode.REPORT
                else -> CaptureMode.PHOTO
            }
            // REPORT badge visible only in report mode
            binding.reportBadge.visibility =
                if (captureMode == CaptureMode.REPORT) View.VISIBLE else View.GONE
            // Rebind CameraX use cases for the new mode so VideoCapture is actually active.
            cameraController.setMode(
                if (captureMode == CaptureMode.VIDEO) CameraController.Mode.VIDEO
                else CameraController.Mode.PHOTO
            )
            // When REPORTS is tapped, immediately show the quick-action sheet.
            if (captureMode == CaptureMode.REPORT) {
                binding.reportBadge.post { showReportQuickSheet() }
            }
        }
    }

    private fun toggleToolRail() {
        toolRailOpen = !toolRailOpen
        binding.toolRail.visibility = if (toolRailOpen) View.VISIBLE else View.GONE
        binding.btnToolsIndicator.visibility = if (toolRailOpen) View.VISIBLE else View.INVISIBLE
        val tint = if (toolRailOpen) R.color.charcoal_900 else R.color.white_pure
        binding.btnTools.imageTintList =
            androidx.core.content.ContextCompat.getColorStateList(requireContext(), tint)
    }

    private fun showPermissionRationale() { binding.permissionState.visibility = View.VISIBLE }

    private fun showPermissionDenied() {
        binding.permissionTitle.setText(R.string.perm_denied_title)
        binding.permissionBody.setText(R.string.perm_denied_body)
        binding.btnPermissionGrant.setText(R.string.common_open_settings)
        binding.btnPermissionGrant.setOnClickListener {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", requireContext().packageName, null)
            )
            startActivity(intent)
        }
    }

    private fun startCameraIfReady() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) return
        binding.permissionState.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                cameraController.start(
                    CameraController.Facing.BACK,
                    CameraController.AspectMode.RATIO_4_3,
                    CameraController.CaptureQuality.HIGH
                )
            }.onFailure {
                Toast.makeText(requireContext(), R.string.error_camera_unavailable, Toast.LENGTH_LONG).show()
            }
            if (!cameraController.supportsFlash()) {
                binding.btnFlashWrap.visibility = View.INVISIBLE
            } else {
                applyFlashUi(cameraController.currentFlashMode)
            }
            applyTimerUi()
            bindToolRail()
            // After start, cameras expose zoom range; conditionally show 0.5x/5x pills
            val range = cameraController.zoomState.value
            binding.zoom05.visibility = if (range.min < 0.9f) View.VISIBLE else View.GONE
            binding.zoom5.visibility = if (range.max >= 5f) View.VISIBLE else View.GONE
            // Exposure
            val exp = cameraController.exposureRange.value
            binding.exposureStrip.visibility = if (exp.last > exp.first) View.VISIBLE else View.GONE
            binding.exposureBar.setLevel(0f, notify = false)
            applyExposureLabel(0)
        }
    }

    private fun toggleFlash() {
        if (!cameraController.supportsFlash()) {
            Toast.makeText(requireContext(), R.string.error_feature_unsupported, Toast.LENGTH_SHORT).show()
            return
        }
        val next = when (cameraController.currentFlashMode) {
            CameraController.FlashMode.OFF -> CameraController.FlashMode.AUTO
            CameraController.FlashMode.AUTO -> CameraController.FlashMode.ON
            CameraController.FlashMode.ON -> CameraController.FlashMode.TORCH
            CameraController.FlashMode.TORCH -> CameraController.FlashMode.OFF
        }
        cameraController.setFlashMode(next)
        applyFlashUi(next)
    }

    private fun applyFlashUi(mode: CameraController.FlashMode) {
        val binding = _binding ?: return
        binding.btnFlash.setImageResource(
            when (mode) {
                CameraController.FlashMode.OFF -> R.drawable.ic_flash_off
                CameraController.FlashMode.AUTO -> R.drawable.ic_flash_auto
                CameraController.FlashMode.ON -> R.drawable.ic_flash_on
                CameraController.FlashMode.TORCH -> R.drawable.ic_flash_torch
            }
        )
        binding.btnFlashLabel.setText(
            when (mode) {
                CameraController.FlashMode.OFF -> R.string.camera_flash_off
                CameraController.FlashMode.AUTO -> R.string.camera_flash_auto
                CameraController.FlashMode.ON -> R.string.camera_flash_on
                CameraController.FlashMode.TORCH -> R.string.camera_flash_torch
            }
        )
        val active = mode != CameraController.FlashMode.OFF
        binding.btnFlashIndicator.visibility = if (active) View.VISIBLE else View.INVISIBLE
        val tint = if (active) R.color.charcoal_900 else R.color.white_pure
        binding.btnFlash.imageTintList =
            androidx.core.content.ContextCompat.getColorStateList(requireContext(), tint)
    }

    private fun toggleGrid() {
        val binding = _binding ?: return
        val show = binding.gridOverlay.visibility != View.VISIBLE
        binding.gridOverlay.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnGridIndicator.visibility = if (show) View.VISIBLE else View.INVISIBLE
        val tint = if (show) R.color.charcoal_900 else R.color.white_pure
        binding.btnGrid.imageTintList =
            androidx.core.content.ContextCompat.getColorStateList(requireContext(), tint)
    }

    private fun showTimerMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 0, 0, R.string.camera_timer_off)
        popup.menu.add(0, 3, 1, R.string.camera_timer_3s)
        popup.menu.add(0, 10, 2, R.string.camera_timer_10s)
        popup.setOnMenuItemClickListener { item ->
            timerSeconds = item.itemId
            applyTimerUi()
            true
        }
        popup.show()
    }

    private fun applyTimerUi() {
        val binding = _binding ?: return
        val active = timerSeconds > 0
        binding.btnTimerLabel.text = when (timerSeconds) {
            3 -> getString(R.string.camera_timer_3s)
            10 -> getString(R.string.camera_timer_10s)
            else -> getString(R.string.camera_timer_off)
        }
        binding.btnTimerIndicator.visibility = if (active) View.VISIBLE else View.INVISIBLE
        val tint = if (active) R.color.charcoal_900 else R.color.white_pure
        binding.btnTimer.imageTintList =
            androidx.core.content.ContextCompat.getColorStateList(requireContext(), tint)
    }

    private var pendingReportDraft: ReportDraft? = null
    private var lastReportPhotoId: Long? = null

    data class ReportDraft(
        val type: String?,
        val reporter: String?,
        val project: String?,
        val reference: String?,
        val note: String?
    )

    private fun triggerCapture() {
        if (viewModel.captureState.value is CameraViewModel.CaptureState.Processing) return
        when (captureMode) {
            CaptureMode.VIDEO -> toggleVideo()
            CaptureMode.REPORT -> showReportForm()
            CaptureMode.PHOTO -> {
                if (timerSeconds <= 0) { doCapture(); return }
                countdownJob?.cancel()
                countdownJob = viewLifecycleOwner.lifecycleScope.launch {
                    var remaining = timerSeconds
                    binding.timerCountdown.visibility = View.VISIBLE
                    while (remaining > 0 && isAdded) {
                        binding.timerCountdown.text = remaining.toString()
                        kotlinx.coroutines.delay(1000)
                        remaining--
                    }
                    binding.timerCountdown.visibility = View.GONE
                    doCapture()
                }
            }
        }
    }

    private fun toggleVideo() {
        if (cameraController.videoRecording.value) {
            cameraController.stopVideoRecording()
            return
        }
        if (PermissionUtils.hasRecordAudio(requireContext())) {
            launchVideo(withAudio = true)
        } else {
            pendingVideoStart = true
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun launchVideo(withAudio: Boolean) {
        val dir = File(requireContext().filesDir, "videos").apply { if (!exists()) mkdirs() }
        val target = File(dir, "video_${System.currentTimeMillis()}.mp4")
        // Freeze the stamp as of recording-start and hand it to CameraController so
        // OverlayEffect burns it into every recorded frame.
        prepareVideoStampOverlay()
        // Lock orientation for the duration of the recording so a mid-record rotation
        // can't flip the frames or stretch the stamp overlay to the wrong aspect.
        lockOrientationForRecording()
        cameraController.startVideoRecording(target, withAudio) { res ->
            activity?.runOnUiThread {
                cameraController.setVideoStampFactory(null)
                releaseOrientationLock()
                res.fold(
                    onSuccess = { onVideoRecorded(target) },
                    onFailure = { Toast.makeText(requireContext(), R.string.error_photo_save_failed, Toast.LENGTH_SHORT).show() }
                )
            }
        }
    }

    /** Pins the activity to the current orientation. Restored on stop or on fragment destroy. */
    private fun lockOrientationForRecording() {
        val act = activity ?: return
        val landscape = resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        act.requestedOrientation = if (landscape) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }

    private fun releaseOrientationLock() {
        activity?.requestedOrientation =
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    /**
     * Hands CameraController a renderer closure. OverlayEffect calls it with the exact
     * canvas dimensions on the first frame, so the bitmap is produced at the right aspect
     * — no portrait/landscape guessing and no scaling artefacts. The card background alpha
     * is reduced to 0.35 so the recorded video shows through clearly behind the card; text
     * and the map thumbnail stay at full opacity for readability.
     */
    private fun prepareVideoStampOverlay() {
        val template = viewModel.selectedTemplate.value ?: run {
            cameraController.setVideoStampFactory(null); return
        }
        // Snapshot everything the renderer needs so the closure can run on the GL thread
        // without touching ViewModel state later. These values are frozen at record-start.
        val captureAt = System.currentTimeMillis()
        val location = viewModel.location.value
        val compass = viewModel.lastCompass.value
        val magnetic = viewModel.lastMagnetic.value
        val weather = viewModel.lastWeather.value
        val mapBitmap = cachedMapBitmap
        val renderer = photoStampRenderer
        cameraController.setVideoStampFactory { w, h ->
            val stamp = com.idp.geostampcamera.domain.model.StampContext(
                template = template,
                captureAtMillis = captureAt,
                location = location,
                compass = compass,
                magnetic = magnetic,
                weather = weather,
                mapBitmap = mapBitmap
            )
            android.util.Log.d(
                "VideoStamp",
                "render overlay canvas=${w}x$h cardAlpha=0.4 template=${template.name}"
            )
            // cardAlpha = 0.4 → the template's background color still tints the card (so the
            // user recognises their chosen template) but at ~28% opacity instead of ~70%, so
            // the video shows through clearly. Text/map/border stay at full opacity.
            runCatching { renderer.renderOverlayBitmap(w, h, stamp, cardAlpha = 0.4f) }.getOrNull()
        }
    }

    private fun onVideoRecorded(file: File) {
        val ctx = requireContext()
        // Publish to Movies/GeoStamp so the device Gallery / Photos / File manager find it.
        val publicUri = com.idp.geostampcamera.util.VideoPublisher.publish(ctx, file)
        val playUri = publicUri ?: androidx.core.content.FileProvider.getUriForFile(
            ctx, "${ctx.packageName}.fileprovider", file
        )
        val root = _binding?.cameraRoot ?: return
        val sb = com.google.android.material.snackbar.Snackbar.make(
            root, R.string.camera_video_saved_to_gallery,
            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
        )
        sb.setAction(R.string.camera_video_play) {
            runCatching {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(playUri, "video/mp4")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(intent)
            }.onFailure {
                Toast.makeText(ctx, R.string.error_photo_save_failed, Toast.LENGTH_SHORT).show()
            }
        }
        sb.show()
    }

    private fun doCapture() {
        val dir = File(requireContext().filesDir, "photos").apply { if (!exists()) mkdirs() }
        val target = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        binding.captureProgress.visibility = View.VISIBLE
        cameraController.capture(target) { res ->
            binding.captureProgress.post {
                binding.captureProgress.visibility = View.GONE
                res.fold(
                    onSuccess = {
                        val draft = pendingReportDraft
                        pendingReportDraft = null
                        viewModel.onCapturedToFile(
                            file = target,
                            projectOverride = draft?.project,
                            noteOverride = listOfNotNull(draft?.type, draft?.reference?.let { "Ref: $it" }, draft?.note, draft?.reporter?.let { "By: $it" })
                                .joinToString(" • ").takeIf { it.isNotBlank() }
                        )
                    },
                    onFailure = {
                        Toast.makeText(requireContext(), R.string.camera_capture_failed, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    private fun observeLevel() {
        if (!levelSensor.isAvailable) return
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                levelSensor.rollDegrees().collect { roll ->
                    binding.levelOverlay.setRoll(roll)
                }
            }
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.selectedTemplate.collect { template ->
                        if (template != null) rebindPreview(template)
                    }
                }
                launch {
                    viewModel.location.collect { snap ->
                        rebindPreview(viewModel.selectedTemplate.value ?: return@collect)
                        binding.locationStatus.text = when {
                            snap == null -> getString(R.string.camera_location_waiting)
                            snap.isStale -> getString(R.string.camera_location_missing)
                            else -> snap.address?.shortAddress
                                ?: com.idp.geostampcamera.util.CoordinateFormatter.formatDecimal(snap.latitude, snap.longitude)
                        }
                    }
                }
                launch {
                    viewModel.captureState.collect { state ->
                        binding.captureProgress.visibility =
                            if (state is CameraViewModel.CaptureState.Processing) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    cameraController.zoomState.collect { z ->
                        val label = String.format("%.1fx", z.ratio)
                        binding.zoom1.text = label
                    }
                }
                launch {
                    cameraController.videoRecording.collect { recording ->
                        binding.btnCapture.setBackgroundResource(
                            if (recording) R.drawable.bg_shutter_recording else R.drawable.bg_shutter
                        )
                        if (recording) {
                            startRecIndicator()
                        } else {
                            stopRecIndicator()
                        }
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is CameraViewModel.CameraEvent.CaptureSaved -> {
                                Toast.makeText(requireContext(), getString(R.string.common_done), Toast.LENGTH_SHORT).show()
                                if (captureMode == CaptureMode.REPORT) {
                                    lastReportPhotoId = event.photoId
                                    showReportSheet(event.photoId)
                                } else {
                                    // Opportunistic interstitial after a successful capture.
                                    adsManager.maybeShowInterstitial(requireActivity()) { /* no-op */ }
                                }
                            }
                            is CameraViewModel.CameraEvent.CaptureFailed ->
                                Toast.makeText(requireContext(), event.message, Toast.LENGTH_LONG).show()
                            CameraViewModel.CameraEvent.DriveReauthNeeded ->
                                Toast.makeText(
                                    requireContext(),
                                    R.string.settings_storage_drive_needs_reauth,
                                    Toast.LENGTH_LONG
                                ).show()
                            is CameraViewModel.CameraEvent.DriveSyncFailed ->
                                Toast.makeText(requireContext(), event.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun rebindPreview(template: com.idp.geostampcamera.domain.model.StampTemplate) {
        val loc = viewModel.location.value
        val stamp = StampContext(
            template = template,
            captureAtMillis = System.currentTimeMillis(),
            location = loc,
            compass = viewModel.lastCompass.value,
            magnetic = viewModel.lastMagnetic.value,
            weather = viewModel.lastWeather.value,
            mapBitmap = cachedMapBitmap
        )
        binding.stampPreview.bind(stamp, cachedMapBitmap)
        if (template.showMap && loc != null) refreshMapThumbnailIfNeeded(template, loc)
    }

    private fun refreshMapThumbnailIfNeeded(
        template: com.idp.geostampcamera.domain.model.StampTemplate,
        loc: com.idp.geostampcamera.domain.model.LocationSnapshot
    ) {
        val key = "%.3f_%.3f_%s".format(loc.latitude, loc.longitude, template.id)
        if (key == cachedMapKey && cachedMapBitmap != null) return
        // Cancel any in-flight snapshot for the previous location so switching locations
        // (manual pick, saved location, GPS fix) always renders the latest coords.
        mapGenerationJob?.cancel()
        mapGenerationJob = viewLifecycleOwner.lifecycleScope.launch {
            // Oversample vs the on-screen 96dp thumb so the Google watermark + marker
            // stay crisp when fitCenter-scaled into the ImageView.
            val size = (resources.displayMetrics.density * 120).toInt().coerceAtLeast(260)
            val mapType = runCatching { settingsRepository.mapTypeFlow().first() }.getOrNull() ?: "normal"
            val bitmap = runCatching {
                mapSnapshotProvider.snapshot(
                    lat = loc.latitude,
                    lng = loc.longitude,
                    widthPx = size,
                    heightPx = size,
                    mapType = mapSnapshotProvider.mapTypeFromTemplate(template, mapType)
                )
            }.getOrNull() ?: mapSnapshotProvider.placeholder(size, size)
            if (_binding == null) return@launch
            cachedMapBitmap = bitmap
            cachedMapKey = key
            binding.stampPreview.bind(
                StampContext(
                    template = template,
                    captureAtMillis = System.currentTimeMillis(),
                    location = loc,
                    compass = viewModel.lastCompass.value,
                    magnetic = viewModel.lastMagnetic.value,
                    weather = viewModel.lastWeather.value,
                    mapBitmap = bitmap
                ),
                bitmap
            )
        }
    }

    // ---------- REPORT mode: pre-capture form + post-capture sheet ----------

    /** Pops as soon as the user taps the REPORTS mode tab. */
    private fun showReportQuickSheet() {
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.sheet_report_quick, null, false)
        sheet.setContentView(view)

        // Track whether the user actively chose an action. If they just dismissed
        // (back press, tap outside, swipe down), fall back to Photo mode so the camera
        // isn't stuck showing the REPORT badge with no sheet.
        var proceeded = false
        val lastId = lastReportPhotoId
        val toastNoLast = { Toast.makeText(requireContext(), R.string.report_no_last, Toast.LENGTH_SHORT).show(); Unit }

        view.findViewById<View>(R.id.quickNew).setOnClickListener {
            proceeded = true
            sheet.dismiss(); showReportForm()
        }
        view.findViewById<View>(R.id.quickLastOpen).setOnClickListener {
            proceeded = true
            sheet.dismiss()
            if (lastId == null) toastNoLast()
            else findNavController().navigate(
                R.id.photoViewerFragment,
                android.os.Bundle().apply { putLong("photoId", lastId) }
            )
        }
        view.findViewById<View>(R.id.quickLastShare).setOnClickListener {
            proceeded = true
            sheet.dismiss()
            if (lastId == null) toastNoLast() else sharePdfReport(lastId)
        }
        view.findViewById<View>(R.id.quickLastEmail).setOnClickListener {
            proceeded = true
            sheet.dismiss()
            if (lastId == null) toastNoLast() else emailWithPhoto(lastId)
        }
        view.findViewById<View>(R.id.quickBrowse).setOnClickListener {
            proceeded = true
            sheet.dismiss()
            findNavController().navigate(R.id.action_global_gallery)
        }
        view.findViewById<View>(R.id.quickRate).setOnClickListener {
            proceeded = true
            sheet.dismiss(); openPlayStoreForRating()
        }
        view.findViewById<View>(R.id.btnQuickBack).setOnClickListener {
            sheet.dismiss()
        }
        sheet.setOnDismissListener {
            if (!proceeded && _binding != null) binding.modePhoto.isChecked = true
        }
        sheet.show()
    }

    private fun showReportForm() {
        val view = layoutInflater.inflate(R.layout.dialog_report_form, null, false)
        val typeGroup = view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.reportTypeGroup)
        val chipCheckIn = view.findViewById<com.google.android.material.chip.Chip>(R.id.chipCheckIn)
        val chipCheckOut = view.findViewById<com.google.android.material.chip.Chip>(R.id.chipCheckOut)
        val chipInspection = view.findViewById<com.google.android.material.chip.Chip>(R.id.chipInspection)
        val chipOther = view.findViewById<com.google.android.material.chip.Chip>(R.id.chipOther)
        val reporter = view.findViewById<android.widget.EditText>(R.id.inputReporter)
        val project = view.findViewById<android.widget.EditText>(R.id.inputProject)
        val reference = view.findViewById<android.widget.EditText>(R.id.inputReference)
        val noteInput = view.findViewById<android.widget.EditText>(R.id.inputReportNote)

        pendingReportDraft?.let { draft ->
            reporter.setText(draft.reporter)
            project.setText(draft.project)
            reference.setText(draft.reference)
            noteInput.setText(draft.note)
            when (draft.type) {
                getString(R.string.report_form_type_check_in) -> chipCheckIn.isChecked = true
                getString(R.string.report_form_type_check_out) -> chipCheckOut.isChecked = true
                getString(R.string.report_form_type_inspection) -> chipInspection.isChecked = true
                getString(R.string.report_form_type_other) -> chipOther.isChecked = true
            }
        }

        var proceeded = false
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.report_form_title)
            .setView(view)
            .setPositiveButton(R.string.report_form_continue) { _, _ ->
                proceeded = true
                val checkedChip = typeGroup.checkedChipId.takeIf { it != android.view.View.NO_ID }
                    ?.let { view.findViewById<com.google.android.material.chip.Chip>(it) }
                pendingReportDraft = ReportDraft(
                    type = checkedChip?.text?.toString(),
                    reporter = reporter.text?.toString()?.trim().orEmpty().takeIf { it.isNotEmpty() },
                    project = project.text?.toString()?.trim().orEmpty().takeIf { it.isNotEmpty() },
                    reference = reference.text?.toString()?.trim().orEmpty().takeIf { it.isNotEmpty() },
                    note = noteInput.text?.toString()?.trim().orEmpty().takeIf { it.isNotEmpty() }
                )
                doCapture()
            }
            .setNegativeButton(R.string.common_cancel, null)
            // Also fires on back-press / outside-tap dismissals, so a cancelled form
            // doesn't leave the camera stuck in REPORT mode.
            .setOnDismissListener {
                if (!proceeded && _binding != null) binding.modePhoto.isChecked = true
            }
            .show()
    }

    private fun showReportSheet(photoId: Long) {
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.sheet_report_options, null, false)
        sheet.setContentView(view)
        view.findViewById<View>(R.id.optionPdf).setOnClickListener {
            sheet.dismiss(); sharePdfReport(photoId)
        }
        view.findViewById<View>(R.id.optionEmail).setOnClickListener {
            sheet.dismiss(); emailWithPhoto(photoId)
        }
        view.findViewById<View>(R.id.optionShare).setOnClickListener {
            sheet.dismiss(); sharePhoto(photoId)
        }
        view.findViewById<View>(R.id.optionView).setOnClickListener {
            sheet.dismiss()
            findNavController().navigate(
                R.id.photoViewerFragment,
                android.os.Bundle().apply { putLong("photoId", photoId) }
            )
        }
        view.findViewById<View>(R.id.optionCopy).setOnClickListener {
            sheet.dismiss(); copyReportDetails(photoId)
        }
        view.findViewById<View>(R.id.optionRate).setOnClickListener {
            sheet.dismiss(); openPlayStoreForRating()
        }
        view.findViewById<View>(R.id.btnDismiss).setOnClickListener { sheet.dismiss() }
        sheet.show()
    }

    private fun copyReportDetails(photoId: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            val photo = photoRepository.get(photoId) ?: return@launch
            val coords = if (photo.latitude != null && photo.longitude != null)
                com.idp.geostampcamera.util.CoordinateFormatter.formatDecimal(photo.latitude, photo.longitude)
            else getString(R.string.common_not_available)
            val summary = buildString {
                photo.projectName?.let { appendLine("Project: $it") }
                photo.shortAddress?.let { appendLine("Address: $it") }
                appendLine("Coordinates: $coords")
                appendLine("Captured: ${com.idp.geostampcamera.util.DateFormatter.format(photo.timestampMillis, com.idp.geostampcamera.util.DateFormatter.FORMAT_FULL)}")
                photo.note?.let { appendLine("Note: $it") }
            }.trim()
            val cb = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cb.setPrimaryClip(android.content.ClipData.newPlainText("Report", summary))
            Toast.makeText(requireContext(), R.string.report_copied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPlayStoreForRating() {
        val pkg = requireContext().packageName
        val market = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse("market://details?id=$pkg")
        ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
        val web = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
        ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
        runCatching { startActivity(market) }.recoverCatching { startActivity(web) }
    }

    private fun sharePdfReport(photoId: Long) {
        // Gate behind a rewarded ad (free users). Premium users skip the ad.
        val proceed = { generateAndShareReport(photoId) }
        adsManager.showRewarded(
            activity = requireActivity(),
            onRewardGranted = {
                Toast.makeText(requireContext(), R.string.ad_reward_granted, Toast.LENGTH_SHORT).show()
                proceed()
            },
            onUnavailable = { proceed() } // no ad loaded or user is Pro — just generate.
        )
    }

    private fun generateAndShareReport(photoId: Long) {
        val progress = Toast.makeText(requireContext(), R.string.report_generating, Toast.LENGTH_SHORT)
        progress.show()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = photoReportGenerator.generate(photoId)
            progress.cancel()
            result.onSuccess { uri ->
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(android.content.Intent.createChooser(send, getString(R.string.report_action_pdf)))
            }.onFailure { throwable ->
                val reason = throwable.message?.take(140) ?: "unknown"
                Toast.makeText(
                    requireContext(),
                    getString(R.string.report_pdf_failed) + "\n" + reason,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun emailWithPhoto(photoId: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            val photo = photoRepository.get(photoId) ?: return@launch
            val path = photo.stampedPath ?: photo.originalPath ?: return@launch
            val file = java.io.File(path)
            if (!file.exists()) return@launch
            val uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(), requireContext().packageName + ".fileprovider", file
            )
            val subject = "${getString(R.string.report_subject_prefix)}: ${photo.shortAddress ?: ""}".trim()
            val body = getString(
                R.string.report_email_body,
                photo.fullAddress ?: photo.shortAddress ?: "",
                if (photo.latitude != null && photo.longitude != null)
                    com.idp.geostampcamera.util.CoordinateFormatter.formatDecimal(photo.latitude, photo.longitude)
                else getString(R.string.common_not_available),
                com.idp.geostampcamera.util.DateFormatter.format(photo.timestampMillis, com.idp.geostampcamera.util.DateFormatter.FORMAT_FULL)
            )
            val email = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "message/rfc822"
                putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
                putExtra(android.content.Intent.EXTRA_TEXT, body)
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(email, getString(R.string.report_action_email)))
        }
    }

    private fun sharePhoto(photoId: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            val photo = photoRepository.get(photoId) ?: return@launch
            val path = photo.stampedPath ?: photo.originalPath ?: return@launch
            val file = java.io.File(path)
            if (!file.exists()) return@launch
            val uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(), requireContext().packageName + ".fileprovider", file
            )
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(send, getString(R.string.report_action_share_photo)))
        }
    }
}
