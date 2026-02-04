package com.example.uberfrontend.ui.home

import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.uberfrontend.R
import com.example.uberfrontend.data.model.DriverStatusEnum
import com.example.uberfrontend.data.model.UpdateLocationRequestDto
import com.example.uberfrontend.databinding.FragmentDriverHomeBinding
import com.example.uberfrontend.data.network.ApiClient
import com.example.uberfrontend.data.network.DriverApi
import com.example.uberfrontend.data.realtime.StompManager
import com.example.uberfrontend.data.session.SessionManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject
import ua.naiksoftware.stomp.Stomp
import ua.naiksoftware.stomp.dto.LifecycleEvent
import ua.naiksoftware.stomp.dto.StompHeader
import kotlinx.coroutines.Dispatchers
import retrofit2.HttpException
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import ua.naiksoftware.stomp.StompClient
import ua.naiksoftware.stomp.dto.StompMessage
import java.net.URLEncoder

private const val LOCATION_REQ_CODE = 101

class DriverHomeFragment : Fragment(R.layout.fragment_driver_home) {

    private lateinit var binding: FragmentDriverHomeBinding
    private var webSocket: WebSocket? = null
    private var currentRideId: Int? = null
    private val pendingRides = mutableListOf<Int>()
    private var isOnActiveRide = false

    private val disposables = CompositeDisposable()

    private var lifecycleDisp: Disposable? = null
    private var testDisp: Disposable? = null
    private var rideReqDisp: Disposable? = null
    private var rideCancelDisp: Disposable? = null

    private var currentPickupLat: Double? = null

    private var currentPickupLng: Double? = null

    private var currentDropLng: Double? = null

    private var currentDropLat: Double? = null

    private var homeSubscribed = false

