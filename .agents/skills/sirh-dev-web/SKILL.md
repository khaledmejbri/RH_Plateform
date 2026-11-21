---
name: sirh-dev-web
description: Développeur front web React/TypeScript de la Plateforme RH (rh-admin-web, back-office RH / Direction / Admin). Utiliser pour créer ou corriger une page, un formulaire, un tableau, un appel API ou un écran de l'admin web.
---

# Développeur web — Plateforme RH (`rh-admin-web`)

Back-office pour les rôles **RH**, **DIRECTION** (lecture seule) et **ADMIN**. Les collaborateurs n'y ont pas accès.

## Stack et structure

- React 18, TypeScript, Vite, react-router-dom, axios, STOMP (`@stomp/stompjs` + sockjs) pour le temps réel.
- `src/api/` : clients HTTP (toujours via la gateway, base URL configurée, jamais un port de microservice).
- `src/auth/` : JWT, `ProtectedRoute` (exige RH|DIRECTION|ADMIN). `src/layout/`, `src/components/`, `src/pages/` (une page par module).
- Design system : `design-system/sirh-rh-evenement` et le CSS existant. Réutilise les composants avant d'en créer.

## Règles

1. Rôle DIRECTION : aucune action d'écriture visible (boutons masqués, bandeau « Consultation uniquement »). Ne te fie pas qu'au front : l'API doit aussi refuser.
2. Chaque écran gère quatre états : chargement, vide, erreur (message lisible en français), données.
3. Formulaires : validation côté client alignée sur la validation serveur, erreurs 400 du serveur affichées sur le bon champ.
4. Pas de logique métier dupliquée (scoring, workflow) : le serveur décide, le front affiche.
5. Textes de l'interface en français.

## Avant de pousser

```bash
cd rh-admin-web
npx tsc --noEmit -p .
npx vite build
```

Si tu ajoutes des tests (Vitest + Testing Library recommandé, ticket dédié dans le backlog), lance-les aussi.
