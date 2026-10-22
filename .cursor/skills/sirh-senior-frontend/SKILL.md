---
name: sirh-senior-frontend
description: Senior frontend SIRH (React admin + Flutter mobile). Implémente ou sécurise les écrans, corrige les features Partielles, RBAC UI. Use when the user is senior frontend, React, Flutter, rh-admin-web, rh_mobile_app, UX implémentation, ou finir une feature incomplète côté client.
---

# Senior frontend — mode sécurisé

Tu implémentes la spec BA + maquettes UI/UX. **Un canal par tâche** : soit `rh-admin-web`, soit `rh_mobile_app`. Pas les deux dans le même run sauf demande explicite.

## Commun

- API via **gateway** uniquement (URLs relatives `/api/...`). Token : header / storage sécurisé, jamais localStorage clair si le projet a déjà un pattern cookie/secure storage.
- Gérer 401 (relogin), 403 (message métier), 409/422 (règle métier).
- Loading / erreur / vide. Pas de données `undefined` brutes.
- Français UI. Pas d’invention de KPI M12.
- Si le backend n’existe pas (M03, M09 présence) : rester prototype **étiqueté**, ne pas simuler un succès métier.

## Web (`rh-admin-web`)

- React fonctionnel, TS, client dans `src/api/`. TanStack Query si déjà en place, sinon le client existant.
- Back-office : files RH, FIFO + justification dérogation, organigramme **édition**, campagnes/templates M07.
- `hasAnyRole` côté nav : ne pas montrer d’actions RH à un USER.

## Mobile (`rh_mobile_app`)

- `ConsumerWidget` / Riverpod, repository par feature, `go_router`.
- Sortie : indicateur durée live + blocage > 4 h.
- RO : écran validation unité. Refus : motif requis.
- M09 : QR **puis** face ; aucune photo uploadée.
- M03 : mock — ne pas brancher un fake « lu » comme s’il était persisté.

## Features incomplètes côté UI

- M12 : compteurs seulement → pas de charts Direction sans spec.
- Organigramme mobile : lecture seule.
- Évaluations : étapes général → technique ; manager après collaborateur.

## Après

Aligner les types TS/Dart sur le JSON réel. Signaler tout écart spec ↔ API. Mettre à jour le doc fonctionnel si un Partiel devient Livré **côté client et API**.
