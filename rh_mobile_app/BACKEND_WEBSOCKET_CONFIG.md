# Backend WebSocket Configuration Checklist

## ✅ Required Spring Boot Configuration

Your backend **MUST** have this configuration class:

```java
package com.example.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry
            .addEndpoint("/ws")                    // ✅ MUST be /ws
            .setAllowedOrigins("*")               // ✅ Allow all origins
            .withSockJS();                        // ✅ Enable SockJS for mobile
    }
}
```

---

## ✅ Required for JWT Authentication

Since you're sending `Authorization: Bearer {token}` header, backend must validate it:

```java
package com.example.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.channel.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolHandler;

@Component
public class StompInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        
        if (StompCommand.CONNECT == accessor.getCommand()) {
            // Get Authorization header
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                
                try {
                    // Validate JWT token here
                    String userId = validateAndGetUserId(token);
                    
                    // Set principal for user-specific subscriptions
                    accessor.setUser(new StompPrincipal(userId));
                    
                } catch (Exception e) {
                    // Token invalid - connection will be rejected
                    return null;
                }
            }
        }
        
        return message;
    }

    private String validateAndGetUserId(String token) {
        // Your JWT validation logic here
        // Must throw exception if invalid
        return "user-id";
    }
}

// Simple principal class
class StompPrincipal implements java.security.Principal {
    private final String name;
    
    public StompPrincipal(String name) {
        this.name = name;
    }
    
    @Override
    public String getName() {
        return name;
    }
}
```

---

## ✅ Controller for Sending Notifications

Your backend needs a controller to send notifications:

```java
package com.example.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @PostMapping("/send-to-user/{userId}")
    public void sendToUser(@PathVariable String userId, 
                           @RequestBody NotificationDto notification) {
        // Send to user-specific queue
        messagingTemplate.convertAndSendToUser(
            userId,
            "/queue/notifications",  // ✅ Must match client subscription path
            notification
        );
    }

    @PostMapping("/broadcast-to-rh")
    public void broadcastToRH(@RequestBody NotificationDto notification) {
        // Send to RH topic (all RH users)
        messagingTemplate.convertAndSend(
            "/topic/RH",  // ✅ Must match client subscription path
            notification
        );
    }

    @PostMapping("/send-to-topic/{userId}")
    public void sendToTopic(@PathVariable String userId,
                            @RequestBody NotificationDto notification) {
        // Send to user topic
        messagingTemplate.convertAndSend(
            "/topic/" + userId,  // ✅ Must match client subscription path
            notification
        );
    }
}

class NotificationDto {
    public String subject;
    public String content;
}
```

---

## 🔍 Debugging: Backend Logs to Check

### 1. **When mobile app connects, you should see:**

```log
INFO  o.s.w.s.m.SubProtocolHandler : Received CONNECT
INFO  o.s.w.s.m.StompSubProtocolHandler : Handling CONNECT frame: version...
INFO  o.s.w.s.m.StompSubProtocolHandler : Processing CONNECT frame
INFO  o.s.m.s.s.StompSubProtocolHandler : onConnect() for session ...
```

### 2. **If JWT validation fails, you'll see:**

```log
WARN  StompSubProtocolHandler : STOMP CONNECT failed
ERROR StompPrincipalExtractor : Authentication failed: Invalid token
```

### 3. **When subscription happens:**

```log
DEBUG o.s.m.s.StompSubProtocolHandler : User ... subscribed to /user/john.doe/queue/notifications
DEBUG o.s.m.s.StompSubProtocolHandler : User ... subscribed to /topic/john.doe
```

### 4. **When notification is sent:**

```log
DEBUG o.s.m.s.DefaultSimpMessageSendingOperations : Sending message to destination '/user/john.doe/queue/notifications'
```

---

## ✅ Complete Flow Check

When mobile app connects:

1. ✅ **Backend receives CONNECT:**
   ```
   CONNECT
   Authorization: Bearer eyJhbGc...
   ```

