---
name: sirh-redaction-jira
description: Rédaction des tickets Jira du projet SIRH (projet SCRUM). Utiliser pour créer un ticket à partir d'une fiche besoin de l'expert fonctionnel ou d'un rapport de bug du testeur, compléter ou corriger un ticket existant, le commenter ou le passer en « A tester ».
---

# Rédaction Jira — projet SCRUM

## Paramètres
- cloudId `da52b5bd-0de2-43cf-b5ee-d135af8fe669`, projectKey `SCRUM`, issueTypeName `Tâche`.
- Transition `41` = « A tester ». Ne fais transiter que sur demande explicite.
- Labels : `rh`, `backend`, `web`, `mobile`, `qa`, `architecte`, `securite`, `devops`, `notification` (voir `AGENTS.md`).
- Descriptions en Markdown (`contentFormat: markdown`).

## Avant de créer
1. JQL : `project = SCRUM AND summary ~ "<mots clés>" ORDER BY created DESC`. Doublon → compléter plutôt que créer.
2. Vérifier que la fiche contient : règles chiffrées, critères d'acceptation testables, labels. Sinon, s'arrêter et
   demander l'information manquante (ne rien inventer).

## Modèle — fonctionnalité
```
**Origine** : <client / testeur / interne, date>
**Module** : Mxx

## Règles
1. ...

## Critères d'acceptation
- [ ] <rôle> <action> → <résultat attendu> (code HTTP si API)
- [ ] Cas d'erreur : ...
- [ ] Droits : <rôle non autorisé> → 403

## Hors périmètre
- ...

**Référence** : docs/sirh_basefonctionnelle.md § <x> (D-xxx)
```

## Modèle — anomalie
Titre `[Bug] <symptôme>` ; sections Environnement, Rôle, Étapes, Obtenu, Attendu (règle citée), Gravité
(Bloquant / Majeur / Mineur), Pièces (nom des captures, pas de données personnelles).

## Commentaire de recette (avant « A tester »)
`Recette <date> : <x/y> conformes. Commit <hash>. Points non testés : <liste ou « aucun »>.`

## Interdits
Mot de passe, jeton, URL de tunnel privé, numéro de téléphone ou donnée personnelle réelle dans un ticket.
