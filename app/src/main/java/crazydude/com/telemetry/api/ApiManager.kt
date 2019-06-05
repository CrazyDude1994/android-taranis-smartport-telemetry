package crazydude.com.telemetry.api

import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.Retrofit



class ApiManager {

    companion object {

        const val API_URL = "https://uavradar.org/"

        private val retrofit = Retrofit.Builder()
            .baseUrl(API_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val apiService = retrofit.create(ApiService::class.java)
    }
}