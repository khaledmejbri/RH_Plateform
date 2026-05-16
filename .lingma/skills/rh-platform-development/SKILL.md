---
name: rh-platform-development
description: Expert guide for developing and enhancing the RH-Évènement SIRH platform v2.0 for AGUA Service d'Assainissement. Covers 12 functional modules (M01-M12) with AI integration, real-time processing, and cloud-native architecture. Includes microservices (svc-referentiel-rh, svc-demandes, svc-formation, svc-evaluation, svc-plainte, svc-epi, svc-presence, svc-notification, svc-document, svc-ia, svc-recrutement), Flutter mobile app with Riverpod and biometric attendance, React admin portal, WebSocket notifications, Kafka events, and production-ready features (S3 GED, Redis cache, offline mode). Use when working on HR platform development, implementing workflows, fixing bugs, or optimizing backend/mobile/web components.
---

# RH-Évènement Platform Development Guide

## Platform Overview

RH-Évènement v2.0 is a comprehensive HR Information System (SIRH) for **AGUA Service d'Assainissement** that digitizes 100% of HR processes through:
- **Mobile App** (Flutter cross-platform iOS/Android) with offline-first capability and biometric attendance
- **Web Admin Portal** (React/TypeScript) for HR administration and executive dashboards
- **Microservices Backend** (Spring Boot 3, Java 21) with 12 specialized services
- **AI Integration** (Python FastAPI + LangChain) for chatbot, CV matching, predictive analytics

### Strategic Objectives
- Reduce HR request processing delays by 80% through automation
- Provide modern mobile experience with offline capability
- Integrate AI for RH matching, chatbots, evaluations, and predictive alerts
- Ensure complete traceability with immutable audit logs (5-year retention)
- Prepare future integration with payroll module
- RGPD compliance and enterprise-level security
- 99.5% SLA availability

---

## Architecture & Microservices

### Service Structure (v2.0 - 12 Specialized Services)

| Service | Responsibility | Database |
|---------|---------------|----------|
| **svc-referentiel-rh** | Employee reference data, organizational structure (Dept → Unit → RO → Employee) | `referentiel_db` |
| **svc-demandes** | M01: Leave requests, exit permits (max 4h), mission orders + M02: Administrative documents | `demandes_db` |
| **svc-formation** | M05: Training needs, sessions, hot/cold evaluations with schedulers | `formation_db` |
| **svc-evaluation** | M07: Semi-annual and annual performance reviews with AI color coding | `evaluation_db` |
| **svc-plainte** | M04: Internal/external complaints, voice transcription, inter-service dashboard | `plainte_db` |
| **svc-epi** | M08: PPE catalog, stock management, attributions, PDF receipts | `epi_db` |
| **svc-presence** | M09: QR code + facial recognition attendance, monthly sheets, overtime calculation | `presence_db` |
| **svc-notification** | Transversal: Push FCM, emails, WebSocket STOMP for real-time updates | `notification_db` |
| **svc-document** | GED: S3 storage, PDF generation, versioning, document templates | S3/MinIO + metadata DB |
| **svc-ia** | M10+M11: Chatbot RAG, CV matching ATS, predictive analytics, Whisper transcription | Statelessness + vector DB |
| **svc-recrutement** | M10: Job postings, candidate pipeline, ATS workflow | `recrutement_db` |
| **api-gateway** | Spring Cloud Gateway: JWT auth, routing, rate limiting, CORS | - |
| **Keycloak** | OAuth2/OIDC authentication server, RBAC roles management | `keycloak_db` |

### Key Architectural Principles

1. **API-First**: REST contracts documented with OpenAPI 3
2. **Event-Driven**: Apache Kafka for async communication between services
3. **Data Ownership**: Each service owns its PostgreSQL database; no cross-service JOINs
4. **Stateless APIs**: Horizontal scaling with Kubernetes auto-scaling
5. **Idempotency**: Critical operations support idempotency keys
6. **Offline-First Mobile**: Local file queue with automatic sync when online
7. **RGPD Compliance**: Biometric data stays on-device, consent management, right to be forgotten

### User Roles & Access Control (RBAC)

| Role | Responsibilities | Primary Interface |
|------|-----------------|-------------------|
| **Collaborateur** | Submit requests, check status, respond to evaluations, attendance clock-in | Mobile app |
| **Responsable Opérationnel (RO)** | Validate team requests, create external complaints, track PPE, manual attendance fallback | Mobile + Web |
| **Chef de Département** | Hierarchical validation, team monitoring, personnel need forms | Mobile + Web |
| **Responsable RH (RRH)** | Complete administration, dashboards, reporting, system configuration | Web portal |
| **HSE** | PPE management: catalog, attributions, stock control | Web + Mobile |
| **Direction Générale** | Executive dashboards, budget validation, read-only access | Web |

**Security Requirements**:
- JWT access token: 15 minutes expiration
- Refresh token: 7 days expiration
- AES-256 encryption for sensitive data
- Immutable audit log with 5-year retention
- RGPD compliance: right to be forgotten, biometric consent

---

## Functional Modules (Business Requirements v2.0)

### M01.A: Leave Requests (Congé)
**Types**: Configurable by RH (annual, sick leave, maternity, unpaid, etc.)
**Workflow**: Employee → RO (validation) → RRH (final approval) → System (balance update + PDF archive S3)
**Features**:
- Automatic balance calculation (future payroll integration)
- Mandatory attachments per type (medical certificate for sick leave)
- Minimum delay before departure configurable (e.g., 48h for annual leave)
- Real-time push notifications at each step

