# Architecture — API, événements et intégrations

**Rôle** : architecte d’intégration  
**But** : définir comment les services communiquent et s’intègrent au SI.

---

## 1. Exposition externe

| Élément | Choix typique |
|---------|----------------|
| **Entrée unique** | API Gateway (Kong, Spring Cloud Gateway, Traefik, AWS API Gateway) |
| **Protocole client** | HTTPS + JSON (REST) ; GraphQL optionnel pour agrégations mobiles |
| **Contrat** | OpenAPI 3 par service ; publication dans un portail API |

---

## 2. REST — conventions

| Convention | Détail |
|------------|--------|
| **Préfixes** | `/api/v1/{domaine}/...` (ex. `/api/v1/demandes/conges`) |
| **Idempotence** | Header `Idempotency-Key` sur POST critiques (demandes, attributions) |
| **Erreurs** | Format JSON homogène `{ "erreur": "...", "code": "..." }` ; codes HTTP alignés RFC |
| **Pagination** | `page`, `taille` ou curseur ; tri explicite |
| **Internationalisation** | Header `Accept-Language` ; messages métier en français par défaut (CDC) |

---

## 3. Authentification des appels

| Flux | Mécanisme |
|------|-----------|
| **Utilisateur humain** | JWT OAuth2 / OIDC émis par **svc-identite-acces** ; validation par resource servers |
| **Service à service** | Client credentials ou mTLS (selon niveau de confiance du réseau) |

---

## 4. Événements métier (asynchrone)

| Bus suggéré | Kafka, RabbitMQ, ou NATS (selon volume et équipe). |
|-------------|-----------------------------------------------------|
| **Format** | CloudEvents ou enveloppe maison `{ type, source, time, data }` |
| **Nommage** | `rh.demandes.conge.approuvee`, `rh.notification.envoyer`, etc. |

### Exemples d’événements

| Événement | Producteur | Consommateurs typiques |
|-----------|------------|-------------------------|
| `DemandeSoumise` | demandes-personnel | notification |
| `DemandeValideeNiveauSuperieur` | demandes-personnel | notification, audit |
| `DocumentPret` | documents-admin | notification |
| `NoteServicePubliee` | notes-service | notification, index recherche |
| `PlainteCreee` | plaintes | notification, audit |
| `EpiAttribue` | epi | notification, fichiers (PDF) |
| `PointageEnregistre` | presence | notification (anomalies), analytics |

---

## 5. Intégrations tierces

| Système | Usage possible | Mode |
|---------|----------------|------|
| **SMTP / SendGrid / SES** | E-mails transactionnels | Via svc-notification |
| **FCM / APNS** | Push mobile | Via svc-notification |
| **Active Directory / LDAP** | Identité entreprise (futur) | SCIM ou sync vers référentiel |
| **Paie** | Soldes congés, bulletins | API fichier ou batch sécurisé (hors périmètre V1 si non prêt) |
| **Signature électronique** | Reçus EPI, évaluations | Connecteur dédié phase ultérieure |

---

## 6. Génération documentaire

| Besoin | Approche |
|--------|----------|
| PDF ordre de mission, reçus EPI, évaluations | Librairie (OpenPDF, Flying Saucer, etc.) dans le service métier **ou** microservice `svc-rendering` si mutualisation |

---

## 7. Webhooks (optionnel)

Pour intégrations partenaires : endpoints signés (HMAC) avec retry et journal des livraisons.

---

## 8. JWKS et interop (existant)

| Paramètre | Valeur type |
|-----------|-------------|
| Issuer | `http://localhost:8080` (dev) — URL publique en prod |
| JWKS | `/oauth2/jwks` accessible sans authentification |
| Claims utiles | `sub`, `scope` / rôles, `identifiant_utilisateur`, `email` |

---

*Compléter avec des diagrammes de séquence par workflow dans un outil de modélisation (PlantUML, Mermaid) si besoin.*
