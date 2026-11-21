---
trigger: always_on
---

# Méthode de travail (comment raisonner sur ce projet)

Ces habitudes ont fait la qualité des lots livrés jusqu'ici. Elles priment sur la vitesse.

## 1. Comprendre avant d'agir
- Reformule la demande en une phrase et classe-la : **question**, **petite correction** (une règle claire, un ou
  deux fichiers), **lot** (plusieurs fichiers / services / canaux) ou **exploitation** (kit, tunnel, build).
- Lis `CONTEXTE.md` (dont « Reste à faire ») et la section concernée de `docs/sirh_basefonctionnelle.md`.
- Vérifie l'état réel dans le code (graphify d'abord) **avant** d'affirmer qu'une chose existe ou manque.
  Cite `fichier:ligne`. Une affirmation non vérifiée s'écrit « à vérifier ».

## 2. Ne jamais inventer
- Règle métier absente ou ambiguë → **stop** : propose 1 à 4 options avec ta recommandation et demande à
  l'utilisateur. Une fois tranchée, écris-la dans le doc fonctionnel (`D-xxx`) avant de coder.
- Architecture, noms, contrats d'API fixés par une spec ou un ADR : **on les suit à la lettre**. Un écart
  nécessaire se signale et se fait valider ; il ne se décide pas en silence.
- Ne devine pas un chemin, un nom de classe, un endpoint, une option de commande : vérifie-le.

## 3. Faire petit, vérifiable, réversible
- Découpe en lots dont les fichiers sont **disjoints** (backend `svc-*` / web `rh-admin-web` / mobile
  `rh_mobile_app` / docs). Un fichier n'a qu'un seul rédacteur à la fois.
- Imite le code voisin (style, nommage français, densité de commentaires, composants existants) au lieu de créer
  un nouveau motif.
- Un test par règle du ticket, plus les droits (200 / 403, autre collaborateur = 403) et les erreurs
  (400 / 404 / 409 / 422).
- Avant de supprimer, écraser ou redéployer : regarde la cible, garde une sauvegarde ou un tag d'image
  (`docker tag … :avant-<lot>`), annonce le retour arrière possible.

## 4. Vérifier soi-même, rapporter honnêtement
- « Terminé » = compilation + tests verts + vérification réelle (API appelée, écran ouvert, build Codemagic) quand
  c'est possible. Lance les commandes, lis leur sortie, ne suppose pas.
- Un critère non exécuté s'écrit « non testé (raison) », jamais « OK ». Un échec s'annonce avec la sortie.
- Un diagnostic se prouve : reproduis, isole la cause (logs, `docker logs`, requête directe), corrige la cause et
  non le symptôme, puis revérifie.
- Obstacle ordinaire (timeout, service lent, port occupé, Docker arrêté) : diagnostique et contourne avant de
  t'arrêter. Obstacle délibéré (fichier secret, garde-fou, décision client) : respecte-le et dis-le.

## 5. Économiser le contexte et le budget
- graphify et lectures ciblées plutôt que des fichiers entiers ; ne relis pas ce que tu viens d'écrire.
- Tâche lourde mais mécanique (saisie Jira, recette répétitive) → modèle léger (Flash). Décision, architecture,
  revue → modèle fort (Claude Opus ou Gemini Pro). Voir `03-boucle-qualite.md`.
- Résume en fin de tâche : ce qui est fait, vérifié, **non vérifié**, et la prochaine étape.

## 6. Communiquer avec l'utilisateur
- Réponds en français, phrases courtes, résultat d'abord. Pas de jargon inutile.
- Pose une question seulement quand la décision lui revient (règle métier, risque, coût, action irréversible ou
  visible du client : commit, push, envoi, déploiement sur le kit de démo).
- Le kit de démo (port 8090) et le tunnel Cloudflare peuvent être **en cours d'utilisation par le client** :
  préviens avant tout redémarrage ou redéploiement.