### M01.B: Exit Permit (Autorisation de Sortie) - NOT A LEAVE
**Critical**: This is a short exit (max 4 hours, less than half-day), NOT deducted from leave balance
**Fields**: Date, departure time, return time, free-form reason
**Validation**: Client-side AND server-side validation of max 4h duration
**UI**: Real-time duration indicator in mobile form
**Workflow**: Employee → RO → RRH

### M01.C: Mission Order (Ordre de Mission)
**Fields**: Location, purpose, objectives, start/end dates, estimated costs, attachments
**Features**:
- Automatic PDF generation with digital signature capability
- File uploads to S3 (invitations, letters)
- Same workflow: Employee → RO → RRH

### M02: Administrative Documents
**Types & SLAs**:
- Work certificate (auto PDF generation, configurable template) - < 24h
- Salary certificate (from payroll module future) - < 24h
- Pay slip (secure PDF, 5-year history) - < 48h
- CNSS certificate (with employer number) - < 48h
- Monthly attendance sheet (PDF/Excel export) - < 1h (automatic)
- Specific internal documents (free-form with reason) - < 72h

**Features**:
- Priority queue system based on action_date (PriorityQueue algorithm)
- **AI Chatbot Integration**: Collaborator can chat with bot based on historical RH comments/responses to anticipate request status
- Technology: RAG (Retrieval-Augmented Generation) on request history
- Acceptance/rejection with comments

### M03: Service Notes
**Features**:
- Publication by Direction or RRH with attachments (PDF, images) stored on S3
- Instant push notification + WebSocket to all employees or targeted groups
- **Mandatory read acknowledgment** (collaborator confirms having read)
- Searchable archive with full-text search
- **GED (Electronic Document Management)**: versioning, categorization, role-based access
- **Reading indicator**: RH sees who has read / not read

### M04: Complaint Management
**Critical Distinction v2.0**:
- **Internal complaint**: Submitted by collaborator → sent to RH only
- **External/community complaint**: Created by RO/RH/Technique → sent simultaneously to Technical Services + Environment & Social Direction + RH
- Complaints can be submitted **WRITTEN or VOICE** (audio message transcribed by IA)

**Status Workflow**:
1. **New**: Collaborator/RO submits complaint with description, photos/videos, optional audio
2. **In Analysis**: RH/concerned team examines, requests complements if needed
3. **In Treatment**: Responsible service takes corrective actions
4. **Resolved**: RH closes with resolution comment
5. **Closed**: System/RH auto-archives after 30 days without contestation

**Features**:
- Complete action logs (who did what and when)
- Inter-service dashboard for external complaints
- **Audio transcription**: Voice → text via Whisper API for voice complaints

### M05: Training & Evaluation

**M05.A - Training Need/Demand**:
- Submission by collaborator or service head
- Validation: service head → RH → integration into annual training plan
- Fields: training type, organization, duration, estimated cost, pedagogical objectives, justification

**M05.B - Hot Evaluation (J+1 via Scheduler)**:
⏰ **Automatic scheduler**: System triggers hot evaluation 24 hours after training ends.
- Collaborator receives push notification with direct link to questionnaire
- If not answered within additional 48h: automatic reminder
- Customizable questionnaire (1-5 rating, open questions, trainer NPS)
- Evaluation of content, trainer, logistics, and perceived usefulness

**M05.C - Cold Evaluation (M+3 via Scheduler)**:
- Automatically triggered 3 months after training
- Measures real impact on daily work
- Questions on application of acquired skills, observed changes, complementary needs
- Results consolidated in RH dashboard with hot/cold comparison

### M06: Personnel Needs & Recruitment
- Need form created by service head (position, profile, desired date, justification)
- Workflow: head → direction → RH → integration into recruitment plan
- See M10 for AI CV/job matching module

### M07: RH Evaluations

**M07.A - Semi-Annual Evaluation**:
- 4 criteria rated 1-5: Work Quality, Performance, Punctuality, Team Spirit
- Score out of 20 - appreciation grid: Insufficient / To Improve / Satisfactory / Positive / Excellent
- Strengths, areas for improvement, recommended action plan
- Cross-validation between collaborator and superior

**M07.B - Annual Skills Evaluation**:
- Complete assessment: Knowledge (technical), Know-how (practical), Soft skills (behavioral)
- Results vs objectives N, definition of objectives N+1
- Training recommended by superior → automatically integrated into M05
- PDF export for archiving - S3 storage

🤖 **AI Innovation - Intelligent Color Coding**:
- 🟢 Green: excellent score on all criteria
- 🟠 Orange: 2 criteria below threshold - alert to RH
- 🔴 Red: 3+ insufficient criteria - mandatory action plan + DG escalation
- System automatically detects patterns and generates recommendations

### M08: PPE (Personal Protective Equipment) Distribution & Tracking

**PPE Catalog**:
- Database of PPE: name, category, size, stock quantity, expiration date, supplier
- Managed by HSE: add, modify, deactivate
- Automatic alerts: low stock + expiring PPE (< 30 days)

**Distribution Workflow**:
1. **Service Head**: Group request (PPE type, quantity, justification, normal/urgent priority) → transmitted to HSE
2. **HSE**: Validates, attributes partially or totally, defines delivery date → Head notified
3. **HSE**: Records physical delivery with digital signature of beneficiary → Traceability created
4. **System**: Generates PDF delivery receipt, updates stock, archives to S3 → Stock updated

**Features**:
- Complete traceability: beneficiary, date, quantity, validator, lifespan, history
- Monthly stock reporting exportable PDF/Excel

### M09: Digital Attendance - Multi-Modal Clock-In

