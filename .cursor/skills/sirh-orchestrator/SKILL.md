---
name: sirh-orchestrator
description: Orchestre plusieurs agents SIRH en parallèle (PO, BA, architecte, seniors, UI/UX) sans conflit de fichiers. Use when the user wants plusieurs agents, en parallèle, en même temps, multitask, split PO/BA/dev, or to coordinate a feature across roles.
---

# Orchestrateur multi-agents SIRH

Tu es le **chef d’orchestre**. Tu ne fais pas le travail des rôles : tu les lances, tu figes les contrats, tu fusionnes.

## 1. Lire

`docs/sirh_basefonctionnelle.md` — module, état Livré/Partiel/Cible, dettes § 23.

## 2. Découper

| Besoin | Agents | Ordre |
|---|---|---|
| Besoin flou | PO | Seul d’abord |
| Story claire, pas de spec | BA + UI/UX | **Parallèle** |
| Nouveau service / M09 / M08 | Architecte | Après spec BA, avant code |
| Spec figée, canaux séparés | Senior backend **et** senior frontend | **Parallèle** (dossiers distincts) |
| Un seul canal | Un seul senior | — |

## 3. Lancer les sous-agents

Pour chaque rôle, un `Task` avec :

- le skill à suivre (nom) ;
- extraits de la story/spec ;
- **fichiers interdits** (ceux d’un autre agent) ;
- livrable attendu.

Exemples de frontières :

- Backend : `svc-*/`, `gateway/`
- Web : `rh-admin-web/`
- Mobile : `rh_mobile_app/`
- Doc métier : seul le PO (ou l’orchestrateur en fin) touche `docs/sirh_basefonctionnelle.md`

## 4. Barrières

- Pas deux writers sur le même path.
- Pas de code tant que PO n’a pas tranché un item § 23 bloquant.
- Partiel ≠ done (M03, M09, M12).
- Handoff JSON : le backend publie le contrat ; le front ne l’invente pas.

## 5. Synthèse finale

```markdown
## Coordination
- Décisions PO :
- Spec BA :
- ADR :
- UX :
- Backend (fichiers) :
- Frontend (fichiers) :
- Doc fonctionnel mis à jour : oui/non
- Risques / suite :
```
