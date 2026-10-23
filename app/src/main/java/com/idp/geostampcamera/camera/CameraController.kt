package com.idp.geostampcamera.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.media.MediaActionSound
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.view.OrientationEventListener
import android.view.ScaleGestureDetector
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.effects.OverlayEffect
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView
) {

    enum class Facing { BACK, FRONT }
    enum class FlashMode { OFF, AUTO, ON, TORCH }
    enum class AspectMode(val cameraXRatio: Int) {
        RATIO_4_3(AspectRatio.RATIO_4_3),
        RATIO_16_9(AspectRatio.RATIO_16_9)
    }
    enum class CaptureQuality { HIGH, MEDIUM, LOW }
    enum class Mode { PHOTO, VIDEO }

    data class ZoomState(val ratio: Float, val min: Float, val max: Float, val linear: Float)

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var currentRecording: Recording? = null
    private var camera: androidx.camera.core.Camera? = null
    private var facing: Facing = Facing.BACK
    private var flashMode: FlashMode = FlashMode.OFF
    private var aspectMode: AspectMode = AspectMode.RATIO_4_3
    private var captureQuality: CaptureQuality = CaptureQuality.HIGH
    private var mirrorSelfieOutput: Boolean = true
    private var shutterSoundEnabled: Boolean = true
    private var mode: Mode = Mode.PHOTO

    private val _zoomState = MutableStateFlow(ZoomState(1f, 1f, 1f, 0f))
    val zoomState: StateFlow<ZoomState> = _zoomState

    private val _exposureIndex = MutableStateFlow(0)
    val exposureIndex: StateFlow<Int> = _exposureIndex

    private val _exposureRange = MutableStateFlow(0..0)
    val exposureRange: StateFlow<IntRange> = _exposureRange

    private val _videoRecording = MutableStateFlow(false)
    val videoRecording: StateFlow<Boolean> = _videoRecording

    private val executor: Executor = Executors.newSingleThreadExecutor()
    private val mediaSound: MediaActionSound by lazy { MediaActionSound() }

    // Video overlay: a FACTORY so the stamp bitmap is rendered at the exact canvas size
    // the OverlayEffect gives us on the first frame. Avoids guessing portrait-vs-landscape
    // dimensions up-front and scaling incorrectly later. UI thread writes the factory via
    // setVideoStampFactory(); GL thread reads it in the on-draw callback.
    @Volatile private var videoStampFactory: ((Int, Int) -> Bitmap?)? = null
    private var cachedVideoStamp: Bitmap? = null
    private var cachedStampWidth: Int = 0
    private var cachedStampHeight: Int = 0
    private var overlayEffect: OverlayEffect? = null
    private var overlayHandlerThread: HandlerThread? = null
    // SRC mode makes the overlay bitmap's pixels REPLACE the canvas content (including
    // its alpha channel) instead of blending onto whatever the canvas was initialised to.
    // Without this, a canvas initialised to opaque black would ignore our transparent pixels
    // and render the card as fully opaque regardless of our requested alpha.
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
    }

    private val orientationListener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            val rotation = when (orientation) {
                in 45 until 135 -> android.view.Surface.ROTATION_270
                in 135 until 225 -> android.view.Surface.ROTATION_180
                in 225 until 315 -> android.view.Surface.ROTATION_90
                else -> android.view.Surface.ROTATION_0
            }
            imageCapture?.targetRotation = rotation
            // VideoCapture needs the same rotation hint so the saved MP4 has correct
            // orientation metadata — without this a landscape-held recording plays back
            // sideways and the stamp overlay ends up on the wrong edge.
            videoCapture?.targetRotation = rotation
        }
    }

    private val scaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val current = _zoomState.value.ratio
            setZoomRatio(current * detector.scaleFactor)
            return true
        }
    }
    val scaleDetector = ScaleGestureDetector(context, scaleListener)

    val isFrontFacing: Boolean get() = facing == Facing.FRONT
    val currentFlashMode: FlashMode get() = flashMode
    val currentAspect: AspectMode get() = aspectMode
    val isMirrorSelfieOutput: Boolean get() = mirrorSelfieOutput
    val isShutterSoundEnabled: Boolean get() = shutterSoundEnabled

    suspend fun start(
        facing: Facing = Facing.BACK,
        aspect: AspectMode = AspectMode.RATIO_4_3,
        quality: CaptureQuality = CaptureQuality.HIGH
    ) {
        this.facing = facing
        this.aspectMode = aspect
        this.captureQuality = quality
        val provider: ProcessCameraProvider = suspendCoroutine { cont: kotlin.coroutines.Continuation<ProcessCameraProvider?> ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                runCatching { cont.resume(future.get()) }
                    .onFailure { cont.resume(null) }
            }, ContextCompat.getMainExecutor(context))
        } ?: error("Could not initialise CameraX")
        cameraProvider = provider
        rebind()
        orientationListener.enable()
    }

    fun switch() {
        facing = if (facing == Facing.BACK) Facing.FRONT else Facing.BACK
        rebind()
    }

    fun setFlashMode(mode: FlashMode) {
        flashMode = mode
        // ImageCapture flash fires only at shutter time, so TORCH falls back to OFF there and
        // we drive the actual torch LED continuously via cameraControl.enableTorch().
        imageCapture?.flashMode = when (mode) {
            FlashMode.OFF, FlashMode.TORCH -> ImageCapture.FLASH_MODE_OFF
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
        }
        applyTorchState()
    }

    private fun applyTorchState() {
        val control = camera?.cameraControl ?: return
        val wantTorch = flashMode == FlashMode.TORCH
        runCatching { control.enableTorch(wantTorch) }
    }

    fun setAspect(mode: AspectMode) { aspectMode = mode; rebind() }

    fun setCaptureQuality(quality: CaptureQuality) { captureQuality = quality; rebind() }

    fun setMode(newMode: Mode) {
        if (mode == newMode) return
        mode = newMode
        rebind()
    }

    val currentMode: Mode get() = mode

    fun setMirrorSelfieOutput(mirror: Boolean) { mirrorSelfieOutput = mirror }

    fun setShutterSoundEnabled(enabled: Boolean) { shutterSoundEnabled = enabled }

    fun focus(x: Float, y: Float) {
        val factory = previewView.meteringPointFactory
        val point = factory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point).build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    fun setZoomRatio(ratio: Float) {
        val state = camera?.cameraInfo?.zoomState?.value ?: return
        val clamped = ratio.coerceIn(state.minZoomRatio, state.maxZoomRatio)
        camera?.cameraControl?.setZoomRatio(clamped)
        updateZoomState()
    }

    fun setZoomLinear(fraction: Float) {
        camera?.cameraControl?.setLinearZoom(fraction.coerceIn(0f, 1f))
        updateZoomState()
    }

    fun stepZoom(preset: Float) { setZoomRatio(preset) }

    fun setExposureIndex(index: Int) {
        val info = camera?.cameraInfo ?: return
        val range = info.exposureState.exposureCompensationRange
        val clamped = index.coerceIn(range.lower, range.upper)
        camera?.cameraControl?.setExposureCompensationIndex(clamped)
        _exposureIndex.value = clamped
    }

    fun supportsFlash(): Boolean = camera?.cameraInfo?.hasFlashUnit() ?: false
    fun supportsExposure(): Boolean =
        camera?.cameraInfo?.exposureState?.isExposureCompensationSupported ?: false

    fun release() {
        orientationListener.disable()
        currentRecording?.stop()
        cameraProvider?.unbindAll()
        releaseOverlay()
    }

    @SuppressLint("RestrictedApi")
    fun capture(outputFile: File, onDone: (Result<Uri>) -> Unit) {
        val capture = imageCapture ?: run {
            onDone(Result.failure(IllegalStateException("Camera not started"))); return
        }
        if (shutterSoundEnabled) {
            runCatching { mediaSound.play(MediaActionSound.SHUTTER_CLICK) }
        }
        val output = ImageCapture.OutputFileOptions.Builder(outputFile).apply {
            if (facing == Facing.FRONT && mirrorSelfieOutput) {
                setMetadata(ImageCapture.Metadata().apply { isReversedHorizontal = true })
            }
        }.build()
        capture.takePicture(
            output,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    onDone(Result.success(result.savedUri ?: Uri.fromFile(outputFile)))
                }
                override fun onError(exc: ImageCaptureException) { onDone(Result.failure(exc)) }
            }
        )
    }

    @SuppressLint("MissingPermission")
    fun startVideoRecording(outputFile: File, withAudio: Boolean, onFinish: (Result<Uri>) -> Unit) {
        val vc = videoCapture ?: run {
            onFinish(Result.failure(IllegalStateException("Video not initialised"))); return
        }
        if (currentRecording != null) return
        val outputOptions = androidx.camera.video.FileOutputOptions.Builder(outputFile).build()
        var pending = vc.output.prepareRecording(context, outputOptions)
        if (withAudio) pending = pending.withAudioEnabled()
        currentRecording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> _videoRecording.value = true
                    is VideoRecordEvent.Finalize -> {
                        _videoRecording.value = false
                        currentRecording = null
                        if (!event.hasError()) {
                            onFinish(Result.success(event.outputResults.outputUri))
                        } else {
                            onFinish(Result.failure(RuntimeException("Record failed: ${event.error}")))
                        }
                    }
                }
            }
    }

    fun stopVideoRecording() { currentRecording?.stop() }

    /**
     * Supply the renderer that OverlayEffect calls on the first frame with the exact canvas
     * dimensions. Pass `null` to clear. The returned bitmap should be mostly transparent
     * except for the stamp card.
     */
    fun setVideoStampFactory(factory: ((Int, Int) -> Bitmap?)?) {
        videoStampFactory = factory
        synchronized(this) {
            cachedVideoStamp?.recycle()
            cachedVideoStamp = null
            cachedStampWidth = 0
            cachedStampHeight = 0
        }
    }

    private fun buildOverlayEffect(): OverlayEffect {
        val thread = HandlerThread("video-overlay").also { it.start() }
        overlayHandlerThread = thread
        // Target VIDEO_CAPTURE only — the preview already shows the stamp through the
        // StampPreviewView card below the camera; adding the overlay to the preview too
        // would show two stamps while filming (user flagged this). Overlay still bakes into
        // the recorded MP4 because VIDEO_CAPTURE is the target.
        val effect = OverlayEffect(
            CameraEffect.VIDEO_CAPTURE,
            0,
            Handler(thread.looper)
        ) { err ->
            android.util.Log.w("VideoStamp", "OverlayEffect draw error", err)
        }
        effect.setOnDrawListener { frame ->
            val factory = videoStampFactory ?: return@setOnDrawListener true
            val canvas = frame.overlayCanvas
            val cw = canvas.width
            val ch = canvas.height
            if (cw <= 0 || ch <= 0) return@setOnDrawListener true
            // Reset the overlay canvas to fully transparent before each frame. Without this,
            // some devices leave the overlay surface initialised to opaque black and the
            // card plate ends up fully black regardless of the alpha we drew into it.
            canvas.drawColor(0, PorterDuff.Mode.CLEAR)
            val (bmp, justRendered) = synchronized(this) {
                var cached = cachedVideoStamp
                val rendered = cached == null || cached.isRecycled ||
                    cachedStampWidth != cw || cachedStampHeight != ch
                if (rendered) {
                    cached?.recycle()
                    cached = runCatching { factory(cw, ch) }.getOrNull()
                    cachedVideoStamp = cached
                    cachedStampWidth = cw
                    cachedStampHeight = ch
                }
                cached to rendered
            }
            if (justRendered) {
                android.util.Log.d(
                    "VideoStamp",
                    "overlay first-frame canvas=${cw}x$ch bitmap=${bmp != null}"
                )
            }
            if (bmp != null && !bmp.isRecycled) {
                // 1:1 draw — bitmap was rendered at exactly the canvas size.
                canvas.drawBitmap(bmp, 0f, 0f, overlayPaint)
            }
            true
        }
        return effect
    }

    private fun releaseOverlay() {
        runCatching { overlayEffect?.close() }
        overlayEffect = null
        runCatching { overlayHandlerThread?.quitSafely() }
        overlayHandlerThread = null
        synchronized(this) {
            cachedVideoStamp?.recycle()
            cachedVideoStamp = null
            cachedStampWidth = 0
            cachedStampHeight = 0
        }
    }

    private fun updateZoomState() {
        val z = camera?.cameraInfo?.zoomState?.value ?: return
        _zoomState.value = ZoomState(z.zoomRatio, z.minZoomRatio, z.maxZoomRatio, z.linearZoom)
    }

    private fun rebind() {
        val provider = cameraProvider ?: return
        provider.unbindAll()
        val selector = CameraSelector.Builder().requireLensFacing(
            if (facing == Facing.BACK) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
        ).build()
        preview = Preview.Builder().setTargetAspectRatio(aspectMode.cameraXRatio).build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val captureMode = when (captureQuality) {
            CaptureQuality.LOW -> ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            else -> ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
        }
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(captureMode)
            .setTargetAspectRatio(aspectMode.cameraXRatio)
            .setFlashMode(when (flashMode) {
                FlashMode.OFF, FlashMode.TORCH -> ImageCapture.FLASH_MODE_OFF
                FlashMode.ON -> ImageCapture.FLASH_MODE_ON
                FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
            })
            .build()

        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(
                when (captureQuality) {
                    CaptureQuality.LOW -> Quality.SD
                    CaptureQuality.MEDIUM -> Quality.HD
                    CaptureQuality.HIGH -> Quality.FHD
                },
                FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
            )).build()
        videoCapture = VideoCapture.withOutput(recorder)

        // Bind by mode. VIDEO mode goes through a UseCaseGroup so we can attach the
        // OverlayEffect that burns the GPS stamp into the recorded frames in real-time.
        // PHOTO mode stays on the plain bindToLifecycle path (photos are stamped afterwards
        // by PhotoStampRenderer so no live overlay is needed).
        releaseOverlay()
        camera = try {
            if (mode == Mode.VIDEO) {
                try {
                    val effect = buildOverlayEffect().also { overlayEffect = it }
                    val group = UseCaseGroup.Builder()
                        .addUseCase(preview!!)
                        .addUseCase(videoCapture!!)
                        .addEffect(effect)
                        .build()
                    provider.bindToLifecycle(lifecycleOwner, selector, group)
                } catch (effectErr: Exception) {
                    // Effect binding failed (older device / unsupported combo). Fall back to
                    // plain preview + videoCapture so recording still works — just without
                    // the burned-in stamp. User can still see the stamp in StampPreviewView.
                    android.util.Log.w("VideoStamp", "OverlayEffect bind failed; using plain video", effectErr)
                    releaseOverlay()
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, videoCapture)
                }
            } else {
                provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            }
        } catch (e: Exception) {
            // Fall back to preview-only if the combination fails outright.
            android.util.Log.w("VideoStamp", "Full rebind failed; preview-only", e)
            releaseOverlay()
            provider.bindToLifecycle(lifecycleOwner, selector, preview)
        }
        camera?.cameraInfo?.zoomState?.observe(lifecycleOwner) {
            _zoomState.value = ZoomState(it.zoomRatio, it.minZoomRatio, it.maxZoomRatio, it.linearZoom)
        }
        val expRange = camera?.cameraInfo?.exposureState?.exposureCompensationRange
            ?: android.util.Range(0, 0)
        _exposureRange.value = expRange.lower..expRange.upper
        _exposureIndex.value = camera?.cameraInfo?.exposureState?.exposureCompensationIndex ?: 0
        // Restore torch after rebind — switching modes/facing unbinds the camera and resets torch.
        applyTorchState()
    }
}
