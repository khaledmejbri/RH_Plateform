import { apiFetch, clearToken } from './auth';
import { parseJson } from './rhClient';

const BASE = '/api/rh/v1/admin/presence';

export class PresenceApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = 'PresenceApiError';
  }
}

export type QrCredentialStatut = 'ACTIF' | 'REVOQUE' | 'EXPIRE';

export type PresenceSite = {
  id: string;
  code: string;
  libelle: string;
  latitude: number | null;
  longitude: number | null;
  rayonMetres: number;
  actif: boolean;
  qrVersion: number | null;
  validFrom: string | null;
  validUntil: string | null;
  qrStatut: QrCredentialStatut | null;
  /** Flag API J-30 ; sinon dérivé côté UI. */
  expireBientot: boolean;
  hasQrActif: boolean;
};

export type PresenceQrGenerateResult = {
  siteId: string;
  qrVersion: number;
  validFrom: string;
  validUntil: string;
};

export type PointageType = 'ENTREE' | 'SORTIE';

export type PointageStatut =
  | 'VALIDE'
  | 'REJETE_QR_INVALIDE'
  | 'REJETE_QR_EXPIRE'
  | 'REJETE_QR_REVOQUE'
  | 'REJETE_HORS_ZONE'
  | 'REJETE_SITE_INACTIF'
  | 'REJETE_EMPLACEMENT_ABSENT'
  | 'REJETE_PRECISION_GPS'
  | string;

export type PresencePointage = {
  id: string;
  collaborateurId: string;
  siteId: string;
  siteLibelle: string | null;
  type: PointageType | string;
  statut: PointageStatut;
  serverTs: string;
  distanceMetres: number | null;
  motifRejet: string | null;
};

export type PagePresence<T> = {
  contenu: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  taille: number;
};

export const POINTAGE_STATUTS_FILTRE: { value: string; label: string }[] = [
  { value: '', label: 'Tous les statuts' },
  { value: 'VALIDE', label: 'Validé' },
  { value: 'REJETE_HORS_ZONE', label: 'Hors zone' },
  { value: 'REJETE_QR_INVALIDE', label: 'QR non reconnu' },
  { value: 'REJETE_QR_EXPIRE', label: 'QR expiré' },
  { value: 'REJETE_QR_REVOQUE', label: 'QR remplacé' },
  { value: 'REJETE_SITE_INACTIF', label: 'Site indisponible' },
  { value: 'REJETE_EMPLACEMENT_ABSENT', label: 'Emplacement absent' },
  { value: 'REJETE_PRECISION_GPS', label: 'GPS imprécis' },
];

export const LIBELLES_POINTAGE_STATUT: Record<string, string> = {
  VALIDE: 'Validé',
  REJETE_HORS_ZONE: 'Hors zone',
  REJETE_QR_INVALIDE: 'QR non reconnu',
  REJETE_QR_EXPIRE: 'QR expiré',
  REJETE_QR_REVOQUE: 'QR remplacé',
  REJETE_SITE_INACTIF: 'Site indisponible',
  REJETE_EMPLACEMENT_ABSENT: 'Emplacement absent',
  REJETE_PRECISION_GPS: 'GPS imprécis',
};

async function handlePresenceResponse<T>(res: Response): Promise<T> {
  if (res.status === 401) {
    clearToken();
    window.location.href = '/login';
    throw new PresenceApiError('Session expirée', 401);
  }
  if (res.status === 204) {
    return undefined as T;
  }
  if (!res.ok) {
    const body = (await parseJson<{ erreur?: string; message?: string; error?: string }>(res).catch(
      () => ({}) as { erreur?: string; message?: string; error?: string },
    )) as { erreur?: string; message?: string; error?: string };
    const msg = body.erreur ?? body.message ?? body.error ?? res.statusText;
    throw new PresenceApiError(msg || `Erreur ${res.status}`, res.status);
  }
  if (res.status === 201 || res.status === 200) {
    const text = await res.text();
    if (!text) return undefined as T;
    return JSON.parse(text) as T;
  }
  return parseJson<T>(res);
}

function asNum(v: unknown): number | null {
  if (v == null || v === '') return null;
  const n = typeof v === 'number' ? v : Number(v);
  return Number.isFinite(n) ? n : null;
}

function asStr(v: unknown): string | null {
  if (v == null) return null;
  const s = String(v).trim();
  return s || null;
}

function asBool(v: unknown, fallback = false): boolean {
  if (typeof v === 'boolean') return v;
  if (v == null) return fallback;
  return Boolean(v);
}

const J30_MS = 30 * 24 * 60 * 60 * 1000;

export function isQrExpired(validUntil: string | null | undefined): boolean {
  if (!validUntil) return false;
  const t = Date.parse(validUntil);
  return Number.isFinite(t) && t < Date.now();
}

export function isExpireBientot(
  validUntil: string | null | undefined,
  flag?: boolean | null,
): boolean {
  if (flag === true) return true;
  if (!validUntil || isQrExpired(validUntil)) return false;
  const t = Date.parse(validUntil);
  if (!Number.isFinite(t)) return false;
  const restant = t - Date.now();
  return restant >= 0 && restant <= J30_MS;
}

