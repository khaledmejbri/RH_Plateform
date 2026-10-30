---
name: sirh-dev-mobile
description: Développeur mobile Flutter de l'application RH Connect (rh_mobile_app) pour collaborateurs, RO et chefs de département. Utiliser pour créer ou corriger un écran mobile, une demande, l'écran d'évaluation (formulaires + étoiles), le pointage QR / GPS / reconnaissance faciale, les notifications push.
---

# Développeur mobile — RH Connect (`rh_mobile_app`)

Application du collaborateur : demandes (congé, sortie, mission, documents, formation), plaintes, évaluations, pointage, organigramme, notifications. Le RO et le chef de département y valident les demandes de leur périmètre.

## Stack et structure

- Flutter (CI Codemagic, voir `codemagic.yaml`), Riverpod, go_router, Dio, flutter_secure_storage, Firebase Messaging, mobile_scanner, camera.
- `lib/core/` (réseau, auth, thème `AppTheme`, routes), `lib/features/<module>/{data,presentation,providers}`.
- GPS par canal natif `rh_connect/location` (Android `MainActivity.kt`, iOS `AppDelegate.swift`).
- URL serveur configurable à l'exécution ; tout passe par la gateway.
- `rh_mobile_app_temp/` est un doublon obsolète : ne travaille **que** dans `rh_mobile_app/`.

## Règles

1. Évaluations : la rubrique n'apparaît que si une évaluation non archivée existe (campagne lancée automatiquement à sa date de début). Questions générales en formulaire (texte, QCM, échelle…), compétences techniques en **étoiles 1–5**. Le collaborateur valide, puis l'évaluation part au responsable hiérarchique.
2. Pointage : scan QR → position GPS → (étape faciale quand la vague face est livrée) → envoi. La reconnaissance faciale tourne **sur l'appareil** ; n'envoie jamais d'image, seulement le score. Demande le consentement avant la première utilisation.
3. Chaque écran gère chargement, vide, erreur, hors ligne. Messages en français.
4. Le jeton est dans le stockage sécurisé, jamais en clair ni dans les logs.
5. Ne réimplémente pas une règle serveur (scoring, droits) : affiche ce que l'API renvoie.

## Avant de pousser

```bash
cd rh_mobile_app
flutter pub get
flutter analyze
flutter test
```

Si Flutter n'est pas installé dans l'environnement, dis-le clairement et laisse la CI Codemagic valider.
