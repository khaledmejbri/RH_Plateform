# Skill Refactoring Summary - RH-Évènement v2.0

## Overview
Successfully refactored the `rh-platform-development` skill and API reference based on the authoritative **SIRH_Eh_Evenement_CDC_v2.md** cahier des charges for AGUA Service d'Assainissement.

## Key Changes Made

### 1. Architecture Updates
**Before**: Minimized approach with single `svc-rh-plateforme`  
**After**: 12 specialized microservices matching v2.0 architecture

| Old Service | New Services |
|------------|--------------|
| `svc-identite-acces` | Replaced with **Keycloak** (OAuth2/OIDC) |
| `svc-rh-plateforme` | Split into: `svc-referentiel-rh`, `svc-demandes`, `svc-formation`, `svc-evaluation`, `svc-plainte`, `svc-epi`, `svc-presence`, `svc-document`, `svc-recrutement` |
| `svc-notification` | Kept (transversal service) |
| N/A | Added `svc-ia` (Python FastAPI + LangChain) |

### 2. Module Numbering & Naming
**Before**: M1-M9 generic modules  
**After**: M01-M12 with precise naming per CDC v2.0

- M01.A: Congé (Leave)
- M01.B: Autorisation de Sortie (Exit Permit - max 4h, NOT a leave)
- M01.C: Ordre de Mission
- M02: Documents Administratifs
- M03: Notes de Service
- M04: Gestion des Plaintes (Internal vs External distinction)
- M05: Formation & Évaluation (Hot J+1, Cold M+3)
- M06: Besoin en Personnel
- M07: Évaluations RH (Semi-annual + Annual with AI color coding 🟢🟠🔴)
- M08: EPI Distribution & Tracking
- M09: Présence (QR + Facial Recognition + GPS - Anti-fraud)
- M10: Recrutement IA & ATS
- M11: Chatbot RH & Assistant IA
- M12: Tableaux de Bord & Analytics

### 3. Critical Business Logic Corrections

#### Exit Permit (Autorisation de Sortie)
- **Before**: Treated as a leave type with enum `type_sortie`
- **After**: Separate module M01.B with strict 4-hour max validation
  - Client-side AND server-side duration validation
  - NO deduction from leave balance
  - Real-time duration indicator in mobile UI

#### Attendance (M09) - Anti-Fraud System
- **Before**: Simple QR scan with optional face verification
- **After**: Mandatory two-step sequential process:
  1. QR Code scan (TOTP renewed every 5min) + GPS validation (configurable radius)
  2. Facial recognition (on-device ML, liveness detection, 92% threshold default)
  - If either fails → REJECTED
  - 3 consecutive failures → 15-minute account block + RO alert
  - Cryptographically signed timestamps for irrefutability

#### Complaints (M04)
- **Before**: Single complaint type
- **After**: Clear distinction:
  - Internal: Collaborator → RH only
  - External: RO/RH/Technique → Technical Services + Environment/Social Direction + RH (simultaneous)
  - Voice complaints with Whisper transcription

#### Evaluations (M07)
- **Before**: Generic evaluation with gap matrix
- **After**: 
  - Semi-annual: 4 criteria (Quality, Performance, Punctuality, Team Spirit) scored 1-5
  - Annual: 3 domains (Knowledge, Know-how, Soft skills) with objectives N+1
  - AI color coding: 🟢 (all excellent), 🟠 (2 below threshold), 🔴 (3+ insufficient → mandatory action plan + DG escalation)

### 4. User Roles Updated
**Before**: EMPLOYE, CHEF_SERVICE, RH, RRH, DIRECTION, HSE, TECHNIQUE, ENV_SOCIAL  
**After**: COLLABORATEUR, RESPONSABLE_OPERATIONNEL (RO), CHEF_DEPARTEMENT, RESPONSABLE_RH (RRH), HSE, DIRECTION_GÉNÉRALE

### 5. Technology Stack Updates
- **Java**: 17 → **21**
- **PostgreSQL**: Generic → **16**
- **Authentication**: Spring Security → **Keycloak** (OAuth2/OIDC)
- **Mobile**: Android-only → **Cross-platform iOS/Android** (Flutter)
- **AI**: Future → **Production-ready** (Python FastAPI + LangChain + Ollama)
- **OCR**: N/A → **Tesseract** (CV parsing)
- **Audio**: N/A → **OpenAI Whisper** (voice transcription)
- **Service Discovery**: Eureka → **Kubernetes native**

