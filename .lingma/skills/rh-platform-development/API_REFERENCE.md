# API Reference & Endpoint Documentation - RH-Évènement v2.0

**Base URL**: `https://api.agua-rh.tn/api/v1`  
**Authentication**: JWT Bearer Token (15min expiry) + Refresh Token (7 days)  
**Content-Type**: `application/json` (unless multipart/form-data for uploads)

---

## Authentication Endpoints

### Login
```http
POST /auth/login
Content-Type: application/json

{
  "username": "employee.matricule",
  "password": "securePassword123"
}

Response:
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4...",
    "expiresIn": 900,
    "refreshExpiresIn": 604800,
    "user": {
      "id": "uuid-here",
      "email": "employee@agua.tn",
      "roles": ["COLLABORATEUR"],
      "employeId": "uuid-employe",
      "name": "Doe",
      "prenom": "John"
    }
  }
}
```

**Available Roles**: COLLABORATEUR, RESPONSABLE_OPERATIONNEL, CHEF_DEPARTEMENT, RESPONSABLE_RH, HSE, DIRECTION

### Refresh Token
```http
POST /api/v1/auth/refresh
Content-Type: application/json

{
  "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4..."
}
```

### Logout
```http
POST /api/v1/auth/logout
Authorization: Bearer <token>
```

---

## M01.A: Leave Requests (Congé)

### Submit Leave Request
```http
POST /demandes/conges
Authorization: Bearer <token>
Content-Type: application/json

{
  "typeConge": "CONGE_ANNUEL",
  "dateDebut": "2026-05-15",
  "dateFin": "2026-05-20",
  "motif": "Vacances familiales",
  "piecesJointes": ["uuid-file1", "uuid-file2"] // Optional, mandatory for sick leave
}

Response: 201 Created
{
  "success": true,
  "data": {
    "id": "uuid-demande",
    "statut": "EN_ATTENTE_VALIDATION_RO",
    "dateSoumission": "2026-05-08T10:30:00Z",
    "soldeRestant": 15.5
  }
}
```

**Validation Rules**:
- Minimum delay before departure configurable (e.g., 48h for annual leave)
- Mandatory medical certificate for sick leave (`typeConge = MALADIE`)
- Balance check against available leave days

## M01.B: Exit Permit (Autorisation de Sortie) - NOT A LEAVE

⚠ **Critical**: Max 4 hours duration, NOT deducted from leave balance

```http
POST /demandes/autorisations-sortie
Authorization: Bearer <token>
Content-Type: application/json

{
  "date": "2026-05-10",
  "heureDebut": "14:00",
  "heureFin": "16:00",
  "motif": "Rendez-vous médical"
}

Response: 201 Created
{
  "success": true,
  "data": {
    "id": "uuid-demande",
    "statut": "EN_ATTENTE_VALIDATION_RO",
    "dureeHeures": 2.0
  }
}
```

**Server-Side Validation**:
```java
// Backend validation in svc-demandes
if (heureFin.minusHours(4).isBefore(heureDebut)) {
    throw new BusinessException("DUREE_EXCEEDEE", "Durée maximale 4 heures");
}
```

**Client-Side Validation** (Flutter):
```dart
final duration = heureFin.difference(heureDebut);
if (duration.inHours > 4) {
  showError('Durée maximale: 4 heures');
  return;
}
```

### Submit Mission Order
```http
POST /api/v1/demandes/missions
Authorization: Bearer <token>
Content-Type: multipart/form-data

{
  "lieu": "Paris, France",
  "dateDebut": "2026-06-01",
  "dateFin": "2026-06-05",
  "objectif": "Formation technique",
  "fraisEstimes": 1500.00,
  "piecesJointes": [file1.pdf, file2.docx]
}
```

