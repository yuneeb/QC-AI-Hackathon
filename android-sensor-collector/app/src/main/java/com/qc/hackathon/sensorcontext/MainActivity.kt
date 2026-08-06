// Main screen: start/stop the sensor collection service and show live status.
package com.qc.hackathon.sensorcontext

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.qc.hackathon.sensorcontext.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    // Permissions we need to ask the user for at runtime
    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ACTIVITY_RECOGNITION,
        Manifest.permission.POST_NOTIFICATIONS   // required on Android 13+ for foreground service notification
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestMissingPermissions()
        showDeviceIp()

        // Start/Stop button toggles the background service
        binding.btnToggle.setOnClickListener {
            if (isRunning) stopCollection() else startCollection()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    private fun startCollection() {
        isRunning = true
        binding.btnToggle.text = getString(R.string.stop_collection)
        binding.tvStatus.text = getString(R.string.status_running)
        // startForegroundService sends a message to Android to start SensorCollectorService
        startForegroundService(Intent(this, SensorCollectorService::class.java))
        handler.post(statusRefreshRunnable)
    }

    private fun stopCollection() {
        isRunning = false
        binding.btnToggle.text = getString(R.string.start_collection)
        binding.tvStatus.text = getString(R.string.status_stopped)
        stopService(Intent(this, SensorCollectorService::class.java))
        handler.removeCallbacks(statusRefreshRunnable)
    }

    // Every 5s: reads the JSON file written by ContextFileWriter and shows a preview on screen
    private val statusRefreshRunnable = object : Runnable {
        override fun run() {
            val file = File(getExternalFilesDir(null), "context_snapshot.json")
            if (file.exists()) {
                binding.tvJsonPreview.text = file.readText().take(400)
            }
            handler.postDelayed(this, SensorCollectorService.COLLECTION_INTERVAL_MS)
        }
    }

    private fun showDeviceIp() {
        binding.tvHttpUrl.text = "Fetch data at: http://10.73.51.106:8080/context"
    }

    // Checks which permissions are missing and shows the system dialog to request them
    private fun requestMissingPermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 0)
        }
    }
}