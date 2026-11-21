---
trigger: always_on
---

# Boucle qualité : du besoin au commit (rôles et modèles)

Un besoin traverse ces étapes dans l'ordre. Chaque étape a un **agent** (`.agents/agents/`) et un **skill**
(`.agents/skills/`). Le détail et les prompts types sont dans le skill `sirh-boucle-qualite`.

| # | Étape | Agent | Modèle conseillé | Sortie |
|---|---|---|---|---|
| 1 | Clarifier, trancher les règles | `expert-fonctionnel` | Claude Opus / Gemini Pro | Fiche besoin + doc fonctionnel à jour |
| 2 | Tickets Jira | `redacteur-jira` | Flash | SCRUM-xx créés |
| 3 | Relire les tickets (testabilité) | `testeur` | Flash | Tickets corrigés |
| 4 | Concevoir (plusieurs services, Kafka, table, sécurité, déploiement) | `architecte` | Claude Opus | Plan par lot, ADR |
| 5 | Développer (dossiers disjoints) | `dev-backend` · `dev-web` · `dev-mobile` | Gemini Pro | Code + tests verts |
| 6 | Recette de bout en bout | `testeur` | Flash | Rapport `x/y`, anomalies |
| 7 | Revue de code GO / NO-GO | `code-reviewer` | **Claude Opus** | Verdict + améliorations |
| 8 | Commit, push, Jira « A tester », `CONTEXTE.md` | agent principal, **sur accord de l'utilisateur** | — | Commit |

Règles de la boucle :
- Pas de code tant qu'une règle métier est ambiguë → retour à l'étape 1.
- Le développeur **n'arbitre pas** l'architecture ni les règles : il suit la spec / l'ADR / le plan. En cas de doute,
  il s'arrête et pose la question.
- Recette KO → retour au développeur concerné avec le rapport d'anomalies, puis nouvelle recette (6).
- Recette OK → revue (7). NO-GO → corrections (5), nouvelle recette ciblée (6), nouvelle revue (7). On boucle
  jusqu'au GO.
- Petite correction (une ligne, un libellé) : les étapes 1 à 4 peuvent être sautées ; 5, 6 (ciblée) et 7 restent.
- Seul l'agent principal commite et pousse, et seulement quand l'utilisateur le demande.
- Le modèle qui a écrit le code ne fait pas sa propre revue : la revue se fait dans une **nouvelle conversation**
  avec Claude Opus sélectionné, à partir du diff.