🆕 **Innovative Module v2.0** - Pointage multi-modal:
- **QR Code**: Each employee has personal QR - scanned at entry/exit
- **Facial Recognition**: Via smartphone camera (on-device, RGPD-compliant)
- **Manual Fallback**: RO can manually clock-in if technical issues

**Critical Security Requirement - Anti-Fraud:**
Clock-in is a **two-step mandatory and sequential process**:
1. **Step 1**: Scan QR Code + GPS geolocation validation
2. **Step 2**: Facial recognition to confirm pointer identity
⚠ If either step fails, clock-in is REJECTED. No partial clock-in accepted.

**Detailed Process**:
| Step | Mechanism | Technical Description | Result/Rejection |
|------|-----------|----------------------|------------------|
| 1 | App Opening | Employee opens mobile app, accesses clock-in screen. App auto-activates camera and GPS. | Session initiated |
| 2 | QR Scan | Scan QR displayed on physical site (entrance, locker room, workstation). QR encodes signed terminal ID + GPS reference coordinates. QR renewed every 5 minutes (TOTP) to prevent capture/reuse off-site. | QR valid → step 3 |
| 3 | GPS Validation | Server compares real phone GPS with GPS reference in QR. Configurable tolerance radius per site (e.g., 50m). If deviation exceeds radius, clock-in REJECTED with alert to RO. | GPS out of zone → REJECT + RO alert. GPS valid → step 4 |
| 4 | Facial Recognition | App activates front camera, captures real-time photo (liveness detection active to detect printed photos or screens). Facial vector compared to reference vector registered during RH enrollment. Similarity threshold configurable (default: 92%). 100% on-device processing (on-device ML) - no photo transmitted to server. Only match score sent. | Score < threshold → REJECT. Score ≥ threshold → step 5 |
| 5 | Validated | Server records: employee ID, type (entry/exit), precise timestamp, real GPS coords, QR terminal ID, facial score (no photo), QR version. Cryptographically signed timestamp for irrefutability. | Attendance sheet updated. Push confirmation to employee. |

**QR Code Anti-Fraud Specifications**:
- Dynamic QR renewed every 5 minutes (TOTP algorithm - Time-based One-Time Password)
- QR content: {terminal_id, site_id, lat_ref, lng_ref, TOTP_token, HMAC_signature}
- Displayed on dedicated physical screen (tablet or welcome screen) auto-updated
- Captured QR sent to distant colleague is unusable (token expired before use)
- Anti-replay: each TOTP token accepted only once by server (nonce blacklist)
- Multi-site management: each site/unit has own QR terminals with independently configurable GPS radius

**Facial Recognition Specifications**:
- Technology: ML Kit Face Recognition (Google, on-device) - no biometric data leaves phone
- Liveness detection mandatory: detects fraud attempts with printed photo or screen display
- Reference photo taken by RH during employee registration (encrypted storage in S3)
- Reference facial vector downloaded encrypted to device at login - never raw photo
- Similarity threshold: 92% default, adjustable by RH (80% minimum imposed)
- After 3 consecutive failures: automatic alert to RO + 15-minute temporary block
- RGPD compliance: employee signs biometric consent during registration, revocable anytime

**Fraud Detection, Alerts & Exceptions**:

| Fraud Attempt Detected | System Response | Traceability |
|----------------------|-----------------|---------------|
| QR scanned from distant location (GPS out of zone) | Immediate rejection + push notification to RO with fraudster's real GPS coords | Timestamped log with GPS + identity + screenshot |
| Photo of another person (liveness fail) | Rejection + RO alert. 3 attempts → 15 min block | Log with facial score + timestamp + device ID |
| Expired QR or already-used token | Clear error message on phone, invitation to rescan current QR | Log with identity + used token |
| GPS unavailable (no signal area) | Wi-Fi geolocation fallback (site local network BSSID). If still impossible: manual RO clock-in required | Flagged "GPS unavailable" in attendance sheet |

**Data Recorded per Clock-In**:
- `collaborateur_id` - Unique employee identifier
- `type_pointage` - ENTREE / SORTIE
- `horodatage` - Precise UTC timestamp cryptographically signed
- `latitude_reelle` / `longitude_reelle` - Real GPS coordinates at clock-in
- `borne_id` / `site_id` - Scanned QR identifier
- `score_facial` - Similarity score (0.0 to 1.0) - no biometric image stored
- `device_id` - Smartphone identifier (to detect suspicious device changes)
- `statut` - VALIDE / REJETE_GPS / REJETE_FACIAL / REJETE_QR / MANUEL
- `signature_hmac` - Hash of all above fields, verifiable for integrity proof

### M10: Recruitment IA & ATS
🧠 **Artificial Intelligence Module** for candidate management.
ATS = Applicant Tracking System with intelligent CV/job matching.

**Features**:
- Job posting publication (from M06) on internal portal
- CV reception (PDF) - automatic AI parsing (extraction of skills, experiences, education)
- Intelligent matching: compatibility score CV/job description (0-100%)
- Automatic candidate ranking by matching score
- Detection of missing skills compared to required profile
- Recruitment pipeline tracking: Received → Preselected → Interview → Offer → Hired/Rejected
- Automatic collaborator account generation if hired (integration M00 RH structure)
- Recruitment analytics: time-to-hire, conversion rate, candidate sources

### M11: Chatbot RH & AI Assistant
**Features**:
- Chatbot available in mobile app for collaborators
- Responses based on RH FAQ + history of similar requests (RAG)
- Collaborator can query bot on: leave balance, request status, RH procedures
- Bot suggests procedures to follow based on situation
- Escalation to human RH agent if bot cannot respond
- Sentiment analysis of complaints for automatic prioritization
- Voice message transcription (Whisper) for accessibility

