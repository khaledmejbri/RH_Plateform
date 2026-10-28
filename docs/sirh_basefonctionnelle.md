# Base fonctionnelle — SIRH RH-Évènement

| | |
|---|---|
| **Produit** | Plateforme SIRH *RH-Évènement* (v2) |
| **Client cible** | AGUA — Service d’assainissement |
| **Canaux** | Application mobile *RH Connect* (Flutter) · Portail d’administration *Plateforme RH* (React) |
| **Langue métier** | Français |
| **Cadre légal de référence** | Tunisie (CNSS, conservation des pièces, RGPD / protection des données) |
| **Statut du document** | Base fonctionnelle consolidée à partir du CDC v2 et du code existant |
| **Date** | 12 août 2026 |

Ce document décrit **à quoi sert l’application**, **tous les modules fonctionnels**, **les règles métier** et **les contraintes**.  
Quand le produit livré diverge du CDC, l’écart est indiqué explicitement (`Livré` / `Partiel` / `Cible`).

---

## 1. À quoi sert cette application

Le SIRH digitalise les processus RH d’une organisation de service public / semi-public (assainissement) pour que :

1. **Le collaborateur** gère depuis son téléphone : congés, autorisations de sortie, missions, documents, formations, plaintes, évaluations, organigramme, pointage.
2. **Le responsable opérationnel (RO)** valide les demandes de son unité, déclare les plaintes externes, suit son équipe.
3. **La RH (RRH)** administre le référentiel, traite les files d’attente, pilote les campagnes d’évaluation, le plan de formation et le suivi des plaintes.
4. **La direction** dispose d’une vue consolidée (effectifs, demandes, alertes) — cible CDC.

**Problème métier résolu** : aujourd’hui les processus RH sont papier / e-mail / Excel, lents, peu traçables, et inadaptés au terrain (équipes mobiles, sites multiples). L’application vise à :

- réduire les délais de traitement des demandes RH (objectif CDC : **−80 %**) ;
- garantir une **traçabilité** de chaque décision (qui a validé, quand, avec quel motif) ;
- offrir une expérience **mobile-first** pour les agents de terrain ;
- préparer l’intégration future d’une **paie** (soldes de congés, bulletins) sans la porter dans le SIRH aujourd’hui.

**Ce que l’application n’est pas** : un logiciel de paie, un ERP financier, un GED documentaire complet, ni un ATS de recrutement mature. Ces sujets sont hors périmètre immédiat ou en cible (voir § 10).

---

## 2. Objectifs stratégiques

| Objectif | Indicateur / cible CDC |
|---|---|
| Automatiser les workflows RH (demande → RO → RRH) | Délai de traitement −80 % |
| Expérience mobile moderne, y compris hors ligne | File locale + synchro automatique |
| Aide à la décision (évaluations, files SLA, alertes) | Codage couleur, KPI, files d’attente |
| Traçabilité complète | Journal d’actions immuable, conservation **5 ans** (audit) / **10 ans** (pièces, droit tunisien) |
| Conformité | RGPD, consentement biométrique, droit à l’oubli |
| Disponibilité | SLA **99,5 %** |
| Intégration paie | Préparée, **non livrée** |

---

## 3. Périmètre produit

### 3.1 Canaux

| Canal | Public | Rôle |
|---|---|---|
| **RH Connect** (Flutter, iOS / Android) | Collaborateur, RO, chef de département | Saisie, suivi, validation de premier niveau, pointage, organigramme |
| **Plateforme RH** (React, back-office) | RH, Direction, Admin | Référentiel, files d’attente, campagnes, organigramme éditable, tableaux de bord |
| **API Gateway** (point d’entrée unique) | Tous les clients | Authentification JWT, routage, CORS |

Tous les appels clients passent par la gateway (`/api/**`). Les microservices internes ne sont pas exposés directement.

### 3.2 Modules fonctionnels (carte)

| Code | Module | Canal principal | État |
|---|---|---|---|
| **M00** | Référentiel RH, structure, organigramme, comptes | Web + Mobile (lecture) | **Livré** |
| **AUTH** | Identité, rôles, provisionnement de comptes | Tous | **Livré** |
| **NOTIF** | Notifications temps réel (WebSocket) + e-mail | Tous | **Livré** |
| **M01** | Demandes administratives (congé, sortie, mission) | Mobile + Web RH | **Livré** |
| **M02** | Documents administratifs (attestations, bulletins…) | Mobile + Web RH | **Livré** |
| **M03** | Notes de service / actualités + accusé de lecture | Mobile | **Partiel** (fil mock, pas de GED) |
| **M04** | Plaintes internes / externes | Mobile + Web RH | **Livré** (transcription vocale : champ prévu, moteur IA non branché) |
| **M05** | Besoins / demandes de formation + plan annuel | Mobile + Web RH | **Livré** (évaluations à chaud/froid : **cible**) |
| **M06** | Besoins en personnel | — | **Cible** |
| **M07** | Évaluations (semestrielles / annuelles, templates, scoring) | Mobile + Web RH | **Livré** (vague améliorations : matching profil métier, notif/rubrique, UX — voir `docs/sirh_evaluations_vague.md`) |
| **M08** | EPI (catalogue, stock, attributions) | — | **Cible** |
| **M09** | Pointage digital (QR + GPS ; face = Cible) | Mobile + Web RH | **Livré (Must QR+géofence)** ; face/TOTP = Cible |
| **M10** | Recrutement / ATS + matching CV | — | **Cible** |
| **M11** | Chatbot RH (RAG) | — | **Cible** |
| **M12** | Tableaux de bord & analytics | Web | **Partiel** (compteurs d’accueil) |

---

## 4. Acteurs et droits d’accès

### 4.1 Rôles métier

| Rôle | Interface | Responsabilités |
|---|---|---|
| **Collaborateur** (`USER`) | Mobile | Soumettre demandes, consulter ses dossiers, répondre aux évaluations, pointer, consulter l’organigramme |
| **Responsable opérationnel (RO)** (`profil_acces=RO` → JWT `{USER, RO}`) | Mobile | Valider les demandes de **son unité**, déclarer une plainte externe, voir les formations de son périmètre |
| **Chef de département** (`profil_acces=RESPONSABLE` → JWT `{USER, RESPONSABLE}`) | Mobile | Origine des demandes de formation ; pas d’accès Plateforme RH (cette vague) |
| **Responsable RH / RRH** (`RH`) | Web (back-office) | Administration complète : référentiel, files, formations, évaluations, plaintes, organigramme |
| **Direction** (`DIRECTION`) | Web (lecture) | Consultation listes / compteurs ; **pas** d’édition, FIFO, RRH, campagnes |
| **Admin technique** (`ADMIN`) | Web | Même périmètre back-office que RH + administration technique |
| **HSE** | Web + Mobile | Catalogue et attributions EPI (**cible M08**) |
| **Technique / Env. & Social** | Web | Destinataires des plaintes **externes** (cible CDC ; notification RH déjà en place) |

