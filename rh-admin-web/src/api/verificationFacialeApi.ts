import { apiFetch, clearToken } from './auth';
import { parseJson } from './rhClient';

/** Contrat : docs/adr/ADR-M09-reconnaissance-faciale.md (admin, révision 2). Aucune image n'est échangée. */
const BASE = '/api/rh/v1/admin/presence/verification-faciale';

export class FacialApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code: string | null,
  ) {
    super(message);
    this.name = 'FacialApiError';
  }
}

export type ReglageStatut = 'ACTIVE' | 'INACTIVE';

export type ReglageFacial = {
  statut: ReglageStatut;
  dateMiseEnService: string | null;
  seuilScore: number;
  versionConsentementCourante: string | null;
  controleExige: boolean;
  cadreLegalConfirmePar: string | null;
  cadreLegalConfirmeLe: string | null;
  modifiePar: string | null;
  modifieLe: string | null;
};

export type ReglageFacialInput = {
  statut: ReglageStatut;
  date_mise_en_service: string | null;
  seuil_score: number;
  cadre_legal_valide: boolean;
};

export type ConsentementVersion = {
  version: string;
  publieLe: string | null;
  publiePar: string | null;
  courante: boolean;
};

export type ConsentementDetail = {
  version: string;
  texte: string;
  publieLe: string | null;
  publiePar: string | null;
};

export type EnrolementStatut = 'NON_ENROLE' | 'ENROLE' | 'REFUSE' | 'REVOQUE' | 'A_REENROLER';

export const SEUIL_MIN = 80;
export const SEUIL_MAX = 99;
export const MOTIF_MIN = 5;
export const MOTIF_MAX = 500;
export const TEXTE_CONSENTEMENT_MIN = 50;
export const TEXTE_CONSENTEMENT_MAX = 20000;

export const LIBELLES_ENROLEMENT_STATUT: Record<string, string> = {
  NON_ENROLE: 'Non enrôlé',
  ENROLE: 'Enrôlé',
  REFUSE: 'A refusé',
  REVOQUE: 'Consentement retiré',
  A_REENROLER: 'Ré-enrôlement demandé',
};

export const ENROLEMENT_STATUTS_FILTRE: { value: string; label: string }[] = [
  { value: '', label: 'Tous les statuts' },
  ...(['ENROLE', 'A_REENROLER', 'NON_ENROLE', 'REFUSE', 'REVOQUE'] as const).map((value) => ({
    value,
    label: LIBELLES_ENROLEMENT_STATUT[value],
  })),
];

export const LIBELLES_AUDIT_ACTION: Record<string, string> = {
  CONSENTEMENT_PUBLIE: 'Texte de consentement publié',
  REGLAGE_MODIFIE: 'Réglage modifié',
  CADRE_LEGAL_CONFIRME: 'Cadre légal confirmé',
  CONSENTEMENT_ACCEPTE: 'Consentement accepté',
  CONSENTEMENT_REFUSE: 'Consentement refusé',
  ENROLEMENT: 'Enrôlement',
  RETRAIT: 'Consentement retiré',
  APPAREIL_INCOMPATIBLE: 'Appareil déclaré incompatible',
  DEMANDE_REENROLEMENT: 'Demande de ré-enrôlement',
  DEBLOCAGE_RH: 'Déblocage par les RH',
  BLOCAGE_ECHECS: 'Blocage après 3 échecs',
};

export type AuditEntree = {
  action: string;
  statutAvant: string | null;
  statutApres: string | null;
  acteur: string | null;
  acteurRole: string | null;
  motif: string | null;
  version: string | null;
  horodatage: string | null;
};

export type Enrolement = {
  id: string;
  compteId: string | null;
  collaborateurId: string | null;
  collaborateurNom: string | null;
  statut: EnrolementStatut | string;
  versionConsentement: string | null;
  decideLe: string | null;
  enroleLe: string | null;
  demandeReenrolementLe: string | null;
  deviceIdMasque: string | null;
  deviceIdDemandeMasque: string | null;
  appareilIncompatible: boolean;
  echecsConsecutifs: number;
  bloqueJusqua: string | null;
  historique: AuditEntree[];
};

export type PageEnrolements = {
  contenu: Enrolement[];
  totalElements: number;
  totalPages: number;
  page: number;
};

export type EnrolementsParams = {
  statut?: string;
  q?: string;
  appareilIncompatible?: boolean;
  page?: number;
  size?: number;
};

// --- Utilitaires ---

type Raw = Record<string, unknown>;

