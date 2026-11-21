---
name: sirh-livraison
description: Livrer un lot du SIRH après recette conforme et revue GO, quand l'utilisateur demande « commit », « push », « livre », « passe en A tester » ou « mets à jour CONTEXTE ». Commit en français, push sur origin et sirh, build Codemagic, Jira « A tester », mise à jour de CONTEXTE.md et du graphe.
---

# Livraison d'un lot

Ne commence **que si l'utilisateur l'a demandé** et que la revue est GO.

1. **Vérifier l'arbre** : `git status`, `git branch --show-current` (branche de travail `delivery`, ou la branche
   du lot si l'utilisateur l'a choisie). Ne committer **que** les fichiers du lot ; laisser ceux des autres.
2. **Ajouter fichier par fichier** : `git add <fichier>` (jamais `-A` ni `.`). `docs/sirh_basefonctionnelle.md` et
   `docs/adr/*.md` avec `git add -f`. Jamais `recette/`, `deploy/.env`, `deploy/donnees/`, `docs/` (le reste).
3. **Commit** en français :
   ```
   type(portée): résumé court (SCRUM-xx, SCRUM-yy)

   <2 à 6 lignes : quoi et pourquoi>
   ```
   Types : `feat`, `fix`, `docs`, `test`, `refactor`, `ci`, `chore`. Le hook pre-commit (`tools/git-hooks`) bloque
   les secrets et les chemins interdits : s'il refuse, corrige, ne contourne pas.
4. **Push** : `git push origin <branche>` puis `git push sirh <branche>`. Refus → `git fetch` + merge (jamais de
   force), relire le commit distant.
5. **Mobile modifié** → build Codemagic `rh-connect-android` sur la branche poussée (outil MCP
   `codemagic_lancer_build`, puis `codemagic_etat_build` jusqu'à la fin). Rouge → retour au développeur.
6. **Jira** : commentaire `Recette <date> : <x/y> conformes. Commit <hash>. Points non testés : <liste>.` puis
   transition 41 « A tester » des tickets du lot.
7. **`CONTEXTE.md`** : nouvelle ligne en haut du « Journal » (date, hash, contenu, tickets), mise à jour du
   « Reste à faire » (non testés, dettes de revue, décisions à confirmer, données de recette laissées), date en tête.
   Commit `docs(contexte): …` puis push sur les deux remotes.
8. `graphify update .` (graphe à jour, sans coût).
9. Rendre à l'utilisateur : hash, tickets passés « A tester », état du build, ce qui reste non vérifié.
