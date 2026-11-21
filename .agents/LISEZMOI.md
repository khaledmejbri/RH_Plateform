# Travailler sur le SIRH avec Antigravity

Ce dossier transmet à Antigravity tout le savoir-faire accumulé avec Claude Code : contexte du projet, méthode,
règles de sécurité, rôles, procédures et outils. Antigravity le découvre seul à l'ouverture du dossier du projet.

## 1. Installation (une seule fois, 10 minutes)

1. **Ouvrir le dossier** `C:\Local\Khaled\project` dans Antigravity (File ▸ Open Folder). Il lit automatiquement
   `AGENTS.md`, `.agents/rules/`, `.agents/skills/`, `.agents/agents/`, `.agents/hooks.json` et
   `.agents/mcp_config.json`.
2. **Serveur MCP `sirh`** (kit, tunnel, Codemagic, base, graphify) :
   ```bash
   python -m pip install --user -r tools/mcp-sirh/requirements.txt
   ```
   L'application Antigravity lit le fichier **global** `C:\Users\<vous>\.gemini\config\mcp_config.json` (déjà rempli
   sur le PC de Khaled, même contenu que `.agents/mcp_config.json`, qui ne sert qu'à la CLI). Redémarrer
   Antigravity, puis panneau Agent ▸ `…` ▸ MCP Servers : `sirh` et `atlassian` doivent être verts.
   L'écran Paramètres ▸ MCP Tools ne sert qu'aux permissions (Allow / Deny par outil), pas à déclarer un serveur.
3. **Jira** : au premier appel, `atlassian` ouvre le navigateur pour la connexion Atlassian (OAuth). Accepter avec
   votre compte. Node.js est requis (déjà installé).
4. **Garde-fou Git** (bloque les secrets et les chemins interdits au moment du commit, quel que soit l'outil) :
   ```bash
   git config core.hooksPath tools/git-hooks
   ```
5. **Secrets** : rien à copier. Le serveur MCP lit lui-même les variables utilisateur Windows `CODEMAGIC_TOKEN` et
   `DEMO_PW`. Ne les collez jamais dans une conversation.
6. Vérifier : nouvelle conversation ▸ « Utilise l'outil kit_etat » → l'état du kit s'affiche.

## 2. Quel modèle pour quoi (budget)

| Travail | Modèle | Pourquoi |
|---|---|---|
| Règles métier, architecture, ADR, **revue de code** | **Claude Opus 5.5** | Raisonnement le plus sûr ; c'est lui qui a conçu le projet |
| Développement backend / web / mobile | **Gemini Pro** (ou Gemini « Neon » si disponible) | Production de code rapide, grand contexte |
| Recette répétitive, tickets Jira, résumés | **Gemini Flash** | Peu coûteux, suffisant pour exécuter un plan |

Règle d'or : **le modèle qui a écrit le code ne fait pas sa revue**. Revue = nouvelle conversation avec Claude Opus.

## 3. Commandes du quotidien (skills, à taper avec `/`)

| Vous voulez… | Tapez |
|---|---|
| Reprendre le projet / savoir où on en est | `/sirh-reprise` |
| Relancer la démo et obtenir l'adresse pour le client | `/sirh-relancer-demo` |
| Faire un besoin complet (règles → Jira → code → recette → revue) | `/sirh-boucle-qualite` + votre besoin |
| Lancer un lot du moteur de workflow | `/sirh-boucle-qualite lot W1` (prompts dans `docs/specs/workflow/lots/`) |
| Relire le code avant commit | `/sirh-code-review` (avec Claude Opus sélectionné) |
| Commit, push, Jira « A tester », CONTEXTE.md | `/sirh-livraison` |
| Question sur le code | « graphify : … » ou outil `graphify_query` |

Skills métier chargés automatiquement selon la tâche : `sirh-expert-rh`, `sirh-architecte`, `sirh-dev-backend`,
`sirh-dev-web`, `sirh-dev-mobile`, `sirh-testeur`, `sirh-redaction-jira`, `graphify`, `ui-ux-pro-max`.

## 4. Le cycle d'un lot (moteur de workflow W0 → W3…)

1. Conversation **Gemini Pro** : « /sirh-boucle-qualite — développe le lot W1 en suivant
   `docs/specs/workflow/lots/W1/prompt-gemini-W1.md` à la lettre ». Exiger le rapport de développement.
2. Conversation **Gemini Flash** : coller le prompt testeur du lot (même dossier) → rapport de recette `x/y`.
   KO → recoller les anomalies dans la conversation Gemini Pro (« corrige uniquement ceci »), puis refaire 2.
3. Conversation **Claude Opus** : `/sirh-code-review` sur le diff + rapport de recette → GO / NO-GO.
   NO-GO → donner la liste « Améliorations pour le développeur » à Gemini Pro, puis 2 et 3 à nouveau.
4. GO → `/sirh-livraison` (vous décidez du commit et du push).

## 5. Ce qui protège le projet

| Protection | Où | Effet |
|---|---|---|
| Règles toujours actives | `.agents/rules/00` à `03` | Contexte, méthode, sécurité, boucle qualité injectés dans chaque conversation |
| Règles par type de fichier | `backend-java`, `web-react`, `mobile-flutter` | Conventions chargées quand on touche ces fichiers |
| Hook avant chaque outil | `.agents/hooks/garde_secrets.py` | Refuse lecture des secrets, `git add -A`, push forcé, `reset --hard`, `rh_mobile_app_temp` |
| Hook après écriture | `.agents/hooks/controle_code.py` | Signale UUID / secret / tunnel en dur, contrôleur sans `@PreAuthorize`, API Flutter > 3.29 |
| Pre-commit Git | `tools/git-hooks/pre-commit` | Dernière barrière : refuse le commit fautif |
| Outils MCP à confirmation | `kit_relancer_tunnel`, `kit_redeployer`, `codemagic_lancer_build` | Rien ne part sans `confirmer=True` (votre accord) |

Le format exact des hooks Antigravity peut évoluer : s'ils ne se déclenchent pas, le pre-commit et les règles
restent actifs. Les tester : `/sirh-reprise` puis demander de lire `gittoken.txt` → doit être refusé.

## 6. Économiser vos crédits

- Une conversation par lot et par rôle ; ne collez pas de gros fichiers, laissez l'agent utiliser graphify.
- Demandez des réponses courtes (« résultat d'abord, 10 lignes max »).
- Flash pour la recette et Jira ; Opus seulement pour décider et relire.
- `CONTEXTE.md` est la mémoire : faites-le mettre à jour à chaque livraison, et démarrez chaque conversation par
  `/sirh-reprise` au lieu de réexpliquer le projet.
