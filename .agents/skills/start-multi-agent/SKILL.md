---
name: start-multi-agent
description: "Orchestration multi-agents SIRH à modèles dédiés (Swarm) déclenchée par « /start-multi-agent <prompt> » ou « /start-multi-agent reprends <nom> ». Enchaîne en séquence ou en parallèle selon les dépendances : Gemini Neon High (PO fonctionnel, faisabilité, benchmark systèmes réels + délégation scraping web à Gemini 3.8 Flash), Gemini 4 Carbon High (architecture prod-ready, system design, algorithmes, scalabilité, Java 21 virtual threads & async), 3 développeurs seniors Gemini 4 Hydrogen en parallèle (Backend Java 21 / Spring Boot 4 SOLID/DRY/KISS/POO, Frontend React, Mobile Flutter — implémentant strictement les besoins de Carbon sans extra), Gemini 3.8 Flash (tests & recette), Gemini 4 Carbon (code review technique) et Gemini Neon (décision GO fonctionnelle finale)."
---

# Orchestrateur `/start-multi-agent` (Neon · Carbon · Hydrogen ×3 · Flash)

Tu es le **coordinateur** de l'essaim multi-agents. Ce skill t'**autorise et t'oblige explicitement à utiliser l'outil `swarm`** d'Antigravity afin d'épingler chaque agent sur le modèle exact demandé par l'utilisateur et de faire travailler les agents **séquentiellement ou simultanément** selon leurs dépendances réelles.

---

## 1. Cartographie stricte des rôles et des modèles

Dès le démarrage, appelle `swarm(action="models", all=True)` pour lire les identifiants exacts disponibles dans la session et associer chaque worker au modèle demandé :

