# Frontend Flow Documentation

Complete flow documentation aligned with backend architecture. This document describes the exact user journeys and system interactions.

## Table of Contents

1. [Authentication Flow](#authentication-flow)
2. [Customer Ride Booking Flow](#customer-ride-booking-flow)
3. [Driver Ride Management Flow](#driver-ride-management-flow)
4. [WebSocket Communication Flow](#websocket-communication-flow)
5. [Ride Lifecycle States](#ride-lifecycle-states)

---

## Authentication Flow

### Sequence Diagram

```
┌─────────┐         ┌──────────┐         ┌─────────┐
│  User   │         │  Android │         │ Backend │
│         │         │   App    │         │   API   │
└────┬────┘         └────┬─────┘         └────┬────┘
     │                   │                     │
     │ 1. Launch App     │                     │
     ├──────────────────►│                     │
     │                   │                     │
     │ 2. Choose Role    │                     │
     │    (USER/DRIVER)  │                     │
     ├──────────────────►│                     │
     │                   │                     │
     │ 3. Navigate to    │                     │
     │    LoginFragment  │                     │
     │◄──────────────────┤                     │
     │                   │                     │
     │ 4. Enter Creds    │                     │
     │    (mobile/pwd)   │                     │
     ├──────────────────►│                     │
     │                   │                     │
     │                   │ 5. POST /api/auth/login
     │                   ├─────────────────────►│
     │                   │                     │
     │                   │ 6. Response: {token, userId, role}
     │                   │◄─────────────────────┤
     │                   │                     │
     │                   │ 7. Save to SessionManager
     │                   │    (JWT token, user data)
     │                   │                     │
     │ 8. Navigate based │                     │
     │    on role        │                     │
     │◄──────────────────┤                     │
     │                   │                     │
     │ USER → HomeFragment│                     │
     │ DRIVER → DriverHome│                     │
     │                   │                     │
```

### Implementation Details

**ChooseRoleFragment**
- Start destination of navigation graph
- User selects role: USER or DRIVER
- Navigates to LoginFragment

**LoginFragment**
```kotlin
// Current implementation
val req = LoginRequestDto(mobileNum = phone, password = password)
val res = api.login(req)

SessionManager.saveLogin(
    context = requireContext(),
    jwt = res.accessToken,
    userId = res.userId,
    firstName = res.firstName,
    lastName = res.lastName,
    mobile = res.mobileNum,
    email = res.email
)

// Navigate based on role
when (res.role) {
    "DRIVER" -> navigateToDriverHome()
    "USER" -> navigateToHome()
}
```

**Backend Alignment:**
- ✅ Endpoint: `POST /api/auth/login`
- ✅ Request: `{mobileNum, password}`
- ✅ Response: `{token, userId, email, role}`
- ✅ JWT token stored in SessionManager

---

## Customer Ride Booking Flow

### Complete Sequence

```
┌─────────┐    ┌──────────┐    ┌─────────┐    ┌──────────┐
│Customer │    │Android   │    │ Backend  │    │  Driver  │
│         │    │   App    │    │   API    │    │   App    │
└────┬────┘    └────┬─────┘    └────┬─────┘    └────┬─────┘
     │              │               │               │
     │ 1. Select    │               │               │
     │    locations │               │               │
     ├─────────────►│               │               │
     │              │               │               │
     │ 2. Calculate │               │               │
     │    route     │               │               │
     │    (Google)  │               │               │
     ├─────────────►│               │               │
     │              │               │               │
     │ 3. Show fare │               │               │
     │    estimate  │               │               │
     │◄─────────────┤               │               │
     │              │               │               │
     │ 4. Select    │               │               │
     │    ride type │               │               │
     ├─────────────►│               │               │
     │              │               │               │
     │              │ 5. POST /api/rides
     │              ├───────────────►│               │
     │              │               │               │
     │              │ 6. Response: {rideId, estimatedFare, otpCode}
     │              │◄───────────────┤               │
     │              │               │               │
     │              │ 7. Find nearby drivers
     │              │    (within 3km, online)
     │              │               │               │
     │              │ 8. WebSocket: /queue/ride-requests{driverId}
     │              │               ├───────────────►│
     │              │               │               │
     │              │ 9. Subscribe to /queue/ride-status{userId}
     │              │               │               │
     │              │               │ 10. Driver accepts
     │              │               │◄───────────────┤
     │              │               │               │
     │              │ 11. Assign driver to ride
     │              │               │               │
     │              │ 12. WebSocket: /queue/ride-status{userId}
     │              │◄───────────────┤               │
     │              │               │               │
     │ 13. Display  │               │               │
     │     driver   │               │               │
     │     info     │               │               │
     │◄─────────────┤               │               │
     │              │               │               │
     │              │ 14. Subscribe to /topic/ride/{rideId}/location
     │              │               │               │
     │              │               │ 15. Driver sends location
     │              │               │◄───────────────┤
     │              │               │               │
     │              │ 16. Broadcast: /topic/ride/{rideId}/location
     │              │◄───────────────┤               │
     │              │               │               │
     │ 17. Update   │               │               │
     │     driver   │               │               │
     │     marker   │               │               │
     │◄─────────────┤               │               │
     │              │               │               │
     │ 18. Enter OTP│               │               │
     │     to start │               │               │
     ├─────────────►│               │               │
     │              │               │               │
     │              │ 19. POST /api/rides/start
     │              ├───────────────►│               │
     │              │               │               │
     │              │ 20. Status: STARTED
     │              │◄───────────────┤               │
     │              │               │               │
     │              │               │ 21. Driver ends ride
     │              │               │◄───────────────┤
     │              │               │               │
     │              │ 22. POST /api/rides/{id}/end
     │              │               ├───────────────►│
     │              │               │               │
     │              │ 23. Status: COMPLETED
     │              │◄───────────────┤               │
     │              │               │               │
     │              │ 24. WebSocket: Status update
     │              │◄───────────────┤               │
     │              │               │               │
     │ 25. Show     │               │               │
     │     payment  │               │               │
     │◄─────────────┤               │               │
     │              │               │               │
     │ 26. Select   │               │               │
     │     payment  │               │               │
     │     method   │               │               │
     ├─────────────►│               │               │
     │              │               │               │
     │              │ 27. POST /api/rides/{id}/payment-success
     │              │               ├───────────────►│
     │              │               │               │
     │              │ 28. Payment: PAID
     │              │◄───────────────┤               │
     │              │               │               │
     │ 29. Return   │               │               │
     │     to home  │               │               │
     │◄─────────────┤               │               │
```

### Step-by-Step Implementation

#### Step 1-3: Location Selection & Route Calculation

**Current Implementation:**
```kotlin
// HomeFragment.kt
private fun drawRoute(pickup: LatLng, drop: LatLng) {
    lifecycleScope.launch(Dispatchers.IO) {
        val response = GoogleDirectionsClient.api.getRoute(
            origin = "${pickup.latitude},${pickup.longitude}",
            destination = "${drop.latitude},${drop.longitude}",
            apiKey = mapsApiKey
        )
        
        // Decode polyline and draw on map
        // Calculate fare estimate
        showFareEstimateCard(distanceMeters, durationSeconds)
    }
}
```

**Status:** ✅ Implemented

#### Step 4-6: Create Ride Request

**Current Implementation:**
```kotlin
// HomeFragment.kt
private fun createRideAndPoll() {
    val req = CreateRideRequestDto(
        pickupLat = pickup.latitude,
        pickupLng = pickup.longitude,
        dropLat = drop.latitude,
        dropLng = drop.longitude
    )
    
    lifecycleScope.launch {
        val response = api.createRide(req)
        val rideId = response.body()?.rideId
        currentRideId = rideId
        
        // ❌ Currently using polling
        startPollingForDriver(rideId)
    }
}
```

**Backend Endpoint:**
- `POST /api/rides`
- Request: `{pickupLat, pickupLng, dropLat, dropLng}`
- Response: `{rideId, estimatedFare, status: "REQUESTED"}`

**Status:** ✅ Implemented (but using polling instead of WebSocket)

#### Step 7-12: Driver Assignment

**Backend Flow:**
1. Server finds nearby drivers (within 3km, status=ONLINE)
2. Server sends WebSocket message to each driver: `/queue/ride-requests{driverId}`
3. First driver to accept gets assigned
4. Server sends notification to customer: `/queue/ride-status{custId}`

**Current Implementation (WRONG):**
```kotlin
// ❌ Using polling every 3 seconds
private fun startPollingForDriver(rideId: Int) {
    pollingJob = lifecycleScope.launch {
        while (isActive) {
            val response = api.getRideCard(rideId)
            if (card.driver != null && card.status == "ASSIGNED") {
                showRideCard(card)
                break
            }
            delay(3000)  // Poll every 3 seconds
        }
    }
}
```

**Should Be:**
```kotlin
// ✅ Use WebSocket subscription
private fun subscribeToRideUpdates(rideId: Int) {
    val userId = SessionManager.userId ?: return
    
    stompClient.topic("/queue/ride-status$userId")
        .subscribe { message ->
            val card = Gson().fromJson(message.payload, RideCardResponse::class.java)
            if (card.driver != null && card.status == "ASSIGNED") {
                showRideCard(card)
                subscribeToDriverLocation(rideId)
            }
        }
}
```

**Status:** ❌ Not implemented correctly (using polling)

#### Step 13-17: Driver Location Tracking

**Backend Flow:**
1. Customer subscribes to: `/topic/ride/{rideId}/location`
2. Driver sends location via: `/app/driver/location` or `PUT /api/driver-location/{driverId}`
3. Server broadcasts to: `/topic/ride/{rideId}/location`
4. Customer receives location updates and updates map marker

**Current Implementation:**
```kotlin
// ❌ Not implemented
private fun subscribeToDriverLocation(rideId: Int) {
    // Should subscribe to /topic/ride/{rideId}/location
    // Should update driver marker on map
}
```

**Status:** ❌ Not implemented

#### Step 18-20: Start Ride

**Backend Endpoint:**
- `POST /api/rides/start`
- Request: `{user_id, otp_code}`
- Response: `"Ride started successfully"`

**Current Implementation:**
```kotlin
// ❌ Not implemented
// Should have OTP input dialog
// Should call POST /api/rides/start
```

**Status:** ❌ Not implemented

#### Step 21-24: End Ride

**Backend Endpoint:**
- `POST /api/rides/{rideId}/end`
- Response: `{id, status: "COMPLETED", finalFare, endedOn}`

**Current Implementation:**
```kotlin
// ❌ Not implemented
// Driver ends ride, customer receives status update via WebSocket
```

**Status:** ❌ Not implemented

#### Step 25-28: Payment

**Backend Endpoint:**
- `POST /api/rides/{rideId}/payment-success`
- Request: `{method: "CASH" | "CARD" | "UPI"}`
- Response: `{id, paymentStatus: "PAID", paymentMethod}`

**Current Implementation:**
```kotlin
// ❌ Not implemented
// Should show payment method selection
// Should call POST /api/rides/{rideId}/payment-success
```

**Status:** ❌ Not implemented

---

## Driver Ride Management Flow

### Complete Sequence

```
┌─────────┐    ┌──────────┐    ┌─────────┐    ┌──────────┐
│ Driver  │    │Android   │    │ Backend │    │Customer  │
│         │    │   App    │    │   API   │    │   App    │
└────┬────┘    └────┬─────┘    └────┬─────┘    └────┬─────┘
     │              │               │               │
     │ 1. Go Online │               │               │
     ├─────────────►│               │               │
     │              │               │               │
     │              │ 2. POST /drivers/{id}/status?status=ONLINE
     │              ├───────────────►│               │
     │              │               │               │
     │              │ 3. Connect WebSocket
     │              ├───────────────►│               │
     │              │               │               │
     │              │ 4. Subscribe to /queue/ride-requests{driverId}
     │              ├───────────────►│               │
     │              │               │               │
     │              │               │ 5. Customer creates ride
     │              │               │◄───────────────┤
     │              │               │               │
     │              │ 6. Find nearby drivers
     │              │               │               │
     │              │ 7. WebSocket: /queue/ride-requests{driverId}
     │              │◄───────────────┤               │
     │              │               │               │
     │ 8. Display   │               │               │
     │    ride      │               │               │
     │    request   │               │               │
     │◄─────────────┤               │               │
     │              │               │               │
     │ 9. Accept    │               │               │
     │    ride      │               │               │
     ├─────────────►│               │               │
     │              │               │               │
     │              │ 10. SEND /app/driver/ride/response
     │              │     {rideId, accepted: true, driverId}
     │              ├───────────────►│               │
     │              │               │               │
     │              │ 11. Assign driver to ride
     │              │               │               │
     │              │ 12. Notify customer
     │              │               ├───────────────►│
     │              │               │               │
     │ 13. Navigate │               │               │
     │     to       │               │               │
     │     Current  │               │               │
     │     Ride     │               │               │
     │◄─────────────┤               │               │
     │              │               │               │
     │ 14. Start    │               │               │
     │     location │               │               │
     │     tracking │               │               │
     ├─────────────►│               │               │
     │              │               │               │
     │              │ 15. PUT /api/driver-location/{driverId}
     │              │     (every 5 seconds)
     │              ├───────────────►│               │
     │              │               │               │
     │              │ 16. Broadcast to /topic/ride/{rideId}/location
     │              │               ├───────────────►│
     │              │               │               │
     │ 17. Enter OTP│               │               │
     │     to start │               │               │
     ├─────────────►│               │               │
     │              │               │               │
     │              │ 18. POST /api/rides/start
     │              │     {user_id, otp_code}
     │              ├───────────────►│               │
     │              │               │               │
     │              │ 19. Status: STARTED
     │              │◄───────────────┤               │
     │              │               │               │
     │              │ 20. Continue location updates
     │              │               │               │
     │ 21. End ride │               │               │
     ├─────────────►│               │               │
     │              │               │               │
     │              │ 22. POST /api/rides/{rideId}/end
     │              │               ├───────────────►│
     │              │               │               │
     │              │ 23. Status: COMPLETED
     │              │◄───────────────┤               │
     │              │               │               │
     │              │ 24. Notify customer
     │              │               ├───────────────►│
     │              │               │               │
     │ 25. Return   │               │               │
     │     to home  │               │               │
     │◄─────────────┤               │               │
```

### Step-by-Step Implementation

#### Step 1-4: Go Online & Connect WebSocket

**Current Implementation:**
```kotlin
// DriverHomeFragment.kt
private fun setupOnlineSwitch() {
    binding.switchOnline.setOnCheckedChangeListener { _, isChecked ->
        val driverId = SessionManager.userId ?: return
        val token = SessionManager.token ?: return
        
        lifecycleScope.launch {
            api.updateOnlineStatus(
                driverId, 
                mapOf("online" to isChecked), 
                "Bearer $token"
            )
        }
    }
}

private fun connectStomp() {
    val token = SessionManager.token ?: return
    
    stompClient = Stomp.over(
        Stomp.ConnectionProvider.OKHTTP,
        "ws://10.0.2.2:9090/ws"
    )
    
    val headers = listOf(
        StompHeader("Authorization", "Bearer $token")
    )
    
    stompClient.connect(headers)
    
    // ⚠️ Topic path may be incorrect
    stompClient.topic("/user/queue/ride-request")
        .subscribe { message ->
            // Handle ride request
        }
}
```

**Backend Alignment:**
- ✅ Endpoint: `POST /drivers/{driverId}/status?status=ONLINE`
- ⚠️ WebSocket topic should be: `/queue/ride-requests{driverId}` (not `/user/queue/ride-request`)

**Status:** ⚠️ Partially implemented (topic path may be wrong)

#### Step 5-8: Receive Ride Request

**Backend Flow:**
1. Customer creates ride
2. Server finds nearby drivers
3. Server sends to each driver: `/queue/ride-requests{driverId}`
4. Message contains: `{rideId, pickupLat, pickupLng, dropLat, dropLng, estimatedFare}`

**Current Implementation:**
```kotlin
// DriverHomeFragment.kt
stompClient.topic("/user/queue/ride-request")
    .subscribe { stompMessage ->
        val json = JSONObject(stompMessage.payload)
        val rideId = json.getInt("rideId")
        
        requireActivity().runOnUiThread {
            handleIncomingRide(rideId)
        }
    }
```

**Status:** ⚠️ Implemented but topic path may be incorrect

#### Step 9-12: Accept Ride

**Current Implementation:**
```kotlin
// DriverHomeFragment.kt
private fun sendRideResponse(status: String, rideId: Int) {
    val payload = JSONObject().apply {
        put("rideId", rideId)
        put("status", status)
        put("driverId", SessionManager.userId)
    }
    
    // ⚠️ Endpoint path may be incorrect
    stompClient.send("/app/ride/response", payload.toString())
        .subscribe()
}
```

**Backend Alignment:**
- Should be: `/app/driver/ride/response`
- Payload: `{rideId, accepted: true, driverId}`

**Status:** ⚠️ Implemented but endpoint path may be incorrect

#### Step 13-16: Location Tracking

**Current Implementation:**
```kotlin
// DriverHomeFragment.kt
private fun startLocationUpdates() {
    val request = LocationRequest.create().apply {
        interval = 5000
        priority = Priority.PRIORITY_HIGH_ACCURACY
    }
    
    locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            sendLocationToBackend(location.latitude, location.longitude)
        }
    }
    
    fusedLocationClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
}

private fun sendLocationToBackend(lat: Double, lng: Double) {
    lifecycleScope.launch {
        val api = ApiClient.create(DriverApi::class.java)
        api.updateDriverLocation(
            token = "Bearer ${SessionManager.token}",
            body = mapOf("lat" to lat, "lng" to lng)
        )
    }
}
```

**Backend Endpoint:**
- `PUT /api/driver-location/{driverId}`
- Request: `{lat, lng}`

**Status:** ⚠️ Partially implemented (code exists but needs integration)

#### Step 17-19: Start Ride

**Status:** ❌ Not implemented

#### Step 20-24: End Ride

**Status:** ❌ Not implemented

---

## WebSocket Communication Flow

### Connection Establishment

```
┌─────────┐                    ┌─────────┐
│ Client  │                    │ Server │
│  App    │                    │        │
└────┬────┘                    └────┬────┘
     │                              │
     │ 1. CONNECT                   │
     │    ws://localhost:9090/ws   │
     │    Authorization: Bearer <token>│
     ├──────────────────────────────►│
     │                              │
     │                              │ 2. Validate JWT token
     │                              │
     │ 3. CONNECTED                 │
     │◄──────────────────────────────┤
     │                              │
     │ 4. SUBSCRIBE                 │
     │    /queue/ride-requests{id}  │
     ├──────────────────────────────►│
     │                              │
     │ 5. SUBSCRIBED                │
     │◄──────────────────────────────┤
```

### Customer WebSocket Flow

```
┌─────────┐                    ┌─────────┐
│Customer │                    │ Server  │
│  App    │                    │         │
└────┬────┘                    └────┬────┘
     │                              │
     │ 1. After creating ride       │
     │    Subscribe to:             │
     │    /queue/ride-status{userId}│
     ├──────────────────────────────►│
     │                              │
     │                              │ 2. Driver accepts
     │                              │
     │ 3. MESSAGE                   │
     │    {rideId, driverId, status: ASSIGNED}
     │◄──────────────────────────────┤
     │                              │
     │ 4. Subscribe to:             │
     │    /topic/ride/{rideId}/location
     ├──────────────────────────────►│
     │                              │
     │                              │ 5. Driver sends location
     │                              │
     │ 6. MESSAGE                   │
     │    {lat, lng}                │
     │◄──────────────────────────────┤
     │                              │
     │ 7. Update driver marker      │
     │                              │
```

### Driver WebSocket Flow

```
┌─────────┐                    ┌─────────┐
│ Driver  │                    │ Server  │
│  App    │                    │         │
└────┬────┘                    └────┬────┘
     │                              │
     │ 1. Go online                 │
     │    Subscribe to:            │
     │    /queue/ride-requests{driverId}
     ├──────────────────────────────►│
     │                              │
     │                              │ 2. Customer creates ride
     │                              │
     │ 3. MESSAGE                   │
     │    {rideId, pickup, drop, fare}
     │◄──────────────────────────────┤
     │                              │
     │ 4. Accept ride                │
     │    SEND /app/driver/ride/response
     │    {rideId, accepted: true} │
     ├──────────────────────────────►│
     │                              │
     │                              │ 5. Assign driver
     │                              │
     │ 6. MESSAGE                   │
     │    /queue/ride-status{driverId}
     │    "Ride assigned"           │
     │◄──────────────────────────────┤
```

---

## Ride Lifecycle States

### State Transitions

```
REQUESTED ──► ASSIGNED ──► STARTED ──► COMPLETED
    │             │            │            │
    └─────────────┴────────────┴────────────┘
                    CANCELLED
```

### State Descriptions

| State | Description | Trigger | Frontend Action |
|-------|-------------|---------|-----------------|
| **REQUESTED** | Ride created, waiting for driver | Customer creates ride | Show "Finding driver..." |
| **ASSIGNED** | Driver accepted ride | Driver accepts | Show driver info, OTP code |
| **STARTED** | Ride in progress | OTP verified | Show "Ride in progress" |
| **COMPLETED** | Ride finished | Driver ends ride | Show final fare, payment |
| **CANCELLED** | Ride cancelled | Customer/Driver cancels | Return to home |

### Frontend Implementation Status

| State | Customer | Driver | Status |
|-------|----------|--------|--------|
| REQUESTED | ✅ Shows "Finding driver" | ✅ Receives request | ✅ Implemented |
| ASSIGNED | ⚠️ Uses polling | ✅ Shows ride card | ⚠️ Partial |
| STARTED | ❌ Not implemented | ❌ Not implemented | ❌ Missing |
| COMPLETED | ❌ Not implemented | ❌ Not implemented | ❌ Missing |
| CANCELLED | ⚠️ Basic cancel | ❌ Not implemented | ⚠️ Partial |

---

## Summary

### Current Implementation Status

| Flow | Status | Notes |
|------|--------|-------|
| Authentication | ✅ Complete | Login, signup working |
| Customer: Book Ride | ✅ Complete | Location selection, route calculation |
| Customer: Wait for Driver | ⚠️ Partial | Using polling instead of WebSocket |
| Customer: Track Driver | ❌ Missing | Not implemented |
| Customer: Start Ride | ❌ Missing | OTP verification not implemented |
| Customer: Payment | ❌ Missing | Payment flow not implemented |
| Driver: Go Online | ✅ Complete | Status toggle working |
| Driver: Receive Request | ⚠️ Partial | WebSocket topic may be wrong |
| Driver: Accept Ride | ⚠️ Partial | Endpoint path may be wrong |
| Driver: Location Tracking | ⚠️ Partial | Code exists but not fully integrated |
| Driver: Start Ride | ❌ Missing | OTP verification not implemented |
| Driver: End Ride | ❌ Missing | Not implemented |

### Critical Gaps

1. ❌ Customer WebSocket subscription (using polling)
2. ❌ Ride start flow (both customer and driver)
3. ❌ Ride end flow
4. ❌ Payment flow
5. ❌ Driver location tracking during active ride
6. ⚠️ WebSocket topic/endpoint paths may be incorrect

### Recommended Priority

1. **Immediate**: Fix WebSocket implementation for customers
2. **High**: Implement ride start/end flows
3. **High**: Implement payment flow
4. **Medium**: Fix WebSocket topic paths
5. **Medium**: Integrate driver location tracking
6. **Low**: Refactor to MVVM architecture
