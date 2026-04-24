# RH Mobile — Flutter (iOS / Android / Web)

Application mobile pour les collaborateurs et le RRH : authentification, plaintes, demandes administratives (congés, missions, sorties), demandes de documents avec suivi FIFO / SLA.

## Prérequis

- [Flutter SDK](https://docs.flutter.dev/get-started/install) 3.16+
- Un émulateur ou appareil, ou Chrome pour le web.

## Première installation

Depuis ce dossier :

```bash
flutter create . --project-name rh_mobile_app --org com.hr.rh
```

Cette commande ajoute les dossiers `android/`, `ios/`, `web/`, etc. sans écraser votre `lib/` ni `pubspec.yaml`.

Puis :

```bash
flutter pub get
flutter run
```

## Configuration API

Par défaut l’URL pointe vers la gateway locale. Modifiez `lib/core/constants/api_constants.dart` ou passez :

```bash
flutter run --dart-define=API_BASE_URL=https://votre-api.com
```

Pour le mode démo sans backend, l’écran de connexion propose **« Continuer en démo »** (token fictif + navigation complète).

## Structure

- `lib/core/` — thème, routeur, client HTTP, widgets réutilisables
- `lib/features/auth/` — connexion / inscription
- `lib/features/home/` — tableau de bord et accès aux modules
- `lib/features/plaintes/`, `demandes_admin/`, `documents/` — workflows RH

## Production

- Remplacer le flux démo par vos endpoints OAuth2 / identité (`svc-identite-acces`).
- Activer certificate pinning (package dédié) si requis.
- Configurer `signing` Android / iOS pour les stores.