### M12: Dashboards & Analytics

**RRH Dashboard**:
- Real-time view: pending requests, open complaints, planned training
- RH KPIs: absenteeism rate, staff turnover, average processing time
- Alerts: overdue evaluations, expired PPE, unevaluated training

**Direction Générale Dashboard**:
- Executive charts: headcount by department, attendance trends, training budget
- Auto-generated monthly report (PDF) emailed to DG on 1st of month

**Predictive AI Analytics**:
- Absenteeism rate prediction by unit (ML model on attendance history)
- Early detection of employees at risk of resignation (engagement score)
- Automatic recommendation of annual training plan based on evaluations

---

## Technology Stack

### Backend (Spring Boot Microservices)

**Core Technologies**:
- **Java 21** / Spring Boot 3.x
- **Spring Security** + OAuth2/JWT with Keycloak
- **Spring Data JPA** + PostgreSQL 16
- **Spring Cloud Gateway** for API routing
- **Apache Kafka** for event streaming and audit logs
- **Redis** for caching, sessions, rate limiting
- **AWS S3/MinIO** for object storage (GED)
- **Flyway/Liquibase** for database migrations
- **Spring WebSocket + STOMP** for real-time notifications

**Key Dependencies**:
```xml
<!-- Real-time WebSocket -->
spring-boot-starter-websocket
stomp-protocol

<!-- Kafka for events -->
spring-kafka

<!-- Document generation -->
openpdf / flying-saucer (PDF generation)

<!-- AI integration -->
spring-ai or FastAPI bridge (Python)

<!-- OCR & Audio -->
tesseract (CV parsing)
openai-whisper (voice transcription)
```

### Mobile App (Flutter)

**Stack**:
- **Flutter 3.x** (Dart) - Cross-platform iOS/Android, offline-first
- State Management: **Riverpod** (`flutter_riverpod`)
- Navigation: **go_router**
- HTTP Client: **dio** with interceptors
- Real-time: **stomp_dart_client** + **web_socket_channel**
- Local Storage: **flutter_secure_storage**, **hive** (offline mode)
- Notifications: **firebase_messaging** + **flutter_local_notifications**
- Camera/ML: **google_mlkit_face_detection**, **mobile_scanner** (QR)
- Image Picker: **image_picker**
- Audio Recording: **record** (for voice complaints)
- Geolocation: **geolocator** (GPS validation)

**Project Structure**:
```
lib/
├── core/
│   ├── theme/          # AppTheme, colors, typography
│   ├── network/        # Dio client, interceptors (JWT, StompAuth)
│   ├── constants/      # API endpoints, keys
│   └── utils/          # Helpers, validators
├── features/
│   ├── auth/           # Login, token management
│   ├── demandes/       # M01: Leave, exits (max 4h), missions
│   ├── documents/      # M02: Admin documents with priority queue
│   ├── notes/          # M03: Service notes with read acknowledgment
│   ├── plaintes/       # M04: Complaints (text/voice)
│   ├── formation/      # M05: Training with hot/cold evaluations
│   ├── evaluations/    # M07: Reviews with AI color coding
│   ├── epi/            # M08: PPE catalog and attributions
│   ├── presence/       # M09: Attendance (QR + Face + GPS)
│   ├── recrutement/    # M10: ATS with CV matching
│   └── chatbot/        # M11: RH assistant
├── widgets/            # Reusable components
└── main.dart
```

**Critical Patterns**:
- Use `ConsumerWidget` / `ConsumerStatefulWidget` for Riverpod
- Repository pattern for data access
- AutoDispose providers for memory efficiency
- WebSocket reconnection logic with exponential backoff
- JWT token refresh before expiration

### Web Admin (React/TypeScript)

**Stack**:
- **React 18+** with Vite, TypeScript
- State Management: Zustand or Redux Toolkit
- UI: TailwindCSS + component library
- Charts: Recharts or Chart.js for dashboards
- Real-time: WebSocket client (SockJS + STOMP)
- File Upload: Axios with progress tracking

**Key Features**:
- Kanban view for document priority queue (M02)
- Color-coded evaluation gap matrix (M07 - 🟢🟠🔴)
- Executive dashboards with analytics (M12)
- Real-time notification bell via WebSocket
- Bulk actions for HR processing
- Organizational tree view: Department → Unit → RO → Employees
- GED interface with versioning and role-based access

---

## Non-Functional Requirements (v2.0)

| Category | Requirement | Target Value |
|----------|-------------|--------------|
| **Security** | JWT Authentication + Refresh Token | 15min access / 7 days refresh |
| **Security** | Granular RBAC roles and permissions | 5 distinct roles minimum |
| **Security** | Immutable audit log of all actions | 5-year retention |
| **Security** | Encryption of sensitive data in database | AES-256 |
| **Security** | RGPD compliance - right to be forgotten, consent | Mandatory |
| **Performance** | API response time | < 300ms at P95 |
| **Performance** | Mobile screen load time | < 2s on 4G |
| **Performance** | Redis cache for frequent data | Configurable TTL per entity |
| **Reliability** | Application availability | 99.5% SLA |
| **Reliability** | Network loss tolerance (offline mobile) | Local file queue with auto sync |
| **Reliability** | ACID transactions for financial/payroll data | Mandatory |
| **Real-time** | Mobile push notifications | < 5s delay |
| **Real-time** | WebSocket for live updates | STOMP over WebSocket |
| **Real-time** | Auto-refresh dashboards | 30s polling or WebSocket |
| **Scalability** | Horizontally scalable microservices | Kubernetes auto-scaling |
| **Storage** | GED - Documents in S3-compatible storage | AWS S3 or MinIO on-premise |
| **Storage** | Data retention period | 10 years (Tunisia legal compliance) |
| **UI/UX** | Cross-platform mobile app | Flutter (iOS + Android) |
| **UI/UX** | Responsive web portal | React + Tailwind |
| **UI/UX** | WCAG 2.1 Level AA accessibility | Mandatory |
| **AI** | LLM inference for chatbot and matching | < 3s per response |
| **AI** | ML models trained on-premise | Data never leaves SI |

