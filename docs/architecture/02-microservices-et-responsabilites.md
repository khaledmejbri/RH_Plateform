# Architecture — Microservices et responsabilités

**Rôle** : architecte système  
**Objectif** : **réduire le nombre de services** tout en gardant des frontières claires là où le métier ou la technique l’imposent (CDC RH-Évènement).

---

## 1. Stratégie de minimisation

| Principe | Choix |
|----------|--------|
| **Regrouper le métier RH** | Congés, documents administratifs, notes de service, plaintes, formation, besoin en personnel, évaluations, EPI, contrats / métadonnées documents, etc. vivent dans **un seule application déployable** (`svc-rh-plateforme`), structurée en **modules internes** (packages Maven ou sous-modules), pas en microservices séparés par défaut. |
| **Isoler ce qui diffère** | **Notifications** (canaux, files, retry), **pointage / présence** (QR, contraintes temps réel, géolocalisation ponctuelle), **IA** (modèles, GPU, cycle de vie) restent des **services à part** pour scaling, déploiement et sécurité distincts. |
| **Identité** | Reste un service **dédié** (`svc-identite-acces`) : périmètre sécurité, secrets, JWKS. |
| **Évolution** | Si un module devient trop volumineux ou une équipe dédiée émerge, extraire un bounded context vers son propre service (**strangler**), sans changer le contrat API côté gateway si possible. |

---

## 2. Carte cible (peu de services)

| Service | Responsabilité | Modules / périmètre CDC (rappel) |
|---------|----------------|-----------------------------------|
| **svc-identite-acces** | Authentification, JWT/OAuth2, comptes, rôles. | Transversal. |
| **svc-rh-plateforme** | **Tout le métier RH « événements et processus »** : demandes (congé, sortie, mission), documents admin, notes de service, plaintes, formation, besoin recrutement, évaluations, EPI, référentiel collaborateurs/unités (optionnellement fusionné ici pour minimiser encore), métadonnées fichiers RH. | M1 à M8 + référentiel si regroupé. |
| **svc-notification** | E-mail, push, templates, préférences, consommation d’événements émis par la plateforme RH. | Transversal. |
| **svc-presence** | Présence digitale : QR dynamique, scan, zones, anomalies, tableaux de bord RH (M9). | M9 — isolé (charge, contraintes lieu/heure). |
| **svc-ia** (futur) | Fonctions d’IA : aide à la rédaction, classification plaintes, suggestions formation, analytics avancées. | Extension ; API dédiée ou async. |
| **gateway** | Routage, rate limiting, TLS. | Infra. |
| **eureka** (ou équivalent) | Découverte des instances. | Infra. |
| **svc-fichiers** (optionnel) | Stockage objet (S3/MinIO), scan antivirus, URLs signées — **sinon** bibliothèque partagée dans `svc-rh-plateforme`. | Transversal léger. |

**En résumé** : au lieu de ~10 microservices métier, la cible est **1 service métier RH principal** + **identité** + **notification** + **présence** + **IA** plus tard (+ infra).

---

## 3. Structure interne recommandée (`svc-rh-plateforme`)

Même JAR / même processus, découpage **logique** (packages ou sous-modules Maven) :

| Module interne | Exemples de capacités |
|----------------|------------------------|
| `referentiel` | Unités, collaborateurs (existant `svc-referentiel-rh` → à fusionner ou consommer en lib). |
| `demandes` | Congés, autorisations de sortie, ordres de mission, workflows, PDF. |
| `documents` | Attestations, bulletins, file RH (ex. `humain_resource` / documents). |
| `notes` | Notes de service, diffusion, archive. |
| `plaintes` | Interne / externe, statuts, médias. |
| `formation` | Besoins, invitations, évaluations chaud/froid. |
| `recrutement` | Fiches besoin, workflow. |
| `evaluations` | Semestrielles, annuelles, exports. |
| `epi` | Catalogue, stock, attributions, reçus. |

Une **base PostgreSQL** par environnement pour ce service (`rh_db`) avec **schémas** optionnels (`referentiel`, `demandes`, …) pour garder la lisibilité sans multiplier les instances de base.

---

## 4. État actuel du dépôt (migration vers la cible)

| Composant existant | Cible minimisée |
|--------------------|-----------------|
| `svc-identite-acces` | Inchangé (service séparé). |
| `svc-referentiel-rh` | **Intégrer** dans `svc-rh-plateforme` (module `referentiel`) ou le garder temporairement puis fusionner pour un seul déploiement RH. |
| `humain_resource` | **Intégrer** dans `svc-rh-plateforme` (module `documents` / `contrats`). |
| Nouveaux flux congés, plaintes, etc. | Développer dans le même artefact `svc-rh-plateforme`. |

---

## 5. Frontières et règles

| Règle | Détail |
|-------|--------|
| **Transactions métier** | Les workflows qui touchent plusieurs « modules » (ex. demande + pièce jointe) restent dans **la même** unité déployable → cohérence plus simple (transactions locales). |
| **Notifications** | `svc-rh-plateforme` **publie** des événements ; `svc-notification` **exécute** l’envoi (pas de logique métier RH dans le service notification). |
| **Présence** | Appels REST ou événements depuis `svc-rh-plateforme` si besoin (ex. vérifier qu’un employé est actif) ; données de pointage dans `svc-presence`. |
| **IA** | Appels sortants depuis `svc-rh-plateforme` vers `svc-ia` (ou file dédiée) pour ne pas mélanger charge ML et API transactionnelle. |

---

## 6. Dépendances entre services (vue simplifiée)

| Consommateur | Appelle / écoute |
|--------------|------------------|
| Clients | **gateway** |
| `svc-rh-plateforme` | **identité** (JWT) ; **référentiel** (interne si fusionné) ; **notification** (events) ; **svc-ia** (optionnel) |
| `svc-presence` | **identité** ; optionnellement **svc-rh-plateforme** pour enrichissement |
| `svc-notification` | Bus / files ← `svc-rh-plateforme`, `svc-presence` |

---

## 7. Déploiement et priorisation (V1 minimale)

| Phase | Livrables |
|-------|-----------|
| P0 | gateway, eureka, **svc-identite-acces**, **svc-rh-plateforme** (référentiel + documents/contrats en premier), **svc-notification** (minimal). |
| P1 | **svc-presence** (M9). |
| P2 | Enrichissement modules dans `svc-rh-plateforme` (demandes, plaintes, formation…). |
| P3 | **svc-ia**. |

---

## 8. Référence — ancien découpage fin (non prioritaire)

Si un jour une équipe doit **extraire** un domaine, les noms ci-dessous restent des candidats : `svc-demandes-personnel`, `svc-documents-admin`, `svc-plaintes`, etc. Ce n’est **pas** la cible par défaut pour limiter l’empreinte opérationnelle.

---

*Voir `03-bases-de-donnees.md` pour la persistance (schéma unique RH vs multiples bases).*
