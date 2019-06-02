package crazydude.com.telemetry.api

import com.google.gson.annotations.SerializedName

data class AddLogResponse(@SerializedName("status") val status: String)