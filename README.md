# Uber Frontend - Android Application

Android frontend application for the Uber Clone ride-sharing platform.

## 📚 Documentation

This project includes comprehensive documentation aligned with the backend architecture:

- **[FRONTEND_ARCHITECTURE.md](FRONTEND_ARCHITECTURE.md)** - Complete architecture documentation
  - Current structure overview
  - Component details
  - Backend integration status
  - Recommended improvements

- **[FLOW_DOCUMENTATION.md](FLOW_DOCUMENTATION.md)** - Complete flow documentation
  - Authentication flow
  - Customer ride booking flow
  - Driver ride management flow
  - WebSocket communication flow
  - Ride lifecycle states

- **[STRUCTURE_ASSESSMENT.md](STRUCTURE_ASSESSMENT.md)** - Structure assessment and recommendations
  - Current vs ideal structure comparison
  - Detailed assessment by category
  - Priority-based recommendations
  - Migration path

## 🏗️ Current Structure

```
com.example.uberfrontend/
├── MainActivity.kt              # Single activity host
├── network/                     # Network layer
│   ├── ApiClient.kt            # Retrofit setup
│   ├── ApiInterfaces.kt        # API endpoints
│   ├── dto/                    # Data Transfer Objects
│   └── sockets/                # WebSocket clients
├── session/                     # Session management
│   └── SessionManager.kt       # JWT token storage
└── ui/                          # UI layer
    ├── auth/                   # Authentication screens
    ├── home/                   # Main screens
    ├── drop/                   # Location selection
    └── profile/               # Profile screen
```

## 📊 Current Status

### Structure Rating: **6/10 (Moderate)**

**Strengths:**
- ✅ Clear package organization
- ✅ Navigation Component integration
- ✅ Basic backend integration working
- ✅ Google Maps integration

**Areas for Improvement:**
- ⚠️ No MVVM architecture (business logic in fragments)
- ⚠️ Incomplete WebSocket implementation (customer uses polling)
- ⚠️ Missing critical flows (ride start, end, payment)
- ⚠️ No dependency injection
- ⚠️ Limited error handling

### Backend Integration: **5/10 (Partial)**

**Implemented:**
- ✅ Authentication (login, signup)
- ✅ Ride creation
- ✅ Basic driver assignment (using polling)

**Missing:**
- ❌ Ride start flow (OTP verification)
- ❌ Ride end flow
- ❌ Payment flow
- ❌ Customer WebSocket subscription
- ❌ Driver location tracking during ride

## 🚀 Quick Start

### Prerequisites

- Android Studio Arctic Fox or later
- Android SDK 24+
- Kotlin 1.8+
- Backend server running on `http://localhost:9090`

### Setup

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd UberFrontend
   ```

2. **Configure API Base URL**
   - Update `ApiClient.kt` with your backend URL
   - For Android emulator: `http://10.0.2.2:9090/`
   - For physical device: Use your computer's IP address

