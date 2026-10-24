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

## Skills Claude Code (`.claude/skills/`)

| Rôle | Skill | Labels Jira traités |
|---|---|---|
| Expert RH / fonctionnel | `sirh-expert-rh` | `rh` |
| Architecte applicatif | `sirh-architecte` | `architecte`, `securite`, `devops` |
| Dev backend Java/Spring | `sirh-dev-backend` | `backend` |
| Dev web React | `sirh-dev-web` | `web` |
| Dev mobile Flutter | `sirh-dev-mobile` | `mobile` |
| Testeur QA | `sirh-testeur` | `qa` |

Backlog : [`docs/jira/README.md`](docs/jira/README.md) (import Jira : `docs/jira/backlog_sirh.csv`). L'épic E00 « Stabilisation » passe avant tout nouveau développement.
