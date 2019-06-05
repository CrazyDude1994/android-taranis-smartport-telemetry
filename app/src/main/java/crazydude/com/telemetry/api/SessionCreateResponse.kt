package crazydude.com.telemetry.api

import com.google.gson.annotations.SerializedName

data class SessionCreateResponse(
    @SerializedName("status") val status: String,
    @SerializedName("session_id") val sessionId: String
)