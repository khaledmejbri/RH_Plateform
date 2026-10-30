---
paths:
  - "rh-admin-web/**"
---

# Web React / TypeScript (Plateforme RH)

- TypeScript strict, pas de `any` nouveau ; types des réponses déclarés dans `src/api/*Api.ts`.
- Appels HTTP uniquement via les clients de `src/api/` (base URL de la gateway), jamais un port de microservice.
- Composants petits et réutilisés ; logique de données dans des fonctions ou hooks, pas dans le JSX.
- Quatre états par écran : chargement, vide, erreur en français, données.
- DIRECTION = lecture seule : aucune action d'écriture visible. Le serveur refuse aussi (ne pas s'y fier côté client).
- Pas de règle métier dupliquée (calculs, droits) : le serveur décide, le front affiche ce qu'il renvoie.
- Aucun secret ni jeton dans le code ou `localStorage` en clair hors du mécanisme d'auth existant.
- Vérifier : `cd rh-admin-web && npx tsc --noEmit -p . && npx vite build`.
