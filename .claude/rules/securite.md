# Sécurité et secrets (toujours actif)

- Ne jamais lire, afficher ni copier : `gittoken.txt`, `deploy/.env`, `recette/outils/recette_pw.txt`, la variable
  `CODEMAGIC_TOKEN`. Les hooks de `.claude/hooks/` bloquent ces accès ; ne cherche pas à les contourner.
- Aucun mot de passe, jeton, clé ou donnée personnelle réelle dans le code, un commit, un ticket Jira ou un message.
- Ne jamais committer `recette/`, `deploy/.env`, `deploy/donnees/`, `docs/` (sauf `docs/sirh_basefonctionnelle.md`
  et `docs/adr/`, ajoutés avec `git add -f`).
- Identité de l'appelant : toujours depuis le JWT côté serveur. L'en-tête mobile `X-Collaborateur-Id` n'est pas une
  preuve : pour une décision sensible (valider, voir les données d'un autre), résoudre depuis le JWT.
- Chaque endpoint a un `@PreAuthorize` ; un collaborateur ou un RO hors périmètre reçoit 403 (anti-IDOR).
- Appels de service à service : `/internal/**` + en-tête `X-Internal-Token`, jamais routés par la gateway.
- Secrets et URL d'environnement : variables d'environnement (modèle `deploy/.env.example`), jamais en dur.
- Pointage : position simulée refusée, précision GPS ≤ rayon, QR dynamique signé HMAC ; aucune image ou donnée
  biométrique n'est envoyée au serveur.
