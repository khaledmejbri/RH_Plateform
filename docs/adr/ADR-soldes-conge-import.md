# ADR-SOLDES-IMPORT — Solde de congé annuel, jours fériés et import Excel (D-S01 à D-S15, D-I01 à D-I13)

| | |
|---|---|
| **Statut** | proposé (12/11/2026), intègre les précisions du 12/11/2026 après relecture du testeur (Q1 à Q28, D-S07.a à d, D-S15, D-I13) |
| **Date** | 2026-11-12 |
| **Modules** | M01 (solde, décompte, annulation après approbation), M00 (import des fiches), Paramètres (jours fériés, réglage) |
| **Tickets** | SCRUM-88 (T1 solde), SCRUM-89 (T2 import fiches API), SCRUM-90 (T3 ajustement / import soldes API), SCRUM-91 (T4 web import), SCRUM-92 (T5 web soldes), SCRUM-93 (T6 web paramètres), SCRUM-94 (T7 mobile), SCRUM-87 (T8 jours fériés / réglage API) |
| **Références** | `docs/sirh_basefonctionnelle.md` § 3.5 (D-R06, D-R08), § 5.4, § 5.5 (D-I01 à D-I13), § 6.1, § 6.6 (D-S01 à D-S15), § 23 ; `docs/jira/fiche_soldes_import.md` (G1 à G7, compléments de testabilité) ; `docs/jira/relecture_testeur_soldes_import.md` ; `ADR-UAT-lot3-parametrage.md` ; `ADR-UAT-lot2-fiches-detail.md` |

L'**annexe A** (fin du document) est l'annexe d'API demandée par G1 : chemins, champs, énumérations, forme des erreurs,
matrice des rôles. Les critères de recette la citent.

---

## Contexte

Le SIRH tient le solde de congé annuel en attendant la paie : jours ouvrés avec demi-journées, acquisition mensuelle
automatique, décompte à l'approbation, recrédit quand la RH annule un congé approuvé, ajustements tracés, import Excel des
soldes et des fiches collaborateurs.

État vérifié dans le code le 12/11/2026 :

| Élément | Constat |
|---|---|
| Demandes M01 | `rh_demande_administrative` : statut `@Enumerated` varchar(40), CHECK `rh_demande_administrative_statut_check` qui contient déjà `APPROUVEE` et `ANNULEE` ; type de congé dans le jsonb `contenu.type_conge` ; aucun verrou sur les décisions. |
| Historique M01 | `rh_demande_admin_workflow_history.action` `@Enumerated` varchar(50), **CHECK `rh_demande_admin_workflow_history_action_check`** (9 valeurs) : une action ajoutée exige un runner qui recrée la contrainte. |
| Annulation | `POST …/{id}/annuler` : demandeur seulement, statuts en cours seulement ; sinon 409 `DEMANDE_DEJA_DECIDEE` (c'est le code actuel pour un ordre de mission approuvé, à garder, C1-13). |
| Corps invalide | `date_fin < date_debut` → `IllegalArgumentException` → 400 `{erreur}` **sans code** (code actuel relevé, C1-02) : ce lot le passe en 400 `CORPS_INVALIDE`. |
| Lecture d'une demande | `GET …/{id}` et `/suivi` : hors back-office, **seul le demandeur** lit (403 sinon). Le responsable voit ses demandes par `GET …/en-attente-ro`. |
| Paramétrage référentiel | `rh_parametre_valeur` / `rh_parametre_journal`, `ParametresCache` (30 s, invalidé après commit), `ParametresAmorceRunner` (verrou consultatif). **Aucun réglage numérique** : `GET …/reglages` renvoie `[]`. Le DTO commun `Reglage` porte des **entiers**. |
| Libellés paramétrés | `uk_rh_parametre_ref_libelle` et code tiré du libellé : **incompatible avec des jours fériés** (libellé non unique, D-S15.e). |
| Fiche | `rh_collaborateur.cree_le` existe (not null) : date de création utilisable pour l'éligibilité D-S07.a. `courriel_professionnel` et `compte_utilisateur_id` déjà nullables. |
| Création de fiche | `ReferentielRhService.creerCollaborateur` : courriel obligatoire, compte toujours demandé par Kafka `collaborateur.compte.demande` après commit, **mot de passe saisi transporté dans l'événement**. `svc-identite-acces` (`UserProvisioningService`) crée le compte (identifiant = matricule), lie un compte existant de même identifiant sans courriel, ignore la demande sans mot de passe, puis `CollaborateurWelcomeMailService` publie le courriel sur `rh.notifications`. |
| Lecture Excel | **aucune** bibliothèque (ni POI, ni fastexcel, ni commons-csv). `spring.servlet.multipart.*` non réglé (défaut 1 Mo). nginx du kit : `client_max_body_size 20m`, 413 en HTML. Gateway webflux : corps relayé en flux, sans limite. |
| Gateway | `/api/rh/v1/**` (après évaluation, présence, notifications) et `/api/referentiel/**` vont déjà au référentiel ; `/internal/**` non routé. |
| Appels sortants du référentiel | aucun client HTTP aujourd'hui. svc-identite-acces expose déjà `/internal/users/by-role` (jeton `X-Internal-Token` comparé à temps constant). |
| Notifications | `RhNotificationPublisher` : compte puis repli fiche, `objet_type` / `objet_id` / `objet_numero`, envoi **dans** la transaction appelante. |

## Décision

### 1. Tout dans `svc-referentiel-rh`

Le solde dépend des demandes M01, des fiches et du paramétrage, tous dans le référentiel : pas de nouveau service. L'import
des fiches est aussi dans le référentiel. `svc-identite-acces` change pour générer le mot de passe initial et répondre à une
question interne « ces identifiants ont-ils déjà un compte ? » (§ 7). **Aucune route de gateway à ajouter.** `deploy/nginx.conf`
change pour la taille de requête et le 413 en JSON (§ 11).

### 2. Modèle de données (ddl-auto=update, colonnes ajoutées nullables)

**Nouvelles tables**

| Table | Colonnes | Contraintes / index |
|---|---|---|
| `rh_solde_conge` (une ligne par fiche, créée à la première écriture ; absente = acquis 0, version 0) | `collaborateur_id` uuid **PK** (FK locale `rh_collaborateur`), `acquis` numeric(6,1) not null default 0, `version` bigint not null, `dernier_mouvement_id` uuid null, `modifie_le` timestamptz | CHECK `acquis >= 0` (posé par le runner § 9). `version` incrémentée **à chaque mouvement seulement** (pas quand le réservé change, D-S08.a) |
| `rh_mouvement_solde_conge` (immuable) | `id` uuid PK, `collaborateur_id` uuid not null, `type` varchar(30) not null, `nombre` numeric(5,1) not null (signé), `solde_apres` numeric(6,1) not null, `origine` varchar(20) null, `motif` varchar(500), `demande_id` uuid null, `demande_numero` varchar(24) null (instantané), `mois_acquisition` varchar(7) null (`AAAA-MM`), `import_id` uuid null, `auteur_compte_id` uuid null, `auteur_nom` varchar(160) not null, `cree_le` timestamptz not null | **unique `uk_mvt_solde_acquisition (collaborateur_id, mois_acquisition)`** (NULL distincts en PostgreSQL : seules les acquisitions sont contraintes) ; **unique `uk_mvt_solde_demande (demande_id, type)`** (un « Congé pris » et un « Recrédit » au plus par demande) ; index `(collaborateur_id, cree_le desc)` |
| `rh_acquisition_mois` | `mois` varchar(7) PK, `jours_par_mois` numeric(3,1) not null, `premier_passage_le` timestamptz not null | N figé au premier passage du mois (D-S07.c) |
| `rh_jour_ferie` | `id` uuid PK, `date_jour` date not null, `libelle` varchar(80) not null, `actif` boolean not null, `version` bigint not null, `cree_le`, `modifie_le`, `modifie_par_compte_id` | **unique `uk_jour_ferie_date (date_jour)`** (actifs et inactifs, D-S15.e) |
| `rh_jour_ferie_amorcage` | `annee` int PK, `amorce_le` timestamptz | une ligne par année amorcée (une date supprimée n'est jamais recréée) |
| `rh_parametres` (ligne unique `id = 1`) | `id` smallint PK, `jours_acquis_par_mois` numeric(3,1) not null default 1.5, `debut_acquisitions_le` date, `version` bigint not null, `modifie_le`, `modifie_par_compte_id` | — |
| `rh_import` (analyse puis historique, 5 ans) | `id` uuid PK, `type_import` varchar(20), `statut` varchar(20) (`ANALYSEE`, `CONFIRMEE`), `auteur_compte_id` uuid not null, `auteur_fiche_id` uuid null, `auteur_nom` varchar(160), `nom_fichier` varchar(255) (sans chemin), `ligne_en_tete` int, `correspondance` jsonb, `creer_comptes` boolean, `motif_global` varchar(500), `erreurs_fichier` jsonb, `totaux` jsonb, `cree_le`, `derniere_action_le`, `analyse_le`, `confirme_le` | index `(statut, derniere_action_le)`, `(type_import, confirme_le desc)` |
| `rh_import_contenu` (contenu extrait, temporaire) | `import_id` uuid PK, `cellules` bytea (JSON gzip : en-têtes et cellules de toutes les colonnes, ignorées comprises), `resultat` bytea (JSON gzip : lignes évaluées de la dernière analyse) | supprimée 30 min après la dernière action (§ 6) |
| `rh_import_ligne` (historique par ligne, 5 ans ; sert aussi de trace par fiche, D-I10) | `id` uuid PK, `import_id` uuid not null, `numero_ligne` int, `matricule` varchar(64), `collaborateur_id` uuid null, `action` varchar(20), `changements` jsonb (`[{champ, avant, apres}]`), `erreurs` jsonb (codes et colonnes, **sans valeur de cellule**) | index `(import_id, numero_ligne)`, `(collaborateur_id)`. Lignes « Inchangée » non écrites (C2-09) |

Les colonnes `type`, `origine`, `type_import`, `statut`, `action` des nouvelles tables sont des enums Java **mappés par
`AttributeConverter`** (pas `@Enumerated`) : Hibernate ne génère aucun CHECK, une valeur future n'exige pas de runner.

**Colonnes ajoutées à `rh_demande_administrative`** (nullables) : `jours_decomptes` numeric(4,1), `motif_annulation`
varchar(500), `annule_apres_approbation_le` timestamptz, `annule_par_nom` varchar(160). **`jours_decomptes` non nul ⇔ congé
`ANNUEL` créé après la mise en service** : c'est le marqueur D-S06.g / D-S15.j (null = demande antérieure : réservé 0, ni
contrôle ni mouvement) et la base du réservé. Les périodes restent dans `contenu` : `periode_premier_jour`,
`periode_dernier_jour`.

**Enum stocké modifié** : `ActionWorkflowAdministratif` gagne `ANNULATION_APRES_APPROBATION` (« Annulée après
approbation ») ; runner `WorkflowAdministratifConstraintRunner` qui recrée
`rh_demande_admin_workflow_history_action_check` depuis `values()` (modèle `PointageStatutConstraintRunner`, svc-presence).
`StatutDemandeAdministrativeRh` ne change pas. La mention D-S06.g passe par le `commentaire` de l'action.

**Référentiel de comportement ajouté** : `TYPE_MOUVEMENT_SOLDE` (domaine `DEMANDES`, nature `COMPORTEMENT`, lisible partout)
amorcé avec `ACQUISITION` « Acquisition », `CONGE_PRIS` « Congé pris », `RECREDIT_ANNULATION` « Recrédit d'annulation »,
`AJUSTEMENT` « Ajustement » (`POST` / `DELETE` → 405 `PARAMETRE_COMPORTEMENT` par le code existant).

### 3. Calculs du solde

- **Décompte** (`DecompteJoursOuvres`, pur) : jours lundi–vendredi de `date_debut` à `date_fin` hors jours fériés **actifs**,
  −0,5 si premier jour `APRES_MIDI`, −0,5 si dernier jour `MATIN` ; demande d'un jour : `JOURNEE` 1, `MATIN` ou
  `APRES_MIDI` 0,5. Périodes absentes = `JOURNEE` (ancienne appli mobile).
- **Contrôles de corps (400) avant les règles (422) puis l'état (409)**, D-S15.a / f :
  400 `CORPS_INVALIDE` (période hors liste, dernier jour `APRES_MIDI`, premier jour `MATIN` sur plusieurs jours, demande
  d'un jour à deux périodes différentes, période ≠ `JOURNEE` sur un autre type qu'`ANNUEL`, `date_fin < date_debut`) ;
  400 `DEMI_JOURNEE_JOUR_NON_OUVRE` (demi-journée sur un samedi, un dimanche ou un férié actif, même sans aucun jour
  ouvré) ; 422 `AUCUN_JOUR_OUVRE` ; 422 `SOLDE_INSUFFISANT` ; 409 `CONGE_CHEVAUCHEMENT`.
- **Acquis** = `rh_solde_conge.acquis` (invariant testé : = somme des mouvements). **Réservé** = `sum(jours_decomptes)` des
  demandes du collaborateur en `EN_VALIDATION_SUPERIEUR` / `EN_VALIDATION_RRH` (null ignoré). **Disponible** = acquis −
  réservé. Listes : deux requêtes groupées (soldes, réservés), jamais une par ligne.
- **Création** : jours > disponible ⇒ 422 `SOLDE_INSUFFISANT`. **Validation responsable et approbation RH** : jours recalculés
  (fériés courants) puis **jours > acquis** ⇒ 422 `SOLDE_INSUFFISANT`, statut inchangé ; la première approuvée passe
  (D-S06.b, Q1). Corps de l'erreur : `jours_demandes`, `solde_disponible` (disponible à la création, acquis aux
  validations) ; message « Solde insuffisant : 3,5 jours demandés, 3 jours disponibles ».