### Get My Demands
```http
GET /api/v1/demandes/mes-demandes?page=0&size=20&statut=EN_COURS
Authorization: Bearer <token>

Response:
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "uuid",
        "typeDemande": "CONGE",
        "statut": "EN_ATTENTE_VALIDATION",
        "periodeDebut": "2026-05-15",
        "periodeFin": "2026-05-20",
        "dateSoumission": "2026-05-08T10:30:00Z"
      }
    ],
    "pageable": {
      "page": 0,
      "size": 20,
      "totalElements": 45,
      "totalPages": 3
    }
  }
}
```

### Validate Demand (Manager/RH)
```http
PUT /api/v1/demandes/{id}/valider
Authorization: Bearer <token>
Content-Type: application/json

{
  "action": "APPROUVER", // or "REJETER"
  "commentaire": "Demande approuvée, bonnes vacances!"
}
```

### Cancel Demand
```http
DELETE /api/v1/demandes/{id}
Authorization: Bearer <token>
```

---

## M2: Administrative Documents

### Request Document
```http
POST /api/v1/documents
Authorization: Bearer <token>
Content-Type: application/json

{
  "typeDocument": "ATTESTATION_TRAVAIL",
  "urgence": "NORMAL", // NORMAL, URGENT, TRES_URGENT
  "commentaire": "Needed for bank loan application"
}
```

### Get Priority Queue (RH Only)
```http
GET /api/v1/documents/prioritaires
Authorization: Bearer <token>
Role: RH or RRH

Response: Sorted by SLA urgency
[
  {
    "id": "uuid",
    "typeDocument": "ATTESTATION_SALAIRE",
    "demandeur": "John Doe",
    "dateDemande": "2026-05-01T09:00:00Z",
    "slaDeadline": "2026-05-03T09:00:00Z",
    "hoursRemaining": 4,
    "priorityScore": 95
  }
]
```

### Process Document (RH)
```http
PUT /api/v1/documents/{id}/traiter
Authorization: Bearer <token>
Content-Type: multipart/form-data

{
  "action": "APPROUVER",
  "documentFile": [attestation.pdf],
  "commentaire": "Document prêt à récupérer au bureau RH"
}
```

---

## M3: Service Notes

### Publish Note (Direction/RH)
```http
POST /api/v1/notes-service
Authorization: Bearer <token>
Content-Type: multipart/form-data

{
  "titre": "Nouvelle politique de télétravail",
  "contenu": "À partir du 1er juin...",
  "destinataires": "TOUS", // TOUS, PAR_SERVICE, SPECIFIQUE
  "services": ["IT", "RH"], // if PAR_SERVICE
  "piecesJointes": [policy.pdf]
}
```

### Get All Notes
```http
GET /api/v1/notes-service?page=0&size=10
Authorization: Bearer <token>
```

### Acknowledge Reading
```http
POST /api/v1/notes-service/{id}/accuser-reception
Authorization: Bearer <token>
```

### Get Reading Statistics (RH/Direction)
```http
GET /api/v1/notes-service/{id}/statistiques-lecture
Authorization: Bearer <token>

Response:
{
  "totalDestinataires": 150,
  "lus": 120,
  "nonLus": 30,
  "tauxLecture": 80.0
}
```

---

## M4: Complaints

### Submit Internal Complaint
```http
POST /api/v1/plaintes
Authorization: Bearer <token>
Content-Type: multipart/form-data

{
  "type": "INTERNE",
  "categorie": "HARCELEMENT",
  "description": "Description détaillée de la plainte...",
  "mediaFiles": [photo1.jpg, video.mp4] // optional
}
```

### Submit External Complaint (Manager Only)
```http
POST /api/v1/plaintes/externes
Authorization: Bearer <token>
Role: CHEF_SERVICE or higher

{
  "type": "EXTERNE",
  "source": "Communauté locale",
  "categorie": "ENVIRONNEMENT",
  "description": "Plainte reçue concernant...",
  "mediaFiles": [...]
}
```

