package com.example.uberfrontend.data.network

import com.example.uberfrontend.data.network.DirectionsResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface DirectionsApi {

    @GET("maps/api/directions/json")
    suspend fun getRoute(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("key") apiKey: String
    ): Response<DirectionsResponse>
}