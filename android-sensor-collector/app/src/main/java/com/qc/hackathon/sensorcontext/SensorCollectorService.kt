// Foreground service: reads all sensors every 5s and builds a ContextPayload.
package com.qc.hackathon.sensorcontext

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import java.time.Instant

class SensorCollectorService : Service(), SensorEventListener {

    companion object {
        const val COLLECTION_INTERVAL_MS = 5000L
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "sensor_context_channel"
    }

    private lateinit var sensorManager: SensorManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var activityDetector: ActivityDetector
    private lateinit var locationDetector: LocationDetector
    private lateinit var fileWriter: ContextFileWriter
    private lateinit var httpServer: ContextHttpServer
    private val handler = Handler(Looper.getMainLooper())

    // Latest raw sensor values — updated continuously by onSensorChanged
    private var accel: Vector3? = null
    private var gyro: Vector3? = null
    private var linearAccel: Vector3? = null
    private var gravity: Vector3? = null
    private var rotationVec: Vector3? = null
    private var magnetometer: Vector3? = null
    private var pressure: Float? = null
    private var ambientLight: Float? = null
    private var proximity: Float? = null
    private var temperature: Float? = null
    private var humidity: Float? = null
    private var significantMotion = false
    private var lastLocation: android.location.Location? = null

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        activityDetector = ActivityDetector()
        locationDetector = LocationDetector()
        fileWriter = ContextFileWriter(this)
        httpServer = ContextHttpServer()

        startForegroundNotification()
        registerSensors()
        startLocationUpdates()
        httpServer.start()
        handler.post(collectionRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(collectionRunnable)
        sensorManager.unregisterListener(this)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        httpServer.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Sensor registration ───────────────────────────────────────────────────

    private fun registerSensors() {
        // Helper to register a sensor type if the device has it
        fun reg(type: Int) {
            sensorManager.getDefaultSensor(type)?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
        reg(Sensor.TYPE_ACCELEROMETER)
        reg(Sensor.TYPE_GYROSCOPE)
        reg(Sensor.TYPE_LINEAR_ACCELERATION)
        reg(Sensor.TYPE_GRAVITY)
        reg(Sensor.TYPE_ROTATION_VECTOR)
        reg(Sensor.TYPE_MAGNETIC_FIELD)
        reg(Sensor.TYPE_PRESSURE)
        reg(Sensor.TYPE_LIGHT)
        reg(Sensor.TYPE_PROXIMITY)
        reg(Sensor.TYPE_AMBIENT_TEMPERATURE)
        reg(Sensor.TYPE_RELATIVE_HUMIDITY)
        reg(Sensor.TYPE_STEP_DETECTOR)
        // Significant motion is a one-shot trigger — re-armed after each fire
        sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)?.let {
            sensorManager.requestTriggerSensor(significantMotionTrigger, it)
        }
    }

    // ── SensorEventListener callbacks ────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER       -> accel = Vector3(v[0], v[1], v[2])
            Sensor.TYPE_GYROSCOPE           -> gyro = Vector3(v[0], v[1], v[2])
            Sensor.TYPE_LINEAR_ACCELERATION -> linearAccel = Vector3(v[0], v[1], v[2])
            Sensor.TYPE_GRAVITY             -> gravity = Vector3(v[0], v[1], v[2])
            Sensor.TYPE_ROTATION_VECTOR     -> rotationVec = Vector3(v[0], v[1], v[2])
            Sensor.TYPE_MAGNETIC_FIELD      -> magnetometer = Vector3(v[0], v[1], v[2])
            Sensor.TYPE_PRESSURE            -> pressure = v[0]
            Sensor.TYPE_LIGHT               -> ambientLight = v[0]
            Sensor.TYPE_PROXIMITY           -> proximity = v[0]
            Sensor.TYPE_AMBIENT_TEMPERATURE -> temperature = v[0]
            Sensor.TYPE_RELATIVE_HUMIDITY   -> humidity = v[0]
            Sensor.TYPE_STEP_DETECTOR       -> activityDetector.onStepDetected()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private val significantMotionTrigger = object : android.hardware.TriggerEventListener() {
        override fun onTrigger(event: android.hardware.TriggerEvent) {
            significantMotion = true
            sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)?.let {
                sensorManager.requestTriggerSensor(this, it)  // re-arm for next trigger
            }
        }
    }

