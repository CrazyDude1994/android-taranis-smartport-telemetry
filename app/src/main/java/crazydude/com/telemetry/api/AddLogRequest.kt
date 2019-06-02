package crazydude.com.telemetry.api

import com.google.gson.annotations.SerializedName

data class AddLogRequest(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("altitude") val altitude: Float,
    @SerializedName("heading") val heading: Float,
    @SerializedName("speed") val speed: Float
)