Catalogue unique **fiche `profil_acces` = rôles JWT** (story 1 livrée) :

| `profil_acces` | JWT |
|---|---|
| `COLLABORATEUR` | `{USER}` |
| `RO` | `{USER, RO}` |
| `RESPONSABLE` | `{USER, RESPONSABLE}` |
| `RH` | `{USER, RH}` |
| `DIRECTION` | `{USER, DIRECTION}` |
| `ADMIN` | `{USER, ADMIN}` |

`RO` et `RESPONSABLE` sont **distincts** (plus de mapping RO → RESPONSABLE au provisioning). Tout rôle métier **inclut** `USER`. Le profil se décide **sur la fiche** ; l’organigramme n’y touche plus.

Reste ouvert : types congés + PJ (story 7) ; refresh token 7 j (story 6 — ADR).

### 4.2 Matrice d’accès (règle actuelle du code)

| Capacité | Collaborateur | RO | RH / Admin | Direction |
|---|---|---|---|---|
| Login JWT | Oui | Oui | Oui | Oui |
| SPA Plateforme RH | Non | Non | Oui | Oui (lecture) |
| Créer / suivre ses demandes M01–M02–M04–M05 | Oui | Oui | Oui | Oui (mobile) |
| Valider M01 au niveau supérieur | Si manager nœud | Si manager nœud | Non (passe à l’étape RRH)* | Non |
| Approuver / refuser M01 en dernier ressort | Non | Non | Oui | Non |
| File documents M02, dérogation FIFO | Non | Non | Oui | Non (consultation file) |
| Liste RH plaintes / formations / collaborateurs | Non | Non | Oui | Oui (lecture) |
| Éditer organigramme / unités / fiches | Non | Non | Oui | Non |
| Consulter organigramme | Oui | Oui | Oui | Oui |
| Campagnes & templates d’évaluation (écriture) | Non | Non | Oui | Non |
| Campagnes / scores (lecture) | Non | Non | Oui | Oui |
| Répondre à son évaluation | Oui | Oui | Oui (en tant que USER) | Oui |

Expressions Spring : `BACKOFFICE_LECTURE` = RH\|DIRECTION\|ADMIN ; `BACKOFFICE_ECRITURE` = RH\|ADMIN.  
\*RH/ADMIN traitent l’étape RRH, pas l’étape supérieur (sauf s’ils sont aussi le manager ACTIF du nœud). Jamais sur leur propre demande (D-H03 : 403, un autre RH / ADMIN traite).  
SPA : `ProtectedRoute` exige JWT RH\|DIRECTION\|ADMIN ; Direction = bandeau « Consultation uniquement ».  
Les API collaborateur exigent `hasRole('USER')` ou `isAuthenticated()`.

### 4.3 Authentification

- Login : `POST /api/auth/signin` (identifiant = **matricule** ou e-mail).
- Jeton JWT (issuer gateway). TTL d’accès configurable (`app.jwt.access-ttl-seconds`, défaut **3600 s** dans le code actuel ; CDC : **15 min** + refresh **7 jours** — écart à aligner).
- Claims utiles : `sub`, `roles` / `scope`, identifiant utilisateur.
- Liaison **compte ↔ fiche collaborateur** via `compte_utilisateur_id`.
- À la création d’un collaborateur avec courriel : provisionnement asynchrone du compte (Kafka) + e-mail de bienvenue (mot de passe initial). Les rôles JWT suivent le mapping § 4.1 (`RO` pose `RO`, plus `RESPONSABLE`).
- Un changement ultérieur de `profil_acces` **resynchronise** les rôles du compte lié via Kafka `MAJ_ROLES` (story 2).

---

## 5. M00 — Référentiel RH, structure, organigramme

### 5.1 Unité organisationnelle

<!-- HIÉRARCHIE / M00 — décision PO 27/10/2026 (D-H04) : types de nœud fermés et ordonnés -->

Arbre hiérarchique à **4 niveaux typés** : **Direction Générale** → **Direction** → **Département** → **Service**.

- `code` unique, `libelle`, `actif`
- `type_noeud` ∈ { `Direction Générale`, `Direction`, `Département`, `Service` } (casse et accents tolérés en saisie ; autre valeur → **400**)
- `titre_poste` du management du nœud
- `parent` (optionnel selon le type, voir ci-dessous)
- `manager` (collaborateur assigné au nœud)

**Contraintes**

- **Direction Générale** : **unique**, toujours à la racine (pas de parent).
- **Direction** : toujours rattachée à la Direction Générale ; créée sans parent → rattachée **automatiquement** à la DG (DG inexistante → 400). Ne peut pas être détachée.
- **Département / Service** : parent de niveau **strictement supérieur** (un département sous la DG ou une direction ; un service sous la DG, une direction ou un département). Peuvent être créés **sans parent** : ils sont alors « **à rattacher** ».
- Un service ne contient aucun sous-élément. Un changement de type est refusé s'il devient incompatible avec le parent ou les enfants.
- Code unique (insensible à la casse).
- Un nœud inactif n’apparaît pas dans l’organigramme « standard ».
- Impossible d’affecter un collaborateur sans unité.

### 5.2 Collaborateur (fiche agent)

Données minimales :

- `matricule` unique (sert aussi de login)
- identité (`prenom`, `nom`), `courriel_professionnel`
- `poste`, `fonction`, `qualification_affectation`, `qualite`, `affectation`, `departement_libelle`
- `date_recrutement`
- `statut` (`ACTIF` / autres)
- `profil_acces` : `COLLABORATEUR` \| `RO` \| `RESPONSABLE` \| `RH` \| `DIRECTION` \| `ADMIN` (hors catalogue → 400)
- rattachement **obligatoire** à une unité
- `superieur` (collaborateur, optionnel) — **dérivé** du manager du nœud d’affectation (sync Should — vague hiérarchie H3) ; **ne** constitue **pas** la source de vérité pour valider M01

