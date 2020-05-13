package com.cordova.plugin.camerarecorder

import android.hardware.camera2.CameraCharacteristics
import org.json.JSONObject


class RecordOptions() {

}

class PreviewOptions() {
    open var fps = 30
    open var width = 352
    open var height = 288
    open var canvasWidth = 352
    open var canvasHeight = 288
    open var captureWidth = 352
    open var captureHeight = 288
    open var hasThumbnail = false
    open var thumbnailRatio: Double = 1 / 6.0
    open var use = "data"
    open var flashMode = CameraCharacteristics.FLASH_MODE_OFF
    open var cameraFacing = CameraCharacteristics.LENS_FACING_FRONT

    protected val K_USE_KEY: String? = "use"
    protected val K_FPS_KEY = "fps"
    protected val K_WIDTH_KEY = "width"
    protected val K_HEIGHT_KEY = "height"
    protected val K_CANVAS_KEY = "canvas"
    protected val K_CAPTURE_KEY = "capture"
    protected val K_FLASH_MODE_KEY = "flashMode"
    protected val K_HAS_THUMBNAIL_KEY = "hasThumbnail"
    protected val K_THUMBNAIL_RATIO_KEY = "thumbnailRatio"
    protected val K_LENS_ORIENTATION_KEY = "cameraFacing"

    private fun getFlashMode(isFlashModeOn: Boolean): Int {
        return if (isFlashModeOn) {
            CameraCharacteristics.FLASH_MODE_TORCH
        } else {
            CameraCharacteristics.FLASH_MODE_OFF
        }
    }

    private fun getCameraFacing(option: String): Int {
        return if ("front" == option) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }
    }

    open fun parseOptions(options: JSONObject) {
        if (options == null) {
            return
        }

        // usage
        if (options.has(K_USE_KEY)) {
            use = options.getString(K_USE_KEY)
        }

        // flash mode
        if (options.has(K_FLASH_MODE_KEY)) {
            flashMode = getFlashMode(options.getBoolean(K_FLASH_MODE_KEY))
        }

        // lens orientation
        if (options.has(K_LENS_ORIENTATION_KEY)) {
            cameraFacing = getCameraFacing(options.getString(K_LENS_ORIENTATION_KEY))
        }

        // fps
        if (options.has(K_FPS_KEY)) {
            fps = options.getInt(K_FPS_KEY)
        }

        // width
        if (options.has(K_WIDTH_KEY)) {
            canvasWidth = options.getInt(K_WIDTH_KEY)
            captureWidth = canvasWidth
            width = captureWidth
        }

        // height
        if (options.has(K_HEIGHT_KEY)) {
            canvasHeight = options.getInt(K_HEIGHT_KEY)
            captureHeight = canvasHeight
            height = captureHeight
        }

        // hasThumbnail
        if (options.has(K_HAS_THUMBNAIL_KEY)) {
            hasThumbnail = options.getBoolean(K_HAS_THUMBNAIL_KEY)
        }

        // thumbnailRatio
        if (options.has(K_THUMBNAIL_RATIO_KEY)) {
            thumbnailRatio = options.getDouble(K_THUMBNAIL_RATIO_KEY)
        }

        // canvas
        if (options.has(K_CANVAS_KEY)) {
            val canvas = options.getJSONObject(K_CANVAS_KEY)
            if (canvas.has(K_WIDTH_KEY)) {
                canvasWidth = canvas.getInt(K_WIDTH_KEY)
            }
            if (canvas.has(K_HEIGHT_KEY)) {
                canvasHeight = canvas.getInt(K_HEIGHT_KEY)
            }
        }

        // capture
        if (options.has(K_CAPTURE_KEY)) {
            val capture = options.getJSONObject(K_CAPTURE_KEY)
            // resolution.width
            if (capture.has(K_WIDTH_KEY)) {
                captureWidth = capture.getInt(K_WIDTH_KEY)
            }
            // resolution.height
            if (capture.has(K_HEIGHT_KEY)) {
                captureHeight = capture.getInt(K_HEIGHT_KEY)
            }
        }

    }
}