- **Chevauchement** (nouvelle demande `ANNUEL`) : chaque congé (tout type) en cours ou approuvé du collaborateur devient un
  intervalle de demi-journées `[2·début + (APRES_MIDI ? 1 : 0), 2·fin + (MATIN ? 1 : 2))` (autres types : jours entiers) ;
  intersection non vide ⇒ 409 `CONGE_CHEVAUCHEMENT`. Les nouvelles demandes d'autres types ne sont pas contrôlées.
- **Arithmétique** : `BigDecimal` d'échelle 1 ; multiple de 0,5 ⇔ `valeur × 2` entier. JSON : nombres (`12.5`) ; virgule
  et accord (« 0,5 jour », « 1,5 jour », « 2 jours », « − » devant un débit) côté affichage.
- **Champs du décideur** (D-S15.c) : `solde_disponible_demandeur` = **acquis** du demandeur (la valeur contrôlée par
  D-S06.b) et `solde_apres_demande` = acquis − jours, renvoyés **seulement** au valideur attendu tant que
  `EN_VALIDATION_SUPERIEUR` et à un RH / ADMIN autre que le demandeur tant que `EN_VALIDATION_RRH`, sur une demande
  `ANNUEL` décomptée ; absents partout ailleurs.

### 4. Concurrence (verrous, ordre fixe)

- Verrou pessimiste `SELECT … FOR UPDATE` sur `rh_solde_conge` du collaborateur, ligne créée au besoin par
  `INSERT … ON CONFLICT (collaborateur_id) DO NOTHING` juste avant.
- **Ordre unique : demande (`PESSIMISTIC_WRITE`, statut recontrôlé après verrou) puis solde.** Création `ANNUEL` : solde
  (D-S15.d). Validation, approbation, refus, annulations : demande puis solde. Ajustement, acquisition : solde. Import des
  soldes : soldes verrouillés dans l'ordre croissant des `collaborateur_id`. Aucun interblocage possible.
- Effets : deux créations simultanées → une 201, l'autre 422 (C1-08) ; deux approbations de deux demandes du même
  collaborateur → sérialisées, la seconde en 422 si l'acquis restant ne suffit plus (CA 10) ; deux RH sur la même demande →
  l'un 200, l'autre 409 `DEMANDE_DEJA_DECIDEE`.
- Ajustement : `version` du client comparée **sous verrou** à `rh_solde_conge.version` (0 si absente) → 409
  `SOLDE_MODIFIE_ENTRE_TEMPS`.
- Mouvement et changement de statut dans **la même transaction** ; acquis négatif impossible (contrôle sous verrou + CHECK).

### 5. Acquisition mensuelle (D-S07, D-S07.a à d, D-S10, D-S13)

- `AcquisitionCongeService.traiter(YearMonth mois)` ; `Clock` injecté partout (zone `Africa/Tunis`).
- Déclencheurs : cron `${referentiel.conges.acquisition.cron:0 15 0 * * *}` (mois courant) ; **une passe au démarrage**
  (`ApplicationReadyEvent`, asynchrone : service relancé le 03/12 à 10 h → acquisitions de décembre le 03/12) ;
  **`POST /internal/referentiel/conges/acquisitions/{mois}`** (jeton interne, jamais routé, pour la recette et
  l'exploitation, mêmes règles d'idempotence ; D-S07.d).
- Mois traité refusé (rien) s'il précède `rh_parametres.debut_acquisitions_le` = **1er du mois qui suit la mise en
  service**, posé une fois au premier démarrage (D-S07.b) ; le cron ne traite jamais un mois passé.
- N : au premier passage d'un mois, `INSERT INTO rh_acquisition_mois … ON CONFLICT DO NOTHING` avec le réglage courant,
  puis relecture ; la valeur figée vaut pour tout le mois ; N = 0 → rien ce mois-ci (D-S07.c).
- Fiche éligible : `cree_le` ≤ fin du 1er du mois (heure de Tunis), `date_recrutement` nulle ou ≤ 1er du mois, `statut =
  'ACTIF'` au moment du passage (D-S07.a).
- Une transaction **par fiche** (`REQUIRES_NEW`) : verrou du solde, test d'existence, insertion, acquis mis à jour. Deux
  exécutions simultanées : l'unicité `(collaborateur_id, mois_acquisition)` tranche ; la violation est ignorée (journal
  DEBUG) et le lot continue. Auteur « Système (automatique) », origine `SYSTEME`, aucune notification.

### 6. Import : lecture bornée, contenu extrait gardé 30 min, confirmation revalidée

**Bibliothèques** (ajout à `svc-referentiel-rh/pom.xml`) : `org.dhatim:fastexcel-reader` (lecture xlsx en flux StAX ; DTD
désactivée par `DefaultXMLInputFactory`, vérifié dans le jar 0.15.3), `org.dhatim:fastexcel` (écriture du modèle et du
fichier des erreurs), `org.apache.commons:commons-csv`. Version 0.15.3 (présente dans le dépôt Maven local) ou la dernière
0.x stable, la même pour les deux artefacts fastexcel. Apache POI écarté (lourd, API événementielle complexe).

