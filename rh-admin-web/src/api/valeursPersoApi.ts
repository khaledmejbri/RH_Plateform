import { ApiMetierError, appelerJson, avecParametres, corpsJson, telecharger, type PageContrat } from './metierApi';
import type { ReglesPerso, TypeValeurPerso } from './champsPersoApi';
import type { CollaborateurRow } from './rhClient';

/** Valeurs des champs personnalisés d'une fiche, filtre et export de la liste (annexe A bis : V1 à V6). */

const F = '/api/referentiel/v1/collaborateurs';

export type ValeurListeSaisie = { code: string; libelle: string; actif?: boolean };

export type ChampValeur = {
  code: string;
  libelle: string;
  type_valeur: TypeValeurPerso;
  type_valeur_libelle: string;
  regles: ReglesPerso | null;
  valeurs_liste: ValeurListeSaisie[] | null;
  /** Chaîne canonique (nombre exact, date ISO, code de liste) ; null = non renseigné. */
  valeur: string | null;
  valeur_affichee: string | null;
  valeur_inactive?: boolean;
};

export type ChampDesactive = {
  code: string;
  libelle: string;
  type_valeur: TypeValeurPerso;
  valeur: string | null;
  valeur_affichee: string | null;
};

export type ValeursFiche = {
  collaborateur_id: string;
  version: number;
  modifiable: boolean;
  raison_non_modifiable: string | null;
  champs: ChampValeur[];
  champs_desactives: ChampDesactive[];
};

export type ErreurValeur = { champ: string | null; code: string; message: string };

export type EntreeJournalValeur = {
  id: string;
  horodatage: string;
  auteur_nom: string;
  champ_code: string;
  champ_libelle: string;
  avant: string | null;
  apres: string | null;
  origine: string;
  origine_libelle: string;
};

/** Page du journal V3 : `total` (et non `total_elements`). */
export type PageJournalValeurs = {
  contenu: EntreeJournalValeur[];
  page: number;
  taille: number;
  total: number;
};

export function getValeursFiche(id: string): Promise<ValeursFiche> {
  return appelerJson(`${F}/${encodeURIComponent(id)}/champs-personnalises`, { headers: { 'Cache-Control': 'no-cache' } });
}

/** `null` ou chaîne vide efface la valeur. Seuls les codes envoyés sont écrits. */
export function ecrireValeursFiche(id: string, version: number, valeurs: Record<string, string | null>): Promise<ValeursFiche> {
  return appelerJson(`${F}/${encodeURIComponent(id)}/champs-personnalises`, corpsJson('PUT', { version, valeurs }));
}

export function getJournalValeurs(id: string, page: number, taille = 20): Promise<PageJournalValeurs> {
  return appelerJson(avecParametres(`${F}/${encodeURIComponent(id)}/champs-personnalises/journal`, { page, taille }));
}

/* ─── Filtre de la liste (V5) et export (V6) ───────────────────────────── */

export type FiltreFixe = {
  q?: string;
  direction?: string;
  departement?: string;
  service?: string;
  profil?: string;
  statut?: string;
  famille?: string;
  niveau?: string;
  metier?: string;
};

/** Critères d'un champ personnalisé, selon son type : texte (contient), nombre (min, max), date (du, au), liste (valeurs), etat. */
export type CriterePerso = Partial<Record<'contient' | 'min' | 'max' | 'du' | 'au' | 'valeurs' | 'etat', string>>;

export type CriteresPerso = Record<string, CriterePerso>;

export const PREFIXE_CRITERE = 'cp.';

/** Paramètres `cp.<CODE>.<critère>` (même forme dans l'URL de la page et dans l'appel serveur). */
export function parametresCriteres(criteres: CriteresPerso): Record<string, string> {
  const p: Record<string, string> = {};
  for (const [code, c] of Object.entries(criteres)) {
    for (const [critere, valeur] of Object.entries(c)) {
      if (valeur) p[`${PREFIXE_CRITERE}${code}.${critere}`] = valeur;
    }
  }
  return p;
}

/** Relit les critères depuis une query string (clés `cp.<CODE>.<critère>`). */
export function criteresDepuisUrl(params: URLSearchParams): CriteresPerso {
  const r: CriteresPerso = {};
  params.forEach((valeur, cle) => {
    if (!cle.startsWith(PREFIXE_CRITERE) || !valeur) return;
    const reste = cle.slice(PREFIXE_CRITERE.length);
    const point = reste.lastIndexOf('.');
    if (point <= 0) return;
    const code = reste.slice(0, point);
    const critere = reste.slice(point + 1) as keyof CriterePerso;
    r[code] = { ...(r[code] ?? {}), [critere]: valeur };
  });
  return r;
}

export function nbCriteres(criteres: CriteresPerso): number {
  return Object.values(criteres).filter((c) => Object.values(c).some(Boolean)).length;
}

function parametresFiltre(f: FiltreFixe, criteres: CriteresPerso): Record<string, string> {
  const base: Record<string, string> = {};
  for (const [k, v] of Object.entries(f)) if (v) base[k] = v;
  return { ...base, ...parametresCriteres(criteres) };
}

/** V5 : liste paginée et filtrée côté serveur (format de page habituel). */
export function getCollaborateursFiltres(
  f: FiltreFixe, criteres: CriteresPerso, page: number, taille: number,
): Promise<PageContrat<CollaborateurRow>> {
  return appelerJson(avecParametres(F, { ...parametresFiltre(f, criteres), page, taille }));
}

/** V6 : CSV des mêmes lignes que l'écran (tous les filtres, sans pagination). */
export function exporterCollaborateurs(f: FiltreFixe, criteres: CriteresPerso): Promise<void> {
  return telecharger(avecParametres(`${F}/export`, parametresFiltre(f, criteres)), 'collaborateurs.csv');
}

/** CR : création de la fiche avec ses champs personnalisés ; les erreurs de valeur arrivent dans `erreurs[]`. */
export function creerCollaborateurAvecValeurs(corps: Record<string, unknown>): Promise<CollaborateurRow> {
  return appelerJson(F, corpsJson('POST', corps));
}

/* ─── Erreurs ──────────────────────────────────────────────────────────── */

/** Erreurs de valeur par code de champ (le serveur les renvoie toutes dans `erreurs[]`). */
export function erreursParChamp(e: unknown): Record<string, string> {
  const r: Record<string, string> = {};
  if (!(e instanceof ApiMetierError)) return r;
  const liste = e.donnees.erreurs;
  if (Array.isArray(liste)) {
    for (const x of liste as ErreurValeur[]) {
      if (x && typeof x.message === 'string') r[typeof x.champ === 'string' ? x.champ : ''] = x.message;
    }
  }
  if (Object.keys(r).length === 0 && typeof e.donnees.champ === 'string') r[e.donnees.champ] = e.message;
  return r;
}

export function estValeursModifiees(e: unknown): boolean {
  return e instanceof ApiMetierError && e.code === 'VALEURS_MODIFIEES_ENTRE_TEMPS';
}

export function messageValeursPerso(e: unknown): string {
  if (!(e instanceof ApiMetierError)) return 'Une erreur inattendue est survenue. Rien n’a été modifié.';
  if (e.code === 'AUTO_MODIFICATION_INTERDITE') return e.message || 'Vous ne pouvez pas modifier vos propres informations.';
  if (e.code === 'VALEURS_MODIFIEES_ENTRE_TEMPS') return 'Ces informations ont été modifiées par quelqu’un d’autre. Elles ont été rechargées.';
  return e.message;
}
