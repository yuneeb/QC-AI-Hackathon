package com.qc.hackathon.sensorcontext

class LocationDetector {
    /**
     * Quantizes a compass bearing in degrees to an 8-way cardinal direction.
     * Handles North, Northeast, East, Southeast, South, Southwest, West, and Northwest.
     */
    fun getCardinalDirection(bearingDeg: Float): String {
        // 1. Normalize the bearing to a clean 0.0 to 359.99... range
        val normalizedDeg = ((bearingDeg % 360) + 360) % 360

        // 2. Offset by 22.5 degrees and divide by 45 to get 8 centered sectors
        val index = (((normalizedDeg + 22.5) % 360) / 45).toInt()

        // 3. Map the 8 sectors to cardinal/intercardinal directions
        return when (index) {
            0 -> "North"
            1 -> "Northeast"
            2 -> "East"
            3 -> "Southeast"
            4 -> "South"
            5 -> "Southwest"
            6 -> "West"
            7 -> "Northwest"
            else -> "North"
        }
    }
}
