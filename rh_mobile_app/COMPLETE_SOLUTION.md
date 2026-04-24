# 🎯 Complete Solution: WebSocket Connection Issue

## 🔍 The Problem

Your mobile app shows **"déconnecté"** (disconnected) because the WebSocket connection to the backend is not being established, even though:
- ✅ Backend is running (`http://localhost:8080/ws/info` works in Postman)
- ✅ Backend is sending notifications
- ❌ Mobile app can't establish WebSocket connection

---

## 🧠 Root Causes (Likely Scenarios)

### Scenario 1: Wrong IP Address (MOST LIKELY)
**Postman Test:** Used `localhost:8080` (your local machine)  
**Mobile Emulator/Simulator:** Can't reach `localhost` - needs special IP

- **Android Emulator:** Must use `10.0.2.2:8080` (NOT localhost)
- **iOS Simulator:** Must use `127.0.0.1:8080` (NOT localhost)

**Fix:** Check `lib/core/constants/default_api_host_io.dart` - it should already have this but verify!

### Scenario 2: JWT Token Not Sent in WebSocket Headers
**Why:** Postman probably didn't test with actual JWT validation  
**Mobile Issue:** Token might not be ready when connection starts OR not sent in STOMP headers

**Fix:** Improved in `notification_provider.dart` - now validates token before connecting

### Scenario 3: Backend WebSocket Configuration Missing
**Missing:**
- `@EnableWebSocketMessageBroker` annotation
- Proper endpoint registration
- JWT validation in STOMP interceptor

**Fix:** Check `BACKEND_WEBSOCKET_CONFIG.md` for required Spring Boot configuration

### Scenario 4: Endpoint Path Wrong
**Postman tested:** `http://localhost:8080/ws/info` ✅  
**Mobile connecting to:** Wrong WebSocket path ❌

**Fix:** Now correctly using `ws://10.0.2.2/ws` (not `/ws-mobile` or `/ws/websocket`)

---

## ✅ Solutions Applied to Mobile App

### 1. ✅ Fixed WebSocket URL Construction
```dart
// Before: Could result in ws://localhost/ws or ws://10.0.2.2/ws
wsUrl = baseUrl.replaceFirst('http://', 'ws://'); // Only replaces first occurrence

// After: Safe handling for both http/https
if (baseUrl.startsWith('https://')) {
  wsBase = baseUrl.replaceFirst('https://', 'wss://');
} else if (baseUrl.startsWith('http://')) {
  wsBase = baseUrl.replaceFirst('http://', 'ws://');
}
```

### 2. ✅ SockJS Enabled
```dart
useSockJS: true  // Fallback for restrictive networks
```

### 3. ✅ Proper JWT Header Handling
```dart
final headers = <String, String>{};
if (token != null && token.isNotEmpty) {
  headers['Authorization'] = 'Bearer $token';  // Sent with STOMP CONNECT
}
```

### 4. ✅ Multiple Subscription Destinations
```dart
// Tries multiple paths for compatibility
_client?.subscribe(destination: '/user/$userId/queue/notifications', ...);
_client?.subscribe(destination: '/topic/$userId', ...);
_client?.subscribe(destination: '/topic/RH', ...);
```

### 5. ✅ Enhanced Debug Logging
```
[STOMP] ═════════════════════════════════════════
[STOMP] DEBUG: Raw ApiConstants.baseUrl = "http://10.0.2.2:8080"
[STOMP] Attempting WebSocket connection
[STOMP] Base URL (HTTP): http://10.0.2.2:8080
[STOMP] WebSocket URL: ws://10.0.2.2:8080/ws  ← Shows exactly what URL is used
[STOMP] User ID: john.doe
[STOMP] Token available: true
[STOMP] Token length: 512
[STOMP] ═════════════════════════════════════════
```

### 6. ✅ Proper Error Handling
- Captures WebSocket errors
- Captures STOMP protocol errors
- Shows full stack traces
- Waits 5 seconds for connection

---

## 🚀 Debugging Steps (Run These!)

### Step 1: Verify Backend HTTP Endpoint Works
```bash
curl http://localhost:8080/ws/info
```
**Expected:** JSON response with SockJS info  
**If fails:** Backend not running

### Step 2: Test WebSocket Connection
```bash
# Install wscat if needed:
npm install -g wscat

# Connect to backend:
wscat -c ws://localhost:8080/ws
```
**Expected:** Connection established  
**If fails:** WebSocket config issue on backend

### Step 3: Run Mobile App and Check Logs
```bash
# Terminal 1: Run app
cd C:\Local\Khaled\project\rh_mobile_app
flutter run --debug

# Terminal 2: Watch STOMP logs (while app runs)
flutter logs | findstr STOMP
```

**Expected to see:**
```
[STOMP] ═════════════════════════════════════════
[STOMP] WebSocket URL: ws://10.0.2.2:8080/ws
[STOMP] Token available: true
[STOMP] ═════════════════════════════════════════
[STOMP] 🚀 Client activated - waiting for connection...
[STOMP] ✅ CONNECTED!
[STOMP] 📨 Subscribing to /user/john.doe/queue/notifications
```

