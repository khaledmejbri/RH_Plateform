---
name: sirh-autopilote
description: Pilote automatique du SIRH, du besoin jusqu'à la livraison et la démo, avec deux arrêts seulement (questions métier, puis accord de livraison). Utiliser quand l'utilisateur tape « /sirh-autopilote <besoin> », « /sirh-autopilote lot W5 », « /sirh-autopilote reprends <nom> », ou dit « fais tout », « de bout en bout », « jusqu'à la démo ». Enchaîne expert fonctionnel, architecte, développeurs, testeur, relecteur et livraison par sous-agents, en tenant un fichier d'état pour pouvoir reprendre.
---

# Pilote automatique SIRH

Tu es l'**orchestrateur**. Tu enchaînes les rôles toi-même (étapes 1, 2, 6, 7, 8) ou par sous-agents de
`.agents/agents/` (outil `invoke_subagent` : `dev-backend`, `dev-web`, `dev-mobile`, `testeur`, `code-reviewer`,
`redacteur-jira`). Les sous-agents ne partagent pas ta mémoire : donne-leur **le texte complet** dont ils ont besoin
(prompt, décisions, rapport, chemins), jamais « comme vu plus haut ».

L'utilisateur n'intervient qu'à **deux arrêts** : 🛑 1 (règles métier) et 🛑 2 (livraison). Entre les deux, tu ne
lui demandes rien, sauf s'il y a un blocage réel (§ « Limites »).

## 0. Démarrage

1. Lis `CONTEXTE.md`, `AGENTS.md`, les règles `.agents/rules/` (surtout `01-methode`, `02-securite`,
   `03-boucle-qualite`, `exploitation`). Pour toute question sur le code : `graphify query "<question>"` d'abord.
2. Selon l'entrée :
   - **`reprends <nom>`** : lis `docs/autopilote/<nom>/etat.md` et repars de l'étape notée, sans rien refaire.
   - **`lot <Wx>`** (prompts prêts dans `docs/specs/workflow/lots/<Wx>/`) : saute les étapes 1 et 2 ; le prompt
     `prompt-gemini-<Wx>*.md` le plus récent est le prompt de développement, `prompt-testeur-<Wx>*.md` celui de la
     recette. Va directement à l'étape 3, **sans arrêt 1**.
   - **Un besoin** (texte libre, retour client, bug) : étape 1.
3. Crée `docs/autopilote/<AAAAMMJJ>-<nom-court>/etat.md` (dossier non versionné) et tiens-le à jour **après chaque
   étape** : étape en cours, branche, dossier de travail, décisions, fichiers produits, boucles faites, prochaine
   action. C'est ce qui permet de reprendre dans une nouvelle conversation si celle-ci devient trop longue.
4. Dossier et branche de travail (jamais dans `C:\Local\Khaled\project`, jamais directement sur `delivery`) :
   ```bash
   cd /c/Local/Khaled/project-delivery && git pull --ff-only origin delivery
   git worktree add -b ap/<nom-court> ../project-<nom-court> delivery
   ```
   Pour un lot de la spec workflow, utilise la branche et le dossier que son prompt impose.

## 1. Analyse fonctionnelle (rôle `expert-fonctionnel`, toi-même)

- Applique le skill `sirh-expert-rh`. Sources : `docs/sirh_basefonctionnelle.md` (règles D-xxx déjà tranchées),
  le code existant (graphify), `CONTEXTE.md` (décisions et points ouverts).
- Classe le besoin : **petite correction** (un libellé, un bug localisé, une ligne) ou **lot**.
- Écris `docs/autopilote/<nom>/fiche-besoin.md` : contexte, règles (numérotées, chiffrées), user stories, critères
  d'acceptation testables (rôle, action, résultat, code HTTP), cas d'erreur (400 / 403 / 404 / 409 / 422), rôles,
  canaux touchés (API, web, mobile), ce qui est hors périmètre.
- Chaque point incertain devient une **question avec une réponse par défaut proposée** et sa raison.

### 🛑 Arrêt 1 — les règles métier (toujours, sauf entrée `lot <Wx>`)

Rends à l'utilisateur, en une fois et en moins de 30 lignes :
- le besoin reformulé en 3 lignes, et le classement petite correction / lot ;
- les règles retenues (une ligne chacune) ;
- **toutes** les questions numérotées, chacune avec « Par défaut : … » ;
- la phrase : « Répondez aux questions (ou « défauts OK »), et je fais le reste jusqu'au rapport final. »

