/**
 * Table unique code → libellé français de la Plateforme RH (D-L01.b, D-L01.e).
 * Une seule source : le lot 3 la remplacera par le paramétrage servi par l'API
 * (`enregistrerLibelles`). Un code absent s'affiche « Valeur non reconnue », jamais le code brut.
 */

export const VALEUR_NON_RECONNUE = 'Valeur non reconnue';

export type Domaine =
  | 'statut'
  | 'typeDemande'
  | 'typeConge'
  | 'typeDocument'
  | 'profilAcces'
  | 'statutCollaborateur'
  | 'etapeDemande'
  | 'actionDemande'
  | 'acteur'
  | 'typeNoeud'
  | 'etatTemps'
  | 'typePlainte'
  | 'origineFormation'
  | 'cibleFormation'
  | 'typeSite'
  | 'typePointage';

type Table = Record<string, string>;

const TABLES: Record<Domaine, Table> = {
  statut: {
    NOUVEAU: 'Nouveau',
    EN_ANALYSE: 'En analyse',
    EN_TRAITEMENT: 'En traitement',
    EN_TRAITEMENT_RH: 'En traitement',
    EN_COURS: 'En cours',
    EN_ATTENTE: 'En attente',
    EN_ATTENTE_VALIDATION: 'En attente de validation',
    EN_ATTENTE_FILE: 'En attente',
    EN_VALIDATION_SUPERIEUR: 'Chez le responsable',
    EN_VALIDATION_RRH: 'En attente de validation RH',
    EN_VALIDATION_RH: 'En attente de validation RH',
    PLANIFIEE: 'Planifiée',
    INTEGREE_PLAN: 'Acceptée',
    APPROUVEE: 'Approuvée',
    VALIDEE: 'Validée',
    VALIDE: 'Valide',
    CONFIRME: 'Confirmé',
    RESOLU: 'Résolu',
    TERMINEE: 'Terminée',
    FERME: 'Fermé',
    ACTIF: 'Actif',
    ACTIVE: 'Active',
    INACTIVE: 'Inactive',
    SUSPENDU: 'Suspendu',
    PUBLISHED: 'Publié',
    DRAFT: 'Brouillon',
    ARCHIVE: 'Archivé',
    ARCHIVED: 'Archivé',
    REJETEE: 'Refusée',
    REFUSEE: 'Refusée',
    ANNULEE: 'Annulée',
    EXPIRE: 'Expiré',
    EXPIREE: 'Expirée',
    DISPONIBLE: 'Disponible',
    REVOQUE: 'Révoqué',
  },
  typeDemande: {
    CONGE: 'Congé',
    AUTORISATION_SORTIE: 'Autorisation de sortie',
    ORDRE_MISSION: 'Ordre de mission',
  },
  typeConge: {
    ANNUEL: 'Congé annuel',
    MALADIE: 'Congé maladie',
    MATERNITE: 'Congé maternité',
    SANS_SOLDE: 'Congé sans solde',
    AUTRE: 'Autre congé',
  },
  typeDocument: {
    ATTESTATION_TRAVAIL: 'Attestation de travail',
    ATTESTATION_SALAIRE: 'Attestation de salaire',
    BULLETIN_PAIE: 'Bulletin de paie',
    ATTESTATION_CNSS: 'Attestation CNSS',
    FEUILLE_POINTAGE_MENSUELLE: 'Feuille de pointage mensuelle',
    DOCUMENT_INTERNE: 'Document interne',
    AUTRE: 'Autre',
  },
  // Profils d'accès (D-H06) : le profil ne s'affiche que dans « Droits d'accès ».
  profilAcces: {
    COLLABORATEUR: 'Collaborateur',
    RO: 'Responsable opérationnel',
    RESPONSABLE: 'Responsable (département ou direction)',
    RH: 'Ressources humaines',
    DIRECTION: 'Direction (consultation)',
    ADMIN: 'Administrateur',
  },
  statutCollaborateur: {
    ACTIF: 'Actif',
    SUSPENDU: 'Suspendu',
    ARCHIVE: 'Archivé',
  },
  etapeDemande: {
    DEPOT: 'Dépôt',
    RO: 'Validation par le responsable',
    SUPERIEUR: 'Validation par le responsable',
    RRH: 'Validation RH',
    RH: 'Validation RH',
    CLOTURE: 'Clôture',
  },
  actionDemande: {
    CREATION_DEMANDE: 'Création de la demande',
    SOUMISE_A_RO: 'Soumise au responsable',
    VALIDATION_RO: 'Validée par le responsable',
    REFUS_RO: 'Refusée par le responsable',
    VALIDATION_RRH: 'Transmise à la RH',
    REFUS_RRH: 'Refusée par la RH',
    APPROBATION_RRH: 'Approuvée par la RH',
    ANNULATION_DEMANDEUR: 'Annulée par le demandeur',
  },
  acteur: {
    SYSTEME: 'Système (automatique)',
  },
  typeNoeud: {
    DIRECTION_GENERALE: 'Direction générale',
    DIRECTION: 'Direction',
    DEPARTEMENT: 'Département',
    SERVICE: 'Service',
  },
  etatTemps: {
    COMPLET: 'Complet',
    EN_COURS: 'En cours',
    INCOMPLET: 'Incomplet',
  },
  typePlainte: {
    INTERNE: 'Interne',
    EXTERNE: 'Externe',
  },
  origineFormation: {
    CHEF_DEPARTEMENT: 'Chef de département',
    RESPONSABLE_OPERATIONNEL: 'Responsable opérationnel',
  },
  cibleFormation: {
    UNITE: 'Une unité',
    COLLABORATEURS: 'Des collaborateurs',
  },
  typeSite: {
    EXTERIEUR: 'Extérieur',
    INTERIEUR: 'Intérieur',
  },
  typePointage: {
    ENTREE: 'Entrée',
    SORTIE: 'Sortie',
  },
};

