package com.cordova.plugin.camerarecorder

import android.Manifest
import android.app.Activity
import android.util.Log
import cordova.plugin.camerarecorder.CameraSession
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.apache.cordova.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject


fun deferPluginResultCallback(callbackContext: CallbackContext) {
    val pluginResult = PluginResult(PluginResult.Status.NO_RESULT)
    pluginResult.keepCallback = true
    callbackContext.sendPluginResult(pluginResult)
}

const val SEC_START_CAPTURE = 0

class CordovaCameraRecorder : CordovaPlugin() {
    var cameraSession: CameraSession? = null
    lateinit var activity: Activity

    override fun initialize(cordova: CordovaInterface, webView: CordovaWebView?) {
        activity = cordova.activity
        super.initialize(cordova, webView)
    }

    @Throws(JSONException::class)
    override fun execute(action: String, data: JSONArray, callbackContext: CallbackContext): Boolean {
        Log.i(TAG, "exec: " + action + " data: " + data)
        when (action) {
            "startCapture" -> handleStartCapture(data, callbackContext)
            "startRecord" -> handleStartRecord(data, callbackContext)
            "stopCapture" -> handleStopCapture(data, callbackContext)
            else -> {
                return false
            }
        }

        return true
    }

    override fun onStop() {
        super.onStop()
        Log.i(TAG, "onStop")
        cameraSession?.onStop()
    }

    override fun onResume(multitasking: Boolean) {
        super.onResume(multitasking)
        Log.i(TAG, "onResume")
        cameraSession?.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy")
        cameraSession?.onDestroy()
    }

    fun handleStartCapture(data: JSONArray, callbackContext: CallbackContext): Boolean {
        if (cameraSession != null) {
            callbackContext.error("Capture session duplicated")
            return true
        }

        if (!PermissionHelper.hasPermission(this, Manifest.permission.CAMERA) ||
                !PermissionHelper.hasPermission(this, Manifest.permission.RECORD_AUDIO) ||
                !PermissionHelper.hasPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ||
                !PermissionHelper.hasPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ) {
            deferPluginResultCallback(callbackContext)
            PermissionHelper.requestPermissions(
                    this, SEC_START_CAPTURE,
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            )
            return true
        }

        var args: JSONObject
        var options = PreviewOptions()
        try {
            args = data.getJSONObject(0)
            options.parseOptions(args)
        } catch (e: Exception) {
            callbackContext.error("Parse argument failed: " + e)
            return true
        }

        cameraSession = CameraSession(activity, options, callbackContext)
        deferPluginResultCallback(callbackContext)
        GlobalScope.launch {
            try {
                cameraSession!!.startCapture()
            } catch (ex: Exception) {
                Log.e(TAG, "startCapture failed.", ex)
                callbackContext.error("startCapture failed: " + ex)
                cameraSession = null
            }
        }

        return true
    }

    fun handleStartRecord(args: JSONArray, callbackContext: CallbackContext): Boolean {
        if (cameraSession == null) {
            callbackContext.error("Capture session not started")
            return true
        }

        cordova.threadPool.submit {
            try {
                cameraSession!!.startRecord()
            } catch (ex: Exception) {
                Log.e(TAG, "startRecord failed.", ex)
                callbackContext.error("Start record failed:" + ex)
            }

            callbackContext.success("OK")
        }

        return true
    }

    fun handleStopCapture(args: JSONArray, callbackContext: CallbackContext): Boolean {
        if (cameraSession == null) {
            callbackContext.error("Capture session not started")
            return false
        }

        val prevSession = cameraSession
        cordova.threadPool.submit {
            try {
                val outputFile = prevSession!!.stop()

                val result = JSONObject()
                result.put("file", outputFile?.absolutePath)
                callbackContext.success(result)
            } catch (ex: Exception) {
                Log.e(TAG, "stop failed", ex)
                callbackContext.error("stop failed: " + ex)
            }


        }

        cameraSession = null
        return true
    }


    companion object {
        protected val TAG = this.javaClass.name
    }
}