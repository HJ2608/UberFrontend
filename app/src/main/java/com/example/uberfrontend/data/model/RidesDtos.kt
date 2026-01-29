package com.example.uberfrontend.data.model

data class CreateRideRequestDto(
    val pickupLat: Double,
    val pickupLng: Double,
    val dropLat: Double,
    val dropLng: Double
)

data class CreateRideResponseDto(
    val rideId: Int,
    val estimatedFare: Double,
    val otpCode: String
)

data class RideCardResponse(
    val rideId: Int,
    val status: String,
    val estimatedFare: Double,
    val finalFare: Double?,
    val paymentStatus: String,
    val paymentMethod: String,
    val pickupLat: Double,
    val pickupLng: Double,
    val dropLat: Double,
    val dropLng: Double,
    val otpCode: String,
    val driver: DriverSummary?,
    val cab: CabSummary?,
    val eta: EtaResponseDto?
)

data class DriverSummary(
    val driverId: Int,
    val name: String,
    val avgRating: Double,
)

enum class CabType {
    MINI,
    SEDAN,
    SUV
}

data class CabSummary(
    val cabId: Int,
    val model: String?,
    val color: String?,
    val registrationNo: String?,
    val cabType: CabType?
)

data class EtaResponseDto(
    val distanceKm: Double,
    val etaMinutes: Int
)