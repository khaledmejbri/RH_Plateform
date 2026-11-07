import { apiFetch, clearToken } from './auth';
import { parseJson } from './rhClient';
import { handlePresenceResponse, normalizePage, PresenceApiError, type PagePresence } from './presenceApi';

/** Heures supplémentaires (D-P13 à D-P16, SCRUM-50 / 51) — contrat : ADR-M09-horaires-heures-sup § 10. */
const BASE = '/api/rh/v1/admin/presence/heures-sup';

export type StatutHeuresSup = 'EN_ATTENTE' | 'VALIDEE' | 'REFUSEE' | 'ANNULEE' | 'EXPIREE';

export const LIBELLES_STATUT_HEURES_SUP: Record<StatutHeuresSup, string> = {
  EN_ATTENTE: 'En attente',
  VALIDEE: 'Validée',
  REFUSEE: 'Refusée',
  ANNULEE: 'Annulée',
  EXPIREE: 'Expirée',
};

export type BeneficiaireHeuresSup = {
  collaborateur_id: string;
  matricule: string | null;
  nom: string | null;
};

export type EtatHeuresComptees = 'COMPLET' | 'INCOMPLET' | 'EN_COURS';

export const LIBELLES_ETAT_COMPTEES: Record<EtatHeuresComptees, string> = {
  COMPLET: 'Complet',
  INCOMPLET: 'Incomplet',
  EN_COURS: 'En cours',
};

/** `DemandeHeuresSupResponse` (ADR-M09-horaires-heures-sup § 10, statut « accepté »). */
export type DemandeHeuresSup = {
  id: string;
  /** Numéro lisible HS-AAAAMM-NNNN (D-L02). */
  numero?: string;
  reference: string;
  statut: StatutHeuresSup;
  site_id: string;
  site_libelle: string | null;
  date_debut: string;
  date_fin: string;
  /** Jours ouverts restants (yyyy-MM-dd). */
  jours: string[];
  ouverture_avant_minutes: number;
  ouverture_apres_minutes: number;
  /** Ouverture de la fenêtre élargie du premier jour ouvert restant : après cet instant, plus d'annulation. */
  debut_plage_ts: string | null;
  motif: string;
  demandeur_nom: string | null;
  beneficiaires: BeneficiaireHeuresSup[];
  creee_validee: boolean;
  decide_par: string | null;
  decide_le: string | null;
  motif_refus: string | null;
  annule_par: string | null;
  annule_le: string | null;
  motif_annulation: string | null;
  cree_le: string | null;
};

export type FiltreDemandes = {
  statut?: string;
  siteId?: string;
  collaborateurId?: string;
  directionId?: string;
  du?: string;
  au?: string;
};

export type FiltreComptees = Omit<FiltreDemandes, 'statut'>;

export type CreationHeuresSupRequest = {
  collaborateur_ids: string[];
  site_id: string;
  date_debut: string;
  date_fin?: string;
  ouverture_avant_minutes?: number;
  ouverture_apres_minutes?: number;
  motif: string;
};

export type HeuresComptees = {
  collaborateur_id: string;
  matricule: string | null;
  nom: string | null;
  site_id: string;
  site_libelle: string | null;
  journee: string;
  horaire_debut: string | null;
  horaire_fin: string | null;
  plage_avant: string | null;
  plage_apres: string | null;
  entree: string | null;
  sortie: string | null;
  minutes_comptees: number;
  heures_comptees: string;
  reference_demande: string | null;
  etat: EtatHeuresComptees;
};

function setIf(q: URLSearchParams, key: string, v: string | undefined) {
  if (v && v.trim()) q.set(key, v.trim());
}

function filtreVersParams(f: FiltreDemandes, page?: number, size?: number): URLSearchParams {
  const q = new URLSearchParams();
  if (page != null) q.set('page', String(page));
  if (size != null) q.set('size', String(size));
  setIf(q, 'statut', f.statut);
  setIf(q, 'site_id', f.siteId);
  setIf(q, 'collaborateur_id', f.collaborateurId);
  setIf(q, 'direction_id', f.directionId);
  setIf(q, 'du', f.du);
  setIf(q, 'au', f.au);
  return q;
}

export function getDemandesHeuresSup(
  f: FiltreDemandes,
  page = 0,
  size = 20,
): Promise<PagePresence<DemandeHeuresSup>> {
  return apiFetch(`${BASE}/demandes?${filtreVersParams(f, page, size)}`)
    .then((r) => handlePresenceResponse<Record<string, unknown>>(r))
    .then((raw) => normalizePage<DemandeHeuresSup>(raw ?? {}, (i) => i as unknown as DemandeHeuresSup));
}

export function postCreerDemandeHeuresSup(body: CreationHeuresSupRequest) {
  return apiFetch(`${BASE}/demandes`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }).then((r) => handlePresenceResponse<DemandeHeuresSup>(r));
}

export function postValiderHeuresSup(id: string) {
  return apiFetch(`${BASE}/demandes/${encodeURIComponent(id)}/valider`, { method: 'POST' }).then((r) =>
    handlePresenceResponse<DemandeHeuresSup>(r),
  );
}

export function postRefuserHeuresSup(id: string, motif: string) {
  return apiFetch(`${BASE}/demandes/${encodeURIComponent(id)}/refuser`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ motif }),
  }).then((r) => handlePresenceResponse<DemandeHeuresSup>(r));
}

export function postAnnulerHeuresSup(id: string, motif?: string) {
  return apiFetch(`${BASE}/demandes/${encodeURIComponent(id)}/annuler`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(motif?.trim() ? { motif: motif.trim() } : {}),
  }).then((r) => handlePresenceResponse<DemandeHeuresSup>(r));
}

export function getHeuresComptees(f: FiltreComptees, page = 0, size = 20): Promise<PagePresence<HeuresComptees>> {
  return apiFetch(`${BASE}/comptees?${filtreVersParams(f, page, size)}`)
    .then((r) => handlePresenceResponse<Record<string, unknown>>(r))
    .then((raw) => normalizePage<HeuresComptees>(raw ?? {}, (i) => i as unknown as HeuresComptees));
}

/** Export CSV : téléchargement d'un blob avec le JWT (un simple lien ne porterait pas l'en-tête Authorization). */
export async function telechargerHeuresSupCsv(f: FiltreComptees): Promise<void> {
  const res = await apiFetch(`${BASE}/comptees/export.csv?${filtreVersParams(f)}`, {
    headers: { Accept: 'text/csv' },
  });
  if (res.status === 401) {
    clearToken();
    window.location.href = '/login';
    throw new PresenceApiError('Session expirée', 401);
  }
  if (!res.ok) {
    const body = await parseJson<{ erreur?: string; message?: string; code?: string }>(res).catch(
      () => ({}) as { erreur?: string; message?: string; code?: string },
    );
    throw new PresenceApiError(
      body.erreur ?? body.message ?? (res.statusText || 'Export impossible'),
      res.status,
      body.code,
    );
  }
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `heures-supplementaires-${f.du ?? 'debut'}-${f.au ?? 'fin'}.csv`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
