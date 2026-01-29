package com.example.uberfrontend.data.network

import com.example.uberfrontend.data.network.DirectionsApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object GoogleDirectionsClient {

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://maps.googleapis.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: DirectionsApi = retrofit.create(DirectionsApi::class.java)
}