// Writes the latest ContextPayload as JSON to a file on the device.
package com.qc.hackathon.sensorcontext

import android.content.Context
import java.io.File

class ContextFileWriter(private val context: Context) {

    // Output file location: app's external files dir (no extra permissions needed)
    private val outputFile: File = File(
        context.getExternalFilesDir(null),
        "context_snapshot.json"
    )

    // Overwrites the file with the latest payload JSON on every call
    fun write(payload: ContextPayload) {
        try {
            outputFile.writeText(payload.toJson())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Returns the full path so MainActivity can display it to the user
    fun getFilePath(): String = outputFile.absolutePath
}