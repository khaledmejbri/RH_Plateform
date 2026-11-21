---
trigger: always_on
---

# Sécurité et secrets (toujours actif, non négociable)

- Ne jamais lire, afficher, copier ni modifier : `gittoken.txt`, `deploy/.env`, `recette/outils/recette_pw.txt`,
  la valeur des variables `CODEMAGIC_TOKEN` et `DEMO_PW`. Les scripts et le serveur MCP `sirh` les lisent eux-mêmes ;
  ne cherche pas à contourner le hook `.agents/hooks/garde_secrets.py`.
- Aucun mot de passe, jeton, clé, adresse de tunnel ou donnée personnelle réelle dans le code, un commit, un ticket
  Jira, un fichier versionné ou une réponse.
- Ne jamais committer `recette/`, `deploy/.env`, `deploy/donnees/`, `docs/` (sauf `docs/sirh_basefonctionnelle.md`
  et `docs/adr/`, ajoutés avec `git add -f`). Ajoute les fichiers un par un : **jamais** `git add -A` / `git add .`.
  Interdits : push forcé, `--no-verify`, `git reset --hard` (demander à l'utilisateur).
- Identité de l'appelant : **toujours depuis le JWT** côté serveur. L'en-tête mobile `X-Collaborateur-Id` n'est pas
  une preuve : pour une décision sensible (valider, voir les données d'un autre), résoudre depuis le JWT
  (ex. `GET /api/referentiel/v1/collaborateurs/moi/...`).
- Chaque endpoint a un `@PreAuthorize` ; un collaborateur ou un RO hors périmètre reçoit **403** (anti-IDOR),
  sans révéler l'existence de la ressource quand la spec le demande.
- Appels de service à service : `/internal/**` + en-tête `X-Internal-Token`, jamais routés par la gateway.
- Secrets et URL d'environnement : variables d'environnement (modèle `deploy/.env.example`), jamais en dur.
- Pointage : position simulée refusée, précision GPS ≤ rayon, QR dynamique signé HMAC ; aucune image ni donnée
  biométrique envoyée au serveur (score seulement).
- Saisie dans un navigateur : ne tape jamais un mot de passe réel ; la connexion de recette se fait par les scripts
  (`recette/outils/seed_lib.py`) ou par l'utilisateur lui-même.
- Entrées utilisateur : DTO validés (`@Valid`), aucune entité JPA renvoyée, fichiers importés contrôlés (taille,
  zip bomb, XXE, formules Excel échappées).
