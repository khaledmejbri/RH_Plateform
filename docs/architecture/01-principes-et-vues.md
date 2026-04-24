# Architecture — Principes et vues d’ensemble

**Rôle** : architecte logiciel / système  
**Application** : plateforme RH-Évènement (microservices).

---

## 1. Principes directeurs

| Principe | Application |
|----------|-------------|
| **Microservices par domaine** | Découpage aligné sur les **gros** domaines (ex. une **plateforme RH** regroupe congés, documents, plaintes, etc.) ; services séparés seulement quand les contraintes (sécurité, charge, cycle de vie) diffèrent (notification, présence, IA). |
| **API-first** | Contrats HTTP (REST) et/ou événements asynchrones documentés ; versioning d’API. |
| **Sécurité centralisée** | Authentification fédérée (tokens) ; autorisation par service avec politiques fines. |
| **Données par service** | Chaque service possède sa base (ou schéma dédié) ; pas de base unique partagée en écriture. |
| **Idempotence & traçabilité** | Workflows et notifications traçables ; opérations critiques répétables sans double effet. |
| **Observabilité** | Logs structurés, métriques, corrélation des requêtes (trace ID). |
| **Évolutivité** | Stateless côté API ; montée en charge horizontale derrière un reverse proxy / ingress. |

---

## 2. Vue logique (C4 — niveau conteneurs)

```
                    ┌─────────────────┐
                    │  Clients        │
                    │  Web / Mobile   │
                    └────────┬────────┘
                             │ HTTPS
                    ┌────────▼────────┐
                    │  API Gateway    │
                    │  (routage,      │
                    │   rate-limit)   │
                    └────────┬────────┘
         ┌───────────────────┼───────────────────┐
         │                   │                   │
   ┌─────▼─────┐      ┌──────▼──────┐     ┌──────▼──────┐
   │ Service   │      │ Service     │     │ Service     │
   │ Identité  │      │ RH Core     │     │ … (autres)  │
   │ & accès   │      │ (domaines)  │     │             │
   └─────┬─────┘      └──────┬──────┘     └──────┬──────┘
         │                   │                   │
   ┌─────▼─────┐      ┌──────▼──────┐     ┌──────▼──────┐
   │ DB Auth   │      │ DB RH …     │     │ DB …        │
   └───────────┘      └─────────────┘     └─────────────┘
```

---

## 3. Styles d’interaction

| Style | Usage |
|-------|--------|
| **REST synchrone** | Commandes utilisateur, lectures immédiates, agrégations simples. |
| **Messagerie / événements** | Notifications, indexation recherche, synchronisations faible couplage (ex. « DemandeApprouvée »). |
| **Jobs planifiés** | Rapports mensuels stock EPI, relances SLA, expiration QR présence. |

---

## 4. Alignement avec la conception métier

| Document conception | Lien architecture |
|---------------------|-----------------|
| `03-modules-fonctionnels-et-flux.md` | Découpage en services et frontières transactionnelles |
| `02-acteurs-et-droits-metier.md` | Politiques d’autorisation, scopes, rôles techniques |
| `04-dictionnaire-donnees-metier.md` | Modèles persistés par service, anti-duplication maîtrisée |

---

## 5. Qualités non fonctionnelles (cibles)

| Qualité | Cible indicative |
|---------|------------------|
| Disponibilité API | 99,5 % — 99,9 % (à contractualiser) |
| RTO / RPO | Définir par criticité (plaintes, présence, paie liée) |
| Performance | P95 lecture < 300 ms sur référentiels ; workflows async si lourd |
| Conformité | RGPD / droit local travail — minimisation des données présence |

---

## 6. Documents de la série architecture

| Fichier | Contenu |
|---------|---------|
| `02-microservices-et-responsabilites.md` | Liste des services et périmètres |
| `03-bases-de-donnees.md` | Stratégie données par service |
| `04-apis-evenements-integrations.md` | Contrats et intégrations |
| `05-outillage-devops-observabilite.md` | Chaîne outils |
| `06-securite.md` | AuthN/Z, secrets, JWT |

---

*Vue d’ensemble — détails dans les fichiers suivants.*
