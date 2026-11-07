# ADR-UAT-LOT3 — Paramétrage des référentiels (D-R01 à D-R10)

| | |
|---|---|
| **Statut** | proposé (06/11/2026), à valider par l'utilisateur (critère du ticket 3A) |
| **Date** | 2026-11-06 |
| **Module** | Transverse — écran « Paramètres » (recette UAT acheteur, lot 3) |
| **Tickets** | 3A (cet ADR), 3B (référentiel), 3C (évaluation), 3D (présence), 3E (web), 3F (mobile) |
| **Références** | `docs/sirh_basefonctionnelle.md` § 3.5 (D-R01 à D-R10), C-PAR-01, C-L-02, § 4.2, D-E04, D-E13, D-P19 ; `docs/jira/fiche_lot3_uat.md` ; `ADR-UAT-lot2-fiches-detail.md` ; `ADR-M07-alertes-ecart.md` |

---

## Contexte

Le client veut une application paramétrable (« not just hardcoded list »). Règle tranchée (D-R01) : on paramètre les
**données** (valeurs métier : création, modification, désactivation, suppression si jamais utilisées) et seulement
l'**habillage** des codes de **comportement** (statuts, types pilotés par une règle : libellé, aide, couleur, ordre).
Décisions utilisateur du 06/11/2026 : RH et ADMIN ont les mêmes droits d'écriture, DIRECTION lit tout ; toute action à
effet (désactiver, supprimer…) est confirmée côté front.

État vérifié dans le code au 06/11/2026 :

| Élément | Stockage actuel | Contrainte en base |
|---|---|---|
| Familles métier | table `rh_famille_metier` (`code` PK, `libelle`, `actif`, `systeme`), CRUD `FamilleMetierController`, seed `FamilleMetierSeedRunner` ; pas d'ordre, pas de version, pas de journal | — |
| Niveau de séniorité | `rh_collaborateur.niveau_seniorite` **varchar(32)**, validé par l'enum `NiveauSeniorite` ; copié dans `evaluation.niveau_seniorite` (32), `technical_template.niveau_seniorite` (50) ; catalogue recopié en dur dans `svc-evaluation` (`NiveauSenioriteCodes`) | aucune |
| Type de congé | clé `type_conge` du **jsonb** `rh_demande_administrative.contenu`, validée par l'enum `TypeConge` (pièce jointe : `MALADIE`, `MATERNITE`) | aucune |
| Type de document | `rh_demande_document_administratif.type_document` `@Enumerated` varchar(40) ; SLA = enum + surcharge `referentiel.evenements.document-sla-heures-par-type.*` ; SLA copié dans la demande (`delai_sla_heures`) à la création | CHECK recréé par `TypeDocumentConstraintRunner` |
| Niveau d'alerte d'écart | enum `NiveauEcart` (`ALIGNE < ORANGE < ROUGE < URGENTE`, ordre de déclaration utilisé par `compareTo`, `max`, `estAlerte`) ; seuils constants dans `EcartEtoiles` ; colonnes `niveau_detection`, `niveau_courant`, `niveau_max_notifie` (alerte), `niveau_detection`, `niveau_actuel` (ligne), `niveau_avant`, `niveau_apres` (historique), toutes **varchar(20)** | CHECK recréés par `AlerteEcartConstraintRunner` |
| Conclusion d'alerte | enum `ConclusionAlerteEcart`, colonnes `conclusion` varchar(40) (alerte, historique) | CHECK (même runner) |
| Compteurs par niveau | `AlerteEcartOuvertesParNiveau` à clés fixes `ORANGE`, `ROUGE`, `URGENTE` | — |
| Réglages | `evaluation_parametres` (ligne unique : `duree_campagne_jours`, sans version) ; seuil d'insuffisance = propriété `evaluation.alert.seuil-insuffisant=3` ; seuil des pointages manuels = propriété ; délai de grâce des retards = propriété (lot 2) ; rayons par défaut en dur | — |
| Statuts de workflow | enums Java, CHECK générés par Hibernate ou recréés par runner (`PointageStatutConstraintRunner`, `HeuresSupStatutConstraintRunner`, `CampagneTypeConstraintRunner`…) ; libellés en dur dans le web et le mobile (lot 1 : une table par canal, alimentée par le code) | CHECK |
| Cache | Caffeine dans le référentiel seulement (`CacheConfig`, une seule spec 5 min pour tous les caches) ; aucun cache dans évaluation / présence | — |

## Décision

### 1. Un paramétrage par service propriétaire du code

Chaque service garde les paramètres des codes qu'il stocke et dont il applique les règles ; pas de service de
paramétrage central.