function asStr(v: unknown): string | null {
  if (v == null) return null;
  const s = String(v).trim();
  return s || null;
}

function asNum(v: unknown, fallback = 0): number {
  if (v == null || v === '') return fallback;
  const n = typeof v === 'number' ? v : Number(v);
  return Number.isFinite(n) ? n : fallback;
}

async function handle<T>(res: Response): Promise<T> {
  if (res.status === 401) {
    clearToken();
    window.location.href = '/login';
    throw new FacialApiError('Session expirée', 401, null);
  }
  if (!res.ok) {
    const body = (await parseJson<{ code?: string; message?: string; erreur?: string; error?: string }>(res).catch(
      () => ({}) as { code?: string; message?: string; erreur?: string; error?: string },
    )) as { code?: string; message?: string; erreur?: string; error?: string };
    throw new FacialApiError(
      body.message ?? body.erreur ?? body.error ?? res.statusText ?? `Erreur ${res.status}`,
      res.status,
      body.code ?? null,
    );
  }
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

function jsonInit(method: string, body: unknown): RequestInit {
  return { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) };
}

/** Libellés français des erreurs nommées du serveur (ADR). */
export const LIBELLES_ERREUR_FACIALE: Record<string, string> = {
  SEUIL_HORS_PLAGE: `Le seuil doit être compris entre ${SEUIL_MIN} et ${SEUIL_MAX}.`,
  VERSION_CONSENTEMENT_NON_PUBLIEE:
    'Publiez d’abord une version du texte de consentement avant d’activer la vérification faciale.',
  CONFIRMATION_CADRE_LEGAL_REQUISE:
    'Confirmez que le cadre légal est validé pour activer la vérification faciale.',
  DATE_MISE_EN_SERVICE_REQUISE: 'Indiquez la date de mise en service pour activer la vérification faciale.',
  VERSION_EXISTANTE: 'Cette version existe déjà : choisissez un autre nom de version.',
  VERSION_INTROUVABLE: 'Version du texte introuvable.',
  ENROLEMENT_INTROUVABLE: 'Enrôlement introuvable.',
  AUTO_DEBLOCAGE_INTERDIT: 'Vous ne pouvez pas débloquer votre propre ré-enrôlement : un autre agent RH doit le faire.',
  DEJA_ENROLE: 'Ce collaborateur est déjà enrôlé : aucun déblocage n’est nécessaire.',
  RIEN_A_DEBLOQUER: 'Aucun ré-enrôlement n’a été demandé par ce collaborateur : rien à débloquer.',
  ACCESS_DENIED: 'Accès refusé — droits insuffisants pour cette action.',
};

export function codeErreurFaciale(err: unknown): string | null {
  return err instanceof FacialApiError ? err.code : null;
}

export function messageFacialeErreur(err: unknown, fallback = 'Erreur'): string {
  if (err instanceof FacialApiError) {
    if (err.code && LIBELLES_ERREUR_FACIALE[err.code]) return LIBELLES_ERREUR_FACIALE[err.code];
    if (err.status === 403) return 'Accès refusé — droits insuffisants pour cette action.';
    if (err.status === 404) return 'Ressource introuvable (le service présence est peut-être indisponible).';
    if (err.status === 409) return err.message || 'Conflit : l’état a changé, rechargez puis réessayez.';
    if (err.status === 400 || err.status === 422) return err.message || 'Données invalides.';
    return err.message || fallback;
  }
  if (err instanceof Error) return err.message || fallback;
  return fallback;
}

// --- Normalisation ---

function normalizeReglage(raw: Raw): ReglageFacial {
  return {
    statut: asStr(raw.statut) === 'ACTIVE' ? 'ACTIVE' : 'INACTIVE',
    dateMiseEnService: asStr(raw.date_mise_en_service),
    seuilScore: asNum(raw.seuil_score, 92),
    versionConsentementCourante: asStr(raw.version_consentement_courante),
    controleExige: raw.controle_exige === true,
    cadreLegalConfirmePar: asStr(raw.cadre_legal_confirme_par),
    cadreLegalConfirmeLe: asStr(raw.cadre_legal_confirme_le),
    modifiePar: asStr(raw.modifie_par),
    modifieLe: asStr(raw.modifie_le),
  };
}

function normalizeAudit(raw: Raw): AuditEntree {
  return {
    action: String(raw.action ?? ''),
    statutAvant: asStr(raw.statut_avant),
    statutApres: asStr(raw.statut_apres),
    acteur: asStr(raw.acteur_nom_ou_compte),
    acteurRole: asStr(raw.acteur_role),
    motif: asStr(raw.motif),
    version: asStr(raw.version),
    horodatage: asStr(raw.horodatage),
  };
}

