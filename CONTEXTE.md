# Contexte du projet SIRH RH-Évènement

Mémo de reprise : à lire en début de session à la place de l'historique des conversations.
À tenir à jour à chaque lot livré (section « Journal »). Dernière mise à jour : 30/10/2026.

## 1. Le produit

SIRH sans paie pour RH-Évènement. Source de vérité fonctionnelle : `docs/sirh_basefonctionnelle.md`
(dossier `docs/` ignoré par Git sauf ce fichier : l'ajouter avec `git add -f`). Rôles des agents et skills : `AGENTS.md`.

| Canal | Dossier | Utilisateurs |
|---|---|---|
| **Plateforme RH** (React 18 / Vite, port 5173) | `rh-admin-web` | RH, DIRECTION (lecture seule), ADMIN |
| **RH Connect** (Flutter, iOS / Android ; web en recette sur 5180) | `rh_mobile_app` (jamais `rh_mobile_app_temp`) | Collaborateur, RO, chef de département |

## 2. Architecture technique

Spring Boot 4.0.3 / Java 21, monorepo Maven (`pom.xml` racine). Tout passe par la gateway.

| Service | Port | Base | Rôle |
|---|---|---|---|
| eureka-server | 8761 | — | Annuaire |
| gateway | 8080 | — | Routage seul (pas de sécurité), routes dans `gateway/src/main/resources/application.yml` |
| svc-identite-acces | 8081 | authentication_db | Comptes, JWT (claim `identifiant_utilisateur` = compte), `/api/auth/signin` |
| svc-referentiel-rh | 8083 | referentiel_rh_db | Collaborateurs, organigramme, demandes M01, documents, formations, plaintes |
| svc-evaluation | 8084 | evaluation_rh_db | Campagnes, templates, évaluations M07 |
| svc-notification | 8085 | — | Kafka `rh.notifications` → STOMP `/ws` (`/user/queue/notifications`) ; destinataire `RH` = tous les RH |
| svc-presence | 8086 | presence_db | Pointage QR + GPS, borne écran, pointage manuel |

- PostgreSQL local sur **5444** (`postgres` / `password`). Accès : `docker exec -e PGPASSWORD=password abonne-postgres psql -h host.docker.internal -p 5444 -U postgres -d <base>`.
- Kafka (`project-kafka-1`, 9092), Redis, Mailhog : `docker-compose.kafka.yml`, `docker-compose.infra.yml`.
- `ddl-auto=update` partout (pas de Flyway). Une valeur ajoutée à un enum stocké en base exige un runner qui recrée la contrainte CHECK (`TypeDocumentConstraintRunner`, `PointageStatutConstraintRunner`).
- Spring Boot 4 n'auto-configure plus `KafkaTemplate` avec la seule dépendance spring-kafka : chaque service déclare son producteur (`KafkaEvaluationConfig`, `KafkaPresenceConfig`, `KafkaReferentielConfig`) et envoie du JSON texte (`KafkaTemplate<String, String>`).
- Notifications : toujours adresser au **compte** (`compte_utilisateur_id`), repli sur la fiche (SCRUM-27).
- Mobile : l'appli envoie l'en-tête `X-Collaborateur-Id` (fiche). Pour une décision sensible, résoudre l'identité depuis le JWT (ex. `GET /api/referentiel/v1/collaborateurs/moi/valideur-pointage`).

## 3. Lancer et tester en local

- JDK : `export JAVA_HOME="/c/Program Files/Java/jdk-21"` (sinon Maven prend un mauvais JDK).
- Build : `mvn -B -q -pl <module> -am package -DskipTests`. **Arrêter le service avant** (jar verrouillé).
- Lancer : `nohup "$JAVA_HOME/bin/java" -jar <module>/target/<module>-0.0.1-SNAPSHOT.jar > <log> 2>&1 &`.
- Cache Caffeine dans le référentiel : redémarrer après une modification directe en base.
- Tests : `mvn -B -pl <module> test`. Échec connu et ancien : `EvaluationTemplateServiceTest.listerTemplatesRetourneListe`.
- Web : `cd rh-admin-web && npx tsc --noEmit -p . && npx vite build`.
- Mobile : `cd rh_mobile_app && flutter analyze && flutter test` ; recette web : `flutter run -d web-server --web-port 5180`
  (tuer l'ancien `dartvm` qui garde le port 5180). Le GPS natif ne marche pas en web ; si le panneau navigateur est masqué, Flutter web se fige.
- Automatisation navigateur : React → saisie clavier, `select` via le setter natif + événement `change`. Flutter → nœuds `flt-semantics`, champs via `find` puis `triple_click` + `type`.

## 4. Comptes de recette

Liste complète (login, rôle, unité, direction, supérieur) : `recette/comptes_recette_SIRH.xlsx` (hors Git, `recette/` exclu localement).
Mot de passe commun de recette : `recette/outils/recette_pw.txt` (et colonne de l'Excel) — **ne jamais l'écrire dans le code, un commit ou un ticket**.
Régénérer l'Excel depuis la base : `bash recette/outils/export_comptes.sh`.
Outils de recette (`recette/outils/`) : `seed_lib.py` (connexion + appels API), `stomp_listen.py MATRICULE DUREE_S`
(écoute les notifications d'un compte → `notif_<MATRICULE>.log`), `recette_pointage.py ac|d|d2|e` (recette M09).

| Login | Personne | Rôle utile |
|---|---|---|
| DRH01 | Amel Trabelsi (DRH) | RH — Plateforme RH |
| DG01 | Karim Ben Salah (DG) | DIRECTION (lecture seule) |
| ADM01 | Nizar Gharbi | ADMIN |
| CS-WEB | Omar Belhadj | Chef de service web (RO) |
| COL-WEB | Sarra Khemiri | Collaboratrice, backend confirmée |
| COL-WEB2 | Ahmed Nasri | Collaborateur, backend senior |
| COL-MOB | Mariem Guesmi | Collaboratrice, backend junior |
| CD-SW | Youssef Chaabane | Chef de département (responsable d'Omar) |

Organigramme : DG → directions RH / Technique / Finance (+ « Direction System ») → 2 départements chacune → services.
Anomalie de données connue : DRH01 n'a pas de supérieur (devrait être DG01).

## 5. Règles métier déjà tranchées (résumé — détail dans le doc)

- **D-H03** valideur M01 = manager du nœud du demandeur ; si c'est lui, premier ancêtre managé ; sinon RH. Personne ne valide sa propre demande (RH/ADMIN compris).
- **D-H04** organigramme typé : DG unique > Direction (toujours sous la DG) > Département > Service ; Département / Service peuvent être « à rattacher ».
- **M07 évaluations** : campagne activée → une évaluation par collaborateur actif avec supérieur ; template technique choisi par famille × niveau ; compétences notées en **étoiles 1–5** ; le responsable est notifié quand le collaborateur valide.
- **M09 pointage (D-P01 à D-P05)** : rayon par site (extérieur 20 m, intérieur 50 m, 10–100 m) ; précision GPS obligatoire ≤ rayon ; faux GPS refusé ; pose du site par moyenne de 15 s (≤ 10 m ext. / 25 m int.) ; pointage manuel validé par le responsable D-H03, alerte RH au-delà de 3/mois ; borne écran QR renouvelé toutes les 30 s (± 1 fenêtre).

## 6. Outils et dépôts

- Remotes Git : `origin` = RH_Plateform.git, `sirh` = SIRH-Plateform-Back-Front.git (celui que Codemagic construit). Branche de travail : `delivery` ; pousser sur les deux.
- Codemagic : API `api.codemagic.io`, en-tête `x-auth-token` = variable d'environnement utilisateur `CODEMAGIC_TOKEN` (ne jamais l'afficher). App `6a7f0915506a25c33389a606`, workflow `rh-connect-ios`. Un push ne déclenche pas de build : le lancer par l'API.
- Jira : cloudId `da52b5bd-0de2-43cf-b5ee-d135af8fe669`, projet `SCRUM`, type « Tâche », transition **41 = « A tester »**. Backlog : `docs/jira/README.md`.
- `gittoken.txt` à la racine est un secret (ignoré par Git) : ne pas le lire ni le copier.
- Messages de commit en français : `type(portée): résumé (SCRUM-xx)` + ligne `Co-Authored-By`.

## 6 bis. Déploiement (kit `deploy/`)

- Tout sur une machine : `deploy/docker-compose.yml` (PostgreSQL 17, Kafka KRaft, Eureka, gateway, 5 services, web nginx, Caddy HTTPS, Mailpit).
  Guide pas à pas : `deploy/GUIDE_DEPLOIEMENT.md` (Oracle Cloud Always Free, DuckDNS, Android Codemagic, passation client).
- Secrets dans `deploy/.env` (ignoré par Git, modèle `.env.example`). Eureka `defaultZone` passé par `-D` dans `JAVA_OPTS` (la clé perd sa casse en variable d'environnement).
- Les jetons sont émis et vérifiés sur `http://gateway:8080` (adresse interne) ; nginx relaie `/api`, `/oauth2`, `/.well-known`, `/ws` vers la gateway.
- Aucun compte initial : charger les données de recette avec `deploy/scripts/exporter-donnees-locales.sh` (PC) puis `importer-donnees.sh` (serveur). `deploy/donnees/` est ignoré par Git.
- Test local du kit : `.env` avec `DOMAINE=:80`, `HTTP_PORT=8090`.

## 7. Journal (le plus récent en haut)

| Date | Commit | Contenu | Tickets |
|---|---|---|---|
| 30/10/2026 | (ce commit) | Workflow multi-agents Claude Code : 8 agents (Opus fonctionnel / architecte / revue, Sonnet devs / testeur, Haiku Jira), règles `.claude/rules/`, skills `sirh-workflow`, `sirh-redaction-jira`, `sirh-code-review`, hooks (secrets, qualité, rappel CONTEXTE). Voir AGENTS.md | — |
| 29/10/2026 | 21a69c8 | Retours testeurs : sortie ≤ 2 h ; campagnes à mois libre, jour 20 par défaut, durée société, type trimestriel ; lancement / clôture automatiques (ancien planificateur supprimé), endpoint interne référentiel. Recette 20/20 | SCRUM-40 à 42 → A tester |
| 28/10/2026 | 545a906 | Kit de déploiement `deploy/` testé en local (~3,5 Go), CONTEXTE.md / CLAUDE.md, index `idx_rh_evaluation_collaborateur` renommé, APK Android avec `API_BASE_URL` | — |
| 28/10/2026 | be87fee | Pointage : zone par site, anti-faux GPS, pose par moyenne, pointage manuel, borne écran. Recette 43/43 (`recette/captures_pointage`) | SCRUM-35 à 39 → A tester |
| 27/10/2026 | def38ac | Compétences techniques notées en étoiles (mobile) | — |
| 27/10/2026 | 462bfce | Évaluations : notification d'ouverture publiée, responsable notifié, noms affichés | SCRUM-31 à 34 → A tester |
| 27/10/2026 | 072184e | Notifications M01 adressées au compte, valideur D-H03, ADMIN notifié | SCRUM-27 à 30 |
| 27/10/2026 | 00de717 | Organigramme typé D-H04 | — |
| 26/10/2026 | 9d8f0dc, 579e71f | Auto-validation interdite, 403 au lieu de 400, matrice des rôles | SCRUM-23 à 26 |
| antérieur | — | Recette web/mobile, SCRUM-5 à 22 | — |

## 8. Reste à faire / points ouverts

- **Démo en cours sur le PC de Khaled (28/10/2026)** : kit `deploy/` lancé avec `DOMAINE=:80`, `HTTP_PORT=8090`, tunnel Cloudflare rapide
  (adresse dans `recette/outils/adresse_publique.txt`, change à chaque relance ; un tunnel rapide peut aussi expirer
  tout seul, ex. la nuit du 28 au 29/10). Relancer : `.\deploy\scripts\demarrer-demo-pc.ps1`.
  APK de démo : `recette/RH-Connect-demo.apk` (construit en local, Flutter 3.41). Message client : `recette/message_client.md`.
- Codemagic utilise Flutter 3.29.2 : ne pas employer d'API Flutter plus récente (ex. `initialValue` de `DropdownButtonFormField`, 3.33+).

- Tester sur un vrai téléphone (build Codemagic) : faux GPS, Test terrain, pose 15 s, écrans de pointage manuel.
- Onglet « Pointages » web : colonnes Collaborateur et Site affichent des identifiants au lieu des noms (antérieur).
- Sécurité : l'API mobile fait confiance à l'en-tête `X-Collaborateur-Id` fourni par le client (risque d'accès aux données d'un autre collaborateur) — pas encore de ticket.
- Notifications non persistées (perdues si l'utilisateur n'est pas connecté) — pas encore de ticket.
- Libellé de la famille `DEV_BACKEND` absent côté mobile (affiche le code).
- `svc-notification` : `NotificationTestController` (`/api/notifications/test/*`) sans `@PreAuthorize` ; non routé par la gateway mais à supprimer ou protéger (signalé par le hook `verifier_code.py`).
- Agents Claude Code : workflow pas encore éprouvé sur un vrai lot ; ajuster les prompts de `.claude/agents/` après le premier usage.