3. **Add Google Maps API Key**
   - Get API key from [Google Cloud Console](https://console.cloud.google.com/)
   - Add to `AndroidManifest.xml`:
     ```xml
     <meta-data
         android:name="com.google.android.geo.API_KEY"
         android:value="YOUR_API_KEY"/>
     ```

4. **Build and Run**
   ```bash
   ./gradlew assembleDebug
   ```

## 🔄 Application Flows

### Customer Flow

1. **Launch** → Choose Role (USER/DRIVER)
2. **Login** → Enter credentials
3. **Home** → Select pickup & drop locations
4. **Request Ride** → Create ride request
5. **Wait for Driver** → Receive driver assignment (⚠️ currently using polling)
6. **Track Driver** → See driver location (❌ not implemented)
7. **Start Ride** → Enter OTP (❌ not implemented)
8. **Ride in Progress** → Track driver (❌ not implemented)
9. **End Ride** → Driver ends ride (❌ not implemented)
10. **Payment** → Select payment method (❌ not implemented)

### Driver Flow

1. **Launch** → Choose Role (DRIVER)
2. **Login** → Enter credentials
3. **Go Online** → Toggle online status
4. **Receive Request** → Get ride request via WebSocket
5. **Accept Ride** → Accept or reject
6. **Current Ride** → Navigate to ride screen
7. **Start Location Tracking** → Send location updates (⚠️ partial)
8. **Start Ride** → Verify OTP (❌ not implemented)
9. **End Ride** → Complete ride (❌ not implemented)
10. **Return Home** → Back to driver home

## 🔌 Backend Integration

### API Endpoints

| Endpoint | Status | Implementation |
|----------|--------|----------------|
| `POST /api/auth/login` | ✅ | `LoginFragment.kt` |
| `POST /api/auth/signup` | ✅ | `SignupFragment.kt` |
| `POST /api/rides` | ✅ | `HomeFragment.kt` |
| `GET /api/rides/card` | ✅ | `HomeFragment.kt` (polling) |
| `POST /api/rides/start` | ❌ | Not implemented |
| `POST /api/rides/{id}/end` | ❌ | Not implemented |
| `POST /api/rides/{id}/payment-success` | ❌ | Not implemented |
| `POST /drivers/{id}/status` | ⚠️ | `DriverHomeFragment.kt` |
| `PUT /api/driver-location/{id}` | ⚠️ | Partial |

### WebSocket Topics

| Topic | Status | Implementation |
|-------|--------|----------------|
| `/queue/ride-requests{driverId}` | ⚠️ | `DriverHomeFragment.kt` (path may be wrong) |
| `/queue/ride-status{custId}` | ❌ | Using polling instead |
| `/queue/ride-status{driverId}` | ❌ | Not implemented |
| `/topic/ride/{rideId}/location` | ❌ | Not implemented |

## 🛠️ Technology Stack

- **Language**: Kotlin
- **UI Framework**: Android Jetpack (Fragments, Navigation Component)
- **Networking**: Retrofit 2, OkHttp
- **WebSocket**: STOMP Client (ua.naiksoftware.stomp)
- **Maps**: Google Maps SDK, Places API
- **Location**: Google Play Services Location API
- **Architecture**: Fragment-based (no MVVM yet)
- **Dependency Injection**: Manual (no Dagger/Hilt)

## 📋 Known Issues

1. **Customer WebSocket**: Uses polling instead of WebSocket subscription
2. **Ride Start**: OTP verification flow not implemented
3. **Ride End**: Ride completion flow not implemented
4. **Payment**: Payment flow not implemented
5. **Driver Location**: Location tracking during ride not fully integrated
6. **WebSocket Topics**: Topic paths may be incorrect

## 🎯 Recommended Next Steps

### Priority 1: Complete Backend Integration

1. Fix customer WebSocket subscription (replace polling)
2. Implement ride start flow (OTP verification)
3. Implement ride end flow
4. Implement payment flow

### Priority 2: Architecture Improvements

1. Implement MVVM architecture
2. Add dependency injection (Hilt)
3. Implement Repository pattern
4. Centralize error handling

### Priority 3: Code Quality

1. Add configuration management
2. Improve session management
3. Add unit tests
4. Add integration tests

## 📖 Backend Documentation

This frontend is designed to work with the backend documented in:
- `d:\uber_CopyA\uber\API.md` - API endpoint reference
- `d:\uber_CopyA\uber\ARCHITECTURE.md` - Backend architecture
- `d:\uber_CopyA\uber\WEBSOCKET_ARCHITECTURE.md` - WebSocket details

## 🤝 Contributing

When contributing, please:

1. Follow the existing code structure
2. Complete backend integration for new features
3. Update documentation for changes
4. Test on both emulator and physical device

## 📝 License

[Add your license here]

## 📞 Support

For issues or questions:
1. Check the documentation files
2. Review backend API documentation
3. Check WebSocket architecture documentation

---

**Last Updated**: [Current Date]
**Version**: 1.0.0
**Status**: Development (Incomplete Backend Integration)
