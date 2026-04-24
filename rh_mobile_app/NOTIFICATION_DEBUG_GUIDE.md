# Guide de Débogage des Notifications WebSocket

## Problème : "Toujours en attente de connexion"

Si la connexion WebSocket reste bloquée sur "en attente", suivez ce guide pour diagnostiquer et résoudre le problème.

---

## 1. **Vérifier la Configuration du Backend**

### Assurez-vous que le backend expose un endpoint WebSocket

```
Le endpoint doit être disponible sur: `ws://localhost:8080/ws`
```

**Vérifier le endpoint sur le serveur Spring Boot:**

```java
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
        registry.addEndpoint("/ws")
                .setAllowedOrigins("*")
                .withSockJS(); // Important pour la compatibilité mobile
    }
}
```

**Points clés:**
- ✅ L'endpoint doit être `/ws` (actuellement configuré dans le client)
- ✅ `withSockJS()` doit être activé pour la meilleure compatibilité
- ✅ `setAllowedOrigins("*")` doit autoriser le domaine/l'IP de l'app

---

## 2. **Vérifier l'URL de la Base API**

Voir `lib/core/constants/api_constants.dart` et `lib/core/constants/default_api_host_io.dart`:

### Android Emulator
```
HTTP: http://10.0.2.2:8080
WS:   ws://10.0.2.2:8080/ws
```

### iOS Simulator
```
HTTP: http://127.0.0.1:8080
WS:   ws://127.0.0.1:8080/ws
```

### Vérifier la connexion:
```bash
# Android
adb shell
ping 10.0.2.2

# iOS
xcrun simctl diagnose
```

---

## 3. **Vérifier le Token d'Authentification**

Le client envoie le token JWT dans les headers STOMP:

```dart
headers['Authorization'] = 'Bearer $token'
```

**À vérifier:**
- ✅ L'utilisateur est correctement authentifié
- ✅ Le token est stocké dans le secure storage
- ✅ Le token n'est pas expiré

### Voir les logs de connexion:
- Cherchez `[STOMP] Token available: true` dans les logs
- Si `false`, le token n'a pas été chargé à temps

---

## 4. **Logs de Débogage Disponibles**

Quand l'app démarre avec une authentification réussie, vous verrez dans les logs:

```
[STOMP] ═════════════════════════════════════════
[STOMP] Attempting WebSocket connection
[STOMP] URL: ws://10.0.2.2:8080/ws
[STOMP] User ID: john.doe
[STOMP] Token available: true
[STOMP] Token length: 512
[STOMP] ═════════════════════════════════════════
[STOMP] 🚀 Client activated - waiting for connection...
```

### Connexion réussie:
```
[STOMP] ✅ CONNECTED!
[STOMP] Server response: ...
[STOMP] 📨 Subscribing to /user/john.doe/queue/notifications
[STOMP] 📨 Subscribing to /topic/john.doe
[STOMP] 📨 Subscribing to /topic/RH
```

### Si stuck après 5 secondes:
```
[STOMP] ⚠️  Connection not established after 5 seconds
[STOMP] ⚠️  WebSocket Error: ...
```

---

## 5. **Checklist de Débogage**

### Backend
- [ ] Spring Boot server est en cours d'exécution (`./mvnw spring-boot:run`)
- [ ] Port 8080 est accessible (`curl http://localhost:8080/ws`)
- [ ] WebSocket endpoint est configuré (`/ws`)
- [ ] CORS/SockJS sont activés
- [ ] JWT validation est correcte

### Emulator/Simulator
- [ ] Android Emulator utilise `10.0.2.2` OU
- [ ] iOS Simulator utilise `127.0.0.1`
- [ ] Réseau est activé dans le simulateur
- [ ] Pas de firewall bloquant le port 8080

### App
- [ ] Utilisateur est loggé
- [ ] Token est présent en secure storage
- [ ] Logs montrent `Token available: true`
- [ ] App affiche "Connected: true" dans l'UI (si implémenté)

### Commandes de test

```bash
# Redémarrer l'app en mode debug
flutter run --debug -v

# Voir tous les logs incluant STOMP
flutter logs | grep STOMP

# Tester le endpoint WebSocket directement
wscat -c ws://localhost:8080/ws
```

---

## 6. **Solutions Courantes**

### **Symptôme: "Connection timed out"**
- ✅ Vérifier que le backend est démarré
- ✅ Vérifier que l'URL est correcte pour le device
- ✅ Vérifier le firewall/proxy réseau

### **Symptôme: "401 Unauthorized"**
- ✅ Vérifier que le token JWT est valide
- ✅ Vérifier que le backend accepte les tokens
- ✅ Vérifier l'expiration du token

### **Symptôme: Connecté mais pas de notifications reçues**
- ✅ Vérifier le chemin de la subscription (`/user/{id}/queue/notifications`, `/topic/{id}`, `/topic/RH`)
- ✅ Vérifier que le backend envoie les messages aux bons topics
- ✅ Vérifier la structure JSON de la notification

### **Symptôme: Connexion se déconnecte rapidement**
- ✅ Vérifier la configuration heartbeat du backend
- ✅ Vérifier la timeout de connexion (30s actuellement)
- ✅ Vérifier les logs du backend pour les erreurs

---

## 7. **Commandes de Test Backend**

### Envoyer une notification de test depuis Spring Boot

```bash
# Via curl/STOMP broker
curl -X POST http://localhost:8080/api/rh/v1/test-notification \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "Test Notification",
    "content": "This is a test message",
    "userId": "john.doe"
  }'
```

### Vérifier l'état STOMP sur le backend
- Actuator endpoint: `http://localhost:8080/actuator`
- Vérifier la santé de WebSocket dans les logs Spring Boot

---

## 8. **Améliorations Implémentées**

✅ **Debug logging amélioré** - Logs détaillés avec emojis et séparation claire  
✅ **Configuration SockJS** - Meilleure compatibilité  
✅ **Endpoints alternatifs** - Essaie plusieurs queues (`/user/X/queue/notifications`, `/topic/X`)  
✅ **Heartbeat** - Maintient la connexion active  
✅ **Error handling** - Capture toutes les erreurs possibles  
✅ **Timeout** - Attend 5 secondes pour la connexion  

---

## 9. **Prochaines Étapes**

1. **Vérifiez les logs** avec `flutter logs | grep STOMP`
2. **Partagez les logs** si la connexion échoue
3. **Testez le endpoint** directement: `wscat -c ws://10.0.2.2:8080/ws`
4. **Vérifiez le backend** envoie les notifications correctement

---

## Questions?

Si la connexion échoue:
1. Collez les logs STOMP
2. Confirmez que le backend expose `/ws`
3. Testez avec `curl http://10.0.2.2:8080` depuis l'emulator