**Lecture** (`importation/lecture/`) :
1. Taille > **10 485 760 octets** → 413 `FICHIER_TROP_VOLUMINEUX` (contrôle du `MultipartFile` ; `spring.servlet.multipart.max-file-size=10485760`, `max-request-size=11MB`, `server.tomcat.max-swallow-size=12MB` pour répondre proprement ; `MaxUploadSizeExceededException` traduite en 413 JSON par le `@ControllerAdvice`). Fichier de 0 octet → 400 `FORMAT_NON_SUPPORTE`.
2. Type par **octets magiques** : `PK\x03\x04` → xlsx ; OLE2 `D0 CF 11 E0` (`.xls`, xlsx protégé) → 400 `FORMAT_NON_SUPPORTE` ; sinon, extension `.csv` → CSV ; sinon 400 (texte renommé `.xlsx`, `.ods`).
3. **Garde d'archive** (`GardeArchiveXlsx`, avant fastexcel, sur le flux zip) : total décompressé > 100 Mo ou rapport de compression > 100 → 400 **`FICHIER_TROP_COMPLEXE`** (arrêt dès le dépassement, pas de décompression complète) ; `xl/vbaProject.bin` ou type `macroEnabled` → 400 `FORMAT_NON_SUPPORTE`. Pendant la lecture : plus de **500 000 cellules non vides** lues → 400 `FICHIER_TROP_COMPLEXE`. Liens externes jamais résolus (aucune requête sortante).
4. Feuilles parcourues dans l'ordre jusqu'à la première qui contient une ligne d'en-tête reconnue ; formule = valeur en cache ; nombre entier rendu sans décimale (`12345`, jamais `12345.0`) ; cellule texte gardée telle quelle (zéros de tête) ; date Excel = numéro de série converti si la colonne est une date ; nombre issu d'une formule arrondi au millième avant les contrôles (D-S09.g) ; cellule tronquée à 1 000 caractères (la règle `VALEUR_TROP_LONGUE` s'applique avant).
5. CSV : UTF-8 avec ou sans BOM, sinon Windows-1252 ; séparateur `;` ou `,` selon le plus fréquent dans la première ligne non vide ; guillemets standard.
6. Lignes entièrement vides ignorées et non comptées (1 000 000 de lignes vides mises en forme acceptées) ; à la 5 001ᵉ ligne de données → 422 `TROP_DE_LIGNES`.

**Reconnaissance des en-têtes** (`ReconnaissanceEnTetes`) : normalisation D-I03 précisée (minuscules, sans accents,
« (obligatoire) » et astérisques retirés, ponctuation et symboles → espace, espaces réduits, coupure au premier mot fait de
chiffres, égalité stricte avec un synonyme) ; ligne d'en-tête = première des 10 premières avec ≥ 2 en-têtes reconnus, sinon
422 `EN_TETES_NON_RECONNUS` ; erreurs de fichier `COLONNE_EN_DOUBLE`, `COLONNE_MATRICULE_ABSENTE`,
`COLONNE_SOLDE_ABSENTE` (soldes) : lignes affichées avec les colonnes non ambiguës, `confirmable = false`, confirmation → 422
`ERREUR_DE_FICHIER` (D-I13.c). Colonnes « supérieur » et « nom et prénom » ignorées et signalées.

**Analyse** : `POST` multipart → lecture → **contenu extrait** (cellules, toutes colonnes) écrit gzip dans
`rh_import_contenu`, en-tête dans `rh_import` (statut `ANALYSEE`) → évaluation des lignes → résultat gzip dans
`rh_import_contenu.resultat`. Le **fichier brut n'est jamais écrit** (C-L-03). Aucune écriture métier (fiches, comptes,
mouvements, historique). Correspondance corrigée ou case comptes changée : `PUT` JSON sur l'analyse, **sans renvoyer le
fichier**, même `analyse_id`, nouvelle évaluation. Lignes servies par pages de 50 (`GET …/lignes`). Chaque action (analyse,
correspondance, lecture des lignes, fichier des erreurs, confirmation) met à jour `derniere_action_le`.

**Visibilité** : une analyse n'est visible que de son auteur (`auteur_compte_id` = claim `identifiant_utilisateur`) ; tout
autre utilisateur, même RH / ADMIN → 404 `ANALYSE_INTROUVABLE` (D-I13.i). L'**historique** (analyses confirmées) est lu par
RH, ADMIN, DIRECTION.

**Expiration et purge** (`referentiel.imports.expiration-minutes`, défaut 30, réglable pour la recette, D-S07.d) :
- `now > derniere_action_le + expiration` : analyse `ANALYSEE` → 409 `IMPORT_EXPIRE` ; analyse `CONFIRMEE` → 409
  `IMPORT_DEJA_CONFIRME` sur toute action d'écriture, **sans limite de temps** (prioritaire sur l'expiration).
- `ImportPurgeScheduler` (`${referentiel.imports.purge.cron:0 */5 * * * *}`) : supprime `rh_import_contenu` des analyses
  dont la dernière action date de plus de l'expiration (confirmées comprises : leur fichier des erreurs reste disponible
  30 min) ; supprime les `rh_import` `ANALYSEE` expirées depuis plus de 24 h (ensuite 404 `ANALYSE_INTROUVABLE`).
  Historique `CONFIRMEE` et `rh_import_ligne` gardés 5 ans (purge à prévoir, hors lot).

**Confirmation** (`POST`, une transaction, `timeout = 120 s`). Ordre des contrôles : 401 → 403 rôle → 404
`ANALYSE_INTROUVABLE` → 400 `MOTIF_OBLIGATOIRE` (soldes) → 409 `IMPORT_DEJA_CONFIRME` → 409 `IMPORT_EXPIRE` → 409
`IMPORT_EN_COURS` → 422 `ERREUR_DE_FICHIER` / `AUCUNE_LIGNE_IMPORTABLE`. Les deux 422 dépendent de la réanalyse faite
**sous le verrou** : c'est la seule exception assumée à l'ordre « 422 avant 409 » de D-S15.f.
1. `pg_try_advisory_xact_lock(<constante IMPORT>)`, **un seul verrou pour tous les imports** ; `false` ⇒ 409 `IMPORT_EN_COURS`. Verrou de transaction : libéré au commit, au rollback et à la coupure de connexion (arrêt du service).
2. `rh_import` relue `FOR UPDATE` ; contrôles d'état.
3. Réanalyse sur l'état courant de la base. Fiches : ligne dont l'action a changé ou dont la fiche a été modifiée après l'analyse (`rh_collaborateur.modifie_le` > `analyse_le`, l'identifiant et `modifie_le` sont gardés dans le résultat) → rejetée `LIGNE_MODIFIEE_DEPUIS_ANALYSE` (D-I13.d). Soldes : écart recalculé (cible − acquis du moment ; ligne devenue égale → « Inchangé »).
4. Lignes importables = Créations + Mises à jour (fiches), Ajustements (soldes) ; zéro ⇒ 422 `AUCUNE_LIGNE_IMPORTABLE`.
5. Écritures (fiches, ou mouvements origine `IMPORT`), `rh_import_ligne` pour chaque ligne importée ou rejetée, `rh_import` → `CONFIRMEE` avec totaux. JDBC par lots (`hibernate.jdbc.batch_size=50`, `order_inserts=true`, `order_updates=true`).
6. **Après commit** : demandes de compte Kafka, notifications d'ajustement, invalidation des caches de fiches. Toute panne avant le commit n'écrit rien (C2-19) et l'analyse reste confirmable.

**Délais visés** (D-I13.k, validés) : analyse de 5 000 lignes ≤ 10 s, confirmation ≤ 60 s (5 000 fiches ou 5 000
ajustements), sous réserve de la requête groupée des fiches (une lecture de toutes les fiches par matricule, des unités et
des valeurs paramétrées en mémoire, jamais une requête par ligne).

**Fichiers produits** : fichier des erreurs `.xlsx` (colonnes d'origine, même ignorées, dans l'ordre d'origine + « Erreurs »
en français, lignes en erreur seulement) ; modèles `.xlsx` ; CSV des soldes et des mouvements. Toute cellule **texte**
commençant par `=`, `+`, `-`, `@`, tabulation ou retour chariot est préfixée d'une apostrophe ; dans le fichier des
erreurs, toutes les valeurs d'origine sont des textes (« -5 » est donc préfixé, C2-22). Les nombres calculés par le
serveur (CSV) ne sont pas préfixés.

### 7. Comptes d'accès (D-I09, D-I13.g)

- Import, case décochée : fiche créée **sans compte** (`compte_utilisateur_id` nul, courriel facultatif). La création
  manuelle (`POST /collaborateurs`) **ne change pas**.
- Import, case cochée, ligne **créée** avec courriel : événement `collaborateur.compte.demande` existant, `operation =
  CREATION`, `mot_de_passe_initial = null` et **nouveau champ `mot_de_passe_genere = true`**, publié après commit.
- `svc-identite-acces` : si `mot_de_passe_genere` et aucun compte → `GenerateurMotDePasse` (`SecureRandom`, 16
  caractères, au moins une minuscule, une majuscule, un chiffre, un symbole, sans caractères ambigus) ; encodé, transmis
  **seulement** à `CollaborateurWelcomeMailService` ; jamais journalisé ni renvoyé. Compte existant de même identifiant : lié
  comme aujourd'hui (pas de courriel). Échec (courriel déjà pris…) : comme la création manuelle, fiche sans compte (D-I13.g).
- **« Compte déjà existant »** (D-I07, C2-15) : à l'analyse avec la case cochée, un appel interne
  `POST /internal/users/identifiants-existants` (svc-identite-acces, jeton `X-Internal-Token` comparé à temps constant,
  `permitAll` + contrôle du jeton comme `/internal/users/by-role`) reçoit les matricules des créations et renvoie ceux qui
  ont déjà un compte (comparaison sans casse). Client `IdentiteInterneClient` dans le référentiel : `RestClient`
  `@LoadBalanced` vers `http://svc-identite-acces`, délai 3 s. **Panne : l'analyse continue** avec l'avertissement de
  fichier `VERIFICATION_COMPTES_INDISPONIBLE` ; jamais d'appel pendant la transaction de confirmation.

### 8. Notifications (Kafka `rh.notifications`, JSON texte, au compte puis repli fiche, après commit)

Une seule notification par compte et par événement ; **l'auteur de l'action n'est jamais notifié** (D-S15.c).

| Événement | Destinataires | `objet_type` / `evenement` | Texte |
|---|---|---|---|
| Ajustement (manuel ou import) | collaborateur | `SOLDE_CONGE` / `SOLDE_AJUSTE`, `objet_id` null | « Votre solde de congé a été ajusté : +2 jours (solde : 12 jours). Motif : Report 2025 » |
| Annulation après approbation | demandeur ; valideur D-H03 figé s'il existe et s'il n'est pas l'auteur | `DEMANDE` / `CONGE_ANNULE_APRES_APPROBATION`, `objet_id` = demande, `objet_numero` = `DEM-…` | demandeur : « Votre congé du 14/12/2026 au 18/12/2026 a été annulé par Amel Trabelsi. Motif : … [DEM-…] » ; valideur : « Le congé de Sarra Khemiri du 14/12/2026 au 18/12/2026 a été annulé par la RH. [DEM-…] » |

Mobile : `SOLDE_CONGE` ouvre « Mon solde de congé » ; `DEMANDE` ouvre la demande (existant). svc-notification inchangé.

### 9. Paramétrage : réglage décimal et jours fériés

- **Réglage `jours_acquis_par_mois`** dans `rh_parametres` (0 à 5, pas 0,5, défaut 1,5). Le DTO `Reglage` du référentiel
  passe en décimal (`valeur`, `min`, `max`, `defaut`) et gagne `pas` ; `ReglageRequest.valeur` en `BigDecimal`. Hors bornes
  ou hors pas → 400 `REGLAGE_HORS_BORNES`. Journal dans `rh_parametre_journal` (action `REGLAGE`, `referentiel =
  'REGLAGE'`). Les autres services ne changent pas (`pas` absent = 1). Lecture seule : `GET …/parametres/conges` donne la
  date de début des acquisitions et le N figé du mois courant (D-S07.b).
