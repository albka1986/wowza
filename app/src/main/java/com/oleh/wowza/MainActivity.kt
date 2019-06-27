package com.oleh.wowza

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.wowza.gocoder.sdk.api.WowzaGoCoder
import com.wowza.gocoder.sdk.api.broadcast.WOWZBroadcast
import com.wowza.gocoder.sdk.api.broadcast.WOWZBroadcastConfig
import com.wowza.gocoder.sdk.api.configuration.WOWZMediaConfig
import com.wowza.gocoder.sdk.api.devices.WOWZAudioDevice
import com.wowza.gocoder.sdk.api.player.WOWZPlayerConfig
import com.wowza.gocoder.sdk.api.status.WOWZState
import com.wowza.gocoder.sdk.api.status.WOWZStatus
import com.wowza.gocoder.sdk.api.status.WOWZStatusCallback
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity(), WOWZStatusCallback, View.OnClickListener {


    // The top-level GoCoder API interface
    private var goCoder: WowzaGoCoder? = null
    // The GoCoder SDK audio device
    private val goCoderAudioDevice by lazy { WOWZAudioDevice() }
    // The GoCoder SDK broadcaster
    private val goCoderBroadcaster by lazy { WOWZBroadcast() }
    private val wowzaKey = "GOSK-9946-010C-02C3-9F78-1CEC"
    // The broadcast configuration settings
    private val goCoderBroadcastConfig by lazy {
        WOWZBroadcastConfig(WOWZMediaConfig.FRAME_SIZE_640x480).apply {
            hostAddress = "192.168.89.30"
            portNumber = 1935
            applicationName = "EP"
            streamName = "Android"
            username = "easternpeak"
            password = "12345678"
        }
    }

    //Player config
    private val mStreamPlayerConfig by lazy {
        WOWZPlayerConfig().apply {
            isPlayback = true
            hostAddress = "192.168.89.30"
            applicationName = "EP"
            streamName = "Android"
            portNumber = 1935
            isVideoEnabled = true
            isAudioEnabled = true
        }
    }

    // Properties needed for Android 6+ permissions handling
    private val PERMISSIONS_REQUEST_CODE = 0x1
    private var mPermissionsGranted = true
    private val mRequiredPermissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize the GoCoder SDK
        goCoder = WowzaGoCoder.init(applicationContext, wowzaKey)

        if (goCoder == null) {
            // If initialization failed, retrieve the last error and display it
            val goCoderInitError = WowzaGoCoder.getLastError()
            Toast.makeText(
                this,
                "GoCoder SDK error: " + goCoderInitError.errorDescription,
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Associate the onClick() method as the callback for the broadcast button's click event
        val broadcastButton = findViewById<Button>(R.id.broadcast_button)
        broadcastButton.setOnClickListener(this)
        play_button.setOnClickListener {
            vwStreamPlayer.play(mStreamPlayerConfig, this)
        }
    }


    //
    // Enable Android's immersive, sticky full-screen mode|
    //
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        val rootView = window.decorView.findViewById<View>(android.R.id.content)
        if (rootView != null)
            rootView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    //
    // Called when an activity is brought to the foreground
    //
    override fun onResume() {
        super.onResume()

        // If running on Android 6 (Marshmallow) and later, check to see if the necessary permissions
        // have been granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mPermissionsGranted = hasPermissions(mRequiredPermissions)
            if (!mPermissionsGranted)
                ActivityCompat.requestPermissions(this, mRequiredPermissions, PERMISSIONS_REQUEST_CODE)
        } else
            mPermissionsGranted = true

        // Start the camera preview display
        if (mPermissionsGranted) {
            if (camera_preview.isPreviewPaused)
                camera_preview.onResume()
            else
                camera_preview.startPreview()
        }
        // Designate the camera preview as the video source
        goCoderBroadcastConfig.videoBroadcaster = camera_preview

        // Designate the audio device as the audio broadcaster
        goCoderBroadcastConfig.audioBroadcaster = goCoderAudioDevice


    }

    //
    // Callback invoked in response to a call to ActivityCompat.requestPermissions() to interpret
    // the results of the permissions request
    //
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        mPermissionsGranted = true
        when (requestCode) {
            PERMISSIONS_REQUEST_CODE -> {
                // Check the result of each permission granted
                for (grantResult in grantResults) {
                    if (grantResult != PackageManager.PERMISSION_GRANTED) {
                        mPermissionsGranted = false
                    }
                }
            }
        }
    }

    //
    // Utility method to check the status of a permissions request for an array of permission identifiers
    //
    private fun hasPermissions(permissions: Array<String>): Boolean {
        for (permission in permissions)
            if (checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED)
                return false

        return true
    }

    //
    // The callback invoked upon changes to the state of the broadcast
    //
    override fun onWZStatus(goCoderStatus: WOWZStatus) {
        // A successful status transition has been reported by the GoCoder SDK
        val statusMessage = StringBuffer("Broadcast status: ")

        when (goCoderStatus.state) {
            WOWZState.STARTING -> statusMessage.append("Broadcast initialization")

            WOWZState.READY -> statusMessage.append("Ready to begin streaming")

            WOWZState.RUNNING -> statusMessage.append("Streaming is active")

            WOWZState.STOPPING -> statusMessage.append("Broadcast shutting down")

            WOWZState.IDLE -> statusMessage.append("The broadcast is stopped")

            else -> return
        }

        // Display the status message using the U/I thread
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                this@MainActivity,
                statusMessage,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    //
    // The callback invoked when an error occurs during a broadcast
    //
    override fun onWZError(goCoderStatus: WOWZStatus) {
        // If an error is reported by the GoCoder SDK, display a message
        // containing the error details using the U/I thread
        Handler(Looper.getMainLooper()).post(Runnable {
            Toast.makeText(
                this@MainActivity,
                "Streaming error: " + goCoderStatus.lastError.errorDescription,
                Toast.LENGTH_LONG
            ).show()
        })
    }

    //
    // The callback invoked when the broadcast button is tapped
    //
    override fun onClick(view: View) {
        // return if the user hasn't granted the app the necessary permissions
        if (!mPermissionsGranted) return

        // Ensure the minimum set of configuration settings have been specified necessary to
        // initiate a broadcast streaming session
        val configValidationError = goCoderBroadcastConfig.validateForBroadcast()

        if (configValidationError != null) {
            Toast.makeText(this, configValidationError.errorDescription, Toast.LENGTH_LONG).show()
        } else if (goCoderBroadcaster.status?.isRunning!!) {
            // Stop the broadcast that is currently running
            goCoderBroadcaster.endBroadcast(this)
        } else {
            // Start streaming
            goCoderBroadcaster.startBroadcast(goCoderBroadcastConfig, this)
        }
    }
}
