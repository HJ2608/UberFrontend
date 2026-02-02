package com.example.uberfrontend.data.model

import java.math.BigDecimal

data class RideDetail(
    val rideId: Int,
    val riderName: String?,
    val riderMobile: String?,
    val pickupLat: Double,
    val pickupLng: Double,
    val dropLat: Double,
    val dropLng: Double,
    val estimatedFare: Double
)