**Règles**

- Un collaborateur **ACTIF** est visible dans l’organigramme de son unité.
- Valideur M01 1er niveau = **manager ACTIF du nœud d’unité** d’affectation du demandeur (snapshot `valideur_attendu` à la création). Un JWT `RO` d’un autre nœud → **403**.
- Si aucun manager actif sur le nœud : la demande M01 saute l’étape supérieur et part directement en **validation RRH**.
- <!-- HIÉRARCHIE / M00 — décision PO vague hiérarchie (D-H01) --> Le manager du **nœud parent** ne valide **pas** automatiquement les demandes des sous-unités : seule l’unité d’affectation compte.
- <!-- HIÉRARCHIE / M01 — décision PO 27/10/2026 (D-H03, SCRUM-23) --> **Exception — personne ne valide sa propre demande** : si le demandeur est lui-même le manager de son nœud, le valideur 1er niveau est le manager ACTIF du **premier nœud ancêtre** qui en a un (chef de service → chef de département → directeur → DG). Sans ancêtre managé (ex. DG) : passage direct en validation RRH.

### 5.3 Organigramme

<!-- HIÉRARCHIE / M00 — décision PO (D-H02) : structure ≠ chaîne d’approbation multi-niveaux -->

L’organigramme est la **structure organisationnelle éditable** (services / unités, parents, effectifs) **et** le lieu où la RH désigne le **responsable de service** (= valideur M01 1er niveau). Ce n’est **pas** une matrice d’approbation N+1 → N+2 → Direction le long de l’arbre.

- **Lecture** : tout utilisateur authentifié (mobile + web).
- **Écriture** (RH) : créer / modifier un nœud, assigner ou retirer un manager, rattacher un parent.
- Assigner / retirer un manager **ne modifie pas** `profil_acces` (`role_workflow` ignoré s’il est encore envoyé).
- Vue : racines → enfants, membres de l’unité, manager du nœud (signal « Sans responsable » critique pour le routage M01).
- UX Plateforme RH : organigramme en cartes par niveaux. Sélection d’une carte, puis actions explicites : créer en dessous **uniquement les types de niveau inférieur** (DG → Direction / Département / Service ; Direction → Département / Service ; Département → Service ; Service → rien), modifier (dont `titre_poste`), rattacher via une liste limitée aux parents valides, nommer ou retirer le responsable. Le rôle d’accès (`profil_acces`) se lit, il ne se change pas depuis cet écran. Direction = lecture seule.
- <!-- D-H04 --> **Sans sélection**, « Ajouter » crée une direction (rattachée à la DG) ou un département / service **à rattacher**. Dès qu’il existe des éléments à rattacher, l’écran se partage : à **gauche** la liste « À rattacher », à **droite** la hiérarchie complète. Un élément à rattacher se **glisse-dépose** sur une carte de niveau supérieur (cibles valides mises en évidence, cibles interdites grisées) ; le dépôt est **confirmé** (ancien → nouveau parent, SCRUM-6). Les éléments déjà dans la hiérarchie se déplacent par « Rattacher… ».

### 5.4 Provisionnement de compte

À la création d’une fiche avec courriel :

1. Événement Kafka `collaborateur.compte.demande`
2. `svc-identite-acces` crée l’utilisateur (username = matricule) si inexistant
3. Rôles dérivés du `profil_acces`
4. Événement `collaborateur.compte.cree` → liaison `compte_utilisateur_id`
5. E-mail de bienvenue avec mot de passe initial

**Contraintes** : matricule ≤ 100 caractères ; courriel unique côté comptes ; pas de double compte pour un même matricule.

---

## 6. M01 — Demandes administratives

Trois types **distincts**. L’autorisation de sortie **n’est pas un congé** et **n’est pas déduite du solde**.

### 6.1 Workflow commun

<!-- HIÉRARCHIE / M01 — décision PO (D-H01) : 1 niveau manager nœud → RRH ; pas de remontée N+2 / Direction -->

```
Collaborateur soumet
        │
        ▼
  Manager nœud ACTIF ? ──non──► EN_VALIDATION_RRH
        │ oui (snapshot valideur_attendu)
        ▼
EN_VALIDATION_SUPERIEUR
        │
   Manager valide ──► EN_VALIDATION_RRH ──► RRH approuve ──► APPROUVEE
        │                                    │
        └── refuse (motif) ──► REFUSEE       └── refuse (motif) ──► REFUSEE

Demandeur peut ANNULER si statut = EN_VALIDATION_SUPERIEUR ou EN_VALIDATION_RRH
```

- Validation 1er niveau : **uniquement le manager ACTIF du nœud** d’affectation (droit = nœud, pas le seul JWT `RO`). Autre nœud / manager inactif → **403**.
- <!-- D-H03 (SCRUM-23 / SCRUM-24) --> **Personne ne valide sa propre demande.** Chacun passe à son responsable : collaborateur → chef de service → RRH ; chef de service → chef de département → RRH ; chef de département → directeur → RRH ; directeur → DG → RRH ; DG → RRH. Un RH / ADMIN ne traite pas l’étape RRH de sa propre demande : un **autre** RH / ADMIN le fait (sinon **403**). L’historique trace l’identité de l’approbateur RRH.
- **Pas** de validation successive le long de l’arbre (manager du parent, N+2, Direction). `DIRECTION` consulte le BO ; elle **n’est pas** valideur M01.
- Multi-niveaux configurable (ex. congé long → N+2) = **Cible** hors vague actuelle — gate expert RH (`docs/sirh_hierarchie_vague.md` story H4).
- Refus manager ou RRH : **motif obligatoire**.
- Historique d’actions horodaté (création, soumission RO, validation/refus, approbation RRH, annulation).
- Notification à chaque transition (demandeur, manager nœud, RH).

### 6.2 Congé (`CONGE`)

**Champs obligatoires** : `date_debut`, `date_fin`, `type_conge`.

**Catalogue fermé `type_conge`** (validation **serveur** — hors catalogue → **400**) :

| Code | Libellé typique | Pièce jointe |
|---|---|---|
| `ANNUEL` | Congé annuel | Non |
| `MALADIE` | Congé maladie | **Obligatoire** (certificat) |
| `MATERNITE` | Congé maternité | **Obligatoire** (certificat) |
| `SANS_SOLDE` | Congé sans solde | Non |
| `AUTRE` | Autre | Non |