| Roster (`name`) | Modèle cible à épingler (`model`) | Rôle & Responsabilité | Mode d'exécution |
|---|---|---|---|
| `po-neon` | **Gemini Neon (High)** *(ex. `Gemini 4 Neon (High)` / identifiant contenant `neon` + `high`)* | **Product Owner & Expert Fonctionnel** (`sirh-expert-rh`) : analyse du besoin, faisabilité, recherche de systèmes réels similaires, user stories, critères d'acceptation, puis **GO / NO-GO fonctionnel final** en fin de cycle. | Étape 1 (cadrage) puis Étape 6 (décision finale) |
| `scraper-flash` | **Gemini 3.8 Flash** *(identifiant contenant `3.8` et `flash`, sinon le modèle `Flash` le plus récent)* | **Chercheur & Scraper Web** : invoqué **à la demande de `po-neon`** pour chercher/analyser sur Internet le fonctionnement de systèmes SIRH existants (docs publiques, benchmarks fonctionnels, UX) et synthétiser les faits sourcés. | Optionnel / en parallèle pendant l'Étape 1 |
| `architect-carbon` | **Gemini 4 Carbon (High effort)** *(identifiant contenant `carbon` + `high`, sinon `carbon`)* | **Architecte Système & Sécurité** (`sirh-architecte`) : décisions techniques expertes, system design, algorithmes, scalabilité, Java 21 Virtual Threads & programmation asynchrone, sécurité, application prod-ready, contrats d'API et frontières de dossiers disjointes. | Étape 2 (peut explorer le code en parallèle de `scraper-flash`, fige le design après `po-neon`) |
| `dev-backend` | **Gemini 4 Hydrogen** *(identifiant contenant `hydrogen` / `hydrogene`)* | **Senior Backend Developer** (`sirh-dev-backend`) : Java 21, Spring Boot 4.0.3, SOLID, DRY, KISS, POO propre, sécurité JWT/`@PreAuthorize`, JUnit 5. **Implémente strictement ce que Carbon demande, sans aucun extra.** | Étape 3 — **en parallèle** avec `dev-web` et `dev-mobile` |
| `dev-web` | **Gemini 4 Hydrogen** | **Senior Frontend React Developer** (`sirh-dev-web`) : React 18 / TypeScript / Vite (`rh-admin-web`). Applique exactement les besoins et contrats de Carbon, sans extra. | Étape 3 — **en parallèle** avec `dev-backend` et `dev-mobile` |
| `dev-mobile` | **Gemini 4 Hydrogen** | **Senior Mobile Flutter Developer** (`sirh-dev-mobile`) : Flutter compatible 3.29.2 (`rh_mobile_app`, jamais `rh_mobile_app_temp/`). Applique exactement les besoins et contrats de Carbon, sans extra. | Étape 3 — **en parallèle** avec `dev-backend` et `dev-web` |
| `qa-flash` | **Gemini 3.8 Flash** | **Ingénieur QA — Tests & Recette** (`sirh-testeur`) : exécution des tests unitaires/intégration et recette de bout en bout (200, 400, 403 anti-IDOR, 404, 409, 422). | Étape 4 (après convergence des devs) |
| `reviewer-carbon` | **Gemini 4 Carbon (High effort)** *(worker distinct et neuf, n'ayant écrit aucun code)* | **Technical Code Reviewer** (`sirh-code-review`) : revue approfondie du `git diff` (conformité stricte au plan Carbon, zéro code superflu, algorithmes, Virtual Threads/async, SOLID, sécurité, tests réels) → **GO / NO-GO technique**. | Étape 5 (après recette QA verte) |

### Gestion de disponibilité ou de quota des modèles
- Si un nom exact (ex. `Neon`, `Carbon`, `Hydrogen`, `3.8 Flash`) porte un identifiant technique différent dans la sortie de `swarm(action="models", all=True)`, choisis la correspondance exacte dans cette liste et affiche la table de correspondance retenue en 5 lignes au démarrage.
- Si un modèle n'apparaît pas dans `swarm(action="models", all=True)` ou tombe en erreur de quota (`429 RESOURCE_EXHAUSTED`), **ne tue jamais le worker** (`kill` interdit pour changer de modèle) : utilise `swarm(action="model", name="<worker>", model="<fallback>")` en expliquant la raison à l'utilisateur.
- Si l'environnement courant ne supporte pas `swarm` (ex. session CLI restreinte), bascule proprement sur `invoke_subagent` (`pro` pour Neon/Carbon/Hydrogen, `flash` pour Flash) en prévenant l'utilisateur dès l'Étape 0.

---

## 2. Règles d'orchestration : Séquentiel vs Simultané

```mermaid
flowchart TD
    U["/start-multi-agent + prompt"] --> S0["0. Init worktree + etat.md + swarm(models)"]
    S0 --> PO["1. po-neon (Gemini Neon High)<br/>PO, faisabilité, benchmark"]
    PO -. "si besoin d'infos web" .-> SCRAP["1b. scraper-flash (Gemini 3.8 Flash)<br/>Scraping / recherche systèmes similaires"]
    SCRAP -. "synthèse sourcée" .-> PO
    PO --> ARC["2. architect-carbon (Gemini 4 Carbon High)<br/>System design, algos, Virtual Threads, async, contrats"]
    ARC --> B["3a. dev-backend (Gemini 4 Hydrogen)<br/>Java 21 / Spring Boot 4 / SOLID / DRY / KISS"]
    ARC --> W["3b. dev-web (Gemini 4 Hydrogen)<br/>React 18 / TypeScript (rh-admin-web)"]
    ARC --> M["3c. dev-mobile (Gemini 4 Hydrogen)<br/>Flutter 3.29.2 (rh_mobile_app)"]
    B & W & M --> QA["4. qa-flash (Gemini 3.8 Flash)<br/>Tests auto & Recette E2E"]
    QA -- "KO -> correctif ciblé" --> B & W & M
    QA -- "Recette OK" --> REV["5. reviewer-carbon (Gemini 4 Carbon)<br/>Technical Code Review (GO / NO-GO)"]
    REV -- "NO-GO -> correctif ciblé" --> B & W & M
    REV -- "GO Technique" --> GO["6. po-neon (Gemini Neon)<br/>Décision GO Fonctionnelle Finale"]
    GO -- "NO-GO Fonctionnel" --> B & W & M
    GO -- "GO Fonctionnel" --> FIN["7. Rapport final -> attente 'livre' / 'démo'"]
```

1. **Parallélisme intelligent** :
   - **Étape 1** : `po-neon` démarre immédiatement. S'il faut étudier des solutions du marché sur le web, lance `scraper-flash` pendant que `po-neon` inspecte `docs/sirh_basefonctionnelle.md` et l'existant via `graphify_query`. Si le périmètre technique à explorer dans le dépôt est déjà évident, `architect-carbon` peut lancer sa lecture `graphify` en parallèle (lecture seule), mais il ne publie son contrat final qu'après la spec de `po-neon`.
   - **Étape 3** : Lance **dans le même tour d'appels `swarm(action="add", ...)`** tous les développeurs `Hydrogen` requis par `architect-carbon` (1, 2 ou les **3 simultanément**) dès lors que leurs périmètres de fichiers sont strictement disjoints :
     - `dev-backend` → `svc-*`, `gateway/`, `pom.xml`
     - `dev-web` → `rh-admin-web/`
     - `dev-mobile` → `rh_mobile_app/`
     - Si deux développeurs doivent compiler/tester lourdement en parallèle sans gêner l'indexation de l'autre, utilise `workspace="share"` (chacun reçoit sa copie de travail du dépôt, que tu fusionnes avant l'Étape 4) ; sinon `workspace="inherit"` sur le worktree dédié suffit grâce aux dossiers disjoints.
     - N'instancie **pas** un développeur dont le canal est explicitement « Hors périmètre / Aucun changement » dans `02-architecture-carbon.md`.
2. **Autonomie des briefs** : Par défaut, un worker `swarm` démarre sans l'historique de ta conversation. Chaque `brief` passé à `swarm(action="add", ...)` doit donc indiquer **les chemins exacts** du worktree et des documents `docs/autopilote/<nom>/...` à lire et à produire.

---

## 3. Déroulement détaillé étape par étape

### Étape 0 — Initialisation & Worktree (Coordinateur)
1. Lis `CONTEXTE.md`, `.agents/rules/00-projet.md` à `03-boucle-qualite.md`, et interroge `graphify_query` sur le domaine touché.
2. Si l'utilisateur a écrit `/start-multi-agent reprends <nom>` : lis `docs/autopilote/<nom>/etat.md` et reprends directement à l'étape indiquée.
3. Sinon, crée :
   - un dossier de suivi `docs/autopilote/<AAAAMMJJ>-<slug>/` avec `etat.md` ;
   - un worktree Git dédié pour ne jamais polluer `delivery` avant validation :
     ```bash
     cd /c/Local/Khaled/project-delivery && git worktree add -b ma/<slug> ../project-<slug> delivery
     ```
4. Appelle `swarm(action="models", all=True)` puis `swarm(action="title", title="SIRH — <slug>")`.

---

### Étape 1 — Product Owner, Faisabilité & Benchmark (`po-neon` + `scraper-flash` si nécessaire)
1. Ajoute `po-neon` sur **Gemini Neon High** (`workspace="inherit"`).
2. **Consignes impératives dans le brief de `po-neon`** :
   - Appliquer `.agents/skills/sirh-expert-rh/SKILL.md` et lire `docs/sirh_basefonctionnelle.md` + `CONTEXTE.md`.
   - Évaluer la **faisabilité métier et produit** dans le SIRH RH-Évènement (sans paie, rôles `COLLABORATEUR`, `RO` / manager de nœud, `CHEF_DEPARTEMENT`, `RH`, `DIRECTION`, `ADMIN`).
   - Chercher comment fonctionnent des **systèmes SIRH réels similaires** (ex. Lucca, Factorial, BambooHR, Personio, Kelio, Workday). Si une recherche Internet fraîche est utile, demander sur le board `@scraper-flash` (ou laisser le coordinateur spawner `scraper-flash` sur **Gemini 3.8 Flash**) avec les questions précises à vérifier sur le web.
   - **Consignes pour `scraper-flash` (Gemini 3.8 Flash)** lorsqu'il est sollicité :
     - Utiliser `search_web` et la lecture de documentations publiques pour extraire les bonnes pratiques ergonomiques, règles métier standards et cas limites des SIRH du marché.
     - Respecter strictement les garde-fous : ne jamais contourner un CAPTCHA, un mur d'authentification ou un contrôle d'accès ; s'en tenir aux sources publiques et citer les URLs dans `docs/autopilote/<nom>/01b-benchmark-flash.md`.
   - Rédiger `docs/autopilote/<nom>/01-po-neon-spec.md` contenant :
     1. Vision PO & analyse de faisabilité ;
     2. Synthèse comparative des systèmes similaires et ce qu'on retient (sans sur-complexifier) ;
     3. Règles métier numérotées et chiffrées (`R-01`, `R-02`…) alignées avec `sirh_basefonctionnelle.md` ;
     4. User stories & critères d'acceptation testables (`CA-01`… : rôle, action, résultat attendu, codes HTTP `200 / 400 / 403 / 404 / 409 / 422`) ;
     5. Périmètre par canal (`backend`, `web`, `mobile`) et **Hors périmètre explicite** ;
     6. Questions métier ouvertes éventuelles (chacune avec une **option recommandée par défaut**).
3. **Point d'arrêt conditionnel (Arrêt 1)** :
   - Si `01-po-neon-spec.md` lève une vraie ambiguïté métier non tranchée dans `sirh_basefonctionnelle.md`, présente les questions (≤ 25 lignes, avec recommandations par défaut) à l'utilisateur et attends sa réponse avant l'Étape 2.
   - Si le besoin utilisateur et `sirh_basefonctionnelle.md` suffisent sans ambiguïté, enchaîne immédiatement sur l'Étape 2.

---

### Étape 2 — Architecture & System Design Prod-Ready (`architect-carbon`)
1. Ajoute `architect-carbon` épinglé sur **Gemini 4 Carbon (High effort)** (`workspace="inherit"`).
2. **Consignes impératives dans le brief de `architect-carbon`** :
   - Appliquer `.agents/skills/sirh-architecte/SKILL.md`, `.agents/rules/00-projet.md` et `.agents/rules/02-securite.md`.
   - S'appuyer sur `01-po-neon-spec.md` et inspecter le code réel via `graphify_query` / lectures ciblées.
   - Concevoir une solution **Production-Ready** couvrant explicitement :
     - **System Design & Algorithmes** : structures de données, complexité temporelle et mémoire, requêtes SQL/JPA sans N+1, pagination/streaming ou traitement par lots si volumétrie élevée, index recommandés, idempotence.
     - **Scalabilité, Virtual Threads Java 21 & Asynchrone** :
       - Tirer parti des **Virtual Threads Java 21** (`Executors.newVirtualThreadPerTaskExecutor()` ou exécuteur dédié) lorsque plusieurs appels E/S indépendants (ex. appels REST internes `/internal/**`) peuvent être parallélisés proprement.
       - Garantir l'absence de *thread pinning* (`ReentrantLock` plutôt que `synchronized` autour d'E/S), **aucun appel HTTP ou envoi Kafka à l'intérieur d'une transaction `@Transactional`**, timeouts explicites de connexion/lecture, et propagation sûre des contextes (`SecurityContext`, `SocieteContexte` / identifiants explicites passés aux tâches enfants).
       - Utiliser Kafka (`KafkaTemplate<String, String>` JSON, jamais auto-configuré implicitement en Boot 4) ou `@TransactionalEventListener(phase = AFTER_COMMIT)` quand un découplage asynchrone post-commit est pertinent.
     - **Contraintes d'architecture SIRH non négociables** :
       - Monorepo Spring Boot 4.0.3 / Java 21, routage via `gateway`, une base par service, appels inter-services via `/internal/**` + `X-Internal-Token`.
       - **Jamais** de bean `RestClient.Builder` ou `RestTemplate` `@LoadBalanced` (casse Eureka).
       - `ddl-auto=update` : toute nouvelle valeur d'enum stockée en base avec contrainte CHECK exige un `ApplicationRunner` dédié.
       - Sécurité : `@PreAuthorize` sur chaque endpoint, identité résolue depuis le JWT (jamais faire confiance à `X-Collaborateur-Id` seul pour autoriser), anti-IDOR renvoyant `403`.
   - Rédiger `docs/autopilote/<nom>/02-architecture-carbon.md` contenant :
     1. Décisions techniques numérotées (`ARCH-01`…), choix algorithmiques et modèle de concurrence (Virtual Threads / async / transactions) ;
     2. Contrats exacts d'API REST / `/internal/**` / événements Kafka (méthode, URL, rôles, DTO JSON entrée/sortie, codes d'erreur) ;
     3. **Plan d'implémentation strict par développeur** avec liste fermée des dossiers/fichiers autorisés (`dev-backend`, `dev-web`, `dev-mobile`), cas de tests exigés et interdiction formelle d'ajouter quoi que ce soit hors contrat.

