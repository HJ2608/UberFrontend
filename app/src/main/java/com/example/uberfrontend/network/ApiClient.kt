package com.example.uberfrontend.network

import com.example.uberfrontend.session.SessionManager
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object ApiClient {

    private const val BASE_URL = "http://10.0.2.2:9090/"//"http://10.83.49.29:9090/"//

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()


        SessionManager.token?.let {
            builder.header("Authorization", "Bearer $it")
        }

        builder.header("Content-Type", "application/json")

        chain.proceed(builder.build())
    }

    private val okHttp = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttp)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    fun <T> create(service: Class<T>): T = retrofit.create(service)
}