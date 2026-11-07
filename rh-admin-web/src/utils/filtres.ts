import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import type { Unite } from '../api/rhClient';

/* ─── Recherche texte « intelligente » ──────────────────────────────────── */

/** Minuscules, sans accents, espaces compactés. */
export function normaliser(valeur: unknown): string {
  if (valeur == null) return '';
  return String(valeur)
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * Tous les mots de la recherche doivent se retrouver (dans n'importe quel champ).
 * Insensible à la casse et aux accents ; recherche vide = tout passe.
 */
export function correspond(champs: ReadonlyArray<unknown>, recherche: string): boolean {
  const mots = normaliser(recherche).split(' ').filter(Boolean);
  if (mots.length === 0) return true;
  const foin = champs.map(normaliser).join(' ');
  return mots.every((m) => foin.includes(m));
}

/* ─── Unités (Direction / Département / Service) ───────────────────────── */

export type SelectionUnite = { direction: string; departement: string; service: string };
export const SELECTION_UNITE_VIDE: SelectionUnite = { direction: '', departement: '', service: '' };

export type NiveauUnite = 'direction' | 'departement' | 'service';

/** Niveau d'une unité d'après son `type_noeud` (null pour la DG ou un type inconnu). */
export function niveauUnite(u: Pick<Unite, 'type_noeud'>): NiveauUnite | null {
  const t = normaliser(u.type_noeud).replace(/[\s_-]/g, '');
  if (t === 'direction') return 'direction';
  if (t === 'departement') return 'departement';
  if (t === 'service') return 'service';
  return null;
}

export function indexerUnites(unites: ReadonlyArray<Unite>): Map<string, Unite> {
  return new Map(unites.map((u) => [u.identifiant, u]));
}

/** Identifiants de l'unité et de tous ses ancêtres. */
export function chaineUnite(index: Map<string, Unite>, uniteId: string | null | undefined): Set<string> {
  const ids = new Set<string>();
  let courant = uniteId ? index.get(uniteId) : undefined;
  while (courant && !ids.has(courant.identifiant)) {
    ids.add(courant.identifiant);
    courant = courant.parent_identifiant ? index.get(courant.parent_identifiant) : undefined;
  }
  return ids;
}

export function selectionActive(sel: SelectionUnite): boolean {
  return Boolean(sel.direction || sel.departement || sel.service);
}

/** L'unité appartient-elle (elle-même ou via un ancêtre) à chaque niveau sélectionné ? */
export function appartientSelection(
  index: Map<string, Unite>,
  uniteId: string | null | undefined,
  sel: SelectionUnite,
): boolean {
  if (!selectionActive(sel)) return true;
  const chaine = chaineUnite(index, uniteId);
  return [sel.direction, sel.departement, sel.service].every((id) => !id || chaine.has(id));
}

/** Unités (et sous-unités) retenues par la sélection, ou null si aucune sélection. */
export function unitesRetenues(unites: ReadonlyArray<Unite>, sel: SelectionUnite): Unite[] | null {
  if (!selectionActive(sel)) return null;
  const index = indexerUnites(unites);
  return unites.filter((u) => appartientSelection(index, u.identifiant, sel));
}

/** Pour les listes qui ne portent que le libellé de l'unité (demandes, formations). */
export function libelleDansSelection(
  retenues: Unite[] | null,
  libelle: string | null | undefined,
): boolean {
  if (!retenues) return true;
  const cle = normaliser(libelle);
  if (!cle) return false;
  return retenues.some((u) => normaliser(u.libelle) === cle || normaliser(u.code) === cle);
}

/* ─── Filtres mémorisés dans l'URL ─────────────────────────────────────── */

export type FiltresUrl<K extends string> = {
  valeurs: Record<K, string>;
  /** Modifie un ou plusieurs filtres (valeur par défaut = paramètre retiré de l'URL). */
  definir: (changements: Partial<Record<K, string>>) => void;
  reinitialiser: () => void;
  /** Nombre de filtres différents de leur valeur par défaut. */
  nbActifs: number;
};

/**
 * Filtres d'une page stockés dans la query string (survivent à un rafraîchissement).
 * `prefixe` distingue plusieurs jeux de filtres sur la même page (onglets).
 */
export function useFiltresUrl<K extends string>(defauts: Record<K, string>, prefixe = ''): FiltresUrl<K> {
  const [params, setParams] = useSearchParams();
  const defautsJson = JSON.stringify(defauts);
  const cles = Object.keys(defauts) as K[];

  const valeurs = {} as Record<K, string>;
  for (const k of cles) {
    const brut = params.get(prefixe + k);
    valeurs[k] = brut === null ? defauts[k] : brut;
  }

  const definir = useCallback(
    (changements: Partial<Record<K, string>>) => {
      setParams(
        (prev) => {
          const suivant = new URLSearchParams(prev);
          const d = JSON.parse(defautsJson) as Record<K, string>;
          for (const [k, v] of Object.entries(changements) as Array<[K, string]>) {
            if (v === d[k]) suivant.delete(prefixe + k);
            else suivant.set(prefixe + k, v);
          }
          return suivant;
        },
        { replace: true },
      );
    },
    [setParams, defautsJson, prefixe],
  );

  const reinitialiser = useCallback(() => {
    setParams(
      (prev) => {
        const suivant = new URLSearchParams(prev);
        for (const k of Object.keys(JSON.parse(defautsJson) as object)) suivant.delete(prefixe + k);
        return suivant;
      },
      { replace: true },
    );
  }, [setParams, defautsJson, prefixe]);

  const nbActifs = useMemo(
    () => (Object.keys(JSON.parse(defautsJson) as object) as K[]).filter((k) => {
      const brut = params.get(prefixe + k);
      return brut !== null && brut !== (JSON.parse(defautsJson) as Record<K, string>)[k];
    }).length,
    [params, defautsJson, prefixe],
  );

  return { valeurs, definir, reinitialiser, nbActifs };
}

/** Comparaison de dates ISO (AAAA-MM-JJ…) : `date` dans [debut, fin] (bornes vides ignorées). */
export function dansPeriode(date: string | null | undefined, debut: string, fin: string): boolean {
  if (!debut && !fin) return true;
  if (!date) return false;
  const j = date.slice(0, 10);
  if (debut && j < debut) return false;
  if (fin && j > fin) return false;
  return true;
}

export function pluriel(n: number, singulier: string, plur = singulier + 's'): string {
  return `${n} ${n > 1 ? plur : singulier}`;
}

/** « TYPE_DOCUMENT » → « Type document » (libellé de repli pour un code). */
export function humanizeCode(code: string | null | undefined): string {
  if (!code) return '';
  const s = code.replaceAll('_', ' ').toLowerCase();
  return s.charAt(0).toUpperCase() + s.slice(1);
}

/** « Direction Technique › Département Software » : chemin d'une unité depuis la racine (sans la DG). */
export function cheminUnite(index: Map<string, Unite>, uniteId: string | null | undefined): string {
  const noms: string[] = [];
  const vus = new Set<string>();
  let courant = uniteId ? index.get(uniteId) : undefined;
  while (courant && !vus.has(courant.identifiant)) {
    vus.add(courant.identifiant);
    noms.unshift(courant.libelle);
    courant = courant.parent_identifiant ? index.get(courant.parent_identifiant) : undefined;
  }
  return noms.join(' › ');
}
