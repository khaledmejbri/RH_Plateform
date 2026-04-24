# Conception — Périmètre et vision du produit

**Document** : analyse fonctionnelle (niveau conception métier)  
**Application** : RH-Évènement (V1 — référence cahier des charges)  
**Rôle** : analyste système

---

## 1. Contexte

L’application **RH-Évènement** vise à informatiser les activités RH de l’entreprise pour :

- les **employés** ;
- les **chefs hiérarchiques** ;
- le **service RH** (dont le RRH) ;
- d’autres acteurs transverses (Direction, HSE, services techniques, etc., selon modules).

Les canaux cibles sont :

- une **application mobile** (Android) pour les employés et usages terrain ;
- un **portail web** orienté administration et pilotage RH.

---

## 2. Vision produit

| Élément | Description |
|--------|-------------|
| **Objectif principal** | Centraliser les demandes RH, automatiser les circuits de validation et assurer la traçabilité. |
| **Valeur métier** | Réduction des délais (soumission → validation → traitement), transparence, historique des actions, communication structurée. |
| **Périmètre V1** | Modules décrits au chapitre « Modules fonctionnels » du cahier des charges (demandes, documents, notes, plaintes, formation, recrutement/besoin personnel, évaluations, EPI, présence digitale). |

---

## 3. Objectifs métier (rappel synthétique)

| ID | Objectif |
|----|----------|
| O1 | Digitaliser les processus aujourd’hui manuels. |
| O2 | Réduire les délais de traitement et les allers-retours informels. |
| O3 | Améliorer traçabilité et historique des actions. |
| O4 | Offrir une interface moderne, accessible au plus grand nombre (mobile). |
| O5 | Structurer plaintes, formations, évaluations et suivi EPI. |
| O6 | Proposer une alternative à la présence papier / pointage classique (module présence). |

---

## 4. Hors périmètre (à préciser avec le métier)

Les éléments suivants sont **souvent** hors périmètre ou en intégration future — à valider en atelier :

- **Paie** : calcul des soldes de congés si non intégré à un module paie.
- **Comptabilité / ERP** : échanges financiers détaillés.
- **Badgeuse / biométrie** : explicitement non retenus comme prérequis pour le module présence (QR + zone ponctuelle).

---

## 5. Critères de succès (indicateurs métier suggérés)

| Indicateur | Exemple de mesure |
|------------|-------------------|
| Délai moyen de traitement | Jours ouvrés par type de demande. |
| Taux de demandes traitées dans les SLA | % par workflow. |
| Adoption | % d’employés ayant au moins une action sur 90 jours. |
| Satisfaction | Enquêtes ciblées (formation, RH). |
| Conformité traçabilité | Audits sur historiques et pièces jointes. |

---

## 6. Glossaire métier (extrait)

| Terme | Sens |
|-------|------|
| **RRH** | Responsable des ressources humaines. |
| **Workflow** | Enchaînement de validations (ex. employé → supérieur → RH). |
| **EPI** | Équipement de protection individuelle. |
| **HSE** | Hygiène, sécurité, environnement (rôle clé pour EPI). |
| **Note de service** | Publication officielle avec diffusion et archivage. |

---

*Document de conception — aligné sur le cahier des charges Application RH-Évènement V1.*