| Service | Valeurs métier (D-R01) | Codes de comportement (libellé seul) | Réglages (D-R06) |
|---|---|---|---|
| `svc-referentiel-rh` | `FAMILLE_METIER`, `NIVEAU_SENIORITE`, `TYPE_DOCUMENT`, `TYPE_CONGE` | `STATUT_DEMANDE`, `STATUT_DOCUMENT`, `STATUT_PLAINTE`, `STATUT_FORMATION`, `STATUT_COLLABORATEUR`, `TYPE_DEMANDE`, `TYPE_PLAINTE`, `PROFIL_ACCES` | — |
| `svc-evaluation` | `NIVEAU_ALERTE`, `CONCLUSION_ALERTE` | `STATUT_EVALUATION`, `STATUT_CAMPAGNE`, `STATUT_ALERTE`, `TYPE_QUESTION` (activable sauf `RATING`), `ECHELLE_ETOILES` (codes `1` à `5`), `APPRECIATION`, `ETAT_ECART` (`ALIGNE` seul) | `duree_campagne_jours` (existant), `seuil_insuffisance` |
| `svc-presence` | — | `STATUT_POINTAGE` (dont les motifs `REJETE_*` : libellé RH + aide), `STATUT_DEMANDE_MANUELLE`, `STATUT_HEURES_SUP` | `seuil_pointages_manuels_mois`, `delai_grace_retard_minutes`, `rayon_defaut_exterieur_metres`, `rayon_defaut_interieur_metres` |

Raison : la détection d'une alerte lit ses seuils, la création d'une demande lit son type, le retard lit son délai de
grâce — **dans le même service, la même base, sans appel réseau**. Un service central ferait dépendre chaque
création d'un appel synchrone (et d'une panne de plus).

### 2. Modèle de données (une table de valeurs, un journal, une ligne de réglages par service)

`<p>` = `rh` (référentiel), `evaluation`, `presence`.

**`<p>_parametre_valeur`**

| Colonne | Type | Remarque |
|---|---|---|
| `id` | uuid PK | |
| `referentiel` | varchar(40), non nul | ex. `TYPE_DOCUMENT` |
| `code` | varchar(40), non nul | immuable (D-R02.a) |
| `nature` | varchar(12), non nul | `VALEUR` ou `COMPORTEMENT` (enum Java fixe, CHECK Hibernate) |
| `libelle` | varchar(80), non nul | |
| `libelle_normalise` | varchar(80), non nul | minuscules, sans accents ni espaces doubles (unicité D-R02.b) |
| `description` | varchar(300) | aide |
| `couleur` | varchar(16) | code de palette (§ 5), null = `NEUTRE` |
| `ordre` | int, non nul | |
| `actif` | boolean, non nul | |
| `systeme` | boolean, non nul | cadenas : jamais supprimée ; désactivation interdite |
| `premiere_utilisation_le` | timestamptz | **marque collante** « déjà utilisée » (§ 6) |
| `version` | bigint, non nul | `@Version` |
| `cree_le`, `modifie_le`, `modifie_par_compte_id` | | |
| Référentiel seulement : `sla_heures` (int), `piece_jointe_obligatoire` (bool), `piece_jointe_verrouillee` (bool) | | SLA des documents ; pièce jointe des congés (`MALADIE`, `MATERNITE` verrouillées à vrai) |
| Évaluation seulement : `seuil_ecart` (int) | | niveaux d'alerte, 1 à 4 |

Index : unique (`referentiel`, `code`) ; unique (`referentiel`, `libelle_normalise`) ; évaluation : **index unique
partiel** `(seuil_ecart) WHERE referentiel = 'NIVEAU_ALERTE' AND actif` (créé par le runner, Hibernate ne sait pas
faire un index partiel) — il garantit D-R04.a même en cas de double clic concurrent.

**`<p>_parametre_journal`** (D-R02.g, C-L-02) : `id`, `referentiel`, `code`, `action` (`CREATION`, `MODIFICATION`,
`DESACTIVATION`, `REACTIVATION`, `SUPPRESSION`, `ORDRE`, `REGLAGE` — enum fixe), `avant_json`, `apres_json` (text,
sérialisés par le service), `auteur_compte_id`, `auteur_nom` (depuis le JWT), `horodatage`. Toutes colonnes
`updatable = false`, aucun endpoint d'écriture ni de suppression ; écrit dans la **même transaction** que la
modification. Index (`referentiel`, `horodatage` décroissant). Conservation : **aucune purge** (≥ 5 ans ; une purge
au-delà de 5 ans sera un ticket séparé).

**Réglages** : `evaluation_parametres` (existante) + `seuil_insuffisance` (Integer, null lu comme 3) + `version` ;
nouvelle `presence_parametres` (ligne unique `id = 1`, colonnes du § 1, `version`, `modifie_le`, `modifie_par`).
Bornes dans le code (D-R06) : seuil d'insuffisance 1–5, pointages manuels 1–31, délai de grâce 0–60, rayons 10–100,
durée de campagne 1–120.

### 3. Conversion des enums stockés en codes référencés

