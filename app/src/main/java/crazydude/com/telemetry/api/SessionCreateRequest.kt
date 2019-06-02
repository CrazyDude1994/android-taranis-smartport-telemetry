package crazydude.com.telemetry.api

import com.google.gson.annotations.SerializedName

data class SessionCreateRequest(
    @SerializedName("callsign") val callsign: String,
    @SerializedName("model") val model: String
)