# Cahier des Charges & Conception Technique Enhanced V2
**Projet :** Plateforme SIRH & Application Mobile "RH-Évènement"
**Version :** 2.0 (Ready for Prod & IA-Powered)

---

## 1. Vision et Objectifs
La plateforme RH-Évènement vise à centraliser, automatiser et moderniser les processus RH via une architecture microservices robuste, un portail web d'administration riche, et une application mobile "vivante" (temps réel, UI/UX soignée). 

**Nouveaux objectifs (V2) :**
- Intégrer l'Intelligence Artificielle (IA) pour automatiser les tâches (ATS, Chatbot, Reconnaissance faciale).
- Offrir une expérience utilisateur (UX) mobile exceptionnelle avec des fonctionnalités temps réel (WebSockets) et multimédia (vocales, biométrie).
- Garantir un niveau "Ready for Production" (haute disponibilité, tolérance aux pannes, S3 GED, cache).

---

## 2. Spécifications Fonctionnelles Améliorées (Basées sur le Draft V2)

### M1. Demandes Administratives (Congés, Autorisations, Missions)
- **Fonctionnalités :** Workflow de validation (Employé -> Manager -> RH).
- **Exigences Non-Fonctionnelles (NFR) :** Sécurité, fiabilité, temps réel, tolérance aux pertes (Kafka), mise en cache (Redis).
- **Innovations :** UI/UX mobile fluide, notifications en temps réel via **WebSockets**, gestion transactionnelle stricte.

