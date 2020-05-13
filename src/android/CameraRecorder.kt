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
    var session: CameraSession? = null
    lateinit var activity: Activity

    override fun initialize(cordova: CordovaInterface, webView: CordovaWebView?) {
        activity = cordova.activity
        super.initialize(cordova, webView)
    }

    @Throws(JSONException::class)
    override fun execute(action: String, data: JSONArray, callbackContext: CallbackContext): Boolean {
        Log.i(TAG, "execute: " + action + "  callbackId: " + callbackContext.callbackId)
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
    }

    override fun onResume(multitasking: Boolean) {
        super.onResume(multitasking)
        Log.i(TAG, "onResume")
    }

    fun handleStartCapture(data: JSONArray, callbackContext: CallbackContext): Boolean {
        if (session != null) {
            callbackContext.error("Capture session duplicated")
            return true
        }

        if (!PermissionHelper.hasPermission(this, Manifest.permission.CAMERA)) {
            deferPluginResultCallback(callbackContext)
            PermissionHelper.requestPermission(this, SEC_START_CAPTURE, Manifest.permission.CAMERA)
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

        session = CameraSession(activity, options, callbackContext)
        deferPluginResultCallback(callbackContext)
        GlobalScope.launch {
            try {
                session!!.startCapture()
            } catch (ex: Exception) {
                Log.e(TAG, "startCapture failed.", ex)
                callbackContext.error("startCapture failed: " + ex)
                session = null
            }
        }

        return true
    }

    fun handleStartRecord(args: JSONArray, callbackContext: CallbackContext): Boolean {
        if (session == null) {
            callbackContext.error("Capture session not started")
            return true
        }

        try {
            session!!.startRecord()
        } catch (ex: Exception) {
            Log.e(TAG, "startRecord failed.", ex)
            callbackContext.error("Start record failed:" + ex)
        }

        return true
    }

    fun handleStopCapture(args: JSONArray, callbackContext: CallbackContext): Boolean {
        if (session == null) {
            callbackContext.error("Capture session not started")
            return false
        }

        //session!!.stop(callbackContext)
        session = null
        return true
    }


    companion object {
        protected val TAG = this.javaClass.name
    }
}