### Update Complaint Status
```http
PUT /api/v1/plaintes/{id}/statut
Authorization: Bearer <token>
Content-Type: application/json

{
  "nouveauStatut": "EN_TRAITEMENT",
  "commentaire": "Enquête en cours"
}
```

### Get Complaint Dashboard
```http
GET /api/v1/plaintes/tableau-de-bord?periode=MOIS_COURANT
Authorization: Bearer <token>
Role: RH, DIRECTION

Response:
{
  "total": 25,
  "parStatut": {
    "NOUVEAU": 5,
    "EN_ANALYSE": 8,
    "EN_TRAITEMENT": 7,
    "RESOLU": 4,
    "FERME": 1
  },
  "parCategorie": {
    "HARCELEMENT": 3,
    "DISCRIMINATION": 2,
    "CONDITIONS_TRAVAIL": 10,
    "AUTRE": 10
  }
}
```

---

## M5: Training

### Submit Training Need
```http
POST /api/v1/formation/besoins
Authorization: Bearer <token>

{
  "theme": "Leadership Management",
  "justification": "Besoin de développer les compétences...",
  "publicCible": "Chefs de service",
  "dureeSouhaitee": "3 jours"
}
```

### Create Training Session (RH)
```http
POST /api/v1/formation/sessions
Authorization: Bearer <token>
Role: RH

{
  "titre": "Formation Leadership - Session 1",
  "formateur": "Cabinet XYZ",
  "dateDebut": "2026-06-15T09:00:00Z",
  "dateFin": "2026-06-17T17:00:00Z",
  "lieu": "Salle de conférence A",
  "placesDisponibles": 20,
  "participantsIds": ["uuid1", "uuid2", ...]
}
```

### Confirm Participation
```http
POST /api/v1/formation/sessions/{sessionId}/confirmer
Authorization: Bearer <token>

{
  "participantId": "uuid-employe",
  "confirmation": "OUI" // or "NON"
}
```

### Submit Hot Evaluation (24h after training)
```http
POST /api/v1/formation/evaluations/chaud
Authorization: Bearer <token>

{
  "sessionId": "uuid-session",
  "questions": [
    {
      "questionId": "q1",
      "reponse": 4, // scale 1-5
      "commentaire": "Très bonne formation"
    }
  ]
}
```

### Submit Cold Evaluation (3 months after)
```http
POST /api/v1/formation/evaluations/froid
Authorization: Bearer <token>

{
  "sessionId": "uuid-session",
  "impactMesure": {
    "applicationCompetences": 4,
    "ameliorationPerformance": 3,
    "retourSurInvestissement": "POSITIF"
  }
}
```

---

## M7: Evaluations

### Start Evaluation Campaign (RH)
```http
POST /api/v1/evaluations/campagnes
Authorization: Bearer <token>
Role: RH

{
  "type": "ANNUELLE",
  "periode": "2026",
  "dateDebut": "2026-11-01",
  "dateFin": "2026-12-31",
  "critères": [
    {
      "name": "Performance",
      "poids": 40
    },
    {
      "name": "Compétences",
      "poids": 30
    },
    {
      "name": "Attitude",
      "poids": 30
    }
  ]
}
```

### Submit Self-Evaluation
```http
POST /api/v1/evaluations/{campagneId}/auto-evaluation
Authorization: Bearer <token>

{
  "employeId": "uuid-employe",
  "criteria": [
    {
      "critereId": "c1",
      "note": 4,
      "commentaire": "J'ai atteint tous mes objectifs..."
    }
  ],
  "objectifsAtteints": ["obj1", "obj2"],
  "axesAmelioration": ["Formation leadership"]
}
```

### Submit Manager Evaluation
```http
POST /api/v1/evaluations/{campagneId}/manager-evaluation
Authorization: Bearer <token>
Role: CHEF_SERVICE

{
  "employeId": "uuid-employe",
  "criteria": [...],
  "recommendations": "Promotion recommandée",
  "planDeveloppement": "Formation management avancé"
}
```

