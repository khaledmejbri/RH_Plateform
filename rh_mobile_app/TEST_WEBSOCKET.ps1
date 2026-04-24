# WebSocket Connection Testing Script for Windows
# Run from: C:\Local\Khaled\project\rh_mobile_app

function Test-WebSocketConnection {
    param(
        [string]$BackendUrl = "localhost:8080"
    )

    Write-Host "=== WebSocket Connection Diagnostic ===" -ForegroundColor Cyan
    Write-Host ""

    # Test 1: HTTP connectivity
    Write-Host "1️⃣  Testing HTTP connectivity to backend:" -ForegroundColor Yellow
    Write-Host "   URL: http://$BackendUrl/ws/info" -ForegroundColor Gray
    Write-Host ""

    try {
        $response = Invoke-WebRequest "http://$BackendUrl/ws/info" -UseBasicParsing -TimeoutSec 5
        Write-Host "   ✅ SUCCESS! Response: $($response.StatusCode)" -ForegroundColor Green
        Write-Host "   ✅ Backend is reachable" -ForegroundColor Green
        Write-Host ""
    }
    catch {
        Write-Host "   ❌ FAILED! Backend not reachable" -ForegroundColor Red
        Write-Host "   ❌ Is backend running on port 8080?" -ForegroundColor Red
        Write-Host "   ❌ Check firewall settings" -ForegroundColor Red
        Write-Host ""
        return
    }

    # Test 2: Check if wscat is available
    Write-Host "2️⃣  Checking for wscat (WebSocket testing tool):" -ForegroundColor Yellow

    try {
        $wscat = wscat --version 2>$null
        Write-Host "   ✅ wscat found: $wscat" -ForegroundColor Green
        Write-Host ""

        Write-Host "3️⃣  Testing WebSocket connection:" -ForegroundColor Yellow
        Write-Host "   Run this command:" -ForegroundColor Gray
        Write-Host "   wscat -c ws://$BackendUrl/ws" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "   Then send STOMP CONNECT frame:" -ForegroundColor Gray
        Write-Host "   CONNECT" -ForegroundColor Cyan
        Write-Host "   login:guest" -ForegroundColor Cyan
        Write-Host "   passcode:guest" -ForegroundColor Cyan
        Write-Host "   " -ForegroundColor Cyan
        Write-Host ""
    }
    catch {
        Write-Host "   ❌ wscat not found. Install with: npm install -g wscat" -ForegroundColor Red
        Write-Host ""
    }

    # Test 3: Android Emulator specific
    Write-Host "4️⃣  For Android Emulator:" -ForegroundColor Yellow
    Write-Host "   Check if backend is reachable from emulator:" -ForegroundColor Gray
    Write-Host "   adb shell curl -v http://10.0.2.2:8080/ws/info" -ForegroundColor Cyan
    Write-Host ""

    # Test 4: Logs
    Write-Host "5️⃣  Check mobile app logs:" -ForegroundColor Yellow
    Write-Host "   Run: flutter logs | findstr STOMP" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "   Look for:" -ForegroundColor Gray
    Write-Host "   ✅ [STOMP] ✅ CONNECTED!" -ForegroundColor Green
    Write-Host "   ❌ [STOMP] ❌ WebSocket Error:" -ForegroundColor Red
    Write-Host "   ❌ [STOMP] ⚠️  Connection not established after 5 seconds" -ForegroundColor Red
    Write-Host ""

    # Test 5: Verify token
    Write-Host "6️⃣  Check if token is being sent:" -ForegroundColor Yellow
    Write-Host "   Look for in logs:" -ForegroundColor Gray
    Write-Host "   [STOMP] Token available: true" -ForegroundColor Green
    Write-Host ""
    Write-Host "   If shows 'false', the token is not loaded in time" -ForegroundColor Yellow
    Write-Host ""

    # Summary
    Write-Host "=== Summary Checklist ===" -ForegroundColor Cyan
    Write-Host "✅ Backend HTTP endpoint responds" -ForegroundColor Green
    Write-Host "⏳ WebSocket endpoint connects (test with wscat)" -ForegroundColor Yellow
    Write-Host "⏳ Mobile app shows [STOMP] ✅ CONNECTED!" -ForegroundColor Yellow
    Write-Host "⏳ App receives notifications" -ForegroundColor Yellow
    Write-Host ""
}

# Run tests
Test-WebSocketConnection

# Additional help
Write-Host ""
Write-Host "=== Quick Troubleshooting ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "If WebSocket fails but HTTP works:" -ForegroundColor Yellow
Write-Host "  - Check if Spring Boot WebSocket config has @EnableWebSocketMessageBroker" -ForegroundColor Gray
Write-Host "  - Check if endpoint is registered with .addEndpoint('/ws')" -ForegroundColor Gray
Write-Host "  - Check if .withSockJS() is called" -ForegroundColor Gray
Write-Host ""

Write-Host "If connection drops immediately:" -ForegroundColor Yellow
Write-Host "  - Check JWT validation on backend" -ForegroundColor Gray
Write-Host "  - Check if token is expired" -ForegroundColor Gray
Write-Host "  - Check if 'Authorization' header is being validated" -ForegroundColor Gray
Write-Host ""

Write-Host "If mobile shows 'Connection not established':" -ForegroundColor Yellow
Write-Host "  - Verify correct IP is being used (10.0.2.2 for Android)" -ForegroundColor Gray
Write-Host "  - Verify port 8080 is open" -ForegroundColor Gray
Write-Host "  - Try: adb shell curl http://10.0.2.2:8080/ws/info" -ForegroundColor Gray
Write-Host ""

