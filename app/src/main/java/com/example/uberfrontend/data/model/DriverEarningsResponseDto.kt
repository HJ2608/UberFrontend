package com.example.uberfrontend.data.model

data class DriverEarningsResponseDto(
    val summary: DriverEarningsSummaryDto,
    val trips: List<DriverTripDto>
)

data class DriverEarningsSummaryDto(
    val today: Double,
    val week: Double,
    val month: Double,
    val total: Double
)

data class DriverTripDto(
    val rideId: Int,
    val amount: Double,
    val distanceKm: Double? = null,
    val durationMin: Int? = null,
    val endedOn: String? = null
)
