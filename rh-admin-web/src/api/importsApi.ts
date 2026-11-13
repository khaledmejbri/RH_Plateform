import {
  ApiMetierError, appelerJson, avecParametres, corpsJson, envoyerFormulaire, telecharger, type PageContrat,
} from './metierApi';

/** Imports Excel (collaborateurs, soldes), annexe A de l'ADR : I1 à I12. */

const BASE = '/api/rh/v1/admin/imports';

export type TypeImport = 'COLLABORATEURS' | 'SOLDES';

export const TAILLE_MAX_OCTETS = 10_485_760;

export type ColonneAnalyse = {
  index: number;
  en_tete: string;
  champ: string | null;
  champ_libelle: string | null;
  etat: 'RECONNUE' | 'IGNOREE' | 'EN_DOUBLE';
};

export type ChampDisponible = { champ: string; libelle: string; obligatoire_creation?: boolean };

export type MessageCode = { code: string; message: string };

export type Totaux = {
  lignes: number;
  creations: number | null;
  mises_a_jour: number | null;
  inchangees: number | null;
  ajustements: number | null;
  erreurs: number;
  avertissements: number;
};

export type ErreurLigne = { code: string; colonne?: string | null; valeur?: string | null; message: string };

export type Modification = { champ: string; champ_libelle: string; avant: string | null; apres: string | null };

export type LigneAnalyse = {
  numero: number;
  matricule: string | null;
  nom_complet: string | null;
  action: string;
  action_libelle: string;
  erreurs: ErreurLigne[];
  avertissements: MessageCode[];
  modifications: Modification[];
  solde_actuel?: number | null;
  solde_cible?: number | null;
  ecart?: number | null;
  motif?: string | null;
};

export type Analyse = {
  analyse_id: string;
  type_import: TypeImport;
  statut: 'ANALYSEE' | 'CONFIRMEE';
  expire_le: string;
  nom_fichier: string;
  creer_comptes: boolean;
  motif_global: string | null;
  ligne_en_tete: number;
  colonnes: ColonneAnalyse[];
  champs_disponibles: ChampDisponible[];
  erreurs_fichier: MessageCode[];
  colonnes_ignorees: string[];
  avertissements_fichier: MessageCode[];
  totaux: Totaux;
  confirmable: boolean;
  lignes?: PageContrat<LigneAnalyse>;
};

export type CompteRendu = {
  import_id: string;
  type_import: TypeImport;
  creees: number | null;
  mises_a_jour: number | null;
  inchangees: number | null;
  rejetees: number;
  comptes_demandes: number | null;
  ajustements: number | null;
  avertissements: string[];
};

export type ImportResume = {
  import_id: string;
  type_import: TypeImport;
  type_import_libelle: string;
  confirme_le: string;
  auteur_nom: string | null;
  nom_fichier: string;
  creer_comptes: boolean | null;
  motif_global: string | null;
  totaux: Partial<Totaux> & { creees?: number; rejetees?: number };
};

export type LigneHistorique = {
  numero_ligne: number;
  matricule: string | null;
  action: string;
  action_libelle: string;
  changements: Array<{ champ_libelle: string; avant: string | null; apres: string | null }>;
  erreurs_libelle: string | null;
};

export function analyserCollaborateurs(fichier: File, creerComptes: boolean): Promise<Analyse> {
  const f = new FormData();
  f.append('fichier', fichier);
  f.append('creer_comptes', String(creerComptes));
  return envoyerFormulaire(`${BASE}/collaborateurs/analyses`, f);
}

export function analyserSoldes(fichier: File, motifGlobal: string): Promise<Analyse> {
  const f = new FormData();
  f.append('fichier', fichier);
  if (motifGlobal.trim()) f.append('motif_global', motifGlobal.trim());
  return envoyerFormulaire(`${BASE}/soldes/analyses`, f);
}

