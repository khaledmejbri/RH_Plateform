# 🎯 Root Cause Found: Cookie Requirement

## ✅ What Your Backend Response Tells Us

```json
{
    "entropy": 272570011,
    "origins": ["*:*"],
    "cookie_needed": true,      ← ⚠️ THIS IS KEY!
    "websocket": true
}
```

### ✅ Good News
- ✅ WebSocket is enabled (`"websocket": true`)
- ✅ All origins allowed (`"origins": ["*:*"]`)
- ✅ SockJS is working (`/ws/info` endpoint responds)
- ✅ Backend is properly configured

### ⚠️ The Problem
- ⚠️ `"cookie_needed": true` - Cookies are REQUIRED
- This means SockJS needs session cookies to work
- The `stomp_dart_client` might not be handling cookies properly

---

## 🔧 Solution 1: Disable SockJS and Use Raw WebSocket (RECOMMENDED)

The easiest fix is to disable SockJS and use raw WebSocket. Since both HTTP and WebSocket work on your backend, try this:

Edit `lib/core/notifications/notification_provider.dart`:

```dart
_client = StompClient(
  config: StompConfig(
    url: wsUrl,
    stompConnectHeaders: headers,
    connectionTimeout: const Duration(seconds: 30),
    useSockJS: false,  // ← Change from: true → false
    onConnect: (frame) {
      // ... rest of code
    },
    // ... rest of config
  ),
);
```

**Why this works:**
- Raw WebSocket doesn't require SockJS session cookies
- WebSocket protocol sends Authorization headers directly
- More lightweight and fewer intermediaries

---

## 🔧 Solution 2: Force Connection Without Cookies

If Solution 1 doesn't work, the issue might be that the token isn't being sent correctly. Try this enhanced version:

```dart
Future<void> connect(String userId) async {
  try {
    final token = await _storage.readAccessToken();

    final baseUrl = ApiConstants.baseUrl;
    debugPrint('[STOMP] ═════════════════════════════════════════');
    debugPrint('[STOMP] DEBUG: Raw ApiConstants.baseUrl = "$baseUrl"');

    String wsBase;
    if (baseUrl.startsWith('https://')) {
      wsBase = baseUrl.replaceFirst('https://', 'wss://');
    } else if (baseUrl.startsWith('http://')) {
      wsBase = baseUrl.replaceFirst('http://', 'ws://');
    } else {
      wsBase = baseUrl;
    }

    final wsUrl = '$wsBase/ws';

    debugPrint('[STOMP] ═════════════════════════════════════════');
    debugPrint('[STOMP] Attempting WebSocket connection');
    debugPrint('[STOMP] Base URL (HTTP): $baseUrl');
    debugPrint('[STOMP] WebSocket URL: $wsUrl');
    debugPrint('[STOMP] User ID: $userId');
    debugPrint('[STOMP] Token available: ${token != null && token.isNotEmpty}');
    debugPrint('[STOMP] Token length: ${token?.length ?? 0}');
    debugPrint('[STOMP] ═════════════════════════════════════════');

    final headers = <String, String>{};
    if (token != null && token.isNotEmpty) {
      headers['Authorization'] = 'Bearer $token';
    }

    _client = StompClient(
      config: StompConfig(
        url: wsUrl,
        stompConnectHeaders: headers,
        connectionTimeout: const Duration(seconds: 30),
        useSockJS: false,  // ← TRY WITHOUT SOCKJS FIRST
        onConnect: (frame) {
          debugPrint('[STOMP] ✅ CONNECTED!');
          debugPrint('[STOMP] Server response: ${frame.body}');
          state = state.copyWith(connected: true);

          // Subscribe to user-specific queue
          debugPrint('[STOMP] 📨 Subscribing to /user/$userId/queue/notifications');
          _client?.subscribe(
            destination: '/user/$userId/queue/notifications',
            callback: _handleFrame,
            headers: {'ack': 'auto'},
          );

          // Also subscribe to topic
          debugPrint('[STOMP] 📨 Subscribing to /topic/$userId');
          _client?.subscribe(
            destination: '/topic/$userId',
            callback: _handleFrame,
            headers: {'ack': 'auto'},
          );

          // Subscribe to broadcast
          debugPrint('[STOMP] 📨 Subscribing to /topic/RH');
          _client?.subscribe(
            destination: '/topic/RH',
            callback: _handleFrame,
            headers: {'ack': 'auto'},
          );
        },
        onDisconnect: (_) {
          debugPrint('[STOMP] ❌ DISCONNECTED');
          state = state.copyWith(connected: false);
        },
        onWebSocketError: (dynamic error) {
          debugPrint('[STOMP] ⚠️  WebSocket Error: $error');
          debugPrint('[STOMP] Error type: ${error.runtimeType}');
          state = state.copyWith(connected: false);
        },
        onStompError: (StompFrame frame) {
          debugPrint('[STOMP] ⚠️  STOMP Protocol Error');
          debugPrint('[STOMP] Command: ${frame.command}');
          debugPrint('[STOMP] Headers: ${frame.headers}');
          debugPrint('[STOMP] Body: ${frame.body}');
          state = state.copyWith(connected: false);
        },
        beforeConnect: () async {
          debugPrint('[STOMP] 🔄 beforeConnect - preparing connection');
        },
        onDebugMessage: (String message) {
          debugPrint('[STOMP] 🔍 DEBUG: $message');
        },
      ),
    );

    _client!.activate();
    debugPrint('[STOMP] 🚀 Client activated - waiting for connection...');

    // Give it time to connect
    await Future.delayed(const Duration(seconds: 5));

    if (!state.connected) {
      debugPrint('[STOMP] ⚠️  Connection not established after 5 seconds');
    }
  } catch (e, stackTrace) {
    debugPrint('[STOMP] ❌ Fatal Connection Error: $e');
    debugPrint('[STOMP] Stack: $stackTrace');
    state = state.copyWith(connected: false);
  }
}
```