export function normalizeSite(raw: Record<string, unknown>): PresenceSite {
  const validUntil = asStr(raw.validUntil ?? raw.valid_until);
  const qrStatutRaw = asStr(raw.qrStatut ?? raw.qr_statut)?.toUpperCase() as QrCredentialStatut | null;
  const hasQrActif =
    asBool(raw.hasQrActif ?? raw.has_qr_actif, false) ||
    qrStatutRaw === 'ACTIF' ||
    (Boolean(validUntil) && !isQrExpired(validUntil) && qrStatutRaw !== 'REVOQUE' && qrStatutRaw !== 'EXPIRE');

  const expireFlag = raw.expireBientot ?? raw.expire_bientot;
  const expireBientot =
    typeof expireFlag === 'boolean'
      ? expireFlag
      : hasQrActif && isExpireBientot(validUntil);

  return {
    id: String(raw.id ?? raw.identifiant ?? ''),
    code: String(raw.code ?? ''),
    libelle: String(raw.libelle ?? ''),
    latitude: asNum(raw.latitude),
    longitude: asNum(raw.longitude),
    rayonMetres: asNum(raw.rayonMetres ?? raw.rayon_metres) ?? 50,
    actif: asBool(raw.actif, true),
    qrVersion: asNum(raw.qrVersion ?? raw.qr_version),
    validFrom: asStr(raw.validFrom ?? raw.valid_from),
    validUntil,
    qrStatut: qrStatutRaw,
    expireBientot,
    hasQrActif,
  };
}

function normalizePointage(raw: Record<string, unknown>): PresencePointage {
  return {
    id: String(raw.id ?? raw.identifiant ?? ''),
    collaborateurId: String(
      raw.collaborateurId ?? raw.collaborateur_id ?? raw.matricule ?? '',
    ),
    siteId: String(raw.siteId ?? raw.site_id ?? ''),
    siteLibelle: asStr(raw.siteLibelle ?? raw.site_libelle),
    type: String(raw.type ?? ''),
    statut: String(raw.statut ?? ''),
    serverTs: String(raw.serverTs ?? raw.server_ts ?? raw.creeLe ?? raw.cree_le ?? ''),
    distanceMetres: asNum(raw.distanceMetres ?? raw.distance_metres),
    motifRejet: asStr(raw.motifRejet ?? raw.motif_rejet),
  };
}

function normalizePage<T>(
  raw: Record<string, unknown>,
  mapItem: (item: Record<string, unknown>) => T,
): PagePresence<T> {
  const list = (raw.content ?? raw.contenu ?? []) as unknown[];
  const contenu = list.map((item) => mapItem((item ?? {}) as Record<string, unknown>));
  const totalElements = Number(raw.totalElements ?? raw.total_elements ?? contenu.length);
  const taille = Number(raw.size ?? raw.taille ?? (contenu.length || 20));
  const totalPages = Number(
    raw.totalPages ?? raw.total_pages ?? Math.max(1, Math.ceil(totalElements / (taille || 1))),
  );
  const page = Number(raw.number ?? raw.page ?? 0);
  return { contenu, totalElements, totalPages, page, taille };
}

export function formatDateTunis(iso: string | null | undefined): string {
  if (!iso) return '—';
  try {
    // fr-TN rend l'heure en 12 h (AM/PM) selon les navigateurs : fr-FR + h23 force le format 24 h (SCRUM-16).
    return new Intl.DateTimeFormat('fr-FR', {
      timeZone: 'Africa/Tunis',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    }).format(new Date(iso));
  } catch {
    return iso;
  }
}

export function formatDateCourteTunis(iso: string | null | undefined): string {
  if (!iso) return '—';
  try {
    return new Intl.DateTimeFormat('fr-FR', {
      timeZone: 'Africa/Tunis',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    }).format(new Date(iso));
  } catch {
    return iso;
  }
}

export function isServiceUnavailable(err: unknown): boolean {
  if (err instanceof PresenceApiError) {
    return err.status === 0 || err.status === 404 || err.status === 502 || err.status === 503;
  }
  return false;
}

export function messagePresenceErreur(err: unknown, fallback = 'Erreur'): string {
  if (err instanceof PresenceApiError) {
    if (err.status === 403) return 'Accès refusé — droits insuffisants pour cette action.';
    if (err.status === 409) return err.message || 'Conflit (code déjà utilisé ou état incompatible).';
    if (err.status === 422) return err.message || 'Données invalides.';
    if (err.status === 404) return err.message || 'Ressource introuvable (service présence peut être indisponible).';
    return err.message || fallback;
  }
  if (err instanceof Error) return err.message || fallback;
  return fallback;
}

export type SitesListParams = {
  page?: number;
  size?: number;
  actif?: boolean | null;
  recherche?: string;
  expireBientot?: boolean | null;
};

