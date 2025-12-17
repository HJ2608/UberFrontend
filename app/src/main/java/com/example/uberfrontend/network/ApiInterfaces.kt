package com.example.uberfrontend.network

import com.example.uberfrontend.network.dto.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AuthApi {

    @POST("api/auth/signup")
    suspend fun signup(@Body req: SignupRequestDto): Response<SignupResponseDto>

    @POST("api/auth/login")
    suspend fun login(@Body req: LoginRequestDto): LoginResponseDto
}

interface RideApi {


    @POST("api/rides")
    suspend fun createRide(
        @Body body: CreateRideRequestDto
    ): Response<CreateRideResponseDto>


    @POST("api/rides/{rideId}/assign-driver")
    suspend fun assignDriver(
        @Path("rideId") rideId: Int,
        @Body body: AssignDriverRequestDto
    ): Response<Unit>


    @GET("api/rides/card")
    suspend fun getRideCard(
        @Query("rideId") rideId: Int
    ): Response<RideCardResponse>


    @POST("api/rides/{rideId}/payment-success")
    suspend fun markPaymentSuccess(
        @Path("rideId") rideId: Int,
        @Body body: PaymentRequestDto
    ): Response<Unit>


    @POST("api/rides/{rideId}/end")
    suspend fun endRide(
        @Path("rideId") rideId: Int
    ): Response<Unit>
}
