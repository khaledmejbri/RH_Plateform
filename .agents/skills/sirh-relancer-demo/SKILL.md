---
name: sirh-relancer-demo
description: Relancer le kit de démo SIRH et donner l'adresse publique au client. Utiliser quand l'utilisateur dit « relance les services », « ouvre cloudflare », « donne-moi l'URL », « le client ne peut plus se connecter », « endpoints en 404 / 502 / 503 », ou après un redémarrage du PC.
---

# Relancer la démo (kit 8090 + tunnel Cloudflare)

Utilise de préférence les outils MCP du serveur `sirh` ; les commandes équivalentes sont dans la règle
`exploitation.md`.

1. `kit_etat` : Docker répond-il ? les 12 conteneurs `sirh-*` sont-ils `Up` ? le tunnel tourne-t-il ?
2. Docker arrêté → `kit_demarrer_docker` (attend `docker info`). Conteneur arrêté → `docker compose -p sirh up -d
   --no-build <service>` depuis `deploy/` (jamais de reconstruction sans accord).
3. Attendre que la connexion réponde 400 (services enregistrés dans Eureka, 1 à 3 min), puis `kit_sante` : tout en 200.
   - 503 persistant → instance Eureka `DOWN` : voir « Redéployer » dans `exploitation.md` (étape 6).
   - « Ressource introuvable » sur un écran → web et référentiel d'images incohérentes : vérifier les tags avec
     `kit_etat` et en parler à l'utilisateur avant de changer une image.
4. Tester l'adresse publique actuelle (`recette/outils/adresse_publique.txt`). Si elle ne répond pas (code 000) →
   `kit_relancer_tunnel` : nouvelle adresse, fichiers `adresse_publique.txt` et `message_client.md` mis à jour.
5. Attendre que l'adresse publique réponde (200 sur `/`, 400 sur signin).
6. Rendre à l'utilisateur : l'adresse, l'état des services, l'APK courant (dernier `recette/RH-Connect-*.apk`),
   le rappel que l'adresse change à chaque relance et que le PC doit rester allumé.
