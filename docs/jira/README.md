# Backlog Jira — SIRH RH-Évènement

Backlog généré à partir de l'audit du code (branche `delivery`) et de `docs/sirh_basefonctionnelle.md`. Le fichier `backlog_sirh.csv` s'importe dans Jira ; ce document en est la version lisible.

## Importer dans Jira

1. Jira → **Paramètres (⚙) → Système → Import externe → CSV** (Jira Cloud : *Projet → … → Importer des tickets depuis un CSV*).
2. Choisir `docs/jira/backlog_sirh.csv`, encodage UTF-8, séparateur virgule.
3. Associer les colonnes : `Issue Id` → *Issue Id*, `Parent Id` → *Parent*, `Issue Type`, `Summary`, `Description`, `Priority`, `Labels`, `Component/s`, `Story Points`.
4. Les épics ont les `Issue Id` 1 à 12 (E00 = 1 … E11 = 12) ; chaque ticket T0NN a l'`Issue Id` 100+NN et pointe vers son épic via `Parent Id`.

Labels utilisés : `correction` (existant à corriger), `rh`, `architecte`, `backend`, `web`, `mobile`, `qa`, `securite`, `devops`, et le module (`evaluation`, `pointage`). Ils servent à router chaque ticket vers le bon skill Claude (`.claude/skills/`).

## Ordre de réalisation

1. **Sprint 1-2 — E00 Stabilisation** : rien de nouveau tant que les tickets `Highest` de E00 ne sont pas fermés.
2. **Sprint 3** — E01 socle (Flyway, CI, migrations) + spécification du solde de congés.
3. **Sprints 4-6** — E07 évaluations juin/décembre, E03 solde de congés, E05 formations par département/équipe.
4. **Sprints 7-9** — E08 reconnaissance faciale (après consentement + ADR), E09, E04, E06.
5. **Ensuite** — E10 tableaux de bord, E11 E2E.

Total : 12 épics, 58 tickets, 260 points.

## E00 — Stabilisation et corrections de l'existant

Corriger les défauts du code actuel avant tout nouveau développement. Doit être terminé en premier (sprints 1-2).

| Id | Type | Ticket | Priorité | Labels | Pts |
|---|---|---|---|---|---|
| T001 | Task | Faire de la branche delivery la référence et l'aligner sur main | Highest | correction, git, architecte | 2 |
| T002 | Bug | Évaluations : le scheduler crée des évaluations pour une liste de collaborateurs codée en dur | Highest | correction, backend, evaluation | 8 |
| T003 | Bug | Évaluations : le scheduler tourne toutes les 2 minutes (réglage de test) | Highest | correction, backend, evaluation | 1 |
| T004 | Bug | Test unitaire en échec : EvaluationTemplateServiceTest.listerTemplatesRetourneListe | High | correction, backend, evaluation, qa | 1 |
| T005 | Bug | Tests contextLoads en échec sans base PostgreSQL | High | correction, backend, qa | 3 |
| T006 | Bug | Secrets et mots de passe par défaut dans application.properties | Highest | correction, securite, backend | 3 |
| T007 | Bug | CORS ouvert à tous (allowedOriginPatterns '*') sur la gateway et le WebSocket | High | correction, securite, backend | 2 |
| T008 | Bug | Endpoint /internal/users/by-role accessible sans authentification | High | correction, securite, backend | 3 |
| T009 | Bug | Notes de service (M03) : fil d'actualité mobile en données factices | High | correction, mobile | 1 |
| T010 | Bug | Mobile : bouton 'Modifier le profil' sans action | Medium | correction, mobile | 1 |
| T011 | Task | Nettoyer le dépôt : dossiers en double et fichiers parasites | Medium | correction, architecte | 2 |
| T012 | Task | Mobile : identifiant d'application com.example.rh_mobile_app | Medium | correction, mobile | 2 |
| T013 | Task | Recette de non-régression de l'existant par module | Highest | qa, correction | 8 |
| T014 | Story | Aligner la durée de vie du JWT et ajouter le refresh token | High | securite, backend, web, mobile, architecte | 5 |

## E01 — Socle sécurité, plateforme et DevOps

Sécurité, configuration, migrations BD, CI/CD, environnements.

| Id | Type | Ticket | Priorité | Labels | Pts |
|---|---|---|---|---|---|
| T015 | Story | Migrations de schéma versionnées avec Flyway | High | backend, architecte | 5 |
| T016 | Story | Pipeline CI : build, tests et analyse à chaque PR | High | devops, qa | 5 |
| T017 | Story | Environnement complet en docker-compose (services + infra) | Medium | devops, architecte | 5 |
| T018 | Story | Journal d'audit immuable des décisions RH | Medium | backend, securite, rh | 5 |
| T019 | Story | Stockage objet (MinIO/S3) pour pièces jointes et PDF | Medium | backend, architecte | 5 |

## E02 — Référentiel RH : départements, équipes, organigramme

Structure de l'organisation, fiches collaborateurs, rattachement départements / équipes.

| Id | Type | Ticket | Priorité | Labels | Pts |
|---|---|---|---|---|---|
| T020 | Story | Notion d'équipe sous le département dans l'organigramme | High | rh, backend, web | 3 |
| T021 | Story | Organigramme web en glisser-déposer pour affecter les collaborateurs | Medium | web | 5 |
| T022 | Story | Modification et réaffectation d'un collaborateur depuis la fiche | Medium | web, backend | 3 |
| T023 | Story | Import en masse des collaborateurs (Excel/CSV) | Medium | backend, web, rh | 5 |

## E03 — Demandes administratives : congé, sortie, mission

Workflow demande → manager du nœud → RRH, solde de congés sans paie.

