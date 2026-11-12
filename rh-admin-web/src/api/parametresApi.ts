import { apiFetch, clearToken } from './auth';

/**
 * Paramétrage des référentiels (lot 3, ADR-UAT-lot3-parametrage § 7).
 * Même contrat dans les trois services propriétaires des codes ; seule la base d'URL change.
 */
export type ServiceParametre = 'referentiel' | 'evaluation' | 'presence';

export const SERVICES: readonly ServiceParametre[] = ['referentiel', 'evaluation', 'presence'];

const BASES: Record<ServiceParametre, { admin: string; libelles: string }> = {
  referentiel: {
    admin: '/api/referentiel/v1/parametres',
    libelles: '/api/referentiel/v1/parametres/libelles',
  },
  evaluation: {
    admin: '/api/rh/v1/admin/evaluations/parametres',
    libelles: '/api/rh/v1/evaluations/parametres/libelles',
  },
  presence: {
    admin: '/api/rh/v1/admin/presence/parametres',
    libelles: '/api/rh/v1/presence/parametres/libelles',
  },
};

export type AttributDescripteur = { nom: string; type: string };

export type DescripteurReferentiel = {
  referentiel: string;
  libelle: string;
  domaine: string;
  nature: 'VALEUR' | 'COMPORTEMENT';
  operations: { creer: boolean; supprimer: boolean; desactiver: boolean };
  attributs: AttributDescripteur[];
};

/** Valeur telle que servie par l'API d'administration (actives et inactives). */
export type Valeur = {
  code: string;
  libelle: string;
  description: string | null;
  couleur: string | null;
  ordre: number;
  actif: boolean;
  systeme: boolean;
  nature: 'VALEUR' | 'COMPORTEMENT';
  version: number;
  utilisations: number;
  deja_utilisee: boolean;
  sla_heures?: number | null;
  piece_jointe_obligatoire?: boolean | null;
  piece_jointe_verrouillee?: boolean | null;
  seuil_ecart?: number | null;
};

/** Valeur telle que servie par `GET L` (libellés pour tous les écrans). */
export type ValeurLibelle = {
  code: string;
  libelle: string;
  description?: string | null;
  couleur?: string | null;
  ordre: number;
  actif: boolean;
  sla_heures?: number | null;
  piece_jointe_obligatoire?: boolean | null;
  seuil_ecart?: number | null;
};

export type Libelles = { version: string; referentiels: Record<string, ValeurLibelle[]> };

export type Reglage = {
  cle: string;
  libelle: string;
  valeur: number;
  min: number;
  max: number;
  unite: string | null;
  defaut: number;
  version: number;
};

export type EntreeJournal = {
  action: string;
  code: string | null;
  auteur_nom: string | null;
  horodatage: string;
  avant: Record<string, unknown> | null;
  apres: Record<string, unknown> | null;
};

export type PageJournal = { contenu: EntreeJournal[]; page: number; taille: number; total: number };

export type CorpsValeur = {
  libelle: string;
  description?: string | null;
  couleur?: string | null;
  sla_heures?: number | null;
  piece_jointe_obligatoire?: boolean;
  seuil_ecart?: number | null;
};

/** Erreur du paramétrage : statut HTTP, code métier (ex. `PARAMETRE_UTILISE`) et données de la réponse. */
export class ParametreApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code: string | null = null,
    readonly utilisations: number | null = null,
    readonly champ: string | null = null,
  ) {
    super(message);
  }
}

type CorpsErreur = {
  code?: string;
  erreur?: string;
  message?: string;
  utilisations?: number;
  /** Évaluation : `ErrorResponse` existante, données complémentaires dans `donnees`. */
  donnees?: { utilisations?: number; deja_utilisee?: boolean } | null;
  champ?: string;
  field?: string;
};

const CHAMP_PAR_CODE: Record<string, string> = {
  LIBELLE_INVALIDE: 'libelle',
  LIBELLE_EN_DOUBLE: 'libelle',
  DESCRIPTION_TROP_LONGUE: 'description',
  COULEUR_INVALIDE: 'couleur',
  SLA_INVALIDE: 'sla_heures',
  SEUIL_INVALIDE: 'seuil_ecart',
  SEUIL_EN_DOUBLE: 'seuil_ecart',
  NIVEAUX_ACTIFS_MAX: 'seuil_ecart',
  PIECE_JOINTE_VERROUILLEE: 'piece_jointe_obligatoire',
  REGLAGE_HORS_BORNES: 'valeur',
};

async function lire<T>(res: Response): Promise<T> {
  if (res.status === 401) {
    clearToken();
    window.location.href = '/login';
    throw new ParametreApiError('Session expirée', 401);
  }
  if (!res.ok) {
    const corps = (await res.json().catch(() => ({}))) as CorpsErreur;
    const code = corps.code ?? null;
    throw new ParametreApiError(
      corps.message ?? corps.erreur ?? res.statusText,
      res.status,
      code,
      typeof corps.utilisations === 'number'
        ? corps.utilisations
        : typeof corps.donnees?.utilisations === 'number' ? corps.donnees.utilisations : null,
      corps.champ ?? corps.field ?? (code ? CHAMP_PAR_CODE[code] ?? null : null),
    );
  }
  if (res.status === 204) return undefined as T;
  const texte = await res.text();
  return (texte ? JSON.parse(texte) : undefined) as T;
}

async function appeler<T>(url: string, init: RequestInit = {}): Promise<T> {
  let res: Response;
  try {
    res = await apiFetch(url, init);
  } catch {
    throw new ParametreApiError('Le serveur ne répond pas.', 0);
  }
  return lire<T>(res);
}

