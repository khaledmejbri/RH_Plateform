# ADR-M09-FACE — Reconnaissance faciale au pointage (D-P06 à D-P10)

| | |
|---|---|
| **Statut** | accepté (implémenté le 30/10/2026, SCRUM-43 à SCRUM-47 ; contrat d'API révision 2) |
| **Date** | 2026-10-30 |
| **Module** | M09 — Pointage digital (vague reconnaissance faciale, **Cible**) |
| **Auteur** | Architecte SIRH |
| **Références** | `docs/sirh_basefonctionnelle.md` § 4.2, § 14.2 bis (D-P06 à D-P10 et précisions D-P06.a à D-P10.b), § 14.3 à 14.5, § 19 (C-M09-02, C-L-02, C-L-04, C-L-07) ; `docs/adr/ADR-M09-pointage-qr-gps.md` |

---

## Contexte

Le client veut, au pointage, vérifier que la personne qui tient le téléphone est bien le collaborateur.
Contraintes tranchées : comparaison **sur l'appareil** ; le serveur ne reçoit qu'un **score 0–100**, le résultat du
contrôle du vivant et un `device_id` ; aucun visage, aucune image, aucun vecteur côté serveur (C-L-04).
Face ID / empreinte de l'OS sont écartés (ils prouvent le propriétaire du téléphone, pas le collaborateur).

État vérifié dans le code au 30/10/2026 :

- `svc-presence` porte tout M09 (`PresencePointageService`, `presence_db`) ; les routes gateway couvrent déjà
  `/api/rh/v1/mobile/presence/**` et `/api/rh/v1/admin/presence/**` (aucune route à ajouter).
- L'identité du pointage vient de l'en-tête `X-Collaborateur-Id` (`PresenceAccessService.requireCurrentCollaborateurId`),
  non prouvé. Le compte se lit dans le JWT (`currentCompteId()`, claim `identifiant_utilisateur`) ; la fiche et le
  valideur D-H03 s'obtiennent avec le JWT de l'appelant par `GET /api/referentiel/v1/collaborateurs/moi/valideur-pointage`
  (`ReferentielClient.monValideurPointage()`).
- Le mobile **n'envoie aucun `device_id`** aujourd'hui (champ prévu dans `PointageRequest`, jamais renseigné).
- Les erreurs `@Valid` sortent en 422 (`PresenceExceptionHandler`) ; les 400 de cette vague sont des contrôles explicites.
- Mobile : Flutter 3.29.2 sur Codemagic (Dart 3.7), `minSdk 24`, iOS 14.0, `camera`, `mobile_scanner 5.2.3`
  (iOS : pod `GoogleMLKit/BarcodeScanning ~> 6.0.0`), `flutter_secure_storage 9.2.4`.

## Décision

### 1. Mobile : ML Kit (détection + vivacité) + MobileFaceNet sur LiteRT (comparaison)

| Rôle | Paquet (version **figée**, compatible Flutter 3.29.2 / Dart 3.7) |
|---|---|
| Détection, clignement (`left/rightEyeOpenProbability`), rotation (`headEulerAngleY`), suivi (`trackingId`) | `google_mlkit_face_detection: 0.13.1` (+ `google_mlkit_commons 0.11.0`), licence MIT. ≥ 0.13.2 exige Flutter 3.32 : **pas de `^`**. |
| Vecteur facial (embedding) | `tflite_flutter: 0.12.1` (Apache-2.0 ; Android LiteRT 1.4.0, pages 16 Ko conformes Google Play ; iOS TensorFlowLiteSwift 2.12.0) |
| Caméra (flux d'images) | `camera` déjà présent (0.11.2+1 sous 3.29.2), `ImageFormatGroup.nv21` (Android) / `bgra8888` (iOS) |
| Recadrage 112×112 | `image: ^4.5.0` (Dart pur) |
| Stockage chiffré du gabarit et du `device_id` | `flutter_secure_storage` déjà présent (Keystore / Keychain) |
| Conflit de pods iOS | **`mobile_scanner` 5.2.3 → `6.0.11`** : la 5.x tire `GoogleMLKit ~> 6.0.0`, la détection de visage `GoogleMLKit ~> 7.0.0` ; CocoaPods refuse les deux. 6.0.x : aucune rupture d'API Dart (iOS 15.5, MLKit 7, Xcode 15.3). |

Plateformes : **iOS 15.5 minimum** (`platform :ios, '15.5'`, `IPHONEOS_DEPLOYMENT_TARGET = 15.5`) ; Android `minSdk 24`
suffit (ML Kit : 23), `compileSdk 36` en place. ML Kit iOS : appareils 64 bits, pas de simulateur arm64.
Flutter web : `tflite_flutter` importe `dart:ffi` et casse la compilation web → moteur facial derrière un import
conditionnel (`if (dart.library.ffi)`), implémentation vide sur le web ; aucun écran facial sur le web.

**Modèle retenu (V1) : MobileFaceNet de Qualcomm AI Hub** (`huggingface.co/qualcomm/MobileFaceNet`) : export `.tflite`
officiel (pas de conversion maison), poids publiés sous **Apache-2.0**, ≈ 4 Mo, entrée 112×112, embedding **128**.
Fichier `assets/models/mobilefacenet.tflite`, somme SHA-256 et `modele_version` (ex. `mfn-qaihub-1`) dans le dépôt.

**Risque de licence (à remonter, SCRUM-43 / C-L-07)** : aucun modèle d'embedding facial léger trouvé dont **à la fois**
les poids **et** les données d'entraînement soient clairement utilisables commercialement. Le modèle retenu est entraîné
sur MS-Celeb-1M (retiré par Microsoft en 2019) ; les autres candidats ont le même défaut ou pire
(`sirius-ai/MobileFaceNet_TF` : MS1M ; OpenCV SFace : poids Apache-2.0, données non documentées ; EdgeFace :
CC BY-NC-SA ; InsightFace : non commercial). La licence Apache-2.0 des poids ne règle pas la question des données.
Parade : moteur **interchangeable** (interface `FaceEmbedder`, `modele_version` envoyé et stocké, calibrage lié à la
version) ; en cible, licence commerciale d'un fournisseur ou modèle entraîné sur des données sous licence.
Activation subordonnée à l'avis du juriste (C-L-07).

Score : cosinus entre l'embedding du pointage et chacun des 3 embeddings d'enrôlement, on garde le meilleur.
Le « 92 % » est une **échelle produit**, pas un cosinus brut : conversion sur l'appareil, linéaire par morceaux autour
d'un point de fonctionnement `t` calibré : `score = cos ≤ t ? 92 × max(cos, 0) / t : 92 + 8 × (cos − t) / (1 − t)`,
arrondi, borné 0–100. `t` (départ 0,60) est calibré sur vrais téléphones (≥ 20 personnes) et lié à `modele_version`.
Le seuil n'est jamais envoyé au mobile (D-P06.d) : l'appareil envoie le score, le serveur décide.

Vivacité (D-P09, D-P09.a) : 2 défis **distincts** tirés au hasard sur l'appareil parmi `CLIGNER`, `TETE_GAUCHE`,
`TETE_DROITE` ; chrono de 10 s lancé à l'affichage du premier défi ; un seul visage du début à la fin, même
`trackingId` jusqu'à la prise de comparaison (|angle Y| < 10°). À l'enrôlement, les 3 prises suivent les défis, hors chrono.

Taille ajoutée (à mesurer, `flutter build apk --analyze-size`) : ≈ **+15 Mo par architecture**, +25 à 30 Mo pour l'APK universel.
Permissions : `CAMERA` déjà déclarée ; `NSCameraUsageDescription` complétée (« … et vérifier votre visage au pointage ;
aucune photo n'est envoyée »).

### 2. `device_id` : UUID par installation, effacé à la réinstallation

- UUID v4 généré au premier lancement, `flutter_secure_storage` clé `rh_device_id`, envoyé à **chaque** pointage
  (même réglage INACTIVE), à l'enrôlement et à `GET /moi`.
- iOS : le Keychain survit à la désinstallation → marqueur d'installation dans le dossier de l'appli (`path_provider`) ;
  absent au démarrage = effacer `rh_device_id` et les gabarits. Accessibilité `unlocked_this_device`.
- Android : `android:allowBackup="false"`.
- Gabarit stocké par compte (`facial_gabarit_<compteId>`), effacé au retrait, au refus d'une nouvelle version, ou dès que
  `/moi` indique `gabarit_a_effacer = true` (déblocage RH qui invalide l'appareil, statut non enrôlé).

### 3. Backend : tout dans `svc-presence`, aucune route gateway ni secret nouveau

Tables (`ddl-auto=update`, colonnes ajoutées nullables) :

- `presence_reglage_facial` (ligne unique `id = 'SOCIETE'`, absente = INACTIVE / 92) : `statut` ACTIVE|INACTIVE,
  `date_mise_en_service` (date, obligatoire si ACTIVE), `seuil_score` (80–99, défaut 92), `cadre_legal_confirme_par`,
  `cadre_legal_confirme_le`, `modifie_par_compte_id`, `modifie_le`.
- `presence_consentement_version` : `version` (PK, ≤ 32), `texte` (≤ 20 000), `publie_par_compte_id`, `publie_le`.
  La version publiée la plus récente est la **version courante** ; jamais modifiée ni supprimée (preuve du texte accepté).
- `presence_enrolement_facial` (une ligne par **compte**, unique) : `compte_id`, `collaborateur_id` (fiche),
  `collaborateur_nom`, `valideur_compte_id` / `valideur_nom` (instantané D-H03 au dernier appel du collaborateur),
  `statut` NON_ENROLE|ENROLE|REFUSE|REVOQUE|A_REENROLER, `device_id` (appareil enrôlé actif, conservé pendant
  A_REENROLER), `device_id_demande` (nouvel appareil), `device_id_incompatible`, `version_consentement` (acceptée ou
  refusée), `modele_version`, `idempotency_key_enrolement`, `decide_le`, `enrole_le`, `demande_reenrolement_le`,
  `echecs_consecutifs` (défaut 0), `dernier_echec_le`, `bloque_jusqu_a`. Aucune donnée biométrique.
- `presence_audit_facial` (ajout seul, conservé emploi + 5 ans, C-L-02 / C-L-07) : `action`, statut avant / après,
  acteur (compte JWT + rôle), `device_id`, version, motif, horodatage.
  Actions : `CONSENTEMENT_PUBLIE`, `REGLAGE_MODIFIE`, `CADRE_LEGAL_CONFIRME`, `CONSENTEMENT_ACCEPTE`, `CONSENTEMENT_REFUSE`,
  `ENROLEMENT`, `RETRAIT`, `APPAREIL_INCOMPATIBLE`, `DEMANDE_REENROLEMENT`, `DEBLOCAGE_RH`, `BLOCAGE_ECHECS`.
- `presence_pointage` + `score_facial` (smallint), `vivacite_reussie` (bool), `verification_faciale`
  (VERIFIEE|NON_CONSENTIE|NON_DISPONIBLE, nul si aucun contrôle), `modele_facial_version`, `plateforme`.
  Score et indicateur supprimés avec le pointage (C-L-07).
- `PointageStatut` + `REJETE_VISAGE_NON_RECONNU`, `REJETE_VIVACITE`, `REJETE_VERIFICATION_FACIALE_ABSENTE`
  (35 car. ≤ 40) : `PointageStatutConstraintRunner` suit l'enum. Nouveau `FacialConstraintRunner` : CHECK de
  `presence_enrolement_facial.statut`, `presence_audit_facial.action`, `presence_reglage_facial.statut`,
  `presence_pointage.verification_faciale`, `presence_pointage.plateforme`.

États du réglage : **INACTIVE** = ni écran, ni enrôlement, ni contrôle, tout est conservé (D-P06.b) ; **ACTIVE avant la
date** = consentement et enrôlement possibles, aucun contrôle au pointage (D-P06.a) ; **ACTIVE à partir de la date
(Africa/Tunis)** = contrôle exigé. Passage à ACTIVE : version publiée **et** `cadre_legal_valide = true` (D-P06.c).

Identité : tous les endpoints faciaux résolvent le compte depuis le JWT et la fiche par `ReferentielClient`. Au
pointage, si le compte a une ligne d'enrôlement, la fiche du pointage est celle de l'enrôlement ; un
`X-Collaborateur-Id` différent → 403 `IDENTITE_INCOHERENTE`.

Passage en `A_REENROLER` : **demande explicite** de l'appli depuis le nouvel appareil (ou l'appareil enrôlé sans
gabarit) quand `/moi` renvoie `appareil_correspond = false` ou que le gabarit local manque. Pas de bascule
automatique sur la seule présentation d'un autre `device_id` : un jeton volé ne doit pas suffire à faire basculer la
victime. L'ancien appareil reste accepté jusqu'au déblocage ; le déblocage met `device_id` à nul (ancien appareil
invalidé, gabarit effacé à sa synchronisation suivante).

Compteur d'échecs (D-P10.a) : seuls `REJETE_VISAGE_NON_RECONNU` et `REJETE_VIVACITE` comptent ; tout le reste est
neutre. Commun ENTREE / SORTIE. Remise à zéro : pointage `VERIFIEE`, début du blocage (équivaut à la fin), changement
de jour Africa/Tunis (si `dernier_echec_le` n'est pas aujourd'hui, le compteur repart de 0 avant d'ajouter l'échec).
Au 3ᵉ : `bloque_jusqu_a = maintenant + 15 min`, audit `BLOCAGE_ECHECS`, alerte au valideur D-H03 (compte, lu par
`monValideurPointage()` ; repli sur l'instantané, puis destinataire `RH`, jamais ADMIN, pas de notification au
collaborateur). Ligne verrouillée `PESSIMISTIC_WRITE`. `java.time.Clock` injecté. Tentative pendant le blocage : 409,
aucun pointage, journal WARN.

Compatibilité : nouveaux champs d'entrée facultatifs, réponses `NON_NULL` ; INACTIVE ou date non atteinte =
comportement actuel. Après la mise en service, une appli sans vérification faciale est rejetée (mise à jour imposée
avant la date) — choix assumé (arbitrage du 30/10/2026).

## Contrat d'API définitif

Format d'erreur : `ErrorResponse` existant (`code`, `message`, `details`, `http_status`). 403 `ACCESS_DENIED` sur tout
endpoint si le rôle manque ; 422 `VALIDATION_ERROR` pour les champs `@Valid` (format, taille, obligatoire).

### Mobile — `/api/rh/v1/mobile/presence/verification-faciale`, `@PreAuthorize("hasRole('USER')")`

Contrôle commun : `X-Collaborateur-Id` présent et différent de la fiche du JWT → **403 `IDENTITE_INCOHERENTE`**.

| Endpoint | Corps | Succès | Erreurs nommées |
|---|---|---|---|
| `GET /moi?device_id=` | — | 200 `EtatFacialMoi` (ci-dessous) | — |
| `GET /consentement/courant` | — | 200 `{version, texte, publie_le}` | 404 `AUCUNE_VERSION_PUBLIEE` |
| `POST /enrolement` | `{device_id (UUID), version_consentement, vivacite_reussie: true, modele_version ≤ 32, idempotency_key (UUID)}` | 201 `EtatFacialMoi`, statut ENROLE (depuis NON_ENROLE ou REFUSE). Même `idempotency_key` déjà appliquée → 200 identique | 409 `VERIFICATION_FACIALE_INACTIVE` ; 409 `DEJA_ENROLE` ; 409 `REENROLEMENT_A_DEBLOQUER` (REVOQUE, A_REENROLER, ENROLE autre appareil) ; (un enrôlement réussi depuis un `device_id` marqué incompatible efface le marquage — revue du 30/10/2026 ; plus de 409 `APPAREIL_INCOMPATIBLE`) ; 422 `VERSION_CONSENTEMENT_OBSOLETE` ; 422 `VIVACITE_ECHOUEE` |
| `POST /consentement/acceptation` | `{version_consentement}` | 200, reste ENROLE (ou A_REENROLER), version mise à jour (D-P07.f) | 409 `VERIFICATION_FACIALE_INACTIVE` ; 409 `RECONSENTEMENT_NON_ATTENDU` (statut ≠ ENROLE/A_REENROLER ou version déjà acceptée) ; 422 `VERSION_CONSENTEMENT_OBSOLETE` |
| `POST /consentement/refus` | `{version_consentement}` | 200 : NON_ENROLE → REFUSE ; ENROLE / A_REENROLER (reconsentement) → REVOQUE, appareil invalidé | 409 `VERIFICATION_FACIALE_INACTIVE` ; 409 `DECISION_DEJA_ENREGISTREE` (REFUSE, REVOQUE, ou ENROLE sans reconsentement attendu) ; 422 `VERSION_CONSENTEMENT_OBSOLETE` |
| `POST /consentement/retrait` | — | 200, ENROLE / A_REENROLER → REVOQUE ; déjà REVOQUE → 200 sans changement (renvoi d'un retrait en attente). **Permis même INACTIVE** | 409 `AUCUN_CONSENTEMENT_ACTIF` (NON_ENROLE, REFUSE) |
| `POST /appareil-incompatible` | `{device_id, raison: SANS_CAMERA_FRONTALE\|MODELE_NON_CHARGEABLE}` | 200, statut reste NON_ENROLE, `device_id_incompatible` enregistré (D-P07.g) | 409 `VERIFICATION_FACIALE_INACTIVE` ; 409 `STATUT_INCOMPATIBLE` (statut ≠ NON_ENROLE) |
| `POST /demande-reenrolement` | `{device_id}` | 200, statut A_REENROLER, `device_id_demande` enregistré, notification `RH` | 409 `VERIFICATION_FACIALE_INACTIVE` ; 409 `DEJA_ENROLE` (même appareil avec gabarit, signalé par l'appli) ; 409 `DEMANDE_DEJA_EN_COURS` ; 409 `STATUT_INCOMPATIBLE` (NON_ENROLE, REFUSE) |

`POST /demande-reenrolement` depuis l'appareil enrôlé est accepté (cas « gabarit perdu ») ; l'appli ne l'appelle que si
son gabarit manque.

`EtatFacialMoi` : `{verification_active (statut ACTIVE), controle_exige (ACTIVE et date atteinte), date_mise_en_service,
version_consentement_courante, statut, version_consentement_acceptee, reconsentement_requis (ENROLE/A_REENROLER et
version acceptée ≠ courante), ecran_consentement_requis (actif et NON_ENROLE, ou reconsentement_requis), appareil_correspond
(nul si aucun appareil enrôlé), appareil_incompatible, gabarit_a_effacer (statut ∉ {ENROLE, A_REENROLER} ou
appareil_correspond = false après déblocage), echecs_consecutifs, bloque_jusqu_a, decide_le}`. **Pas de seuil.**

### Pointage — `POST /api/rh/v1/mobile/presence/pointages` (existant)

Champs ajoutés, facultatifs : `score_facial` (entier), `vivacite_reussie` (bool), `modele_facial_version` (≤ 32),
`plateforme` (`ANDROID`|`IOS`|`WEB`) ; `device_id` désormais toujours envoyé.

Contrôle explicite avant tout : `score_facial` hors 0–100, ou `score_facial` fourni sans `vivacite_reussie` →
**400 `PREUVE_FACIALE_INVALIDE`** (aucun pointage, non compté). `vivacite_reussie = false` sans score est valide.
Blocage en cours (statut ENROLE ou A_REENROLER) → **409** `{code: "POINTAGE_BLOQUE_VISAGE", message, bloque_jusqu_a, http_status: 409}`.
Réponse : champs existants + `verification_faciale`, `score_facial`, `vivacite_reussie`, `echecs_consecutifs`,
`bloque_jusqu_a` (au 3ᵉ échec), en `NON_NULL`. Les rejets restent des 200 avec `statut = REJETE_*` (comportement actuel).

### Admin — `/api/rh/v1/admin/presence/verification-faciale`

Lecture `hasAnyRole('RH','DIRECTION','ADMIN')` ; écriture `hasAnyRole('RH','ADMIN')` (DIRECTION → 403 `ACCESS_DENIED`).

| Endpoint | Rôle | Corps / paramètres | Succès | Erreurs nommées |
|---|---|---|---|---|
| `GET /reglage` | lecture | — | `{statut, date_mise_en_service, seuil_score, version_consentement_courante, controle_exige, cadre_legal_confirme_par, cadre_legal_confirme_le, modifie_par, modifie_le}` | — |
| `PUT /reglage` | écriture | `{statut, date_mise_en_service, seuil_score, cadre_legal_valide}` | 200 ; audit `REGLAGE_MODIFIE` (+ `CADRE_LEGAL_CONFIRME` au passage à ACTIVE) | **400 `SEUIL_HORS_PLAGE`** (hors 80–99) ; 422 `VERSION_CONSENTEMENT_NON_PUBLIEE` ; 422 `CONFIRMATION_CADRE_LEGAL_REQUISE` (ACTIVE sans `cadre_legal_valide = true`) ; 422 `DATE_MISE_EN_SERVICE_REQUISE` |
| `GET /consentements` | lecture | — | liste `{version, publie_le, publie_par, courante}` | — |
| `GET /consentements/{version}` | lecture | — | `{version, texte, publie_le, publie_par}` | 404 `VERSION_INTROUVABLE` |
| `POST /consentements` | écriture | `{version ≤ 32, texte 50–20 000}` | 201, devient courante (reconsentement à la connexion suivante) ; audit `CONSENTEMENT_PUBLIE` | 409 `VERSION_EXISTANTE` |
| `GET /enrolements?statut=&q=&appareil_incompatible=&page=&size=` | lecture | — | page `{id, compte_id, collaborateur_id, collaborateur_nom, statut, version_consentement, decide_le, enrole_le, demande_reenrolement_le, device_id_masque, device_id_demande_masque, appareil_incompatible, echecs_consecutifs, bloque_jusqu_a}` (6 derniers caractères des `device_id`) | — |
| `GET /enrolements/{id}` | lecture | — | ligne ci-dessus + `historique` `[{action, statut_avant, statut_apres, acteur_nom_ou_compte, acteur_role, motif, version, horodatage}]` | 404 `ENROLEMENT_INTROUVABLE` |
| `POST /enrolements/{id}/deblocage` | écriture | `{motif 5–500}` | 200, A_REENROLER → NON_ENROLE, `device_id` et `device_id_demande` à nul, compteur et blocage remis à zéro ; notification collaborateur + responsable (instantané) ; audit `DEBLOCAGE_RH` | 404 `ENROLEMENT_INTROUVABLE` ; **403 `AUTO_DEBLOCAGE_INTERDIT`** ; **409 `DEJA_ENROLE`** ; 409 `RIEN_A_DEBLOQUER` |
| `GET /api/rh/v1/admin/presence/pointages` (existant) | lecture | + `verification_faciale` (liste, ex. `NON_CONSENTIE,NON_DISPONIBLE`) | + champs faciaux | — |

Limite V1 acceptée : la liste des enrôlements ne contient que les collaborateurs **ayant une ligne** (une décision ou
une démarche) ; un collaborateur qui n'a jamais ouvert l'appli depuis l'activation n'y figure pas.

Notifications (`rh.notifications`, `PresenceEventPublisher.notifier`, au compte) : demande de ré-enrôlement → `RH` ;
déblocage → collaborateur + responsable ; 3ᵉ échec → responsable D-H03, repli `RH`.

## Table de décision du pointage

Ordre (D-P08.a) : 400 `PREUVE_FACIALE_INVALIDE` → 409 `POINTAGE_BLOQUE_VISAGE` → contrôles QR / site / GPS actuels
(un rejet ici n'ouvre pas l'étape visage, neutre pour le compteur) → étape visage ci-dessous.

| Réglage | Statut | Appareil / preuve | Résultat | Compteur |
|---|---|---|---|---|
| INACTIVE, ou ACTIVE avant la date | quelconque | quelconque (champs faciaux ignorés, non stockés) | comportement actuel, indicateur nul | — |
| ACTIVE, date atteinte | pas de ligne / NON_ENROLE | `device_id` = appareil marqué incompatible, ou `plateforme = WEB` | `VALIDE`, `NON_DISPONIBLE` | — |
| idem | pas de ligne / NON_ENROLE | autre cas | `REJETE_VERIFICATION_FACIALE_ABSENTE` (« Terminez votre enrôlement ») | neutre |
| idem | REFUSE / REVOQUE | quelconque | `VALIDE`, `NON_CONSENTIE` | — |
| idem | ENROLE ou A_REENROLER | `device_id` ≠ appareil enrôlé (ou nul), `plateforme = WEB`, ou score et vivacité absents | `REJETE_VERIFICATION_FACIALE_ABSENTE` (puis D-P04) | neutre |
| idem | ENROLE ou A_REENROLER (appareil enrôlé) | `vivacite_reussie = false` | `REJETE_VIVACITE` | + 1 |
| idem | ENROLE ou A_REENROLER (appareil enrôlé) | vivacité OK, score < seuil | `REJETE_VISAGE_NON_RECONNU` | + 1 |
| idem | ENROLE ou A_REENROLER (appareil enrôlé) | vivacité OK, score ≥ seuil | `VALIDE`, `VERIFIEE`, score stocké | remis à 0 |

S'applique à l'ENTREE et à la SORTIE. Un reconsentement en attente ne change pas la décision (l'écran est imposé à la connexion).

## Alternatives écartées

- `local_auth` (Face ID / empreinte) : prouve le propriétaire du téléphone, accepte le code PIN.
- Comparaison côté serveur (photo ou vecteur envoyé) : contraire à C-L-04.
- Géométrie des points ML Kit sans embedding : trop peu discriminante.
- Poids `sirius-ai` convertis à la main : provenance moins traçable que l'export officiel Qualcomm, même jeu d'entraînement.
- EdgeFace, InsightFace : licences non commerciales.
- `google_mlkit_face_detection ≥ 0.13.2` / `camera ≥ 0.11.3` : exigent Flutter ≥ 3.32 / 3.35.
- `mobile_scanner 7.x` : saut majeur plus large que nécessaire ; 6.0.11 suffit.
- Bascule automatique en A_REENROLER à la connexion depuis un autre appareil : un jeton volé suffirait à faire basculer la victime.
- Nonce de pointage serveur en V1 : sans clé matérielle qui le signe, gain quasi nul ; gardé pour la cible avec l'attestation.
- Texte de consentement embarqué dans l'appli : le RH doit pouvoir publier une version (D-P06.d, § 4.2) → texte servi par le serveur.
- Nouveau microservice « biométrie » : rien de biométrique côté serveur, `svc-presence` suffit.
- Endpoint interne référentiel « valideur d'une fiche » : remplacé en V1 par l'instantané pris au dernier appel du collaborateur.

## Conséquences

- iOS minimum 14.0 → **15.5** ; `mobile_scanner` 6.0.11 ; APK ≈ +15 Mo par architecture.
- Après la date de mise en service, chaque collaborateur sur appareil compatible doit avoir décidé (enrôlé ou refusé)
  pour pointer ; l'appli doit être mise à jour avant la date.
- Qualité réelle (faux rejets, fausses acceptations) mesurable seulement sur vrais téléphones ; `t` et le seuil se
  calibrent en recette terrain. Recette automatique : API (scores simulés) + tests unitaires.
- Blocage 15 min ou attente de déblocage → pointage manuel D-P04 (charge pour les responsables).
- Purge de l'audit à départ + 5 ans : hors V1 (aucune purge ; à outiller avant la première échéance).
- Risque de licence du modèle porté au juriste (C-L-07) ; remplacement possible sans changer l'API.

## Sécurité / RGPD

| Menace | Scénario | Mesure V1 | Reste en Cible |
|---|---|---|---|
| Usurpation (S) | Un collègue pointe avec le téléphone d'un autre | Visage + vivacité + appareil enrôlé ; 3 échecs → blocage + alerte responsable | Anti-spoofing passif (photo, écran, masque) |
| Usurpation (S) | `X-Collaborateur-Id` forgé | Fiche prise dans l'enrôlement (résolue par JWT) ; en-tête différent → 403 | Supprimer l'en-tête partout |
| Altération (T) | Appli modifiée : `score = 99`, `vivacite_reussie = true` | Décision serveur selon le statut stocké ; `device_id` lié à l'enrôlement ; obfuscation Dart ; scores stockés, visibles RH | Play Integrity / App Attest + nonce + HMAC du payload |
| Altération (T) | NON_ENROLE qui déclare `plateforme = WEB` ou marque son appareil incompatible pour échapper au contrôle | Pointage `NON_DISPONIBLE` visible et filtrable par le RH ; marquage audité ; impossible pour un ENROLE | Attestation d'appareil |
| Altération (T) | Rejeu d'une requête capturée | TLS, JWT, clé d'idempotence, QR écran 30 s, GPS | Nonce signé |
| Répudiation (R) | « Je n'ai jamais consenti à ce texte » / déblocage contesté | Textes publiés immuables ; audit append-only (version, horodatage, acteur JWT, motif) | Signature de l'audit |
| Fuite (I) | Vol de la base ou des journaux | Rien de biométrique côté serveur ; seuil non exposé au mobile ; `device_id` masqué en liste ; gabarit Keystore/Keychain `ThisDeviceOnly`, `allowBackup=false` | — |
| Déni de service (D) | Jeton volé : 3 échecs pour bloquer la victime | 15 min max, D-P04 toujours possible ; pas de bascule A_REENROLER automatique | Alerte RH sur rafales |
| Élévation (E) | RH qui débloque son propre ré-enrôlement ; DIRECTION qui modifie | 403 `AUTO_DEBLOCAGE_INTERDIT` ; écriture RH/ADMIN seulement | — |

RGPD : consentement sur texte versionné publié par le RH, horodaté ; nouvelle version = reconsentement ; retrait à tout
moment (même INACTIVE) qui efface le gabarit ; refus non pénalisant ; score supprimé avec le pointage ; traces de
consentement emploi + 5 ans ; aucune biométrie traitée tant que le réglage est INACTIVE ; activation subordonnée à
C-L-07 (INPDP, texte, durées, licence du modèle).
