# Workflow multi-agents SIRH (toujours actif)

Un besoin passe par ces étapes, dans l'ordre. Le détail est dans le skill `sirh-workflow`.

| # | Étape | Agent (`.claude/agents/`) | Modèle | Sortie |
|---|---|---|---|---|
| 1 | Clarifier le besoin, trancher les règles | `expert-fonctionnel` | Opus | Fiche besoin + doc fonctionnel à jour |
| 2 | Créer les tickets Jira | `redacteur-jira` | Haiku | Tickets SCRUM-xx |
| 3 | Relire les tickets (testabilité) | `testeur` (moment 1) → `redacteur-jira` | Sonnet / Haiku | Tickets corrigés |
| 4 | Concevoir (si plusieurs services, Kafka, sécurité, table, déploiement) | `architecte` | Opus | Plan par lot, ADR |
| 5 | Développer, en parallèle, dossiers disjoints | `dev-backend` · `dev-web` · `dev-mobile` | Sonnet | Code + tests verts |
| 6 | Recette de bout en bout | `testeur` (moment 2) | Sonnet | Rapport `x/y`, anomalies |
| 7 | Revue et validation | `code-reviewer` | Opus | GO / NO-GO |
| 8 | Commit, push, Jira « A tester », `CONTEXTE.md` | session principale, **sur accord de l'utilisateur** | — | Commit |

Règles :
- Pas de code tant qu'une règle métier est ambiguë : retour à l'étape 1.
- Un fichier n'a qu'un seul agent qui l'écrit à la fois (`svc-*` / `rh-admin-web` / `rh_mobile_app` / `docs`).
- Une anomalie trouvée en recette ou un NO-GO renvoie au développeur concerné, puis on refait 6 et 7.
- Seule la session principale commite et pousse, et seulement quand l'utilisateur le demande.
- Petite correction (une ligne, un libellé) : les étapes 1 à 4 peuvent être sautées ; 5, 6 (ciblée) et 7 restent.