**Règles**

- `date_fin` ≥ `date_debut`.
- `type_conge` ∈ catalogue ci-dessus (enum `TypeConge` dans `svc-referentiel-rh`).
- Pour `MALADIE` et `MATERNITE` : présence d’une PJ dans le contenu JSON — champ `certificat` non vide **ou** `pieces_jointes` non vide — sinon **422** (`CERTIFICAT_OBLIGATOIRE`). `AUTRE` n’exige pas de PJ.
- L’autorisation de sortie (§ 6.3) reste un type de demande **distinct** (≤ 4 h) et n’est pas un congé.
- **Cible CDC (non toutes implémentées)** :
  - solde automatique (dépend de la paie) ;
  - upload multipart / stockage S3 du certificat (aujourd’hui : référence dans le JSON) ;
  - délai minimum avant départ (ex. 48 h pour congé annuel) ;
  - génération PDF + archive S3 à l’approbation.

### 6.3 Autorisation de sortie (`AUTORISATION_SORTIE`)

Sortie courte, **maximum 4 heures** (moins d’une demi-journée). **Ne consomme pas le solde de congés.**

**Champs obligatoires** : `date_jour`, `heure_debut`, `heure_fin`, `motif` (libre).

**Règles (client + serveur)**

- `heure_fin` > `heure_debut`.
- Durée ≤ **240 minutes**. Sinon rejet métier.
- Indicateur de durée en temps réel sur le formulaire mobile.

### 6.4 Ordre de mission (`ORDRE_MISSION`)

**Champs obligatoires** : `lieu`, `date_debut`, `date_fin`, `motif`.

**Règles**

- `date_fin` ≥ `date_debut`.
- **Cible CDC** : coûts estimés, pièces jointes (invitations), PDF signé, archive S3.

### 6.5 Suivi

Le demandeur et la RH voient un suivi d’étapes (RO / RRH) et l’historique.  
Le RO a une file dédiée : demandes `EN_VALIDATION_SUPERIEUR` de son unité.

---

## 7. M02 — Documents administratifs

Demande **directe à la RH** (pas d’étape RO). File unique, **premier arrivé, premier servi (FIFO)**, avec SLA par type.

### 7.1 Types et SLA par défaut

| Type | SLA (heures) | Commentaire |
|---|---|---|
| Attestation de travail | 24 | PDF auto (cible template) |
| Attestation de salaire | 24 | Dépendra du module paie |
| Bulletin de paie | 48 | PDF sécurisé, historique 5 ans (cible) |
| Attestation CNSS | 48 | N° employeur |
| Feuille de pointage mensuelle | **1** | Génération automatique (cible) |
| Document interne | 96 | Motif libre |
| Autre | 120 | |

Les SLA sont **surchargeables** en configuration (`referentiel.evenements.document-sla-heures-par-type.*`).

### 7.2 Statuts

`EN_ATTENTE_FILE` → `EN_TRAITEMENT_RH` → `DISPONIBLE` | `REJETEE`

### 7.3 Règles FIFO

1. File unique chronologique (`cree_le`) : 1ʳᵉ demande = 1ʳᵉ traitée (C-M02-01).
2. Chemin normal : la RH prend **la prochaine** (`prendre-prochaine`) → statut `EN_TRAITEMENT_RH` → clôture **sans** justification.
3. Traiter une demande encore `EN_ATTENTE_FILE` **hors ordre** exige une **justification obligatoire** (`justification_derogation_fifo`) ; l’acteur RH est tracé ; alerte DRH possible.
4. Une demande déjà `EN_TRAITEMENT_RH` se clôture **sans** re-demander de dérogation (déjà prise en tête de file).
5. Sans justification hors ordre : **rejet métier** (pas de saut de file silencieux).
6. À la mise à disposition : référence livrable (URL PDF / S3).
7. Au refus : motif RH obligatoire.

Le collaborateur suit sa demande (étape + SLA restant).  
Voir aussi `docs/sirh_fifo_documents_vague.md`.

---

## 8. M03 — Notes de service / actualités

**Cible CDC**

- Publication par Direction ou RRH, pièces jointes S3.
- Push + WebSocket vers tous ou groupes ciblés.
- **Accusé de lecture obligatoire**.
- Archive searchable, versioning GED, indicateur lu / non lu pour la RH.

**État actuel** : écran mobile « Actualités RH » alimenté par des **données mock**. Pas de backend notes / GED. À traiter comme **maquette UX**, pas comme processus métier.

---

## 9. M04 — Plaintes

### 9.1 Distinction critique

| Type | Auteur | Destinataires |
|---|---|---|
| **INTERNE** | Collaborateur | RH uniquement |
| **EXTERNE** (communauté / usagers) | RO / RH / Technique | RH **et** (cible) Services techniques + Direction Environnement & Social |

### 9.2 Cycle de vie (transitions unidirectionnelles)

```
NOUVEAU → EN_ANALYSE → EN_TRAITEMENT → RESOLU → FERME
```

- Fermeture automatique **cible** : 30 jours sans contestation après `RESOLU`.
- Numéro de ticket lisible : `PLT-YYYYMM-XXXX`.
- Pièces jointes (photos, vidéos, audio) — URLs.
- Champ `transcription_audio` prévu (Whisper) — moteur IA **non livré**.
- Journal d’actions : qui a changé le statut, quand, commentaire.
- RO : action `valider-ro` sur une plainte externe.
- RH : changement de statut + commentaire.

---

## 10. M05 — Formations

### 10.1 Demande de formation (livré)

**Origine** : `CHEF_DEPARTEMENT` ou `RESPONSABLE_OPERATIONNEL` (pas le collaborateur lambda comme demandeur d’origine métier — le demandeur connecté est toutefois un collaborateur).

**Cible de la formation** :

- `UNITE` — une unité organisationnelle ;
- `COLLABORATEURS` — liste nominative.

**Champs** : type, organisme, durée (heures), coût estimé, objectifs pédagogiques, justification, dates souhaitées.

**Workflow**

```
Création → EN_VALIDATION_RRH
              │
              ├── RH intègre au plan → INTEGREE_PLAN
              ├── RH refuse (motif)  → REFUSEE
              └── Demandeur annule   → ANNULEE
```

Le collaborateur voit :

- ses demandes ;
- les formations **où il est cible** ;
- (RO) les demandes de son périmètre.

