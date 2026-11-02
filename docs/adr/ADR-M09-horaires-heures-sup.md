# ADR-M09-HORAIRES — Horaires par site et heures supplémentaires (D-P11 à D-P16)

| | |
|---|---|
| **Statut** | accepté et implémenté (02/11/2026, SCRUM-48 à SCRUM-51 ; décisions client : `docs/jira/decisions_M09_heures_sup.md`) |
| **Date** | 2026-11-02 |
| **Module** | M09 — Pointage digital (vague horaires / heures sup, **Cible**) |
| **Tickets** | SCRUM-48 (horaire du site), SCRUM-49 (rejet hors horaire), SCRUM-50 (demandes d'heures sup), SCRUM-51 (comptage, suivi, CSV) |
| **Références** | `docs/sirh_basefonctionnelle.md` § 4.2, § 14.2 ter (D-P11 à D-P16), § 19 (C-M09-03, C-L-02) ; `docs/jira/fiche_M09_horaires_heures_sup.md` ; `docs/jira/decisions_M09_heures_sup.md` ; `ADR-M09-pointage-qr-gps.md`, `ADR-M09-reconnaissance-faciale.md` |

---

## Contexte

Le client veut un horaire hebdomadaire par site, une pointeuse fermée hors fenêtre (horaire ± tolérance) et des
heures supplémentaires demandées par le RO, validées par la RH, qui élargissent la fenêtre et sont comptées (sans paie).

État vérifié dans le code au 02/11/2026 :

- `PresencePointageService.pointer` : visage `preparer` (400 / 403) → rejeu d'idempotence → `controlerBlocage` (409)
  → QR (statique ou borne) → emplacement → `verifierPosition` (faux GPS, précision, zone) → `verificationFaciale.decider`
  → `VALIDE`. Le compteur D-P10.a n'est touché que dans `decider`.
- `PointageStatutConstraintRunner` recrée `presence_pointage_statut_check` depuis l'enum à chaque démarrage.
- `PresenceDemandeManuelleService` : modèle demande → décision (valideur D-H03 ou RH / ADMIN, anti-auto-validation
  par compte JWT + fiche), notifications `PresenceEventPublisher.notifier` **après commit**.
- `ReferentielClient` ne connaît que `GET /api/referentiel/v1/collaborateurs/moi/valideur-pointage` (JWT relayé) ;
  la règle D-H03 est `SuperieurHierarchieRules.resoudreValideurM01(Collaborateur)` dans le référentiel.
  Le référentiel expose déjà `/internal/referentiel/**` (jeton `X-Internal-Token`, comparaison à temps constant,
  `InterneReferentielController`) ; `svc-presence` n'a pas encore la propriété `app.internal-api.token`
  ni la variable `APP_INTERNALAPI_TOKEN` dans `deploy/docker-compose.yml`.
- Gateway : la route `svc-presence` couvre déjà `/api/rh/v1/admin/presence/**` et `/api/rh/v1/mobile/presence/**` ;
  `/internal/**` n'est routé nulle part.
- `ClockConfig` fournit une `Clock` Africa/Tunis ; `@EnableScheduling` actif ; crons en propriété
  (`presence.alert.j30-cron`).
- `PresenceExceptionHandler` : `@Valid` → 422 `VALIDATION_ERROR` ; `SecurityException` → 403 `ACCESS_DENIED` ;
  `PresenceMetierException` porte code + statut (fabriques 404 / 409 / 422 uniquement).
- Identité du pointage : `requireCurrentCollaborateurId()` lit `X-Collaborateur-Id` (dette connue, cf. Sécurité).
  La ligne `presence_pointage.collaborateur_id` peut donc contenir la fiche (en-tête) ou le compte (repli JWT).

## Décision

### 1. Modèle de données (`presence_db`, `ddl-auto=update`)

| Table | Colonnes clés | Remarques |
|---|---|---|
| `presence_site` (existante) | + `tolerance_avant_minutes`, `tolerance_apres_minutes` (`Integer`, **nullables**, null lu comme 60) | Colonne ajoutée à une table existante → nullable. |
| `presence_horaire_site` | `id`, `site_id`, `jour_semaine` (1–7, ISO), `heure_debut` (`time`), `heure_fin` (`time`) | Unique (`site_id`, `jour_semaine`). Aucune ligne = site sans horaire (D-P11.b). Jour absent = fermé. `fin < debut` = nuit. |
| `presence_horaire_site_historique` | `id`, `site_id`, `modifie_par_compte_id`, `modifie_par_nom`, `modifie_le`, `avant_json`, `apres_json` (`text`) | Une ligne par PUT / DELETE (C-L-02). JSON = semaine + tolérances, sérialisé par le service (pas d'entrée client brute). |
| `presence_heures_sup_demande` | `id`, `reference` (`HS-` + 8 hex de l'id), `site_id`, `date_debut`, `date_fin`, `ouverture_avant_minutes`, `ouverture_apres_minutes` (0 = côté non demandé), `motif`, `statut`, `debut_plage_ts`, `demandeur_fiche_id`, `demandeur_compte_id`, `demandeur_nom`, `creee_validee` (bool), `decide_par_compte_id`, `decide_par_nom`, `decide_le`, `motif_refus`, `annule_par_compte_id`, `annule_par_nom`, `annule_le`, `cree_le`, `version` (`@Version`) | `statut` ∈ `EN_ATTENTE, VALIDEE, REFUSEE, ANNULEE, EXPIREE` (enum `StatutHeuresSup`, CHECK recréé par runner). `debut_plage_ts` = ouverture de la fenêtre élargie du **premier jour ouvert restant** : `debut_horaire − ouverture_avant − tolerance_avant` (ouverture avant = 0 si seul « après » est demandé). Ex. 06:00, 2 h avant, tol. 60 → 03:00 ; « après » seul → 05:00. Recalculé quand l'horaire change ou qu'un jour est retiré (§ 2 bis). |
| `presence_heures_sup_beneficiaire` | `id`, `demande_id`, `collaborateur_id` (fiche), `compte_id`, `matricule`, `nom`, `direction_id`, `direction_libelle` | Instantané lu au référentiel à la création (filtres web, CSV, notifications). |
| `presence_heures_sup_jour` | `id`, `demande_id`, `beneficiaire_id`, `collaborateur_id`, `site_id`, `journee` (`date`) | **Unique (`collaborateur_id`, `site_id`, `journee`)** = garde-fou D-P13.f. Une ligne par bénéficiaire × jour ouvert. Lignes **supprimées** quand la demande sort de `EN_ATTENTE` / `VALIDEE`. Index (`site_id`, `journee`). |
| `presence_pointage` (existante) | + `journee` (`date`), `horaire_debut_ts`, `horaire_fin_ts` (`timestamptz`), tous **nullables** | Instantané de la journée de rattachement et de l'horaire en vigueur, posé sur les pointages acceptés d'un site à horaire (y compris `VALIDE_MANUEL` à la validation). Index (`site_id`, `journee`). |

**Heures comptées : calculées à la volée, pas matérialisées.** Le calcul ne lit que des données figées :
l'instantané d'horaire porté par chaque pointage et les ouvertures (minutes) de la demande validée. Une modification
d'horaire ne change donc pas les heures déjà comptées (D-P11.c) sans table de résultats à synchroniser, sans job de
recalcul, et un pointage manuel validé après coup est pris en compte naturellement.

### 2. Calcul de la fenêtre (`FenetreHoraireService`, classe pure + `Clock`)

- Zone `ClockConfig.ZONE_TUNIS`, calculs en `ZonedDateTime` (sûr même si l'heure d'été revenait).
- Journée J d'un site : `debut = J @ heure_debut` ; `fin = (heure_fin <= heure_debut ? J+1 : J) @ heure_fin`
  (rattachement au jour du début, D-P11.a).
- Fenêtre(J, collaborateur) = `[debut − tolAvant − ouvAvant ; fin + tolApres + ouvApres]`, bornes **incluses**
  (SORTIE à 16:00 → `VALIDE`, 16:01 → rejet). `ouv*` = ouverture de la demande `VALIDEE` trouvée dans
  `presence_heures_sup_jour` pour (site, J, collaborateur), recherchée sur **la fiche ou le compte** du pointeur
  (la colonne `collaborateur_id` du pointage peut contenir l'un ou l'autre).
- Pour un instant t : candidats J−1, J, J+1 (date locale de t) ; on garde ceux dont la fenêtre contient t.
  Plusieurs candidats (seulement possible après un changement d'horaire) : ENTREE → journée la plus tardive,
  SORTIE → la plus ancienne. Aucun : rejet avec la fenêtre de la journée J (ou « Site fermé aujourd'hui »).
- Chevauchement D-P11.a (PUT horaire) : pour chaque jour ouvert d et le suivant d+1 (dimanche → lundi compris),
  `fin(d) + tolApres > debut(d+1) − tolAvant` → 422 `HORAIRES_CHEVAUCHANTS` (égalité tolérée).
  À la création d'une demande, même contrôle avec les fenêtres **élargies** (cette demande + demandes actives des jours
  voisins pour chaque bénéficiaire) → 422 `HEURES_SUP_CHEVAUCHENT_JOUR_VOISIN`.
- Instant commun à deux fenêtres qui se touchent : ENTREE → journée la plus tardive, SORTIE → la plus ancienne ;
  comparaison à la seconde.
- Résultat typé : `SITE_SANS_HORAIRE` | `DANS_FENETRE(journee, debutTs, finTs)` | `HORS_FENETRE(message)`.
  Message : « Pointage ouvert de HH:mm à HH:mm » ou « Site fermé aujourd'hui ».

### 2 bis. Horaire modifié : effet sur les demandes (décision client n° 2)

Dans `HoraireSiteService`, **même transaction** que le PUT / DELETE de l'horaire :

- Jours restés ouverts dont seules les heures changent : demandes intactes, la fenêtre élargie suit le nouvel horaire ;
  `debut_plage_ts` des demandes `EN_ATTENTE` / `VALIDEE` du site est recalculé.
- Jour devenu fermé, ou DELETE (site sans horaire) : pour les demandes `EN_ATTENTE` / `VALIDEE` du site, suppression des
  lignes `presence_heures_sup_jour` des journées concernées **dont la fenêtre n'a pas encore ouvert** (les journées
  passées ne bougent pas : heures déjà comptées conservées). Plus aucune ligne → demande `ANNULEE`
  (`annule_par_*` = auteur de la modification, motif « Horaire du site modifié »). Sinon la demande garde son statut
  avec moins de jours, et `debut_plage_ts` est recalculé.
- Notifications après commit (via `notifier`) : demandeur, et bénéficiaires si la demande était `VALIDEE`, pour une
  annulation totale comme partielle (jours retirés listés).
- Conflit de version avec une décision simultanée → 409 sur le PUT (rejouable), jamais d'état partiel.
- Le PUT ne vérifie que le chevauchement de base (D-P11.a), pas celui des fenêtres élargies existantes.

### 3. Place du contrôle dans `PresencePointageService.pointer`

Ordre D-P12.b : `preparer` (400/403) → idempotence → `controlerBlocage` (409) → QR → emplacement → GPS
(faux GPS, précision, zone) → **horaire** → `decider` (visage).

Le contrôle s'insère dans `verifierPosition`, après le test de zone et **avant** `verificationFaciale.decider` :
hors fenêtre → `persistRejet(..., REJETE_HORS_HORAIRE, message, siteId, credentialId, distance)` et retour,
donc ni étape visage ni incrément du compteur D-P10.a (rejet neutre). Dans la fenêtre → `journee`,
`horaire_debut_ts`, `horaire_fin_ts` posés sur le pointage. Site sans horaire → aucun effet (non-régression).

Pointage manuel (D-P12.c) : `PresenceDemandeManuelleService.creer` **et** `valider` appellent `FenetreHoraireService`
avec le site du pointage rejeté et l'heure déclarée → 422 `HEURE_HORS_FENETRE`. Rejet sans site connu
(ex. QR invalide) : pas de contrôle horaire possible. À la validation, l'instantané d'horaire est posé sur le
`VALIDE_MANUEL`.

### 4. Périmètre D-H03 des bénéficiaires (référentiel)

- Demandeur : identité **JWT uniquement**, `ReferentielClient.monValideurPointage()` (fiche, compte, nom).
  `X-Collaborateur-Id` n'est jamais lu par ces endpoints.
- Bénéficiaires : nouvel endpoint interne du référentiel, même contrôleur et même jeton que l'existant :
  `POST /internal/referentiel/collaborateurs/resolution-pointage` (`X-Internal-Token`) — corps
  `{"collaborateur_ids": [uuid…]}` (1 à 100) → pour chaque id : `collaborateur_id`, `compte_id`, `matricule`,
  `nom`, `actif`, `direction_id`, `direction_libelle`, `valideur_id`, `valideur_compte_id` (valideur =
  `SuperieurHierarchieRules.resoudreValideurM01`). Id inconnu → absent de la réponse.
- `svc-presence` vérifie : tous les ids résolus et actifs (sinon 422 `COLLABORATEUR_INVALIDE`) ;
  `valideur_id == fiche du demandeur` pour **chacun** (sinon 403 `HORS_PERIMETRE`, rien n'est créé) ;
  demandeur parmi les bénéficiaires → 403 `HORS_PERIMETRE` (il n'est jamais son propre valideur).
- Sélecteur mobile du RO : `GET /internal/referentiel/collaborateurs/{ficheId}/perimetre-validation`
  (collaborateurs actifs dont le valideur D-H03 est `ficheId`), relayé par `svc-presence`
  sur `GET /api/rh/v1/mobile/presence/heures-sup/beneficiaires-possibles` avec `ficheId` tiré du JWT.
  Le contrôle serveur reste la résolution ci-dessus (la liste n'est qu'une aide de saisie).
- Pourquoi `/internal` plutôt que le JWT relayé : la création directe RH (D-P13.c) et le comptage ont besoin de
  données sur **d'autres** collaborateurs (compte, matricule, direction) qu'aucun endpoint « moi » ne fournit ;
  un endpoint public « valideur de X » ouvrirait une énumération de l'organigramme.
- Référentiel indisponible → 503 `REFERENTIEL_INDISPONIBLE` (aucune création en mode dégradé).

### 5. Anti-auto-validation et décisions

- Valider / refuser : `BACKOFFICE_ECRITURE` ; refus si le compte JWT (`currentCompteId`) ou la fiche
  (`monValideurPointage`, tolérant l'absence de fiche comme `autoriserDecision`) correspond au demandeur ou à un
  bénéficiaire → 403 `AUTO_VALIDATION_INTERDITE`.
- Liste des bénéficiaires dédoublonnée, 1 à 50 ; inactif ou introuvable → 422 `COLLABORATEUR_INVALIDE`.
- Annulation par RH / ADMIN : uniquement une demande `VALIDEE` non commencée ; `EN_ATTENTE` → 409 `STATUT_INCOMPATIBLE`
  (la RH la refuse) ; RH / ADMIN bénéficiaire de la demande → 403 `AUTO_VALIDATION_INTERDITE`.
- Création directe validée (RH / ADMIN) : mêmes contrôles d'entrée, pas de contrôle D-H03, appelant bénéficiaire →
  403 `AUTO_VALIDATION_INTERDITE` ; `creee_validee = true`, `decide_par_* = appelant`.
- Expiration paresseuse : toute décision ou annulation sur une demande `EN_ATTENTE` dont `debut_plage_ts <= now`
  la passe d'abord en `EXPIREE` (notification) puis répond 409 `STATUT_INCOMPATIBLE`.

### 6. Concurrence

- Doublon (collaborateur, site, journée) : pré-contrôle lisible (409 `CHEVAUCHEMENT_HEURES_SUP` avec la référence
  en conflit) **et** contrainte unique de `presence_heures_sup_jour` ; `DataIntegrityViolationException` traduite en
  409 `CHEVAUCHEMENT_HEURES_SUP` (deux RO simultanés).
- Transitions de statut : `@Version` sur la demande ; `ObjectOptimisticLockingFailureException` → 409
  `STATUT_INCOMPATIBLE` (RH qui valide pendant que le planificateur expire ou que le RO annule).
- PUT horaire : remplacement complet en une transaction, dernier écrit gagnant, historique complet.

### 7. Planificateur `EXPIREE`

`HeuresSupExpirationScheduler` : `@Scheduled(cron = "${presence.heures-sup.expiration-cron:0 */5 * * * *}", zone = "Africa/Tunis")`,
sélection `EN_ATTENTE AND debut_plage_ts <= now` (`Clock`), passage en `EXPIREE`, suppression des lignes
`presence_heures_sup_jour`, notification au demandeur après commit. Une transaction par demande ; idempotent
(`@Version`) ; l'expiration paresseuse (§ 5) rend la fréquence non critique.

### 8. Statuts stockés et contraintes CHECK

- `PointageStatut.REJETE_HORS_HORAIRE` : `PointageStatutConstraintRunner` existant le prend en compte (lit l'enum).
- `StatutHeuresSup` : nouveau runner `HeuresSupStatutConstraintRunner` (même modèle, table
  `presence_heures_sup_demande`, contrainte `presence_heures_sup_demande_statut_check`).

### 9. Notifications (topic `rh.notifications`, après commit)

Via `PresenceEventPublisher.notifier` (déjà `afterCommit`), adresse = compte, repli fiche :
créée `EN_ATTENTE` → `RH` (comptes RH seulement, sémantique existante) ; validée → demandeur + chaque bénéficiaire
(dates, site, fenêtre élargie du premier jour) ; **créée validée par RH / ADMIN → bénéficiaires seulement** (ni l'auteur,
ni `RH`) ; refusée → demandeur (motif) ; annulée par RH / ADMIN → demandeur + bénéficiaires ; **annulée par le demandeur
(EN_ATTENTE) → aucune notification** ; annulation automatique (horaire, § 2 bis) → demandeur (+ bénéficiaires si
`VALIDEE`) ; expirée → demandeur. Rien aux bénéficiaires d'une demande `EN_ATTENTE`, refusée ou expirée.
Le corps ne contient ni matricule ni motif de refus pour les bénéficiaires.

### 10. Contrat d'API

Format d'erreur : `ErrorResponse` existant (`code`, `message`, `status`). Les 400 ci-dessous sont des contrôles
explicites du service (`PresenceMetierException.badRequest`, à ajouter) ; un `@Valid` reste en 422 `VALIDATION_ERROR`
(comportement existant, ADR facial). Les 403 métier portent leur code via `PresenceMetierException.forbidden`.
Expression de rôle des demandes d'heures sup côté mobile (décision n° 13 ; matrice § 4.2, ligne « Demander ») :
`PresenceSecurityExpressions.DEMANDEUR_HEURES_SUP = "hasAnyRole('RO','RESPONSABLE','RH','ADMIN')"` (rôles émis par
`ApplicationRoleMatrix`) ; le périmètre reste D-H03 vérifié en service. Les autres endpoints mobiles restent `USER`.
JSON en `snake_case`, heures `HH:mm`, dates `yyyy-MM-dd`, instants ISO-8601.

**Plateforme RH** — `/api/rh/v1/admin/presence`

| Méthode | Chemin | Rôle | Corps / paramètres | Réponse | Erreurs |
|---|---|---|---|---|---|
| GET | `/sites/{siteId}/horaire` | RH, DIRECTION, ADMIN | — | 200 `{site_id, tolerance_avant_minutes, tolerance_apres_minutes, jours:[{jour:"LUNDI", debut, fin, nuit}], avec_horaire, modifie_par, modifie_le}` | 404 `SITE_INTROUVABLE` |
| PUT | `/sites/{siteId}/horaire` | RH, ADMIN | `{tolerance_avant_minutes, tolerance_apres_minutes, jours:[{jour, debut, fin}]}` (1 à 7 jours, jour absent = fermé) | 200 (même corps que GET) | 400 `TOLERANCE_INVALIDE` (hors 0–90 ou pas de 15), 400 `HORAIRE_FORMAT_INVALIDE` (jour en double, heure illisible, `jours` vide → utiliser DELETE), 422 `HORAIRE_INVALIDE` (début = fin), 422 `HORAIRES_CHEVAUCHANTS`, 404 |
| DELETE | `/sites/{siteId}/horaire` | RH, ADMIN | — | 204 (site revenu sans horaire, tracé) | 404 |
| GET | `/sites/{siteId}/horaire/historique` | RH, DIRECTION, ADMIN | `page`, `size` | 200 page `{modifie_par, modifie_le, avant, apres}` | 404 |
| GET | `/heures-sup/demandes` | RH, DIRECTION, ADMIN | `statut`, `site_id`, `collaborateur_id`, `direction_id`, `du`, `au`, `page`, `size` | 200 page de `DemandeHeuresSupResponse` | 400 `PERIODE_INVALIDE` |
| GET | `/heures-sup/demandes/{id}` | RH, DIRECTION, ADMIN | — | 200 | 404 `DEMANDE_INTROUVABLE` |
| POST | `/heures-sup/demandes` | RH, ADMIN | `CreationHeuresSupRequest` | 201 `VALIDEE`, `creee_validee=true` | comme la création mobile, sans `HORS_PERIMETRE` ; 403 `AUTO_VALIDATION_INTERDITE` |
| POST | `/heures-sup/demandes/{id}/valider` | RH, ADMIN | — | 200 `VALIDEE` | 403 `AUTO_VALIDATION_INTERDITE`, 409 `STATUT_INCOMPATIBLE`, 404 |
| POST | `/heures-sup/demandes/{id}/refuser` | RH, ADMIN | `{motif}` | 200 `REFUSEE` | 400 `MOTIF_OBLIGATOIRE`, 403, 409, 404 |
| POST | `/heures-sup/demandes/{id}/annuler` | RH, ADMIN | `{motif?}` | 200 `ANNULEE` | 403 `AUTO_VALIDATION_INTERDITE` (appelant bénéficiaire), 409 `STATUT_INCOMPATIBLE` (`EN_ATTENTE`, autre statut, ou plage commencée), 404 |
| GET | `/heures-sup/comptees` | RH, DIRECTION, ADMIN | `du`, `au` (≤ 93 j), `site_id`, `collaborateur_id`, `direction_id`, `page`, `size` | 200 page de `HeuresComptees` | 400 `PERIODE_INVALIDE` / `PERIODE_TROP_LONGUE` |
| GET | `/heures-sup/comptees/export.csv` | RH, DIRECTION, ADMIN | mêmes filtres | 200 `text/csv; charset=UTF-8`, BOM, séparateur `;`, `Content-Disposition: attachment` | idem |

**RH Connect** — `/api/rh/v1/mobile/presence` (rôle `USER`, identité JWT)

| Méthode | Chemin | Corps / paramètres | Réponse | Erreurs |
|---|---|---|---|---|
| POST | `/fenetre-du-jour` | `{qr_token}` (dans le corps, jamais en query) | 200 `{site_id, site_libelle, avec_horaire, journee, ferme, horaire_debut, horaire_fin, ouverture_debut, ouverture_fin, heures_sup:[{reference, ouverture_avant_minutes, ouverture_apres_minutes}]}` | 422 `QR_INVALIDE` (signature ; aucun pointage enregistré), 404 `SITE_INTROUVABLE` |
| GET | `/sites` | — | 200 `[{site_id, libelle, avec_horaire}]` (sites actifs) | — |
| GET | `/heures-sup/beneficiaires-possibles` (DEMANDEUR_HEURES_SUP) | — | 200 `[{collaborateur_id, matricule, nom}]` (vide si non valideur) | 503 `REFERENTIEL_INDISPONIBLE` |
| POST | `/heures-sup/demandes` (DEMANDEUR_HEURES_SUP) | `CreationHeuresSupRequest` | 201 `EN_ATTENTE` | voir ci-dessous |
| GET | `/heures-sup/demandes/moi` (DEMANDEUR_HEURES_SUP) | `statut?` | 200 liste (demandes dont je suis le demandeur) | — |
| POST | `/heures-sup/demandes/{id}/annuler` (DEMANDEUR_HEURES_SUP) | — | 200 `ANNULEE` | 403 `ACCESS_DENIED` (pas le demandeur, ou demande `VALIDEE`), 409 `STATUT_INCOMPATIBLE`, 404 |
| GET | `/heures-sup/demandes/moi/comptees` (DEMANDEUR_HEURES_SUP) | `du`, `au` (≤ 93 j), `demande_id?` | 200 liste de `HeuresComptees` limitée aux bénéficiaires × journées de **mes propres demandes** `VALIDEE` (décision n° 4) | 400 `PERIODE_INVALIDE` / `PERIODE_TROP_LONGUE` ; `demande_id` d'un autre demandeur → 403 `ACCESS_DENIED` |
| GET | `/heures-sup/moi` | `du?`, `au?` | 200 `{a_venir:[…], comptees:[HeuresComptees]}` (les miennes, fiche et compte du JWT) | aucun paramètre d'identité accepté |

`CreationHeuresSupRequest` : `{collaborateur_ids:[uuid] (1–50), site_id, date_debut, date_fin? (défaut = date_debut),
ouverture_avant_minutes? , ouverture_apres_minutes?, motif}`. Erreurs (dans l'ordre d'évaluation) :
400 `MOTIF_OBLIGATOIRE`, 400 `DUREE_HEURES_SUP_INVALIDE` (hors 30–240 ou pas de 30), 400 `OUVERTURE_ABSENTE`
(aucun côté), 400 `PLAGE_TROP_LONGUE` (> 31 j), 400 `PERIODE_INVALIDE` (fin < début), 404 `SITE_INTROUVABLE`,
422 `SITE_SANS_HORAIRE`, 422 `AUCUN_JOUR_OUVERT`, 422 `PLAGE_DEJA_COMMENCEE`, 422 `COLLABORATEUR_INVALIDE`
(inactif ou introuvable ; ids dédoublonnés avant contrôle), 403 `HORS_PERIMETRE`, 422 `HEURES_SUP_CHEVAUCHENT_JOUR_VOISIN`,
409 `CHEVAUCHEMENT_HEURES_SUP`, 503 `REFERENTIEL_INDISPONIBLE`.

`DemandeHeuresSupResponse` : `{id, reference, statut, site_id, site_libelle, date_debut, date_fin, jours:[date]
(jours ouverts restants), ouverture_avant_minutes, ouverture_apres_minutes, debut_plage_ts, motif, demandeur_nom,
beneficiaires:[{collaborateur_id, matricule, nom}], creee_validee, decide_par, decide_le, motif_refus, annule_par,
annule_le, motif_annulation, cree_le}`.

`HeuresComptees` : `{collaborateur_id, matricule, nom, site_id, site_libelle, journee, horaire_debut, horaire_fin,
plage_avant, plage_apres, entree, sortie, minutes_comptees, heures_comptees ("hh:mm"), reference_demande, etat}` avec `etat` ∈ `COMPLET`, `INCOMPLET`, `EN_COURS`.
Colonnes CSV = D-P16.a (matricule, nom, site, date, horaire du jour, plage validée, ENTREE, SORTIE,
heures comptées hh:mm, référence, état : complet / incomplet / en cours).

**Pointage existant** : `POST /pointages` inchangé ; `statut` peut valoir `REJETE_HORS_HORAIRE` (motif = message
de fenêtre) ; `PointageResponse` gagne `journee` (nullable). Pointage manuel : 422 `HEURE_HORS_FENETRE` en création et
en validation.

**Interne référentiel** (non routé) : voir § 4 ; 401 sans jeton valide.

### 11. Calcul des heures comptées (`CalculHeuresSupService`, pur)

Pour chaque ligne `presence_heures_sup_jour` d'une demande `VALIDEE` dans le filtre : pointages `VALIDE` /
`VALIDE_MANUEL` du site, de la `journee`, du collaborateur (fiche ou compte), triés par `server_ts` ; appariement par
**paires successives** : chaque ENTREE est appariée à la SORTIE suivante, les paires s'additionnent, toute ENTREE ou
SORTIE orpheline est ignorée (ENTREE, ENTREE, SORTIE → la seconde ENTREE forme la paire) ; plages = `[horaire_debut − ouvAvant ; horaire_debut[` ∪ `]horaire_fin ; horaire_fin + ouvApres]`
(instantané d'horaire du pointage d'ENTREE) ; somme des intersections ; arrondi 15 min vers le bas.
État du jour : pas d'orphelin → `COMPLET` ; orphelin et fenêtre du jour (élargie) encore ouverte à `now` → `EN_COURS` ;
orphelin et fenêtre fermée → `INCOMPLET`. **Les paires complètes d'un jour incomplet sont comptées** (décision n° 3). Chargement par lot
(une requête pointages par site × période), pas de N+1.

### 12. Export CSV

Généré côté serveur. Neutralisation de l'injection de formule : toute cellule commençant par `=`, `+`, `-`, `@`,
tabulation ou retour chariot est préfixée par `'` ; guillemets doublés, cellules entre guillemets. Période ≤ 93 jours.
Aucune donnée hors filtre ; DIRECTION autorisée en lecture (D-P16.a).

### 13. Gateway et configuration

- Gateway : **aucune route à ajouter** (préfixes déjà couverts) ; ne jamais ajouter `/internal/**`.
- `svc-presence/application.properties` : `app.internal-api.token=${INTERNAL_API_TOKEN:change-me-internal-token}`
  (même convention locale que le référentiel), `presence.heures-sup.expiration-cron`,
  `presence.heures-sup.periode-export-max-jours=93`.
- `deploy/docker-compose.yml` : ajouter `APP_INTERNALAPI_TOKEN: ${INTERNAL_API_TOKEN:?…}` au service `svc-presence`.

## Alternatives écartées

- Heures comptées matérialisées à chaque SORTIE : synchronisation fragile (pointage manuel validé après coup, annulation), sans gain à cette volumétrie.
- Fenêtre recalculée sur l'horaire courant pour le comptage : violerait D-P11.c (recalcul rétroactif).
- Index unique partiel `WHERE statut IN (…)` : non géré par `ddl-auto` ; la suppression des lignes « jour » à la sortie d'état suffit avec une contrainte unique simple.
- Endpoint public « valideur de X » relayé avec le JWT : énumération possible de l'organigramme ; inutile pour la création RH et le comptage.
- Rejet horaire avant le GPS : contredirait D-P12.b et masquerait un faux GPS.
- Verrou pessimiste sur la demande : `@Version` suffit (conflits rares, réponse 409 attendue).

## Conséquences

- Trois nouvelles tables, deux colonnes de tolérance, trois colonnes d'instantané sur `presence_pointage` (toutes nullables, aucune migration de données).
- `svc-presence` dépend du référentiel pour **créer** une demande (pas pour pointer) ; le pointage reste autonome.
- Le paramétrage d'un site est réversible (DELETE) : déploiement site par site (D-P11.b).
- Les heures comptées sont exactes même si un horaire change en cours de mois, au prix d'un calcul par requête (borné à 93 jours).

## Sécurité / RGPD (STRIDE court)

| Menace | Scénario | Parade |
|---|---|---|
| **S**poofing | RO qui forge `X-Collaborateur-Id` pour demander ou valider au nom d'un autre | Endpoints heures sup : identité **JWT uniquement** (`monValideurPointage`, `currentCompteId`) ; test dédié avec en-tête falsifié. |
| Spoofing | Collaborateur qui pointe avec l'en-tête d'un collègue bénéficiaire pour profiter de sa fenêtre | **Dette existante** (pointage sur en-tête quand le visage est inactif) ; le pointage serait enregistré au nom du collègue. Ticket sécurité séparé recommandé (contrôle en-tête = fiche JWT comme `preparer`). |
| Spoofing | Appel direct d'un `/internal/**` | Non routé par la gateway, jeton partagé comparé à temps constant, secret en variable d'environnement. |
| **T**ampering | Heure de pointage falsifiée côté client | Heure **serveur** (`Clock`) uniquement ; `client_ts` ignoré pour la fenêtre. |
| Tampering | Modification rétroactive d'horaire pour gonfler des heures | Instantané d'horaire figé sur le pointage, historique avant / après. |
| **R**epudiation | « Je n'ai pas validé / modifié » | Auteur (compte + nom) et date sur décision, annulation, création validée, chaque modification d'horaire. |
| **I**nformation disclosure | Un collaborateur lit les heures d'un autre ; énumération de l'organigramme | `/heures-sup/moi` sans paramètre d'identité ; endpoints admin `BACKOFFICE_*` ; sélecteur RO limité à son périmètre ; vue RO des heures limitée aux bénéficiaires × journées de ses propres demandes (filtre serveur sur `demandeur_compte_id` du JWT) ; aucun endpoint public « valideur de X ». |
| Information disclosure | Injection de formule dans le CSV (nom, motif) | Neutralisation des cellules (§ 12). |
| **D**enial of service | Export ou liste sur des années ; demande à 1 000 bénéficiaires | Période ≤ 93 j, pagination, 1–50 bénéficiaires, 31 jours max. |
| **E**levation of privilege | RO qui valide, RH qui se valide, DIRECTION qui agit | `@PreAuthorize` sur chaque méthode, anti-auto-validation compte + fiche, tests 403 par rôle. |

Données personnelles : matricule, nom, direction et horaires de présence (finalité : suivi des heures, pas de paie).
Aucune donnée biométrique. Durée de conservation : alignée sur celle des pointages (C-L-02).
