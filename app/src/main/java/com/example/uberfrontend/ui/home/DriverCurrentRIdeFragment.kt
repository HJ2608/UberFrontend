package com.example.uberfrontend.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.uberfrontend.R
import com.example.uberfrontend.databinding.FragmentDriverCurrentRideBinding
import com.example.uberfrontend.network.ApiClient
import com.example.uberfrontend.network.DriverApi
import com.example.uberfrontend.network.GoogleDirectionsClient
import com.example.uberfrontend.session.SessionManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.gson.Gson
import kotlinx.coroutines.launch
import com.google.maps.android.PolyUtil
import ua.naiksoftware.stomp.Stomp
import ua.naiksoftware.stomp.StompClient
import android.util.Log


class DriverCurrentRideFragment :
    Fragment(R.layout.fragment_driver_current_ride),
    OnMapReadyCallback {

    private lateinit var binding: FragmentDriverCurrentRideBinding
    private lateinit var googleMap: GoogleMap

    private val rideId = 1 // TODO: pass via arguments

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentDriverCurrentRideBinding.bind(view)

        val mapFragment = childFragmentManager
            .findFragmentById(R.id.driverMapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupRideActionButton()
        stompClient = Stomp.over(
            Stomp.ConnectionProvider.OKHTTP,
            "ws://YOUR_SERVER_URL/ws"
        )
        stompClient.connect()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isMyLocationButtonEnabled = true

        val pickupLatLng = LatLng(28.6139, 77.2090)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pickupLatLng, 15f))

        loadRouteToPickup()
    }

    private fun setupRideActionButton() {
        binding.btnRideAction.setOnClickListener {
            when (binding.btnRideAction.text.toString()) {
                "ARRIVED" -> {
                    showOtpDialog()
                }
                "START RIDE" -> {
                    binding.btnRideAction.text = "END RIDE"
                }
                "END RIDE" -> {
                    // TODO: end ride API
                }
            }
        }
    }

    private fun showOtpDialog() {
        val input = android.widget.EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_NUMBER

        AlertDialog.Builder(requireContext())
            .setTitle("Enter Ride OTP")
            .setView(input)
            .setPositiveButton("Verify") { _, _ ->
                verifyOtp(input.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun verifyOtp(otp: String) {
        lifecycleScope.launch {
            try {
                val api = ApiClient.create(DriverApi::class.java)

                val body = mapOf(
                    "rideId" to rideId,
                    "otp" to otp
                )

                val token = SessionManager.token
                if (token.isNullOrBlank()) {
                    Toast.makeText(requireContext(), "Session expired", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                api.verifyOtp(body, token)

                binding.btnRideAction.text = "START RIDE"
                startLiveLocation()

            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Invalid OTP",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }



    private fun loadRouteToPickup() {
        val key = getMapsApiKey()
        if (key.isBlank()) {
            Toast.makeText(requireContext(), "Maps API key missing", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val response = GoogleDirectionsClient.api.getRoute(
                origin = "28.6139,77.2090",
                destination = "28.5355,77.3910",
                apiKey = key
            )

            if (response.isSuccessful) {
                response.body()?.routes
                    ?.firstOrNull()
                    ?.overview_polyline
                    ?.points
                    ?.let { drawPolyline(it) }
            }
        }
    }



    private fun openGoogleMaps() {
        val uri = Uri.parse("google.navigation:q=28.5355,77.3910")
        startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        })
    }

    private fun drawPolyline(encodedPolyline: String) {
        val decodedPath = PolyUtil.decode(encodedPolyline)

        googleMap.addPolyline(
            PolylineOptions()
                .addAll(decodedPath)
                .width(10f)
                .color(0xFF1976D2.toInt())
        )
    }

    private fun getMapsApiKey(): String {
        return try {
            val appInfo = requireContext().packageManager
                .getApplicationInfo(
                    requireContext().packageName,
                    android.content.pm.PackageManager.GET_META_DATA
                )
            appInfo.metaData.getString("com.google.android.geo.API_KEY") ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    lateinit var stompClient: StompClient

    private fun startLiveLocation(){
        fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(requireContext())

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L
        ).setMinUpdateIntervalMillis(3000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                sendLocationViaSocket(loc.latitude, loc.longitude)
            }
        }

        fusedLocationClient.requestLocationUpdates(
            request,
            locationCallback,
            Looper.getMainLooper()
        )

    }


    private fun sendLocationViaSocket(lat: Double, lng: Double) {
        val payload = mapOf(
            "rideId" to rideId,
            "lat" to lat,
            "lng" to lng
        )

        stompClient.send(
            "/app/ride/location",
            Gson().toJson(payload)
        ).subscribe(
            { /* sent */ },
            { error -> Log.e("STOMP", error.message ?: "") }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::stompClient.isInitialized) {
            stompClient.disconnect()
        }

        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }


}
