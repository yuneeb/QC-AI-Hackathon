package com.qc.hackathon.sensorcontext

class BatteryDetector {

    /**
     * Determines the battery state score on a scale of 1 to 5.
     * 1: Bad state (e.g., low battery, discharging)
     * 5: Best state (e.g., full battery, charging)
     *
     * @param charging Whether the device is currently plugged in and charging.
     * @param batteryLevel The current battery level as a percentage (0-100).
     * @return An integer from 1 to 5 representing the battery health/state.
     */
    fun detect(batteryLevel: Int, chargeType: String): Int {
        // Base score based on battery level (1-5)
        var score = when {
            batteryLevel >= 80 -> 5
            batteryLevel >= 60 -> 4
            batteryLevel >= 40 -> 3
            batteryLevel >= 20 -> 2
            else -> 1
        }

        // Only increase score if charging via AC
        if (chargeType == "AC") {
            score += 1
        }

        // Cap score at 5
        return if (score > 5) 5 else score
    }
}
