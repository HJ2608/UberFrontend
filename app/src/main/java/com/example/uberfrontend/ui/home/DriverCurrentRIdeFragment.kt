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
import com.example.uberfrontend.data.network.ApiClient
import com.example.uberfrontend.data.network.DriverApi
import com.example.uberfrontend.data.network.GoogleDirectionsClient
import com.example.uberfrontend.data.session.SessionManager
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
import com.example.uberfrontend.data.model.StartRideRequest
import com.example.uberfrontend.data.network.RideApi
import com.example.uberfrontend.data.realtime.StompManager
import io.reactivex.disposables.CompositeDisposable
import org.json.JSONObject
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController


class DriverCurrentRideFragment :
    Fragment(R.layout.fragment_driver_current_ride),
    OnMapReadyCallback {

    private val TAG = "DriverCurrentRIdeFragment"
    private lateinit var binding: FragmentDriverCurrentRideBinding
    private lateinit var googleMap: GoogleMap

    private val rideId: Int by lazy {
        requireArguments().getInt("rideId")
    }
    private val pickupLat: Double by lazy {
        requireArguments().getDouble("pickupLat")
    }
    private val pickupLng: Double by lazy {
        requireArguments().getDouble("pickupLng")
    }

    private val dropLng: Double by lazy {
        requireArguments().getDouble("dropLng")
    }

    private val dropLat: Double by lazy {
        requireArguments().getDouble("dropLat")
    }

    private enum class Phase { TO_PICKUP, TO_DROP }
    private var phase: Phase = Phase.TO_PICKUP


    private val stompDisposables = CompositeDisposable()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentDriverCurrentRideBinding.bind(view)

        val mapFragment = childFragmentManager
            .findFragmentById(R.id.driverMapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupRideActionButton()
        loadRideDetailsIntoCard()
        startLiveLocation()
        val client = StompManager.clientOrNull()
        if (client == null) {
            Toast.makeText(requireContext(), "Socket not connected", Toast.LENGTH_SHORT).show()
            return
        }

        stompDisposables.add(
            client.topic("/user/queue/ride-cancelled")
                .subscribe({ msg ->
                    val json = JSONObject(msg.payload)
                    val cancelledRideId = json.optInt("rideId", -1)
                    Log.i(TAG, "rideId=$rideId")
                    if (cancelledRideId != rideId) return@subscribe

                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Ride cancelled by user", Toast.LENGTH_LONG).show()

                    }
                }, { err ->
                    Log.e("STOMP_FLOW", "cancel sub error", err)
                })
        )

    }

    private val locationPermLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            val granted = result[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    result[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true

            val dest = if (phase == Phase.TO_PICKUP) LatLng(pickupLat, pickupLng) else LatLng(dropLat, dropLng)

            if (granted) {
                try {
                    googleMap.isMyLocationEnabled = true
                } catch (_: SecurityException) {}
                loadRouteToPickup(dest)
            } else {
                Log.e(TAG, "Location permission denied -> using fallback origin")
                drawRouteWithOrigin(LatLng(28.6139, 77.2090), dest, getMapsApiKey())
            }
        }


    private fun ensureLocationPermissionThenDraw() {
        val fine = androidx.core.content.ContextCompat.checkSelfPermission(
            requireContext(),
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val coarse = androidx.core.content.ContextCompat.checkSelfPermission(
            requireContext(),
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val granted = fine || coarse
        val dest = if (phase == Phase.TO_PICKUP) LatLng(pickupLat, pickupLng) else LatLng(dropLat, dropLng)

        if (granted) {
            try {
                googleMap.isMyLocationEnabled = true
            } catch (_: SecurityException) {}
            loadRouteToPickup(dest)
        } else {
            locationPermLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }


    private fun loadRideDetailsIntoCard() {
        lifecycleScope.launch {
            try {
                val token = SessionManager.token
                if (token.isNullOrBlank()) {
                    Toast.makeText(requireContext(), "Session expired", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val api = ApiClient.create(DriverApi::class.java)
                val ride = api.getRideDetails(rideId, "Bearer $token")

                // Update UI
                binding.tvRiderName.text =
                    "Rider: " + (ride.riderName ?: ride.riderMobile ?: "Unknown")

                // If you don’t have pickup/drop address strings from backend,
                // just show lat/lng for demo (or reverse geocode later)
                binding.tvPickupLocation.text =
                    "Pickup: ${ride.pickupLat}, ${ride.pickupLng}"

                binding.tvDropLocation.text =
                    "Drop: ${ride.dropLat}, ${ride.dropLng}"

                // Keep OTP hidden by default; show only when ARRIVED pressed if you want
                binding.tvOtp.text = "OTP: ----"
                binding.tvOtp.visibility = View.GONE


            } catch (e: Exception) {
                Log.e(TAG, "Failed to load ride details", e)
                Toast.makeText(requireContext(), "Failed to load ride details", Toast.LENGTH_SHORT).show()
            }
        }
    }


    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isMyLocationButtonEnabled = true

        Log.i(TAG,"pickupLat:"+pickupLat+"pickupLng:"+pickupLng)
        Log.i(TAG,"dropLat:"+dropLat+"dropLng:"+dropLng)
        phase = Phase.TO_PICKUP

        val pickupLatLng = LatLng(pickupLat, pickupLng)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pickupLatLng, 15f))

        ensureLocationPermissionThenDraw()

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
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            val api = ApiClient.create(RideApi::class.java)
                            val resp = api.endRide(rideId)

                            if (resp.isSuccessful) {
                                Toast.makeText(requireContext(), "Ride ended", Toast.LENGTH_SHORT).show()
                                findNavController().navigate(
                                    R.id.action_driverCurrentRideFragment_to_driverPaymentFragment,
                                    bundleOf("rideId" to rideId)
                                )
                            } else {
                                Toast.makeText(
                                    requireContext(),
                                    resp.errorBody()?.string() ?: "Failed to end ride",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "endRide failed", e)
                            Toast.makeText(requireContext(), "Network error", Toast.LENGTH_SHORT).show()
                        }
                    }
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
                val token = SessionManager.token
                val userId = SessionManager.userId

                if (token.isNullOrBlank() || userId == null) {
                    Toast.makeText(requireContext(), "Session expired", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val api = ApiClient.create(DriverApi::class.java)

                val resp = api.startRide(
                    StartRideRequest(rideId = rideId, otp = otp),
                    "Bearer $token"
                )

                Log.i(TAG,"response from backend is: "+resp)

                if (!resp.isSuccessful) {
                    Toast.makeText(requireContext(), resp.errorBody()?.string() ?: "Invalid OTP", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // ✅ success
                binding.btnRideAction.text = "END RIDE"   // ✅ better UX
                phase = Phase.TO_DROP
                loadRouteToPickup(LatLng(dropLat, dropLng))
                //startLiveLocation()

            } catch (e: Exception) {
                Log.e(TAG, "startRide failed", e)
                Toast.makeText(requireContext(), "Invalid OTP", Toast.LENGTH_SHORT).show()
            }
        }
    }



    private fun loadRouteToPickup(dest: LatLng) {
        Log.i(TAG,"loadRouteToPickup got called!")
        val key = getMapsApiKey()
        if (key.isBlank()) {
            Toast.makeText(requireContext(), "Maps API key missing", Toast.LENGTH_SHORT).show()
            return
        }

        val fused = LocationServices.getFusedLocationProviderClient(requireContext())
        Log.i(TAG,"fused is set and fused.lastLocation is yet to be called")

        val fine = androidx.core.content.ContextCompat.checkSelfPermission(
            requireContext(),
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val coarse = androidx.core.content.ContextCompat.checkSelfPermission(
            requireContext(),
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!fine && !coarse) {
            Log.e(TAG, "No location permission -> using fallback origin")
            drawRouteWithOrigin(LatLng(28.6139, 77.2090), dest, key)
            return
        }
        if (fine || coarse) {
            googleMap.isMyLocationEnabled = true
        }

        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
            Log.i(TAG,"fused.lastLocation.addOnSuccessListener got called")
            val originLatLng = if (loc != null) {
                LatLng(loc.latitude, loc.longitude)
            } else {
                LatLng(28.6139, 77.2090)
            }
            Log.i(TAG,"originLatLng is set")
            googleMap.clear()

            Log.i(TAG,"originLatLng: "+originLatLng)

            val origin = "${originLatLng.latitude},${originLatLng.longitude}"
            val destination = "${dest.latitude},${dest.longitude}"
            Log.i(TAG,"origin: "+origin)
            Log.i(TAG,"destination: "+destination)
            lifecycleScope.launch {
                val response = GoogleDirectionsClient.api.getRoute(
                    origin = origin,
                    destination = destination,
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
    }

    private fun drawRouteWithOrigin(originLatLng: LatLng, dest: LatLng, key: String) {
        val origin = "${originLatLng.latitude},${originLatLng.longitude}"
        val destination = "${dest.latitude},${dest.longitude}"

        lifecycleScope.launch {
            val response = GoogleDirectionsClient.api.getRoute(
                origin = origin,
                destination = destination,
                apiKey = key
            )

            if (response.isSuccessful) {
                response.body()?.routes?.firstOrNull()
                    ?.overview_polyline?.points
                    ?.let { drawPolyline(it) }
            } else {
                Log.e(TAG, "Directions failed: ${response.code()} ${response.message()}")
            }
        }
    }



    private fun openGoogleMaps() {
        val uri = Uri.parse("google.navigation:q=${pickupLat},${pickupLng}")
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

        val builder = LatLngBounds.Builder()
        decodedPath.forEach { builder.include(it) }
        val bounds = builder.build()

        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))

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
        val client = StompManager.clientOrNull() ?: return

        val payload = JSONObject().apply {
            put("lat", lat)
            put("lng", lng)
            put("timestamp", System.currentTimeMillis())
        }

        stompDisposables.add(
            client.send("/app/ride/location", payload.toString())
                .subscribe(
                    { Log.d("STOMP_FLOW", "location sent") },
                    { err -> Log.e("STOMP_FLOW", "location send error", err) }
                )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stompDisposables.clear()

        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }


}
