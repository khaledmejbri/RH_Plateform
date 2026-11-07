# ADR-UAT-LOT2 — Fiches de détail, liens croisés, retards et notifications cliquables (D-N01, D-N02, D-P19, D-E15)

| | |
|---|---|
| **Statut** | proposé (06/11/2026), à valider par l'utilisateur avant le développement des tickets 2A à 2F |
| **Date** | 2026-11-06 |
| **Module** | Transverse (M00, M01, M02, M04, M05, M07, M09, NOTIF), recette UAT acheteur, lot 2 |
| **Tickets** | 2A (référentiel), 2B (évaluation), 2C (présence), 2D (web), 2E (mobile), 2F (notifications cliquables), 2G (notifications conservées, décision utilisateur du 06/11) |
| **Références** | `docs/sirh_basefonctionnelle.md` § 3.4 (D-N01, D-N02), § 4.2, § 14.2 sexies (D-P19), D-E15, D-L01, D-L02, D-P11.c, D-P17 ; `docs/jira/fiche_lot2_uat.md` ; `ADR-M09-horaires-heures-sup.md` ; `ADR-M07-alertes-ecart.md` |

---

## Contexte

Le client veut ouvrir la fiche de toute entité depuis son nom (« je clique sur un nom et j'ai toutes ses
informations : retard, hiérarchie, pointage… »), sur le web (RH, DIRECTION, ADMIN) et sur le mobile (fiche annuaire
pour tous, fiche d'équipe pour le valideur D-H03). Il faut aussi un calcul des retards (D-P19) et des notifications
qui ouvrent l'objet.

État vérifié dans le code au 06/11/2026 :

- **Résolution des noms entre services.** Le référentiel expose déjà deux résolutions par lot sous
  `/internal/referentiel` (jeton `X-Internal-Token`, comparaison à temps constant) :
  `POST /collaborateurs/resolution-pointage` (fiches seulement, nom, matricule, actif, direction, valideur D-H03)
  et `POST /collaborateurs/affectations` (fiche **ou** compte, nom, matricule, direction / département / service),
  1 à 100 identifiants. `svc-presence` les appelle par page (pas de N+1) via `ReferentielClient`.
  `svc-evaluation` n'appelle que `/collaborateurs/actifs-evaluation` et `/{ficheId}/fiche-evaluation`.
  **Aucun client HTTP inter-services n'a de délai d'attente** (`RestClient.builder()` sans `requestFactory`).
- **Cause commune d'EVA-8 / U03.** `GET /api/rh/v1/admin/evaluations` (`EvaluationAdminController.listAllEvaluations`)
  renvoie `collaborateurIdentifiant` et `superieurIdentifiant` seulement, alors que l'entité `Evaluation` porte déjà
  `collaborateur_nom`, `superieur_nom` (instantanés, remplis pour 35 / 35 évaluations en base locale). Le web affiche
  donc des UUID tronqués (U03) et, faute de libellé et de lien, un badge « profil incomplet » sans explication (EVA-8,
  instantané famille / niveau vide pour 32 / 35). Le serveur ne renvoie pas les données lisibles qu'il possède :
  c'est la règle D-L01.a (« toute réponse destinée à un écran porte les noms ») qui manque, pas une donnée.
- **Valideur D-H03.** Règle unique `SuperieurHierarchieRules.resoudreValideurM01(Collaborateur)` dans le
  référentiel ; `PerimetreValidationService.perimetreDe(ficheId)` liste les collaborateurs actifs dont une fiche est
  le valideur (parcourt toutes les fiches actives). `rh_demande_administrative.valideur_attendu_identifiant`
  (FK vers la fiche) est **stocké** à la soumission.
- **Horaire en vigueur.** Chaque pointage accepté sur un site à horaire porte déjà l'instantané
  `journee`, `horaire_debut_ts`, `horaire_fin_ts` (posé par `PresencePointageService.pointer` et, pour un manuel, par
  `PresenceDemandeManuelleService` à la validation, ADR-M09-HORAIRES § 1). `horaire_debut_ts` = début brut de
  l'horaire du jour (sans tolérance). `presence_horaire_site_historique` garde avant / après en JSON.
- **Évaluation côté collaborateur.** `GET /api/rh/v1/mobile/evaluations/{id}/reponses` renvoie la **liste d'entités
  JPA `EvaluationAnswer`**, `note_manager`, `reponse_manager` et `commentaire_manager` compris, quel que soit le
  statut : fuite contraire à D-E15 et au principe « pas d'entité exposée ». `/{id}/analytics` (mobile) est à vérifier
  sous le même angle (moyenne du responsable).
- **Notifications.** `NotificationMessage(type, recipient, subject, content)` dans chaque producteur ; `type` est le
  **canal** (`WEBSOCKET`, `EMAIL`, `BOTH`). Les identifiants d'objet sont glissés dans `content` sous forme de
  préfixe `TYPE|cle=valeur|texte` (évaluation, alerte d'écart, QR de site) et retirés à l'affichage par une regex
  côté web (`notificationContenu.ts`) et mobile (`notification_model.dart`). `svc-notification` désérialise dans une
  classe Lombok à 4 champs et relaie **cet objet** sur STOMP : un champ ajouté par un producteur est perdu s'il n'est
  pas ajouté aussi dans `svc-notification`.
- **Gateway.** Toutes les routes utiles existent : `/api/referentiel/**`, `/api/rh/v1/**` (référentiel par défaut),
  `/api/rh/v1/{admin,mobile}/evaluations/**`, `/api/rh/v1/{admin,mobile}/presence/**`. `/internal/**` n'est routé
  nulle part.
- Conventions JSON existantes : référentiel et présence en `snake_case` (`@JsonProperty`), évaluation en
  `camelCase`. On les garde (pas de migration de style dans ce lot).

## Décision

### 1. Noms et libellés : renvoyés par chaque service, résolus par lot avec cache (D-L01.a)

**Règle.** Toute réponse destinée à un écran porte le nom, le matricule et les libellés des objets qu'elle référence.
Le client n'appelle jamais un autre service pour afficher un nom.

**Source du nom, par ordre :**

1. **Résolveur par lot** dans le service consommateur : composant `NomsCollaborateurs` (un par service :
   `svc-evaluation`, `svc-presence`), méthode `Map<UUID, PersonneResumee> resoudre(Collection<UUID> ficheOuCompte)`.
   - Cache Caffeine local, clé = identifiant (fiche ou compte), **TTL 5 min**, 10 000 entrées max. Les manquants
     partent en **un seul** `POST /internal/referentiel/collaborateurs/affectations` par tranche de 100
     (`getAll` / `loadAll`). Une page de 50 lignes coûte au plus 1 appel, 0 si tout est en cache.
   - Délai d'attente : connexion 2 s, lecture 3 s (propriétés `*.referentiel.timeout-connexion-ms`,
     `*.referentiel.timeout-lecture-ms`). Tous les `RestClient` existants vers le référentiel reçoivent ces délais
     dans le même lot (dette relevée en revue).
   - **Lecture dégradée** : référentiel injoignable → les listes répondent quand même, avec le nom de
     l'instantané local s'il existe, sinon `null` ; le client affiche « Nom indisponible » (jamais l'identifiant).
     Une **décision** (§ 2) ne se dégrade jamais (503).
2. **Instantané local** quand la règle métier l'exige (alerte d'écart, profil d'une évaluation, bénéficiaire
   d'heures sup) : affiché tel quel, c'est une donnée figée volontairement.

`PersonneResumee` (interne aux services) = `collaborateur_id`, `compte_id`, `nom_complet`, `matricule`, `actif`,
`poste_libelle`, `unite_id`, `unite_libelle`. On **enrichit** la réponse de `/collaborateurs/affectations` de
`actif`, `poste_libelle`, `unite_id`, `unite_libelle` (ajout de champs, compatible : les clients ont
`@JsonIgnoreProperties(ignoreUnknown = true)`). Côté référentiel, la résolution charge fiches et unités en une
requête (`join fetch`) ; la remontée des parents d'unité passe par le cache `unites` existant.

**Pas de nouvel endpoint de résolution** : `affectations` accepte déjà fiche ou compte et renvoie l'essentiel.

**Correction EVA-8 / U03 (2B).** `GET /api/rh/v1/admin/evaluations` et `/collaborateur/{id}` renvoient
`collaborateurNom`, `collaborateurMatricule`, `superieurNom`, `superieurMatricule` (résolveur, repli instantané),
`familleMetierLibelle`, `niveauSenioriteLibelle` (libellé lu au référentiel, § 1 bis) et `motifProfilIncomplet`
(`FAMILLE_ABSENTE`, `NIVEAU_ABSENT`, `LES_DEUX`, ou `null`) pour que le badge s'explique. Les noms d'identifiant
restent dans la réponse (liens), mais aucun écran ne doit en dépendre pour afficher.

**1 bis. Libellés de famille et de niveau en lot 2.** En attendant le paramétrage (lot 3), `svc-evaluation` ne
relaie pas le JWT de l'utilisateur pour lire `/api/referentiel/v1/familles-metier` : on ajoute au référentiel
`GET /internal/referentiel/libelles-profil` → `{ "familles": {code: libelle}, "niveaux": {code: libelle} }`
(jeton interne, cache 60 s côté évaluation). Au lot 3, sa source devient la table de paramètres
(ADR-UAT-LOT3 § 7) sans changer l'appelant.

### 2. Fiche d'équipe mobile : endpoint interne « est-il le valideur D-H03 ? »

`svc-presence` doit décider si l'appelant peut voir la présence d'un collaborateur (D-N02). Décision sensible :
identité **depuis le JWT**, contrôle **au référentiel**, aucune mise en cache, échec fermé.

```
GET /internal/referentiel/collaborateurs/{collaborateurId}/valideur?compte_id={compteAppelant}
X-Internal-Token: <jeton>
200 { "est_valideur": true, "collaborateur_actif": true, "valideur_fiche_id": "<uuid>" }
401 sans jeton ou jeton faux ; 400 identifiant mal formé
```

- `compte_id` = claim `identifiant_utilisateur` du JWT, lu **localement** par `svc-presence` (pas de relais du
  jeton, un seul appel). Le référentiel retrouve la fiche du compte, applique `resoudreValideurM01` au collaborateur
  et compare.
- Collaborateur inconnu, compte sans fiche, collaborateur inactif : `est_valideur: false` (200). `svc-presence`
  répond alors **403 `HORS_PERIMETRE`**, jamais 404 (pas d'énumération des fiches).
- Référentiel injoignable ou délai dépassé : **503 `REFERENTIEL_INDISPONIBLE`**, aucune donnée renvoyée.
- Si l'en-tête `X-Collaborateur-Id` est présent et diffère de la fiche de l'appelant : 403 `IDENTITE_INCOHERENTE`
  (règle SCRUM-52, réutiliser le contrôle existant).
- Le référentiel applique la **même** règle en local pour ses propres endpoints d'équipe (§ 5.1), sans appel.

Choix écarté : réutiliser `resolution-pointage` (deux appels — `moi` puis résolution — et logique de comparaison
dupliquée dans `svc-presence`).

### 3. Retards (D-P19) : calcul à la volée sur l'instantané d'horaire du pointage

**Horaire en vigueur à la date = l'instantané `horaire_debut_ts` porté par l'ENTREE retenue.** Aucune reconstruction
depuis `presence_horaire_site_historique`, aucune table nouvelle. C'est exactement D-P11.c (une modification
s'applique aux pointages postérieurs à son enregistrement) et cela satisfait le critère « horaire changé le 04/11 de
08:00 à 09:00 : ENTREE du 03/11 à 08:30 → 30 min » (l'ENTREE du 03/11 porte 08:00).

Calcul (classe pure `RetardCalcul`, `Clock` injectée, appelée depuis `TempsTravailCalcul` par
journée × collaborateur × site) :

1. Pointages retenus de la journée (`VALIDE`, `VALIDE_MANUEL`), triés par `server_ts` (heure déclarée pour un
   manuel, déjà portée par `server_ts`).
2. Le premier retenu est une `SORTIE`, ou aucune `ENTREE` : `retard_minutes = null` (« — »).
3. `horaire_debut_ts` de cette première ENTREE est `null` (site sans horaire, jour fermé, pointage antérieur au
   02/11/2026) : `null`.
4. `ecart = server_ts − (horaire_debut_ts + delaiGrace)` ; `retard_minutes = max(0, floor(ecart / 60 s))`.
   `0` = à l'heure (pas un retard) ; `> 0` = retard. Exemples du ticket : 08:12:40 → 12 ; 08:00:59 → 0 ; 07:50 → 0.
5. `delaiGrace` : constante `Duration.ZERO` dans `PresenceProperties` (`presence.retard.delai-grace-minutes=0`) en
   lot 2 ; lue dans `presence_parametres` au lot 3 (D-R06).

Limite assumée : pour un pointage manuel, l'instantané est celui de l'horaire en vigueur **à la validation**
(comportement D-P12.c existant). Aucune notification, aucun stockage (D-P19.f).

Le pointage antérieur au 02/11/2026 (pas d'instantané) n'a pas de retard : pas de reprise de données.

### 4. Notifications cliquables (2F) : champs structurés dans le message Kafka

Le message `rh.notifications` reçoit des champs **facultatifs** ; `content` devient du texte pur.

```json
{
  "type": "WEBSOCKET",
  "recipient": "<compte_id> | RH",
  "subject": "Demande validée",
  "content": "Votre demande de congé du 25/11/2026 au 27/11/2026 a été validée par Omar Belhadj.",
  "notification_id": "<uuid v4, généré par le producteur>",
  "evenement": "DEMANDE_VALIDEE_RESPONSABLE",
  "objet_type": "DEMANDE",
  "objet_id": "<uuid>",
  "objet_numero": "DEM-202611-0007",
  "emis_le": "2026-11-06T09:12:40Z"
}
```

- `objet_type` ∈ `DEMANDE`, `DOCUMENT`, `PLAINTE`, `FORMATION`, `EVALUATION`, `ALERTE_ECART`, `POINTAGE_MANUEL`,
  `HEURES_SUP`, `SITE` (expiration de QR, web RH seulement). Liste fermée côté producteurs ; **un client qui ne
  connaît pas la valeur ne crée pas de lien** (ouvre la liste du module ou rien).
- `objet_id` = identifiant technique (UUID) de l'objet, jamais affiché ; `objet_numero` = numéro lisible D-L02 s'il
  existe (affichable).
- `evenement` : code libre du producteur (icône, regroupement) ; jamais affiché brut.
- `notification_id` : dédoublonnage côté client (l'appli peut recevoir deux fois le même message après reconnexion).
- Aucune donnée nouvelle sensible : le texte existait déjà, l'identifiant n'ouvre rien sans les droits.
- **`svc-notification`** : ajouter ces 6 champs (annotés `@JsonProperty` snake_case) à `dto/NotificationMessage` pour
  qu'ils soient relayés sur STOMP ; aucune autre logique.
- **Producteurs** : `svc-referentiel-rh` (`RhNotificationPublisher`), `svc-evaluation` (`EvaluationEventPublisher`,
  `AlerteEcartNotificationListener`), `svc-presence` (`PresenceEventPublisher`, `HeuresSupNotifier`) passent à un
  `NotificationMessage` à 10 composantes avec une fabrique `NotificationMessage.vers(compte, sujet, texte, objetType,
  objetId, numero, evenement)` ; le préfixe `TYPE|cle=…|` disparaît de `content`.
- **Clients** : ouverture par `objet_type` + `objet_id` (table ci-dessous). Message sans `objet_type` (ancien format,
  ou producteur non migré) : on garde **une version** le parseur de préfixe actuel en repli, puis on le supprime.
  Les notifications n'étant pas persistées (M-91), la transition est courte.
- Objet inaccessible (403) ou disparu (404) à l'ouverture : écran « Élément introuvable » (aucun détail de cause).

| `objet_type` | Web (`rh-admin-web`) | Mobile (`rh_mobile_app`, go_router) |
|---|---|---|
| `DEMANDE` | `/app/demandes-administratives/:id` | `/demandes-admin/:id` (existe) |
| `DOCUMENT` | `/app/documents-administratifs/:id` | `/documents/:id` (existe) |
| `PLAINTE` | `/app/plaintes/:id` | `/plaintes/:id` (existe) |
| `FORMATION` | `/app/formations/:id` | `/formations/:id` (nouveau) |
| `EVALUATION` | `/app/evaluations/:id` | `/evaluations/:id` (existe) |
| `ALERTE_ECART` | `/app/evaluations/alertes-ecart/:id` | — (jamais envoyée à un collaborateur, D-E09) |
| `POINTAGE_MANUEL` | `/app/pointage/manuels/:id` | `/pointage/manuels/:id` (nouveau) |
| `HEURES_SUP` | `/app/heures-sup/:id` | `/heures-sup/demandes/:id` (nouveau) |
| `SITE` | `/app/pointage/sites/:id` | — |

### 5. Contrats d'API

Erreurs : format existant de chaque service (`code`, `message`). `400` = paramètre mal formé, `403` = rôle ou
périmètre, `404` = objet inconnu pour un back-office (pour un mobile hors périmètre : 403). Toute liste vide = `200 []`.
DIRECTION : `BACKOFFICE_LECTURE` partout ci-dessous, aucune écriture nouvelle, **sauf les plaintes** (décision
utilisateur du 06/11/2026, révise le CPD D-N01 : plaintes réservées aux RH). Seule route gateway ajoutée : celle des
notifications conservées (§ 7).

**Plaintes et DIRECTION.** Nouvelle expression `PreAuthorizeExpressions.PLAINTES_LECTURE = "hasAnyRole('RH','ADMIN')"`
appliquée à `GET /api/rh/v1/plaintes/liste` (A6) — qui passe de `BACKOFFICE_LECTURE` à `PLAINTES_LECTURE`. Pour
`GET /api/rh/v1/plaintes/{id}` et `/{id}/suivi` (`isAuthenticated()`, contrôle dans le service) : un appelant dont
le seul rôle back-office est `DIRECTION` reçoit 403 sauf s'il est l'auteur (ou le responsable valideur d'une plainte
externe, règle existante). ADMIN garde l'accès (même profil d'écriture que la RH, à confirmer : « réservées aux RH »
pourrait exclure aussi ADMIN — une ligne à changer). La recherche globale n'indexe pas `PLT-` pour la DIRECTION ;
aucune notification de plainte n'est adressée à la DIRECTION. Le web masque l'onglet Plaintes de la fiche 360° pour
la DIRECTION (le serveur reste le garde).

Type commun **`PersonneResume`** (référentiel, `snake_case`) :
`{ "collaborateur_id", "nom_complet", "matricule", "poste_libelle", "intitule_role", "unite_id", "unite_libelle", "actif" }`
(`intitule_role` = D-H06, livré par le lot 1).

Type commun **`UniteResume`** : `{ "unite_id", "libelle", "type_noeud", "type_libelle" }`.

#### 5.1 Ticket 2A — `svc-referentiel-rh`

| # | Méthode et chemin | `@PreAuthorize` | Réponse 200 | Erreurs |
|---|---|---|---|---|
| A1 | `GET /api/referentiel/v1/collaborateurs/{id}/hierarchie` | `BACKOFFICE_LECTURE` | `Hierarchie` | 404 `COLLABORATEUR_INTROUVABLE` |
| A2 | `GET /api/referentiel/v1/unites/{id}/fiche` | `BACKOFFICE_LECTURE` | `FicheUnite` | 404 `UNITE_INTROUVABLE` |
| A3 | `GET /api/rh/v1/demandes-administratives/liste?collaborateur_id=&unite_id=` (+ filtres existants) | `BACKOFFICE_LECTURE` | liste existante | 400 UUID mal formé |
| A4 | `GET /api/rh/v1/demandes-documents-administratifs/liste?collaborateur_id=&statut=` (**nouveau**, tous statuts, la file d'attente reste à part) | `BACKOFFICE_LECTURE` | `DemandeDocumentAdministratifRhResponse[]` | 400 |
| A5 | `GET /api/rh/v1/demandes-formations/liste?collaborateur_id=` (demandeur **ou** cible) | `BACKOFFICE_LECTURE` | liste existante | 400 |
| A6 | `GET /api/rh/v1/plaintes/liste?collaborateur_id=` (auteur) | `PLAINTES_LECTURE` (RH, ADMIN ; **pas** DIRECTION) | liste existante | 400, 403 DIRECTION |
| A7 | `GET /api/referentiel/v1/collaborateurs/moi/hierarchie` | `isAuthenticated()` | `Hierarchie` de l'appelant (JWT) | 403 compte sans fiche |
| A8 | `GET /api/referentiel/v1/annuaire/{collaborateurId}` | `hasRole('USER')` | `FicheAnnuaire` | 404 inconnu **ou inactif** |
| A9 | `GET /api/referentiel/v1/collaborateurs/moi/equipe` | `hasRole('USER')` | `PersonneResume[]` (collaborateurs actifs dont l'appelant est le valideur D-H03) | — |
| A10 | `GET /api/referentiel/v1/collaborateurs/moi/equipe/{collaborateurId}` | `hasRole('USER')` | `FicheEquipe` | 403 `HORS_PERIMETRE` (y compris inconnu) |
| A11 | `GET /internal/referentiel/collaborateurs/{id}/valideur?compte_id=` | jeton interne | § 2 | 401 |
| A12 | `GET /internal/referentiel/libelles-profil` | jeton interne | § 1 bis | 401 |
| A13 | `POST /internal/referentiel/collaborateurs/affectations` (existant, **enrichi**) | jeton interne | + `actif`, `poste_libelle`, `unite_id`, `unite_libelle` | 400 / 401 |

`unite_id` (A3) inclut les sous-unités. Filtres A3 à A6 en `snake_case` (convention de ces contrôleurs) : le
critère du ticket écrit `collaborateurId`, à lire `collaborateur_id`.

```json
// Hierarchie (A1, A7)
{
  "collaborateur": PersonneResume,
  "chemin": [UniteResume, "… de la DG à l'unité du collaborateur"],
  "responsable_direct": PersonneResume | null,      // manager du nœud du collaborateur (hors lui-même)
  "valideur": PersonneResume | null,                 // D-H03 ; null = demandes à la RH
  "valideur_est_rh": false,
  "noeuds_manages": [UniteResume],
  "collaborateurs_encadres": [PersonneResume]        // = périmètre D-H03 (même définition que A9)
}
// FicheUnite (A2)
{
  "unite": UniteResume, "code": "DTECH-SW", "actif": true, "a_rattacher": false,
  "chemin": [UniteResume], "parent": UniteResume | null,
  "responsable": PersonneResume | null,              // null → « Sans responsable — demandes à la RH »
  "sous_unites": [{ "unite": UniteResume, "effectif_total": 7 }],
  "membres": [PersonneResume],                       // rattachés directement, actifs
  "effectif_direct": 3, "effectif_total": 10
}
// FicheAnnuaire (A8) — rien d'autre (D-N02) : ni matricule, ni statut de compte, ni profil, ni date de recrutement
{
  "collaborateur_id", "nom_complet", "poste_libelle", "intitule_role",
  "unite_id", "unite_libelle",
  "responsable": { "collaborateur_id", "nom_complet" } | null,
  "courriel_professionnel"
}
// FicheEquipe (A10)
{
  "annuaire": FicheAnnuaire,
  "demandes": [{ "identifiant", "numero", "type_demande", "type_libelle", "statut", "statut_libelle",
                 "periode_debut", "periode_fin", "cree_le" }]
}
```

Règle A10 : périmètre = l'appelant est **aujourd'hui** le valideur D-H03 du collaborateur ; demandes visibles =
celles dont `valideur_attendu_identifiant` est la fiche de l'appelant **ou** dont l'historique contient une action
`VALIDATION_RO` / `REFUS_RO` de l'appelant (comparer à sa fiche et à son compte, l'`acteur_identifiant` historique
pouvant porter l'un ou l'autre).

#### 5.2 Ticket 2B — `svc-evaluation` (`camelCase`)

| # | Méthode et chemin | `@PreAuthorize` | Réponse 200 | Erreurs |
|---|---|---|---|---|
| B1 | `GET /api/rh/v1/admin/evaluations/campaigns/{id}` | `BACKOFFICE_LECTURE` | `CampagneDetail` | 404 `CAMPAGNE_INTROUVABLE` |
| B2 | `GET /api/rh/v1/admin/evaluations/campaigns/{id}/evaluations?statut=&page=0&taille=50` | `BACKOFFICE_LECTURE` | `Page<EvaluationLigne>` | 404, 400 |
| B3 | `GET /api/rh/v1/admin/evaluations/{id}/detail` | `BACKOFFICE_LECTURE` | `EvaluationDetail` | 404 `EVALUATION_INTROUVABLE` |
| B4 | `GET /api/rh/v1/admin/evaluations?campagneId=&collaborateurId=&superieurId=&profilIncomplet=` (existant, **enrichi**) | `BACKOFFICE_LECTURE` | `EvaluationLigne[]` | 400 |
| B5 | `GET /api/rh/v1/admin/evaluations/collaborateur/{collaborateurId}` (existant, **enrichi**) | `BACKOFFICE_LECTURE` | `EvaluationLigne[]` | — |
| B6 | `GET /api/rh/v1/admin/evaluations/alertes-ecart?collaborateurId=` (+ filtres existants) | `BACKOFFICE_LECTURE` | page existante | 400 |
| B7 | `GET /api/rh/v1/mobile/evaluations/{id}/detail` | `MOBILE_USER` + participant | `EvaluationDetailMobile` | 403 non participant, 404 |
| B8 | `GET /api/rh/v1/mobile/evaluations/{id}/reponses` (existant, **corrigé**) | `MOBILE_USER` + participant | `ReponseMobile[]` (DTO, plus d'entité) masqué selon D-E15 | 403 |

```json
// EvaluationLigne (B2, B4, B5)
{ "identifiant", "campagneId", "campagneNom",
  "collaborateurId", "collaborateurNom", "collaborateurMatricule",
  "superieurId", "superieurNom", "superieurMatricule",
  "statut", "etapeActuelle", "scoreSur20", "couleurAlerte",      // D-E12.b, null si le responsable n'a pas noté
  "aNoteResponsable", "niveauAlerte": "URGENTE" | null, "alerteId": "<uuid>" | null,
  "familleMetierCode", "familleMetierLibelle", "niveauSeniorite", "niveauSenioriteLibelle",
  "profilMetierIncomplet", "motifProfilIncomplet", "creeLe" }
// CampagneDetail (B1)
{ "identifiant", "nom", "description", "type", "statut", "annee", "dateDebut", "dateFin", "dureeJours",
  "templateGeneral": { "identifiant", "nom" } | null, "templateTechnique": { "identifiant", "nom" } | null,
  "compteurs": { "total", "enCours", "validees", "tauxCompletion", "sansQuestionnaireTechnique" },
  "alertesOuvertesParNiveau": { "ORANGE": 0, "ROUGE": 0, "URGENTE": 1, "total": 1 } }
// EvaluationDetail (B3)
{ "entete": EvaluationLigne + { "collaborateurPoste", "validationCollaborateurLe", "validationSuperieurLe" },
  "questionsGenerales": [Question], "competencesTechniques": [Question],
  "scores": { "moyenneCollaborateur", "moyenneResponsable", "scoreSur20", "appreciation" } | null,   // D-E14
  "alerte": { "identifiant", "niveau", "statut" } | null,
  "historique": [{ "evenement": "CREEE|VALIDEE_COLLABORATEUR|VALIDEE_RESPONSABLE", "le", "acteurNom" }] }
// Question
{ "questionId", "libelle", "section", "type",
  "reponseCollaborateur", "noteCollaborateur", "reponseResponsable", "noteResponsable", "commentaireResponsable",
  "ecart": 4 | null, "niveauEcart": "URGENTE" | null }          // écart et niveau : compétences techniques seulement
```

- Historique (B3) **dérivé** de `cree_le`, `validation_collaborateur_le`, `validation_superieur_le`,
  `validation_superieur_par` : pas de table d'audit nouvelle (hors périmètre).
- Compteurs B1 = même calcul que Suivi & scores filtré sur la campagne (une seule méthode de service, réutilisée).
- **D-E15 (B7, B8, et `/{id}/analytics` mobile)** : si l'appelant est le **collaborateur** et que
  `statut != VALIDEE`, les champs `reponseResponsable`, `noteResponsable`, `commentaireResponsable`, `scores`,
  `moyenneResponsable` sont **absents** du JSON (`@JsonInclude(NON_NULL)` sur un DTO dédié, pas de zéro). Le
  responsable voit les deux séries. **Aucun champ `alerte*` ni `niveauEcart`** dans les réponses mobiles, quel que
  soit l'appelant (D-E09). Le rôle (collaborateur / responsable) se déduit de la fiche de l'appelant résolue depuis le
  JWT, pas d'un en-tête.

#### 5.3 Ticket 2C — `svc-presence` (`snake_case`, paramètres de requête en `camelCase` comme `/pointages`)

| # | Méthode et chemin | `@PreAuthorize` | Réponse 200 | Erreurs |
|---|---|---|---|---|
| C1 | `GET /api/rh/v1/admin/presence/temps-travail` et `/export` (existants) | `BACKOFFICE_LECTURE` | ligne + `retard_minutes` ; CSV + colonnes « Retard (minutes) », « Retard (hh:mm) » (vides si `null`) | inchangées |
| C2 | `GET /api/rh/v1/admin/presence/collaborateurs/{collaborateurId}/synthese?du=&au=` | `BACKOFFICE_LECTURE` | `SynthesePresence` | 400 `PERIODE_TROP_LONGUE` (> 31 j), 400 `PERIODE_INVALIDE` |
| C3 | `GET /api/rh/v1/admin/presence/demandes-manuelles?statut=&collaborateurId=&siteId=&du=&au=` (existant, **filtres ajoutés**) | `BACKOFFICE_LECTURE` | `DemandeManuelleResponse[]` | 400 |
| C4 | `GET /api/rh/v1/admin/presence/demandes-manuelles/{id}` | `BACKOFFICE_LECTURE` | `DemandeManuelleDetail` | 404 `DEMANDE_INTROUVABLE` |
| C5 | `GET /api/rh/v1/mobile/presence/demandes-manuelles/{id}` | `MOBILE_USER` ; demandeur ou valideur (JWT) | `DemandeManuelleDetail` | 403 `HORS_PERIMETRE` (inconnu compris) |
| C6 | `GET /api/rh/v1/mobile/presence/pointages/me/journees?du=&au=` | `MOBILE_USER` ; identité JWT | `JourneePresence[]` (plus récent d'abord) | 400 `PERIODE_TROP_LONGUE`, 403 `IDENTITE_INCOHERENTE` |
| C7 | `GET /api/rh/v1/mobile/presence/equipe/{collaborateurId}?du=&au=` | `MOBILE_USER` ; valideur D-H03 (§ 2) | `PresenceEquipe` | 400, 403 `HORS_PERIMETRE`, 503 `REFERENTIEL_INDISPONIBLE` |
| C8 | `GET /api/rh/v1/admin/presence/heures-sup/demandes?collaborateur_id=&site_id=` et `/comptees` (existants) | `BACKOFFICE_LECTURE` | inchangé | vérifier seulement |

Période par défaut : 31 derniers jours (aujourd'hui inclus, Africa/Tunis) ; 31 jours maximum (D-P17.g). Ordre des
contrôles : 400 de format → 403 de périmètre → données. Les filtres `collaborateurId` acceptent la fiche ; le service
cherche les pointages sous la fiche **et** sous le compte (lignes anciennes, ADR-M09-HORAIRES § Contexte), le compte
étant obtenu par le résolveur § 1.

```json
// SynthesePresence (C2) et bloc "synthese" de C7
{ "collaborateur_id", "du", "au",
  "journees_pointees": 18, "temps_total_minutes": 9012,
  "retards_nombre": 3, "retards_cumul_minutes": 41,
  "journees_incompletes": 1, "journees_en_cours": 0,
  "pointages_manuels_valides": 1, "pointages_manuels_en_attente": 0,
  "heures_sup_minutes": 120 }
// JourneePresence (C6, C7)
{ "journee": "2026-11-05", "site_id", "site_libelle",
  "temps_minutes": 482, "retard_minutes": 12 | 0 | null, "etat": "COMPLET|EN_COURS|INCOMPLET",
  "pointages": [{ "id", "type", "statut", "server_ts", "manuel": false,
                  "motif_rejet": "Hors zone de pointage (179,8 m pour une zone de 50 m)" | null,
                  "eligible_pointage_manuel": true }] }
// PresenceEquipe (C7)
{ "collaborateur": { "collaborateur_id", "nom_complet" },
  "synthese": SynthesePresence, "journees": [JourneePresence],
  "pointages_manuels": [DemandeManuelleResponse] }
// DemandeManuelleDetail (C4, C5) = DemandeManuelleResponse + 
{ "site_libelle", "unite_libelle", "valideur": { "collaborateur_id", "nom_complet" } | null,
  "pointage_rejete": { "statut", "motif_rejet", "distance_metres", "server_ts" } | null,
  "manuels_du_mois": 2, "seuil_mois": 3 }
```

- `eligible_pointage_manuel` = refus éligible D-P04 / D-P12.a (faux pour `REJETE_HORS_HORAIRE`), calculé par le
  serveur : le mobile ne recode pas la règle.
- `motif_rejet` : texte déjà en clair stocké ; le libellé du **statut** vient de la table de libellés (lot 1, puis
  lot 3).
- C7 n'expose ni évaluation, ni document, ni plainte, ni formation (D-N02).
- Compteur « À valider » (2E) : **aucun endpoint nouveau** ; le mobile additionne
  `GET /api/rh/v1/demandes-administratives/en-attente-ro` + `GET /api/rh/v1/mobile/presence/demandes-manuelles/a-valider`
  + `GET /api/rh/v1/mobile/presence/heures-sup/demandes/moi?statut=EN_ATTENTE` (ce dernier pour `RO`,
  `RESPONSABLE`, `RH`, `ADMIN` seulement ; un 403 compte pour 0).

### 6. Recherche globale (P2, 2D)

Deux endpoints, interrogés en parallèle par le web, 8 résultats au total :
`GET /api/referentiel/v1/recherche?q=` (`BACKOFFICE_LECTURE`, ≥ 2 caractères, personnes par nom / matricule, unités,
numéros `DEM-`, `DOC-`, `PLT-`, `FOR-`) et `GET /api/rh/v1/admin/presence/recherche?q=` (sites, numéros `HS-`,
`PM-`). Réponse : `[{ "type": "COLLABORATEUR|UNITE|SITE|DEMANDE|DOCUMENT|PLAINTE|FORMATION|HEURES_SUP|POINTAGE_MANUEL",
"id", "libelle", "sous_libelle" }]`, 8 maximum par service. Recherche sans accents ni casse ; aucune donnée au-delà du
libellé.

### 7. Notifications conservées, lu / non lu (nouveau ticket « 2G », décision utilisateur du 06/11/2026)

**Propriétaire : `svc-notification`.** C'est lui qui reçoit `rh.notifications`, résout le destinataire `RH` en comptes
et pousse sur STOMP : il persiste **avant** de pousser. Aucun producteur ne change (hors champs du § 4).

**Base** : nouvelle base `notification_db` (PostgreSQL, `ddl-auto=update` comme les autres services). Ajouts au
`pom.xml` : `spring-boot-starter-data-jpa`, `postgresql`, `spring-boot-starter-security`,
`spring-boot-starter-oauth2-resource-server`. Déploiement : `CREATE DATABASE notification_db;` dans
`deploy/postgres/init/01-bases.sql` (n'agit qu'à la création du volume : sur un serveur déjà installé, la commande est
à passer à la main — à écrire dans `GUIDE_DEPLOIEMENT.md`), `SPRING_DATASOURCE_*` (mot de passe en variable
d'environnement) dans `docker-compose.yml`, base ajoutée aux scripts `exporter-donnees-locales.sh` /
`importer-donnees.sh`. En local : base créée sur le PostgreSQL 5444.

**Table `notification`** (une ligne par compte destinataire ; un envoi `RH` = une ligne par compte RH) :

| Colonne | Type | Remarque |
|---|---|---|
| `id` | uuid PK | renvoyé aux clients (marquer lu) |
| `notification_id` | uuid, non nul | id du producteur (§ 4) ; généré par `svc-notification` s'il manque (ancien format) |
| `compte_id` | uuid, non nul | destinataire (compte, SCRUM-27) |
| `sujet` | varchar(200) | |
| `contenu` | varchar(2000) | texte sans préfixe |
| `evenement`, `objet_type` | varchar(60), varchar(30) | texte libre, **pas d'enum ni de CHECK** (liste extensible sans runner) |
| `objet_id` | uuid | nullable |
| `objet_numero` | varchar(24) | nullable |
| `emis_le`, `recu_le` | timestamptz | |
| `lu_le` | timestamptz | null = non lue |

Contraintes : **unique (`notification_id`, `compte_id`)** = idempotence Kafka (relecture d'un message : insertion
ignorée, **aucun nouvel envoi STOMP**) ; index (`compte_id`, `lu_le`, `recu_le` décroissant).

**Rétention : 6 mois** (CPD, à confirmer client ; pas d'obligation légale sur une notification, l'objet métier garde
son propre historique). Purge quotidienne `@Scheduled` (`notification.retention-jours=180`) par lots de 1 000.

**Sécurité de `svc-notification` (prérequis, dans ce ticket).** `StompAuthInterceptor` lit l'identité dans le JWT
**sans vérifier la signature** (commentaire « In production, you should validate the JWT signature here ») : un jeton
forgé permet aujourd'hui de s'abonner à la file d'un autre compte. Avec des notifications lisibles par API, c'est
bloquant. Décision : un `JwtDecoder` sur le JWKS de `svc-identite-acces` (même configuration que les autres
services), utilisé par le resource server **et** par l'intercepteur STOMP (`CONNECT` refusé si jeton absent, invalide
ou expiré). `NotificationTestController` est supprimé (dette connue).

**Endpoints** (identité = claim `identifiant_utilisateur` du JWT ; jamais de compte en paramètre) :

| Méthode et chemin | `@PreAuthorize` | Réponse | Erreurs |
|---|---|---|---|
| `GET /api/rh/v1/notifications?lu=false&page=0&taille=20` (`lu` facultatif ; `taille` ≤ 100 ; tri `recu_le` décroissant) | `isAuthenticated()` | `{ "contenu": [NotificationDto], "page", "taille", "total" }` | 400 |
| `GET /api/rh/v1/notifications/compteur` | `isAuthenticated()` | `{ "non_lues": 3 }` | — |
| `POST /api/rh/v1/notifications/{id}/lue` | `isAuthenticated()` | 204 (idempotent) | 404 si inconnue **ou d'un autre compte** |
| `POST /api/rh/v1/notifications/lues` (toutes) | `isAuthenticated()` | 204 | — |

`NotificationDto` (snake_case) = `id`, `sujet`, `contenu`, `evenement`, `objet_type`, `objet_id`, `objet_numero`,
`emis_le`, `recu_le`, `lu` (booléen). **Le message STOMP poussé a la même forme** : un seul modèle côté client pour la
liste et le temps réel. Pas de suppression par l'utilisateur dans ce lot.

**Gateway** : nouvelle route `svc-notification-api`, `Path=/api/rh/v1/notifications/**`, `uri: lb://svc-notification`,
**placée avant** la route générique `svc-referentiel-rh-v1` (`/api/rh/v1/**`), sinon le référentiel la capte.

**Couplage avec 2F** : ouvrir une notification = `POST …/{id}/lue` puis navigation par `objet_type` / `objet_id`
(table § 4). Badge = `/compteur` au démarrage puis +1 par message STOMP. Web : cloche avec liste et page
`/app/notifications` ; mobile : écran `/notifications` existant branché sur l'API (ferme M-91).

### 8. Confirmation avant toute action à effet (décision utilisateur, front seulement)

Web et mobile demandent une confirmation explicite avant toute action à effet : archiver, clôturer, désactiver,
supprimer, refuser, annuler, valider une décision. Boîte de dialogue avec le nom de l'objet (numéro lisible ou
libellé), un bouton d'action nommé (« Clôturer l'alerte », jamais « OK ») et « Annuler » par défaut. Aucune
modification serveur : les contrôles et codes d'erreur existants restent la garantie. Un composant par canal
(`ConfirmationDialog` web, `confirmerAction()` mobile), réutilisé par les lots 2 et 3.

## Alternatives écartées

- **Le web appelle le référentiel pour chaque nom** : N+1 côté navigateur, viole D-L01.a, rien n'est résolu dans les
  exports.
- **Réplication des noms par Kafka** (table de noms dans chaque service, alimentée par événement) : plus juste en
  disponibilité, mais nouveau topic, nouvelle table, rattrapage initial ; disproportionné pour ~100 fiches.
- **BFF / agrégateur pour la fiche 360°** : un service de plus ; le web compose déjà plusieurs appels.
- **Reconstruction de l'horaire depuis l'historique JSON** : inutile, l'instantané existe sur chaque pointage.
- **Stockage du retard** : à recalculer à chaque correction manuelle ; contraire à D-P19.c.
- **Endpoint « compteur à valider » agrégé** : appels inter-services de plus pour un simple total.
- **Champ `metadata` libre dans la notification** : contrat flou ; on fixe des champs nommés.
- **Notifications conservées par chaque producteur** (référentiel, évaluation, présence) : trois tables, trois API à
  agréger côté client ; `svc-notification` voit déjà tous les messages.
- **Redis pour les notifications** : pas de requêtes paginées ni de rétention fiable ; PostgreSQL est déjà là.

## Conséquences

- Une seule table nouvelle (`notification`, base `notification_db`, § 7). Champs ajoutés aux réponses (compatibles),
  endpoints 2A–2C sous des préfixes déjà routés ; **une route gateway** ajoutée (`/api/rh/v1/notifications/**`).
- `svc-evaluation` et `svc-presence` gagnent `spring-boot-starter-cache` + `caffeine` (déjà dans le référentiel).
- Tous les appels vers le référentiel ont un délai d'attente ; les listes se dégradent, les décisions échouent fermées.
- La fuite D-E15 de `/{id}/reponses` est fermée (changement du JSON : DTO au lieu de l'entité, champs `camelCase`
  identiques ; le mobile vérifie son modèle).
- Le préfixe des notifications disparaît : web et mobile gardent un repli une version.
- Dettes connues conservées : `perimetreDe` parcourt toutes les fiches actives (acceptable à ~100 fiches) ;
  pagination en mémoire du temps de travail (974f2db) utilisée par C2 ; noms en cache jusqu'à 5 min après un
  renommage de fiche.
- `svc-notification` devient un service avec base (`notification_db`), sécurité JWT vérifiée et une route gateway :
  dossier supplémentaire du lot (ticket 2G), un seul développeur backend dessus.
- Nouvelle base à créer à la main sur un serveur déjà déployé (volume PostgreSQL existant).

## Sécurité / RGPD (STRIDE léger)

| Menace | Point d'entrée | Parade |
|---|---|---|
| **Usurpation** d'identité pour voir l'équipe d'un autre | C7, A9, A10, C5 | Identité = JWT (claim `identifiant_utilisateur`) ; `X-Collaborateur-Id` ignoré pour décider, contrôlé pour cohérence (403 `IDENTITE_INCOHERENTE`). Périmètre D-H03 calculé au référentiel. |
| **Usurpation** d'un service | A11, A12, A13 | `/internal/**` non routé ; `X-Internal-Token` comparé à temps constant ; jeton en variable d'environnement (`INTERNAL_API_TOKEN`). Dette : la valeur par défaut `change-me-internal-token` des `@Value` doit disparaître (démarrage refusé sans variable) — à traiter dans ce lot pour les deux nouveaux endpoints au moins. |
| **Divulgation** (IDOR) | A8, A10, B7, B8, C5, C7 | 403 `HORS_PERIMETRE` sans distinguer inconnu / interdit ; annuaire limité à 7 champs, fiches inactives en 404 ; D-E15 masque les notes du responsable avant `VALIDEE` ; aucune alerte d'écart côté mobile. |
| **Divulgation** de données personnelles à la DIRECTION | fiches web | Décision utilisateur du 06/11 : la DIRECTION voit la fiche 360° **sans les plaintes** (`PLAINTES_LECTURE` = RH, ADMIN ; 403 sur liste et détail) ; courriels visibles. |
| **Usurpation** sur le WebSocket | `/ws` | Signature JWT vérifiée au `CONNECT` STOMP (aujourd'hui non vérifiée : jeton forgé = abonnement à la file d'autrui). |
| **Divulgation** des notifications d'autrui | API § 7 | Compte lu dans le JWT ; `{id}` d'un autre compte → 404 ; rétention 6 mois. |
| **Rejeu** Kafka | consommateur | Unique (`notification_id`, `compte_id`) : ni doublon en base, ni second envoi. |
| **Altération** | — | Aucun endpoint d'écriture nouveau ; les actions des fiches réutilisent les endpoints existants et leurs contrôles. |
| **Déni de service** | C2, C6, C7, résolveur | Période ≤ 31 j ; 100 identifiants max par appel interne ; délais d'attente 2 s / 3 s ; cache 5 min. |
| **Répudiation** | — | Lecture seule ; les décisions restent tracées par les historiques existants. |
| **Fuite par notification** | 2F | `objet_id` n'ouvre rien sans droit ; aucun texte nouveau ; destinataire = compte (SCRUM-27). |

## Plan d'implémentation

Prérequis : **lot 1 fusionné** (numéros D-L02, `intitule_role` D-H06, libellés), car les mêmes fichiers sont touchés.
Ordre : 2A, 2B, 2C, 2G (backend, quatre dossiers disjoints) en parallèle → 2D et 2E en parallèle (démarrent tout de
suite sur les contrats ci-dessus, avec des données simulées) → 2F (producteurs puis clients ; dépend de 2G pour le
« marquer lu »). Le lot gateway (une route) part avec 2G.

### Lot backend 2A — `svc-referentiel-rh/` (dev-backend)
- Fichiers : `web/ReferentielController.java` (A1, A7), nouveau `web/AnnuaireController.java` (A8, A9, A10),
  `web/ReferentielController.java` ou `OrganigrammeController.java` (A2), contrôleurs Demande*/Plainte (A3 à A6),
  `web/InterneReferentielController.java` (A11, A12, A13), nouveaux `service/HierarchieService.java`,
  `service/AnnuaireService.java`, DTO `dto/HierarchieResponse`, `FicheUniteResponse`, `FicheAnnuaireResponse`,
  `FicheEquipeResponse`, `PersonneResume`, `UniteResume`, `ValideurControleResponse`, `RechercheResultat`
  (+ `web/RechercheController.java`, P2).
- Tests : critères 2A (COL-WEB → chemin et valideur Omar Belhadj ; CS-WEB → valideur Youssef Chaabane, encadrés
  COL-WEB et COL-WEB2 sans COL-MOB ; filtre mal formé 400, inconnu `[]` ; A10 200 / 403 ; A8 sans champ interdit
  — test qui sérialise et vérifie l'ensemble exact des clés ; DG01 lecture 200, écriture 403 ; DG01 sur
  `/plaintes/liste` et `/plaintes/{id}` : 403 ; DRH01 et ADM01 : 200 ; A11 sans jeton 401).
- Fichier en plus : `security/PreAuthorizeExpressions.java` (`PLAINTES_LECTURE`), `service/PlainteRhService.java`
  (refus DIRECTION non auteur sur le détail et le suivi).

### Lot backend 2B — `svc-evaluation/` (dev-backend)
- Fichiers : `web/EvaluationAdminController.java` (B1 à B5), `web/AlerteEcartController.java` +
  `service/AlerteEcartTraitementService.java` (B6), `web/EvaluationMobileController.java` (B7, B8, analytics
  mobile), nouveaux `service/NomsCollaborateurs.java`, `service/EvaluationDetailService.java`,
  `client/RestReferentielCollaborateurClient.java` (affectations, libellés profil, délais), DTO
  `dto/EvaluationLigne`, `CampagneDetail`, `EvaluationDetail`, `EvaluationDetailMobile`, `ReponseMobile`,
  `pom.xml` (cache + caffeine), `application.properties` (délais).
- Tests : critères 2B (36 évaluations, compteurs = Suivi & scores ; 5 contre 1 « Urgente » ; DG01 200 ; COL-WEB sur
  B3 403 ; D-E15 : clés absentes avant `VALIDEE`, présentes après ; aucune clé commençant par `alerte` ni
  `niveauEcart` côté mobile ; 404 campagne) ; résolveur : un seul appel pour 50 lignes, repli instantané si 503.

### Lot backend 2C — `svc-presence/` (dev-backend)
- Fichiers : nouveau `service/RetardCalcul.java` (pur), `service/TempsTravailCalcul.java`,
  `service/TempsTravailService.java`, `service/TempsTravailCsvService.java`, `dto/TempsTravailDtos.java`
  (`retard_minutes`), nouveau `web/PresenceCollaborateurAdminController.java` (C2) et
  `service/SynthesePresenceService.java`, `web/PresenceDemandeManuelleController.java` +
  `service/PresenceDemandeManuelleService.java` (C3, C4, C5), `web/PresenceMobileController.java` (C6), nouveau
  `web/PresenceEquipeMobileController.java` (C7), `client/ReferentielClient.java` (A11, délais, résolveur
  `NomsCollaborateurs`), `config/PresenceProperties.java` (`retard.delai-grace-minutes`, délais), `pom.xml`.
- Tests : `RetardCalculTest` avec `Clock` (08:12:40 → 12 ; 08:00:59 → 0 ; 07:50 → 0 ; sans horaire → null ; SORTIE
  première → null ; manuel déclaré 08:30 → 30 ; instantané 08:00 conservé après changement d'horaire → 30) ; CSV
  colonne présente ; C7 : 200 valideur, 403 hors périmètre, 400 sur 32 jours, 503 référentiel coupé (aucune donnée) ;
  C6 : 403 `IDENTITE_INCOHERENTE` ; DG01 lecture 200.

### Lot web 2D — `rh-admin-web/` (dev-web)
- Démarre sur les contrats § 5 ; pages P1 (collaborateur 360°, unité, site, campagne, évaluation, demande M01, liens
  de l'alerte) puis P2 (document, plainte, formation, heures sup, pointage manuel, recherche).
- Onglet Plaintes de la fiche 360° masqué pour la DIRECTION ; confirmation (§ 8) avant toute action à effet.
- Un composant `LienEntite` (type + id + libellé) pour **tous** les noms ; une table de routes unique (réutilisée par
  2F) ; pages « Introuvable » (404) et « Accès refusé » (403) ; `/app/unites` → organigramme.
- Fiche 360° : appels parallèles A1, A3–A6, B5, B6, C2, C1 (`collaborateurId`), C3 ; chaque onglet a ses états
  vide / erreur / chargement. DIRECTION : aucun bouton d'action.
- Vérif : `npx tsc --noEmit -p . && npx vite build`.
- **Décision utilisateur du 07/11/2026** (D-N01.b, D-N03, fiche lot 2 § 2 bis, § 2 ter, critères 2D-M1 à 2D-M14 et
  2D-N1 à 2D-N9) : `LienEntite` ouvre d'abord une **modale de détail à onglets** (pile interne avec « Retour »,
  Échap, focus piégé et rendu), qui réutilise les mêmes appels que la page et propose « Ouvrir la fiche complète » ;
  le lien garde un `href` réel vers la page. Le menu de `AppShell` devient un menu à groupes repliables (Pointage,
  Collaborateurs, Paramétrage) dont les sous-entrées Pointage visent les onglets existants (`?onglet=`). Aucun
  changement d'API ni de sécurité ; la redirection `/app/unites` → organigramme est suspendue (Q15).

### Lot mobile 2E — `rh_mobile_app/` (dev-mobile)
- Écrans : Mon dossier (A7 + `/collaborateurs/moi`), Mes pointages (C6), fiche annuaire (A8) depuis l'organigramme,
  fiche d'équipe (A10 + C7 ; si C7 répond 403, afficher la fiche annuaire seule), Mon équipe (A9, P2), hub
  À valider (§ 5.3), détails demande / évaluation (B7, D-E15) ; nouvelles routes `/pointage/manuels/:id`,
  `/heures-sup/demandes/:id`, `/formations/:id`, `/annuaire/:id`, `/equipe/:id`, `/dossier`, `/a-valider`.
- Un 403 n'affiche pas « Réessayer » (T-04). Confirmation (§ 8) avant annuler, refuser, valider. Flutter ≤ 3.29.2 (pas d'`initialValue`).
- Vérif : `flutter analyze && flutter test` (sur Codemagic si Flutter absent du poste).

### Lot 2F — producteurs puis clients
1. Backend (dev-backend, après 2A–2C) : `svc-notification/.../dto/NotificationMessage.java` (6 champs) ;
   `NotificationMessage` + publieurs de `svc-referentiel-rh`, `svc-evaluation`, `svc-presence` ; tests : JSON émis
   contient `objet_type` / `objet_id` et un `content` sans `|`.
2. Web (dev-web) : `api/notificationContenu.ts` + `layout/NotificationBell.tsx` → route par `objet_type`, repli
   préfixe.
3. Mobile (dev-mobile) : `core/notifications/notification_model.dart` (champs, repli), navigation au toucher,
   « Élément introuvable » sur 403 / 404.

### Lot backend 2G — `svc-notification/` + `gateway/` + `deploy/` (dev-backend)
- `pom.xml` (JPA, PostgreSQL, sécurité, resource server), `application.properties` (datasource en variables
  d'environnement, JWKS, `notification.retention-jours=180`), nouveaux `entity/Notification.java`,
  `repository/NotificationRepository.java`, `service/NotificationStockageService.java`,
  `web/NotificationController.java`, `config/SecurityConfig.java` ; `config/StompAuthInterceptor.java` (signature
  vérifiée) ; `listener/NotificationListener.java` (persister puis pousser, idempotent) ; suppression de
  `controller/NotificationTestController.java`.
- `gateway/src/main/resources/application.yml` : route `/api/rh/v1/notifications/**` avant `svc-referentiel-rh-v1`.
- `deploy/postgres/init/01-bases.sql`, `deploy/docker-compose.yml`, scripts d'export / import,
  `deploy/GUIDE_DEPLOIEMENT.md` (création manuelle de la base sur un serveur existant).
- Tests : même message relu deux fois → une ligne, un seul envoi ; `RH` → une ligne par compte RH ; `{id}` d'un autre
  compte → 404 ; `CONNECT` STOMP avec jeton à signature fausse → refusé ; purge supprime au-delà de 180 jours ; route
  gateway atteinte (pas le référentiel).
- Clients (dans 2F) : web `layout/NotificationBell.tsx` + page `/app/notifications` ; mobile écran `/notifications`
  (liste paginée, lu / non lu, badge).

### Configuration
- `svc-evaluation`, `svc-presence` : `*.referentiel.timeout-connexion-ms=2000`, `*.referentiel.timeout-lecture-ms=3000`,
  `presence.retard.delai-grace-minutes=0`. Aucune variable d'environnement nouvelle ; `INTERNAL_API_TOKEN` existe
  déjà pour les trois services dans `deploy/docker-compose.yml`.
