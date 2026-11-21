---
name: sirh-reprise
description: Reprendre le projet SIRH en début de conversation ou après une pause. Utiliser quand l'utilisateur dit « reprends », « où en est-on », « qu'est-ce qui reste à faire », « résume le projet », ou avant de démarrer un nouveau lot sans contexte.
---

# Reprise du projet

1. Lire `CONTEXTE.md` en entier (journal + reste à faire) et `git log --oneline -10`, `git status -sb`,
   `git branch --show-current`.
2. Lire la liste des specs en cours : `docs/specs/` (moteur de workflow : `SPEC-moteur-workflow.md`, lots dans
   `docs/specs/workflow/lots/`), et les ADR récents (`docs/adr/`).
3. Si besoin de Jira : JQL `project = SCRUM AND status != Terminé ORDER BY priority DESC, created DESC` (connecteur
   `atlassian`).
4. Rendre en 10 lignes au plus :
   - branche courante et fichiers non committés (à qui ils appartiennent si identifiable) ;
   - dernier lot livré (hash, tickets) ;
   - lot en cours et sa prochaine étape dans la boucle qualité ;
   - 3 à 5 priorités du « Reste à faire » (bloquants, non testés, décisions client attendues) ;
   - état du kit de démo si l'utilisateur en parle (`kit_etat`).
5. Proposer **une** prochaine action concrète, sans la lancer si elle touche le kit, Git ou Jira.
