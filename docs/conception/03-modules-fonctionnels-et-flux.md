# Conception — Modules fonctionnels et flux métier

**Rôle** : analyste système  
**Source** : cahier des charges Application RH-Évènement V1 (partie fonctionnelle).

---

## 1. Inventaire des modules

| ID | Module | Description synthétique |
|----|--------|-------------------------|
| M1 | **Demandes du personnel** | Congés, autorisations de sortie, ordres de mission (PDF, PJ). |
| M2 | **Documents administratifs** | Demandes d’attestations, bulletins, CNSS, documents internes ; suivi RH. |
| M3 | **Notes de service** | Publication, pièces jointes, notification, archive, recherche. |
| M4 | **Gestion des plaintes** | Interne / externe, statuts, médias, tableau de bord interservices. |
| M5 | **Formation** | Besoin, plan, invitations, évaluations chaud / froid, pilotage RRH. |
| M6 | **Besoin en personnel** | Fiche besoin, workflow chef → direction → RH, lien recrutement. |
| M7 | **Évaluations RH** | Semestrielle, annuelle, objectifs, exports, tableaux de bord. |
| M8 | **EPI** | Catalogue, demandes, attribution, traçabilité, stock, alertes, reçus PDF. |
| M9 | **Présence digitale** | QR dynamique, scan, contrôle de zone ponctuel, anomalies, suivi RH. |

---

## 2. Flux transverses

| Flux | Déclencheur | Étapes métier (schéma) | Notifications |
|------|-------------|------------------------|---------------|
| **WF standard RH** | Soumission employé | Employé → Supérieur → RRH (refus avec commentaire possible) | Push + e-mail à chaque étape (CDC) |
| **Document administratif** | Demande employé | File d’attente RH directe + suivi statut | Selon paramétrage |
| **Note de service** | Publication Direction/RRH | Diffusion + archivage | Notification instantanée à tous |
| **Plainte interne** | Formulaire employé | RH + suivi statuts | Selon politique |
| **Plainte externe** | Responsable interne | Envoi simultané Technique + Env.&Social + RH + tableau de bord | Selon politique |
| **EPI** | Demande (ex. chef) | Chef → HSE → RH (optionnel) | Chef notifié à validation / remise |
| **Présence** | Scan QR | Vérification validité QR + appartenance + zone ponctuelle | Alertes RH si anomalies |

---

## 3. Statuts et cycles de vie (extraits à détailler en spécifications)

### 3.1 Plaintes (CDC)

| Statut | Signification métier |
|--------|----------------------|
| Nouveau | Déposée / enregistrée |
| En analyse | Qualification |
| En traitement | Actions en cours |
| Résolu | Traitée |
| Fermé | Clôturée |

### 3.2 Demandes génériques (congé, sortie, mission)

| État | Description |
|------|-------------|
| Brouillon | Optionnel si le métier le souhaite |
| Soumise | En attente supérieur |
| En validation RH | Après supérieur |
| Approuvée / Refusée | Terminal |
| Annulée | Par demandeur ou RH selon règles |

*Les libellés exacts et transitions sont à figer avec le métier.*

---

## 4. Pièces jointes et documents générés

| Module | Types de fichiers / sorties |
|--------|----------------------------|
| Ordre de mission | PDF généré + PJ (invitation, courrier) |
| Documents admin | PDF bulletins, attestations (selon source) |
| Notes de service | PDF, images |
| Plaintes | Photos / vidéos |
| Évaluations | Export PDF archivage |
| EPI | Reçu PDF par attribution |
| Formation | Questionnaires paramétrables (contenu métier) |

---

## 5. Dépendances fonctionnelles entre modules

| Module | Dépend de (métier) |
|--------|---------------------|
| Congés | Référentiel types de congés ; éventuellement soldes (paie future) |
| Présence | Référentiel employés, services, zones autorisées |
| EPI | Référentiel employés, services, catalogue EPI |
| Évaluations | Hiérarchie, fiches de poste, périodes |
| Besoin personnel | Fiches de poste, organigramme |
| Tous | **Identité / employé** cohérente (qui est le demandeur, le valideur) |

---

## 6. Exigences non fonctionnelles côté métier (rappel)

| Thème | Attendu |
|-------|---------|
| Traçabilité | Historique des actions sur demandes et validations |
| Disponibilité | Heures ouvrées + mobile pour employés |
| Confidentialité | Données RH et plaintes protégées |
| Éthique présence | Pas de localisation continue ; pas de biométrie (CDC) |

---

*Ce document sert de base aux scénarios détaillés (user stories) et à l’architecture applicative.*