---

## Critical Implementation Patterns

### 1. WebSocket Authentication (STOMP)

**Problem**: WebSocket handshake must include JWT token for authentication.

**Solution**: Use custom interceptor to add token as query parameter:

```dart
// lib/core/network/stomp_auth_interceptor.dart
class StompAuthInterceptor {
  final String Function() getToken;
  
  StompAuthInterceptor({required this.getToken});
  
  String buildWebSocketUrl(String baseUrl) {
    final token = getToken();
    return '$baseUrl?token=$token';
  }
}
```

**Backend Configuration**:
```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String token = accessor.getFirstNativeHeader("token");
                    // Validate JWT and set authentication
                }
                return message;
            }
        });
    }
}
```

### 2. Real-Time Notification Flow

**Architecture**:
```
User Action → svc-rh-plateforme → Kafka Event → svc-notification → WebSocket/FCM → Client
```

**Event Examples**:
- `rh.demandes.conge.soumise` - Leave request submitted
- `rh.demandes.conge.approuvee` - Leave request approved
- `rh.documents.prioritaire` - High-priority document alert
- `rh.plainte.nouvelle` - New complaint received
- `rh.presence.anomalie` - Attendance anomaly detected

**Frontend Subscription**:
```dart
final stompClient = StompClient(
  config: StompConfig.SockJS(
    url: wsUrl,
    onConnect: (frame) {
      stompClient.subscribe(
        destination: '/user/queue/notifications',
        callback: (message) {
          final notification = Notification.fromJson(jsonDecode(message.body));
          ref.read(notificationProvider.notifier).add(notification);
        },
      );
    },
  ),
);
```

### 3. Priority Queue for Documents (SLA-Based)

**Backend Algorithm**:
```java
@Component
public class DocumentPriorityService {
    
    public int calculatePriorityScore(DocumentRequest doc) {
        long hoursElapsed = Duration.between(doc.getCreatedAt(), Instant.now()).toHours();
        int slaHours = getSLAForType(doc.getType()); // e.g., 48h for salary cert
        int remainingHours = slaHours - hoursElapsed;
        
        if (remainingHours <= 0) return Integer.MAX_VALUE; // Overdue
        return slaHours - remainingHours; // Higher score = higher priority
    }
    
    public List<DocumentRequest> getPriorityQueue() {
        return repository.findAll().stream()
            .sorted(Comparator.comparingInt(this::calculatePriorityScore).reversed())
            .collect(Collectors.toList());
    }
}
```

### 4. Facial Recognition for Attendance

**Mobile Implementation**:
```dart
import 'package:google_mlkit_face_detection/google_mlkit_face_detection.dart';

class FaceVerificationService {
  final FaceDetector detector = FaceDetector(
    options: FaceDetectorOptions(
      enableContours: true,
      enableClassification: true,
      performanceMode: FaceDetectorMode.accurate,
    ),
  );
  
  Future<bool> verifyFace(InputImage image, String userId) async {
    final faces = await detector.processImage(image);
    if (faces.isEmpty) return false;
    
    // Compare face embedding with stored reference
    final storedEmbedding = await _getStoredEmbedding(userId);
    final currentEmbedding = _extractEmbedding(faces.first);
    
    return _calculateSimilarity(storedEmbedding, currentEmbedding) > 0.85;
  }
}
```

**Workflow**:
1. User scans dynamic QR code (validates location/time)
2. App captures frontal face photo
3. ML Kit detects face and extracts features
4. Compare with registered face embedding (stored securely)
5. If match > 85% + QR valid → Record attendance
6. Send to `svc-presence` via API

### 5. Kafka Event Publishing

**Backend Publisher**:
```java
@Service
@RequiredArgsConstructor
public class DemandeEventPublisher {
    
    private final KafkaTemplate<String, DemandeEvent> kafkaTemplate;
    
    public void publishDemandeSoumise(Demande demande) {
        DemandeEvent event = DemandeEvent.builder()
            .type("DEMANDE_SOUMISE")
            .demandeId(demande.getId())
            .demandeurId(demande.getEmployeId())
            .typeDemande(demande.getType())
            .timestamp(Instant.now())
            .build();
        
        kafkaTemplate.send("rh-demandes-events", demande.getId().toString(), event);
    }
}
```

**Configuration**:
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
```

### 6. Riverpod State Management Pattern

**Repository Provider**:
```dart
// lib/features/demandes_admin/data/demande_repository.dart
final demandeRepositoryProvider = Provider<DemandeRepository>((ref) {
  final dio = ref.watch(dioProvider);
  return DemandeRepository(dio);
});

// Async notifier for list state
class DemandeListNotifier extends AsyncNotifier<List<DemandeItem>> {
  @override
  Future<List<DemandeItem>> build() async {
    return ref.read(demandeRepositoryProvider).getAll();
  }
  
  Future<void> submitDemande(DemandeCreateRequest request) async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      await ref.read(demandeRepositoryProvider).create(request);
      return ref.read(demandeRepositoryProvider).getAll();
    });
  }
}