export function getPresenceSites(params: SitesListParams = {}) {
  const q = new URLSearchParams();
  q.set('page', String(params.page ?? 0));
  q.set('size', String(params.size ?? 20));
  if (params.actif === true) q.set('actif', 'true');
  if (params.actif === false) q.set('actif', 'false');
  if (params.recherche?.trim()) q.set('q', params.recherche.trim());
  if (params.expireBientot === true) q.set('expireBientot', 'true');
  return apiFetch(`${BASE}/sites?${q}`)
    .then((r) => handlePresenceResponse<Record<string, unknown>>(r))
    .then((raw) => {
      if (Array.isArray(raw)) {
        const contenu = raw.map((item) => normalizeSite(item as Record<string, unknown>));
        return {
          contenu,
          totalElements: contenu.length,
          totalPages: 1,
          page: 0,
          taille: contenu.length,
        } satisfies PagePresence<PresenceSite>;
      }
      return normalizePage(raw ?? {}, normalizeSite);
    });
}

export function getPresenceSite(siteId: string) {
  return apiFetch(`${BASE}/sites/${encodeURIComponent(siteId)}`)
    .then((r) => handlePresenceResponse<Record<string, unknown>>(r))
    .then((raw) => normalizeSite(raw ?? {}));
}

export function postPresenceSite(body: {
  code: string;
  libelle: string;
  latitude?: number | null;
  longitude?: number | null;
}) {
  return apiFetch(`${BASE}/sites`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
    .then((r) => handlePresenceResponse<Record<string, unknown>>(r))
    .then((raw) => normalizeSite(raw ?? {}));
}

export function patchPresenceSite(
  siteId: string,
  body: { libelle?: string; actif?: boolean },
) {
  return apiFetch(`${BASE}/sites/${encodeURIComponent(siteId)}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
    .then((r) => handlePresenceResponse<Record<string, unknown>>(r))
    .then((raw) => normalizeSite(raw ?? {}));
}

/** Should / optionnel : pose GPS manuelle web (Must = mobile RH). */
export function putPresenceEmplacement(
  siteId: string,
  body: { latitude: number; longitude: number },
) {
  return apiFetch(`${BASE}/sites/${encodeURIComponent(siteId)}/emplacement`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
    .then((r) => handlePresenceResponse<Record<string, unknown>>(r))
    .then((raw) => normalizeSite(raw ?? {}));
}

export function postGenerateQr(siteId: string) {
  return apiFetch(`${BASE}/sites/${encodeURIComponent(siteId)}/qr/generate`, {
    method: 'POST',
  }).then(async (r) => {
    const raw = await handlePresenceResponse<Record<string, unknown>>(r);
    return {
      siteId: String(raw?.siteId ?? raw?.site_id ?? siteId),
      qrVersion: Number(raw?.qrVersion ?? raw?.qr_version ?? 0),
      validFrom: String(raw?.validFrom ?? raw?.valid_from ?? ''),
      validUntil: String(raw?.validUntil ?? raw?.valid_until ?? ''),
    } satisfies PresenceQrGenerateResult;
  });
}

export async function downloadSiteQrPng(siteId: string, filename?: string): Promise<void> {
  const res = await apiFetch(`${BASE}/sites/${encodeURIComponent(siteId)}/qr/download`, {
    headers: { Accept: 'image/png' },
  });
  if (res.status === 401) {
    clearToken();
    window.location.href = '/login';
    throw new PresenceApiError('Session expirée', 401);
  }
  if (!res.ok) {
    const body = (await parseJson<{ erreur?: string; message?: string }>(res).catch(
      () => ({}) as { erreur?: string; message?: string },
    )) as { erreur?: string; message?: string };
    throw new PresenceApiError(
      body.erreur ?? body.message ?? (res.statusText || 'Téléchargement impossible'),
      res.status,
    );
  }
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename ?? `qr-site-${siteId}.png`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

export function postRevokeQr(siteId: string) {
  return apiFetch(`${BASE}/sites/${encodeURIComponent(siteId)}/revoke-qr`, {
    method: 'POST',
  }).then((r) => handlePresenceResponse<void>(r));
}

export type PointagesListParams = {
  page?: number;
  size?: number;
  siteId?: string;
  statut?: string;
  type?: string;
  du?: string;
  au?: string;
};

export function getPresencePointages(params: PointagesListParams = {}) {
  const q = new URLSearchParams();
  q.set('page', String(params.page ?? 0));
  q.set('size', String(params.size ?? 20));
  if (params.siteId) q.set('siteId', params.siteId);
  if (params.statut) q.set('statut', params.statut);
  if (params.type) q.set('type', params.type);
  if (params.du) q.set('du', params.du);
  if (params.au) q.set('au', params.au);
  return apiFetch(`${BASE}/pointages?${q}`)
    .then((r) => handlePresenceResponse<Record<string, unknown>>(r))
    .then((raw) => {
      if (Array.isArray(raw)) {
        const contenu = raw.map((item) => normalizePointage(item as Record<string, unknown>));
        return {
          contenu,
          totalElements: contenu.length,
          totalPages: 1,
          page: 0,
          taille: contenu.length,
        } satisfies PagePresence<PresencePointage>;
      }
      return normalizePage(raw ?? {}, normalizePointage);
    });
}
