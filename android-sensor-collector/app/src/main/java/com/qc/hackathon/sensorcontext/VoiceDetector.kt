package com.qc.hackathon.sensorcontext

class VoiceDetector {

    /**
     * Determines the confidence percentage (0-100) for responding via voice.
     * Gracefully handles null/missing parameters.
     */
    fun detectConfidence(
        audio_output: String?,
        headphones_connected: Boolean?,
        dnd_active: Boolean?,
        screen_on: Boolean?,
        call_state: String?,
        activity: String?,
        battery_score: Int?
    ): Int {
        // If the user is on a call, voice reply confidence is 0.
        if (call_state != null && call_state != "IDLE") return 0

        var confidence = 40 // Start with a neutral baseline

        val isHandsFree = headphones_connected == true || audio_output == "BLUETOOTH" || audio_output == "WIRED_HEADSET"

        // Screen state: If the screen is off, user is likely not looking at it.
        if (screen_on != null) {
            if (!screen_on) {
                confidence += 20
            } else {
                // Screen is ON. Penalty is 20 normally, but reduced to 5 if hands-free.
                val penalty = if (isHandsFree) 5 else 20
                confidence -= penalty
            }
        }

        // Audio routing bonus
        if (isHandsFree) {
            confidence += 30
        }

        // Do Not Disturb: Likely indicates the user wants quiet.
        if (dnd_active == true) {
            confidence -= 40
        }

        // Activity-based adjustments
        activity?.uppercase()?.let { act ->
            when (act) {
                "IN_VEHICLE", "DRIVING" -> confidence += 40
                "WALK", "WALKING", "RUN", "RUNNING", "RIGOROUS" -> confidence += 25
                "STILL" -> confidence -= 10
                else -> {} 
            }
        }

        // Battery-based adjustments: Low battery reduces voice confidence (power saving)
        battery_score?.let { score ->
            when (score) {
                1 -> confidence -= 30
                2 -> confidence -= 15
                3 -> confidence -= 5
                else -> {} // No penalty for scores 4 and 5
            }
        }

        return confidence.coerceIn(0, 100)
    }
}
