# Architecture — Bases de données et persistance

**Rôle** : architecte données  
**Modèle** : **une base logique par service** (ou schéma isolé sur même moteur en phase de transition).

---

## 1. Principes

| Principe | Détail |
|----------|--------|
| **Propriété des données** | Seul le service propriétaire écrit dans sa base. |
| **Pas de JOIN inter-services** | Les lectures croisées passent par API ou vues matérialisées / réplicas en lecture. |
| **Migrations versionnées** | Flyway ou Liquibase par service. |
| **Identifiants** | UUID pour clés techniques exposées ; matricule côté métier dans référentiel. |

---

## 2. Inventaire par service

### 2.1 Cible minimisée (recommandée)

| Service | Base / schéma suggéré | Contenu principal |
|---------|------------------------|-------------------|
| **svc-identite-acces** | `auth_db` | Comptes, secrets hashés, refresh tokens (si utilisés), liaisons `user_id` ↔ `employe_id`. |
| **svc-rh-plateforme** | **`rh_db`** (un seul moteur PostgreSQL) avec **schémas logiques** optionnels : `referentiel`, `demandes`, `documents`, `notes`, `plaintes`, `formation`, `recrutement`, `evaluations`, `epi` | Tout le métier RH événementiel et référentiel si fusionné (M1–M8 + collaborateurs/unités). |
| **svc-presence** | `presence_db` | QR sessions, pointages, anomalies, paramètres zones (M9) — **séparé** pour isolation charge / contraintes. |
| **svc-notification** | `notification_db` | Files sortantes, états envoi, préférences utilisateur, templates. |
| **svc-ia** (futur) | `ia_db` ou stateless + stockage objet | Modèles, prompts, traces si nécessaire. |

*Un seul utilisateur DB par service reste la règle ; `svc-rh-plateforme` possède **une** base dont il est seul propriétaire, avec schémas pour clarté sans multiplier les microservices.*

### 2.2 Variante historique (multi-bases fines)

Si l’on ne regroupe pas le métier : une base par ancien microservice (`referentiel_rh_db`, `demandes_db`, …). **Non recommandé** tant que l’équipe est petite et le déploiement doit rester simple.

---

## 3. Stockage fichiers (binaire)

| Composant | Rôle |
|-----------|------|
| **Objet** (S3, MinIO, Azure Blob) | Contenu des PJ, PDF générés, médias plaintes. |
| **Métadonnées** | Dans la base du service métier : clé objet, checksum, taille, type MIME, rétention. |

---

## 4. Réplication et lecture

| Besoin | Pattern |
|--------|---------|
| Tableaux de bord multi-domaines | Requêtes par service + agrégation côté BFF / gateway **ou** projet analytics (CQRS). |
| Recherche full-text notes | Index Elasticsearch / OpenSearch alimenté par événements. |

---

## 5. Cohérence inter-services

| Scénario | Approche |
|----------|----------|
| Création employé | `referentiel-rh` crée l’employé → événement `EmployeCree` → `identite-acces` crée compte (saga orchestrée ou chorégraphie). |
| Suppression / départ | Workflow métier + anonymisation RGPD planifiée par référentiel avec propagation d’événements. |

---

## 6. Environnements

| Env | Données |
|-----|---------|
| **Dev** | Docker Compose : une instance PostgreSQL par service ou schémas séparés. |
| **Recette / Prod** | Instances managées, sauvegardes PITR, chiffrement au repos. |

---

## 7. Correspondance avec l’existant

| Projet actuel | Base configurée | Cible regroupement |
|----------------|-----------------|-------------------|
| `authenctication` / `svc-identite-acces` | `authentication_db` | Inchangé |
| `svc-referentiel-rh` | `referentiel_rh_db` | Schéma `referentiel` dans **`rh_db`** (`svc-rh-plateforme`) |
| `humain_resource` | `hr_db` | Schéma `documents` / `contrats` dans **`rh_db`** |

*Stratégie minimisée : une base **`rh_db`** pour toute la plateforme RH métier ; migrations Flyway/Liquibase par schéma.*

---

*Voir `04-apis-evenements-integrations.md` pour la synchronisation via événements.*
