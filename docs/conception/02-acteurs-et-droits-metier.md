# Conception — Acteurs et droits métier

**Rôle** : analyste système  
**Niveau** : besoins d’habilitation et responsabilités (sans implémentation technique).

---

## 1. Cartographie des acteurs

| Acteur | Description | Canaux typiques |
|--------|-------------|-----------------|
| **Employé** | Soumet des demandes, consulte notes, formations, pointage, ses dossiers. | Mobile, Web (selon politique) |
| **Chef hiérarchique** | Valide / refuse des demandes de son équipe ; peut initier certaines demandes groupées (ex. EPI). | Web, Mobile |
| **Service RH / RRH** | Pilote workflows, traite documents administratifs, paramétrages, tableaux de bord. | Web |
| **Direction** | Publication notes de service ; validations de niveau direction selon règles. | Web |
| **HSE** | Catalogue EPI, validation attribution, stock, alertes, reçus. | Web |
| **Service technique** | Destinataire des plaintes externes / communautaires (selon circuit). | Web |
| **Direction Environnement & Social** | Co-destinataire des plaintes externes (selon CDC). | Web |
| **Système / intégrations** | Notifications, génération PDF, archivage (vue métier : « le système notifie »). | — |

---

## 2. Matrice des responsabilités par domaine (RACI simplifié)

*Légende : R = réalise, A = accountable, C = consulté, I = informé*

| Domaine | Employé | Chef | RH | Direction | HSE |
|---------|---------|------|-----|-----------|-----|
| Demande congé / sortie / mission | R | A | A (final) | I | I |
| Documents administratifs (demande) | R | I | A | I | I |
| Notes de service | I | I | C | A | I |
| Plainte interne | R | I | A | I | C |
| Plainte externe | I | R (création par resp.) | A | C | R/C |
| Formation (besoin, invitation, éval.) | R/C | A/C | A | I | I |
| Besoin en personnel | C | R | A | A | I |
| Évaluations | R | A | A | C | I |
| EPI (demande / attribution) | I | R (demande groupe) | C | I | A |
| Présence (pointage) | R | I | A | I | C |

*À affiner avec les fiches de poste réelles de l’entreprise.*

---

## 3. Règles d’accès métier (intention)

| Règle | Détail |
|-------|--------|
| **Confidentialité RH** | Un employé ne consulte que ses propres dossiers et demandes, sauf délégation explicite. |
| **Validation hiérarchique** | Une étape « supérieur » ne concerne que la ligne hiérarchique du demandeur. |
| **Séparation des plaintes** | Plainte interne vs externe : circuits et visibilités différents ; tableau de bord interservices pour l’externe. |
| **EPI** | Stock et attributions visibles par HSE et RH ; historique accessible aux rôles définis (demandeur, HSE, RH). |
| **Présence** | Données de présence consultables par RH pour pilotage ; pas de suivi de localisation continu (principe CDC). |

---

## 4. Profils applicatifs suggérés (à valider)

| Profil | Usage principal |
|--------|-----------------|
| `EMPLOYE` | Self-service RH + consultation. |
| `CHEF_SERVICE` | Validations équipe, demandes groupées EPI, besoins personnel. |
| `RH` | Traitement, paramétrage métier, tableaux de bord. |
| `RRH` | Même périmètre que RH avec validations finales selon processus. |
| `DIRECTION` | Notes de service, validations stratégiques. |
| `HSE` | EPI (catalogue, stock, attribution). |
| `TECHNIQUE` | Suivi plaintes externes (lecture / mise à jour statut selon règles). |
| `ENV_SOCIAL` | Idem pour direction Environnement & Social. |

---

## 5. Points d’atelier avec le métier

- Qui peut **déléguer** une validation en cas d’absence du supérieur ?
- Gestion des **structures matricielles** (double hiérarchie) ?
- **SLA** par type de demande et escalade automatique ou non ?
- **Rétention** des données (plaintes, pointage, EPI) et anonymisation éventuelle.

---

*Conception — pas de choix technique (rôles techniques IAM) : voir document d’architecture sécurité.*
