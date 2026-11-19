# ADR — Moteur de workflow paramétrable par société et passage au multi-société

| | |
|---|---|
| **Statut** | **Accepté** par l'utilisateur le 17/11/2026 (avant le lot W0) |
| **Décideurs** | Utilisateur (porteur produit), architecte (Claude, session du 17/11/2026) |
| **Spécification liée** | `docs/specs/SPEC-moteur-workflow.md` (règles D-W, D-SOC, contrat d'API, plan de lots) |
| **Audits pris en compte** | `docs/audit/audit_production_2026-11-14.md` (T-06, T-09 à T-12, T-15, T-17, E-05, E-06), `docs/audit/audit_scalabilite_resilience_2026-11-14.md` (R-01, R-03, R-06, R-08, R-12) |

## 1. Contexte

- Le SIRH est **mono-société** : aucune table ne porte d'identifiant de société, le matricule sert d'identifiant de connexion unique, le destinataire de notification `RH` vise tous les comptes RH de la base.
- Les circuits de validation sont **codés en dur** dans chaque service :
  - M01 (`DemandeAdministrativeRhService`, svc-referentiel-rh) : manager du nœud D-H03 → RRH, statuts `EN_VALIDATION_SUPERIEUR` / `EN_VALIDATION_RRH` ;
  - M09 (svc-presence) : pointage manuel → valideur D-H03 avec expiration 24 h ; heures sup RO → RH ;
  - M02 / M05 (svc-referentiel-rh) : files RH propres.
- Besoin client (17/11/2026, incomplet) : chaque société définit **ses** procédures (étapes, intervenants, conditions, délais, escalades, notifications), peut avoir un workflow qu'une autre n'a pas, et a **ses** réglages de travail (40 h ou 48 h par semaine, jours travaillés, fériés…).
- Le développement sera fait par un autre modèle (Gemini, Antigravity) en suivant la spécification à la lettre ; la revue reste chez Claude (Opus).

## 2. Décisions

### D1 — Multi-société : base partagée, schéma partagé, colonne `societe_id` sur chaque table métier

- Chaque entité métier de chaque service porte `societe_id uuid not null`, annotée **`@org.hibernate.annotations.TenantId`** (Hibernate 7, Spring Boot 4) : Hibernate renseigne la colonne à l'insertion et filtre **toutes** les requêtes JPQL / Criteria.
- La société courante vient d'un `CurrentTenantIdentifierResolver` qui lit un contexte de requête (`SocieteContexte`) alimenté :
  - par le **claim JWT `societe_id`** sur les routes publiques (jamais par un paramètre, un corps ou un en-tête venant du client) ;
  - par l'en-tête **`X-Societe-Id`** sur `/internal/**`, accepté **seulement** si `X-Internal-Token` est valide ;
  - par l'en-tête Kafka **`societe_id`** dans les consommateurs ;
  - explicitement (`SocieteContexte.executer(societeId, …)`) dans les planificateurs, qui bouclent sur les sociétés actives.
- Les requêtes **natives** ne sont pas filtrées par Hibernate : chacune contient `societe_id = :societeId` (contrôlé en revue et par un test d'architecture).
- Toutes les contraintes d'unicité métier incluent `societe_id` (matricule, codes, numéros lisibles, codes de paramètre…).
- **Registre des sociétés** : table `societe` dans **svc-identite-acces** (la connexion doit refuser une société suspendue). Les réglages métier de la société (temps de travail, calendrier, congés) vivent dans **svc-referentiel-rh** (`rh_societe_parametres`), les réglages de pointage et d'évaluation restent dans leurs services, désormais par société.
- **Reprise de l'existant** : svc-identite-acces crée au démarrage la société par défaut (code lu dans `SOCIETE_DEFAUT_CODE`, défaut `PRINCIPALE`) ; chaque service obtient son identifiant par `GET /internal/identite/v1/societes/par-code/{code}` puis un runner (`SocieteRepriseRunner`) renseigne `societe_id` sur toutes les lignes existantes, pose `NOT NULL` et recrée les contraintes d'unicité, sous `pg_advisory_xact_lock`. Aucun UUID en dur.
- Code commun (contexte, filtre, résolveur, propagation HTTP et Kafka, verrou de planificateur) dans un **nouveau module Maven `sirh-commun`** (bibliothèque, pas un service), dépendance de chaque service.

**Alternatives écartées**
- *Une base ou un schéma par société* : isolation plus forte, mais N bases × 6 services, migrations et pools multipliés, impossible à exploiter avec `ddl-auto=update` et sans Flyway (T-06). Gardé pour le palier 3 (R-12) : la colonne `societe_id` permet de partitionner plus tard.
- *Filtre Hibernate `@Filter` manuel* : oubli facile ; `@TenantId` est appliqué par défaut.
- *Sécurité au niveau des lignes PostgreSQL (RLS)* : défense en profondeur souhaitable, mais exige `SET app.societe` à chaque transaction sur un pool partagé ; reportée à un lot ultérieur, notée dans le § 6.

### D2 — Un service dédié `svc-workflow` (port 8087, base `workflow_db`)

Le moteur est un **nouveau microservice**, pas un module de svc-referentiel-rh.

| Critère | Service dédié | Module du référentiel |
|---|---|---|
| Domaines servis | M01, M02, M05 (référentiel), M09 (présence), demandes personnalisées et processus (nouveaux) | Le référentiel deviendrait le point de passage de la présence : couplage inverse |
| Croissance des données | Instances, tâches, historique (5 ans) : la table la plus volumineuse après les pointages | Alourdit une base déjà centrale (fiches, demandes, soldes, imports) |
| Planificateurs | Échéances chaque minute, relais d'envoi : isolés, mis à l'échelle seuls | Partagent threads et pool avec les imports et les écrans RH (R-09) |
| Pannes | Moteur arrêté : les demandes existantes restent lisibles, seules les décisions attendent | Une panne du référentiel arrête tout |
| Coût | Un service, une base, une route gateway, des appels internes | Aucun appel réseau |

Le coût réseau est accepté ; il est encadré par D4 (protocole d'effets) et des délais courts (connexion 1 s, lecture 3 s).

**Alternatives écartées** : moteur BPMN embarqué (Camunda 7 en fin de support, Camunda 8 sous licence pour la production, Flowable 7 : lourd, compatibilité Spring Boot 4 non garantie, multi-société et éditeur à reconstruire, modèle BPMN trop riche pour la RH et pour un développement par un tiers). Un moteur maison **borné** (D3) est plus simple à tester, à relire et à expliquer au client.

### D3 — Modèle de workflow : séquence d'étapes en avant seulement, définition JSON immuable par version

- Un **modèle** (par société, lié à un type d'objet et éventuellement à un sous-type, ex. `CONGE` / `MALADIE`) a des **versions** : `BROUILLON` (modifiable) → `PUBLIEE` (immuable, une seule à la fois par modèle) → `RETIREE`.
- La définition d'une version est un **document JSON** (format 1, schéma dans la spec § 9.3) stocké en `jsonb`, avec une empreinte SHA-256 ; il est validé par le serveur à l'enregistrement et à la publication.
- Une étape est de type `VALIDATION`, `TACHE`, `TACHES_PARALLELES` ou `INFORMATION`. Les branchements se font par **condition d'activation** (étape sautée si la condition est fausse) et par **saut en avant** (`aller_a` vers une étape **plus loin** ou une fin). Le seul retour en arrière est le **renvoi pour complément** au demandeur (3 fois au plus), qui revient à la même étape. Conséquence : **aucun cycle**, terminaison garantie, validation statique simple.
- Les conditions sont un **arbre JSON** (champ / opérateur / valeur, `et` / `ou` / `non`, profondeur ≤ 5, ≤ 20 feuilles) évalué par du code Java ; **aucun langage d'expression** (pas de SpEL, pas de script) : pas d'injection possible.
- Une **instance** référence la version avec laquelle elle a démarré ; une nouvelle version ne la modifie jamais.

### D4 — L'objet métier reste chez son propriétaire ; le moteur orchestre et appelle des « effets » idempotents

- Le service propriétaire (référentiel pour une demande M01, présence pour un pointage manuel, le moteur lui-même pour une demande personnalisée) **garde l'objet et ses règles** (solde, chevauchement, pièces jointes).
- **Démarrage** : le propriétaire écrit l'objet avec `workflow_etat = A_DEMARRER` dans sa transaction ; après la validation de celle-ci, il appelle `POST /internal/workflow/v1/instances` avec la clé `objet_type:objet_id`. En cas d'échec, un planificateur du propriétaire rejoue (la clé rend l'appel idempotent).
- **Décision** : quand une décision a un **effet** chez le propriétaire (contrôle avant approbation, approbation finale, refus final, expiration), le moteur suit un protocole en deux temps :
  1. hors transaction : évaluation des conditions, résolution des intervenants de l'étape suivante, contrôle `CONTROLER` chez le propriétaire (lecture seule) ;
  2. transaction courte : verrou de l'instance puis de la tâche, vérifications, tâche `RESERVEE` + ligne `wf_appel_effet` ;
  3. hors transaction : appel `APPLIQUER` chez le propriétaire avec la clé d'idempotence ;
  4. transaction courte : décision enregistrée, étape suivante créée, événements écrits dans la table d'envoi.
  Un refus métier du propriétaire (ex. 422 `SOLDE_INSUFFISANT`) rend la tâche à `A_TRAITER` ; une réponse incertaine (délai dépassé) laisse la tâche `RESERVEE` et un planificateur rejoue le même appel jusqu'à une réponse définitive.
- Aucun appel HTTP n'est fait dans une transaction ni sous verrou (T-12).

### D5 — Intervenants résolus par le référentiel, figés sur la tâche

- Les règles d'intervenant (`MANAGER_NOEUD` = D-H03, `MANAGER_NIVEAU`, `RESPONSABLE_TYPE`, `ROLE`, `FONCTION`, `COLLABORATEUR`, `DEMANDEUR`) sont résolues par **un appel groupé** `POST /internal/referentiel/v1/workflow/resolutions`, hors transaction, avant la création des tâches.
- La liste des candidats est **figée** sur la tâche (`wf_tache_candidat`), comme le `valideur_attendu` actuel. Elle ne change que par délégation, escalade ou réaffectation RH, toujours tracées.
- **Invariant non paramétrable** : le demandeur et les bénéficiaires ne sont jamais candidats, y compris par délégation, rôle, fonction ou escalade.
- Nouvelle notion du référentiel : **fonctions** par société (ex. « Gestionnaire paie », « Responsable HSE ») affectées à des collaborateurs, avec un périmètre d'unité facultatif.

### D6 — Concurrence, idempotence, planificateurs

- `@Version` sur modèle, version, instance, tâche, délégation ; verrou pessimiste `SELECT … FOR UPDATE` sur l'instance **puis** la tâche (toujours cet ordre) pour chaque décision.
- Clé d'idempotence fournie par le client sur chaque décision (unique par tâche) ; clé de démarrage unique par objet.
- Planificateurs du moteur (échéances, relais d'envoi, reprise des effets) : sélection par lots `FOR UPDATE SKIP LOCKED` → sûrs à plusieurs instances sans verrou global. Les 8 planificateurs existants reçoivent le verrou consultatif de `sirh-commun` (`VerrouPlanificateur`, T-11).

### D7 — Événements : table d'envoi (outbox) puis Kafka

- Le moteur écrit ses événements et ses notifications dans `wf_outbox` **dans la transaction métier** ; un relais publie sur `rh.workflow.evenements` (clé = identifiant d'instance) et `rh.notifications` (format existant, adressé au compte), et marque la ligne (R-06).
- Les propriétaires consomment `rh.workflow.evenements` pour tenir une **projection** (étape courante, échéance) affichée dans leurs listes ; la décision fait foi dans le moteur.
- Chaque message porte l'en-tête et le champ `societe_id`. svc-notification résout le destinataire `RH` **dans la société du message**.

### D8 — Migration progressive et rétrocompatibilité

- Interrupteur par **société × type d'objet** (`wf_activation`) : `HERITE` (circuit actuel codé en dur, défaut) ou `MOTEUR`.
- Les objets créés avant le passage à `MOTEUR` terminent sur le circuit hérité ; les nouveaux démarrent une instance.
- Pour la société par défaut, des modèles amorcés reproduisent **exactement** les circuits actuels (D-H03 → RRH, pointage manuel 24 h, heures sup RO → RH) ; la bascule se fait type par type après recette de non-régression.
- Les contrats d'API existants (`/api/referentiel/v1/demandes…`, présence) restent valables ; les réponses gagnent des champs (`workflow_mode`, `workflow_instance_id`, `etape_courante_libelle`, `echeance_le`), aucun champ n'est retiré.

## 3. Conséquences

**Positives** : procédures propres à chaque société sans code ; délégation, relance et escalade (E-05) ; historique homogène de toutes les décisions ; préparation des processus d'arrivée et de départ (E-06) ; tenant présent partout pour le palier 3.

**Négatives / risques**
- Chantier de tenantisation large (5 services, ~60 entités) : risque de fuite entre sociétés si une requête native oublie le filtre → test d'architecture, tests d'isolation par service, revue dédiée.
- Un service et une base de plus à exploiter (supervision T-08, sauvegarde T-07).
- Décisions en deux temps : état transitoire `RESERVEE` visible quelques secondes ; à expliquer dans l'interface (« Décision en cours d'enregistrement »).
- Identifiant de connexion : le matricule n'est plus unique globalement → code société à la connexion (question client Q-SOC-01).

## 4. Sécurité (STRIDE léger)

| Menace | Parade |
|---|---|
| Usurpation de société (S) | Société lue dans le JWT signé ; `X-Societe-Id` accepté seulement avec `X-Internal-Token` valide ; jeton sans claim `societe_id` → 401 |
| Altération d'un workflow pour s'auto-valider (T, E) | Invariant : demandeur et bénéficiaires exclus de tout candidat ; journal des modèles (auteur, avant / après) ; publication avec confirmation ; option « quatre yeux » (Q-WF-12) |
| Répudiation (R) | Historique append-only : acteur (compte JWT), « pour le compte de » en délégation, horodatage serveur, conservé 5 ans |
| Fuite entre sociétés (I) | `@TenantId`, filtre explicite des requêtes natives, identifiant d'une autre société → 404 identique à « introuvable », clés de cache préfixées par la société |
| Déni de service (D) | Taille de définition ≤ 256 Ko, ≤ 30 étapes, ≤ 10 candidats en mode « tous », pagination en base, limites de débit gateway (R-01) |
| Élévation (E) | `@PreAuthorize` sur chaque point d'entrée ; DIRECTION jamais décideur ; rôle `PLATEFORME` sans accès aux données RH |

## 5. Performance (cibles, à mesurer au lot W3 puis T-16)

- « Mes tâches » : p95 < 200 ms avec 10 000 tâches ouvertes dans la société (index candidat × compte, pagination en base).
- Décision sans effet : p95 < 300 ms ; avec effet : p95 < 800 ms (appel propriétaire compris).
- Échéances : 1 000 tâches échues traitées en < 60 s.
- Versions publiées mises en cache sans expiration (immuables) ; catalogue des types en cache 5 min.

## 6. Suites hors périmètre de cet ADR

- RLS PostgreSQL par société ; base par groupe de sociétés (R-12, palier 3).
- Migration Flyway (T-06) : si elle est livrée avant le lot W1, les runners de reprise deviennent des migrations, mêmes règles.
- Éditeur graphique libre (canevas) : non retenu, l'éditeur est une liste d'étapes.
