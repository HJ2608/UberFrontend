package com.example.uberfrontend.ui.home

import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.uberfrontend.R
import com.example.uberfrontend.databinding.FragmentDriverHomeBinding
import com.example.uberfrontend.network.ApiClient
import com.example.uberfrontend.network.DriverApi
import com.example.uberfrontend.session.SessionManager
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject
import ua.naiksoftware.stomp.Stomp
import ua.naiksoftware.stomp.StompClient
import ua.naiksoftware.stomp.dto.StompMessage
import ua.naiksoftware.stomp.dto.LifecycleEvent
import ua.naiksoftware.stomp.dto.StompHeader



class DriverHomeFragment : Fragment(R.layout.fragment_driver_home) {

    private lateinit var binding: FragmentDriverHomeBinding
    private var webSocket: WebSocket? = null
    private var currentRideId: Int? = null
    private val pendingRides = mutableListOf<Int>()
    private var isOnActiveRide = false


    //private lateinit var stompClient: StompClient
    private lateinit var stompClient: ua.naiksoftware.stomp.StompClient

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentDriverHomeBinding.bind(view)

        setupOnlineSwitch()
        setupButtons()
        connectStomp()
    }

    private fun setupOnlineSwitch() {
        binding.switchOnline.setOnCheckedChangeListener { _, isChecked ->
            val msg = if (isChecked) "You are Online" else "You are Offline"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            // Optional: call backend API to mark driver online/offline

            val driverId = SessionManager.userId ?: return@setOnCheckedChangeListener
            val token = SessionManager.token ?: return@setOnCheckedChangeListener

            lifecycleScope.launch {
                try {
                    val api = ApiClient.create(DriverApi::class.java)
                    api.updateOnlineStatus(driverId, mapOf("online" to isChecked), "Bearer $token")
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Failed to update status", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupButtons() {
        binding.btnAcceptRide.setOnClickListener {
            currentRideId?.let { rideId ->
                isOnActiveRide = true
                sendRideResponse("ACCEPTED", rideId)
                Toast.makeText(requireContext(), "Ride Accepted", Toast.LENGTH_SHORT).show()
                pendingRides.remove(rideId)
                showNextRide()

                val action =
                    DriverHomeFragmentDirections
                        .actionDriverHomeFragmentToDriverCurrentRideFragment(rideId)

                findNavController().navigate(action)

                findNavController().navigate(
                    R.id.action_driverHomeFragment_to_driverCurrentRideFragment,
                    bundleOf("rideId" to rideId)
                )
            }
        }


        binding.btnRejectRide.setOnClickListener {
            currentRideId?.let { rideId ->
                sendRideResponse("REJECTED", rideId)
                Toast.makeText(requireContext(), "Ride Rejected", Toast.LENGTH_SHORT).show()
                pendingRides.remove(rideId)
                showNextRide()
            }
        }


        // TEMP testing button
        binding.btnMockRide.setOnClickListener {
            handleIncomingRide(999)
        }
    }

    private fun sendRideResponse(status: String, rideId: Int) {
        val payload = JSONObject().apply {
            put("rideId", rideId)
            put("status", status)
            put("driverId", SessionManager.userId)
        }

        stompClient.send("/app/ride/response", payload.toString())
            .subscribe({
                println("Ride response sent")
            }, { error ->
                println("Failed to send ride response: $error")
            })
    }

    private fun handleIncomingRide(rideId: Int) {
        // Add ride to queue if not already there

        if(isOnActiveRide) return
        if (!pendingRides.contains(rideId)) {
            pendingRides.add(rideId)
        }

        // If no ride is being displayed, show the first one
        if (currentRideId == null) {
            showNextRide()
        }
    }

    private fun showNextRide() {
        if (pendingRides.isNotEmpty()) {
            currentRideId = pendingRides.first()
            binding.cardRide.visibility = View.VISIBLE
            binding.tvRideStatus.text = "New ride request (ID: $currentRideId)"
        } else {
            currentRideId = null
            binding.cardRide.visibility = View.GONE
        }
    }


    private fun connectStomp() {
        SessionManager.init(requireContext())

        val token = SessionManager.token ?: return

        val headers = listOf(
            StompHeader("Authorization", "Bearer $token")
        )

        stompClient = Stomp.over(
            Stomp.ConnectionProvider.OKHTTP,
            "ws://10.0.2.2:9090/ws"
        )

        // Optional: add headers (JWT)
        stompClient.connect(headers)

        // Subscribe to ride requests
        stompClient.topic("/user/queue/ride-request")
            .subscribe { stompMessage ->
                val json = JSONObject(stompMessage.payload)
                val rideId = json.getInt("rideId")

                requireActivity().runOnUiThread {
                    handleIncomingRide(rideId)
                }
            }

        // (Optional but recommended) lifecycle logging
        stompClient.lifecycle().subscribe { event ->
            when (event.type) {
                LifecycleEvent.Type.OPENED ->
                    println("STOMP Connected")

                LifecycleEvent.Type.ERROR ->
                    println("STOMP Error: ${event.exception}")

                LifecycleEvent.Type.CLOSED ->
                    println("STOMP Closed")

                else -> {}
            }
        }
    }

//    private fun connectWebSocket() {
//        SessionManager.init(requireContext())
//
//        val driverId = SessionManager.userId
//        val token = SessionManager.token
//
//        val request = Request.Builder()
//            .url("ws://10.0.2.2:9090/ws") // emulator → localhost
//            .addHeader("Authorization", "Bearer $token")
//            .build()
//
//        val client = OkHttpClient()
//
//        webSocket = client.newWebSocket(request, object : WebSocketListener() {
//
//            override fun onOpen(ws: WebSocket, response: Response) {
//                subscribeToRideQueue(ws, driverId)
//            }
//
//            override fun onMessage(ws: WebSocket, text: String) {
//                requireActivity().runOnUiThread {
//                    handleRideMessage(text)
//                }
//            }
//
//            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
//                Toast.makeText(requireContext(), "WebSocket error", Toast.LENGTH_SHORT).show()
//            }
//        })
//    }
//
//    private fun subscribeToRideQueue(ws: WebSocket, driverId: Int?) {
//        val subscribeMsg = JSONObject()
//        subscribeMsg.put("command", "subscribe")
//        subscribeMsg.put("destination", "/user/queue/ride-request")
//        subscribeMsg.put("driverId", driverId)
//
//        ws.send(subscribeMsg.toString())
//    }

//    private fun handleRideMessage(message: String) {
//        val json = JSONObject(message)
//        val rideId = json.getInt("rideId")
//
//        showRideCard(rideId)
//    }

    private fun showRideCard(rideId: Int) {
        currentRideId = rideId
        binding.cardRide.visibility = View.VISIBLE
        binding.tvRideStatus.text = "New ride request (ID: $rideId)"
    }

    private fun hideRideCard() {
        currentRideId = null
        binding.cardRide.visibility = View.GONE
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    private fun startLocationUpdates() {
        fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(requireContext())

        val request = LocationRequest.create().apply {
            interval = 5000        // every 5 sec
            fastestInterval = 3000
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                sendLocationToBackend(location.latitude, location.longitude)
            }
        }

        fusedLocationClient.requestLocationUpdates(
            request,
            locationCallback!!,
            Looper.getMainLooper()
        )
    }

    private fun sendLocationToBackend(lat: Double, lng: Double) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = SessionManager.token ?: return@launch
                val api = ApiClient.create(DriverApi::class.java)

                api.updateDriverLocation(
                    token = "Bearer $token",
                    body = mapOf(
                        "rideId" to rideId,
                        "lat" to lat,
                        "lng" to lng
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateDriverMarker(position: LatLng) {
        if (!::googleMap.isInitialized) return

        if (driverMarker == null) {
            driverMarker = googleMap.addMarker(
                MarkerOptions()
                    .position(position)
                    .title("Driver")
                    .icon(BitmapDescriptorFactory.defaultMarker(
                        BitmapDescriptorFactory.HUE_BLUE
                    ))
            )
            googleMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(position, 15f)
            )
        } else {
            driverMarker!!.position = position
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
    }
}