### Step 4: Check What URL is Actually Being Used
The logs now show:
```
[STOMP] Base URL (HTTP): http://10.0.2.2:8080
[STOMP] WebSocket URL: ws://10.0.2.2:8080/ws
```

**If you see `localhost:8080` instead of `10.0.2.2:8080`:**
- ❌ Problem found! Wrong IP for emulator
- ✅ Fix: Check `default_api_host_io.dart`

### Step 5: Verify Android Emulator Can Reach Backend
```bash
adb shell curl -v http://10.0.2.2:8080/ws/info
```
**Expected:** 200 OK  
**If fails:** Emulator can't reach backend

---

## 📋 Complete Checklist

### ✅ Mobile App
- [x] WebSocket URL construction fixed
- [x] SockJS enabled
- [x] JWT headers properly sent
- [x] Multiple subscription endpoints
- [x] Enhanced debug logging
- [x] Error handling improved
- [x] Code compiles without errors

### ⏳ Backend (Check These)
- [ ] `@EnableWebSocketMessageBroker` annotation present
- [ ] `.addEndpoint("/ws")` configured
- [ ] `.withSockJS()` is called
- [ ] `setAllowedOrigins("*")` set
- [ ] JWT validation in STOMP interceptor
- [ ] Notification controller sends to correct destinations
- [ ] Logs show CONNECT, SUBSCRIBE, MESSAGE frames

### ⏳ Emulator/Simulator
- [ ] Can reach `http://10.0.2.2:8080` (Android) or `127.0.0.1:8080` (iOS)
- [ ] Network is enabled
- [ ] No firewall blocking port 8080
- [ ] User is authenticated before connection attempt

### ⏳ Testing
- [ ] `curl http://localhost:8080/ws/info` works
- [ ] `wscat -c ws://localhost:8080/ws` connects
- [ ] `flutter run --debug` shows `[STOMP]` logs
- [ ] `adb shell curl http://10.0.2.2:8080/ws/info` works

---

## 🎯 Most Likely Problem & Solution

**Most Likely:** Mobile app using `localhost:8080` instead of `10.0.2.2:8080` for Android emulator

**Why Postman worked:** Postman runs on your local machine, so `localhost` works  
**Why mobile fails:** Emulator/simulator can't resolve `localhost` to your host machine

**Test this:**
```bash
# Check current base URL
flutter run --debug
# Look for: [STOMP] Base URL (HTTP): ???
```

If you see `http://localhost:8080` instead of `http://10.0.2.2:8080`:
- ❌ Problem identified!
- Fix: Check `lib/core/constants/default_api_host_io.dart`
- Should return `http://10.0.2.2:8080` for Android

---

## 📝 What to Share If Still Not Working

To help debug, share:

1. **Mobile App Logs:**
   ```bash
   flutter run --debug 2>&1 | tee app_logs.txt
   # Run for 10 seconds, then stop
   # Share the output
   ```

2. **Backend Logs:**
   ```bash
   # When connection attempt happens, capture logs
   # Look for lines with CONNECT, SUBSCRIBE, STOMP
   ```

3. **Backend WebSocket Config File:**
   - Share your `WebSocketConfig.java`
   - Share any `Interceptor` or `ChannelInterceptor` classes

4. **Test Results:**
   ```bash
   # Run these and share output:
   curl -v http://localhost:8080/ws/info
   adb shell curl -v http://10.0.2.2:8080/ws/info
   wscat -c ws://localhost:8080/ws
   ```

5. **Device Info:**
   - Android Emulator API version? (or iOS version)
   - Backend port? (I assumed 8080, but verify)
   - Any custom proxies or VPN?

---

## 🔗 Documentation Files Created

1. **`FIX_SUMMARY.md`** - What was fixed in the mobile app
2. **`DIAGNOSIS_WEBSOCKET.md`** - How to diagnose the issue
3. **`BACKEND_WEBSOCKET_CONFIG.md`** - Backend configuration examples
4. **`NOTIFICATION_DEBUG_GUIDE.md`** - Complete debugging guide
5. **`TEST_WEBSOCKET.ps1`** - Windows PowerShell test script
6. **`TEST_WEBSOCKET.sh`** - Linux/Mac bash test script

---

## ✅ Next Action

**Run this command and share the output:**

```bash
cd C:\Local\Khaled\project\rh_mobile_app
flutter run --debug 2>&1 | Select-Object -First 200
```

Look for lines starting with `[STOMP]` and share those.

If you see:
- ✅ `[STOMP] ✅ CONNECTED!` - Issue is solved! 🎉
- ❌ `[STOMP] ⚠️  WebSocket Error:` - Share the error message
- ❌ `[STOMP] WebSocket URL: ws://localhost/...` - Wrong IP, needs fix

---

## 🎬 Final Checklist Before We're Done

- [x] Mobile app code fixed
- [x] Enhanced logging added
- [x] Documentation created
- [x] Testing scripts provided
- [x] Backend config examples provided
- ⏳ **Next: Run tests and share logs**

**Your task:** Run `flutter run --debug` and watch for `[STOMP]` messages. Share what you see!