final demandeListProvider = AsyncNotifierProvider<DemandeListNotifier, List<DemandeItem>>(() {
  return DemandeListNotifier();
});
```

**Usage in Widget**:
```dart
class DemandeListScreen extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncDemandes = ref.watch(demandeListProvider);
    
    return asyncDemandes.when(
      loading: () => CircularProgressIndicator(),
      error: (err, stack) => ErrorWidget(err),
      data: (demandes) => ListView.builder(
        itemCount: demandes.length,
        itemBuilder: (ctx, i) => DemandeCard(demande: demandes[i]),
      ),
    );
  }
}
```

### 7. Evaluation Gap Matrix Visualization

**Color Logic**:
```dart
Color getGapColor(int employeeRating, int managerRating) {
  final gap = (employeeRating - managerRating).abs();
  
  if (gap <= 1) return Colors.green;      // Aligned
  if (gap == 2) return Colors.orange;     // Moderate gap
  return Colors.red;                       // Significant gap
}
```

**UI Component**:
```dart
Container(
  padding: EdgeInsets.all(16),
  decoration: BoxDecoration(
    color: getGapColor(empRating, mgrRating).withOpacity(0.1),
    borderRadius: BorderRadius.circular(12),
    border: Border.all(color: getGapColor(empRating, mgrRating)),
  ),
  child: Column(
    children: [
      Text('Employee: $empRating'),
      Text('Manager: $mgrRating'),
      Icon(
        getGapColor(empRating, mgrRating) == Colors.green
          ? Icons.check_circle
          : Icons.warning,
        color: getGapColor(empRating, mgrRating),
      ),
    ],
  ),
)
```

---

## API Conventions

### REST Endpoints Structure

```
/api/v1/{module}/{resource}

Examples:
POST   /api/v1/demandes/conges              # Submit leave request
GET    /api/v1/demandes/conges/mes-demandes # My leave requests
PUT    /api/v1/demandes/conges/{id}/valider # Approve request
DELETE /api/v1/demandes/conges/{id}         # Cancel request