| Code | Avant | Après | Contrainte CHECK | Ce qui reste en Java |
|---|---|---|---|---|
| `TYPE_DOCUMENT` | `@Enumerated TypeDocumentAdministratifDemandeRh` | `String typeDocument` validé contre la table (existe + actif) ; SLA lu dans la table et **copié** dans la demande (C-M02-02, inchangé) | **supprimée** : le runner fait `DROP CONSTRAINT IF EXISTS rh_demande_document_administratif_type_document_check` ; `TypeDocumentConstraintRunner` est retiré | l'enum devient la liste des **valeurs initiales** (classe `ValeursInitialesReferentiel`) ; la propriété de surcharge du SLA n'est lue qu'au premier amorçage |
| `TYPE_CONGE` | `TypeConge.parse` sur `contenu.type_conge` | lecture dans la table : inconnu → 400 `TYPE_CONGE_INVALIDE` (code existant), inactif → **422 `TYPE_CONGE_INACTIF`**, pièce jointe exigée selon `piece_jointe_obligatoire` → 422 `CERTIFICAT_OBLIGATOIRE` | aucune (jsonb) | valeurs initiales ; `ANNUEL` système ; `MALADIE`, `MATERNITE` : `piece_jointe_verrouillee = true` |
| `NIVEAU_SENIORITE` | enum `NiveauSeniorite` (+ `NiveauSenioriteCodes` dans l'évaluation) | validé contre la table (actif à l'écriture) ; alias historiques (`CONFIRMED` → `CONFIRME`…) gardés dans une petite table d'alias du service | aucune | valeurs initiales |
| `FAMILLE_METIER` | table `rh_famille_metier` | rangée dans `rh_parametre_valeur` (§ 8) ; `FamilleMetierController` devient une façade sur le service générique, puis disparaît au lot suivant | — | — |
| `NIVEAU_ALERTE` | enum `NiveauEcart` | `String` dans les 7 colonnes ; objet de valeur `NiveauAlerte(code, libelle, couleur, seuil, actif)` lu dans l'instantané (§ 4) ; `ALIGNE` reste une **constante** (« pas d'écart »), hors table des niveaux | **supprimées** pour les 7 colonnes `niveau_*` : retirées de `AlerteEcartConstraintRunner.CIBLES` et ajoutées à une liste `A_SUPPRIMER` (`DROP CONSTRAINT IF EXISTS`) | `ALIGNE` ; les CHECK de `statut`, `sens_*`, `type_evenement` restent |
| `CONCLUSION_ALERTE` | enum `ConclusionAlerteEcart` | `String`, validé (existe + actif) à la clôture → 422 `CONCLUSION_INACTIVE` | **supprimées** (2 colonnes, même mécanisme) | `AUTRE` système (repli) |

Restent des **enums Java avec CHECK** (codes de comportement) : tous les statuts, `TypeDemandeAdministrativeRh`,
`TypePlainteRh`, `ProfilAccesCollaborateur`, `QuestionType`, `CouleurAlerteEvaluationRh`, `AppreciationEvaluationRh`,
`PointageStatut`, `StatutDemandeManuelle`, `StatutHeuresSup`, `SensEcart`, `EvenementAlerteEcart`. Leurs runners
restent inchangés ; leurs lignes `COMPORTEMENT` ne portent que l'habillage.

**Largeur des colonnes** : un code fait jusqu'à 40 caractères ; `ddl-auto=update` n'élargit pas une colonne. Le runner
de chaque service passe en `varchar(40)` (idempotent : `ALTER COLUMN … TYPE varchar(40)` seulement si plus étroite,
lu dans `information_schema.columns`) : les 7 colonnes `niveau_*` (20) de l'évaluation,
`rh_collaborateur.niveau_seniorite` (32), `evaluation.niveau_seniorite` (32).

**Calcul D-R04 (classe pure, remplace `EcartEtoiles.niveau(int)`)** :
`niveau(ecart, niveauxActifs)` = le niveau actif de **plus grand seuil ≤ écart**, sinon `ALIGNE`. Toute comparaison
de niveaux (`max`, « renotifier si le niveau dépasse celui déjà notifié », tri de la liste) compare les **seuils
actuels** des codes (un code désactivé garde son seuil pour comparer ; un code utilisé ne peut pas être supprimé,
donc tout code stocké se résout). Les alertes existantes ne sont pas recalculées (D-R04.c). `AlerteEcartOuvertesParNiveau`
devient une liste `[{ "code", "libelle", "couleur", "seuil", "nombre" }]` + `total` (changement de contrat, adapté par
3E), le paramètre `niveau` des listes devient un `String`. Titre de notification = libellé courant.

### 4. Cache et prise en compte en moins d'une minute (D-R02.j)

- Chaque service tient un **instantané immuable** de tous ses paramètres (quelques centaines de lignes) dans un cache
  Caffeine **dédié** : une clé, `refreshAfterWrite` 20 s, `expireAfterWrite` **30 s**. Toute lecture métier
  (détection, création de demande, libellés des réponses) passe par cet instantané.
- **Écriture** : invalidation locale **après commit** (`TransactionSynchronization.afterCommit`).
- **Plusieurs instances** : aucune diffusion d'invalidation ; une autre instance voit le changement au plus tard à
  l'expiration, **≤ 30 s < 1 min**. Pas de Redis ni de Kafka pour ce besoin.
- Référentiel : le `CacheConfig` actuel applique une seule spec (5 min) à tous les caches ; le cache `parametres` est
  enregistré à part (`registerCustomCache`) avec 30 s. Les objets mis en cache 5 min
  (`collaborateurs-par-id`, `collaborateurs-par-matricule`) **ne contiennent que des codes** : le libellé (famille,
  niveau…) est ajouté à chaque réponse depuis l'instantané, après le cache. Sinon un renommage resterait invisible
  5 min.