    // ── GPS location updates ──────────────────────────────────────────────────

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            lastLocation = result.lastLocation
        }
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(COLLECTION_INTERVAL_MS)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()
        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            // Location permission not yet granted — location fields will be null
        }
    }

    // ── 5-second collection cycle ─────────────────────────────────────────────

    private val collectionRunnable = object : Runnable {
        override fun run() {
            val payload = buildPayload()
            fileWriter.write(payload)
            httpServer.updatePayload(payload)
            handler.postDelayed(this, COLLECTION_INTERVAL_MS)
        }
    }

    private fun buildPayload(): ContextPayload {
        val loc = lastLocation
        val btDevices = getBluetoothDevices()
        val speedKmh = loc?.speed?.times(3.6f)  // convert m/s → km/h

        // Pass the 3 key signals to ActivityDetector for activity inference
        val activity = activityDetector.infer(
            speedKmh = speedKmh,
            linearAccelMagnitude = magnitude(linearAccel),
            bluetoothConnected = btDevices.isNotEmpty()
        )

        return ContextPayload(
            device_id = fetchDeviceId(),
            timestamp = Instant.now().toString(),
            inferred_activity = activity,
            location = loc?.let {
                LocationData(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    altitude_m = it.altitude,
                    accuracy_m = it.accuracy,
                    speed_kmh = it.speed * 3.6f,
                    bearing_deg = it.bearing,
                    cardinal_direction = locationDetector.getCardinalDirection(it.bearing)
                )
            },
            sensors = SensorData(
                accelerometer = accel,
                gyroscope = gyro,
                linear_acceleration = linearAccel,
                gravity = gravity,
                rotation_vector = rotationVec,
                magnetometer = magnetometer,
                pressure_hpa = pressure,
                ambient_light_lux = ambientLight,
                proximity_cm = proximity,
                temperature_c = temperature,
                humidity_percent = humidity,
                step_count = 0,
                significant_motion_detected = significantMotion.also { significantMotion = false }
            ),
            device_state = buildDeviceState(btDevices)
        )
    }

    // ── Device state helpers ──────────────────────────────────────────────────

    private fun buildDeviceState(btDevices: List<String>): DeviceState {
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryLevel = if (scale > 0) (level * 100 / scale) else -1
        val chargeStatus = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = chargeStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
                chargeStatus == BatteryManager.BATTERY_STATUS_FULL
        val chargeType = when (batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
            else -> "NONE"
        }

        val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val notifManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        val caps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        val wifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val networkType = when {
            wifiConnected -> "WIFI"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> getCellularType(telephonyManager)
            else -> "NONE"
        }

        return DeviceState(
            screen_on = powerManager.isInteractive,
            battery_level = batteryLevel,
            charging = charging,
            charge_type = chargeType,
            ringer_mode = when (audioManager.ringerMode) {
                AudioManager.RINGER_MODE_NORMAL -> "NORMAL"
                AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE"
                else -> "SILENT"
            },
            dnd_active = notifManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL,
            call_state = when (telephonyManager.callState) {
                TelephonyManager.CALL_STATE_RINGING -> "RINGING"
                TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK"
                else -> "IDLE"
            },
            headphones_connected = audioManager.isWiredHeadsetOn,
            audio_output = when {
                audioManager.isBluetoothA2dpOn -> "BLUETOOTH"
                audioManager.isWiredHeadsetOn -> "WIRED_HEADSET"
                else -> "SPEAKER"
            },
            wifi_connected = wifiConnected,
            network_type = networkType,
            bluetooth_connected_devices = btDevices,
            foreground_app = getForegroundApp(),
            ambient_noise_db = null  // reserved for future mic-based measurement
        )
    }

    private fun getBluetoothDevices(): List<String> {
        return try {
            val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            btManager.adapter?.bondedDevices
                ?.filter { it.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED }
                ?.map { it.name ?: "Unknown" }
                ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    private fun getCellularType(tm: TelephonyManager): String {
        return try {
            when (tm.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_HSPA -> "3G"
                else -> "CELLULAR"
            }
        } catch (e: SecurityException) { "CELLULAR" }
    }

    private fun getForegroundApp(): String? {
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE)
                    as android.app.usage.UsageStatsManager
            val now = System.currentTimeMillis()
            usm.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_DAILY, now - 10000, now
            )?.maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (e: Exception) {
            null  // requires PACKAGE_USAGE_STATS — user must grant manually in Settings
        }
    }

    private fun fetchDeviceId(): String =
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    // Computes the magnitude (total intensity) of a 3-axis vector
    private fun magnitude(v: Vector3?): Float {
        if (v == null) return 0f
        return kotlin.math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z)
    }

    // ── Foreground notification (keeps service alive when app is closed) ──────

    private fun startForegroundNotification() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Sensor Context", NotificationManager.IMPORTANCE_LOW
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Android Context")
            .setContentText("Collecting sensor data…")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }
}