### Get Evaluation with Gap Analysis
```http
GET /api/v1/evaluations/{campagneId}/{employeId}/analyse-ecarts
Authorization: Bearer <token>

Response:
{
  "employe": "John Doe",
  "manager": "Jane Smith",
  "ecarts": [
    {
      "critere": "Performance",
      "noteEmploye": 5,
      "noteManager": 3,
      "ecart": 2,
      "couleur": "ORANGE",
      "recommandation": "Discussion nécessaire pour aligner les perceptions"
    }
  ],
  "ecartGlobal": 1.5,
  "statut": "ALIGNEMENT_MODERE"
}
```

### Generate PDF Report
```http
POST /api/v1/evaluations/{evaluationId}/generer-rapport
Authorization: Bearer <token>

Response: PDF file download
Content-Type: application/pdf
Content-Disposition: attachment; filename="evaluation_2026_john_doe.pdf"
```

---

## M8: PPE (EPI)

### Get PPE Catalog
```http
GET /api/v1/epi/catalogue?categorie=CHAUSSURES&taille=42
Authorization: Bearer <token>

Response:
[
  {
    "id": "uuid",
    "designation": "Chaussures de sécurité S3",
    "categorie": "CHAUSSURES",
    "taillesDisponibles": [39, 40, 41, 42, 43, 44],
    "stock": 45,
    "dureeVieMois": 12,
    "fournisseur": "SafetyPro Inc."
  }
]
```

### Submit PPE Request (Manager for team)
```http
POST /api/v1/epi/demandes
Authorization: Bearer <token>
Role: CHEF_SERVICE

{
  "serviceId": "uuid-service",
  "lignes": [
    {
      "articleId": "uuid-article",
      "quantite": 10,
      "taille": "42",
      "urgence": "NORMAL"
    }
  ],
  "justification": "Renouvellement annuel"
}
```

### Validate PPE Request (HSE)
```http
PUT /api/v1/epi/demandes/{id}/valider
Authorization: Bearer <token>
Role: HSE

{
  "action": "APPROUVER",
  "commentaire": "Stock disponible, livraison prévue"
}
```

### Record PPE Attribution
```http
POST /api/v1/epi/attributions
Authorization: Bearer <token>
Role: HSE or RH

{
  "beneficiaireId": "uuid-employe",
  "articleId": "uuid-article",
  "quantite": 1,
  "dateAttribution": "2026-05-08",
  "dateExpirationPrevue": "2027-05-08",
  "signatureNumerique": "base64-encoded-signature"
}
```

### Get Stock Alerts
```http
GET /api/v1/epi/alertes-stock
Authorization: Bearer <token>
Role: HSE, RH

Response:
[
  {
    "article": "Casque de protection",
    "stockActuel": 5,
    "seuilMinimum": 10,
    "statut": "STOCK_FAIBLE"
  }
]
```

### Download Attribution Receipt
```http
GET /api/v1/epi/attributions/{id}/recu
Authorization: Bearer <token>

Response: PDF receipt
```

---

## M09: Digital Attendance (Présence)

### Record Attendance - Two-Step Anti-Fraud Process

**Step 1: QR Scan + GPS Validation**
```http
POST /presence/valider-qr
Authorization: Bearer <token>
Content-Type: application/json

{
  "qrToken": "abc123xyz",
  "latitude": 48.8566,
  "longitude": 2.3522,
  "timestamp": "2026-05-08T08:15:00Z"
}

Response:
{
  "success": true,
  "data": {
    "qrValid": true,
    "gpsValid": true,
    "distanceMeters": 12.5,
    "message": "QR et GPS validés, procédez à la reconnaissance faciale"
  }
}
```