function cle(code: string): string {
  return code.trim().toUpperCase();
}

/** Libellé français d'un code ; absent → « — » ; inconnu → « Valeur non reconnue ». */
export function libelle(domaine: Domaine, code: string | null | undefined): string {
  if (code === null || code === undefined || code === '') return '—';
  return TABLES[domaine][cle(code)] ?? VALEUR_NON_RECONNUE;
}

/** Libellé s'il existe, sinon `undefined` (pour laisser la main à un libellé fourni par l'API). */
export function libelleConnu(domaine: Domaine, code: string | null | undefined): string | undefined {
  return code ? TABLES[domaine][cle(code)] : undefined;
}

/** Options `{ valeur, libelle }` d'un domaine (listes déroulantes), dans l'ordre de la table. */
export function optionsDomaine(domaine: Domaine, codes?: readonly string[]): Array<{ valeur: string; libelle: string }> {
  return (codes ?? Object.keys(TABLES[domaine])).map((c) => ({ valeur: c, libelle: libelle(domaine, c) }));
}

/** Point d'entrée du lot 3 : l'API remplace ou complète une table. */
export function enregistrerLibelles(domaine: Domaine, table: Table): void {
  TABLES[domaine] = { ...TABLES[domaine], ...Object.fromEntries(Object.entries(table).map(([k, v]) => [cle(k), v])) };
}

/** Libellé d'un type de nœud d'organigramme, quelle que soit la graphie (« Direction Générale », DIRECTION_GENERALE…). */
export function libelleTypeNoeud(type: string | null | undefined): string {
  if (!type) return '—';
  const cleNorm = type.normalize('NFD').replace(/\p{M}/gu, '').toUpperCase().replace(/[\s-]/g, '_');
  return TABLES.typeNoeud[cleNorm] ?? VALEUR_NON_RECONNUE;
}

/** Intitulé du rôle d'un responsable d'après le type du nœud managé (D-H06) ; `titrePoste` du nœud prioritaire. */
export function intituleResponsable(typeNoeud: string | null | undefined, titrePoste?: string | null): string {
  if (titrePoste && titrePoste.trim()) return titrePoste.trim();
  const t = (typeNoeud ?? '').normalize('NFD').replace(/\p{M}/gu, '').toUpperCase().replace(/[\s-]/g, '_');
  if (t === 'DIRECTION_GENERALE' || t === 'DG') return 'Directeur général';
  if (t === 'DIRECTION') return 'Directeur';
  if (t === 'DEPARTEMENT') return 'Chef de département';
  if (t === 'SERVICE') return 'Chef de service';
  return 'Responsable';
}