- `svc-evaluation` et `svc-presence` ajoutent `spring-boot-starter-cache` + `caffeine` (déjà prévu au lot 2).
- Réponses des endpoints de libellés : `Cache-Control: no-cache` + `ETag` (empreinte de l'instantané) ; le client
  revalide (304) sans retélécharger.

### 5. Règles communes (service `ParametreService`, même code dans les trois services)

- **Code** (D-R02.a) : depuis le libellé : majuscules, accents retirés, tout caractère hors `[A-Z0-9]` → `_`, `_`
  consécutifs fusionnés, bords retirés, 40 caractères max, 2 min (sinon préfixe `VAL_`) ; si pris, suffixe `_2`,
  `_3`… Généré par le serveur, jamais saisi.
- **Libellé** : 2 à 80 caractères, pas que des espaces → 400 `LIBELLE_INVALIDE` ; doublon (normalisé) dans le
  référentiel → 400 `LIBELLE_EN_DOUBLE`. Description ≤ 300 → 400 `DESCRIPTION_TROP_LONGUE`.
- **Palette** (8 codes, rendu WCAG AA à la charge des fronts) : `NEUTRE`, `BLEU`, `CYAN`, `VERT`, `AMBRE`, `ORANGE`,
  `ROUGE`, `VIOLET`. Autre valeur → 400 `COULEUR_INVALIDE`.
- **Version** (D-R02.h) : chaque écriture porte `version` ; différente de la base, ou `OptimisticLockException` →
  **409 `PARAMETRE_MODIFIE_ENTRE_TEMPS`**.
- **Système** : désactivation ou suppression → **409 `PARAMETRE_SYSTEME`**.
- **Comportement** : `POST` (création) et `DELETE` sur un référentiel `COMPORTEMENT` → **405
  `PARAMETRE_COMPORTEMENT`** ; désactivation → 405 sauf `TYPE_QUESTION` (hors `RATING` : 409 `PARAMETRE_SYSTEME`).
- **Règles propres** : `TYPE_DOCUMENT` SLA 1–720 → 400 `SLA_INVALIDE` ; `TYPE_CONGE` pièce jointe verrouillée mise à
  faux → **422 `PIECE_JOINTE_VERROUILLEE`** ; `NIVEAU_ALERTE` seuil hors 1–4 → 400 `SEUIL_INVALIDE`, seuil déjà pris
  par un niveau actif → 400 `SEUIL_EN_DOUBLE`, 5e niveau actif → 422 `NIVEAUX_ACTIFS_MAX`, désactivation du dernier
  actif → 422 `NIVEAU_ACTIF_MIN` ; `TYPE_QUESTION` désactivé : ajout d'une question de ce type → 422
  `TYPE_QUESTION_INACTIF`.
- **Droits** (décision utilisateur) : écriture `hasAnyRole('RH','ADMIN')` (expressions `BACKOFFICE_ECRITURE`
  existantes) ; lecture complète `BACKOFFICE_LECTURE` (RH, DIRECTION, ADMIN) ; libellés actifs pour tout utilisateur
  authentifié. Auteur du journal = JWT.

### 6. Suppression et « déjà utilisée » (409 `PARAMETRE_UTILISE`) sans appel entre services

Une valeur se supprime seulement si : non système, **`premiere_utilisation_le` nul** et **0 utilisation locale**.

- **Marque collante** : posée une fois, jamais retirée, à la première utilisation, dans la transaction qui utilise :
  `UPDATE <p>_parametre_valeur SET premiere_utilisation_le = now() WHERE referentiel = ? AND code = ? AND premiere_utilisation_le IS NULL`.
  Référentiel : création ou modification de fiche (famille, niveau), de demande de congé, de document. Évaluation :
  détection d'alerte (niveau), clôture (conclusion).
- **Utilisations par un autre service** (familles et niveaux ciblés par un template technique ou figés dans une
  évaluation) : `svc-evaluation` appelle le référentiel, **dans le sens d'appel qui existe déjà** :
  `POST /internal/referentiel/parametres/utiliser` `{ "references": [{ "referentiel": "NIVEAU_SENIORITE", "code": "SENIOR" }] }`
  → 200 `{ "valides": [...], "inactifs": [...], "inconnus": [...] }` ; il **valide** les codes ciblés (template :
  inconnu → 400, inactif → 422 `VALEUR_INACTIVE`) et **pose la marque**. Appelé à la création / modification d'un
  template technique (remplace `NiveauSenioriteCodes`). Les évaluations copient les codes des fiches, déjà marqués.
  Aucun appel référentiel → évaluation : pas de dépendance circulaire.
- **Valeurs initiales** (amorçage) : marquées utilisées d'office (catalogue historique, on ne sait pas ce que
  l'évaluation en a fait) ; elles restent désactivables.
- **Nombre d'utilisations** affiché (et renvoyé dans la 409) = comptage **local** (requêtes `count` par code ;
  jsonb `contenu->>'type_conge'` pour les congés) ; la réponse porte aussi `deja_utilisee: true`. 409 :
  `{ "code": "PARAMETRE_UTILISE", "message": "…utilisée 3 fois, vous pouvez la désactiver", "utilisations": 3 }`.
- Course résiduelle (une valeur supprimée au moment exact où une demande la prend) : la suppression verrouille la
  ligne (`PESSIMISTIC_WRITE`) et la marque est posée par un `UPDATE` sur cette même ligne ; l'une des deux
  transactions attend l'autre. En dernier recours, le rapprochement du § 8 recrée tout code orphelin.

### 7. Contrat d'API (identique dans les trois services, seule la base change)

