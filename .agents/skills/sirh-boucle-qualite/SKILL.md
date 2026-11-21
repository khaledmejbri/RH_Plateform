---
name: sirh-boucle-qualite
description: Orchestration du SIRH du besoin au commit dans Antigravity. Utiliser dès qu'un besoin complet arrive (retour client ou testeur, nouvelle fonctionnalité, lot de la spec workflow) ou quand l'utilisateur dit « lance le lot », « boucle qualité », « recette puis revue ». Enchaîne expert fonctionnel → Jira → architecte → développement (Gemini) → recette (modèle léger) → revue (Claude Opus) → livraison, et fournit les prompts et formats de rapport à échanger.
---

# Boucle qualité SIRH (Antigravity)

L'agent principal orchestre. Chaque étape est confiée à un sous-agent de `.agents/agents/` (outil
`invoke_subagent`) **ou** à une nouvelle conversation avec le bon modèle sélectionné. Les agents ne partagent pas
leur mémoire : transmets **en entier** la sortie d'une étape à la suivante (fiche, clés Jira, plan, rapport, diff).

## 0. Cadrer
- Lire `CONTEXTE.md`. Classer : petite correction (→ étape 5, puis 6 ciblée et 7) ou lot.
- Pour un lot de la spec du moteur de workflow : `docs/specs/SPEC-moteur-workflow.md`, ADR
  `docs/adr/ADR-moteur-workflow.md`, prompts prêts dans `docs/specs/workflow/lots/<LOT>/` (`prompt-gemini-*.md`,
  prompt du testeur). Ces documents **font foi** : on ne les réinterprète pas.

## 1. Besoin — `expert-fonctionnel` (Claude Opus ou Gemini Pro)
Entrée : texte exact de l'utilisateur. Sortie : fiches besoin (format du skill `sirh-expert-rh`) +
`docs/sirh_basefonctionnelle.md` à jour. Questions ouvertes → les poser à l'utilisateur, puis relancer.

## 2. Tickets — `redacteur-jira` (Flash)
Fiches → tickets SCRUM (JQL anti-doublon d'abord). Skill `sirh-redaction-jira`.

## 3. Relecture des tickets — `testeur` moment 1 (Flash)
Chaque règle chiffrée ? chaque critère testable (rôle, action, résultat, code HTTP) ? cas 403 / 400 / 409 / 422 ?

## 4. Conception — `architecte` (Claude Opus), si plusieurs services, Kafka, table, enum stocké, sécurité, déploiement
Sortie : ADR + plan par lot avec **dossiers disjoints** et contrat d'API complet.

## 5. Développement — `dev-backend` · `dev-web` · `dev-mobile` (Gemini Pro)
Prompt type à donner au développeur :
```
Tu es <dev-backend|dev-web|dev-mobile> du SIRH. Applique le skill sirh-dev-<canal> et les règles .agents/rules.
Lot : <id>. Documents qui font foi : <spec § …>, <ADR>, <plan>, tickets <SCRUM-xx>.
Périmètre de fichiers autorisé : <dossiers>. Interdit d'en sortir.
Tu ne changes ni l'architecture, ni les noms, ni le contrat d'API, ni les règles. En cas de doute : arrête-toi et
pose la question (ne devine pas).
Terminé = code + un test par règle + RBAC 200/403 + erreurs 400/409/422 + commandes de vérification vertes :
<mvn … test | npx tsc --noEmit -p . && npx vite build | flutter analyze && flutter test>.
Rends le RAPPORT DE DÉVELOPPEMENT (format ci-dessous).
```
Rapport de développement :
```
## Rapport de développement — lot <id>
- Fichiers créés / modifiés : <liste>
- Règles → tests : | Règle | Test | Résultat |
- Commandes lancées et résultat (copie des dernières lignes) : …
- Écarts à la spec (doivent être vides ou validés) : …
- Questions / points ouverts : …
```

## 6. Recette — `testeur` moment 2 (Flash)
Préparer : services modifiés reconstruits et démarrés (kit 8090 : règle `exploitation.md`, prévenir l'utilisateur).
Prompt type :
```
Tu es testeur QA du SIRH (skill sirh-testeur, règle tests-recette). Lot <id>, tickets <SCRUM-xx>, critères :
<liste>. Environnement : <http://localhost:8090 | 8080>. Comptes : DRH01, DG01, ADM01, CS-WEB, COL-WEB, COL-MOB…
(connexion par recette/outils/seed_lib.py, jamais de mot de passe écrit).
Pour CHAQUE critère : exécute réellement (API, écran, mobile via les mêmes API), note obtenu / attendu.
Données de test préfixées [RECETTE-<id>] ; ne supprime que tes propres données.
Rends le RAPPORT DE RECETTE (format ci-dessous). Un critère non exécuté = « non testé (raison) ».
```
Rapport de recette :
```
## Recette lot <id> — <date> — score x/y
| ID | Critère | Rôle | Étapes | Attendu | Obtenu | Verdict (OK / KO / non testé) |
### Anomalies
- [Bug] <symptôme> — gravité <Bloquant|Majeur|Mineur> — étapes — obtenu — attendu (règle D-xxx) — fichier suspect
### Données créées : …
### Verdict : CONFORME | NON CONFORME
```
NON CONFORME → renvoyer au développeur : « Corrige ces anomalies sans rien changer d'autre : <anomalies> », puis
recette ciblée sur les critères KO **et** les critères voisins.

## 7. Revue — `code-reviewer` (**Claude Opus**, nouvelle conversation)
Prompt type :
```
Tu es le relecteur du SIRH (skill sirh-code-review). Relis le diff du lot <id> : `git status` + `git diff`
(+ fichiers non suivis). Documents qui font foi : <spec/ADR/tickets>. Rapport de recette : <coller>.
Relance toi-même les tests du module touché. Rends le tableau des constats (Bloquant/Majeur/Mineur, fichier:ligne,
scénario concret, correction suggérée) puis GO / NO-GO, et la liste « Améliorations pour le développeur » rédigée
comme des consignes exécutables.
```
NO-GO → donner la liste au développeur (étape 5), puis 6 ciblée et 7 à nouveau. On boucle jusqu'au GO.

## 8. Livraison — agent principal, sur accord de l'utilisateur
Skill `sirh-livraison` : commit français, push `origin` + `sirh`, build Codemagic si mobile, Jira 41 « A tester »
avec commentaire de recette, `CONTEXTE.md` (journal + reste à faire), `graphify update .`.

## Répartition des modèles (budget)
- Claude Opus : décisions, architecture, **revue**. Gemini Pro : production de code. Flash : saisie Jira, recette
  répétitive, résumés.
- Ne jamais donner une décision à Flash, ni de la saisie à Opus.