const JSON_HEADERS = { 'Content-Type': 'application/json' };

function corpsJson(corps: unknown): RequestInit {
  return { headers: JSON_HEADERS, body: JSON.stringify(corps) };
}

/** Message affichable pour une erreur de paramétrage (le serveur décide, on formule). */
export function messageParametre(e: unknown): string {
  if (!(e instanceof ParametreApiError)) return 'Une erreur inattendue est survenue. Rien n’a été modifié.';
  if (e.status === 0) return 'Le serveur ne répond pas. Rien n’a été modifié.';
  if (e.status === 403) return 'Action non autorisée : la consultation est réservée à votre profil.';
  if (e.status >= 500) return 'Le serveur a rencontré une erreur. Rien n’a été modifié, réessayez plus tard.';
  return e.message;
}

/* ─── Libellés (tous les utilisateurs authentifiés) ─────────────────────── */

export type ReponseLibelles = { statut: 'modifie'; libelles: Libelles; etag: string | null } | { statut: 'inchange' };

/** `GET L` avec revalidation par ETag : 304 → rien à retélécharger. */
export async function getLibelles(service: ServiceParametre, etag?: string | null): Promise<ReponseLibelles> {
  const headers: Record<string, string> = {};
  if (etag) headers['If-None-Match'] = etag;
  let res: Response;
  try {
    res = await apiFetch(`${BASES[service].libelles}?actifs=false`, { headers });
  } catch {
    throw new ParametreApiError('Le serveur ne répond pas.', 0);
  }
  if (res.status === 304) return { statut: 'inchange' };
  const libelles = await lire<Libelles>(res);
  return { statut: 'modifie', libelles, etag: res.headers.get('ETag') ?? libelles.version ?? null };
}

/* ─── Administration (lecture : RH, DIRECTION, ADMIN ; écriture : RH, ADMIN) ─ */

export function getDescripteurs(service: ServiceParametre): Promise<DescripteurReferentiel[]> {
  return appeler<DescripteurReferentiel[]>(`${BASES[service].admin}/referentiels`);
}

export function getValeurs(service: ServiceParametre, referentiel: string): Promise<Valeur[]> {
  return appeler<Valeur[]>(`${BASES[service].admin}/referentiels/${encodeURIComponent(referentiel)}/valeurs`);
}

export function creerValeur(service: ServiceParametre, referentiel: string, corps: CorpsValeur): Promise<Valeur> {
  return appeler<Valeur>(`${BASES[service].admin}/referentiels/${encodeURIComponent(referentiel)}/valeurs`, {
    method: 'POST',
    ...corpsJson(corps),
  });
}

export function modifierValeur(
  service: ServiceParametre,
  referentiel: string,
  code: string,
  corps: CorpsValeur,
  version: number,
): Promise<Valeur> {
  return appeler<Valeur>(
    `${BASES[service].admin}/referentiels/${encodeURIComponent(referentiel)}/valeurs/${encodeURIComponent(code)}`,
    { method: 'PUT', ...corpsJson({ ...corps, version }) },
  );
}

export function desactiverValeur(service: ServiceParametre, referentiel: string, code: string, version: number): Promise<Valeur> {
  return appeler<Valeur>(
    `${BASES[service].admin}/referentiels/${encodeURIComponent(referentiel)}/valeurs/${encodeURIComponent(code)}/desactiver`,
    { method: 'POST', ...corpsJson({ version }) },
  );
}

export function reactiverValeur(service: ServiceParametre, referentiel: string, code: string, version: number): Promise<Valeur> {
  return appeler<Valeur>(
    `${BASES[service].admin}/referentiels/${encodeURIComponent(referentiel)}/valeurs/${encodeURIComponent(code)}/reactiver`,
    { method: 'POST', ...corpsJson({ version }) },
  );
}

export function supprimerValeur(service: ServiceParametre, referentiel: string, code: string, version: number): Promise<void> {
  return appeler<void>(
    `${BASES[service].admin}/referentiels/${encodeURIComponent(referentiel)}/valeurs/${encodeURIComponent(code)}?version=${version}`,
    { method: 'DELETE' },
  );
}

/** Ordre complet du référentiel (tous les codes, actifs et inactifs). */
export function ordonnerValeurs(service: ServiceParametre, referentiel: string, codes: string[]): Promise<Valeur[]> {
  return appeler<Valeur[]>(`${BASES[service].admin}/referentiels/${encodeURIComponent(referentiel)}/ordre`, {
    method: 'PUT',
    ...corpsJson({ codes }),
  });
}

export function getJournalReferentiel(
  service: ServiceParametre,
  referentiel: string,
  page = 0,
  taille = 20,
): Promise<PageJournal> {
  return appeler<PageJournal>(
    `${BASES[service].admin}/referentiels/${encodeURIComponent(referentiel)}/journal?page=${page}&taille=${taille}`,
  );
}

export function getReglages(service: ServiceParametre): Promise<Reglage[]> {
  return appeler<Reglage[]>(`${BASES[service].admin}/reglages`);
}

export function modifierReglage(service: ServiceParametre, cle: string, valeur: number, version: number): Promise<Reglage> {
  return appeler<Reglage>(`${BASES[service].admin}/reglages/${encodeURIComponent(cle)}`, {
    method: 'PUT',
    ...corpsJson({ valeur, version }),
  });
}

export function getJournalReglages(service: ServiceParametre, page = 0, taille = 20): Promise<PageJournal> {
  return appeler<PageJournal>(`${BASES[service].admin}/reglages/journal?page=${page}&taille=${taille}`);
}
