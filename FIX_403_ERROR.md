# 🔧 403 Forbidden Error - FIXED

## Problem Summary
Getting `403 Forbidden` when trying to access evaluation admin endpoints.

## Root Causes Identified

### ❌ Issue 1: Wrong Backend Port
- **svc-evaluation** runs on port **8084** (not 8080)
- Vite proxy was configured for port 8080
- Axios baseURL was pointing to wrong port

### ❌ Issue 2: Missing Security Configuration
- Admin endpoints `/api/rh/v1/admin/evaluations/**` were not in security config
- Required authentication but path wasn't recognized

### ❌ Issue 3: Controller Path Conflicts
- Two controllers with same base path caused routing issues
- `EvaluationMobileController` and `EvaluationAdminController` both used `/api/rh/v1/evaluations`

## ✅ Fixes Applied

### Fix 1: Updated Vite Proxy Configuration
**File**: `rh-admin-web/vite.config.ts`

```typescript
server: {
  port: 5173,
  proxy: {
    '/api': {
      target: 'http://localhost:8084', // Changed from 8080 to 8084
      changeOrigin: true,
    },
  },
}
```

### Fix 2: Updated Axios Configuration
**File**: `rh-admin-web/src/api/evaluationApi.ts`

```typescript
const api = axios.create({
  baseURL: '', // Use Vite proxy instead of hardcoded URL
  headers: {
    'Content-Type': 'application/json'
  }
});
```

### Fix 3: Added Request/Response Logging
**File**: `rh-admin-web/src/api/evaluationApi.ts`

Added interceptors to log all API requests and errors for debugging:
```typescript
api.interceptors.request.use((config) => {
  console.log('[API Request]', config.method?.toUpperCase(), config.url);
  // ... token handling
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    console.error('[API Error]', error.response?.status, error.config?.url);
    return Promise.reject(error);
  }
);
```

### Fix 4: Updated Security Configuration
**File**: `svc-evaluation/src/main/java/com/hr/evaluation/config/SecurityConfig.java`

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
    .requestMatchers("/api/rh/v1/evaluations/**").authenticated()
    .requestMatchers("/api/rh/v1/admin/evaluations/**").authenticated() // Added this line
    .anyRequest().denyAll())
```

### Fix 5: Separated Admin Controller Paths
**File**: `svc-evaluation/src/main/java/com/hr/evaluation/web/EvaluationAdminController.java`

Changed from:
```java
@RequestMapping("/api/rh/v1/evaluations")
```

To:
```java
@RequestMapping("/api/rh/v1/admin/evaluations")
```

This prevents conflicts with the mobile controller.

## 🚀 How to Apply Fixes

### Step 1: Restart Frontend Dev Server
The Vite config changes require a restart:

```powershell
# Stop the current frontend (Ctrl+C)
# Then restart:
cd C:\Local\Khaled\project\rh-admin-web
npm run dev
```

### Step 2: Restart Backend (if not already running)
```powershell
cd C:\Local\Khaled\project\svc-evaluation
mvn spring-boot:run
```

Wait for: `Started EvaluationApplication in X seconds`

### Step 3: Verify Services Are Running
- Frontend: `http://localhost:5173`
- Backend (svc-evaluation): `http://localhost:8084`
- Check backend logs for no errors

### Step 4: Login and Test
1. Open browser: `http://localhost:5173`
2. Login with your credentials
3. Navigate to **Évaluations** tab
4. Try creating a campaign
5. Should work now! ✅

## 🔍 Debugging Checklist

If you still get 403 after applying fixes:

### 1. Check Browser Console
Open DevTools (F12) → Console tab, look for:
```
[API Request] POST /api/rh/v1/admin/evaluations/campaigns
```

If you see this, the request is being made correctly.

### 2. Check Network Tab
- Go to DevTools → Network tab
- Filter by "campaigns"
- Click on the POST request
- Check:
  - **Status Code**: Should be 201 (Created), not 403
  - **Request URL**: Should be `http://localhost:5173/api/rh/v1/admin/evaluations/campaigns`
  - **Request Headers**: Should have `Authorization: Bearer <token>`
  - **Response**: Check for error messages

### 3. Verify Token Exists
In browser console:
```javascript
sessionStorage.getItem('rh_admin_access_token')
// Should return a JWT token string
```

### 4. Check Backend Logs
Look for these patterns in svc-evaluation logs:
```
✅ Good: "POST /api/rh/v1/admin/evaluations/campaigns" - 201 Created
❌ Bad: "AccessDeniedException" or "Authentication failed"
```

### 5. Test Direct API Call
In browser console:
```javascript
fetch('/api/rh/v1/admin/evaluations/campaigns', {
  method: 'GET',
  headers: {
    'Authorization': 'Bearer ' + sessionStorage.getItem('rh_admin_access_token')
  }
})
.then(r => {
  console.log('Status:', r.status);
  return r.json();
})
.then(console.log)
.catch(console.error);
```

## 📋 Common Issues & Solutions

### Issue: "Cannot reach localhost:8084"
**Solution**: Make sure svc-evaluation is running:
```powershell
netstat -ano | findstr :8084
```
Should show LISTENING state.

### Issue: "CORS error" 
**Solution**: The Vite proxy should handle CORS. If still seeing errors:
- Clear browser cache
- Hard refresh (Ctrl+Shift+R)
- Check vite.config.ts has correct port

### Issue: "Token expired"
**Solution**: Logout and login again to get fresh token

### Issue: "401 Unauthorized" instead of 403
**Solution**: You're not logged in or token is invalid
- Check Session Storage for token
- Try logging in again

## 🎯 Quick Verification Steps

After applying all fixes:

1. ✅ Frontend running on port 5173
2. ✅ Backend running on port 8084
3. ✅ Logged in (token in Session Storage)
4. ✅ Browser console shows `[API Request]` logs
5. ✅ Network tab shows Authorization header
6. ✅ Campaign creation returns 201 status

## 📝 Files Modified

| File | Change | Purpose |
|------|--------|---------|
| `vite.config.ts` | Port 8080 → 8084 | Point to correct backend port |
| `evaluationApi.ts` | Removed baseURL | Use Vite proxy |
| `evaluationApi.ts` | Added logging | Debug requests/errors |
| `SecurityConfig.java` | Added admin path | Allow authenticated access |
| `EvaluationAdminController.java` | Changed path | Avoid controller conflicts |

---

## ✨ Expected Behavior After Fix

When you create a campaign:
1. Frontend sends POST to `/api/rh/v1/admin/evaluations/campaigns`
2. Vite proxies to `http://localhost:8084/api/rh/v1/admin/evaluations/campaigns`
3. Backend validates JWT token
4. Backend creates campaign
5. Returns 201 Created with campaign data
6. Frontend shows success message

**No more 403 errors!** 🎉
