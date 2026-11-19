import { ApiMetierError, appelerJson, avecParametres, corpsJson } from './metierApi';
import type { ParametrageImport, TypeParametrable } from './importParametrageApi';

/** Champs personnalisés : définitions (ADR-import-champs-parametrables, annexe A bis, C1 à C6). */

const BASE = '/api/referentiel/v1/parametres/imports';

export type TypeValeurPerso = 'TEXTE' | 'NOMBRE' | 'DATE' | 'LISTE';

export const TYPES_VALEUR: ReadonlyArray<{ code: TypeValeurPerso; libelle: string }> = [
  { code: 'TEXTE', libelle: 'Texte' },
  { code: 'NOMBRE', libelle: 'Nombre' },
  { code: 'DATE', libelle: 'Date' },
  { code: 'LISTE', libelle: 'Liste de valeurs' },
];

/** Règles d'un champ : nombres en chaîne (jamais arrondis), dates ISO. Tout peut être absent selon le type. */
export type ReglesPerso = {
  longueur_max?: number | null;
  decimales?: number | null;
  minimum?: string | null;
  maximum?: string | null;
  date_min?: string | null;
  date_max?: string | null;
};

export type ValeurListe = {
  code: string;
  libelle: string;
  ordre: number;
  actif: boolean;
  utilisations: number;
  supprimable: boolean;
};

export type CorpsChampPerso = {
  libelle: string;
  finalite: string;
  type_valeur: TypeValeurPerso;
  regles: ReglesPerso;
  visible_collaborateur: boolean;
};

export type CreerChampPerso = CorpsChampPerso & {
  version: number;
  valeurs_liste?: Array<{ libelle: string }>;
  confirmation_donnees_non_sensibles: boolean;
};

export type ModifierChampPerso = CorpsChampPerso & {
  version: number;
  /** Seulement si le type devient LISTE. */
  valeurs_liste?: Array<{ libelle: string }>;
};

const chemin = (type: TypeParametrable, code?: string) =>
  `${BASE}/${type}/champs-personnalises${code ? `/${encodeURIComponent(code)}` : ''}`;

export function creerChampPerso(type: TypeParametrable, corps: CreerChampPerso): Promise<ParametrageImport> {
  return appelerJson(chemin(type), corpsJson('POST', corps));
}

export function modifierChampPerso(type: TypeParametrable, code: string, corps: ModifierChampPerso): Promise<ParametrageImport> {
  return appelerJson(chemin(type, code), corpsJson('PUT', corps));
}

export function desactiverChampPerso(type: TypeParametrable, code: string, version: number): Promise<ParametrageImport> {
  return appelerJson(`${chemin(type, code)}/desactiver`, corpsJson('POST', { version }));
}

export function reactiverChampPerso(type: TypeParametrable, code: string, version: number): Promise<ParametrageImport> {
  return appelerJson(`${chemin(type, code)}/reactiver`, corpsJson('POST', { version }));
}

/** 204 sans corps : l'appelant relit P1 pour obtenir la nouvelle version. */
export function supprimerChampPerso(type: TypeParametrable, code: string, version: number): Promise<void> {
  return appelerJson(avecParametres(chemin(type, code), { version }), { method: 'DELETE' });
}

export function ajouterValeurListe(type: TypeParametrable, code: string, libelle: string, version: number): Promise<ParametrageImport> {
  return appelerJson(`${chemin(type, code)}/valeurs`, corpsJson('POST', { libelle, version }));
}

export function modifierValeurListe(
  type: TypeParametrable, code: string, codeValeur: string, libelle: string, ordre: number, version: number,
): Promise<ParametrageImport> {
  return appelerJson(`${chemin(type, code)}/valeurs/${encodeURIComponent(codeValeur)}`, corpsJson('PUT', { libelle, ordre, version }));
}

export function desactiverValeurListe(type: TypeParametrable, code: string, codeValeur: string, version: number): Promise<ParametrageImport> {
  return appelerJson(`${chemin(type, code)}/valeurs/${encodeURIComponent(codeValeur)}/desactiver`, corpsJson('POST', { version }));
}

export function reactiverValeurListe(type: TypeParametrable, code: string, codeValeur: string, version: number): Promise<ParametrageImport> {
  return appelerJson(`${chemin(type, code)}/valeurs/${encodeURIComponent(codeValeur)}/reactiver`, corpsJson('POST', { version }));
}

export function supprimerValeurListe(type: TypeParametrable, code: string, codeValeur: string, version: number): Promise<void> {
  return appelerJson(avecParametres(`${chemin(type, code)}/valeurs/${encodeURIComponent(codeValeur)}`, { version }), { method: 'DELETE' });
}

/** Code du champ de formulaire visé par une erreur de définition (le serveur peut aussi fournir `champ`). */
export function champFormulaireDeLErreur(e: unknown): 'libelle' | 'finalite' | 'type_valeur' | 'regles' | 'valeurs_liste' | 'confirmation' | null {
  if (!(e instanceof ApiMetierError)) return null;
  const champ = typeof e.donnees.champ === 'string' ? e.donnees.champ : null;
  if (champ === 'libelle' || champ === 'finalite' || champ === 'type_valeur' || champ === 'regles' || champ === 'valeurs_liste') return champ;
  switch (e.code) {
    case 'LIBELLE_INVALIDE':
    case 'LIBELLE_EN_DOUBLE':
      return 'libelle';
    case 'CONFIRMATION_OBLIGATOIRE':
      return 'confirmation';
    case 'REGLE_INVALIDE':
    case 'LISTE_SANS_VALEUR_ACTIVE':
      return 'regles';
    case 'PARAMETRE_UTILISE':
      return 'type_valeur';
    default:
      return null;
  }
}