Historique de workflow conservé.

### 10.2 Évaluations à chaud / à froid (cible, non livrées comme module autonome)

| Évaluation | Déclenchement | Objet |
|---|---|---|
| **À chaud** | J+1 après fin de session (scheduler) | Contenu, formateur, logistique, NPS ; relance J+3 si non répondu |
| **À froid** | M+3 | Impact réel au poste ; comparaison chaud / froid au dashboard RH |

Les formations recommandées depuis une évaluation annuelle (M07) doivent **alimenter automatiquement** M05 (cible).

---

## 11. M06 — Besoins en personnel (cible)

- Formulaire chef de service : poste, profil, date souhaitée, justification.
- Workflow : chef → direction → RH → plan de recrutement.
- Passe-relais vers M10 (publication de poste / ATS).

**Non implémenté** dans le code actuel.

---

## 12. M07 — Évaluations de performance

Module le plus riche du produit actuel. Deux familles, unifiées par **campagnes** et **templates**.

**Vague produit (août 2026)** — coordination PO : [`docs/sirh_evaluations_vague.md`](sirh_evaluations_vague.md). Objectif : industrialiser le matching **famille métier × niveau**, fiabiliser **notification + rubrique mobile** à l’activation, clarifier l’UX (sans changer le scoring ni rouvrir le RBAC story 5). État module carte : **Livré** ; sous-écarts de la vague = améliorations, pas un retour en *Cible*.

### 12.1 Types

| Type campagne | Usage | Statut produit |
|---|---|---|
| `SEMESTRIELLE` | Bilan à mi-parcours (S1 / S2) | **Must / Livré** |
| `ANNUELLE` | Bilan compétences + objectifs N / N+1 | **Must / Livré** |
| `TRIMESTRIELLE` | Cycles courts (population ciblée éventuelle) | **Cible / Could** — hors vague Must (décision PO D-E01) |

### 12.2 Campagne (RH)

Statuts : `PLANIFIEE` → `ACTIVE` → `TERMINEE` | `ANNULEE`.

Une campagne porte :

- période (`annee`, `moisDebut` / `moisFin`, dates) ;
- **template général** (questions périodiques communes) ;
- **template technique / compétences** (optionnel ; fallback campagne si pas de résolution individuelle).

Une évaluation n’est créée que si la campagne est **ACTIVE**. Une seule évaluation par couple (campagne, collaborateur).

Un **scheduler** crée / notifie les évaluations sur campagne active (période). Déclenchement manuel possible pour tests.  
**Vague** : à l’activation / création, notification collaborateur (NOTIF) + rubrique Évaluations visible sur mobile tant qu’une évaluation non archivée existe.

### 12.3 Templates (constructeur RH)

- Types : `GENERIC` (périodique / standard), `TECHNICAL` (compétences / métier), `DRAFT`.
- Cycle de vie template : `DRAFT` → `PUBLISHED` → `ARCHIVED`.
- Questions ordonnées, obligatoire ou non, sections, poids.
- Types de question : texte, paragraphe, QCM, cases, note 1–5, échelle, date, nombre.
- Template technique ciblable par **`famille_metier`** (catalogue RH) × **`niveau_seniorite`** (`JUNIOR`, `CONFIRME` / `CONFIRMED`, `SENIOR`, `TEAM_LEAD`…) — **pas** par `profil_acces`.
- Fiche collaborateur : renseigner `famille_metier` + `niveau_seniorite` (snapshot figé sur l’évaluation à la création — vague E2/E4).
- Résolution : famille+niveau > famille seule > template technique de campagne > GENERIC seul (+ alerte RH si profil incomplet).
- *Écart code actuel* : résolution encore formulée « rôle métier + niveau » / params — à aligner sur la clé produit ci-dessus dans la vague.

### 12.4 Parcours collaborateur / manager (mobile)

Deux étapes séquentielles :

1. **Évaluation générale** — le collaborateur répond d’abord ; le manager ne peut noter une question que si le collaborateur a déjà répondu.
2. Passage à l’étape **technique** : toutes les questions générales **obligatoires** doivent être répondues.
3. **Évaluation compétences** — auto-évaluation (`SkillLevel`) puis notation manager.

Niveaux de compétence : `DEBUTANT` (1) → `SUPERVISE` / `INTERMEDIAIRE` (2) → `AUTONOME` (3) → `AVANCE` (4) → `EXPERT` (5).

**Validation croisée**

| État | Signification |
|---|---|
| `EN_ATTENTE_VALIDATION_CROISEE` | En cours de saisie |
| `VALIDEE_COLLABORATEUR` | Le collaborateur a validé |
| `VALIDEE_SUPERIEUR` | Le manager a validé |
| `VALIDEE` | Les deux ont validé |
| `ARCHIVEE` | Clôturée |

### 12.5 Scoring et alertes

- Notes sur 5 ; score consolidé **sur 20**.
- Score final si notes manager présentes : **70 % manager + 30 % auto-évaluation** (pondération des questions).
- Grille d’appréciation :

| Score /20 | Appréciation |
|---|---|
| ≤ 7 | Insuffisant |
| ≤ 10 | À améliorer |
| ≤ 14 | Satisfaisant |
| ≤ 17 | Positif |
| > 17 | Excellent |

- **Codage couleur** (seuil d’insuffisance configurable, défaut note &lt; 3) :
  - **Vert** : toutes notes à 5, ou moins de 2 critères sous le seuil ;
  - **Orange** : **2** critères sous le seuil → alerte RH ;
  - **Rouge** : **≥ 3** critères insuffisants → **plan d’action obligatoire + escalade DG**.
- Matrice d’écarts self / manager : `ALIGNED` / `MODERATE` / `HIGH` / `CRITICAL`.
- Forces (notes ≥ 4), axes d’amélioration (≤ 2), recommandations automatiques.

**Cible CDC restante** (hors vague évaluations Must) : export PDF + archive S3 ; bascule auto des formations recommandées vers M05 ; évaluations formation chaud/froid (M05).

**Qui note** : collaborateur (auto) + manager / responsable ACTIF du nœud d’affectation (notation croisée). RH administre campagnes / templates et suit les alertes couleur — **pas** de 3ᵉ note RH en Must (Could).

---

## 13. M08 — EPI (cible)

Catalogue HSE (nom, catégorie, taille, stock, péremption, fournisseur).  
Alertes stock bas et péremption &lt; 30 jours.

