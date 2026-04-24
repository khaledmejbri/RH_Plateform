# 🚀 Quick Start: Test the Fix

## ✅ What Was Changed

**File:** `lib/core/notifications/notification_provider.dart`  
**Change:** Line 83 → `useSockJS: false` (disabled SockJS cookies)

---

## 🎯 Test in 3 Steps

### 1️⃣ Run the App
```bash
cd C:\Local\Khaled\project\rh_mobile_app
flutter run --debug
```

### 2️⃣ Watch for CONNECTED Message
```bash
# Open another terminal/PowerShell
flutter logs | findstr STOMP
```

### 3️⃣ Look for Success
```
[STOMP] ✅ CONNECTED!
```

**If you see this → ✅ PROBLEM SOLVED!** 🎉

---

## 📊 Expected Log Output

```
[STOMP] ═════════════════════════════════════════
[STOMP] Attempting WebSocket connection
[STOMP] Base URL (HTTP): http://10.0.2.2:8080
[STOMP] WebSocket URL: ws://10.0.2.2:8080/ws
[STOMP] User ID: john.doe
[STOMP] Token available: true
[STOMP] ═════════════════════════════════════════
[STOMP] 🚀 Client activated - waiting for connection...
[STOMP] ✅ CONNECTED!
[STOMP] 📨 Subscribing to /user/john.doe/queue/notifications
[STOMP] 📨 Subscribing to /topic/john.doe
[STOMP] 📨 Subscribing to /topic/RH
```

---

## ❌ If Still Getting Error

### Error: "Connection not established after 5 seconds"

**Check 1:** Is backend running?
```bash
curl http://localhost:8080/ws/info
```
Should return JSON with `"cookie_needed": true`

**Check 2:** Can emulator reach backend?
```bash
adb shell curl http://10.0.2.2:8080/ws/info
```
Should return same JSON

**Check 3:** Is user logged in?
```
[STOMP] Token available: true
```
If false: User needs to authenticate first

**Check 4:** Is the base URL correct?
```
[STOMP] Base URL (HTTP): http://10.0.2.2:8080
```
Should be `10.0.2.2:8080` (not localhost!)

---

## 📱 Test Notification

After app connects, send a test notification:

```bash
curl -X POST http://localhost:8080/api/notifications/send-to-user/john.doe \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "subject": "Test",
    "content": "Hello from backend!"
  }'
```

**Expected in app logs:**
```
[STOMP] ✅ Notification received: "Test"
```

---

## 🔄 Rebuild (If Needed)

```bash
# If you want clean rebuild:
flutter clean
flutter pub get
flutter run --debug
```

---

## ✨ That's It!

The fix is:
- ✅ Already applied
- ✅ One line change (`useSockJS: false`)
- ✅ Ready to test

**Just run `flutter run --debug` and watch for the CONNECTED message!**

