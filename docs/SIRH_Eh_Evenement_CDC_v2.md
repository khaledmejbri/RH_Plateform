**AGUA SERVICE D'ASSAINISSEMENT**

───────────────────────────────────

Système d'Information des Ressources Humaines

**CAHIER DES CHARGES ENRICHI**

**RH-Évènement v2.0 - Ready for Production**

| **Projet**  | **SIRH RH-Évènement**                       |
| ----------- | ------------------------------------------- |
| **Version** | 2.0 - Enrichi & Production-Ready            |
| **Société** | AGUA Service d'Assainissement               |
| **Date**    | Mai 2025                                    |
| **Statut**  | **CONFIDENTIEL - Usage interne uniquement** |

12 modules fonctionnels • IA intégrée • Mobile-first • Cloud-native

# **1\. Présentation du Projet**

RH-Évènement est un SIRH (Système d'Information des Ressources Humaines) complet destiné à AGUA Service d'Assainissement. Il digitalise l'intégralité des processus RH via une application mobile cross-platform (iOS/Android) et un portail web d'administration, avec des fonctionnalités d'intelligence artificielle, de traitement en temps réel et une architecture cloud-native prête pour la production.

## **🎯 1.1 Objectifs Stratégiques**

- Digitaliser 100% des processus RH aujourd'hui réalisés manuellement
- Réduire de 80% les délais de traitement des demandes via l'automatisation
- Offrir une expérience mobile moderne, rapide et hors-ligne capable
- Intégrer l'IA pour le matching RH, les chatbots, les évaluations et les alertes prédictives
- Garantir la traçabilité complète avec audit log immuable
- Préparer l'intégration future avec le module de paie
- Conformité RGPD et sécurité niveau entreprise

## **👥 1.2 Périmètre des Utilisateurs**

| **Rôle**                          | **Responsabilités clés**                                               | **Interface principale** |
| --------------------------------- | ---------------------------------------------------------------------- | ------------------------ |
| **Collaborateur**                 | Soumettre demandes, consulter statuts, répondre évaluations, pointage  | Application mobile       |
| **Responsable Opérationnel (RO)** | Valider demandes de son unité, créer plaintes externes, suivre EPI     | Mobile + Web             |
| **Chef de Département**           | Validation hiérarchique, suivi d'équipe, fiches de besoin en personnel | Mobile + Web             |
| **Responsable RH (RRH)**          | Administration complète, tableaux de bord, reporting, configuration    | Portail web              |
| **HSE**                           | Gestion EPI, catalogue, attributions, stock                            | Web + Mobile             |
| **Direction Générale**            | Dashboards exécutifs, validation budgétaire, accès lecture             | Web                      |

# **2\. Vue d'Ensemble des Modules**

L'application se compose de 12 modules fonctionnels interconnectés, organisés en 4 domaines.

| **Domaine**               | **Modules inclus**                                                                                                      |
| ------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| **🗂 Demandes & Admin**   | M01 Demandes administratives (congé, autorisation, ordre mission) - M02 Documents administratifs - M03 Notes de service |
| **🤝 RH & Développement** | M04 Gestion des plaintes - M05 Formation & évaluation - M06 Besoin en personnel (recrutement) - M07 Évaluations RH      |
| **🦺 HSE & Présence**     | M08 EPI & équipements - M09 Gestion des présences (pointage QR + biométrie faciale)                                     |
| **🤖 IA & Transversal**   | M10 Recrutement IA & ATS - M11 Chatbot RH & assistant - M12 Tableaux de bord & analytics                                |

# **3\. Spécifications Fonctionnelles Détaillées**

## **📋 M01 - Demandes Administratives**

⚠ Correction v2.0 : L'autorisation de sortie N'EST PAS un congé. Elle est une sortie courte (max 4h, moins d'une demi-journée).

Le calcul du solde de congé ne s'applique pas aux autorisations de sortie.

Les trois types ont des circuits de validation distincts et des données spécifiques.

### **M01.A - Congé**

- Types configurables par le RH (annuel, maladie, maternité, sans solde, etc.)
- Calcul automatique du solde restant (intégration future module paie)
- Pièces jointes obligatoires selon type (certificat médical pour maladie)
- Délai minimum avant départ configurable (ex: 48h pour congé annuel)

| **Étape** | **Acteur**                   | **Action**                                                     | **Résultat**                               |
| --------- | ---------------------------- | -------------------------------------------------------------- | ------------------------------------------ |
| **1**     | **Collaborateur**            | Soumet la demande avec dates, type, motif et pièces jointes    | Demande créée - Notification push au RO    |
| **2**     | **Responsable Opérationnel** | Valide ou refuse avec commentaire obligatoire si refus         | Notification push au RRH si validé         |
| **3**     | **RRH**                      | Approbation finale ou refus motivé                             | Notification push + email au collaborateur |
| **4**     | **Système**                  | Mise à jour du solde de congé, archivage PDF automatique en S3 | Solde débité, PDF généré                   |

### **M01.B - Autorisation de Sortie**

- Durée maximale : 4 heures (moins d'une demi-journée de 8h)
- Champs : date, heure départ, heure retour, motif libre
- Validation côté client ET côté serveur de la durée max 4h
- Pas de déduction de solde de congé
- Indicateur de durée en temps réel dans le formulaire mobile

### **M01.C - Ordre de Mission**

- Champs : lieu, motif, objectifs, date début/fin, frais estimés, pièces jointes
- Génération automatique d'un PDF d'ordre de mission signable
- Upload pièces jointes (invitation, courrier) via S3
- Workflow identique : collaborateur → RO → RRH

## **📄 M02 - Documents Administratifs**

Les employés demandent des documents officiels directement depuis l'application. Un système de file d'attente avec priorité (PriorityQueue basée sur action_date) gère les demandes.

| **Type de document**               | **Détail**                                          | **SLA**                |
| ---------------------------------- | --------------------------------------------------- | ---------------------- |
| **Attestation de travail**         | Génération PDF automatique avec modèle configurable | < 24h                  |
| **Attestation de salaire**         | Données issues du module paie (futur)               | < 24h                  |
| **Bulletin de paie**               | PDF sécurisé, historique 5 ans                      | < 48h                  |
| **Attestation CNSS**               | Génération avec numéro employeur                    | < 48h                  |
| **Feuille de pointage mensuelle**  | Export PDF/Excel du relevé mensuel                  | < 1h (automatique)     |
| **Documents internes spécifiques** | Formulaire libre avec motif                         | < 72h selon complexité |

🤖 Innovation : Chatbot RH intégré dans M02 - Le collaborateur peut discuter avec le chatbot basé sur les

commentaires et réponses historiques du RH pour anticiper le statut de sa demande.

Technologie : RAG (Retrieval-Augmented Generation) sur l'historique des demandes.

## **📢 M03 - Notes de Service**

- Publication par Direction ou RRH avec pièces jointes (PDF, images) stockées sur S3
- Notification instantanée push + WebSocket vers tous les employés ou groupes ciblés
- Accusé de réception obligatoire (le collaborateur confirme avoir lu)
- Archive consultable avec recherche full-text
- GED (Gestion Électronique des Documents) : versioning, catégorisation, accès par rôle
- Indicateur de lecture : RH voit qui a lu / pas lu

## **🗣 M04 - Gestion des Plaintes**

📌 Distinction importante v2.0 :

• Plainte interne : soumise par un collaborateur → envoyée au RH

• Plainte externe/communautaire : créée par un RO/RH/Technique → envoyée simultanément

aux Services Techniques + Direction Environnement & Social + Ressources Humaines

• Les plaintes peuvent être déposées par ÉCRIT ou par VOCAL (message audio transcrit par IA)

### **Workflow des plaintes - Statuts**

| **Étape** | **Acteur**        | **Action**            | **Résultat**                                                       |
| --------- | ----------------- | --------------------- | ------------------------------------------------------------------ |
| **1**     | **Nouveau**       | Collaborateur / RO    | Soumet la plainte avec description, photos/vidéos, audio optionnel |
| **2**     | **En analyse**    | RH / Équipe concernée | Examine la plainte, demande compléments si besoin                  |
| **3**     | **En traitement** | Service responsable   | Actions correctives engagées                                       |
| **4**     | **Résolu**        | RH                    | Clôture avec commentaire de résolution                             |
| **5**     | **Fermé**         | Système / RH          | Archivage automatique après 30 jours sans contestation             |

- Gestion complète des logs d'actions (qui a fait quoi et quand)
- Tableau de bord inter-services pour les plaintes externes
- Transcription audio → texte via API Whisper pour les plaintes vocales

## **🎓 M05 - Formation & Évaluation des Formations**

### **M05.A - Besoin / Demande de Formation**

- Soumission par un collaborateur ou un chef de service
- Validation : chef service → RH → intégration dans le plan annuel de formation
- Champs : type formation, organisme, durée, coût estimé, objectifs pédagogiques, justification

### **M05.B - Évaluation à Chaud (J+1 via scheduler)**

⏰ Scheduler automatique : Le système déclenche l'évaluation à chaud 24 heures après la fin de la formation.

Le collaborateur reçoit une notification push avec un lien direct vers le questionnaire.

Si non répondu dans 48h supplémentaires : rappel automatique.

- Questionnaire personnalisable (notation 1-5, questions ouvertes, NPS formateur)
- Évaluation du contenu, du formateur, de la logistique et de l'utilité perçue

### **M05.C - Évaluation à Froid (M+3 via scheduler)**

- Déclenchée automatiquement 3 mois après la formation
- Mesure de l'impact réel sur le travail quotidien
- Questions sur l'application des acquis, les changements observés, les besoins complémentaires
- Résultats consolidés dans un dashboard RH avec comparaison chaud/froid

## **👤 M06 - Besoin en Personnel & Recrutement**

- Fiche de besoin créée par un chef de service (poste, profil, date souhaitée, justification)
- Workflow : chef → direction → RH → intégration dans le plan de recrutement
- Voir M10 pour le module IA de matching CV/demande

## **⭐ M07 - Évaluations RH**

### **M07.A - Évaluation Semestrielle**

- 4 critères notés de 1 à 5 : Qualité du travail, Rendement, Ponctualité, Esprit d'équipe
- Score sur 20 - grille d'appréciation : Insuffisant / À améliorer / Satisfaisant / Positif / Excellent
- Points forts, points à améliorer, plan d'action recommandé
- Validation croisée entre collaborateur et supérieur

### **M07.B - Évaluation Annuelle des Compétences**

- Bilan complet : Savoir (technique), Savoir-faire (pratique), Savoir-être (comportemental)
- Résultats vs objectifs N, définition des objectifs N+1
- Formations recommandées par le supérieur → automatiquement intégrées dans M05
- Export PDF pour archivage - stockage S3

🤖 Innovation IA - Code couleur intelligent (concept conception) :

🟢 Vert : score excellent sur tous les critères

🟠 Orange : 2 critères en dessous du seuil - alerte au RH

🔴 Rouge : 3+ critères insuffisants - plan d'action obligatoire + escalade DG

Le système détecte automatiquement les patterns et génère des recommandations.

## **🦺 M08 - Distribution & Suivi des EPI**

### **Catalogue EPI**

- Base de données des EPI : nom, catégorie, taille, quantité stock, date expiration, fournisseur
- Gestion par le HSE : ajout, modification, désactivation
- Alertes automatiques stock bas + EPI en expiration (< 30 jours)

### **Flux de distribution**

| **Étape** | **Acteur**          | **Action**                                                                   | **Résultat**             |
| --------- | ------------------- | ---------------------------------------------------------------------------- | ------------------------ |
| **1**     | **Chef de service** | Demande groupée (type EPI, quantité, justification, urgence normale/urgente) | Demande transmise au HSE |
| **2**     | **HSE**             | Valide, attribue partiellement ou totalement, définit date de remise         | Chef notifié             |
| **3**     | **HSE**             | Enregistre la remise physique avec signature numérique du bénéficiaire       | Traçabilité créée        |
| **4**     | **Système**         | Génère le reçu PDF de remise, met à jour le stock, archive en S3             | Stock mis à jour         |

- Traçabilité complète : bénéficiaire, date, quantité, validateur, durée de vie, historique
- Reporting stock mensuel exportable PDF/Excel

## **📍 M09 - Gestion des Présences**

🆕 Module innovant v2.0 - Pointage multi-modal :

• QR Code : chaque employé a un QR personnel - scanné à l'entrée/sortie

• Empreinte faciale : reconnaissance via la caméra du smartphone (en local, RGPD-compliant)

• Fallback manuel : le RO peut pointer manuellement en cas de problème technique

- Génération automatique de la feuille de pointage mensuelle (format identique au document existant)
- Calcul automatique : heures normales, heures supplémentaires, absences, jours travaillés
- Gestion des horaires variables par unité (quarts, décalés, normaux)
- Alertes retard automatiques au RO si > seuil configuré
- Export Excel/PDF de la feuille de pointage pour validation RH
- Intégration future avec module de paie pour le calcul des heures sup

### **Processus de Pointage Obligatoire - QR Code + Géolocalisation + Reconnaissance Faciale**

**🚨 Exigence de sécurité critique - Anti-fraude pointage :**

Le pointage est un processus en deux étapes obligatoires et séquentielles :

**Étape 1 - Scan QR Code + validation géolocalisation GPS**

**Étape 2 - Reconnaissance faciale obligatoire pour confirmer l'identité du pointeur**

⚠ Si l'une des deux étapes échoue, le pointage est rejeté. Aucun pointage partiel n'est accepté.

| **Étape** | **Mécanisme**                 | **Description technique**                                                                                                                                                                                                                                                                                                                                                                                                                     | **Résultat / Rejet**                                                 |
| --------- | ----------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------- |
| **1**     | **📱 Ouverture App**          | Le collaborateur ouvre l'application mobile et accède à l'écran de pointage. L'app active automatiquement la caméra et le GPS.                                                                                                                                                                                                                                                                                                                | Session de pointage initiée                                          |
| **2**     | **📷 Scan QR Code**           | Scan du QR Code affiché sur le site physique (entrée du site, vestiaire, poste). Le QR encode un identifiant de borne signé + coordonnées GPS de référence. Le QR est renouvelé toutes les 5 minutes (TOTP) pour empêcher la capture et réutilisation hors site.                                                                                                                                                                              | QR valide → passe à l'étape 3                                        |
| **3**     | **📍 Validation GPS**         | Le serveur compare les coordonnées GPS réelles du téléphone avec les coordonnées GPS de référence encodées dans le QR. Rayon de tolérance configurable par site (ex : 50 mètres). Si l'écart dépasse le rayon, le pointage est REJETÉ avec alerte au RO.                                                                                                                                                                                      | GPS hors zone → REJET + alerte RO. GPS valide → étape 4              |
| **4**     | **🧠 Reconnaissance Faciale** | L'application active la caméra frontale et capture une photo en temps réel (liveness detection active pour détecter les photos imprimées ou écrans). Le vecteur facial est comparé au vecteur de référence enregistré lors de l'inscription RH. Seuil de similarité configurable (défaut : 92%). Traitement 100% en local sur le device (on-device ML) - aucune photo n'est transmise au serveur. Seul le score de correspondance est envoyé. | Score < seuil → REJET. Score ≥ seuil → étape 5                       |
| **5**     | **✅ Pointage validé**        | Le serveur enregistre : identifiant collaborateur, type (entrée/sortie), timestamp précis, coordonnées GPS réelles, identifiant de borne QR, score facial (sans photo), version QR. Horodatage signé cryptographiquement pour irréfutabilité.                                                                                                                                                                                                 | Feuille de présence mise à jour. Confirmation push au collaborateur. |

### **Spécifications Techniques - QR Code Anti-Fraude**

- QR Code dynamique renouvelé toutes les 5 minutes (algorithme TOTP - Time-based One-Time Password)
- Contenu du QR : { borne_id, site_id, lat_ref, lng_ref, token_TOTP, signature_HMAC }
- Affiché sur un écran physique dédié (tablette ou écran d'accueil) mis à jour automatiquement
- Un QR capturé et envoyé à un collègue distant est inutilisable (token expiré avant utilisation)
- Anti-replay : chaque token TOTP n'est accepté qu'une seule fois par le serveur (nonce blacklist)
- Gestion multi-sites : chaque site/unité a ses propres bornes QR avec rayon GPS configurable indépendamment

### **Spécifications Techniques - Reconnaissance Faciale**

- Technologie : ML Kit Face Recognition (Google, on-device) - aucune donnée biométrique ne quitte le téléphone
- Liveness detection obligatoire : détecte les tentatives de fraude avec une photo imprimée ou affichée sur écran
- Photo de référence enregistrée par le RH lors de l'inscription du collaborateur (stockée chiffrée en S3)
- Le vecteur facial de référence est téléchargé chiffré sur le device à la connexion - jamais la photo brute
- Seuil de similarité : 92% par défaut, ajustable par le RH (80% minimum imposé)
- En cas de 3 échecs consécutifs : alerte automatique au RO + blocage temporaire 15 minutes
- Conformité RGPD : le collaborateur signe un consentement biométrique lors de l'inscription, révocable à tout moment

### **Gestion des Fraudes, Alertes et Exceptions**

| **Tentative de fraude détectée**                 | **Réponse du système**                                                                                             | **Traçabilité**                                       |
| ------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------- |
| QR scanné depuis un lieu distant (GPS hors zone) | Rejet immédiat + notification push au RO avec coordonnées GPS réelles du fraudeur                                  | Log horodaté avec GPS + identité + screenshot         |
| Photo d'une autre personne (liveness fail)       | Rejet + alerte RO. 3 tentatives → blocage 15 min                                                                   | Log avec score facial + timestamp + device ID         |
| QR expiré ou token déjà utilisé                  | Message d'erreur clair sur le téléphone, invitation à rescanner le QR actuel                                       | Log avec identité + token utilisé                     |
| GPS indisponible (zone sans signal)              | Fallback Wi-Fi géolocalisation (BSSID du réseau local du site). Si toujours impossible : pointage manuel RO requis | Flagué "GPS indisponible" dans la feuille de présence |

### **Données Enregistrées par Pointage**

- collaborateur_id - Identifiant unique du collaborateur
- type_pointage - ENTREE / SORTIE
- horodatage - Timestamp précis UTC signé cryptographiquement
- latitude_reelle / longitude_reelle - Coordonnées GPS du device au moment du pointage
- borne_id / site_id - Identifiant du QR scanné
- score_facial - Score de similarité (0.0 à 1.0) - aucune image biométrique stockée
- device_id - Identifiant du smartphone utilisé (pour détecter les changements de device suspects)
- statut - VALIDE / REJETE_GPS / REJETE_FACIAL / REJETE_QR / MANUEL
- signature_hmac - Hash de tous les champs ci-dessus, vérifiable pour preuve d'intégrité

## **🤖 M10 - Recrutement IA & ATS**

🧠 Module d'intelligence artificielle pour la gestion des candidatures.

ATS = Applicant Tracking System avec matching intelligent CV/poste.

- Publication des offres de poste (issues de M06) sur le portail interne
- Réception des CV (PDF) - parsing automatique par IA (extraction compétences, expériences, formations)
- Matching intelligent : score de compatibilité CV/fiche de poste (0-100%)
- Classement automatique des candidatures par score de matching
- Détection des compétences manquantes par rapport au profil requis
- Suivi du pipeline de recrutement : Reçu → Présélectionné → Entretien → Offre → Embauché/Refusé
- Génération automatique du compte collaborateur si embauché (intégration M00 structure RH)
- Analytics recrutement : time-to-hire, taux de conversion, sources des candidats

## **💬 M11 - Chatbot RH & Assistant IA**

- Chatbot disponible dans l'application mobile pour les collaborateurs
- Réponses basées sur les FAQ RH + historique des demandes similaires (RAG)
- Le collaborateur peut interroger le bot sur : solde congé, statut demande, procédures RH
- Le bot suggère les démarches à suivre selon la situation
- Escalade vers un agent RH humain si le bot ne peut pas répondre
- Analyse des sentiments des plaintes pour priorisation automatique
- Transcription des messages vocaux (Whisper) pour accessibilité

## **📊 M12 - Tableaux de Bord & Analytics**

### **Dashboard RRH**

- Vue temps réel : demandes en attente, plaintes ouvertes, formations planifiées
- KPIs RH : taux d'absentéisme, rotation du personnel, temps moyen de traitement
- Alertes : évaluations en retard, EPI expirés, formations non évaluées

### **Dashboard Direction Générale**

- Graphiques exécutifs : effectifs par département, tendances présence, budget formation
- Rapport mensuel auto-généré (PDF) envoyé par email à la DG le 1er du mois

### **Analytics prédictifs IA**

- Prédiction du taux d'absentéisme par unité (modèle ML sur historique présences)
- Détection précoce des collaborateurs à risque de démission (score d'engagement)
- Recommandation automatique du plan de formation annuel basé sur les évaluations

# **4\. Exigences Non Fonctionnelles**

| **Catégorie**   | **Exigence**                                        | **Valeur cible**                         |
| --------------- | --------------------------------------------------- | ---------------------------------------- |
| **Sécurité**    | Authentification JWT + Refresh Token                | **Expiration 15min access / 7j refresh** |
| **Sécurité**    | Rôles et permissions granulaires (RBAC)             | **5 rôles distincts minimum**            |
| **Sécurité**    | Audit log immuable de toutes les actions            | **Retention 5 ans**                      |
| **Sécurité**    | Chiffrement des données sensibles en base           | **AES-256**                              |
| **Sécurité**    | Conformité RGPD - droit à l'oubli, consentement     | **Obligatoire**                          |
| **Performance** | Temps de réponse API                                | **< 300ms au P95**                       |
| **Performance** | Temps de chargement écran mobile                    | **< 2s sur 4G**                          |
| **Performance** | Cache Redis pour données fréquentes                 | **TTL configurable par entité**          |
| **Fiabilité**   | Disponibilité de l'application                      | **99.5% SLA**                            |
| **Fiabilité**   | Tolérance aux pertes réseau (offline mobile)        | **File locale avec sync automatique**    |
| **Fiabilité**   | Transactions ACID pour données financières/paie     | **Obligatoire**                          |
| **Temps réel**  | Notifications push mobile                           | **< 5s de délai**                        |
| **Temps réel**  | WebSocket pour mises à jour live                    | **STOMP sur WebSocket**                  |
| **Temps réel**  | Rafraîchissement auto des tableaux de bord          | **Polling 30s ou WebSocket**             |
| **Scalabilité** | Architecture microservices horizontalement scalable | **Auto-scaling Kubernetes**              |
| **Stockage**    | GED - Documents en S3 compatible                    | **AWS S3 ou MinIO on-premise**           |
| **Stockage**    | Durée de rétention des données                      | **10 ans (conformité légale Tunisie)**   |
| **UI/UX**       | Application mobile cross-platform                   | **Flutter (iOS + Android)**              |
| **UI/UX**       | Portail web responsive                              | **React + Tailwind**                     |
| **UI/UX**       | Accessibilité WCAG 2.1 niveau AA                    | **Obligatoire**                          |
| **IA**          | Inférence LLM pour chatbot et matching              | **< 3s par réponse**                     |
| **IA**          | Modèles ML entraînés on-premise                     | **Données ne quittent pas le SI**        |

# **5\. Architecture Technique**

## **🛠 5.1 Stack Technologique**

| **Couche**             | **Technologie**                         | **Rôle**                                                     |
| ---------------------- | --------------------------------------- | ------------------------------------------------------------ |
| **Mobile**             | **Flutter 3.x**                         | Application iOS et Android cross-platform, offline-first     |
| **Web Admin**          | **React 18 + TypeScript + Vite**        | SPA avec routage, state management, design system            |
| **API Gateway**        | **Spring Cloud Gateway**                | Routage, rate limiting, authentification centralisée         |
| **Backend**            | **Spring Boot 3 (Java 21)**             | Microservices métier (référentiel, demandes, formation...)   |
| **Temps réel**         | **Spring WebSocket + STOMP**            | Notifications live, mises à jour tableaux de bord            |
| **Messagerie**         | **Apache Kafka**                        | Events asynchrones inter-services, audit log, emails         |
| **Cache**              | **Redis**                               | Sessions, cache API, rate limiting, file d'attente           |
| **Base de données**    | **PostgreSQL 16**                       | Données transactionnelles, UUID, JSONB pour contenu flexible |
| **Stockage fichiers**  | **AWS S3 / MinIO**                      | GED, PDF générés, pièces jointes, photos EPI                 |
| **IA / ML**            | **Python FastAPI + LangChain + Ollama** | Chatbot RAG, matching CV, analytics prédictifs               |
| **OCR / Audio**        | **Tesseract + OpenAI Whisper**          | Parsing CV PDF, transcription plaintes vocales               |
| **Authentification**   | **Keycloak / Spring Security**          | OAuth2, JWT, gestion des rôles RBAC                          |
| **Containerisation**   | **Docker + Kubernetes**                 | Déploiement, scaling, rolling updates                        |
| **CI/CD**              | **GitHub Actions + ArgoCD**             | Pipeline automatisé test → build → deploy                    |
| **Monitoring**         | **Prometheus + Grafana + Sentry**       | Métriques, alertes, erreurs front/back                       |
| **Notifications push** | **Firebase Cloud Messaging (FCM)**      | Push iOS et Android                                          |

## **🏗 5.2 Architecture Microservices**

| **Service**            | **Responsabilité**                                               |
| ---------------------- | ---------------------------------------------------------------- |
| **svc-referentiel-rh** | Collaborateurs, structure organisationnelle, unités, profils     |
| **svc-demandes**       | Congés, autorisations sortie, ordres de mission, documents admin |
| **svc-formation**      | Demandes formation, invitations, évaluations chaud/froid         |
| **svc-evaluation**     | Évaluations semestrielles et annuelles, objectifs                |
| **svc-plainte**        | Plaintes internes et externes, logs, tableaux de bord            |
| **svc-epi**            | Catalogue EPI, demandes, attributions, stock, traçabilité        |
| **svc-presence**       | Pointage QR/biométrie, feuilles de présence, heures sup          |
| **svc-notification**   | Push FCM, emails, WebSocket - service transversal                |
| **svc-document**       | GED, génération PDF, gestion S3, versioning                      |
| **svc-ia**             | Chatbot RAG, matching CV, analytics prédictifs, transcription    |
| **svc-recrutement**    | Pipeline candidatures, fiches de poste, ATS                      |
| **api-gateway**        | Authentification JWT, routage, rate limiting, CORS               |

# **6\. Roadmap & Priorités de Développement**

P0 = Critique (bloquant) P1 = Haute priorité P2 = Moyenne priorité P3 = Amélioration / Innovation

| **Prio** | **Fonctionnalité**           | **Description**                                               | **Sprint** |
| -------- | ---------------------------- | ------------------------------------------------------------- | ---------- |
| **P0**   | **Structure RH & Auth**      | Hiérarchie Département → Unité → RO → Collaborateur, JWT/RBAC | S1         |
| **P0**   | **Demandes admin (M01)**     | Congé, autorisation sortie (max 4h), ordre mission            | S2         |
| **P0**   | **Documents admin (M02)**    | Demande, suivi, génération PDF, SLA                           | S2         |
| **P0**   | **Notifications temps réel** | Push FCM + WebSocket pour tous les workflows                  | S2         |
| **P1**   | **Plaintes (M04)**           | Internes + externes, vocal → texte, logs                      | S3         |
| **P1**   | **Notes de service (M03)**   | GED, accusé de réception, archive                             | S3         |
| **P1**   | **EPI (M08)**                | Catalogue, demandes, attributions, stock, reçu PDF            | S4         |
| **P1**   | **Formation (M05)**          | Demandes, évaluations chaud/froid avec schedulers             | S4         |
| **P1**   | **Évaluations RH (M07)**     | Semestrielle + annuelle, code couleur, export PDF             | S5         |
| **P2**   | **Présences (M09)**          | Pointage QR + biométrie faciale, feuille mensuelle            | S6         |
| **P2**   | **Besoin personnel (M06)**   | Fiche besoin, workflow, intégration recrutement               | S6         |
| **P2**   | **Dashboards (M12)**         | KPIs temps réel, rapports auto, export                        | S7         |
| **P3**   | **Recrutement IA (M10)**     | ATS, matching CV intelligent, pipeline                        | S8         |
| **P3**   | **Chatbot RH (M11)**         | RAG sur historique RH, transcription vocale                   | S9         |
| **P3**   | **Analytics prédictifs**     | Prédiction absentéisme, score engagement                      | S10        |

# **7\. Corrections & Évolutions par rapport à v1.0**

| **Point**                       | **v1.0**                                                       | **v2.0 - Correction/Évolution**                                                     |
| ------------------------------- | -------------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| **Autorisation de sortie**      | Traitée comme un congé                                         | Module distinct, max 4h, pas de déduction solde, heure début/fin obligatoires       |
| **Workflow autorisation**       | type_sortie enum (ADMIN/PERSO/EXCEPTIONNEL)                    | Supprimé - motif libre obligatoire, plus flexible                                   |
| **profil_acces**                | Non persisté en BDD, ignoré silencieusement                    | Colonne BDD ajoutée, exposé dans l'API, validé côté back                            |
| **Chef département / RO**       | Toujours affiché "Aucun" - bug critique                        | Corrigé : persistance + détection dans dept ET unités enfants                       |
| **Formulaire collaborateur**    | 8 champs inutiles (Qualité, Affectation, profil, supérieur...) | Simplifiés à 8 champs essentiels, profil = COLLABORATEUR par défaut                 |
| **Structure organisationnelle** | Page plate sans hiérarchie                                     | Vue arborescente Département → Unité → Travailleurs, création guidée 2 étapes       |
| **Évaluation annuelle**         | Référencée mais non détaillée                                  | Spécifiée : 3 domaines (Savoir/Savoir-faire/Savoir-être), objectifs N+1, export PDF |
| **Plainte externe**             | Même formulaire que plainte interne                            | Distinguées : interne → RH seul, externe → 3 services simultanément                 |
| **Feuille de pointage**         | Mentionnée dans documents admin                                | Module dédié M09 avec pointage QR + biométrie faciale                               |
| **Matching recrutement**        | Non détaillé                                                   | Module M10 avec ATS, scoring IA, pipeline complet                                   |

# **8\. Glossaire**

| **Terme**     | **Définition**                                                          |
| ------------- | ----------------------------------------------------------------------- |
| **ATS**       | Applicant Tracking System - logiciel de suivi des candidatures          |
| **EPI**       | Équipement de Protection Individuelle (casque, gants, gilet...)         |
| **FCM**       | Firebase Cloud Messaging - service de notifications push Google         |
| **GED**       | Gestion Électronique des Documents                                      |
| **JWT**       | JSON Web Token - standard d'authentification stateless                  |
| **LLM**       | Large Language Model - modèle de langage IA (ex: LLaMA, GPT)            |
| **P95**       | Percentile 95 - 95% des requêtes sous ce seuil de temps                 |
| **RAG**       | Retrieval-Augmented Generation - IA qui interroge une base documentaire |
| **RBAC**      | Role-Based Access Control - contrôle d'accès basé sur les rôles         |
| **RO**        | Responsable Opérationnel - chef d'unité organisationnelle               |
| **RRH**       | Responsable des Ressources Humaines                                     |
| **S3**        | Simple Storage Service - stockage objet cloud (AWS ou compatible MinIO) |
| **SLA**       | Service Level Agreement - engagement de niveau de service               |
| **SIRH**      | Système d'Information des Ressources Humaines                           |
| **STOMP**     | Simple Text Oriented Message Protocol - protocole WebSocket             |
| **WebSocket** | Protocole de communication bidirectionnelle temps réel                  |

Document généré automatiquement - AGUA Service d'Assainissement - Mai 2025

Pour toute question : <rh@agua-assainissement.tn>