function normalizeEnrolement(raw: Raw): Enrolement {
  const hist = Array.isArray(raw.historique) ? raw.historique : [];
  return {
    id: String(raw.id ?? ''),
    compteId: asStr(raw.compte_id),
    collaborateurId: asStr(raw.collaborateur_id),
    collaborateurNom: asStr(raw.collaborateur_nom),
    statut: String(raw.statut ?? 'NON_ENROLE'),
    versionConsentement: asStr(raw.version_consentement),
    decideLe: asStr(raw.decide_le),
    enroleLe: asStr(raw.enrole_le),
    demandeReenrolementLe: asStr(raw.demande_reenrolement_le),
    deviceIdMasque: asStr(raw.device_id_masque),
    deviceIdDemandeMasque: asStr(raw.device_id_demande_masque),
    appareilIncompatible: raw.appareil_incompatible === true,
    echecsConsecutifs: asNum(raw.echecs_consecutifs, 0),
    bloqueJusqua: asStr(raw.bloque_jusqu_a),
    historique: hist.map((h) => normalizeAudit((h ?? {}) as Raw)),
  };
}

// --- Appels ---

export function getReglageFacial() {
  return apiFetch(`${BASE}/reglage`)
    .then((r) => handle<Raw>(r))
    .then((raw) => normalizeReglage(raw ?? {}));
}

export function putReglageFacial(body: ReglageFacialInput) {
  return apiFetch(`${BASE}/reglage`, jsonInit('PUT', body))
    .then((r) => handle<Raw>(r))
    .then((raw) => normalizeReglage(raw ?? {}));
}

export function getConsentements() {
  return apiFetch(`${BASE}/consentements`)
    .then((r) => handle<unknown[]>(r))
    .then((raw) =>
      (Array.isArray(raw) ? raw : []).map((x): ConsentementVersion => {
        const o = (x ?? {}) as Raw;
        return {
          version: String(o.version ?? ''),
          publieLe: asStr(o.publie_le),
          publiePar: asStr(o.publie_par),
          courante: o.courante === true,
        };
      }),
    );
}

export function getConsentementDetail(version: string) {
  return apiFetch(`${BASE}/consentements/${encodeURIComponent(version)}`)
    .then((r) => handle<Raw>(r))
    .then(
      (o): ConsentementDetail => ({
        version: String(o?.version ?? version),
        texte: String(o?.texte ?? ''),
        publieLe: asStr(o?.publie_le),
        publiePar: asStr(o?.publie_par),
      }),
    );
}

export function postConsentement(body: { version: string; texte: string }) {
  return apiFetch(`${BASE}/consentements`, jsonInit('POST', body)).then((r) => handle<Raw>(r));
}

export function getEnrolements(params: EnrolementsParams = {}): Promise<PageEnrolements> {
  const q = new URLSearchParams();
  q.set('page', String(params.page ?? 0));
  q.set('size', String(params.size ?? 20));
  if (params.statut) q.set('statut', params.statut);
  if (params.q?.trim()) q.set('q', params.q.trim());
  if (params.appareilIncompatible === true) q.set('appareil_incompatible', 'true');
  return apiFetch(`${BASE}/enrolements?${q}`)
    .then((r) => handle<Raw>(r))
    .then((raw) => {
      const o = raw ?? {};
      const list = (Array.isArray(o) ? o : ((o.content ?? o.contenu ?? []) as unknown[])) as unknown[];
      const contenu = list.map((x) => normalizeEnrolement((x ?? {}) as Raw));
      const totalElements = asNum(o.totalElements ?? o.total_elements, contenu.length);
      const size = asNum(o.size ?? o.taille, params.size ?? 20) || 20;
      return {
        contenu,
        totalElements,
        totalPages: asNum(o.totalPages ?? o.total_pages, Math.max(1, Math.ceil(totalElements / size))),
        page: asNum(o.number ?? o.page, 0),
      };
    });
}

export function getEnrolement(id: string) {
  return apiFetch(`${BASE}/enrolements/${encodeURIComponent(id)}`)
    .then((r) => handle<Raw>(r))
    .then((raw) => normalizeEnrolement(raw ?? {}));
}

export function postDeblocageEnrolement(id: string, motif: string) {
  return apiFetch(`${BASE}/enrolements/${encodeURIComponent(id)}/deblocage`, jsonInit('POST', { motif })).then((r) =>
    handle<Raw>(r),
  );
}
