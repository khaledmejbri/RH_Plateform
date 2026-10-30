---
paths:
  - "rh_mobile_app/**"
  - "codemagic.yaml"
---

# Mobile Flutter (RH Connect)

- **Compatibilité Flutter 3.29.2** (Codemagic) : aucune API plus récente. Ex. `DropdownButtonFormField` →
  `value:` (pas `initialValue`, 3.33+). En local on a 3.41 : une compilation locale réussie ne suffit pas.
- Travailler uniquement dans `rh_mobile_app/` (jamais `rh_mobile_app_temp/`).
- Structure `lib/features/<module>/{data,presentation,providers}` ; Riverpod, go_router, Dio existants.
- Widgets courts, logique dans les providers ou repositories ; pas d'appel HTTP dans un widget.
- États chargement / vide / erreur / hors ligne, messages en français.
- Jeton dans `flutter_secure_storage`, jamais dans les logs ; aucune image ni biométrie envoyée au serveur.
- Code natif GPS : `MainActivity.kt` / `AppDelegate.swift` (canal `rh_connect/location`), garder `isMock` et `fresh`.
- Vérifier : `flutter analyze` (zéro nouvel avertissement) et `flutter test`.