2. ✅ **Backend validates JWT:**
   - Extract token from header
   - Validate signature and expiration
   - Extract user ID

3. ✅ **Backend sends CONNECTED:**
   ```
   CONNECTED
   version:1.2
   server:RabbitMQ/...
   ```

4. ✅ **Mobile sends SUBSCRIBE:**
   ```
   SUBSCRIBE
   destination:/user/john.doe/queue/notifications
   id:sub-0
   ```

5. ✅ **Mobile receives RECEIPT:**
   ```
   RECEIPT
   receipt-id:sub-0
   ```

6. ✅ **Backend sends notification:**
   ```
   MESSAGE
   destination:/user/john.doe/queue/notifications
   content-type:application/json
   
   {"subject":"Test","content":"Hello"}
   ```

---

## ❌ Common Issues & Fixes

### Issue: "STOMP CONNECT failed"
**Cause:** JWT validation error  
**Fix:**
- Check JWT token is valid
- Check token expiration
- Check JWT secret key is same on backend

```java
// Add more detailed logging
catch (Exception e) {
    log.error("JWT validation failed", e);  // ✅ Will show actual error
    return null;
}
```

### Issue: Connection closes immediately
**Cause:** Principal is null or missing  
**Fix:**
- Ensure `setUser()` is called in interceptor
- Check SecurityContext is configured

```java
// Must set principal
accessor.setUser(new StompPrincipal(userId));  // ✅ Don't skip this
```

### Issue: Notifications not received by mobile
**Cause:** Wrong subscription destination  
**Fix:**
- Backend sends to: `/user/john.doe/queue/notifications`
- Mobile subscribes to: `/user/john.doe/queue/notifications`  
- Must be EXACT match

```java
// Backend sends to:
messagingTemplate.convertAndSendToUser(userId, "/queue/notifications", msg);
// Maps to: /user/{userId}/queue/notifications

// Mobile must subscribe to:
// /user/john.doe/queue/notifications  ✅ Must be exact
```

### Issue: Headers not being received
**Cause:** SockJS or proxy stripping headers  
**Fix:**
- Try without SockJS first
- Check if reverse proxy is stripping headers

```java
registry.addEndpoint("/ws")
        .setAllowedOrigins("*")
        // .withSockJS()  // ❌ Try commenting this out first
        ;
```

---

## ✅ Verification Commands

### Test if endpoint is listening:
```bash
curl http://localhost:8080/ws/info
# Should return JSON with info about SockJS endpoint
```

### Test WebSocket connection:
```bash
wscat -c ws://localhost:8080/ws
```

### Test with token:
```bash
wscat -c ws://localhost:8080/ws \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### Send test notification from curl:
```bash
curl -X POST http://localhost:8080/api/notifications/send-to-user/john.doe \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "Test",
    "content": "Hello from backend"
  }'
```

---

## 📋 Pre-Launch Checklist

Backend checklist before testing with mobile:

- [ ] `@EnableWebSocketMessageBroker` is present
- [ ] `.addEndpoint("/ws")` is configured
- [ ] `.withSockJS()` is called
- [ ] `setAllowedOrigins("*")` is set
- [ ] JWT validation is implemented
- [ ] `accessor.setUser()` is called with principal
- [ ] Notification sending controller exists
- [ ] Subscription destinations match client (`/user/X/queue/notifications` or `/topic/X`)
- [ ] Backend logs show CONNECT, SUBSCRIBE, MESSAGE frames
- [ ] Token validation logs show successful validation

---

## 🚀 Next Steps

1. **Check backend logs** when mobile connects
2. **Share the logs** if connection fails
3. **Run `curl http://localhost:8080/ws/info`** from terminal
4. **Test WebSocket** with `wscat`
5. **Run mobile app** with `flutter run --debug`
6. **Check logs** for `[STOMP]` messages

If you share:
- Backend logs (CONNECT/SUBSCRIBE frames)
- Mobile logs ([STOMP] messages)
- Your WebSocket config class

I can pinpoint the exact issue!

