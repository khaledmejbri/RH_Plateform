# Architecture — Outillage, DevOps et observabilité

**Rôle** : architecte plateforme  
**Objectif** : outils pour construire, déployer et exploiter la plateforme microservices.

---

## 1. Développement

| Outil | Usage |
|-------|--------|
| **JDK 21** | Runtime aligné sur les projets Spring Boot actuels |
| **Maven** | Build, modules multi-artefacts |
| **Git** | Versionnement ; stratégie trunk-based ou GitFlow à définir |
| **OpenAPI Generator** | Clients / stubs (optionnel) |
| **Testcontainers** | Tests d’intégration avec PostgreSQL, broker |

---

## 2. Conteneurisation et orchestration

| Outil | Usage |
|-------|--------|
| **Docker** | Image par service (JAR + JRE distroless ou eclipse-temurin) |
| **Docker Compose** | Environnement local (Postgres multiples, broker, MinIO) |
| **Kubernetes** (prod) | Deployments, Services, Ingress, HPA, PDB |

---

## 3. CI/CD

| Étape | Action |
|-------|--------|
| **Build** | `mvn verify`, analyse statique (SpotBugs, Checkstyle), tests |
| **Image** | Build Docker, scan vulnérabilités (Trivy, Grype) |
| **Déploiement** | Helm ou Kustomize ; blue/green ou canary si critique |

| Plateformes possibles | GitLab CI, GitHub Actions, Jenkins, Azure DevOps |

---

## 4. Observabilité

| Pilier | Outil typique |
|--------|----------------|
| **Logs** | JSON vers ELK, Loki, ou CloudWatch |
| **Métriques** | Micrometer + Prometheus + Grafana |
| **Traces** | OpenTelemetry → Jaeger / Tempo |
| **Corrélation** | `traceparent` / `X-Request-Id` propagés par la gateway |

---

## 5. Configuration et secrets

| Sujet | Recommandation |
|-------|----------------|
| **Config non sensible** | Spring Cloud Config, fichiers par profil, ou ConfigMaps K8s |
| **Secrets** | Vault, AWS Secrets Manager, ou Sealed Secrets |
| **Rotation** | Politique pour mots de passe DB, clés JWT, clients OAuth |

---

## 6. Qualité et sécurité pipeline

| Contrôle | Outil |
|----------|--------|
| SCA dépendances | OWASP Dependency-Check, Snyk |
| SAST | SonarQube, Semgrep |
| Politique images | Admission controller (signature Notary / cosign) |

---

## 7. Environnements

| Env | Objectif |
|-----|----------|
| **local** | Développeur, Compose |
| **dev** | Intégration continue déployée |
| **staging** | Recette métier, données anonymisées |
| **prod** | Haute dispo, sauvegardes, monitoring alerting |

---

## 8. Gestion des versions

| Artefact | Stratégie |
|----------|-----------|
| Services | SemVer par API (`v1`, `v2` en parallèle si breaking) |
| Schémas DB | Migrations strictement ascendantes sauf fenêtre maintenance |

---

*Adapter la stack aux contraintes cloud on-premise ou hébergeur du client.*
