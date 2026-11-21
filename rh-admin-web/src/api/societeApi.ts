import { apiFetch, clearToken } from './auth';

/**
 * Client API Paramètres de la société et Fonctions transverses (`svc-referentiel-rh`, lot W7a, D-W7-03 / D-W7-11).
 */

export type BaseDecompteConge = 'JOURS_TRAVAILLES' | 'JOURS_OUVRES_LUN_VEN' | 'JOURS_OUVRABLES_LUN_SAM';

export type SocieteParametres = {
  id: string;
  societe_id: string;
  jours_travailles: string[];
  duree_hebdo_minutes: number;
  duree_jour_minutes: Record<string, number>;
  heure_debut_journee: string;
  heure_fin_journee: string;
  base_decompte_conge: BaseDecompteConge;
  jours_acquis_par_mois: number;
  duree_max_sortie_minutes: number;
  delegation_auto_conges: boolean;
  publication_quatre_yeux: boolean;
  version: number;
  modifie_le: string | null;
  modifie_par_nom: string | null;
};

export type SocieteParametresUpdate = {
  jours_travailles: string[];
  duree_hebdo_minutes: number;
  duree_jour_minutes: Record<string, number>;
  heure_debut_journee: string;
  heure_fin_journee: string;
  base_decompte_conge: BaseDecompteConge;
  jours_acquis_par_mois: number;
  duree_max_sortie_minutes: number;
  delegation_auto_conges: boolean;
  publication_quatre_yeux: boolean;
  version: number;
};

export type SocieteJournalItem = {
  id: string;
  auteur_compte_id: string | null;
  auteur_nom: string | null;
  avant_json: string | null;
  apres_json: string | null;
  horodatage: string;
};

export type PageSpring<T> = {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type FonctionRh = {
  id: string;
  code: string;
  libelle: string;
  description: string | null;
  actif: boolean;
  utilisee: boolean;
  version: number;
  cree_le: string | null;
  modifie_le: string | null;
};

export type AffectationFonction = {
  id: string;
  fonction_id: string;
  collaborateur_id: string;
  collaborateur_nom: string;
  collaborateur_matricule: string | null;
  unite_perimetre_id: string | null;
  unite_perimetre_nom: string | null;
  du: string;
  au: string | null;
  cree_le: string | null;
};

export class SocieteApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code: string | null = null,
  ) {
    super(message);
  }
}

async function lire<T>(res: Response): Promise<T> {
  if (res.status === 401) {
    clearToken();
    window.location.href = '/login';
    throw new SocieteApiError('Session expirée', 401);
  }
  if (!res.ok) {
    const corps = (await res.json().catch(() => ({}))) as { code?: string; message?: string; erreur?: string };
    throw new SocieteApiError(corps.message ?? corps.erreur ?? res.statusText, res.status, corps.code ?? null);
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
    throw new SocieteApiError('Le serveur ne répond pas.', 0);
  }
  return lire<T>(res);
}

const JSON_HEADERS = { 'Content-Type': 'application/json' };
const BASE_SOCIETE = '/api/rh/v1/admin/societe/parametres';
const BASE_FONCTIONS = '/api/rh/v1/admin/fonctions';

function jsonBody(corps: unknown): RequestInit {
  return { headers: JSON_HEADERS, body: JSON.stringify(corps) };
}

export function messageErreurSociete(e: unknown): string {
  if (!(e instanceof SocieteApiError)) return 'Une erreur inattendue est survenue.';
  if (e.status === 0) return 'Le serveur ne répond pas. Rien n’a été modifié.';
  if (e.status === 403) return 'Action non autorisée pour votre profil.';
  if (e.status >= 500) return 'Le serveur a rencontré une erreur. Réessayez plus tard.';
  return e.message;
}

// ─── Paramètres société ──────────────────────────────────────────────────────

export function getSocieteParametres(): Promise<SocieteParametres> {
  return appeler<SocieteParametres>(BASE_SOCIETE);
}

export function putSocieteParametres(corps: SocieteParametresUpdate): Promise<SocieteParametres> {
  return appeler<SocieteParametres>(BASE_SOCIETE, { method: 'PUT', ...jsonBody(corps) });
}

export function getSocieteJournal(page = 0, size = 20): Promise<PageSpring<SocieteJournalItem>> {
  return appeler<PageSpring<SocieteJournalItem>>(`${BASE_SOCIETE}/journal?page=${page}&size=${size}`);
}

// ─── Fonctions transverses ───────────────────────────────────────────────────

export function getFonctions(actifsSeulement = false): Promise<FonctionRh[]> {
  return appeler<FonctionRh[]>(`${BASE_FONCTIONS}?actifs=${actifsSeulement}`);
}

export function creerFonction(corps: { libelle: string; description?: string | null }): Promise<FonctionRh> {
  return appeler<FonctionRh>(BASE_FONCTIONS, { method: 'POST', ...jsonBody(corps) });
}

export function modifierFonction(
  id: string,
  corps: { libelle: string; description?: string | null; version: number },
): Promise<FonctionRh> {
  return appeler<FonctionRh>(`${BASE_FONCTIONS}/${encodeURIComponent(id)}`, {
    method: 'PUT',
    ...jsonBody(corps),
  });
}

export function activerFonction(id: string): Promise<FonctionRh> {
  return appeler<FonctionRh>(`${BASE_FONCTIONS}/${encodeURIComponent(id)}/activer`, { method: 'POST' });
}

export function desactiverFonction(id: string): Promise<FonctionRh> {
  return appeler<FonctionRh>(`${BASE_FONCTIONS}/${encodeURIComponent(id)}/desactiver`, { method: 'POST' });
}

export function supprimerFonction(id: string): Promise<void> {
  return appeler<void>(`${BASE_FONCTIONS}/${encodeURIComponent(id)}`, { method: 'DELETE' });
}

export function getAffectationsFonction(fonctionId: string): Promise<AffectationFonction[]> {
  return appeler<AffectationFonction[]>(`${BASE_FONCTIONS}/${encodeURIComponent(fonctionId)}/affectations`);
}

export function ajouterAffectationFonction(
  fonctionId: string,
  corps: { collaborateur_id: string; unite_perimetre_id?: string | null; du: string; au?: string | null },
): Promise<AffectationFonction> {
  return appeler<AffectationFonction>(`${BASE_FONCTIONS}/${encodeURIComponent(fonctionId)}/affectations`, {
    method: 'POST',
    ...jsonBody(corps),
  });
}

export function retirerAffectationFonction(fonctionId: string, affectationId: string): Promise<void> {
  return appeler<void>(
    `${BASE_FONCTIONS}/${encodeURIComponent(fonctionId)}/affectations/${encodeURIComponent(affectationId)}`,
    { method: 'DELETE' },
  );
}
