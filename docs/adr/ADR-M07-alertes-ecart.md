# ADR-M07-ALERTES-ECART — Alertes d'écart d'étoiles collaborateur / RO et validation RO (D-E04 à D-E10)

| | |
|---|---|
| **Statut** | accepté et implémenté (04/11/2026, SCRUM-53 à SCRUM-56) |
| **Date** | 2026-11-04 |
| **Module** | M07 — Évaluations (vague alertes d'écart, **Cible**) |
| **Tickets** | SCRUM-53 (détection + notification), SCRUM-54 (liste + traitement RH), SCRUM-55 (écran Plateforme RH), SCRUM-56 (validation RO mobile + backend) |
| **Références** | `docs/sirh_basefonctionnelle.md` § 4.2, § 12.5 bis (D-E04 à D-E10), § 19 (C-M07-05, C-L-02), § 23 ; `docs/jira/fiche_M07_alertes_ecart.md` ; `docs/jira/relecture_testeur_M07_alertes_ecart.md` ; modèle de forme `ADR-M09-horaires-heures-sup.md` |

---

## Contexte

Le client veut qu'un écart d'étoiles ≥ 2 entre l'auto-évaluation et la note du RO sur une compétence technique crée
une alerte (orange 2, rouge 3, urgente 4) notifiée en temps réel aux RH, traitée par la RH (conclusion + commentaire),
lue par la DIRECTION. L'alerte naît à la validation de l'évaluation par le RO, qui n'existe pas encore côté API.

État vérifié dans le code au 04/11/2026 :

- **Source des étoiles** : les compétences techniques sont des `evaluation_question` d'un template `TECHNICAL` ; les
  réponses sont dans `evaluation_answer`. `repondreQuestionCollaborateur` écrit **la même valeur** dans
  `note_attribuee` et `note_collaborateur` ; `repondreQuestionManager` écrit `note_manager`. Le scoring
  (`EvaluationScoringService.analyser`) et le mobile lisent déjà `COALESCE(note_collaborateur, note_attribuee)`.
  Base locale : 10 réponses TECHNICAL, `note_collaborateur` et `note_attribuee` renseignées toutes les deux, 1 `note_manager`.
  `skill_answer` / `technical_question` : 0 ligne, hors source (dette § 23).
- **Template technique d'une évaluation** : `EvaluationMobileController.getTechnicalQuestions` résout
  `templateCompetenceAssigne`, sinon `campaign.templateCompetence`, sinon `resoudreTemplateCompetence(...)` (arguments
  inversés, voie héritée), sinon `TechnicalTemplate` (voie `skill_answer`). Cette résolution n'existe qu'en contrôleur.
- **Validation RO** : `EvaluationWorkflowService.validerParManager` (L343) n'a ni garde ni appelant ; elle recalcule le
  score. Seul `POST /{id}/validate/collaborator` existe. `actualiserStatut` pose `VALIDEE` si les deux dates existent.
- **Identité** : `EvaluationAccessService.actorMatches` accepte l'en-tête `X-Collaborateur-Id` **ou** le JWT. Le mobile
  reçoit `superieurIdentifiant` (fiche du RO) dans `EvaluationItemResponse` : un collaborateur peut donc envoyer
  `X-Collaborateur-Id = fiche du RO` et écrire les « notes RO » de sa propre évaluation (`requireManagerEvaluation`).
  Ce trou fausse directement l'alerte (voir Sécurité).
- Toutes les évaluations locales ont `collaborateur_compte_id` et `superieur_compte_id` (snapshot à l'activation) ;
  aucune n'a `validation_superieur_le`.
- **Erreurs** : `IllegalArgumentException` → 400 (même pour « introuvable ») ; `SecurityException` → 403 ;
  `EvaluationMetierException` porte code + statut (fabriques 400 / 409 / 422 seulement) ; aucun gestionnaire pour
  `MethodArgumentTypeMismatchException` (UUID ou enum de requête invalide → 500 aujourd'hui).
- **Kafka** : `EvaluationEventPublisher.notifier(destinataire, sujet, corps)` envoie un `NotificationMessage`
  `{type, recipient, subject, content}` en JSON texte sur `rh.notifications`, erreurs avalées. `svc-notification`
  désérialise ce DTO à 4 champs et le relaie tel quel sur `/user/{compte}/queue/notifications` : **tout champ en plus
  est perdu**. Convention déjà utilisée : `content = "TYPE|cle=valeur|...|texte"` (`EVALUATION_A_NOTER|evaluation=…|…`).
- **Cloche web** (`rh-admin-web/src/layout/NotificationBell.tsx`) : affiche `subject` et `content` bruts, le clic ne
  fait que marquer lu.
- **`/internal/users/by-role`** (svc-identite-acces) : `permitAll` dans la chaîne Spring Security, mais le contrôleur
  vérifie `X-Internal-Token` (403 sinon) ; comparaison `String.equals` (non constante), propriété
  `app.internal-api.token=change-me-internal-token` en dur (le kit la surcharge par `APP_INTERNALAPI_TOKEN`).
  Réponse : `identifiant` (compte), `nom_utilisateur`, `courriel`, `roles`. La gateway ne route pas `/internal/**`.
  Le référentiel fait déjà la comparaison à temps constant (`InterneReferentielController.jetonValide`).
- **Gateway** : la route `svc-evaluation` couvre `/api/rh/v1/admin/evaluations/**` et `/api/rh/v1/mobile/evaluations/**`
  et passe **avant** le fourre-tout référentiel `/api/rh/v1/**`.
- svc-evaluation n'a ni matricule ni unité du collaborateur (le snapshot d'activation ne les porte pas) ;
  `/internal/referentiel/**` existe (jeton, temps constant).

## Décision

### 1. Champ exact des notes (D-E04.a, décision B1 confirmée)

| Côté | Valeur lue | Justification |
|---|---|---|
| Collaborateur | `COALESCE(note_collaborateur, note_attribuee)` | Les deux sont écrites ensemble ; même règle que le scoring et le mobile. `note_attribuee` couvre d'anciennes lignes. |
| RO | `note_manager` | Seule colonne écrite par `repondreQuestionManager`. |

Une compétence dont l'une des deux valeurs est nulle est ignorée. Valeurs hors 1–5 : ignorées avec un log
d'avertissement (pas d'exception dans la validation).

**Questions techniques d'une évaluation** : nouveau composant `TemplateTechniqueEvaluation` (service, sans état) qui
reprend **exactement** la résolution du contrôleur pour la voie `evaluation_answer` : `templateCompetenceAssigne`,
sinon `campaign.templateCompetence`, sinon `resoudreTemplateCompetence` (même appel qu'aujourd'hui), et retient le
résultat seulement si `type = TECHNICAL`. Questions retenues : `findByTemplateIdAndActifTrueOrderByOrdreAsc`.
`getTechnicalQuestions` l'utilise aussi (une seule vérité pour le mobile, la complétude D-E10 et la détection).
Aucun template → « évaluation sans template technique » (validation permise, pas d'alerte).

### 2. Calcul (classe pure `EcartEtoiles`)

- `ecart = |ro − collaborateur|` (0 à 4).
- `NiveauEcart` (enum ordonné) : `ALIGNE` (0–1) < `ORANGE` (2) < `ROUGE` (3) < `URGENTE` (4). Constantes nommées
  `SEUIL_ORANGE = 2`, `SEUIL_ROUGE = 3`, `SEUIL_URGENTE = 4`.
- `SensEcart` : `RO_INFERIEUR` (ro < collab), `RO_SUPERIEUR` (ro > collab), `null` si égalité.
- Sens global d'une alerte (calculé, non stocké) sur les lignes dont `niveau_actuel ≥ ORANGE` : un seul sens →
  ce sens ; les deux → `DEUX_SENS` ; aucune → `null`.
- Libellés D-E05 (`libelleSens`, `aideSens`, « Écarts dans les deux sens ») produits **par le serveur** (une constante
  par sens dans `SensEcart`).
- `EcartEtoiles` sert aussi à D-E09.c (§ 8) : un seul calcul pour l'alerte et l'affichage.

### 3. Modèle de données (`evaluation_rh_db`, `ddl-auto=update`)

| Table | Colonnes | Contraintes / index |
|---|---|---|
| `evaluation` (existante) | + `validation_superieur_par` (`uuid`, **nullable**) = compte JWT du RO qui a validé par le nouvel endpoint | Sert d'**éligibilité** : seules les évaluations où cette colonne est posée sont détectées et recalculées (garantit « pas de calcul rétroactif », AC 17). |
| `evaluation_alerte_ecart` | `identifiant` (uuid PK) ; `evaluation_identifiant` (uuid, NOT NULL, FK `evaluation`) ; `campagne_identifiant` (uuid NOT NULL), `campagne_nom` ; `collaborateur_fiche_id` (NOT NULL), `collaborateur_compte_id`, `collaborateur_nom`, `collaborateur_matricule`, `collaborateur_unite_id`, `collaborateur_unite_libelle` ; `superieur_fiche_id` (NOT NULL), `superieur_compte_id`, `superieur_nom` ; `niveau_detection`, `niveau_courant`, `niveau_max_notifie` (`NiveauEcart`, varchar 20) ; `statut` (`StatutAlerteEcart`, NOT NULL) ; `conclusion` (`ConclusionAlerteEcart`, nullable) ; `notes_modifiees_apres_detection` (bool NOT NULL default false) ; `detectee_le`, `modifiee_le` (timestamptz) ; `version` (`@Version`) | **Unique `uk_alerte_ecart_evaluation` (`evaluation_identifiant`)** = une alerte par évaluation. Index `idx_alerte_ecart_statut_niveau` (`statut`, `niveau_courant`), `idx_alerte_ecart_campagne`, `idx_alerte_ecart_unite` (`collaborateur_unite_id`). |
| `evaluation_alerte_ecart_ligne` | `identifiant` ; `alerte_identifiant` (FK NOT NULL) ; `question_identifiant` (uuid NOT NULL) ; `competence_libelle` (text, snapshot) ; `ordre` ; `etoiles_collaborateur_detection`, `etoiles_ro_detection`, `ecart_detection`, `niveau_detection`, `sens_detection` ; `etoiles_collaborateur_actuelles`, `etoiles_ro_actuelles`, `ecart_actuel`, `niveau_actuel`, `sens_actuel` ; `detectee_le`, `modifiee_le` | Unique (`alerte_identifiant`, `question_identifiant`). Une ligne par compétence ayant atteint un écart ≥ 2 ; **jamais supprimée**. |
| `evaluation_alerte_ecart_historique` | `identifiant` ; `alerte_identifiant` (FK NOT NULL) ; `type_evenement` (`EvenementAlerteEcart`) ; `auteur_compte_id` (null = SYSTEME) ; `auteur_libelle` (« SYSTEME » ou nom de l'auteur) ; `cree_le` (timestamptz UTC) ; `statut_avant`, `statut_apres` ; `niveau_avant`, `niveau_apres` ; `conclusion` ; `commentaire` (varchar 1000) | Toutes colonnes `updatable = false` ; aucun endpoint de modification ni de suppression. Index (`alerte_identifiant`, `cree_le`). Conservé 5 ans (C-L-02 ; purge hors lot). |

Enums (package `domain`) :

| Enum | Valeurs | Colonnes |
|---|---|---|
| `NiveauEcart` | `ALIGNE, ORANGE, ROUGE, URGENTE` | alerte : `niveau_detection`, `niveau_courant`, `niveau_max_notifie` ; ligne : `niveau_detection`, `niveau_actuel` ; historique : `niveau_avant`, `niveau_apres` |
| `StatutAlerteEcart` | `OUVERTE, EN_COURS, TRAITEE` | alerte : `statut` ; historique : `statut_avant`, `statut_apres` |
| `SensEcart` | `RO_INFERIEUR, RO_SUPERIEUR` | ligne : `sens_detection`, `sens_actuel` (nullables) |
| `ConclusionAlerteEcart` | `MANQUE_EFFORT, SURESTIMATION_COLLABORATEUR, SUIVI_INSUFFISANT_RO, SOUS_ESTIMATION_COLLABORATEUR, NOTE_GONFLEE_RO, ECART_JUSTIFIE, AUTRE` (+ libellé D-E08.c) | alerte et historique : `conclusion` |
| `EvenementAlerteEcart` | `CREATION, RECALCUL, PRISE_EN_CHARGE, CLOTURE, REOUVERTURE_AUTOMATIQUE` | historique : `type_evenement` |

**Runner CHECK** : `AlerteEcartConstraintRunner` (modèle `CampagneTypeConstraintRunner`) parcourt une liste
(table, colonne, enum) et, pour chacune, `DROP CONSTRAINT IF EXISTS <table>_<colonne>_check` puis
`ADD CONSTRAINT <table>_<colonne>_check CHECK (<colonne> IN (...))` (NULL accepté par SQL). Idempotent, warn si échec.

`niveau_courant = ALIGNE` est possible après une correction des notes (AC 11) : l'alerte reste, triée après `ORANGE`.

### 4. Validation RO (D-E10) — `EvaluationValidationSuperieurService`

`validerParSuperieur(UUID evaluationId, IdentiteAppelant appelant)` remplace `validerParManager` (supprimée : aucun
appelant). Ordre strict, dans **une transaction** :

1. Évaluation chargée **avec verrou** `PESSIMISTIC_WRITE` (`findByIdForUpdate`, avec `JOIN FETCH` campagne et
   templates) ; absente → 404 `EVALUATION_INTROUVABLE`.
2. Appelant = RO (§ 6) ; sinon 403 `PAS_RO_EVALUATION` (RH et ADMIN compris).
3. `validation_superieur_le` déjà posée → 200, rien ne change (ni date, ni alerte, ni notification).
4. `validation_collaborateur_le` nulle → 409 `COLLABORATEUR_NON_VALIDE`.
5. Template technique présent et au moins une question active sans `note_manager` → 422 `COMPETENCES_NON_NOTEES`,
   `donnees.competencesManquantes = [{questionId, libelle}]` (ordre des questions).
6. `validation_superieur_le = now`, `validation_superieur_par = compte JWT`, `actualiserStatut` → `VALIDEE`.
   **Pas** d'appel à `recalculerScore` (score et couleur C-M07-04 inchangés, AC 20).
7. `AlerteEcartService.detecter(evaluation)` (§ 5), même transaction.
8. Aucune notification au collaborateur ni au RO.

`etatValidationSuperieur(evaluationId, appelant)` (lecture, mêmes étapes 1–2) renvoie l'état pour le mobile (§ 9).

### 5. Détection et recalcul — `AlerteEcartService`

**Détection** (à l'étape 7, ou au recalcul s'il n'existe pas encore d'alerte, Q-C) :
- Pour chaque question technique active ayant les deux notes : `ecart`. Lignes = celles avec `ecart ≥ 2`.
- Aucune ligne → rien. Sinon : alerte `OUVERTE`, `niveau_detection = niveau_courant = max(lignes)`,
  snapshot des participants (§ 7), historique `CREATION` (SYSTEME, `statut_apres = OUVERTE`, `niveau_apres`),
  `niveau_max_notifie = niveau_courant`, publication d'un `AlerteEcartANotifierEvent` (§ 7).
- Course possible (deux validations simultanées) : neutralisée par le verrou de l'étape 1 ; la contrainte unique reste
  le garde-fou (`DataIntegrityViolationException` → relire l'alerte existante, sans 500).

**Recalcul** (D-E06.c) — point d'accroche : fin de `repondreQuestionCollaborateur` **et** de `repondreQuestionManager`,
après `answerRepository.save` :
- Conditions : `evaluation.validation_superieur_par != null` **et** la question appartient au template technique (§ 1).
  Sinon rien (aucun impact sur les questions générales, ni avant la validation RO).
- Évaluation verrouillée (`findByIdForUpdate`) puis alerte verrouillée (`findByEvaluationIdForUpdate`) — ordre
  toujours évaluation → alerte (les actions RH ne verrouillent que l'alerte : pas de cycle).
- Pas d'alerte → détection ci-dessus (Q-C, AC 18).
- Alerte existante : pour chaque question technique ayant les deux notes, ligne existante → mise à jour des valeurs
  `*_actuel*` ; pas de ligne et `ecart ≥ 2` → nouvelle ligne (valeurs de détection = valeurs actuelles).
  Lignes jamais supprimées. Si une valeur actuelle diffère de la détection ou si une ligne est ajoutée →
  `notes_modifiees_apres_detection = true` (indicateur collant).
- `niveau_courant = max(niveau_actuel des lignes)` (`ALIGNE` si toutes ≤ 1). Hausse = nouveau > ancien.
  - Hausse et `TRAITEE` → `OUVERTE` + historique `REOUVERTURE_AUTOMATIQUE` (SYSTEME), `conclusion` remise à null
    (l'ancienne reste dans l'historique).
  - Hausse et `OUVERTE` / `EN_COURS` → statut inchangé (Q-E), historique `RECALCUL`.
  - Baisse → statut inchangé, historique `RECALCUL`. Maintien → aucun historique.
- Notification si `niveau_courant > niveau_max_notifie` (Q-D) **ou** réouverture automatique ; dans ce cas
  `niveau_max_notifie = max(niveau_max_notifie, niveau_courant)`.
  *Précision d'architecte* : une alerte rouverte est toujours renotifiée, même si son niveau ne dépasse pas le maximum
  déjà notifié (sinon une alerte `TRAITEE → OUVERTE` passerait inaperçue) ; l'anti-rafale Q-D reste vrai tant que la RH
  ne reclôt pas.

### 6. Identité de l'appelant (JWT uniquement)

Nouveau `IdentiteAppelantResolver` dans `security` :
- `compteJwt` = claim `identifiant_utilisateur` (repli `sub` si UUID), jamais `X-Collaborateur-Id`.
- `ficheJwt()` (paresseux) = `GET /api/referentiel/v1/collaborateurs/moi` avec le JWT relayé (client
  `ReferentielMoiClient`, délai 2 s) ; utilisé **seulement** quand un compte de comparaison manque (repli fiche).
- `estSuperieur(evaluation)` : `superieurCompteId != null` → `compteJwt.equals(superieurCompteId)` ; sinon
  `ficheJwt == superieurIdentifiant`. Même logique pour `estCollaborateur`.
- `libelle()` = `prenom nom` de `moi` si disponible, sinon claim `sub` (pour `auteur_libelle`).

**Correctif inclus (SCRUM-56, sécurité)** : `EvaluationAccessService.requireCollaborateurEvaluation`,
`requireManagerEvaluation` et `requireParticipantEvaluation` utilisent ce résolveur (JWT + snapshot de compte) ;
l'en-tête n'est plus une preuve. S'il est présent et différent de la fiche de l'acteur reconnu → 403
`IDENTITE_INCOHERENTE` (comme SCRUM-52). `requireCurrentActorId` (listes `/moi`, `/manager/pending`) garde l'en-tête
dans ce lot (lecture de listes, ticket de dette à ouvrir : résoudre la fiche par `moi`).

### 7. Notification (D-E07) — après commit

- Dans la transaction : `ApplicationEventPublisher.publishEvent(new AlerteEcartANotifierEvent(alerteId, niveau,
  collaborateurNom, campagneNom, nbCompetencesEnEcart, ecartMax, comptesExclus))` — l'événement porte tout le
  nécessaire (aucune relecture après commit).
- `AlerteEcartNotificationListener` : `@TransactionalEventListener(phase = AFTER_COMMIT)`. Rollback → rien n'est envoyé ;
  commit puis panne Kafka / identité → validation déjà 200, alerte en base, log `warn` (AC 19).
- **Destinataires** : `IdentiteClient.comptesParRole("RH")` → `GET {evaluation.identite.base-url}/internal/users/by-role?role=RH`
  (`X-Internal-Token`, délai connexion 1 s / lecture 2 s), moins `comptesExclus` = { `collaborateur_compte_id`,
  `superieur_compte_id` de l'alerte } (comptes résolus au snapshot, repli : `compte_utilisateur_id` lu au référentiel
  pour la fiche). Liste vide → même appel avec `role=ADMIN`, moins les mêmes exclusions. Toujours vide → log `warn`
  « alerte {id} sans destinataire ». Dédoublonnage.
- **Un** `NotificationMessage` par compte, **jamais** le destinataire littéral `RH`, type `WEBSOCKET` (aucun e-mail) :
  - `recipient` = compte (UUID texte), clé Kafka = compte ;
  - `subject` = `Alerte d'écart d'évaluation — Orange|Rouge|Urgente` ;
  - `content` = `ALERTE_ECART_EVALUATION|alerte=<uuid>|niveau=<NIVEAU>|Évaluation de {nom} ({campagne}) : {n} compétence(s) en écart, écart max {e} étoiles.`
    (`n` = lignes avec `niveau_actuel ≥ ORANGE`, `e` = max `ecart_actuel`). Format compatible avec `svc-notification`
    (aucun changement) et la convention `TYPE|cle=valeur|texte` existante.
- **Snapshot participants** (à la création) : comptes et noms depuis l'évaluation ; matricule, unité (id + libellé) et
  comptes manquants par le nouvel endpoint interne du référentiel
  `GET /internal/referentiel/collaborateurs/{ficheId}/fiche-evaluation` (`X-Internal-Token`) →
  `{identifiant, compte_utilisateur_id, matricule, nom_complet, unite_identifiant, unite_libelle, statut}` (404 si inconnu).
  Référentiel indisponible → alerte créée quand même, champs nuls, `warn` ; nouvel essai à chaque recalcul tant que
  `collaborateur_matricule` est nul.

### 8. D-E09.c — niveau d'écart des compétences techniques dans le détail web

`EvaluationAnalyticsResponse.GapItem` gagne deux champs (additifs, JSON camelCase comme le reste du DTO) :
- `competenceTechnique` (boolean) ;
- `niveauEcart` (`ALIGNE | ORANGE | ROUGE | URGENTE`, `null` pour une question générale).

`severity` reste inchangé (questions générales : « Écart élevé » etc.). Nouvelle surcharge
`analyser(answers, skillAnswers, Set<UUID> questionsTechniques)`. `GET /api/rh/v1/admin/evaluations/{id}/analytics`
passe l'ensemble (via `TemplateTechniqueEvaluation`) dans une méthode de service `@Transactional(readOnly = true)` avec
une requête `JOIN FETCH a.question` (open-in-view = false). L'endpoint mobile `/{id}/analytics` passe un ensemble
**vide** (`niveauEcart` toujours null) : ni le collaborateur ni le RO ne voient un niveau d'alerte (Q4).

### 9. Contrat d'API

Erreurs au format `ErrorResponse` existant (`code`, `message`, `details`, `httpStatus`, `timestamp`) + nouveau champ
facultatif `donnees` (objet, `NON_NULL`). `EvaluationMetierException` gagne `notFound`, `forbidden` et un constructeur
avec `donnees`. Nouveau gestionnaire `MethodArgumentTypeMismatchException` → 400 `PARAMETRE_INVALIDE`.

#### 9.1 Alertes (SCRUM-54) — `AlerteEcartController`, base `/api/rh/v1/admin/evaluations/alertes-ecart`

Classe : `@PreAuthorize(BACKOFFICE_LECTURE)` ; actions : `@PreAuthorize(BACKOFFICE_ECRITURE)`. Aucune route gateway
à ajouter (couvert par `/api/rh/v1/admin/evaluations/**`, avant le fourre-tout référentiel ; les segments littéraux
`alertes-ecart` priment sur `/{id}` d'`EvaluationAdminController` — test MockMvc de non-régression exigé).

| Méthode | Chemin | Rôle | Corps | Réponse 200 |
|---|---|---|---|---|
| GET | `/alertes-ecart` | RH, ADMIN, DIRECTION | — ; requête `statut`, `niveau`, `campagneId`, `uniteId`, `page` (déf. 0), `taille` (déf. 20, 1–100) | `{elements: [AlerteEcartResume], total, page, taille}` |
| GET | `/alertes-ecart/compteur` | RH, ADMIN, DIRECTION | — | `{nonCloses, urgentesNonCloses}` |
| GET | `/alertes-ecart/{alerteId}` | RH, ADMIN, DIRECTION | — | `AlerteEcartDetail` |
| POST | `/alertes-ecart/{alerteId}/prise-en-charge` | RH, ADMIN | `{commentaire?: string ≤ 1000}` (corps facultatif) | `AlerteEcartDetail` (`EN_COURS`) |
| POST | `/alertes-ecart/{alerteId}/cloture` | RH, ADMIN | `{conclusion: string, commentaire: string}` | `AlerteEcartDetail` (`TRAITEE`) |

`AlerteEcartResume` : `identifiant, evaluationId, campagneId, campagneNom, collaborateurNom, collaborateurMatricule,
collaborateurUniteId, collaborateurUniteLibelle, superieurNom, niveau` (= courant), `niveauDetection, statut,
sensGlobal` (`RO_INFERIEUR|RO_SUPERIEUR|DEUX_SENS|null`), `nbCompetencesEnEcart, ecartMax, detecteeLe,
notesModifieesApresDetection`.

`AlerteEcartDetail` = résumé + `libelleSensGlobal`, `aideSensGlobal`, `lignes: [{questionId, competence, ordre,
etoilesCollaborateurDetection, etoilesRoDetection, ecartDetection, niveauDetection, sensDetection,
etoilesCollaborateurActuelles, etoilesRoActuelles, ecartActuel, niveauActuel, sensActuel, libelleSens, aideSens}]`
(ordre des questions), `conclusion`, `conclusionLibelle`, `historique: [{identifiant, typeEvenement, auteurLibelle,
auteurSysteme, le, statutAvant, statutApres, niveauAvant, niveauApres, conclusion, conclusionLibelle, commentaire}]`
(chronologique), `peutTraiter` (bool), `motifNonTraitable` (`LECTURE_SEULE | CONCERNE | null`).
`peutTraiter = rôle RH/ADMIN ∧ appelant non concerné` (indépendant du statut ; le web combine avec `statut`).

Filtres : `statut ∈ StatutAlerteEcart`, `niveau ∈ NiveauEcart` (sur `niveau_courant`), `campagneId` / `uniteId` UUID ;
valeur inconnue ou mal formée, `page < 0`, `taille ∉ [1;100]` → 400 `PARAMETRE_INVALIDE`. Combinaison en ET.
Liste vide → `{elements: [], total: 0, ...}`.

Tri (avant pagination) : non closes avant `TRAITEE` ; `URGENTE > ROUGE > ORANGE > ALIGNE` ; `detecteeLe` croissante ;
`identifiant` croissant. Mise en œuvre : filtres en base (`JpaSpecificationExecutor`), tri par `AlerteEcartComparateur`
(classe testée seule, AC 21), pagination en mémoire. Volume attendu : au plus une alerte par évaluation (quelques
centaines par an) ; au-delà de ~10 000 lignes, passer à un `ORDER BY CASE` SQL.

Compteur : `nonCloses` = statut ≠ `TRAITEE` ; `urgentesNonCloses` = idem et `niveau_courant = URGENTE`.

**Précédence (Q-H) et codes** — le contrôleur ne porte **pas** `@Valid` sur les corps d'action et reçoit `conclusion`
en `String` : la validation est faite dans le service, après 404 et conflit d'intérêts.

| Ordre | Statut | `code` | Cas |
|---|---|---|---|
| 1 | 401 | (resource server) | sans jeton / jeton invalide |
| 2 | 403 | `ACCESS_DENIED` | rôle hors `@PreAuthorize` (COLLABORATEUR, RO, RESPONSABLE ; DIRECTION sur les actions) |
| — | 400 | `PARAMETRE_INVALIDE` | `alerteId` non UUID (conversion Spring, avant le service — AC 14) ; JSON illisible |
| 3 | 404 | `ALERTE_INTROUVABLE` | |
| 4 | 403 | `CONFLIT_INTERETS` | appelant = collaborateur ou RO de l'évaluation (compte, repli fiche) — même sur `TRAITEE` |
| 5 | 400 | `VALIDATION_ERROR` | commentaire > 1000 ; clôture : `conclusion` absente / inconnue, `commentaire` absent, `strip().length() < 10`, > 1000 |
| 6 | 409 | `ALERTE_ETAT_INVALIDE` + `donnees.statutActuel` | prise en charge hors `OUVERTE` ; clôture sur `TRAITEE` |

Concurrence : chaque action charge l'alerte en `PESSIMISTIC_WRITE` ; la seconde clôture simultanée lit `TRAITEE` → 409,
une seule ligne d'historique (AC 12). `@Version` reste en filet (`ObjectOptimisticLockingFailureException` → 409).
Auteur de l'historique : `compteJwt` + `libelle()` (§ 6). Aucune action ne touche l'évaluation (score, statut,
couleur, notes).

Libellés de conclusion (le web peut les coder en dur, le serveur les renvoie aussi dans `conclusionLibelle`) :
`MANQUE_EFFORT` « Manque d'effort confirmé » ; `SURESTIMATION_COLLABORATEUR` « Le collaborateur a surestimé son
niveau » ; `SUIVI_INSUFFISANT_RO` « Le responsable ne suivait pas assez le travail » ; `SOUS_ESTIMATION_COLLABORATEUR`
« Le collaborateur a sous-estimé son travail » ; `NOTE_GONFLEE_RO` « Le responsable a surévalué la note » ;
`ECART_JUSTIFIE` « Écart expliqué, aucune action » ; `AUTRE` « Autre (préciser dans le commentaire) ».

#### 9.2 Validation RO (SCRUM-56) — `EvaluationMobileController` (classe `@PreAuthorize(MOBILE_USER)`, tout profil a `USER`)

| Méthode | Chemin | Réponse 200 | Erreurs (précédence 401 > 404 > 403 > 409 > 422) |
|---|---|---|---|
| GET | `/api/rh/v1/mobile/evaluations/{id}/validate/manager/etat` | `{evaluationId, dejaValidee, collaborateurAValide, avecCompetencesTechniques, competencesManquantes: [{questionId, libelle}], nbCompetencesManquantes, peutValider}` | 401 ; 404 `EVALUATION_INTROUVABLE` ; 403 `PAS_RO_EVALUATION` |
| POST | `/api/rh/v1/mobile/evaluations/{id}/validate/manager` | `EvaluationItemResponse` (DTO existant, `statut = VALIDEE`, `validationSuperieurLe`) | 401 ; 404 `EVALUATION_INTROUVABLE` ; 403 `PAS_RO_EVALUATION` ; 409 `COLLABORATEUR_NON_VALIDE` ; 422 `COMPETENCES_NON_NOTEES` + `donnees.competencesManquantes` |

`peutValider = !dejaValidee ∧ collaborateurAValide ∧ nbCompetencesManquantes = 0`. Aucun champ d'alerte dans ces
réponses ni dans `EvaluationItemResponse`.

#### 9.3 Interne (service à service, non routé par la gateway)

| Service | Chemin | Jeton | Usage |
|---|---|---|---|
| svc-referentiel-rh | `GET /internal/referentiel/collaborateurs/{ficheId}/fiche-evaluation` | `X-Internal-Token` (temps constant, 401 sinon) | snapshot de l'alerte (§ 7) |
| svc-identite-acces | `GET /internal/users/by-role?role=RH|ADMIN` (existant) | `X-Internal-Token` | destinataires (§ 7) |

### 10. `/internal/users/by-role` : corrigé dans ce lot (petit, nécessaire)

Le `permitAll` est voulu (appel sans JWT) ; la protection est le jeton. Corrections :
- comparaison à temps constant (`MessageDigest.isEqual`, jeton nul → refus), comme le référentiel ; statut 403 conservé
  (svc-notification ne dépend pas du code) ;
- `app.internal-api.token=${INTERNAL_API_TOKEN:change-me-internal-token}` (aligné sur les autres services ; le kit
  fournit déjà `APP_INTERNALAPI_TOKEN`, obligatoire) ;
- `role` validé `^[A-Z_]{2,30}$`, sinon 400.
Pas de changement de chemin ni de réponse : svc-notification continue de fonctionner sans modification.

### 11. Configuration

| Propriété (svc-evaluation) | Défaut local | Kit `deploy/docker-compose.yml` |
|---|---|---|
| `evaluation.identite.base-url` | `http://localhost:8081` | `EVALUATION_IDENTITE_BASEURL: http://svc-identite-acces:8081` (à ajouter) |
| `evaluation.referentiel.base-url` | existant | existant |
| `app.internal-api.token` | existant (`INTERNAL_API_TOKEN`) | existant |

Aucune nouvelle variable secrète.

## Alternatives écartées

- Destinataire littéral `RH` : impossible d'exclure le collaborateur et le RO concernés.
- Champ `alerteId` ajouté au `NotificationMessage` : perdu par `svc-notification` (DTO à 4 champs) ; il faudrait modifier un 4e service.
- Détection par planificateur : contraire à D-E06.a (même transaction que la validation).
- Publication Kafka dans la transaction : un rollback enverrait une notification fantôme.
- Matricule / unité ajoutés au snapshot d'activation de l'évaluation : les évaluations existantes n'en ont pas ; le
  snapshot à la détection suffit.
- Réutiliser `requireManagerEvaluation` pour la validation RO : accepte l'en-tête `X-Collaborateur-Id` (usurpable).
- Tri SQL `ORDER BY CASE` : inutile au volume attendu ; le comparateur Java est testable seul.

## Conséquences

- Nouvelles tables créées par Hibernate au démarrage ; runner CHECK pour 5 enums ; colonne nullable sur `evaluation`.
- `EvaluationWorkflowService` gagne deux appels de recalcul ; la logique d'alerte vit dans `AlerteEcartService`
  (le service workflow est déjà à 422 lignes : ne pas l'y mettre).
- Le mobile du RO peut désormais valider ; les écritures de notes refusent l'en-tête usurpé (régression possible si une
  évaluation a des comptes snapshot nuls et que le référentiel est indisponible : 503 plutôt qu'une ouverture).
- Le web ouvre l'alerte depuis la cloche grâce au préfixe de `content` ; le mobile doit masquer ce préfixe.
- Notification perdue si l'identité est indisponible au moment de l'envoi (pas de file de reprise) ; l'alerte reste dans
  la liste et le compteur.

## Sécurité / RGPD (STRIDE léger)

| Menace | Point | Parade |
|---|---|---|
| **S**poofing | Collaborateur qui envoie `X-Collaborateur-Id` = fiche du RO pour écrire les notes RO ou valider | Identité JWT + snapshot de compte (§ 6), en-tête incohérent → 403 ; validation RO jamais sur l'en-tête |
| **S**poofing | Appel direct de `/internal/**` | Non routé par la gateway, jeton partagé comparé à temps constant, jeton en variable d'environnement |
| **T**ampering | Notes modifiées après validation pour « effacer » une alerte | Lignes jamais supprimées, valeurs de détection figées, indicateur collant, historique `RECALCUL` |
| **T**ampering | Historique | Colonnes non modifiables, aucun endpoint d'écriture hors actions |
| **R**epudiation | Qui a validé / clos | `validation_superieur_par`, `auteur_compte_id` du JWT, date UTC |
| **I**nformation | Alerte visible du collaborateur / RO | Endpoints `BACKOFFICE_LECTURE` ; `niveauEcart` absent des réponses mobiles ; notifications sans étoiles, exclues pour les deux intéressés |
| **I**nformation | IDOR sur `/alertes-ecart/{id}` | Toute lecture exige un rôle back-office ; identifiants UUID aléatoires |
| **D**oS | Rafale de notifications par modifications de notes | Q-D (niveau max notifié), délais courts sur les appels internes, `taille ≤ 100` |
| **E**levation | DIRECTION qui agit ; RH qui traite sa propre alerte | `BACKOFFICE_ECRITURE` ; conflit d'intérêts contrôlé côté serveur (403 avant l'état), `peutTraiter` calculé par le serveur |

Données personnelles : nom, matricule, unité, étoiles. Pas de nouvelle donnée sensible au sens RGPD art. 9 ;
conservation 5 ans (C-L-02) ; purge à prévoir avec les évaluations.