### M2. Documents Administratifs
- **Fonctionnalités :** Demande de documents (Attestations, fiches de paie). Acceptation/Refus avec commentaires.
- **NFR :** Gestion via **PriorityQueue** basée sur les SLA (date d'action cible). Système de suivi détaillé.
- **Innovations (IA) :** Intégration d'un **Chatbot IA** capable d'interpréter les commentaires RH et de répondre aux questions des collaborateurs sur le statut de leurs documents.

### M3. Notes de Service
- **Fonctionnalités :** Publication, diffusion.
- **NFR :** Accusé de réception obligatoire (tracking de lecture), stockage déporté type **GED (S3 / MinIO)**.
- **Innovations :** Notifications push enrichies.

### M4. Gestion des Plaintes
- **Fonctionnalités :** Soumission de plaintes internes (routage vers RH) et externes (routage vers le Responsable Opérationnel - RO).
- **Innovations :** 
  - Possibilité de soumettre des plaintes **vocales** (Speech-to-Text) ou écrites.
  - Gestion stricte des logs d'audit.
  - **IA :** Analyse de sentiment basique sur le texte de la plainte pour prioriser les urgences.

### M5. Formation & Évaluation
- **Fonctionnalités :** Planification des formations et évaluations.
- **Innovations :** Système de **Scheduler intelligent** :
  - Évaluation "à chaud" déclenchée automatiquement 24h après la formation.
  - Évaluation "à froid" déclenchée 3 mois après pour mesurer l'impact réel.

### M6. Évaluation Annuelle
- **Fonctionnalités :** Campagnes d'évaluation croisée (Employé / Manager).
- **Innovations :** 
  - **Indicateur visuel dynamique (Matrice d'écart) :** Vert si l'écart d'évaluation employé/manager est nul ou de 1 point, Orange si écart de 2 points, Rouge si écart de 3 points ou plus.
  - Génération de rapports PDF certifiés et archivage automatique sur **S3**.

### M7. EPI (Équipements de Protection Individuelle)
- **Fonctionnalités :** Demandes de tenues, gestion des stocks, reporting et bons de livraison (papiers/numériques).
- **NFR :** Traçabilité complète des attributions.

### M8. Gestion de Présence
- **Fonctionnalités :** Pointage digital in-situ.
- **Innovations (IA & Hardware) :** Pointage combinant scan de **QR Code** (dynamique) et vérification par **Empreinte Faciale (Facial Recognition)** via `google_mlkit_face_detection` sur l'application mobile pour lutter contre la fraude.

### M9. Recrutement & IA (Nouveau Module)
- **Fonctionnalités :** Gestion des offres et des candidatures.
- **Innovations (IA) :** Module **ATS Intelligent (Applicant Tracking System)**. Matching IA entre les descriptions de poste (demandes RH) et les CV parsés pour présélectionner les meilleurs candidats.

---

## 3. Architecture Technique (System Design V2)

L'architecture repose sur des microservices isolant les domaines avec une communication asynchrone forte.

### 3.1. Vue Microservices (Découpage Cible)
1. **`svc-identite-acces` :** AuthN/AuthZ, JWT, gestion des rôles.
2. **`svc-rh-plateforme` (Core) :** Regroupe les modules M1 à M7 (Référentiel, Demandes, Plaintes, Évaluations, EPI). Base de données : PostgreSQL (`rh_db`).
3. **`svc-notification` :** Gestion des WebSockets (temps réel), Push (Firebase FCM), et Emails. Connecté via Kafka.
4. **`svc-presence` :** Traite les flux de pointage, QR codes, et validation faciale.
5. **`svc-ia` (Nouveau) :** Microservice en Python (FastAPI/Flask) ou Spring AI pour héberger/consommer les modèles ML (Chatbot RAG, Parsing de CV, Matching, Speech-to-Text).
6. **`API Gateway` :** Routage, Rate limiting (Redis).

### 3.2. Stockage et Infrastructure
- **Bases de Données Relatives :** PostgreSQL par service (ou schémas isolés).
- **Message Broker :** Kafka (Zookeeper/Kraft) pour les événements asynchrones (`DemandeSoumise`, `PointageRealise`, `EvaluationPlanifiee`).
- **Cache & Rate Limit :** Redis (Gestion des sessions WebSocket temporaires, cache du référentiel).
- **GED (Stockage Fichiers) :** Déportation de tous les médias, PDF, CV et notes de service vers du stockage objet **S3-compatible (MinIO ou AWS S3)**.

### 3.3. Application Mobile (Flutter)
- **State Management :** Riverpod.
- **Temps réel :** `stomp_dart_client` & `web_socket_channel` pour le suivi direct des workflows.
- **Innovations UI/UX :** 
  - Thème dynamique et micro-animations.
  - Intégration de `google_mlkit_face_detection` pour la vérification de présence.
  - Capture audio (`record` / `audioplayers`) pour les plaintes vocales.

### 3.4. Portail Web Admin (React/TypeScript)
- **Stack :** React, Vite, TailwindCSS (ou CSS modulaire), Zustand/Redux.
- **Fonctionnalités :** 
  - Tableaux de bord enrichis (Graphiques des évaluations et écarts colorimétriques).
  - Gestion de la PriorityQueue des documents administratifs (vues Kanban/Liste par SLA).
  - Interface ATS (Matching CV <-> Besoin).

---

## 4. Stratégie d'Implémentation des Fonctionnalités Innovantes

### 4.1. Pointage par Reconnaissance Faciale
- **Processus :**
  1. Le collaborateur enregistre une photo de référence (encodée en vecteur par l'IA et stockée de manière sécurisée).
  2. Au moment du pointage, l'app Flutter scanne le QR code (pour la localisation) puis utilise l'appareil photo frontal.
  3. Le modèle (ML Kit embarqué ou via `svc-ia`) valide la correspondance (Liveness detection + Match).

### 4.2. File de Priorité (PriorityQueue) pour Documents RH
- **Implémentation Backend :** Au lieu d'un tri simple par date de création, un algorithme calcule le `score_urgence` basé sur le type de document (ex: Attestation de salaire = 48h, Autre = 120h) et le temps écoulé.
- **Exposition :** L'API fournit une vue `/api/documents/prioritaires` que le front-end React affiche en surbrillance.

### 4.3. Chatbot RH (Assistant Virtuel)
- **Technologie :** RAG (Retrieval-Augmented Generation).
- **Données :** Base de connaissances RH (règlement interne) + statut des demandes de l'utilisateur + commentaires RH.
- **Interface :** Bulle flottante sur l'application mobile et le portail web.

### 4.4. Plaintes Vocales
- **Flux :** 
  1. Enregistrement audio sur mobile.
  2. Envoi vers le serveur.
  3. `svc-ia` convertit l'audio en texte (Speech-to-Text via Whisper ou équivalent) et l'associe à la plainte.
  4. Routage selon la catégorie (Interne -> RH, Externe -> RO).

### 4.5. Évaluations Schedulées
- **Implémentation :** Utilisation de Quartz Scheduler ou `@Scheduled` dans Spring Boot couplé à Kafka.
  - À la clôture d'une formation, création d'un événement différé (delay=24h et delay=3mois) pour envoyer les notifications (Email/Push) contenant les liens des questionnaires.

---
*Ce document sert de référence cible (Ready for Prod) pour les prochains développements et itérations.*
