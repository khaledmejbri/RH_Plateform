# 🚪 API Gateway Architecture

## Overview

Your RH platform uses an **API Gateway** pattern where all client requests go through a single entry point (Gateway on port 8080), which then routes them to the appropriate microservices.

## Architecture Diagram

```
┌──────────────────┐
│  rh-admin-web    │  Port 5173 (React Frontend)
│  Flutter Mobile  │  Mobile App
└────────┬─────────┘
         │
         │ All requests → http://localhost:8080
         │
         ▼
┌─────────────────────────────────────┐
│     API GATEWAY (Port 8080)         │
│  - Route resolution                 │
│  - Load balancing                   │
│  - CORS handling                    │
│  - Authentication forwarding        │
└────┬────────┬───────────┬──────────┘
     │        │           │
     │        │           │
     ▼        ▼           ▼
┌────────┐ ┌────────┐ ┌──────────────┐
│Port 8081│ │Port 8084│ │ Port 8083    │
│        │ │        │ │              │
│svc-    │ │svc-    │ │svc-referent  │
│identite│ │evalua  │ │iel-rh        │
│-acces  │ │tion    │ │              │
│        │ │        │ │              │
│/api/   │ │/api/rh/│ │/api/rh/v1/   │
│auth/** │ │v1/eval │ │referentiel/* │
└────────┘ └────────┘ └──────────────┘
```

## Gateway Configuration

### File: `gateway/src/main/resources/application.yml`

The Gateway routes requests based on URL path patterns:

```yaml
spring:
  cloud:
    gateway:
      routes:
        # 1. Authentication routes → svc-identite-acces (port 8081)
        - id: svc-identite-acces
          uri: lb://svc-identite-acces
          predicates:
            - Path=/api/auth/**,/oauth2/**,/.well-known/**
        
        # 2. Evaluation routes → svc-evaluation (port 8084)
        - id: svc-evaluation
          uri: lb://svc-evaluation
          predicates:
            - Path=/api/rh/v1/evaluations/**
        
        # 3. RH v1 routes → svc-referentiel-rh (port 8083)
        - id: svc-referentiel-rh-v1
          uri: lb://svc-referentiel-rh
          predicates:
            - Path=/api/rh/v1/**
        
        # 4. Referentiel routes → svc-referentiel-rh (port 8083)
        - id: svc-referentiel-rh
          uri: lb://svc-referentiel-rh
          predicates:
            - Path=/api/referentiel/**
```

## Request Flow Examples

### Example 1: Login Request

```
Frontend: POST /api/auth/signin
    ↓
Gateway (port 8080): Matches route "svc-identite-acces"
    ↓
Routes to: http://svc-identite-acces:8081/api/auth/signin
    ↓
Response flows back through Gateway to Frontend
```

### Example 2: Create Campaign

```
Frontend: POST /api/rh/v1/admin/evaluations/campaigns
    ↓
Gateway (port 8080): Matches route "svc-evaluation" 
    ↓
Routes to: http://svc-evaluation:8084/api/rh/v1/admin/evaluations/campaigns
    ↓
Response flows back through Gateway to Frontend
```

### Example 3: Get Employees

```
Frontend: GET /api/rh/v1/referentiel/collaborateurs
    ↓
Gateway (port 8080): Matches route "svc-referentiel-rh-v1"
    ↓
Routes to: http://svc-referentiel-rh:8083/api/rh/v1/referentiel/collaborateurs
    ↓
Response flows back through Gateway to Frontend
```

## Vite Proxy Configuration

### File: `rh-admin-web/vite.config.ts`

The frontend only needs ONE proxy rule - send everything to the Gateway:

```typescript
server: {
  port: 5173,
  proxy: {
    '/api': {
      target: 'http://localhost:8080', // Gateway
      changeOrigin: true,
    },
  },
}
```

**Why this works:**
- All `/api/*` requests → Gateway (port 8080)
- Gateway examines the path and routes to correct service
- Frontend doesn't need to know about individual service ports
- Single point of configuration

## Benefits of Gateway Pattern

### ✅ Advantages

1. **Single Entry Point**
   - Clients only need to know Gateway URL
   - No need to track multiple service ports

2. **Centralized Cross-Cutting Concerns**
   - CORS configuration in one place
   - Authentication/authorization
   - Rate limiting
   - Logging & monitoring

3. **Service Discovery Integration**
   - Uses Eureka for dynamic service discovery
   - Automatic load balancing (`lb://` prefix)
   - Services can scale horizontally