    //private lateinit var stompClient: StompClient
    private lateinit var stompClient: ua.naiksoftware.stomp.StompClient

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentDriverHomeBinding.bind(view)
        Log.i("Harshit","Healthy app")
        setupOnlineSwitch()
        setupButtons()
        connectStomp()
    }

    private var ignoreSwitchChange = false

    override fun onStart() {

        super.onStart()
        val nav = findNavController()
        val finished = nav.currentBackStackEntry
            ?.savedStateHandle
            ?.get<Boolean>("RIDE_FINISHED") == true

        if (finished) {
            nav.currentBackStackEntry?.savedStateHandle?.remove<Boolean>("RIDE_FINISHED")

            isOnActiveRide = false
            currentRideId = null
            pendingRides.clear()
            showNextRide()
            Log.e("STOMP_FLOW", "✅ Reset after ride completion")
        }

        connectStomp()
        if (::binding.isInitialized && binding.switchOnline.isChecked) {
            if (hasLocationPermission()) {
                startLocationUpdates()
            } else {
                requestLocationPermission()
            }
        }
    }

    private fun setupOnlineSwitch() {
        binding.switchOnline.setOnCheckedChangeListener { _, isChecked ->
            if (ignoreSwitchChange) return@setOnCheckedChangeListener
            val msg = if (isChecked) "You are Online" else "You are Offline"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

            val userId = SessionManager.userId ?: return@setOnCheckedChangeListener
            val token = SessionManager.token ?: return@setOnCheckedChangeListener

            lifecycleScope.launch {
                try {
                    val api = ApiClient.create(DriverApi::class.java)
                    val driverId = SessionManager.driverId ?:api.getDriverIdFromUserId(userId,"Bearer $token")
                    if (driverId == null) {
                        Toast.makeText(requireContext(), "No driver profile found", Toast.LENGTH_SHORT).show()
                        ignoreSwitchChange = true
                        binding.switchOnline.isChecked = false
                        ignoreSwitchChange = false
                        return@launch
                    }
                    if (SessionManager.driverId == null) {
                        SessionManager.saveDriverId(requireContext(), driverId)
                    }
                    val status = if (isChecked) DriverStatusEnum.ONLINE else DriverStatusEnum.OFFLINE
                    api.updateOnlineStatus(driverId, status, "Bearer $token")
                    val msg = if (isChecked) "You are Online" else "You are Offline"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    if (isChecked) {
                        if (!hasLocationPermission()) {
                            requestLocationPermission()
                        } else {
                            startLocationUpdates()
                        }
                    } else {
                        stopLocationUpdates()
                    }
                }catch (e: HttpException) {
                    if (e.code() == 404) {
                        Toast.makeText(
                            requireContext(),
                            "No driver profile found",
                            Toast.LENGTH_SHORT
                        ).show()
                        binding.switchOnline.isChecked = false
                    } else {
                        Toast.makeText(requireContext(), "Failed: ${e.code()}", Toast.LENGTH_SHORT)
                            .show()
                    }

                    ignoreSwitchChange = true
                    binding.switchOnline.isChecked = !isChecked
                    ignoreSwitchChange = false

                }catch (e: Exception) {
                    Toast.makeText(requireContext(), "Failed to update status", Toast.LENGTH_SHORT).show()

                    ignoreSwitchChange = true
                    binding.switchOnline.isChecked = !isChecked
                    ignoreSwitchChange = false
                }
            }
        }
    }



    private fun setupButtons() {
        binding.btnAcceptRide.setOnClickListener {
            currentRideId?.let { rideId ->
                isOnActiveRide = true
                sendRideResponse(true, rideId)
                Log.i("DriverHomeFragment", "Accept clicked for ride $rideId")
                Toast.makeText(requireContext(), "Ride Accepted", Toast.LENGTH_SHORT).show()
                pendingRides.remove(rideId)
                val pickupLat = currentPickupLat
                val pickupLng = currentPickupLng
                val dropLat = currentDropLat
                val dropLng = currentDropLng
                if (pickupLat == null || pickupLng == null) {
                    Toast.makeText(requireContext(), "Pickup location missing", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                showNextRide()

//                val action =
//                    DriverHomeFragmentDirections
//                        .actionDriverHomeFragmentToDriverCurrentRideFragment(rideId)
//
//                findNavController().navigate(action)

                findNavController().navigate(
                    R.id.action_driverHomeFragment_to_driverCurrentRideFragment,
                    bundleOf("rideId" to rideId,
                        "pickupLat" to pickupLat,
                        "pickupLng" to pickupLng,
                        "dropLat" to dropLat,
                        "dropLng" to dropLng
                    )
                )
            }
        }


        binding.btnRejectRide.setOnClickListener {
            currentRideId?.let { rideId ->
                sendRideResponse(false, rideId)
                Log.i("DriverHomeFragment", "Reject clicked for ride $rideId")
                Toast.makeText(requireContext(), "Ride Rejected", Toast.LENGTH_SHORT).show()
                pendingRides.remove(rideId)
                showNextRide()
            }
        }


        // TEMP testing button
//        binding.btnMockRide.setOnClickListener {
//            handleIncomingRide(999)
//        }
    }

    private fun sendRideResponse(accepted: Boolean, rideId: Int) {
        val driverId = SessionManager.driverId
        Log.e("STOMP_FLOW", "incoming ride: $rideId | isOnActiveRide=$isOnActiveRide")
        val stomp = StompManager.clientOrNull() ?: run {
            Log.e("STOMP_FLOW", "No stomp client available")
            return
        }
        if (driverId == null) {
            println("Cannot send ride response: driverId is null")
            return
        }
        val payload = JSONObject().apply {
            put("rideId", rideId)
            put("driverId", driverId)
            put("accepted", accepted)
        }

        disposables.add(
            stomp.send("/app/driver/ride/response", payload.toString())
                .subscribe({ Log.i("STOMP_FLOW", "Ride response sent") },
                    { err -> Log.e("STOMP_FLOW", "Ride response send failed", err) })
        )
    }

    private fun handleIncomingRide(rideId: Int) {
        // Add ride to queue if not already there

        Log.e(
            "STOMP_FLOW",
            "handleIncomingRide called | rideId=$rideId | active=$isOnActiveRide"
        )

        if (!pendingRides.contains(rideId)) {
            pendingRides.add(rideId)
        }

        if (!isOnActiveRide && currentRideId == null) {
            showNextRide()
        }
    }

    private fun showNextRide() {
        Log.e("STOMP_FLOW", "showNextRide | pending=${pendingRides.size}")
        if (pendingRides.isNotEmpty()) {
            currentRideId = pendingRides.first()
            Log.e("STOMP_FLOW", "Showing ride card for $currentRideId")
            binding.cardRide.visibility = View.VISIBLE
            binding.tvRideStatus.text = "New ride request (ID: $currentRideId)"
            Log.i("DriverHomeFragment", "Showing ride $currentRideId")
        } else {
            currentRideId = null
            binding.cardRide.visibility = View.GONE
        }
    }


    private fun connectStomp() {
        Log.i("STOMP_FLOW", "connectstomp() is called")
        SessionManager.init(requireContext())

        val token = SessionManager.token ?: return
        Log.i("STOMP_FLOW", "JWT_TOKEN = ${token != null}")

//        disposables.clear()
        val headers = listOf(
            StompHeader("Authorization", "Bearer $token")
        )

        val encoded = URLEncoder.encode("Bearer $token", "UTF-8")
        val wsUrl = "ws://10.84.42.92:9090/ws?token=$encoded"

        StompManager.setOnConnectedListener {
            requireActivity().runOnUiThread {
                val stomp = StompManager.clientOrNull() ?: run {
                    Log.e("STOMP_FLOW", "Stomp is null even after OPENED")
                    return@runOnUiThread
                }

                subscribeHomeQueues(stomp)

                Log.e("STOMP_FLOW", "OPENED -> now subscribing")

//                testDisp = stomp.topic("/topic/ride-request-test")
//                    .subscribe({ msg ->
//                        Log.e("WS_TEST", "BROADCAST RECEIVED: ${msg.payload}")
//                    }, { err ->
//                        Log.e("WS_TEST", "BROADCAST SUB ERROR", err)
//                    })
//                disposables.add(testDisp!!)
//
//                rideReqDisp = stomp.topic("/user/queue/ride-request")
//                    .subscribe({ stompMessage ->
//                        Log.e("STOMP_FLOW", "RAW MESSAGE RECEIVED: ${stompMessage.payload}")
//                        val json = JSONObject(stompMessage.payload)
//                        val rideId = json.getInt("rideId")
//                        val pickupLat = json.getDouble("pickupLat")
//                        val pickupLng = json.getDouble("pickupLng")
//                        val dropLat = json.getDouble("dropLat")
//                        val dropLng = json.getDouble("dropLng")
//                        Log.e("STOMP_FLOW", "Parsed rideId=$rideId")
//                        requireActivity().runOnUiThread {
//                            currentPickupLat = pickupLat
//                            currentPickupLng = pickupLng
//                            currentDropLat = dropLat
//                            currentDropLng = dropLng
//                            handleIncomingRide(rideId)
//                        }
//                    }, { err ->
//                        Log.e("STOMP_FLOW", "ride-request SUB ERROR", err)
//                    })
//                disposables.add(rideReqDisp!!)
//
//                rideCancelDisp = stomp.topic("/user/queue/ride-cancelled")
//                    .subscribe({ stompMessage ->
//                        Toast.makeText(requireContext(), stompMessage.payload, Toast.LENGTH_SHORT).show()
//                        requireActivity().runOnUiThread {
//                            pendingRides.clear()
//                            showNextRide()
//                        }
//                    }, { err ->
//                        Log.e("STOMP_FLOW", "ride-cancelled SUB ERROR", err)
//                    })
//                disposables.add(rideCancelDisp!!)
            }
        }

        StompManager.connect("ws://10.84.42.92:9090", token)

        val stomp = StompManager.clientOrNull() ?: run {
            Log.e("STOMP_FLOW", "Stomp not connected")
            return
        }

        // (Optional but recommended) lifecycle logging
//        stompClient.lifecycle().subscribe { event ->
//            Log.e("STOMP_FLOW", "Lifecycle event = ${event.type}")
//            when (event.type) {
//                LifecycleEvent.Type.OPENED ->
//                    Log.e("STOMP_FLOW", "STOMP Connected")
//
//                LifecycleEvent.Type.ERROR ->
//                    Log.e("STOMP_FLOW", "STOMP ERROR", event.exception)
//
//                LifecycleEvent.Type.CLOSED ->
//                    Log.e("STOMP_FLOW", "STOMP CLOSED")
//
//                else -> {
//                    Log.d("STOMP_FLOW", "Unhandled lifecycle event: ${event.type}")
//                }
//            }
//        }
//        Log.e("STOMP_FLOW", "Subscribing to /user/queue/ride-request")
//
//        testDisp = stomp.topic("/topic/ride-request-test")
//            .subscribe({ msg ->
//                Log.e("WS_TEST", "BROADCAST RECEIVED: ${msg.payload}")
//            }, { err ->
//                Log.e("WS_TEST", "BROADCAST SUB ERROR", err)
//            })
//        disposables.add(testDisp!!)
//        //Check WS
////        stompClient.topic("/topic/ride-request-test")
////            .subscribe({ msg ->
////                Log.e("WS_TEST", "BROADCAST RECEIVED: ${msg.payload}")
////            }, { err ->
////                Log.e("WS_TEST", "BROADCAST SUB ERROR", err)
////            })
//
//        // Subscribe to ride requests
//        rideReqDisp = stomp.topic("/user/queue/ride-request")
//            .subscribe ({ stompMessage ->
//                Log.e("STOMP_FLOW", "RAW MESSAGE RECEIVED: ${stompMessage.payload}")
//                val json = JSONObject(stompMessage.payload)
//                if (!json.has("rideId")) {
//                    Log.e("STOMP_FLOW", "Missing rideId in payload: ${stompMessage.payload}")
//                    return@subscribe
//                }
//                val rideId = json.getInt("rideId")
//                val pickupLat = json.getDouble("pickupLat")
//                val pickupLng = json.getDouble("pickupLng")
//                Log.e("STOMP_FLOW", "Parsed rideId=$rideId")
//                requireActivity().runOnUiThread {
//                    Log.e("STOMP_FLOW", "Calling handleIncomingRide($rideId)")
//                    currentPickupLat = pickupLat
//                    currentPickupLng = pickupLng
//                    handleIncomingRide(rideId)
//                }
//            }, { err ->
//            Log.e("STOMP_FLOW", "ride-request SUB ERROR", err)
//        })
//        disposables.add(rideReqDisp!!)
//
//        rideCancelDisp=stomp.topic("/user/queue/ride-cancelled")
//            .subscribe ({ stompMessage ->
//                requireActivity().runOnUiThread {
//                    Toast.makeText(
//                        requireContext(),
//                        stompMessage.payload,
//                        Toast.LENGTH_SHORT
//                    ).show()
//
//                    // clear pending if needed
//                    pendingRides.clear()
//                    showNextRide()
//                }
//            },{err ->
//                Log.e("STOMP_FLOW", "ride-cancelled SUB ERROR", err)
//            })
//        disposables.add(rideCancelDisp!!)
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
        Log.i("STOMP_FLOW","startLocationUpdates() is called")
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

                sendLocationViaSocket(location.latitude, location.longitude)

                sendLocationToBackend(location.latitude, location.longitude)
            }
        }

        fusedLocationClient.requestLocationUpdates(
            request,
            locationCallback!!,
            Looper.getMainLooper()
        )
    }

    private fun subscribeHomeQueues(stomp: StompClient) {
        if (homeSubscribed) return
        homeSubscribed = true
        testDisp?.dispose()
        rideReqDisp?.dispose()
        rideCancelDisp?.dispose()

        testDisp = stomp.topic("/topic/ride-request-test")
            .subscribe({ msg ->
                Log.e("WS_TEST", "BROADCAST RECEIVED: ${msg.payload}")
            }, { err ->
                Log.e("WS_TEST", "BROADCAST SUB ERROR", err)
            })
        disposables.add(testDisp!!)
        rideReqDisp = stomp.topic("/user/queue/ride-request")
            .subscribe({ stompMessage ->
                Log.e("STOMP_FLOW", "RAW MESSAGE RECEIVED: ${stompMessage.payload}")
                val json = JSONObject(stompMessage.payload)
                val rideId = json.getInt("rideId")
                val pickupLat = json.getDouble("pickupLat")
                val pickupLng = json.getDouble("pickupLng")
                val dropLat = json.getDouble("dropLat")
                val dropLng = json.getDouble("dropLng")
                Log.e("STOMP_FLOW", "Parsed rideId=$rideId")
                requireActivity().runOnUiThread {
                    currentPickupLat = pickupLat
                    currentPickupLng = pickupLng
                    currentDropLat = dropLat
                    currentDropLng = dropLng
                    handleIncomingRide(rideId)
                }
            }, { err ->
                Log.e("STOMP_FLOW", "ride-request SUB ERROR", err)
            })
        disposables.add(rideReqDisp!!)

        rideCancelDisp = stomp.topic("/user/queue/ride-cancelled")
            .subscribe({ stompMessage ->
                Toast.makeText(requireContext(), stompMessage.payload, Toast.LENGTH_SHORT).show()
                requireActivity().runOnUiThread {
                    pendingRides.clear()
                    showNextRide()
                }
            }, { err ->
                Log.e("STOMP_FLOW", "ride-cancelled SUB ERROR", err)
            })
        disposables.add(rideCancelDisp!!)

        Log.e("STOMP_FLOW", "✅ HOME subscribed")
    }

    private fun stopLocationUpdates() {
        if (::fusedLocationClient.isInitialized) {
            locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        }
        locationCallback = null
    }


    private fun sendLocationToBackend(lat: Double, lng: Double) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = SessionManager.token ?: return@launch
                val driverId = SessionManager.driverId ?: return@launch
                val api = ApiClient.create(DriverApi::class.java)

                api.updateDriverLocation(
                    driverId = driverId,
                    token = "Bearer $token",
                    body = UpdateLocationRequestDto(lat, lng)
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun sendLocationViaSocket(lat: Double, lng: Double) {
        val client = StompManager.clientOrNull() ?: return

        val payload = JSONObject().apply {
            put("lat", lat)
            put("lng", lng)
            put("timestamp", System.currentTimeMillis())
        }

        disposables.add(
            client.send("/app/ride/location", payload.toString())
                .subscribe(
                    { Log.d("STOMP_FLOW", "location sent") },
                    { err -> Log.e("STOMP_FLOW", "location send error", err) }
                )
        )
    }

//    private fun updateDriverMarker(position: LatLng) {
//        if (!::googleMap.isInitialized) return
//
//        if (driverMarker == null) {
//            driverMarker = googleMap.addMarker(
//                MarkerOptions()
//                    .position(position)
//                    .title("Driver")
//                    .icon(BitmapDescriptorFactory.defaultMarker(
//                        BitmapDescriptorFactory.HUE_BLUE
//                    ))
//            )
//            googleMap.animateCamera(
//                CameraUpdateFactory.newLatLngZoom(position, 15f)
//            )
//        } else {
//            driverMarker!!.position = position
//        }
//    }

    private fun hasLocationPermission(): Boolean {
        val fine = androidx.core.content.ContextCompat.checkSelfPermission(
            requireContext(),
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val coarse = androidx.core.content.ContextCompat.checkSelfPermission(
            requireContext(),
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        return fine || coarse
    }

    private fun requestLocationPermission() {
        requestPermissions(
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_REQ_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_REQ_CODE) {
            if (grantResults.isNotEmpty() &&
                grantResults.any { it == android.content.pm.PackageManager.PERMISSION_GRANTED }
            ) {
                Toast.makeText(requireContext(), "Location permission granted", Toast.LENGTH_SHORT).show()
                startLocationUpdates()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Location permission is required for driver navigation",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        stopLocationUpdates()
        homeSubscribed = false

        testDisp?.dispose(); testDisp = null
        rideReqDisp?.dispose(); rideReqDisp = null
        rideCancelDisp?.dispose(); rideCancelDisp = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopLocationUpdates()
    }


}
