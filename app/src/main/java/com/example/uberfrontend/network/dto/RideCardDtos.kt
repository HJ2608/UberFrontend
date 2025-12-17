package com.example.uberfrontend.network.dto

data class PaymentRequestDto(
    val method: String
)

data class AssignDriverRequestDto(
    val driverId: Int
)