**Step 2: Facial Recognition**
```http
POST /presence/pointage
Authorization: Bearer <token>
Content-Type: application/json

{
  "qrToken": "abc123xyz",
  "typePointage": "ENTREE",
  "timestamp": "2026-05-08T08:15:00Z",
  "scoreFacial": 0.94,
  "deviceId": "device-uuid-here"
}

Response:
{
  "success": true,
  "data": {
    "pointageId": "uuid-pointage",
    "statut": "VALIDE",
    "message": "Pointage enregistré avec succès",
    "horodatageSigne": "signed-timestamp-hash"
  }
}
```

**Rejection Responses**:

GPS Out of Zone:
```json
{
  "success": false,
  "error": {
    "code": "PRESENCE_GPS_HORS_ZONE",
    "message": "Vous êtes hors de la zone autorisée (rayon 50m)",
    "details": {
      "distanceMeters": 125,
      "rayonAutorise": 50
    }
  }
}
```

Facial Recognition Failed:
```json
{
  "success": false,
  "error": {
    "code": "PRESENCE_FACIAL_ECHEC",
    "message": "Reconnaissance faciale échouée. Tentatives restantes: 2",
    "details": {
      "scoreObtenu": 0.78,
      "seuilRequis": 0.92,
      "tentativesRestantes": 2
    }
  }
}
```

After 3 Failures:
```json
{
  "success": false,
  "error": {
    "code": "PRESENCE_BLOQUE_TEMPORAIREMENT",
    "message": "Compte bloqué 15 minutes après 3 échecs. Contactez votre RO.",
    "deblocageA": "2026-05-08T08:30:00Z"
  }
}
```

### Get My Attendance Records
```http
GET /api/v1/presence/mes-pointages?mois=2026-05
Authorization: Bearer <token>

Response:
[
  {
    "date": "2026-05-08",
    "entree": "08:15",
    "sortie": "17:30",
    "duree": "9h15",
    "statut": "PRESENT",
    "anomalie": null
  }
]
```

### Get Attendance Anomalies (RH)
```http
GET /api/v1/presence/anomalies?periode=SEMAINE_COURANTE
Authorization: Bearer <token>
Role: RH

Response:
[
  {
    "employe": "John Doe",
    "date": "2026-05-07",
    "typeAnomalie": "RETARD",
    "heurePrevu": "08:00",
    "heureReelle": "08:45",
    "retardMinutes": 45
  }
]
```

---

## WebSocket Events

### Connection

```dart
// Connect to WebSocket
final wsUrl = 'ws://localhost:8080/ws?token=$jwtToken';
final stompClient = StompClient(
  config: StompConfig.SockJS(
    url: wsUrl,
    onConnect: (frame) {
      print('Connected: $frame');
      
      // Subscribe to personal notifications
      stompClient.subscribe(
        destination: '/user/queue/notifications',
        callback: (message) {
          final notification = jsonDecode(message.body);
          handleNotification(notification);
        },
      );
    },
  ),
);

stompClient.activate();
```

### Event Types Received

**Demande Status Change**:
```json
{
  "type": "DEMANDE_STATUT_CHANGE",
  "demandeId": "uuid",
  "ancienStatut": "EN_ATTENTE_VALIDATION",
  "nouveauStatut": "APPROUVEE",
  "validateur": "Jane Manager",
  "commentaire": "Approuvé",
  "timestamp": "2026-05-08T14:30:00Z"
}
```

**New Service Note**:
```json
{
  "type": "NOTE_SERVICE_PUBLIEE",
  "noteId": "uuid",
  "titre": "Nouvelle politique",
  "auteur": "Direction RH",
  "timestamp": "2026-05-08T10:00:00Z"
}
```

**Document Ready**:
```json
{
  "type": "DOCUMENT_PRET",
  "documentId": "uuid",
  "typeDocument": "ATTESTATION_TRAVAIL",
  "message": "Votre document est prêt à récupérer",
  "timestamp": "2026-05-08T11:00:00Z"
}
```

