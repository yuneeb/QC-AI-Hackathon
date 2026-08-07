// Data model for one context snapshot. All sensor data lives here.
package com.qc.hackathon.sensorcontext

import com.google.gson.GsonBuilder

// 3-axis sensor reading (accelerometer, gyroscope, etc.)
data class Vector3(val x: Float, val y: Float, val z: Float)

// GPS-derived location and speed
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val altitude_m: Double,
    val accuracy_m: Float,
    val speed_kmh: Float,
    val bearing_deg: Float
)

// All SensorManager-based motion and environment readings
data class SensorData(
    val accelerometer: Vector3?,
    val gyroscope: Vector3?,
    val linear_acceleration: Vector3?,
    val gravity: Vector3?,
    val rotation_vector: Vector3?,
    val magnetometer: Vector3?,
    val pressure_hpa: Float?,
    val ambient_light_lux: Float?,
    val proximity_cm: Float?,
    val temperature_c: Float?,
    val humidity_percent: Float?,
    val step_count: Int,
    val significant_motion_detected: Boolean
)

// System-level device state (not from SensorManager)
data class DeviceState(
    val screen_on: Boolean,
    val battery_level: Int,
    val charging: Boolean,
    val charge_type: String,        // "AC", "USB", "WIRELESS", "NONE"
    val ringer_mode: String,        // "NORMAL", "VIBRATE", "SILENT"
    val dnd_active: Boolean,
    val call_state: String,         // "IDLE", "RINGING", "OFFHOOK"
    val headphones_connected: Boolean,
    val audio_output: String,       // "SPEAKER", "WIRED_HEADSET", "BLUETOOTH"
    val wifi_connected: Boolean,
    val network_type: String,       // "WIFI", "LTE", "5G", "3G", "NONE"
    val bluetooth_connected_devices: List<String>,
    val foreground_app: String?,
    val ambient_noise_db: Float?,
    val battery_score: Int,          // 1 (bad) to 5 (best)
    val voice_confidence: Int        // 0 to 100
)

// Rule-based activity inference result
data class InferredActivity(
    val activity: String,   // "IN_VEHICLE", "RUNNING", "WALKING", "STILL", "UNKNOWN"
    val confidence: Int     // 0–100
)

// Top-level snapshot sent every 5 seconds
data class ContextPayload(
    val device_id: String,
    val timestamp: String,
    val platform: String = "android",
    val collection_interval_ms: Long = 5000L,
    val inferred_activity: InferredActivity,
    val location: LocationData?,
    val sensors: SensorData,
    val device_state: DeviceState
) {
    // Converts this object to a pretty-printed JSON string
    fun toJson(): String = GsonBuilder().setPrettyPrinting().create().toJson(this)
}