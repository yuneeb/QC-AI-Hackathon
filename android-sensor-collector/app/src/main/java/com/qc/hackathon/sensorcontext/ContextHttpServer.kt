// Local HTTP server on port 8080 — serves the latest context snapshot as JSON.
package com.qc.hackathon.sensorcontext

import fi.iki.elonen.NanoHTTPD

class ContextHttpServer : NanoHTTPD("10.73.51.106", 8080) {

    // Holds the most recent JSON string; updated every 1s by SensorCollectorService
    @Volatile
    private var latestJson: String = "{\"status\": \"waiting for first snapshot\"}"

    // Called by SensorCollectorService after each collection cycle
    fun updatePayload(batch: ContextBatch) {
        latestJson = batch.toJson()
    }

    // Called by NanoHTTPD for every incoming HTTP request
    override fun serve(session: IHTTPSession): Response {
        return when (session.uri) {
            "/context" -> newFixedLengthResponse(
                Response.Status.OK, "application/json", latestJson
            )
            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND, "text/plain", "Use /context"
            )
        }
    }
}