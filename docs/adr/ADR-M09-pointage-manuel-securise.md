# ADR M09 — Pointage manuel sécurisé (D-P21 à D-P26)

Date : 11/11/2026 · Statut : accepté · Tickets : SCRUM-83, SCRUM-84 (backend), SCRUM-85 (web), SCRUM-86 (mobile)
Références : `docs/sirh_basefonctionnelle.md` § 14.2 octies, § 14.3, § 14.4, C-M09-05, D-R06.

## Contexte

Le pointage manuel D-P04 permet de rattraper n'importe quel refus `REJETE_*`, avec une heure et un type libres
(jusqu'à 7 jours en arrière), sans délai de décision, et le valideur ne voit qu'un motif texte. Un QR papier
photographié permet de pointer depuis chez soi puis de faire valider un pointage antidaté. Le contrôle du pointage
lui-même (D-P01, D-P05, D-P08, D-P12) n'est pas modifié.

Constats dans le code (10/11/2026) : `PresenceDemandeManuelleService.creer` accepte `heure_declaree` / `type`,
`ancienneteMaxJours = 7` ; un refus `REJETE_PRECISION_GPS` est enregistré sans distance ; la décision n'a ni verrou
ni version (validation et expiration simultanées possibles) ; `valider` n'a pas de corps.

## Décision

Tout reste dans **svc-presence** (aucun nouveau service, aucune route gateway, aucun appel interne nouveau).

1. **Éligibilité centralisée** : un composant pur `EligibiliteManuelService` (sans I/O hors objets fournis) dit si un
   refus est rattrapable et pourquoi. Il sert à la création (contrôle faisant foi) et aux lectures mobiles
   (`manuel_possible`, `manuel_impossible_motif` sur la réponse du pointage, « Mes pointages » et les journées).
2. **Données figées à la création** dans `presence_demande_manuelle` (colonnes ajoutées par `ddl-auto=update`) :
   statut du refus, distance, précision, rayon, mode QR, plafond appliqué, délai de demande, échéance, signaux de
   vigilance (codes séparés par des virgules, convertisseur JPA, pas de colonne enum donc pas de CHECK) ; à la
   décision : commentaire du valideur et délai tentative → décision. Les coordonnées restent sur le pointage refusé,
   jamais dans une réponse d'API.
3. **Heure et type figés** = ceux de la tentative refusée (`CHAMP_NON_MODIFIABLE` si différents).
4. **Expiration** : statut `EXPIREE` (stocké → `DemandeManuelleStatutConstraintRunner` recrée la CHECK au démarrage) ;
   planificateur `DemandeManuelleExpirationScheduler` (cron en propriété, zone Africa/Tunis, une transaction par
   demande) + expiration paresseuse à la décision (`noRollbackFor = PresenceMetierException`, comme les heures sup).
5. **Concurrence** : verrou pessimiste (`SELECT … FOR UPDATE`, délai 3 s) sur la demande pour valider, refuser et
   expirer, pris après l'autorisation ; le statut est relu sous verrou.
6. **Reprise D-P26.a** : `DemandeManuelleRepriseRunner` (verrou consultatif PostgreSQL, idempotent) complète les
   demandes `EN_ATTENTE` dont l'échéance est nulle : échéance = démarrage + délai de décision, signaux et données
   figées calculés depuis la tentative d'origine, `plafond_metres` nul, `reprise = true`.
7. **Réglages** en propriétés `presence.manuel.*` (lot 3 : écran Paramètres D-R06). Les bornes hautes sont imposées
   (sécurité : une faute de frappe ne doit pas ouvrir le dispositif) ; les délais acceptent une valeur basse pour la
   recette (expiration en quelques minutes), avec un avertissement au démarrage hors plage D-R06.

## Alternatives écartées

- Calculer distance et signaux à la lecture : non conforme à D-P22 / D-P26 (plafond et signaux figés) et coûteux.
- Verrou optimiste `@Version` : colonne ajoutée sur des lignes existantes (valeur nulle), et une erreur 409 générique
  au lieu de `DEMANDE_EXPIREE` ; le verrou pessimiste est plus simple ici (décisions rares, unitaires).
- Table séparée des signaux : inutile, six codes fixes, filtre « avec signal » = colonne non vide.
- Seuils dans svc-referentiel-rh : pas avant le lot 3 (Paramètres), une propriété suffit.

## Conséquences

- L'API de création refuse d'office (422 / 400) sans rien créer ni notifier ; chaque refus d'office est journalisé
  (collaborateur, pointage, code), sans coordonnées.
- `anciennete-max-jours` est retiré (remplacé par `delai-demande-minutes`).
- Les demandes antérieures (VALIDEE / REFUSEE) gardent des champs nuls : affichage « — ».
- Le contrat JSON est unique pour la liste et le détail, mobile et Plateforme RH (voir le plan du lot) ; les champs
  réservés à qui décide (signaux, compteurs, commentaire) sont absents pour le demandeur.

## Sécurité / RGPD

- Identité tirée du JWT (inchangé, `X-Collaborateur-Id` comparé, 403 `IDENTITE_INCOHERENTE`).
- Aucune latitude / longitude dans une réponse (C-L-03) ; conservées sur le pointage refusé pour la preuve (C-L-01).
- Traces de décision conservées 5 ans (C-L-02) ; visibles RH / ADMIN / DIRECTION en lecture.
- Risque résiduel accepté : QR papier photographié + position simulée non détectée à moins de 300 m → signaux de
  vigilance au valideur ; option client : interdire le manuel sur les sites PAPIER (D-P25).

## Arbitrages du 11/11 (session principale) — font foi sur le plan ci-dessus

Le doc fonctionnel (§ 14.2 octies, D-P21.b à D-P26.a) a été complété en parallèle de cet ADR. En cas d'écart, **le doc fait foi**, avec ces précisions :
- Création : ordre D-P21.c du doc. Pointage inconnu **ou** d'un autre collaborateur → 403 `HORS_PERIMETRE` (pas de 404 `POINTAGE_INTROUVABLE`) ; `CHAMP_NON_MODIFIABLE` avant `POINTAGE_NON_REJETE` / `DEMANDE_DEJA_FAITE` ; `PRECISION_TROP_FAIBLE` **avant** `DISTANCE_TROP_GRANDE` (doc mis à jour).
- Validation : codes de commentaire du doc D-P24.b : 400 `COMMENTAIRE_OBLIGATOIRE` (signal et vide), 400 `COMMENTAIRE_TROP_COURT` (1 à 9 caractères après trim, avec ou sans signal), 400 `COMMENTAIRE_TROP_LONG` (> 500). Pas de `COMMENTAIRE_INVALIDE`.
- Expiration : message unifié du doc (« … ne peut plus être validée ni refusée. »), 409 `DEMANDE_EXPIREE` sur valider **et** refuser.
- Propriétés : au-dessus de la borne haute → le service refuse de démarrer (message nommant la propriété) ; sous la borne basse → démarrage avec WARN (plus strict, sert à la recette : `PRESENCE_MANUEL_DELAI_DECISION_MINUTES=3`). Doc D-P22.b mis à jour.
- Destinataire de l'expiration : valideur figé à la création (D-P23.c) — stocker le valideur attendu à la création s'il ne l'est pas déjà.
- Site sans rayon : rayon par défaut du type (20 m extérieur, 50 m intérieur / sans type) ; site sans mode QR → PAPIER (D-P24.c).
- Signal `MANUELS_MOIS_SEUIL` : demandes VALIDEE du mois civil Africa/Tunis de la tentative, demande en cours exclue, ≥ seuil ; figé à la création. Demande expirée non comptée.
- Demandeur : ni signaux, ni compteurs, ni commentaire du valideur (D-P24.d).
- Délais affichés « 40 min » / « 1 h 40 » (D-P24.e) ; minutes entières tronquées.

## Annexe A — Contrat d'API (fait foi pour backend, web, mobile ; les arbitrages ci-dessus s'appliquent)

### Routes
| Méthode | Route | Rôles | Corps |
|---|---|---|---|
| POST | `/api/rh/v1/mobile/presence/demandes-manuelles` | MOBILE_USER | `{pointage_rejete_id, justification}` (10–500) ; `type` / `heure_declaree` tolérés seulement identiques (heure à la seconde), sinon 400 `CHAMP_NON_MODIFIABLE` |
| POST | `/api/rh/v1/mobile/presence/demandes-manuelles/{id}/valider` | USER (valideur attendu) | `{commentaire?}` |
| POST | `/api/rh/v1/admin/presence/demandes-manuelles/{id}/valider` | RH, ADMIN | `{commentaire?}` |
| POST | `…/{id}/refuser` (mobile et admin) | idem | `{motif}` (inchangé) |
| GET | `/api/rh/v1/admin/presence/demandes-manuelles` | RH, ADMIN, DIRECTION | filtres existants + `statut=EXPIREE` + `avecSignal=true` |
| GET | listes mobiles existantes (`/moi`, `/a-valider`) et détails mobile / admin | inchangés | — |

Création : ordre des erreurs D-P21.c du doc (403 `HORS_PERIMETRE` pour inconnu ou autre collaborateur ; précision avant distance). Validation / refus : 404 `DEMANDE_INTROUVABLE`, 403, 409 `DEMANDE_EXPIREE` (statut `EXPIREE` enregistré avant l'erreur), 409 `DEMANDE_DEJA_TRAITEE`, 400 `COMMENTAIRE_OBLIGATOIRE` / `COMMENTAIRE_TROP_COURT` / `COMMENTAIRE_TROP_LONG` (valider), 422 `HEURE_HORS_FENETRE`.

### Champs ajoutés à la ressource demande (`DemandeManuelleResponse`, listes et détails ; nullables, absents si nuls)
| Champ JSON | Type | Visible par |
|---|---|---|
| `statut_libelle` | « En attente » / « Validée » / « Refusée » / « Expirée » | tous |
| `refus_statut`, `refus_statut_libelle` | code `REJETE_*` + libellé | tous |
| `distance_metres` | nombre | tous |
| `precision_metres` | nombre | tous |
| `rayon_metres` | entier | tous |
| `mode_qr`, `mode_qr_libelle` | `PAPIER` / `ECRAN`, « Papier » / « Écran » | tous |
| `delai_demande_minutes` | entier (création − tentative) | tous |
| `echeance` | instant ISO | tous |
| `delai_decision_minutes` | entier (décision − tentative) | tous |
| `plafond_metres` | entier (nul si reprise) | décideur, back-office |
| `reprise` | booléen | décideur, back-office |
| `avec_signal` | booléen | décideur, back-office |
| `signaux` | `[{"code","libelle"}]`, libellé rédigé par le serveur | décideur, back-office |
| `manuels_du_mois`, `seuil_mois`, `alerte_seuil` | entiers / booléen | décideur, back-office |
| `refus_du_jour` | entier (compteur D-P20, jour Tunis de la tentative, calculé à la lecture) | décideur, back-office |
| `commentaire_valideur` | texte | décideur, back-office |

`heure_declaree` reste le nom de l'heure de la tentative. Demandeur (non décideur, non back-office) : `signaux`, `avec_signal`, `plafond_metres`, `refus_du_jour`, `manuels_du_mois`, `seuil_mois`, `alerte_seuil`, `commentaire_valideur` absents. Jamais `latitude` / `longitude`.

### Codes de signal (enum `SignalVigilance`, non stocké en enum)
| Code | Condition (figée à la création) |
|---|---|
| `DISTANCE_SUP_2X_RAYON` | distance > 2 × rayon |
| `PRECISION_SUP_RAYON` | précision > rayon |
| `SITE_QR_PAPIER` | site PAPIER (ou sans mode) |
| `MANUELS_MOIS_SEUIL` | VALIDEE du mois civil Tunis, demande en cours exclue, ≥ seuil |
| `REFUS_FACIAL` | refus d'origine facial |
| `DEMANDE_TARDIVE` | demande > 30 min après la tentative |

### Réponse d'un pointage (`PointageResponse` de `POST /pointages` et `GET /pointages/me`, `PointageJournee`)
`manuel_possible` (booléen ; null pour VALIDE / VALIDE_MANUEL) et `manuel_impossible_motif` (texte D-P21.d, null si possible). `eligible_pointage_manuel` conservé = `manuel_possible` (compatibilité).

### Propriétés `presence.manuel.*`
`plafond-distance-metres`=300 (haut 1 000), `delai-demande-minutes`=120 (haut 480), `delai-decision-minutes`=1440 (haut 4 320), `signal-delai-minutes`=30, `expiration-cron`=`0 */5 * * * *`, `seuil-alerte-mois`=3 ; `anciennete-max-jours` supprimé. Recette : `PRESENCE_MANUEL_DELAI_DECISION_MINUTES=3`, `PRESENCE_MANUEL_EXPIRATION_CRON=0 * * * * *`.

### Notifications (objet `POINTAGE_MANUEL`)
Nouvel événement `POINTAGE_MANUEL_EXPIRE` : au collaborateur (compte, repli fiche) et au valideur figé à la création (ou `RH`), textes D-P23.b / D-P23.c du doc. Aucune notification pour un refus d'office 422.
