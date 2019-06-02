package crazydude.com.telemetry.api

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

    @POST("api/session/create")
    fun createSession(@Body sessionCreateRequest: SessionCreateRequest): Call<SessionCreateResponse>

    @POST("api/data/add")
    fun sendData(@Body addLogRequest: AddLogRequest): Call<AddLogResponse>
}