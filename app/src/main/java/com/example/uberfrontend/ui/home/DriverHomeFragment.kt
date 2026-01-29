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
import ua.naiksoftware.stomp.dto.StompMessage
import java.net.URLEncoder

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


    //private lateinit var stompClient: StompClient
    private lateinit var stompClient: ua.naiksoftware.stomp.StompClient

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentDriverHomeBinding.bind(view)
        Log.i("Rachit","Healthy app")
        setupOnlineSwitch()
        setupButtons()
        connectStomp()
    }

    private var ignoreSwitchChange = false
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
                    if (isChecked) startLocationUpdates() else stopLocationUpdates()
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
                sendRideResponse("ACCEPTED", rideId)
                Log.i("DriverHomeFragment", "Accept clicked for ride $rideId")
                Toast.makeText(requireContext(), "Ride Accepted", Toast.LENGTH_SHORT).show()
                pendingRides.remove(rideId)
                val json = JSONObject(stompMessage.payload)
                val pickupLat = json.getDouble("pickupLat")
                val pickupLng = json.getDouble("pickupLng")

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
                        "pickupLng" to pickupLng)
                )
            }
        }


        binding.btnRejectRide.setOnClickListener {
            currentRideId?.let { rideId ->
                sendRideResponse("REJECTED", rideId)
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

    private fun sendRideResponse(status: String, rideId: Int) {
        val driverId = SessionManager.driverId
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
            put("status", status)
            put("driverId", driverId)
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

        val headers = listOf(
            StompHeader("Authorization", "Bearer $token")
        )

        val encoded = URLEncoder.encode("Bearer $token", "UTF-8")
        val wsUrl = "ws://192.168.1.5:9090/ws?token=$encoded"

        StompManager.connect("ws://192.168.1.5:9090", token)

        val stomp = StompManager.clientOrNull()

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
        Log.e("STOMP_FLOW", "Subscribing to /user/queue/ride-request")

        testDisp = stomp.topic("/topic/ride-request-test")
            .subscribe({ msg ->
                Log.e("WS_TEST", "BROADCAST RECEIVED: ${msg.payload}")
            }, { err ->
                Log.e("WS_TEST", "BROADCAST SUB ERROR", err)
            })
        disposables.add(testDisp!!)
        //Check WS
//        stompClient.topic("/topic/ride-request-test")
//            .subscribe({ msg ->
//                Log.e("WS_TEST", "BROADCAST RECEIVED: ${msg.payload}")
//            }, { err ->
//                Log.e("WS_TEST", "BROADCAST SUB ERROR", err)
//            })

        // Subscribe to ride requests
        rideReqDisp = stomp.topic("/user/queue/ride-request")
            .subscribe ({ stompMessage ->
                Log.e("STOMP_FLOW", "RAW MESSAGE RECEIVED: ${stompMessage.payload}")
                val json = JSONObject(stompMessage.payload)
                if (!json.has("rideId")) {
                    Log.e("STOMP_FLOW", "Missing rideId in payload: ${stompMessage.payload}")
                    return@subscribe
                }
                val rideId = json.getInt("rideId")
                Log.e("STOMP_FLOW", "Parsed rideId=$rideId")
                requireActivity().runOnUiThread {
                    Log.e("STOMP_FLOW", "Calling handleIncomingRide($rideId)")
                    handleIncomingRide(rideId)
                }
            }, { err ->
            Log.e("STOMP_FLOW", "ride-request SUB ERROR", err)
        })
        disposables.add(rideReqDisp!!)

        rideCancelDisp=stomp.topic("/user/queue/ride-cancelled")
            .subscribe ({ stompMessage ->
                requireActivity().runOnUiThread {
                    Toast.makeText(
                        requireContext(),
                        stompMessage.payload,
                        Toast.LENGTH_SHORT
                    ).show()

                    // clear pending if needed
                    pendingRides.clear()
                    showNextRide()
                }
            },{err ->
                Log.e("STOMP_FLOW", "ride-cancelled SUB ERROR", err)
            })
        disposables.add(rideCancelDisp!!)
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

    private fun stopLocationUpdates() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
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

    override fun onDestroyView() {
        super.onDestroyView()
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
    }
}
