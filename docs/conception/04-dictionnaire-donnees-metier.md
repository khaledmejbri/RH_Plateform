# Conception — Dictionnaire de données métier (conceptuel)

**Rôle** : analyste système  
**Niveau** : concepts et attributs métier (indépendant de la base technique).

---

## 1. Entités cœur

| Entité conceptuelle | Définition | Attributs métier typiques |
|---------------------|------------|---------------------------|
| **Employé / collaborateur** | Personne de l’entreprise utilisatrice du système (données alignées sur les annexes du cahier des charges : fiche agent, pointage, besoin en recrutement, EPI, évaluations). | **Matricule**, **name**, **prénom**, **courriel professionnel**, **poste** (libellé), **fonction** (fiche agent / évaluation), **qualification et affectation** (annexe besoin recrutement), **qualité** (ex. feuille de pointage), **affectation** / lieu de travail, **département** (libellé, en complément du service structuré), **date de recrutement**, **service / unité organisationnelle** (référentiel), **supérieur hiérarchique**, **statut** (actif / inactif, etc.). |
| **Service / Département** | Unité organisationnelle. | Code, libellé, responsable. |
| **Utilisateur applicatif** | Compte de connexion lié à un employé (ou compte technique limité). | Login, état du compte, profil(s) métier. |
| **Demande générique** | Unité de travail soumise dans un workflow. | Type, date soumission, demandeur, état, commentaires, historique des étapes. |
| **Pièce jointe** | Fichier associé à une demande ou publication. | Nom, type, taille, auteur, date dépôt. |

---

## 2. Modules — concepts principaux

### M1 — Demandes du personnel

| Concept | Attributs métier (exemples) |
|---------|----------------------------|
| **Demande de congé** | Type de congé, date début/fin, durée, solde affiché (si applicable), motif. |
| **Autorisation de sortie** | Tranche horaire, type (administratif, personnel, exceptionnel). |
| **Ordre de mission** | Lieu, motif, objectifs, dates, frais estimés, PDF généré, PJ. |

### M2 — Documents administratifs

| Concept | Attributs métier |
|---------|------------------|
| **Demande de document** | Type (attestation travail, salaire, bulletin, CNSS, interne), urgence, statut traitement RH. |

### M3 — Notes de service

| Concept | Attributs métier |
|---------|------------------|
| **Note de service** | Titre, corps ou référence, date publication, auteur, PJ, périmètre de diffusion. |

### M4 — Plaintes

| Concept | Attributs métier |
|---------|------------------|
| **Plainte** | Canal (interne / externe), catégorie, description, statut, médias, services concernés, historique. |

### M5 — Formation

| Concept | Attributs métier |
|---------|------------------|
| **Besoin / demande formation** | Thème, public, justification, validation hiérarchique + RH. |
| **Session / invitation** | Dates, lieu ou modalité, participants, confirmations. |
| **Évaluation** | Type (chaud / froid), questionnaire, période de collecte, résultats agrégés. |

### M6 — Besoin en personnel

| Concept | Attributs métier |
|---------|------------------|
| **Fiche besoin** | Poste, profil, nombre, motif, affectation, date souhaitée, visa chef, direction, RH. |

### M7 — Évaluations RH

| Concept | Attributs métier |
|---------|------------------|
| **Évaluation semestrielle / annuelle** | Période, critères, notes, commentaires, plans d’action, signatures. |

### M8 — EPI

| Concept | Attributs métier |
|---------|------------------|
| **Article EPI** | Désignation, catégorie, taille/specs, stock, péremption, fournisseur, référence, actif/inactif. |
| **Demande EPI** | Lignes (type, quantité, urgence), demandeur, équipe concernée. |
| **Attribution** | Bénéficiaire, date, quantité, validateur HSE/RH, référence matériel, durée de vie / expiration. |
| **Mouvement de stock** | Type (sortie, réappro), quantité, date, lien attribution. |

### M9 — Présence

| Concept | Attributs métier |
|---------|------------------|
| **Jeton / QR de pointage** | Période de validité, créneau ou journée, zone associée. |
| **Pointage** | Employé, date/heure entrée (sortie optionnelle), résultat contrôle zone, indicateur anomalie. |
| **Zone autorisée** | Libellé, coordonnées ou périmètre métier (décrit côté solution). |

---

## 3. Référentiels (données de paramétrage)

| Référentiel | Usage |
|-------------|--------|
| Types de congés | M1 |
| Types de documents administratifs | M2 |
| Types / catégories de plaintes | M4 |
| Catalogues de formations et questionnaires | M5 |
| Grilles d’évaluation | M7 |
| Catalogue EPI | M8 |
| Sites / zones de présence | M9 |

---

## 4. Règles de cohérence métier (à valider)

| ID | Règle |
|----|--------|
| DC1 | Un pointage valide doit associer un employé actif à un QR valide à l’instant T. |
| DC2 | Une attribution EPI décrémente le stock disponible. |
| DC3 | Une demande en workflow ne peut passer à l’étape suivante que si l’étape courante est validée par le bon rôle. |
| DC4 | Les exports PDF d’évaluation et reçus EPI doivent pouvoir être rattachés au dossier employé (concept archive métier). |

---

*Les implémentations physiques (tables, schémas par service) sont décrites dans `docs/architecture/03-bases-de-donnees.md`.*