### 6. Security Enhancements
- JWT access token: Generic → **15 minutes** expiration
- Refresh token: N/A → **7 days** expiration
- Audit log: Generic → **5-year immutable retention**
- Encryption: Generic → **AES-256** for sensitive data
- Biometric data: Server-stored → **100% on-device** (RGPD compliant)
- Data retention: Generic → **10 years** (Tunisia legal compliance)

### 7. Non-Functional Requirements Added
Comprehensive table with targets:
- Performance: API < 300ms P95, Mobile < 2s on 4G
- Reliability: 99.5% SLA, offline-first mobile with auto-sync
- Real-time: Push < 5s, WebSocket STOMP
- Scalability: Kubernetes auto-scaling
- Accessibility: WCAG 2.1 Level AA mandatory

### 8. API Endpoints Restructured
- Base path simplified: `/api/v1/{module}` → `/{module}` (gateway handles versioning)
- Added detailed anti-fraud validation examples for M09
- Added exit permit 4h validation code examples (backend + Flutter)
- New error codes: `SORTIE_DUREE_EXCEEDEE`, `PRESENCE_GPS_HORS_ZONE`, `PRESENCE_FACIAL_ECHEC`, `PRESENCE_BLOQUE_TEMPORAIREMENT`, etc.

### 9. AI Features Documented
- **M02 Chatbot**: RAG on historical RH comments/responses
- **M07 AI Color Coding**: Automatic pattern detection and recommendations
- **M10 ATS**: CV parsing + intelligent job matching (0-100% score)
- **M11 Chatbot**: RH FAQ + request history + voice transcription
- **M12 Predictive Analytics**: Absenteeism prediction, resignation risk, training plan recommendations

### 10. Scheduler Implementations
- **M05.B Hot Evaluation**: Triggered 24h after training end + 48h reminder if no response
- **M05.C Cold Evaluation**: Triggered 3 months after training
- **M12 Monthly Report**: Auto-generated PDF emailed to DG on 1st of month

## Files Modified

1. **SKILL.md** (C:\Local\Khaled\project\.lingma\skills\rh-platform-development\SKILL.md)
   - Updated description to mention v2.0 and AGUA Service d'Assainissement
   - Replaced architecture section with 12 specialized services
   - Updated all 12 modules with v2.0 specifications
   - Added non-functional requirements table
   - Updated technology stack (Java 21, Keycloak, etc.)
   - Enhanced user roles section

2. **API_REFERENCE.md** (C:\Local\Khaled\project\.lingma\skills\rh-platform-development\API_REFERENCE.md)
   - Updated authentication endpoints (15min/7days tokens)
   - Separated M01 into A/B/C with exit permit 4h validation
   - Added detailed M09 anti-fraud two-step process with rejection examples
   - Updated error codes with new v2.0 codes
   - Simplified endpoint paths (removed /api/v1 prefix)

## Impact on Development

When you now ask for help with the RH platform, I will automatically apply:
- ✅ Correct microservice boundaries (12 services, not monolith)
- ✅ Exit permit max 4h validation logic
- ✅ Two-step attendance anti-fraud process
- ✅ Internal vs external complaint routing
- ✅ AI color coding for evaluations
- ✅ Proper role names (COLLABORATEUR, RO, etc.)
- ✅ v2.0 security requirements (15min JWT, AES-256, etc.)
- ✅ Scheduler patterns for training evaluations
- ✅ RGPD-compliant biometric handling (on-device only)

## Next Steps Recommended

1. **Update existing code** to match v2.0 specifications:
   - Refactor exit permit validation (remove `type_sortie` enum, add 4h check)
   - Implement two-step attendance flow (QR+GPS → Facial)
   - Separate internal/external complaint workflows
   - Add AI color coding to evaluation dashboard

2. **Database migrations**:
   - Add `profil_acces` column to users table (was missing in v1.0)
   - Create separate schemas per service if using single PostgreSQL instance
   - Add HMAC signature field to attendance records

3. **Mobile app updates**:
   - Add geolocator package for GPS validation
   - Implement liveness detection for facial recognition
   - Add offline queue with Hive for network loss tolerance
   - Update role checks to use new role names

4. **Infrastructure**:
   - Deploy Keycloak for OAuth2/OIDC
   - Set up Kubernetes cluster for auto-scaling
   - Configure MinIO or AWS S3 for GED
   - Set up Apache Kafka cluster for event streaming

---

**Refactoring completed**: May 8, 2026  
**Based on**: SIRH_Eh_Evenement_CDC_v2.md (AGUA Service d'Assainissement)  
**Version**: v2.0 - Ready for Production
