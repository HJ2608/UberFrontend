package com.example.uberfrontend.network.dto

data class SignupRequestDto(
    val firstName: String,
    val lastName: String,
    val mobileNum: String,
    val email: String,
    val password: String
)

data class SignupResponseDto(
    val userId: Int,
    val firstName: String,
    val lastName: String,
    val mobileNum: String,
    val email: String
)

data class LoginRequestDto(
    val mobileNum: String,
    val password: String
)

data class LoginResponseDto(
    val accessToken: String,
    val userId: Int,
    val firstName: String,
    val lastName: String,
    val mobileNum: String,
    val email: String
)