export function getLignes(analyseId: string, page: number, erreursSeulement: boolean, taille = 50): Promise<PageContrat<LigneAnalyse>> {
  return appelerJson(avecParametres(`${BASE}/analyses/${encodeURIComponent(analyseId)}/lignes`, {
    page, taille, erreurs_seulement: erreursSeulement,
  }));
}

export type CorpsCorrespondance = {
  ligne_en_tete?: number;
  colonnes: Array<{ index: number; champ: string | null }>;
  creer_comptes?: boolean;
  motif_global?: string;
};

export function corrigerCorrespondance(analyseId: string, corps: CorpsCorrespondance): Promise<Analyse> {
  return appelerJson(`${BASE}/analyses/${encodeURIComponent(analyseId)}/correspondance`, corpsJson('PUT', corps));
}

export function confirmerImport(analyseId: string, motifGlobal?: string): Promise<CompteRendu> {
  return appelerJson(
    `${BASE}/analyses/${encodeURIComponent(analyseId)}/confirmation`,
    corpsJson('POST', motifGlobal !== undefined ? { motif_global: motifGlobal } : {}),
  );
}

export function telechargerLignesEnErreur(analyseId: string): Promise<void> {
  return telecharger(`${BASE}/analyses/${encodeURIComponent(analyseId)}/lignes-en-erreur`, 'lignes-en-erreur.xlsx');
}

export function telechargerModele(type: TypeImport): Promise<void> {
  return type === 'COLLABORATEURS'
    ? telecharger(`${BASE}/collaborateurs/modele`, 'modele-collaborateurs.xlsx')
    : telecharger(`${BASE}/soldes/modele`, 'modele-soldes.xlsx');
}

export function getHistorique(type: string, page: number, taille = 20): Promise<PageContrat<ImportResume>> {
  return appelerJson(avecParametres(`${BASE}/historique`, { type, page, taille }));
}

export function getLignesHistorique(importId: string, page: number, taille = 50): Promise<PageContrat<LigneHistorique>> {
  return appelerJson(avecParametres(`${BASE}/historique/${encodeURIComponent(importId)}/lignes`, { page, taille }));
}

/** Contrôle fait avant tout envoi réseau (10 Mo, .xlsx / .csv). Retourne le message d'erreur ou null. */
export function controlerFichier(f: File): string | null {
  const nom = f.name.toLowerCase();
  if (!nom.endsWith('.xlsx') && !nom.endsWith('.csv')) return 'Format non supporté : utilisez .xlsx ou .csv';
  if (f.size > TAILLE_MAX_OCTETS) return 'Le fichier dépasse 10 Mo';
  return null;
}

/** Un texte par code d'erreur d'import (D-I07) ; jamais le code brut. */
export function messageImport(e: unknown): string {
  if (!(e instanceof ApiMetierError)) return 'Une erreur inattendue est survenue. Rien n’a été importé.';
  switch (e.code) {
    case 'IMPORT_EXPIRE': return 'L’analyse a expiré, déposez à nouveau le fichier';
    case 'IMPORT_DEJA_CONFIRME': return 'Cet import a déjà été confirmé. Consultez l’historique des imports.';
    case 'IMPORT_EN_COURS': return 'Un autre import est en cours, réessayez dans quelques instants';
    case 'AUCUNE_LIGNE_IMPORTABLE': return 'Aucune ligne importable : corrigez le fichier et déposez-le à nouveau.';
    case 'ERREUR_DE_FICHIER': return 'Le fichier contient une erreur de structure : corrigez-le et déposez-le à nouveau.';
    case 'FICHIER_TROP_VOLUMINEUX': return 'Le fichier dépasse 10 Mo';
    case 'FORMAT_NON_SUPPORTE': return 'Format non supporté : utilisez .xlsx ou .csv';
    case 'ANALYSE_INTROUVABLE': return 'Analyse introuvable. Déposez à nouveau le fichier.';
    default: break;
  }
  if (e.status === 413) return 'Le fichier dépasse 10 Mo';
  return e.message;
}