Workflow d’attribution :

1. Chef de service : demande groupée (type, quantité, justification, priorité).
2. HSE valide (partiel / total), date de livraison.
3. Remise physique + signature numérique du bénéficiaire.
4. PDF de reçu, MAJ stock, archive S3.

Traçabilité complète + reporting mensuel stock. **Aucun service `svc-epi` dans le monorepo actuel.**

---

## 14. M09 — Pointage digital

**État** : **Partiel → Livré (sous-périmètre QR + géofence)** pour la vague Must. Face / TOTP 5 min / HMAC d’intégrité payload CDC restent **Cible**.

Docs de vague : `docs/sirh_pointage_qr_vague.md`, `docs/adr/ADR-M09-pointage-qr-gps.md`, `docs/sirh_pointage_qr_spec.md`, `docs/sirh_pointage_qr_ux.md`.

### 14.1 Principe opérationnel (vague Must — recette)

Pointage **QR + GPS** (pas de face en Must) :

1. RH/ADMIN génère un QR **semi-statique** (validité **90 j**, signé HMAC) depuis la Plateforme RH ; bouton **« Télécharger code QR »** toujours visible ; alerte **J-30**.
2. RH/ADMIN pose / modifie l’**emplacement GPS** du site (mobile sur place).
3. Collaborateur scanne le QR, envoie sa position GPS ; le serveur calcule Haversine et n’accepte que si **≤ 50 m** du site, QR actif et site actif.
4. Types `ENTREE` / `SORTIE` ; horodatage de vérité = serveur ; rejets persistés (`REJETE_HORS_ZONE`, `REJETE_QR_*`, etc.).

Service : **`svc-presence`** (`presence_db`), routes gateway `/api/rh/v1/.../presence/**`.

### 14.1 bis Décisions client du 27/10/2026 (D-P01 à D-P05 — SCRUM-35 à SCRUM-39)

QR **papier et écran**, sites **intérieurs et extérieurs** ; la distance de 10 m n'est pas une exigence ; un rejet de bonne foi est rattrapé par un **pointage manuel validé par le responsable**.

