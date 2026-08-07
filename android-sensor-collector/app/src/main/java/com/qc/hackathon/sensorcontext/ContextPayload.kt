// Data model for one context snapshot. All sensor data lives here.
package com.qc.hackathon.sensorcontext

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName

// 3-axis sensor reading (accelerometer, gyroscope, etc.)
data class Vector3(val x: Float, val y: Float, val z: Float)

// GPS-derived location and speed
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    @SerializedName("altitude_m") val altitudeM: Double,
    @SerializedName("accuracy_m") val accuracyM: Float,
    @SerializedName("speed_kmh") val speedKmh: Float,
    @SerializedName("bearing_deg") val bearingDeg: Float,
    @SerializedName("cardinal_direction") val cardinalDirection: String?,
)

// All SensorManager-based motion and environment readings
data class SensorData(
    val accelerometer: Vector3?,
    val gyroscope: Vector3?,
    @SerializedName("linear_acceleration") val linearAcceleration: Vector3?,
    val gravity: Vector3?,
    @SerializedName("rotation_vector") val rotationVector: Vector3?,
    val magnetometer: Vector3?,
    @SerializedName("pressure_hpa") val pressureHpa: Float?,
    @SerializedName("ambient_light_lux") val ambientLightLux: Float?,
    @SerializedName("proximity_cm") val proximityCm: Float?,
    @SerializedName("temperature_c") val temperatureC: Float?,
    @SerializedName("humidity_percent") val humidityPercent: Float?,
    @SerializedName("step_count") val stepCount: Int,
    @SerializedName("significant_motion_detected") val significantMotionDetected: Boolean,
)

// System-level device state (not from SensorManager)
data class DeviceState(
    @SerializedName("screen_on") val screenOn: Boolean,
    @SerializedName("battery_level") val batteryLevel: Int,
    val charging: Boolean,
    @SerializedName("charge_type") val chargeType: String,        // "AC", "USB", "WIRELESS", "NONE"
    @SerializedName("ringer_mode") val ringerMode: String,        // "NORMAL", "VIBRATE", "SILENT"
    @SerializedName("dnd_active") val dndActive: Boolean,
    @SerializedName("call_state") val callState: String,         // "IDLE", "RINGING", "OFFHOOK"
    @SerializedName("headphones_connected") val headphonesConnected: Boolean,
    @SerializedName("audio_output") val audioOutput: String,       // "SPEAKER", "WIRED_HEADSET", "BLUETOOTH"
    @SerializedName("wifi_connected") val wifiConnected: Boolean,
    @SerializedName("network_type") val networkType: String,       // "WIFI", "LTE", "5G", "3G", "NONE"
    @SerializedName("bluetooth_connected_devices") val bluetoothConnectedDevices: List<String>,
    @SerializedName("foreground_app") val foregroundApp: String?,
    @SerializedName("ambient_noise_db") val ambientNoiseDb: Float?,
)

// Rule-based activity inference result
data class InferredActivity(
    val activity: String,   // "IN_VEHICLE", "RUNNING", "WALKING", "STILL", "UNKNOWN"
    val confidence: Int,     // 0–100
)

// Top-level snapshot sent every 1 second
data class ContextPayload(
    @SerializedName("device_id") val deviceId: String,
    val timestamp: String,
    val platform: String = "android",
    @SerializedName("collection_interval_ms") val collectionIntervalMs: Long = 1000L,
    @SerializedName("inferred_activity") val inferredActivity: InferredActivity,
    val location: LocationData?,
    val sensors: SensorData,
    @SerializedName("device_state") val deviceState: DeviceState,
) {
    // Converts this object to a pretty-printed JSON string
    fun toJson(): String = GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .create()
        .toJson(this)
}