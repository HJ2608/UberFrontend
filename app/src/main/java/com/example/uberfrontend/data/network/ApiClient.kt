package com.example.uberfrontend.data.network

import com.example.uberfrontend.data.session.SessionManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object ApiClient {

    private const val BASE_URL = "http://192.168.1.5:9090/"//"http://10.83.49.29:9090/"//

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