package com.example.uberfrontend.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.uberfrontend.databinding.FragmentHomeBinding
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.example.uberfrontend.R
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.uberfrontend.data.network.GoogleDirectionsClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.launch
import android.app.Activity
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.Color
import android.util.Log
import com.example.uberfrontend.data.network.ApiClient
import com.example.uberfrontend.data.network.RideApi
import com.example.uberfrontend.data.model.CreateRideRequestDto
import com.example.uberfrontend.data.model.RideCardResponse
import com.example.uberfrontend.data.realtime.StompManager
import com.example.uberfrontend.data.session.SessionManager
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Marker
import com.google.gson.Gson
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import ua.naiksoftware.stomp.Stomp
import ua.naiksoftware.stomp.StompClient


class HomeFragment : Fragment(), OnMapReadyCallback {

    private enum class LocationType {
        PICKUP,
        DROP
    }

    private enum class UserPhase { WAITING_ASSIGN, TO_PICKUP, TO_DROP }
    private var phase: UserPhase = UserPhase.WAITING_ASSIGN

    private val wsDisposables = io.reactivex.disposables.CompositeDisposable()
    private var rideStatusDisp: Disposable? = null
    private var rideLocationDisp: Disposable? = null

    private var assignedDriverId: Int? = null
    private var lastDriverLatLng: LatLng? = null

    private var routeJob: kotlinx.coroutines.Job? = null
    private enum class RideType {
        MINI,
        SEDAN,
        SUV
    }

    private var selectedRideType: RideType? = null
    private var currentMapSelection: LocationType? = null
    private var autocompleteLocationType: LocationType? = null
    private lateinit var googleMap: GoogleMap
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    lateinit var stompClient: StompClient
    private var rideSubscription: Disposable? = null

