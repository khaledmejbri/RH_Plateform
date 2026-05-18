# 🔧 Troubleshooting 403 Forbidden Errors

## Problem
Getting `403 Forbidden` when trying to create campaigns or access evaluation endpoints.

## Root Cause
The admin evaluation endpoints require authentication with a valid JWT token.

## Solutions

### ✅ Solution 1: Login First (Most Common)

1. **Make sure you're logged in to the admin panel**
   - Go to `http://localhost:5173/login`
   - Enter your credentials
   - Click "Se connecter"

2. **Verify you have a token**
   - Open browser DevTools (F12)
   - Go to **Application** tab → **Session Storage**
   - Check if `rh_admin_access_token` exists
   - If empty, you need to login again

3. **Check the token in requests**
   - Go to **Network** tab in DevTools
   - Try creating a campaign
   - Click on the failed request
   - Check **Headers** → Look for `Authorization: Bearer <token>`
   - If missing, the axios interceptor isn't working

### ✅ Solution 2: Restart Backend

After updating security configuration, restart the backend:

```powershell
# Stop the running svc-evaluation service
# Then restart it
cd C:\Local\Khaled\project\svc-evaluation
mvn spring-boot:run
```

Wait for: `Started EvaluationApplication in X seconds`

### ✅ Solution 3: Check CORS Configuration

If you see CORS errors along with 403:

The backend has `@CrossOrigin(origins = "*")` which should allow all origins. If still failing:

1. Check browser console for CORS errors
2. Verify frontend is running on `http://localhost:5173`
3. Verify backend is running on `http://localhost:8080`

### ✅ Solution 4: Verify Security Config

Check that the security configuration includes admin endpoints:

**File**: `svc-evaluation/src/main/java/com/hr/evaluation/config/SecurityConfig.java`

Should have BOTH lines:
```java
.requestMatchers("/api/rh/v1/evaluations/**").authenticated()
.requestMatchers("/api/rh/v1/admin/evaluations/**").authenticated()
```

## Quick Diagnostic Checklist

- [ ] Backend is running (`http://localhost:8080`)
- [ ] Frontend is running (`http://localhost:5173`)
- [ ] You are logged in (check Session Storage)
- [ ] JWT token exists in Session Storage
- [ ] Authorization header is sent with requests (check Network tab)
- [ ] Backend security config updated and restarted
- [ ] No CORS errors in browser console

## Testing Authentication

### Test 1: Manual Token Check
```javascript
// In browser console (F12)
sessionStorage.getItem('rh_admin_access_token')
// Should return a JWT token string, not null
```

### Test 2: Manual API Call
```javascript
// In browser console
fetch('http://localhost:8080/api/rh/v1/admin/evaluations/campaigns', {
  headers: {
    'Authorization': 'Bearer ' + sessionStorage.getItem('rh_admin_access_token')
  }
})
.then(r => r.json())
.then(console.log)
.catch(console.error)
```

### Test 3: Check Request Headers
1. Open DevTools → Network tab
2. Try to create a campaign
3. Click on the POST request to `/campaigns`
4. Go to **Headers** tab
5. Look for:
   ```
   Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
   ```

## Common Mistakes

❌ **Not logging in before accessing protected pages**
- Always login first at `/login`

❌ **Using wrong API URL**
- Admin endpoints: `/api/rh/v1/admin/evaluations/*`
- Mobile endpoints: `/api/rh/v1/evaluations/*`

❌ **Backend not restarted after config changes**
- Security config changes require restart

❌ **Token expired**
- Logout and login again to get fresh token

## Still Getting 403?

### Check Backend Logs
Look for security-related errors:
```
org.springframework.security.access.AccessDeniedException
Authentication failed
Invalid JWT token
```

### Enable Debug Logging
Add to `application.properties`:
```properties
logging.level.org.springframework.security=DEBUG
```

### Verify JWT Token Validity
Decode your token at https://jwt.io and check:
- Token is not expired (`exp` claim)
- Token has required scopes/roles
- Token issuer matches your auth server

---

**Quick Fix Summary:**
1. Login to get JWT token
2. Restart backend to apply security config
3. Refresh browser page
4. Try again!
