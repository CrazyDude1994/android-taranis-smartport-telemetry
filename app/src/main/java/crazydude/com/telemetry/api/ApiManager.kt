package crazydude.com.telemetry.api

import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.Retrofit



class ApiManager {

    companion object {
        private val retrofit = Retrofit.Builder()
            .baseUrl("http://149.154.71.100/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val apiService = retrofit.create(ApiService::class.java)
    }
}