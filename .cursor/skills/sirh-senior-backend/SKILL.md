---
name: sirh-senior-backend
description: Senior backend SIRH (Java 21, Spring). Implémente ou sécurise les APIs, corrige les features Partielles, workflows, RBAC. Use when the user is senior backend, développeur Java, Spring, corriger un endpoint, workflow M01-M07, Kafka, ou finir une feature incomplète côté serveur.
---

# Senior backend — mode sécurisé

Tu implémentes la spec BA dans `svc-*` + `gateway`. Tu ne changes pas les règles métier. Tu ne touches à `rh-admin-web` / `rh_mobile_app` que si un contrat JSON l’exige (prévenir le front).

## Avant de coder

1. Spec BA (ou story + `docs/sirh_basefonctionnelle.md`). Point § 23 non tranché → **stop**, PO/BA.
2. Identifier le service : référentiel (M00–M05, organigramme) vs `svc-evaluation` (M07) vs `svc-identite-acces` vs `svc-notification`.
3. Vérifier la route **gateway** (`gateway/src/main/resources/application.yml`).

## Features incomplètes (priorité si on te demande de « finir »)

| Module | Manque backend |
|---|---|
| M03 | Pas d’API notes / accusé de lecture |
| M09 | Pas de `svc-presence` (TOTP, GPS, score, HMAC) |
| M04 | Whisper non branché ; fermeture J+30 absente |
| M05 | Schedulers chaud/froid absents |
| M07 | PDF + S3 archive |
| M02 | Génération PDF réelle / S3 |
| AUTH | Refresh token 7 j vs TTL 1 h |

## Secure checklist

- `@PreAuthorize` aligné matrice § 4.2 ; jamais `permitAll` sur du métier.
- DTO in/out, `@Valid`, pas d’entité JPA exposée.
- `@Transactional` service ; lazy collections ; index FK.
- Erreurs métier → 409/422 + message stable (pas 500).
- Validation **serveur** des règles C-M* (sortie 4 h, FIFO, transitions plainte, une éval/campagne).
- JWT : ownership (demandeur / RO unité / RH).
- Kafka idempotent ; pas de secret en log.
- Tests : happy path + refus motif + 403 rôle + cas limite (4 h + 1 min, FIFO sans justification).

## Après

- Mettre à jour `docs/sirh_basefonctionnelle.md` si le comportement livré change (Partiel → Livré).
- Handoff front : contrat JSON réel (champs snake_case existants du projet).
