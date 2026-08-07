// Rule-based activity detector using raw sensor values (no Google AR dependency).
package com.qc.hackathon.sensorcontext

class ActivityDetector {

    companion object {
        private const val VEHICLE_SPEED_KMH = 10f      // above this = likely in vehicle
        private const val RUNNING_SPEED_KMH = 6f       // above this + steps = running
        private const val STILL_ACCEL_THRESHOLD = 0.5f // linear accel below this = still
    }

    private var stepsInWindow = 0  // counts steps fired by TYPE_STEP_DETECTOR in this 5s window

    fun onStepDetected() {
        stepsInWindow++
    }

    // Main inference: call once per 5s cycle with latest values
    fun infer(
        speedKmh: Float?,
        linearAccelMagnitude: Float?,
        bluetoothConnected: Boolean
    ): InferredActivity {

        val speed = speedKmh ?: 0f
        val accel = linearAccelMagnitude ?: 0f
        val hasSteps = stepsInWindow > 0

        val result = when {
            speed > VEHICLE_SPEED_KMH && !hasSteps ->
                InferredActivity("IN_VEHICLE", if (bluetoothConnected) 95 else 85)

            hasSteps && speed > RUNNING_SPEED_KMH ->
                InferredActivity("RUNNING", 85)

            hasSteps ->
                InferredActivity("WALKING", 85)

            accel < STILL_ACCEL_THRESHOLD ->
                InferredActivity("STILL", 90)

            else ->
                InferredActivity("UNKNOWN", 40)
        }

        stepsInWindow = 0
        return result
    }

}