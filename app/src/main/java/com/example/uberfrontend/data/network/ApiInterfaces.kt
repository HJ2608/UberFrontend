package com.example.uberfrontend.data.network

import com.example.uberfrontend.data.model.AssignDriverRequestDto
import com.example.uberfrontend.data.model.CreateRideRequestDto
import com.example.uberfrontend.data.model.CreateRideResponseDto
import com.example.uberfrontend.data.model.DriverEarningsResponseDto
import com.example.uberfrontend.data.model.DriverLedgerDto
import com.example.uberfrontend.data.model.DriverStatusEnum
import com.example.uberfrontend.data.model.LoginRequestDto
import com.example.uberfrontend.data.model.LoginResponseDto
import com.example.uberfrontend.data.model.PaymentRequestDto
import com.example.uberfrontend.data.model.RideCardResponse
import com.example.uberfrontend.data.model.SignupRequestDto
import com.example.uberfrontend.data.model.SignupResponseDto
import com.example.uberfrontend.data.model.UpdateLocationRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
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

interface DriverApi {

    @POST("drivers/{id}/online")
    suspend fun setOnlineStatus(
        @Path("id") driverId: Int,
        @Query("online") online: Boolean
    )

    @POST("drivers/{driverId}/status")
    suspend fun updateOnlineStatus(
        @Path("driverId") driverId: Int,
        @Query("status") status: DriverStatusEnum,
        @Header("Authorization") token: String
    )

    @POST("driver/ride/verify-otp")
    suspend fun verifyOtp(
        @Body body: Map<String, Any>,
        @Header("Authorization") token: String
    ): Response<Unit>

    @POST("api/drivers-location/{driverId}")
    suspend fun updateDriverLocation(
        @Path("driverId") driverId: Int,
        @Header("Authorization") token: String,
        @Body body: UpdateLocationRequestDto
    ): Response<Unit>

    @GET("/drivers/by-user/{userId}")
    suspend fun getDriverIdFromUserId(
        @Path("userId") userId: Int,
        @Header("Authorization") token: String
    ): Int?

//    @GET("driver/earnings/total")
//    suspend fun getTotalEarnings(
//        @Header("Authorization") token: String
//    ): Double

    @GET("driver/earnings/today")
    suspend fun getTodayEarnings(): Double

    @GET("driver/earnings/total")
    suspend fun getTotalEarnings(): Double

    @GET("driver/earnings/rides")
    suspend fun getEarningTrips(): List<DriverLedgerDto>

}