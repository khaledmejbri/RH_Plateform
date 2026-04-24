# Documentation RH-Évènement

Ce dossier regroupe la **conception** (analyse métier / système) et l’**architecture** (décisions techniques).

## Conception (analyste système)

| Fichier | Contenu |
|---------|---------|
| [conception/01-perimetre-et-vision.md](conception/01-perimetre-et-vision.md) | Périmètre, vision, objectifs |
| [conception/02-acteurs-et-droits-metier.md](conception/02-acteurs-et-droits-metier.md) | Acteurs, RACI, profils |
| [conception/03-modules-fonctionnels-et-flux.md](conception/03-modules-fonctionnels-et-flux.md) | Modules CDC, flux, statuts |
| [conception/04-dictionnaire-donnees-metier.md](conception/04-dictionnaire-donnees-metier.md) | Entités et attributs métier |

## Architecture (architecte)

| Fichier | Contenu |
|---------|---------|
| [architecture/01-principes-et-vues.md](architecture/01-principes-et-vues.md) | Principes, vue logique |
| [architecture/02-microservices-et-responsabilites.md](architecture/02-microservices-et-responsabilites.md) | Découpage services (**cible minimisée** : `svc-rh-plateforme` + notification + présence + IA) |
| [architecture/03-bases-de-donnees.md](architecture/03-bases-de-donnees.md) | Bases par service, fichiers |
| [architecture/04-apis-evenements-integrations.md](architecture/04-apis-evenements-integrations.md) | REST, événements, intégrations |
| [architecture/05-outillage-devops-observabilite.md](architecture/05-outillage-devops-observabilite.md) | Outils, CI/CD, observabilité |
| [architecture/06-securite.md](architecture/06-securite.md) | AuthN/Z, données, réseau |

**Référence métier** : `Cahier des Charges Application RH-Évènement V1 .docx` (dossier projet parent).