GET    /api/v1/documents/prioritaires       # Priority queue
POST   /api/v1/plaintes                     # Submit complaint
GET    /api/v1/epi/catalogue                # PPE catalog
POST   /api/v1/presence/pointage            # Record attendance
```

### Standard Response Format

```json
{
  "success": true,
  "data": { ... },
  "message": "Operation successful",
  "timestamp": "2026-05-08T10:30:00Z"
}
```

### Error Response Format

```json
{
  "success": false,
  "error": {
    "code": "DEMANDE_NOT_FOUND",
    "message": "Demande with ID 123 not found",
    "details": { ... }
  },
  "timestamp": "2026-05-08T10:30:00Z"
}
```

### Common HTTP Status Codes

- `200 OK` - Successful GET/PUT
- `201 Created` - Successful POST
- `204 No Content` - Successful DELETE
- `400 Bad Request` - Validation error
- `401 Unauthorized` - Missing/invalid JWT
- `403 Forbidden` - Insufficient permissions
- `404 Not Found` - Resource doesn't exist
- `409 Conflict` - Business rule violation (e.g., overlapping leave)
- `422 Unprocessable Entity` - Semantic errors
- `500 Internal Server Error` - Unexpected error

---

## Security & Authentication

### JWT Token Flow

1. User logs in via `/api/v1/auth/login`
2. Backend validates credentials against `svc-identite-acces`
3. Returns JWT with claims: `sub`, `email`, `roles`, `employeId`
4. Store token securely:
   - **Mobile**: `flutter_secure_storage`
   - **Web**: HttpOnly cookie or secure storage
5. Include token in all API requests:
   ```
   Authorization: Bearer <jwt_token>
   ```
6. Token expires after configured time (e.g., 1 hour)
7. Refresh token mechanism for seamless UX

### Role-Based Access Control (RBAC)

**Roles**:
- `EMPLOYE` - Submit requests, view own data
- `CHEF_SERVICE` - Validate team requests, group PPE requests
- `RH` - Process requests, manage configurations, dashboards
- `RRH` - Same as RH + final approvals
- `DIRECTION` - Publish service notes, strategic validations
- `HSE` - Manage PPE catalog and stock
- `TECHNIQUE` - Handle external complaints
- `ENV_SOCIAL` - Handle external complaints (Environment/Social)

**Example Security Config**:
```java
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/api/v1/demandes/**").hasAnyRole("EMPLOYE", "CHEF_SERVICE", "RH")
                .requestMatchers("/api/v1/epi/**").hasAnyRole("HSE", "RH", "CHEF_SERVICE")
                .requestMatchers("/api/v1/admin/**").hasRole("RH")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        
        return http.build();
    }
}
```

---

## Database Schema Guidelines

### Naming Conventions

- **Tables**: snake_case, plural (e.g., `demandes_conges`, `employes`)
- **Columns**: snake_case (e.g., `date_debut`, `statut_validation`)
- **Foreign Keys**: `{table}_id` (e.g., `employe_id`, `service_id`)
- **Indexes**: Add on frequently queried columns and foreign keys

### Key Entities (Conceptual)

**Employé (Employee)**:
```sql
CREATE TABLE employes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    matricule VARCHAR(50) UNIQUE NOT NULL,
    nom VARCHAR(100) NOT NULL,
    prenom VARCHAR(100) NOT NULL,
    email_professionnel VARCHAR(255) UNIQUE NOT NULL,
    poste VARCHAR(200),
    fonction VARCHAR(200),
    service_id UUID REFERENCES services(id),
    superieur_id UUID REFERENCES employes(id),
    date_recrutement DATE,
    statut VARCHAR(20) DEFAULT 'ACTIF',
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
```

**Demande Congé (Leave Request)**:
```sql
CREATE TABLE demandes_conges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employe_id UUID NOT NULL REFERENCES employes(id),
    type_conge VARCHAR(50) NOT NULL,
    date_debut DATE NOT NULL,
    date_fin DATE NOT NULL,
    duree_jours INTEGER,
    motif TEXT,
    statut VARCHAR(30) DEFAULT 'EN_ATTENTE_VALIDATION',
    valideur_niveau1_id UUID REFERENCES employes(id),
    valideur_niveau2_id UUID REFERENCES employes(id),
    commentaire_refus TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_demandes_employe ON demandes_conges(employe_id);
CREATE INDEX idx_demandes_statut ON demandes_conges(statut);
```

### Migration Strategy

Use Flyway or Liquibase for versioned migrations:
```
db/migration/
├── V1__Initial_schema.sql
├── V2__Add_demandes_conges.sql
├── V3__Add_plaintes_module.sql
└── V4__Add_indexes_performance.sql
```

---

## Testing Strategy

### Backend Testing

**Unit Tests**:
```java
@SpringBootTest
class DemandeServiceTest {
    
    @Autowired
    private DemandeService demandeService;
    
    @Test
    void shouldSubmitLeaveRequest() {
        DemandeCreateRequest request = new DemandeCreateRequest();
        request.setType("CONGE");
        request.setDateDebut(LocalDate.now().plusDays(5));
        
        Demande result = demandeService.submit(request);
        
        assertNotNull(result.getId());
        assertEquals("EN_ATTENTE_VALIDATION", result.getStatut());
    }
}
```

**Integration Tests**:
- Test full workflow: Submit → Validate → Notify
- Use Testcontainers for PostgreSQL + Kafka
- Mock external services (email, FCM)

### Mobile Testing

**Widget Tests**:
```dart
testWidgets('DemandeCard displays correct status', (tester) async {
  final demande = DemandeItem(
    id: '123',
    typeDemande: 'CONGE',
    statut: 'APPROUVEE',
    periodeDebut: '2026-05-10',
  );
  
  await tester.pumpWidget(
    MaterialApp(home: DemandeCard(d: demande)),
  );
  
  expect(find.text('Approuvée'), findsOneWidget);
  expect(find.byIcon(Icons.check_circle_outline_rounded), findsOneWidget);
});
```

**Integration Tests**:
- Test API calls with mock server
- Test WebSocket reconnection
- Test offline mode with cached data

---

## Deployment & DevOps

### Docker Compose (Development)

```yaml
version: '3.8'
services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: rh_db
      POSTGRES_USER: rh_user
      POSTGRES_PASSWORD: secret
    ports:
      - "5432:5432"
  
  kafka:
    image: confluentinc/cp-kafka:latest
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
  
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
  
  minio:
    image: minio/minio
    command: server /data
    ports:
      - "9000:9000"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
```

### Production Considerations

- **High Availability**: Multiple instances per service behind load balancer
- **Database**: Managed PostgreSQL with automatic backups (PITR)
- **Kafka**: Cluster with replication factor ≥ 3
- **Monitoring**: Prometheus + Grafana for metrics, ELK for logs
- **Tracing**: Distributed tracing with Jaeger or Zipkin
- **CI/CD**: GitHub Actions or GitLab CI for automated builds and deployments

---

## Common Pitfalls & Solutions

### 1. WebSocket Connection Drops

**Problem**: Mobile devices lose WebSocket connection when app goes to background.

**Solution**: Implement reconnection with exponential backoff:
```dart
void connectWithRetry() {
  int retryCount = 0;
  const maxRetries = 10;
  
  void attemptConnect() {
    if (retryCount >= maxRetries) return;
    
    stompClient.activate();
    
    stompClient.onDisconnect = (frame) {
      retryCount++;
      final delay = Duration(seconds: pow(2, retryCount).toInt());
      Future.delayed(delay, attemptConnect);
    };
  }
  
  attemptConnect();
}
```

### 2. JWT Token Expiration During Long Operations

**Problem**: Token expires while user is filling a long form.

**Solution**: Check token expiry before critical operations:
```dart
Future<void> ensureValidToken() async {
  final token = await secureStorage.read(key: 'jwt_token');
  final decoded = JwtDecoder.decode(token!);
  final expiry = DateTime.fromMillisecondsSinceEpoch(decoded['exp'] * 1000);
  
  if (expiry.isBefore(DateTime.now().add(Duration(minutes: 5)))) {
    await refreshToken();
  }
}
```

### 3. Kafka Message Duplication

**Problem**: Network issues cause duplicate event processing.

**Solution**: Implement idempotency checks:
```java
@KafkaListener(topics = "rh-demandes-events")
public void handleEvent(DemandeEvent event) {
    if (processedEvents.contains(event.getId())) {
        log.warn("Duplicate event ignored: {}", event.getId());
        return;
    }
    
    processEvent(event);
    processedEvents.add(event.getId());
}
```

### 4. Large File Uploads Timeout

**Problem**: Uploading large PDFs or videos times out.

**Solution**: Use multipart upload with progress tracking:
```dart
Future<void> uploadFile(File file) async {
  final formData = FormData.fromMap({
    'file': await MultipartFile.fromFile(file.path),
  });
  
  final response = await dio.post(
    '/api/v1/upload',
    data: formData,
    onSendProgress: (sent, total) {
      final progress = sent / total * 100;
      updateProgress(progress);
    },
    options: Options(
      sendTimeout: Duration(minutes: 5),
      receiveTimeout: Duration(minutes: 5),
    ),
  );
}
```

### 5. Flutter Build Errors with Native Plugins

**Problem**: Android/iOS build fails after adding native plugins.

**Solution**:
```bash
# Clean build artifacts
flutter clean
cd android && ./gradlew clean && cd ..

# Update dependencies
flutter pub get

# Rebuild
flutter build apk --release
```

Common fixes:
- Update `compileSdkVersion` to 34+ in `android/app/build.gradle.kts`
- Add `afterEvaluate` block for plugin compatibility
- Check plugin compatibility with Flutter version

---

## Performance Optimization

### Backend

1. **Database Indexing**: Add indexes on foreign keys and frequently filtered columns
2. **Connection Pooling**: Configure HikariCP properly (max pool size = CPU cores × 2 + 1)
3. **Caching**: Use Redis for reference data (employee list, PPE catalog)
4. **Pagination**: Always paginate large lists (default page size: 20-50)
5. **Async Processing**: Offload heavy tasks (PDF generation, email sending) to background threads

### Mobile

1. **Image Optimization**: Compress images before upload, use thumbnails in lists
2. **Lazy Loading**: Load data on scroll, not all at once
3. **State Management**: Use `autoDispose` providers to free memory
4. **Offline Support**: Cache critical data with Hive or SQLite
5. **Battery Efficiency**: Limit location updates, batch network requests

### Web

1. **Code Splitting**: Lazy load routes with React.lazy()
2. **Bundle Optimization**: Tree-shake unused code, compress assets
3. **Virtual Scrolling**: For large tables/lists (react-window)
4. **Debouncing**: Debounce search inputs and filters
5. **Memoization**: Use React.memo and useMemo for expensive calculations

---

## Monitoring & Observability

### Key Metrics to Track

**Backend**:
- API response times (P50, P95, P99)
- Error rates by endpoint
- Kafka consumer lag
- Database connection pool usage
- WebSocket active connections

**Mobile**:
- App crash rate (Firebase Crashlytics)
- API call success/failure rates
- Average screen load time
- Offline mode usage

**Business**:
- Average request processing time by type
- SLA compliance rate
- User adoption rate (% active users)
- Complaint resolution time

### Logging Standards

```java
@Slf4j
@Service
public class DemandeService {
    
    public Demande submit(DemandeRequest request) {
        log.info("Submitting demande for employee: {}", request.getEmployeId());
        
        try {
            Demande demande = repository.save(mapToEntity(request));
            log.info("Demande created successfully: {}", demande.getId());
            return demande;
        } catch (Exception e) {
            log.error("Failed to submit demande", e);
            throw new BusinessException("DEMANDE_SUBMIT_FAILED", e);
        }
    }
}
```

**Log Levels**:
- `ERROR`: System failures, exceptions
- `WARN`: Degraded performance, retries
- `INFO`: Business events (demande submitted, validated)
- `DEBUG`: Detailed flow tracing (dev only)

---

## Future Enhancements (Roadmap)

### Phase 1 (Immediate)
- Complete M1-M9 implementation
- WebSocket real-time notifications
- Mobile app polish and testing
- Admin dashboard MVP

### Phase 2 (Short-term)
- AI Chatbot for HR queries (RAG-based)
- Voice complaint submission with Speech-to-Text
- Advanced analytics dashboard
- Export reports (Excel, PDF)

### Phase 3 (Medium-term)
- Facial recognition for attendance (M9)
- Intelligent ATS with CV parsing (M10)
- Sentiment analysis for complaints
- Predictive analytics for HR trends

### Phase 4 (Long-term)
- Integration with payroll systems
- Electronic signature for documents
- Multi-language support
- Mobile app iOS version

---

## Quick Reference Commands

### Backend Development

```bash
# Run specific service
mvn spring-boot:run -pl svc-identite-acces

# Run all services (from root)
mvn spring-boot:run

# Build JAR
mvn clean package -DskipTests

# Run tests
mvn test

# Database migration
mvn flyway:migrate
```

### Mobile Development

```bash
# Get dependencies
flutter pub get

# Run on emulator
flutter run

# Build APK
flutter build apk --release

# Build AAB (for Play Store)
flutter build appbundle --release

# Analyze code
flutter analyze

# Format code
flutter format lib/

# Run tests
flutter test
```

### Web Development

```bash
# Install dependencies
npm install

# Run dev server
npm run dev

# Build for production
npm run build

# Type checking
npm run type-check

# Linting
npm run lint
```

### Infrastructure

```bash
# Start infrastructure (Docker Compose)
docker-compose -f docker-compose.infra.yml up -d

# Start Kafka
docker-compose -f docker-compose.kafka.yml up -d

# View logs
docker-compose logs -f svc-rh-plateforme

# Stop all
docker-compose down
```

---

## Support & Resources

### Documentation
- Architecture docs: `docs/architecture/`
- Business conception: `docs/conception/`
- API specs: Swagger UI at `http://localhost:8080/swagger-ui.html`

### Key Contacts
- Backend Lead: [Contact Info]
- Mobile Lead: [Contact Info]
- Product Owner: [Contact Info]

### Useful Links
- Flutter Docs: https://flutter.dev/docs
- Spring Boot Docs: https://spring.io/projects/spring-boot
- Riverpod Docs: https://riverpod.dev
- Kafka Docs: https://kafka.apache.org/documentation

---

*This skill is continuously updated. Last reviewed: May 2026*