4. **Decoupling**
   - Frontend doesn't depend on backend service topology
   - Backend services can change ports/locations without affecting clients
   - Easy to add/remove services

5. **Security**
   - Gateway can validate tokens before forwarding
   - Internal services not directly exposed
   - Can implement IP whitelisting, rate limiting, etc.

## Service Ports Reference

| Service | Direct Port | Gateway Route | Path Pattern |
|---------|-------------|---------------|--------------|
| **Gateway** | 8080 | N/A (entry point) | All `/api/*` |
| **svc-identite-acces** | 8081 | `lb://svc-identite-acces` | `/api/auth/**` |
| **svc-referentiel-rh** | 8083 | `lb://svc-referentiel-rh` | `/api/rh/v1/**`, `/api/referentiel/**` |
| **svc-evaluation** | 8084 | `lb://svc-evaluation` | `/api/rh/v1/evaluations/**` |
| **svc-notification** | 8085 | `lb:ws://svc-notification` | `/ws/**`, `/ws-sockjs/**` |
| **Eureka Server** | 8761 | N/A (service registry) | N/A |

## Starting the Platform

### Required Services (in order):

1. **Eureka Server** (Service Discovery)
   ```powershell
   cd C:\Local\Khaled\project\eureka-server
   mvn spring-boot:run
   ```

2. **API Gateway**
   ```powershell
   cd C:\Local\Khaled\project\gateway
   mvn spring-boot:run
   ```

3. **Authentication Service**
   ```powershell
   cd C:\Local\Khaled\project\svc-identite-acces
   mvn spring-boot:run
   ```

4. **Evaluation Service** (when needed)
   ```powershell
   cd C:\Local\Khaled\project\svc-evaluation
   mvn spring-boot:run
   ```

5. **Referentiel RH Service** (when needed)
   ```powershell
   cd C:\Local\Khaled\project\svc-referentiel-rh
   mvn spring-boot:run
   ```

6. **Frontend**
   ```powershell
   cd C:\Local\Khaled\project\rh-admin-web
   npm run dev
   ```

### Verify Services Registered in Eureka

Open: `http://localhost:8761`

You should see:
- ✅ GATEWAY
- ✅ SVC-IDENTITE-ACCES
- ✅ SVC-EVALUATION
- ✅ SVC-REFERENTIEL-RH

## Troubleshooting

### Problem: 404 Not Found
**Cause**: Service not registered in Eureka or Gateway route misconfigured

**Solution**:
1. Check Eureka dashboard: `http://localhost:8761`
2. Verify service is UP
3. Check Gateway logs for routing errors
4. Verify path matches Gateway route predicate

### Problem: 503 Service Unavailable
**Cause**: Service is down or not reachable

**Solution**:
1. Check if service is running
2. Check service logs for startup errors
3. Verify service registered with correct name in Eureka

### Problem: CORS Errors
**Cause**: Gateway CORS config not applied or direct service access

**Solution**:
1. Ensure all requests go through Gateway (port 8080)
2. Don't access services directly (e.g., don't use localhost:8084)
3. Check Gateway CORS configuration in application.yml

### Problem: Login fails with 401/403
**Cause**: Auth service not running or Gateway not routing correctly

**Solution**:
1. Verify svc-identite-acces is running on port 8081
2. Check it's registered in Eureka
3. Test direct: `POST http://localhost:8081/api/auth/signin`
4. Test via Gateway: `POST http://localhost:8080/api/auth/signin`

## Testing Gateway Routing

### Test 1: Direct Service Access
```bash
# Should work if service is running
curl -X POST http://localhost:8081/api/auth/signin \
  -H "Content-Type: application/json" \
  -d '{"nom_utilisateur":"admin","mot_de_passe":"password"}'
```

### Test 2: Via Gateway
```bash
# Should also work (and is the correct way)
curl -X POST http://localhost:8080/api/auth/signin \
  -H "Content-Type: application/json" \
  -d '{"nom_utilisateur":"admin","mot_de_passe":"password"}'
```

Both should return the same result, but **always use the Gateway** in production!

## Key Takeaways

1. ✅ **All client requests → Gateway (port 8080)**
2. ✅ **Gateway routes to services using Eureka discovery**
3. ✅ **Frontend only knows about Gateway URL**
4. ✅ **Services communicate internally via service names**
5. ✅ **CORS, auth, logging handled centrally**

---

**Remember**: Never access microservices directly from the frontend. Always go through the Gateway!
