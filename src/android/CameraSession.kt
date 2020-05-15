package cordova.plugin.camerarecorder

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import com.cordova.plugin.camerarecorder.PreviewOptions
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import kotlinx.coroutines.suspendCancellableCoroutine
import org.apache.cordova.CallbackContext
import org.apache.cordova.PluginResult
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


class CameraSession(
        val activity: Activity,
        val options: PreviewOptions,
        val previewCtx: CallbackContext
) {
    private var previewing = false
    private var recording = false
    private lateinit var camera: CameraDevice
    private lateinit var captureSession: CameraCaptureSession
    private val imageFormat = ImageFormat.JPEG
    private val cameraThread = HandlerThread("CameraThread").apply { start() }
    private val callbackThread = HandlerThread("CallbackThread").apply { start() }

    /** [Handler] corresponding to [cameraThread] */
    private val cameraHandler = Handler(cameraThread.looper)

    /** Detects, characterizes, and connects to a CameraDevice (used for all camera operations) */
    private val cameraManager: CameraManager by lazy {
        activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private val imageReader: ImageReader by lazy {
        ImageReader.newInstance(options.canvasWidth, options.canvasHeight, imageFormat, 10)
    }

    private val supportSizes: Array<Size> by lazy {
        val streamConfigurationMap = cameraCharacteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]
        val sizes = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG)
        sizes
    }

    private val cameraId: String by lazy {
        var targetCameraId = ""
        for (cameraId in cameraManager.cameraIdList) {
            val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
            val cameraFacing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)
            if (options.cameraFacing == cameraFacing) {
                targetCameraId = cameraId
                break
            }
        }

        if (targetCameraId == "") {
            throw (Exception("Invalid facing: " + options.cameraFacing))
        }

        targetCameraId
    }

    private val cameraCharacteristics: CameraCharacteristics by lazy {
        cameraManager.getCameraCharacteristics(cameraId)
    }

    private val outputFile: File by lazy { createFile(activity, "mp4") }
    private val recorder: MediaRecorder by lazy { createRecorder(recorderSurface) }

    private val recorderSurface: Surface by lazy {
        // Get a persistent Surface from MediaCodec, don't forget to release when done
        val surface = MediaCodec.createPersistentInputSurface()

        // Prepare and release a dummy MediaRecorder with our new surface
        // Required to allocate an appropriately sized buffer before passing the Surface as the
        //  output target to the capture session
        createRecorder(surface).apply {
            prepare()
            release()
        }

        surface
    }

    /** Requests used for preview and recording in the [CameraCaptureSession] */
    private val recordRequest: CaptureRequest by lazy {
        // Capture request holds references to target surfaces
        captureSession.device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
            // Add the preview and recording surface targets
            addTarget(imageReader.surface)
            addTarget(recorderSurface)
            // Sets user requested FPS for all targets
            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(options.fps, options.fps))
        }.build()
    }

    fun onStop() {
    }

    fun onResume() {
    }

    fun onDestroy() {
        try {
            camera.close()
        } catch (ex: Exception) {
            Log.e(TAG, "camera destroyed failed", ex)
        }
    }

    suspend fun startCapture() {
        if (previewing) {
            throw(Exception("Already capturing"))
        }

        camera = openCamera(cameraManager, cameraHandler)
        val targets = listOf(imageReader.surface, recorderSurface)
        captureSession = createCaptureSession(targets, camera, cameraHandler)
        initImageReader()

        previewing = true
    }

    fun startRecord() {
        if (!previewing || recording) {
            throw(Exception("recorder is recording"))
        }

        captureSession.setRepeatingRequest(recordRequest, null, cameraHandler)
        recorder.apply {
            prepare()
            start()
        }

        recording = true
    }

    fun stop(): File? {
        if (!previewing) {
            return null
        }

        if (recording) {
            recorder.stop()
        }

        captureSession.stopRepeating()
        camera.close()

        if (!recording) {
            return null
        }

        previewing = false
        recording = false

        return outputFile
    }

    private fun onCaputre(image: Image) {
        val buffer: ByteBuffer = image.getPlanes().get(0).getBuffer()
        val jpegBytes = ByteArray(buffer.remaining())
        buffer.get(jpegBytes)

        Handler(callbackThread.looper).post {
            val start = System.currentTimeMillis()
            val jpegBuffer = ByteArrayInputStream(jpegBytes)
            val jpegMetadata = ImageMetadataReader.readMetadata(jpegBuffer)
            val directory = jpegMetadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)

            val meta = JSONObject()
            val data = JSONObject()
            val output = JSONObject()
            val images = JSONObject()
            val fullsize = JSONObject()

            for (tag in directory.tags) {
                meta.put(tag.tagName, directory.getObject(tag.tagType))
            }

            val imageData = "data:image/jpeg;base64," +
                    Base64.encodeToString(jpegBytes, Base64.DEFAULT)
            Log.i(TAG, "compress cost: " + (System.currentTimeMillis() - start))

            fullsize.put("data", imageData)
            fullsize.put("metadata", meta)
            fullsize.put("cameraFacing", options.facing)
            images.put("fullsize", fullsize)
            output.put("images", images)
            data.put("output", output)

            val res = PluginResult(PluginResult.Status.OK, data)
            res.keepCallback = true
            previewCtx.sendPluginResult(res)
            Log.i(TAG, "compress and callback cost: " + (System.currentTimeMillis() - start))
        }
    }

    /** Opens the camera and returns the opened device (as the result of the suspend coroutine) */
    @SuppressLint("MissingPermission")
    private suspend fun openCamera(
            manager: CameraManager,
            handler: Handler? = null
    ): CameraDevice = suspendCancellableCoroutine { cont ->
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) =
                    cont.resumeWith(Result.success(device))

            override fun onDisconnected(device: CameraDevice) {
                val msg = "Camera $cameraId has been disconnected"
                Log.w(TAG, msg)
                if (cont.isActive) {
                    cont.resumeWithException(RuntimeException(msg))
                }
            }

            override fun onError(device: CameraDevice, error: Int) {
                val msg = when (error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Device policy"
                    ERROR_CAMERA_IN_USE -> "Camera in use"
                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                    else -> "Unknown"
                }
                val exc = RuntimeException("Camera $cameraId error: ($error) $msg")
                Log.e(TAG, exc.message, exc)
                if (cont.isActive) cont.resumeWithException(exc)
            }
        }, handler)
    }

    private suspend fun createCaptureSession(
            targets: List<Surface>,
            device: CameraDevice,
            handler: Handler? = null
    ): CameraCaptureSession = suspendCoroutine { cont ->
        // Creates a capture session using the predefined targets, and defines a session state
        // callback which resumes the coroutine once the session is configured
        device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) =
                    cont.resumeWith(Result.success(session))

            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exc = RuntimeException("Camera ${device.id} session configuration failed")
                Log.e(TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }
        }, handler)
    }

    private fun initImageReader() {
        captureSession.setRepeatingRequest(
                captureSession.device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    //set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(options.fps, options.fps))
                    addTarget(imageReader.surface)
                }.build(),
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureSequenceAborted(session: CameraCaptureSession, sequenceId: Int) {
                        super.onCaptureSequenceAborted(session, sequenceId)
                        Log.i(TAG, "capture sequence aborted: " + sequenceId)
                    }

                    override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                        super.onCaptureFailed(session, request, failure)
                        Log.i(TAG, "capture failed: " + failure)
                    }
                }, cameraHandler)

        imageReader.setOnImageAvailableListener(object : ImageReader.OnImageAvailableListener {
            override fun onImageAvailable(reader: ImageReader?) {
                val image = imageReader.acquireNextImage()
                onCaputre(image)
                image.close()
            }
        }, cameraHandler)
    }

    private fun createRecorder(surface: Surface) = MediaRecorder().apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setVideoSource(MediaRecorder.VideoSource.SURFACE)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setOutputFile(outputFile.absolutePath)
        setVideoEncodingBitRate(RECORDER_VIDEO_BITRATE)
        if (options.fps > 0) setVideoFrameRate(options.fps)
        setVideoSize(options.captureWidth, options.captureHeight)
        setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setInputSurface(surface)
    }


    companion object {
        val TAG = CameraSession.javaClass.name

        private const val RECORDER_VIDEO_BITRATE: Int = 10_000_000

        /** Creates a [File] named with the current date and time */
        private fun createFile(context: Context, extension: String): File {
            val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
            return File(context.filesDir, "VID_${sdf.format(Date())}.$extension")
        }
    }
}
