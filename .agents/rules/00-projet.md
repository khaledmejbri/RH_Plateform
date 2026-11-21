---
trigger: always_on
---

# SIRH RH-Évènement — contexte du projet (à lire avant toute action)

Tu travailles sur un SIRH **sans paie** pour la société RH-Évènement. Ces fichiers remplacent l'historique des
conversations : **lis-les en début de tâche**, dans cet ordre.

1. `CONTEXTE.md` — mémo de reprise : architecture réelle, commandes, comptes de recette, journal des lots,
   « Reste à faire » (dettes, points non testés, décisions à confirmer). C'est la mémoire du projet.
2. `docs/sirh_basefonctionnelle.md` — **source de vérité fonctionnelle** (décisions `D-xxx`, contraintes `C-xxx`,
   § 23 = questions client). Une règle qui n'y est pas n'existe pas : on la fait trancher, on ne l'invente pas.
3. `AGENTS.md` — rôles et agents. Les spécifications en cours sont dans `docs/specs/` et les ADR dans `docs/adr/`.

## Architecture (ne jamais la changer sans ADR validé par l'utilisateur)

Spring Boot **4.0.3** / Java **21**, monorepo Maven (`pom.xml` racine). Tout passe par la **gateway**.

| Service | Port | Base | Rôle |
|---|---|---|---|
| eureka-server | 8761 | — | Annuaire |
| gateway | 8080 | — | Routage seul, routes dans `gateway/src/main/resources/application.yml` |
| svc-identite-acces | 8081 | authentication_db | Comptes, JWT (claim `identifiant_utilisateur` = compte), `/api/auth/signin` |
| svc-referentiel-rh | 8083 | referentiel_rh_db | Collaborateurs, organigramme, demandes M01, documents, formations, plaintes, soldes, imports, paramètres |
| svc-evaluation | 8084 | evaluation_rh_db | Campagnes, templates, évaluations M07, alertes d'écart |
| svc-notification | 8085 | notification_db | Kafka `rh.notifications` → STOMP `/ws` + stockage des notifications |
| svc-presence | 8086 | presence_db | Pointage QR + GPS, borne, pointage manuel, horaires, heures sup |

- Clients : **Plateforme RH** `rh-admin-web` (React 18 / Vite / TS, RH · DIRECTION lecture seule · ADMIN) et
  **RH Connect** `rh_mobile_app` (Flutter, collaborateur · RO · chef de département). **Jamais** `rh_mobile_app_temp/`.
- Une base PostgreSQL par service, pas de jointure entre services : appel interne `/internal/**` + en-tête
  `X-Internal-Token` (jamais routé par la gateway) ou événement Kafka.
- `ddl-auto=update` partout (pas de Flyway). Une valeur ajoutée à un enum stocké exige un `ApplicationRunner` qui
  recrée la contrainte CHECK (modèles : `PointageStatutConstraintRunner`, `WorkflowAdministratifConstraintRunner`).
- Boot 4 n'auto-configure pas `KafkaTemplate` : chaque service déclare son producteur (`Kafka*Config`) et envoie
  du JSON texte (`KafkaTemplate<String, String>`). Notifications adressées au **compte** (`compte_utilisateur_id`).
- **Ne jamais** exposer de bean `RestClient.Builder` / `RestTemplate` `@LoadBalanced` : le client Eureka le reprend
  et le service ne s'enregistre plus (incident du 13/11/2026).

## Commandes utiles (Windows, Git Bash)

- JDK : `export JAVA_HOME="/c/Program Files/Java/jdk-21"`.
- Build d'un module : `mvn -B -q -pl <module> -am package -DskipTests` (arrêter le service avant : jar verrouillé).
- Tests : `mvn -B -pl <module> test`. Échec ancien toléré : `EvaluationTemplateServiceTest.listerTemplatesRetourneListe`.
- Web : `cd rh-admin-web && npx tsc --noEmit -p . && npx vite build`.
- Mobile : `cd rh_mobile_app && flutter analyze && flutter test` (Flutter local :
  `/c/Users/kmejbri/.android/flutter/bin/flutter`). Codemagic est en **Flutter 3.29.2** : aucune API plus récente.
- Kit de démo Docker (port 8090, projet `-p sirh`) et tunnel Cloudflare : voir la règle `exploitation.md` et le
  serveur MCP `sirh` (outils `kit_*`).

## Graphe de connaissance (graphify)

`graphify-out/graph.json` existe. Pour une question sur le code, **commence par** `graphify query "<question>"`
(ou l'outil MCP `graphify_query`), puis `graphify path "<A>" "<B>"` / `graphify explain "<concept>"`. Ne lis les
fichiers bruts que pour les lignes que le graphe a localisées. Après une modification de code : `graphify update .`.

## Conventions

- Code et textes d'interface **en français** (nommage métier : `creerEvaluationPourCollaborateur`).
- Commits en français : `type(portée): résumé (SCRUM-xx)`. Branche de travail `delivery`, push sur `origin` **et**
  `sirh` (celui que Codemagic construit). Commit et push **seulement quand l'utilisateur le demande**.
- Jira : cloudId `da52b5bd-0de2-43cf-b5ee-d135af8fe669`, projet `SCRUM`, type « Tâche », transition **41 = « A tester »**.
- À la fin de chaque lot livré (commit poussé) : mettre à jour `CONTEXTE.md` (« Journal » + « Reste à faire » +
  date en tête). Skill : `sirh-livraison`.
