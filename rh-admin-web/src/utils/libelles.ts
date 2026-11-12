/**
 * Table unique code → libellé français de la Plateforme RH (D-L01.b, D-L01.e).
 * Lot 3 : les domaines paramétrables (types, statuts, familles, niveaux, conclusions…) sont alimentés
 * par l'API (`GET .../parametres/libelles` des trois services, voir `parametresStore.ts`) ; aucune liste
 * de secours. Un code absent s'affiche « Valeur non reconnue », jamais le code brut.
 * Restent en dur seulement les libellés de structure (étapes, actions, types de nœud…) qui ne sont pas
 * des référentiels paramétrables.
 */

export const VALEUR_NON_RECONNUE = 'Valeur non reconnue';

export type Domaine =
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
  | 'typePointage'
  | 'familleMetier'
  | 'niveauSeniorite'
  | 'niveauAlerte'
  | 'conclusionAlerte'
  | 'typeQuestion'
  | 'echelleEtoiles'
  | 'appreciation'
  | 'etatEcart';

/** Domaines remplis par l'API de paramétrage (lot 3). */
export const DOMAINES_PARAMETRES: readonly Domaine[] = [
  'typeDemande',
  'typeConge',
  'typeDocument',
  'profilAcces',
  'statutCollaborateur',
  'typePlainte',
  'familleMetier',
  'niveauSeniorite',
  'niveauAlerte',
  'conclusionAlerte',
  'typeQuestion',
  'echelleEtoiles',
  'appreciation',
  'etatEcart',
];

type Table = Record<string, string>;

const TABLES: Record<Domaine, Table> = {
  typeDemande: {},
  typeConge: {},
  typeDocument: {},
  // Profils d'accès (D-H06) : le profil ne s'affiche que dans « Droits d'accès ».
  profilAcces: {},
  statutCollaborateur: {},
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
  typePlainte: {},
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
  familleMetier: {},
  niveauSeniorite: {},
  niveauAlerte: {},
  conclusionAlerte: {},
  typeQuestion: {},
  echelleEtoiles: {},
  appreciation: {},
  etatEcart: {},
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

/** Complète une table (les codes déjà présents sont remplacés). */
export function enregistrerLibelles(domaine: Domaine, table: Table): void {
  TABLES[domaine] = { ...TABLES[domaine], ...Object.fromEntries(Object.entries(table).map(([k, v]) => [cle(k), v])) };
}

/** Remplace entièrement la table d'un domaine paramétré (lot 3) : un code supprimé disparaît. */
export function remplacerLibelles(domaine: Domaine, table: Table): void {
  TABLES[domaine] = Object.fromEntries(Object.entries(table).map(([k, v]) => [cle(k), v]));
}

/** Libellé d'un niveau d'alerte d'écart ; « Aligné » est la constante « pas d'écart », hors paramétrage. */
export function libelleNiveauAlerte(code: string | null | undefined): string {
  if (!code) return '—';
  if (cle(code) === 'ALIGNE') return 'Aligné';
  return libelle('niveauAlerte', code);
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

const TYPES_FORMATION: Record<string, string> = {
  TECHNIQUE: 'Technique',
  COMPORTEMENTALE: 'Comportementale',
  COMPORTEMENTAL: 'Comportementale',
  MANAGERIALE: 'Managériale',
  MANAGEMENT: 'Management',
  LINGUISTIQUE: 'Linguistique',
  REGLEMENTAIRE: 'Réglementaire',
  SECURITE: 'Sécurité',
};

/** Type de formation saisi : un code (« TECHNIQUE ») est affiché en clair, un texte libre reste tel quel. */
export function libelleTypeFormation(valeur: string | null | undefined): string {
  const v = (valeur ?? '').trim();
  if (!v) return '—';
  if (!/^[A-Z][A-Z0-9_]*$/.test(v)) return v;
  if (TYPES_FORMATION[v]) return TYPES_FORMATION[v];
  const t = v.replaceAll('_', ' ').toLowerCase();
  return t.charAt(0).toUpperCase() + t.slice(1);
}

/** Appréciation d'une évaluation : le libellé du serveur s'il existe, sinon le libellé paramétré du code. */
export function libelleAppreciation(valeur: string | null | undefined): string | undefined {
  const v = (valeur ?? '').trim();
  if (!v) return undefined;
  const norm = v.normalize('NFD').replace(/\p{M}/gu, '').toUpperCase().replace(/[\s-]/g, '_');
  return libelleConnu('appreciation', norm) ?? v;
}
