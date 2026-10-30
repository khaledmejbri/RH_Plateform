---
name: sirh-workflow
description: Orchestration du travail multi-agents du SIRH RH-Évènement. Utiliser dès qu'un besoin complet arrive (retour client ou testeur, nouvelle fonctionnalité, lot de corrections) pour l'emmener du besoin au commit : expert fonctionnel → tickets Jira → relecture testeur → architecte → développeurs en parallèle → recette → revue de code → commit sur accord.
---

# Workflow SIRH — du besoin au commit

La session principale orchestre ; chaque étape est confiée à un agent de `.claude/agents/` (outil Agent,
`subagent_type` = nom de l'agent). Les agents ne se parlent pas entre eux : tu transmets la sortie d'une étape
en entrée de la suivante, **en entier** (fiche besoin, clés Jira, plan, rapport).

## 0. Cadrer
- Lire `CONTEXTE.md`. Classer la demande : **petite correction** (une règle claire, un fichier ou deux) ou **lot**.
- Petite correction : aller directement à l'étape 5, puis 6 ciblée et 7.

## 1. Besoin — `expert-fonctionnel` (Opus)
Entrée : le texte exact de l'utilisateur. Sortie : une fiche besoin par ticket + `docs/sirh_basefonctionnelle.md`
à jour. Si l'agent remonte des questions, pose-les à l'utilisateur (AskUserQuestion) et relance-le avec les réponses.

## 2. Tickets — `redacteur-jira` (Haiku)
Entrée : les fiches besoin. Sortie : clés SCRUM-xx créées (recherche de doublon d'abord).

## 3. Relecture des tickets — `testeur` (Sonnet, moment 1)
Entrée : les clés. Sortie : corrections de testabilité → appliquées par `redacteur-jira`. Question de règle
métier → retour à l'étape 1.

## 4. Conception — `architecte` (Opus), si nécessaire
Obligatoire si : plusieurs services, nouvel endpoint inter-services, Kafka, nouvelle table ou enum stocké,
authentification, secret, déploiement. Sortie : plan par lot avec dossiers disjoints et contrats d'API.

## 5. Développement — `dev-backend` · `dev-web` · `dev-mobile` (Sonnet)
- Lancer en parallèle (plusieurs appels Agent dans le même message) les lots dont les dossiers sont disjoints.
- Le front dépend du contrat d'API, pas du code backend : il peut démarrer en même temps si le contrat est écrit.
- Chaque agent rend : fichiers, tests, résultat des vérifications. Une vérification rouge = lot non terminé.

## 6. Recette — `testeur` (Sonnet, moment 2)
Démarrer ou redémarrer les services modifiés (voir `CONTEXTE.md` § 3), puis lancer l'agent. Sortie : score `x/y`,
anomalies créées via `redacteur-jira`. Anomalie → retour à l'étape 5 pour l'agent concerné, puis nouvelle recette.

## 7. Revue — `code-reviewer` (Opus)
Sortie : GO / NO-GO. NO-GO → corrections (étape 5) puis nouvelle revue.

## 8. Livraison — session principale
- Montrer à l'utilisateur : tickets, score de recette, verdict de revue. **Commit et push seulement s'il le demande.**
- Commit en français `type(portée): résumé (SCRUM-xx)` + `Co-Authored-By`, branche `delivery`, push sur `origin`
  et `sirh`.
- Jira : transition 41 « A tester » + commentaire de recette (via `redacteur-jira`).
- `CONTEXTE.md` : ligne dans « Journal » (hash du commit), « Reste à faire » à jour, date de mise à jour.

## Modèles et coûts
Opus = réflexion (besoin, architecture, revue) ; Sonnet = production (code, tests, recette) ; Haiku = saisie
(tickets). Ne pas donner à un agent Opus un travail de saisie, ni à Haiku une décision.