- **Jours fériés** : table dédiée, date unique, libellé 2–80 non unique, année entre l'année en cours − 1 et + 5 (sinon 400
  `DATE_INVALIDE`). Journal dans `rh_parametre_journal` (`referentiel = 'JOUR_FERIE'`, `code` = date ISO, actions existantes :
  aucune valeur d'enum ajoutée). Suppression libre. Cache Caffeine des dates actives (TTL
  `app.parametres.cache-ttl-secondes`, invalidé après commit).
- **Amorçage** `SoldesCongeAmorceRunner` (verrou consultatif, idempotent) au démarrage **et chaque jour** (tâche à
  `${referentiel.conges.amorcage.cron:0 5 0 * * *}`) : ligne `rh_parametres` ; `debut_acquisitions_le` si nul ; pour l'année
  en cours et la suivante sans ligne dans `rh_jour_ferie_amorcage` : 8 fêtes civiles (`ON CONFLICT (date_jour) DO NOTHING`)
  puis ligne d'amorçage (D-S15.e) ; CHECK `acquis >= 0`. Valeurs de `TYPE_MOUVEMENT_SOLDE` par
  `ValeursInitialesReferentiel` / `ParametresAmorceRunner`.

### 10. Contrat d'API

Voir **annexe A**.

### 11. Configuration et déploiement

- `svc-referentiel-rh/src/main/resources/application.properties` : `spring.servlet.multipart.max-file-size=10485760`,
  `spring.servlet.multipart.max-request-size=11MB`, `server.tomcat.max-swallow-size=12MB`,
  `referentiel.conges.acquisition.cron=0 15 0 * * *`, `referentiel.conges.amorcage.cron=0 5 0 * * *`,
  `referentiel.imports.expiration-minutes=30`, `referentiel.imports.purge.cron=0 */5 * * * *`,
  `referentiel.identite.base-url=http://svc-identite-acces`, `spring.jpa.properties.hibernate.jdbc.batch_size=50`,
  `spring.jpa.properties.hibernate.order_inserts=true`, `spring.jpa.properties.hibernate.order_updates=true`.
  Le jeton interne existe déjà (`app.internal-api.token` ← `INTERNAL_API_TOKEN`). Aucun secret nouveau.
- **Gateway** : relaie le corps en flux sans limite ; aucun changement. Le 413 JSON vient du référentiel (par 8080).
- **`deploy/nginx.conf`** : `client_max_body_size 12m;` et `error_page 413 = @trop_volumineux;` avec
  `location @trop_volumineux { default_type application/json; return 413 '{"code":"FICHIER_TROP_VOLUMINEUX","erreur":"Le fichier dépasse 10 Mo.","message":"Le fichier dépasse 10 Mo."}'; }`.
  Entre 10 485 761 octets et 12 Mo, c'est le référentiel qui répond 413 JSON ; au-delà, nginx répond le même JSON (D-I13.h).
  `proxy_read_timeout 120s` existant suffit à la confirmation. Caddy : pas de limite par défaut.
- Mise à jour d'un serveur existant : reconstruire svc-referentiel-rh, svc-identite-acces et `web` (nginx), puis redémarrer
  la gateway et ensuite `sirh-web-1` (rappel du lot 2).

## Alternatives écartées

- Solde = somme des mouvements à chaque lecture : pas de ligne à verrouiller ni de version pour D-S08.e.
- Contrôle des validations sur « acquis − réservé des autres » : refusait toutes les demandes concurrentes (Q1).
- Renvoi du fichier à la confirmation (aucun contenu gardé) : écarté par D-I10 (correction sans renvoi du fichier).
- Analyse en cache mémoire Caffeine : perdue au redémarrage, propre à une instance, ne distingue pas « confirmée » d'« expirée ».
- Contenu extrait en jsonb clair : plus volumineux que du JSON gzip en bytea, sans besoin de requête dans le contenu.
- Apache POI : lourd ; fastexcel-reader lit en flux avec la DTD désactivée.
- Jours fériés dans `rh_parametre_valeur` : libellé unique et code tiré du libellé incompatibles avec D-S15.e.
- Compte créé par appel HTTP interne : le chemin Kafka existe et garde le mot de passe hors du référentiel ; seul le contrôle d'existence est synchrone.
- Statut `ANNULEE_APRES_APPROBATION` : CHECK et toutes les listes de statuts à changer ; un horodatage plus une action d'historique suffisent.
- ShedLock pour l'acquisition : l'unicité en base suffit.

## Conséquences

- Pour `ANNUEL`, `jours_decomptes` devient la source de vérité ; création, validations et annulations passent par le verrou
  du solde. `DemandeAdministrativeRhService` (596 lignes) se découpe : `SoldeCongeService`, `DecompteJoursOuvres`,
  `AnnulationCongeService`, `ChampsDecideurSolde`.