    private val locationPermission = arrayOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    )


    private var pickupLatLng: LatLng? = null
    private var dropLatLng: LatLng? = null
    private var mapsApiKey: String? = null
    

    private var pollingJob: kotlinx.coroutines.Job? = null


    private val autocompleteLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val data = result.data!!
                val place = Autocomplete.getPlaceFromIntent(data)
                val latLng = place.latLng

                if (latLng != null) {
                    when (autocompleteLocationType) {
                        LocationType.PICKUP -> {
                            pickupLatLng = latLng
                            binding.tvPickup.text = place.address ?: "Pickup: ${latLng.latitude}, ${latLng.longitude}"
                        }
                        LocationType.DROP -> {
                            dropLatLng = latLng
                            binding.tvDrop.text = place.address ?: "Drop: ${latLng.latitude}, ${latLng.longitude}"
                        }
                        null -> {}
                    }

                    updateMarkers()

//                    val pickup = pickupLatLng
//                    val drop = dropLatLng
//                    if (pickup != null && drop != null) {
//                        drawRoute(pickup, drop)
//                    }
                }
            } else if (result.resultCode == AutocompleteActivity.RESULT_ERROR && result.data != null) {
                val status = Autocomplete.getStatusFromIntent(result.data!!)
                Toast.makeText(requireContext(), status.statusMessage ?: "Autocomplete error", Toast.LENGTH_SHORT).show()
            }
        }
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if ((fineGranted || coarseGranted) && ::googleMap.isInitialized) {
                enableUserLocation()
            } else {
                Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val key = getMapsApiKey()
        if (key.isNotBlank() && !Places.isInitialized()) {
            Places.initialize(requireContext(), key)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cardRidePanel.visibility = View.VISIBLE
        binding.btnRequestRide.visibility = View.VISIBLE

        binding.tvPickup.setOnClickListener {
            showLocationChoiceDialog(LocationType.PICKUP)
        }

        binding.tvDrop.setOnClickListener {
            showLocationChoiceDialog(LocationType.DROP)
        }

        binding.btnRequestRide.setOnClickListener {
            val pickup = pickupLatLng
            val drop = dropLatLng

            if(pickup == null){
                Toast.makeText(requireContext(),"Pickup location not ready yet", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if(drop ==null){
                Toast.makeText(requireContext(),"Tap on map to choose drop location", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            drawRoute(pickup,drop)
            showRideOptionsCard()
        }
        val mapFragment =
            childFragmentManager.findFragmentById(R.id.map_container) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.optionMini.setOnClickListener {
            selectRideType(RideType.MINI)
        }
        binding.optionSedan.setOnClickListener {
            selectRideType(RideType.SEDAN)
        }
        binding.optionSuv.setOnClickListener {
            selectRideType(RideType.SUV)
        }

        binding.btnConfirmRide.setOnClickListener {
            val ride = selectedRideType
            if (ride == null) {
                Toast.makeText(requireContext(), "Please select a ride option", Toast.LENGTH_SHORT).show()
            } else {
                createRideAndPoll()
            }
        }


        val cancelBtn = binding.root.findViewById<Button>(R.id.btnCancelRide)
        cancelBtn?.setOnClickListener {
            stopPolling()

            val rideCard = binding.root.findViewById<View>(R.id.layout_ride_card)
            rideCard?.visibility = View.GONE
            binding.cardRidePanel.visibility = View.VISIBLE
            Toast.makeText(requireContext(), "Ride cancelled", Toast.LENGTH_SHORT).show()
        }

        SessionManager.init(requireContext())
        val token = SessionManager.token ?: return

        StompManager.setOnConnectedListener {
            Log.e("STOMP_FLOW", "✅ USER socket connected")
        }

        StompManager.connect("ws://192.168.1.8:9090", token)
    }

    private var currentRideId: Int? = null
    private fun createRideAndPoll() {
        val pickup = pickupLatLng
        val drop = dropLatLng
        val userId = SessionManager.userId

        if (pickup == null || drop == null || userId == null) {
            Toast.makeText(requireContext(), "Missing location or user session", Toast.LENGTH_SHORT).show()
            return
        }


        binding.btnConfirmRide.isEnabled = false
        binding.btnConfirmRide.text = "Requesting..."

        val req = CreateRideRequestDto(
            pickupLat = pickup.latitude,
            pickupLng = pickup.longitude,
            dropLat = drop.latitude,
            dropLng = drop.longitude
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val api = ApiClient.create(RideApi::class.java)
                val response = api.createRide(req)


                if (response.isSuccessful) {
                    val rideId = response.body()?.rideId
                    withContext(Dispatchers.Main) {
                        if (rideId == null) {
                            Toast.makeText(requireContext(), "Ride created but rideId missing", Toast.LENGTH_SHORT).show()
                            binding.btnConfirmRide.isEnabled = true
                            binding.btnConfirmRide.text = "Confirm ride"
                            return@withContext
                        }

                        currentRideId = rideId
                        subscribeRideStatus(rideId)
                        subscribeRideLocation(rideId)

                        Toast.makeText(requireContext(), "Ride requested! Finding driver...", Toast.LENGTH_SHORT).show()
                        binding.btnConfirmRide.text = "Finding Driver..."


                        Toast.makeText(requireContext(), "RideId=$rideId", Toast.LENGTH_LONG).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Failed to create ride: ${response.code()}", Toast.LENGTH_SHORT).show()
                        binding.btnConfirmRide.isEnabled = true
                        binding.btnConfirmRide.text = "Confirm ride"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    binding.btnConfirmRide.isEnabled = true
                    binding.btnConfirmRide.text = "Confirm ride"
                }
            }
        }
    }

    private fun subscribeRideStatus(rideId: Int) {
        val stomp = StompManager.clientOrNull() ?: return

        rideStatusDisp?.dispose()
        rideStatusDisp = stomp.topic("/topic/ride/$rideId")
            .subscribe({ msg ->
                Log.e("USER_WS", "ride status payload=${msg.payload}")

                // Expecting: { "rideId": 123, "status": "ASSIGNED", "driverId": 45 }
                val json = org.json.JSONObject(msg.payload)
                val status = json.optString("status")

                when(status){
                    "ASSIGNED" -> {
                        assignedDriverId = json.optInt("driverId")
                        phase = UserPhase.TO_PICKUP


                        fetchAndShowRideCard(rideId)

                        Log.e("USER_WS", "✅ ASSIGNED -> start showing driver->pickup route")
                        redrawRouteIfPossible()
                    }

                    "ONGOING" -> {
                        phase = UserPhase.TO_DROP
                        Log.e("USER_WS", "✅ ONGOING -> switch to driver->drop route")
                        redrawRouteIfPossible()
                    }
                    "COMPLETED" -> {
                        fetchFinalFareAndShow(rideId)
                    }

                    "PAYMENT_COMPLETED" -> {
                        resetUserToInitialState()
                    }
                }
//
//                if (status == "ASSIGNED") {
//                    assignedDriverId = json.optInt("driverId")
//                    phase = UserPhase.TO_PICKUP
//
//                    // optionally fetch ride card via REST once assigned:
//                    fetchAndShowRideCard(rideId)
//
//                    Log.e("USER_WS", "✅ ASSIGNED -> start showing driver->pickup route")
//                    redrawRouteIfPossible()
//                }
//
//                if (status == "ONGOING") {
//                    phase = UserPhase.TO_DROP
//                    Log.e("USER_WS", "✅ ONGOING -> switch to driver->drop route")
//                    redrawRouteIfPossible()
//                }

            }, { err ->
                Log.e("USER_WS", "ride status sub error", err)
            })

        wsDisposables.add(rideStatusDisp!!)
    }



    private fun subscribeRideLocation(rideId: Int) {
        val stomp = StompManager.clientOrNull() ?: return

        rideLocationDisp?.dispose()
        rideLocationDisp = stomp.topic("/topic/ride/$rideId/location")
            .subscribe({ msg ->
                // Backend sends RideLocationBroadcast(driverId, lat, lng, timestamp)
                val json = org.json.JSONObject(msg.payload)
                val lat = json.optDouble("lat", Double.NaN)
                val lng = json.optDouble("lng", Double.NaN)
                if (lat.isNaN() || lng.isNaN()) return@subscribe

                val pos = LatLng(lat, lng)
                lastDriverLatLng = pos

                requireActivity().runOnUiThread {
                    updateDriverMarker(pos)
                }

                // throttle route redraw (Directions API is expensive)
                redrawRouteIfPossibleThrottled()

            }, { err ->
                Log.e("USER_WS", "ride location sub error", err)
            })

        wsDisposables.add(rideLocationDisp!!)
    }

    private fun fetchAndShowRideCard(rideId: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val api = ApiClient.create(RideApi::class.java)
                val resp = api.getRideCard(rideId)
                if (resp.isSuccessful && resp.body() != null) {
                    withContext(Dispatchers.Main) { showRideCard(resp.body()!!) }
                }
            } catch (_: Exception) {}
        }
    }

    private var lastRouteRedrawAt = 0L

    private fun redrawRouteIfPossibleThrottled() {
        val now = System.currentTimeMillis()
        if (now - lastRouteRedrawAt < 8000) return // every 8s
        lastRouteRedrawAt = now
        redrawRouteIfPossible()
    }
    private fun redrawRouteIfPossible() {
        val driverPos = lastDriverLatLng ?: return
        val pickup = pickupLatLng ?: return
        val drop = dropLatLng ?: return

        val dest = when (phase) {
            UserPhase.TO_PICKUP -> pickup
            UserPhase.TO_DROP -> drop
            else -> return
        }

        // cancel previous directions call if still running
        routeJob?.cancel()
        routeJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val key = getMapsApiKey()
                if (key.isBlank()) return@launch

                val origin = "${driverPos.latitude},${driverPos.longitude}"
                val destination = "${dest.latitude},${dest.longitude}"

                val response = GoogleDirectionsClient.api.getRoute(
                    origin = origin,
                    destination = destination,
                    apiKey = key
                )

                if (!response.isSuccessful) return@launch

                val points = response.body()
                    ?.routes?.firstOrNull()
                    ?.overview_polyline?.points
                    ?: return@launch

                val decoded = decodePolyline(points)

                withContext(Dispatchers.Main) {
                    if (!::googleMap.isInitialized) return@withContext

                    // IMPORTANT: don't clear map each time (or you'll lose markers)
                    // Instead: keep references to polylines and replace them
                    drawOrReplaceUserPolyline(decoded)
                }
            } catch (_: Exception) {}
        }
    }

    private var liveRoutePolyline: com.google.android.gms.maps.model.Polyline? = null
    private fun drawOrReplaceUserPolyline(path: List<LatLng>) {
        liveRoutePolyline?.remove()
        liveRoutePolyline = googleMap.addPolyline(
            PolylineOptions()
                .addAll(path)
                .width(12f)
        )
    }




    private fun startPollingForDriver(rideId: Int) {

        stopPolling()
        
        pollingJob = lifecycleScope.launch(Dispatchers.IO) {
            val api = ApiClient.create(RideApi::class.java)
            while (isActive) {
                try {
                    val response = api.getRideCard(rideId)
                    val card = response.body()
                    println("POLL rideId=$rideId code=${response.code()} status=${card?.status} driver=${card?.driver}")

                    if (response.isSuccessful && response.body() != null) {
                        val card = response.body()!!

                        if (card.driver != null && card.status == "ASSIGNED") {
                            withContext(Dispatchers.Main) {
                                showRideCard(card)
                                stopPolling()
                            }
                            break
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(3000)
            }
        }
    }

    private fun showFinalFareCard(finalFare: String?) {
        binding.root.findViewById<View>(R.id.layout_ride_card)?.visibility = View.GONE
        binding.cardRidePanel.visibility = View.GONE
        val paymentCard = binding.root.findViewById<View>(R.id.paymentCard)
        if (paymentCard == null) {
            Log.e("USER_UI", "layout_payment_card not found. Did you <include> it in fragment_home.xml ?")
            return
        }
        paymentCard.visibility = View.VISIBLE

        binding.root.findViewById<TextView>(R.id.tvFinalFare)?.text =
            "Final Fare: ₹${finalFare ?: "--"}"

        binding.root.findViewById<TextView>(R.id.tvPaymentStatus)?.text =
            "Please pay the driver"

        paymentCard.findViewById<Button>(R.id.btnOk)?.setOnClickListener {
            resetUserToInitialState()
        }
    }


    private fun resetUserToInitialState() {

        stopPolling()


        rideSubscription?.dispose()
        rideSubscription = null


        driverMarker?.remove()
        driverMarker = null
        liveRoutePolyline?.remove()
        liveRoutePolyline = null

        currentRideId = null
        assignedDriverId = null
        lastDriverLatLng = null
        phase = UserPhase.WAITING_ASSIGN

        binding.root.findViewById<View>(R.id.paymentCard)?.visibility = View.GONE

        binding.estimateContainer.visibility = View.GONE
        binding.cardRidePanel.visibility = View.VISIBLE
        binding.btnConfirmRide.isEnabled = true
        binding.btnConfirmRide.text = "Confirm ride"
        binding.btnRequestRide.visibility = View.VISIBLE

        Toast.makeText(requireContext(), "Payment completed. You can book a new ride.", Toast.LENGTH_SHORT).show()
    }


    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        googleMap.uiSettings.isMapToolbarEnabled = false
        googleMap.uiSettings.isZoomControlsEnabled = false

        requestUserLocation()

        val defaultLocation = LatLng(28.6139, 77.2090)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 14f))

        googleMap.setOnMapClickListener { latLng ->
            when (currentMapSelection) {
                LocationType.PICKUP -> {
                    pickupLatLng = latLng
                    binding.tvPickup.text = "Pickup: ${latLng.latitude}, ${latLng.longitude}"
                }
                LocationType.DROP -> {
                    dropLatLng = latLng
                    binding.tvDrop.text = "Drop: ${latLng.latitude}, ${latLng.longitude}"
                }
                null -> {

                    dropLatLng = latLng
                    binding.tvDrop.text = "Drop: ${latLng.latitude}, ${latLng.longitude}"
                }
            }


            currentMapSelection = null

            updateMarkers()


            val pickup = pickupLatLng
            val drop = dropLatLng
            if (pickup != null && drop != null) {
                drawRoute(pickup, drop)
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()

        rideSubscription?.dispose()
        if (::stompClient.isInitialized) {
            stompClient.disconnect()
        }

        wsDisposables.clear()
        rideStatusDisp?.dispose(); rideStatusDisp = null
        rideLocationDisp?.dispose(); rideLocationDisp = null
        routeJob?.cancel(); routeJob = null
        stopPolling()
        _binding = null
    }


    private fun requestUserLocation(){
        val context = requireContext()

        val fine = android.Manifest.permission.ACCESS_FINE_LOCATION
        val coarse = android.Manifest.permission.ACCESS_COARSE_LOCATION

        if(ActivityCompat.checkSelfPermission(context,fine)== PackageManager.PERMISSION_GRANTED||
            ActivityCompat.checkSelfPermission(context,coarse) == PackageManager.PERMISSION_GRANTED
        ){
            enableUserLocation()
            return
        }
        requestPermissionLauncher.launch(locationPermission)
    }

    private fun enableUserLocation(){
        try{
            googleMap.isMyLocationEnabled = true

            val locationProvider = LocationServices.getFusedLocationProviderClient(requireContext())
            locationProvider.lastLocation.addOnSuccessListener{ loc ->
                if(loc != null){
                    val pos = LatLng(loc.latitude,loc.longitude)
                    pickupLatLng = pos
                    binding.tvPickup.text = "Pickup: ${pos.latitude}, ${pos.longitude}"
                    updateMarkers()
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 16f))

                }
            }
        }catch (e: SecurityException){
            e.printStackTrace()
        }
    }
    private fun drawRoute(pickup: LatLng, drop: LatLng) {
        val key = getMapsApiKey()
        if (key.isBlank()) {
            Toast.makeText(
                requireContext(),
                "Set mapApiKey in HomeFragment first",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val origin = "${pickup.latitude},${pickup.longitude}"
        val destination = "${drop.latitude},${drop.longitude}"

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = GoogleDirectionsClient.api.getRoute(
                    origin = origin,
                    destination = destination,
                    apiKey = key
                )

                if (response.isSuccessful) {
                    val body = response.body()
                    val route = body?.routes?.firstOrNull()
                    val points = route?.overview_polyline?.points

                    val leg = route?.legs?.firstOrNull()
                    val distanceMeters = leg?.distance?.value
                    val durationSeconds = leg?.duration?.value

                    if (!points.isNullOrEmpty()) {

                        val decodedPath = decodePolyline(points)

                        withContext(Dispatchers.Main) {
                            if (!::googleMap.isInitialized) return@withContext

                            googleMap.addPolyline(
                                PolylineOptions()
                                    .addAll(decodedPath)
                                    .width(12f)
                            )

                            googleMap.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(pickup, 13f)
                            )

                            showFareEstimateCard(distanceMeters, durationSeconds)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "No route found", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            requireContext(),
                            "Route error: ${response.code()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Route fetch failed: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun decodePolyline(encoded: String): List<LatLng>{
        val poly = ArrayList<LatLng>()
        var index = 0
        var len = encoded.length
        var lat = 0
        var lng = 0

        while(index <len){
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code -63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >=0x20)
            val dlat = if((result and 1) != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code -63
                result = result or ((b and 0x1f) shl shift)
                shift +=5
            } while (b >= 0x20)
            val dlng = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val latLng = LatLng(
                lat/ 1E5,
                lng / 1E5
            )
            poly.add(latLng)
        }

        return poly
    }
    private fun getMapsApiKey(): String {
        if (mapsApiKey != null) return mapsApiKey!!
        
        return try {
            val appInfo = requireContext().packageManager
                .getApplicationInfo(requireContext().packageName, PackageManager.GET_META_DATA)
            val key = appInfo.metaData.getString("com.google.android.geo.API_KEY") ?: ""
            mapsApiKey = key
            key
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun updateMarkers() {
        if (!::googleMap.isInitialized) return

        googleMap.clear()

        pickupLatLng?.let {
            googleMap.addMarker(
                MarkerOptions()
                    .position(it)
                    .title("Pickup")
            )
        }

        dropLatLng?.let {
            googleMap.addMarker(
                MarkerOptions()
                    .position(it)
                    .title("Drop")
            )
        }
    }

    private fun selectRideType(type: RideType) {
        selectedRideType = type
        updateRideOptionUI()
        binding.btnConfirmRide.isEnabled = true
    }

    private fun updateRideOptionUI() {

        setRowSelected(binding.optionMini, false)
        setRowSelected(binding.optionSedan, false)
        setRowSelected(binding.optionSuv, false)


        when (selectedRideType) {
            RideType.MINI -> setRowSelected(binding.optionMini, true)
            RideType.SEDAN -> setRowSelected(binding.optionSedan, true)
            RideType.SUV -> setRowSelected(binding.optionSuv, true)
            null -> { /* no selection */ }
        }
    }

    private fun fetchFinalFareAndShow(rideId: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val api = ApiClient.create(RideApi::class.java)
                val resp = api.getRideCard(rideId)
                val fare = resp.body()?.finalFare?.toString()

                withContext(Dispatchers.Main) {
                    showFinalFareCard(fare)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showFinalFareCard(null)
                    Toast.makeText(requireContext(), "Failed to load final fare", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }



    private fun setRowSelected(row: View, selected: Boolean) {
        if (selected) {
            row.setBackgroundColor(Color.parseColor("#E0F7FA")) // light teal
        } else {
            row.setBackgroundColor(Color.TRANSPARENT)
        }
    }


    private fun showFareEstimateCard(distanceMeters: Int?, durationSeconds: Int?) {

        binding.cardRidePanel.visibility = View.VISIBLE
        binding.estimateContainer.visibility = View.VISIBLE

        selectedRideType = null
        updateRideOptionUI()
        binding.btnConfirmRide.isEnabled = false

        if (distanceMeters == null || durationSeconds == null) {
            binding.tvEta.text = "ETA: --"
            binding.tvDistance.text = "Distance: --"
            binding.tvRideMiniFare.text = "₹0"
            binding.tvRideSedanFare.text = "₹0"
            binding.tvRideSuvFare.text = "₹0"
            return
        }

        val distanceKm = distanceMeters / 1000.0
        val durationMin = durationSeconds / 60


        val miniFare = 40.0 + 10.0 * distanceKm
        val sedanFare = 60.0 + 13.0 * distanceKm
        val suvFare = 80.0 + 16.0 * distanceKm

        // Update text
        binding.tvEta.text = "ETA: ${durationMin} min"
        binding.tvDistance.text = "Distance: %.1f km".format(distanceKm)

        binding.tvRideMiniFare.text = "₹%.0f".format(miniFare)
        binding.tvRideSedanFare.text = "₹%.0f".format(sedanFare)
        binding.tvRideSuvFare.text = "₹%.0f".format(suvFare)
    }


    private fun showRideOptionsCard() {

        binding.cardRidePanel.visibility = View.VISIBLE


    }
    private fun showLocationChoiceDialog(type: LocationType) {
        val options = arrayOf("Select on map", "Search address")

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(
                if (type == LocationType.PICKUP) "Set pickup location"
                else "Set drop location"
            )
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { // Select on map
                        currentMapSelection = type
                        Toast.makeText(requireContext(), "Tap on the map to choose location", Toast.LENGTH_SHORT).show()
                    }
                    1 -> { // Search using Google Places UI
                        autocompleteLocationType = type
                        openPlacesAutocomplete()
                    }
                }
            }
            .show()
    }

    private fun openPlacesAutocomplete() {
        val fields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.ADDRESS,
            Place.Field.LAT_LNG
        )

        val intent = Autocomplete.IntentBuilder(
            AutocompleteActivityMode.FULLSCREEN,
            fields
        ).build(requireContext())

        autocompleteLauncher.launch(intent)
    }

    fun showRideCard(response: RideCardResponse) {
        binding.cardRidePanel.visibility = View.GONE

        val rideCard = binding.root.findViewById<View>(R.id.layout_ride_card)
        rideCard?.visibility = View.VISIBLE

        val tvDriverName = binding.root.findViewById<TextView>(R.id.tvDriverName)
        val tvVehicleInfo = binding.root.findViewById<TextView>(R.id.tvVehicleInfo)
        val tvRating = binding.root.findViewById<TextView>(R.id.tvRating)
        val tvOtpCode = binding.root.findViewById<TextView>(R.id.tvOtpCode)
        val tvEstimatedFare = binding.root.findViewById<TextView>(R.id.tvEstimatedFare)

        val driver = response.driver
        val cab = response.cab

        tvDriverName?.text = driver?.name ?: "Unknown Driver"
        tvRating?.text = "★ ${driver?.avgRating ?: 4.5}"


        tvVehicleInfo?.text = when {
            cab != null -> {
                val color = cab.color ?: ""
                val model = cab.model ?: ""
                val regNo = cab.registrationNo ?: ""
                listOf("$color $model".trim(), regNo).filter { it.isNotBlank() }.joinToString(" • ")
                    .ifBlank { "Vehicle details unavailable" }
            }
            else -> "Vehicle details unavailable"
        }

        tvOtpCode?.text = response.otpCode ?: "--"
        tvEstimatedFare?.text = "Est. Fare: ₹${response.estimatedFare}"
    }


    private var driverMarker: Marker? = null

    private fun updateDriverMarker(position: LatLng) {
        if (!::googleMap.isInitialized) return

        if (driverMarker == null) {
            driverMarker = googleMap.addMarker(
                MarkerOptions()
                    .position(position)
                    .title("Driver")
                    .icon(
                        BitmapDescriptorFactory.defaultMarker(
                            BitmapDescriptorFactory.HUE_BLUE
                        )
                    )
            )
            googleMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(position, 15f)
            )
        } else {
            driverMarker!!.position = position
        }
    }



}