Puis **arrête-toi**. À la réponse : reporte les réponses dans la fiche et dans `docs/sirh_basefonctionnelle.md`
(nouvelles décisions D-xxx, marquées « tranché le <date> » ou « défaut à confirmer client »), puis continue sans
redemander.

## 2. Conception (rôle `architecte`, toi-même) — sautée pour une petite correction

- Applique le skill `sirh-architecte`. Décide : services et dossiers touchés, tables et colonnes, endpoints
  (méthode, chemin, rôle `@PreAuthorize`, corps, codes d'erreur), événements Kafka, sécurité, migration
  (`ddl-auto=update` + runner CHECK si un enum stocké change), impact déploiement.
- Écris `docs/autopilote/<nom>/decisions.md` (décisions numérotées `D-<NOM>-01…`, contrat d'API complet) et
  découpe en sous-lots à **dossiers disjoints** : backend / web / mobile.
- Écris, pour chaque sous-lot, `prompt-dev-<canal>.md` sur le modèle des prompts de
  `docs/specs/workflow/lots/W5/prompt-gemini-W5.md` : lectures obligatoires, frontière de fichiers, décisions,
  ordre de développement, tests exigés (un par critère, 200 / 403 anti-IDOR, erreurs), commandes de vérification,
  format du rapport. Écris aussi `prompt-recette.md` sur le modèle de `prompt-testeur-W5.md`.
- Jira (si le serveur MCP `atlassian` répond) : sous-agent `redacteur-jira` avec la fiche → tickets SCRUM ; note
  les clés dans `etat.md`. Jira indisponible : continue et note-le.

## 3. Développement (sous-agents `dev-backend` · `dev-web` · `dev-mobile`)

- Un sous-agent par sous-lot, avec le **contenu entier** de `prompt-dev-<canal>.md` + le dossier de travail.
  Plusieurs sous-agents en parallèle **seulement** si leurs dossiers sont disjoints ; sinon l'un après l'autre.
- Exige le rapport de développement (fichiers, règle → test, sortie réelle des commandes, écarts, questions) ;
  enregistre-le dans `docs/autopilote/<nom>/dev-<canal>-<n>.md`.
- Un sous-agent pose une question métier → réponds-y si `fiche-besoin.md` ou `decisions.md` la tranchent ; sinon
  c'est un blocage (§ « Limites »).
- Vérifie toi-même avant de passer à la suite : relance les commandes de vérification du sous-lot (`mvn -B -pl <module>
  -am test`, `npx tsc --noEmit -p . && npx vite build`, `flutter analyze && flutter test`). Un rapport « vert »
  dont la commande échoue chez toi = retour au développeur.

## 4. Recette (sous-agent `testeur`, Flash)

- Donne-lui le contenu entier de `prompt-recette.md`, les rapports de développement et le dossier de travail.
- Environnement : services **locaux** reconstruits depuis le dossier de travail (eureka 8761, gateway 8080, services
  touchés). **Jamais le kit 8090** à cette étape.
- Rapport `docs/autopilote/<nom>/recette-<n>.md` : score `x/y`, anomalies (gravité, étapes, obtenu, attendu).
- Anomalie Bloquante ou Majeure → étape 3 avec **uniquement** la liste des anomalies (« corrige ceci, rien
  d'autre »), puis recette ciblée sur les critères KO et leurs voisins.

## 5. Revue (sous-agent `code-reviewer`, **nouveau**, qui n'a pas écrit le code)

- Donne-lui : dossier et branche, `git diff delivery...HEAD`, fiche, décisions, rapports de recette.
  Applique le skill `sirh-code-review` : il vérifie **dans le code** chaque décision et chaque test cité (un test
  qui bouchonne ce qu'il prétend tester ne compte pas), relance les tests, cherche HTTP dans une transaction,
  `@PreAuthorize` manquant, secret ou UUID en dur, IDOR, régression.
- Rapport `docs/autopilote/<nom>/revue-<n>.md` : constats (gravité, fichier:ligne, scénario, correction) et GO / NO-GO.
- NO-GO → étape 3 avec la liste « corrections pour le développeur », puis 4 ciblée, puis 5 avec un **nouveau**
  sous-agent relecteur.

## 6. Rapport final et 🛑 Arrêt 2 — accord de livraison

Quand la revue interne est GO, rends à l'utilisateur (moins de 40 lignes) :
- ce qui a été fait (fonctionnel, en mots simples), branche, commits ;
- score de recette, verdict de revue, nombre de boucles ;
- ce qui n'a **pas** été vérifié (écran non vu, téléphone, Kafka absent…) et les défauts proposés à faire confirmer
  par le client ;
- **le message prêt à coller** dans une nouvelle conversation **Claude Opus** pour la revue finale :
  ```
  /sirh-code-review Branche ap/<nom>, dossier C:\Local\Khaled\project-<nom>. Documents : docs/autopilote/<nom>/
  (fiche-besoin.md, decisions.md, recette-<n>.md, revue-<n>.md). Vérifie chaque décision et chaque test dans le
  code. GO / NO-GO ; si NO-GO, écris docs/autopilote/<nom>/corrections-opus.md (consignes numérotées).
  ```
- la phrase : « Après la revue Opus : répondez « livre » (et « démo » si vous voulez la démo client), ou collez
  corrections-opus.md pour que je corrige. »

Puis **arrête-toi**. Si l'utilisateur colle des corrections : étapes 3 → 4 → 5, puis nouveau rapport et arrêt 2.

## 7. Livraison (sur le mot « livre » de l'utilisateur seulement)

Skill `sirh-livraison` : fusion `--no-ff` de la branche dans `delivery` (dossier `project-delivery`), compilation
de tout le dépôt (`mvn -B -q -T4 test-compile`), push `origin` puis `sirh`, build Codemagic `rh-connect-android` si
le mobile a changé (outil MCP `codemagic_lancer_build`, puis `codemagic_etat_build` jusqu'au résultat ; rouge =
retour à l'étape 3), Jira « A tester » avec le score de recette, `CONTEXTE.md` (journal + reste à faire) commité et
poussé, `graphify update .`.

## 8. Démo (sur le mot « démo » de l'utilisateur seulement)

- Les données et images du kit 8090 sont celles du client : sauvegarde des bases touchées d'abord (règle
  `exploitation.md`, « Redéployer », étape 0).
- Reconstruire **seulement** les services touchés : outil MCP `kit_redeployer(service, image_tag, confirmer=True)`
  (tag daté, l'ancienne image reste disponible), en respectant l'ordre de la règle (gateway puis `sirh-web-1`
  redémarrés, instances Eureka `DOWN` supprimées).
- Puis skill `sirh-relancer-demo` : santé des services, tunnel, adresse publique, APK courant.
- Rends : l'adresse, ce qui a changé pour le client, comment revenir à l'image précédente.

## Limites (pour ne pas tourner en rond ni dépenser pour rien)

- Au plus **3 boucles** développement → recette et **3 revues internes** NO-GO. Au-delà : arrête-toi, rends l'état
  (ce qui reste KO, pourquoi, ce que tu proposes) et attends l'utilisateur.
- Une règle métier manque ou se contredit après l'arrêt 1 : arrête-toi et pose **cette** question seulement.
- Une action hors de ce skill (autre service, donnée réelle à modifier, dépendance nouvelle) : demande d'abord.
- Conversation trop longue (tu perds le fil, réponses qui se répètent) : mets `etat.md` à jour et dis à
  l'utilisateur d'ouvrir une nouvelle conversation avec `/sirh-autopilote reprends <nom>`.

## Interdits absolus (rappel des règles 02 et 03)

- Aucun commit sur `delivery`, aucun push, avant « livre ». Aucune action sur le kit 8090 ou le tunnel avant « démo ».
- Jamais lire ni afficher `gittoken.txt`, `deploy/.env`, `recette/outils/recette_pw.txt`, `CODEMAGIC_TOKEN`,
  `DEMO_PW`, `INTERNAL_API_TOKEN`. Jamais de `git add -A`, `git stash`, `reset --hard`, push forcé, `--no-verify`.
- Jamais écrire dans `rh_mobile_app_temp/`. Données de recette préfixées `[RECETTE-<NOM>]` ; ne supprimer que les
  siennes, jamais une table entière.
- N'annoncer que ce qui est fait et vérifié, avec la sortie réelle des commandes.
