package com.example.uberfrontend.data.model

data class DriverLedgerDto(
    val id: Long,
    val rideId: Int,
    val totalFare: Double,
    val driverCut: Double,
    val companyCut: Double,
    val createdAt: String?
)