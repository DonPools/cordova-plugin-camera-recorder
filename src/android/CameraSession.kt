package cordova.plugin.camerarecorder

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.util.Log
import android.util.Range
import android.view.Surface
import android.view.WindowManager
import com.cordova.plugin.camerarecorder.PreviewOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import org.apache.cordova.CallbackContext
import org.apache.cordova.PluginResult
import org.json.JSONObject
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
    private lateinit var imageReader: ImageReader

    private val cameraThread = HandlerThread("CameraThread").apply { start() }
    private val callbackThread = HandlerThread("CallbackThread").apply { start() }

    /** [Handler] corresponding to [cameraThread] */
    private val cameraHandler = Handler(cameraThread.looper)

    /** Detects, characterizes, and connects to a CameraDevice (used for all camera operations) */
    private val cameraManager: CameraManager by lazy {
        activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private val windowManager: WindowManager by lazy {
        activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private val outputFile: File by lazy { createFile(activity, "mp4") }
    private val recorder: MediaRecorder by lazy { createRecorder() }


    open suspend fun startCapture() {
        if (previewing) {
            throw(Exception("Already capturing"))
        }

        val cameraId = getCameraId(options.cameraFacing)
        camera = openCamera(cameraManager, cameraId, cameraHandler)

        imageReader = ImageReader.newInstance(options.canvasWidth, options.canvasHeight, ImageFormat.JPEG, 2)
        captureSession = createCaptureSession(listOf(imageReader.surface), camera, cameraHandler)
        Log.i(TAG, "capture sesssion created.")
        initImageReader(imageReader, cameraHandler)

        previewing = true
    }


    open fun startRecord() {
        if (!previewing || recording) {
            throw(Exception("recorder is recording"))
        }

        recorder.apply {
            prepare()
            start()
        }

        recording = true
    }

    open fun stop(): Boolean {
        return true
    }

    private fun onCaputre(image: Image) {

        val buffer: ByteBuffer = image.getPlanes().get(0).getBuffer()
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val outStart = System.currentTimeMillis()
        Handler(callbackThread.looper).post {
            val start = System.currentTimeMillis()
            val imageData = "data:image/jpeg;base64," +
                    Base64.encodeToString(bytes, Base64.DEFAULT)

            val data = JSONObject()
            val output = JSONObject()
            val images = JSONObject()
            val fullsize = JSONObject()

            fullsize.put("data", imageData)
            images.put("fullsize", fullsize)
            output.put("images", images)
            data.put("output", output)

            val res = PluginResult(PluginResult.Status.OK, data)
            res.keepCallback = true
            previewCtx.sendPluginResult(res)
            Log.i(TAG, "callback cost: " + (System.currentTimeMillis() - start))
        }
        Log.i(TAG, "outside callback cost: " + (System.currentTimeMillis() - outStart))
    }

    private fun getCameraId(facing: Int): String {
        for (cameraId in cameraManager.cameraIdList) {
            val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
            val cameraFacing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)
            if (facing == cameraFacing) {
                return cameraId
            }
        }

        throw (Exception("Invalid facing: " + facing))
    }

    /** Opens the camera and returns the opened device (as the result of the suspend coroutine) */
    @SuppressLint("MissingPermission")
    private suspend fun openCamera(
            manager: CameraManager,
            cameraId: String,
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

    private fun initImageReader(imageReader: ImageReader, handler: Handler?) {
        captureSession.setRepeatingRequest(
                captureSession.device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(1, options.fps))
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
        }, handler)
    }

    /** Creates a [MediaRecorder] instance using the provided [Surface] as input */
    private fun createRecorder() = MediaRecorder().apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setVideoSource(MediaRecorder.VideoSource.SURFACE)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setOutputFile(outputFile.absolutePath)
        setVideoEncodingBitRate(RECORDER_VIDEO_BITRATE)
        if (options.fps > 0) setVideoFrameRate(options.fps)
        setVideoSize(options.width, options.height)
        setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
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
