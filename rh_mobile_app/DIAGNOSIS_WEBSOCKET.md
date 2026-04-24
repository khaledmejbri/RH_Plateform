# Diagnostic: WebSocket Connection Issue

## 🔍 What We Know

✅ **Backend is configured correctly:**
- `http://localhost:8080/ws/info` returns 200 (SockJS info endpoint works)
- This means `/ws` endpoint exists and SockJS is enabled

❌ **But mobile app still shows "déconnecté":**
- This suggests the WebSocket handshake is failing
- OR the connection is being made but immediately closed

---

## 🔧 Diagnostic Steps

### 1. Check Backend Logs During Connection Attempt

When you run the mobile app, look at your Spring Boot console logs for:

**Expected (successful):**
```
2026-04-13 10:30:45.123 INFO  o.s.w.s.m.WebSocketAnnotationMethodMessageHandler
  : Mapped handler method: ... 
2026-04-13 10:30:45.124 INFO  StompSubProtocolHandler
  : Processing CONNECT frame from user: ...
```

**Error (connection fails):**
```
WARN StompSubProtocolHandler : STOMP CONNECT failed
ERROR SecurityContext : Authentication failed
ERROR StompSubProtocolHandler : Error handling STOMP frame
```

**Also look for:**
- Is the `CONNECT` frame even being received?
- Is JWT validation failing?
- Are there any CORS or auth errors?

---

### 2. Verify Endpoint Configuration

Check if your Spring Boot WebSocket config is correct:

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins("*")
                .withSockJS();  // ✅ SockJS must be enabled
    }
}
```

**Key points:**
- ✅ Endpoint is `/ws` (not `/ws-mobile` or `/ws/websocket`)
- ✅ `withSockJS()` is called
- ✅ `setAllowedOrigins("*")` allows all origins

---

### 3. Check if App is Using Correct IP Address

Your app uses:
```
Android Emulator: http://10.0.2.2:8080
iOS Simulator:    http://127.0.0.1:8080
Local Machine:    http://localhost:8080
```

**But the issue is:** The Postman request you made (`http://localhost:8080/ws/info`) was from your **local machine**, not from the emulator!

**Run this test:**

#### Option A: Test from Android Emulator
```bash
# On Android Emulator terminal
adb shell
curl http://10.0.2.2:8080/ws/info

# If this fails: backend is unreachable from emulator
# If this works: network is OK, but WebSocket handshake might be failing
```

#### Option B: Test from iOS Simulator
```bash
# On iOS Simulator
xcrun simctl ssh booted 'curl http://127.0.0.1:8080/ws/info'

# Or use Safari console to test WebSocket
```

---

## 🚀 Potential Fixes

### Fix 1: Update notification_provider.dart to be explicit about endpoint

```dart
// Try this exact endpoint:
final wsUrl = '$wsBase/ws';  // ✅ Let SockJS handle the rest
```

### Fix 2: Add more debugging to see what's actually happening

Add this to your `app.dart` after authentication:

```dart
ref.listenManual<AuthState>(authNotifierProvider, (prev, next) async {
  if (next.isAuthenticated && prev?.isAuthenticated != true) {
    await ref.read(collaborateurNotifierProvider.notifier).fetchMoi();
    final info = ref.read(collaborateurNotifierProvider).value;
    
    if (info != null) {
      // Add delay to ensure token is fully saved
      await Future.delayed(const Duration(milliseconds: 500));
      
      debugPrint('[AUTH] ✅ User authenticated: ${info.identifiant}');
      debugPrint('[AUTH] Connecting to WebSocket...');
      
      ref.read(notificationProvider.notifier).connect(info.identifiant);
    }
  }
}, fireImmediately: true);
```

### Fix 3: Disable SockJS and test raw WebSocket

Temporarily change this line:

```dart
// From:
useSockJS: true,

// To:
useSockJS: false,
```

If it connects with `useSockJS: false`, then the issue is with SockJS compatibility on mobile.

### Fix 4: Check if JWT token is being validated on backend

Your backend must validate the JWT in the STOMP CONNECT headers. Check if:

```java
@Component
public class StompPrincipalExtractor implements ChannelInterceptor {
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        
        // Get Authorization header
        String auth = accessor.getFirstNativeHeader("Authorization");
        
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            // Validate token and set principal
            // If validation fails, return null to reject connection
        }
        
        return message;
    }
}
```

---

## 🔍 Quick Diagnostics Checklist

Run these commands to test:

```bash
# 1. Test from local machine (Postman-like test)
curl http://localhost:8080/ws/info

# 2. Test from emulator (or similar)
adb shell curl http://10.0.2.2:8080/ws/info

# 3. Check if WebSocket works at all
wscat -c ws://localhost:8080/ws

# 4. Test with authentication
wscat -c ws://localhost:8080/ws \
  -H "Authorization: Bearer YOUR_TOKEN"
```

---

## 📋 What to Share

To help debug, share:

1. **Backend logs** when connection attempt fails:
   ```bash
   grep -i "stomp\|websocket\|connect" <your-spring-boot-logs>
   ```

2. **Mobile app logs** showing STOMP debug output:
   ```bash
   flutter logs | grep STOMP
   ```

3. **Verify the emulator can reach backend:**
   ```bash
   adb shell curl -v http://10.0.2.2:8080/ws/info
   ```

4. **Your WebSocket config class** from Spring Boot

5. **If you're using a Spring Security filter**, check if it allows WebSocket connections

---

## 🎯 Most Likely Cause

Based on your Postman test working but mobile failing, the issue is probably:

**Mobile app is trying to connect to `localhost:8080` instead of the correct IP:**
- ✅ Android should use `10.0.2.2:8080`
- ✅ iOS should use `127.0.0.1:8080`

Check if `ApiConstants.baseUrl` is returning the correct IP for the device.

Run:
```dart
debugPrint('[DEBUG] API Base URL: ${ApiConstants.baseUrl}');
```

And share the output.