**Training Reminder**:
```json
{
  "type": "FORMATION_RAPPEL",
  "sessionId": "uuid",
  "titre": "Formation Leadership",
  "dateDebut": "2026-06-15T09:00:00Z",
  "message": "Rappel: Formation dans 7 jours",
  "timestamp": "2026-06-08T09:00:00Z"
}
```

**Attendance Anomaly**:
```json
{
  "type": "PRESENCE_ANOMALIE",
  "employeId": "uuid",
  "employeNom": "John Doe",
  "typeAnomalie": "ABSENCE_NON_JUSTIFIEE",
  "date": "2026-05-08",
  "timestamp": "2026-05-08T10:00:00Z"
}
```

---

## Error Codes Reference

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `AUTH_INVALID_CREDENTIALS` | 401 | Username or password incorrect |
| `AUTH_TOKEN_EXPIRED` | 401 | JWT token has expired (15min) |
| `AUTH_REFRESH_TOKEN_EXPIRED` | 401 | Refresh token expired (7 days) - re-login required |
| `AUTH_INSUFFICIENT_ROLE` | 403 | User lacks required role |
| `DEMANDE_NOT_FOUND` | 404 | Demand ID doesn't exist |
| `DEMANDE_INVALID_DATES` | 400 | Start date after end date |
| `DEMANDE_CONFLICT` | 409 | Overlapping leave request |
| `DEMANDE_ALREADY_VALIDATED` | 409 | Cannot validate twice |
| `DEMANDE_SOLDE_INSUFFISANT` | 422 | Insufficient leave balance |
| `SORTIE_DUREE_EXCEEDEE` | 422 | Exit permit exceeds 4 hours max |
| `DOCUMENT_TYPE_INVALID` | 400 | Unknown document type |
| `DOCUMENT_SLA_EXCEEDED` | 422 | SLA deadline passed |
| `PLAINTE_MEDIA_TOO_LARGE` | 413 | File exceeds size limit (10MB) |
| `PLAINTE_AUDIO_TRANSCRIPTION_FAILED` | 500 | Whisper API transcription error |
| `EPI_STOCK_INSUFFISANT` | 409 | Not enough stock |
| `EPI_ATTRIBUTION_EXPIRED` | 422 | PPE attribution expired |
| `PRESENCE_QR_EXPIRED` | 422 | QR token expired (5min TOTP) |
| `PRESENCE_GPS_HORS_ZONE` | 422 | User not in authorized GPS zone |
| `PRESENCE_FACIAL_ECHEC` | 422 | Face verification failed (< threshold) |
| `PRESENCE_BLOQUE_TEMPORAIREMENT` | 423 | Account blocked 15min after 3 failures |
| `PRESENCE_LIVENESS_FAIL` | 422 | Liveness detection failed (photo/screen fraud attempt) |
| `EVALUATION_PERIOD_CLOSED` | 422 | Campaign period ended |
| `EVALUATION_ALREADY_SUBMITTED` | 409 | Evaluation already completed |
| `FORMATION_SESSION_FULL` | 409 | No available seats |
| `RECRUTEMENT_CV_PARSING_FAILED` | 500 | AI CV parsing error |
| `CHATBOT_RAG_ERROR` | 500 | Chatbot RAG query failed |
| `VALIDATION_ERROR` | 400 | Generic validation error |
| `BUSINESS_RULE_VIOLATION` | 422 | Business logic constraint |
| `INTERNAL_ERROR` | 500 | Unexpected server error |

---

## Rate Limiting

| Endpoint | Limit | Window |
|----------|-------|--------|
| `/api/v1/auth/login` | 5 requests | 15 minutes |
| `/api/v1/demandes/*` | 30 requests | 1 minute |
| `/api/v1/presence/pointage` | 5 requests | 1 minute |
| `/api/v1/upload/*` | 10 requests | 1 minute |
| General API | 100 requests | 1 minute |

**Headers**:
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1620000000
```

**Response when limited**:
```json
{
  "success": false,
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "Too many requests. Try again in 45 seconds.",
    "retryAfter": 45
  }
}
```