---

## 🔧 Solution 3: If Raw WebSocket Doesn't Work, Use SockJS with Cookie Handling

If raw WebSocket doesn't work, then we need SockJS but with proper cookie support.

**Update to use SockJS but add login credentials:**

```dart
final headers = <String, String>{
  'Authorization': 'Bearer $token',
};

// Add STOMP login/passcode as fallback
// Some backends use these instead of Authorization header
const stompHeaders = <String, String>{
  'login': 'guest',  // Or extract from token
  'passcode': 'guest',  // Or use token
};

_client = StompClient(
  config: StompConfig(
    url: wsUrl,
    stompConnectHeaders: headers,  // Authorization header
    connectionTimeout: const Duration(seconds: 30),
    useSockJS: true,  // ← Use SockJS with cookie support
    onConnect: (frame) {
      // ... rest of code
    },
    // ... rest of config
  ),
);
```

---

## 🚀 What to Do NOW

### Step 1: Try Raw WebSocket First (Simplest)

Change this line in `notification_provider.dart`:

```dart
useSockJS: true,   // ← Change to: false
```

Then test:
```bash
flutter run --debug
flutter logs | findstr STOMP
```

**If you see `✅ CONNECTED!` - PROBLEM SOLVED!** 🎉

**If still fails, go to Step 2...**

---

### Step 2: Verify Backend Configuration

Your backend might need cookie configuration. Check your Spring Boot `application.properties` or `application.yml`:

```properties
# Should have:
server.servlet.session.persistent=false
server.servlet.session.timeout=30m
```

Or if using `application.yml`:
```yaml
server:
  servlet:
    session:
      persistent: false
      timeout: 30m
```

---

### Step 3: Test Backend's WebSocket Directly

```bash
# Test raw WebSocket with Authentication
wscat -c ws://localhost:8080/ws

# Once connected, send STOMP CONNECT with auth:
CONNECT
Authorization:Bearer YOUR_JWT_TOKEN
login:guest
passcode:guest

```

If this works, then the issue is how the Dart client is sending headers.

---

## 📊 Diagnosis Matrix

| Test | Expected | Actual | Status |
|------|----------|--------|--------|
| `curl http://localhost:8080/ws/info` | 200 with cookie_needed:true | ✅ Works | ✅ OK |
| `wscat -c ws://localhost:8080/ws` | Connected | ? | ⏳ Test this |
| `adb shell curl http://10.0.2.2:8080/ws/info` | 200 | ? | ⏳ Test this |
| `flutter run` with `useSockJS: false` | Logs show CONNECTED | ? | ⏳ Test this |
| `flutter run` with `useSockJS: true` | Logs show CONNECTED | ? | ⏳ Test this |

---

## 🎯 Most Likely Solution

**Change 1 line:**

```dart
// File: lib/core/notifications/notification_provider.dart
// Line ~80 (inside StompConfig)

useSockJS: false,  // ← Change from: true
```

**Then test:**
```bash
cd C:\Local\Khaled\project\rh_mobile_app
flutter run --debug
```

**Watch logs for:**
```
[STOMP] ✅ CONNECTED!
```

---

## 🔗 Understanding the Issue

**Why Postman worked but mobile app didn't:**
1. Postman's HTTP request → `localhost:8080/ws/info` → Backend responds
2. Mobile app's STOMP+SockJS → Tries to establish session with cookies
3. Cookies might not be sent by `stomp_dart_client`
4. Backend rejects connection because session cookie missing

**How changing `useSockJS: false` fixes it:**
- Raw WebSocket doesn't use cookies
- Uses HTTP Upgrade header instead
- Authorization header is sent directly in WebSocket frame
- No need for session management

---

## 📝 What to Share When You Run Tests

1. **Run this:**
   ```bash
   flutter run --debug
   ```

2. **Look for these logs:**
   ```
   [STOMP] WebSocket URL: ws://10.0.2.2:8080/ws
   [STOMP] Token available: true
   [STOMP] 🚀 Client activated
   [STOMP] ✅ CONNECTED!   ← THIS IS WHAT WE WANT TO SEE
   ```

3. **If you see these errors instead:**
   ```
   [STOMP] ⚠️  WebSocket Error: ...
   [STOMP] ⚠️  Connection not established after 5 seconds
   ```
   
   Share the full error message.

---

## ✅ Action Plan

1. ✅ **Already done:** Added enhanced logging to show exact URL and token status
2. ⏳ **Next step:** Change `useSockJS: true` → `useSockJS: false`
3. ⏳ **Test:** Run `flutter run --debug` and watch for CONNECTED message
4. ⏳ **Verify:** Try sending a test notification from backend
5. ⏳ **Celebrate:** If it works! 🎉

---

**TL;DR:** The backend requires cookies for SockJS but the Dart client might not handle them. **Solution: Disable SockJS and use raw WebSocket instead.**

Try changing `useSockJS: true` to `useSockJS: false` and test again!

