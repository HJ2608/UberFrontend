package com.example.uberfrontend.data.network

data class DirectionsResponse(
    val routes: List<Route>?
)

data class Route(
    val overview_polyline: OverviewPolyline?,
    val legs: List<Leg>?
)

data class OverviewPolyline(
    val points: String?
)

data class Leg(
    val distance: Distance,
    val duration: Duration,
    val start_address: String?,
    val end_address: String?,
    val start_location: LocationPoint,
    val end_location: LocationPoint
)

data class Distance(
    val text: String,
    val value: Int   // meters
)

data class Duration(
    val text: String,
    val value: Int   // seconds
)

data class LocationPoint(
    val lat: Double,
    val lng: Double
)