- L'ancienne appli mobile marche (périodes `JOURNEE` par défaut) ; son « Annuler » sur un congé approuvé reçoit 403 au lieu de 409.
- `date_fin < date_debut` passe de 400 sans code à 400 `CORPS_INVALIDE` (même statut).
- Le DTO `Reglage` du référentiel diffère des deux autres services (décimal, `pas`) ; le web lit `pas ?? 1`.
- Une confirmation d'import garde le verrou et les soldes verrouillés quelques secondes : les approbations concernées attendent.
- Changer un jour férié change les jours des demandes en cours au prochain recalcul (D-S01.c) ; une demande approuvée garde ses jours.
- Le référentiel appelle désormais svc-identite-acces (un seul appel, à l'analyse, dégradé sans erreur).

## Sécurité / RGPD (STRIDE léger)

| Menace | Risque | Parade |
|---|---|---|
| Usurpation | Lire le solde d'un autre via `X-Collaborateur-Id` ou un paramètre | A1 à A3 n'acceptent aucun identifiant : fiche résolue depuis le JWT (`collaborateurId` et en-tête ignorés). Routes back-office : `@PreAuthorize` ; collaborateur / responsable → 403 identique que la fiche existe ou non. |
| Usurpation | Lire ou confirmer l'analyse d'un autre RH | Analyse liée à l'auteur JWT ; autre utilisateur → 404 `ANALYSE_INTROUVABLE`. |
| Usurpation | Appeler `/internal` (acquisition, identifiants existants) | Non routé par la gateway ; `X-Internal-Token` comparé à temps constant ; nginx ne relaie que `/api`, `/oauth2`, `/.well-known`, `/ws`. |
| Altération | Jours envoyés par le client ; aperçu différent de la confirmation | Jours recalculés au serveur à chaque étape ; confirmation réanalysée sur l'état courant, lignes modifiées entre-temps rejetées. |
| Altération | Courses (approbations, ajustements, créations, acquisition, import) | Verrous § 4, version, unicités `(collaborateur, mois)` et `(demande, type)`, CHECK `acquis >= 0`, verrou unique des imports. |
| Altération | Réécriture de l'historique du solde | Mouvements immuables (`updatable=false`, aucun endpoint de modification ni de suppression). |
| Répudiation | « Ce n'est pas moi » | Auteur JWT et nom figés sur chaque mouvement, historique d'annulation avec motif, historique d'import par ligne, journal des paramètres. |
| Élévation | Droits élevés par import, ajuster son solde, annuler son congé | `PROFIL_NON_IMPORTABLE` si le profil changerait vers ou depuis RH / DIRECTION / ADMIN ; `AUTO_AJUSTEMENT_INTERDIT` (manuel et ligne d'import) ; D4 refuse le demandeur ; DIRECTION : 403 sur toute écriture. |
| Divulgation | Solde exposé à un lecteur de la demande | Champs du décideur seulement (§ 3). |
| Divulgation | Mot de passe initial | Généré dans svc-identite-acces, jamais dans le référentiel, l'API, l'historique ou un journal. **Reste en clair sur `rh.notifications`** vers le courriel (existant, réseau interne). |
| Divulgation | Contenu du fichier | Fichier brut jamais écrit ; contenu extrait gzip, visible de son seul auteur, supprimé 30 min après la dernière action ; historique sans valeur de cellule hors matricule et champs modifiés ; aucune valeur de cellule dans les journaux INFO. |
| Injection | Formules dans les fichiers produits | Apostrophe § 6. Textes jamais interprétés (`<script>` rendu en texte par React / Flutter). |
| Injection | XXE dans un xlsx | DTD désactivée par fastexcel-reader ; test avec un `<!DOCTYPE>` externe. |
| Déni de service | Archive piégée, fichier énorme, imports concurrents | 10 Mo, 5 000 lignes, 100 Mo décompressés, rapport 100, 500 000 cellules (`FICHIER_TROP_COMPLEXE` en moins de 10 s), une confirmation à la fois, `timeout` de transaction, nginx 12 Mo. |
| RGPD | Conservation | Mouvements 10 ans (C-L-01), historique d'import 5 ans (C-L-02, purge hors lot), contenu extrait 30 min, analyses non confirmées supprimées 24 h après expiration, fichier jamais conservé (C-L-03). |

## Risques

1. Mot de passe en clair sur `rh.notifications` (existant) : à remplacer par un lien d'activation dans un lot sécurité.
2. Annulation par la RH d'une demande **en cours** : non décrite ; ce plan répond 409 `DEMANDE_NON_APPROUVEE` (la RH la refuse). À confirmer par l'expert.
3. `solde_disponible_demandeur` = acquis (D-S06.b) : le valideur peut voir un nombre supérieur au disponible de « Mon solde » quand d'autres demandes sont en cours. Libellé web / mobile : « Solde acquis du demandeur » serait plus juste ; à trancher par l'expert (le champ ne change pas).
4. Garde de lecture : à éprouver avec de vrais fichiers piégés (zip bomb, xlsx chiffré, xlsm, DOCTYPE externe, 1 000 000 de lignes mises en forme).
5. Le contenu extrait d'un fichier de 500 000 cellules pèse quelques Mo compressés en base pendant 30 min : acceptable.
6. Liste des soldes filtrée en mémoire : jusqu'à quelques milliers de fiches.
7. `/internal/…/acquisitions/{mois}` accepte un mois futur (recette) : réservé à l'exploitation, tracé en INFO avec le mois.
8. Base locale : `rh_demande_document_administratif_type_document_check` existe encore sur le poste (runner du lot 3 non passé ?) : à vérifier avant la recette.

## Plan d'implémentation

**Règle graphify (obligatoire pour chaque développeur)** : `graphify-out/graph.json` existe. Avant tout grep ou lecture de
code source, lancer `graphify query "<question>"` ; utiliser `graphify path "<A>" "<B>"` et `graphify explain "<concept>"` ;
lire les fichiers bruts seulement pour les lignes ainsi localisées. Après modification du code : `graphify update .`.

Ordre : le backend livre B1 (T8 + T1 + ajustement de T3), puis B2 (T2 + reste de T3). Le web et le mobile démarrent en
parallèle **sur l'annexe A** (bouchons) et branchent l'API dès B1 / B2 livrés.

### Lot BACK — `dev-backend` : `svc-referentiel-rh/**`, `svc-identite-acces/**`, `deploy/nginx.conf` (seul agent sur ces fichiers ; aucune modification de `gateway/`)

B1 — T8, T1, ajustement :
- Domaine : `TypeMouvementSolde`, `OrigineMouvementSolde`, `PeriodeJour`, `ActionWorkflowAdministratif` (+1), `ReferentielParametre` (+ `TYPE_MOUVEMENT_SOLDE`), `ValeursInitialesReferentiel`.
- Entités / dépôts : `SoldeConge`, `MouvementSoldeConge`, `AcquisitionMois`, `JourFerie`, `JourFerieAmorcage`, `ReferentielParametres` ; colonnes de `DemandeAdministrativeRh` ; requêtes : verrou du solde (upsert + `FOR UPDATE`), réservé (seul et groupé), congés chevauchants, fiches éligibles, demande `PESSIMISTIC_WRITE`.
- Config : `SoldesCongeAmorceRunner`, `WorkflowAdministratifConstraintRunner`, cache des jours fériés (`CacheConfig`), `Clock` (bean existant ou ajouté).
- Services : `DecompteJoursOuvres`, `JourFerieService`, `ReglagesReferentielService` (remplace les bouchons de `ParametreService.reglages*`), `SoldeCongeService`, `AjustementSoldeService`, `AcquisitionCongeService` + `AcquisitionCongeScheduler`, `AnnulationCongeService`, `ChampsDecideurSolde` ; branchements dans `DemandeAdministrativeRhService` et `DemandeAdministrativeValidationService` ; `RhNotificationPublisher` (+ 2 notifications après commit, sans l'auteur, une par compte).
- Web : `CongeCollaborateurController` (A1 à A3), `SoldeCongeAdminController` (B1 à B6), `JourFerieController` (P4 à P10), `ParametreController` (P1 à P3, P12), `DemandeAdministrativeRhController` (D1 à D5), `InterneReferentielController` (+ X1) ; `ReferentielMetierException.forbidden(...)` et `payloadTooLarge(...)`, 413 dans `ReferentielExceptionHandler`, `date_fin < date_debut` en `CORPS_INVALIDE`. DTO en `record` (`SoldeDtos`, `JourFerieDtos`, `ParametreDto.Reglage` décimal + `pas`).

B2 — T2, import des soldes :
- `pom.xml` : `fastexcel-reader`, `fastexcel`, `commons-csv`.
- Paquet `importation/` : `lecture/` (`DetectionFormat`, `GardeArchiveXlsx`, `LecteurXlsx`, `LecteurCsv`, `ContenuExtrait`), `ReconnaissanceEnTetes` (+ synonymes), `ImportVerrou`, `ImportAnalyseStore` (gzip, `derniere_action_le`, auteur), `ImportCollaborateursService`, `ImportSoldesService`, `ImportHistoriqueService`, `FichierErreursXlsx`, `ModeleImportXlsx`, `EchappementFormule` (partagé avec les CSV), `ImportPurgeScheduler`, `IdentiteInterneClient` ; entités `ImportAnalyse`, `ImportContenu`, `ImportLigne`.
- `ReferentielRhService` : extraire sans changer le comportement l'application des champs et les effets (supérieur dérivé, `MAJ_ROLES`, archivage) pour l'import ; création sans compte réservée à l'import.
- `kafka/CollaborateurCompteDemandeEvent` (+ `mot_de_passe_genere`) dans **les deux** services.
- `svc-identite-acces` : `UserProvisioningService`, `GenerateurMotDePasse`, `InternalUserController` (+ `POST /internal/users/identifiants-existants`), `SecurityConfig` (matcher de cette route), `UserRepository` (recherche sans casse par lot).
- `ImportController` (I1 à I12) ; `application.properties` (§ 11) ; `deploy/nginx.conf` (§ 11).

Tests attendus (JUnit 5 + Mockito, `@WebMvcTest` + JWT simulé, `Clock` injecté, noms en français, un test par règle) :
- `DecompteJoursOuvresTest` : C1-01 (semaine du 07/12/2026), C1-02, férié un samedi, férié inactif.
- `SoldeCongeServiceTest` : C1-03, C1-04, C1-05, C1-06, C1-09, C1-14, C1-15, invariant acquis = somme des mouvements, `MALADIE` sans contrôle.
- `AnnulationCongeServiceTest` : CA 12 à 17, C1-10 (23:30 UTC = lendemain à Tunis), C1-11, C1-12, notifications (auteur exclu, une par compte).
- `AcquisitionCongeServiceTest` : C1-16 complet (éligibilité au 1er, `cree_le`, N figé, N = 0, mois de mise en service, collision d'unicité, lot continué).
- `ChampsDecideurSoldeTest` : C1-18.
- Concurrence (test d'intégration sur PostgreSQL de recette ou `@DataJpaTest` dédié) : C1-08, CA 10 (≥ 20 essais), C3-06.
- `AjustementSoldeServiceTest` : C3-01 à C3-06, ordre des contrôles D-S15.f.
- `ReconnaissanceEnTetesTest` (C2-03, C2-06, C3-08), `LecteurXlsxTest` (date Excel, formule, matricule numérique, zéros de tête, 2,5000000000000004), `LecteurCsvTest` (`;` / `,`, BOM, Windows-1252), `GardeArchiveXlsxTest` (zip bomb → `FICHIER_TROP_COMPLEXE` < 10 s, 500 001 cellules, xlsx chiffré, xlsm, 0 octet, DOCTYPE), `EchappementFormuleTest` (C2-22).
- `ImportCollaborateursServiceTest` : C2-02, C2-04, C2-05, C2-09 à C2-18, C2-20, C2-23 (aucune écriture à l'analyse, correspondance sans renvoi, `LIGNE_MODIFIEE_DEPUIS_ANALYSE`, expiration réglable, déjà confirmé prioritaire, verrou unique, compte demandé après commit seulement, aucun mot de passe dans l'événement, panne d'identité → avertissement).
- Échec forcé à la ligne k (C2-19) : rien n'est écrit, aucun événement Kafka, analyse reconfirmable.
- `ImportSoldesServiceTest` : C3-09 à C3-13, C3-16 (5 000 lignes ≤ 60 s, test de charge marqué et non bloquant si l'environnement est lent).
- `UserProvisioningServiceTest` / `InternalUserControllerTest` (identité) : mot de passe généré ≥ 12 caractères absent des journaux ; compte existant lié sans courriel ; route interne 403 sans jeton.
- Contrôleurs : matrice G4 de l'annexe A.3 pour chaque endpoint (sans JWT, COL-WEB, CS-WEB, CS-MOB, RH02, DRH01, ADM01, DG01) ; A1 avec `X-Collaborateur-Id` d'un autre ; 413 à 10 485 761 octets.
- `SoldesCongeAmorceRunnerTest` : 16 jours fériés, redémarrage sans doublon, date supprimée non recréée, année suivante amorcée le 1er janvier.
- Vérifier : `mvn -B -pl svc-referentiel-rh -am test`, `mvn -B -pl svc-identite-acces -am test`.

### Lot WEB — `dev-web` : `rh-admin-web/**`

- `src/api/congesApi.ts` (B1 à B6, D4), `src/api/importsApi.ts` (I1 à I12, multipart, téléchargements), `src/api/parametresApi.ts` (`Reglage.pas`, décimaux, P4 à P12).
- `src/pages/SoldesCongePage.tsx` (+ route `/app/conges/soldes` dans `App.tsx`, entrée « Soldes de congé » du groupe Collaborateurs dans `layout/AppShell.tsx`, RH / ADMIN / DIRECTION) : liste (statut Actif par défaut, 20 par page), filtres dans l'URL (`utils/filtres.ts`, `BarreFiltres`), tri, export, « Ajuster » (écart ou nouveau solde, motif dans la même fenêtre, bouton inactif sans motif, absent si `est_moi`, avertissement, 422 en clair, 409 → message et liste rechargée), « Importer des soldes ».
- `src/pages/imports/` (nouveau) : `AssistantImport.tsx` générique (dépôt avec contrôle 10 485 760 octets et extension avant envoi, lien du modèle, correspondance par colonne avec « Ignorer » → `PUT` sans renvoi du fichier, aperçu paginé par 50 côté serveur, filtre « Erreurs seulement », confirmation `ConfirmDialog` « Importer 8 fiches ? 2 lignes en erreur ne seront pas importées. », bouton inactif pendant l'appel, compte rendu, fichier des erreurs, `IMPORT_EXPIRE` → message dédié et retour au dépôt, `ERREUR_DE_FICHIER` et autres codes en clair, focus sur le titre de chaque étape), `ImportCollaborateurs.tsx` (case « Créer les comptes d'accès » + explication), `ImportSoldes.tsx` (motif global obligatoire, solde actuel / cible / écart), `HistoriqueImports.tsx` (I10 à I12, lecture DIRECTION).
- `src/pages/CollaborateursPage.tsx` : bouton « Importer » (RH, ADMIN).
- `src/detail/contenus/CollaborateurContenu.tsx` : onglet « Congés » (solde, mouvements paginés, lien `DEM-…`, « Ajuster »).
- `src/pages/DemandesAdministrativesPage.tsx`, `src/detail/contenus/DetailContenu.tsx` : jours décomptés, périodes, champs du décideur s'ils sont présents, « Annuler le congé » si `peut_annuler_apres_approbation` (motif 5–500 dans la même fenêtre), « Annulée après approbation » + motif + auteur, 422 `SOLDE_INSUFFISANT` et 403 en clair.
- `src/pages/parametres/JoursFeriesPanel.tsx` (nouveau, filtre par année, ajout / modification / désactivation / suppression confirmées, « Ce jour férié existe déjà », journal) ; `ParametresPage.tsx` (entrée « Jours fériés », domaine Demandes, et date de début des acquisitions en lecture) ; `ReglagesPanel.tsx` (`step = pas ?? 1`, virgule, bornes).
- `src/utils/format.ts` : jours avec accord (« 0,5 jour », « 1,5 jour », « 2 jours », « − » devant un débit). DIRECTION : aucune action.
- Vérifier : `npx tsc --noEmit -p .` et `npx vite build` ; 1 024 px sans débordement ; navigation clavier.

### Lot MOBILE — `dev-mobile` : `rh_mobile_app/**` (jamais `rh_mobile_app_temp` ; Flutter 3.29.2 max : pas d'`initialValue` de `DropdownButtonFormField` ni d'API 3.33+)

- `lib/features/conges/` (nouveau) : `data/solde_models.dart`, `data/solde_repository.dart` (A1 à A3), `providers/solde_providers.dart`, `presentation/mon_solde_screen.dart` (disponible en grand, acquis, réservé, mouvements par 20, filtre par type, « Aucun mouvement », « Réessayer »).
- `lib/core/constants/api_constants.dart`, `lib/core/router/app_router.dart` (route du solde).
- `lib/features/auth/presentation/profile_screen.dart` : carte « Jours de congé » = disponible, « Réessayer » en erreur, jamais « 0 ».
- `lib/features/demandes_admin/` : `demande_conge_create_screen.dart` (périodes premier / dernier jour, une seule pour un jour, simulation à chaque changement, `blocage` affiché et envoi inactif), `demande_admin_repository.dart`, `demande_admin_models.dart` (périodes, champs ajoutés), `demandes_admin_list_screen.dart` (filtres type et statut, jours), `demande_admin_detail_screen.dart` (aucun « Annuler » sur un congé approuvé, mention RH, « Annulée après approbation » + motif + recrédit ; 403 en clair), `ro_validation_screen.dart` (champs du décideur, 422 en clair).
- `lib/core/notifications/notification_navigation.dart` (`SOLDE_CONGE` → mon solde), `lib/core/libelles/` si besoin.
- Accord des nombres comme le web. Tests `test/features/conges/` (modèles, écran : chargement, vide, erreur), formulaire (simulation, bouton inactif), détail (pas d'annulation sur un congé approuvé). Vérifier : `flutter analyze`, `flutter test` (Flutter absent du poste : build Codemagic).

### Recette (rappel pour le testeur)

Scripts dans `recette/outils/` (hors Git), données préfixées « [RECETTE-SOLDES] », ne supprimer que ses propres données ;
expiration des analyses abaissée par `REFERENTIEL_IMPORTS_EXPIRATIONMINUTES=2` ; acquisition par
`POST /internal/referentiel/conges/acquisitions/2026-12` (jeton interne, depuis le réseau Docker ou `localhost:8083`) ;
fichiers piégés générés localement, jamais committés.

---

## Annexe A — Contrat d'API (G1)

### A.0 Conventions

- JSON snake_case. Décimaux en nombre JSON (`12.5`). Dates `aaaa-mm-jj`, instants ISO-8601 UTC. Mois `AAAA-MM`.
- Erreur : `{ "code": "SOLDE_INSUFFISANT", "erreur": "<message>", "message": "<message>" }` + champs propres indiqués
  (ex. `jours_demandes`, `solde_disponible`, `champs` pour une validation multiple). 401 sans JWT : corps vide (Spring).
- Page : `{ "contenu": [...], "total_elements": 0, "total_pages": 0, "page": 0, "taille": 20 }` (`PageReferentielResponse`).
- Rôles : `USER` = `hasRole('USER')` ; `BO_LECTURE` = RH, DIRECTION, ADMIN ; `BO_ECRITURE` = RH, ADMIN ; `AUTH` = `isAuthenticated()`.
- Ordre des contrôles (D-S15.f) : 401 → 403 rôle → 404 (ou 403 masqué) → 403 identité → 400 corps → 422 règle → 409 état,
  sauf la confirmation d'import (§ 6).
- Aucun UUID ni code de comportement dans un champ destiné à l'écran (`*_libelle`, `auteur_nom`, `numero`, `message`) ; les
  `*_id` et codes servent aux liens et aux filtres seulement (G7).

### A.1 Énumérations

| Énumération | Valeurs (libellé) |
|---|---|
| `periode_premier_jour` | `JOURNEE` (Journée entière), `APRES_MIDI` (Après-midi), `MATIN` (Matin, demande d'un jour seulement) |
| `periode_dernier_jour` | `JOURNEE` (Journée entière), `MATIN` (Matin) ; absente ou égale à la première pour une demande d'un jour |
| `type` de mouvement | `ACQUISITION`, `CONGE_PRIS`, `RECREDIT_ANNULATION`, `AJUSTEMENT` (libellés paramétrés `TYPE_MOUVEMENT_SOLDE`) |
| `origine` | `SAISIE_MANUELLE` (Saisie manuelle), `IMPORT` (Import), `SYSTEME` (Système (automatique)) |
| `blocage.code` (simulation) | `SOLDE_INSUFFISANT`, `AUCUN_JOUR_OUVRE`, `CONGE_CHEVAUCHEMENT` |
| `type_import` | `COLLABORATEURS` (Collaborateurs), `SOLDES` (Soldes) |
| `statut` d'import | `ANALYSEE`, `CONFIRMEE` |
| `etat` de colonne | `RECONNUE`, `IGNOREE`, `EN_DOUBLE` |
| `action` de ligne (fiches) | `CREATION` (Création), `MISE_A_JOUR` (Mise à jour), `INCHANGEE` (Inchangée), `ERREUR` (Erreur) ; historique : `CREATION`, `MISE_A_JOUR`, `REJETEE` |
| `action` de ligne (soldes) | `AJUSTEMENT` (Ajustement), `INCHANGE` (Inchangé), `ERREUR` (Erreur) ; historique : `AJUSTEMENT`, `REJETEE` |
| `champ` (fiches) | `MATRICULE`, `PRENOM`, `NOM`, `COURRIEL`, `UNITE`, `POSTE`, `FONCTION`, `QUALITE`, `AFFECTATION`, `DATE_RECRUTEMENT`, `STATUT`, `PROFIL_ACCES`, `FAMILLE_METIER`, `NIVEAU_SENIORITE` |
| `champ` (soldes) | `MATRICULE`, `SOLDE`, `MOTIF` |
| Erreurs de ligne (fiches) | `MATRICULE_OBLIGATOIRE`, `MATRICULE_EN_DOUBLE_FICHIER`, `CHAMP_OBLIGATOIRE`, `COURRIEL_OBLIGATOIRE`, `COURRIEL_EN_DOUBLE`, `UNITE_INCONNUE`, `UNITE_INACTIVE`, `UNITE_AMBIGUE`, `VALEUR_INCONNUE`, `PROFIL_NON_IMPORTABLE`, `DATE_INVALIDE`, `FORMAT_INVALIDE`, `VALEUR_TROP_LONGUE`, `LIGNE_MODIFIEE_DEPUIS_ANALYSE` |
| Erreurs de ligne (soldes) | `MATRICULE_OBLIGATOIRE`, `MATRICULE_INCONNU`, `MATRICULE_EN_DOUBLE_FICHIER`, `SOLDE_OBLIGATOIRE`, `SOLDE_INVALIDE`, `MOTIF_INVALIDE`, `AUTO_AJUSTEMENT_INTERDIT` |
| Erreurs de fichier | `COLONNE_EN_DOUBLE`, `COLONNE_MATRICULE_ABSENTE`, `COLONNE_SOLDE_ABSENTE` |
| Avertissements de ligne | `COMPTE_NON_CREE` (« Compte non créé : courriel absent »), `COMPTE_DEJA_EXISTANT`, `COLLABORATEUR_ARCHIVE`, `CIBLE_INFERIEURE_RESERVE` |
| Avertissements de fichier | `COLONNE_IGNOREE` (listées à part, hors du total « Avertissements »), `ACQUISITION_RECENTE` (analyse du 1er au 5, heure de Tunis), `VERIFICATION_COMPTES_INDISPONIBLE` |

Longueurs (saisie manuelle, relevées dans `CollaborateurCreationRequest`) : matricule 64, prénom 120, nom 120, courriel 255,
poste 255, fonction 255, qualité 255, affectation 500. Date de recrutement : `jj/mm/aaaa`, `aaaa-mm-jj` ou série Excel,
au plus aujourd'hui + 1 an.

### A.2 Endpoints

#### Collaborateur (mobile)

| # | Méthode, chemin (via la gateway) | Rôle | Entrée | 2xx | Erreurs |
|---|---|---|---|---|---|
| A1 | `GET /api/rh/v1/conges/solde/moi` | USER | — | 200 `Solde` | 404 `COLLABORATEUR_INTROUVABLE` (compte sans fiche) |
| A2 | `GET /api/rh/v1/conges/solde/moi/mouvements?page=0&taille=20&type=` | USER | `taille` 1–100 (défaut 20) | 200 page de `Mouvement` (plus récents d'abord) | 400 `CORPS_INVALIDE` (type inconnu, taille hors bornes) |
| A3 | `GET /api/rh/v1/conges/simulation?date_debut=&date_fin=&periode_premier_jour=&periode_dernier_jour=` | USER | congé `ANNUEL` | 200 `Simulation` | 400 `CORPS_INVALIDE`, 400 `DEMI_JOURNEE_JOUR_NON_OUVRE` |

```json
Solde      { "acquis": 10.0, "reserve": 5.0, "disponible": 5.0 }
Mouvement  { "type": "CONGE_PRIS", "type_libelle": "Congé pris", "nombre": -5.0, "solde_apres": 5.0, "cree_le": "…",
             "auteur_nom": "Amel Trabelsi", "origine": null, "origine_libelle": null, "motif": null,
             "demande_id": "…", "demande_numero": "DEM-202612-0001", "mois_acquisition": null, "mois_acquisition_libelle": null }
Simulation { "jours_decomptes": 2.0, "disponible": 12.5, "disponible_apres": 10.5,
             "jours_feries": [ { "date": "2026-12-17", "libelle": "Fête de la Révolution" } ],
             "blocage": null }   // ou { "code": "SOLDE_INSUFFISANT", "libelle": "Solde insuffisant : 3,5 jours demandés, 3 jours disponibles" }
```

`mois_acquisition_libelle` = « décembre 2026 » ; auteur système = « Système (automatique) ». A1 à A3 ignorent
`X-Collaborateur-Id` et tout paramètre `collaborateurId`.

#### Demandes M01 (existant, modifié)

| # | Méthode, chemin | Rôle | Changement |
|---|---|---|---|
| D1 | `POST /api/rh/v1/demandes-administratives` | USER | `contenu.periode_premier_jour`, `contenu.periode_dernier_jour` ; `ANNUEL` : verrou du solde, 400 `CORPS_INVALIDE` / `DEMI_JOURNEE_JOUR_NON_OUVRE`, 422 `AUCUN_JOUR_OUVRE`, 422 `SOLDE_INSUFFISANT` (+ `jours_demandes`, `solde_disponible`), 409 `CONGE_CHEVAUCHEMENT`. Période ≠ `JOURNEE` sur un autre type → 400 `CORPS_INVALIDE`. |
| D2 | `POST …/{id}/valider-superieur`, `POST …/{id}/valider-rrh` | inchangé | verrous ; jours recalculés ; jours > acquis → 422 `SOLDE_INSUFFISANT` statut inchangé ; à l'approbation : jours figés, mouvement `CONGE_PRIS` ; `jours_decomptes` nul : historique « Demande antérieure aux soldes : non décomptée ». Second décideur : 409 `DEMANDE_DEJA_DECIDEE`. |
| D3 | `POST …/{id}/annuler` | USER (demandeur) | congé `APPROUVEE` → **403** « Pour annuler un congé approuvé, contactez les RH. » ; ordre de mission approuvé et demande déjà décidée : 409 `DEMANDE_DEJA_DECIDEE` (inchangé). |
| D4 | **`POST …/{id}/annuler-apres-approbation`** | BO_ECRITURE | corps `{ "motif": "…" }`. 403 rôle (DIRECTION, responsable, collaborateur) → 404 `DEMANDE_INTROUVABLE` → 403 son propre congé → 400 `MOTIF_OBLIGATOIRE` (absent, blanc, < 5 ou > 500 après retrait des espaces de bord) → 409 `DEMANDE_DEJA_DECIDEE` (annulée, refusée) → 409 `DEMANDE_NON_APPROUVEE` (en cours) → 409 `DEMANDE_NON_ANNULABLE` (ordre de mission, autorisation de sortie) → 409 `CONGE_DEJA_COMMENCE` (`date_debut` ≤ aujourd'hui à Tunis). 200 `DemandeAdministrativeRhResponse` : `ANNULEE`, recrédit si `jours_decomptes` non nul, notifications. |
| D5 | `GET …/{id}` | AUTH | lecture ouverte aussi au **valideur attendu** ; tout autre non back-office → 403 (inchangé). |

**Champs ajoutés à `DemandeAdministrativeRhResponse`** (absents si nuls) : `jours_decomptes`, `periode_premier_jour`,
`periode_premier_jour_libelle`, `periode_dernier_jour`, `periode_dernier_jour_libelle`, `annulee_apres_approbation`
(bool), `motif_annulation`, `annule_apres_approbation_le`, `annule_par_nom`, `jours_recredites` (si recrédit),
`peut_annuler_apres_approbation` (bool pour l'appelant : RH / ADMIN, pas le demandeur, congé approuvé non commencé),
`solde_disponible_demandeur` et `solde_apres_demande` (décideur de l'étape en cours seulement, § 3). Mêmes champs dans
`GET …` (mes demandes), `GET …/en-attente-ro`, `GET …/liste`, `GET …/{id}`. Codes existants inchangés ailleurs.

#### Soldes, back-office

| # | Méthode, chemin | Rôle | Entrée | 2xx | Erreurs |
|---|---|---|---|---|---|
| B1 | `GET /api/rh/v1/admin/conges/soldes?q=&unite_id=&statut=ACTIF&tri=nom&sens=asc&page=0&taille=20` | BO_LECTURE | `q` sans accents multi-mots (nom, prénom, matricule) ; `unite_id` = nœud **et descendants** ; `statut` défaut `ACTIF` (`TOUS` pour tout) ; `tri` ∈ `nom`, `matricule`, `unite`, `acquis`, `reserve`, `disponible`, `dernier_mouvement` ; `taille` 1–100 | 200 page de `SoldeLigne` | 400 `CORPS_INVALIDE` |
| B2 | `GET /api/rh/v1/admin/conges/soldes/export?<filtres B1>` | BO_LECTURE | — | 200 `text/csv; charset=UTF-8` (format des exports existants : BOM, `;`, virgule décimale, `jj/mm/aaaa`) ; colonnes Nom, Matricule, Unité, Statut, Solde acquis, Réservé (demandes en cours), Solde disponible, Dernier mouvement | 400 |
| B3 | `GET /api/rh/v1/admin/conges/soldes/{collaborateurId}` | BO_LECTURE | — | 200 `SoldeLigne` | 404 `COLLABORATEUR_INTROUVABLE` |
| B4 | `GET /api/rh/v1/admin/conges/soldes/{collaborateurId}/mouvements?page&taille&type` | BO_LECTURE | `taille` 1–100 (défaut 20) | 200 page de `Mouvement` | 404, 400 |
| B5 | `GET /api/rh/v1/admin/conges/soldes/{collaborateurId}/mouvements/export` | BO_LECTURE | — | 200 CSV (Date, Type, Nombre, Solde après, Auteur, Origine, Motif, Demande) | 404 |
| B6 | `POST /api/rh/v1/admin/conges/soldes/{collaborateurId}/ajustements` | BO_ECRITURE | `{ "ecart": 2.0 }` **ou** `{ "nouveau_solde": 7.5 }` + `"motif"`, `"version"` | 200 `{ "solde": SoldeLigne, "mouvement": Mouvement, "avertissement": "Des demandes en cours dépassent le nouveau solde" \| null }` | 404 → 403 `AUTO_AJUSTEMENT_INTERDIT` → 400 `CORPS_INVALIDE` (version absente) / `VALEUR_INVALIDE` (format, pas, bornes, deux modes ou aucun) / `MOTIF_OBLIGATOIRE` (réponse `champs` liste tous les champs fautifs) → 422 `AJUSTEMENT_NUL` / `SOLDE_NEGATIF` → 409 `SOLDE_MODIFIE_ENTRE_TEMPS` |

```json
SoldeLigne { "collaborateur_id": "…", "matricule": "COL-WEB", "nom_complet": "Sarra Khemiri", "unite_libelle": "Service Web",
  "statut": "ACTIF", "statut_libelle": "Actif", "acquis": 10.0, "reserve": 5.0, "disponible": 5.0,
  "dernier_mouvement": { "date": "…", "type_libelle": "Acquisition", "nombre": 1.5 },   // null si aucun
  "version": 3, "est_moi": false }
```

#### Imports (back-office)

| # | Méthode, chemin | Rôle | Entrée | 2xx | Erreurs |
|---|---|---|---|---|---|
| I1 | `POST /api/rh/v1/admin/imports/collaborateurs/analyses` | BO_ECRITURE | multipart `fichier`, `creer_comptes` (défaut `false`) | 200 `Analyse` (page 0 des lignes) | 413 `FICHIER_TROP_VOLUMINEUX`, 400 `FORMAT_NON_SUPPORTE`, 400 `FICHIER_TROP_COMPLEXE`, 422 `TROP_DE_LIGNES`, 422 `EN_TETES_NON_RECONNUS` |
| I2 | `POST /api/rh/v1/admin/imports/soldes/analyses` | BO_ECRITURE | multipart `fichier`, `motif_global` (facultatif ici) | 200 `Analyse` | comme I1 |
| I3 | `GET /api/rh/v1/admin/imports/analyses/{analyseId}` | BO_ECRITURE (auteur) | — | 200 `Analyse` (sans lignes) | 404 `ANALYSE_INTROUVABLE`, 409 `IMPORT_EXPIRE` |
| I4 | `GET /api/rh/v1/admin/imports/analyses/{analyseId}/lignes?page=0&taille=50&erreurs_seulement=false` | BO_ECRITURE (auteur) | `taille` 1–100 (défaut 50) | 200 page de `LigneAnalyse` | 404, 409 `IMPORT_EXPIRE` |
| I5 | `PUT /api/rh/v1/admin/imports/analyses/{analyseId}/correspondance` | BO_ECRITURE (auteur) | `{ "ligne_en_tete": 1, "colonnes": [ { "index": 0, "champ": "MATRICULE" }, { "index": 3, "champ": null } ], "creer_comptes": true, "motif_global": "…" }` (champs facultatifs sauf `colonnes` ; colonne absente = ignorée) | 200 `Analyse` (page 0) | 404, 400 `CORPS_INVALIDE`, 409 `IMPORT_DEJA_CONFIRME`, 409 `IMPORT_EXPIRE` |
| I6 | `POST /api/rh/v1/admin/imports/analyses/{analyseId}/confirmation` | BO_ECRITURE (auteur) | `{ "motif_global": "…" }` (soldes : obligatoire, 5–500 ; fiches : ignoré) | 200 `CompteRendu` | 404 → 400 `MOTIF_OBLIGATOIRE` → 409 `IMPORT_DEJA_CONFIRME` → 409 `IMPORT_EXPIRE` → 409 `IMPORT_EN_COURS` → 422 `ERREUR_DE_FICHIER` / `AUCUNE_LIGNE_IMPORTABLE` |
| I7 | `GET /api/rh/v1/admin/imports/analyses/{analyseId}/lignes-en-erreur` | BO_ECRITURE (auteur) | — | 200 `.xlsx` (`lignes-en-erreur.xlsx`) | 404, 409 `IMPORT_EXPIRE` (contenu purgé) |
| I8 | `GET /api/rh/v1/admin/imports/collaborateurs/modele` | BO_ECRITURE | — | 200 `.xlsx` (en-têtes canoniques) | — |
| I9 | `GET /api/rh/v1/admin/imports/soldes/modele` | BO_ECRITURE | — | 200 `.xlsx` (Matricule, Solde, Motif) | — |
| I10 | `GET /api/rh/v1/admin/imports/historique?type=&page=0&taille=20` | BO_LECTURE | — | 200 page de `ImportResume` (confirmés seulement) | 400 |
| I11 | `GET /api/rh/v1/admin/imports/historique/{importId}` | BO_LECTURE | — | 200 `ImportResume` | 404 `IMPORT_INTROUVABLE` |
| I12 | `GET /api/rh/v1/admin/imports/historique/{importId}/lignes?page&taille` | BO_LECTURE | — | 200 page de `{ numero_ligne, matricule, action, action_libelle, changements: [{champ_libelle, avant, apres}], erreurs_libelle }` | 404 |

```json
Analyse { "analyse_id": "…", "type_import": "COLLABORATEURS", "statut": "ANALYSEE", "expire_le": "…", "nom_fichier": "agents.xlsx",
  "creer_comptes": false, "motif_global": null, "ligne_en_tete": 1,
  "colonnes": [ { "index": 0, "en_tete": "Mat.", "champ": "MATRICULE", "champ_libelle": "Matricule", "etat": "RECONNUE" },
                { "index": 5, "en_tete": "Téléphone", "champ": null, "champ_libelle": null, "etat": "IGNOREE" } ],
  "champs_disponibles": [ { "champ": "MATRICULE", "libelle": "Matricule", "obligatoire_creation": true } ],
  "erreurs_fichier": [ { "code": "COLONNE_EN_DOUBLE", "message": "Les colonnes « Email » et « Courriel » désignent toutes deux le courriel." } ],
  "colonnes_ignorees": [ "Téléphone" ],
  "avertissements_fichier": [ { "code": "ACQUISITION_RECENTE", "message": "…" } ],
  "totaux": { "lignes": 10, "creations": 4, "mises_a_jour": 2, "inchangees": 2, "ajustements": null, "erreurs": 2, "avertissements": 1 },
  "confirmable": true,
  "lignes": { "contenu": [ LigneAnalyse ], "page": 0, "taille": 50, "total_elements": 10, "total_pages": 1 } }

LigneAnalyse { "numero": 4, "matricule": "COL-WEB", "nom_complet": "Sarra Khemiri", "action": "MISE_A_JOUR", "action_libelle": "Mise à jour",
  "erreurs": [ { "code": "UNITE_INCONNUE", "colonne": "Code unité", "valeur": "XYZ", "message": "Unité inconnue « XYZ » (colonne « Code unité »)." } ],
  "avertissements": [ { "code": "COMPTE_NON_CREE", "message": "Compte non créé : courriel absent" } ],
  "modifications": [ { "champ": "POSTE", "champ_libelle": "Poste", "avant": "Développeur", "apres": "Développeur senior" } ],
  // soldes seulement :
  "solde_actuel": 10.0, "solde_cible": 12.5, "ecart": 2.5, "motif": "Report 2025" }

CompteRendu { "import_id": "…", "type_import": "COLLABORATEURS", "creees": 4, "mises_a_jour": 2, "inchangees": 2, "rejetees": 2,
  "comptes_demandes": 3, "ajustements": null, "avertissements": [ "…" ] }

ImportResume { "import_id": "…", "type_import": "SOLDES", "type_import_libelle": "Soldes", "confirme_le": "…", "auteur_nom": "Amel Trabelsi",
  "nom_fichier": "soldes.xlsx", "creer_comptes": null, "motif_global": "Reprise des soldes au 30/11/2026", "totaux": { … } }
```

`expire_le` = `derniere_action_le` + expiration. Une analyse avec erreur de fichier a `confirmable = false`.

#### Paramètres

| # | Méthode, chemin | Rôle | Entrée | 2xx | Erreurs |
|---|---|---|---|---|---|
| P1 | `GET /api/referentiel/v1/parametres/reglages` | BO_LECTURE | — | 200 `[{ "cle": "jours_acquis_par_mois", "libelle": "Jours acquis par mois", "valeur": 1.5, "min": 0, "max": 5, "pas": 0.5, "unite": "JOURS", "defaut": 1.5, "version": 0 }]` | — |
| P2 | `PUT /api/referentiel/v1/parametres/reglages/jours_acquis_par_mois` | BO_ECRITURE | `{ "valeur": 2, "version": 0 }` | 200 `Reglage` | 404 `REGLAGE_INCONNU`, 400 `REGLAGE_HORS_BORNES`, 409 `PARAMETRE_MODIFIE_ENTRE_TEMPS` |
| P3 | `GET /api/referentiel/v1/parametres/reglages/journal?page&taille` | BO_LECTURE | — | 200 `PageJournal` | — |
| P4 | `GET /api/referentiel/v1/parametres/jours-feries?annee=2027` | BO_LECTURE | — | 200 `[{ "id", "date", "libelle", "actif", "version" }]` triés par date | 400 |
| P5 | `GET /api/referentiel/v1/parametres/jours-feries/actifs?annee=2027` | AUTH | — | 200 `[{ "date", "libelle" }]` | 400 |
| P6 | `POST /api/referentiel/v1/parametres/jours-feries` | BO_ECRITURE | `{ "date": "2027-03-20", "libelle": "Aïd el-Fitr" }` | 201 | 400 `CORPS_INVALIDE` (date absente, libellé hors 2–80), 400 `DATE_INVALIDE` (hors année − 1 … + 5), 409 `JOUR_FERIE_EN_DOUBLE` |
| P7 | `PUT /api/referentiel/v1/parametres/jours-feries/{id}` | BO_ECRITURE | `{ "date"?, "libelle"?, "version" }` | 200 | 404 `JOUR_FERIE_INTROUVABLE`, 400, 409 `JOUR_FERIE_EN_DOUBLE`, 409 `PARAMETRE_MODIFIE_ENTRE_TEMPS` |
| P8 | `POST …/jours-feries/{id}/desactiver`, `POST …/jours-feries/{id}/reactiver` | BO_ECRITURE | `{ "version" }` | 200 | 404, 400, 409 |
| P9 | `DELETE …/jours-feries/{id}?version=` | BO_ECRITURE | — | 204 | 404, 409 |
| P10 | `GET …/jours-feries/journal?page&taille` | BO_LECTURE | — | 200 `PageJournal` | — |
| P11 | `POST` / `DELETE` `…/referentiels/TYPE_MOUVEMENT_SOLDE/valeurs[/{code}]` | BO_ECRITURE | — | — | 405 `PARAMETRE_COMPORTEMENT` |
| P12 | `GET /api/referentiel/v1/parametres/conges` | BO_LECTURE | — | 200 `{ "debut_acquisitions_le": "2026-12-01", "mois_courant": "2026-11", "jours_mois_courant": null }` | — |

#### Interne (jamais routé par la gateway, en-tête `X-Internal-Token`)

| # | Méthode, chemin | Service | Entrée | 2xx | Erreurs |
|---|---|---|---|---|---|
| X1 | `POST /internal/referentiel/conges/acquisitions/{mois}` | svc-referentiel-rh | `mois` `AAAA-MM` | 200 `{ "mois": "2026-12", "jours_par_mois": 1.5, "eligibles": 30, "creees": 30, "deja_faites": 0, "ignore": false }` (`ignore=true` si mois avant le début des acquisitions ou N = 0) | 403 jeton, 400 `CORPS_INVALIDE` |
| X2 | `POST /internal/users/identifiants-existants` | svc-identite-acces | `{ "identifiants": ["COL-X", …] }` (≤ 5 000) | 200 `{ "existants": ["col-x"] }` (minuscules) | 403 jeton, 400 |

### A.3 Matrice des rôles (G4)

`200` = autorisé (sous réserve des règles), `403` = refusé par le rôle ou l'identité, `401` sans JWT partout.
CS-MOB = responsable d'un autre nœud que COL-WEB.

| Endpoint | COL-WEB | CS-WEB | CS-MOB | RH02 | DRH01 | ADM01 | DG01 |
|---|---|---|---|---|---|---|---|
| A1, A2, A3 (les siens) | 200 | 200 | 200 | 200 | 200 | 200 | 200 |
| B1, B2, B3, B4, B5 | 403 | 403 | 403 | 200 | 200 | 200 | 200 |
| B6 ajustement de COL-WEB | 403 | 403 | 403 | 200 | 200 | 200 | 403 |
| B6 sur sa propre fiche | — | — | — | 403 `AUTO_AJUSTEMENT_INTERDIT` | 403 | 403 | 403 |
| D3 son congé approuvé | 403 | — | — | — | — | — | — |
| D4 congé approuvé de COL-WEB | 403 | 403 | 403 | 200 | 200 | 200 | 403 |
| D4 son propre congé | — | — | — | 403 | 403 | 403 | 403 |
| D5 demande `ANNUEL` de COL-WEB | 200 (demandeur, sans champs du décideur) | 200 (champs si `EN_VALIDATION_SUPERIEUR`) | 403 | 200 (champs si `EN_VALIDATION_RRH`) | 200 (idem) | 200 (idem) | 200 (sans champs) |
| I1, I2, I5, I6, I7, I8, I9 | 403 | 403 | 403 | 200 (ses analyses) | 200 (ses analyses) | 200 (ses analyses) | 403 |
| I3, I4, I5, I6, I7 sur l'analyse d'un autre | 403 | 403 | 403 | 404 `ANALYSE_INTROUVABLE` | 404 | 404 | 403 |
| I10, I11, I12 | 403 | 403 | 403 | 200 | 200 | 200 | 200 |
| P1, P3, P4, P10, P12 | 403 | 403 | 403 | 200 | 200 | 200 | 200 |
| P2, P6 à P9 | 403 | 403 | 403 | 200 | 200 | 200 | 403 |
| P5 | 200 | 200 | 200 | 200 | 200 | 200 | 200 |
| X1, X2 | non routés par la gateway (404 `ROUTE_INCONNUE` ou aucune route) ; sans jeton interne → 403 | | | | | | |
