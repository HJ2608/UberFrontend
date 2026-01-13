# Frontend Architecture Documentation

Complete documentation for the Android frontend application, aligned with the backend architecture and flow.

## Table of Contents

1. [Current Architecture](#current-architecture)
2. [Application Flow](#application-flow)
3. [Component Structure](#component-structure)
4. [Backend Integration](#backend-integration)
5. [WebSocket Implementation](#websocket-implementation)
6. [Structure Assessment](#structure-assessment)
7. [Recommended Improvements](#recommended-improvements)

---

## Current Architecture

### Overview

The Android application follows a **Fragment-based navigation** architecture with:
- **Single Activity** (`MainActivity`) hosting all fragments
- **Navigation Component** for fragment navigation
- **Retrofit** for REST API calls
- **STOMP over WebSocket** for real-time communication
- **SharedPreferences** for session management
- **Google Maps SDK** for location services

### Package Structure

```
com.example.uberfrontend/
├── MainActivity.kt                    # Single activity host
│
├── network/                           # Network layer
│   ├── ApiClient.kt                  # Retrofit client setup
│   ├── ApiInterfaces.kt              # API endpoint definitions
│   ├── GoogleDirectionsClient.kt     # Google Maps API client
│   ├── DirectionsApi.kt              # Directions API interface
│   ├── DirectionsResponse.kt         # Directions response models
│   ├── dto/                          # Data Transfer Objects
│   │   ├── AuthDtos.kt               # Authentication DTOs
│   │   ├── RideCardDtos.kt           # Ride card DTOs
│   │   └── RidesDtos.kt              # Ride request/response DTOs
│   └── sockets/
│       └── DriverWebSocketClient.kt  # WebSocket client (unused)
│
├── session/                          # Session management
│   └── SessionManager.kt              # JWT token & user data storage
│
└── ui/                                # UI layer
    ├── auth/                          # Authentication screens
    │   ├── ChooseRoleFragment.kt      # Role selection (USER/DRIVER)
    │   ├── LoginFragment.kt           # Login screen
    │   └── SignupFragment.kt          # Registration screen
    │
    ├── home/                          # Main screens
    │   ├── HomeFragment.kt            # Customer home (ride booking)
    │   ├── DriverHomeFragment.kt      # Driver home (ride requests)
    │   └── DriverCurrentRIdeFragment.kt  # Driver active ride screen
    │
    ├── drop/                          # Location selection
    │   └── DropLocationFragment.kt    # Drop location picker
    │
    └── profile/                       # User profile
        └── ProfileFragment.kt         # Profile screen
```

### Technology Stack

- **Language**: Kotlin
- **UI Framework**: Android Jetpack (Fragments, Navigation Component)
- **Networking**: Retrofit 2, OkHttp
- **WebSocket**: STOMP Client (ua.naiksoftware.stomp)
- **Maps**: Google Maps SDK, Places API
- **Location**: Google Play Services Location API
- **Architecture**: Fragment-based (no ViewModel/MVVM yet)
- **Dependency Injection**: Manual (no Dagger/Hilt)

---

## Application Flow

### Complete User Journey (Aligned with Backend)

#### 1. Authentication Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    Authentication Flow                        │
└─────────────────────────────────────────────────────────────┘

1. App Launch
   └─► ChooseRoleFragment (start destination)
       ├─► User selects "USER" or "DRIVER"
       └─► Navigate to LoginFragment

2. Login
   └─► LoginFragment
       ├─► User enters mobile/password
       ├─► POST /api/auth/login
       ├─► Receive JWT token + user data
       ├─► SessionManager.saveLogin()
       └─► Navigate based on role:
           ├─► USER → HomeFragment
           └─► DRIVER → DriverHomeFragment

3. Signup (Alternative)
   └─► SignupFragment
       ├─► User enters details
       ├─► POST /api/auth/signup
       └─► Navigate to LoginFragment
```

**Backend Alignment:**
- ✅ Uses `/api/auth/login` endpoint
- ✅ Stores JWT token in SessionManager
- ✅ Role-based navigation matches backend roles (USER, DRIVER)

---

#### 2. Customer Ride Booking Flow

```
┌─────────────────────────────────────────────────────────────┐
│              Customer Ride Booking Flow                      │
└─────────────────────────────────────────────────────────────┘

1. HomeFragment (Customer)
   ├─► Display Google Map
   ├─► Get user's current location (pickup)
   ├─► User selects drop location (map tap or Places search)
   └─► User clicks "Request Ride"

2. Route Calculation
   └─► Google Directions API
       ├─► Calculate route
       ├─► Display polyline on map
       ├─► Show fare estimate (MINI/SEDAN/SUV)
       └─► User selects ride type

3. Create Ride Request
   └─► POST /api/rides
       ├─► Request: {pickupLat, pickupLng, dropLat, dropLng}
       ├─► Response: {rideId, estimatedFare, otpCode}
       └─► Current Implementation:
           ├─► ❌ Uses polling (getRideCard every 3 seconds)
           └─► ✅ Should use WebSocket subscription

4. Driver Assignment (Backend)
   └─► Server finds nearby drivers
       ├─► Sends WebSocket message to drivers
       │   └─► /queue/ride-requests{driverId}
       └─► First driver to accept gets assigned

5. Receive Driver Assignment
   └─► Should receive via WebSocket:
       └─► /queue/ride-status{custId}
           ├─► Message: {rideId, driverId, status: "ASSIGNED", ...}
           └─► Display RideCard with driver info

6. Ride Start
   └─► POST /api/rides/start
       ├─► Request: {user_id, otp_code}
       └─► Status changes to "STARTED"

7. Driver Location Updates (During Ride)
   └─► Subscribe to: /topic/ride/{rideId}/location
       ├─► Receive real-time driver location
       └─► Update driver marker on map

8. Ride End
   └─► POST /api/rides/{rideId}/end
       └─► Status changes to "COMPLETED"

9. Payment
   └─► POST /api/rides/{rideId}/payment-success
       ├─► Request: {method: "CASH" | "CARD" | "UPI"}
       └─► Payment status updated
```

**Current Implementation Status:**
- ✅ Step 1-3: Implemented
- ⚠️ Step 4-5: Using polling instead of WebSocket
- ❌ Step 6: Not implemented (ride start with OTP)
- ❌ Step 7: Not implemented (driver location tracking)
- ❌ Step 8: Not implemented (ride end)
- ❌ Step 9: Not implemented (payment)

---

#### 3. Driver Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    Driver Flow                              │
└─────────────────────────────────────────────────────────────┘

1. DriverHomeFragment
   ├─► Toggle online/offline status
   │   └─► POST /drivers/{driverId}/status?status=ONLINE/OFFLINE
   └─► Connect to WebSocket
       └─► Subscribe to: /queue/ride-requests{driverId}

2. Receive Ride Request
   └─► WebSocket message received
       ├─► Display ride request card
       ├─► Show pickup/drop locations
       ├─► Show estimated fare
       └─► Driver can Accept or Reject

3. Accept Ride
   └─► SEND /app/driver/ride/response
       ├─► Payload: {rideId, accepted: true, driverId}
       └─► Backend assigns driver to ride
           └─► Navigate to DriverCurrentRideFragment

4. Driver Current Ride Screen
   └─► DriverCurrentRideFragment
       ├─► Display ride details
       ├─► Start location tracking
       │   └─► PUT /api/driver-location/{driverId}
       │       └─► Send location every 5 seconds
       └─► Actions:
           ├─► Start ride (with OTP verification)
           └─► End ride

5. Start Ride
   └─► POST /api/rides/start
       ├─► Request: {user_id, otp_code}
       └─► Status changes to "STARTED"

6. Location Updates (During Active Ride)
   └─► Continuously send location
       ├─► PUT /api/driver-location/{driverId}
       └─► Backend broadcasts to: /topic/ride/{rideId}/location

7. End Ride
   └─► POST /api/rides/{rideId}/end
       └─► Status changes to "COMPLETED"
       └─► Navigate back to DriverHomeFragment
```

**Current Implementation Status:**
- ✅ Step 1: Partially implemented (WebSocket connection exists)
- ⚠️ Step 2: WebSocket subscription topic may be incorrect
- ✅ Step 3: Accept ride implemented
- ⚠️ Step 4: DriverCurrentRideFragment exists but incomplete
- ❌ Step 5: Not implemented (ride start)
- ⚠️ Step 6: Location tracking code exists but not fully integrated
- ❌ Step 7: Not implemented (ride end)

---

## Component Structure

### Network Layer

#### API Client Setup
```kotlin
// ApiClient.kt
object ApiClient {
    private val retrofit = Retrofit.Builder()
        .baseUrl("http://10.0.2.2:9090/")  // Android emulator → localhost
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    fun <T> create(service: Class<T>): T = retrofit.create(service)
}
```

**Issues:**
- ❌ Hardcoded base URL (should use BuildConfig or config)
- ❌ No authentication interceptor (JWT token added manually)
- ❌ No error handling interceptor

#### API Interfaces
```kotlin
// ApiInterfaces.kt
interface AuthApi {
    @POST("api/auth/signup")
    suspend fun signup(@Body req: SignupRequestDto): Response<SignupResponseDto>
    
    @POST("api/auth/login")
    suspend fun login(@Body req: LoginRequestDto): LoginResponseDto
}

interface RideApi {
    @POST("api/rides")
    suspend fun createRide(@Body body: CreateRideRequestDto): Response<CreateRideResponseDto>
    
    @GET("api/rides/card")
    suspend fun getRideCard(@Query("rideId") rideId: Int): Response<RideCardResponse>
    // ... more endpoints
}
```

**Backend Alignment:**
- ✅ Endpoints match backend API documentation
- ⚠️ Missing some endpoints (start ride, end ride, payment)

---

### Session Management

```kotlin
// SessionManager.kt
object SessionManager {
    var token: String? = null
    var userId: Int? = null
    var firstName: String? = null
    // ... more fields
    
    fun saveLogin(context: Context, jwt: String, userId: Int, ...)
    fun init(context: Context)
    fun clear(context: Context)
}
```

**Issues:**
- ⚠️ Uses SharedPreferences (works but not encrypted)
- ⚠️ No token expiration handling
- ⚠️ No automatic token refresh

---

### WebSocket Implementation

#### Current Implementation (DriverHomeFragment)

```kotlin
// DriverHomeFragment.kt
stompClient = Stomp.over(
    Stomp.ConnectionProvider.OKHTTP,
    "ws://10.0.2.2:9090/ws"
)

stompClient.connect(headers)  // with JWT token

// Subscribe to ride requests
stompClient.topic("/user/queue/ride-request")
    .subscribe { message ->
        // Handle ride request
    }
```

**Issues:**
- ⚠️ Topic path may be incorrect (should be `/queue/ride-requests{driverId}`)
- ⚠️ No reconnection logic
- ⚠️ No error handling

#### Customer WebSocket (HomeFragment)

```kotlin
// HomeFragment.kt
stompClient = Stomp.over(...)
stompClient.connect()

// Currently using polling instead:
pollingJob = lifecycleScope.launch {
    while (isActive) {
        val response = api.getRideCard(rideId)
        // Poll every 3 seconds
        delay(3000)
    }
}
```

**Issues:**
- ❌ Not using WebSocket for ride status updates
- ❌ Polling is inefficient and battery-draining
- ⚠️ Should subscribe to `/queue/ride-status{custId}`

---

## Backend Integration

### API Endpoint Mapping

| Frontend Endpoint | Backend Endpoint | Status |
|------------------|------------------|--------|
| `POST api/auth/login` | `POST /api/auth/login` | ✅ Implemented |
| `POST api/auth/signup` | `POST /api/auth/signup` | ✅ Implemented |
| `POST api/rides` | `POST /api/rides` | ✅ Implemented |
| `GET api/rides/card` | `GET /api/rides/card?rideId={id}` | ✅ Implemented |
| `POST api/rides/start` | `POST /api/rides/start` | ❌ Missing |
| `POST api/rides/{id}/end` | `POST /api/rides/{rideId}/end` | ❌ Missing |
| `POST api/rides/{id}/payment-success` | `POST /api/rides/{rideId}/payment-success` | ❌ Missing |
| `POST /drivers/{id}/status` | `POST /drivers/{driverId}/status?status={status}` | ⚠️ Partial |
| `PUT /api/driver-location/{id}` | `PUT /api/driver-location/{driverId}` | ⚠️ Partial |

### WebSocket Topic Mapping

| Frontend Topic | Backend Topic | Status |
|---------------|---------------|--------|
| `/user/queue/ride-request` | `/queue/ride-requests{driverId}` | ⚠️ Path mismatch |
| (Polling used) | `/queue/ride-status{custId}` | ❌ Not implemented |
| (Not implemented) | `/queue/ride-status{driverId}` | ❌ Missing |
| (Not implemented) | `/topic/ride/{rideId}/location` | ❌ Missing |

### Message Endpoints

| Frontend Endpoint | Backend Endpoint | Status |
|------------------|------------------|--------|
| `/app/ride/response` | `/app/driver/ride/response` | ⚠️ Path mismatch |
| (Not implemented) | `/app/driver/location` | ❌ Missing |

---

## Structure Assessment

### Current Structure: **Moderate** (6/10)

#### ✅ Strengths

1. **Clear Package Organization**
   - Separation of network, UI, and session layers
   - Logical grouping of fragments by feature

2. **Navigation Component**
   - Uses Android Navigation Component (modern approach)
   - Centralized navigation graph

3. **Backend Alignment**
   - API endpoints mostly match backend
   - DTOs align with backend models

4. **Maps Integration**
   - Google Maps SDK properly integrated
   - Location services working

#### ⚠️ Areas for Improvement

1. **Architecture Pattern**
   - ❌ No ViewModel layer (business logic in fragments)
   - ❌ No Repository pattern
   - ❌ No separation of concerns

2. **WebSocket Implementation**
   - ❌ Customer side uses polling instead of WebSocket
   - ⚠️ Driver WebSocket topic paths may be incorrect
   - ❌ No reconnection logic
   - ❌ No proper error handling

3. **Missing Features**
   - ❌ Ride start flow (OTP verification)
   - ❌ Ride end flow
   - ❌ Payment flow
   - ❌ Driver location tracking during ride
   - ❌ Customer tracking driver location

4. **Code Quality**
   - ⚠️ Hardcoded URLs and configuration
   - ⚠️ No dependency injection
   - ⚠️ Limited error handling
   - ⚠️ No unit tests

5. **Session Management**
   - ⚠️ No token expiration handling
   - ⚠️ No automatic refresh

### Comparison to Ideal Android Architecture

| Aspect | Current | Ideal | Gap |
|--------|---------|-------|-----|
| Architecture Pattern | Fragment-based | MVVM + Repository | High |
| Dependency Injection | Manual | Dagger/Hilt | Medium |
| State Management | Fragment state | ViewModel + LiveData/Flow | High |
| Error Handling | Basic try-catch | Centralized error handling | Medium |
| Testing | No tests | Unit + UI tests | High |
| Configuration | Hardcoded | BuildConfig/Config | Low |

---

## Recommended Improvements

### Priority 1: Critical (Complete Backend Integration)

#### 1. Fix WebSocket Implementation

**Customer Side:**
```kotlin
// HomeFragment.kt - Replace polling with WebSocket
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

private fun subscribeToDriverLocation(rideId: Int) {
    stompClient.topic("/topic/ride/$rideId/location")
        .subscribe { message ->
            val location = Gson().fromJson(message.payload, LocationDto::class.java)
            updateDriverMarker(LatLng(location.lat, location.lng))
        }
}
```

**Driver Side:**
```kotlin
// DriverHomeFragment.kt - Fix topic path
val driverId = SessionManager.userId ?: return
stompClient.topic("/queue/ride-requests$driverId")
    .subscribe { message ->
        // Handle ride request
    }
```

#### 2. Implement Ride Start Flow

```kotlin
// Add to RideApi
@POST("api/rides/start")
suspend fun startRide(
    @Body body: StartRideRequestDto,
    @Header("Authorization") token: String
): Response<Unit>

// In HomeFragment or new RideStartFragment
private fun startRide(otpCode: String) {
    lifecycleScope.launch {
        val api = ApiClient.create(RideApi::class.java)
        val response = api.startRide(
            StartRideRequestDto(
                user_id = SessionManager.userId!!,
                otp_code = otpCode
            ),
            "Bearer ${SessionManager.token}"
        )
        // Handle response
    }
}
```

#### 3. Implement Ride End Flow

```kotlin
// Add to RideApi
@POST("api/rides/{rideId}/end")
suspend fun endRide(
    @Path("rideId") rideId: Int,
    @Header("Authorization") token: String
): Response<EndRideResponseDto>

// In DriverCurrentRideFragment
private fun endRide() {
    currentRideId?.let { rideId ->
        lifecycleScope.launch {
            val api = ApiClient.create(RideApi::class.java)
            val response = api.endRide(rideId, "Bearer ${SessionManager.token}")
            // Navigate back to driver home
        }
    }
}
```

#### 4. Implement Payment Flow

```kotlin
// Add to RideApi
@POST("api/rides/{rideId}/payment-success")
suspend fun markPaymentSuccess(
    @Path("rideId") rideId: Int,
    @Body body: PaymentRequestDto,
    @Header("Authorization") token: String
): Response<Unit>

// Create PaymentFragment
class PaymentFragment : Fragment() {
    private fun processPayment(method: String) {
        lifecycleScope.launch {
            val api = ApiClient.create(RideApi::class.java)
            api.markPaymentSuccess(
                rideId = currentRideId!!,
                body = PaymentRequestDto(method = method),
                token = "Bearer ${SessionManager.token}"
            )
        }
    }
}
```

### Priority 2: Architecture Improvements

#### 1. Implement MVVM Architecture

```
ui/
├── auth/
│   ├── ChooseRoleFragment.kt
│   ├── ChooseRoleViewModel.kt      # NEW
│   └── AuthRepository.kt            # NEW
├── home/
│   ├── HomeFragment.kt
│   ├── HomeViewModel.kt             # NEW
│   └── RideRepository.kt            # NEW
```

**Benefits:**
- Separation of business logic from UI
- Easier testing
- Better state management
- Lifecycle-aware

#### 2. Add Repository Pattern

```kotlin
// RideRepository.kt
class RideRepository(
    private val rideApi: RideApi,
    private val webSocketManager: WebSocketManager
) {
    suspend fun createRide(request: CreateRideRequestDto): Result<CreateRideResponseDto>
    fun subscribeToRideStatus(userId: Int): Flow<RideCardResponse>
    fun subscribeToDriverLocation(rideId: Int): Flow<LocationDto>
}
```

#### 3. Centralize WebSocket Management

```kotlin
// WebSocketManager.kt
class WebSocketManager(
    private val sessionManager: SessionManager
) {
    private val stompClient: StompClient
    
    fun connect()
    fun disconnect()
    fun subscribeToRideRequests(driverId: Int): Flow<RideRequest>
    fun subscribeToRideStatus(userId: Int): Flow<RideStatusUpdate>
    fun sendRideResponse(rideId: Int, accepted: Boolean)
}
```

### Priority 3: Code Quality

#### 1. Add Dependency Injection (Hilt)

```kotlin
// Application.kt
@HiltAndroidApp
class UberApplication : Application()

// ApiModule.kt
@Module
@InstallIn(SingletonComponent::class)
object ApiModule {
    @Provides
    @Singleton
    fun provideRideApi(): RideApi {
        // Retrofit setup
    }
}

// HomeViewModel.kt
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val rideRepository: RideRepository
) : ViewModel()
```

#### 2. Add Configuration Management

```kotlin
// Config.kt
object Config {
    const val BASE_URL = BuildConfig.BASE_URL
    const val WS_URL = BuildConfig.WS_URL
}

// build.gradle.kts
android {
    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:9090/\"")
            buildConfigField("String", "WS_URL", "\"ws://10.0.2.2:9090/ws\"")
        }
    }
}
```

#### 3. Improve Error Handling

```kotlin
// ErrorHandler.kt
sealed class ApiError {
    object NetworkError : ApiError()
    data class HttpError(val code: Int, val message: String) : ApiError()
    data class UnknownError(val throwable: Throwable) : ApiError()
}

// Extension function
suspend fun <T> safeApiCall(apiCall: suspend () -> Response<T>): Result<T> {
    return try {
        val response = apiCall()
        if (response.isSuccessful) {
            Result.success(response.body()!!)
        } else {
            Result.failure(ApiError.HttpError(response.code(), response.message()))
        }
    } catch (e: Exception) {
        Result.failure(ApiError.NetworkError)
    }
}
```

### Priority 4: Testing

#### 1. Unit Tests

```kotlin
// HomeViewModelTest.kt
@Test
fun `createRide should emit success when API call succeeds`() {
    // Test ViewModel logic
}

// RideRepositoryTest.kt
@Test
fun `getRideCard should return ride card from API`() {
    // Test repository
}
```

#### 2. Integration Tests

```kotlin
// RideFlowTest.kt
@Test
fun `complete ride flow from booking to payment`() {
    // Test complete user journey
}
```

---

## Complete Flow Documentation

### Customer Complete Flow

```
1. Launch App
   └─► ChooseRoleFragment
       └─► Select "USER"
           └─► LoginFragment

2. Login
   └─► POST /api/auth/login
       └─► Save JWT token
           └─► Navigate to HomeFragment

3. Book Ride
   └─► HomeFragment
       ├─► Select pickup (current location or map)
       ├─► Select drop (map tap or Places search)
       ├─► Calculate route (Google Directions API)
       ├─► Show fare estimate
       ├─► Select ride type (MINI/SEDAN/SUV)
       └─► POST /api/rides
           └─► Receive {rideId, estimatedFare, otpCode}

4. Wait for Driver
   └─► Subscribe to /queue/ride-status{userId}
       └─► Receive driver assignment
           └─► Display RideCard with driver info
               └─► Subscribe to /topic/ride/{rideId}/location
                   └─► Track driver location on map

5. Start Ride
   └─► Enter OTP code
       └─► POST /api/rides/start
           └─► Status: STARTED
               └─► Show ride in progress

6. Ride in Progress
   └─► Continuously receive driver location
       └─► Update driver marker on map

7. End Ride
   └─► Driver ends ride
       └─► Receive status update: COMPLETED
           └─► Show final fare

8. Payment
   └─► Select payment method (CASH/CARD/UPI)
       └─► POST /api/rides/{rideId}/payment-success
           └─► Payment complete
               └─► Return to HomeFragment
```

### Driver Complete Flow

```
1. Launch App
   └─► ChooseRoleFragment
       └─► Select "DRIVER"
           └─► LoginFragment

2. Login
   └─► POST /api/auth/login
       └─► Save JWT token
           └─► Navigate to DriverHomeFragment

3. Go Online
   └─► DriverHomeFragment
       ├─► Toggle online switch
       ├─► POST /drivers/{driverId}/status?status=ONLINE
       └─► Connect to WebSocket
           └─► Subscribe to /queue/ride-requests{driverId}

4. Receive Ride Request
   └─► WebSocket message received
       └─► Display ride request card
           ├─► Show pickup/drop locations
           ├─► Show estimated fare
           └─► Driver can Accept or Reject

5. Accept Ride
   └─► SEND /app/driver/ride/response
       └─► {rideId, accepted: true, driverId}
           └─► Navigate to DriverCurrentRideFragment

6. Current Ride Screen
   └─► DriverCurrentRideFragment
       ├─► Display ride details
       ├─► Start location tracking
       │   └─► PUT /api/driver-location/{driverId} (every 5s)
       └─► Actions:
           ├─► Start ride (OTP verification)
           │   └─► POST /api/rides/start
           └─► End ride
               └─► POST /api/rides/{rideId}/end

7. During Ride
   └─► Continuously send location
       └─► Backend broadcasts to customer

8. End Ride
   └─► POST /api/rides/{rideId}/end
       └─► Status: COMPLETED
           └─► Navigate back to DriverHomeFragment
```

---

## WebSocket Topics Reference

### Customer Topics

| Topic | Purpose | When to Subscribe |
|-------|---------|-------------------|
| `/queue/ride-status{userId}` | Receive ride status updates | After creating ride |
| `/topic/ride/{rideId}/location` | Receive driver location | After driver assigned |

### Driver Topics

| Topic | Purpose | When to Subscribe |
|-------|---------|-------------------|
| `/queue/ride-requests{driverId}` | Receive ride requests | When driver goes online |
| `/queue/ride-status{driverId}` | Receive ride status updates | After accepting ride |

### Message Endpoints (Where to Send)

| Endpoint | Purpose | Sender |
|----------|---------|--------|
| `/app/driver/ride/response` | Accept/reject ride | Driver |
| `/app/driver/location` | Send location update | Driver |

---

## Summary

### Current State

- **Structure**: Moderate (6/10) - Basic organization but missing modern patterns
- **Backend Integration**: Partial (60%) - Core features work but missing critical flows
- **WebSocket**: Incomplete - Driver side partially works, customer side uses polling
- **Code Quality**: Basic - Works but needs refactoring

### Key Gaps

1. ❌ Customer WebSocket subscription (using polling)
2. ❌ Ride start flow (OTP verification)
3. ❌ Ride end flow
4. ❌ Payment flow
5. ❌ Driver location tracking during ride
6. ❌ No MVVM architecture
7. ❌ No dependency injection
8. ❌ Limited error handling

### Recommended Next Steps

1. **Immediate**: Fix WebSocket implementation for customers
2. **Short-term**: Implement missing ride flows (start, end, payment)
3. **Medium-term**: Refactor to MVVM + Repository pattern
4. **Long-term**: Add dependency injection, improve testing, enhance error handling

---

## References

- Backend API Documentation: `API.md`
- Backend Architecture: `ARCHITECTURE.md`
- WebSocket Architecture: `WEBSOCKET_ARCHITECTURE.md`
- Backend Setup: `SETUP.md`