| ID | Décision |
|---|---|
| **D-P01** (SCRUM-35) | Chaque site a un **type de lieu** : `EXTERIEUR` (rayon par défaut **20 m**) ou `INTERIEUR` (**50 m**) ; rayon modifiable par le RH entre **10 et 100 m**. Au pointage, la **précision GPS est obligatoire** et doit être **≤ rayon** (`REJETE_PRECISION_GPS`) ; une **position simulée** (appli de faux GPS détectée par l'OS) est refusée (`REJETE_POSITION_SIMULEE`). |
| **D-P02** (SCRUM-36) | Le mobile remonte l'indicateur « position simulée » (Android `isMock`, iOS `isSimulatedBySoftware`). Écran **test terrain** RH/ADMIN : distance, précision, rayon et verdict en direct. |
| **D-P03** (SCRUM-37) | La pose de l'emplacement du site fait une **moyenne de mesures (~15 s)** pondérée par la précision ; précision exigée **≤ 10 m extérieur / ≤ 25 m intérieur** ; la précision d'ancrage est enregistrée. |
| **D-P04** (SCRUM-38) | Après un rejet, le collaborateur peut **demander un pointage manuel** (justification obligatoire). Valideur = règle **D-H03** ; personne ne valide sa propre demande. Validé → statut **`VALIDE_MANUEL`**. Alerte RH au-delà de **3 pointages manuels par mois** (configurable). |
| **D-P05** (SCRUM-39) | Mode QR par site : **`PAPIER`** (statique 90 j) ou **`ECRAN`** (borne web plein écran, QR **renouvelé toutes les 30 s**, signé HMAC, tolérance ± 1 fenêtre). Un QR d'un mode n'est pas accepté sur un site de l'autre mode. |

### 14.2 Principe cible CDC (anti-fraude complète)

Vision longue : deux étapes séquentielles **QR dynamique (TOTP ~ 5 min) + GPS** puis **reconnaissance faciale on-device** (liveness, seuil similarité défaut **92 %**, mini **80 %**). Seul le score part au serveur, **pas la photo**. Si une étape échoue → **REJET**.

### 14.3 Données d’un pointage

**Must** : collaborateur (JWT), type `ENTREE`/`SORTIE`, `server_ts`, GPS réel, site, distance, `device_id`, statut `VALIDE` / `REJETE_*`.

**Livré (D-P01 à D-P04)** : + précision GPS, indicateur position simulée, statut `VALIDE_MANUEL`.

**Cible** : + score facial, HMAC d’intégrité payload.

### 14.4 Exceptions

- GPS hors zone → `REJETE_HORS_ZONE` (Must) ; alerte RO = Should.
- QR expiré / révoqué / invalide → message clair + log (Must).
- GPS indisponible ou imprécis → rejet (Must) ; **pointage manuel validé par le responsable (D-P04)** ; fallback Wi-Fi = Cible.
- 3 échecs faciaux → alerte RO + blocage 15 min (**Cible**, vague face).

### 14.5 RGPD biométrie (vague face — Cible)

- Consentement signé à l’enrôlement, révocable (C-L-03).
- Vecteur facial chiffré on-device ; pas de photo au serveur au pointage (C-L-04).
- Hors Must actuel : aucune biométrie traitée.

---

## 15. M10 — Recrutement & ATS (cible)

- Publication de poste (depuis M06).
- Parsing CV (PDF), matching 0–100 %, ranking, écarts de compétences.
- Pipeline : Reçu → Présélection → Entretien → Offre → Embauché / Rejeté.
- Embauche → création automatique de fiche + compte (M00).
- Analytics : time-to-hire, conversion, sources.

**Non implémenté.**

---

## 16. M11 — Chatbot RH (cible)

- FAQ + historique des demandes (RAG) dans l’app mobile.
- Questions types : solde, statut de demande, procédures.
- Escalade humaine si échec.
- Analyse de sentiment des plaintes (priorisation).
- Transcription vocale (accessibilité).

**Non implémenté** (`svc-ia` absent du monorepo).

---

## 17. M12 — Tableaux de bord

**Cible RRH** : files en cours, plaintes ouvertes, formations planifiées, absentéisme, turnover, délai moyen, alertes (évaluations en retard, EPI périmés, formations non évaluées).

**Cible Direction** : effectifs par département, tendances de présence, budget formation, rapport mensuel PDF J+1.

**Cible IA prédictive** : absentéisme par unité, risque de départ, plan de formation recommandé.

**État actuel** : page d’accueil web avec **compteurs** (plaintes, demandes, documents en file, collaborateurs). Pas de KPI avancés ni d’export DG.

---

## 18. Notifications (transversal)

Canal unique Kafka `rh.notifications`, consommé par `svc-notification`.

| Type message | Effet |
|---|---|
| `EMAIL` | E-mail texte |
| `WEBSOCKET` | Push temps réel STOMP vers l’utilisateur |
| `BOTH` | Les deux |

Destinataire spécial `RH` : diffusion à **tous les comptes rôle RH**.

Événements métier typiques : demande reçue / validée / refusée / annulée, document disponible, nouvelle plainte, cycle d’évaluation, bienvenue compte.

**Contraintes** : JWT sur le handshake WebSocket ; reconnexion mobile avec backoff ; cloche de notifications sur web et mobile.

---

## 19. Contraintes

### 19.1 Métier

| ID | Contrainte |
|---|---|
| C-M01-01 | Autorisation de sortie ≤ **4 h**, hors solde de congés |
| C-M01-02 | Dates de fin ≥ dates de début (congé, mission) |
| C-M01-03 | Refus RO/RRH : motif obligatoire |
| C-M01-04 | Annulation demandeur seulement avant décision finale (statuts RO ou RRH) |
| C-M01-05 | Validation 1er niveau limitée au **manager ACTIF du nœud** d’unité (snapshot) ; sinon skip vers RRH ; autre nœud / inactif → **403** |
| C-M01-06 | **Personne ne valide sa propre demande** (D-H03) : demande d’un manager de nœud → manager ACTIF du premier nœud ancêtre (sinon RRH) ; RH / ADMIN demandeur → étape RRH traitée par un **autre** RH / ADMIN ; tentative sur sa propre demande → **403** |
| C-M02-01 | File documents **FIFO** ; dérogation justifiée et tracée |
| C-M02-02 | SLA par type, échéance calculée à la soumission |
| C-M04-01 | Plainte interne ≠ externe (destinataires et auteurs) |
| C-M04-02 | Transitions de statut strictement séquentielles |
| C-M05-01 | Intégration au plan / refus : action RH ; annulation : demandeur |
| C-M07-01 | Une évaluation par collaborateur et par campagne |
| C-M07-02 | Le manager ne note qu’après l’auto-évaluation de la question |
| C-M07-03 | Passage étape technique : questions générales obligatoires complètes |
| C-M07-04 | Rouge (≥ 3 critères insuffisants) → plan d’action + escalade DG |
| C-M09-01 | **Vague Must** : pointage `VALIDE` = QR actif valide **et** GPS ≤ rayon du site (serveur, D-P01 : 20 m extérieur / 50 m intérieur par défaut, précision ≤ rayon, position non simulée) ; pas de partiel QR-sans-GPS. **Cible CDC (C-M09-01b)** : QR+GPS **puis** face on-device |
| C-ORG-01 | Collaborateur toujours rattaché à une unité ; matricule unique |

### 19.2 Légales / conformité

| ID | Contrainte |
|---|---|
| C-L-01 | Conservation pièces / données RH : **10 ans** (Tunisie) |
| C-L-02 | Journal d’audit immuable : **5 ans** |
| C-L-03 | RGPD : minimisation, droit à l’oubli, consentement biométrique révocable |
| C-L-04 | Données biométriques : **pas de photo** transmise au serveur au pointage ; traitement on-device |
| C-L-05 | Données IA / ML : **ne quittent pas le SI** (cible) |
| C-L-06 | Bulletins / attestations salaire : accès restreint, canal sécurisé |

### 19.3 Sécurité

| ID | Contrainte |
|---|---|
| C-S-01 | Tous les clients → **gateway unique** ; services internes non exposés |
| C-S-02 | JWT + RBAC ; back-office ≠ rôle `USER` seul |
| C-S-03 | Sessions API **stateless** |
| C-S-04 | Chiffrement données sensibles (cible AES-256) |
| C-S-05 | Idempotence des opérations critiques / événements Kafka |
| C-S-06 | Token interne pour appels service-à-service (notifications → identité) |

### 19.4 Non-fonctionnelles (cibles CDC)

| Catégorie | Cible |
|---|---|
| Temps de réponse API | &lt; 300 ms au P95 |
| Chargement écran mobile | &lt; 2 s en 4G |
| Disponibilité | 99,5 % |
| Push | &lt; 5 s |
| Inférence chatbot | &lt; 3 s |
| Accessibilité web / mobile | WCAG 2.1 AA |
| Offline mobile | File locale + synchro |
| Pagination listes | 20–50 éléments par défaut |
| Rétention GED | S3 / MinIO compatible |

### 19.5 Techniques / architecture (cadre de réalisation)

- Java 21, Spring Boot 3, PostgreSQL par service (pas de JOIN inter-services).
- Découverte Eureka, gateway Spring Cloud, Kafka, Redis, WebSocket STOMP.
- Mobile : Flutter, Riverpod, go_router, Dio, stockage sécurisé.
- Web : React + TypeScript, TanStack Query / client API centralisé.
- Un service = une base ; communication asynchrone par événements.
- Secrets hors dépôt (variables d’environnement).

### 19.6 Organisationnelles

- Le SIRH **ne calcule pas encore la paie** : soldes de congés, attestations de salaire et bulletins restent partiellement manuels ou futurs.
- Multi-sites (pointage) : chaque site a ses bornes QR et son rayon GPS.
- Langue UI : français (i18n : cible ultérieure).

---

## 20. Parcours utilisateurs synthétiques

### Collaborateur (journée type)

1. Se connecte (matricule).
2. Consulte l’accueil : raccourcis Pointage, Congés, Documents, Formations, Plaintes, Évaluations, Organigramme, Autorisation, Actualités.
3. Dépose une demande ; suit le workflow ; reçoit une notification à chaque étape.
4. Pendant une campagne : complète l’auto-évaluation (général puis compétences), valide.
5. Consulte l’organigramme pour identifier son manager / son unité.

### RO / manager de nœud

1. File « à valider » : demandes M01 dont il est le **manager ACTIF du nœud** (snapshot) — pas les demandes des sous-unités dont il n’est pas le manager direct.
2. Approuve ou refuse (motif). Un JWT `RO` d’un autre nœud → **403**.
3. Peut créer une plainte externe et une demande de formation pour son unité / ses collaborateurs.

### RH (back-office)

1. Accueil : volumes plaintes, demandes, documents en file, effectif.
2. Traite la file documents (FIFO ou dérogation justifiée).
3. Approuve les congés / missions après le RO.
4. Intègre les formations au plan annuel.
5. Suit les plaintes (statut + journal).
6. Maintient collaborateurs, unités, organigramme (managers).
7. Construit templates, lance / active des campagnes d’évaluation, suit les scores et couleurs d’alerte.
8. Gère les **sites de pointage** (génération / téléchargement QR, consultation pointages) ; pose GPS site depuis mobile RH/ADMIN.

---

## 21. Hors périmètre immédiat

- Calcul et versement de **paie**, déclarations sociales automatiques.
- Signature électronique qualifiée (eIDAS) — prévue en phase ultérieure.
- Application iOS store-ready distincte (Flutter couvre iOS, publication non traitée ici).
- Multi-langue, multi-société / multi-tenant.
- GED complète (versioning, droits fins par document) — M03.
- Services dédiés absents du monorepo : `svc-demandes`, `svc-formation`, `svc-plainte`, `svc-epi`, `svc-document`, `svc-ia`, `svc-recrutement` (M01–M05 concentrés dans `svc-referentiel-rh` ; M07 = `svc-evaluation` ; M09 Must = **`svc-presence`**).

---

## 22. Hypothèses

1. Un agent a **une** unité d’affectation principale à un instant T.
2. Valideur M01 = **manager ACTIF du nœud** d’affectation (1 niveau puis RRH) ; `superieur` fiche = dérivé **synchronisé** depuis ce manager (Should H3) — pas une chaîne N+1→N+2.
3. Le login est le **matricule**.
4. Les types de congés métier sont un **catalogue fermé** de 5 codes (`ANNUEL`, `MALADIE`, `MATERNITE`, `SANS_SOLDE`, `AUTRE`) — PJ obligatoire pour maladie/maternité ; une liste administrable RH reste une évolution future.
5. L’intégration paie fournira soldes et données d’attestations salaire / bulletins.
6. Pointage Must (QR 90 j + géofence 50 m via `svc-presence`) = engagement de **recette anti-domicile**. TOTP 5 min, face/liveness, HMAC intégrité payload CDC = **pas** encore engagement de recette.
7. Les modules M06, M08, M10, M11 sont du **backlog produit**, pas du périmètre de recette actuel.

---

## 23. TODO / dettes fonctionnelles à trancher

- [x] Catalogue unique `profil_acces` ↔ JWT (6 valeurs ; RO ≠ RESPONSABLE ; organigramme ne mute plus le profil).
- [x] Sync JWT à chaque changement de `profil_acces` (story 2 — Kafka `MAJ_ROLES`).
- [x] Filtrer la Plateforme RH sur JWT `RH` \| `DIRECTION` \| `ADMIN` + Direction lecture (story 4).
- [x] Valideur M01 = manager ACTIF du nœud + snapshot + 403 (story 3 — backend + mobile file).
- [x] M07 RBAC + anti-IDOR (story 5 — backend `svc-evaluation` ; claim JWT collaborateur reste une dette).
- [x] Vague hiérarchie (avant story 7) : UX organigramme pro (H1), clarté règles structure≠chaîne (H2), sync `superieur` (H3 Should) — voir `docs/sirh_hierarchie_vague.md`. Multi-niveaux M01 = Cible (H4, gate RH).
- [x] Vague évaluations M07 Must E1–E5 : annuelle+semestrielle ; matching `famille_metier`×`niveau_seniorite` ; templates par profil ; notif + rubrique mobile — voir `docs/sirh_evaluations_vague.md`. Trimestriel / note RH 3ᵉ voix / PDF+S3 = hors Must.
- [x] FIFO documents M02 (C-M02-01) : clôture de la tête de file sans fausse dérogation ; exception hors ordre avec justification obligatoire — voir `docs/sirh_fifo_documents_vague.md`.
- [ ] Aligner TTL JWT réel (1 h) sur la cible CDC (15 min + refresh 7 j).
- [x] Spécifier les types de congés et pièces jointes obligatoires (story 7 — catalogue fermé 5 valeurs ; PJ `MALADIE`/`MATERNITE` ; validation serveur).
- [ ] Spécifier le solde de congés (en attendant la paie : saisie RH ? import ?).
- [ ] Brancher M03 (notes + accusé de lecture) sur un vrai backend.
- [x] Vague M09 Must QR+géofence : `svc-presence` + BO téléchargement QR + mobile scan/GPS RH — voir `docs/sirh_pointage_qr_vague.md` / spec / ADR. Face + consentement RGPD = vague suivante.
- [ ] Évaluations formation chaud/froid (schedulers M05.B / M05.C).
- [ ] PDF + S3 pour missions, documents, évaluations.
- [ ] Fermeture auto des plaintes à J+30.
- [ ] Dashboard Direction (M12) et rapport mensuel.
- [ ] Consentement biométrique et politique RGPD opérationnelle avant go-live **vague face** M09 (C-M09-01b).

---

## 24. Glossaire

| Terme | Définition |
|---|---|
| **SIRH** | Système d’information des ressources humaines |
| **RO** | Responsable opérationnel de l’unité |
| **RRH** | Responsable ressources humaines |
| **FIFO** | Premier arrivé, premier servi (file documents) |
| **SLA** | Délai maximal de traitement d’un document |
| **CDC** | Cahier des charges (vision v2) |
| **Campagne** | Période d’évaluation RH (semestrielle ou annuelle ; trimestrielle = cible) |
| **Template** | Questionnaire réutilisable (général / standard ou compétences / technique) |
| **Famille métier** | Catalogue RH pour matcher le template technique d’évaluation (ex. développement, génie civil) — distinct du `profil_acces` |
| **Niveau de séniorité** | Junior / Confirmé / Senior / Team lead — 2ᵉ axe du matching template technique |
| **GED** | Gestion électronique de documents |
| **ATS** | Applicant Tracking System (recrutement) |

---

*Document généré comme base fonctionnelle unique du dépôt. Toute évolution métier (nouveau type de demande, nouveau rôle, nouveau SLA) doit mettre à jour ce fichier en même temps que le code.*