---

### Étape 3 — Développement Senior en Parallèle (`dev-backend` · `dev-web` · `dev-mobile` sur Gemini 4 Hydrogen)
Lance **en parallèle dans le même tour** tous les workers `Hydrogen` requis par `02-architecture-carbon.md` (jusqu'aux 3 simultanément) :

1. **`dev-backend` (Gemini 4 Hydrogen)** — si le backend est touché :
   - Applique `.agents/skills/sirh-dev-backend/SKILL.md` et `.agents/rules/backend-java.md`.
   - **Exigences d'ingénierie** : Java 21 idiomatique (records pour DTO immuables, pattern matching, `Optional` propre, Virtual Threads conformes au plan Carbon), Spring Boot 4.0.3, principes **SOLID, DRY, KISS, POO propre**, séparation stricte Controller / Service / Repository / Client interne, validation `@Valid`, `@PreAuthorize`, logs structurés sans données sensibles.
   - **Discipline stricte** : implémente **uniquement et exactement** ce que `architect-carbon` a spécifié dans `02-architecture-carbon.md` — zéro endpoint fantôme, zéro champ non demandé, zéro refactoring hors périmètre.
   - Vérification obligatoire : `export JAVA_HOME="/c/Program Files/Java/jdk-21"` puis `mvn -B -pl <modules> -am test` et écriture du rapport `docs/autopilote/<nom>/03-dev-backend.md` avec sortie réelle.

2. **`dev-web` (Gemini 4 Hydrogen)** — si le front web est touché :
   - Applique `.agents/skills/sirh-dev-web/SKILL.md` et `.agents/rules/web-react.md`.
   - Périmètre exclusif : `rh-admin-web/`.
   - **Exigences d'ingénierie** : React 18, TypeScript strict (zéro `any` non justifié), composants découplés et accessibles (labels, états de chargement/erreur/vide, rôle `DIRECTION` en lecture seule si applicable), respect à la lettre des DTO et URLs fixés par `architect-carbon` **sans ajout non demandé**.
   - Vérification obligatoire : `cd rh-admin-web && npx tsc --noEmit -p . && npx vite build` + tests Vitest du composant/page touché, et rapport `docs/autopilote/<nom>/03-dev-web.md`.

3. **`dev-mobile` (Gemini 4 Hydrogen)** — si l'application mobile est touchée :
   - Applique `.agents/skills/sirh-dev-mobile/SKILL.md` et `.agents/rules/mobile-flutter.md`.
   - Périmètre exclusif : `rh_mobile_app/` (**interdiction absolue** de toucher `rh_mobile_app_temp/`).
   - **Exigences d'ingénierie** : compatibilité stricte **Flutter 3.29.2** (Codemagic — ex. utiliser `value:` et jamais `initialValue:` sur `DropdownButtonFormField`), architecture propre, gestion d'erreurs réseau/hors-ligne, respect strict des contrats de `architect-carbon` **sans ajout non demandé**.
   - Vérification obligatoire : `/c/Users/kmejbri/.android/flutter/bin/flutter analyze` et `flutter test` dans `rh_mobile_app/`, et rapport `docs/autopilote/<nom>/03-dev-mobile.md`.

> **Contrôle coordinateur en sortie d'Étape 3** : si `workspace="share"` a été utilisé, fusionne les branches des workers dans le worktree `../project-<slug>` et vérifie que l'ensemble compile proprement avant de lancer `qa-flash`.

---

### Étape 4 — Tests & Recette (`qa-flash` sur Gemini 3.8 Flash)
1. Ajoute `qa-flash` épinglé sur **Gemini 3.8 Flash**.
2. **Consignes impératives dans le brief de `qa-flash`** :
   - Appliquer `.agents/skills/sirh-testeur/SKILL.md` et `.agents/rules/tests-recette.md`.
   - Lire `01-po-neon-spec.md`, `02-architecture-carbon.md` et les rapports `03-dev-*.md`.
   - Exécuter toutes les suites de tests automatisés des modules touchés + la recette fonctionnelle par critère d'acceptation (`CA-01`…) sur les services locaux du worktree (**ne jamais modifier ni polluer le kit de démo client `8090`**).
   - Vérifier les cas nominaux, les cas limites, les erreurs (`400`, `404`, `409`, `422`) et l'anti-IDOR (`403` entre collaborateurs/RO hors périmètre).
   - Produire `docs/autopilote/<nom>/04-recette-flash.md` : tableau des critères testés, preuves/sorties réelles, éléments éventuellement non testables localement (avec justification honnête), score `x/y` et liste des anomalies classées `Bloquante / Majeure / Mineure`.
3. **Boucle QA (max 3 tours)** :
   - Si au moins une anomalie `Bloquante` ou `Majeure` est détectée, réveille uniquement le(s) développeur(s) `Hydrogen` concerné(s) (en parallèle si multi-canal) avec pour seule mission : *« Corrige uniquement les anomalies listées dans `04-recette-flash.md` et ajoute un test qui échoue sans le correctif. »*
   - Relance ensuite `qa-flash` pour revalider les anomalies corrigées et la non-régression.

---

### Étape 5 — Technical Code Review (`reviewer-carbon` sur Gemini 4 Carbon)
1. Ajoute un **nouveau** worker `reviewer-carbon` épinglé sur **Gemini 4 Carbon (High effort)** (indépendant des développeurs et n'ayant écrit aucun code).
2. **Consignes impératives dans le brief de `reviewer-carbon`** :
   - Appliquer `.agents/skills/sirh-code-review/SKILL.md`.
   - Inspecter `git status` et `git diff delivery...HEAD` dans le worktree, ainsi que le contexte complet des fichiers modifiés, `02-architecture-carbon.md` et `04-recette-flash.md`.
   - Auditer rigoureusement :
     1. **Conformité au contrat Carbon & absence de sur-ingénierie** (tout code/endpoint/option non demandé par Carbon doit être signalé) ;
     2. **SOLID, DRY, KISS, POO & qualité algorithmique** (pas de N+1, complexité maîtrisée) ;
     3. **Concurrence, Virtual Threads & Asynchrone** (aucun appel HTTP/Kafka dans `@Transactional`, pas de pinning, timeouts présents, fermeture propre des `ExecutorService` / `try-with-resources`, propagation sûre du contexte) ;
     4. **Sécurité SIRH** (`@PreAuthorize` présent partout, JWT source de vérité, anti-IDOR, pas d'UUID/secret/URL de tunnel en dur, pas de `@LoadBalanced` sur `RestClient.Builder`, compatibilité Flutter 3.29.2) ;
     5. **Véracité des tests** (un test qui mocke le composant qu'il prétend tester ne compte pas ; relancer les commandes clés pour vérifier).
   - Écrire `docs/autopilote/<nom>/05-revue-technique-carbon.md` avec constats classés `Bloquant / Majeur / Mineur` (`fichier:ligne`, scénario concret, correction attendue) et verdict **GO TECHNIQUE** (0 Bloquant, 0 Majeur) ou **NO-GO TECHNIQUE**.
3. **Boucle Revue Technique (max 3 tours)** :
   - Si **NO-GO TECHNIQUE** : renvoie les corrections numérotées aux développeurs `Hydrogen` concernés → passe rapide de `qa-flash` sur les tests touchés → nouvelle relecture par `reviewer-carbon`.

---

### Étape 6 — Décision GO Fonctionnelle Finale (`po-neon` sur Gemini Neon)
1. Une fois le **GO TECHNIQUE** obtenu de `reviewer-carbon`, poste un message `@po-neon` sur le board du swarm avec les chemins vers `01-po-neon-spec.md`, `04-recette-flash.md` et `05-revue-technique-carbon.md`.
2. **Mission finale de `po-neon`** :
   - Vérifier point par point que l'implémentation livrée et recettée répond exactement au besoin initial de l'utilisateur, aux règles métier `R-xx` et aux critères d'acceptation `CA-xx`, sans régression fonctionnelle ni oubli UX.
   - Mettre à jour `docs/sirh_basefonctionnelle.md` dans le worktree si une nouvelle décision `D-xxx` validée doit y être consignée.
   - Rédiger `docs/autopilote/<nom>/06-decision-go-neon.md` et prononcer le verdict **GO FONCTIONNEL** ou **NO-GO FONCTIONNEL**.
3. Si **NO-GO FONCTIONNEL** : boucle ciblée vers le(s) développeur(s) `Hydrogen` → `qa-flash` → `reviewer-carbon` → `po-neon` (dans la limite globale de 3 boucles).

---

### Étape 7 — Synthèse Finale & Attente de Livraison (Coordinateur)
Lorsque **Carbon (`GO TECHNIQUE`)** et **Neon (`GO FONCTIONNEL`)** sont tous deux au vert :
1. Mets à jour `docs/autopilote/<nom>/etat.md` (`statut: PRET_POUR_LIVRAISON`).
2. Ferme proprement les workers temporaires dont le travail est achevé (après avoir vérifié qu'aucun changement non fusionné ne réside dans un workspace `share`).
3. Présente à l'utilisateur un rapport final synthétique (≤ 35 lignes) :
   - **Modèles exécutés** : table récapitulative (`po-neon`, `scraper-flash` si utilisé, `architect-carbon`, `dev-*` Hydrogen en parallèle, `qa-flash`, `reviewer-carbon`, décision finale `po-neon`) ;
   - **Branche & worktree** : `ma/<slug>` (`C:\Local\Khaled\project-<slug>`) et liens cliquables vers `01-po-neon-spec.md`, `02-architecture-carbon.md`, `04-recette-flash.md`, `05-revue-technique-carbon.md`, `06-decision-go-neon.md` ;
   - **Résultats vérifiés** : score de recette `x/y`, verdicts **GO Technique (Carbon)** et **GO Fonctionnel (Neon)**, points éventuellement non testés ;
   - **Prochaine étape (décision utilisateur)** :
     - Répondre **« livre »** pour déclencher `.agents/skills/sirh-livraison/SKILL.md` (merge `--no-ff` dans `delivery`, compilation complète, push `origin` + `sirh`, build Codemagic si mobile touché, Jira « A tester », `CONTEXTE.md`, `graphify update .`) ;
     - Répondre **« livre et démo »** pour enchaîner en plus la sauvegarde et le redéploiement contrôlé sur le kit `8090` (`exploitation.md` + `sirh-relancer-demo`).

---

## 4. Garde-fous absolus (Règles `00` à `03`)
- **Zéro commit sur `delivery`, zéro push, zéro action sur le kit `8090` ou le tunnel Cloudflare** tant que l'utilisateur ne l'a pas explicitement demandé après le double GO (`Carbon` + `Neon`).
- **Zéro secret lu, affiché ou écrit** : ne jamais accéder aux fichiers secrets protégés par `.agents/hooks/garde_secrets.py` ni afficher les jetons d'environnement ; ajouter les fichiers un par un lors des commits (jamais d'ajout global massif).
- **Zéro dérive de périmètre chez les développeurs Hydrogen** : toute fonctionnalité, option ou route absente de `02-architecture-carbon.md` est interdite.
- **Plafond de sécurité** : au maximum **3 boucles** de correction (QA ou Revue). Au-delà, arrête l'essaim, consigne le diagnostic exact dans `etat.md` et demande l'arbitrage de l'utilisateur.
