import { ApiMetierError, appelerJson, avecParametres, corpsJson } from './metierApi';
import type { ReglesPerso, TypeValeurPerso, ValeurListe } from './champsPersoApi';

/** Paramétrage des champs d'import Excel (ADR-import-champs-parametrables, annexe A : P1 à P7). */

const BASE = '/api/referentiel/v1/parametres/imports';

export type TypeParametrable = 'collaborateurs' | 'soldes';

export const TYPES_PARAMETRABLES: ReadonlyArray<{ type: TypeParametrable; libelle: string; typeImport: 'COLLABORATEURS' | 'SOLDES' }> = [
  { type: 'collaborateurs', libelle: 'Collaborateurs', typeImport: 'COLLABORATEURS' },
  { type: 'soldes', libelle: 'Soldes de congé', typeImport: 'SOLDES' },
];

export type Exigence = 'FACULTATIF' | 'CREATION' | 'TOUJOURS';

export type ExigencePossible = { code: Exigence; libelle: string };

export type SynonymeImport = {
  id: string;
  texte: string;
  forme_normalisee: string;
  origine: 'DEFAUT' | 'RH';
  origine_libelle: string;
  actif: boolean;
  supprimable: boolean;
};

export type ChampParametre = {
  code: string;
  libelle: string;
  en_tete: string;
  en_tete_defaut: string;
  dans_modele: boolean;
  retirable_du_modele: boolean;
  exigence: Exigence;
  exigence_libelle: string;
  exigence_minimale: Exigence;
  exigences_possibles: ExigencePossible[];
  verrouille: boolean;
  verrou_libelle: string | null;
  /** Null pour un champ personnalisé inactif (hors modèle). */
  ordre: number | null;
  synonymes: SynonymeImport[];
  /** Champs personnalisés (partie 2) : absents ou à null pour un champ fixe. */
  personnalise?: boolean;
  actif?: boolean;
  type_valeur?: TypeValeurPerso | null;
  type_valeur_libelle?: string | null;
  regles?: ReglesPerso | null;
  regles_libelle?: string | null;
  valeurs_liste?: ValeurListe[] | null;
  finalite?: string | null;
  visible_collaborateur?: boolean | null;
  utilisations?: number;
  utilisations_libelle?: string | null;
  type_modifiable?: boolean;
  supprimable?: boolean;
  cree_le?: string | null;
  cree_par_nom?: string | null;
};

export type ParametrageImport = {
  type: TypeParametrable;
  type_libelle: string;
  version: number;
  modifie_le: string | null;
  modifie_par_nom: string | null;
  champs: ChampParametre[];
};

export type EntreeJournalImport = {
  id: string;
  horodatage: string;
  auteur_nom: string;
  champ: string | null;
  champ_libelle: string;
  attribut: string;
  attribut_libelle: string;
  avant: string | null;
  apres: string | null;
};

/** Page du journal P7 : `total` (et non `total_elements`). */
export type PageJournalImport = {
  contenu: EntreeJournalImport[];
  page: number;
  taille: number;
  total: number;
};

export type ChampEnvoye = { code: string; en_tete: string; dans_modele: boolean; exigence: Exigence };

const chemin = (type: TypeParametrable) => `${BASE}/${type}`;

export function getParametrageImport(type: TypeParametrable): Promise<ParametrageImport> {
  return appelerJson(chemin(type), { headers: { 'Cache-Control': 'no-cache' } });
}

export function enregistrerParametrage(type: TypeParametrable, version: number, champs: ChampEnvoye[]): Promise<ParametrageImport> {
  return appelerJson(chemin(type), corpsJson('PUT', { version, champs }));
}

export function ajouterSynonyme(type: TypeParametrable, code: string, texte: string, version: number): Promise<ParametrageImport> {
  return appelerJson(`${chemin(type)}/champs/${encodeURIComponent(code)}/synonymes`, corpsJson('POST', { texte, version }));
}

export function desactiverSynonyme(type: TypeParametrable, id: string, version: number): Promise<ParametrageImport> {
  return appelerJson(`${chemin(type)}/synonymes/${encodeURIComponent(id)}/desactiver`, corpsJson('POST', { version }));
}

export function reactiverSynonyme(type: TypeParametrable, id: string, version: number): Promise<ParametrageImport> {
  return appelerJson(`${chemin(type)}/synonymes/${encodeURIComponent(id)}/reactiver`, corpsJson('POST', { version }));
}

/** 204 sans corps : l'appelant relit P1 pour obtenir la nouvelle version. */
export function supprimerSynonyme(type: TypeParametrable, id: string, version: number): Promise<void> {
  return appelerJson(avecParametres(`${chemin(type)}/synonymes/${encodeURIComponent(id)}`, { version }), { method: 'DELETE' });
}

export function reinitialiserParametrage(type: TypeParametrable, version: number): Promise<ParametrageImport> {
  return appelerJson(`${chemin(type)}/reinitialiser`, corpsJson('POST', { version }));
}

export function getJournalImport(type: TypeParametrable, page: number, taille = 20): Promise<PageJournalImport> {
  return appelerJson(avecParametres(`${chemin(type)}/journal`, { page, taille }));
}

export const MESSAGE_CONFLIT = 'Ce paramétrage a été modifié par quelqu’un d’autre.';

export function estConflit(e: unknown): boolean {
  return e instanceof ApiMetierError && e.code === 'PARAMETRE_MODIFIE_ENTRE_TEMPS';
}

/** Code du champ visé par une erreur serveur (P2, P3), s'il est fourni. */
export function champDeLErreur(e: unknown): string | null {
  return e instanceof ApiMetierError && typeof e.donnees.champ === 'string' ? e.donnees.champ : null;
}

/** Message lisible : le texte serveur est rendu tel quel, jamais le code brut. */
export function messageParametrageImport(e: unknown): string {
  if (estConflit(e)) return MESSAGE_CONFLIT;
  if (!(e instanceof ApiMetierError)) return 'Une erreur inattendue est survenue. Rien n’a été modifié.';
  if (e.code === 'PARAMETRE_SYSTEME') return e.message || 'Un synonyme par défaut ne peut pas être supprimé : désactivez-le.';
  return e.message;
}