| Service | Base admin (`B`) | Libellés (`L`) — route gateway existante |
|---|---|---|
| référentiel | `/api/referentiel/v1/parametres` | `/api/referentiel/v1/parametres/libelles` |
| évaluation | `/api/rh/v1/admin/evaluations/parametres` (contrôleur existant, `GET` / `PUT` racine conservés) | `/api/rh/v1/evaluations/parametres/libelles` |
| présence | `/api/rh/v1/admin/presence/parametres` | `/api/rh/v1/presence/parametres/libelles` |

**Aucune route gateway nouvelle** (préfixes `/api/referentiel/**`, `/api/rh/v1/evaluations/**`,
`/api/rh/v1/admin/evaluations/**`, `/api/rh/v1/presence/**`, `/api/rh/v1/admin/presence/**` déjà routés).
JSON `snake_case` dans les trois services pour ces endpoints (contrat unique pour les fronts, y compris dans
l'évaluation).

| Méthode et chemin | `@PreAuthorize` | Corps | Réponse | Erreurs |
|---|---|---|---|---|
| `GET L?actifs=true` | `isAuthenticated()` | — | `Libelles` | — |
| `GET B/referentiels` | `BACKOFFICE_LECTURE` | — | `DescripteurReferentiel[]` | — |
| `GET B/referentiels/{ref}/valeurs` | `BACKOFFICE_LECTURE` | — | `Valeur[]` (actives et inactives, avec `utilisations`) | 404 `REFERENTIEL_INCONNU` |
| `POST B/referentiels/{ref}/valeurs` | `BACKOFFICE_ECRITURE` | `{ libelle, description?, couleur?, ordre?, sla_heures?, piece_jointe_obligatoire?, seuil_ecart? }` | 201 `Valeur` | 400, 405, 422 |
| `PUT B/referentiels/{ref}/valeurs/{code}` | `BACKOFFICE_ECRITURE` | même corps + `version` | 200 `Valeur` | 400, 404, 409, 422 |
| `POST B/referentiels/{ref}/valeurs/{code}/desactiver` | `BACKOFFICE_ECRITURE` | `{ version }` | 200 `Valeur` | 404, 405, 409, 422 |
| `POST B/referentiels/{ref}/valeurs/{code}/reactiver` | `BACKOFFICE_ECRITURE` | `{ version }` | 200 `Valeur` | 404, 409, 422 (5e niveau actif, seuil pris) |
| `PUT B/referentiels/{ref}/ordre` | `BACKOFFICE_ECRITURE` | `{ codes: [...] }` (tous les codes du référentiel) | 200 `Valeur[]` | 400 `ORDRE_INCOMPLET` |
| `DELETE B/referentiels/{ref}/valeurs/{code}?version=` | `BACKOFFICE_ECRITURE` | — | 204 | 404, 405, 409 `PARAMETRE_UTILISE` / `PARAMETRE_SYSTEME` / `PARAMETRE_MODIFIE_ENTRE_TEMPS` |
| `GET B/referentiels/{ref}/journal?page=0&taille=20` | `BACKOFFICE_LECTURE` | — | `{ contenu: [Journal], page, taille, total }` | 404 |
| `GET B/reglages` | `BACKOFFICE_LECTURE` | — | `Reglage[]` | — |
| `PUT B/reglages/{cle}` | `BACKOFFICE_ECRITURE` | `{ valeur, version }` | 200 `Reglage` | 400 `REGLAGE_HORS_BORNES`, 404, 409 |
| `GET B/reglages/journal?page&taille` | `BACKOFFICE_LECTURE` | — | page de `Journal` | — |
| `POST /internal/referentiel/parametres/utiliser` | jeton interne | § 6 | § 6 | 401 |

```json
// Libelles (GET L) — un seul appel par service, fusionné par le front
{ "version": "W/\"9f2c…\"",
  "referentiels": {
    "TYPE_DOCUMENT": [{ "code": "ATTESTATION_TRAVAIL", "libelle": "Attestation de travail", "description": null,
                        "couleur": "BLEU", "ordre": 1, "actif": true, "sla_heures": 24 }],
    "STATUT_DEMANDE": [{ "code": "EN_VALIDATION_RRH", "libelle": "En attente de la RH", "couleur": "AMBRE", "ordre": 3, "actif": true }] } }
// Valeur (admin)
{ "code", "libelle", "description", "couleur", "ordre", "actif", "systeme", "nature", "version",
  "utilisations": 3, "deja_utilisee": true, "sla_heures"?, "piece_jointe_obligatoire"?, "piece_jointe_verrouillee"?, "seuil_ecart"? }
// DescripteurReferentiel — l'écran Paramètres se construit sans liste figée
{ "referentiel": "TYPE_CONGE", "libelle": "Types de congé", "domaine": "DEMANDES", "nature": "VALEUR",
  "operations": { "creer": true, "supprimer": true, "desactiver": true },
  "attributs": [{ "nom": "piece_jointe_obligatoire", "type": "BOOLEEN" }] }
// Reglage
{ "cle": "delai_grace_retard_minutes", "libelle": "Délai de grâce des retards", "valeur": 0, "min": 0, "max": 60,
  "unite": "MINUTES", "defaut": 0, "version": 4 }
// Journal
{ "action": "MODIFICATION", "code": "EN_VALIDATION_RRH", "auteur_nom": "Amel Trabelsi", "horodatage", "avant": {…}, "apres": {…} }
```

- `domaine` ∈ `ORGANISATION`, `DEMANDES`, `DOCUMENTS`, `PLAINTES_FORMATIONS`, `EVALUATIONS`, `POINTAGE` (D-R10).
- **Lecture non back-office** (collaborateur, responsable) : `GET L` ne renvoie que les valeurs actives des
  référentiels utiles à la saisie et à l'affichage mobile (liste blanche par service : types de congé, types de
  document, statuts, types de demande / plainte, échelle des étoiles, appréciations) ; jamais `description` vide
  remplie, jamais de compteur. Back-office : tout, actifs et inactifs (`actifs=false` par défaut).
