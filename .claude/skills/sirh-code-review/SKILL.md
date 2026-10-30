---
name: sirh-code-review
description: Revue de code et validation avant commit du SIRH RH-Évènement. Utiliser pour relire un diff (backend Spring, web React, mobile Flutter, déploiement) sous l'angle correction, sécurité, architecture, SOLID, tests et compatibilité, et rendre un verdict GO / NO-GO.
---

# Revue de code SIRH

## Périmètre
`git diff` + fichiers non suivis (`git status --porcelain`), tickets concernés, rapport de recette.
Relire chaque fichier modifié en entier autour du changement, et les appelants des méthodes modifiées.

## Grille (dans cet ordre)
1. **Correction** : le code fait-il ce que disent les critères d'acceptation ? cas limites (null, liste vide,
   bornes, fuseau `Africa/Tunis`, doublons, concurrence) ; transactions ; lecture paresseuse hors transaction.
2. **Sécurité** : `@PreAuthorize` sur tout nouvel endpoint ; identité depuis le JWT ; IDOR (accès aux données
   d'un autre collaborateur ou d'un autre nœud) ; entrée validée ; pas d'entité JPA exposée ; aucun secret, UUID
   ou URL en dur ; `/internal/**` protégé par jeton ; rien de sensible dans les logs ou les tickets.
3. **Architecture** : couches respectées ; gateway seule entrée (route ajoutée) ; Kafka JSON texte via producteur
   déclaré ; notifications au compte ; un service ne lit pas la base d'un autre.
4. **SOLID / lisibilité** : responsabilité unique, pas de duplication évitable, noms métier français cohérents
   avec le voisinage, constantes nommées, pas de code mort ni de commentaire obsolète.
5. **Tests** : une règle = un test ; RBAC 200 / 403 ; tests qui passent vraiment (relancer) ; pas de test désactivé.
6. **Compatibilité** : Flutter 3.29.2 ; base existante (`ddl-auto=update`, colonnes nullables, runner de
   contrainte CHECK pour un enum) ; kit `deploy/` (nouvelle variable d'environnement dans `.env.example` et
   `docker-compose.yml`) ; contrat d'API identique entre backend, web et mobile.
7. **Documentation** : décision dans `docs/sirh_basefonctionnelle.md`, ADR si structurant, `CONTEXTE.md` prévu.

## Gravité
- **Bloquant** : faille de sécurité, perte ou corruption de données, règle métier fausse, build ou tests rouges.
- **Majeur** : cas limite réaliste non géré, test manquant sur une règle, incompatibilité Codemagic ou déploiement.
- **Mineur** : lisibilité, nommage, duplication légère.

## Rendu
| Gravité | Fichier:ligne | Problème | Scénario concret | Correction suggérée (agent) |

Verdict : **GO** (zéro bloquant, zéro majeur) ou **NO-GO** (liste à corriger). Pas de constat sans scénario.
