# Agents SIRH RH-Évènement

Source de vérité : [`docs/sirh_basefonctionnelle.md`](docs/sirh_basefonctionnelle.md)

Rules : `.cursor/rules/` · Skills : `.cursor/skills/`

## Rôles (un agent = un skill)

| Rôle | Skill | Fait | Ne fait pas |
|---|---|---|---|
| Product owner | `sirh-product-owner` | Clarifier, prioriser, user stories | Coder |
| Business analyst | `sirh-business-analyst` | Spec règles / API / AC | Coder, inventer un § 23 |
| Architecte | `sirh-architect` | ADR, gateway, Kafka, sécu | Implémenter le métier |
| Senior backend | `sirh-senior-backend` | Java/Spring, features Partielles | UI |
| Senior frontend | `sirh-senior-frontend` | React **ou** Flutter (un canal) | Changer les règles métier |
| UI/UX | `sirh-ui-ux` | Parcours, états, a11y | Logique serveur |
| Orchestrateur | `sirh-orchestrator` | Parallèle + frontières fichiers | Tout faire seul |

## Parallèle

1. PO d’abord si le besoin est flou.
2. BA + UI/UX en même temps.
3. Architecte si nouveau service (M09, M08, GED…).
4. Backend + front (web **ou** mobile) en même temps, **dossiers disjoints**.

Prompt type : *« En tant que [rôle], … »* ou *« Lance PO + BA + UI/UX en parallèle sur M03 »*.

## Agents Claude Code (`.claude/agents/`) — workflow du besoin au commit

Orchestration : skill `sirh-workflow` (lancé par la session principale) et règle `.claude/rules/workflow-agents.md`.

| # | Agent | Modèle | Rôle | Skill appliqué |
|---|---|---|---|---|
| 1 | `expert-fonctionnel` | Opus | Business analyste : règles, user stories, critères d'acceptation, doc fonctionnel | `sirh-expert-rh` |
| 2 | `redacteur-jira` | Haiku | Crée / corrige les tickets SCRUM à partir des fiches besoin | `sirh-redaction-jira` |
| 3 | `testeur` (moment 1) | Sonnet | Relit les tickets du BA et les fait corriger pour qu'ils soient testables | `sirh-testeur` |
| 4 | `architecte` | Opus | System design, décisions, ADR, sécurité, plan par lot | `sirh-architecte` |
| 5 | `dev-backend` · `dev-web` · `dev-mobile` | Sonnet | Code propre, SOLID, sécurisé + tests, en parallèle sur dossiers disjoints | `sirh-dev-*` |
| 6 | `testeur` (moment 2) | Sonnet | Recette de bout en bout, anomalies, verdict par ticket | `sirh-testeur` |
| 7 | `code-reviewer` | Opus | Revue du diff et validation GO / NO-GO | `sirh-code-review` |
| 8 | session principale | — | Commit / push sur accord, Jira « A tester », `CONTEXTE.md` | `sirh-workflow` |

Règles (`.claude/rules/`) : `workflow-agents.md` et `securite.md` toujours actives ; `backend-java.md`,
`web-react.md`, `mobile-flutter.md`, `tests.md` chargées selon les fichiers touchés.

Hooks (`.claude/settings.json`, scripts `.claude/hooks/`) :
- `proteger_secrets.py` (avant chaque outil) : bloque l'accès à `gittoken.txt`, `deploy/.env`, `recette_pw.txt`,
  l'affichage de `CODEMAGIC_TOKEN`, `git add` de `recette/` ou `deploy/donnees/`, `git add -A`, push forcé,
  `--no-verify`, `reset --hard`, et l'écriture dans `rh_mobile_app_temp/`.
- `verifier_code.py` (après Edit / Write) : UUID ou secret en dur, contrôleur sans `@PreAuthorize`,
  `initialValue:` (Flutter 3.33+, Codemagic 3.29), adresse de tunnel Cloudflare en dur.
- `rappel_contexte.py` (fin de tour) : rappelle de mettre à jour `CONTEXTE.md` si le dernier commit ne le fait pas.

Exemple : *« Lance le workflow sur ce retour client : … »* ou *« Utilise l'agent code-reviewer sur le diff »*.

## Skills Claude Code (`.claude/skills/`)

| Rôle | Skill | Labels Jira traités |
|---|---|---|
| Expert RH / fonctionnel | `sirh-expert-rh` | `rh` |
| Architecte applicatif | `sirh-architecte` | `architecte`, `securite`, `devops` |
| Dev backend Java/Spring | `sirh-dev-backend` | `backend` |
| Dev web React | `sirh-dev-web` | `web` |
| Dev mobile Flutter | `sirh-dev-mobile` | `mobile` |
| Testeur QA | `sirh-testeur` | `qa` |
| Rédaction Jira | `sirh-redaction-jira` | tous (saisie) |
| Revue de code | `sirh-code-review` | — |
| Orchestration | `sirh-workflow` | — |

Backlog : [`docs/jira/README.md`](docs/jira/README.md) (import Jira : `docs/jira/backlog_sirh.csv`). L'épic E00 « Stabilisation » passe avant tout nouveau développement.