- **Réponses métier** : chaque objet renvoyé par les API métier porte déjà ses `*_libelle` (lot 1) ; leur source
  devient l'instantané (§ 4). Un objet d'un type désactivé s'affiche donc avec son libellé même si `GET L?actifs=true`
  ne le propose plus.
- **Fronts** : la table de libellés unique du lot 1 (web, mobile) est chargée depuis les trois `GET L` à la connexion
  et revalidée toutes les 60 s et à l'ouverture de chaque formulaire ; code absent → « Valeur non reconnue ». Aucune
  liste de secours figée (3F) ; échec à l'ouverture d'un formulaire → erreur + « Réessayer ».
- **Confirmation** (décision utilisateur) : désactiver, supprimer, réordonner en masse et modifier un seuil d'alerte
  passent par la boîte de confirmation du lot 2 (ADR-UAT-LOT2 § 8) ; un seuil à 1 affiche en plus l'avertissement
  « ce réglage peut multiplier les alertes » (D-R04.d). 409 affiché avec son message et, pour `PARAMETRE_UTILISE`,
  le bouton « Désactiver ».

### 8. Amorçage et migration des données sans Flyway (runners idempotents)

Un `ParametresAmorceRunner` par service (`ApplicationRunner`, ordre : avant tout runner qui lit les paramètres),
rejouable à chaque démarrage :

1. Élargir les colonnes (§ 3) et supprimer les CHECK des colonnes converties (`DROP CONSTRAINT IF EXISTS`).
2. Créer l'index partiel (évaluation), `CREATE UNIQUE INDEX IF NOT EXISTS`.
3. **Valeurs initiales** : `INSERT … ON CONFLICT (referentiel, code) DO NOTHING` à partir des enums actuels, avec les
   libellés français du lot 1 (une seule source : les tables de libellés serveur écrites au lot 1), les SLA (enum +
   surcharge de propriété), la pièce jointe, les seuils `ORANGE` 2 / `ROUGE` 3 / `URGENTE` 4, `systeme` (`ANNUEL`,
   `AUTRE` des conclusions, `RATING`, tous les codes de comportement), `premiere_utilisation_le = now()` pour les
   valeurs métier initiales. Une ligne déjà présente n'est **jamais** réécrite (la RH a pu la modifier).
4. **Codes de comportement** : une ligne par valeur d'enum manquante (nouveau statut ajouté au code → apparaît
   automatiquement) ; une ligne dont le code n'existe plus dans l'enum est laissée (journalisée en `WARN`).
5. **Familles** : `INSERT INTO rh_parametre_valeur (…) SELECT … FROM rh_famille_metier ON CONFLICT DO NOTHING`
   (code, libellé, actif, système conservés ; ordre = rang alphabétique). `rh_famille_metier` n'est plus lue ni
   supprimée (suppression au lot suivant, après vérification). `FamilleMetierSeedRunner` est retiré.
6. **Rapprochement des orphelins** (critère « 0 objet dont le code n'existe pas ») : `SELECT DISTINCT` des codes
   stockés (`type_document`, `contenu->>'type_conge'`, `niveau_seniorite`, `famille_metier_code`, colonnes
   `niveau_*` et `conclusion`) absents de la table → insérés **inactifs**, libellé = code mis en forme, marqués
   utilisés, journalisés (`auteur_nom` = « Système (automatique) »).
7. Lignes existantes d'`evaluation_parametres` : `UPDATE … SET version = 0 WHERE version IS NULL` (une colonne
   `@Version` ajoutée à une table existante est nullable sous `ddl-auto=update`). Même règle pour toute colonne non
   nulle ajoutée à une table existante : déclarée nullable, remplie par le runner.

Ordre de déploiement : un service peut être déployé seul ; l'évaluation tolère un référentiel ancien (endpoint
`utiliser` absent → 503 à la création d'un template seulement). Retour arrière : les CHECK supprimés ne gênent pas
l'ancien code ; un code créé par la RH puis relu par l'ancien enum ferait échouer la lecture → **pas de retour
arrière après la première création** (sauvegarde `pg_dump` des quatre bases avant déploiement, consigne du guide).

### 9. Propagation des familles et niveaux vers `svc-evaluation` (D-R07)