| Id | Type | Ticket | Priorité | Labels | Pts |
|---|---|---|---|---|---|
| T024 | Task | Règles du solde de congés (sans paie) — spécification | Highest | rh | 3 |
| T025 | Story | Solde de congés : calcul, affichage mobile et blocage au-delà du solde | High | backend, mobile, web | 8 |
| T026 | Story | Autorisation de sortie distincte du congé (max 4 h / demi-journée) | High | backend, mobile, rh | 3 |
| T027 | Story | Ordre de mission : frais estimés, pièces jointes, PDF signé | Low | backend, mobile | 5 |
| T028 | Story | Calendrier d'équipe des absences pour le manager | Medium | mobile, backend | 5 |

## E04 — Demandes de documents administratifs

Attestations, certificats, file FIFO RH, génération PDF.

| Id | Type | Ticket | Priorité | Labels | Pts |
|---|---|---|---|---|---|
| T029 | Story | Génération automatique des attestations en PDF | Medium | backend, web | 8 |
| T030 | Story | Suivi des SLA de la file documents avec alertes de retard | Medium | backend, web | 3 |

## E05 — Formations par département et équipe

Besoins et demandes de formation, plan annuel, évaluation à chaud / à froid.

| Id | Type | Ticket | Priorité | Labels | Pts |
|---|---|---|---|---|---|
| T031 | Story | Liste des collaborateurs demandés par leur supérieur dans le portail admin | High | web, backend | 3 |
| T032 | Story | Durée de formation exprimée en mois | Medium | web, mobile, backend | 2 |
| T033 | Story | Catalogue de formations filtré par département et équipe | High | backend, mobile, rh | 5 |
| T034 | Story | Plan annuel de formation et budget | Medium | web, backend, rh | 5 |
| T035 | Story | Évaluations de formation à chaud et à froid | Low | backend, mobile | 5 |

## E06 — Plaintes internes et externes

Déclaration, suivi, traitement, clôture.

| Id | Type | Ticket | Priorité | Labels | Pts |
|---|---|---|---|---|---|
| T036 | Story | Clôture automatique des plaintes à J+30 | Medium | backend | 2 |
| T037 | Story | Plainte anonyme et confidentialité | Medium | rh, backend, mobile | 3 |
| T038 | Story | Transcription vocale des plaintes | Low | backend, mobile, architecte | 8 |

## E07 — Évaluations semestrielles (juin) et annuelles (décembre)

Formulaires savoir-être / savoir-faire / objectifs, compétences techniques en étoiles, envoi au responsable hiérarchique.

| Id | Type | Ticket | Priorité | Labels | Pts |
|---|---|---|---|---|---|
| T039 | Story | Ouverture automatique des campagnes : semestrielle en juin, annuelle en décembre | Highest | backend, rh, evaluation | 5 |
| T040 | Story | Formulaire d'évaluation : savoir-être, savoir-faire, objectifs | High | rh, web, mobile, evaluation | 5 |
| T041 | Story | Compétences techniques notées en étoiles (1 à 5) | High | mobile, evaluation | 3 |
| T042 | Story | Envoi au responsable hiérarchique et notation croisée | High | backend, mobile, evaluation | 5 |
| T043 | Story | Suivi RH des campagnes : taux de complétion, retards, alertes couleur | Medium | web, evaluation | 5 |
| T044 | Story | Export PDF de l'évaluation et archivage | Low | backend, evaluation | 3 |

## E08 — Pointage : QR code puis reconnaissance faciale

Pointage mobile QR + GPS, puis vérification faciale on-device avec consentement RGPD.

| Id | Type | Ticket | Priorité | Labels | Pts |
|---|---|---|---|---|---|
| T045 | Story | Recette complète du pointage QR + GPS sur Android et iOS | High | qa, mobile, pointage | 3 |
| T046 | Story | Consentement biométrique RGPD avant la reconnaissance faciale | Highest | rh, mobile, backend, securite, pointage | 3 |
| T047 | Task | ADR : reconnaissance faciale on-device | High | architecte, pointage | 3 |
| T048 | Story | Enrôlement facial du collaborateur | High | mobile, pointage | 8 |
| T049 | Story | Pointage : vérification faciale après QR + GPS | High | mobile, backend, pointage | 8 |
| T050 | Story | Pointage manuel par le RO et correction par la RH | Medium | backend, mobile, web, pointage | 5 |
| T051 | Story | Rapport de présence mensuel par équipe | Medium | web, backend, pointage | 5 |

## E09 — Notifications et notes de service

Notifications push / temps réel, notes de service avec accusé de lecture (M03).

| Id | Type | Ticket | Priorité | Labels | Pts |
|---|---|---|---|---|---|
| T052 | Story | Backend des notes de service avec accusé de lecture | Medium | backend, web, mobile | 8 |
| T053 | Story | Centre de notifications mobile et préférences | Medium | mobile | 3 |

## E10 — Tableaux de bord et reporting

KPI RH, Direction, exports.

| Id | Type | Ticket | Priorité | Labels | Pts |
|---|---|---|---|---|---|
| T054 | Story | Tableau de bord RRH | Medium | web, backend | 8 |
| T055 | Story | Tableau de bord Direction et rapport mensuel | Low | web, backend | 8 |

## E11 — Qualité et tests

Stratégie de test, tests automatisés web / mobile / API, recette.

| Id | Type | Ticket | Priorité | Labels | Pts |
|---|---|---|---|---|---|
| T056 | Story | Mettre en place les tests web (Vitest + Testing Library) | High | web, qa | 5 |
| T057 | Story | Tests de sécurité RBAC / anti-IDOR sur tous les contrôleurs | High | backend, qa, securite | 8 |
| T058 | Story | Tests E2E des parcours principaux | Medium | qa | 8 |
