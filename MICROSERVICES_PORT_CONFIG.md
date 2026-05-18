# 🏗️ Microservices Port Configuration Guide

## Service Architecture

Your RH platform uses multiple microservices, each running on different ports:

| Service | Port | Purpose | Endpoints |
|---------|------|---------|-----------|
| **authenctication** | 8080 | User authentication & login | `/api/auth/*` |
| **svc-identite-acces** | 8081 | Identity & access management | `/api/identity/*` |
| **svc-referentiel-rh** | 8083 | HR reference data (employees, units, etc.) | `/api/rh/v1/referentiel/*` |
| **svc-evaluation** | 8084 | Evaluation management | `/api/rh/v1/evaluations/*` |
| **svc-notification** | 8085 | Notifications & messaging | `/api/notifications/*` |
| **rh-admin-web** | 5173 | Admin web application (React) | Frontend UI |

## Vite Proxy Configuration

The frontend uses Vite's proxy feature to route API requests to the correct microservice:

### Configuration File: `rh-admin-web/vite.config.ts`

```typescript
server: {
  port: 5173,
  proxy: {
    // Authentication -> port 8080
    '/api/auth': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
    
    // Evaluation admin -> port 8084
    '/api/rh/v1/admin/evaluations': {
      target: 'http://localhost:8084',
      changeOrigin: true,
    },
    
    // Other RH endpoints -> port 8083
    '/api/rh': {
      target: 'http://localhost:8083',
      changeOrigin: true,
    },
  },
}
```

### How It Works

When you make a request from the frontend:

1. **Login Request**:
   ```javascript
   fetch('/api/auth/signin', ...)
   ```
   ↓ Vite proxies to:
   ```
   http://localhost:8080/api/auth/signin
   ```

2. **Evaluation Request**:
   ```javascript
   api.post('/api/rh/v1/admin/evaluations/campaigns', ...)
   ```
   ↓ Vite proxies to:
   ```
   http://localhost:8084/api/rh/v1/admin/evaluations/campaigns
   ```

3. **Employee List Request**:
   ```javascript
   api.get('/api/rh/v1/referentiel/collaborateurs', ...)
   ```
   ↓ Vite proxies to:
   ```
   http://localhost:8083/api/rh/v1/referentiel/collaborateurs
   ```

## Why This Matters

### ❌ Before Fix (Wrong Configuration)
All `/api` requests went to port 8084 (evaluation service):
```
/api/auth/signin → http://localhost:8084/api/auth/signin ❌ (No auth endpoint on 8084!)
→ Result: 404 Not Found or 401 Unauthorized
```

### ✅ After Fix (Correct Configuration)
Requests are routed to the correct service:
```
/api/auth/signin → http://localhost:8080/api/auth/signin ✅
/api/rh/v1/admin/evaluations/campaigns → http://localhost:8084/... ✅
```

## Starting All Services

To run the complete platform, you need to start multiple services:

### Option 1: Start Individually

```powershell
# Terminal 1 - Authentication Service
cd C:\Local\Khaled\project\authenctication
mvn spring-boot:run

# Terminal 2 - Referentiel RH Service
cd C:\Local\Khaled\project\svc-referentiel-rh
mvn spring-boot:run

# Terminal 3 - Evaluation Service
cd C:\Local\Khaled\project\svc-evaluation
mvn spring-boot:run

# Terminal 4 - Frontend
cd C:\Local\Khaled\project\rh-admin-web
npm run dev
```

### Option 2: Use Docker Compose (if available)
```powershell
docker-compose up -d
```

### Option 3: Use Start Script (if available)
```powershell
.\start-all.ps1
```

## Verifying Services Are Running

Check if services are listening on their ports:

```powershell
# Check all Java processes
netstat -ano | findstr "LISTENING" | findstr ":808"

# Should show:
# :8080 - authenctication
# :8081 - svc-identite-acces
# :8083 - svc-referentiel-rh
# :8084 - svc-evaluation
# :8085 - svc-notification
```

Or use PowerShell:
```powershell
Get-NetTCPConnection -State Listen | Where-Object {$_.LocalPort -like "808*"}
```

## Troubleshooting

### Problem: Login returns 404 or 401
**Cause**: Auth service not running on port 8080

**Solution**:
```powershell
# Start auth service
cd C:\Local\Khaled\project\authenctication
mvn spring-boot:run
```

### Problem: Evaluation requests return 404
**Cause**: Evaluation service not running on port 8084

**Solution**:
```powershell
# Start evaluation service
cd C:\Local\Khaled\project\svc-evaluation
mvn spring-boot:run
```

### Problem: Employee/unit data not loading
**Cause**: Referentiel RH service not running on port 8083

**Solution**:
```powershell
# Start referentiel service
cd C:\Local\Khaled\project\svc-referentiel-rh
mvn spring-boot:run
```

### Problem: CORS errors
**Cause**: Direct requests bypassing Vite proxy

**Solution**: 
- Make sure axios/fetch uses relative URLs (e.g., `/api/auth/signin`)
- Don't use absolute URLs like `http://localhost:8080/api/auth/signin`
- Let Vite proxy handle routing

## Adding New Services

If you add a new microservice, update the Vite proxy config:

```typescript
proxy: {
  // Existing routes...
  
  // New service
  '/api/new-service': {
    target: 'http://localhost:NEW_PORT',
    changeOrigin: true,
  },
}
```

## Architecture Diagram

```
┌─────────────────┐
│  rh-admin-web   │  Port 5173 (React Frontend)
│  (Vite Dev)     │
└────────┬────────┘
         │
         │ Proxy Routes
         │
    ┌────┴──────────────────────────────────┐
    │                                       │
    ▼                                       ▼
┌──────────┐                    ┌──────────────────┐
│ Port 8080│                    │   Port 8084      │
│          │                    │                  │
│authenctic│                    │ svc-evaluation   │
│  ation   │                    │                  │
│          │                    │                  │
│/api/auth │                    │/api/rh/v1/admin/ │
│          │                    │  /evaluations    │
└──────────┘                    └──────────────────┘
         ▲                              ▲
         │                              │
         └──────────────────────────────┘
                    │
                    ▼
            ┌──────────────┐
            │  Port 8083   │
            │              │
            │svc-referent  │
            │  iel-rh      │
            │              │
            │/api/rh/v1/   │
            │  referentiel │
            └──────────────┘
```

## Key Takeaways

1. ✅ Each microservice runs on its own port
2. ✅ Vite proxy routes requests to correct service
3. ✅ Frontend uses relative URLs (no hardcoded ports)
4. ✅ All required services must be running
5. ✅ Restart frontend after changing proxy config

---

**Remember**: After updating `vite.config.ts`, always restart the frontend dev server!