- À la création d'une évaluation, le snapshot garde **code et libellé** : colonnes `famille_metier_libelle`,
  `niveau_seniorite_libelle` ajoutées à `evaluation` ; l'endpoint interne `actifs-evaluation` renvoie
  `familleMetierLibelle`, `niveauSenioriteLibelle` (lus dans l'instantané du référentiel).
- Affichage : libellé **courant** lu dans `GET /api/referentiel/v1/parametres/libelles` par les fronts (D-R02.c),
  libellé du snapshot en repli. `/internal/referentiel/libelles-profil` (lot 2) lit désormais la table.
- Templates techniques : ciblage validé et marqué par `POST /internal/referentiel/parametres/utiliser` (§ 6) ;
  l'écran de ciblage (web) propose les valeurs actives du référentiel. La résolution D-E13 compare des codes : une
  valeur désactivée continue de fonctionner pour les fiches qui la portent.
- Aucune réplication par événement : le libellé change rarement, il est lu à la demande (cache 30 s).

## Alternatives écartées

- **Service de paramétrage unique** (nouveau `svc-parametrage`) : appel synchrone à chaque détection / création,
  panne de plus, nouveau déploiement.
- **Tout dans le référentiel** : l'évaluation et la présence dépendraient du référentiel pour leurs propres règles
  (seuils, délai de grâce).
- **Une table par référentiel** (une quinzaine) : quinze entités, quinze CRUD ; la table générique + colonnes
  spécifiques couvre tout.
- **Attributs en jsonb** : validation et index partiel impossibles en SQL.
- **Garder les CHECK et les régénérer depuis la table** : une création de valeur devrait modifier le schéma à chaud
  (DDL en production, verrous).
- **Clé étrangère vers la table de paramètres** : demanderait une colonne `referentiel` dans chaque table métier ;
  la validation applicative + le rapprochement suffisent.
- **Invalidation par Kafka ou Redis** : le TTL de 30 s tient déjà la minute.
- **Comptage des utilisations par appel référentiel → évaluation** : dépendance circulaire ; remplacé par la marque
  collante.
- **Un endpoint de libellés agrégé** (le référentiel appelle les deux autres) : couplage et panne en cascade pour
  économiser deux requêtes parallèles côté client.

## Conséquences

- Trois tables nouvelles par service (valeurs, journal ; `presence_parametres`), colonnes ajoutées à
  `evaluation_parametres` et `evaluation`, colonnes élargies, CHECK supprimés pour 9 colonnes ; `TypeDocumentConstraintRunner`
  et `FamilleMetierSeedRunner` retirés.
- `NiveauEcart`, `ConclusionAlerteEcart`, `TypeConge`, `NiveauSeniorite`, `TypeDocumentAdministratifDemandeRh`,
  `NiveauSenioriteCodes` ne pilotent plus de règle ; ils ne servent plus qu'aux valeurs initiales (ou disparaissent).
- Changement de contrat : `ouvertes-par-niveau` (liste), paramètre `niveau` en `String`, codes de niveaux libres
  côté fronts (aucun `switch` sur `ORANGE` / `ROUGE` / `URGENTE` dans le web ou le mobile).
- Les propriétés `evaluation.alert.seuil-insuffisant`, le seuil des pointages manuels et
  `referentiel.evenements.document-sla-heures-par-type.*` ne servent plus qu'à l'amorçage.
- Le codage couleur d'une évaluation se calcule à la lecture avec le seuil courant (D-R06, CPD).
- Rétention du journal sans purge : volume négligeable.

## Sécurité / RGPD (STRIDE léger)

| Menace | Parade |
|---|---|
| **Élévation** : un collaborateur ou la DIRECTION modifie un paramètre | `BACKOFFICE_ECRITURE` (RH, ADMIN) sur chaque écriture ; DIRECTION et utilisateurs mobiles : 403. Tests par rôle. |
| **Altération** silencieuse (deux RH) | `@Version` → 409 ; journal avant / après dans la même transaction. |
| **Répudiation** | Journal append-only, auteur tiré du JWT, sans endpoint d'écriture ; 5 ans minimum. Limite : un administrateur de base peut modifier la table (même utilisateur SQL pour tout, dette connue). |
| **Déni de service fonctionnel** (seuil 1, désactivation massive, suppression du dernier niveau) | Bornes, 1 à 4 niveaux actifs, avertissement front, système non désactivable, confirmation obligatoire. |
| **Injection** par libellé (XSS dans les écrans, CSV) | Libellé texte brut, longueur bornée ; les fronts n'injectent jamais de HTML ; exports CSV : préfixer `'` une cellule commençant par `=`, `+`, `-`, `@`. |
| **Divulgation** | `GET L` pour un non back-office : valeurs actives de la liste blanche seulement ; aucun nombre d'utilisations ; aucune donnée personnelle dans les paramètres. |
| **Usurpation de service** (`/internal/referentiel/parametres/utiliser`) | Jeton interne, non routé par la gateway ; ne fait que valider et marquer. |

## Plan d'implémentation

Prérequis : lot 1 livré (tables de libellés serveur = source des valeurs initiales) et lot 2C (délai de grâce).
3B, 3C, 3D en parallèle (dossiers disjoints) ; 3E et 3F démarrent en parallèle sur les contrats § 7 avec des données
simulées, puis se branchent. Aucune modification de `gateway/`.

### 3B — `svc-referentiel-rh/` (dev-backend)
- Nouveaux : `entity/ParametreValeur`, `entity/ParametreJournal`, repositories, `service/ParametreService`,
  `service/ParametresCache`, `service/CodeParametre` (génération), `config/ParametresAmorceRunner`,
  `domain/ValeursInitialesReferentiel`, `web/ParametreController` (§ 7), `web/InterneReferentielController`
  (`utiliser`), DTO du § 7.
- Modifiés : `entity/DemandeDocumentAdministratifRh` (`String`), `service/DemandeDocumentAdministratifRhService`
  (type actif, SLA de la table, marque), `service/DemandeAdministrativeValidationService` (types de congé),
  `service/ReferentielRhService` (familles, niveaux, libellés après cache, marque), `config/CacheConfig` (cache 30 s),
  `web/FamilleMetierController` (façade) ; retraits : `TypeDocumentConstraintRunner`, `FamilleMetierSeedRunner`.
- Tests : critères 3B (attestation de stage 72 h puis 24 h sans effet sur l'échéance ; 409 utilisée ; désactivée
  → 422 au `POST` ; congé de mariage ; `MALADIE` 422 ; `ANNUEL` 409 ; renommage visible ≤ 30 s ; `POST` sur un
  statut 405 ; doublon 400 ; concurrence 409 ; journal ; DG01 et COL-WEB 403 en écriture) ; runner rejoué deux fois
  sans effet ; orphelin recréé inactif.

### 3C — `svc-evaluation/` (dev-backend)
- Nouveaux : mêmes briques génériques (préfixe `evaluation_`), `domain/NiveauAlerte` (valeur), `service/NiveauxAlerte`
  (calcul pur § 3) ; `web/EvaluationParametresController` étendu (§ 7, `GET`/`PUT` racine gardés).
- Modifiés : `entity/AlerteEcart*` (`String`), `service/EcartEtoiles`, `service/AlerteEcartService`,
  `service/AlerteEcartTraitementService`, `service/AlerteEcartComparateur`, `dto/AlerteEcartOuvertesParNiveau`,
  `kafka/AlerteEcartNotificationListener` (libellé), `config/AlerteEcartConstraintRunner` (cibles retirées +
  `DROP`), `entity/EvaluationParametres` (+ seuil, version), `service/EvaluationScoringService` (seuil lu en base),
  `service/TechnicalTemplateService` (appel `utiliser`), `entity/Evaluation` (+ 2 libellés), templates (types de
  question inactifs → 422).
- Tests : critères 3C (non-régression 5 contre 1 Urgente, 4 contre 2 Alerte ; seuils 2 / 1 appliqués aux détections
  postérieures seulement ; seuil en double 400 ; 5e actif 422 ; dernier actif 422 ; écart léger supprimé 204 puis
  409 ; `AUTRE` 409 ; type « Date » 422, `RATING` 409 ; échelle renommée renvoyée au mobile ; seuil d'insuffisance 2 ;
  titre de notification ; droits) ; index partiel : deux activations concurrentes au même seuil → une seule réussit.

### 3D — `svc-presence/` (dev-backend)
- Nouveaux : briques génériques (préfixe `presence_`, comportement seulement), `entity/PresenceParametres`,
  `web/PresenceParametresController` (§ 7).
- Modifiés : `service/PresenceDemandeManuelleService` (seuil), `service/RetardCalcul` (délai de grâce lu dans
  l'instantané), `service/PresenceSiteService` (rayons par défaut à la création seulement), réponses pointages
  (libellé du motif depuis l'instantané).
- Tests : critères 3D (seuil 2 → alerte à la 3e ; grâce 5 : 08:04 → 0, 08:06 → 1, 61 → 400 ; rayon intérieur 30 sur
  un nouveau site seulement ; libellé `REJETE_QR_INVALIDE` ; DG01 403 en écriture).

### 3E — `rh-admin-web/` (dev-web)
- Écran « Paramètres » construit depuis `GET B/referentiels` des trois services (domaines D-R10), tableau, formulaire,
  ordre par glisser-déposer, historique, réglages ; DIRECTION : lecture seule, « Consultation uniquement ».
- Table de libellés du lot 1 alimentée par les trois `GET L` ; suppression de toutes les listes figées (types,
  statuts, familles, niveaux, conclusions) ; niveaux d'alerte sans code en dur ; `ouvertes-par-niveau` en liste.
- Confirmation avant désactiver / supprimer / modifier un seuil ; 409 avec message et proposition de désactiver.
- Vérif : `npx tsc --noEmit -p . && npx vite build`.

### 3F — `rh_mobile_app/` (dev-mobile)
- Lecture des `GET L` (actifs) à l'ouverture des formulaires congé / document et au démarrage pour les statuts et
  l'échelle ; suppression des tables figées (`type_conge.dart`, `document_create_screen.dart`,
  `evaluation_models.dart`) ; pièce jointe exigée selon `piece_jointe_obligatoire` ; échec → erreur + « Réessayer » ;
  confirmation avant toute action à effet.
- Tests : mapping des listes, absence de liste de secours ; `flutter analyze && flutter test` ; Flutter ≤ 3.29.2.

### Configuration
- `app.parametres.cache-ttl-secondes=30` (trois services). Aucune variable d'environnement nouvelle ; aucune route
  gateway nouvelle. Guide de déploiement : sauvegarde des bases avant la mise à jour du lot 3 (pas de retour arrière
  après la première valeur créée).
